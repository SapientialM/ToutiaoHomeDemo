import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { handleScreenshot } from "../tools/screenshot.js";
import { handleTap, handleSwipe, handleInputText, handlePressKey, handleBuild } from "../tools/interaction.js";
import { handleVerifyUI } from "../tools/verify.js";
import { handleGetLogs } from "../tools/logs.js";
import { handleAnalyzeScreenshot } from "../tools/analyze.js";
import { handleCompareScreenshots } from "../tools/compare.js";
import { checkDevice, resetDeviceCheck } from "../utils/adb.js";
import { execAsyncWithTimeout, fileExists } from "../utils/exec.js";
import { log } from "../utils/logger.js";
import fs from "node:fs";
import path from "node:path";

// Test configuration
const TEST_TIMEOUT = 120000; // 2 minutes for vision tests
const UNIT_TIMEOUT = 10000;  // 10 seconds for unit tests

// Use existing screenshots from .opencode-lark/attachments
const TEST_SCREENSHOTS_DIR = path.resolve(process.cwd(), "..", ".opencode-lark", "attachments");
const TEST_SCREENSHOT = path.join(TEST_SCREENSHOTS_DIR, "screenshot_home.png");
const TEST_SCREENSHOT_2 = path.join(TEST_SCREENSHOTS_DIR, "screenshot_current.png");

/**
 * Helper to find an available test screenshot
 */
async function findTestScreenshot(): Promise<string | null> {
  const candidates = [
    TEST_SCREENSHOT,
    TEST_SCREENSHOT_2,
    path.join(TEST_SCREENSHOTS_DIR, "screenshot_latest.png"),
    path.join(TEST_SCREENSHOTS_DIR, "screenshot_final.png"),
  ];

  for (const candidate of candidates) {
    if (await fileExists(candidate)) {
      return candidate;
    }
  }
  return null;
}

/**
 * Helper to check if ADB device is available
 */
async function hasDevice(): Promise<boolean> {
  const status = await checkDevice();
  return status.available;
}

