-- Migration number: 0004 	 2025-08-26T08:50:34.419Z
-- Migration to add a short, user-facing ID for Click.uz
ALTER TABLE payment_transactions ADD COLUMN shortId TEXT;
CREATE INDEX IF NOT EXISTS idx_transactions_shortId ON payment_transactions(shortId);