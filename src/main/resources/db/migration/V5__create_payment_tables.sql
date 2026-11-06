
-- =========================================
-- V5: Create Payment Domain Tables
-- =========================================

-- =========================================
-- 1. Payments
-- =========================================

CREATE TABLE payments (
    id UUID PRIMARY KEY,

    payment_reference VARCHAR(64) NOT NULL,

    customer_id UUID NOT NULL,
    merchant_id UUID NOT NULL,

    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,

    description VARCHAR(500),

    expires_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_payment_reference
        UNIQUE (payment_reference),

    CONSTRAINT fk_payment_customer
        FOREIGN KEY (customer_id)
        REFERENCES users(id),

    CONSTRAINT fk_payment_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES users(id),

    CONSTRAINT chk_payment_amount
        CHECK (amount > 0),

    CONSTRAINT chk_payment_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_payment_status
        CHECK (
            status IN (
                'CREATED',
                'PENDING',
                'PROCESSING',
                'SUCCEEDED',
                'FAILED',
                'CANCELLED',
                'EXPIRED'
            )
        ),

    CONSTRAINT chk_payment_method
        CHECK (
            payment_method IN (
                'CARD',
                'UPI',
                'NET_BANKING',
                'WALLET'
            )
        )
);

-- Payment indexes

CREATE INDEX idx_payment_customer
    ON payments(customer_id);

CREATE INDEX idx_payment_merchant
    ON payments(merchant_id);

CREATE INDEX idx_payment_status
    ON payments(status);

CREATE INDEX idx_payment_created_at
    ON payments(created_at);


-- =========================================
-- 2. Payment Attempts
-- =========================================

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,

    payment_id UUID NOT NULL,

    attempt_number INTEGER NOT NULL,

    gateway_name VARCHAR(50) NOT NULL,
    gateway_reference VARCHAR(100),

    status VARCHAR(20) NOT NULL,

    error_code VARCHAR(100),
    error_message VARCHAR(1000),

    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_payment_attempt_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id),

    CONSTRAINT uk_payment_attempt_number
        UNIQUE (payment_id, attempt_number),

    CONSTRAINT uk_gateway_reference
        UNIQUE (gateway_name, gateway_reference),

    CONSTRAINT chk_payment_attempt_number
        CHECK (attempt_number > 0),

    CONSTRAINT chk_payment_attempt_status
        CHECK (
            status IN (
                'INITIATED',
                'PROCESSING',
                'SUCCEEDED',
                'FAILED',
                'TIMEOUT'
            )
        ),

    CONSTRAINT chk_payment_attempt_timestamps
        CHECK (
            completed_at IS NULL
            OR completed_at >= started_at
        )
);

-- Payment attempt indexes

CREATE INDEX idx_payment_attempt_payment
    ON payment_attempts(payment_id);

CREATE INDEX idx_payment_attempt_status
    ON payment_attempts(status);
