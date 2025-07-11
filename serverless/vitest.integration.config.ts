// serverless/vitest.integration.config.ts
import { defineWorkersProject } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersProject({
  test: {
    name: "integration",
    include: ["src/**/*.integration.test.js"],
    tsconfig: "tests/tsconfig.json",
    // --- THIS IS THE FIX ---
    // Use setupFiles to run schema migration inside the isolated test environment.
    setupFiles: ["./tests/apply-migrations.js"],
    // We no longer need the globalSetup for this project.
    // --- END OF FIX ---
    poolOptions: {
      workers: {
        singleWorker: true,
        wrangler: { configPath: "./wrangler.toml" },
      },
    },
  },
});
