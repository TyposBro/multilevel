// serverless/vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    projects: ["vitest.unit.config.ts", "vitest.integration.config.ts"],
    coverage: {
      // --- THIS IS THE FIX ---
      provider: "istanbul", // Use the compatible provider
      // --- END OF FIX ---
      reporter: ["text", "json", "html"],
      all: true,
      include: ["src/**"],
      exclude: ["src/index.js", "src/db/d1-client.integration.test.js", "src/routes/**/*.test.js"],
    },
  },
});
