CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    bill_type VARCHAR(255) NOT NULL,
    institution_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
