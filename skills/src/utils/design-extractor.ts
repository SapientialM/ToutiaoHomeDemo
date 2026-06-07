import OpenAI from "openai";
import https from "node:https";
import { URL } from "node:url";
import fs from "fs";
import path from "path";
import { statSync } from "node:fs";
import { log, error } from "./logger.js";
import { execAsyncWithTimeout } from "./exec.js";

// ── LLM 提供商配置 ─────────────────────────────────────

export type LLMProvider = "kimi" | "minimax";

interface ProviderConfig {
  provider: LLMProvider;
  baseURL: string;
  apiKeyEnv: string;
  defaultModel: string;
  availableModels: { id: string; tps: number; thinking: boolean; description: string }[];
  insecureEnvVar: string; // 自签名证书环境变量名（运行时读取）
  insecureTLS: boolean;  // 运行时计算
}

const PROVIDERS: Record<LLMProvider, Omit<ProviderConfig, "insecureTLS">> = {
  kimi: {
    provider: "kimi",
    baseURL: "https://api.moonshot.cn/v1",
    apiKeyEnv: "MOONSHOT_API_KEY",
    defaultModel: "kimi-k2.6",
    availableModels: [
      { id: "kimi-k2.6", tps: 0, thinking: true, description: "Reasoning model, 慢但精准，输出 13K+ reasoning tokens" },
    ],
    insecureEnvVar: "MOONSHOT_INSECURE_TLS",
  },
  minimax: {
    provider: "minimax",
    baseURL: "https://api.minimaxi.com/v1",
    apiKeyEnv: "MINIMAX_API_KEY",
    defaultModel: "MiniMax-M3",
    availableModels: [
      { id: "MiniMax-M3", tps: 60, thinking: true, description: "最新 M 系列，1M 上下文，可关 thinking 加速" },
      { id: "MiniMax-M2.7", tps: 60, thinking: true, description: "M2.7，开启 self-iteration，thinking 不能关" },
      { id: "MiniMax-M2.7-highspeed", tps: 100, thinking: true, description: "M2.7 极速版（100 TPS），thinking 不能关" },
      { id: "MiniMax-M2.5-highspeed", tps: 100, thinking: true, description: "M2.5 极速版（100 TPS）" },
    ],
    insecureEnvVar: "MINIMAX_INSECURE_TLS",
  },
};

function activeProvider(): ProviderConfig {
  const p = (process.env.VISION_PROVIDER as LLMProvider) || "minimax";
  if (!PROVIDERS[p]) {
    throw new Error(`Unknown VISION_PROVIDER: ${p}. Available: ${Object.keys(PROVIDERS).join(", ")}`);
  }
  // 运行时读 env（dotenv 后置加载）
  return { ...PROVIDERS[p], insecureTLS: process.env[PROVIDERS[p].insecureEnvVar] === "1" };
}

