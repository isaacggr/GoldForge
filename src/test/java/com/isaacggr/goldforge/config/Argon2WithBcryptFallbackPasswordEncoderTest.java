package com.isaacggr.goldforge.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes Unitários - Argon2WithBcryptFallbackPasswordEncoder")
class Argon2WithBcryptFallbackPasswordEncoderTest {

    private Argon2WithBcryptFallbackPasswordEncoder passwordEncoder;
    private BCryptPasswordEncoder legacyBcryptEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new Argon2WithBcryptFallbackPasswordEncoder();
        legacyBcryptEncoder = new BCryptPasswordEncoder();
    }

    @Test
    @DisplayName("Deve gerar hash no formato Argon2id para novas senhas")
    void encode_ShouldGenerateArgon2idHash() {
        String rawPassword = "SenhaSegura123!";
        String encoded = passwordEncoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$argon2id$"), "O hash gerado deve iniciar com $argon2id$");
        assertNotEquals(rawPassword, encoded);
    }

    @Test
    @DisplayName("Deve validar com sucesso senha codificada com Argon2id")
    void matches_ShouldValidateArgon2idHash() {
        String rawPassword = "SenhaValida123";
        String encoded = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encoded));
        assertFalse(passwordEncoder.matches("SenhaIncorreta", encoded));
    }

    @Test
    @DisplayName("Deve validar com sucesso senha legada codificada com BCrypt ($2a$)")
    void matches_ShouldValidateLegacyBcryptHash() {
        String rawPassword = "SenhaLegadaBCrypt";
        String legacyHash = legacyBcryptEncoder.encode(rawPassword);

        assertTrue(legacyHash.startsWith("$2a$"));
        assertTrue(passwordEncoder.matches(rawPassword, legacyHash), "Deve autenticar com sucesso senhas criadas no BCrypt");
        assertFalse(passwordEncoder.matches("SenhaIncorreta", legacyHash));
    }

    @Test
    @DisplayName("Deve sinalizar upgradeEncoding = true para hashes legados em BCrypt")
    void upgradeEncoding_ShouldReturnTrueForBcryptHash() {
        String legacyHash = legacyBcryptEncoder.encode("SenhaAntiga");

        assertTrue(passwordEncoder.upgradeEncoding(legacyHash),
                "upgradeEncoding deve retornar true para senhas salvas em BCrypt, disparando o Lazy Rehashing");
    }

    @Test
    @DisplayName("Deve retornar upgradeEncoding = false para senhas já migradas para Argon2id")
    void upgradeEncoding_ShouldReturnFalseForArgon2idHash() {
        String argon2idHash = passwordEncoder.encode("SenhaNovaArgon2");

        assertFalse(passwordEncoder.upgradeEncoding(argon2idHash),
                "upgradeEncoding deve retornar false para hashes Argon2id modernos, evitando updates desnecessários no banco");
    }

    @Test
    @DisplayName("Deve tratar entradas nulas ou vazias com segurança")
    void shouldHandleNullOrEmptySafely() {
        assertFalse(passwordEncoder.matches("senha", null));
        assertFalse(passwordEncoder.upgradeEncoding(null));
        assertFalse(passwordEncoder.upgradeEncoding(""));
        assertFalse(passwordEncoder.upgradeEncoding("   "));
    }
}
