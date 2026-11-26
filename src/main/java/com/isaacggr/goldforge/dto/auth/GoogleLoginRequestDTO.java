package com.isaacggr.goldforge.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record GoogleLoginRequestDTO(
        @Schema(description = "ID Token (JWT assinado) emitido pelo Google", example = "eyJhbGciOiJSUzI1NiIs...")
        String idToken,

        @Schema(description = "Access Token OAuth 2.0 emitido pelo Google", example = "ya29.a0AfH6SM...")
        String accessToken
) {
    public GoogleLoginRequestDTO(String idToken) {
        this(idToken, null);
    }

    public String token() {
        if (idToken != null && !idToken.isBlank()) {
            return idToken.trim();
        }
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken.trim();
        }
        return null;
    }
}
