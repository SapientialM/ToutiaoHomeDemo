import fs from "fs";
import path from "path";
import { log, error } from "../utils/logger.js";
import {
  extractDesignSpec,
  extractColorTokens,
  extractComponents,
  designToComposeSkeleton,
} from "../utils/design-extractor.js";

const DESIGN_DIR = process.env.DESIGN_DIR || "./design";

/**
 * 工具 1：抽取设计稿为结构化规范（JSON / Markdown / 二者）
 * 使用 Kimi 视觉 LLM 把设计截图解析为 Agent 可读的规范文档
 */
export async function handleExtractDesignSpec(args: Record<string, unknown>) {
  try {
    const imagePath = resolveImagePath(args.imagePath as string);
    const format = (args.format as "json" | "markdown" | "both") || "both";
    const pageHint = (args.pageHint as string) ?? derivePageHint(imagePath);
    const model = args.model as string | undefined;
    const provider = args.provider as string | undefined;

    log(`extract_design_spec: ${imagePath}, format=${format}, hint=${pageHint}, model=${model || "default"}, provider=${provider || "default"}`);
    const result = await extractDesignSpec(imagePath, { format, pageHint, model, provider: provider as any });

    if (format === "json") {
      return {
        content: [{ type: "text", text: JSON.stringify({
          success: true,
          source: result.source,
          model: result.model,
          pageHint,
          json: result.json,
        }, null, 2) }],
      };
    }
    if (format === "markdown") {
      return {
        content: [{ type: "text", text: result.markdown || "" }],
      };
    }
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        source: result.source,
        model: result.model,
        pageHint,
        json: result.json,
        markdown: result.markdown,
        usage: "Use 'json' field for programmatic consumption (Compose theme, layout code). Use 'markdown' field for human review.",
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("extract_design_spec failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 工具 2：抽取颜色 token（只关注颜色 + 占比）
 * 用于：直接喂给 Compose 的 ColorScheme
 */
export async function handleExtractDesignTokens(args: Record<string, unknown>) {
  try {
    const imagePath = resolveImagePath(args.imagePath as string);
    const model = args.model as string | undefined;
    const provider = args.provider as string | undefined;
    const result = await extractColorTokens(imagePath, model, provider as any);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("extract_design_tokens failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 工具 3：抽取 UI 组件列表（带坐标和类型）
 * 用于：直接对照实现 / 给 Agent 精确坐标
 */
export async function handleExtractComponents(args: Record<string, unknown>) {
  try {
    const imagePath = resolveImagePath(args.imagePath as string);
    const pageHint = (args.pageHint as string) ?? derivePageHint(imagePath);
    const model = args.model as string | undefined;
    const provider = args.provider as string | undefined;
    const result = await extractComponents(imagePath, pageHint, model, provider as any);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("extract_design_components failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 工具 4：列出 design 目录下的所有设计稿文件
 * 用于：让 Agent 知道有哪些页面可参考
 */
export async function handleListDesignFiles(args: Record<string, unknown>) {
  try {
    const dir = (args.dir as string) || DESIGN_DIR;
    if (!fs.existsSync(dir)) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: `Directory not found: ${dir}`,
        hint: `Set DESIGN_DIR env or pass dir= argument`,
      }) }] };
    }

    const entries = fs.readdirSync(dir, { withFileTypes: true });
    const imageExts = new Set([".png", ".jpg", ".jpeg", ".webp"]);
    const files = entries
      .filter((e) => e.isFile() && imageExts.has(path.extname(e.name).toLowerCase()))
      .map((e) => {
        const full = path.join(dir, e.name);
        const stat = fs.statSync(full);
        return {
          name: e.name,
          path: full,
          sizeKB: Math.round(stat.size / 1024),
          modifiedAt: stat.mtime.toISOString(),
          pageHint: fileNameToPageHint(e.name),
        };
      });

    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        dir,
        count: files.length,
        files,
        usage: "Pass any file.path to extract_design_spec to get its structured design spec.",
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("list_design_files failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 工具 5：设计稿 → Jetpack Compose 代码骨架
 * 用于：让 Agent 拿到一份可直接落地的 Screen.kt 初稿
 */
export async function handleDesignToCompose(args: Record<string, unknown>) {
  try {
    const imagePath = resolveImagePath(args.imagePath as string);
    const packageName = (args.packageName as string) || "com.example.app";
    const model = args.model as string | undefined;
    const provider = args.provider as string | undefined;
    const result = await designToComposeSkeleton(imagePath, packageName, model, provider as any);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        source: result.source,
        model: result.model,
        packageName,
        fileName: deriveScreenFileName(imagePath),
        kotlin: result.kotlin,
        notes: result.notes,
        usage: `Write result.kotlin to app/src/main/java/${packageName.replace(/\./g, "/")}/presentation/<page>/${deriveScreenFileName(imagePath)}`,
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("design_to_compose failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ── 内部工具 ─────────────────────────────────────

/**
 * 解析图片路径：支持相对路径、绝对路径、自动加 ./design/ 前缀
 */
function resolveImagePath(input: string | undefined): string {
  if (!input) {
    throw new Error("imagePath is required. Use list_design_files to discover available designs.");
  }
  if (fs.existsSync(input)) return path.resolve(input);

  // 尝试加 ./design/ 前缀
  const inDesign = path.join(DESIGN_DIR, input);
  if (fs.existsSync(inDesign)) return path.resolve(inDesign);

  // 尝试加 .jpg 扩展名
  if (fs.existsSync(inDesign + ".jpg")) return path.resolve(inDesign + ".jpg");
  if (fs.existsSync(inDesign + ".png")) return path.resolve(inDesign + ".png");

  throw new Error(`Image not found: ${input} (also tried ${inDesign}{.jpg,.png})`);
}

/**
 * 从文件名推断页面名提示（如 "首页-推荐.jpg" → "首页-推荐"）
 */
function fileNameToPageHint(filename: string): string {
  return path.basename(filename, path.extname(filename));
}

function derivePageHint(imagePath: string): string | undefined {
  return fileNameToPageHint(imagePath);
}

function deriveScreenFileName(imagePath: string): string {
  // "首页-推荐.jpg" → "HomeRecommendScreen.kt" (简化：用页名作为文件名)
  // 这里保守一点，用原始名加 Screen 后缀，去除特殊字符
  const base = path.basename(imagePath, path.extname(imagePath));
  const safe = base.replace(/[^\w一-龥-]/g, "");
  return `${safe}Screen.kt`;
}
