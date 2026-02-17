ALTER TABLE otp_verifications
ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'REGISTRATION';

CREATE INDEX idx_otp_purpose ON otp_verifications(purpose);
CREATE INDEX idx_otp_destination_channel_purpose ON otp_verifications(destination, channel, purpose);
