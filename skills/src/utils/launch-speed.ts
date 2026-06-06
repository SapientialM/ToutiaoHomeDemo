import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";

/**
 * 应用启动速度测量工具
 * 支持冷启动、热启动、页面跳转耗时测量
 */

export interface LaunchMetrics {
  iteration: number;
  ttid: number;        // Time To Initial Display (ms)
  ttfd: number;        // Time To Full Display (ms)
  totalTime: number;   // am start -W TotalTime
  waitTime: number;    // am start -W WaitTime
  timestamp: string;
}

export interface LaunchStatistics {
  min: number;
  max: number;
  avg: number;
  p95: number;
}

export interface LaunchMeasurementResult {
  packageName: string;
  launchType: "cold_start" | "warm_start" | "page_transition";
  activityName?: string;
  iterations: number;
  results: LaunchMetrics[];
  statistics: {
    ttid: LaunchStatistics;
    ttfd: LaunchStatistics;
    totalTime: LaunchStatistics;
  };
  grade: string;
  recommendations: string[];
}

/**
 * 获取应用当前运行的 Activity
 */
export async function getCurrentActivity(packageName: string): Promise<string | null> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `adb shell dumpsys activity activities | grep "mResumedActivity" | grep "${packageName}"`,
      { timeout: 5000 }
    );
    const match = stdout.match(/\.(\w+Activity)/);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}

/**
 * 强制停止应用
 */
export async function forceStopApp(packageName: string): Promise<void> {
  await execAsyncWithTimeout(`adb shell am force-stop ${packageName}`, { timeout: 5000 });
  // 等待应用完全停止
  await new Promise((resolve) => setTimeout(resolve, 500));
}

/**
 * 清除应用数据（模拟完全冷启动）
 */
export async function clearAppData(packageName: string): Promise<void> {
  await execAsyncWithTimeout(`adb shell pm clear ${packageName}`, { timeout: 10000 });
  await new Promise((resolve) => setTimeout(resolve, 1000));
}

/**
 * 使用 am start -W 测量启动时间
 * 返回 ThisTime, TotalTime, WaitTime
 */
export async function measureWithAmStart(
  packageName: string,
  activityName?: string
): Promise<{ thisTime: number; totalTime: number; waitTime: number }> {
  // 必须指定 Activity，否则 am start -W 会报错
  const component = activityName 
    ? `${packageName}/${activityName}` 
    : `${packageName}/.MainActivity`;
  
  const { stdout, stderr } = await execAsyncWithTimeout(
    `adb shell am start -W -n ${component}`,
    { timeout: 30000 }
  );

  // am start -W 输出到 stderr
  const output = stdout || stderr;
  log(`am start output: ${output}`);

  const thisTimeMatch = output.match(/ThisTime:\s*(\d+)/);
  const totalTimeMatch = output.match(/TotalTime:\s*(\d+)/);
  const waitTimeMatch = output.match(/WaitTime:\s*(\d+)/);

  const thisTime = thisTimeMatch ? parseInt(thisTimeMatch[1]) : 0;
  const totalTime = totalTimeMatch ? parseInt(totalTimeMatch[1]) : 0;
  const waitTime = waitTimeMatch ? parseInt(waitTimeMatch[1]) : 0;

  log(`Parsed: thisTime=${thisTime}, totalTime=${totalTime}, waitTime=${waitTime}`);

  return {
    thisTime,
    totalTime,
    waitTime,
  };
}

/**
 * 从 logcat 获取 Displayed 时间
 * 注意：此函数假设日志已经被清除，且应用已经启动
 */
