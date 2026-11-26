package com.isaacggr.goldforge.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponseDTO(
        @Schema(description = "Tipo de sessão emitida", example = "Cookie")
        String type,
        @Schema(description = "Tempo de expiração da sessão em milissegundos", example = "86400000")
        Long expiresIn,
        @Schema(description = "Presente como true quando uma conta excluída dentro do prazo de 30 dias é reativada automaticamente neste login", example = "true")
        Boolean reactivated
) {
    public LoginResponseDTO(String type, Long expiresIn) {
        this(type, expiresIn, null);
    }

    public static LoginResponseDTO of(Long expiresIn) {
        return new LoginResponseDTO("Cookie", expiresIn, null);
    }

    public static LoginResponseDTO reactivated(Long expiresIn) {
        return new LoginResponseDTO("Cookie", expiresIn, true);
    }
}
