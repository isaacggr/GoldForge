package com.isaacggr.goldforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;

/**
 * Serviço responsável por validar criptograficamente os ID Tokens e Access Tokens do Google
 * utilizando a biblioteca oficial Google API Client e endpoints OIDC do Google.
 */
@Slf4j
@Service
public class GoogleTokenVerifierService {

    public record GoogleUserData(String email, String name, boolean emailVerified) {}

    private final GoogleIdTokenVerifier verifier;
    private final GoogleAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GoogleTokenVerifierService(GoogleAuthProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
        GoogleAuthProperties testProps = new GoogleAuthProperties();
        testProps.setClientId("test-client-id");
        this.properties = testProps;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public GoogleUserData verify(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            throw new UnauthorizedException("Token do Google não fornecido");
        }

        String trimmed = tokenString.trim();

        // Se o token for um access token do Google (começa com ya29.) ou não for formato JWT
        if (trimmed.startsWith("ya29.")) {
            return verifyAccessToken(trimmed);
        } else {
            return verifyIdToken(trimmed);
        }
    }

    private GoogleUserData verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
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

    private GoogleUserData verifyAccessToken(String accessToken) {
        try {
            // 1. Validar audiência/client_id no endpoint tokeninfo oficial do Google
            HttpRequest tokenInfoReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> tokenInfoRes = httpClient.send(tokenInfoReq, HttpResponse.BodyHandlers.ofString());
            if (tokenInfoRes.statusCode() != 200) {
                log.warn("⚠️ [GOOGLE AUTH] Token de acesso rejeitado pelo Google tokeninfo. Status: {}", tokenInfoRes.statusCode());
                throw new UnauthorizedException("Token de acesso do Google inválido ou expirado");
            }

            JsonNode tokenInfoNode = objectMapper.readTree(tokenInfoRes.body());
            String aud = tokenInfoNode.has("aud") ? tokenInfoNode.get("aud").asText() : "";
            String azp = tokenInfoNode.has("azp") ? tokenInfoNode.get("azp").asText() : "";
            String configuredClientId = properties.getClientId();

            if (!configuredClientId.equals(aud) && !configuredClientId.equals(azp)) {
                log.warn("⚠️ [GOOGLE AUTH] Client ID do token [{}/{}] não confere com o configurado [{}]", aud, azp, configuredClientId);
                throw new UnauthorizedException("Token do Google não autorizado para esta aplicação");
            }

            // 2. Buscar perfil oficial (nome, e-mail) no userinfo do Google
            HttpRequest userInfoReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> userInfoRes = httpClient.send(userInfoReq, HttpResponse.BodyHandlers.ofString());
            if (userInfoRes.statusCode() != 200) {
                log.warn("⚠️ [GOOGLE AUTH] Falha ao recuperar userinfo do Google. Status: {}", userInfoRes.statusCode());
                throw new UnauthorizedException("Falha ao recuperar informações da conta Google");
            }

            JsonNode userInfoNode = objectMapper.readTree(userInfoRes.body());
            boolean emailVerified = userInfoNode.has("email_verified") && userInfoNode.get("email_verified").asBoolean();
            if (!emailVerified) {
                throw new UnauthorizedException("O e-mail da conta Google não foi verificado.");
            }

            String email = userInfoNode.has("email") ? userInfoNode.get("email").asText() : null;
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("E-mail ausente nas credenciais do Google.");
            }

            String name = userInfoNode.has("name") ? userInfoNode.get("name").asText() : null;
            return new GoogleUserData(email.trim().toLowerCase(), name, true);
        } catch (IOException | InterruptedException ex) {
            log.error("Erro de comunicação ao validar access token do Google: {}", ex.getMessage());
            Thread.currentThread().interrupt();
            throw new UnauthorizedException("Falha na validação das credenciais do Google");
        }
    }
}
