import { log, error } from "../utils/logger.js";
import { compareWithVision } from "./vision-analyze.js";

export async function handleCompareScreenshots(args: Record<string, unknown>) {
  try {
    const baselinePath = (args.baselinePath as string) ?? "";
    const currentPath = (args.currentPath as string) ?? "";
    const prompt = (args.prompt as string) ?? "";

    if (!baselinePath || !currentPath) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: "baselinePath and currentPath required" }) }] };
    }

    log(`compare: ${baselinePath} vs ${currentPath}`);
    const result = await compareWithVision(baselinePath, currentPath, prompt);

    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        analysis: result,
        baseline: baselinePath,
        current: currentPath,
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("compare failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
