CREATE TABLE users (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    email       VARCHAR(254)  NOT NULL,
    document    VARCHAR(14)   NOT NULL,
    balance_brl NUMERIC(19,2) NOT NULL DEFAULT 0,
    balance_usd NUMERIC(19,2) NOT NULL DEFAULT 0,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_document UNIQUE (document),
    CONSTRAINT ck_users_balance_brl CHECK (balance_brl >= 0),
    CONSTRAINT ck_users_balance_usd CHECK (balance_usd >= 0)
);
