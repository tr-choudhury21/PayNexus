-- ============================================================
-- Wallets
-- ============================================================

CREATE TABLE wallets (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    balance NUMERIC(19, 4) NOT NULL DEFAULT 0,

    currency VARCHAR(3) NOT NULL DEFAULT 'INR',

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_wallet_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT uk_wallet_user_currency
        UNIQUE (user_id, currency)
);


-- ============================================================
-- Ledger Entries
-- ============================================================

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,

    wallet_id UUID NOT NULL,

    amount NUMERIC(19, 4) NOT NULL,

    entry_type VARCHAR(10) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    description VARCHAR(500),

    reference_id VARCHAR(100),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ledger_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets(id),

    CONSTRAINT chk_ledger_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_ledger_entry_type
        CHECK (entry_type IN ('CREDIT', 'DEBIT'))
);


-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX idx_ledger_wallet_created
    ON ledger_entries(wallet_id, created_at);