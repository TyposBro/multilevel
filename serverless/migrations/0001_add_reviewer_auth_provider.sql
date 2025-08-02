-- Migration number: 0001 	 2025-08-02T15:11:18.276Z
ALTER TABLE users RENAME TO users_old;

CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT,
  authProvider TEXT NOT NULL CHECK(authProvider IN ('google', 'telegram', 'apple', 'reviewer')),
  googleId TEXT UNIQUE,
  telegramId INTEGER UNIQUE,
  appleId TEXT UNIQUE,
  firstName TEXT,
  username TEXT,
  subscription_tier TEXT DEFAULT 'free' NOT NULL,
  subscription_expiresAt TEXT,
  subscription_providerId TEXT,
  subscription_hasUsedGoldTrial INTEGER DEFAULT 0 NOT NULL,
  dailyUsage_fullExams_count INTEGER DEFAULT 0,
  dailyUsage_fullExams_lastReset TEXT,
  dailyUsage_partPractices_count INTEGER DEFAULT 0,
  dailyUsage_partPractices_lastReset TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO users SELECT * FROM users_old;

DROP TABLE users_old;