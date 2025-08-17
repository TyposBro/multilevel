-- Test data for local development

-- Insert a test user
INSERT INTO users (
  id, 
  email, 
  authProvider, 
  googleId, 
  firstName, 
  username,
  subscription_tier,
  subscription_expiresAt,
  createdAt, 
  updatedAt
) VALUES (
  'test-user-123',
  'testuser@example.com',
  'google',
  'google-test-123',
  'Test',
  'testuser',
  'free',
  NULL,
  '2024-01-01T00:00:00.000Z',
  '2024-01-01T00:00:00.000Z'
);

-- Insert another test user with active subscription
INSERT INTO users (
  id, 
  email, 
  authProvider, 
  googleId, 
  firstName, 
  username,
  subscription_tier,
  subscription_expiresAt,
  createdAt, 
  updatedAt
) VALUES (
  'test-user-456',
  'premiumuser@example.com',
  'google',
  'google-test-456',
  'Premium',
  'premiumuser',
  'silver',
  '2025-12-31T23:59:59.000Z',
  '2024-01-01T00:00:00.000Z',
  '2024-01-01T00:00:00.000Z'
);

-- Insert a test admin
INSERT INTO admins (
  id,
  email,
  password,
  role,
  createdAt
) VALUES (
  'admin-test-123',
  'admin@example.com',
  '$2a$10$example.hash.here.for.testing',
  'admin',
  '2024-01-01T00:00:00.000Z'
);

-- Insert some test payment transactions
INSERT INTO payment_transactions (
  id,
  userId,
  planId,
  provider,
  amount,
  status,
  providerTransactionId,
  createdAt,
  updatedAt
) VALUES (
  'txn-test-123',
  'test-user-123',
  'silver_monthly',
  'click',
  100000,
  'PENDING',
  NULL,
  '2024-01-01T00:00:00.000Z',
  '2024-01-01T00:00:00.000Z'
);

INSERT INTO payment_transactions (
  id,
  userId,
  planId,
  provider,
  amount,
  status,
  providerTransactionId,
  createdAt,
  updatedAt
) VALUES (
  'txn-test-456',
  'test-user-456',
  'silver_monthly',
  'click',
  100000,
  'COMPLETED',
  'click-12345',
  '2024-01-01T00:00:00.000Z',
  '2024-01-01T00:00:00.000Z'
);
