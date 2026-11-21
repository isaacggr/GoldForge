package com.isaacggr.goldforge.service;

import com.isaacggr.goldforge.domain.entity.Goal;
import com.isaacggr.goldforge.domain.entity.GoalContribution;
import com.isaacggr.goldforge.domain.entity.Transaction;
import com.isaacggr.goldforge.domain.entity.User;
import com.isaacggr.goldforge.domain.enums.GoalContributionType;
import com.isaacggr.goldforge.domain.enums.GoalStatus;
import com.isaacggr.goldforge.domain.enums.TransactionType;
import com.isaacggr.goldforge.dto.goal.GoalContributionCreateDTO;
import com.isaacggr.goldforge.dto.goal.GoalContributionResponseDTO;
import com.isaacggr.goldforge.dto.goal.GoalCreateDTO;
import com.isaacggr.goldforge.dto.goal.GoalResponseDTO;
import com.isaacggr.goldforge.dto.goal.GoalUpdateDTO;
import com.isaacggr.goldforge.dto.goal.GoalWithdrawRequestDTO;
import com.isaacggr.goldforge.exception.ConflictException;
import com.isaacggr.goldforge.exception.NotFoundException;
import com.isaacggr.goldforge.repository.GoalContributionRepository;
import com.isaacggr.goldforge.repository.GoalRepository;
import com.isaacggr.goldforge.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalContributionRepository goalContributionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private GamificationService gamificationService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private GoalService goalService;

    private User sampleUser;
    private UUID userId;
    private UUID goalId;
    private Goal sampleGoal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        goalId = UUID.randomUUID();

        sampleUser = new User("Isaac Gregorio", "isaac@example.com", "hash123");
        ReflectionTestUtils.setField(sampleUser, "id", userId);

        sampleGoal = new Goal(
                sampleUser,
                "Reserva de Emergência",
                "6 meses de custos fixos",
                new BigDecimal("4000.00"),
                LocalDate.of(2027, 12, 31),
                true
        );
        ReflectionTestUtils.setField(sampleGoal, "id", goalId);

        lenient().when(transactionService.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Transaction tx = new Transaction(
                            inv.getArgument(0),
                            inv.getArgument(1),
                            inv.getArgument(2),
                            inv.getArgument(3),
                            inv.getArgument(4),
                            inv.getArgument(5)
                    );
                    ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
                    return tx;
                });
    }

    @Test
    @DisplayName("Deve criar nova meta como principal por padrão, desmarcar anterior e registrar aporte inicial")
    void create_DefaultsToPrimary_ClearsPrevious_AndRecordsInitialContribution() {
        Goal oldPrimary = new Goal(sampleUser, "Viagem", null, new BigDecimal("2000.00"), null, true);
        ReflectionTestUtils.setField(oldPrimary, "id", UUID.randomUUID());

        when(goalRepository.findByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.of(oldPrimary));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            if (g.getId() == null) {
                ReflectionTestUtils.setField(g, "id", UUID.randomUUID());
            }
            return g;
        });
        when(goalContributionRepository.save(any(GoalContribution.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GoalCreateDTO dto = new GoalCreateDTO(
                "  Notebook Novo  ",
                "  Para trabalho  ",
                new BigDecimal("4000.00"),
                new BigDecimal("1000.00"),
                LocalDate.of(2027, 6, 30),
                null
        );

        GoalResponseDTO response = goalService.create(sampleUser, dto);

        assertFalse(oldPrimary.isPrimary());
        assertTrue(response.primary());
        assertEquals("Notebook Novo", response.title());
        assertEquals("Para trabalho", response.description());
        assertEquals(new BigDecimal("1000.00"), response.currentAmount());
        assertEquals(new BigDecimal("3000.00"), response.remainingAmount());
        assertEquals(new BigDecimal("25.00"), response.percentage());
        assertEquals(GoalStatus.IN_PROGRESS, response.status());

        ArgumentCaptor<GoalContribution> contribCaptor = ArgumentCaptor.forClass(GoalContribution.class);
        verify(goalContributionRepository).save(contribCaptor.capture());
        assertEquals("Saldo inicial pré-existente", contribCaptor.getValue().getNote());
        assertEquals(new BigDecimal("1000.00"), contribCaptor.getValue().getAmount());
        assertNull(contribCaptor.getValue().getTransaction());
        verify(transactionService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve preservar meta principal existente quando criar nova meta com primary=false")
    void create_WithExplicitPrimaryFalse_PreservesExistingPrimary() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalCreateDTO dto = new GoalCreateDTO(
                "Curso Inglês",
                null,
                new BigDecimal("3000.00"),
                null,
                null,
                false
        );

        GoalResponseDTO response = goalService.create(sampleUser, dto);

        assertFalse(response.primary());
        assertEquals(new BigDecimal("0.00"), response.currentAmount());
        assertEquals(new BigDecimal("0.00"), response.percentage());
        verify(goalRepository, never()).findByUserIdAndPrimaryTrue(any());
        verify(goalContributionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar NotFoundException ao criar meta para usuário nulo ou deletado")
    void create_ThrowsNotFound_WhenUserNullOrDeleted() {
        GoalCreateDTO dto = new GoalCreateDTO("Meta", null, new BigDecimal("100.00"), null, null, true);

        assertThrows(NotFoundException.class, () -> goalService.create(null, dto));

        sampleUser.markAsDeleted();
        assertThrows(NotFoundException.class, () -> goalService.create(sampleUser, dto));
        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve listar metas do usuário ordenadas e consultar meta principal opcional")
    void listAllAndFindPrimary_WorkCorrectly() {
        when(goalRepository.findByUserIdOrderByPrimaryDescCreatedAtDesc(userId)).thenReturn(List.of(sampleGoal));
        when(goalRepository.findByUserIdAndPrimaryTrue(userId))
                .thenReturn(Optional.of(sampleGoal))
                .thenReturn(Optional.empty());

        List<GoalResponseDTO> all = goalService.listAll(userId);
        Optional<GoalResponseDTO> foundPrimary = goalService.findPrimary(userId);
        Optional<GoalResponseDTO> emptyPrimary = goalService.findPrimary(userId);

        assertEquals(1, all.size());
        assertEquals("Reserva de Emergência", all.getFirst().title());
        assertTrue(foundPrimary.isPresent());
        assertTrue(emptyPrimary.isEmpty());
    }

    @Test
    @DisplayName("Deve promover outra meta como principal desmarcando a anterior e ser idempotente se já principal")
    void setAsPrimary_SwapsPrimarySafelyAndIsIdempotent() {
        Goal secondaryGoal = new Goal(sampleUser, "Viagem", null, new BigDecimal("5000.00"), null, false);
        UUID secondaryId = UUID.randomUUID();
        ReflectionTestUtils.setField(secondaryGoal, "id", secondaryId);

        when(goalRepository.findByIdAndUserId(secondaryId, userId)).thenReturn(Optional.of(secondaryGoal));
        when(goalRepository.findByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.of(sampleGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponseDTO promoted = goalService.setAsPrimary(secondaryId, userId);

        assertFalse(sampleGoal.isPrimary());
        assertTrue(promoted.primary());

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));
        sampleGoal.markAsPrimary();
        clearInvocations(goalRepository);
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));

        GoalResponseDTO alreadyPrimary = goalService.setAsPrimary(goalId, userId);
        assertTrue(alreadyPrimary.primary());
        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atualizar campos da meta e promover a principal quando solicitado")
    void update_UpdatesFieldsAndSwapsPrimary() {
        Goal targetGoal = new Goal(sampleUser, "Férias", null, new BigDecimal("3000.00"), null, false);
        UUID targetId = UUID.randomUUID();
        ReflectionTestUtils.setField(targetGoal, "id", targetId);

        when(goalRepository.findByIdAndUserId(targetId, userId)).thenReturn(Optional.of(targetGoal));
        when(goalRepository.findByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.of(sampleGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalUpdateDTO dto = new GoalUpdateDTO(
                "Férias Europa",
                "Passagem + hospedagem",
                new BigDecimal("6000.00"),
                LocalDate.of(2028, 1, 15),
                true
        );

        GoalResponseDTO updated = goalService.update(targetId, userId, dto);

        assertFalse(sampleGoal.isPrimary());
        assertTrue(updated.primary());
        assertEquals("Férias Europa", updated.title());
        assertEquals(new BigDecimal("6000.00"), updated.targetAmount());
        assertEquals(LocalDate.of(2028, 1, 15), updated.deadline());
    }

    @Test
    @DisplayName("Deve registrar aporte com cálculo exato em HALF_EVEN e marcar meta como COMPLETED ao atingir alvo")
    void addContribution_CalculatesPercentageAndCompletesGoal() {
        Goal goal = new Goal(sampleUser, "Celular", null, new BigDecimal("300.00"), null, true);
        ReflectionTestUtils.setField(goal, "id", goalId);

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(goalContributionRepository.save(any(GoalContribution.class))).thenAnswer(inv -> {
            GoalContribution c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });
        when(goalRepository.save(goal)).thenReturn(goal);

        GoalContributionResponseDTO first = goalService.addContribution(
                goalId, userId, new GoalContributionCreateDTO(new BigDecimal("100.00"), LocalDate.of(2026, 11, 11), "Parcela 1")
        );
        assertEquals(new BigDecimal("33.33"), first.goal().percentage());
        assertEquals(new BigDecimal("200.00"), first.goal().remainingAmount());
        assertEquals(GoalStatus.IN_PROGRESS, first.goal().status());

        GoalContributionResponseDTO second = goalService.addContribution(
                goalId, userId, new GoalContributionCreateDTO(new BigDecimal("250.00"), null, "Restante")
        );
        assertEquals(new BigDecimal("350.00"), second.goal().currentAmount());
        assertEquals(new BigDecimal("0.00"), second.goal().remainingAmount());
        assertEquals(new BigDecimal("116.67"), second.goal().percentage());
        assertEquals(GoalStatus.COMPLETED, second.goal().status());
        assertNotNull(second.contributionDate());
    }

    @Test
    @DisplayName("Deve estornar aporte revertendo saldo/status da meta e respeitar Zero IDOR")
    void deleteContribution_RevertsAmountAndEnforcesOwnership() {
        sampleGoal.applyContribution(new BigDecimal("4000.00"));
        assertEquals(GoalStatus.COMPLETED, sampleGoal.getStatus());

        UUID contribId = UUID.randomUUID();
        GoalContribution contribution = new GoalContribution(sampleGoal, new BigDecimal("500.00"), LocalDate.now(), "Extra");
        ReflectionTestUtils.setField(contribution, "id", contribId);

        when(goalContributionRepository.findByIdAndGoalIdAndGoalUserId(contribId, goalId, userId))
                .thenReturn(Optional.of(contribution));

        goalService.deleteContribution(goalId, contribId, userId);

        assertEquals(new BigDecimal("3500.00"), sampleGoal.getCurrentAmount());
        assertEquals(GoalStatus.IN_PROGRESS, sampleGoal.getStatus());
        verify(goalContributionRepository).delete(contribution);
        verify(goalRepository).save(sampleGoal);

        UUID foreignContribId = UUID.randomUUID();
        when(goalContributionRepository.findByIdAndGoalIdAndGoalUserId(foreignContribId, goalId, userId))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> goalService.deleteContribution(goalId, foreignContribId, userId));
    }

    @Test
    @DisplayName("Deve garantir Zero IDOR ao buscar/listar aportes e promover a mais recente ao excluir meta principal")
    void ownershipGuardsAndDelete_EnforceIdorAndPromoteFallbackPrimary() {
        UUID foreignGoalId = UUID.randomUUID();
        when(goalRepository.findByIdAndUserId(foreignGoalId, userId)).thenReturn(Optional.empty());
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));

        assertThrows(NotFoundException.class, () -> goalService.findById(foreignGoalId, userId));
        assertThrows(NotFoundException.class, () -> goalService.listContributions(foreignGoalId, userId));
        assertThrows(NotFoundException.class, () -> goalService.delete(foreignGoalId, userId));

        Goal fallbackGoal = new Goal(sampleUser, "Reserva Secundária", null, new BigDecimal("2000.00"), null, false);
        ReflectionTestUtils.setField(fallbackGoal, "id", UUID.randomUUID());
        when(transactionRepository.findByGoalId(goalId)).thenReturn(List.of());
        when(goalContributionRepository.findByGoalIdOrderByContributionDateDescCreatedAtDesc(goalId)).thenReturn(List.of());
        when(goalRepository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(userId, goalId))
                .thenReturn(Optional.of(fallbackGoal));

        goalService.delete(goalId, userId);

        verify(goalRepository).delete(sampleGoal);
        assertTrue(fallbackGoal.isPrimary());
        verify(goalRepository).save(fallbackGoal);
    }

    @Test
    @DisplayName("Deve excluir transações vinculadas e contribuições ao deletar meta para estornar saldo do mês")
    void delete_DeletesLinkedTransactionsAndRestoresCheckingBalance() {
        Transaction savingsTx = new Transaction(sampleUser, new BigDecimal("500.00"), TransactionType.SAVINGS, "METAS", LocalDate.now(), "Aporte: Reserva");
        ReflectionTestUtils.setField(savingsTx, "id", UUID.randomUUID());

        GoalContribution contribution = new GoalContribution(sampleGoal, new BigDecimal("500.00"), LocalDate.now(), "Aporte", GoalContributionType.CONTRIBUTION, savingsTx);
        ReflectionTestUtils.setField(contribution, "id", UUID.randomUUID());

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));
        when(transactionRepository.findByGoalId(goalId)).thenReturn(List.of(savingsTx));
        when(goalContributionRepository.findByGoalIdOrderByContributionDateDescCreatedAtDesc(goalId)).thenReturn(List.of(contribution));

        goalService.delete(goalId, userId);

        assertNull(contribution.getTransaction());
        verify(goalContributionRepository).deleteAll(List.of(contribution));
        verify(transactionRepository).deleteAll(List.of(savingsTx));
        verify(goalRepository).delete(sampleGoal);
    }

    @Test
    @DisplayName("Deve notificar meta concluída quando aporte atingir 100% do objetivo")
    void addContribution_NotifiesWhenGoalCompleted() {
        sampleGoal.applyContribution(new BigDecimal("3500.00"));
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));
        when(goalContributionRepository.save(any(GoalContribution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalContributionResponseDTO response = goalService.addContribution(
                goalId, userId, new GoalContributionCreateDTO(new BigDecimal("500.00"), LocalDate.now(), "Meta batida")
        );

        assertEquals(GoalStatus.COMPLETED, response.goal().status());
        verify(notificationService).notifyGoalCompleted(sampleUser, sampleGoal);
    }

    @Test
    @DisplayName("Deve realizar saque parcial da meta gerando transação de entrada (INCOME) e reduzindo saldo da meta")
    void withdraw_Partial_Success_ReducesGoalAndCreatesIncomeTransaction() {
        sampleGoal.applyContribution(new BigDecimal("2000.00"));
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));
        when(goalContributionRepository.save(any(GoalContribution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalWithdrawRequestDTO dto = new GoalWithdrawRequestDTO(
                new BigDecimal("600.00"),
                LocalDate.of(2026, 11, 21),
                "Manutenção do carro",
                false
        );

        GoalContributionResponseDTO response = goalService.withdraw(goalId, userId, dto);

        assertNotNull(response);
        assertEquals(GoalContributionType.WITHDRAWAL, response.type());
        assertEquals(new BigDecimal("600.00"), response.amount());
        assertEquals(new BigDecimal("1400.00"), response.goal().currentAmount());
        assertEquals(GoalStatus.IN_PROGRESS, response.goal().status());

        verify(transactionService).create(
                eq(sampleUser),
                eq(new BigDecimal("600.00")),
                eq(TransactionType.INCOME),
                eq("RESGATE DE META"),
                eq(LocalDate.of(2026, 11, 21)),
                eq("Resgate: " + sampleGoal.getTitle()),
                isNull(),
                isNull(),
                isNull(),
                eq(sampleGoal),
                isNull()
        );
    }

    @Test
    @DisplayName("Deve lançar ConflictException quando valor do saque exceder o saldo acumulado da meta")
    void withdraw_ThrowsConflict_WhenAmountExceedsCurrentAmount() {
        sampleGoal.applyContribution(new BigDecimal("500.00"));
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));

        GoalWithdrawRequestDTO dto = new GoalWithdrawRequestDTO(
                new BigDecimal("800.00"),
                null,
                "Tentativa excessiva",
                false
        );

        ConflictException ex = assertThrows(ConflictException.class, () -> goalService.withdraw(goalId, userId, dto));
        assertTrue(ex.getMessage().contains("Saldo insuficiente"));
    }

    @Test
    @DisplayName("Deve marcar meta como REALIZED (troféu) quando resgate total for solicitado")
    void withdraw_MarksAsRealized_WhenRequested() {
        sampleGoal.applyContribution(new BigDecimal("4000.00"));
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(sampleGoal));
        when(goalContributionRepository.save(any(GoalContribution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalWithdrawRequestDTO dto = new GoalWithdrawRequestDTO(
                new BigDecimal("4000.00"),
                null,
                "Sonho realizado!",
                true
        );

        GoalContributionResponseDTO response = goalService.withdraw(goalId, userId, dto);

        assertEquals(GoalStatus.REALIZED, response.goal().status());
        assertEquals(new BigDecimal("0.00"), response.goal().currentAmount());
    }

    @Test
    @DisplayName("Deve reverter movimentação de saque devolvendo saldo à meta e excluindo transação correspondente")
    void deleteContribution_WhenWithdrawal_RevertsAmountAndDeletesTransaction() {
        sampleGoal.applyContribution(new BigDecimal("2000.00"));
        sampleGoal.applyWithdrawal(new BigDecimal("500.00"));
        assertEquals(new BigDecimal("1500.00"), sampleGoal.getCurrentAmount());

        UUID contribId = UUID.randomUUID();
        Transaction linkedTx = new Transaction(sampleUser, new BigDecimal("500.00"), TransactionType.INCOME, "RESGATE DE META", LocalDate.now(), "Resgate");
        GoalContribution withdrawal = new GoalContribution(
                sampleGoal,
                new BigDecimal("500.00"),
                LocalDate.now(),
                "Saque",
                GoalContributionType.WITHDRAWAL,
                linkedTx
        );
        ReflectionTestUtils.setField(withdrawal, "id", contribId);

        when(goalContributionRepository.findByIdAndGoalIdAndGoalUserId(contribId, goalId, userId))
                .thenReturn(Optional.of(withdrawal));

        goalService.deleteContribution(goalId, contribId, userId);

        assertEquals(new BigDecimal("2000.00"), sampleGoal.getCurrentAmount());
        verify(goalContributionRepository).delete(withdrawal);
        verify(goalRepository).save(sampleGoal);
        verify(transactionRepository).delete(linkedTx);
    }
}
