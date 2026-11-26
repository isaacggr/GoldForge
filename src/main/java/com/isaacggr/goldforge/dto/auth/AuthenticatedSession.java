package com.isaacggr.goldforge.dto.auth;

/**
 * Resultado interno de autenticação contendo o JWT bruto (destinado ao cookie HTTP-Only)
 * e os metadados públicos retornados no corpo JSON da resposta.
 */
public record AuthenticatedSession(
        String token,
        LoginResponseDTO response
) {}