// 自签名证书环境（公司代理/MITM）：
// OpenAI SDK 在构造时锁定 fetch 引用，patch globalThis.fetch 不生效；
// 必须在 new OpenAI({ fetch }) 时显式传入 https.request-based fetch。
function makeInsecureFetch(): typeof fetch {
  return (input: any, init: any = {}) => {
    return new Promise((resolve, reject) => {
      const url = typeof input === "string" ? new URL(input) : new URL((input as Request).url);
      const body = init?.body;
      const headers: Record<string, any> = {};
      if (init?.headers) {
        if (init.headers instanceof Headers) {
          init.headers.forEach((v: string, k: string) => { headers[k] = v; });
        } else if (Array.isArray(init.headers)) {
          for (const [k, v] of init.headers) headers[k] = v;
        } else {
          Object.assign(headers, init.headers);
        }
      }
      headers["host"] = url.host;
      if (body && !headers["content-length"] && typeof body === "string") {
        headers["content-length"] = Buffer.byteLength(body);
      }
      const req = https.request(
        {
          hostname: url.hostname,
          port: url.port || 443,
          path: url.pathname + url.search,
          method: init?.method || "GET",
          headers,
          rejectUnauthorized: false,
        },
        (res) => {
          const chunks: Buffer[] = [];
          res.on("data", (c: Buffer) => chunks.push(c));
          res.on("end", () => {
            const respBody = Buffer.concat(chunks);
            const respHeaders: Record<string, string> = {};
            for (const [k, v] of Object.entries(res.headers)) {
              if (Array.isArray(v)) respHeaders[k] = v.join(", ");
              else if (v != null) respHeaders[k] = String(v);
            }
            resolve(new Response(respBody, {
              status: res.statusCode || 0,
              statusText: res.statusMessage || "",
              headers: respHeaders,
            }));
          });
        }
      );
      req.on("error", reject);
      if (body) {
        if (typeof body === "string" || Buffer.isBuffer(body)) req.write(body);
        else if (body instanceof Uint8Array) req.write(Buffer.from(body));
        else if (typeof body.pipe === "function") body.pipe(req);
        else reject(new Error("Unsupported fetch body type"));
      }
      req.end();
    });
  };
}

let client: OpenAI | null = null;
let clientKey = "";

function getClient(): OpenAI {
  const cfg = activeProvider();
  const cacheKey = `${cfg.provider}|${cfg.apiKeyEnv}|${cfg.insecureTLS}`;

  if (client && clientKey === cacheKey) return client;

  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) {
    throw new Error(`${cfg.apiKeyEnv} not set. Required for provider '${cfg.provider}' (${cfg.baseURL})`);
  }
  if (cfg.insecureTLS) process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  client = new OpenAI({
    apiKey,
    baseURL: cfg.baseURL,
    ...(cfg.insecureTLS ? { fetch: makeInsecureFetch() } : {}),
  });
  clientKey = cacheKey;
  log(`vision client: provider=${cfg.provider} baseURL=${cfg.baseURL} insecureTLS=${cfg.insecureTLS}`);
  return client;
}

const SKIP_RESIZE_THRESHOLD_BYTES = 80 * 1024;
const MAX_DIMENSION_FOR_API = 768; // 设计稿最长边 768：平衡清晰度与传输耗时

async function resizeForApiAsync(imagePath: string): Promise<string> {
  try {
    const size = statSync(imagePath).size;
    if (size < SKIP_RESIZE_THRESHOLD_BYTES) return imagePath;
  } catch {
    return imagePath;
  }

  const resizedPath = imagePath.replace(/\.(png|jpg|jpeg)$/, "_resized.$1");
  try {
    const cmd = `python3 -c "
from PIL import Image
img = Image.open('${imagePath}')
w, h = img.size
if max(w, h) > ${MAX_DIMENSION_FOR_API}:
    ratio = ${MAX_DIMENSION_FOR_API} / max(w, h)
    img = img.resize((int(w*ratio), int(h*ratio)), Image.LANCZOS)
    img.save('${resizedPath}', quality=85, optimize=True)
"`;
    await execAsyncWithTimeout(cmd, { timeout: 10000 });
    return fs.existsSync(resizedPath) ? resizedPath : imagePath;
  } catch {
    return imagePath;
  }
}

function cleanupResizedFile(resizedPath: string, originalPath: string): void {
  if (resizedPath !== originalPath) {
    try { fs.unlinkSync(resizedPath); } catch { /* ignore */ }
  }
}

function encodeAsDataUrl(p: string): string {
  const buf = fs.readFileSync(p);
  const ext = path.extname(p).slice(1) || "png";
  const mime = ext === "jpg" || ext === "jpeg" ? "image/jpeg" : "image/png";
  return `data:${mime};base64,${buf.toString("base64")}`;
}

// ── Prompt 模板 ─────────────────────────────────────

/**
 * 主 prompt：让模型输出结构化设计规范（JSON 模式）
 */
