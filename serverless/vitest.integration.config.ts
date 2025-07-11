// serverless/vitest.integration.config.ts
import { defineWorkersProject } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersProject({
  test: {
    name: "integration",
    include: ["src/**/*.integration.test.js"],
    // This tells the integration test runner to use the special types
    // which enables the `cloudflare:test` module.
    tsconfig: "tests/tsconfig.json",
    poolOptions: {
      workers: {
        singleWorker: true,
        wrangler: { configPath: "./wrangler.toml" },
      },
    },
    // The global setup is only needed for integration tests
    globalSetup: ["tests/setup.js"],
  },
});
