package com.isaacggr.goldforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades configuráveis do cookie HTTP-Only de autenticação JWT.
 *
 * <p>Em produção ({@code goldforge.com.br} + {@code api.goldforge.com.br}),
 * configurar via variáveis de ambiente: {@code COOKIE_DOMAIN=.goldforge.com.br}
 * e {@code COOKIE_SECURE=true}. Em desenvolvimento local ({@code localhost}),
 * manter {@code domain} vazio e {@code secure=false}.</p>
 */
@ConfigurationProperties(prefix = "app.cookie")
public class AuthCookieProperties {

    private String name = "goldforge_access_token";
    private String domain = "";
    private boolean secure = false;
    private String sameSite = "Lax";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain == null ? "" : domain.trim();
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        if (sameSite != null && !sameSite.isBlank()) {
            this.sameSite = sameSite.trim();
        }
    }
}