const JSON_SYSTEM_PROMPT = `You are a senior Android UI/UX engineer specializing in Jetpack Compose and Material 3. You analyze mobile app screenshots and produce machine-readable design specifications.

## Output rules
- Output a single JSON object only. No markdown fences, no commentary, no code blocks.
- All color values must be exact hex (e.g. "#FF5757"), never "red" / "blue".
- Coordinates use the screenshot's pixel space. Width and height must reflect the image.
- All visible text (including Chinese) must be transcribed EXACTLY as shown.
- Estimate dp values: assume 1080px wide → 360dp (3x density).
- Identify the page type from the bottom navigation and top bar context.
- For each card / item, identify the visual type and structure.

## JSON schema
{
  "page": {
    "name": "string (e.g. '首页-推荐')",
    "type": "home | video | profile | shop | detail | task | earn | other",
    "dimensions": { "width": number, "height": number },
    "description": "1-2 sentence summary of what this page is for"
  },
  "colorTokens": {
    "primary": "#hex (品牌主色)",
    "onPrimary": "#hex (主色上的文字)",
    "background": "#hex (页面背景)",
    "surface": "#hex (卡片/表面)",
    "onSurface": "#hex (主文字)",
    "onSurfaceVariant": "#hex (次文字)",
    "outline": "#hex (分割线/边框)",
    "error": "#hex",
    "accent": "#hex (高亮/红点/数字)"
  },
  "typography": {
    "titleLarge": { "size": number, "weight": "Bold|Medium|Regular", "color": "#hex", "dp": number },
    "titleMedium": { "size": number, "weight": "Bold|Medium|Regular", "color": "#hex", "dp": number },
    "bodyLarge": { "size": number, "weight": "Bold|Medium|Regular", "color": "#hex", "dp": number },
    "bodyMedium": { "size": number, "weight": "Bold|Medium|Regular", "color": "#hex", "dp": number },
    "labelSmall": { "size": number, "weight": "Bold|Medium|Regular", "color": "#hex", "dp": number }
  },
  "layout": {
    "type": "scaffold",
    "sections": [
      { "id": "top-bar", "kind": "TopAppBar|StatusBar|TabRow|SearchBar|Banner|List|FAB|BottomNav", "bounds": {"x":0,"y":0,"width":number,"height":number}, "background": "#hex" }
    ]
  },
  "components": [
    {
      "id": "card-1",
      "kind": "TextTopCard|LeftTextRightImageCard|LargeImageCard|VideoCard|ImageOnlyCard|AvatarBlock|ActionRow|StatBlock|PromoBanner|CategoryGrid|Comment",
      "bounds": { "x": number, "y": number, "width": number, "height": number },
      "text": { "title": "string", "subtitle": "string?", "source": "string?", "time": "string?", "count": "string?" },
      "hasImage": boolean,
      "imagePosition": "top|bottom|right|left|none",
      "isClickable": true
    }
  ],
  "bottomNav": {
    "items": [
      { "label": "string", "iconHint": "home|video|money|shop|profile", "isSelected": boolean, "color": "#hex" }
    ]
  },
  "textContent": {
    "titles": ["array of visible page/card titles"],
    "labels": ["array of button/tab labels"],
    "placeholders": ["array of input placeholders"],
    "hotKeywords": ["array of search hot words / news keywords if visible"]
  },
  "interactions": [
    "Tap card → navigate to detail page",
    "Swipe down → refresh feed",
    "Tap tab in top bar → switch channel"
  ],
  "notes": "any anomalies, red badges (with text), live indicators, special states"
}

Be exhaustive. Capture EVERY card / text / icon you can see. Do not skip elements.`;

