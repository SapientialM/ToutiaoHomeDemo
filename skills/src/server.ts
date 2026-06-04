import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { handleScreenshot } from "./tools/screenshot.js";
import { handleTap, handleSwipe, handleInputText, handlePressKey, handleBuild, handleInstallAndLaunch } from "./tools/interaction.js";
import { handleVerifyUI } from "./tools/verify.js";
import { handleGetLogs } from "./tools/logs.js";
import { handleAnalyzeScreenshot } from "./tools/analyze.js";
import { handleCompareScreenshots } from "./tools/compare.js";
import { handleDeviceManagement } from "./tools/device-management.js";
import { handleAppManagement } from "./tools/app-management.js";
import { handlePerformanceMonitor } from "./tools/performance-monitor.js";
import { handleCodeQuality } from "./tools/code-quality.js";
import { handleUITest } from "./tools/ui-test.js";
import { handleBuildDeploy } from "./tools/build-deploy.js";
import { handleProjectReport } from "./tools/project-report.js";
import { handleFileOperations } from "./tools/file-operations.js";
import { handleNetworkDebug } from "./tools/network-debug.js";
import { handleVisionAction } from "./tools/vision-action.js";
import { log, error } from "./utils/logger.js";

const server = new Server(
  {
    name: "android-dev-assist",
    version: "2.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  },
);

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      // ===== 基础交互工具 =====
      {
        name: "screenshot",
        description: "Take a screenshot of the Android emulator or device via ADB",
        inputSchema: {
          type: "object",
          properties: {
            savePath: { type: "string", description: "Optional custom save path for the screenshot" },
          },
        },
      },
      {
        name: "tap",
        description: "Tap on screen at specified coordinates",
        inputSchema: {
          type: "object",
          properties: {
            x: { type: "number", description: "X coordinate" },
            y: { type: "number", description: "Y coordinate" },
          },
          required: ["x", "y"],
        },
      },
      {
        name: "swipe",
        description: "Swipe from start to end coordinates",
        inputSchema: {
          type: "object",
          properties: {
            x1: { type: "number", description: "Start X" },
            y1: { type: "number", description: "Start Y" },
            x2: { type: "number", description: "End X" },
            y2: { type: "number", description: "End Y" },
            duration: { type: "number", description: "Swipe duration in ms", default: 300 },
          },
          required: ["x1", "y1", "x2", "y2"],
        },
      },
      {
        name: "input_text",
        description: "Input text on the Android device",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "Text to input" },
          },
          required: ["text"],
        },
      },
      {
        name: "press_key",
        description: "Press a hardware key (HOME, BACK, ENTER, MENU, POWER, VOLUME_UP, VOLUME_DOWN, DEL)",
        inputSchema: {
          type: "object",
          properties: {
            key: { type: "string", description: "Key name or keycode number" },
          },
          required: ["key"],
        },
      },
      
      // ===== 构建与部署 =====
      {
        name: "build",
        description: "Build the Android project with Gradle. Supports debug/release variants and custom flavors.",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            variant: { type: "string", description: "Build variant (debug/release)", default: "debug" },
            flavor: { type: "string", description: "Product flavor (optional)" },
          },
        },
      },
      {
        name: "install_and_launch",
        description: "Install APK and launch an app on the device",
        inputSchema: {
          type: "object",
          properties: {
            apkPath: { type: "string", description: "Path to APK file (optional if already installed)" },
            packageName: { type: "string", description: "Android package name" },
            activity: { type: "string", description: "Activity to launch (optional)" },
            serial: { type: "string", description: "Device serial (optional, for multiple devices)" },
          },
          required: ["packageName"],
        },
      },
      {
        name: "build_deploy",
        description: "Complete build and deploy pipeline: clean, build, install, launch",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            variant: { type: "string", description: "Build variant", default: "debug" },
            packageName: { type: "string", description: "Package name to launch after install" },
            autoLaunch: { type: "boolean", description: "Auto launch after install", default: true },
          },
          required: ["packageName"],
        },
      },
      
      // ===== UI验证与分析 =====
      {
        name: "verify_ui",
        description: "Verify UI by comparing screenshots, checking color, or OCR text detection",
        inputSchema: {
          type: "object",
          properties: {
            type: {
              type: "string",
              enum: ["compare", "color", "ocr"],
              description: "Verification type",
            },
            baselinePath: { type: "string", description: "Baseline image path (for compare)" },
            currentPath: { type: "string", description: "Current screenshot path" },
            checkText: { type: "string", description: "Expected text (for OCR)" },
            checkColor: { type: "string", description: "Expected hex color (for color check)" },
            x: { type: "number", description: "X coordinate (for color check)" },
            y: { type: "number", description: "Y coordinate (for color check)" },
          },
          required: ["type"],
        },
      },
      {
        name: "analyze_screenshot",
        description: "3-stage screenshot analysis: (1) Python PIL pixel measurements, (2) Kimi k2.6 vision AI for visual understanding, (3) precise card-by-card verification. Returns comprehensive UI report with issues and suggestions.",
        inputSchema: {
          type: "object",
          properties: {
            filePath: { type: "string", description: "Path to the screenshot PNG file" },
            prompt: { type: "string", description: "Optional custom analysis focus (e.g. 'check card spacing')" },
          },
          required: ["filePath"],
        },
      },
      {
        name: "compare_screenshots",
        description: "Compare two screenshots (baseline vs current) using Kimi k2.6 vision AI. Detects layout differences, color mismatches, and regressions.",
        inputSchema: {
          type: "object",
          properties: {
            baselinePath: { type: "string", description: "Path to baseline/design screenshot" },
            currentPath: { type: "string", description: "Path to current implementation screenshot" },
            prompt: { type: "string", description: "Optional custom comparison focus" },
          },
          required: ["baselinePath", "currentPath"],
        },
      },
      
      // ===== 日志与调试 =====
      {
        name: "get_logs",
        description: "Fetch Android device logs (logcat). Filters for crash/fatal/exception by default. Use 'all' filter to see all logs for a package.",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Filter logs by package name (e.g. com.example.toutiao)" },
            filter: { type: "string", description: "Log filter: 'crash' (default) for errors/exceptions, 'all' for all logs", default: "crash" },
            lines: { type: "number", description: "Max lines to return", default: 50 },
            serial: { type: "string", description: "Device serial (optional)" },
          },
        },
      },
      {
        name: "clear_logs",
        description: "Clear device log buffer",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial (optional)" },
          },
        },
      },
      
      // ===== 设备管理 =====
      {
        name: "list_devices",
        description: "List all connected Android devices with detailed information",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
      {
        name: "device_info",
        description: "Get detailed information about a specific device",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial number" },
          },
          required: ["serial"],
        },
      },
      {
        name: "shell_command",
        description: "Execute a shell command on the device",
        inputSchema: {
          type: "object",
          properties: {
            command: { type: "string", description: "Shell command to execute" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["command"],
        },
      },
      
      // ===== 应用管理 =====
      {
        name: "list_apps",
        description: "List installed applications on the device",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial (optional)" },
            system: { type: "boolean", description: "Include system apps", default: false },
            thirdParty: { type: "boolean", description: "Include third-party apps only", default: true },
          },
        },
      },
      {
        name: "app_info",
        description: "Get detailed information about a specific app",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["packageName"],
        },
      },
      {
        name: "uninstall_app",
        description: "Uninstall an application",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to uninstall" },
            serial: { type: "string", description: "Device serial (optional)" },
            keepData: { type: "boolean", description: "Keep app data", default: false },
          },
          required: ["packageName"],
        },
      },
      {
        name: "clear_app_data",
        description: "Clear application data and cache",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["packageName"],
        },
      },
      {
        name: "stop_app",
        description: "Force stop an application",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["packageName"],
        },
      },
      
      // ===== 性能监控 =====
      {
        name: "performance_metrics",
        description: "Collect device performance metrics: CPU, memory, FPS, battery, temperature",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to monitor (optional)" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
        },
      },
      {
        name: "record_screen",
        description: "Record device screen for specified duration",
        inputSchema: {
          type: "object",
          properties: {
            duration: { type: "number", description: "Recording duration in seconds", default: 10 },
            outputPath: { type: "string", description: "Output file path", default: "./screen_record.mp4" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
        },
      },
      
      // ===== 代码质量 =====
      {
        name: "code_quality",
        description: "Run code quality checks: ktlint, complexity analysis, line counts",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            fix: { type: "boolean", description: "Auto-fix ktlint issues", default: false },
          },
        },
      },
      {
        name: "run_tests",
        description: "Run unit tests and instrumented tests",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            type: { type: "string", description: "Test type: unit/instrumented/all", default: "unit" },
            module: { type: "string", description: "Specific module to test (optional)" },
          },
        },
      },
      
      // ===== UI自动化测试 =====
      {
        name: "ui_test",
        description: "Run UI automation test with specified steps",
        inputSchema: {
          type: "object",
          properties: {
            steps: {
              type: "array",
              description: "Test steps array",
              items: {
                type: "object",
                properties: {
                  action: { type: "string", enum: ["tap", "swipe", "input", "wait", "screenshot"] },
                  params: { type: "object" },
                },
              },
            },
          },
          required: ["steps"],
        },
      },
      {
        name: "regression_test",
        description: "Run regression test suite: app launch, screenshot, UI hierarchy",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to test" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["packageName"],
        },
      },
      
      // ===== 项目报告 =====
      {
        name: "project_report",
        description: "Generate comprehensive project report with metrics, quality, architecture analysis",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            includePerformance: { type: "boolean", description: "Include performance metrics", default: false },
            packageName: { type: "string", description: "Package name for performance monitoring" },
            format: { type: "string", description: "Output format: markdown/json", default: "markdown" },
          },
        },
      },
      
      // ===== 文件操作 =====
      {
        name: "push_file",
        description: "Push file to device",
        inputSchema: {
          type: "object",
          properties: {
            localPath: { type: "string", description: "Local file path" },
            remotePath: { type: "string", description: "Remote destination path" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["localPath", "remotePath"],
        },
      },
      {
        name: "pull_file",
        description: "Pull file from device",
        inputSchema: {
          type: "object",
          properties: {
            remotePath: { type: "string", description: "Remote file path" },
            localPath: { type: "string", description: "Local destination path" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["remotePath", "localPath"],
        },
      },
      
      // ===== 视觉驱动交互 =====
      {
        name: "vision_action",
        description: "Use vision AI (Kimi k2.6) to locate and interact with UI elements by natural language description. Screenshots the app, asks the vision model to find the target element and return precise coordinates, executes the action (tap/swipe/input), and captures a confirmation screenshot. Supports multi-step sequences via the 'prompts' array.",
        inputSchema: {
          type: "object",
          properties: {
            prompt: { type: "string", description: "Natural language instruction, e.g. 'tap the 视频 tab in bottom nav' or 'swipe down to scroll'" },
            prompts: { type: "array", items: { type: "string" }, description: "Multiple instructions for sequential actions (each step uses the previous step's after-screenshot as its before-screenshot)" },
            beforeScreenshot: { type: "string", description: "Optional path to an existing screenshot to use instead of taking a new one" },
          },
        },
      },

      // ===== 网络调试 =====
      {
        name: "network_state",
        description: "Get device network state (WiFi, mobile, airplane mode)",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial (optional)" },
          },
        },
      },
      {
        name: "set_network",
        description: "Set device network state",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", enum: ["wifi", "mobile", "airplane"], description: "Network type" },
            enabled: { type: "boolean", description: "Enable or disable" },
            serial: { type: "string", description: "Device serial (optional)" },
          },
          required: ["type", "enabled"],
        },
      },
    ],
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  log(`tool call: ${name}`);

  try {
    switch (name) {
      // 基础交互
      case "screenshot":
        return handleScreenshot(args as Record<string, unknown>);
      case "tap":
        return handleTap(args as Record<string, unknown>);
      case "swipe":
        return handleSwipe(args as Record<string, unknown>);
      case "input_text":
        return handleInputText(args as Record<string, unknown>);
      case "press_key":
        return handlePressKey(args as Record<string, unknown>);
      
      // 构建与部署
      case "build":
        return handleBuild(args as Record<string, unknown>);
      case "install_and_launch":
        return handleInstallAndLaunch(args as Record<string, unknown>);
      case "build_deploy":
        return handleBuildDeploy(args as Record<string, unknown>);
      
      // UI验证与分析
      case "verify_ui":
        return handleVerifyUI(args as Record<string, unknown>);
      case "analyze_screenshot":
        return handleAnalyzeScreenshot(args as Record<string, unknown>);
      case "compare_screenshots":
        return handleCompareScreenshots(args as Record<string, unknown>);
      
      // 日志与调试
      case "get_logs":
        return handleGetLogs(args as Record<string, unknown>);
      case "clear_logs":
        return handleDeviceManagement(args as Record<string, unknown>, "clear_logs");
      
      // 设备管理
      case "list_devices":
        return handleDeviceManagement(args as Record<string, unknown>, "list_devices");
      case "device_info":
        return handleDeviceManagement(args as Record<string, unknown>, "device_info");
      case "shell_command":
        return handleDeviceManagement(args as Record<string, unknown>, "shell_command");
      
      // 应用管理
      case "list_apps":
        return handleAppManagement(args as Record<string, unknown>, "list_apps");
      case "app_info":
        return handleAppManagement(args as Record<string, unknown>, "app_info");
      case "uninstall_app":
        return handleAppManagement(args as Record<string, unknown>, "uninstall_app");
      case "clear_app_data":
        return handleAppManagement(args as Record<string, unknown>, "clear_app_data");
      case "stop_app":
        return handleAppManagement(args as Record<string, unknown>, "stop_app");
      
      // 性能监控
      case "performance_metrics":
        return handlePerformanceMonitor(args as Record<string, unknown>);
      case "record_screen":
        return handleDeviceManagement(args as Record<string, unknown>, "record_screen");
      
      // 代码质量
      case "code_quality":
        return handleCodeQuality(args as Record<string, unknown>);
      case "run_tests":
        return handleBuildDeploy(args as Record<string, unknown>, "run_tests");
      
      // UI自动化测试
      case "ui_test":
        return handleUITest(args as Record<string, unknown>, "run_test");
      case "regression_test":
        return handleUITest(args as Record<string, unknown>, "regression");
      
      // 项目报告
      case "project_report":
        return handleProjectReport(args as Record<string, unknown>);
      
      // 文件操作
      case "push_file":
        return handleFileOperations(args as Record<string, unknown>, "push");
      case "pull_file":
        return handleFileOperations(args as Record<string, unknown>, "pull");
      
      // 视觉驱动交互
      case "vision_action":
        return handleVisionAction(args as Record<string, unknown>);

      // 网络调试
      case "network_state":
        return handleNetworkDebug(args as Record<string, unknown>, "get_state");
      case "set_network":
        return handleNetworkDebug(args as Record<string, unknown>, "set_state");
      
      default:
        error(`Unknown tool: ${name}`);
        return {
          isError: true,
          content: [{ type: "text", text: `Unknown tool: ${name}` }],
        };
    }
  } catch (e) {
    const err = e as Error;
    error(`Error executing tool ${name}:`, err);
    return {
      isError: true,
      content: [{ type: "text", text: `Error: ${err.message || "Unknown error"}` }],
    };
  }
});

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  log("AndroidDev-Assist MCP Server v2.0.0 running on stdio");
  log("Capabilities: device management, app management, performance monitoring, code quality, UI testing, project reporting");

  // Graceful shutdown handlers
  const shutdown = async (signal: string) => {
    log(`Received ${signal}, shutting down gracefully...`);
    try {
      await transport.close();
      log("Transport closed");
    } catch (e) {
      error("Error during shutdown:", e);
    }
    process.exit(0);
  };

  process.on("SIGINT", () => void shutdown("SIGINT"));
  process.on("SIGTERM", () => void shutdown("SIGTERM"));

  // Handle uncaught errors
  process.on("uncaughtException", (err) => {
    error("Uncaught exception:", err);
    process.exit(1);
  });

  process.on("unhandledRejection", (reason) => {
    error("Unhandled rejection:", reason);
  });
}

main().catch((err) => {
  error("Fatal error:", err);
  process.exit(1);
});
