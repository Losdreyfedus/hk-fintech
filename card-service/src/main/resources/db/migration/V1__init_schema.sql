CREATE TABLE cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    card_holder VARCHAR(255) NOT NULL,
    masked_card_number VARCHAR(255) NOT NULL,
    expire_month VARCHAR(2) NOT NULL,
    expire_year VARCHAR(4) NOT NULL,
    card_token VARCHAR(255) NOT NULL,
    card_alias VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
