// serverless/tests/setup.js
import { execa } from "execa"; // A better tool for running shell commands

// This will run before Vitest starts executing tests
export async function setup() {
  console.log("Setting up D1 database schema for tests...");
  try {
    // This command tells wrangler to apply your schema to the local in-memory DB
    await execa("npx", [
      "wrangler",
      "d1",
      "execute",
      "multilevel-db",
      "--local",
      "--file=./src/db/schema.sql",
    ]);
    console.log("Database schema applied successfully.");
  } catch (e) {
    console.error("Failed to apply database schema:", e.message);
    throw e; // Fail fast if schema can't be applied
  }
}
