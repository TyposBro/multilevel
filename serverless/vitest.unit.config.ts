// serverless/vitest.unit.config.ts
import { defineWorkersProject } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersProject({
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
