import fs from "fs";
import { log, error } from "../utils/logger.js";
import { screenshot, tap, swipe, inputText } from "../utils/adb.js";
import { execAsyncWithTimeout } from "../utils/exec.js";
import { analyzeWithVision } from "./vision-analyze.js";

// ── Types ──────────────────────────────────────────────

interface VisionAction {
  action: "tap" | "swipe" | "input" | "wait" | "none";
  x?: number;
  y?: number;
  x2?: number;
  y2?: number;
  duration?: number;
  text?: string;
  confidence: number;
  reasoning: string;
}

interface StepResult {
  action: string;
  x?: number;
  y?: number;
  x2?: number;
  y2?: number;
  text?: string;
  confidence: number;
  reasoning: string;
  error?: string;
}

// ── Cached screen size ─────────────────────────────────

let cachedScreen: { width: number; height: number } | null = null;

export async function getScreenSize(): Promise<{ width: number; height: number }> {
  if (cachedScreen) return cachedScreen;
  try {
    const { stdout } = await execAsyncWithTimeout("adb shell wm size", { timeout: 5000 });
    const match = stdout.match(/(\d+)x(\d+)/);
    if (match) {
      cachedScreen = { width: parseInt(match[1]), height: parseInt(match[2]) };
      return cachedScreen;
    }
  } catch (e) { error("getScreenSize:", e); }
  cachedScreen = { width: 1080, height: 2400 };
  return cachedScreen;
}

// ── System prompt ───────────────────────────────────────

function buildSystemPrompt(width: number, height: number): string {
  return `You are an Android UI automation agent. Output ONLY a single JSON object.

Screen: ${width}x${height}px.

Format: {"action":"tap|swipe|input|none","x":<0-${width}>,"y":<0-${height}>,"x2":<endX>,"y2":<endY>,"duration":300,"confidence":0.0-1.0,"reasoning":"<5 words>"}

Rules: tap→center of target. swipe→x,y=start x2,y2=end. none→not found(confidence:0). NO markdown, NO extra text.`;
}

// ── JSON parser ─────────────────────────────────────────

function parseVisionAction(raw: string, width: number, height: number): VisionAction {
  let jsonStr = raw.trim();
  const f = jsonStr.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (f) jsonStr = f[1].trim();
  const a = jsonStr.indexOf("{"), b = jsonStr.lastIndexOf("}");
  if (a !== -1 && b > a) jsonStr = jsonStr.slice(a, b + 1);

  let parsed: Record<string, unknown>;
  try { parsed = JSON.parse(jsonStr); } catch {
    const fixed = jsonStr.replace(/,(\s*[}\]])/g, "$1").replace(/([{,]\s*)(\w+)(\s*:)/g, '$1"$2"$3');
    try { parsed = JSON.parse(fixed); } catch {
      throw new Error(`Bad JSON from vision: ${raw.slice(0, 200)}`);
    }
  }

  const action = (parsed.action as string) || "none";
  if (!["tap", "swipe", "input", "wait", "none"].includes(action)) throw new Error(`Invalid action: ${action}`);

  const r: VisionAction = {
    action: action as VisionAction["action"],
    confidence: typeof parsed.confidence === "number" ? parsed.confidence : 0.5,
    reasoning: (parsed.reasoning as string) || "",
  };
  if (parsed.x !== undefined) { const x = Number(parsed.x); if (isNaN(x) || x < 0 || x > width) throw new Error(`X=${x} OOB`); r.x = Math.round(x); }
  if (parsed.y !== undefined) { const y = Number(parsed.y); if (isNaN(y) || y < 0 || y > height) throw new Error(`Y=${y} OOB`); r.y = Math.round(y); }
  if (parsed.x2 !== undefined) r.x2 = Math.round(Number(parsed.x2));
  if (parsed.y2 !== undefined) r.y2 = Math.round(Number(parsed.y2));
  if (parsed.duration !== undefined) r.duration = Number(parsed.duration);
  if (parsed.text !== undefined) r.text = String(parsed.text);
  return r;
}

// ── Single step ─────────────────────────────────────────

async function executeVisionStep(
  instruction: string,
  beforeScreenshotPath?: string,
): Promise<{ action: VisionAction; screenshot: string }> {
  const beforePath = beforeScreenshotPath && fs.existsSync(beforeScreenshotPath)
    ? beforeScreenshotPath
    : (await screenshot()).path;

  const { width, height } = await getScreenSize();
  log(`Vision: ${instruction}`);

  const t0 = Date.now();
  const response = await analyzeWithVision(beforePath, instruction, buildSystemPrompt(width, height));
  log(`Vision ${Date.now() - t0}ms: ${response.slice(0, 150)}`);

  const action = parseVisionAction(response, width, height);

  switch (action.action) {
    case "tap":
      if (action.x === undefined || action.y === undefined) throw new Error("Tap missing x,y");
      await tap(action.x, action.y);
      break;
    case "swipe":
      await swipe(action.x ?? width / 2, action.y ?? height * 0.7, action.x2 ?? width / 2, action.y2 ?? height * 0.3, action.duration ?? 300);
      break;
    case "input":
      if (action.text) await inputText(action.text);
      break;
    case "wait":
      await new Promise((r) => setTimeout(r, action.duration ?? 500));
      break;
  }

  await new Promise((r) => setTimeout(r, 400));
  const after = await screenshot();
  return { action, screenshot: after.path };
}

// ── Main handler ────────────────────────────────────────

export async function handleVisionAction(
  args: Record<string, unknown>,
): Promise<{ isError?: boolean; content: Array<{ type: string; text: string }> }> {
  const prompt = (args.prompt as string) ?? "";
  const prompts = (args.prompts as string[]) ?? [];
  const beforeScreenshot = (args.beforeScreenshot as string) ?? "";

  const allPrompts: string[] = prompt ? [prompt] : prompts;
  if (allPrompts.length === 0) {
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: "prompt or prompts required" }) }] };
  }

  const tStart = Date.now();
  const steps: StepResult[] = [];
  let lastScreenshot = beforeScreenshot || "";
  let allSuccess = true;

  for (let i = 0; i < allPrompts.length; i++) {
    try {
      const { action, screenshot: ss } = await executeVisionStep(allPrompts[i], lastScreenshot || undefined);
      lastScreenshot = ss;
      steps.push({
        action: action.action,
        x: action.x, y: action.y, x2: action.x2, y2: action.y2,
        text: action.text, confidence: action.confidence, reasoning: action.reasoning,
      });
    } catch (e: unknown) {
      steps.push({ action: "error", confidence: 0, reasoning: "", error: (e as Error).message });
      allSuccess = false;
    }
  }

  return {
    isError: !allSuccess,
    content: [{ type: "text", text: JSON.stringify({
      success: allSuccess,
      steps,
      screenshot: lastScreenshot || null,
      durationMs: Date.now() - tStart,
    }) }],
  };
}
