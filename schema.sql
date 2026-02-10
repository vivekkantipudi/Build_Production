CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    webhook_secret VARCHAR(64),
    webhook_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(64) PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    order_id VARCHAR(64) NOT NULL,
    amount INTEGER NOT NULL,
    currency VARCHAR(3) DEFAULT 'INR',
    method VARCHAR(20),
    status VARCHAR(20) DEFAULT 'pending',
    vpa VARCHAR(255),
    error_description TEXT,
    captured BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refunds (
    id VARCHAR(64) PRIMARY KEY,
    payment_id VARCHAR(64) NOT NULL REFERENCES payments(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    amount INTEGER NOT NULL,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    event VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    attempts INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    response_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key VARCHAR(255),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    response JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (key, merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX IF NOT EXISTS idx_webhook_logs_merchant_id ON webhook_logs(merchant_id);
CREATE INDEX IF NOT EXISTS idx_webhook_logs_status ON webhook_logs(status);
CREATE INDEX IF NOT EXISTS idx_webhook_logs_retry ON webhook_logs(next_retry_at) WHERE status = 'pending';

-- Seed Data (Email only, let DB generate the ID)
INSERT INTO merchants (email, password_hash, name, webhook_secret, webhook_url)
VALUES (
    'test@example.com', 
    'dummy_hash_123', 
    'Test Merchant',
    'whsec_test_abc123',
    'http://host.docker.internal:4000/webhook'
)
ON CONFLICT (email) 
DO UPDATE SET webhook_secret = 'whsec_test_abc123';