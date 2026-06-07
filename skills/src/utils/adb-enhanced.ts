import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";

/**
 * 增强的ADB工具链
 * 设备管理、应用管理、文件操作、网络调试
 */

export interface DeviceInfo {
  serial: string;
  state: string;
  model?: string;
  androidVersion?: string;
  sdkVersion?: string;
  screenResolution?: string;
  density?: string;
}

export interface AppInfo {
  packageName: string;
  versionName: string;
  versionCode: string;
  firstInstallTime: string;
  lastUpdateTime: string;
  dataDir: string;
}

/**
 * 获取所有连接的设备
 */
export async function listDevices(): Promise<DeviceInfo[]> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      "adb devices -l",
      { timeout: 10000 }
    );
    
    const devices: DeviceInfo[] = [];
    const lines = stdout.split("\n").slice(1); // Skip header
    
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || !trimmed.includes("device")) continue;
      
      const parts = trimmed.split(/\s+/);
      const serial = parts[0];
      const state = parts[1];
      
      // Extract additional info
      const modelMatch = line.match(/model:(\S+)/);
      const deviceMatch = line.match(/device:(\S+)/);
      
      devices.push({
        serial,
        state,
        model: modelMatch ? modelMatch[1] : deviceMatch ? deviceMatch[1] : undefined,
      });
    }
    
    // Get detailed info for each device — 4 个独立查询并行执行
    await Promise.all(devices.map(async (device) => {
      try {
        const [version, sdk, resolution, density] = await Promise.all([
          execAsyncWithTimeout(`adb -s ${device.serial} shell getprop ro.build.version.release`, { timeout: 5000 }),
          execAsyncWithTimeout(`adb -s ${device.serial} shell getprop ro.build.version.sdk`, { timeout: 5000 }),
          execAsyncWithTimeout(`adb -s ${device.serial} shell wm size`, { timeout: 5000 }),
          execAsyncWithTimeout(`adb -s ${device.serial} shell wm density`, { timeout: 5000 }),
        ]);
        device.androidVersion = version.stdout.trim();
        device.sdkVersion = sdk.stdout.trim();
        const resMatch = resolution.stdout.match(/(\d+x\d+)/);
        device.screenResolution = resMatch ? resMatch[1] : undefined;
        const densityMatch = density.stdout.match(/(\d+)dpi/);
        device.density = densityMatch ? densityMatch[1] : undefined;
      } catch (e) {
        error(`Failed to get device info for ${device.serial}:`, e);
      }
    }));

    return devices;
  } catch (e) {
    error("Failed to list devices:", e);
    return [];
  }
}

/**
 * 获取设备详细信息
 */
export async function getDeviceDetails(serial: string): Promise<Record<string, string>> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `adb -s ${serial} shell getprop`,
      { timeout: 10000 }
    );
    
    const details: Record<string, string> = {};
    const lines = stdout.split("\n");
    
    for (const line of lines) {
      const match = line.match(/\[([^\]]+)\]: \[([^\]]*)\]/);
      if (match) {
        details[match[1]] = match[2];
      }
    }
    
    return details;
  } catch (e) {
    error("Failed to get device details:", e);
    return {};
  }
}

/**
 * 安装APK
 */
export async function installApk(
  apkPath: string,
  serial?: string,
  options: { reinstall?: boolean; downgrade?: boolean } = {}
): Promise<{ success: boolean; message: string }> {
  try {
    let cmd = serial ? `adb -s ${serial} install` : "adb install";
    
    if (options.reinstall) cmd += " -r";
    if (options.downgrade) cmd += " -d";
    
    cmd += ` "${apkPath}"`;
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 120000 });
    
    return {
      success: stdout.includes("Success"),
      message: stdout.trim(),
    };
  } catch (e) {
    const err = e as Error & { stderr?: string };
    return {
      success: false,
      message: err.stderr || err.message || "Install failed",
    };
  }
}

/**
 * 卸载应用
 */
export async function uninstallApp(
  packageName: string,
  serial?: string,
  keepData: boolean = false
): Promise<{ success: boolean; message: string }> {
  try {
    let cmd = serial ? `adb -s ${serial} uninstall` : "adb uninstall";
    if (keepData) cmd += " -k";
    cmd += ` ${packageName}`;
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 30000 });
    
    return {
      success: stdout.includes("Success"),
      message: stdout.trim(),
    };
  } catch (e) {
    const err = e as Error & { stderr?: string };
    return {
      success: false,
      message: err.stderr || err.message || "Uninstall failed",
    };
  }
}

/**
 * 启动应用
 */
export async function startApp(
  packageName: string,
  activity?: string,
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    let cmd = serial ? `adb -s ${serial} shell am start` : "adb shell am start";
    
    if (activity) {
      cmd += ` -n ${packageName}/${activity}`;
    } else {
      cmd += ` -n ${packageName}/.MainActivity`;
    }
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 10000 });
    
    return {
      success: stdout.includes("Starting") || stdout.includes("Warning"),
      message: stdout.trim(),
    };
  } catch (e) {
    const err = e as Error & { stderr?: string };
    return {
      success: false,
      message: err.stderr || err.message || "Start failed",
    };
  }
}

