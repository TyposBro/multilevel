// serverless/vitest.integration.config.ts
import { defineWorkersProject } from "@cloudflare/vitest-pool-workers/config";
import { readFileSync } from "fs";
import path from "path";

// Read the schema file in Node.js, where we have file system access.
const schema = readFileSync(path.resolve(__dirname, "src/db/schema.sql"), "utf-8");

export default defineWorkersProject({
  test: {
    name: "integration",
    include: ["src/**/*.integration.test.js"],
    tsconfig: "tests/tsconfig.json",
    // We no longer need setupFiles for this

    poolOptions: {
      workers: {
        singleWorker: true,
        wrangler: { configPath: "./wrangler.toml" },
        // --- THIS IS THE FIX ---
        // We add a `miniflare` block to inject an extra binding.
        miniflare: {
          // Bind the content of our schema file to a variable named `SCHEMA`.
          // This variable will be available on `env` inside the test worker.
          bindings: {
            SCHEMA: schema,
          },
        },
        // --- END OF FIX ---
      },
    },
  },
});
