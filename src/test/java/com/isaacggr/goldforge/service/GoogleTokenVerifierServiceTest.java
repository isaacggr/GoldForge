package com.isaacggr.goldforge.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.isaacggr.goldforge.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários - GoogleTokenVerifierService")
class GoogleTokenVerifierServiceTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    @Mock
    private GoogleIdToken idToken;

    @Mock
    private GoogleIdToken.Payload payload;

    private GoogleTokenVerifierService service;

    @BeforeEach
    void setUp() {
        service = new GoogleTokenVerifierService(verifier);
    }

    @Test
    @DisplayName("Deve extrair e-mail e nome com sucesso quando o token for válido e e-mail estiver verificado")
    void verify_Success() throws Exception {
        when(verifier.verify("valid.token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmailVerified()).thenReturn(true);
        when(payload.getEmail()).thenReturn("ISAAC@GMAIL.COM");
        when(payload.get("name")).thenReturn("Isaac Gregório");

        GoogleTokenVerifierService.GoogleUserData data = service.verify("valid.token");

        assertNotNull(data);
        assertEquals("isaac@gmail.com", data.email(), "E-mail deve ser normalizado em minúsculas");
        assertEquals("Isaac Gregório", data.name());
        assertTrue(data.emailVerified());
    }

    @Test
    @DisplayName("Deve lançar UnauthorizedException quando o token for nulo ou em branco")
    void verify_ThrowsUnauthorized_WhenTokenBlank() {
        assertThrows(UnauthorizedException.class, () -> service.verify(null));
        assertThrows(UnauthorizedException.class, () -> service.verify("   "));
    }

    @Test
    @DisplayName("Deve lançar UnauthorizedException quando o verificador rejeitar o token (retornar null)")
    void verify_ThrowsUnauthorized_WhenTokenInvalid() throws Exception {
        when(verifier.verify("invalid.token")).thenReturn(null);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> service.verify("invalid.token"));
        assertEquals("Token do Google inválido ou expirado", ex.getMessage());
    }

    @Test
    @DisplayName("Deve lançar UnauthorizedException quando o e-mail não estiver verificado no Google")
    void verify_ThrowsUnauthorized_WhenEmailNotVerified() throws Exception {
        when(verifier.verify("unverified.token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmailVerified()).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> service.verify("unverified.token"));
        assertEquals("O e-mail da conta Google não foi verificado.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve lançar UnauthorizedException quando ocorrer erro de segurança ao verificar")
    void verify_ThrowsUnauthorized_OnSecurityException() throws Exception {
        when(verifier.verify("error.token")).thenThrow(new GeneralSecurityException("Assinatura inválida"));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> service.verify("error.token"));
        assertEquals("Falha na validação das credenciais do Google", ex.getMessage());
    }
}