export async function getDisplayedTimeFromLogcat(
  packageName: string,
  timeoutMs: number = 10000
): Promise<{ ttid: number; ttfd: number }> {
  const startTime = Date.now();

  let ttid = 0;
  let ttfd = 0;

  // 轮询日志，查找 Displayed 信息
  while (Date.now() - startTime < timeoutMs) {
    try {
      // 使用更宽泛的匹配，因为不同 Android 版本的日志标签可能不同
      const { stdout } = await execAsyncWithTimeout(
        `adb logcat -d | grep -i "displayed.*${packageName}"`,
        { timeout: 5000 }
      );

      // 匹配 TTID: Displayed com.package/.Activity: +897ms 或 +1s32ms
      // 格式1: +897ms (纯毫秒)
      // 格式2: +1s32ms (秒+毫秒)
      // 实际日志格式: "Displayed com.example.toutiao/.MainActivity for user 0: +840ms"
      // 注意：for user 0 后面有空格
      const ttidMatchMs = stdout.match(/Displayed\s+[^:]+:\s*\+(\d+)ms/);
      const ttidMatchSec = stdout.match(/Displayed\s+[^:]+:\s*\+(\d+)s(\d+)ms/);
      if (ttid === 0) {
        if (ttidMatchMs) {
          ttid = parseInt(ttidMatchMs[1]) || 0;
        } else if (ttidMatchSec) {
          const seconds = parseInt(ttidMatchSec[1]) || 0;
          const millis = parseInt(ttidMatchSec[2]) || 0;
          ttid = seconds * 1000 + millis;
        }
      }

      // 匹配 TTFD: Fully drawn com.package/.Activity: +1s234ms
      const ttfdMatch = stdout.match(/Fully drawn\s+[^:]+:\s*\+(\d+)s(\d+)ms/);
      const ttfdMatchMs = stdout.match(/Fully drawn\s+[^:]+:\s*\+(\d+)ms/);
      if (ttfdMatch) {
        const seconds = parseInt(ttfdMatch[1]) || 0;
        const millis = parseInt(ttfdMatch[2]) || 0;
        ttfd = seconds * 1000 + millis;
      } else if (ttfdMatchMs) {
        ttfd = parseInt(ttfdMatchMs[1]) || 0;
      }

      if (ttid > 0) break;
    } catch {
      // 继续轮询
    }

    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  return { ttid, ttfd };
}

/**
 * 执行单次启动测量
 */
export async function measureSingleLaunch(
  packageName: string,
  launchType: "cold_start" | "warm_start",
  activityName?: string,
  iteration: number = 1
): Promise<LaunchMetrics> {
  log(`Measuring ${launchType} iteration ${iteration} for ${packageName}`);
  
  if (launchType === "cold_start") {
    // 冷启动：强制停止 + 清除缓存
    await forceStopApp(packageName);
    await clearAppData(packageName);
  } else {
    // 热启动：按 HOME 退到后台
    await execAsyncWithTimeout("adb shell input keyevent 3", { timeout: 5000 }); // HOME key
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  
  // 先清除日志，确保只捕获本次启动的日志
  await execAsyncWithTimeout("adb logcat -c", { timeout: 5000 });

  // 使用 am start -W 测量
  const amStartResult = await measureWithAmStart(packageName, activityName);

  // 从 logcat 获取 Displayed 时间（等待日志写入）
  await new Promise((resolve) => setTimeout(resolve, 500));
  const { ttid, ttfd } = await getDisplayedTimeFromLogcat(packageName);

  // 如果 logcat 没有获取到，使用 am start -W 的结果作为备选
  const finalTtid = ttid > 0 ? ttid : (amStartResult.thisTime > 0 ? amStartResult.thisTime : amStartResult.totalTime);
  const finalTtfd = ttfd > 0 ? ttfd : amStartResult.totalTime;

  log(`Measurement result: ttid=${finalTtid}, ttfd=${finalTtfd}, totalTime=${amStartResult.totalTime}, waitTime=${amStartResult.waitTime}`);

  return {
    iteration,
    ttid: finalTtid,
    ttfd: finalTtfd,
    totalTime: amStartResult.totalTime,
    waitTime: amStartResult.waitTime,
    timestamp: new Date().toISOString(),
  };
}

/**
 * 计算统计数据
 */
export function calculateStatistics(values: number[]): LaunchStatistics {
  if (values.length === 0) {
    return { min: 0, max: 0, avg: 0, p95: 0 };
  }
  
  const sorted = [...values].sort((a, b) => a - b);
  const min = sorted[0];
  const max = sorted[sorted.length - 1];
  const avg = Math.round(sorted.reduce((a, b) => a + b, 0) / sorted.length);
  
  // P95
  const p95Index = Math.ceil(sorted.length * 0.95) - 1;
  const p95 = sorted[Math.max(0, p95Index)];
  
  return { min, max, avg, p95 };
}

/**
 * 根据 TTID 评分
 */
export function getGrade(ttidAvg: number): string {
  if (ttidAvg < 1000) return "A";
  if (ttidAvg < 2000) return "B";
  if (ttidAvg < 3000) return "C";
  return "D";
}

/**
 * 生成优化建议
 */
export function generateRecommendations(
  stats: { ttid: LaunchStatistics; ttfd: LaunchStatistics; totalTime: LaunchStatistics },
  launchType: string
): string[] {
  const recommendations: string[] = [];
  
  const ttidAvg = stats.ttid.avg;
  const ttfdAvg = stats.ttfd.avg;
  
  // TTID 评估
  if (ttidAvg < 500) {
    recommendations.push(`✅ ${launchType} TTID 优秀 (${ttidAvg}ms)，继续保持`);
  } else if (ttidAvg < 1000) {
    recommendations.push(`✅ ${launchType} TTID 良好 (${ttidAvg}ms)`);
  } else if (ttidAvg < 2000) {
    recommendations.push(`⚠️ ${launchType} TTID 一般 (${ttidAvg}ms)，建议优化至 1s 以内`);
  } else {
    recommendations.push(`❌ ${launchType} TTID 较差 (${ttidAvg}ms)，需要重点优化`);
  }
  
  // TTFD 评估
  if (ttfdAvg > 0 && ttfdAvg > ttidAvg * 1.5) {
    recommendations.push(`⚠️ TTFD (${ttfdAvg}ms) 比 TTID 慢 ${Math.round((ttfdAvg / ttidAvg - 1) * 100)}%，建议检查异步加载逻辑`);
  }
  
  // 稳定性评估
  const variance = stats.ttid.max - stats.ttid.min;
  if (variance > 500) {
    recommendations.push(`⚠️ 启动时间波动较大 (${stats.ttid.min}ms ~ ${stats.ttid.max}ms)，建议检查是否有阻塞 IO 操作`);
  }
  
  return recommendations;
}

/**
 * 执行完整的启动速度测量
 */
export async function measureAppLaunch(
  packageName: string,
  options: {
    launchType?: "cold_start" | "warm_start" | "page_transition";
    activityName?: string;
    iterations?: number;
  } = {}
): Promise<LaunchMeasurementResult> {
  const {
    launchType = "cold_start",
    activityName,
    iterations = 3,
  } = options;
  
  log(`Starting ${launchType} measurement for ${packageName} (${iterations} iterations)`);
  
  const results: LaunchMetrics[] = [];
  
  for (let i = 1; i <= iterations; i++) {
    try {
      const metric = await measureSingleLaunch(packageName, launchType as "cold_start" | "warm_start", activityName, i);
      results.push(metric);
      
      // 每次测量间隔 2 秒，让系统稳定
      if (i < iterations) {
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    } catch (e) {
      error(`Measurement iteration ${i} failed:`, e);
      // 继续下一次测量
    }
  }
  
  // 计算统计
  const ttidValues = results.map((r) => r.ttid).filter((v) => v > 0);
  const ttfdValues = results.map((r) => r.ttfd).filter((v) => v > 0);
  const totalTimeValues = results.map((r) => r.totalTime).filter((v) => v > 0);
  
  const statistics = {
    ttid: calculateStatistics(ttidValues),
    ttfd: calculateStatistics(ttfdValues),
    totalTime: calculateStatistics(totalTimeValues),
  };
  
  const grade = getGrade(statistics.ttid.avg);
  const recommendations = generateRecommendations(statistics, launchType);
  
  return {
    packageName,
    launchType,
    activityName,
    iterations: results.length,
    results,
    statistics,
    grade,
    recommendations,
  };
}

/**
 * 格式化测量报告
 */
export function formatLaunchReport(result: LaunchMeasurementResult): string {
  const lines = [
    "═══════════════════════════════════════════",
    "🚀 APP LAUNCH SPEED REPORT",
    "═══════════════════════════════════════════",
    `📦 Package: ${result.packageName}`,
    `🔄 Type: ${result.launchType}`,
    result.activityName ? `🎯 Activity: ${result.activityName}` : "",
    `📊 Iterations: ${result.iterations}`,
    `🏆 Grade: ${result.grade}`,
    "",
    "⏱️  STATISTICS (ms)",
    "───────────────────────────────────────────",
    `TTID (Initial Display):`,
    `   Min: ${result.statistics.ttid.min}ms`,
    `   Max: ${result.statistics.ttid.max}ms`,
    `   Avg: ${result.statistics.ttid.avg}ms`,
    `   P95: ${result.statistics.ttid.p95}ms`,
    "",
    `TTFD (Full Display):`,
    `   Min: ${result.statistics.ttfd.min}ms`,
    `   Max: ${result.statistics.ttfd.max}ms`,
    `   Avg: ${result.statistics.ttfd.avg}ms`,
    `   P95: ${result.statistics.ttfd.p95}ms`,
    "",
    `Total Time (am start -W):`,
    `   Min: ${result.statistics.totalTime.min}ms`,
    `   Max: ${result.statistics.totalTime.max}ms`,
    `   Avg: ${result.statistics.totalTime.avg}ms`,
    "",
    "📋 RAW RESULTS",
    "───────────────────────────────────────────",
    ...result.results.map((r) => 
      `  #${r.iteration}: TTID=${r.ttid}ms, TTFD=${r.ttfd}ms, Total=${r.totalTime}ms`
    ),
    "",
    "💡 RECOMMENDATIONS",
    "───────────────────────────────────────────",
    ...result.recommendations.map((r) => `  ${r}`),
    "═══════════════════════════════════════════",
  ];
  
  return lines.filter((line) => line !== "").join("\n");
}
