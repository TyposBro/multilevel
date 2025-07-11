// serverless/vitest.workspace.ts
import { defineWorkspace } from "vitest/config";

export default defineWorkspace([
  // Project for Unit/Mocked Tests
  {
    test: {
      name: "unit",
      include: ["src/**/*.test.ts"],
      exclude: ["src/**/*.integration.test.ts"], // Exclude the D1 integration test
      poolOptions: {
        workers: {
          singleWorker: true,
          wrangler: { configPath: "./wrangler.toml" },
          // No compatibility flags needed for unit tests
        },
      },
    },
  },
  // Project for D1 Integration Tests
  {
    test: {
      name: "integration",
      include: ["src/**/*.integration.test.ts"], // ONLY run the D1 integration test
      poolOptions: {
        workers: {
          singleWorker: true,
          wrangler: { configPath: "./wrangler.toml" },
          miniflare: {
            // Enable Node.js compatibility ONLY for this test suite
            compatibilityFlags: ["nodejs_compat"],
          },
        },
      },
    },
  },
]);
