CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,

    operation VARCHAR(20) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    response_status INTEGER,
    response_body TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_idempotency_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT uk_idempotency_user_key
        UNIQUE (user_id, idempotency_key),

    CONSTRAINT chk_idempotency_operation
        CHECK (operation IN ('CREDIT', 'DEBIT'))
);
