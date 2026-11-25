package com.isaacggr.goldforge.config;

import com.isaacggr.goldforge.domain.entity.User;
import com.isaacggr.goldforge.repository.UserRepository;
import com.isaacggr.goldforge.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthCookieProperties authCookieProperties;

    public SecurityFilter(
            TokenService tokenService,
            UserRepository userRepository,
            AuthCookieProperties authCookieProperties
    ) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.authCookieProperties = authCookieProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = recoverToken(request);

        if (token != null) {
            String subject = tokenService.validateToken(token);

            if (subject != null) {
                try {
                    UUID userId = UUID.fromString(subject);
                    userRepository.findByIdAndDeletedFalse(userId)
                            .filter(User::isEmailVerified)
                            .ifPresent(user -> {
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        user,
                                        null,
                                        Collections.emptyList()
                                );
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            });
                } catch (IllegalArgumentException ignored) {
                    // Se o subject não for um UUID válido, ignora e segue a cadeia
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        // Prioriza o Cookie HTTP-Only First-Party; mantém Bearer Header como fallback (Swagger/Postman/Mobile)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieName = authCookieProperties.getName();
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}

