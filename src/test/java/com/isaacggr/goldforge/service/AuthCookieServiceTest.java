package com.isaacggr.goldforge.service;

import com.isaacggr.goldforge.config.AuthCookieProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes Unitários - AuthCookieService")
class AuthCookieServiceTest {

    @Test
    @DisplayName("Deve gerar cookie HTTP-Only com domínio e flag Secure configurados para produção")
    void createAuthCookie_ProductionSettings() {
        AuthCookieProperties props = new AuthCookieProperties();
        props.setName("goldforge_access_token");
        props.setDomain(".goldforge.com.br");
        props.setSecure(true);
        props.setSameSite("Lax");

        AuthCookieService service = new AuthCookieService(props);
        ResponseCookie cookie = service.createAuthCookie("jwt.token.value", 86_400_000L);

        assertEquals("goldforge_access_token", cookie.getName());
        assertEquals("jwt.token.value", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals(".goldforge.com.br", cookie.getDomain());
        assertEquals("/", cookie.getPath());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals(Duration.ofHours(24), cookie.getMaxAge());
    }

    @Test
    @DisplayName("Deve omitir atributo Domain quando vazio (desenvolvimento local localhost)")
    void createAuthCookie_LocalDevelopmentOmitsDomain() {
        AuthCookieProperties props = new AuthCookieProperties();
        props.setDomain("   ");
        props.setSecure(false);

        AuthCookieService service = new AuthCookieService(props);
        ResponseCookie cookie = service.createAuthCookie("local.jwt", 3_600_000L);

        assertNull(cookie.getDomain());
        assertFalse(cookie.isSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getSameSite());
    }

    @Test
    @DisplayName("Deve gerar cookie de logout com valor vazio e Max-Age=0 preservando atributos")
    void createLogoutCookie_ExpiresImmediately() {
        AuthCookieProperties props = new AuthCookieProperties();
        props.setDomain(".goldforge.com.br");
        props.setSecure(true);

        AuthCookieService service = new AuthCookieService(props);
        ResponseCookie cookie = service.createLogoutCookie();

        assertEquals("goldforge_access_token", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals(".goldforge.com.br", cookie.getDomain());
        assertEquals("Lax", cookie.getSameSite());
    }
}
