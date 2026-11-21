package com.isaacggr.goldforge.dto.goal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalWithdrawRequestDTO(
        @Schema(description = "Valor a ser resgatado/sacado da meta (escala 2, RoundingMode.HALF_EVEN)", example = "300.00")
        @NotNull(message = "O valor do saque é obrigatório")
        @Positive(message = "O valor do saque deve ser positivo")
        @DecimalMin(value = "0.01", message = "O valor mínimo do saque é 0.01")
        @DecimalMax(value = "9999999999999.99", message = "O valor máximo permitido é 9999999999999.99")
        @Digits(integer = 13, fraction = 2, message = "O valor deve ter no máximo 13 dígitos inteiros e 2 casas decimais")
        BigDecimal amount,

        @Schema(description = "Data do saque (AAAA-MM-DD); assume a data atual em America/Sao_Paulo quando omitida", example = "2026-11-21")
        LocalDate withdrawalDate,

        @Schema(description = "Observação opcional sobre o motivo do resgate", example = "Troca de pneus do carro")
        @Size(max = 255, message = "A observação deve ter no máximo 255 caracteres")
        String note,

        @Schema(description = "Indica se o resgate conclui a realização definitiva da meta (troféu permanente no histórico)", example = "false")
        Boolean realizeGoal
) {
}
