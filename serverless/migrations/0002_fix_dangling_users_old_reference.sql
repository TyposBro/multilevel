-- Migration number: 0002 	 2025-08-06T15:58:19.319Z
-- Migration to safely remove any lingering reference to the old users table.
DROP TABLE IF EXISTS users_old;