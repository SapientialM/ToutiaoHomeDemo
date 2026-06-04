import { screenshot } from "../utils/adb.js";
import { error } from "../utils/logger.js";
import fs from "fs";

export async function handleScreenshot(args: Record<string, unknown>) {
  try {
    const savePath = args.savePath as string | undefined;
    const result = await screenshot(savePath);
    let sizeBytes = 0;
    try { sizeBytes = fs.statSync(result.path).size; } catch { /* ignore */ }
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        path: result.path,
        timestamp: result.timestamp,
        sizeBytes,
      }) }],
    };
  } catch (err) {
    error("screenshot failed:", err);
    return {
      isError: true,
      content: [{ type: "text", text: JSON.stringify({ success: false, error: String(err) }) }],
    };
  }
}