const MARKDOWN_SYSTEM_PROMPT = `你是资深 Android UI 工程师，专注 Jetpack Compose + Material 3。分析移动端 App 截图，输出一份 Markdown 格式的设计规范文档，便于开发者据此实现 UI。

## 输出结构（严格按顺序）
# [页面名]
> 1-2 句话描述这个页面的功能

## 页面元信息
- 类型：home / video / profile / shop / detail / task / earn / other
- 设计稿尺寸：WxH px（对应 W/3 × H/3 dp）
- 整体配色：主色 / 背景 / 卡片 / 主文字 / 次文字

## 颜色 Token
| Token | 颜色 | 用途 |
|-------|------|------|
| primary | #FF5757 | 品牌主色、按钮 |
| ... | ... | ... |

## 字体规范
| 等级 | size | weight | 颜色 | 用途 |
|------|------|--------|------|------|
| titleLarge | 22sp | Bold | #1A1A1A | 页面标题 |
| ... | ... | ... | ... | ... |

## 布局结构
按从上到下顺序描述每个 section：
1. **StatusBar**（状态栏）：高度 24dp，背景 #F5F5F5，显示时间/电量
2. **TopBar**（顶部栏）：高度 56dp，红底白字
3. ...

## 组件列表
按出现顺序（从上到下、从左到右）列出每个组件：
### 组件 1: LeftTextRightImageCard
- 位置：x=0, y=800, 宽=1080, 高=240
- 标题：「xxx」
- 副标题（来源）：「xxx · 2小时前」
- 图片：右侧 200x200 缩略图
- 备注：8dp 边距，圆角 4dp

### 组件 2: ...
（重复直到所有卡片列完）

## 底部导航
| 顺序 | 标签 | 图标 | 选中态颜色 |
|------|------|------|-----------|
| 1 | 首页 | home | #FF5757 |
| ... | ... | ... | ... |

## 文字内容
- 标题：xxx, xxx
- 按钮：xxx, xxx
- 占位符：xxx
- 热搜词：xxx, xxx

## 交互行为
- 点击卡片 → 跳转到详情页
- 下拉 → 刷新列表
- ...

## 备注
- 红色小圆点显示在「我的」图标右上角，数字 3
- 「直播中」红色徽标在第一个卡片左上

要求：
- 完整列出所有可见元素，不要省略
- 中文文本必须原样保留
- 颜色必须用 hex 格式
- 标注所有特殊状态（红点、徽标、直播中、置顶等）`;

export interface ExtractOptions {
  format: "json" | "markdown" | "both";
  pageHint?: string;
  model?: string;
  provider?: LLMProvider;
}

export interface DesignExtractResult {
  success: boolean;
  format: "json" | "markdown" | "both";
  source: string;
  json?: unknown;
  markdown?: string;
  raw: string;
  model: string;
}

/**
 * 主入口：抽取设计稿为结构化规范
 */
export async function extractDesignSpec(
  imagePath: string,
  options: ExtractOptions
): Promise<DesignExtractResult> {
  if (!fs.existsSync(imagePath)) {
    throw new Error(`Image not found: ${imagePath}`);
  }
  const resizedPath = await resizeForApiAsync(imagePath);
  const imageUrl = encodeAsDataUrl(resizedPath);

  // 调用前临时切换 VISION_PROVIDER，让 activeProvider 拿到正确 baseURL
  const prevProvider = process.env.VISION_PROVIDER;
  if (options.provider) process.env.VISION_PROVIDER = options.provider;

  let json: unknown = undefined;
  let markdown: string | undefined = undefined;
  let raw = "";
  let usedModel = "";

  try {
    const cfg = activeProvider();
    const model = options.model || cfg.defaultModel;
    usedModel = model;

    if (options.format === "json" || options.format === "both") {
      const userPrompt = options.pageHint
        ? `Analyze this Android app screenshot. The page name hint is: "${options.pageHint}". Output a single JSON object per the schema.`
        : `Analyze this Android app screenshot. Output a single JSON object per the schema.`;
      const content = await callVision(model, JSON_SYSTEM_PROMPT, userPrompt, imageUrl);
      raw = content;
      json = parseJsonFromVision(content);
    }

    if (options.format === "markdown" || options.format === "both") {
      const userPrompt = options.pageHint
        ? `Analyze this Android app screenshot (page: "${options.pageHint}"). Output a single Markdown document.`
        : `Analyze this Android app screenshot. Output a single Markdown document.`;
      const content = await callVision(model, MARKDOWN_SYSTEM_PROMPT, userPrompt, imageUrl);
      if (!raw) raw = content;
      markdown = content.trim();
    }

    return {
      success: true,
      format: options.format,
      source: imagePath,
      json,
      markdown,
      raw,
      model: usedModel,
    };
  } finally {
    // 还原 env 并清理缓存（让下次调用重读）
    if (prevProvider === undefined) delete process.env.VISION_PROVIDER;
    else process.env.VISION_PROVIDER = prevProvider;
    client = null;
    clientKey = "";
    cleanupResizedFile(resizedPath, imagePath);
  }
}

