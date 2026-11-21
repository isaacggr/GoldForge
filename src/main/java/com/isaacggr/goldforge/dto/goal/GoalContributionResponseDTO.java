package com.isaacggr.goldforge.dto.goal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.isaacggr.goldforge.domain.entity.Goal;
import com.isaacggr.goldforge.domain.entity.GoalContribution;
import com.isaacggr.goldforge.domain.enums.GoalContributionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalContributionResponseDTO(
        @Schema(description = "Identificador único do aporte ou saque", example = "a91f7f64-2b43-4c11-941a-758420c45123")
        UUID id,

        @Schema(description = "Identificador da meta vinculada", example = "6fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID goalId,

        @Schema(description = "Valor movimentado (aporte ou saque)", example = "500.00")
        BigDecimal amount,

        @Schema(description = "Data da movimentação", example = "2026-11-11")
        LocalDate contributionDate,

        @Schema(description = "Observação opcional da movimentação", example = "13º salário - 1ª parcela")
        String note,

        @Schema(description = "Data e hora do registro", example = "2026-11-11T19:55:00")
        LocalDateTime createdAt,

        @Schema(description = "Estado atualizado da meta retornado após a movimentação")
        GoalResponseDTO goal,

        @Schema(description = "Tipo da movimentação (CONTRIBUTION ou WITHDRAWAL)", example = "CONTRIBUTION")
        GoalContributionType type,

        @Schema(description = "ID da transação financeira gerada no extrato", example = "a6703554-1fe5-412f-9b09-cf80ef2df81c")
        UUID transactionId
) {
    public GoalContributionResponseDTO(
            UUID id,
            UUID goalId,
            BigDecimal amount,
            LocalDate contributionDate,
            String note,
            LocalDateTime createdAt,
            GoalResponseDTO goal
    ) {
        this(id, goalId, amount, contributionDate, note, createdAt, goal, GoalContributionType.CONTRIBUTION, null);
    }

    public static GoalContributionResponseDTO fromEntity(GoalContribution contribution) {
        return new GoalContributionResponseDTO(
                contribution.getId(),
                contribution.getGoal().getId(),
                contribution.getAmount(),
                contribution.getContributionDate(),
                contribution.getNote(),
                contribution.getCreatedAt(),
                null,
                contribution.getType(),
                contribution.getTransaction() != null ? contribution.getTransaction().getId() : null
        );
    }

    public static GoalContributionResponseDTO fromEntityWithGoal(GoalContribution contribution, Goal goal) {
        return new GoalContributionResponseDTO(
                contribution.getId(),
                goal.getId(),
                contribution.getAmount(),
                contribution.getContributionDate(),
                contribution.getNote(),
                contribution.getCreatedAt(),
                GoalResponseDTO.fromEntity(goal),
                contribution.getType(),
                contribution.getTransaction() != null ? contribution.getTransaction().getId() : null
        );
    }
}
