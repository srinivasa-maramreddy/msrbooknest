ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX idx_users_phone_number ON users(phone_number);
CREATE INDEX idx_users_enabled ON users(enabled);

CREATE TABLE otp_verifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    destination VARCHAR(180) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_otp_verifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_otp_destination_channel ON otp_verifications(destination, channel);
CREATE INDEX idx_otp_user_id ON otp_verifications(user_id);
CREATE INDEX idx_otp_verified ON otp_verifications(verified);
