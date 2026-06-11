import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout, fileExists } from "../utils/exec.js";
import { screenshot } from "../utils/adb.js";
import { getUIHierarchy } from "../utils/ui-test.js";
import {
  callVisionLlm,
  encodeImageAsDataUrl,
  smartResizeForVision,
  getActiveProvider,
} from "../utils/design-extractor.js";
import { getScreenSize } from "./vision-action.js";
import fs from "node:fs";
import path from "node:path";

// ════════════════════════════════════════════════════════════════════════
// pm_explore 用到的 MCP handlers —— 完全复用现有工具，不重复实现
// ════════════════════════════════════════════════════════════════════════

import { handleTap, handleSwipe, handleInputText, handlePressKey, handleBuild, handleInstallAndLaunch } from "./interaction.js";
import { handleDumpHierarchy, handleFindElement, handleWaitForElement } from "./hierarchy.js";
import { handleDeviceManagement } from "./device-management.js";
import { handleAppManagement } from "./app-management.js";
import { handleGetLogs, handleClearLogs } from "./logs.js";
import { handlePerformanceMonitor } from "./performance-monitor.js";
import { handleMeasureAppLaunch } from "./launch-speed.js";
import { handleVisionAction } from "./vision-action.js";
import { handleAnalyzeScreenshot } from "./analyze.js";
import { handleCompareScreenshots } from "./compare.js";
import { handleVerifyUI } from "./verify.js";
import { handleScreenshot } from "./screenshot.js";
import { handleScreenshotRegion } from "./screenshot-region.js";
import { handleSetOrientation, handleSetGps, handleAnimationScale } from "./device-control.js";
import { handleNetworkDebug } from "./network-debug.js";

/** MCP handler 标准返回类型 */
type HandlerResult = { content: Array<{ type: string; text: string }>; isError?: boolean };
type Handler = (args: Record<string, unknown>) => Promise<HandlerResult>;

/**
 * PM 可调用的 MCP 工具 registry —— 完全复用现有 handler，不重复实现。
 * 任何 tool 没在表里 → dispatcher 报"未知工具"，LLM 下一轮会换一个。
 *
 * 不暴露的：
 *   - pm_* 自身（防递归）
 *   - regression_test / ui_test（太高层）
 *   - apk_metadata（开发者向）
 *   - 设计稿类（PM 不该自动调）
 *   - code_quality / run_tests / project_report（开发者向）
 */
const PM_TOOL_REGISTRY: Record<string, Handler> = {
  // 设备操作
  tap: handleTap,
  swipe: handleSwipe,
  input_text: handleInputText,
  press_key: handlePressKey,

  // 应用生命周期
  // 项目专属默认：build 必须在含 gradlew 的目录跑，install_and_launch 必须带 packageName+activity
  // （handler 自身没有合理的兜底，所以 PM registry 这层补上）
  build: (args) => handleBuild({ ...args, projectPath: args.projectPath || _findProjectRoot() }),
  install_and_launch: (args) => handleInstallAndLaunch({
    packageName: "com.example.toutiao",
    activity: "MainActivity",
    ...args,
  }),
  stop_app: (args) => handleAppManagement({ packageName: "com.example.toutiao", ...args }, "stop_app"),
  clear_app_data: (args) => handleAppManagement({ packageName: "com.example.toutiao", ...args }, "clear_app_data"),
  list_apps: (args) => handleAppManagement(args, "list_apps"),
  app_info: (args) => handleAppManagement({ packageName: "com.example.toutiao", ...args }, "app_info"),

  // 诊断
  get_logs: handleGetLogs,
  shell_command: (args) => handleDeviceManagement(args, "shell_command"),
  performance_metrics: handlePerformanceMonitor,
  measure_app_launch: handleMeasureAppLaunch,
  clear_logs: handleClearLogs,

  // 视觉智能
  vision_action: handleVisionAction,
  find_element: handleFindElement,
  wait_for_element: handleWaitForElement,
  analyze_screenshot: handleAnalyzeScreenshot,
  compare_screenshots: handleCompareScreenshots,
  verify_ui: handleVerifyUI,
  dump_hierarchy: handleDumpHierarchy,
  dump_ui: handleDumpUi,

  // 截图/录屏
  screenshot: handleScreenshot,
  screenshot_region: handleScreenshotRegion,
  record_screen: (args) => handleDeviceManagement(args, "record_screen"),

  // 设备控制
  set_orientation: handleSetOrientation,
  set_gps: handleSetGps,
  set_animation_scale: handleAnimationScale,
  set_network: (args) => handleNetworkDebug(args, "set_state"),
};

/** 影响 UI 的工具（连续 2 步 UI 不变 → 卡住） */
const INTERACTIVE_TOOLS = new Set([
  "tap", "swipe", "input_text", "press_key", "vision_action", "wait_for_element",
]);

/** 不改 UI 的工具（screenshot / dump / set_* / build 等） */
const SETTLING_TOOLS = new Set([
  "build", "install_and_launch", "stop_app", "clear_app_data",
  "list_apps", "app_info", "get_logs", "performance_metrics", "measure_app_launch",
  "shell_command", "clear_logs",
  "find_element", "analyze_screenshot", "compare_screenshots", "verify_ui",
  "dump_hierarchy", "dump_ui", "screenshot", "screenshot_region", "record_screen",
  "set_orientation", "set_gps", "set_animation_scale", "set_network",
]);

// ────────────────────────────────────────────────────────────────────────
// 配置：项目根目录寻址（pm_checklist / prompts / pm_reviews 都在这层）
// ────────────────────────────────────────────────────────────────────────

const REVIEW_DIR = process.env.PM_REVIEW_DIR || "./pm_reviews";
const CHECKLIST_PATH = process.env.PM_CHECKLIST_PATH || "./pm_checklist_toutiao.md";
const PROMPT_TEMPLATE_PATH = process.env.PM_PROMPT_PATH || "./skills/prompts/pm_review.txt";
const PROMPT_EXPLORE_PATH = process.env.PM_EXPLORE_PROMPT_PATH || "./skills/prompts/pm_explore_step.txt";

const DEFAULT_FOCUS = [
  "ui_bug: 看起来对、其实有视觉/交互问题的地方",
  "ux: 用户实际使用时可能卡壳的地方",
  "performance: 性能、流畅度",
];

// ────────────────────────────────────────────────────────────────────────
// 内部工具：截图、加载 checklist、加载并填充 prompt、调用 VLM
// ────────────────────────────────────────────────────────────────────────

async function _takeScreenshot(savePath?: string): Promise<string> {
  const out = savePath || `/tmp/pm_screenshot_${Date.now()}.png`;
  await screenshot(out);
  return out;
}

function _loadChecklist(): string {
  const candidates = [
    CHECKLIST_PATH,
    path.resolve(process.cwd(), CHECKLIST_PATH),
    path.resolve(process.cwd(), "..", CHECKLIST_PATH),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return fs.readFileSync(p, "utf-8");
  }
  return "(checklist 不可用，按通用 Android 最佳实践审查)";
}

function _loadPromptTemplate(): string {
  const candidates = [
    PROMPT_TEMPLATE_PATH,
    path.resolve(process.cwd(), PROMPT_TEMPLATE_PATH),
    path.resolve(process.cwd(), "..", PROMPT_TEMPLATE_PATH),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return fs.readFileSync(p, "utf-8");
  }
  throw new Error(`PM 提示词模板不存在: ${PROMPT_TEMPLATE_PATH}`);
}

function _fillPromptTemplate(
  tpl: string,
  vars: { target: string; focus: string; checklist: string; uiDumpSummary: string },
): string {
  return tpl
    .replace("${target}", vars.target)
    .replace("${focus_or_default}", vars.focus)
    .replace("${checklist}", vars.checklist)
    .replace("${ui_dump_summary}", vars.uiDumpSummary);
}