/**
 * 抽取颜色 token（仅颜色）
 */
export async function extractColorTokens(imagePath: string, modelId?: string, provider?: LLMProvider): Promise<{
  success: boolean;
  source: string;
  model?: string;
  tokens: Record<string, { hex: string; usage: string; pixelPct: number }>;
}> {
  if (!fs.existsSync(imagePath)) {
    throw new Error(`Image not found: ${imagePath}`);
  }
  const resizedPath = await resizeForApiAsync(imagePath);
  const imageUrl = encodeAsDataUrl(resizedPath);

  const prevProvider = process.env.VISION_PROVIDER;
  if (provider) process.env.VISION_PROVIDER = provider;

  const systemPrompt = `You are a design system engineer. Analyze this mobile UI screenshot and output a JSON object listing all color tokens used in the design.

Output format (JSON only, no markdown):
{
  "tokens": {
    "primary": { "hex": "#FF5757", "usage": "brand red, used in top bar and selected tab", "pixelPct": 12.5 },
    "background": { "hex": "#F5F5F5", "usage": "page background", "pixelPct": 45.0 },
    ...
  }
}

Rules:
- pixelPct: estimated coverage of this color in the screenshot (rough percentage)
- Focus on the 5-10 most prominent colors only
- hex values must be exact
- If unsure, omit the token rather than guess`;

  try {
    const content = await callVision(modelId || "", systemPrompt,
      "Extract the color tokens from this Android UI screenshot. Output JSON only.", imageUrl);
    const parsed = parseJsonFromVision(content) as { tokens?: Record<string, { hex: string; usage: string; pixelPct: number }> };
    const cfg = activeProvider();
    return {
      success: true,
      source: imagePath,
      model: modelId || cfg.defaultModel,
      tokens: parsed.tokens || {},
    };
  } finally {
    if (prevProvider === undefined) delete process.env.VISION_PROVIDER;
    else process.env.VISION_PROVIDER = prevProvider;
    client = null;
    clientKey = "";
    cleanupResizedFile(resizedPath, imagePath);
  }
}

/**
 * 抽取 UI 组件列表
 */
