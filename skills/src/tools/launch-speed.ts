import { measureAppLaunch, formatLaunchReport } from "../utils/launch-speed.js";
import { log } from "../utils/logger.js";

/**
 * 处理应用启动速度测量请求
 */
export async function handleMeasureAppLaunch(args: Record<string, unknown>) {
  const {
    packageName,
    launchType = "cold_start",
    activityName,
    iterations = 3,
  } = args as {
    packageName: string;
    launchType?: "cold_start" | "warm_start" | "page_transition";
    activityName?: string;
    iterations?: number;
  };

  if (!packageName) {
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: "packageName is required",
        }),
      }],
    };
  }

  try {
    log(`Measuring app launch: ${packageName}, type: ${launchType}, iterations: ${iterations}`);
    
    const result = await measureAppLaunch(packageName, {
      launchType,
      activityName,
      iterations,
    });
    
    const report = formatLaunchReport(result);
    
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          packageName: result.packageName,
          launchType: result.launchType,
          activityName: result.activityName,
          iterations: result.iterations,
          grade: result.grade,
          statistics: result.statistics,
          results: result.results,
          recommendations: result.recommendations,
          report,
        }, null, 2),
      }],
    };
  } catch (e) {
    const err = e as Error;
    log("measure_app_launch failed:", err);
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Failed to measure app launch speed",
        }),
      }],
    };
  }
}