function _summaryFromDump(elements: Array<{ text?: string; class?: string; clickable?: boolean; bounds?: { x: number; y: number; width: number; height: number } }>): string {
  const texts = elements.map((e) => e.text).filter((t): t is string => Boolean(t));
  const uniqueTexts = Array.from(new Set(texts));
  const clickableCount = elements.filter((e) => e.clickable).length;
  const top = uniqueTexts.slice(0, 30).join(" | ");
  return [
    `节点总数: ${elements.length}`,
    `可点击节点数: ${clickableCount}`,
    `可见文本（前 30 条去重）: ${top || "(无)"}`,
  ].join("\n");
}

/**
 * 容错地从 vision 输出提取 JSON —— 去掉 ``` 围栏、<think> 块、截取第一个 { 到最后一个 }
 * 设计/PM 工具共同用，避免 vlm 返回 markdown 围栏 / thinking 块 / 尾随逗号
 *
 * 边界：
 *   - think 块被截断（只有 `<think>` 没有 `</think>`）→ 视为 think 占满全文，直接从第一个 `{` 之后开始
 *   - JSON 被截断（没有闭合 `}`）→ 也会走到 fallback，保留 _raw 供排查
 */
function _parseJsonFromVision(raw: string): Record<string, unknown> {
  let text = raw.trim();
  // 1) 完整 think 块：去掉
  text = text.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  // 1b) 残留半截 think：去掉 (剩下的内容)
  if (text.startsWith("<think>")) {
    const firstBrace = text.indexOf("{");
    if (firstBrace > 0) text = text.slice(firstBrace);
  }
  // 2) 去掉 ```json / ``` 围栏
  const fenced = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (fenced) text = fenced[1].trim();
  // 3) 截取第一个 { 到最后一个 }
  const a = text.indexOf("{");
  const b = text.lastIndexOf("}");
  if (a !== -1 && b > a) text = text.slice(a, b + 1);
  // 4) 第一次直接 parse
  try {
    return JSON.parse(text) as Record<string, unknown>;
  } catch {
    // 5) 修复常见错误：尾随逗号、未引用的 key
    const fixed = text
      .replace(/,(\s*[}\]])/g, "$1")
      .replace(/([{,]\s*)([A-Za-z_][\w$]*)(\s*:)/g, '$1"$2"$3');
    try {
      return JSON.parse(fixed) as Record<string, unknown>;
    } catch {
      return { _parseError: true, _raw: raw.slice(0, 2000) };
    }
  }
}

async function _callVision(imagePath: string, systemPrompt: string, userPrompt: string, maxTokens = 4000): Promise<string> {
  const cfg = getActiveProvider();
  const modelId = process.env.VISION_MODEL || cfg.defaultModel;
  const resizedPath = await smartResizeForVision(imagePath);
  try {
    const imageUrl = encodeImageAsDataUrl(resizedPath);
    return await callVisionLlmWithTokens(modelId, systemPrompt, userPrompt, imageUrl, maxTokens);
  } finally {
    if (resizedPath !== imagePath) {
      try { fs.unlinkSync(resizedPath); } catch { /* ignore */ }
    }
  }
}

/**
 * callVisionLlm 的 max_tokens 变体（pm_review 需要更大窗口避免 thinking 块被截断）
 */
async function callVisionLlmWithTokens(
  modelId: string,
  systemPrompt: string,
  userPrompt: string,
  imageUrl: string,
  maxTokens: number,
): Promise<string> {
  const { activeProvider, makeInsecureFetch } = await import("../utils/design-extractor.js");
  const { default: OpenAI } = await import("openai");
  const cfg = activeProvider();
  const model = modelId || cfg.defaultModel;
  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) throw new Error(`${cfg.apiKeyEnv} not set`);
  // 自签名证书兼容（同 design-extractor 主路径）
  const clientOpts: any = { apiKey, baseURL: cfg.baseURL };
  if (cfg.insecureTLS) {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    clientOpts.fetch = makeInsecureFetch();
  }
  const client = new OpenAI(clientOpts);
  const requestOpts: any = {
    model,
    messages: [
      { role: "system" as const, content: systemPrompt },
      {
        role: "user" as const,
        content: [
          { type: "image_url" as const, image_url: { url: imageUrl } },
          { type: "text" as const, text: userPrompt },
        ],
      },
    ],
    max_tokens: maxTokens,
    temperature: 1.0,
  };
  if (model === "MiniMax-M3") {
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }
  const response = await client.chat.completions.create(requestOpts, { timeout: 120000 });
  const msg = response.choices[0]?.message as any;
  let content = msg?.content;
  if (!content && msg?.reasoning_content) content = msg.reasoning_content;
  if (!content) throw new Error("Vision model returned empty response");
  return content;
}

function _serializeDumpNode(el: { type: string; text?: string; resourceId?: string; bounds: { x: number; y: number; width: number; height: number }; clickable: boolean; contentDesc?: string; enabled?: boolean }) {
  return {
    class: el.type,
    text: el.text,
    resource_id: el.resourceId,
    content_desc: el.contentDesc,
    bounds: `[${el.bounds.x},${el.bounds.y}][${el.bounds.x + el.bounds.width},${el.bounds.y + el.bounds.height}]`,
    clickable: el.clickable,
    enabled: el.enabled ?? true,
  };
}

async function _dumpUiInternal(savePath?: string): Promise<{ nodes: ReturnType<typeof _serializeDumpNode>[]; rawPath: string }> {
  // uiautomator dump + 落到 /tmp（保留原文便于排查）
  const localPath = savePath || `/tmp/ui_dump_${Date.now()}.xml`;

  let xml = "";
  try {
    // 一次往返：dump 到 stdout（exec-out 走 /dev/tty）
    const { stdout } = await execAsyncWithTimeout(
      'adb exec-out "uiautomator dump /dev/tty"',
      { timeout: 15000 },
    );
    xml = stdout;
  } catch {
    // 退化：写到 /sdcard 再 cat
    await execAsyncWithTimeout(
      'adb shell "uiautomator dump /sdcard/window_dump.xml"',
      { timeout: 15000 },
    );
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "cat /sdcard/window_dump.xml"',
      { timeout: 10000 },
    );
    xml = stdout;
  }

  fs.writeFileSync(localPath, xml, "utf-8");

  // 复用现有解析器拿到结构化节点，再补 content-desc / enabled / full class
  const baseNodes = await getUIHierarchy();
  const enriched = baseNodes.map((el) => ({
    ...el,
    contentDesc: undefined as string | undefined,
    enabled: true as boolean | undefined,
  }));

  // 第二轮：直接从 xml 再补 content-desc / enabled / 全类名
  const nodeRegex = /<node\b([^>]*?)\/?>/g;
  const extras = new Map<string, { contentDesc?: string; enabled?: boolean; fullClass?: string }>();
  let m: RegExpExecArray | null;
  while ((m = nodeRegex.exec(xml)) !== null) {
    const attrs = m[1];
    const textMatch = attrs.match(/text="([^"]*)"/);
    const text = textMatch ? textMatch[1] : "";
    const contentDesc = (attrs.match(/content-desc="([^"]*)"/)?.[1]) || undefined;
    const enabledMatch = attrs.match(/enabled="([^"]*)"/);
    const enabled = enabledMatch ? enabledMatch[1] === "true" : undefined;
    const classMatch = attrs.match(/class="([^"]*)"/)?.[1];
    if (text) extras.set(text, { contentDesc, enabled, fullClass: classMatch });
  }

  for (const el of enriched) {
    const ex = el.text ? extras.get(el.text) : undefined;
    el.contentDesc = ex?.contentDesc;
    el.enabled = ex?.enabled;
    if (ex?.fullClass) el.type = ex.fullClass;
  }

  return { nodes: enriched.map(_serializeDumpNode), rawPath: localPath };
}

