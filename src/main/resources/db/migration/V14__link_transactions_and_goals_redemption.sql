-- ========================================================
-- V14: Integração de Metas com Fluxo de Caixa e Resgate
-- 1. Persistência de status em goals (IN_PROGRESS, COMPLETED, REALIZED)
-- 2. Suporte a aportes e saques em goal_contributions (CONTRIBUTION, WITHDRAWAL)
-- 3. Vínculo de movimentações financeiras em transactions (goal_id, goal_contribution_id)
-- ========================================================

-- 1. Persistir o status em goals e inicializar conforme o progresso atual
ALTER TABLE goals
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS';

UPDATE goals
SET status = CASE
    WHEN current_amount >= target_amount THEN 'COMPLETED'
    ELSE 'IN_PROGRESS'
END;

-- 2. Adicionar tipo de movimentação (CONTRIBUTION / WITHDRAWAL) e vínculo com transactions
ALTER TABLE goal_contributions
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'CONTRIBUTION',
    ADD COLUMN transaction_id BINARY(16) DEFAULT NULL;

ALTER TABLE goal_contributions
    ADD CONSTRAINT fk_goal_contributions_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL;

-- 3. Adicionar referência de meta na tabela transactions
ALTER TABLE transactions
    ADD COLUMN goal_id BINARY(16) DEFAULT NULL,
    ADD COLUMN goal_contribution_id BINARY(16) DEFAULT NULL;

ALTER TABLE transactions
    ADD KEY idx_transactions_goal_id (goal_id),
    ADD KEY idx_transactions_goal_contribution_id (goal_contribution_id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_goal
    FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE SET NULL;
