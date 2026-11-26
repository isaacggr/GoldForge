package com.isaacggr.goldforge.service;

import com.isaacggr.goldforge.domain.entity.User;
import com.isaacggr.goldforge.domain.entity.UserNotificationPreference;
import com.isaacggr.goldforge.domain.entity.VerificationToken;
import com.isaacggr.goldforge.domain.enums.TokenType;
import com.isaacggr.goldforge.dto.auth.AuthenticatedSession;
import com.isaacggr.goldforge.dto.auth.ForgotPasswordRequestDTO;
import com.isaacggr.goldforge.dto.auth.GoogleLoginRequestDTO;
import com.isaacggr.goldforge.dto.auth.LoginRequestDTO;
import com.isaacggr.goldforge.dto.auth.LoginResponseDTO;
import com.isaacggr.goldforge.dto.auth.ResetPasswordRequestDTO;
import com.isaacggr.goldforge.dto.auth.VerifyEmailRequestDTO;
import com.isaacggr.goldforge.exception.ForbiddenException;
import com.isaacggr.goldforge.exception.UnauthorizedException;
import com.isaacggr.goldforge.repository.UserNotificationPreferenceRepository;
import com.isaacggr.goldforge.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final long expirationMillis;

    public AuthService(
            UserRepository userRepository,
            UserNotificationPreferenceRepository preferenceRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            VerificationTokenService verificationTokenService,
            EmailService emailService,
            GoogleTokenVerifierService googleTokenVerifierService,
            @Value("${jwt.expiration:86400000}") long expirationMillis
    ) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.verificationTokenService = verificationTokenService;
        this.emailService = emailService;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.expirationMillis = expirationMillis;
    }

    @Transactional
    public AuthenticatedSession login(LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Credenciais inválidas");
        }

        if (user.isDeleted() && !user.canReactivate(LocalDateTime.now())) {
            throw new UnauthorizedException("Credenciais inválidas");
        }

        // 🔒 Bloqueio estrito de contas com e-mail pendente de ativação (HTTP 403 Forbidden)
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Por favor, confirme seu e-mail antes de efetuar login.");
        }

        // 🔒 Lazy Rehashing: Se a senha foi autenticada via BCrypt ou parâmetros legados,
        // promove de forma transparente e atômica para Argon2id no banco de dados.
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.updatePassword(passwordEncoder.encode(dto.password()));
            userRepository.save(user);
            log.info("🔒 [LAZY REHASH] Senha do usuário [{}] migrada com sucesso de BCrypt para Argon2id.", user.getId());
        }

        boolean reactivated = false;
        if (user.isDeleted()) {
            user.reactivate();
            userRepository.save(user);
            reactivated = true;
        }

        String token = tokenService.generateToken(user);
        LoginResponseDTO response = reactivated
                ? LoginResponseDTO.reactivated(expirationMillis)
                : LoginResponseDTO.of(expirationMillis);
        return new AuthenticatedSession(token, response);
    }

    // ==========================================
    // LOGIN SOCIAL COM GOOGLE (OIDC / OAuth 2.0)
    // ==========================================
    @Transactional
    public AuthenticatedSession loginWithGoogle(GoogleLoginRequestDTO dto) {
        GoogleTokenVerifierService.GoogleUserData userData = googleTokenVerifierService.verify(dto.idToken());
        String email = userData.email();

        Optional<User> optionalUser = userRepository.findByEmail(email);
        User user;
        boolean reactivated = false;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();

            if (user.isDeleted()) {
                if (!user.canReactivate(LocalDateTime.now())) {
                    throw new UnauthorizedException("Conta expirada após 30 dias da exclusão");
                }
                user.reactivate();
                reactivated = true;
            }

            // Conta existente com e-mail pendente é ativada imediatamente pela autoridade do Google
            if (!user.isEmailVerified()) {
                user.verifyEmail();
            }

            if ((user.getName() == null || user.getName().isBlank()) && userData.name() != null && !userData.name().isBlank()) {
                user.changeName(userData.name().trim());
            }

            userRepository.save(user);
            log.info("🌐 [GOOGLE AUTH] Login em conta existente efetuado com sucesso para usuário [{}]", user.getId());
        } else {
            String name = (userData.name() != null && !userData.name().isBlank())
                    ? userData.name().trim()
                    : "Aventureiro";

            // Gera hash seguro aleatório com Argon2id para satisfazer a constraint NOT NULL do banco
            String randomSecret = UUID.randomUUID().toString() + UUID.randomUUID().toString();
            String randomPasswordHash = passwordEncoder.encode(randomSecret);

            user = new User(name, email, randomPasswordHash);
            user.verifyEmail();
            user = userRepository.save(user);
            preferenceRepository.save(UserNotificationPreference.defaultFor(user));
            log.info("🌐 [GOOGLE AUTH] Nova conta criada com sucesso via Google com ID [{}]", user.getId());
        }

        String token = tokenService.generateToken(user);
        LoginResponseDTO response = reactivated
                ? LoginResponseDTO.reactivated(expirationMillis)
                : LoginResponseDTO.of(expirationMillis);
        return new AuthenticatedSession(token, response);
    }

    // ==========================================
    // CONFIRMAÇÃO E ATIVAÇÃO DE CONTA
    // ==========================================
    @Transactional
    public void verifyEmail(VerifyEmailRequestDTO dto) {
        VerificationToken verificationToken = verificationTokenService.validateTokenWithEmail(
                dto.email(),
                dto.code(),
                TokenType.EMAIL_VERIFICATION
        );
        User user = verificationToken.getUser();
        user.verifyEmail();
        userRepository.save(user);
    }

    // ==========================================
    // REENVIO DE E-MAIL DE CONFIRMAÇÃO
    // ==========================================
    @Transactional
    public void resendVerificationEmail(String email) {
        userRepository.findByEmail(email)
                .filter(User::isActive)
                .ifPresent(user -> {
                    if (!user.isEmailVerified()) {
                        VerificationToken token = verificationTokenService.createEmailVerificationToken(user);
                        emailService.sendVerificationEmail(user.getEmail(), user.getName(), token.getToken());
                    }
                });
    }

    // ==========================================
    // RECUPERAÇÃO DE SENHA - SOLICITAÇÃO (OWASP Anti-Enumeração)
    // ==========================================
    @Transactional
    public void forgotPassword(ForgotPasswordRequestDTO dto) {
        userRepository.findByEmail(dto.email())
                .filter(User::isActive)
                .ifPresent(user -> {
                    VerificationToken token = verificationTokenService.createPasswordResetToken(user);
                    emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), token.getToken());
                });
    }

    // ==========================================
    // RECUPERAÇÃO DE SENHA - REDEFINIÇÃO
    // ==========================================
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO dto) {
        VerificationToken verificationToken = verificationTokenService.validateTokenWithEmail(
                dto.email(),
                dto.code(),
                TokenType.PASSWORD_RESET
        );
        User user = verificationToken.getUser();
        user.updatePassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }
}

