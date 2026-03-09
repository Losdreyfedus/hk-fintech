CREATE TABLE wallets (
    id VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(255)
);

CREATE TABLE wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    type VARCHAR(255) NOT NULL,
    reference_id VARCHAR(255),
    description VARCHAR(255),
    created_date TIMESTAMP
);

CREATE TABLE inbox_messages (
    message_id VARCHAR(255) PRIMARY KEY,
    reason VARCHAR(255),
    processed_at TIMESTAMP
);

CREATE TABLE outbox_messages (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255),
    payload TEXT,
    status VARCHAR(255),
    created_at TIMESTAMP
);
