import { config as loadEnv } from "dotenv";
loadEnv();

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { handleScreenshot } from "./tools/screenshot.js";
import { handleTap, handleSwipe, handleInputText, handlePressKey, handleBuild, handleInstallAndLaunch } from "./tools/interaction.js";
import { handleVerifyUI } from "./tools/verify.js";
import { handleGetLogs, handleClearLogs } from "./tools/logs.js";
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
import { handleMeasureAppLaunch } from "./tools/launch-speed.js";
import { handleDumpHierarchy, handleFindElement, handleWaitForElement } from "./tools/hierarchy.js";
import { handleLogcatSearch, handleParseCrash } from "./tools/logcat-search.js";
import { handleApkMetadata } from "./tools/apk-metadata.js";
import { handleScreenshotRegion } from "./tools/screenshot-region.js";
import { handleSetOrientation, handleSetGps, handleAnimationScale } from "./tools/device-control.js";
import { handleExtractDesignSpec, handleExtractDesignTokens, handleExtractComponents, handleListDesignFiles, handleDesignToCompose } from "./tools/design-spec.js";
import { log, error } from "./utils/logger.js";

const server = new Server(
  {
    name: "android-dev-assist",
    version: "3.1.0",
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
      // ════════════════════════════════════════════════════════════
      // 基础交互
      // ════════════════════════════════════════════════════════════
      {
        name: "screenshot",
        description: "截取当前设备屏幕并保存为 PNG 文件（单次 ADB 往返，screencap 直传 stdout）。使用时机：调用 analyze_screenshot / compare_screenshots / verify_ui / vision_action 之前必须先截图。改用 screenshot_region：若你只关心屏幕局部。返回 JSON: {success, path, timestamp, sizeBytes}。耗时 fast (~300ms)。示例：screenshot({ savePath: './home.png' })",
        inputSchema: {
          type: "object",
          properties: {
            savePath: { type: "string", description: "可选。保存路径（默认 ./screenshots/screenshot_<ts>.png）" },
          },
        },
      },
      {
        name: "screenshot_region",
        description: "截取屏幕指定矩形区域并保存为 PNG。适合：只分析顶部 Tab、底部导航、单个卡片，避免传整张 1080×2400 图给视觉 AI。改用 screenshot：若需要全屏。返回 JSON: {success, path, region: {x, y, width, height}, parentPath}。耗时 fast (~500ms, 含全屏+裁剪)。示例：screenshot_region({ x: 0, y: 0, width: 1080, height: 200 })",
        inputSchema: {
          type: "object",
          properties: {
            x: { type: "number", description: "Required. 左上角 X 坐标 (px)" },
            y: { type: "number", description: "Required. 左上角 Y 坐标 (px)" },
            width: { type: "number", description: "Required. 区域宽度 (px, > 0)" },
            height: { type: "number", description: "Required. 区域高度 (px, > 0)" },
            savePath: { type: "string", description: "可选。保存路径（默认在 screenshots/ 下追加 _region 标记）" },
          },
          required: ["x", "y", "width", "height"],
        },
      },
      {
        name: "tap",
        description: "在屏幕指定坐标点击一下。改用 vision_action / find_element：若你不知道坐标。改用 swipe：若需要长按或拖拽。返回 JSON: {success, action: 'tap', x, y}。耗时 fast (~200ms)。示例：tap({ x: 540, y: 1200 })",
        inputSchema: {
          type: "object",
          properties: {
            x: { type: "number", description: "Required. X 坐标 (px)" },
            y: { type: "number", description: "Required. Y 坐标 (px)" },
          },
          required: ["x", "y"],
        },
      },
      {
        name: "swipe",
        description: "从 (x1,y1) 拖到 (x2,y2)，duration 单位 ms（默认 300）。长按：把 x1==x2、y1==y2，duration 设大（如 800ms）。下滑刷新：duration 短、x1=x2=屏幕中线、y1>y2。返回 JSON: {success, action: 'swipe'}。耗时 fast (~400ms)。示例：swipe({ x1: 540, y1: 1800, x2: 540, y2: 400, duration: 250 })",
        inputSchema: {
          type: "object",
          properties: {
            x1: { type: "number", description: "Required. 起点 X (px)" },
            y1: { type: "number", description: "Required. 起点 Y (px)" },
            x2: { type: "number", description: "Required. 终点 X (px)" },
            y2: { type: "number", description: "Required. 终点 Y (px)" },
            duration: { type: "number", description: "可选。滑动时长 ms，默认 300", default: 300 },
          },
          required: ["x1", "y1", "x2", "y2"],
        },
      },
      {
        name: "input_text",
        description: "向当前焦点输入框注入文本。空格会被转义为 %s；中文/特殊字符可能被 input text 拒绝，此时改用 vision_action 自动切换输入法。注意：不负责先点击输入框。返回 JSON: {success, action: 'input_text', text}。耗时 fast (~150ms)。示例：input_text({ text: 'hello world' })",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "Required. 要输入的文本（空格会被转义）" },
          },
          required: ["text"],
        },
      },
      {
        name: "press_key",
        description: "模拟硬件按键。可用 key：HOME, BACK, ENTER, MENU, POWER, VOLUME_UP, VOLUME_DOWN, DEL；或直接传数字 keycode。返回 JSON: {success, action: 'press_key', key}。耗时 fast (~150ms)。示例：press_key({ key: 'HOME' })",
        inputSchema: {
          type: "object",
          properties: {
            key: { type: "string", description: "Required. 按键名或数字 keycode（如 'HOME' 或 3）" },
          },
          required: ["key"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // UI 层级 / 元素查找 / 等待
      // ════════════════════════════════════════════════════════════
      {
        name: "dump_hierarchy",
        description: "使用 uiautomator dump 当前屏幕的 UI 层级，返回结构化元素列表（含 type/text/resource-id/clickable/bounds）。使用时机：需要先看看界面上有什么再决定怎么操作；vision 视觉识别代价高，Agent 在写代码逻辑时优先用此工具。改用 vision_action：若要纯按自然语言点击（不关心元素结构）。返回 JSON: {success, count, elements: [{type, text, resourceId, clickable, bounds}]}。耗时 fast (~800ms)。示例：dump_hierarchy({})",
        inputSchema: {
          type: "object",
          properties: {
            includeRaw: { type: "boolean", description: "可选。是否在结果中附加提示信息", default: false },
          },
        },
      },
      {
        name: "find_element",
        description: "按 text / resource-id / class 查找 UI 元素，返回中心坐标（可直接喂给 tap）。使用时机：知道元素文本（如「确定」「我的」）或 resource-id 但不想算坐标。改用 dump_hierarchy：若想看所有候选。返回 JSON: {success, found, count, primary: {center: {x, y}, ...}, all: [...]}；found=false 时含 hint。耗时 fast (~800ms)。示例：find_element({ text: '确定' })",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "可选。元素显示文本（支持 contains 匹配）" },
            resourceId: { type: "string", description: "可选。资源 ID（精确匹配，如 com.example:id/btn_ok）" },
            className: { type: "string", description: "可选。类名（短名或全名，如 Button）" },
            exact: { type: "boolean", description: "可选。是否精确匹配 text，默认 false（contains）", default: false },
          },
        },
      },
      {
        name: "wait_for_element",
        description: "轮询等待元素出现或消失，避免 Agent 在异步 UI（加载、动画）尚未就绪时盲目操作。返回 JSON: {success, found, waitedMs, element?}。耗时 medium (按 timeoutMs，通常 1-10s)。示例：wait_for_element({ text: '加载完成', timeoutMs: 8000 })",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "可选。待匹配文本" },
            resourceId: { type: "string", description: "可选。待匹配资源 ID" },
            timeoutMs: { type: "number", description: "可选。最长等待毫秒数，默认 10000", default: 10000 },
            intervalMs: { type: "number", description: "可选。轮询间隔 ms，默认 500", default: 500 },
            expect: { type: "string", enum: ["appear", "disappear"], description: "可选。等待出现或消失，默认 appear", default: "appear" },
          },
        },
      },

      // ════════════════════════════════════════════════════════════
      // 构建与部署
      // ════════════════════════════════════════════════════════════
      {
        name: "build",
        description: "使用 Gradle 构建 Android 工程。返回 JSON: {success, apkPath, buildTime, error?}。耗时 slow (1-3 分钟)。示例：build({ projectPath: '.', variant: 'debug' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "可选。Android 工程根路径，默认 '.'", default: "." },
            variant: { type: "string", description: "可选。构建变体：debug | release", default: "debug" },
            flavor: { type: "string", description: "可选。产品 flavor（多渠道工程）" },
          },
        },
      },
      {
        name: "install_and_launch",
        description: "安装 APK 并启动应用。若只传 packageName 则只启动已安装的应用（不重装）。返回 JSON: {success, action, packageName}。耗时 medium (10-30s 含 install)。示例：install_and_launch({ apkPath: './app-debug.apk', packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            apkPath: { type: "string", description: "可选。APK 路径；省略则只启动已安装应用" },
            packageName: { type: "string", description: "Required. Android 包名" },
            activity: { type: "string", description: "可选。要启动的 Activity" },
            serial: { type: "string", description: "可选。设备序列号（多设备场景）" },
          },
          required: ["packageName"],
        },
      },
      {
        name: "build_deploy",
        description: "完整 CI/CD 流水线：clean → build → install → launch，按阶段返回 stage 信息。改用 build + install_and_launch：若你想手动控制每步。返回 JSON: {success, stage, apkPath, buildTime, installed, launched}。耗时 slow (2-5 分钟)。示例：build_deploy({ packageName: 'com.example.app', variant: 'debug' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "可选。工程根路径", default: "." },
            variant: { type: "string", description: "可选。构建变体", default: "debug" },
            packageName: { type: "string", description: "Required. 启动的包名" },
            autoLaunch: { type: "boolean", description: "可选。安装后自动启动，默认 true", default: true },
          },
          required: ["packageName"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 视觉 AI 分析
      // ════════════════════════════════════════════════════════════
      {
        name: "verify_ui",
        description: "UI 像素级验证：'compare' 对比两张截图差异；'color' 取 (x,y) 像素颜色并与预期比对；'ocr' 占位（需安装 tesseract.js）。使用时机：回归测试 / 验证某个像素颜色是否符合设计稿。改用 analyze_screenshot：若要 Minimax 视觉理解整个布局。返回 JSON：compare 返回 {diffPixels, diffPercentage, isMatch, diffImagePath}；color 返回 {match, expected, actual, x, y}。耗时 compare fast (~500ms)，color fast (~100ms)。示例：verify_ui({ type: 'color', currentPath: './home.png', x: 100, y: 50, checkColor: '#FF5757' })",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", enum: ["compare", "color", "ocr"], description: "Required. 验证类型" },
            baselinePath: { type: "string", description: "compare 模式必填：基线截图" },
            currentPath: { type: "string", description: "compare/color 模式必填：当前截图" },
            checkText: { type: "string", description: "ocr 模式必填：要查找的文本" },
            checkColor: { type: "string", description: "color 模式必填：期望颜色 (十六进制，如 '#FF0000')" },
            x: { type: "number", description: "color 模式必填：X 坐标" },
            y: { type: "number", description: "color 模式必填：Y 坐标" },
          },
          required: ["type"],
        },
      },
      {
        name: "analyze_screenshot",
        description: "三阶段截图分析：(1) PIL 像素测量布局 (2) Minimax 视觉 AI 语义理解 (3) 卡片级精确验证。需要 Python3 + PIL 环境；视觉阶段需要 MINIMAX_API_KEY。使用时机：用户说「帮我看看这个页面」「有什么问题」时的一站式 UI 审查。改用 verify_ui：若只需像素级检查。返回 JSON: {file, pil: {...}, vision: '...', cards: [...], checklist: [...]}。耗时 slow (10-30s，vision 阶段占大头)。示例：analyze_screenshot({ filePath: './home.png', prompt: '检查卡片间距' })",
        inputSchema: {
          type: "object",
          properties: {
            filePath: { type: "string", description: "Required. 截图文件路径" },
            prompt: { type: "string", description: "可选。自定义分析焦点（如「检查 header 颜色」「找布局错位」）" },
          },
          required: ["filePath"],
        },
      },
      {
        name: "compare_screenshots",
        description: "用 Minimax 视觉 AI 对比两张截图（设计稿 vs 实现）。比 verify_ui 的 compare 慢但更智能（能识别语义差异）。需要 MINIMAX_API_KEY。返回 JSON: {success, baseline, current, analysis: '...'}。耗时 slow (5-20s)。示例：compare_screenshots({ baselinePath: './design.png', currentPath: './home.png' })",
        inputSchema: {
          type: "object",
          properties: {
            baselinePath: { type: "string", description: "Required. 基线/设计稿" },
            currentPath: { type: "string", description: "Required. 当前实现" },
            prompt: { type: "string", description: "可选。定制对比焦点" },
          },
          required: ["baselinePath", "currentPath"],
        },
      },
      {
        name: "vision_action",
        description: "用 Minimax 视觉 AI 按自然语言描述定位元素并执行点击/滑动/输入。适合：(1) Agent 不知道目标坐标 (2) UI 元素位置/文案会动态变化 (3) 不想写规则。代价：每步 3-8s，且需 MINIMAX_API_KEY。改用 find_element + tap：若元素有稳定 text/resource-id（更快）。返回 JSON: {success, steps: [{action, x, y, text, confidence, reasoning}], screenshot, durationMs}。耗时 slow (3-8s 每步)。示例：vision_action({ prompt: '点击底部导航的「视频」tab' }) 或 prompts: ['点击搜索', '输入 hello', '按回车']",
        inputSchema: {
          type: "object",
          properties: {
            prompt: { type: "string", description: "可选。单步自然语言指令" },
            prompts: { type: "array", items: { type: "string" }, description: "可选。多步串联（每步用前一步的 after-screenshot）" },
            beforeScreenshot: { type: "string", description: "可选。已有的截图路径，省去首次截图" },
          },
        },
      },

      // ════════════════════════════════════════════════════════════
      // 日志与调试
      // ════════════════════════════════════════════════════════════
      {
        name: "get_logs",
        description: "拉取设备 logcat。filter='crash' 默认只看错误/异常（用 logcat 原生 tag 过滤，毫秒级返回）；filter='all' 拿所有日志（传 packageName 时自动加 --pid 过滤）。使用时机：排查崩溃、追踪业务流程日志。改用 logcat_search：若需要正则或特定 tag 过滤。返回 JSON: {success, filter, mode, lines, appRunning, pid, logs: [...]}。耗时 crash fast (~500ms)，all medium (~2s)。示例：get_logs({ packageName: 'com.example.app', filter: 'crash', lines: 50 })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "可选。按包名过滤（自动用 pidof 限定 pid）" },
            filter: { type: "string", enum: ["crash", "all"], description: "可选。crash=只看错误（快），all=全量（慢）", default: "crash" },
            lines: { type: "number", description: "可选。最多返回行数", default: 50 },
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },
      {
        name: "logcat_search",
        description: "logcat 关键词/正则搜索。比 get_logs 更灵活：支持正则模式、tag 过滤、严重度级别、行数限制。使用时机：找特定业务日志（如网络请求、用户操作），或按 tag 过滤。改用 get_logs：若只需错误日志（更快）。返回 JSON: {success, matched, pattern, tag, level, appRunning, lines: [...]}。耗时 fast (~500ms)。示例：logcat_search({ pattern: 'Network.*timeout', tag: 'OkHttp', level: 'W' })",
        inputSchema: {
          type: "object",
          properties: {
            pattern: { type: "string", description: "可选。POSIX 正则（与 tag 二选一）" },
            packageName: { type: "string", description: "可选。按包名过滤（自动取 pid）" },
            tag: { type: "string", description: "可选。Android log tag" },
            level: { type: "string", enum: ["V", "D", "I", "W", "E"], description: "可选。最低严重度", default: "I" },
            maxLines: { type: "number", description: "可选。最大返回行数", default: 200 },
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },
      {
        name: "parse_crash",
        description: "从 logcat 提取并解析 Java 崩溃 / ANR / Native crash，按事件分组，输出结构化堆栈（前 30 行）。使用时机：用户说「为什么崩溃了」「刚才 ANR 了吗」时一键归因。返回 JSON: {success, crashCount, crashes: [{type, timestamp, process, exception, message, stack, raw}], rawLineCount}。耗时 medium (~2-5s)。示例：parse_crash({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "可选。按包名过滤" },
            lookbackSec: { type: "number", description: "可选。回看秒数（保留字段，当前实现拉 -t 2000 行）", default: 300 },
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },
      {
        name: "clear_logs",
        description: "清空 logcat 缓冲。在 measure_app_launch / 自定义测试前清理噪声日志。返回 JSON: {success, message}。耗时 fast (~200ms)。示例：clear_logs({})",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },

      // ════════════════════════════════════════════════════════════
      // 设备管理
      // ════════════════════════════════════════════════════════════
      {
        name: "list_devices",
        description: "列出所有连接的设备，含 Android 版本/SDK/分辨率/DPI。使用时机：开始任何设备操作前先确认目标。返回 JSON: {success, deviceCount, devices: [{serial, state, model, androidVersion, sdkVersion, screenResolution, density}]}。耗时 fast (~1-2s，4 个属性查询并行)。示例：list_devices({})",
        inputSchema: { type: "object", properties: {} },
      },
      {
        name: "device_info",
        description: "获取单台设备的完整 getprop 属性（型号、制造商、电池、内存、CPU 架构等）。使用时机：需要详细硬件/系统信息。返回 JSON: {success, serial, details: {[key]: value}}。耗时 medium (~3-5s, 100+ 属性)。示例：device_info({ serial: 'emulator-5554' })",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Required. 设备序列号" },
          },
          required: ["serial"],
        },
      },
      {
        name: "shell_command",
        description: "在设备上执行任意 shell 命令。慎用：没有安全沙箱，命令直接在设备 shell 执行。改用专用工具：若已有对应能力（如截图用 screenshot）。返回 JSON: {success, output}。耗时 depends on command。示例：shell_command({ command: 'pm list packages | head -5' })",
        inputSchema: {
          type: "object",
          properties: {
            command: { type: "string", description: "Required. shell 命令" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["command"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 应用管理
      // ════════════════════════════════════════════════════════════
      {
        name: "list_apps",
        description: "列出已安装应用。system=false 时排除系统应用（默认），thirdParty=true 时只列第三方。返回 JSON: {success, appCount, apps: [{packageName, versionName, versionCode}]}。耗时 medium (~1-3s, 单次 dumpsys + 解析，无 N+1)。示例：list_apps({ thirdParty: true })",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "可选。设备序列号" },
            system: { type: "boolean", description: "可选。是否包含系统应用", default: false },
            thirdParty: { type: "boolean", description: "可选。是否只列第三方", default: true },
          },
        },
      },
      {
        name: "app_info",
        description: "获取应用详细信息（版本号、安装时间、数据目录、签名信息等）。使用时机：需要确认应用版本或安装来源。返回 JSON: {success, app: {packageName, versionName, versionCode, firstInstallTime, lastUpdateTime, dataDir}}。耗时 fast (~500ms)。示例：app_info({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. 包名" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["packageName"],
        },
      },
      {
        name: "uninstall_app",
        description: "卸载应用。keepData=true 时保留 /data/data/&lt;pkg&gt; 目录（重装可恢复数据）。返回 JSON: {success, message}。耗时 medium (~3-5s)。示例：uninstall_app({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. 包名" },
            serial: { type: "string", description: "可选。设备序列号" },
            keepData: { type: "boolean", description: "可选。保留应用数据", default: false },
          },
          required: ["packageName"],
        },
      },
      {
        name: "clear_app_data",
        description: "清除应用数据（含数据库、SharedPreferences、缓存）。等价于「设置→应用→存储→清除数据」。常用于 measure_app_launch 的冷启动前清理。返回 JSON: {success, message}。耗时 medium (~3-5s)。示例：clear_app_data({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. 包名" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["packageName"],
        },
      },
      {
        name: "stop_app",
        description: "强制停止应用（force-stop），下次启动会完整走 Application.onCreate。返回 JSON: {success, message}。耗时 fast (~500ms)。示例：stop_app({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. 包名" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["packageName"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 性能监控
      // ════════════════════════════════════════════════════════════
      {
        name: "performance_metrics",
        description: "采集设备/应用性能指标：CPU 使用率、PSS 内存、gfxinfo FPS、电池电量、温度。packageName 传入时额外给出应用级 PSS。返回 JSON: {success, cpu, memory, fps, battery, temperature}。耗时 medium (~2-3s, 多个 dumpsys 并行)。示例：performance_metrics({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "可选。包名（传入则额外采集应用级 PSS）" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },
      {
        name: "measure_app_launch",
        description: "测量应用冷启动/热启动/页面跳转耗时，多次采样（默认 3 次）取 min/max/avg/p95，并按 TTID 给出 A/B/C/D 评分 + 优化建议。launchType：cold_start 走 force-stop+clear data；warm_start 走 HOME→重启。返回 JSON: {success, packageName, grade, statistics: {ttid, ttfd, totalTime}, results, recommendations, report}。耗时 slow (~3-10s × iterations)。示例：measure_app_launch({ packageName: 'com.example.app', launchType: 'cold_start', iterations: 3 })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. 包名" },
            launchType: { type: "string", enum: ["cold_start", "warm_start", "page_transition"], description: "可选。冷/热/页面跳转", default: "cold_start" },
            activityName: { type: "string", description: "可选。指定 Activity（全限定类名）" },
            iterations: { type: "number", description: "可选。采样次数，默认 3", default: 3 },
          },
          required: ["packageName"],
        },
      },
      {
        name: "record_screen",
        description: "录屏到本地 MP4 文件。默认 10s。返回 JSON: {success, message, path}。耗时 slow (≥duration)。示例：record_screen({ duration: 15, outputPath: './demo.mp4' })",
        inputSchema: {
          type: "object",
          properties: {
            duration: { type: "number", description: "可选。录制秒数", default: 10 },
            outputPath: { type: "string", description: "可选。输出路径", default: "./screen_record.mp4" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },

      // ════════════════════════════════════════════════════════════
      // 设备控制（新）
      // ════════════════════════════════════════════════════════════
      {
        name: "set_orientation",
        description: "强制设备旋转方向。portrait=竖屏，landscape=横屏，auto=跟随重力。注意：部分 App 在 manifest 中锁定方向，会覆盖此设置。返回 JSON: {success, orientation, accelRotation, userRotation, hint}。耗时 fast (~500ms)。示例：set_orientation({ orientation: 'landscape' })",
        inputSchema: {
          type: "object",
          properties: {
            orientation: { type: "string", enum: ["portrait", "landscape", "auto"], description: "Required. 目标方向" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["orientation"],
        },
      },
      {
        name: "set_gps",
        description: "模拟 GPS 位置（仅 Android 模拟器有效，真机需开启「允许模拟位置」开发者选项并装 mock app）。lat ∈ [-90,90]、lon ∈ [-180,180]（注意 emu 命令是 lon 在前）。返回 JSON: {success, lat, lon, method, warning}。耗时 fast (~500ms)。示例：set_gps({ lat: 39.9042, lon: 116.4074 })",
        inputSchema: {
          type: "object",
          properties: {
            lat: { type: "number", description: "Required. 纬度（十进制度）" },
            lon: { type: "number", description: "Required. 经度（十进制度）" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["lat", "lon"],
        },
      },
      {
        name: "animation_scale",
        description: "调整系统动画缩放：0=关闭动画（UI 测试/录屏首选，瞬间执行）、1=系统默认、2=慢速（演示/截图用）。改回 1 恢复。返回 JSON: {success, scale, applied: [{key, value, success}]}。耗时 fast (~500ms)。示例：animation_scale({ scale: 0 })",
        inputSchema: {
          type: "object",
          properties: {
            scale: { type: "number", description: "Required. 缩放值 [0, 10]，常用 0/0.5/1/2" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["scale"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 代码质量
      // ════════════════════════════════════════════════════════════
      {
        name: "code_quality",
        description: "代码质量检查：ktlint 规范（fix=true 时自动修复）、圈复杂度、代码行数统计。需要 ktlint/detekt 在 PATH 中。返回 JSON: {success, summary, issues: [...], linesOfCode}。耗时 medium (~5-30s)。示例：code_quality({ projectPath: '.', fix: false })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "可选。工程根路径", default: "." },
            fix: { type: "boolean", description: "可选。自动修复 ktlint 问题", default: false },
          },
        },
      },
      {
        name: "run_tests",
        description: "运行单元测试 (JVM, ./gradlew test) 或仪器化测试 (on-device, connectedAndroidTest)。type='all' 跑两类。返回 JSON: {success, results: {unit: {...}, instrumented: {...}}}。耗时 slow (1-10 分钟)。示例：run_tests({ type: 'unit' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "可选。工程根路径", default: "." },
            type: { type: "string", enum: ["unit", "instrumented", "all"], description: "可选。测试类型", default: "unit" },
            module: { type: "string", description: "可选。指定 Gradle module" },
          },
        },
      },

      // ════════════════════════════════════════════════════════════
      // UI 自动化测试
      // ════════════════════════════════════════════════════════════
      {
        name: "ui_test",
        description: "执行声明式 UI 自动化测试：steps 数组中每项 {action, params}。actions：tap(x,y) / swipe(x1,y1,x2,y2,duration) / input(text) / wait(ms) / screenshot()。返回 JSON: {success, message, duration, screenshot}。耗时 depends on steps。示例：ui_test({ steps: [{action: 'screenshot', params: {}}, {action: 'tap', params: {x: 500, y: 800}}] })",
        inputSchema: {
          type: "object",
          properties: {
            steps: {
              type: "array",
              description: "测试步骤序列",
              items: {
                type: "object",
                properties: {
                  action: { type: "string", enum: ["tap", "swipe", "input", "wait", "screenshot"], description: "Required. 动作类型" },
                  params: { type: "object", description: "Required. 动作参数（tap/swipe 需 x,y；input 需 text；wait 需 durationMs）" },
                },
                required: ["action", "params"],
              },
            },
          },
          required: ["steps"],
        },
      },
      {
        name: "regression_test",
        description: "运行基础回归测试套件：启动 App → 截图 → 验证 UI 层级可读 → Activity 跳转。改用 ui_test：若需要自定义步骤。返回 JSON: {success, passed, failed, total, results: [{name, success, message}]}。耗时 slow (~10-15s)。示例：regression_test({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. 包名" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["packageName"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 项目报告
      // ════════════════════════════════════════════════════════════
      {
        name: "project_report",
        description: "生成项目综合报告：模块统计、Clean Architecture 分层、依赖分类、代码指标、质量建议。format=markdown 输出可读文档，=json 输出结构化数据。includePerformance=true 时附带性能指标（需传 packageName）。返回 JSON/Markdown。耗时 medium (~5-15s)。示例：project_report({ projectPath: '.', format: 'markdown' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "可选。工程根路径", default: "." },
            includePerformance: { type: "boolean", description: "可选。是否包含性能数据", default: false },
            packageName: { type: "string", description: "可选。性能数据对应包名" },
            format: { type: "string", enum: ["markdown", "json"], description: "可选。输出格式", default: "markdown" },
          },
        },
      },

      // ════════════════════════════════════════════════════════════
      // 文件操作
      // ════════════════════════════════════════════════════════════
      {
        name: "push_file",
        description: "推送本地文件到设备。返回 JSON: {success, message}。耗时 depends on file size。示例：push_file({ localPath: './data.json', remotePath: '/sdcard/data.json' })",
        inputSchema: {
          type: "object",
          properties: {
            localPath: { type: "string", description: "Required. 本地文件路径" },
            remotePath: { type: "string", description: "Required. 设备目标路径" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["localPath", "remotePath"],
        },
      },
      {
        name: "pull_file",
        description: "从设备拉取文件到本地。返回 JSON: {success, message, localPath}。耗时 depends on file size。示例：pull_file({ remotePath: '/sdcard/data.json', localPath: './data.json' })",
        inputSchema: {
          type: "object",
          properties: {
            remotePath: { type: "string", description: "Required. 设备文件路径" },
            localPath: { type: "string", description: "Required. 本地保存路径" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["remotePath", "localPath"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 网络调试
      // ════════════════════════════════════════════════════════════
      {
        name: "network_state",
        description: "查询设备网络状态：WiFi、移动数据、飞行模式。返回 JSON: {success, wifi, mobile, airplaneMode}。耗时 fast (~500ms, 单次 shell 调用查 3 项)。示例：network_state({})",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "可选。设备序列号" },
          },
        },
      },
      {
        name: "set_network",
        description: "切换网络状态。type=wifi 走 svc wifi；type=mobile 走 svc data；type=airplane 走 settings。改用 shell_command：若需要更精细控制。返回 JSON: {success, message}。耗时 fast (~1s)。示例：set_network({ type: 'airplane', enabled: true })",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", enum: ["wifi", "mobile", "airplane"], description: "Required. 网络类型" },
            enabled: { type: "boolean", description: "Required. 启用或禁用" },
            serial: { type: "string", description: "可选。设备序列号" },
          },
          required: ["type", "enabled"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // APK 元数据（新）
      // ════════════════════════════════════════════════════════════
      {
        name: "apk_metadata",
        description: "解析 APK 的元数据：包名、版本号、minSdk/targetSdk、权限、Activity 列表、native code、签名信息。自动检测 aapt2 / aapt / apkanalyzer（按 Android SDK 工具链查找）。使用时机：用户问「这个 APK 是什么」「它要什么权限」「它能装到 Android 几上」。返回 JSON: {success, tool, packageName, versionName, versionCode, minSdk, targetSdk, permissions, launchableActivity, ...}。耗时 medium (~2-5s)。示例：apk_metadata({ apkPath: './app-debug.apk' })",
        inputSchema: {
          type: "object",
          properties: {
            apkPath: { type: "string", description: "Required. APK 文件路径" },
          },
          required: ["apkPath"],
        },
      },

      // ════════════════════════════════════════════════════════════
      // 设计稿 → 结构化规范（视觉 LLM 转换）
      // ════════════════════════════════════════════════════════════
      {
        name: "list_design_files",
        description: "列出 design 目录下的所有设计稿（默认 ./design/，可传 dir= 覆盖），自动识别 .png/.jpg/.jpeg/.webp，附带文件名→页名映射。用于：开始实现新页面前先看有哪些设计稿可参考。改用 extract_design_spec：拿到具体文件路径后调用。返回 JSON: {success, dir, count, files: [{name, path, sizeKB, pageHint}]}。耗时 fast (~10ms, 纯文件系统)。示例：list_design_files({}) 或 list_design_files({ dir: './design/v2' })",
        inputSchema: {
          type: "object",
          properties: {
            dir: { type: "string", description: "可选。设计稿目录，默认 ./design" },
          },
        },
      },
      {
        name: "extract_design_spec",
        description: "【核心工具】用 Minimax 视觉 LLM 把设计稿截图转换为 Agent 可读的结构化规范（JSON 模式）：含 colorTokens（直接喂给 Compose ColorScheme）、typography（字号/字重/颜色）、layout sections（Scaffold 的 topBar/TabRow/BottomNav 划分）、components 列表（每张卡片的 kind/bounds/text/source/hasImage）、bottomNav、textContent、interactions。format=both 同时返回人类可读的 markdown。改用 extract_design_tokens：只关心颜色。改用 extract_design_components：只关心组件坐标。改用 design_to_compose：想要直接可用的代码。返回 JSON（format=json|markdown|both）。耗时 slow (vision API 主导)。需 MINIMAX_API_KEY。model 可选：MiniMax-M3（默认，thinking-disabled，最快）、MiniMax-M2.7-highspeed（100 TPS）、MiniMax-M2.7（60 TPS）。示例：extract_design_spec({ imagePath: '首页-推荐.jpg' }) 或 { imagePath: '设计/新版首页.png', format: 'json', model: 'MiniMax-M3' }",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. 设计稿路径（绝对路径、相对路径、纯文件名均可，文件名时会自动在 ./design/ 下查找）" },
            format: { type: "string", enum: ["json", "markdown", "both"], description: "可选。输出格式：json=结构化数据（喂给代码）、markdown=人类阅读、both=同时返回", default: "both" },
            pageHint: { type: "string", description: "可选。页面名提示（默认从文件名推断），帮助 LLM 更好理解上下文" },
            model: { type: "string", description: "可选。视觉模型 ID（默认 MiniMax-M3，thinking-disabled）。其他可选：MiniMax-M2.7-highspeed（100 TPS）、MiniMax-M2.7（60 TPS）" },
          },
          required: ["imagePath"],
        },
      },
      {
        name: "extract_design_tokens",
        description: "只抽取设计稿中的颜色 token（hex + 用途 + 大致占比）。输出可直接生成 Compose ColorScheme：primary/onPrimary/background/surface/onSurface/onSurfaceVariant/outline/accent/error 等 5-10 个最显著颜色。改用 extract_design_spec：若还需要字体/布局/组件。返回 JSON: {success, source, model, tokens: {tokenName: {hex, usage, pixelPct}}}。耗时 slow。需 MINIMAX_API_KEY。model 可选：MiniMax-M3（推荐）、MiniMax-M2.7-highspeed。示例：extract_design_tokens({ imagePath: '首页-推荐.jpg' })",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. 设计稿路径" },
            model: { type: "string", description: "可选。视觉模型 ID（默认 MiniMax-M3）" },
          },
          required: ["imagePath"],
        },
      },
      {
        name: "extract_design_components",
        description: "按从上到下顺序抽取设计稿里所有 UI 组件（卡/按钮/图标/标签/Tab/BottomNavItem），含 kind、bounds、title、text、source、time、hasImage。坐标用截图的像素空间。改用 extract_design_spec：若还要颜色字体布局。改用 dump_hierarchy：若想看当前实现的元素（而不是设计稿）。返回 JSON: {success, source, model, components: [{id, kind, bounds, title, text, source, time, hasImage}]}。耗时 slow。需 MINIMAX_API_KEY。示例：extract_design_components({ imagePath: '首页-推荐.jpg' })",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. 设计稿路径" },
            pageHint: { type: "string", description: "可选。页面名提示" },
            model: { type: "string", description: "可选。视觉模型 ID（默认 MiniMax-M3）" },
          },
          required: ["imagePath"],
        },
      },
      {
        name: "design_to_compose",
        description: "设计稿直接转 Jetpack Compose Screen.kt 骨架（Scaffold + TopAppBar + TabRow + BottomNavigationBar + LazyColumn + 卡片占位）。文末用「/* === TODO NOTES === */」注释列出未实现部分。Agent 拿到后可直接落到 /app/src/main/java/<package>/presentation/<page>/ 下继续开发。改用 extract_design_spec：若要结构化数据（用于自己写代码）。返回 JSON: {success, source, model, packageName, fileName, kotlin, notes, usage}。耗时 slow。需 MINIMAX_API_KEY。推荐用 MiniMax-M3（代码生成质量高、thinking-disabled 后快）。示例：design_to_compose({ imagePath: '首页-推荐.jpg', packageName: 'com.example.toutiao', model: 'MiniMax-M3' })",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. 设计稿路径" },
            packageName: { type: "string", description: "可选。包名（用于建议落盘路径），默认 com.example.app" },
            model: { type: "string", description: "可选。视觉模型 ID（默认 MiniMax-M3）" },
          },
          required: ["imagePath"],
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
      case "screenshot_region":
        return handleScreenshotRegion(args as Record<string, unknown>);
      case "tap":
        return handleTap(args as Record<string, unknown>);
      case "swipe":
        return handleSwipe(args as Record<string, unknown>);
      case "input_text":
        return handleInputText(args as Record<string, unknown>);
      case "press_key":
        return handlePressKey(args as Record<string, unknown>);

      // UI 层级
      case "dump_hierarchy":
        return handleDumpHierarchy(args as Record<string, unknown>);
      case "find_element":
        return handleFindElement(args as Record<string, unknown>);
      case "wait_for_element":
        return handleWaitForElement(args as Record<string, unknown>);

      // 构建与部署
      case "build":
        return handleBuild(args as Record<string, unknown>);
      case "install_and_launch":
        return handleInstallAndLaunch(args as Record<string, unknown>);
      case "build_deploy":
        return handleBuildDeploy(args as Record<string, unknown>, "full_deploy");

      // UI 验证与分析
      case "verify_ui":
        return handleVerifyUI(args as Record<string, unknown>);
      case "analyze_screenshot":
        return handleAnalyzeScreenshot(args as Record<string, unknown>);
      case "compare_screenshots":
        return handleCompareScreenshots(args as Record<string, unknown>);
      case "vision_action":
        return handleVisionAction(args as Record<string, unknown>);

      // 日志与调试
      case "get_logs":
        return handleGetLogs(args as Record<string, unknown>);
      case "logcat_search":
        return handleLogcatSearch(args as Record<string, unknown>);
      case "parse_crash":
        return handleParseCrash(args as Record<string, unknown>);
      case "clear_logs":
        return handleClearLogs(args as Record<string, unknown>);

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
      case "measure_app_launch":
        return handleMeasureAppLaunch(args as Record<string, unknown>);
      case "record_screen":
        return handleDeviceManagement(args as Record<string, unknown>, "record_screen");

      // 设备控制
      case "set_orientation":
        return handleSetOrientation(args as Record<string, unknown>);
      case "set_gps":
        return handleSetGps(args as Record<string, unknown>);
      case "animation_scale":
        return handleAnimationScale(args as Record<string, unknown>);

      // 代码质量
      case "code_quality":
        return handleCodeQuality(args as Record<string, unknown>);
      case "run_tests":
        return handleBuildDeploy(args as Record<string, unknown>, "run_tests");

      // UI 自动化测试
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

      // 网络调试
      case "network_state":
        return handleNetworkDebug(args as Record<string, unknown>, "get_state");
      case "set_network":
        return handleNetworkDebug(args as Record<string, unknown>, "set_state");

      // APK 元数据
      case "apk_metadata":
        return handleApkMetadata(args as Record<string, unknown>);

      // 设计稿（视觉 LLM 转换）
      case "list_design_files":
        return handleListDesignFiles(args as Record<string, unknown>);
      case "extract_design_spec":
        return handleExtractDesignSpec(args as Record<string, unknown>);
      case "extract_design_tokens":
        return handleExtractDesignTokens(args as Record<string, unknown>);
      case "extract_design_components":
        return handleExtractComponents(args as Record<string, unknown>);
      case "design_to_compose":
        return handleDesignToCompose(args as Record<string, unknown>);

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
  log("AndroidDev-Assist MCP Server v3.1.0 running on stdio");
  log("Capabilities: 49 tools across 16 categories: screenshot/interaction/hierarchy/build/deploy/verify/vision/logs/device/apps/performance/control/quality/test/report/design");

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
