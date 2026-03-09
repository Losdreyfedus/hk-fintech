CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    role VARCHAR(255)
);

CREATE TABLE outbox_messages (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255),
    payload TEXT,
    status VARCHAR(255),
    created_at TIMESTAMP
);
