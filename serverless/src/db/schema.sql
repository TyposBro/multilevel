-- {PATH_TO_PROJECT}/src/db/schema.sql

-- Users Table
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT,
  authProvider TEXT NOT NULL CHECK(authProvider IN ('google', 'telegram', 'apple')),
  googleId TEXT UNIQUE,
  telegramId INTEGER UNIQUE,
  appleId TEXT UNIQUE,
  firstName TEXT,
  username TEXT,
  subscription_tier TEXT DEFAULT 'free' NOT NULL,
  subscription_expiresAt TEXT,
  subscription_providerId TEXT,
  subscription_hasUsedGoldTrial INTEGER DEFAULT 0 NOT NULL,
  -- START OF NEW COLUMNS --
  dailyUsage_fullExams_count INTEGER DEFAULT 0,
  dailyUsage_fullExams_lastReset TEXT,
  dailyUsage_partPractices_count INTEGER DEFAULT 0,
  dailyUsage_partPractices_lastReset TEXT,
  -- END OF NEW COLUMNS --
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_googleId ON users(googleId);
CREATE INDEX IF NOT EXISTS idx_users_telegramId ON users(telegramId);

-- Admin Table
CREATE TABLE IF NOT EXISTS admins (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    role TEXT DEFAULT 'admin' NOT NULL,
    createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- One-Time Tokens for Telegram Login
CREATE TABLE IF NOT EXISTS one_time_tokens (
    token TEXT PRIMARY KEY,
    telegramId INTEGER NOT NULL,
    botMessageId INTEGER NOT NULL,
    userMessageId INTEGER NOT NULL,
    createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Word Bank Table
CREATE TABLE IF NOT EXISTS words (
    id TEXT PRIMARY KEY,
    word TEXT NOT NULL UNIQUE,
    cefrLevel TEXT NOT NULL,
    topic TEXT NOT NULL,
    translation TEXT NOT NULL,
    example1 TEXT,
    example1Translation TEXT,
    example2 TEXT,
    example2Translation TEXT,
    createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_words_level_topic ON words(cefrLevel, topic);

-- Content Tables (one for each part)
CREATE TABLE IF NOT EXISTS content_part1_1 (
  id TEXT PRIMARY KEY,
  questionText TEXT NOT NULL,
  audioUrl TEXT NOT NULL,
  tags TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS content_part1_2 (
  id TEXT PRIMARY KEY,
  image1Url TEXT NOT NULL,
  image2Url TEXT NOT NULL,
  imageDescription TEXT NOT NULL,
  questions TEXT NOT NULL,
  tags TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS content_part2 (
  id TEXT PRIMARY KEY,
  imageUrl TEXT,
  imageDescription TEXT,
  questions TEXT NOT NULL,
  tags TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS content_part3 (
  id TEXT PRIMARY KEY,
  topic TEXT NOT NULL,
  forPoints TEXT NOT NULL,
  againstPoints TEXT NOT NULL,
  imageUrl TEXT,
  tags TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL

-- Payment Transactions Table
CREATE TABLE IF NOT EXISTS payment_transactions (
  id TEXT PRIMARY KEY,
  userId TEXT NOT NULL,
  planId TEXT NOT NULL,
  provider TEXT NOT NULL,
  amount INTEGER NOT NULL,
  status TEXT DEFAULT 'PENDING' NOT NULL, -- PENDING, COMPLETED, FAILED
  providerTransactionId TEXT, -- The ID from Click/Payme
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL,
  FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
);