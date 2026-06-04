import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    include: ["src/**/*.test.ts"],
    testTimeout: 120000, // 2 minutes for vision tests
    hookTimeout: 30000,
    // Allow tests to run even if some fail
    bail: 0,
    // Show verbose output
    reporters: ["verbose"],
    // Handle ESM imports
    deps: {
      interopDefault: true,
    },
  },
  resolve: {
    alias: {
      "^(.+)\\.js$": "$1",
    },
  },
});