/**
 * 停止应用
 */
export async function stopApp(
  packageName: string,
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial
      ? `adb -s ${serial} shell am force-stop ${packageName}`
      : `adb shell am force-stop ${packageName}`;
    
    await execAsyncWithTimeout(cmd, { timeout: 10000 });
    
    return {
      success: true,
      message: `Stopped ${packageName}`,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Stop failed",
    };
  }
}

/**
 * 清除应用数据
 */
export async function clearAppData(
  packageName: string,
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial
      ? `adb -s ${serial} shell pm clear ${packageName}`
      : `adb shell pm clear ${packageName}`;
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 30000 });
    
    return {
      success: stdout.includes("Success"),
      message: stdout.trim(),
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Clear data failed",
    };
  }
}

/**
 * 获取已安装应用列表
 */
export async function listInstalledApps(
  serial?: string,
  options: { system?: boolean; thirdParty?: boolean } = {}
): Promise<AppInfo[]> {
  try {
    let cmd = serial ? `adb -s ${serial} shell pm list packages` : "adb shell pm list packages";
    
    if (options.system) cmd += " -s";
    if (options.thirdParty) cmd += " -3";
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 30000 });
    
    const apps: AppInfo[] = [];
    const lines = stdout.split("\n");
    
    for (const line of lines) {
      const match = line.match(/package:(.+)/);
      if (match) {
        const packageName = match[1].trim();
        apps.push({
          packageName,
          versionName: "unknown",
          versionCode: "unknown",
          firstInstallTime: "unknown",
          lastUpdateTime: "unknown",
          dataDir: "unknown",
        });
      }
    }

    // 用单次 dumpsys 拉取所有包信息（带分桶 grep），消除 N+1
    if (apps.length > 0) {
      try {
        const serialFlag = serial ? `-s ${serial} ` : "";
        const { stdout: bulk } = await execAsyncWithTimeout(
          `adb ${serialFlag}shell dumpsys package`,
          { timeout: 30000 }
        );

        // 为每个包构建一个快速定位的 map
        const packageSet = new Set(apps.map((a) => a.packageName));
        const blocks = bulk.split(/(?=^Package\s\[[^\]]+\]\s)/m);

        for (const block of blocks) {
          const headerMatch = block.match(/^Package\s\[[^\]]+\]\s([\w.]+)/);
          if (!headerMatch) continue;
          const pkg = headerMatch[1];
          if (!packageSet.has(pkg)) continue;

          const target = apps.find((a) => a.packageName === pkg)!;
          const versionNameMatch = block.match(/versionName=([^\s]+)/);
          const versionCodeMatch = block.match(/versionCode=(\d+)/);
          const firstInstallMatch = block.match(/firstInstallTime=([^\s]+)/);
          const lastUpdateMatch = block.match(/lastUpdateTime=([^\s]+)/);
          const dataDirMatch = block.match(/dataDir=([^\s]+)/);
          if (versionNameMatch) target.versionName = versionNameMatch[1];
          if (versionCodeMatch) target.versionCode = versionCodeMatch[1];
          if (firstInstallMatch) target.firstInstallTime = firstInstallMatch[1];
          if (lastUpdateMatch) target.lastUpdateTime = lastUpdateMatch[1];
          if (dataDirMatch) target.dataDir = dataDirMatch[1];
        }
      } catch (e) {
        error("Bulk dumpsys package failed, returning basic list:", e);
      }
    }
    
    return apps;
  } catch (e) {
    error("Failed to list apps:", e);
    return [];
  }
}

/**
 * 推送文件到设备
 */
export async function pushFile(
  localPath: string,
  remotePath: string,
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial
      ? `adb -s ${serial} push "${localPath}" "${remotePath}"`
      : `adb push "${localPath}" "${remotePath}"`;
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 60000 });
    
    return {
      success: stdout.includes("pushed") || stdout.includes("1 file pushed"),
      message: stdout.trim(),
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Push failed",
    };
  }
}

/**
 * 从设备拉取文件
 */
export async function pullFile(
  remotePath: string,
  localPath: string,
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial
      ? `adb -s ${serial} pull "${remotePath}" "${localPath}"`
      : `adb pull "${remotePath}" "${localPath}"`;
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 60000 });
    
    return {
      success: stdout.includes("pulled") || stdout.includes("1 file pulled"),
      message: stdout.trim(),
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Pull failed",
    };
  }
}

/**
 * 执行shell命令
 */