export async function extractComponents(imagePath: string, pageHint?: string, modelId?: string, provider?: LLMProvider): Promise<{
  success: boolean;
  source: string;
  model?: string;
  components: Array<{
    id: string;
    kind: string;
    bounds: { x: number; y: number; width: number; height: number };
    title?: string;
    text?: string;
    source?: string;
    time?: string;
    hasImage: boolean;
  }>;
}> {
  if (!fs.existsSync(imagePath)) {
    throw new Error(`Image not found: ${imagePath}`);
  }
  const resizedPath = await resizeForApiAsync(imagePath);
  const imageUrl = encodeAsDataUrl(resizedPath);

  const prevProvider = process.env.VISION_PROVIDER;
  if (provider) process.env.VISION_PROVIDER = provider;

  const systemPrompt = `You are an Android UI engineer. Analyze this screenshot and extract every visible UI component as a list.

Output (JSON only):
{
  "components": [
    {
      "id": "card-1",
      "kind": "TextTopCard|LeftTextRightImageCard|LargeImageCard|VideoCard|Avatar|Button|Text|SearchBar|Tab|Icon|BottomNavItem",
      "bounds": { "x": number, "y": number, "width": number, "height": number },
      "title": "string?",
      "text": "string?",
      "source": "string? (publisher/source name)",
      "time": "string? (time text)",
      "hasImage": boolean
    }
  ]
}

Rules:
- Coordinates in pixel space
- Be exhaustive: list every component (cards, buttons, text labels, icons, tabs, etc.)
- Skip invisible/background elements
- Chinese text must be preserved exactly
- Order by Y coordinate (top to bottom)`;

  try {
    const prompt = pageHint
      ? `Extract all UI components from this Android screenshot (page: "${pageHint}"). JSON only.`
      : `Extract all UI components from this Android screenshot. JSON only.`;
    const content = await callVision(modelId || "", systemPrompt, prompt, imageUrl);
    const parsed = parseJsonFromVision(content) as { components?: Array<{
      id: string; kind: string; bounds: { x: number; y: number; width: number; height: number };
      title?: string; text?: string; source?: string; time?: string; hasImage: boolean;
    }> };
    const cfg = activeProvider();
    return {
      success: true,
      source: imagePath,
      model: modelId || cfg.defaultModel,
      components: parsed.components || [],
    };
  } finally {
    if (prevProvider === undefined) delete process.env.VISION_PROVIDER;
    else process.env.VISION_PROVIDER = prevProvider;
    client = null;
    clientKey = "";
    cleanupResizedFile(resizedPath, imagePath);
  }
}

/**
 * 设计稿 → Jetpack Compose 代码骨架
 */
export async function designToComposeSkeleton(imagePath: string, packageName?: string, modelId?: string, provider?: LLMProvider): Promise<{
  success: boolean;
  source: string;
  model?: string;
  kotlin: string;
  notes: string;
}> {
  if (!fs.existsSync(imagePath)) {
    throw new Error(`Image not found: ${imagePath}`);
  }
  const resizedPath = await resizeForApiAsync(imagePath);
  const imageUrl = encodeAsDataUrl(resizedPath);

  const prevProvider = process.env.VISION_PROVIDER;
  if (provider) process.env.VISION_PROVIDER = provider;

  const systemPrompt = `你是 Jetpack Compose 高级工程师。给定移动端 UI 截图，输出一份可直接落到 /app/src/main/java/<package>/presentation/<page>/<Page>Screen.kt 的 Compose 代码骨架。

要求：
- 仅输出代码，不要 markdown 包裹，不要解释
- 用 Material3 + Compose
- 包含 Scaffold、TopAppBar、BottomNavigationBar（如有）、TabRow（如有）、LazyColumn（如有卡片）
- 组件按截图结构从上到下
- 关键文字内容保留原样
- 颜色用 Color(0xFF...) 形式
- 不实现具体业务逻辑：用 TODO() 标注需要填充的地方
- 字体大小用 .sp，距离用 .dp
- 卡片用 Card 包裹，标题/副标题/图片/来源/时间用 Column/Row 排版
- 文末加注释 "/* === TODO NOTES === */" 列出这个骨架未实现的部分`;

  try {
    const userHint = packageName ? `Package: ${packageName}` : "";
    const content = await callVision(modelId || "", systemPrompt,
      `生成此页面的 Compose 骨架代码。${userHint}`.trim(), imageUrl);
    const { code, notes } = splitCodeAndNotes(content);
    const cfg = activeProvider();
    return {
      success: true,
      source: imagePath,
      model: modelId || cfg.defaultModel,
      kotlin: code,
      notes,
    };
  } finally {
    if (prevProvider === undefined) delete process.env.VISION_PROVIDER;
    else process.env.VISION_PROVIDER = prevProvider;
    client = null;
    clientKey = "";
    cleanupResizedFile(resizedPath, imagePath);
  }
}

