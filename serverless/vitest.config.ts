// serverless/vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Tell Vitest to look for project-specific config files
    projects: ["vitest.unit.config.ts", "vitest.integration.config.ts"],
  },
});
