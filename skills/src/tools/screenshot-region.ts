import { log, error } from "../utils/logger.js";
import { screenshot as fullScreenshot } from "../utils/adb.js";
import sharp from "sharp";

/**
 * 区域截图：先全屏截图，再用 sharp 裁剪
 * 用例：Agent 只想分析顶部 Tab 区域 / 底部导航，不需要传整张 1080×2400 的图
 */
export async function handleScreenshotRegion(args: Record<string, unknown>) {
  try {
    const x = args.x as number | undefined;
    const y = args.y as number | undefined;
    const width = args.width as number | undefined;
    const height = args.height as number | undefined;
    const savePath = args.savePath as string | undefined;

    if ([x, y, width, height].some((v) => v === undefined || v < 0)) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "x, y, width, height are all required and must be >= 0",
      }) }] };
    }

    const full = await fullScreenshot();
    const outPath = savePath || full.path.replace(/\.png$/, `_region_${x}_${y}_${width}x${height}.png`);

    await sharp(full.path)
      .extract({ left: Math.round(x!), top: Math.round(y!), width: Math.round(width!), height: Math.round(height!) })
      .toFile(outPath);

    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        path: outPath,
        region: { x, y, width, height },
        parentPath: full.path,
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("screenshot_region failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
