package com.isaacggr.goldforge.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDTO(
        @Schema(description = "ID Token (JWT assinado) emitido pelo Google Identity Services após consentimento do usuário", example = "eyJhbGciOiJSUzI1NiIs...")
        @NotBlank(message = "O ID token do Google é obrigatório")
        String idToken
) {}
