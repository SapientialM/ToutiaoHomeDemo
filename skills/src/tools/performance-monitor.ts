import { collectPerformanceMetrics, formatPerformanceReport } from "../utils/performance.js";
import { log } from "../utils/logger.js";

export async function handlePerformanceMonitor(args: Record<string, unknown>) {
  const { packageName, serial } = args as { packageName?: string; serial?: string };
  
  try {
    const metrics = await collectPerformanceMetrics(packageName);
    const report = formatPerformanceReport(metrics);
    
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          metrics: {
            cpuUsage: metrics.cpuUsage,
            memoryUsage: metrics.memoryUsage,
            fps: metrics.fps,
            batteryLevel: metrics.batteryLevel,
            temperature: metrics.temperature,
            timestamp: metrics.timestamp,
          },
          report,
        }, null, 2),
      }],
    };
  } catch (e) {
    const err = e as Error;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Failed to collect performance metrics",
        }),
      }],
    };
  }
}
