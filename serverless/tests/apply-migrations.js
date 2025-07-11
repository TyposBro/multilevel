// serverless/tests/apply-migrations.js
import { env } from "cloudflare:test";
import { readFileSync } from "fs";
import path from "path";

// This code runs directly when the setup file is executed.
// It runs once per worker before any tests in that worker begin.
const schema = readFileSync(path.resolve(__dirname, "../src/db/schema.sql"), "utf-8");

// The `await` here works at the top level because setup files are treated as modules.
await env.DB.exec(schema);

console.log("✅ D1 schema applied successfully within the isolated test environment.");
