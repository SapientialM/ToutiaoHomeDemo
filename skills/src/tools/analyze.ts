import { log, error } from "../utils/logger.js";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { analyzeWithVision } from "./vision-analyze.js";
import { execAsyncWithTimeout, fileExists } from "../utils/exec.js";
import { execSync } from "node:child_process";

function getPythonScriptPath(): string {
  const currentDir = path.dirname(fileURLToPath(import.meta.url));
  const candidates = [
    path.resolve(currentDir, "..", "src", "tools", "analyze_image.py"),
    path.resolve(currentDir, "..", "..", "src", "tools", "analyze_image.py"),
    path.resolve(process.cwd(), "src", "tools", "analyze_image.py"),
    path.resolve(process.cwd(), "skills", "src", "tools", "analyze_image.py"),
  ];
  for (const c of candidates) {
    try { execSync(`test -f "${c}"`, { timeout: 1000 }); return c; } catch { continue; }
  }
  return path.resolve(currentDir, "..", "src", "tools", "analyze_image.py");
}

const scriptPath = getPythonScriptPath();

export async function handleAnalyzeScreenshot(args: Record<string, unknown>) {
  const filePath = (args.filePath as string) ?? "";
  const prompt = (args.prompt as string) ?? "";

  if (!filePath) {
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: "filePath required" }) }] };
  }
  if (!(await fileExists(filePath))) {
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: `File not found: ${filePath}` }) }] };
  }

  const result: Record<string, unknown> = { file: filePath };

  // Stage 1: PIL pixel measurements
  let pilData: Record<string, unknown> = {};
  try {
    const cmd = `python3 "${scriptPath}" "${filePath}"`;
    log(`PIL: ${cmd}`);
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 15000 });
    pilData = JSON.parse(stdout);
    result.pil = {
      dimensions: pilData.dimensions,
      header: pilData.header,
      content: pilData.content,
      bottomNav: pilData.bottom_nav,
      warnings: pilData.warnings || [],
      problems: pilData.problems || [],
    };
  } catch (e: unknown) {
    result.pil = { error: (e as Error).message };
  }

  // Stage 2: Vision AI
  try {
    const visionCtx = prompt || buildAutoPrompt(pilData);
    const visionResult = await Promise.race([
      analyzeWithVision(filePath, visionCtx),
      new Promise<string>((_, reject) => setTimeout(() => reject(new Error("Vision timeout 90s")), 90000)),
    ]);
    result.vision = visionResult;
  } catch (e: unknown) {
    result.vision = { error: (e as Error).message };
  }

  // Stage 3: Card detail verification
  const cards = (pilData.content as Record<string, unknown>)?.cards as Array<Record<string, unknown>> | undefined;
  if (cards?.length) {
    result.cards = cards.map((c) => ({
      index: c.index,
      type: c.type,
      y: c.y,
      height: c.height,
      hasImage: c.has_image_region,
      imageLoaded: c.image_loaded,
      textLines: c.text_lines,
    }));
  }

  // Checklist
  const checklist: Array<{ label: string; ok: boolean }> = [];
  const h = pilData.header as Record<string, number> | undefined;
  if (h) {
    checklist.push({ label: "Header red (#FF5757)", ok: h.red_pixels_pct > 15 });
    checklist.push({ label: "Search bar visible", ok: Boolean(h.search_bar_detected) });
  }
  const ct = pilData.content as Record<string, unknown> | undefined;
  if (ct) {
    const cardList = ct.cards as Array<Record<string, unknown>> | undefined;
    checklist.push({ label: "Has cards", ok: (cardList?.length ?? 0) > 0 });
    checklist.push({ label: "Proper spacing", ok: (ct.gray_dividers as number) >= (cardList?.length ?? 1) - 1 });
  }
  const nav = pilData.bottom_nav as Record<string, number> | undefined;
  if (nav) {
    checklist.push({ label: "Nav white bg", ok: nav.bg_white_pct > 85 });
    checklist.push({ label: "Tab indicator visible", ok: nav.selected_color_px > 100 });
  }
  result.checklist = checklist;

  return {
    content: [{ type: "text", text: JSON.stringify(result) }],
  };
}

function buildAutoPrompt(pilData: Record<string, unknown>): string {
  const cards = (pilData.content as Record<string, unknown>)?.cards as Array<Record<string, unknown>> | undefined;
  const parts = ["Analyze this Android news-feed screenshot:", ""];
  if (cards) {
    for (const c of cards.slice(0, 10)) {
      parts.push(`- Card ${c.index}: ${c.type}, ${c.height}px, ${c.text_lines} text lines`);
    }
  }
  parts.push("", "Report alignment, spacing, truncation issues with exact offsets and Compose fix suggestions.");
  return parts.join("\n");
}