// ── 内部工具 ─────────────────────────────────────

/**
 * 核心调用：统一处理 Kimi / Minimax 的 reasoning / non-reasoning 模式
 *
 * Kimi k2.6: 强制 temperature=1，答案在 reasoning_content
 * Minimax M3:  默认开 thinking，可关（thinking:disabled）加速 5-10x
 * Minimax M2.x: thinking 不能关
 *
 * 返回的 content 优先取 final answer 字段（content > reasoning_content）
 */
async function callVision(modelId: string, systemPrompt: string, userPrompt: string, imageUrl: string): Promise<string> {
  const cfg = activeProvider();
  const model = modelId || cfg.defaultModel;
  const t0 = Date.now();
  log(`vision call: provider=${cfg.provider} model=${model} promptLen=${userPrompt.length}`);

  const messages = [
    { role: "system" as const, content: systemPrompt },
    {
      role: "user" as const,
      content: [
        { type: "image_url" as const, image_url: { url: imageUrl } },
        { type: "text" as const, text: userPrompt },
      ],
    },
  ];

  // 构造请求体：Minimax M3 支持 thinking:disabled，关掉后速度提升 5-10x
  const requestOpts: any = {
    model,
    messages,
    max_tokens: 4000,
    temperature: cfg.provider === "kimi" ? 1.0 : 1.0, // 都用 1.0（kimi 强制）
  };

  if (cfg.provider === "minimax" && model === "MiniMax-M3") {
    // M3 才能关 thinking；M2.7/M2.5 关不掉
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }

  const response = await getClient().chat.completions.create(requestOpts, { timeout: 120000 });

  const msg = response.choices[0]?.message as any;
  let content = msg?.content;
  let source: "content" | "reasoning_content" = "content";

  // Reasoning 模型兜底：Kimi k2.6 答案在 reasoning_content
  if (!content && msg?.reasoning_content) {
    content = msg.reasoning_content;
    source = "reasoning_content";
  }

  if (!content) {
    log(`vision: empty response. msg keys: ${Object.keys(msg || {}).join(",")}`);
    throw new Error("Vision model returned empty response");
  }

  log(`vision done: ${Date.now() - t0}ms, source=${source}, content=${content.length} chars`);
  return content;
}

/**
 * 容错地从 vision 输出中提取 JSON 对象
 */
function parseJsonFromVision(raw: string): unknown {
  let text = raw.trim();
  // 去掉 markdown 围栏
  const fenced = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (fenced) text = fenced[1].trim();
  // 截取第一个 { 到最后一个 } 之间的内容
  const a = text.indexOf("{");
  const b = text.lastIndexOf("}");
  if (a !== -1 && b > a) text = text.slice(a, b + 1);

  // 第一次直接 parse
  try { return JSON.parse(text); } catch { /* fall through */ }

  // 修复常见错误：尾随逗号、未引用的 key
  const fixed = text
    .replace(/,(\s*[}\]])/g, "$1")
    .replace(/([{,]\s*)([A-Za-z_][\w$]*)(\s*:)/g, '$1"$2"$3');
  try { return JSON.parse(fixed); } catch (e) {
    error("Failed to parse vision JSON:", e);
    error("Raw text preview:", text.slice(0, 500));
    return {};
  }
}

function splitCodeAndNotes(content: string): { code: string; notes: string } {
  const markerMatch = content.match(/\/\*\s*===\s*TODO NOTES\s*===\s*\*\/([\s\S]*)$/);
  if (markerMatch) {
    const code = content.slice(0, markerMatch.index).trim();
    const notes = markerMatch[1].trim();
    return { code, notes };
  }
  // 没有 marker，全部视为 code
  return { code: content.replace(/^```(?:kotlin)?\n?/m, "").replace(/```\s*$/m, "").trim(), notes: "" };
}
