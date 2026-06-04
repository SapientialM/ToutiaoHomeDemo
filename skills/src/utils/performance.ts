import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";

/**
 * 性能监控工具 - 收集Android设备性能指标
 */

export interface PerformanceMetrics {
  cpuUsage: number;
  memoryUsage: MemoryInfo;
  fps: number;
  batteryLevel: number;
  temperature: number;
  timestamp: number;
}

export interface MemoryInfo {
  total: number;      // MB
  used: number;       // MB
  free: number;       // MB
  appUsed: number;    // MB
}

/**
 * 获取CPU使用率
 */
export async function getCpuUsage(): Promise<number> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "dumpsys cpuinfo | grep TOTAL"',
      { timeout: 5000 }
    );
    const match = stdout.match(/(\d+(?:\.\d+)?)%/);
    return match ? parseFloat(match[1]) : 0;
  } catch (e) {
    error("Failed to get CPU usage:", e);
    return 0;
  }
}

/**
 * 获取内存使用情况
 */
export async function getMemoryInfo(packageName?: string): Promise<MemoryInfo> {
  try {
    // 获取系统内存
    const { stdout: meminfo } = await execAsyncWithTimeout(
      'adb shell "cat /proc/meminfo"',
      { timeout: 5000 }
    );
    
    const totalMatch = meminfo.match(/MemTotal:\s+(\d+)/);
    const freeMatch = meminfo.match(/MemFree:\s+(\d+)/);
    const availableMatch = meminfo.match(/MemAvailable:\s+(\d+)/);
    
    const totalKB = totalMatch ? parseInt(totalMatch[1]) : 0;
    const availableKB = availableMatch ? parseInt(availableMatch[1]) : (freeMatch ? parseInt(freeMatch[1]) : 0);
    const usedKB = totalKB - availableKB;
    
    let appUsedKB = 0;
    if (packageName) {
      try {
        const { stdout: appMem } = await execAsyncWithTimeout(
          `adb shell "dumpsys meminfo ${packageName} | grep 'TOTAL PSS'"`,
          { timeout: 5000 }
        );
        const appMatch = appMem.match(/(\d+)/);
        appUsedKB = appMatch ? parseInt(appMatch[1]) : 0;
      } catch {
        // App not running
      }
    }
    
    return {
      total: Math.round(totalKB / 1024),
      used: Math.round(usedKB / 1024),
      free: Math.round(availableKB / 1024),
      appUsed: Math.round(appUsedKB / 1024),
    };
  } catch (e) {
    error("Failed to get memory info:", e);
    return { total: 0, used: 0, free: 0, appUsed: 0 };
  }
}

/**
 * 获取FPS（每秒帧率）
 * 需要设备支持 gfxinfo
 */
export async function getFps(packageName: string): Promise<number> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `adb shell "dumpsys gfxinfo ${packageName} | grep 'Frames produced'"`,
      { timeout: 5000 }
    );
    const match = stdout.match(/(\d+) frames produced/);
    return match ? parseInt(match[1]) : 0;
  } catch (e) {
    error("Failed to get FPS:", e);
    return 0;
  }
}

/**
 * 获取电池信息
 */
export async function getBatteryInfo(): Promise<{ level: number; temperature: number }> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "dumpsys battery"',
      { timeout: 5000 }
    );
    
    const levelMatch = stdout.match(/level: (\d+)/);
    const tempMatch = stdout.match(/temperature: (\d+)/);
    
    return {
      level: levelMatch ? parseInt(levelMatch[1]) : 0,
      temperature: tempMatch ? parseInt(tempMatch[1]) / 10 : 0, // Convert from tenths of degree
    };
  } catch (e) {
    error("Failed to get battery info:", e);
    return { level: 0, temperature: 0 };
  }
}

/**
 * 收集完整性能指标
 */
export async function collectPerformanceMetrics(
  packageName?: string
): Promise<PerformanceMetrics> {
  const [cpuUsage, memoryUsage, batteryInfo] = await Promise.all([
    getCpuUsage(),
    getMemoryInfo(packageName),
    getBatteryInfo(),
  ]);
  
  const fps = packageName ? await getFps(packageName) : 0;
  
  return {
    cpuUsage,
    memoryUsage,
    fps,
    batteryLevel: batteryInfo.level,
    temperature: batteryInfo.temperature,
    timestamp: Date.now(),
  };
}

/**
 * 格式化性能报告
 */
export function formatPerformanceReport(metrics: PerformanceMetrics): string {
  const lines = [
    "═══════════════════════════════════════════",
    "📊 PERFORMANCE METRICS REPORT",
    "═══════════════════════════════════════════",
    `🕐 Time: ${new Date(metrics.timestamp).toLocaleString()}`,
    "",
    `💻 CPU Usage: ${metrics.cpuUsage.toFixed(1)}%`,
    `📝 Memory:`,
    `   Total: ${metrics.memoryUsage.total} MB`,
    `   Used:  ${metrics.memoryUsage.used} MB`,
    `   Free:  ${metrics.memoryUsage.free} MB`,
    `   App:   ${metrics.memoryUsage.appUsed} MB`,
    `🎮 FPS: ${metrics.fps}`,
    `🔋 Battery: ${metrics.batteryLevel}%`,
    `🌡️  Temperature: ${metrics.temperature}°C`,
    "═══════════════════════════════════════════",
  ];
  
  return lines.join("\n");
}
