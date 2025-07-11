// serverless/vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    projects: ["vitest.unit.config.ts", "vitest.integration.config.ts"],
    // --- REMOVE THIS LINE ---
    // globalSetup: ["tests/setup.js"],
  },
});
