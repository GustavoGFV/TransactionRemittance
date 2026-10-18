CREATE TABLE daily_limit_rules (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_type VARCHAR(4)    NOT NULL,
    max_amount    NUMERIC(19,2) NOT NULL,
    CONSTRAINT uk_daily_limit_rules_document_type UNIQUE (document_type),
    CONSTRAINT ck_daily_limit_rules_document_type CHECK (document_type IN ('CPF', 'CNPJ')),
    CONSTRAINT ck_daily_limit_rules_max_amount CHECK (max_amount > 0)
);

-- CPF = individual (PF), CNPJ = company (PJ)
INSERT INTO daily_limit_rules (document_type, max_amount) VALUES
    ('CPF', 10000.00),
    ('CNPJ', 50000.00);

CREATE TABLE user_daily_transactions (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          UUID          NOT NULL,
    daily_limit_rule_id BIGINT     NOT NULL,
    reached_amount   NUMERIC(19,2) NOT NULL DEFAULT 0,
    transaction_date DATE          NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_daily_transactions_user FOREIGN KEY (user_id) REFERENCES users (public_id),
    CONSTRAINT fk_user_daily_transactions_rule FOREIGN KEY (daily_limit_rule_id) REFERENCES daily_limit_rules (id),
    CONSTRAINT uk_user_daily_transactions_user_date UNIQUE (user_id, transaction_date),
    CONSTRAINT ck_user_daily_transactions_amount CHECK (reached_amount >= 0)
);
