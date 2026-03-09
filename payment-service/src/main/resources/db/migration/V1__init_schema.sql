CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT,
    user_id BIGINT NOT NULL,
    card_id BIGINT,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_date TIMESTAMP
);
