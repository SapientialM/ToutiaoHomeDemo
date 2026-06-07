import OpenAI from "openai";
import fs from "fs";
import {
  activeProvider,
  callVisionLlm,
  smartResizeForVision,
  encodeImageAsDataUrl,
  getActiveProvider,
  makeInsecureFetch,
  type LLMProvider,
} from "../utils/design-extractor.js";
import { log, error } from "../utils/logger.js";

/**
 * Analyze a single Android screenshot with vision LLM.
 *
 * Uses the multi-provider abstraction from design-extractor:
 *  - VISION_PROVIDER=minimax (default)
 *  - VISION_MODEL env override (default = provider's default)
 *  - Image auto-resize: < 80KB skip, otherwise long edge → 768px JPEG quality=85
 */
export async function analyzeWithVision(
  imagePath: string,
  prompt?: string,
  systemPrompt?: string,
): Promise<string> {
  if (!fs.existsSync(imagePath)) {
    throw new Error(`File not found: ${imagePath}`);
  }

  const cfg = getActiveProvider();
  const modelId = process.env.VISION_MODEL || cfg.defaultModel;
  const resizedPath = await smartResizeForVision(imagePath);
  try {
    const imageUrl = encodeImageAsDataUrl(resizedPath);
    const defaultPrompt = `You are an Android UI expert. Analyze this screenshot and report:

## Layout
- What type of screen is this? (news feed, detail page, etc.)
- Are there alignment/spacing issues? Be specific about which element and by how much.
- Is the content density appropriate?

## Components
- How many cards/items are visible? List each one.
- Are images loading correctly?
- Is text readable (font size, contrast, truncation)?

## Issues
List every UI problem you see, with:
- Exact location/which component
- What's wrong
- Suggested fix (include specific dp/px/sp values for Jetpack Compose)

## Design Match
- Does the actual implementation match the expected news-feed design?
- Header: is the red correct? (#FF5757)
- Bottom nav: white background? selected tab indicator (#171E38)?
- Cards: proper spacing between them?

Be very specific and quantitative. Measure approximate padding/margins using the screen dimensions as reference.`;

    return await callVisionLlm(
      modelId,
      systemPrompt || "You are an Android UI/UX expert. Always give specific, actionable feedback with exact measurements and Compose code suggestions.",
      prompt || defaultPrompt,
      imageUrl,
    );
  } catch (e) {
    error("analyzeWithVision failed:", e);
    throw e;
  } finally {
    if (resizedPath !== imagePath) {
      try { fs.unlinkSync(resizedPath); } catch { /* ignore */ }
    }
  }
}

/**
 * Compare two Android screenshots (design vs current) with vision LLM.
 *
 * Uses the same multi-provider abstraction as analyzeWithVision.
 */
export async function compareWithVision(baselinePath: string, currentPath: string, prompt?: string): Promise<string> {
  if (!fs.existsSync(baselinePath)) throw new Error(`Baseline not found: ${baselinePath}`);
  if (!fs.existsSync(currentPath)) throw new Error(`Current not found: ${currentPath}`);

  const cfg = getActiveProvider();
  const modelId = process.env.VISION_MODEL || cfg.defaultModel;

  // 两张图并发 resize
  const [bResized, cResized] = await Promise.all([
    smartResizeForVision(baselinePath),
    smartResizeForVision(currentPath),
  ]);

  try {
    const defaultPrompt = `Compare these two Android screenshots. The first is the design/baseline, the second is the current implementation.

Focus on:
1. Layout differences (position, spacing, sizing)
2. Color differences (especially the header red and bottom nav)
3. Content differences (more/less cards, different text)
4. Specific issues introduced in the current version

For each difference, state whether it's acceptable or needs fixing, and suggest exact Compose code changes.`;

    const imageBaseUrl1 = encodeImageAsDataUrl(bResized);
    const imageBaseUrl2 = encodeImageAsDataUrl(cResized);

    // 多图场景用 multi-content message
    const systemPrompt = "You are an Android UI testing expert. Compare screenshots precisely and give actionable feedback.";
    const userContent = [
      { type: "text" as const, text: "Here is the baseline/design screenshot:" },
      { type: "image_url" as const, image_url: { url: imageBaseUrl1 } },
      { type: "text" as const, text: "Here is the current implementation screenshot:" },
      { type: "image_url" as const, image_url: { url: imageBaseUrl2 } },
      { type: "text" as const, text: prompt || defaultPrompt },
    ];

    return await callVisionLlmMultiContent(modelId, systemPrompt, userContent);
  } catch (e) {
    error("compareWithVision failed:", e);
    throw e;
  } finally {
    if (bResized !== baselinePath) {
      try { fs.unlinkSync(bResized); } catch { /* ignore */ }
    }
    if (cResized !== currentPath) {
      try { fs.unlinkSync(cResized); } catch { /* ignore */ }
    }
  }
}

// ── 内部：多图 message 调用（callVisionLlm 是单图版本，多图需自构请求）──

let multiClient: OpenAI | null = null;
let multiClientKey = "";

function getMultiClient(): OpenAI {
  const cfg = activeProvider();
  const cacheKey = `${cfg.provider}|${cfg.apiKeyEnv}|${cfg.insecureTLS}`;
  if (multiClient && multiClientKey === cacheKey) return multiClient;
  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) throw new Error(`${cfg.apiKeyEnv} not set`);
  if (cfg.insecureTLS) process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  multiClient = new OpenAI({
    apiKey,
    baseURL: cfg.baseURL,
    ...(cfg.insecureTLS ? { fetch: makeInsecureFetch() } : {}),
  });
  multiClientKey = cacheKey;
  return multiClient;
}

async function callVisionLlmMultiContent(
  modelId: string,
  systemPrompt: string,
  userContent: Array<{ type: "text"; text: string } | { type: "image_url"; image_url: { url: string } }>,
): Promise<string> {
  const cfg = activeProvider();
  const model = modelId || cfg.defaultModel;
  const t0 = Date.now();
  log(`vision call (multi): provider=${cfg.provider} model=${model} contentParts=${userContent.length}`);

  const requestOpts: any = {
    model,
    messages: [
      { role: "system" as const, content: systemPrompt },
      { role: "user" as const, content: userContent },
    ],
    max_tokens: 4000,
    temperature: 1.0,
  };

  if (model === "MiniMax-M3") {
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }

  const response = await getMultiClient().chat.completions.create(requestOpts, { timeout: 120000 });

  const msg = response.choices[0]?.message as any;
  let content = msg?.content;
  if (!content && msg?.reasoning_content) {
    content = msg.reasoning_content;
  }
  if (!content) {
    log(`vision multi: empty response. msg keys: ${Object.keys(msg || {}).join(",")}`);
    throw new Error("Vision model returned empty response");
  }

  log(`vision multi done: ${Date.now() - t0}ms, content=${content.length} chars`);
  return content;
}

// Avoid unused import warning for LLMProvider type
export type { LLMProvider };
