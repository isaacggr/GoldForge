package com.isaacggr.goldforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de integração com Google Identity Services (OAuth 2.0 / OIDC).
 */
@ConfigurationProperties(prefix = "app.google")
public class GoogleAuthProperties {

    private String clientId = "895347964168-sfcf2qha4g44nee45nqb3qf36a2bd2li.apps.googleusercontent.com";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            this.clientId = clientId.trim();
        }
    }
}
