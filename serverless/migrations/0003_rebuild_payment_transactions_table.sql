-- Migration number: 0003 	 2025-08-06T16:03:03.608Z
-- Step 1: Safely rename the existing table to preserve its data.
ALTER TABLE payment_transactions RENAME TO payment_transactions_old;

-- Step 2: Re-create the table with the correct, final schema and a clean foreign key reference.
CREATE TABLE payment_transactions (
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

-- Step 3: Copy all the data from the old table into the new, corrected table.
INSERT INTO payment_transactions SELECT * FROM payment_transactions_old;

-- Step 4: Drop the old temporary table now that the data is safe.
DROP TABLE payment_transactions_old;