// ────────────────────────────────────────────────────────────────────────
// P0-1: dump_ui
// ────────────────────────────────────────────────────────────────────────

export async function handleDumpUi(args: Record<string, unknown>) {
  try {
    const savePath = args.savePath as string | undefined;
    const { nodes, rawPath } = await _dumpUiInternal(savePath);
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          dump_path: rawPath,
          node_count: nodes.length,
          nodes,
          hint: "Use find_element to locate a specific node by text/resource-id. Pass dump_path to PM tools for full UI context.",
        }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("dump_ui failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P0-2: pm_review（核心）
// ────────────────────────────────────────────────────────────────────────

export async function handlePmReview(args: Record<string, unknown>) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const target = (args.target as string) || "首页列表";
    const focus = (args.focus as string[] | undefined) || DEFAULT_FOCUS;
    const screenshotPath = (args.screenshotPath as string | undefined) || undefined;
    logTrace({ type: "tool_call", tool: "pm_review", args: { target, focus }, session_id: sessionId });

    // Step 1: 收集证据
    const shot = screenshotPath || await _takeScreenshot();
    const { nodes: dumpNodes } = await _dumpUiInternal();

    // Step 2: 加载 prompt + checklist
    const tpl = _loadPromptTemplate();
    const checklist = _loadChecklist();

    const filled = _fillPromptTemplate(tpl, {
      target,
      focus: focus.join("\n- "),
      checklist,
      uiDumpSummary: _summaryFromDump(dumpNodes as never),
    });

    // Step 3: 调 VLM（要求返回 JSON）
    // 用最强约束的系统提示词避免 LLM 输出 markdown / 思考块
    const systemPrompt = "你是 Android 产品经理。严格按用户给出的 JSON Schema 输出：\n" +
      "1) 唯一输出：一个 JSON 对象，从 { 开始到 } 结束\n" +
      "2) 禁止：```json``` 围栏、<think> 块、任何 markdown、任何解释性文字、任何前后缀\n" +
      "3) thinking_process 字段是 JSON 内的字符串值，可以包含换行，但要作为字符串字面量输出";
    // pm_review 给 8000 tokens，避免长 thinking 块 + 完整 JSON 撞 token 上限
    const raw = await _callVision(shot, systemPrompt, filled, 8000);
    const parsed = _parseJsonFromVision(raw);

    // Step 4: 持久化（视频演示用）
    const reviewId = `rev-${Date.now()}`;
    fs.mkdirSync(REVIEW_DIR, { recursive: true });
    const review = {
      timestamp: new Date().toISOString(),
      target,
      focus,
      screenshot: shot,
      ui_dump_summary: {
        node_count: dumpNodes.length,
        texts: dumpNodes.map((n) => n.text).filter(Boolean).slice(0, 50),
      },
      ...parsed,
      review_id: reviewId,
    };
    const reviewFile = path.join(REVIEW_DIR, `${reviewId}.json`);
    fs.writeFileSync(reviewFile, JSON.stringify(review, null, 2), "utf-8");

    const { review_id: _ignoredReviewId, ...reviewForReturn } = review;

    // 更新 Memory
    const memory = _loadPmMemory();
    _updateMemoryFromReview(memory, {
      review_id: reviewId,
      timestamp: review.timestamp,
      tool: "pm_review",
      target,
      overall_rating: String(review.overall_rating || "C"),
      issues: (Array.isArray(review.issues) ? review.issues : []).map((issue: any, idx: number) => ({
        issue_id: issue.id || _generateIssueId(memory),
        severity: issue.severity || "medium",
        category: issue.category || "ui_bug",
        description: `${issue.location || ""}: ${issue.current_state || ""} → ${issue.expected_state || ""}`,
        location: issue.location || "",
        design_ref: issue.design_ref || "",
        status: "open",
      })),
      positives: Array.isArray(review.positives) ? review.positives : [],
    });

    logTrace({ type: "done", tool: "pm_review", overall_rating: String(review.overall_rating || "C"), issues_found: Array.isArray(review.issues) ? review.issues.length : 0, elapsed_ms: Date.now() - Date.parse(review.timestamp), session_id: sessionId });
    logTrace({ type: "memory_update", action: "append_review", review_id: reviewId, session_id: sessionId });

    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          review_id: reviewId,
          review_file: reviewFile,
          ...reviewForReturn,
        }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_review failed:", err);
    logTrace({ type: "error", tool: "pm_review", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P2: pm_compare_with_design（pixelmatch + LLM）
// ────────────────────────────────────────────────────────────────────────

interface PixelDiff {
  mismatched: number;
  total: number;
  ratio: number;
  diffImagePath: string | null;
}

async function _pixelDiff(designPath: string, implPath: string): Promise<PixelDiff> {
  const sharp = (await import("sharp")).default;
  const { default: pixelmatch } = await import("pixelmatch");
  const { PNG } = await import("pngjs");

  const [dBuf, iBuf] = await Promise.all([
    sharp(designPath).raw().toBuffer({ resolveWithObject: true }),
    sharp(implPath).raw().toBuffer({ resolveWithObject: true }),
  ]);

  // 尺寸对齐
  const w = Math.min(dBuf.info.width, iBuf.info.width);
  const h = Math.min(dBuf.info.height, iBuf.info.height);
  const dCrop = await sharp(designPath).resize(w, h).raw().toBuffer();
  const iCrop = await sharp(implPath).resize(w, h).raw().toBuffer();
  const channels = Math.min(dBuf.info.channels, iBuf.info.channels);

  const diff = new PNG({ width: w, height: h });
  const mismatched = pixelmatch(
    Buffer.from(dCrop),
    Buffer.from(iCrop),
    diff.data,
    w, h,
    { threshold: 0.1 },
  );

  const diffImagePath = `/tmp/pm_diff_${Date.now()}.png`;
  // sharp 期望 channels 是字面量类型，用 4（最常见的 RGBA）兜底
  const outChannels = (channels === 3 || channels === 4 ? channels : 4) as 3 | 4;
  await sharp(Buffer.from(diff.data), { raw: { width: w, height: h, channels: outChannels } })
    .png()
    .toFile(diffImagePath);

  return {
    mismatched,
    total: w * h,
    ratio: mismatched / (w * h),
    diffImagePath,
  };
}

export async function handlePmCompareWithDesign(args: Record<string, unknown>) {
  try {
    const designPath = args.designPath as string;
    const implScreenshotPath = (args.implScreenshotPath as string | undefined) || undefined;

    if (!designPath) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "designPath required" }) }] };
    }
    if (!(await fileExists(designPath))) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: `Design not found: ${designPath}` }) }] };
    }

    const impl = implScreenshotPath || await _takeScreenshot();

    // 1) 像素 diff
    let diff: PixelDiff | { error: string } = { mismatched: 0, total: 0, ratio: 0, diffImagePath: null };
    try {
      diff = await _pixelDiff(designPath, impl);
    } catch (e: unknown) {
      diff = { error: (e as Error).message };
    }

    // 2) LLM 分析（用实现截图 + 像素 diff 比例驱动提示词）
    const ratioPct = "ratio" in diff ? (diff.ratio * 100).toFixed(1) : "?";
    const systemPrompt = "你是一位资深 Android 产品经理，擅长把设计稿和实现进行对比，指出可接受的差异和需要修复的差异。";
    const userPrompt = `设计稿: ${designPath}
当前实现截图: ${impl}
像素 diff 比例: ${ratioPct}%（用 pixelmatch 计算，threshold=0.1）

请分析：
1. 哪些差异是 critical（影响功能或视觉一致性）— 列出 2-5 条
2. 哪些差异是 acceptable（实现合理、可不改）— 简短列出
3. 给出修复优先级（先改哪个、后改哪个）

输出 JSON：
{
  "critical_issues": [{"location": "...", "diff": "...", "fix_priority": 1}],
  "acceptable_diffs": ["..."],
  "fix_order": ["critical_issues[0]", "critical_issues[1]"],
  "summary": "一句话"
}`;
    const raw = await _callVision(impl, systemPrompt, userPrompt);
    const llm = _parseJsonFromVision(raw);

    return {
      content: [{
        type: "text",
        text: JSON.stringify({ success: true, design: designPath, impl, pixel_diff: diff, llm_analysis: llm }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_compare_with_design failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P2: pm_mark_fixed（修复追踪）
// ────────────────────────────────────────────────────────────────────────

const PM_STATE_PATH = process.env.PM_STATE_PATH || "./.pm_state.json";
const TRACE_PATH = process.env.PM_TRACE_PATH || "./pm_trace.jsonl";
const PM_MEMORY_PATH = process.env.PM_MEMORY_PATH || "./.pm_memory.json";
const PM_DISCUSSION_PATH = process.env.PM_DISCUSSION_PATH || "./.pm_discussions.json";
const MAX_DISCUSSION_HISTORY = 10;

interface PmState {
  fixed: Array<{ issue_id: string; note?: string; fixed_at: string; review_id?: string }>;
  ignored: Array<{ issue_id: string; note?: string; ignored_at: string }>;
}

function _loadPmState(): PmState {
  if (!fs.existsSync(PM_STATE_PATH)) return { fixed: [], ignored: [] };
  try {
    return JSON.parse(fs.readFileSync(PM_STATE_PATH, "utf-8")) as PmState;
  } catch {
    return { fixed: [], ignored: [] };
  }
}

function _savePmState(state: PmState): void {
  fs.writeFileSync(PM_STATE_PATH, JSON.stringify(state, null, 2), "utf-8");
}

// ────────────────────────────────────────────────────────────────────────
// Trace 系统（旁路日志，供 Monitor CLI 实时读取）
// ────────────────────────────────────────────────────────────────────────

type TraceEventType = "tool_call" | "step" | "vlm_think" | "done" | "memory_update" | "error" | "discuss" | "check";

interface TraceEvent {
  type: TraceEventType;
  tool?: string;
  args?: Record<string, unknown>;
  step?: number;
  total_steps?: number;
  action?: string;
  detail?: string;
  overall_rating?: string;
  issues_found?: number;
  elapsed_ms?: number;
  session_id?: string;
  [key: string]: unknown;
}

function logTrace(event: TraceEvent): void {
  try {
    const line = JSON.stringify({ ...event, ts: new Date().toISOString() });
    fs.appendFileSync(TRACE_PATH, line + "\n");
  } catch {
    // Trace 是旁路，失败不阻塞主流程
  }
}

// ────────────────────────────────────────────────────────────────────────
// Memory 系统（项目级持久化记忆）
// ────────────────────────────────────────────────────────────────────────

interface PmMemoryIssue {
  issue_id: string;
  severity: "high" | "medium" | "low";
  category: string;
  description: string;
  location?: string;
  design_ref?: string;
  status: "open" | "fixed" | "ignored";
  fixed_at?: string;
  verified_by?: string;
  verified_at?: string;
}

interface PmMemoryReview {
  review_id: string;
  timestamp: string;
  tool: string;
  target: string;
  channel?: string;
  overall_rating: string;
  issues: PmMemoryIssue[];
  positives: Array<{ item: string; evidence?: string }>;
}

interface PmMemory {
  project: {
    name: string;
    package_name: string;
    main_activity: string;
    version: string;
  };
  design_specs: {
    sources: string[];
    tokens: Record<string, string>;
  };
  reviews: PmMemoryReview[];
  issue_counter: number;
  current_focus: {
    channel: string;
    page: string;
    last_review_id: string | null;
  };
}

function _loadPmMemory(): PmMemory {
  if (!fs.existsSync(PM_MEMORY_PATH)) {
    return {
      project: { name: "ToutiaoFeedDemo", package_name: "com.example.toutiao", main_activity: "MainActivity", version: "1.0.0" },
      design_specs: { sources: [], tokens: {} },
      reviews: [],
      issue_counter: 0,
      current_focus: { channel: "recommend", page: "首页推荐", last_review_id: null },
    };
  }
  try {
    return JSON.parse(fs.readFileSync(PM_MEMORY_PATH, "utf-8")) as PmMemory;
  } catch {
    return {
      project: { name: "ToutiaoFeedDemo", package_name: "com.example.toutiao", main_activity: "MainActivity", version: "1.0.0" },
      design_specs: { sources: [], tokens: {} },
      reviews: [],
      issue_counter: 0,
      current_focus: { channel: "recommend", page: "首页推荐", last_review_id: null },
    };
  }
}

function _savePmMemory(memory: PmMemory): void {
  fs.writeFileSync(PM_MEMORY_PATH, JSON.stringify(memory, null, 2), "utf-8");
}

function _generateIssueId(memory: PmMemory): string {
  memory.issue_counter += 1;
  return `ISSUE-${String(memory.issue_counter).padStart(3, "0")}`;
}

function _updateMemoryFromReview(memory: PmMemory, review: PmMemoryReview): void {
  memory.reviews.push(review);
  memory.current_focus.last_review_id = review.review_id;
  memory.current_focus.page = review.target;
  if (review.channel) memory.current_focus.channel = review.channel;
  for (const issue of review.issues) {
    if (!issue.status) issue.status = "open";
  }
  _savePmMemory(memory);
}

// ────────────────────────────────────────────────────────────────────────
// Discussion History 管理
// ────────────────────────────────────────────────────────────────────────

interface DiscussionMessage {
  role: "claude" | "pm" | "pm_tool";
  content?: string;
  tool?: string;
  result_summary?: string;
  time: string;
}

interface DiscussionSession {
  session_id: string;
  started_at: string;
  context: {
    current_file?: string;
    current_channel?: string;
  };
  messages: DiscussionMessage[];
}

interface DiscussionHistory {
  sessions: DiscussionSession[];
}

function _loadDiscussionHistory(): DiscussionHistory {
  if (!fs.existsSync(PM_DISCUSSION_PATH)) return { sessions: [] };
  try {
    return JSON.parse(fs.readFileSync(PM_DISCUSSION_PATH, "utf-8")) as DiscussionHistory;
  } catch {
    return { sessions: [] };
  }
}

function _saveDiscussionHistory(history: DiscussionHistory): void {
  fs.writeFileSync(PM_DISCUSSION_PATH, JSON.stringify(history, null, 2), "utf-8");
}

function _appendDiscussion(question: string, answer: string, tool?: string, resultSummary?: string): void {
  const history = _loadDiscussionHistory();
  let session = history.sessions[history.sessions.length - 1];
  if (!session) {
    session = {
      session_id: `sess-${Date.now()}`,
      started_at: new Date().toISOString(),
      context: {},
      messages: [],
    };
    history.sessions.push(session);
  }
  const now = new Date().toISOString();
  if (tool) {
    session.messages.push({ role: "pm_tool", tool, result_summary: resultSummary || "", time: now });
  }
  session.messages.push({ role: "claude", content: question, time: now });
  session.messages.push({ role: "pm", content: answer, time: now });
  // 限制单 session 消息数，避免文件膨胀
  if (session.messages.length > MAX_DISCUSSION_HISTORY * 2 + 4) {
    session.messages = session.messages.slice(-MAX_DISCUSSION_HISTORY * 2);
  }
  _saveDiscussionHistory(history);
}

function _formatDiscussionHistory(limit = MAX_DISCUSSION_HISTORY): string {
  const history = _loadDiscussionHistory();
  const session = history.sessions[history.sessions.length - 1];
  if (!session || session.messages.length === 0) return "(无历史对话)";
  const recent = session.messages.slice(-limit * 2);
  return recent.map((m) => {
    const time = m.time ? new Date(m.time).toLocaleTimeString("zh-CN") : "";
    if (m.role === "pm_tool") return `[${time}] Tool: ${m.tool} → ${m.result_summary}`;
    if (m.role === "claude") return `[${time}] Claude: ${m.content?.slice(0, 100) || ""}`;
    return `[${time}] PM: ${m.content?.slice(0, 200) || ""}`;
  }).join("\n");
}

function _formatMemorySummary(memory: PmMemory): string {
  const openIssues = memory.reviews.flatMap((r) => r.issues).filter((i) => i.status === "open");
  const fixedIssues = memory.reviews.flatMap((r) => r.issues).filter((i) => i.status === "fixed");
  const lines = [
    `项目: ${memory.project.name} (${memory.project.package_name})`,
    `设计稿: ${memory.design_specs.sources.length} 张`,
    `审查记录: ${memory.reviews.length} 次`,
    `Open Issues: ${openIssues.length} 个`,
    `Fixed Issues: ${fixedIssues.length} 个`,
    `当前焦点: ${memory.current_focus.page} (${memory.current_focus.channel})`,
  ];
  if (openIssues.length > 0) {
    lines.push("未修复问题:");
    for (const issue of openIssues.slice(0, 5)) {
      lines.push(`  - ${issue.issue_id} [${issue.severity}] ${issue.description}`);
    }
  }
  if (Object.keys(memory.design_specs.tokens).length > 0) {
    lines.push("设计 Token:");
    for (const [k, v] of Object.entries(memory.design_specs.tokens).slice(0, 5)) {
      lines.push(`  - ${k}: ${v}`);
    }
  }
  return lines.join("\n");
}

// ────────────────────────────────────────────────────────────────────────
// 文本 LLM 调用（pm_discuss 用，无视觉输入）
// ────────────────────────────────────────────────────────────────────────

async function _callTextLlm(systemPrompt: string, userPrompt: string, maxTokens = 4000): Promise<string> {
  const { getActiveProvider, makeInsecureFetch } = await import("../utils/design-extractor.js");
  const { default: OpenAI } = await import("openai");
  const cfg = getActiveProvider();
  const model = process.env.TEXT_MODEL || "MiniMax-M2.7";
  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) throw new Error(`${cfg.apiKeyEnv} not set`);

  const clientOpts: any = { apiKey, baseURL: cfg.baseURL };
  if (cfg.insecureTLS) {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    clientOpts.fetch = makeInsecureFetch();
  }
  const client = new OpenAI(clientOpts);
  const requestOpts: any = {
    model,
    messages: [
      { role: "system" as const, content: systemPrompt },
      { role: "user" as const, content: userPrompt },
    ],
    max_tokens: maxTokens,
    temperature: 0.7,
  };
  if (model === "MiniMax-M3") {
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }
  const response = await client.chat.completions.create(requestOpts, { timeout: 60000 });
  const msg = response.choices[0]?.message as any;
  let content = msg?.content;
  if (!content && msg?.reasoning_content) content = msg.reasoning_content;
  if (!content) throw new Error("Text model returned empty response");
  return content;
}

function _scanDesignFiles(): string[] {
  const designDir = path.resolve(_findProjectRoot(), "design");
  if (!fs.existsSync(designDir)) return [];
  return fs.readdirSync(designDir)
    .filter((f) => f.endsWith(".jpg") || f.endsWith(".png") || f.endsWith(".jpeg") || f.endsWith(".webp"))
    .map((f) => path.join("design", f));
}

export async function handlePmMarkFixed(args: Record<string, unknown>) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const issueId = args.issueId as string;
    logTrace({ type: "tool_call", tool: "pm_mark_fixed", args: { issueId }, session_id: sessionId });
    const note = (args.note as string | undefined) || "";
    const action = (args.action as string | undefined) || "fixed";

    if (!issueId) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "issueId required" }) }] };
    }

    const state = _loadPmState();
    if (action === "fixed") {
      state.fixed.push({ issue_id: issueId, note, fixed_at: new Date().toISOString() });
    } else if (action === "ignored") {
      state.ignored.push({ issue_id: issueId, note, ignored_at: new Date().toISOString() });
    } else if (action === "reopen") {
      // 把 issue 从 fixed 列表里挪回待修复
      state.fixed = state.fixed.filter((f) => f.issue_id !== issueId);
    } else {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: `Unknown action: ${action}` }) }] };
    }
    _savePmState(state);

    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          action,
          issue_id: issueId,
          state_file: PM_STATE_PATH,
          fixed_count: state.fixed.length,
          ignored_count: state.ignored.length,
          open_issues_hint: "下次 pm_review 时仍会重新发现全部 issue；pm_mark_fixed 主要用于状态记录与视频演示追踪",
        }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_mark_fixed failed:", err);
    logTrace({ type: "error", tool: "pm_mark_fixed", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P1: pm_explore（agentic PM — 通过 MCP handler registry 调度现有工具）
// ────────────────────────────────────────────────────────────────────────
//
// 架构（v2）：
//   - PM_TOOL_REGISTRY 把 30+ MCP 工具暴露给 LLM
//   - 每步 LLM 输出 {tool, args, note}
//   - _dispatch 调对应 handler，截断结果回灌给下一步
//   - 完全不重复实现设备操作 —— 跟 interaction.ts / hierarchy.ts 等保持单一来源
//
// 与 pm_review 的区别：
//   - pm_review：单次截图 + 单次 LLM 调用 → 一次产出 issues
//   - pm_explore：多步循环，每步 PM 自主调 MCP 工具，dump 出 issues
//
// 设计要点：
//   - 每步自动：screenshot + dump_ui + 调 VLM
//   - VLM 输出 1 个 {tool, args, note} JSON
//   - done 是特殊 tool（args 里就是 issues/positives）
//   - stale 检测：连续 2 步 INTERACTIVE 工具 UI 文本未变 → 卡住，强制 done
//   - maxSteps 上限 12 防止 runaway

interface ToolCall {
  tool: string;
  args: Record<string, unknown>;
  note: string;
  // done 专用字段
  overall_rating?: string;
  summary?: string;
  thinking_process?: string;
  issues?: Array<Record<string, unknown>>;
  positives?: Array<Record<string, unknown>>;
}

function _clampNum(v: unknown, max: number): number | undefined {
  const n = Number(v);
  if (isNaN(n)) return undefined;
  return Math.max(0, Math.min(max, Math.round(n)));
}

/** 对坐标类工具做 OOB clamp + Y 坐标 guardrail（避免 tap 到 status bar 或 bottom nav） */
function _clampCoordinates(tool: string, args: Record<string, unknown>, screen: { width: number; height: number }): Record<string, unknown> {
  // Y 区域（按 1080×2400 屏幕估算）：
  //   0-150:    系统状态栏 + 红色品牌 header —— 禁止
  //   150-220:  顶部 Tab 栏 (关注/推荐/热榜/深圳/小说) —— Tap Tab 中心 y≈185
  //   220-280:  搜索提示 + 浮窗区
  //   280-2150: 内容区 (新闻卡 / 作者卡) —— Tap 卡片中心
  //   2150-2280: BottomNav (首页/视频/赚钱/商城/我的) —— Tap BottomNav 中心 y≈2210
  //   2280-2400: 系统导航条
  const minY = 150;
  const maxY = 2280;
  const clampY = (v: unknown) => {
    const n = _clampNum(v, screen.height);
    return n === undefined ? undefined : Math.max(minY, Math.min(maxY, n));
  };
  if (tool === "tap") {
    return { ...args, x: _clampNum(args.x, screen.width), y: clampY(args.y) };
  }
  if (tool === "swipe") {
    return {
      ...args,
      x1: _clampNum(args.x1, screen.width), y1: clampY(args.y1),
      x2: _clampNum(args.x2, screen.width), y2: clampY(args.y2),
    };
  }
  if (tool === "screenshot_region" || tool === "verify_ui") {
    return { ...args, x: _clampNum(args.x, screen.width), y: _clampNum(args.y, screen.height) };
  }
  return args;
}

/**
 * 检测当前 dump 文本是否是 Android launcher 而非 Toutiao app
 * 启发式：launcher 包含 Play Store / Gmail / Photos / YouTube + Google 搜索栏
 */
function _isLauncherState(texts: string): boolean {
  const launcherMarkers = ["Play Store", "Gmail", "Photos", "YouTube", "Phone", "Messages", "Chrome", "Google"];
  const toutiaoMarkers = ["ToutiaoFeedDemo", "热搜", "Tab", "推荐", "关注", "首页", "video", "商城"];
  const hasLauncher = launcherMarkers.filter((m) => texts.includes(m)).length >= 3;
  const hasToutiao = toutiaoMarkers.filter((m) => texts.includes(m)).length >= 2;
  return hasLauncher && !hasToutiao;
}

/** 容错地解析 LLM 输出 {tool, args, note} —— 剥 think 块/围栏/修尾逗号 */
function _parseToolCall(raw: string): ToolCall {
  let text = raw.trim();
  text = text.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  const fenced = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (fenced) text = fenced[1].trim();
  const a = text.indexOf("{");
  const b = text.lastIndexOf("}");
  if (a !== -1 && b > a) text = text.slice(a, b + 1);

  let parsed: Record<string, unknown>;
  try { parsed = JSON.parse(text); } catch {
    const fixed = text
      .replace(/,(\s*[}\]])/g, "$1")
      .replace(/([{,]\s*)([A-Za-z_][\w$]*)(\s*:)/g, '$1"$2"$3');
    try { parsed = JSON.parse(fixed); } catch {
      // 解析失败 → fallback 到 wait，PM 下一轮能重试
      return { tool: "tap", args: {}, note: "JSON 解析失败（fallback）" };
    }
  }

  const tool = String(parsed.tool || "").trim();
  const args = (parsed.args && typeof parsed.args === "object" && !Array.isArray(parsed.args))
    ? parsed.args as Record<string, unknown>
    : {};
  const note = String(parsed.note || "");

  if (!tool) {
    return { tool: "tap", args: {}, note: "缺少 tool 字段" };
  }

  const r: ToolCall = { tool, args, note };
  // done 专用：把 issues/positives 等结构化字段也拿出来
  if (tool === "done") {
    r.overall_rating = String(parsed.overall_rating || args.overall_rating || "C");
    r.summary = String(parsed.summary || args.summary || "");
    r.thinking_process = String(parsed.thinking_process || args.thinking_process || "");
    r.issues = Array.isArray(parsed.issues) ? parsed.issues as Array<Record<string, unknown>>
      : Array.isArray(args.issues) ? args.issues as Array<Record<string, unknown>> : [];
    r.positives = Array.isArray(parsed.positives) ? parsed.positives as Array<Record<string, unknown>>
      : Array.isArray(args.positives) ? args.positives as Array<Record<string, unknown>> : [];
  }
  return r;
}

/**
 * 通用 dispatcher：调 PM_TOOL_REGISTRY 里的 handler，截断结果
 * 返回 ok / info / error，让 PM 下一轮能看到上一轮工具结果
 */
async function _dispatch(tool: string, args: Record<string, unknown>, screen: { width: number; height: number }): Promise<{ ok: boolean; info?: string; error?: string }> {
  const handler = PM_TOOL_REGISTRY[tool];
  if (!handler) {
    const known = Object.keys(PM_TOOL_REGISTRY).sort().join(", ");
    return { ok: false, error: `未知工具 "${tool}"。可用: ${known}` };
  }
  try {
    const clamped = _clampCoordinates(tool, args, screen);
    const res = await handler(clamped);
    if ("isError" in res && res.isError) {
      const text = res.content[0]?.text || "(handler 错误但无文本)";
      return { ok: false, error: text.slice(0, 500) };
    }
    const text = res.content[0]?.text || "(无输出)";
    return { ok: true, info: text.slice(0, 1500) };
  } catch (e) {
    return { ok: false, error: (e as Error).message.slice(0, 500) };
  }
}

function _loadExplorePromptTemplate(): string {
  const candidates = [
    PROMPT_EXPLORE_PATH,
    path.resolve(process.cwd(), PROMPT_EXPLORE_PATH),
    path.resolve(process.cwd(), "..", PROMPT_EXPLORE_PATH),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return fs.readFileSync(p, "utf-8");
  }
  throw new Error(`pm_explore prompt template not found: ${PROMPT_EXPLORE_PATH}`);
}

/** 探测项目根（cwd 向上找含 settings.gradle.kts 的目录） */
let _cachedProjectRoot: string | null = null;
function _findProjectRoot(): string {
  if (_cachedProjectRoot) return _cachedProjectRoot;
  let dir = process.cwd();
  for (let i = 0; i < 6; i++) {
    if (fs.existsSync(path.join(dir, "settings.gradle.kts")) ||
        fs.existsSync(path.join(dir, "settings.gradle"))) {
      _cachedProjectRoot = dir;
      return dir;
    }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  // 兜底：cwd 的上级（MCP server 跑在 skills/，项目在上级）
  _cachedProjectRoot = path.resolve(process.cwd(), "..");
  return _cachedProjectRoot;
}

export async function handlePmExplore(args: Record<string, unknown>) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const goal = (args.goal as string) || "审查当前页面的可用性和设计";
    const maxSteps = Math.min(Math.max((args.maxSteps as number | undefined) ?? 6, 1), 12);
    logTrace({ type: "tool_call", tool: "pm_explore", args: { goal, maxSteps }, session_id: sessionId });

    // 准备工作
    const exploreId = `explore-${Date.now()}`;
    const traceDir = path.join(REVIEW_DIR, exploreId);
    fs.mkdirSync(traceDir, { recursive: true });
    const tpl = _loadExplorePromptTemplate();
    const screen = await getScreenSize();
    const checklist = _loadChecklist();
    const systemPrompt =
      "你是 Android 产品经理，正在自主审查 APP。每步严格按 JSON Schema 输出 1 个工具调用：\n" +
      "1) 唯一输出：一个 JSON 对象，从 { 开始到 } 结束\n" +
      "2) 工具名必须严格匹配可用列表（registry 里有的）\n" +
      "3) 禁止：```json``` 围栏、<think> 块、任何 markdown、任何解释性文字\n" +
      "4) 字符串里的双引号必须用 \\\" 转义\n" +
      "5) done 是特殊工具：args 字段含 overall_rating/summary/thinking_process/issues/positives\n\n" +
      "PM 审查标准库（参考）：\n" + checklist;

    const history: string[] = [];
    const trace: Array<Record<string, unknown>> = [];
    let finalResult: Record<string, unknown> | null = null;
    let stuckCount = 0;
    let lastDumpText = "";
    let lastResult = "(无)";
    let lastToolWasSettling = true;  // 第 1 步没有前序
    let effectiveInteractiveSteps = 0;  // 真正改变了 UI 的交互步数
    let tStart = Date.now();

    for (let step = 1; step <= maxSteps; step++) {
      log(`pm_explore — step ${step}/${maxSteps} (goal: ${goal})`);

      // 1) 截图 + dump_ui (auto)
      const shotPath = path.join(traceDir, `step-${step}.png`);
      const shot = await _takeScreenshot(shotPath);
      const { nodes: dumpNodes } = await _dumpUiInternal();
      const texts = dumpNodes.map((n) => n.text).filter(Boolean).join(" | ");
      const dumpSummary = texts.length > 0 ? texts.slice(0, 400) : "(页面无文本节点)";

      // 修复 TDZ bug：原本 logTrace 在 dumpNodes 声明之前就引用 dumpNodes.length
      // 必须放到 _dumpUiInternal() 之后
      logTrace({ type: "step", step, total_steps: maxSteps, action: "screenshot", detail: `dump=${dumpNodes.length} nodes`, session_id: sessionId });

      // ⚠️ 自救协议：检测到 launcher 状态时自动注入强提示
      if (_isLauncherState(texts)) {
        const hint = "⚠️ 当前屏幕是 Android launcher（Play Store / Gmail 等），不是 Toutiao app！必须先调 `install_and_launch({})` 才能继续";
        lastResult = hint;
        log(`pm_explore — detected launcher state at step ${step}, forcing hint`);
      }

      // 2) 调 VLM 决定下一步
      const filled = tpl
        .replace("${goal}", goal)
        .replace("${history}", history.length > 0 ? history.join("\n") : "(无，已是第 1 步)")
        .replace("${last_result}", lastResult)
        .replace("${ui_dump_summary}", dumpSummary)
        .replace("${screen_width}", String(screen.width))
        .replace("${screen_height}", String(screen.height));
      const tVlm = Date.now();
      const raw = await _callVision(shot, systemPrompt, filled, 4000);
      log(`pm_explore — step ${step} VLM ${Date.now() - tVlm}ms`);

      // 修复 TDZ bug：原本 logTrace 在 call 声明之前就引用 call.note/call.tool
      // 必须放到 _parseToolCall() 之后
      const call = _parseToolCall(raw);
      logTrace({ type: "vlm_think", step, thought: call.note || call.tool, model: process.env.VISION_MODEL || "MiniMax-M3", latency_ms: Date.now() - tVlm, session_id: sessionId });

      // 3) stale 检测：只有 INTERACTIVE 工具的 UI 不变才算卡住
      const isInteractive = INTERACTIVE_TOOLS.has(call.tool);
      if (texts === lastDumpText) {
        if (SETTLING_TOOLS.has(call.tool)) {
          stuckCount = 0;
        } else if (lastToolWasSettling) {
          // 上一轮是 settling（install 后），这一步是交互但 UI 还没变 → app 启动慢，再等一步
          stuckCount = 1;
        } else {
          stuckCount++;
        }
      } else {
        stuckCount = 0;
        if (isInteractive) effectiveInteractiveSteps++;
      }
      lastDumpText = texts;
      if (stuckCount >= 2) {
        log(`pm_explore — stuck detected (UI unchanged ${stuckCount} interactive steps), force done`);
        finalResult = {
          _stuck: true,
          _reason: "连续 2 步交互工具 UI 文本未变，PM 卡住了",
          overall_rating: "C",
          summary: "PM 自主探索时卡住（连续 2 步 UI 无变化），可能目标元素不存在、坐标不对或屏幕已锁死",
          thinking_process: "stale 检测触发，强制 done",
          issues: [],
          positives: [],
        };
        break;
      }

      // 3.5) done 最低门槛：至少 3 步交互 + 至少见过 launcher 或 app 切换 → 防止瞎 done
      if (call.tool === "done" && effectiveInteractiveSteps < 3) {
        lastResult = `⚠️ done 太早：才走了 ${effectiveInteractiveSteps} 步有效交互（至少需要 3 步）。请继续：先 install_and_launch，再切 Tab/点卡等`;
        log(`pm_explore — blocked premature done at step ${step} (only ${effectiveInteractiveSteps} interactive steps)`);
        trace.push({
          step,
          tool: call.tool,
          args: call.args,
          note: "BLOCKED: premature done",
          screenshot: shot,
          ui_dump: dumpSummary,
          timestamp: new Date().toISOString(),
        });
        // 不写 history，不算 done，继续循环
        continue;
      }

      // 4) 记录到历史和 trace
      const histLine = call.tool === "done"
        ? `[step ${step}] done — ${call.summary?.slice(0, 50) || ""}`
        : `[step ${step}] ${call.tool}(${JSON.stringify(call.args).slice(0, 80)}) — ${call.note || ""}`;
      history.unshift(histLine);
      if (history.length > 12) history.length = 12;

      trace.push({
        step,
        tool: call.tool,
        args: call.args,
        note: call.note,
        screenshot: shot,
        ui_dump: dumpSummary,
        vlm_response: raw.slice(0, 300),
        timestamp: new Date().toISOString(),
      });

      // 5) done 是退出信号
      if (call.tool === "done") {
        finalResult = {
          overall_rating: call.overall_rating || "C",
          summary: call.summary || "",
          thinking_process: call.thinking_process || "",
          issues: call.issues || [],
          positives: call.positives || [],
        };
        break;
      }

      // 6) 调 PM_TOOL_REGISTRY 里的 handler
      const result = await _dispatch(call.tool, call.args, screen);
      if (!result.ok) {
        log(`pm_explore — step ${step} tool ${call.tool} failed: ${result.error}`);
        trace[trace.length - 1].execution_error = result.error;
        lastResult = `❌ ${call.tool} 失败: ${result.error}`;
      } else {
        trace[trace.length - 1].execution_info = result.info;
        lastResult = `✓ ${call.tool} → ${result.info || "(空)"}`;
      }
      lastToolWasSettling = SETTLING_TOOLS.has(call.tool);

      // 7) 等动画/网络（build/install_and_launch/stop/clear_app_data 后等更久）
      const settleMs = new Set(["build", "install_and_launch", "stop_app", "clear_app_data", "set_orientation"]).has(call.tool)
        ? 2500 : 800;
      await new Promise((r) => setTimeout(r, settleMs));
    }

    // maxSteps 用完还没 done — 兜底
    if (!finalResult) {
      finalResult = {
        _maxStepsReached: true,
        overall_rating: "C",
        summary: `PM 自主探索达到 maxSteps=${maxSteps} 上限，未输出 done。基于已观察到的 ${trace.length} 步动作，建议人工跟进。`,
        thinking_process: history.join(" | "),
        issues: [],
        positives: [],
      };
    }

    // 更新 Memory
    if (finalResult && Array.isArray(finalResult.issues)) {
      const memory = _loadPmMemory();
      _updateMemoryFromReview(memory, {
        review_id: exploreId,
        timestamp: new Date().toISOString(),
        tool: "pm_explore",
        target: goal,
        overall_rating: String(finalResult.overall_rating || "C"),
        issues: (finalResult.issues as Array<Record<string, unknown>>).map((issue: any, idx: number) => ({
          issue_id: issue.id || _generateIssueId(memory),
          severity: issue.severity || "medium",
          category: issue.category || "ui_bug",
          description: `${issue.location || ""}: ${issue.current_state || ""} → ${issue.expected_state || ""}`,
          location: issue.location || "",
          design_ref: issue.design_ref || "",
          status: "open",
        })),
        positives: Array.isArray(finalResult.positives) ? finalResult.positives as Array<Record<string, unknown>> : [],
      });
      logTrace({ type: "memory_update", action: "append_review", review_id: exploreId, session_id: sessionId });
    }

    // 持久化
    const traceFile = path.join(REVIEW_DIR, `${exploreId}.json`);
    const fullTrace = {
      explore_id: exploreId,
      timestamp: new Date().toISOString(),
      goal,
      max_steps: maxSteps,
      steps_taken: trace.length,
      elapsed_ms: Date.now() - tStart,
      history,
      trace,
      trace_dir: traceDir,
      final_result: finalResult,
    };
    fs.writeFileSync(traceFile, JSON.stringify(fullTrace, null, 2), "utf-8");

    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          explore_id: exploreId,
          trace_file: traceFile,
          trace_dir: traceDir,
          steps_taken: trace.length,
          elapsed_ms: Date.now() - tStart,
          history,
          ...finalResult,
        }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_explore failed:", err);
    logTrace({ type: "error", tool: "pm_explore", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P3: pm_discuss（PM 讨论 — 基于记忆的对话接口）
// ────────────────────────────────────────────────────────────────────────

export async function handlePmDiscuss(args: Record<string, unknown>) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const question = (args.question as string) || "";
    const context = (args.context as string) || "";
    const includeHistory = (args.include_history as boolean | undefined) !== false;
    const includeScreenshot = (args.include_screenshot as boolean | undefined) === true;

    if (!question) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "question required" }) }] };
    }

    logTrace({ type: "discuss", tool: "pm_discuss", args: { question: question.slice(0, 100), context: context.slice(0, 100), includeHistory, includeScreenshot }, session_id: sessionId });

    // 1. 加载记忆
    const memory = _loadPmMemory();
    const memorySummary = _formatMemorySummary(memory);

    // 2. 加载对话历史
    const discussionHistory = includeHistory ? _formatDiscussionHistory() : "(历史已忽略)";

    // 3. 可选截图
    let screenshotPath: string | undefined;
    if (includeScreenshot) {
      screenshotPath = await _takeScreenshot();
    }

    // 4. 加载 prompt 模板
    const tplPath = path.resolve(process.cwd(), "./skills/prompts/pm_discuss.txt");
    let tpl = fs.existsSync(tplPath) ? fs.readFileSync(tplPath, "utf-8") : "";
    if (!tpl) {
      tpl = `你是 ToutiaoFeedDemo 的 AI 产品经理。基于项目记忆和对话历史回答产品问题。`;
    }

    const filled = tpl
      .replace("${pm_memory_summary}", memorySummary)
      .replace("${discussion_history}", discussionHistory)
      .replace("${context}", context || "(未提供)");

    // 5. 调用 LLM
    const systemPrompt = "你是 Android 产品经理。回答简洁、具体、可执行。不确定时说\"项目记忆中没有相关信息\"。";
    let answer: string;

    if (includeScreenshot && screenshotPath) {
      // 视觉模式：复用 _callVision
      const userPrompt = filled + "\n\n用户问题：" + question + "\n（用户要求 PM 基于当前截图回答）";
      answer = await _callVision(screenshotPath, systemPrompt, userPrompt, 4000);
    } else {
      answer = await _callTextLlm(systemPrompt, filled + "\n\n用户问题：" + question, 4000);
    }

    // 6. 追加到对话历史
    _appendDiscussion(question, answer, "pm_discuss", "回答完成");

    logTrace({ type: "discuss", tool: "pm_discuss", detail: "completed", session_id: sessionId });

    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          answer,
          memory_summary: memorySummary.slice(0, 500),
          include_screenshot: includeScreenshot,
        }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_discuss failed:", err);
    logTrace({ type: "error", tool: "pm_discuss", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P4: pm_check（修复验证 — VLM 对比截图判断 issue 是否已修复）
// ────────────────────────────────────────────────────────────────────────

export async function handlePmCheck(args: Record<string, unknown>) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const issueId = args.issue_id as string;
    const target = (args.target as string) || "";
    const autoMarkFixed = (args.auto_mark_fixed as boolean | undefined) !== false;

    if (!issueId) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "issue_id required" }) }] };
    }

    logTrace({ type: "check", tool: "pm_check", args: { issue_id: issueId, target, autoMarkFixed }, session_id: sessionId });

    // 1. 从 memory 查找 issue
    const memory = _loadPmMemory();
    const allIssues = memory.reviews.flatMap((r) => r.issues);
    const issue = allIssues.find((i) => i.issue_id === issueId);
    if (!issue) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: `Issue not found in memory: ${issueId}` }) }] };
    }

    // 2. 截图
    const shot = await _takeScreenshot();

    // 3. 加载 prompt 模板
    const tplPath = path.resolve(process.cwd(), "./skills/prompts/pm_check.txt");
    let tpl = fs.existsSync(tplPath) ? fs.readFileSync(tplPath, "utf-8") : "";
    if (!tpl) {
      tpl = "判断以下 issue 是否已修复。输出 JSON: {fixed: boolean, confidence: high/medium/low, note: string}";
    }

    const filled = tpl.replace("${issue_json}", JSON.stringify(issue, null, 2));
    const systemPrompt = "你是 Android 产品经理，正在验证 issue 修复状态。严格按 JSON 格式输出。";
    const raw = await _callVision(shot, systemPrompt, filled, 2000);
    const parsed = _parseJsonFromVision(raw);

    const fixed = parsed.fixed === true;
    const confidence = String(parsed.confidence || "low");
    const note = String(parsed.note || "");
    const remainingConcerns = String(parsed.remaining_concerns || "");

    // 4. 自动标记 fixed
    if (fixed && autoMarkFixed) {
      issue.status = "fixed";
      issue.verified_by = "pm_check";
      issue.verified_at = new Date().toISOString();
      _savePmMemory(memory);

      // 同时更新旧版 .pm_state.json（兼容）
      const state = _loadPmState();
      if (!state.fixed.find((f) => f.issue_id === issueId)) {
        state.fixed.push({ issue_id: issueId, note: `Verified by pm_check: ${note}`, fixed_at: new Date().toISOString() });
        _savePmState(state);
      }

      logTrace({ type: "memory_update", action: "mark_fixed", detail: issueId, session_id: sessionId });
    }

    logTrace({ type: "check", tool: "pm_check", detail: fixed ? "fixed" : "not_fixed", session_id: sessionId });

    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          issue_id: issueId,
          fixed,
          confidence,
          note,
          remaining_concerns: remainingConcerns,
          auto_marked: fixed && autoMarkFixed,
        }, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_check failed:", err);
    logTrace({ type: "error", tool: "pm_check", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ────────────────────────────────────────────────────────────────────────
// P5: pm_get_memory（记忆查询 — 按 scope 返回结构化记忆）
// ────────────────────────────────────────────────────────────────────────

export async function handlePmGetMemory(args: Record<string, unknown>) {
  try {
    const scope = (args.scope as string) || "overview";
    const channel = (args.channel as string) || "";

    const memory = _loadPmMemory();
    const allIssues = memory.reviews.flatMap((r) => r.issues);
    const filteredIssues = channel ? allIssues.filter((i) => {
      const review = memory.reviews.find((r) => r.issues.includes(i));
      return review?.channel === channel;
    }) : allIssues;

    let result: Record<string, unknown> = { success: true, scope };

    switch (scope) {
      case "overview": {
        result = {
          ...result,
          project: memory.project,
          review_count: memory.reviews.length,
          open_issues: filteredIssues.filter((i) => i.status === "open").length,
          fixed_issues: filteredIssues.filter((i) => i.status === "fixed").length,
          ignored_issues: filteredIssues.filter((i) => i.status === "ignored").length,
          current_focus: memory.current_focus,
          design_files: memory.design_specs.sources.length,
        };
        break;
      }
      case "open_issues": {
        result = {
          ...result,
          issues: filteredIssues.filter((i) => i.status === "open"),
          count: filteredIssues.filter((i) => i.status === "open").length,
        };
        break;
      }
      case "fixed_issues": {
        result = {
          ...result,
          issues: filteredIssues.filter((i) => i.status === "fixed"),
          count: filteredIssues.filter((i) => i.status === "fixed").length,
        };
        break;
      }
      case "design_specs": {
        result = {
          ...result,
          sources: memory.design_specs.sources,
          tokens: memory.design_specs.tokens,
        };
        break;
      }
      case "last_review": {
        const last = memory.reviews[memory.reviews.length - 1];
        result = { ...result, review: last || null };
        break;
      }
      case "discussions": {
        const history = _loadDiscussionHistory();
        result = { ...result, sessions: history.sessions.length, last_session: history.sessions[history.sessions.length - 1] || null };
        break;
      }
      default: {
        result = { ...result, hint: `Unknown scope: ${scope}. Available: overview, open_issues, fixed_issues, design_specs, last_review, discussions` };
      }
    }

    return {
      content: [{
        type: "text",
        text: JSON.stringify(result, null, 2),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("pm_get_memory failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
