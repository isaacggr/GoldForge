-- ========================================================
-- V15: Persistência do Avatar RPG do Usuário
-- Armazena o identificador do avatar pixel art no cadastro do usuário,
-- garantindo sincronização permanente entre dispositivos e na nuvem.
-- ========================================================

ALTER TABLE users
    ADD COLUMN avatar_id VARCHAR(50) NOT NULL DEFAULT 'avatar-01';