describe("Android Dev Assist MCP Server", () => {
  let testImagePath: string | null = null;
  let deviceAvailable = false;

  // Kimi 模式下所有 vision 测试都跑得很慢（100-150s/调用），默认跳过
  // 改用：设置 MINIMAX_API_KEY / VISION_PROVIDER=minimax 即可全速跑
  // 或：设置 RUN_KIMI_VISION_TESTS=1 强制启用
  const isKimiMode = (process.env.VISION_PROVIDER || "").toLowerCase() === "kimi" || !!process.env.MOONSHOT_API_KEY && !process.env.MINIMAX_API_KEY;
  const runKimiVision = process.env.RUN_KIMI_VISION_TESTS === "1";
  const skipKimiVision = isKimiMode && !runKimiVision;

  beforeAll(async () => {
    // Find test image
    testImagePath = await findTestScreenshot();
    if (testImagePath) {
      log(`Using test screenshot: ${testImagePath}`);
    } else {
      log("No test screenshot found, some tests will be skipped");
    }

    // Check device
    deviceAvailable = await hasDevice();
    if (deviceAvailable) {
      log("Android device is available");
    } else {
      log("No Android device available, device-dependent tests will be skipped");
    }
  });

  afterAll(() => {
    resetDeviceCheck();
  });

  // ═══════════════════════════════════════════════════════════
  // Group 1: Utility Functions
  // ═══════════════════════════════════════════════════════════
  describe("Utility Functions", () => {
    it("fileExists should return true for existing files", async () => {
      const result = await fileExists(__filename);
      expect(result).toBe(true);
    }, UNIT_TIMEOUT);

    it("fileExists should return false for non-existing files", async () => {
      const result = await fileExists("/nonexistent/file/path.txt");
      expect(result).toBe(false);
    }, UNIT_TIMEOUT);

    it("execAsyncWithTimeout should execute commands", async () => {
      const { stdout } = await execAsyncWithTimeout("echo hello", { timeout: 5000 });
      expect(stdout.trim()).toBe("hello");
    }, UNIT_TIMEOUT);

    it("execAsyncWithTimeout should timeout on slow commands", async () => {
      await expect(
        execAsyncWithTimeout("sleep 5", { timeout: 100 })
      ).rejects.toThrow();
    }, UNIT_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 2: ADB Device Tests
  // ═══════════════════════════════════════════════════════════
  describe("ADB Device", () => {
    it("checkDevice should return a status object", async () => {
      const status = await checkDevice();
      expect(status).toHaveProperty("available");
      expect(status).toHaveProperty("message");
      expect(typeof status.available).toBe("boolean");
    }, UNIT_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 3: Screenshot Tool
  // ═══════════════════════════════════════════════════════════
  describe("screenshot tool", () => {
    it("should return error when no device is available", async () => {
      if (deviceAvailable) {
        // Skip if device is available - we'll test success case separately
        return;
      }

      const result = await handleScreenshot({});
      expect(result.isError).toBe(true);
      const parsed = JSON.parse(result.content[0].text);
      expect(parsed.success).toBe(false);
      expect(parsed.error).toBeTruthy();
    }, UNIT_TIMEOUT);

    it("should take a screenshot when device is available", async () => {
      if (!deviceAvailable) {
        console.log("⏭️ Skipping: No device available");
        return;
      }

      const result = await handleScreenshot({});
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("success");

      // Extract path and verify file exists
      const match = result.content[0].text.match(/"path":"(.+?)"/);
      if (match) {
        const screenshotPath = match[1];
        const exists = await fileExists(screenshotPath);
        expect(exists).toBe(true);

        // Cleanup
        try {
          fs.unlinkSync(screenshotPath);
        } catch {
          // Ignore cleanup errors
        }
      }
    }, TEST_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 4: Interaction Tools (Device-dependent)
  // ═══════════════════════════════════════════════════════════
  describe("interaction tools", () => {
    it("tap should work with device", async () => {
      if (!deviceAvailable) {
        console.log("⏭️ Skipping: No device available");
        return;
      }

      const result = await handleTap({ x: 100, y: 200 });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("tap");
    }, UNIT_TIMEOUT);

    it("swipe should work with device", async () => {
      if (!deviceAvailable) {
        console.log("⏭️ Skipping: No device available");
        return;
      }

      const result = await handleSwipe({ x1: 100, y1: 500, x2: 100, y2: 200, duration: 300 });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("swipe");
    }, UNIT_TIMEOUT);

    it("input_text should work with device", async () => {
      if (!deviceAvailable) {
        console.log("⏭️ Skipping: No device available");
        return;
      }

      const result = await handleInputText({ text: "hello" });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("input_text");
    }, UNIT_TIMEOUT);

    it("press_key should work with device", async () => {
      if (!deviceAvailable) {
        console.log("⏭️ Skipping: No device available");
        return;
      }

      const result = await handlePressKey({ key: "HOME" });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("press_key");
    }, UNIT_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 5: Build Tool (Project-dependent)
  // ═══════════════════════════════════════════════════════════
  describe("build tool", () => {
    it("should handle invalid project path", async () => {
      const result = await handleBuild({ projectPath: "/nonexistent/path", variant: "debug" });
      expect(result.isError).toBe(true);
    }, 30000);

    it("should work with valid project (if available)", async () => {
      // Check if we're in the Android project
      const projectRoot = path.resolve(process.cwd(), "..");
      const hasBuildGradle = await fileExists(path.join(projectRoot, "build.gradle.kts"));

      if (!hasBuildGradle) {
        console.log("⏭️ Skipping: No Android project found");
        return;
      }

      const result = await handleBuild({ projectPath: projectRoot, variant: "debug" });
      // Build may succeed or fail depending on environment, but should not hang
      expect(result).toHaveProperty("content");
      expect(result.content[0]).toHaveProperty("text");
    }, 300000); // 5 minutes for actual build
  });

  // ═══════════════════════════════════════════════════════════
  // Group 6: Logs Tool (Device-dependent)
  // ═══════════════════════════════════════════════════════════
  describe("logs tool", () => {
    it("should get logs with crash filter", async () => {
      const result = await handleGetLogs({ filter: "crash", lines: 10 });
      expect(result).toHaveProperty("content");
      expect(result.content[0]).toHaveProperty("text");
      // Should either return logs or "No matching logs found"
      expect(result.content[0].text.length).toBeGreaterThan(0);
    }, UNIT_TIMEOUT);

    it("should get logs for specific package", async () => {
      const result = await handleGetLogs({
        packageName: "com.example.toutiao",
        filter: "all",
        lines: 5,
      });
      expect(result).toHaveProperty("content");
      expect(result.content[0]).toHaveProperty("text");
    }, UNIT_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 7: Analyze Screenshot (Vision API - uses test images)
  // ═══════════════════════════════════════════════════════════
  describe("analyze_screenshot tool", () => {
    it("should return error for missing file", async () => {
      const result = await handleAnalyzeScreenshot({ filePath: "/nonexistent.png" });
      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("not found");
    }, UNIT_TIMEOUT);

    it("should analyze test screenshot with PIL (Stage 1)", async () => {
      if (skipKimiVision) {
        console.log("⏭️ Skipping: Kimi vision mode disabled (set RUN_KIMI_VISION_TESTS=1 to enable)");
        return;
      }
      if (!testImagePath) {
        console.log("⏭️ Skipping: No test image available");
        return;
      }

      const result = await handleAnalyzeScreenshot({ filePath: testImagePath });
      expect(result.isError).toBeFalsy();
      const parsed = JSON.parse(result.content[0].text);
      expect(parsed.file).toBeTruthy();
      expect(parsed.pil).toBeTruthy();
      expect(parsed.cards).toBeTruthy();
      expect(parsed.checklist).toBeTruthy();

      if (parsed.pil.error) {
        console.log("⚠️ PIL analysis failed:", parsed.pil.error);
      } else {
        expect(parsed.pil.dimensions).toBeTruthy();
      }
    }, TEST_TIMEOUT);

    it("should analyze test screenshot with custom prompt", async () => {
      if (skipKimiVision) {
        console.log("⏭️ Skipping: Kimi vision mode disabled (set RUN_KIMI_VISION_TESTS=1 to enable)");
        return;
      }
      if (!testImagePath) {
        console.log("⏭️ Skipping: No test image available");
        return;
      }

      const result = await handleAnalyzeScreenshot({
        filePath: testImagePath,
        prompt: "Check if the header is red and the bottom nav is white",
      });
      expect(result.isError).toBeFalsy();
      const parsed = JSON.parse(result.content[0].text);
      expect(parsed.file).toBeTruthy();
      expect(parsed.pil).toBeTruthy();
    }, TEST_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 8: Compare Screenshots (Vision API - uses test images)
  // ═══════════════════════════════════════════════════════════
  describe("compare_screenshots tool", () => {
    it("should return error for missing files", async () => {
      const result = await handleCompareScreenshots({
        baselinePath: "/nonexistent1.png",
        currentPath: "/nonexistent2.png",
      });
      expect(result.isError).toBe(true);
    }, UNIT_TIMEOUT);

    it("should compare two test screenshots", async () => {
      if (skipKimiVision) {
        console.log("⏭️ Skipping: Kimi vision mode disabled (set RUN_KIMI_VISION_TESTS=1 to enable)");
        return;
      }
      if (!testImagePath || !TEST_SCREENSHOT_2) {
        console.log("⏭️ Skipping: Not enough test images");
        return;
      }

      const secondImageExists = await fileExists(TEST_SCREENSHOT_2);
      if (!secondImageExists) {
        console.log("⏭️ Skipping: Second test image not found");
        return;
      }

      // Skip if no API key at all (Kimi or Minimax)
      if (!process.env.MOONSHOT_API_KEY && !process.env.MINIMAX_API_KEY) {
        console.log("⏭️ Skipping: no vision API key (MOONSHOT_API_KEY / MINIMAX_API_KEY) set");
        return;
      }

      const result = await handleCompareScreenshots({
        baselinePath: testImagePath,
        currentPath: TEST_SCREENSHOT_2,
      });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("SCREENSHOT COMPARISON REPORT");
    }, TEST_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 9: Verify UI Tool
  // ═══════════════════════════════════════════════════════════
  describe("verify_ui tool", () => {
    it("should return error for invalid type", async () => {
      const result = await handleVerifyUI({ type: "invalid" });
      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("Unknown verify type");
    }, UNIT_TIMEOUT);

    it("should return error for missing compare params", async () => {
      const result = await handleVerifyUI({ type: "compare" });
      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("requires");
    }, UNIT_TIMEOUT);

    it("should return error for missing color params", async () => {
      const result = await handleVerifyUI({ type: "color" });
      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("requires");
    }, UNIT_TIMEOUT);

    it("should perform color check on test image", async () => {
      if (!testImagePath) {
        console.log("⏭️ Skipping: No test image available");
        return;
      }

      const result = await handleVerifyUI({
        type: "color",
        currentPath: testImagePath,
        x: 100,
        y: 100,
        checkColor: "#FF0000",
      });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("match");
    }, UNIT_TIMEOUT);

    it("should handle OCR type gracefully", async () => {
      const result = await handleVerifyUI({
        type: "ocr",
        checkText: "首页",
      });
      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("tesseract");
    }, UNIT_TIMEOUT);
  });

  // ═══════════════════════════════════════════════════════════
  // Group 10: Integration Test - Full Workflow
  // ═══════════════════════════════════════════════════════════
  describe("Integration: Full workflow", () => {
    it("should handle complete screenshot → analyze pipeline", async () => {
      if (skipKimiVision) {
        console.log("⏭️ Skipping: Kimi vision mode disabled (set RUN_KIMI_VISION_TESTS=1 to enable)");
        return;
      }
      if (!testImagePath) {
        console.log("⏭️ Skipping: No test image available");
        return;
      }

      // Step 1: Analyze screenshot
      const analyzeResult = await handleAnalyzeScreenshot({ filePath: testImagePath });
      expect(analyzeResult.isError).toBeFalsy();

      // Step 2: Verify color at a point
      const verifyResult = await handleVerifyUI({
        type: "color",
        currentPath: testImagePath,
        x: 50,
        y: 50,
        checkColor: "#FF5757",
      });
      expect(verifyResult.isError).toBeFalsy();

      // Step 3: Get logs (should work regardless of device)
      const logsResult = await handleGetLogs({ filter: "crash", lines: 5 });
      expect(logsResult).toHaveProperty("content");

      console.log("✅ Full workflow test passed");
    }, TEST_TIMEOUT);
  });
});
