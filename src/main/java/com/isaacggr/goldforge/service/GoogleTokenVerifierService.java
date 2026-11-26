package com.isaacggr.goldforge.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.isaacggr.goldforge.config.GoogleAuthProperties;
import com.isaacggr.goldforge.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Serviço responsável por validar criptograficamente os ID Tokens do Google
 * utilizando a biblioteca oficial Google API Client e as chaves públicas do Google.
 */
@Slf4j
@Service
public class GoogleTokenVerifierService {

    public record GoogleUserData(String email, String name, boolean emailVerified) {}

    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleTokenVerifierService(GoogleAuthProperties properties) {
        try {
            this.verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(properties.getClientId()))
                    .build();
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("Falha ao inicializar o verificador de tokens Google", ex);
        }
    }

    public GoogleTokenVerifierService(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public GoogleUserData verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedException("Token do Google não fornecido");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString.trim());
            if (idToken == null) {
                log.warn("⚠️ [GOOGLE AUTH] Token rejeitado pela validação de assinatura ou audiência.");
                throw new UnauthorizedException("Token do Google inválido ou expirado");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            Boolean emailVerified = payload.getEmailVerified();
            if (!Boolean.TRUE.equals(emailVerified)) {
                throw new UnauthorizedException("O e-mail da conta Google não foi verificado.");
            }

            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("E-mail ausente nas credenciais do Google.");
            }

            String name = (String) payload.get("name");
            return new GoogleUserData(email.trim().toLowerCase(), name, true);
        } catch (GeneralSecurityException | IOException ex) {
            log.error("Erro ao validar token Google: {}", ex.getMessage());
            throw new UnauthorizedException("Falha na validação das credenciais do Google");
        }
    }
}
