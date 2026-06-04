import { runUITest, runRegressionTest } from "../utils/ui-test.js";
import { log } from "../utils/logger.js";

export async function handleUITest(
  args: Record<string, unknown>,
  action: string
) {
  switch (action) {
    case "run_test": {
      const { steps } = args as {
        steps: Array<{
          action: "tap" | "swipe" | "input" | "wait" | "screenshot";
          params: Record<string, unknown>;
        }>;
      };
      
      const result = await runUITest(steps);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            message: result.message,
            duration: result.duration,
            screenshot: result.screenshot,
          }, null, 2),
        }],
      };
    }
    
    case "regression": {
      const { packageName, serial } = args as { packageName: string; serial?: string };
      const result = await runRegressionTest(packageName);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.failed === 0,
            passed: result.passed,
            failed: result.failed,
            total: result.total,
            results: result.results,
          }, null, 2),
        }],
      };
    }
    
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown UI test action: ${action}` }],
      };
  }
}
