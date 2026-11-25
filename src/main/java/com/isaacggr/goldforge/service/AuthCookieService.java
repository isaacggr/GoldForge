package com.isaacggr.goldforge.service;

import com.isaacggr.goldforge.config.AuthCookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Serviço responsável por construir cookies HTTP-Only padronizados para sessão JWT
 * e expiração imediata no logout.
 */
@Service
public class AuthCookieService {

    private final AuthCookieProperties properties;

    public AuthCookieService(AuthCookieProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie createAuthCookie(String token, long expirationMillis) {
        return buildCookie(token, Duration.ofMillis(Math.max(0L, expirationMillis)));
    }

    public ResponseCookie createLogoutCookie() {
        return buildCookie("", Duration.ZERO);
    }

    public String getCookieName() {
        return properties.getName();
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.getName(), value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .path("/")
                .maxAge(maxAge)
                .sameSite(properties.getSameSite());

        if (properties.getDomain() != null && !properties.getDomain().isBlank()) {
            builder.domain(properties.getDomain().trim());
        }

        return builder.build();
    }
}
