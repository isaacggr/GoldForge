package com.isaacggr.goldforge.config;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Adaptador de criptografia de senhas padrão OWASP com suporte a Lazy Rehashing.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li><b>Argon2id como padrão primário:</b> Novas senhas geradas (cadastro, alteração e redefinição)
 *       são hasheadas com Argon2id calibrado (16MB de RAM, 2 iterações, 1 thread).</li>
 *   <li><b>Compatibilidade legada com BCrypt:</b> Usuários cadastrados previamente com BCrypt ($2a$, $2b$, $2y$)
 *       continuam conseguindo se autenticar sem qualquer atrito ou interrupção.</li>
 *   <li><b>Lazy Rehashing:</b> {@link #upgradeEncoding(String)} sinaliza se o hash salvo no banco precisa ser
 *       promovido de BCrypt para Argon2id no momento do login bem-sucedido.</li>
 * </ul>
 */
public class Argon2WithBcryptFallbackPasswordEncoder implements PasswordEncoder {

    private final Argon2PasswordEncoder argon2PasswordEncoder;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public Argon2WithBcryptFallbackPasswordEncoder() {
        // OWASP recommended: salt 16 bytes, hash 32 bytes, parallelism 1, memory 16384 KB (16MB), iterations 2
        this.argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.bCryptPasswordEncoder = new BCryptPasswordEncoder();
    }

    public Argon2WithBcryptFallbackPasswordEncoder(
            Argon2PasswordEncoder argon2PasswordEncoder,
            BCryptPasswordEncoder bCryptPasswordEncoder
    ) {
        this.argon2PasswordEncoder = argon2PasswordEncoder;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return argon2PasswordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }

        if (isArgon2(encodedPassword)) {
            return argon2PasswordEncoder.matches(rawPassword, encodedPassword);
        }

        if (isBcrypt(encodedPassword)) {
            return bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
        }

        return argon2PasswordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }

        // Se a senha estiver salva em BCrypt, requer upgrade imediato para Argon2id
        if (isBcrypt(encodedPassword)) {
            return true;
        }

        // Se for Argon2, valida se os parâmetros (iterações/memória) atendem à política atual
        if (isArgon2(encodedPassword)) {
            return argon2PasswordEncoder.upgradeEncoding(encodedPassword);
        }

        return true;
    }

    private boolean isArgon2(String encodedPassword) {
        return encodedPassword.startsWith("$argon2id$")
                || encodedPassword.startsWith("$argon2i$")
                || encodedPassword.startsWith("$argon2d$");
    }

    private boolean isBcrypt(String encodedPassword) {
        return encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$");
    }
}
