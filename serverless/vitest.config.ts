// serverless/vitest.config.ts
import { defineConfig, defineProject } from "vitest/config";

// Define the "unit" test project
const unitTests = defineProject({
  test: {
    name: "unit",
    include: ["src/**/*.test.js"],
    exclude: ["src/**/*.integration.test.js"],
    poolOptions: {
      workers: {
        singleWorker: true,
        wrangler: { configPath: "./wrangler.toml" },
      },
    },
  },
});

// Define the "integration" test project
const integrationTests = defineProject({
  test: {
    name: "integration",
    include: ["src/**/*.integration.test.js"],
    poolOptions: {
      workers: {
        singleWorker: true,
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          compatibilityFlags: ["nodejs_compat"],
        },
      },
    },
  },
});

// The main configuration now points to the projects
export default defineConfig({
  test: {
    // Vitest will run these projects
    projects: [unitTests, integrationTests],
  },
});