export async function shellCommand(
  command: string,
  serial?: string
): Promise<{ success: boolean; output: string }> {
  try {
    const cmd = serial
      ? `adb -s ${serial} shell ${command}`
      : `adb shell ${command}`;

    const { stdout, stderr } = await execAsyncWithTimeout(cmd, { timeout: 30000 });

    return {
      success: true,
      output: stdout + (stderr ? `\nstderr: ${stderr}` : ""),
    };
  } catch (e) {
    const err = e as Error & { stderr?: string };
    return {
      success: false,
      output: err.stderr || err.message || "Command failed",
    };
  }
}

/**
 * 获取当前 Activity（按包名过滤）。null 表示应用未运行或 Activity 不可读。
 * 修复：旧版贪婪正则 `\.(\w+Activity)` 会匹配出 "ActivityActivity"
 */
export async function getCurrentActivity(packageName?: string): Promise<string | null> {
  try {
    const filter = packageName ? ` | grep "${packageName}"` : "";
    const { stdout } = await execAsyncWithTimeout(
      `adb shell dumpsys activity activities | grep "mResumedActivity"${filter}`,
      { timeout: 5000 }
    );
    const match = stdout.match(/\.([A-Za-z][\w$]*)/);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}

/**
 * 获取网络状态
 */
export async function getNetworkState(serial?: string): Promise<{
  wifi: boolean;
  mobile: boolean;
  airplaneMode: boolean;
}> {
  try {
    const serialFlag = serial ? `-s ${serial} ` : "";
    // 一次 shell 调用同时查三项，避免 3 次 ADB 往返
    const { stdout } = await execAsyncWithTimeout(
      `adb ${serialFlag}shell "settings get global wifi_on; settings get global mobile_data; settings get global airplane_mode_on"`,
      { timeout: 5000 }
    );
    const [w, m, a] = stdout.trim().split(/\s+/);
    return {
      wifi: w === "1",
      mobile: m === "1",
      airplaneMode: a === "1",
    };
  } catch (e) {
    error("Failed to get network state:", e);
    return { wifi: false, mobile: false, airplaneMode: false };
  }
}

/**
 * 设置网络状态
 */
export async function setNetworkState(
  type: "wifi" | "mobile" | "airplane",
  enabled: boolean,
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial ? `adb -s ${serial} shell` : "adb shell";
    let settingCmd = "";
    
    switch (type) {
      case "wifi":
        settingCmd = `${cmd} "svc wifi ${enabled ? "enable" : "disable"}"`;
        break;
      case "mobile":
        settingCmd = `${cmd} "svc data ${enabled ? "enable" : "disable"}"`;
        break;
      case "airplane":
        settingCmd = `${cmd} "settings put global airplane_mode_on ${enabled ? 1 : 0}"`;
        break;
    }
    
    await execAsyncWithTimeout(settingCmd, { timeout: 10000 });
    
    return {
      success: true,
      message: `${type} ${enabled ? "enabled" : "disabled"}`,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Failed to set network state",
    };
  }
}

/**
 * 录制屏幕
 */
export async function recordScreen(
  duration: number = 10,
  outputPath: string = "./screen_record.mp4",
  serial?: string
): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial ? `adb -s ${serial} shell` : "adb shell";
    
    // Start recording
    await execAsyncWithTimeout(
      `${cmd} "screenrecord --time-limit ${duration} /sdcard/screen_record.mp4 &"`,
      { timeout: 5000 }
    );
    
    // Wait for recording
    await new Promise((resolve) => setTimeout(resolve, (duration + 2) * 1000));
    
    // Pull the recording
    const pullCmd = serial
      ? `adb -s ${serial} pull /sdcard/screen_record.mp4 "${outputPath}"`
      : `adb pull /sdcard/screen_record.mp4 "${outputPath}"`;
    
    await execAsyncWithTimeout(pullCmd, { timeout: 60000 });
    
    return {
      success: true,
      message: `Screen recording saved to ${outputPath}`,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Recording failed",
    };
  }
}

/**
 * 获取日志
 */
export async function getLogs(
  options: {
    packageName?: string;
    filter?: string;
    lines?: number;
    serial?: string;
  } = {}
): Promise<{ success: boolean; logs: string }> {
  try {
    let cmd = options.serial ? `adb -s ${options.serial} logcat` : "adb logcat";
    
    if (options.lines) {
      cmd += ` -t ${options.lines}`;
    }
    
    if (options.packageName) {
      cmd += ` --pid=$(adb shell pidof -s ${options.packageName})`;
    }
    
    if (options.filter === "crash") {
      cmd += " *:E";
    }
    
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 30000 });
    
    return {
      success: true,
      logs: stdout,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      logs: err.message || "Failed to get logs",
    };
  }
}

/**
 * 清除日志
 */
export async function clearLogs(serial?: string): Promise<{ success: boolean; message: string }> {
  try {
    const cmd = serial ? `adb -s ${serial} logcat -c` : "adb logcat -c";
    await execAsyncWithTimeout(cmd, { timeout: 10000 });
    
    return {
      success: true,
      message: "Logs cleared",
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Failed to clear logs",
    };
  }
}
