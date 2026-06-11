import {
  activeProvider,
  callVisionLlm,
  designToComposeSkeleton,
  encodeImageAsDataUrl,
  error,
  execAsyncWithTimeout,
  extractColorTokens,
  extractComponents,
  extractDesignSpec,
  fileExists,
  getActiveProvider,
  log,
  makeInsecureFetch,
  smartResizeForVision,
  spawnCommand
} from "./chunk-JRWUD5FU.js";

// src/server.ts
import { config as loadEnv } from "dotenv";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";

// src/utils/adb.ts
import { exec as execCb, spawn as spawnCb } from "child_process";
import { promisify } from "util";
import { mkdirSync, writeFileSync } from "fs";
import { dirname } from "path";
var execAsync = promisify(execCb);
var SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || "./screenshots";
var deviceChecked = false;
var deviceAvailable = false;
var lastCheckAt = 0;
var DEVICE_CHECK_TTL_MS = 1e4;
async function checkDevice(force = false) {
  const now = Date.now();
  if (!force && deviceChecked && now - lastCheckAt < DEVICE_CHECK_TTL_MS) {
    return {
      available: deviceAvailable,
      message: deviceAvailable ? "Device ready" : "No device available"
    };
  }
  try {
    const { stdout } = await execAsync("adb devices", { timeout: 5e3 });
    const lines = stdout.trim().split("\n").slice(1);
    const devices = lines.map((line) => line.trim()).filter((line) => line.length > 0 && line.includes("device")).map((line) => line.split("	")[0]);
    if (devices.length === 0) {
      deviceAvailable = false;
      deviceChecked = true;
      lastCheckAt = now;
      return { available: false, message: "No Android device connected. Please connect a device or start an emulator." };
    }
    const readyDevice = lines.find((line) => line.includes("	device"));
    if (!readyDevice) {
      deviceAvailable = false;
      deviceChecked = true;
      lastCheckAt = now;
      return { available: false, message: "Device found but not ready (may be unauthorized or offline)." };
    }
    deviceAvailable = true;
    deviceChecked = true;
    lastCheckAt = now;
    log(`Device ready: ${readyDevice.split("	")[0]}`);
    return { available: true, message: `Device ready: ${readyDevice.split("	")[0]}` };
  } catch (e) {
    deviceAvailable = false;
    deviceChecked = true;
    lastCheckAt = now;
    return { available: false, message: `ADB check failed: ${e}` };
  }
}
async function adbExec(args) {
  const deviceStatus = await checkDevice();
  if (!deviceStatus.available) {
    throw new Error(deviceStatus.message);
  }
  const cmd = `adb ${args}`;
  log(`exec: ${cmd}`);
  const { stdout, stderr } = await execAsync(cmd, { timeout: 3e4 });
  if (stderr) log(`stderr: ${stderr}`);
  return stdout.trim();
}
async function screenshot(savePath) {
  const deviceStatus = await checkDevice();
  if (!deviceStatus.available) throw new Error(deviceStatus.message);
  const timestamp = Date.now();
  const filename = `screenshot_${timestamp}.png`;
  const localPath = savePath || `${SCREENSHOT_DIR}/${filename}`;
  try {
    mkdirSync(dirname(localPath) || ".", { recursive: true });
  } catch {
  }
  await new Promise((resolve, reject) => {
    const child = spawnCb("adb", ["exec-out", "screencap", "-p"]);
    const chunks = [];
    let settled = false;
    const finish = (err) => {
      if (settled) return;
      settled = true;
      if (err) return reject(err);
      try {
        writeFileSync(localPath, Buffer.concat(chunks));
        resolve();
      } catch (e) {
        reject(e);
      }
    };
    child.stdout?.on("data", (c) => chunks.push(c));
    child.stderr?.on("data", (c) => log(`screencap stderr: ${c.toString().slice(0, 200)}`));
    child.on("error", finish);
    child.on("close", (code) => code === 0 || code === null ? finish() : finish(new Error(`screencap exited ${code}`)));
    setTimeout(() => finish(new Error("screenshot timeout (15s)")), 15e3);
  });
  log(`screenshot saved to ${localPath}`);
  return { path: localPath, timestamp };
}
async function tap(x, y) {
  await adbExec(`shell input tap ${x} ${y}`);
}
async function swipe(x1, y1, x2, y2, duration = 300) {
  await adbExec(`shell input swipe ${x1} ${y1} ${x2} ${y2} ${duration}`);
}
async function inputText(text) {
  const escaped = text.replace(/ /g, "%s");
  await adbExec(`shell input text "${escaped}"`);
}
async function pressKey(key) {
  const keyMap = {
    HOME: 3,
    BACK: 4,
    ENTER: 66,
    MENU: 82,
    POWER: 26,
    VOLUME_UP: 24,
    VOLUME_DOWN: 25,
    DEL: 67
  };
  const code = keyMap[key.toUpperCase()] ?? parseInt(key);
  await adbExec(`shell input keyevent ${code}`);
}
async function launchApp(packageName, activity) {
  const component = activity ? `${packageName}/${activity}` : packageName;
  await adbExec(`shell am start -n ${component}`);
}

// src/tools/screenshot.ts
import fs from "fs";
async function handleScreenshot(args) {
  try {
    const savePath = args.savePath;
    const result = await screenshot(savePath);
    let sizeBytes = 0;
    try {
      sizeBytes = fs.statSync(result.path).size;
    } catch {
    }
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        path: result.path,
        timestamp: result.timestamp,
        sizeBytes
      }) }]
    };
  } catch (err) {
    error("screenshot failed:", err);
    return {
      isError: true,
      content: [{ type: "text", text: JSON.stringify({ success: false, error: String(err) }) }]
    };
  }
}

// src/utils/adb-enhanced.ts
async function listDevices() {
  try {
    const { stdout } = await execAsyncWithTimeout(
      "adb devices -l",
      { timeout: 1e4 }
    );
    const devices = [];
    const lines = stdout.split("\n").slice(1);
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || !trimmed.includes("device")) continue;
      const parts = trimmed.split(/\s+/);
      const serial = parts[0];
      const state = parts[1];
      const modelMatch = line.match(/model:(\S+)/);
      const deviceMatch = line.match(/device:(\S+)/);
      devices.push({
        serial,
        state,
        model: modelMatch ? modelMatch[1] : deviceMatch ? deviceMatch[1] : void 0
      });
    }
    await Promise.all(devices.map(async (device) => {
      try {
        const [version, sdk, resolution, density] = await Promise.all([
          execAsyncWithTimeout(`adb -s ${device.serial} shell getprop ro.build.version.release`, { timeout: 5e3 }),
          execAsyncWithTimeout(`adb -s ${device.serial} shell getprop ro.build.version.sdk`, { timeout: 5e3 }),
          execAsyncWithTimeout(`adb -s ${device.serial} shell wm size`, { timeout: 5e3 }),
          execAsyncWithTimeout(`adb -s ${device.serial} shell wm density`, { timeout: 5e3 })
        ]);
        device.androidVersion = version.stdout.trim();
        device.sdkVersion = sdk.stdout.trim();
        const resMatch = resolution.stdout.match(/(\d+x\d+)/);
        device.screenResolution = resMatch ? resMatch[1] : void 0;
        const densityMatch = density.stdout.match(/(\d+)dpi/);
        device.density = densityMatch ? densityMatch[1] : void 0;
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
async function getDeviceDetails(serial) {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `adb -s ${serial} shell getprop`,
      { timeout: 1e4 }
    );
    const details = {};
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
async function installApk(apkPath, serial, options = {}) {
  try {
    let cmd = serial ? `adb -s ${serial} install` : "adb install";
    if (options.reinstall) cmd += " -r";
    if (options.downgrade) cmd += " -d";
    cmd += ` "${apkPath}"`;
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 12e4 });
    return {
      success: stdout.includes("Success"),
      message: stdout.trim()
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.stderr || err.message || "Install failed"
    };
  }
}
async function uninstallApp(packageName, serial, keepData = false) {
  try {
    let cmd = serial ? `adb -s ${serial} uninstall` : "adb uninstall";
    if (keepData) cmd += " -k";
    cmd += ` ${packageName}`;
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 3e4 });
    return {
      success: stdout.includes("Success"),
      message: stdout.trim()
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.stderr || err.message || "Uninstall failed"
    };
  }
}
async function startApp(packageName, activity, serial) {
  try {
    let cmd = serial ? `adb -s ${serial} shell am start` : "adb shell am start";
    if (activity) {
      cmd += ` -n ${packageName}/${activity}`;
    } else {
      cmd += ` -n ${packageName}/.MainActivity`;
    }
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 1e4 });
    return {
      success: stdout.includes("Starting") || stdout.includes("Warning"),
      message: stdout.trim()
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.stderr || err.message || "Start failed"
    };
  }
}
async function stopApp(packageName, serial) {
  try {
    const cmd = serial ? `adb -s ${serial} shell am force-stop ${packageName}` : `adb shell am force-stop ${packageName}`;
    await execAsyncWithTimeout(cmd, { timeout: 1e4 });
    return {
      success: true,
      message: `Stopped ${packageName}`
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Stop failed"
    };
  }
}
async function clearAppData(packageName, serial) {
  try {
    const cmd = serial ? `adb -s ${serial} shell pm clear ${packageName}` : `adb shell pm clear ${packageName}`;
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 3e4 });
    return {
      success: stdout.includes("Success"),
      message: stdout.trim()
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Clear data failed"
    };
  }
}
async function listInstalledApps(serial, options = {}) {
  try {
    let cmd = serial ? `adb -s ${serial} shell pm list packages` : "adb shell pm list packages";
    if (options.system) cmd += " -s";
    if (options.thirdParty) cmd += " -3";
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 3e4 });
    const apps = [];
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
          dataDir: "unknown"
        });
      }
    }
    if (apps.length > 0) {
      try {
        const serialFlag = serial ? `-s ${serial} ` : "";
        const { stdout: bulk } = await execAsyncWithTimeout(
          `adb ${serialFlag}shell dumpsys package`,
          { timeout: 3e4 }
        );
        const packageSet = new Set(apps.map((a) => a.packageName));
        const blocks = bulk.split(/(?=^Package\s\[[^\]]+\]\s)/m);
        for (const block of blocks) {
          const headerMatch = block.match(/^Package\s\[[^\]]+\]\s([\w.]+)/);
          if (!headerMatch) continue;
          const pkg = headerMatch[1];
          if (!packageSet.has(pkg)) continue;
          const target = apps.find((a) => a.packageName === pkg);
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
async function pushFile(localPath, remotePath, serial) {
  try {
    const cmd = serial ? `adb -s ${serial} push "${localPath}" "${remotePath}"` : `adb push "${localPath}" "${remotePath}"`;
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 6e4 });
    return {
      success: stdout.includes("pushed") || stdout.includes("1 file pushed"),
      message: stdout.trim()
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Push failed"
    };
  }
}
async function pullFile(remotePath, localPath, serial) {
  try {
    const cmd = serial ? `adb -s ${serial} pull "${remotePath}" "${localPath}"` : `adb pull "${remotePath}" "${localPath}"`;
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 6e4 });
    return {
      success: stdout.includes("pulled") || stdout.includes("1 file pulled"),
      message: stdout.trim()
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Pull failed"
    };
  }
}
async function shellCommand(command, serial) {
  try {
    const cmd = serial ? `adb -s ${serial} shell ${command}` : `adb shell ${command}`;
    const { stdout, stderr } = await execAsyncWithTimeout(cmd, { timeout: 3e4 });
    return {
      success: true,
      output: stdout + (stderr ? `
stderr: ${stderr}` : "")
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      output: err.stderr || err.message || "Command failed"
    };
  }
}
async function getNetworkState(serial) {
  try {
    const serialFlag = serial ? `-s ${serial} ` : "";
    const { stdout } = await execAsyncWithTimeout(
      `adb ${serialFlag}shell "settings get global wifi_on; settings get global mobile_data; settings get global airplane_mode_on"`,
      { timeout: 5e3 }
    );
    const [w, m, a] = stdout.trim().split(/\s+/);
    return {
      wifi: w === "1",
      mobile: m === "1",
      airplaneMode: a === "1"
    };
  } catch (e) {
    error("Failed to get network state:", e);
    return { wifi: false, mobile: false, airplaneMode: false };
  }
}
async function setNetworkState(type, enabled, serial) {
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
    await execAsyncWithTimeout(settingCmd, { timeout: 1e4 });
    return {
      success: true,
      message: `${type} ${enabled ? "enabled" : "disabled"}`
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Failed to set network state"
    };
  }
}
async function recordScreen(duration = 10, outputPath = "./screen_record.mp4", serial) {
  try {
    const cmd = serial ? `adb -s ${serial} shell` : "adb shell";
    await execAsyncWithTimeout(
      `${cmd} "screenrecord --time-limit ${duration} /sdcard/screen_record.mp4 &"`,
      { timeout: 5e3 }
    );
    await new Promise((resolve) => setTimeout(resolve, (duration + 2) * 1e3));
    const pullCmd = serial ? `adb -s ${serial} pull /sdcard/screen_record.mp4 "${outputPath}"` : `adb pull /sdcard/screen_record.mp4 "${outputPath}"`;
    await execAsyncWithTimeout(pullCmd, { timeout: 6e4 });
    return {
      success: true,
      message: `Screen recording saved to ${outputPath}`
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Recording failed"
    };
  }
}
async function clearLogs(serial) {
  try {
    const cmd = serial ? `adb -s ${serial} logcat -c` : "adb logcat -c";
    await execAsyncWithTimeout(cmd, { timeout: 1e4 });
    return {
      success: true,
      message: "Logs cleared"
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Failed to clear logs"
    };
  }
}

// src/tools/interaction.ts
async function handleTap(args) {
  const { x, y } = args;
  await tap(x, y);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "tap", x, y }) }]
  };
}
async function handleSwipe(args) {
  const { x1, y1, x2, y2, duration = 300 } = args;
  await swipe(x1, y1, x2, y2, duration);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "swipe" }) }]
  };
}
async function handleInputText(args) {
  const { text } = args;
  await inputText(text);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "input_text", text }) }]
  };
}
async function handlePressKey(args) {
  const { key } = args;
  await pressKey(key);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "press_key", key }) }]
  };
}
async function handleBuild(args) {
  const { projectPath = ".", variant = "debug" } = args;
  const start = Date.now();
  const gradlew = variant === "release" ? "assembleRelease" : "assembleDebug";
  log(`build: ./gradlew ${gradlew} in ${projectPath}`);
  try {
    const { stdout, stderr, exitCode } = await spawnCommand("./gradlew", [gradlew], {
      cwd: projectPath,
      timeout: 18e4
      // 3 minutes max
    });
    if (exitCode !== 0) {
      return {
        isError: true,
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: `Gradle exited with code ${exitCode}`,
            stderr: stderr.slice(0, 2e3),
            buildTime: Date.now() - start
          })
        }]
      };
    }
    const combinedOutput = stdout + stderr;
    const apkPath = combinedOutput.match(/outputs\/apk\/[^\s]+\.apk/)?.[0] || "";
    return {
      content: [{
        type: "text",
        text: JSON.stringify({ success: true, apkPath, buildTime: Date.now() - start, variant })
      }]
    };
  } catch (e) {
    const err = e;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({ success: false, error: err.message || "build failed", buildTime: Date.now() - start })
      }]
    };
  }
}
async function handleInstallAndLaunch(args) {
  const { apkPath, packageName, activity } = args;
  if (apkPath) {
    await installApk(apkPath);
  }
  await launchApp(packageName, activity);
  return {
    content: [{
      type: "text",
      text: JSON.stringify({ success: true, action: "install_and_launch", packageName })
    }]
  };
}

// src/tools/verify.ts
import { readFileSync, writeFileSync as writeFileSync2, mkdirSync as mkdirSync2 } from "fs";
import sharp from "sharp";
import pixelmatch from "pixelmatch";
import { PNG } from "pngjs";
function ensureDir(dir) {
  try {
    mkdirSync2(dir, { recursive: true });
  } catch {
  }
}
async function handleVerifyUI(args) {
  const { type, baselinePath, currentPath, checkText, checkColor, x, y } = args;
  switch (type) {
    case "compare": {
      if (!baselinePath || !currentPath) {
        return {
          isError: true,
          content: [{ type: "text", text: "compare requires baselinePath and currentPath" }]
        };
      }
      return handleCompare(baselinePath, currentPath);
    }
    case "color": {
      if (!currentPath || x === void 0 || y === void 0 || !checkColor) {
        return {
          isError: true,
          content: [{ type: "text", text: "color check requires currentPath, x, y, checkColor" }]
        };
      }
      return handleColorCheck(currentPath, x, y, checkColor);
    }
    case "ocr": {
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({
              note: "OCR requires tesseract.js which is not bundled. Install with: npm install tesseract.js",
              checkText
            })
          }
        ]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown verify type: ${type}` }]
      };
  }
}
async function handleCompare(baselinePath, currentPath) {
  try {
    const baselineBuf = readFileSync(baselinePath);
    const currentBuf = readFileSync(currentPath);
    const baselinePng = PNG.sync.read(baselineBuf);
    const currentPng = PNG.sync.read(currentBuf);
    const { width, height } = baselinePng;
    const diff = new PNG({ width, height });
    const diffPixels = pixelmatch(baselinePng.data, currentPng.data, diff.data, width, height, {
      threshold: 0.1,
      includeAA: true
    });
    const diffPercentage = diffPixels / (width * height) * 100;
    const diffDir = "./reports";
    ensureDir(diffDir);
    const diffPath = `${diffDir}/diff_${Date.now()}.png`;
    writeFileSync2(diffPath, PNG.sync.write(diff));
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({
            diffPixels,
            diffPercentage: parseFloat(diffPercentage.toFixed(2)),
            isMatch: diffPercentage < 1,
            diffImagePath: diffPath
          })
        }
      ]
    };
  } catch (e) {
    const err = e;
    return {
      isError: true,
      content: [{ type: "text", text: `Compare failed: ${err.message}` }]
    };
  }
}
async function handleColorCheck(screenshotPath, x, y, expectedHex) {
  try {
    const buffer = await sharp(screenshotPath).extract({ left: Math.round(x), top: Math.round(y), width: 1, height: 1 }).raw().toBuffer();
    const data = buffer;
    const [r, g, b] = data;
    const actualHex = `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({
            match: actualHex.toLowerCase() === expectedHex.toLowerCase(),
            expected: expectedHex,
            actual: actualHex,
            x,
            y
          })
        }
      ]
    };
  } catch (e) {
    const err = e;
    return {
      isError: true,
      content: [{ type: "text", text: `Color check failed: ${err.message}` }]
    };
  }
}

// src/tools/logs.ts
var DEFAULT_LINES = 50;
var CRASH_TAGS = ["AndroidRuntime:E", "ActivityManager:E", "DEBUG:E", "System.err:W", "*:S"];
async function safePidof(packageName) {
  try {
    const { stdout } = await execAsyncWithTimeout(`adb shell pidof -s ${packageName}`, { timeout: 3e3 });
    const pid = stdout.trim();
    return pid || null;
  } catch {
    return null;
  }
}
async function handleGetLogs(args) {
  try {
    const packageName = args.packageName;
    const filter = args.filter ?? "crash";
    const lines = args.lines ?? DEFAULT_LINES;
    const serial = args.serial;
    const serialFlag = serial ? `-s ${serial}` : "";
    let cmd;
    let mode;
    if (filter === "all" && packageName) {
      const pid2 = await safePidof(packageName);
      if (pid2) {
        cmd = `adb ${serialFlag} logcat -d --pid=${pid2} -t ${lines}`;
        mode = `all+pid(${pid2})`;
      } else {
        cmd = `adb ${serialFlag} logcat -d -t ${lines}`;
        mode = "all (app not running, showing global recent)";
      }
    } else if (filter === "all") {
      cmd = `adb ${serialFlag} logcat -d -t ${lines}`;
      mode = "all";
    } else {
      cmd = `adb ${serialFlag} logcat -d -s ${CRASH_TAGS.join(" ")} -t ${lines}`;
      mode = "crash";
    }
    log(`get_logs: ${cmd}`);
    const { stdout: output, stderr } = await execAsyncWithTimeout(cmd, { timeout: 8e3 });
    const logLines = output.trim() ? output.trim().split("\n") : [];
    let appRunning = null;
    let pid = null;
    if (packageName) {
      pid = await safePidof(packageName);
      appRunning = !!pid;
    }
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        filter,
        mode,
        lines: logLines.length,
        appRunning,
        pid,
        logs: logLines,
        stderr: stderr || void 0
      }) }]
    };
  } catch (e) {
    const err = e;
    error("get_logs failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleClearLogs(args) {
  try {
    const serial = args.serial;
    const result = await clearLogs(serial);
    return {
      content: [{ type: "text", text: JSON.stringify({ success: result.success, message: result.message }) }]
    };
  } catch (e) {
    const err = e;
    error("clear_logs failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// src/tools/analyze.ts
import path from "path";
import { fileURLToPath } from "url";

// src/tools/vision-analyze.ts
import OpenAI from "openai";
import fs2 from "fs";
async function analyzeWithVision(imagePath, prompt, systemPrompt) {
  if (!fs2.existsSync(imagePath)) {
    throw new Error(`File not found: ${imagePath}`);
  }
  const cfg = getActiveProvider();
  const modelId = process.env.VISION_MODEL || cfg.defaultModel;
  const resizedPath = await smartResizeForVision(imagePath);
  try {
    const imageUrl = encodeImageAsDataUrl(resizedPath);
    const defaultPrompt = `You are an Android UI expert. Analyze this screenshot and report:

## Layout
- What type of screen is this? (news feed, detail page, etc.)
- Are there alignment/spacing issues? Be specific about which element and by how much.
- Is the content density appropriate?

## Components
- How many cards/items are visible? List each one.
- Are images loading correctly?
- Is text readable (font size, contrast, truncation)?

## Issues
List every UI problem you see, with:
- Exact location/which component
- What's wrong
- Suggested fix (include specific dp/px/sp values for Jetpack Compose)

## Design Match
- Does the actual implementation match the expected news-feed design?
- Header: is the red correct? (#FF5757)
- Bottom nav: white background? selected tab indicator (#171E38)?
- Cards: proper spacing between them?

Be very specific and quantitative. Measure approximate padding/margins using the screen dimensions as reference.`;
    return await callVisionLlm(
      modelId,
      systemPrompt || "You are an Android UI/UX expert. Always give specific, actionable feedback with exact measurements and Compose code suggestions.",
      prompt || defaultPrompt,
      imageUrl
    );
  } catch (e) {
    error("analyzeWithVision failed:", e);
    throw e;
  } finally {
    if (resizedPath !== imagePath) {
      try {
        fs2.unlinkSync(resizedPath);
      } catch {
      }
    }
  }
}
async function compareWithVision(baselinePath, currentPath, prompt) {
  if (!fs2.existsSync(baselinePath)) throw new Error(`Baseline not found: ${baselinePath}`);
  if (!fs2.existsSync(currentPath)) throw new Error(`Current not found: ${currentPath}`);
  const cfg = getActiveProvider();
  const modelId = process.env.VISION_MODEL || cfg.defaultModel;
  const [bResized, cResized] = await Promise.all([
    smartResizeForVision(baselinePath),
    smartResizeForVision(currentPath)
  ]);
  try {
    const defaultPrompt = `Compare these two Android screenshots. The first is the design/baseline, the second is the current implementation.

Focus on:
1. Layout differences (position, spacing, sizing)
2. Color differences (especially the header red and bottom nav)
3. Content differences (more/less cards, different text)
4. Specific issues introduced in the current version

For each difference, state whether it's acceptable or needs fixing, and suggest exact Compose code changes.`;
    const imageBaseUrl1 = encodeImageAsDataUrl(bResized);
    const imageBaseUrl2 = encodeImageAsDataUrl(cResized);
    const systemPrompt = "You are an Android UI testing expert. Compare screenshots precisely and give actionable feedback.";
    const userContent = [
      { type: "text", text: "Here is the baseline/design screenshot:" },
      { type: "image_url", image_url: { url: imageBaseUrl1 } },
      { type: "text", text: "Here is the current implementation screenshot:" },
      { type: "image_url", image_url: { url: imageBaseUrl2 } },
      { type: "text", text: prompt || defaultPrompt }
    ];
    return await callVisionLlmMultiContent(modelId, systemPrompt, userContent);
  } catch (e) {
    error("compareWithVision failed:", e);
    throw e;
  } finally {
    if (bResized !== baselinePath) {
      try {
        fs2.unlinkSync(bResized);
      } catch {
      }
    }
    if (cResized !== currentPath) {
      try {
        fs2.unlinkSync(cResized);
      } catch {
      }
    }
  }
}
var multiClient = null;
var multiClientKey = "";
function getMultiClient() {
  const cfg = activeProvider();
  const cacheKey = `${cfg.provider}|${cfg.apiKeyEnv}|${cfg.insecureTLS}`;
  if (multiClient && multiClientKey === cacheKey) return multiClient;
  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) throw new Error(`${cfg.apiKeyEnv} not set`);
  if (cfg.insecureTLS) process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  multiClient = new OpenAI({
    apiKey,
    baseURL: cfg.baseURL,
    ...cfg.insecureTLS ? { fetch: makeInsecureFetch() } : {}
  });
  multiClientKey = cacheKey;
  return multiClient;
}
async function callVisionLlmMultiContent(modelId, systemPrompt, userContent) {
  const cfg = activeProvider();
  const model = modelId || cfg.defaultModel;
  const t0 = Date.now();
  log(`vision call (multi): provider=${cfg.provider} model=${model} contentParts=${userContent.length}`);
  const requestOpts = {
    model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userContent }
    ],
    max_tokens: 4e3,
    temperature: 1
  };
  if (model === "MiniMax-M3") {
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }
  const response = await getMultiClient().chat.completions.create(requestOpts, { timeout: 12e4 });
  const msg = response.choices[0]?.message;
  let content = msg?.content;
  if (!content && msg?.reasoning_content) {
    content = msg.reasoning_content;
  }
  if (!content) {
    log(`vision multi: empty response. msg keys: ${Object.keys(msg || {}).join(",")}`);
    throw new Error("Vision model returned empty response");
  }
  log(`vision multi done: ${Date.now() - t0}ms, content=${content.length} chars`);
  return content;
}

// src/tools/analyze.ts
import { execSync } from "child_process";
function getPythonScriptPath() {
  const currentDir = path.dirname(fileURLToPath(import.meta.url));
  const candidates = [
    path.resolve(currentDir, "..", "src", "tools", "analyze_image.py"),
    path.resolve(currentDir, "..", "..", "src", "tools", "analyze_image.py"),
    path.resolve(process.cwd(), "src", "tools", "analyze_image.py"),
    path.resolve(process.cwd(), "skills", "src", "tools", "analyze_image.py")
  ];
  for (const c of candidates) {
    try {
      execSync(`test -f "${c}"`, { timeout: 1e3 });
      return c;
    } catch {
      continue;
    }
  }
  return path.resolve(currentDir, "..", "src", "tools", "analyze_image.py");
}
var scriptPath = getPythonScriptPath();
async function handleAnalyzeScreenshot(args) {
  const filePath = args.filePath ?? "";
  const prompt = args.prompt ?? "";
  if (!filePath) {
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: "filePath required" }) }] };
  }
  if (!await fileExists(filePath)) {
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: `File not found: ${filePath}` }) }] };
  }
  const result = { file: filePath };
  let pilData = {};
  try {
    const cmd = `python3 "${scriptPath}" "${filePath}"`;
    log(`PIL: ${cmd}`);
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 15e3 });
    pilData = JSON.parse(stdout);
    result.pil = {
      dimensions: pilData.dimensions,
      header: pilData.header,
      content: pilData.content,
      bottomNav: pilData.bottom_nav,
      warnings: pilData.warnings || [],
      problems: pilData.problems || []
    };
  } catch (e) {
    result.pil = { error: e.message };
  }
  try {
    const visionCtx = prompt || buildAutoPrompt(pilData);
    const visionResult = await Promise.race([
      analyzeWithVision(filePath, visionCtx),
      new Promise((_, reject) => setTimeout(() => reject(new Error("Vision timeout 90s")), 9e4))
    ]);
    result.vision = visionResult;
  } catch (e) {
    result.vision = { error: e.message };
  }
  const cards = pilData.content?.cards;
  if (cards?.length) {
    result.cards = cards.map((c) => ({
      index: c.index,
      type: c.type,
      y: c.y,
      height: c.height,
      hasImage: c.has_image_region,
      imageLoaded: c.image_loaded,
      textLines: c.text_lines
    }));
  }
  const checklist = [];
  const h = pilData.header;
  if (h) {
    checklist.push({ label: "Header red (#FF5757)", ok: h.red_pixels_pct > 15 });
    checklist.push({ label: "Search bar visible", ok: Boolean(h.search_bar_detected) });
  }
  const ct = pilData.content;
  if (ct) {
    const cardList = ct.cards;
    checklist.push({ label: "Has cards", ok: (cardList?.length ?? 0) > 0 });
    checklist.push({ label: "Proper spacing", ok: ct.gray_dividers >= (cardList?.length ?? 1) - 1 });
  }
  const nav = pilData.bottom_nav;
  if (nav) {
    checklist.push({ label: "Nav white bg", ok: nav.bg_white_pct > 85 });
    checklist.push({ label: "Tab indicator visible", ok: nav.selected_color_px > 100 });
  }
  result.checklist = checklist;
  return {
    content: [{ type: "text", text: JSON.stringify(result) }]
  };
}
function buildAutoPrompt(pilData) {
  const cards = pilData.content?.cards;
  const parts = ["Analyze this Android news-feed screenshot:", ""];
  if (cards) {
    for (const c of cards.slice(0, 10)) {
      parts.push(`- Card ${c.index}: ${c.type}, ${c.height}px, ${c.text_lines} text lines`);
    }
  }
  parts.push("", "Report alignment, spacing, truncation issues with exact offsets and Compose fix suggestions.");
  return parts.join("\n");
}

// src/tools/compare.ts
async function handleCompareScreenshots(args) {
  try {
    const baselinePath = args.baselinePath ?? "";
    const currentPath = args.currentPath ?? "";
    const prompt = args.prompt ?? "";
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
        current: currentPath
      }) }]
    };
  } catch (e) {
    const err = e;
    error("compare failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// src/tools/device-management.ts
async function handleDeviceManagement(args, action) {
  switch (action) {
    case "list_devices": {
      const devices = await listDevices();
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            deviceCount: devices.length,
            devices: devices.map((d) => ({
              serial: d.serial,
              state: d.state,
              model: d.model,
              androidVersion: d.androidVersion,
              sdkVersion: d.sdkVersion,
              screenResolution: d.screenResolution,
              density: d.density
            }))
          }, null, 2)
        }]
      };
    }
    case "device_info": {
      const { serial } = args;
      const details = await getDeviceDetails(serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            serial,
            details
          }, null, 2)
        }]
      };
    }
    case "shell_command": {
      const { command, serial } = args;
      const result = await shellCommand(command, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            output: result.output
          }, null, 2)
        }]
      };
    }
    case "clear_logs": {
      const { serial } = args;
      const result = await clearLogs(serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    case "record_screen": {
      const { duration = 10, outputPath = "./screen_record.mp4", serial } = args;
      const result = await recordScreen(duration, outputPath, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown device management action: ${action}` }]
      };
  }
}

// src/tools/app-management.ts
async function handleAppManagement(args, action) {
  switch (action) {
    case "list_apps": {
      const { serial, system = false, thirdParty = true } = args;
      const apps = await listInstalledApps(serial, { system, thirdParty });
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            appCount: apps.length,
            apps: apps.map((a) => ({
              packageName: a.packageName,
              versionName: a.versionName,
              versionCode: a.versionCode
            }))
          }, null, 2)
        }]
      };
    }
    case "app_info": {
      const { packageName, serial } = args;
      const apps = await listInstalledApps(serial);
      const app = apps.find((a) => a.packageName === packageName);
      if (!app) {
        return {
          isError: true,
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              message: `App ${packageName} not found`
            })
          }]
        };
      }
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            app
          }, null, 2)
        }]
      };
    }
    case "uninstall_app": {
      const { packageName, serial, keepData = false } = args;
      const result = await uninstallApp(packageName, serial, keepData);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    case "clear_app_data": {
      const { packageName, serial } = args;
      const result = await clearAppData(packageName, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    case "stop_app": {
      const { packageName, serial } = args;
      const result = await stopApp(packageName, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown app management action: ${action}` }]
      };
  }
}

// src/utils/performance.ts
async function getCpuUsage() {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "dumpsys cpuinfo | grep TOTAL"',
      { timeout: 5e3 }
    );
    const match = stdout.match(/(\d+(?:\.\d+)?)%/);
    return match ? parseFloat(match[1]) : 0;
  } catch (e) {
    error("Failed to get CPU usage:", e);
    return 0;
  }
}
async function getMemoryInfo(packageName) {
  try {
    const { stdout: meminfo } = await execAsyncWithTimeout(
      'adb shell "cat /proc/meminfo"',
      { timeout: 5e3 }
    );
    const totalMatch = meminfo.match(/MemTotal:\s+(\d+)/);
    const freeMatch = meminfo.match(/MemFree:\s+(\d+)/);
    const availableMatch = meminfo.match(/MemAvailable:\s+(\d+)/);
    const totalKB = totalMatch ? parseInt(totalMatch[1]) : 0;
    const availableKB = availableMatch ? parseInt(availableMatch[1]) : freeMatch ? parseInt(freeMatch[1]) : 0;
    const usedKB = totalKB - availableKB;
    let appUsedKB = 0;
    if (packageName) {
      try {
        const { stdout: appMem } = await execAsyncWithTimeout(
          `adb shell "dumpsys meminfo ${packageName} | grep 'TOTAL PSS'"`,
          { timeout: 5e3 }
        );
        const appMatch = appMem.match(/(\d+)/);
        appUsedKB = appMatch ? parseInt(appMatch[1]) : 0;
      } catch {
      }
    }
    return {
      total: Math.round(totalKB / 1024),
      used: Math.round(usedKB / 1024),
      free: Math.round(availableKB / 1024),
      appUsed: Math.round(appUsedKB / 1024)
    };
  } catch (e) {
    error("Failed to get memory info:", e);
    return { total: 0, used: 0, free: 0, appUsed: 0 };
  }
}
async function getFps(packageName) {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `adb shell "dumpsys gfxinfo ${packageName} | grep 'Frames produced'"`,
      { timeout: 5e3 }
    );
    const match = stdout.match(/(\d+) frames produced/);
    return match ? parseInt(match[1]) : 0;
  } catch (e) {
    error("Failed to get FPS:", e);
    return 0;
  }
}
async function getBatteryInfo() {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "dumpsys battery"',
      { timeout: 5e3 }
    );
    const levelMatch = stdout.match(/level: (\d+)/);
    const tempMatch = stdout.match(/temperature: (\d+)/);
    return {
      level: levelMatch ? parseInt(levelMatch[1]) : 0,
      temperature: tempMatch ? parseInt(tempMatch[1]) / 10 : 0
      // Convert from tenths of degree
    };
  } catch (e) {
    error("Failed to get battery info:", e);
    return { level: 0, temperature: 0 };
  }
}
async function collectPerformanceMetrics(packageName) {
  const [cpuUsage, memoryUsage, batteryInfo] = await Promise.all([
    getCpuUsage(),
    getMemoryInfo(packageName),
    getBatteryInfo()
  ]);
  const fps = packageName ? await getFps(packageName) : 0;
  return {
    cpuUsage,
    memoryUsage,
    fps,
    batteryLevel: batteryInfo.level,
    temperature: batteryInfo.temperature,
    timestamp: Date.now()
  };
}
function formatPerformanceReport(metrics) {
  const lines = [
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
    "\u{1F4CA} PERFORMANCE METRICS REPORT",
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
    `\u{1F550} Time: ${new Date(metrics.timestamp).toLocaleString()}`,
    "",
    `\u{1F4BB} CPU Usage: ${metrics.cpuUsage.toFixed(1)}%`,
    `\u{1F4DD} Memory:`,
    `   Total: ${metrics.memoryUsage.total} MB`,
    `   Used:  ${metrics.memoryUsage.used} MB`,
    `   Free:  ${metrics.memoryUsage.free} MB`,
    `   App:   ${metrics.memoryUsage.appUsed} MB`,
    `\u{1F3AE} FPS: ${metrics.fps}`,
    `\u{1F50B} Battery: ${metrics.batteryLevel}%`,
    `\u{1F321}\uFE0F  Temperature: ${metrics.temperature}\xB0C`,
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550"
  ];
  return lines.join("\n");
}

// src/tools/performance-monitor.ts
async function handlePerformanceMonitor(args) {
  const { packageName, serial } = args;
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
            timestamp: metrics.timestamp
          },
          report
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Failed to collect performance metrics"
        })
      }]
    };
  }
}

// src/utils/quality.ts
async function runKtlint(projectPath = ".") {
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ktlintCheck`,
      { cwd: projectPath, timeout: 12e4 }
    );
    const output = stdout + stderr;
    const issues = [];
    const lines = output.split("\n");
    for (const line of lines) {
      const match = line.match(/^(.+):(\d+):(\d+):\s*(.+)$/);
      if (match) {
        issues.push({
          file: match[1],
          line: parseInt(match[2]),
          column: parseInt(match[3]),
          message: match[4],
          rule: "ktlint",
          severity: line.includes("error") ? "error" : "warning"
        });
      }
    }
    log(`ktlint found ${issues.length} issues`);
    return issues;
  } catch (e) {
    const err = e;
    const output = (err.stdout || "") + (err.stderr || "");
    const issues = [];
    const lines = output.split("\n");
    for (const line of lines) {
      const match = line.match(/^(.+):(\d+):(\d+):\s*(.+)$/);
      if (match) {
        issues.push({
          file: match[1],
          line: parseInt(match[2]),
          column: parseInt(match[3]),
          message: match[4],
          rule: "ktlint",
          severity: line.includes("error") ? "error" : "warning"
        });
      }
    }
    log(`ktlint found ${issues.length} issues`);
    return issues;
  }
}
async function runKtlintFormat(projectPath = ".") {
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ktlintFormat`,
      { cwd: projectPath, timeout: 12e4 }
    );
    const output = stdout + stderr;
    const fixedMatch = output.match(/(\d+) file\(s\) formatted/);
    const fixed = fixedMatch ? parseInt(fixedMatch[1]) : 0;
    return {
      success: true,
      fixed,
      message: `Formatted ${fixed} files`
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      fixed: 0,
      message: err.message || "ktlint format failed"
    };
  }
}
async function analyzeComplexity(projectPath = ".", filePattern = "**/*.kt") {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" | head -50`,
      { timeout: 1e4 }
    );
    const files = stdout.split("\n").filter((f) => f.length > 0);
    let totalComplexity = 0;
    let maxComplexity = 0;
    let maxComplexityFile = "";
    let filesOverThreshold = 0;
    for (const file of files) {
      try {
        const { stdout: content } = await execAsyncWithTimeout(
          `cat "${file}"`,
          { timeout: 5e3 }
        );
        const branches = (content.match(/\bif\b/g) || []).length + (content.match(/\bwhen\b/g) || []).length + (content.match(/\bfor\b/g) || []).length + (content.match(/\bwhile\b/g) || []).length + (content.match(/\breturn\b/g) || []).length;
        const complexity = branches + 1;
        totalComplexity += complexity;
        if (complexity > maxComplexity) {
          maxComplexity = complexity;
          maxComplexityFile = file;
        }
        if (complexity > 10) {
          filesOverThreshold++;
        }
      } catch {
      }
    }
    const averageComplexity = files.length > 0 ? totalComplexity / files.length : 0;
    return {
      averageComplexity: Math.round(averageComplexity * 10) / 10,
      maxComplexity,
      maxComplexityFile,
      filesOverThreshold
    };
  } catch (e) {
    error("Failed to analyze complexity:", e);
    return {
      averageComplexity: 0,
      maxComplexity: 0,
      maxComplexityFile: "",
      filesOverThreshold: 0
    };
  }
}
async function countLines(projectPath = ".") {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" | wc -l`,
      { timeout: 1e4 }
    );
    const totalFiles = parseInt(stdout.trim()) || 0;
    const { stdout: linesOutput } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" -exec cat {} + | wc -l`,
      { timeout: 1e4 }
    );
    const totalLines = parseInt(linesOutput.trim()) || 0;
    return { totalFiles, totalLines };
  } catch (e) {
    error("Failed to count lines:", e);
    return { totalFiles: 0, totalLines: 0 };
  }
}
async function generateQualityReport(projectPath = ".") {
  log("Generating code quality report...");
  const [ktlintIssues, complexityMetrics, lineCounts] = await Promise.all([
    runKtlint(projectPath).catch(() => []),
    analyzeComplexity(projectPath),
    countLines(projectPath)
  ]);
  return {
    ktlintIssues,
    complexityMetrics,
    duplicateCode: [],
    // Would need more sophisticated analysis
    totalFiles: lineCounts.totalFiles,
    totalLines: lineCounts.totalLines,
    timestamp: Date.now()
  };
}
function formatQualityReport(report) {
  const lines = [
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
    "\u{1F4CB} CODE QUALITY REPORT",
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
    `\u{1F550} Time: ${new Date(report.timestamp).toLocaleString()}`,
    `\u{1F4C1} Files: ${report.totalFiles}`,
    `\u{1F4DD} Lines: ${report.totalLines}`,
    "",
    "\u{1F50D} Ktlint Issues:",
    report.ktlintIssues.length === 0 ? "   \u2705 No issues found" : report.ktlintIssues.map((i) => `   \u26A0\uFE0F  ${i.file}:${i.line}:${i.column} - ${i.message}`).join("\n"),
    "",
    "\u{1F4CA} Complexity Metrics:",
    `   Average: ${report.complexityMetrics.averageComplexity}`,
    `   Max: ${report.complexityMetrics.maxComplexity} (${report.complexityMetrics.maxComplexityFile})`,
    `   Files over threshold: ${report.complexityMetrics.filesOverThreshold}`,
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550"
  ];
  return lines.join("\n");
}

// src/tools/code-quality.ts
async function handleCodeQuality(args) {
  const { projectPath = ".", fix = false } = args;
  try {
    if (fix) {
      const fixResult = await runKtlintFormat(projectPath);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: fixResult.success,
            action: "ktlint_format",
            fixed: fixResult.fixed,
            message: fixResult.message
          }, null, 2)
        }]
      };
    }
    const report = await generateQualityReport(projectPath);
    const formattedReport = formatQualityReport(report);
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          report: formattedReport,
          summary: {
            totalFiles: report.totalFiles,
            totalLines: report.totalLines,
            ktlintIssues: report.ktlintIssues.length,
            averageComplexity: report.complexityMetrics.averageComplexity,
            maxComplexity: report.complexityMetrics.maxComplexity,
            filesOverThreshold: report.complexityMetrics.filesOverThreshold
          }
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Code quality check failed"
        })
      }]
    };
  }
}

// src/utils/ui-test.ts
async function getCurrentActivity() {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "dumpsys window windows | grep mCurrentFocus"',
      { timeout: 1e4 }
    );
    const match = stdout.match(/(\S+)\/(\S+)}/);
    return match ? `${match[1]}/${match[2]}` : "unknown";
  } catch (e) {
    error("Failed to get current activity:", e);
    return "unknown";
  }
}
async function getUIHierarchy() {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb exec-out "uiautomator dump /dev/tty"',
      { timeout: 15e3 }
    );
    return parseHierarchyXml(stdout);
  } catch (e) {
    try {
      await execAsyncWithTimeout(
        'adb shell "uiautomator dump /sdcard/window_dump.xml"',
        { timeout: 15e3 }
      );
      const { stdout } = await execAsyncWithTimeout(
        'adb shell "cat /sdcard/window_dump.xml"',
        { timeout: 1e4 }
      );
      return parseHierarchyXml(stdout);
    } catch (e2) {
      error("Failed to get UI hierarchy:", e2);
      return [];
    }
  }
}
function parseHierarchyXml(xml) {
  const elements = [];
  const nodeRegex = /<node\b([^>]*?)\/?>/g;
  let m;
  while ((m = nodeRegex.exec(xml)) !== null) {
    const attrs = m[1];
    const boundsMatch = attrs.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
    const textMatch = attrs.match(/text="([^"]*)"/);
    const classMatch = attrs.match(/class="([^"]*)"/);
    const clickableMatch = attrs.match(/clickable="([^"]*)"/);
    const resourceMatch = attrs.match(/resource-id="([^"]*)"/);
    if (boundsMatch) {
      elements.push({
        type: classMatch ? classMatch[1].split(".").pop() || "unknown" : "unknown",
        text: textMatch ? textMatch[1] : void 0,
        bounds: {
          x: parseInt(boundsMatch[1]),
          y: parseInt(boundsMatch[2]),
          width: parseInt(boundsMatch[3]) - parseInt(boundsMatch[1]),
          height: parseInt(boundsMatch[4]) - parseInt(boundsMatch[2])
        },
        clickable: clickableMatch ? clickableMatch[1] === "true" : false,
        resourceId: resourceMatch ? resourceMatch[1] : void 0
      });
    }
  }
  return elements;
}
async function runUITest(steps) {
  const start = Date.now();
  const screenshots = [];
  try {
    for (let i = 0; i < steps.length; i++) {
      const step = steps[i];
      log(`Executing step ${i + 1}/${steps.length}: ${step.action}`);
      switch (step.action) {
        case "tap": {
          const { x, y } = step.params;
          await execAsyncWithTimeout(
            `adb shell input tap ${x} ${y}`,
            { timeout: 1e4 }
          );
          break;
        }
        case "swipe": {
          const { x1, y1, x2, y2, duration = 300 } = step.params;
          await execAsyncWithTimeout(
            `adb shell input swipe ${x1} ${y1} ${x2} ${y2} ${duration}`,
            { timeout: 1e4 }
          );
          break;
        }
        case "input": {
          const { text } = step.params;
          const escaped = text.replace(/ /g, "%s");
          await execAsyncWithTimeout(
            `adb shell input text "${escaped}"`,
            { timeout: 1e4 }
          );
          break;
        }
        case "wait": {
          const { ms = 1e3 } = step.params;
          await new Promise((resolve) => setTimeout(resolve, ms));
          break;
        }
        case "screenshot": {
          const timestamp = Date.now();
          const path4 = `./screenshots/test_${timestamp}.png`;
          await execAsyncWithTimeout(
            'adb shell "screencap -p /sdcard/screen.png"',
            { timeout: 1e4 }
          );
          await execAsyncWithTimeout(
            `adb pull /sdcard/screen.png "${path4}"`,
            { timeout: 1e4 }
          );
          screenshots.push(path4);
          break;
        }
      }
    }
    return {
      success: true,
      message: `Completed ${steps.length} steps successfully`,
      screenshot: screenshots[screenshots.length - 1],
      duration: Date.now() - start
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      message: err.message || "Test failed",
      duration: Date.now() - start
    };
  }
}
async function runRegressionTest(packageName) {
  const tests = [
    {
      name: "App Launch",
      test: async () => {
        await execAsyncWithTimeout(
          `adb shell am start -n ${packageName}/.MainActivity`,
          { timeout: 1e4 }
        );
        await new Promise((resolve) => setTimeout(resolve, 3e3));
        const activity = await getCurrentActivity();
        return activity.includes(packageName);
      }
    },
    {
      name: "Screenshot Capture",
      test: async () => {
        await execAsyncWithTimeout(
          'adb shell "screencap -p /sdcard/test_screen.png"',
          { timeout: 1e4 }
        );
        return true;
      }
    },
    {
      name: "UI Hierarchy",
      test: async () => {
        const elements = await getUIHierarchy();
        return elements.length > 0;
      }
    }
  ];
  const results = [];
  let passed = 0;
  let failed = 0;
  for (const test of tests) {
    try {
      const success = await test.test();
      results.push({
        name: test.name,
        success,
        message: success ? "Passed" : "Failed"
      });
      if (success) passed++;
      else failed++;
    } catch (e) {
      const err = e;
      results.push({
        name: test.name,
        success: false,
        message: err.message || "Error"
      });
      failed++;
    }
  }
  return {
    passed,
    failed,
    total: tests.length,
    results
  };
}

// src/tools/ui-test.ts
async function handleUITest(args, action) {
  switch (action) {
    case "run_test": {
      const { steps } = args;
      const result = await runUITest(steps);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            message: result.message,
            duration: result.duration,
            screenshot: result.screenshot
          }, null, 2)
        }]
      };
    }
    case "regression": {
      const { packageName, serial } = args;
      const result = await runRegressionTest(packageName);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.failed === 0,
            passed: result.passed,
            failed: result.failed,
            total: result.total,
            results: result.results
          }, null, 2)
        }]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown UI test action: ${action}` }]
      };
  }
}

// src/utils/build-deploy.ts
async function buildApk(config) {
  const start = Date.now();
  const { projectPath, variant, flavor } = config;
  let gradleTask = "assemble";
  if (flavor) {
    gradleTask += flavor.charAt(0).toUpperCase() + flavor.slice(1);
  }
  gradleTask += variant.charAt(0).toUpperCase() + variant.slice(1);
  log(`Building: ./gradlew ${gradleTask}`);
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ${gradleTask}`,
      { cwd: projectPath, timeout: 3e5 }
    );
    const combinedOutput = stdout + stderr;
    const apkMatch = combinedOutput.match(/outputs\/apk\/[^\s]+\.apk/);
    const apkPath = apkMatch ? `${projectPath}/app/build/${apkMatch[0]}` : void 0;
    const warnings = combinedOutput.split("\n").filter((line) => line.includes("warning") || line.includes("Warning"));
    return {
      success: true,
      apkPath,
      buildTime: Date.now() - start,
      warnings: warnings.length > 0 ? warnings : void 0
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      buildTime: Date.now() - start,
      error: err.stderr || err.message || "Build failed"
    };
  }
}
async function buildAab(config) {
  const start = Date.now();
  const { projectPath, variant, flavor } = config;
  let gradleTask = "bundle";
  if (flavor) {
    gradleTask += flavor.charAt(0).toUpperCase() + flavor.slice(1);
  }
  gradleTask += variant.charAt(0).toUpperCase() + variant.slice(1);
  log(`Building AAB: ./gradlew ${gradleTask}`);
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ${gradleTask}`,
      { cwd: projectPath, timeout: 3e5 }
    );
    const combinedOutput = stdout + stderr;
    const aabMatch = combinedOutput.match(/outputs\/bundle\/[^\s]+\.aab/);
    const aabPath = aabMatch ? `${projectPath}/app/build/${aabMatch[0]}` : void 0;
    return {
      success: true,
      aabPath,
      buildTime: Date.now() - start
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      buildTime: Date.now() - start,
      error: err.stderr || err.message || "AAB build failed"
    };
  }
}
async function cleanBuild(projectPath = ".") {
  try {
    await execAsyncWithTimeout(
      `./gradlew clean`,
      { cwd: projectPath, timeout: 12e4 }
    );
    return { success: true, message: "Build cache cleaned" };
  } catch (e) {
    const err = e;
    return { success: false, message: err.message || "Clean failed" };
  }
}
async function runUnitTests(projectPath = ".", module) {
  try {
    const task = module ? `${module}:test` : "test";
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ${task}`,
      { cwd: projectPath, timeout: 3e5 }
    );
    const output = stdout + stderr;
    const passedMatch = output.match(/(\d+) tests? completed/);
    const failedMatch = output.match(/(\d+) failed/);
    const skippedMatch = output.match(/(\d+) skipped/);
    const passed = passedMatch ? parseInt(passedMatch[1]) : 0;
    const failed = failedMatch ? parseInt(failedMatch[1]) : 0;
    const skipped = skippedMatch ? parseInt(skippedMatch[1]) : 0;
    return {
      success: failed === 0,
      passed,
      failed,
      skipped,
      report: output.includes("BUILD SUCCESSFUL") ? "All tests passed" : "Some tests failed"
    };
  } catch (e) {
    const err = e;
    const output = (err.stdout || "") + (err.stderr || "");
    return {
      success: false,
      passed: 0,
      failed: 0,
      skipped: 0,
      report: output || err.message || "Test execution failed"
    };
  }
}
async function runInstrumentedTests(projectPath = ".") {
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew connectedAndroidTest`,
      { cwd: projectPath, timeout: 6e5 }
    );
    const output = stdout + stderr;
    return {
      success: output.includes("BUILD SUCCESSFUL"),
      passed: 0,
      failed: 0,
      message: output.includes("BUILD SUCCESSFUL") ? "Instrumented tests passed" : "Instrumented tests failed"
    };
  } catch (e) {
    const err = e;
    return {
      success: false,
      passed: 0,
      failed: 0,
      message: err.message || "Instrumented test execution failed"
    };
  }
}
async function getApkInfo(apkPath) {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `aapt dump badging "${apkPath}"`,
      { timeout: 3e4 }
    );
    const packageMatch = stdout.match(/package: name='([^']+)'/);
    const versionNameMatch = stdout.match(/versionName='([^']+)'/);
    const versionCodeMatch = stdout.match(/versionCode='([^']+)'/);
    const sdkMatch = stdout.match(/sdkVersion:'([^']+)'/);
    const targetSdkMatch = stdout.match(/targetSdkVersion:'([^']+)'/);
    return {
      packageName: packageMatch ? packageMatch[1] : "unknown",
      versionName: versionNameMatch ? versionNameMatch[1] : "unknown",
      versionCode: versionCodeMatch ? versionCodeMatch[1] : "unknown",
      minSdk: sdkMatch ? sdkMatch[1] : "unknown",
      targetSdk: targetSdkMatch ? targetSdkMatch[1] : "unknown"
    };
  } catch (e) {
    error("Failed to get APK info:", e);
    return {
      packageName: "unknown",
      versionName: "unknown",
      versionCode: "unknown",
      minSdk: "unknown",
      targetSdk: "unknown"
    };
  }
}

// src/tools/build-deploy.ts
async function handleBuildDeploy(args, action) {
  switch (action || "build") {
    case "build": {
      const { projectPath = ".", variant = "debug", flavor } = args;
      const result = await buildApk({ projectPath, variant, flavor });
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            apkPath: result.apkPath,
            buildTime: result.buildTime,
            warnings: result.warnings,
            error: result.error
          }, null, 2)
        }]
      };
    }
    case "build_aab": {
      const { projectPath = ".", variant = "release", flavor } = args;
      const result = await buildAab({ projectPath, variant, flavor });
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            aabPath: result.aabPath,
            buildTime: result.buildTime,
            error: result.error
          }, null, 2)
        }]
      };
    }
    case "clean": {
      const { projectPath = "." } = args;
      const result = await cleanBuild(projectPath);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    case "run_tests": {
      const { projectPath = ".", type = "unit", module } = args;
      const results = {};
      if (type === "unit" || type === "all") {
        const unitResult = await runUnitTests(projectPath, module);
        results.unit = unitResult;
      }
      if (type === "instrumented" || type === "all") {
        const instResult = await runInstrumentedTests(projectPath);
        results.instrumented = instResult;
      }
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: Object.values(results).every((r) => r.success),
            results
          }, null, 2)
        }]
      };
    }
    case "apk_info": {
      const { apkPath } = args;
      const info = await getApkInfo(apkPath);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            info
          }, null, 2)
        }]
      };
    }
    case "full_deploy": {
      const {
        projectPath = ".",
        variant = "debug",
        packageName,
        autoLaunch = true,
        serial
      } = args;
      log("Step 1: Cleaning build...");
      await cleanBuild(projectPath);
      log("Step 2: Building APK...");
      const buildResult = await buildApk({ projectPath, variant });
      if (!buildResult.success || !buildResult.apkPath) {
        return {
          isError: true,
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              stage: "build",
              error: buildResult.error
            })
          }]
        };
      }
      log("Step 3: Installing APK...");
      const installResult = await installApk(buildResult.apkPath, serial, { reinstall: true });
      if (!installResult.success) {
        return {
          isError: true,
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              stage: "install",
              error: installResult.message
            })
          }]
        };
      }
      if (autoLaunch) {
        log("Step 4: Launching app...");
        await startApp(packageName, void 0, serial);
      }
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            apkPath: buildResult.apkPath,
            buildTime: buildResult.buildTime,
            installed: true,
            launched: autoLaunch
          }, null, 2)
        }]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown build/deploy action: ${action}` }]
      };
  }
}

// src/utils/report.ts
async function parseProjectInfo(projectPath) {
  try {
    const { stdout: buildGradle } = await execAsyncWithTimeout(
      `cat "${projectPath}/app/build.gradle.kts"`,
      { timeout: 1e4 }
    );
    const namespaceMatch = buildGradle.match(/namespace\s*=\s*"([^"]+)"/);
    const versionMatch = buildGradle.match(/versionName\s*=\s*"([^"]+)"/);
    const compileSdkMatch = buildGradle.match(/compileSdk\s*=\s*(\d+)/);
    const minSdkMatch = buildGradle.match(/minSdk\s*=\s*(\d+)/);
    const targetSdkMatch = buildGradle.match(/targetSdk\s*=\s*(\d+)/);
    const dependencies = [];
    const depRegex = /implementation\("([^"]+)"\)/g;
    let match;
    while ((match = depRegex.exec(buildGradle)) !== null) {
      const dep = match[1];
      const [name, version] = dep.split(":").slice(0, 2);
      let category = "other";
      if (name.includes("compose") || name.includes("material")) category = "ui";
      else if (name.includes("retrofit") || name.includes("okhttp")) category = "network";
      else if (name.includes("room")) category = "database";
      else if (name.includes("hilt")) category = "di";
      else if (name.includes("test") || name.includes("junit")) category = "testing";
      dependencies.push({ name, version: version || "unknown", category });
    }
    return {
      name: namespaceMatch ? namespaceMatch[1].split(".").pop() || "unknown" : "unknown",
      packageName: namespaceMatch ? namespaceMatch[1] : "unknown",
      version: versionMatch ? versionMatch[1] : "unknown",
      buildToolsVersion: "unknown",
      compileSdk: compileSdkMatch ? compileSdkMatch[1] : "unknown",
      minSdk: minSdkMatch ? minSdkMatch[1] : "unknown",
      targetSdk: targetSdkMatch ? targetSdkMatch[1] : "unknown",
      dependencies
    };
  } catch (e) {
    error("Failed to parse project info:", e);
    return {
      name: "unknown",
      packageName: "unknown",
      version: "unknown",
      buildToolsVersion: "unknown",
      compileSdk: "unknown",
      minSdk: "unknown",
      targetSdk: "unknown",
      dependencies: []
    };
  }
}
async function analyzeCodeMetrics(projectPath) {
  try {
    const { stdout: kotlinFiles } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" | wc -l`,
      { timeout: 1e4 }
    );
    const { stdout: composeFiles } = await execAsyncWithTimeout(
      `grep -r "@Composable" ${projectPath}/app/src --include="*.kt" -l | wc -l`,
      { timeout: 1e4 }
    );
    const { stdout: totalLines } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" -exec cat {} + | wc -l`,
      { timeout: 1e4 }
    );
    const { stdout: commentLines } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" -exec cat {} + | grep -c "//\\|/\\*"`,
      { timeout: 1e4 }
    );
    const totalFiles = parseInt(kotlinFiles.trim()) || 0;
    const composeCount = parseInt(composeFiles.trim()) || 0;
    const lines = parseInt(totalLines.trim()) || 0;
    const comments = parseInt(commentLines.trim()) || 0;
    return {
      totalFiles,
      totalLines: lines,
      kotlinFiles: totalFiles,
      composeFiles: composeCount,
      averageFileLength: totalFiles > 0 ? Math.round(lines / totalFiles) : 0,
      commentRatio: lines > 0 ? Math.round(comments / lines * 100) : 0
    };
  } catch (e) {
    error("Failed to analyze code metrics:", e);
    return {
      totalFiles: 0,
      totalLines: 0,
      kotlinFiles: 0,
      composeFiles: 0,
      averageFileLength: 0,
      commentRatio: 0
    };
  }
}
async function analyzeArchitecture(projectPath) {
  const layers = [];
  const issues = [];
  try {
    const layerChecks = [
      { name: "Presentation", path: "presentation", responsibility: "UI and ViewModel" },
      { name: "Domain", path: "domain", responsibility: "Business logic and models" },
      { name: "Data", path: "data", responsibility: "Data sources and repositories" },
      { name: "DI", path: "di", responsibility: "Dependency injection" }
    ];
    for (const layer of layerChecks) {
      try {
        const { stdout } = await execAsyncWithTimeout(
          `find ${projectPath}/app/src -type d -name "${layer.path}" | wc -l`,
          { timeout: 5e3 }
        );
        const count = parseInt(stdout.trim()) || 0;
        if (count > 0) {
          const { stdout: fileCount } = await execAsyncWithTimeout(
            `find ${projectPath}/app/src -path "*/${layer.path}/*.kt" | wc -l`,
            { timeout: 5e3 }
          );
          layers.push({
            name: layer.name,
            fileCount: parseInt(fileCount.trim()) || 0,
            responsibility: layer.responsibility
          });
        } else {
          issues.push(`Missing ${layer.name} layer (${layer.path})`);
        }
      } catch {
        issues.push(`Failed to analyze ${layer.name} layer`);
      }
    }
    try {
      const { stdout: violations } = await execAsyncWithTimeout(
        `grep -r "import android" ${projectPath}/app/src/main/java/com/example/toutiao/domain --include="*.kt" | wc -l`,
        { timeout: 5e3 }
      );
      const violationCount = parseInt(violations.trim()) || 0;
      if (violationCount > 0) {
        issues.push(`Found ${violationCount} Android imports in Domain layer`);
      }
    } catch {
    }
    return {
      pattern: layers.length >= 3 ? "Clean Architecture + MVI" : "Unknown",
      layers,
      dependenciesClean: issues.length === 0,
      issues
    };
  } catch (e) {
    error("Failed to analyze architecture:", e);
    return {
      pattern: "Unknown",
      layers,
      dependenciesClean: false,
      issues: ["Failed to analyze architecture"]
    };
  }
}
async function generateProjectReport(projectPath, options = {}) {
  log("Generating comprehensive project report...");
  const [projectInfo, codeMetrics, qualityReport, testResults, architecture] = await Promise.all([
    parseProjectInfo(projectPath),
    analyzeCodeMetrics(projectPath),
    generateQualityReport(projectPath).catch(() => ({
      ktlintIssues: [],
      complexityMetrics: { averageComplexity: 0, maxComplexity: 0, maxComplexityFile: "", filesOverThreshold: 0 },
      duplicateCode: [],
      totalFiles: 0,
      totalLines: 0,
      timestamp: Date.now()
    })),
    runUnitTests(projectPath).catch(() => ({
      success: false,
      passed: 0,
      failed: 0,
      skipped: 0
    })),
    analyzeArchitecture(projectPath)
  ]);
  let performanceReport;
  if (options.includePerformance && options.packageName) {
    try {
      const metrics = await collectPerformanceMetrics(options.packageName);
      performanceReport = formatPerformanceReport(metrics);
    } catch (e) {
      error("Failed to collect performance metrics:", e);
    }
  }
  const recommendations = [];
  if (codeMetrics.commentRatio < 5) {
    recommendations.push("Consider adding more code comments for complex logic");
  }
  if (qualityReport.ktlintIssues.length > 0) {
    recommendations.push(`Fix ${qualityReport.ktlintIssues.length} ktlint issues`);
  }
  if (!architecture.dependenciesClean) {
    recommendations.push("Review architecture layer dependencies");
  }
  if (testResults.passed === 0 && testResults.failed === 0) {
    recommendations.push("Add unit tests to improve code coverage");
  }
  if (codeMetrics.composeFiles === 0) {
    recommendations.push("Consider migrating to Jetpack Compose for modern UI");
  }
  return {
    projectInfo,
    codeMetrics,
    qualityReport: formatQualityReport(qualityReport),
    performanceReport,
    testResults: {
      unitTests: {
        passed: testResults.passed,
        failed: testResults.failed,
        skipped: testResults.skipped
      },
      uiTests: {
        passed: 0,
        failed: 0,
        total: 0
      }
    },
    architectureAnalysis: architecture,
    recommendations,
    generatedAt: Date.now()
  };
}
function formatReportAsMarkdown(report) {
  const lines = [
    "# \u{1F4F1} Android Project Report",
    "",
    `Generated: ${new Date(report.generatedAt).toLocaleString()}`,
    "",
    "## \u{1F4CB} Project Information",
    "",
    `| Property | Value |`,
    `|----------|-------|`,
    `| Name | ${report.projectInfo.name} |`,
    `| Package | ${report.projectInfo.packageName} |`,
    `| Version | ${report.projectInfo.version} |`,
    `| Compile SDK | API ${report.projectInfo.compileSdk} |`,
    `| Min SDK | API ${report.projectInfo.minSdk} |`,
    `| Target SDK | API ${report.projectInfo.targetSdk} |`,
    "",
    "## \u{1F4CA} Code Metrics",
    "",
    `| Metric | Value |`,
    `|--------|-------|`,
    `| Total Files | ${report.codeMetrics.totalFiles} |`,
    `| Total Lines | ${report.codeMetrics.totalLines} |`,
    `| Kotlin Files | ${report.codeMetrics.kotlinFiles} |`,
    `| Compose Files | ${report.codeMetrics.composeFiles} |`,
    `| Avg File Length | ${report.codeMetrics.averageFileLength} lines |`,
    `| Comment Ratio | ${report.codeMetrics.commentRatio}% |`,
    "",
    "## \u{1F3D7}\uFE0F Architecture",
    "",
    `Pattern: **${report.architectureAnalysis.pattern}**`,
    "",
    `| Layer | Files | Responsibility |`,
    `|-------|-------|----------------|`,
    ...report.architectureAnalysis.layers.map(
      (l) => `| ${l.name} | ${l.fileCount} | ${l.responsibility} |`
    ),
    "",
    report.architectureAnalysis.dependenciesClean ? "\u2705 Dependencies are clean" : "\u26A0\uFE0F Architecture issues found:",
    ...report.architectureAnalysis.issues.map((i) => `- ${i}`),
    "",
    "## \u{1F9EA} Test Results",
    "",
    `| Type | Passed | Failed | Skipped |`,
    `|------|--------|--------|---------|`,
    `| Unit Tests | ${report.testResults.unitTests.passed} | ${report.testResults.unitTests.failed} | ${report.testResults.unitTests.skipped} |`,
    `| UI Tests | ${report.testResults.uiTests.passed} | ${report.testResults.uiTests.failed} | ${report.testResults.uiTests.total} |`,
    "",
    "## \u{1F4E6} Dependencies",
    "",
    `| Name | Version | Category |`,
    `|------|---------|----------|`,
    ...report.projectInfo.dependencies.map(
      (d) => `| ${d.name} | ${d.version} | ${d.category} |`
    ),
    "",
    "## \u{1F4A1} Recommendations",
    "",
    report.recommendations.length > 0 ? report.recommendations.map((r) => `- ${r}`).join("\n") : "\u2705 No issues found!",
    ""
  ];
  if (report.performanceReport) {
    lines.push(
      "## \u{1F4C8} Performance",
      "",
      "```",
      report.performanceReport,
      "```",
      ""
    );
  }
  lines.push(
    "---",
    "",
    "*Generated by AndroidDev-Assist MCP Server*"
  );
  return lines.join("\n");
}

// src/tools/project-report.ts
async function handleProjectReport(args) {
  const {
    projectPath = ".",
    includePerformance = false,
    packageName,
    format = "markdown"
  } = args;
  try {
    log("Generating project report...");
    const report = await generateProjectReport(projectPath, {
      includePerformance,
      packageName
    });
    if (format === "markdown") {
      const markdown = formatReportAsMarkdown(report);
      return {
        content: [{
          type: "text",
          text: markdown
        }]
      };
    }
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          report: {
            projectInfo: report.projectInfo,
            codeMetrics: report.codeMetrics,
            testResults: report.testResults,
            architectureAnalysis: report.architectureAnalysis,
            recommendations: report.recommendations,
            generatedAt: report.generatedAt
          }
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Failed to generate project report"
        })
      }]
    };
  }
}

// src/tools/file-operations.ts
async function handleFileOperations(args, action) {
  switch (action) {
    case "push": {
      const { localPath, remotePath, serial } = args;
      const result = await pushFile(localPath, remotePath, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    case "pull": {
      const { remotePath, localPath, serial } = args;
      const result = await pullFile(remotePath, localPath, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown file operation: ${action}` }]
      };
  }
}

// src/tools/network-debug.ts
async function handleNetworkDebug(args, action) {
  switch (action) {
    case "get_state": {
      const { serial } = args;
      const state = await getNetworkState(serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            network: state
          }, null, 2)
        }]
      };
    }
    case "set_state": {
      const { type, enabled, serial } = args;
      const result = await setNetworkState(type, enabled, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2)
        }]
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown network debug action: ${action}` }]
      };
  }
}

// src/tools/vision-action.ts
import fs3 from "fs";
var cachedScreen = null;
async function getScreenSize() {
  if (cachedScreen) return cachedScreen;
  try {
    const { stdout } = await execAsyncWithTimeout("adb shell wm size", { timeout: 5e3 });
    const match = stdout.match(/(\d+)x(\d+)/);
    if (match) {
      cachedScreen = { width: parseInt(match[1]), height: parseInt(match[2]) };
      return cachedScreen;
    }
  } catch (e) {
    error("getScreenSize:", e);
  }
  cachedScreen = { width: 1080, height: 2400 };
  return cachedScreen;
}
function buildSystemPrompt(width, height) {
  return `You are an Android UI automation agent. Output ONLY a single JSON object.

Screen: ${width}x${height}px.

Format: {"action":"tap|swipe|input|none","x":<0-${width}>,"y":<0-${height}>,"x2":<endX>,"y2":<endY>,"duration":300,"confidence":0.0-1.0,"reasoning":"<5 words>"}

Rules: tap\u2192center of target. swipe\u2192x,y=start x2,y2=end. none\u2192not found(confidence:0). NO markdown, NO extra text.`;
}
function parseVisionAction(raw, width, height) {
  let jsonStr = raw.trim();
  const f = jsonStr.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (f) jsonStr = f[1].trim();
  const a = jsonStr.indexOf("{"), b = jsonStr.lastIndexOf("}");
  if (a !== -1 && b > a) jsonStr = jsonStr.slice(a, b + 1);
  let parsed;
  try {
    parsed = JSON.parse(jsonStr);
  } catch {
    const fixed = jsonStr.replace(/,(\s*[}\]])/g, "$1").replace(/([{,]\s*)(\w+)(\s*:)/g, '$1"$2"$3');
    try {
      parsed = JSON.parse(fixed);
    } catch {
      throw new Error(`Bad JSON from vision: ${raw.slice(0, 200)}`);
    }
  }
  const action = parsed.action || "none";
  if (!["tap", "swipe", "input", "wait", "none"].includes(action)) throw new Error(`Invalid action: ${action}`);
  const r = {
    action,
    confidence: typeof parsed.confidence === "number" ? parsed.confidence : 0.5,
    reasoning: parsed.reasoning || ""
  };
  if (parsed.x !== void 0) {
    const x = Number(parsed.x);
    if (isNaN(x) || x < 0 || x > width) throw new Error(`X=${x} OOB`);
    r.x = Math.round(x);
  }
  if (parsed.y !== void 0) {
    const y = Number(parsed.y);
    if (isNaN(y) || y < 0 || y > height) throw new Error(`Y=${y} OOB`);
    r.y = Math.round(y);
  }
  if (parsed.x2 !== void 0) r.x2 = Math.round(Number(parsed.x2));
  if (parsed.y2 !== void 0) r.y2 = Math.round(Number(parsed.y2));
  if (parsed.duration !== void 0) r.duration = Number(parsed.duration);
  if (parsed.text !== void 0) r.text = String(parsed.text);
  return r;
}
async function executeVisionStep(instruction, beforeScreenshotPath) {
  const beforePath = beforeScreenshotPath && fs3.existsSync(beforeScreenshotPath) ? beforeScreenshotPath : (await screenshot()).path;
  const { width, height } = await getScreenSize();
  log(`Vision: ${instruction}`);
  const t0 = Date.now();
  const response = await analyzeWithVision(beforePath, instruction, buildSystemPrompt(width, height));
  log(`Vision ${Date.now() - t0}ms: ${response.slice(0, 150)}`);
  const action = parseVisionAction(response, width, height);
  switch (action.action) {
    case "tap":
      if (action.x === void 0 || action.y === void 0) throw new Error("Tap missing x,y");
      await tap(action.x, action.y);
      break;
    case "swipe":
      await swipe(action.x ?? width / 2, action.y ?? height * 0.7, action.x2 ?? width / 2, action.y2 ?? height * 0.3, action.duration ?? 300);
      break;
    case "input":
      if (action.text) await inputText(action.text);
      break;
    case "wait":
      await new Promise((r) => setTimeout(r, action.duration ?? 500));
      break;
  }
  await new Promise((r) => setTimeout(r, 400));
  const after = await screenshot();
  return { action, screenshot: after.path };
}
async function handleVisionAction(args) {
  const prompt = args.prompt ?? "";
  const prompts = args.prompts ?? [];
  const beforeScreenshot = args.beforeScreenshot ?? "";
  const allPrompts = prompt ? [prompt] : prompts;
  if (allPrompts.length === 0) {
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ error: "prompt or prompts required" }) }] };
  }
  const tStart = Date.now();
  const steps = [];
  let lastScreenshot = beforeScreenshot || "";
  let allSuccess = true;
  for (let i = 0; i < allPrompts.length; i++) {
    try {
      const { action, screenshot: ss } = await executeVisionStep(allPrompts[i], lastScreenshot || void 0);
      lastScreenshot = ss;
      steps.push({
        action: action.action,
        x: action.x,
        y: action.y,
        x2: action.x2,
        y2: action.y2,
        text: action.text,
        confidence: action.confidence,
        reasoning: action.reasoning
      });
    } catch (e) {
      steps.push({ action: "error", confidence: 0, reasoning: "", error: e.message });
      allSuccess = false;
    }
  }
  return {
    isError: !allSuccess,
    content: [{ type: "text", text: JSON.stringify({
      success: allSuccess,
      steps,
      screenshot: lastScreenshot || null,
      durationMs: Date.now() - tStart
    }) }]
  };
}

// src/utils/launch-speed.ts
async function forceStopApp(packageName) {
  await execAsyncWithTimeout(`adb shell am force-stop ${packageName}`, { timeout: 5e3 });
  await new Promise((resolve) => setTimeout(resolve, 500));
}
async function clearAppData2(packageName) {
  await execAsyncWithTimeout(`adb shell pm clear ${packageName}`, { timeout: 1e4 });
  await new Promise((resolve) => setTimeout(resolve, 1e3));
}
async function measureWithAmStart(packageName, activityName) {
  const component = activityName ? `${packageName}/${activityName}` : `${packageName}/.MainActivity`;
  const { stdout, stderr } = await execAsyncWithTimeout(
    `adb shell am start -W -n ${component}`,
    { timeout: 3e4 }
  );
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
    waitTime
  };
}
async function getDisplayedTimeFromLogcat(packageName, timeoutMs = 1e4) {
  const startTime = Date.now();
  let ttid = 0;
  let ttfd = 0;
  while (Date.now() - startTime < timeoutMs) {
    try {
      const { stdout } = await execAsyncWithTimeout(
        `adb logcat -d -s ActivityTaskManager:I -t 50 | grep -i "displayed.*${packageName}"`,
        { timeout: 5e3 }
      );
      const ttidMatchMs = stdout.match(/Displayed\s+[^:]+:\s*\+(\d+)ms/);
      const ttidMatchSec = stdout.match(/Displayed\s+[^:]+:\s*\+(\d+)s(\d+)ms/);
      if (ttid === 0) {
        if (ttidMatchMs) {
          ttid = parseInt(ttidMatchMs[1]) || 0;
        } else if (ttidMatchSec) {
          const seconds = parseInt(ttidMatchSec[1]) || 0;
          const millis = parseInt(ttidMatchSec[2]) || 0;
          ttid = seconds * 1e3 + millis;
        }
      }
      const ttfdMatch = stdout.match(/Fully drawn\s+[^:]+:\s*\+(\d+)s(\d+)ms/);
      const ttfdMatchMs = stdout.match(/Fully drawn\s+[^:]+:\s*\+(\d+)ms/);
      if (ttfdMatch) {
        const seconds = parseInt(ttfdMatch[1]) || 0;
        const millis = parseInt(ttfdMatch[2]) || 0;
        ttfd = seconds * 1e3 + millis;
      } else if (ttfdMatchMs) {
        ttfd = parseInt(ttfdMatchMs[1]) || 0;
      }
      if (ttid > 0) break;
    } catch {
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  return { ttid, ttfd };
}
async function measureSingleLaunch(packageName, launchType, activityName, iteration = 1) {
  log(`Measuring ${launchType} iteration ${iteration} for ${packageName}`);
  await execAsyncWithTimeout("adb logcat -c", { timeout: 5e3 });
  if (launchType === "cold_start") {
    await forceStopApp(packageName);
    await clearAppData2(packageName);
  } else {
    await execAsyncWithTimeout("adb shell input keyevent 3", { timeout: 5e3 });
    await new Promise((resolve) => setTimeout(resolve, 1e3));
  }
  const amStartResult = await measureWithAmStart(packageName, activityName);
  await new Promise((resolve) => setTimeout(resolve, 500));
  const { ttid, ttfd } = await getDisplayedTimeFromLogcat(packageName);
  const finalTtid = ttid > 0 ? ttid : amStartResult.thisTime > 0 ? amStartResult.thisTime : amStartResult.totalTime;
  const finalTtfd = ttfd > 0 ? ttfd : amStartResult.totalTime;
  log(`Measurement result: ttid=${finalTtid}, ttfd=${finalTtfd}, totalTime=${amStartResult.totalTime}, waitTime=${amStartResult.waitTime}`);
  return {
    iteration,
    ttid: finalTtid,
    ttfd: finalTtfd,
    totalTime: amStartResult.totalTime,
    waitTime: amStartResult.waitTime,
    timestamp: (/* @__PURE__ */ new Date()).toISOString()
  };
}
function calculateStatistics(values) {
  if (values.length === 0) {
    return { min: 0, max: 0, avg: 0, p95: 0 };
  }
  const sorted = [...values].sort((a, b) => a - b);
  const min = sorted[0];
  const max = sorted[sorted.length - 1];
  const avg = Math.round(sorted.reduce((a, b) => a + b, 0) / sorted.length);
  const rank = sorted.length * 0.95;
  const lower = Math.floor(rank);
  const upper = Math.min(sorted.length - 1, lower + 1);
  const fraction = rank - lower;
  const p95 = Math.round(sorted[lower] * (1 - fraction) + sorted[upper] * fraction);
  return { min, max, avg, p95 };
}
function getGrade(ttidAvg) {
  if (ttidAvg < 1e3) return "A";
  if (ttidAvg < 2e3) return "B";
  if (ttidAvg < 3e3) return "C";
  return "D";
}
function generateRecommendations(stats, launchType) {
  const recommendations = [];
  const ttidAvg = stats.ttid.avg;
  const ttfdAvg = stats.ttfd.avg;
  if (ttidAvg < 500) {
    recommendations.push(`\u2705 ${launchType} TTID \u4F18\u79C0 (${ttidAvg}ms)\uFF0C\u7EE7\u7EED\u4FDD\u6301`);
  } else if (ttidAvg < 1e3) {
    recommendations.push(`\u2705 ${launchType} TTID \u826F\u597D (${ttidAvg}ms)`);
  } else if (ttidAvg < 2e3) {
    recommendations.push(`\u26A0\uFE0F ${launchType} TTID \u4E00\u822C (${ttidAvg}ms)\uFF0C\u5EFA\u8BAE\u4F18\u5316\u81F3 1s \u4EE5\u5185`);
  } else {
    recommendations.push(`\u274C ${launchType} TTID \u8F83\u5DEE (${ttidAvg}ms)\uFF0C\u9700\u8981\u91CD\u70B9\u4F18\u5316`);
  }
  if (ttfdAvg > 0 && ttfdAvg > ttidAvg * 1.5) {
    recommendations.push(`\u26A0\uFE0F TTFD (${ttfdAvg}ms) \u6BD4 TTID \u6162 ${Math.round((ttfdAvg / ttidAvg - 1) * 100)}%\uFF0C\u5EFA\u8BAE\u68C0\u67E5\u5F02\u6B65\u52A0\u8F7D\u903B\u8F91`);
  }
  const variance = stats.ttid.max - stats.ttid.min;
  if (variance > 500) {
    recommendations.push(`\u26A0\uFE0F \u542F\u52A8\u65F6\u95F4\u6CE2\u52A8\u8F83\u5927 (${stats.ttid.min}ms ~ ${stats.ttid.max}ms)\uFF0C\u5EFA\u8BAE\u68C0\u67E5\u662F\u5426\u6709\u963B\u585E IO \u64CD\u4F5C`);
  }
  return recommendations;
}
async function measureAppLaunch(packageName, options = {}) {
  const {
    launchType = "cold_start",
    activityName,
    iterations = 3
  } = options;
  log(`Starting ${launchType} measurement for ${packageName} (${iterations} iterations)`);
  const results = [];
  for (let i = 1; i <= iterations; i++) {
    try {
      const metric = await measureSingleLaunch(packageName, launchType, activityName, i);
      results.push(metric);
      if (i < iterations) {
        await new Promise((resolve) => setTimeout(resolve, 2e3));
      }
    } catch (e) {
      error(`Measurement iteration ${i} failed:`, e);
    }
  }
  const ttidValues = results.map((r) => r.ttid).filter((v) => v > 0);
  const ttfdValues = results.map((r) => r.ttfd).filter((v) => v > 0);
  const totalTimeValues = results.map((r) => r.totalTime).filter((v) => v > 0);
  const statistics = {
    ttid: calculateStatistics(ttidValues),
    ttfd: calculateStatistics(ttfdValues),
    totalTime: calculateStatistics(totalTimeValues)
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
    recommendations
  };
}
function formatLaunchReport(result) {
  const lines = [
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
    "\u{1F680} APP LAUNCH SPEED REPORT",
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
    `\u{1F4E6} Package: ${result.packageName}`,
    `\u{1F504} Type: ${result.launchType}`,
    result.activityName ? `\u{1F3AF} Activity: ${result.activityName}` : "",
    `\u{1F4CA} Iterations: ${result.iterations}`,
    `\u{1F3C6} Grade: ${result.grade}`,
    "",
    "\u23F1\uFE0F  STATISTICS (ms)",
    "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
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
    "\u{1F4CB} RAW RESULTS",
    "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
    ...result.results.map(
      (r) => `  #${r.iteration}: TTID=${r.ttid}ms, TTFD=${r.ttfd}ms, Total=${r.totalTime}ms`
    ),
    "",
    "\u{1F4A1} RECOMMENDATIONS",
    "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
    ...result.recommendations.map((r) => `  ${r}`),
    "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550"
  ];
  return lines.filter((line) => line !== "").join("\n");
}

// src/tools/launch-speed.ts
async function handleMeasureAppLaunch(args) {
  const {
    packageName,
    launchType = "cold_start",
    activityName,
    iterations = 3
  } = args;
  if (!packageName) {
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: "packageName is required"
        })
      }]
    };
  }
  try {
    log(`Measuring app launch: ${packageName}, type: ${launchType}, iterations: ${iterations}`);
    const result = await measureAppLaunch(packageName, {
      launchType,
      activityName,
      iterations
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
          report
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    log("measure_app_launch failed:", err);
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Failed to measure app launch speed"
        })
      }]
    };
  }
}

// src/tools/hierarchy.ts
async function handleDumpHierarchy(args) {
  try {
    const includeRaw = Boolean(args.includeRaw);
    const elements = await getUIHierarchy();
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        count: elements.length,
        elements: elements.map(serializeElement),
        ...includeRaw ? { hint: "Use find_element to locate specific elements by text/resource-id" } : {}
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("dump_hierarchy failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleFindElement(args) {
  try {
    const text = args.text;
    const resourceId = args.resourceId;
    const className = args.className;
    const exact = Boolean(args.exact);
    if (!text && !resourceId && !className) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "At least one of text / resourceId / className is required"
      }) }] };
    }
    const elements = await getUIHierarchy();
    const matched = elements.filter((el) => {
      if (text) {
        const elText = el.text || "";
        if (exact ? elText !== text : !elText.includes(text)) return false;
      }
      if (resourceId && el.resourceId !== resourceId) return false;
      if (className && !el.type.toLowerCase().includes(className.toLowerCase())) return false;
      return true;
    });
    if (matched.length === 0) {
      return {
        content: [{ type: "text", text: JSON.stringify({
          success: true,
          found: false,
          count: 0,
          hint: "Element not visible. Use wait_for_element to poll, or take a fresh screenshot to verify state."
        }) }]
      };
    }
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        found: true,
        count: matched.length,
        // 主元素（第一个匹配）含中心坐标，可直接喂给 tap
        primary: withCenter(matched[0]),
        // 所有匹配项供 Agent 选择
        all: matched.map(withCenter)
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("find_element failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleWaitForElement(args) {
  try {
    const text = args.text;
    const resourceId = args.resourceId;
    const timeoutMs = args.timeoutMs ?? 1e4;
    const intervalMs = Math.max(100, args.intervalMs ?? 500);
    const expect = args.expect ?? "appear";
    if (!text && !resourceId) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "text or resourceId required"
      }) }] };
    }
    const start = Date.now();
    let lastPoll = [];
    while (Date.now() - start < timeoutMs) {
      const elements = await getUIHierarchy();
      lastPoll = elements;
      const matched = elements.filter((el) => {
        if (text && !(el.text || "").includes(text)) return false;
        if (resourceId && el.resourceId !== resourceId) return false;
        return true;
      });
      const isPresent = matched.length > 0;
      if (expect === "appear" && isPresent) {
        return {
          content: [{ type: "text", text: JSON.stringify({
            success: true,
            found: true,
            waitedMs: Date.now() - start,
            element: withCenter(matched[0])
          }) }]
        };
      }
      if (expect === "disappear" && !isPresent) {
        return {
          content: [{ type: "text", text: JSON.stringify({
            success: true,
            found: false,
            waitedMs: Date.now() - start
          }) }]
        };
      }
      await new Promise((r) => setTimeout(r, intervalMs));
    }
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: false,
        found: expect === "appear" ? false : true,
        waitedMs: Date.now() - start,
        pollCount: lastPoll.length,
        hint: expect === "appear" ? "Element did not appear within timeout" : "Element still visible after timeout"
      }) }]
    };
  } catch (e) {
    const err = e;
    error("wait_for_element failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
function serializeElement(el) {
  return {
    type: el.type,
    text: el.text,
    resourceId: el.resourceId,
    clickable: el.clickable,
    bounds: el.bounds
  };
}
function withCenter(el) {
  return {
    ...serializeElement(el),
    center: {
      x: Math.round(el.bounds.x + el.bounds.width / 2),
      y: Math.round(el.bounds.y + el.bounds.height / 2)
    }
  };
}

// src/tools/logcat-search.ts
async function handleLogcatSearch(args) {
  try {
    const pattern = args.pattern;
    const packageName = args.packageName;
    const tag = args.tag;
    const level = args.level ?? "I";
    const maxLines = args.maxLines ?? 200;
    const serial = args.serial;
    if (!pattern && !tag) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "At least one of pattern (regex) or tag is required"
      }) }] };
    }
    const serialFlag = serial ? `-s ${serial} ` : "";
    const tagFilter = tag ? `-s ${tag}:${level}` : `-s *:${level}`;
    const pid = packageName ? await safePidof(packageName) : null;
    const pidFlag = pid ? `--pid=${pid}` : "";
    const grep = pattern ? ` | grep -E ${shellQuote(pattern)}` : "";
    const cmd = `adb ${serialFlag}logcat -d ${tagFilter} -t ${maxLines} ${pidFlag}${grep}`.replace(/\s+/g, " ");
    log(`logcat_search: ${cmd}`);
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 8e3 });
    const lines = stdout.trim() ? stdout.trim().split("\n") : [];
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        matched: lines.length,
        pattern: pattern || null,
        tag: tag || null,
        level,
        appRunning: pid ? true : packageName ? false : null,
        lines: lines.slice(0, maxLines)
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("logcat_search failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleParseCrash(args) {
  try {
    const packageName = args.packageName;
    const lookbackSec = args.lookbackSec ?? 300;
    const serial = args.serial;
    const serialFlag = serial ? `-s ${serial} ` : "";
    const pkgFilter = packageName ? ` | grep -i ${shellQuote(packageName)}` : "";
    const cmd = `adb ${serialFlag}logcat -d -s AndroidRuntime:E ActivityManager:E DEBUG:E System.err:W *:S -t 2000${pkgFilter}`;
    log(`parse_crash: ${cmd}`);
    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 1e4 });
    const raw = stdout.trim();
    if (!raw) {
      return {
        content: [{ type: "text", text: JSON.stringify({
          success: true,
          crashes: 0,
          hint: "No crash/ANR records found. Try lookbackSec larger or check if filter is too strict."
        }) }]
      };
    }
    const groups = groupCrashes(raw);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        crashCount: groups.length,
        crashes: groups,
        rawLineCount: raw.split("\n").length
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("parse_crash failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
function shellQuote(s) {
  return `'${s.replace(/'/g, "'\\''")}'`;
}
function groupCrashes(raw) {
  const lines = raw.split("\n");
  const groups = [];
  let current = null;
  const push = () => {
    if (current && current.stack.length > 0) groups.push(current);
    current = null;
  };
  for (const line of lines) {
    const fatal = line.match(/^(\S+\s+\S+).*FATAL EXCEPTION.*:?\s*(.*)$/);
    if (fatal) {
      push();
      current = {
        type: "java_exception",
        timestamp: fatal[1],
        exception: "FATAL",
        message: fatal[2] || void 0,
        stack: [line],
        raw: line
      };
      continue;
    }
    const anr = line.match(/^(\S+\s+\S+).*ANR in (\S+)/);
    if (anr) {
      push();
      current = {
        type: "anr",
        timestamp: anr[1],
        process: anr[2],
        message: "ANR detected",
        stack: [line],
        raw: line
      };
      continue;
    }
    const native = line.match(/^(\S+\s+\S+).*signal\s+(\d+)\s+\(SIG(SEGV|ABRT|ILL|FPE|BUS)\)/);
    if (native) {
      push();
      current = {
        type: "native_crash",
        timestamp: native[1],
        message: native[0].split(":").slice(1).join(":").trim(),
        stack: [line],
        raw: line
      };
      continue;
    }
    if (current) {
      current.stack.push(line);
      current.raw += "\n" + line;
    } else {
      if (/(FATAL|AndroidRuntime|DEBUG\s|tombstone|signal\s\d+)/i.test(line)) {
        current = {
          type: "fatal",
          stack: [line],
          raw: line
        };
      }
    }
    if (current && /^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+[A-Z]/.test(line) && current.stack.length > 1 && line !== current.stack[0]) {
      push();
    }
  }
  push();
  return groups.map((g) => ({
    ...g,
    // 截取前 30 行 stack 防止输出爆炸
    stack: g.stack.slice(0, 30)
  }));
}

// src/tools/apk-metadata.ts
async function handleApkMetadata(args) {
  try {
    const apkPath = args.apkPath;
    if (!apkPath) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "apkPath is required"
      }) }] };
    }
    const tool = await detectApkTool();
    if (!tool) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "No APK introspection tool found. Set AAPT2_PATH / AAPT_PATH env or install via Android SDK build-tools.",
        hint: "Android SDK build-tools 30+ ships with aapt2. Or use apkanalyzer from cmdline-tools."
      }) }] };
    }
    const { stdout, stderr } = await execAsyncWithTimeout(
      `${tool} dump badging "${apkPath}"`,
      { timeout: 2e4 }
    );
    const meta = parseBadging(stdout);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        tool,
        ...meta
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("apk_metadata failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function detectApkTool() {
  const candidates = [
    process.env.AAPT2_PATH,
    process.env.AAPT_PATH,
    "aapt2",
    "aapt"
  ].filter(Boolean);
  for (const c of candidates) {
    try {
      await execAsyncWithTimeout(`${c} version`, { timeout: 2e3 });
      return c;
    } catch {
    }
  }
  return null;
}
function parseBadging(text) {
  const get = (key) => {
    const m = text.match(new RegExp(`^${key}:\\s*'?([^'\\n]+)'?`, "m"));
    return m ? m[1].trim() : null;
  };
  const all = (key) => {
    const re = new RegExp(`^${key}:\\s*'?([^'\\n]+)'?`, "gm");
    const out = [];
    let m;
    while ((m = re.exec(text)) !== null) out.push(m[1].trim());
    return out;
  };
  return {
    packageName: get("package"),
    versionCode: get("versionCode"),
    versionName: get("versionName"),
    compileSdk: get("sdkVersion"),
    targetSdk: get("targetSdkVersion"),
    minSdk: get("minSdkVersion"),
    applicationLabel: get("application-label"),
    launchableActivity: get("launchable-activity"),
    permissions: all("uses-permission"),
    features: all("uses-feature"),
    locales: all("locales"),
    nativeCode: all("native-code"),
    densities: all("densities")
  };
}

// src/tools/screenshot-region.ts
import sharp2 from "sharp";
async function handleScreenshotRegion(args) {
  try {
    const x = args.x;
    const y = args.y;
    const width = args.width;
    const height = args.height;
    const savePath = args.savePath;
    if ([x, y, width, height].some((v) => v === void 0 || v < 0)) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "x, y, width, height are all required and must be >= 0"
      }) }] };
    }
    const full = await screenshot();
    const outPath = savePath || full.path.replace(/\.png$/, `_region_${x}_${y}_${width}x${height}.png`);
    await sharp2(full.path).extract({ left: Math.round(x), top: Math.round(y), width: Math.round(width), height: Math.round(height) }).toFile(outPath);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        path: outPath,
        region: { x, y, width, height },
        parentPath: full.path
      }) }]
    };
  } catch (e) {
    const err = e;
    error("screenshot_region failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// src/tools/device-control.ts
async function handleSetOrientation(args) {
  try {
    const orientation = args.orientation ?? "portrait";
    const serial = args.serial;
    const serialFlag = serial ? `-s ${serial} ` : "";
    let rotationValue;
    let userRotation;
    let accelRotation;
    switch (orientation) {
      case "portrait":
        rotationValue = 0;
        userRotation = 0;
        accelRotation = 0;
        break;
      case "landscape":
        rotationValue = 1;
        userRotation = 1;
        accelRotation = 0;
        break;
      case "auto":
        rotationValue = 0;
        userRotation = 0;
        accelRotation = 1;
        break;
      default:
        return { isError: true, content: [{ type: "text", text: JSON.stringify({
          success: false,
          error: `Unknown orientation: ${orientation}. Use portrait / landscape / auto`
        }) }] };
    }
    await execAsyncWithTimeout(
      `adb ${serialFlag}shell settings put system accelerometer_rotation ${accelRotation}`,
      { timeout: 5e3 }
    );
    await execAsyncWithTimeout(
      `adb ${serialFlag}shell settings put system user_rotation ${userRotation}`,
      { timeout: 5e3 }
    );
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        orientation,
        accelRotation,
        userRotation,
        hint: "Some apps override orientation via Activity manifest. If rotation did not change, that app locks orientation."
      }) }]
    };
  } catch (e) {
    const err = e;
    error("set_orientation failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleSetGps(args) {
  try {
    const lat = args.lat;
    const lon = args.lon;
    const serial = args.serial;
    if (lat === void 0 || lon === void 0) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "lat and lon are required (decimal degrees, e.g. 39.9042 / 116.4074 for Beijing)"
      }) }] };
    }
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "lat must be [-90, 90], lon must be [-180, 180]"
      }) }] };
    }
    const emuCmd = serial ? `adb -s ${serial} emu geo fix ${lon} ${lat}` : `adb emu geo fix ${lon} ${lat}`;
    const { stderr: emuErr } = await execAsyncWithTimeout(emuCmd, { timeout: 5e3 }).catch((e) => {
      throw new Error("GPS mocking requires Android Emulator. Real devices need 'mock location' app + developer option enabled.");
    });
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        lat,
        lon,
        method: serial ? `emu geo fix on ${serial}` : "emu geo fix (default device)",
        warning: emuErr || "If GPS unchanged, ensure you're on an Android Emulator (not physical device)."
      }) }]
    };
  } catch (e) {
    const err = e;
    error("set_gps failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleAnimationScale(args) {
  try {
    const scale = args.scale;
    const serial = args.serial;
    const serialFlag = serial ? `-s ${serial} ` : "";
    if (scale === void 0 || scale < 0 || scale > 10) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "scale must be a number in [0, 10]. Common values: 0 (off), 0.5, 1 (default), 2 (slow)."
      }) }] };
    }
    const keys = ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"];
    const results = [];
    for (const k of keys) {
      try {
        await execAsyncWithTimeout(
          `adb ${serialFlag}shell settings put global ${k} ${scale}`,
          { timeout: 5e3 }
        );
        results.push({ key: k, value: scale, success: true });
      } catch {
        results.push({ key: k, value: scale, success: false });
      }
    }
    const allOk = results.every((r) => r.success);
    return {
      ...allOk ? {} : { isError: true },
      content: [{ type: "text", text: JSON.stringify({
        success: allOk,
        scale,
        applied: results,
        hint: "0 = animations off (UI tests run instantly). Set back to 1 to restore."
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("animation_scale failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// src/tools/design-spec.ts
import fs4 from "fs";
import path2 from "path";
var DESIGN_DIR = process.env.DESIGN_DIR || "./design";
async function handleExtractDesignSpec(args) {
  try {
    const imagePath = resolveImagePath(args.imagePath);
    const format = args.format || "both";
    const pageHint = args.pageHint ?? derivePageHint(imagePath);
    const model = args.model;
    const provider = args.provider;
    log(`extract_design_spec: ${imagePath}, format=${format}, hint=${pageHint}, model=${model || "default"}, provider=${provider || "default"}`);
    const result = await extractDesignSpec(imagePath, { format, pageHint, model, provider });
    if (format === "json") {
      return {
        content: [{ type: "text", text: JSON.stringify({
          success: true,
          source: result.source,
          model: result.model,
          pageHint,
          json: result.json
        }, null, 2) }]
      };
    }
    if (format === "markdown") {
      return {
        content: [{ type: "text", text: result.markdown || "" }]
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
        usage: "Use 'json' field for programmatic consumption (Compose theme, layout code). Use 'markdown' field for human review."
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("extract_design_spec failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleExtractDesignTokens(args) {
  try {
    const imagePath = resolveImagePath(args.imagePath);
    const model = args.model;
    const provider = args.provider;
    const result = await extractColorTokens(imagePath, model, provider);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("extract_design_tokens failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleExtractComponents(args) {
  try {
    const imagePath = resolveImagePath(args.imagePath);
    const pageHint = args.pageHint ?? derivePageHint(imagePath);
    const model = args.model;
    const provider = args.provider;
    const result = await extractComponents(imagePath, pageHint, model, provider);
    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("extract_design_components failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleListDesignFiles(args) {
  try {
    const dir = args.dir || DESIGN_DIR;
    if (!fs4.existsSync(dir)) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: `Directory not found: ${dir}`,
        hint: `Set DESIGN_DIR env or pass dir= argument`
      }) }] };
    }
    const entries = fs4.readdirSync(dir, { withFileTypes: true });
    const imageExts = /* @__PURE__ */ new Set([".png", ".jpg", ".jpeg", ".webp"]);
    const files = entries.filter((e) => e.isFile() && imageExts.has(path2.extname(e.name).toLowerCase())).map((e) => {
      const full = path2.join(dir, e.name);
      const stat = fs4.statSync(full);
      return {
        name: e.name,
        path: full,
        sizeKB: Math.round(stat.size / 1024),
        modifiedAt: stat.mtime.toISOString(),
        pageHint: fileNameToPageHint(e.name)
      };
    });
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        dir,
        count: files.length,
        files,
        usage: "Pass any file.path to extract_design_spec to get its structured design spec."
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("list_design_files failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handleDesignToCompose(args) {
  try {
    const imagePath = resolveImagePath(args.imagePath);
    const packageName = args.packageName || "com.example.app";
    const model = args.model;
    const provider = args.provider;
    const result = await designToComposeSkeleton(imagePath, packageName, model, provider);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        source: result.source,
        model: result.model,
        packageName,
        fileName: deriveScreenFileName(imagePath),
        kotlin: result.kotlin,
        notes: result.notes,
        usage: `Write result.kotlin to app/src/main/java/${packageName.replace(/\./g, "/")}/presentation/<page>/${deriveScreenFileName(imagePath)}`
      }, null, 2) }]
    };
  } catch (e) {
    const err = e;
    error("design_to_compose failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
function resolveImagePath(input) {
  if (!input) {
    throw new Error("imagePath is required. Use list_design_files to discover available designs.");
  }
  if (fs4.existsSync(input)) return path2.resolve(input);
  const inDesign = path2.join(DESIGN_DIR, input);
  if (fs4.existsSync(inDesign)) return path2.resolve(inDesign);
  if (fs4.existsSync(inDesign + ".jpg")) return path2.resolve(inDesign + ".jpg");
  if (fs4.existsSync(inDesign + ".png")) return path2.resolve(inDesign + ".png");
  throw new Error(`Image not found: ${input} (also tried ${inDesign}{.jpg,.png})`);
}
function fileNameToPageHint(filename) {
  return path2.basename(filename, path2.extname(filename));
}
function derivePageHint(imagePath) {
  return fileNameToPageHint(imagePath);
}
function deriveScreenFileName(imagePath) {
  const base = path2.basename(imagePath, path2.extname(imagePath));
  const safe = base.replace(/[^\w一-龥-]/g, "");
  return `${safe}Screen.kt`;
}

// src/tools/pm.ts
import fs5 from "fs";
import path3 from "path";
var PM_TOOL_REGISTRY = {
  // 设备操作
  tap: handleTap,
  swipe: handleSwipe,
  input_text: handleInputText,
  press_key: handlePressKey,
  // 应用生命周期
  // 项目专属默认：build 必须在含 gradlew 的目录跑，install_and_launch 必须带 packageName+activity
  // （handler 自身没有合理的兜底，所以 PM registry 这层补上）
  build: (args) => handleBuild({ ...args, projectPath: args.projectPath || _findProjectRoot() }),
  install_and_launch: (args) => handleInstallAndLaunch({
    packageName: "com.example.toutiao",
    activity: "MainActivity",
    ...args
  }),
  stop_app: (args) => handleAppManagement({ packageName: "com.example.toutiao", ...args }, "stop_app"),
  clear_app_data: (args) => handleAppManagement({ packageName: "com.example.toutiao", ...args }, "clear_app_data"),
  list_apps: (args) => handleAppManagement(args, "list_apps"),
  app_info: (args) => handleAppManagement({ packageName: "com.example.toutiao", ...args }, "app_info"),
  // 诊断
  get_logs: handleGetLogs,
  shell_command: (args) => handleDeviceManagement(args, "shell_command"),
  performance_metrics: handlePerformanceMonitor,
  measure_app_launch: handleMeasureAppLaunch,
  clear_logs: handleClearLogs,
  // 视觉智能
  vision_action: handleVisionAction,
  find_element: handleFindElement,
  wait_for_element: handleWaitForElement,
  analyze_screenshot: handleAnalyzeScreenshot,
  compare_screenshots: handleCompareScreenshots,
  verify_ui: handleVerifyUI,
  dump_hierarchy: handleDumpHierarchy,
  dump_ui: handleDumpUi,
  // 截图/录屏
  screenshot: handleScreenshot,
  screenshot_region: handleScreenshotRegion,
  record_screen: (args) => handleDeviceManagement(args, "record_screen"),
  // 设备控制
  set_orientation: handleSetOrientation,
  set_gps: handleSetGps,
  set_animation_scale: handleAnimationScale,
  set_network: (args) => handleNetworkDebug(args, "set_state")
};
var INTERACTIVE_TOOLS = /* @__PURE__ */ new Set([
  "tap",
  "swipe",
  "input_text",
  "press_key",
  "vision_action",
  "wait_for_element"
]);
var SETTLING_TOOLS = /* @__PURE__ */ new Set([
  "build",
  "install_and_launch",
  "stop_app",
  "clear_app_data",
  "list_apps",
  "app_info",
  "get_logs",
  "performance_metrics",
  "measure_app_launch",
  "shell_command",
  "clear_logs",
  "find_element",
  "analyze_screenshot",
  "compare_screenshots",
  "verify_ui",
  "dump_hierarchy",
  "dump_ui",
  "screenshot",
  "screenshot_region",
  "record_screen",
  "set_orientation",
  "set_gps",
  "set_animation_scale",
  "set_network"
]);
var REVIEW_DIR = process.env.PM_REVIEW_DIR || "./pm_reviews";
var CHECKLIST_PATH = process.env.PM_CHECKLIST_PATH || "./pm_checklist_toutiao.md";
var PROMPT_TEMPLATE_PATH = process.env.PM_PROMPT_PATH || "./skills/prompts/pm_review.txt";
var PROMPT_EXPLORE_PATH = process.env.PM_EXPLORE_PROMPT_PATH || "./skills/prompts/pm_explore_step.txt";
var DEFAULT_FOCUS = [
  "ui_bug: \u770B\u8D77\u6765\u5BF9\u3001\u5176\u5B9E\u6709\u89C6\u89C9/\u4EA4\u4E92\u95EE\u9898\u7684\u5730\u65B9",
  "ux: \u7528\u6237\u5B9E\u9645\u4F7F\u7528\u65F6\u53EF\u80FD\u5361\u58F3\u7684\u5730\u65B9",
  "performance: \u6027\u80FD\u3001\u6D41\u7545\u5EA6"
];
async function _takeScreenshot(savePath) {
  const out = savePath || `/tmp/pm_screenshot_${Date.now()}.png`;
  await screenshot(out);
  return out;
}
function _loadChecklist() {
  const candidates = [
    CHECKLIST_PATH,
    path3.resolve(process.cwd(), CHECKLIST_PATH),
    path3.resolve(process.cwd(), "..", CHECKLIST_PATH)
  ];
  for (const p of candidates) {
    if (fs5.existsSync(p)) return fs5.readFileSync(p, "utf-8");
  }
  return "(checklist \u4E0D\u53EF\u7528\uFF0C\u6309\u901A\u7528 Android \u6700\u4F73\u5B9E\u8DF5\u5BA1\u67E5)";
}
function _loadPromptTemplate() {
  const candidates = [
    PROMPT_TEMPLATE_PATH,
    path3.resolve(process.cwd(), PROMPT_TEMPLATE_PATH),
    path3.resolve(process.cwd(), "..", PROMPT_TEMPLATE_PATH)
  ];
  for (const p of candidates) {
    if (fs5.existsSync(p)) return fs5.readFileSync(p, "utf-8");
  }
  throw new Error(`PM \u63D0\u793A\u8BCD\u6A21\u677F\u4E0D\u5B58\u5728: ${PROMPT_TEMPLATE_PATH}`);
}
function _fillPromptTemplate(tpl, vars) {
  return tpl.replace("${target}", vars.target).replace("${focus_or_default}", vars.focus).replace("${checklist}", vars.checklist).replace("${ui_dump_summary}", vars.uiDumpSummary);
}
function _summaryFromDump(elements) {
  const texts = elements.map((e) => e.text).filter((t) => Boolean(t));
  const uniqueTexts = Array.from(new Set(texts));
  const clickableCount = elements.filter((e) => e.clickable).length;
  const top = uniqueTexts.slice(0, 30).join(" | ");
  return [
    `\u8282\u70B9\u603B\u6570: ${elements.length}`,
    `\u53EF\u70B9\u51FB\u8282\u70B9\u6570: ${clickableCount}`,
    `\u53EF\u89C1\u6587\u672C\uFF08\u524D 30 \u6761\u53BB\u91CD\uFF09: ${top || "(\u65E0)"}`
  ].join("\n");
}
function _parseJsonFromVision(raw) {
  let text = raw.trim();
  text = text.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  if (text.startsWith("<think>")) {
    const firstBrace = text.indexOf("{");
    if (firstBrace > 0) text = text.slice(firstBrace);
  }
  const fenced = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (fenced) text = fenced[1].trim();
  const a = text.indexOf("{");
  const b = text.lastIndexOf("}");
  if (a !== -1 && b > a) text = text.slice(a, b + 1);
  try {
    return JSON.parse(text);
  } catch {
    const fixed = text.replace(/,(\s*[}\]])/g, "$1").replace(/([{,]\s*)([A-Za-z_][\w$]*)(\s*:)/g, '$1"$2"$3');
    try {
      return JSON.parse(fixed);
    } catch {
      return { _parseError: true, _raw: raw.slice(0, 2e3) };
    }
  }
}
async function _callVision(imagePath, systemPrompt, userPrompt, maxTokens = 4e3) {
  const cfg = getActiveProvider();
  const modelId = process.env.VISION_MODEL || cfg.defaultModel;
  const resizedPath = await smartResizeForVision(imagePath);
  try {
    const imageUrl = encodeImageAsDataUrl(resizedPath);
    return await callVisionLlmWithTokens(modelId, systemPrompt, userPrompt, imageUrl, maxTokens);
  } finally {
    if (resizedPath !== imagePath) {
      try {
        fs5.unlinkSync(resizedPath);
      } catch {
      }
    }
  }
}
async function callVisionLlmWithTokens(modelId, systemPrompt, userPrompt, imageUrl, maxTokens) {
  const { activeProvider: activeProvider2, makeInsecureFetch: makeInsecureFetch2 } = await import("./design-extractor-Q6J6UBIL.js");
  const { default: OpenAI2 } = await import("openai");
  const cfg = activeProvider2();
  const model = modelId || cfg.defaultModel;
  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) throw new Error(`${cfg.apiKeyEnv} not set`);
  const clientOpts = { apiKey, baseURL: cfg.baseURL };
  if (cfg.insecureTLS) {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    clientOpts.fetch = makeInsecureFetch2();
  }
  const client = new OpenAI2(clientOpts);
  const requestOpts = {
    model,
    messages: [
      { role: "system", content: systemPrompt },
      {
        role: "user",
        content: [
          { type: "image_url", image_url: { url: imageUrl } },
          { type: "text", text: userPrompt }
        ]
      }
    ],
    max_tokens: maxTokens,
    temperature: 1
  };
  if (model === "MiniMax-M3") {
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }
  const response = await client.chat.completions.create(requestOpts, { timeout: 12e4 });
  const msg = response.choices[0]?.message;
  let content = msg?.content;
  if (!content && msg?.reasoning_content) content = msg.reasoning_content;
  if (!content) throw new Error("Vision model returned empty response");
  return content;
}
function _serializeDumpNode(el) {
  return {
    class: el.type,
    text: el.text,
    resource_id: el.resourceId,
    content_desc: el.contentDesc,
    bounds: `[${el.bounds.x},${el.bounds.y}][${el.bounds.x + el.bounds.width},${el.bounds.y + el.bounds.height}]`,
    clickable: el.clickable,
    enabled: el.enabled ?? true
  };
}
async function _dumpUiInternal(savePath) {
  const localPath = savePath || `/tmp/ui_dump_${Date.now()}.xml`;
  let xml = "";
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb exec-out "uiautomator dump /dev/tty"',
      { timeout: 15e3 }
    );
    xml = stdout;
  } catch {
    await execAsyncWithTimeout(
      'adb shell "uiautomator dump /sdcard/window_dump.xml"',
      { timeout: 15e3 }
    );
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "cat /sdcard/window_dump.xml"',
      { timeout: 1e4 }
    );
    xml = stdout;
  }
  fs5.writeFileSync(localPath, xml, "utf-8");
  const baseNodes = await getUIHierarchy();
  const enriched = baseNodes.map((el) => ({
    ...el,
    contentDesc: void 0,
    enabled: true
  }));
  const nodeRegex = /<node\b([^>]*?)\/?>/g;
  const extras = /* @__PURE__ */ new Map();
  let m;
  while ((m = nodeRegex.exec(xml)) !== null) {
    const attrs = m[1];
    const textMatch = attrs.match(/text="([^"]*)"/);
    const text = textMatch ? textMatch[1] : "";
    const contentDesc = attrs.match(/content-desc="([^"]*)"/)?.[1] || void 0;
    const enabledMatch = attrs.match(/enabled="([^"]*)"/);
    const enabled = enabledMatch ? enabledMatch[1] === "true" : void 0;
    const classMatch = attrs.match(/class="([^"]*)"/)?.[1];
    if (text) extras.set(text, { contentDesc, enabled, fullClass: classMatch });
  }
  for (const el of enriched) {
    const ex = el.text ? extras.get(el.text) : void 0;
    el.contentDesc = ex?.contentDesc;
    el.enabled = ex?.enabled;
    if (ex?.fullClass) el.type = ex.fullClass;
  }
  return { nodes: enriched.map(_serializeDumpNode), rawPath: localPath };
}
async function handleDumpUi(args) {
  try {
    const savePath = args.savePath;
    const { nodes, rawPath } = await _dumpUiInternal(savePath);
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          dump_path: rawPath,
          node_count: nodes.length,
          nodes,
          hint: "Use find_element to locate a specific node by text/resource-id. Pass dump_path to PM tools for full UI context."
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("dump_ui failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handlePmReview(args) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const target = args.target || "\u9996\u9875\u5217\u8868";
    const focus = args.focus || DEFAULT_FOCUS;
    const screenshotPath = args.screenshotPath || void 0;
    logTrace({ type: "tool_call", tool: "pm_review", args: { target, focus }, session_id: sessionId });
    const shot = screenshotPath || await _takeScreenshot();
    const { nodes: dumpNodes } = await _dumpUiInternal();
    const tpl = _loadPromptTemplate();
    const checklist = _loadChecklist();
    const filled = _fillPromptTemplate(tpl, {
      target,
      focus: focus.join("\n- "),
      checklist,
      uiDumpSummary: _summaryFromDump(dumpNodes)
    });
    const systemPrompt = "\u4F60\u662F Android \u4EA7\u54C1\u7ECF\u7406\u3002\u4E25\u683C\u6309\u7528\u6237\u7ED9\u51FA\u7684 JSON Schema \u8F93\u51FA\uFF1A\n1) \u552F\u4E00\u8F93\u51FA\uFF1A\u4E00\u4E2A JSON \u5BF9\u8C61\uFF0C\u4ECE { \u5F00\u59CB\u5230 } \u7ED3\u675F\n2) \u7981\u6B62\uFF1A```json``` \u56F4\u680F\u3001<think> \u5757\u3001\u4EFB\u4F55 markdown\u3001\u4EFB\u4F55\u89E3\u91CA\u6027\u6587\u5B57\u3001\u4EFB\u4F55\u524D\u540E\u7F00\n3) thinking_process \u5B57\u6BB5\u662F JSON \u5185\u7684\u5B57\u7B26\u4E32\u503C\uFF0C\u53EF\u4EE5\u5305\u542B\u6362\u884C\uFF0C\u4F46\u8981\u4F5C\u4E3A\u5B57\u7B26\u4E32\u5B57\u9762\u91CF\u8F93\u51FA";
    const raw = await _callVision(shot, systemPrompt, filled, 8e3);
    const parsed = _parseJsonFromVision(raw);
    const reviewId = `rev-${Date.now()}`;
    fs5.mkdirSync(REVIEW_DIR, { recursive: true });
    const review = {
      timestamp: (/* @__PURE__ */ new Date()).toISOString(),
      target,
      focus,
      screenshot: shot,
      ui_dump_summary: {
        node_count: dumpNodes.length,
        texts: dumpNodes.map((n) => n.text).filter(Boolean).slice(0, 50)
      },
      ...parsed,
      review_id: reviewId
    };
    const reviewFile = path3.join(REVIEW_DIR, `${reviewId}.json`);
    fs5.writeFileSync(reviewFile, JSON.stringify(review, null, 2), "utf-8");
    const { review_id: _ignoredReviewId, ...reviewForReturn } = review;
    const memory = _loadPmMemory();
    _updateMemoryFromReview(memory, {
      review_id: reviewId,
      timestamp: review.timestamp,
      tool: "pm_review",
      target,
      overall_rating: String(review.overall_rating || "C"),
      issues: (Array.isArray(review.issues) ? review.issues : []).map((issue, idx) => ({
        issue_id: issue.id || _generateIssueId(memory),
        severity: issue.severity || "medium",
        category: issue.category || "ui_bug",
        description: `${issue.location || ""}: ${issue.current_state || ""} \u2192 ${issue.expected_state || ""}`,
        location: issue.location || "",
        design_ref: issue.design_ref || "",
        status: "open"
      })),
      positives: Array.isArray(review.positives) ? review.positives : []
    });
    logTrace({ type: "done", tool: "pm_review", overall_rating: String(review.overall_rating || "C"), issues_found: Array.isArray(review.issues) ? review.issues.length : 0, elapsed_ms: Date.now() - Date.parse(review.timestamp), session_id: sessionId });
    logTrace({ type: "memory_update", action: "append_review", review_id: reviewId, session_id: sessionId });
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          review_id: reviewId,
          review_file: reviewFile,
          ...reviewForReturn
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_review failed:", err);
    logTrace({ type: "error", tool: "pm_review", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function _pixelDiff(designPath, implPath) {
  const sharp3 = (await import("sharp")).default;
  const { default: pixelmatch2 } = await import("pixelmatch");
  const { PNG: PNG2 } = await import("pngjs");
  const [dBuf, iBuf] = await Promise.all([
    sharp3(designPath).raw().toBuffer({ resolveWithObject: true }),
    sharp3(implPath).raw().toBuffer({ resolveWithObject: true })
  ]);
  const w = Math.min(dBuf.info.width, iBuf.info.width);
  const h = Math.min(dBuf.info.height, iBuf.info.height);
  const dCrop = await sharp3(designPath).resize(w, h).raw().toBuffer();
  const iCrop = await sharp3(implPath).resize(w, h).raw().toBuffer();
  const channels = Math.min(dBuf.info.channels, iBuf.info.channels);
  const diff = new PNG2({ width: w, height: h });
  const mismatched = pixelmatch2(
    Buffer.from(dCrop),
    Buffer.from(iCrop),
    diff.data,
    w,
    h,
    { threshold: 0.1 }
  );
  const diffImagePath = `/tmp/pm_diff_${Date.now()}.png`;
  const outChannels = channels === 3 || channels === 4 ? channels : 4;
  await sharp3(Buffer.from(diff.data), { raw: { width: w, height: h, channels: outChannels } }).png().toFile(diffImagePath);
  return {
    mismatched,
    total: w * h,
    ratio: mismatched / (w * h),
    diffImagePath
  };
}
async function handlePmCompareWithDesign(args) {
  try {
    const designPath = args.designPath;
    const implScreenshotPath = args.implScreenshotPath || void 0;
    if (!designPath) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "designPath required" }) }] };
    }
    if (!await fileExists(designPath)) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: `Design not found: ${designPath}` }) }] };
    }
    const impl = implScreenshotPath || await _takeScreenshot();
    let diff = { mismatched: 0, total: 0, ratio: 0, diffImagePath: null };
    try {
      diff = await _pixelDiff(designPath, impl);
    } catch (e) {
      diff = { error: e.message };
    }
    const ratioPct = "ratio" in diff ? (diff.ratio * 100).toFixed(1) : "?";
    const systemPrompt = "\u4F60\u662F\u4E00\u4F4D\u8D44\u6DF1 Android \u4EA7\u54C1\u7ECF\u7406\uFF0C\u64C5\u957F\u628A\u8BBE\u8BA1\u7A3F\u548C\u5B9E\u73B0\u8FDB\u884C\u5BF9\u6BD4\uFF0C\u6307\u51FA\u53EF\u63A5\u53D7\u7684\u5DEE\u5F02\u548C\u9700\u8981\u4FEE\u590D\u7684\u5DEE\u5F02\u3002";
    const userPrompt = `\u8BBE\u8BA1\u7A3F: ${designPath}
\u5F53\u524D\u5B9E\u73B0\u622A\u56FE: ${impl}
\u50CF\u7D20 diff \u6BD4\u4F8B: ${ratioPct}%\uFF08\u7528 pixelmatch \u8BA1\u7B97\uFF0Cthreshold=0.1\uFF09

\u8BF7\u5206\u6790\uFF1A
1. \u54EA\u4E9B\u5DEE\u5F02\u662F critical\uFF08\u5F71\u54CD\u529F\u80FD\u6216\u89C6\u89C9\u4E00\u81F4\u6027\uFF09\u2014 \u5217\u51FA 2-5 \u6761
2. \u54EA\u4E9B\u5DEE\u5F02\u662F acceptable\uFF08\u5B9E\u73B0\u5408\u7406\u3001\u53EF\u4E0D\u6539\uFF09\u2014 \u7B80\u77ED\u5217\u51FA
3. \u7ED9\u51FA\u4FEE\u590D\u4F18\u5148\u7EA7\uFF08\u5148\u6539\u54EA\u4E2A\u3001\u540E\u6539\u54EA\u4E2A\uFF09

\u8F93\u51FA JSON\uFF1A
{
  "critical_issues": [{"location": "...", "diff": "...", "fix_priority": 1}],
  "acceptable_diffs": ["..."],
  "fix_order": ["critical_issues[0]", "critical_issues[1]"],
  "summary": "\u4E00\u53E5\u8BDD"
}`;
    const raw = await _callVision(impl, systemPrompt, userPrompt);
    const llm = _parseJsonFromVision(raw);
    return {
      content: [{
        type: "text",
        text: JSON.stringify({ success: true, design: designPath, impl, pixel_diff: diff, llm_analysis: llm }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_compare_with_design failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
var PM_STATE_PATH = process.env.PM_STATE_PATH || "./.pm_state.json";
var TRACE_PATH = process.env.PM_TRACE_PATH || "./pm_trace.jsonl";
var PM_MEMORY_PATH = process.env.PM_MEMORY_PATH || "./.pm_memory.json";
var PM_DISCUSSION_PATH = process.env.PM_DISCUSSION_PATH || "./.pm_discussions.json";
var MAX_DISCUSSION_HISTORY = 10;
function _loadPmState() {
  if (!fs5.existsSync(PM_STATE_PATH)) return { fixed: [], ignored: [] };
  try {
    return JSON.parse(fs5.readFileSync(PM_STATE_PATH, "utf-8"));
  } catch {
    return { fixed: [], ignored: [] };
  }
}
function _savePmState(state) {
  fs5.writeFileSync(PM_STATE_PATH, JSON.stringify(state, null, 2), "utf-8");
}
function logTrace(event) {
  try {
    const line = JSON.stringify({ ...event, ts: (/* @__PURE__ */ new Date()).toISOString() });
    fs5.appendFileSync(TRACE_PATH, line + "\n");
  } catch {
  }
}
function _loadPmMemory() {
  if (!fs5.existsSync(PM_MEMORY_PATH)) {
    return {
      project: { name: "ToutiaoFeedDemo", package_name: "com.example.toutiao", main_activity: "MainActivity", version: "1.0.0" },
      design_specs: { sources: [], tokens: {} },
      reviews: [],
      issue_counter: 0,
      current_focus: { channel: "recommend", page: "\u9996\u9875\u63A8\u8350", last_review_id: null }
    };
  }
  try {
    return JSON.parse(fs5.readFileSync(PM_MEMORY_PATH, "utf-8"));
  } catch {
    return {
      project: { name: "ToutiaoFeedDemo", package_name: "com.example.toutiao", main_activity: "MainActivity", version: "1.0.0" },
      design_specs: { sources: [], tokens: {} },
      reviews: [],
      issue_counter: 0,
      current_focus: { channel: "recommend", page: "\u9996\u9875\u63A8\u8350", last_review_id: null }
    };
  }
}
function _savePmMemory(memory) {
  fs5.writeFileSync(PM_MEMORY_PATH, JSON.stringify(memory, null, 2), "utf-8");
}
function _generateIssueId(memory) {
  memory.issue_counter += 1;
  return `ISSUE-${String(memory.issue_counter).padStart(3, "0")}`;
}
function _updateMemoryFromReview(memory, review) {
  memory.reviews.push(review);
  memory.current_focus.last_review_id = review.review_id;
  memory.current_focus.page = review.target;
  if (review.channel) memory.current_focus.channel = review.channel;
  for (const issue of review.issues) {
    if (!issue.status) issue.status = "open";
  }
  _savePmMemory(memory);
}
function _loadDiscussionHistory() {
  if (!fs5.existsSync(PM_DISCUSSION_PATH)) return { sessions: [] };
  try {
    return JSON.parse(fs5.readFileSync(PM_DISCUSSION_PATH, "utf-8"));
  } catch {
    return { sessions: [] };
  }
}
function _saveDiscussionHistory(history) {
  fs5.writeFileSync(PM_DISCUSSION_PATH, JSON.stringify(history, null, 2), "utf-8");
}
function _appendDiscussion(question, answer, tool, resultSummary) {
  const history = _loadDiscussionHistory();
  let session = history.sessions[history.sessions.length - 1];
  if (!session) {
    session = {
      session_id: `sess-${Date.now()}`,
      started_at: (/* @__PURE__ */ new Date()).toISOString(),
      context: {},
      messages: []
    };
    history.sessions.push(session);
  }
  const now = (/* @__PURE__ */ new Date()).toISOString();
  if (tool) {
    session.messages.push({ role: "pm_tool", tool, result_summary: resultSummary || "", time: now });
  }
  session.messages.push({ role: "claude", content: question, time: now });
  session.messages.push({ role: "pm", content: answer, time: now });
  if (session.messages.length > MAX_DISCUSSION_HISTORY * 2 + 4) {
    session.messages = session.messages.slice(-MAX_DISCUSSION_HISTORY * 2);
  }
  _saveDiscussionHistory(history);
}
function _formatDiscussionHistory(limit = MAX_DISCUSSION_HISTORY) {
  const history = _loadDiscussionHistory();
  const session = history.sessions[history.sessions.length - 1];
  if (!session || session.messages.length === 0) return "(\u65E0\u5386\u53F2\u5BF9\u8BDD)";
  const recent = session.messages.slice(-limit * 2);
  return recent.map((m) => {
    const time = m.time ? new Date(m.time).toLocaleTimeString("zh-CN") : "";
    if (m.role === "pm_tool") return `[${time}] Tool: ${m.tool} \u2192 ${m.result_summary}`;
    if (m.role === "claude") return `[${time}] Claude: ${m.content?.slice(0, 100) || ""}`;
    return `[${time}] PM: ${m.content?.slice(0, 200) || ""}`;
  }).join("\n");
}
function _formatMemorySummary(memory) {
  const openIssues = memory.reviews.flatMap((r) => r.issues).filter((i) => i.status === "open");
  const fixedIssues = memory.reviews.flatMap((r) => r.issues).filter((i) => i.status === "fixed");
  const lines = [
    `\u9879\u76EE: ${memory.project.name} (${memory.project.package_name})`,
    `\u8BBE\u8BA1\u7A3F: ${memory.design_specs.sources.length} \u5F20`,
    `\u5BA1\u67E5\u8BB0\u5F55: ${memory.reviews.length} \u6B21`,
    `Open Issues: ${openIssues.length} \u4E2A`,
    `Fixed Issues: ${fixedIssues.length} \u4E2A`,
    `\u5F53\u524D\u7126\u70B9: ${memory.current_focus.page} (${memory.current_focus.channel})`
  ];
  if (openIssues.length > 0) {
    lines.push("\u672A\u4FEE\u590D\u95EE\u9898:");
    for (const issue of openIssues.slice(0, 5)) {
      lines.push(`  - ${issue.issue_id} [${issue.severity}] ${issue.description}`);
    }
  }
  if (Object.keys(memory.design_specs.tokens).length > 0) {
    lines.push("\u8BBE\u8BA1 Token:");
    for (const [k, v] of Object.entries(memory.design_specs.tokens).slice(0, 5)) {
      lines.push(`  - ${k}: ${v}`);
    }
  }
  return lines.join("\n");
}
async function _callTextLlm(systemPrompt, userPrompt, maxTokens = 4e3) {
  const { getActiveProvider: getActiveProvider2, makeInsecureFetch: makeInsecureFetch2 } = await import("./design-extractor-Q6J6UBIL.js");
  const { default: OpenAI2 } = await import("openai");
  const cfg = getActiveProvider2();
  const model = process.env.TEXT_MODEL || "MiniMax-M2.7";
  const apiKey = process.env[cfg.apiKeyEnv];
  if (!apiKey) throw new Error(`${cfg.apiKeyEnv} not set`);
  const clientOpts = { apiKey, baseURL: cfg.baseURL };
  if (cfg.insecureTLS) {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    clientOpts.fetch = makeInsecureFetch2();
  }
  const client = new OpenAI2(clientOpts);
  const requestOpts = {
    model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userPrompt }
    ],
    max_tokens: maxTokens,
    temperature: 0.7
  };
  if (model === "MiniMax-M3") {
    requestOpts.extra_body = { thinking: { type: "disabled" } };
  }
  const response = await client.chat.completions.create(requestOpts, { timeout: 6e4 });
  const msg = response.choices[0]?.message;
  let content = msg?.content;
  if (!content && msg?.reasoning_content) content = msg.reasoning_content;
  if (!content) throw new Error("Text model returned empty response");
  return content;
}
async function handlePmMarkFixed(args) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const issueId = args.issueId;
    logTrace({ type: "tool_call", tool: "pm_mark_fixed", args: { issueId }, session_id: sessionId });
    const note = args.note || "";
    const action = args.action || "fixed";
    if (!issueId) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "issueId required" }) }] };
    }
    const state = _loadPmState();
    if (action === "fixed") {
      state.fixed.push({ issue_id: issueId, note, fixed_at: (/* @__PURE__ */ new Date()).toISOString() });
    } else if (action === "ignored") {
      state.ignored.push({ issue_id: issueId, note, ignored_at: (/* @__PURE__ */ new Date()).toISOString() });
    } else if (action === "reopen") {
      state.fixed = state.fixed.filter((f) => f.issue_id !== issueId);
    } else {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: `Unknown action: ${action}` }) }] };
    }
    _savePmState(state);
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          action,
          issue_id: issueId,
          state_file: PM_STATE_PATH,
          fixed_count: state.fixed.length,
          ignored_count: state.ignored.length,
          open_issues_hint: "\u4E0B\u6B21 pm_review \u65F6\u4ECD\u4F1A\u91CD\u65B0\u53D1\u73B0\u5168\u90E8 issue\uFF1Bpm_mark_fixed \u4E3B\u8981\u7528\u4E8E\u72B6\u6001\u8BB0\u5F55\u4E0E\u89C6\u9891\u6F14\u793A\u8FFD\u8E2A"
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_mark_fixed failed:", err);
    logTrace({ type: "error", tool: "pm_mark_fixed", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
function _clampNum(v, max) {
  const n = Number(v);
  if (isNaN(n)) return void 0;
  return Math.max(0, Math.min(max, Math.round(n)));
}
function _clampCoordinates(tool, args, screen) {
  const minY = 150;
  const maxY = 2280;
  const clampY = (v) => {
    const n = _clampNum(v, screen.height);
    return n === void 0 ? void 0 : Math.max(minY, Math.min(maxY, n));
  };
  if (tool === "tap") {
    return { ...args, x: _clampNum(args.x, screen.width), y: clampY(args.y) };
  }
  if (tool === "swipe") {
    return {
      ...args,
      x1: _clampNum(args.x1, screen.width),
      y1: clampY(args.y1),
      x2: _clampNum(args.x2, screen.width),
      y2: clampY(args.y2)
    };
  }
  if (tool === "screenshot_region" || tool === "verify_ui") {
    return { ...args, x: _clampNum(args.x, screen.width), y: _clampNum(args.y, screen.height) };
  }
  return args;
}
function _isLauncherState(texts) {
  const launcherMarkers = ["Play Store", "Gmail", "Photos", "YouTube", "Phone", "Messages", "Chrome", "Google"];
  const toutiaoMarkers = ["ToutiaoFeedDemo", "\u70ED\u641C", "Tab", "\u63A8\u8350", "\u5173\u6CE8", "\u9996\u9875", "video", "\u5546\u57CE"];
  const hasLauncher = launcherMarkers.filter((m) => texts.includes(m)).length >= 3;
  const hasToutiao = toutiaoMarkers.filter((m) => texts.includes(m)).length >= 2;
  return hasLauncher && !hasToutiao;
}
function _parseToolCall(raw) {
  let text = raw.trim();
  text = text.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  const fenced = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (fenced) text = fenced[1].trim();
  const a = text.indexOf("{");
  const b = text.lastIndexOf("}");
  if (a !== -1 && b > a) text = text.slice(a, b + 1);
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    const fixed = text.replace(/,(\s*[}\]])/g, "$1").replace(/([{,]\s*)([A-Za-z_][\w$]*)(\s*:)/g, '$1"$2"$3');
    try {
      parsed = JSON.parse(fixed);
    } catch {
      return { tool: "tap", args: {}, note: "JSON \u89E3\u6790\u5931\u8D25\uFF08fallback\uFF09" };
    }
  }
  const tool = String(parsed.tool || "").trim();
  const args = parsed.args && typeof parsed.args === "object" && !Array.isArray(parsed.args) ? parsed.args : {};
  const note = String(parsed.note || "");
  if (!tool) {
    return { tool: "tap", args: {}, note: "\u7F3A\u5C11 tool \u5B57\u6BB5" };
  }
  const r = { tool, args, note };
  if (tool === "done") {
    r.overall_rating = String(parsed.overall_rating || args.overall_rating || "C");
    r.summary = String(parsed.summary || args.summary || "");
    r.thinking_process = String(parsed.thinking_process || args.thinking_process || "");
    r.issues = Array.isArray(parsed.issues) ? parsed.issues : Array.isArray(args.issues) ? args.issues : [];
    r.positives = Array.isArray(parsed.positives) ? parsed.positives : Array.isArray(args.positives) ? args.positives : [];
  }
  return r;
}
async function _dispatch(tool, args, screen) {
  const handler = PM_TOOL_REGISTRY[tool];
  if (!handler) {
    const known = Object.keys(PM_TOOL_REGISTRY).sort().join(", ");
    return { ok: false, error: `\u672A\u77E5\u5DE5\u5177 "${tool}"\u3002\u53EF\u7528: ${known}` };
  }
  try {
    const clamped = _clampCoordinates(tool, args, screen);
    const res = await handler(clamped);
    if ("isError" in res && res.isError) {
      const text2 = res.content[0]?.text || "(handler \u9519\u8BEF\u4F46\u65E0\u6587\u672C)";
      return { ok: false, error: text2.slice(0, 500) };
    }
    const text = res.content[0]?.text || "(\u65E0\u8F93\u51FA)";
    return { ok: true, info: text.slice(0, 1500) };
  } catch (e) {
    return { ok: false, error: e.message.slice(0, 500) };
  }
}
function _loadExplorePromptTemplate() {
  const candidates = [
    PROMPT_EXPLORE_PATH,
    path3.resolve(process.cwd(), PROMPT_EXPLORE_PATH),
    path3.resolve(process.cwd(), "..", PROMPT_EXPLORE_PATH)
  ];
  for (const p of candidates) {
    if (fs5.existsSync(p)) return fs5.readFileSync(p, "utf-8");
  }
  throw new Error(`pm_explore prompt template not found: ${PROMPT_EXPLORE_PATH}`);
}
var _cachedProjectRoot = null;
function _findProjectRoot() {
  if (_cachedProjectRoot) return _cachedProjectRoot;
  let dir = process.cwd();
  for (let i = 0; i < 6; i++) {
    if (fs5.existsSync(path3.join(dir, "settings.gradle.kts")) || fs5.existsSync(path3.join(dir, "settings.gradle"))) {
      _cachedProjectRoot = dir;
      return dir;
    }
    const parent = path3.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  _cachedProjectRoot = path3.resolve(process.cwd(), "..");
  return _cachedProjectRoot;
}
async function handlePmExplore(args) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const goal = args.goal || "\u5BA1\u67E5\u5F53\u524D\u9875\u9762\u7684\u53EF\u7528\u6027\u548C\u8BBE\u8BA1";
    const maxSteps = Math.min(Math.max(args.maxSteps ?? 6, 1), 12);
    logTrace({ type: "tool_call", tool: "pm_explore", args: { goal, maxSteps }, session_id: sessionId });
    const exploreId = `explore-${Date.now()}`;
    const traceDir = path3.join(REVIEW_DIR, exploreId);
    fs5.mkdirSync(traceDir, { recursive: true });
    const tpl = _loadExplorePromptTemplate();
    const screen = await getScreenSize();
    const checklist = _loadChecklist();
    const systemPrompt = '\u4F60\u662F Android \u4EA7\u54C1\u7ECF\u7406\uFF0C\u6B63\u5728\u81EA\u4E3B\u5BA1\u67E5 APP\u3002\u6BCF\u6B65\u4E25\u683C\u6309 JSON Schema \u8F93\u51FA 1 \u4E2A\u5DE5\u5177\u8C03\u7528\uFF1A\n1) \u552F\u4E00\u8F93\u51FA\uFF1A\u4E00\u4E2A JSON \u5BF9\u8C61\uFF0C\u4ECE { \u5F00\u59CB\u5230 } \u7ED3\u675F\n2) \u5DE5\u5177\u540D\u5FC5\u987B\u4E25\u683C\u5339\u914D\u53EF\u7528\u5217\u8868\uFF08registry \u91CC\u6709\u7684\uFF09\n3) \u7981\u6B62\uFF1A```json``` \u56F4\u680F\u3001<think> \u5757\u3001\u4EFB\u4F55 markdown\u3001\u4EFB\u4F55\u89E3\u91CA\u6027\u6587\u5B57\n4) \u5B57\u7B26\u4E32\u91CC\u7684\u53CC\u5F15\u53F7\u5FC5\u987B\u7528 \\" \u8F6C\u4E49\n5) done \u662F\u7279\u6B8A\u5DE5\u5177\uFF1Aargs \u5B57\u6BB5\u542B overall_rating/summary/thinking_process/issues/positives\n\nPM \u5BA1\u67E5\u6807\u51C6\u5E93\uFF08\u53C2\u8003\uFF09\uFF1A\n' + checklist;
    const history = [];
    const trace = [];
    let finalResult = null;
    let stuckCount = 0;
    let lastDumpText = "";
    let lastResult = "(\u65E0)";
    let lastToolWasSettling = true;
    let effectiveInteractiveSteps = 0;
    let tStart = Date.now();
    for (let step = 1; step <= maxSteps; step++) {
      log(`pm_explore \u2014 step ${step}/${maxSteps} (goal: ${goal})`);
      logTrace({ type: "step", step, total_steps: maxSteps, action: "screenshot", detail: `dump=${dumpNodes.length} nodes`, session_id: sessionId });
      const shotPath = path3.join(traceDir, `step-${step}.png`);
      const shot = await _takeScreenshot(shotPath);
      const { nodes: dumpNodes } = await _dumpUiInternal();
      const texts = dumpNodes.map((n) => n.text).filter(Boolean).join(" | ");
      const dumpSummary = texts.length > 0 ? texts.slice(0, 400) : "(\u9875\u9762\u65E0\u6587\u672C\u8282\u70B9)";
      if (_isLauncherState(texts)) {
        const hint = "\u26A0\uFE0F \u5F53\u524D\u5C4F\u5E55\u662F Android launcher\uFF08Play Store / Gmail \u7B49\uFF09\uFF0C\u4E0D\u662F Toutiao app\uFF01\u5FC5\u987B\u5148\u8C03 `install_and_launch({})` \u624D\u80FD\u7EE7\u7EED";
        lastResult = hint;
        log(`pm_explore \u2014 detected launcher state at step ${step}, forcing hint`);
      }
      const filled = tpl.replace("${goal}", goal).replace("${history}", history.length > 0 ? history.join("\n") : "(\u65E0\uFF0C\u5DF2\u662F\u7B2C 1 \u6B65)").replace("${last_result}", lastResult).replace("${ui_dump_summary}", dumpSummary).replace("${screen_width}", String(screen.width)).replace("${screen_height}", String(screen.height));
      const tVlm = Date.now();
      const raw = await _callVision(shot, systemPrompt, filled, 4e3);
      log(`pm_explore \u2014 step ${step} VLM ${Date.now() - tVlm}ms`);
      logTrace({ type: "vlm_think", step, thought: call.note || call.tool, model: process.env.VISION_MODEL || "MiniMax-M3", latency_ms: Date.now() - tVlm, session_id: sessionId });
      const call = _parseToolCall(raw);
      const isInteractive = INTERACTIVE_TOOLS.has(call.tool);
      if (texts === lastDumpText) {
        if (SETTLING_TOOLS.has(call.tool)) {
          stuckCount = 0;
        } else if (lastToolWasSettling) {
          stuckCount = 1;
        } else {
          stuckCount++;
        }
      } else {
        stuckCount = 0;
        if (isInteractive) effectiveInteractiveSteps++;
      }
      lastDumpText = texts;
      if (stuckCount >= 2) {
        log(`pm_explore \u2014 stuck detected (UI unchanged ${stuckCount} interactive steps), force done`);
        finalResult = {
          _stuck: true,
          _reason: "\u8FDE\u7EED 2 \u6B65\u4EA4\u4E92\u5DE5\u5177 UI \u6587\u672C\u672A\u53D8\uFF0CPM \u5361\u4F4F\u4E86",
          overall_rating: "C",
          summary: "PM \u81EA\u4E3B\u63A2\u7D22\u65F6\u5361\u4F4F\uFF08\u8FDE\u7EED 2 \u6B65 UI \u65E0\u53D8\u5316\uFF09\uFF0C\u53EF\u80FD\u76EE\u6807\u5143\u7D20\u4E0D\u5B58\u5728\u3001\u5750\u6807\u4E0D\u5BF9\u6216\u5C4F\u5E55\u5DF2\u9501\u6B7B",
          thinking_process: "stale \u68C0\u6D4B\u89E6\u53D1\uFF0C\u5F3A\u5236 done",
          issues: [],
          positives: []
        };
        break;
      }
      if (call.tool === "done" && effectiveInteractiveSteps < 3) {
        lastResult = `\u26A0\uFE0F done \u592A\u65E9\uFF1A\u624D\u8D70\u4E86 ${effectiveInteractiveSteps} \u6B65\u6709\u6548\u4EA4\u4E92\uFF08\u81F3\u5C11\u9700\u8981 3 \u6B65\uFF09\u3002\u8BF7\u7EE7\u7EED\uFF1A\u5148 install_and_launch\uFF0C\u518D\u5207 Tab/\u70B9\u5361\u7B49`;
        log(`pm_explore \u2014 blocked premature done at step ${step} (only ${effectiveInteractiveSteps} interactive steps)`);
        trace.push({
          step,
          tool: call.tool,
          args: call.args,
          note: "BLOCKED: premature done",
          screenshot: shot,
          ui_dump: dumpSummary,
          timestamp: (/* @__PURE__ */ new Date()).toISOString()
        });
        continue;
      }
      const histLine = call.tool === "done" ? `[step ${step}] done \u2014 ${call.summary?.slice(0, 50) || ""}` : `[step ${step}] ${call.tool}(${JSON.stringify(call.args).slice(0, 80)}) \u2014 ${call.note || ""}`;
      history.unshift(histLine);
      if (history.length > 12) history.length = 12;
      trace.push({
        step,
        tool: call.tool,
        args: call.args,
        note: call.note,
        screenshot: shot,
        ui_dump: dumpSummary,
        vlm_response: raw.slice(0, 300),
        timestamp: (/* @__PURE__ */ new Date()).toISOString()
      });
      if (call.tool === "done") {
        finalResult = {
          overall_rating: call.overall_rating || "C",
          summary: call.summary || "",
          thinking_process: call.thinking_process || "",
          issues: call.issues || [],
          positives: call.positives || []
        };
        break;
      }
      const result = await _dispatch(call.tool, call.args, screen);
      if (!result.ok) {
        log(`pm_explore \u2014 step ${step} tool ${call.tool} failed: ${result.error}`);
        trace[trace.length - 1].execution_error = result.error;
        lastResult = `\u274C ${call.tool} \u5931\u8D25: ${result.error}`;
      } else {
        trace[trace.length - 1].execution_info = result.info;
        lastResult = `\u2713 ${call.tool} \u2192 ${result.info || "(\u7A7A)"}`;
      }
      lastToolWasSettling = SETTLING_TOOLS.has(call.tool);
      const settleMs = (/* @__PURE__ */ new Set(["build", "install_and_launch", "stop_app", "clear_app_data", "set_orientation"])).has(call.tool) ? 2500 : 800;
      await new Promise((r) => setTimeout(r, settleMs));
    }
    if (!finalResult) {
      finalResult = {
        _maxStepsReached: true,
        overall_rating: "C",
        summary: `PM \u81EA\u4E3B\u63A2\u7D22\u8FBE\u5230 maxSteps=${maxSteps} \u4E0A\u9650\uFF0C\u672A\u8F93\u51FA done\u3002\u57FA\u4E8E\u5DF2\u89C2\u5BDF\u5230\u7684 ${trace.length} \u6B65\u52A8\u4F5C\uFF0C\u5EFA\u8BAE\u4EBA\u5DE5\u8DDF\u8FDB\u3002`,
        thinking_process: history.join(" | "),
        issues: [],
        positives: []
      };
    }
    if (finalResult && Array.isArray(finalResult.issues)) {
      const memory = _loadPmMemory();
      _updateMemoryFromReview(memory, {
        review_id: exploreId,
        timestamp: (/* @__PURE__ */ new Date()).toISOString(),
        tool: "pm_explore",
        target: goal,
        overall_rating: String(finalResult.overall_rating || "C"),
        issues: finalResult.issues.map((issue, idx) => ({
          issue_id: issue.id || _generateIssueId(memory),
          severity: issue.severity || "medium",
          category: issue.category || "ui_bug",
          description: `${issue.location || ""}: ${issue.current_state || ""} \u2192 ${issue.expected_state || ""}`,
          location: issue.location || "",
          design_ref: issue.design_ref || "",
          status: "open"
        })),
        positives: Array.isArray(finalResult.positives) ? finalResult.positives : []
      });
      logTrace({ type: "memory_update", action: "append_review", review_id: exploreId, session_id: sessionId });
    }
    const traceFile = path3.join(REVIEW_DIR, `${exploreId}.json`);
    const fullTrace = {
      explore_id: exploreId,
      timestamp: (/* @__PURE__ */ new Date()).toISOString(),
      goal,
      max_steps: maxSteps,
      steps_taken: trace.length,
      elapsed_ms: Date.now() - tStart,
      history,
      trace,
      trace_dir: traceDir,
      final_result: finalResult
    };
    fs5.writeFileSync(traceFile, JSON.stringify(fullTrace, null, 2), "utf-8");
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          explore_id: exploreId,
          trace_file: traceFile,
          trace_dir: traceDir,
          steps_taken: trace.length,
          elapsed_ms: Date.now() - tStart,
          history,
          ...finalResult
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_explore failed:", err);
    logTrace({ type: "error", tool: "pm_explore", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handlePmDiscuss(args) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const question = args.question || "";
    const context = args.context || "";
    const includeHistory = args.include_history !== false;
    const includeScreenshot = args.include_screenshot === true;
    if (!question) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "question required" }) }] };
    }
    logTrace({ type: "discuss", tool: "pm_discuss", args: { question: question.slice(0, 100), context: context.slice(0, 100), includeHistory, includeScreenshot }, session_id: sessionId });
    const memory = _loadPmMemory();
    const memorySummary = _formatMemorySummary(memory);
    const discussionHistory = includeHistory ? _formatDiscussionHistory() : "(\u5386\u53F2\u5DF2\u5FFD\u7565)";
    let screenshotPath;
    if (includeScreenshot) {
      screenshotPath = await _takeScreenshot();
    }
    const tplPath = path3.resolve(process.cwd(), "./skills/prompts/pm_discuss.txt");
    let tpl = fs5.existsSync(tplPath) ? fs5.readFileSync(tplPath, "utf-8") : "";
    if (!tpl) {
      tpl = `\u4F60\u662F ToutiaoFeedDemo \u7684 AI \u4EA7\u54C1\u7ECF\u7406\u3002\u57FA\u4E8E\u9879\u76EE\u8BB0\u5FC6\u548C\u5BF9\u8BDD\u5386\u53F2\u56DE\u7B54\u4EA7\u54C1\u95EE\u9898\u3002`;
    }
    const filled = tpl.replace("${pm_memory_summary}", memorySummary).replace("${discussion_history}", discussionHistory).replace("${context}", context || "(\u672A\u63D0\u4F9B)");
    const systemPrompt = '\u4F60\u662F Android \u4EA7\u54C1\u7ECF\u7406\u3002\u56DE\u7B54\u7B80\u6D01\u3001\u5177\u4F53\u3001\u53EF\u6267\u884C\u3002\u4E0D\u786E\u5B9A\u65F6\u8BF4"\u9879\u76EE\u8BB0\u5FC6\u4E2D\u6CA1\u6709\u76F8\u5173\u4FE1\u606F"\u3002';
    let answer;
    if (includeScreenshot && screenshotPath) {
      const userPrompt = filled + "\n\n\u7528\u6237\u95EE\u9898\uFF1A" + question + "\n\uFF08\u7528\u6237\u8981\u6C42 PM \u57FA\u4E8E\u5F53\u524D\u622A\u56FE\u56DE\u7B54\uFF09";
      answer = await _callVision(screenshotPath, systemPrompt, userPrompt, 4e3);
    } else {
      answer = await _callTextLlm(systemPrompt, filled + "\n\n\u7528\u6237\u95EE\u9898\uFF1A" + question, 4e3);
    }
    _appendDiscussion(question, answer, "pm_discuss", "\u56DE\u7B54\u5B8C\u6210");
    logTrace({ type: "discuss", tool: "pm_discuss", detail: "completed", session_id: sessionId });
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          answer,
          memory_summary: memorySummary.slice(0, 500),
          include_screenshot: includeScreenshot
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_discuss failed:", err);
    logTrace({ type: "error", tool: "pm_discuss", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handlePmCheck(args) {
  const sessionId = `sess-${Date.now()}`;
  try {
    const issueId = args.issue_id;
    const target = args.target || "";
    const autoMarkFixed = args.auto_mark_fixed !== false;
    if (!issueId) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: "issue_id required" }) }] };
    }
    logTrace({ type: "check", tool: "pm_check", args: { issue_id: issueId, target, autoMarkFixed }, session_id: sessionId });
    const memory = _loadPmMemory();
    const allIssues = memory.reviews.flatMap((r) => r.issues);
    const issue = allIssues.find((i) => i.issue_id === issueId);
    if (!issue) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: `Issue not found in memory: ${issueId}` }) }] };
    }
    const shot = await _takeScreenshot();
    const tplPath = path3.resolve(process.cwd(), "./skills/prompts/pm_check.txt");
    let tpl = fs5.existsSync(tplPath) ? fs5.readFileSync(tplPath, "utf-8") : "";
    if (!tpl) {
      tpl = "\u5224\u65AD\u4EE5\u4E0B issue \u662F\u5426\u5DF2\u4FEE\u590D\u3002\u8F93\u51FA JSON: {fixed: boolean, confidence: high/medium/low, note: string}";
    }
    const filled = tpl.replace("${issue_json}", JSON.stringify(issue, null, 2));
    const systemPrompt = "\u4F60\u662F Android \u4EA7\u54C1\u7ECF\u7406\uFF0C\u6B63\u5728\u9A8C\u8BC1 issue \u4FEE\u590D\u72B6\u6001\u3002\u4E25\u683C\u6309 JSON \u683C\u5F0F\u8F93\u51FA\u3002";
    const raw = await _callVision(shot, systemPrompt, filled, 2e3);
    const parsed = _parseJsonFromVision(raw);
    const fixed = parsed.fixed === true;
    const confidence = String(parsed.confidence || "low");
    const note = String(parsed.note || "");
    const remainingConcerns = String(parsed.remaining_concerns || "");
    if (fixed && autoMarkFixed) {
      issue.status = "fixed";
      issue.verified_by = "pm_check";
      issue.verified_at = (/* @__PURE__ */ new Date()).toISOString();
      _savePmMemory(memory);
      const state = _loadPmState();
      if (!state.fixed.find((f) => f.issue_id === issueId)) {
        state.fixed.push({ issue_id: issueId, note: `Verified by pm_check: ${note}`, fixed_at: (/* @__PURE__ */ new Date()).toISOString() });
        _savePmState(state);
      }
      logTrace({ type: "memory_update", action: "mark_fixed", detail: issueId, session_id: sessionId });
    }
    logTrace({ type: "check", tool: "pm_check", detail: fixed ? "fixed" : "not_fixed", session_id: sessionId });
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          issue_id: issueId,
          fixed,
          confidence,
          note,
          remaining_concerns: remainingConcerns,
          auto_marked: fixed && autoMarkFixed
        }, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_check failed:", err);
    logTrace({ type: "error", tool: "pm_check", detail: err.message, session_id: sessionId });
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
async function handlePmGetMemory(args) {
  try {
    const scope = args.scope || "overview";
    const channel = args.channel || "";
    const memory = _loadPmMemory();
    const allIssues = memory.reviews.flatMap((r) => r.issues);
    const filteredIssues = channel ? allIssues.filter((i) => {
      const review = memory.reviews.find((r) => r.issues.includes(i));
      return review?.channel === channel;
    }) : allIssues;
    let result = { success: true, scope };
    switch (scope) {
      case "overview": {
        result = {
          ...result,
          project: memory.project,
          review_count: memory.reviews.length,
          open_issues: filteredIssues.filter((i) => i.status === "open").length,
          fixed_issues: filteredIssues.filter((i) => i.status === "fixed").length,
          ignored_issues: filteredIssues.filter((i) => i.status === "ignored").length,
          current_focus: memory.current_focus,
          design_files: memory.design_specs.sources.length
        };
        break;
      }
      case "open_issues": {
        result = {
          ...result,
          issues: filteredIssues.filter((i) => i.status === "open"),
          count: filteredIssues.filter((i) => i.status === "open").length
        };
        break;
      }
      case "fixed_issues": {
        result = {
          ...result,
          issues: filteredIssues.filter((i) => i.status === "fixed"),
          count: filteredIssues.filter((i) => i.status === "fixed").length
        };
        break;
      }
      case "design_specs": {
        result = {
          ...result,
          sources: memory.design_specs.sources,
          tokens: memory.design_specs.tokens
        };
        break;
      }
      case "last_review": {
        const last = memory.reviews[memory.reviews.length - 1];
        result = { ...result, review: last || null };
        break;
      }
      case "discussions": {
        const history = _loadDiscussionHistory();
        result = { ...result, sessions: history.sessions.length, last_session: history.sessions[history.sessions.length - 1] || null };
        break;
      }
      default: {
        result = { ...result, hint: `Unknown scope: ${scope}. Available: overview, open_issues, fixed_issues, design_specs, last_review, discussions` };
      }
    }
    return {
      content: [{
        type: "text",
        text: JSON.stringify(result, null, 2)
      }]
    };
  } catch (e) {
    const err = e;
    error("pm_get_memory failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// src/server.ts
loadEnv();
var server = new Server(
  {
    name: "android-dev-assist",
    version: "3.1.0"
  },
  {
    capabilities: {
      tools: {}
    }
  }
);
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      // ════════════════════════════════════════════════════════════
      // 基础交互
      // ════════════════════════════════════════════════════════════
      {
        name: "screenshot",
        description: "\u622A\u53D6\u5F53\u524D\u8BBE\u5907\u5C4F\u5E55\u5E76\u4FDD\u5B58\u4E3A PNG \u6587\u4EF6\uFF08\u5355\u6B21 ADB \u5F80\u8FD4\uFF0Cscreencap \u76F4\u4F20 stdout\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u8C03\u7528 analyze_screenshot / compare_screenshots / verify_ui / vision_action \u4E4B\u524D\u5FC5\u987B\u5148\u622A\u56FE\u3002\u6539\u7528 screenshot_region\uFF1A\u82E5\u4F60\u53EA\u5173\u5FC3\u5C4F\u5E55\u5C40\u90E8\u3002\u8FD4\u56DE JSON: {success, path, timestamp, sizeBytes}\u3002\u8017\u65F6 fast (~300ms)\u3002\u793A\u4F8B\uFF1Ascreenshot({ savePath: './home.png' })",
        inputSchema: {
          type: "object",
          properties: {
            savePath: { type: "string", description: "\u53EF\u9009\u3002\u4FDD\u5B58\u8DEF\u5F84\uFF08\u9ED8\u8BA4 ./screenshots/screenshot_<ts>.png\uFF09" }
          }
        }
      },
      {
        name: "screenshot_region",
        description: "\u622A\u53D6\u5C4F\u5E55\u6307\u5B9A\u77E9\u5F62\u533A\u57DF\u5E76\u4FDD\u5B58\u4E3A PNG\u3002\u9002\u5408\uFF1A\u53EA\u5206\u6790\u9876\u90E8 Tab\u3001\u5E95\u90E8\u5BFC\u822A\u3001\u5355\u4E2A\u5361\u7247\uFF0C\u907F\u514D\u4F20\u6574\u5F20 1080\xD72400 \u56FE\u7ED9\u89C6\u89C9 AI\u3002\u6539\u7528 screenshot\uFF1A\u82E5\u9700\u8981\u5168\u5C4F\u3002\u8FD4\u56DE JSON: {success, path, region: {x, y, width, height}, parentPath}\u3002\u8017\u65F6 fast (~500ms, \u542B\u5168\u5C4F+\u88C1\u526A)\u3002\u793A\u4F8B\uFF1Ascreenshot_region({ x: 0, y: 0, width: 1080, height: 200 })",
        inputSchema: {
          type: "object",
          properties: {
            x: { type: "number", description: "Required. \u5DE6\u4E0A\u89D2 X \u5750\u6807 (px)" },
            y: { type: "number", description: "Required. \u5DE6\u4E0A\u89D2 Y \u5750\u6807 (px)" },
            width: { type: "number", description: "Required. \u533A\u57DF\u5BBD\u5EA6 (px, > 0)" },
            height: { type: "number", description: "Required. \u533A\u57DF\u9AD8\u5EA6 (px, > 0)" },
            savePath: { type: "string", description: "\u53EF\u9009\u3002\u4FDD\u5B58\u8DEF\u5F84\uFF08\u9ED8\u8BA4\u5728 screenshots/ \u4E0B\u8FFD\u52A0 _region \u6807\u8BB0\uFF09" }
          },
          required: ["x", "y", "width", "height"]
        }
      },
      {
        name: "tap",
        description: "\u5728\u5C4F\u5E55\u6307\u5B9A\u5750\u6807\u70B9\u51FB\u4E00\u4E0B\u3002\u6539\u7528 vision_action / find_element\uFF1A\u82E5\u4F60\u4E0D\u77E5\u9053\u5750\u6807\u3002\u6539\u7528 swipe\uFF1A\u82E5\u9700\u8981\u957F\u6309\u6216\u62D6\u62FD\u3002\u8FD4\u56DE JSON: {success, action: 'tap', x, y}\u3002\u8017\u65F6 fast (~200ms)\u3002\u793A\u4F8B\uFF1Atap({ x: 540, y: 1200 })",
        inputSchema: {
          type: "object",
          properties: {
            x: { type: "number", description: "Required. X \u5750\u6807 (px)" },
            y: { type: "number", description: "Required. Y \u5750\u6807 (px)" }
          },
          required: ["x", "y"]
        }
      },
      {
        name: "swipe",
        description: "\u4ECE (x1,y1) \u62D6\u5230 (x2,y2)\uFF0Cduration \u5355\u4F4D ms\uFF08\u9ED8\u8BA4 300\uFF09\u3002\u957F\u6309\uFF1A\u628A x1==x2\u3001y1==y2\uFF0Cduration \u8BBE\u5927\uFF08\u5982 800ms\uFF09\u3002\u4E0B\u6ED1\u5237\u65B0\uFF1Aduration \u77ED\u3001x1=x2=\u5C4F\u5E55\u4E2D\u7EBF\u3001y1>y2\u3002\u8FD4\u56DE JSON: {success, action: 'swipe'}\u3002\u8017\u65F6 fast (~400ms)\u3002\u793A\u4F8B\uFF1Aswipe({ x1: 540, y1: 1800, x2: 540, y2: 400, duration: 250 })",
        inputSchema: {
          type: "object",
          properties: {
            x1: { type: "number", description: "Required. \u8D77\u70B9 X (px)" },
            y1: { type: "number", description: "Required. \u8D77\u70B9 Y (px)" },
            x2: { type: "number", description: "Required. \u7EC8\u70B9 X (px)" },
            y2: { type: "number", description: "Required. \u7EC8\u70B9 Y (px)" },
            duration: { type: "number", description: "\u53EF\u9009\u3002\u6ED1\u52A8\u65F6\u957F ms\uFF0C\u9ED8\u8BA4 300", default: 300 }
          },
          required: ["x1", "y1", "x2", "y2"]
        }
      },
      {
        name: "input_text",
        description: "\u5411\u5F53\u524D\u7126\u70B9\u8F93\u5165\u6846\u6CE8\u5165\u6587\u672C\u3002\u7A7A\u683C\u4F1A\u88AB\u8F6C\u4E49\u4E3A %s\uFF1B\u4E2D\u6587/\u7279\u6B8A\u5B57\u7B26\u53EF\u80FD\u88AB input text \u62D2\u7EDD\uFF0C\u6B64\u65F6\u6539\u7528 vision_action \u81EA\u52A8\u5207\u6362\u8F93\u5165\u6CD5\u3002\u6CE8\u610F\uFF1A\u4E0D\u8D1F\u8D23\u5148\u70B9\u51FB\u8F93\u5165\u6846\u3002\u8FD4\u56DE JSON: {success, action: 'input_text', text}\u3002\u8017\u65F6 fast (~150ms)\u3002\u793A\u4F8B\uFF1Ainput_text({ text: 'hello world' })",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "Required. \u8981\u8F93\u5165\u7684\u6587\u672C\uFF08\u7A7A\u683C\u4F1A\u88AB\u8F6C\u4E49\uFF09" }
          },
          required: ["text"]
        }
      },
      {
        name: "press_key",
        description: "\u6A21\u62DF\u786C\u4EF6\u6309\u952E\u3002\u53EF\u7528 key\uFF1AHOME, BACK, ENTER, MENU, POWER, VOLUME_UP, VOLUME_DOWN, DEL\uFF1B\u6216\u76F4\u63A5\u4F20\u6570\u5B57 keycode\u3002\u8FD4\u56DE JSON: {success, action: 'press_key', key}\u3002\u8017\u65F6 fast (~150ms)\u3002\u793A\u4F8B\uFF1Apress_key({ key: 'HOME' })",
        inputSchema: {
          type: "object",
          properties: {
            key: { type: "string", description: "Required. \u6309\u952E\u540D\u6216\u6570\u5B57 keycode\uFF08\u5982 'HOME' \u6216 3\uFF09" }
          },
          required: ["key"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // UI 层级 / 元素查找 / 等待
      // ════════════════════════════════════════════════════════════
      {
        name: "dump_hierarchy",
        description: "\u4F7F\u7528 uiautomator dump \u5F53\u524D\u5C4F\u5E55\u7684 UI \u5C42\u7EA7\uFF0C\u8FD4\u56DE\u7ED3\u6784\u5316\u5143\u7D20\u5217\u8868\uFF08\u542B type/text/resource-id/clickable/bounds\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u9700\u8981\u5148\u770B\u770B\u754C\u9762\u4E0A\u6709\u4EC0\u4E48\u518D\u51B3\u5B9A\u600E\u4E48\u64CD\u4F5C\uFF1Bvision \u89C6\u89C9\u8BC6\u522B\u4EE3\u4EF7\u9AD8\uFF0CAgent \u5728\u5199\u4EE3\u7801\u903B\u8F91\u65F6\u4F18\u5148\u7528\u6B64\u5DE5\u5177\u3002\u6539\u7528 vision_action\uFF1A\u82E5\u8981\u7EAF\u6309\u81EA\u7136\u8BED\u8A00\u70B9\u51FB\uFF08\u4E0D\u5173\u5FC3\u5143\u7D20\u7ED3\u6784\uFF09\u3002\u8FD4\u56DE JSON: {success, count, elements: [{type, text, resourceId, clickable, bounds}]}\u3002\u8017\u65F6 fast (~800ms)\u3002\u793A\u4F8B\uFF1Adump_hierarchy({})",
        inputSchema: {
          type: "object",
          properties: {
            includeRaw: { type: "boolean", description: "\u53EF\u9009\u3002\u662F\u5426\u5728\u7ED3\u679C\u4E2D\u9644\u52A0\u63D0\u793A\u4FE1\u606F", default: false }
          }
        }
      },
      {
        name: "find_element",
        description: "\u6309 text / resource-id / class \u67E5\u627E UI \u5143\u7D20\uFF0C\u8FD4\u56DE\u4E2D\u5FC3\u5750\u6807\uFF08\u53EF\u76F4\u63A5\u5582\u7ED9 tap\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u77E5\u9053\u5143\u7D20\u6587\u672C\uFF08\u5982\u300C\u786E\u5B9A\u300D\u300C\u6211\u7684\u300D\uFF09\u6216 resource-id \u4F46\u4E0D\u60F3\u7B97\u5750\u6807\u3002\u6539\u7528 dump_hierarchy\uFF1A\u82E5\u60F3\u770B\u6240\u6709\u5019\u9009\u3002\u8FD4\u56DE JSON: {success, found, count, primary: {center: {x, y}, ...}, all: [...]}\uFF1Bfound=false \u65F6\u542B hint\u3002\u8017\u65F6 fast (~800ms)\u3002\u793A\u4F8B\uFF1Afind_element({ text: '\u786E\u5B9A' })",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "\u53EF\u9009\u3002\u5143\u7D20\u663E\u793A\u6587\u672C\uFF08\u652F\u6301 contains \u5339\u914D\uFF09" },
            resourceId: { type: "string", description: "\u53EF\u9009\u3002\u8D44\u6E90 ID\uFF08\u7CBE\u786E\u5339\u914D\uFF0C\u5982 com.example:id/btn_ok\uFF09" },
            className: { type: "string", description: "\u53EF\u9009\u3002\u7C7B\u540D\uFF08\u77ED\u540D\u6216\u5168\u540D\uFF0C\u5982 Button\uFF09" },
            exact: { type: "boolean", description: "\u53EF\u9009\u3002\u662F\u5426\u7CBE\u786E\u5339\u914D text\uFF0C\u9ED8\u8BA4 false\uFF08contains\uFF09", default: false }
          }
        }
      },
      {
        name: "wait_for_element",
        description: "\u8F6E\u8BE2\u7B49\u5F85\u5143\u7D20\u51FA\u73B0\u6216\u6D88\u5931\uFF0C\u907F\u514D Agent \u5728\u5F02\u6B65 UI\uFF08\u52A0\u8F7D\u3001\u52A8\u753B\uFF09\u5C1A\u672A\u5C31\u7EEA\u65F6\u76F2\u76EE\u64CD\u4F5C\u3002\u8FD4\u56DE JSON: {success, found, waitedMs, element?}\u3002\u8017\u65F6 medium (\u6309 timeoutMs\uFF0C\u901A\u5E38 1-10s)\u3002\u793A\u4F8B\uFF1Await_for_element({ text: '\u52A0\u8F7D\u5B8C\u6210', timeoutMs: 8000 })",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "\u53EF\u9009\u3002\u5F85\u5339\u914D\u6587\u672C" },
            resourceId: { type: "string", description: "\u53EF\u9009\u3002\u5F85\u5339\u914D\u8D44\u6E90 ID" },
            timeoutMs: { type: "number", description: "\u53EF\u9009\u3002\u6700\u957F\u7B49\u5F85\u6BEB\u79D2\u6570\uFF0C\u9ED8\u8BA4 10000", default: 1e4 },
            intervalMs: { type: "number", description: "\u53EF\u9009\u3002\u8F6E\u8BE2\u95F4\u9694 ms\uFF0C\u9ED8\u8BA4 500", default: 500 },
            expect: { type: "string", enum: ["appear", "disappear"], description: "\u53EF\u9009\u3002\u7B49\u5F85\u51FA\u73B0\u6216\u6D88\u5931\uFF0C\u9ED8\u8BA4 appear", default: "appear" }
          }
        }
      },
      // ════════════════════════════════════════════════════════════
      // 构建与部署
      // ════════════════════════════════════════════════════════════
      {
        name: "build",
        description: "\u4F7F\u7528 Gradle \u6784\u5EFA Android \u5DE5\u7A0B\u3002\u8FD4\u56DE JSON: {success, apkPath, buildTime, error?}\u3002\u8017\u65F6 slow (1-3 \u5206\u949F)\u3002\u793A\u4F8B\uFF1Abuild({ projectPath: '.', variant: 'debug' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "\u53EF\u9009\u3002Android \u5DE5\u7A0B\u6839\u8DEF\u5F84\uFF0C\u9ED8\u8BA4 '.'", default: "." },
            variant: { type: "string", description: "\u53EF\u9009\u3002\u6784\u5EFA\u53D8\u4F53\uFF1Adebug | release", default: "debug" },
            flavor: { type: "string", description: "\u53EF\u9009\u3002\u4EA7\u54C1 flavor\uFF08\u591A\u6E20\u9053\u5DE5\u7A0B\uFF09" }
          }
        }
      },
      {
        name: "install_and_launch",
        description: "\u5B89\u88C5 APK \u5E76\u542F\u52A8\u5E94\u7528\u3002\u82E5\u53EA\u4F20 packageName \u5219\u53EA\u542F\u52A8\u5DF2\u5B89\u88C5\u7684\u5E94\u7528\uFF08\u4E0D\u91CD\u88C5\uFF09\u3002\u8FD4\u56DE JSON: {success, action, packageName}\u3002\u8017\u65F6 medium (10-30s \u542B install)\u3002\u793A\u4F8B\uFF1Ainstall_and_launch({ apkPath: './app-debug.apk', packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            apkPath: { type: "string", description: "\u53EF\u9009\u3002APK \u8DEF\u5F84\uFF1B\u7701\u7565\u5219\u53EA\u542F\u52A8\u5DF2\u5B89\u88C5\u5E94\u7528" },
            packageName: { type: "string", description: "Required. Android \u5305\u540D" },
            activity: { type: "string", description: "\u53EF\u9009\u3002\u8981\u542F\u52A8\u7684 Activity" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7\uFF08\u591A\u8BBE\u5907\u573A\u666F\uFF09" }
          },
          required: ["packageName"]
        }
      },
      {
        name: "build_deploy",
        description: "\u5B8C\u6574 CI/CD \u6D41\u6C34\u7EBF\uFF1Aclean \u2192 build \u2192 install \u2192 launch\uFF0C\u6309\u9636\u6BB5\u8FD4\u56DE stage \u4FE1\u606F\u3002\u6539\u7528 build + install_and_launch\uFF1A\u82E5\u4F60\u60F3\u624B\u52A8\u63A7\u5236\u6BCF\u6B65\u3002\u8FD4\u56DE JSON: {success, stage, apkPath, buildTime, installed, launched}\u3002\u8017\u65F6 slow (2-5 \u5206\u949F)\u3002\u793A\u4F8B\uFF1Abuild_deploy({ packageName: 'com.example.app', variant: 'debug' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "\u53EF\u9009\u3002\u5DE5\u7A0B\u6839\u8DEF\u5F84", default: "." },
            variant: { type: "string", description: "\u53EF\u9009\u3002\u6784\u5EFA\u53D8\u4F53", default: "debug" },
            packageName: { type: "string", description: "Required. \u542F\u52A8\u7684\u5305\u540D" },
            autoLaunch: { type: "boolean", description: "\u53EF\u9009\u3002\u5B89\u88C5\u540E\u81EA\u52A8\u542F\u52A8\uFF0C\u9ED8\u8BA4 true", default: true }
          },
          required: ["packageName"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 视觉 AI 分析
      // ════════════════════════════════════════════════════════════
      {
        name: "verify_ui",
        description: "UI \u50CF\u7D20\u7EA7\u9A8C\u8BC1\uFF1A'compare' \u5BF9\u6BD4\u4E24\u5F20\u622A\u56FE\u5DEE\u5F02\uFF1B'color' \u53D6 (x,y) \u50CF\u7D20\u989C\u8272\u5E76\u4E0E\u9884\u671F\u6BD4\u5BF9\uFF1B'ocr' \u5360\u4F4D\uFF08\u9700\u5B89\u88C5 tesseract.js\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u56DE\u5F52\u6D4B\u8BD5 / \u9A8C\u8BC1\u67D0\u4E2A\u50CF\u7D20\u989C\u8272\u662F\u5426\u7B26\u5408\u8BBE\u8BA1\u7A3F\u3002\u6539\u7528 analyze_screenshot\uFF1A\u82E5\u8981 Minimax \u89C6\u89C9\u7406\u89E3\u6574\u4E2A\u5E03\u5C40\u3002\u8FD4\u56DE JSON\uFF1Acompare \u8FD4\u56DE {diffPixels, diffPercentage, isMatch, diffImagePath}\uFF1Bcolor \u8FD4\u56DE {match, expected, actual, x, y}\u3002\u8017\u65F6 compare fast (~500ms)\uFF0Ccolor fast (~100ms)\u3002\u793A\u4F8B\uFF1Averify_ui({ type: 'color', currentPath: './home.png', x: 100, y: 50, checkColor: '#FF5757' })",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", enum: ["compare", "color", "ocr"], description: "Required. \u9A8C\u8BC1\u7C7B\u578B" },
            baselinePath: { type: "string", description: "compare \u6A21\u5F0F\u5FC5\u586B\uFF1A\u57FA\u7EBF\u622A\u56FE" },
            currentPath: { type: "string", description: "compare/color \u6A21\u5F0F\u5FC5\u586B\uFF1A\u5F53\u524D\u622A\u56FE" },
            checkText: { type: "string", description: "ocr \u6A21\u5F0F\u5FC5\u586B\uFF1A\u8981\u67E5\u627E\u7684\u6587\u672C" },
            checkColor: { type: "string", description: "color \u6A21\u5F0F\u5FC5\u586B\uFF1A\u671F\u671B\u989C\u8272 (\u5341\u516D\u8FDB\u5236\uFF0C\u5982 '#FF0000')" },
            x: { type: "number", description: "color \u6A21\u5F0F\u5FC5\u586B\uFF1AX \u5750\u6807" },
            y: { type: "number", description: "color \u6A21\u5F0F\u5FC5\u586B\uFF1AY \u5750\u6807" }
          },
          required: ["type"]
        }
      },
      {
        name: "analyze_screenshot",
        description: "\u4E09\u9636\u6BB5\u622A\u56FE\u5206\u6790\uFF1A(1) PIL \u50CF\u7D20\u6D4B\u91CF\u5E03\u5C40 (2) Minimax \u89C6\u89C9 AI \u8BED\u4E49\u7406\u89E3 (3) \u5361\u7247\u7EA7\u7CBE\u786E\u9A8C\u8BC1\u3002\u9700\u8981 Python3 + PIL \u73AF\u5883\uFF1B\u89C6\u89C9\u9636\u6BB5\u9700\u8981 MINIMAX_API_KEY\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u7528\u6237\u8BF4\u300C\u5E2E\u6211\u770B\u770B\u8FD9\u4E2A\u9875\u9762\u300D\u300C\u6709\u4EC0\u4E48\u95EE\u9898\u300D\u65F6\u7684\u4E00\u7AD9\u5F0F UI \u5BA1\u67E5\u3002\u6539\u7528 verify_ui\uFF1A\u82E5\u53EA\u9700\u50CF\u7D20\u7EA7\u68C0\u67E5\u3002\u8FD4\u56DE JSON: {file, pil: {...}, vision: '...', cards: [...], checklist: [...]}\u3002\u8017\u65F6 slow (10-30s\uFF0Cvision \u9636\u6BB5\u5360\u5927\u5934)\u3002\u793A\u4F8B\uFF1Aanalyze_screenshot({ filePath: './home.png', prompt: '\u68C0\u67E5\u5361\u7247\u95F4\u8DDD' })",
        inputSchema: {
          type: "object",
          properties: {
            filePath: { type: "string", description: "Required. \u622A\u56FE\u6587\u4EF6\u8DEF\u5F84" },
            prompt: { type: "string", description: "\u53EF\u9009\u3002\u81EA\u5B9A\u4E49\u5206\u6790\u7126\u70B9\uFF08\u5982\u300C\u68C0\u67E5 header \u989C\u8272\u300D\u300C\u627E\u5E03\u5C40\u9519\u4F4D\u300D\uFF09" }
          },
          required: ["filePath"]
        }
      },
      {
        name: "compare_screenshots",
        description: "\u7528 Minimax \u89C6\u89C9 AI \u5BF9\u6BD4\u4E24\u5F20\u622A\u56FE\uFF08\u8BBE\u8BA1\u7A3F vs \u5B9E\u73B0\uFF09\u3002\u6BD4 verify_ui \u7684 compare \u6162\u4F46\u66F4\u667A\u80FD\uFF08\u80FD\u8BC6\u522B\u8BED\u4E49\u5DEE\u5F02\uFF09\u3002\u9700\u8981 MINIMAX_API_KEY\u3002\u8FD4\u56DE JSON: {success, baseline, current, analysis: '...'}\u3002\u8017\u65F6 slow (5-20s)\u3002\u793A\u4F8B\uFF1Acompare_screenshots({ baselinePath: './design.png', currentPath: './home.png' })",
        inputSchema: {
          type: "object",
          properties: {
            baselinePath: { type: "string", description: "Required. \u57FA\u7EBF/\u8BBE\u8BA1\u7A3F" },
            currentPath: { type: "string", description: "Required. \u5F53\u524D\u5B9E\u73B0" },
            prompt: { type: "string", description: "\u53EF\u9009\u3002\u5B9A\u5236\u5BF9\u6BD4\u7126\u70B9" }
          },
          required: ["baselinePath", "currentPath"]
        }
      },
      {
        name: "vision_action",
        description: "\u7528 Minimax \u89C6\u89C9 AI \u6309\u81EA\u7136\u8BED\u8A00\u63CF\u8FF0\u5B9A\u4F4D\u5143\u7D20\u5E76\u6267\u884C\u70B9\u51FB/\u6ED1\u52A8/\u8F93\u5165\u3002\u9002\u5408\uFF1A(1) Agent \u4E0D\u77E5\u9053\u76EE\u6807\u5750\u6807 (2) UI \u5143\u7D20\u4F4D\u7F6E/\u6587\u6848\u4F1A\u52A8\u6001\u53D8\u5316 (3) \u4E0D\u60F3\u5199\u89C4\u5219\u3002\u4EE3\u4EF7\uFF1A\u6BCF\u6B65 3-8s\uFF0C\u4E14\u9700 MINIMAX_API_KEY\u3002\u6539\u7528 find_element + tap\uFF1A\u82E5\u5143\u7D20\u6709\u7A33\u5B9A text/resource-id\uFF08\u66F4\u5FEB\uFF09\u3002\u8FD4\u56DE JSON: {success, steps: [{action, x, y, text, confidence, reasoning}], screenshot, durationMs}\u3002\u8017\u65F6 slow (3-8s \u6BCF\u6B65)\u3002\u793A\u4F8B\uFF1Avision_action({ prompt: '\u70B9\u51FB\u5E95\u90E8\u5BFC\u822A\u7684\u300C\u89C6\u9891\u300Dtab' }) \u6216 prompts: ['\u70B9\u51FB\u641C\u7D22', '\u8F93\u5165 hello', '\u6309\u56DE\u8F66']",
        inputSchema: {
          type: "object",
          properties: {
            prompt: { type: "string", description: "\u53EF\u9009\u3002\u5355\u6B65\u81EA\u7136\u8BED\u8A00\u6307\u4EE4" },
            prompts: { type: "array", items: { type: "string" }, description: "\u53EF\u9009\u3002\u591A\u6B65\u4E32\u8054\uFF08\u6BCF\u6B65\u7528\u524D\u4E00\u6B65\u7684 after-screenshot\uFF09" },
            beforeScreenshot: { type: "string", description: "\u53EF\u9009\u3002\u5DF2\u6709\u7684\u622A\u56FE\u8DEF\u5F84\uFF0C\u7701\u53BB\u9996\u6B21\u622A\u56FE" }
          }
        }
      },
      // ════════════════════════════════════════════════════════════
      // 日志与调试
      // ════════════════════════════════════════════════════════════
      {
        name: "get_logs",
        description: "\u62C9\u53D6\u8BBE\u5907 logcat\u3002filter='crash' \u9ED8\u8BA4\u53EA\u770B\u9519\u8BEF/\u5F02\u5E38\uFF08\u7528 logcat \u539F\u751F tag \u8FC7\u6EE4\uFF0C\u6BEB\u79D2\u7EA7\u8FD4\u56DE\uFF09\uFF1Bfilter='all' \u62FF\u6240\u6709\u65E5\u5FD7\uFF08\u4F20 packageName \u65F6\u81EA\u52A8\u52A0 --pid \u8FC7\u6EE4\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u6392\u67E5\u5D29\u6E83\u3001\u8FFD\u8E2A\u4E1A\u52A1\u6D41\u7A0B\u65E5\u5FD7\u3002\u6539\u7528 logcat_search\uFF1A\u82E5\u9700\u8981\u6B63\u5219\u6216\u7279\u5B9A tag \u8FC7\u6EE4\u3002\u8FD4\u56DE JSON: {success, filter, mode, lines, appRunning, pid, logs: [...]}\u3002\u8017\u65F6 crash fast (~500ms)\uFF0Call medium (~2s)\u3002\u793A\u4F8B\uFF1Aget_logs({ packageName: 'com.example.app', filter: 'crash', lines: 50 })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "\u53EF\u9009\u3002\u6309\u5305\u540D\u8FC7\u6EE4\uFF08\u81EA\u52A8\u7528 pidof \u9650\u5B9A pid\uFF09" },
            filter: { type: "string", enum: ["crash", "all"], description: "\u53EF\u9009\u3002crash=\u53EA\u770B\u9519\u8BEF\uFF08\u5FEB\uFF09\uFF0Call=\u5168\u91CF\uFF08\u6162\uFF09", default: "crash" },
            lines: { type: "number", description: "\u53EF\u9009\u3002\u6700\u591A\u8FD4\u56DE\u884C\u6570", default: 50 },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      {
        name: "logcat_search",
        description: "logcat \u5173\u952E\u8BCD/\u6B63\u5219\u641C\u7D22\u3002\u6BD4 get_logs \u66F4\u7075\u6D3B\uFF1A\u652F\u6301\u6B63\u5219\u6A21\u5F0F\u3001tag \u8FC7\u6EE4\u3001\u4E25\u91CD\u5EA6\u7EA7\u522B\u3001\u884C\u6570\u9650\u5236\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u627E\u7279\u5B9A\u4E1A\u52A1\u65E5\u5FD7\uFF08\u5982\u7F51\u7EDC\u8BF7\u6C42\u3001\u7528\u6237\u64CD\u4F5C\uFF09\uFF0C\u6216\u6309 tag \u8FC7\u6EE4\u3002\u6539\u7528 get_logs\uFF1A\u82E5\u53EA\u9700\u9519\u8BEF\u65E5\u5FD7\uFF08\u66F4\u5FEB\uFF09\u3002\u8FD4\u56DE JSON: {success, matched, pattern, tag, level, appRunning, lines: [...]}\u3002\u8017\u65F6 fast (~500ms)\u3002\u793A\u4F8B\uFF1Alogcat_search({ pattern: 'Network.*timeout', tag: 'OkHttp', level: 'W' })",
        inputSchema: {
          type: "object",
          properties: {
            pattern: { type: "string", description: "\u53EF\u9009\u3002POSIX \u6B63\u5219\uFF08\u4E0E tag \u4E8C\u9009\u4E00\uFF09" },
            packageName: { type: "string", description: "\u53EF\u9009\u3002\u6309\u5305\u540D\u8FC7\u6EE4\uFF08\u81EA\u52A8\u53D6 pid\uFF09" },
            tag: { type: "string", description: "\u53EF\u9009\u3002Android log tag" },
            level: { type: "string", enum: ["V", "D", "I", "W", "E"], description: "\u53EF\u9009\u3002\u6700\u4F4E\u4E25\u91CD\u5EA6", default: "I" },
            maxLines: { type: "number", description: "\u53EF\u9009\u3002\u6700\u5927\u8FD4\u56DE\u884C\u6570", default: 200 },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      {
        name: "parse_crash",
        description: "\u4ECE logcat \u63D0\u53D6\u5E76\u89E3\u6790 Java \u5D29\u6E83 / ANR / Native crash\uFF0C\u6309\u4E8B\u4EF6\u5206\u7EC4\uFF0C\u8F93\u51FA\u7ED3\u6784\u5316\u5806\u6808\uFF08\u524D 30 \u884C\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u7528\u6237\u8BF4\u300C\u4E3A\u4EC0\u4E48\u5D29\u6E83\u4E86\u300D\u300C\u521A\u624D ANR \u4E86\u5417\u300D\u65F6\u4E00\u952E\u5F52\u56E0\u3002\u8FD4\u56DE JSON: {success, crashCount, crashes: [{type, timestamp, process, exception, message, stack, raw}], rawLineCount}\u3002\u8017\u65F6 medium (~2-5s)\u3002\u793A\u4F8B\uFF1Aparse_crash({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "\u53EF\u9009\u3002\u6309\u5305\u540D\u8FC7\u6EE4" },
            lookbackSec: { type: "number", description: "\u53EF\u9009\u3002\u56DE\u770B\u79D2\u6570\uFF08\u4FDD\u7559\u5B57\u6BB5\uFF0C\u5F53\u524D\u5B9E\u73B0\u62C9 -t 2000 \u884C\uFF09", default: 300 },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      {
        name: "clear_logs",
        description: "\u6E05\u7A7A logcat \u7F13\u51B2\u3002\u5728 measure_app_launch / \u81EA\u5B9A\u4E49\u6D4B\u8BD5\u524D\u6E05\u7406\u566A\u58F0\u65E5\u5FD7\u3002\u8FD4\u56DE JSON: {success, message}\u3002\u8017\u65F6 fast (~200ms)\u3002\u793A\u4F8B\uFF1Aclear_logs({})",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      // ════════════════════════════════════════════════════════════
      // 设备管理
      // ════════════════════════════════════════════════════════════
      {
        name: "list_devices",
        description: "\u5217\u51FA\u6240\u6709\u8FDE\u63A5\u7684\u8BBE\u5907\uFF0C\u542B Android \u7248\u672C/SDK/\u5206\u8FA8\u7387/DPI\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u5F00\u59CB\u4EFB\u4F55\u8BBE\u5907\u64CD\u4F5C\u524D\u5148\u786E\u8BA4\u76EE\u6807\u3002\u8FD4\u56DE JSON: {success, deviceCount, devices: [{serial, state, model, androidVersion, sdkVersion, screenResolution, density}]}\u3002\u8017\u65F6 fast (~1-2s\uFF0C4 \u4E2A\u5C5E\u6027\u67E5\u8BE2\u5E76\u884C)\u3002\u793A\u4F8B\uFF1Alist_devices({})",
        inputSchema: { type: "object", properties: {} }
      },
      {
        name: "device_info",
        description: "\u83B7\u53D6\u5355\u53F0\u8BBE\u5907\u7684\u5B8C\u6574 getprop \u5C5E\u6027\uFF08\u578B\u53F7\u3001\u5236\u9020\u5546\u3001\u7535\u6C60\u3001\u5185\u5B58\u3001CPU \u67B6\u6784\u7B49\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u9700\u8981\u8BE6\u7EC6\u786C\u4EF6/\u7CFB\u7EDF\u4FE1\u606F\u3002\u8FD4\u56DE JSON: {success, serial, details: {[key]: value}}\u3002\u8017\u65F6 medium (~3-5s, 100+ \u5C5E\u6027)\u3002\u793A\u4F8B\uFF1Adevice_info({ serial: 'emulator-5554' })",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Required. \u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["serial"]
        }
      },
      {
        name: "shell_command",
        description: "\u5728\u8BBE\u5907\u4E0A\u6267\u884C\u4EFB\u610F shell \u547D\u4EE4\u3002\u614E\u7528\uFF1A\u6CA1\u6709\u5B89\u5168\u6C99\u7BB1\uFF0C\u547D\u4EE4\u76F4\u63A5\u5728\u8BBE\u5907 shell \u6267\u884C\u3002\u6539\u7528\u4E13\u7528\u5DE5\u5177\uFF1A\u82E5\u5DF2\u6709\u5BF9\u5E94\u80FD\u529B\uFF08\u5982\u622A\u56FE\u7528 screenshot\uFF09\u3002\u8FD4\u56DE JSON: {success, output}\u3002\u8017\u65F6 depends on command\u3002\u793A\u4F8B\uFF1Ashell_command({ command: 'pm list packages | head -5' })",
        inputSchema: {
          type: "object",
          properties: {
            command: { type: "string", description: "Required. shell \u547D\u4EE4" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["command"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 应用管理
      // ════════════════════════════════════════════════════════════
      {
        name: "list_apps",
        description: "\u5217\u51FA\u5DF2\u5B89\u88C5\u5E94\u7528\u3002system=false \u65F6\u6392\u9664\u7CFB\u7EDF\u5E94\u7528\uFF08\u9ED8\u8BA4\uFF09\uFF0CthirdParty=true \u65F6\u53EA\u5217\u7B2C\u4E09\u65B9\u3002\u8FD4\u56DE JSON: {success, appCount, apps: [{packageName, versionName, versionCode}]}\u3002\u8017\u65F6 medium (~1-3s, \u5355\u6B21 dumpsys + \u89E3\u6790\uFF0C\u65E0 N+1)\u3002\u793A\u4F8B\uFF1Alist_apps({ thirdParty: true })",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" },
            system: { type: "boolean", description: "\u53EF\u9009\u3002\u662F\u5426\u5305\u542B\u7CFB\u7EDF\u5E94\u7528", default: false },
            thirdParty: { type: "boolean", description: "\u53EF\u9009\u3002\u662F\u5426\u53EA\u5217\u7B2C\u4E09\u65B9", default: true }
          }
        }
      },
      {
        name: "app_info",
        description: "\u83B7\u53D6\u5E94\u7528\u8BE6\u7EC6\u4FE1\u606F\uFF08\u7248\u672C\u53F7\u3001\u5B89\u88C5\u65F6\u95F4\u3001\u6570\u636E\u76EE\u5F55\u3001\u7B7E\u540D\u4FE1\u606F\u7B49\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u9700\u8981\u786E\u8BA4\u5E94\u7528\u7248\u672C\u6216\u5B89\u88C5\u6765\u6E90\u3002\u8FD4\u56DE JSON: {success, app: {packageName, versionName, versionCode, firstInstallTime, lastUpdateTime, dataDir}}\u3002\u8017\u65F6 fast (~500ms)\u3002\u793A\u4F8B\uFF1Aapp_info({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. \u5305\u540D" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["packageName"]
        }
      },
      {
        name: "uninstall_app",
        description: "\u5378\u8F7D\u5E94\u7528\u3002keepData=true \u65F6\u4FDD\u7559 /data/data/&lt;pkg&gt; \u76EE\u5F55\uFF08\u91CD\u88C5\u53EF\u6062\u590D\u6570\u636E\uFF09\u3002\u8FD4\u56DE JSON: {success, message}\u3002\u8017\u65F6 medium (~3-5s)\u3002\u793A\u4F8B\uFF1Auninstall_app({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. \u5305\u540D" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" },
            keepData: { type: "boolean", description: "\u53EF\u9009\u3002\u4FDD\u7559\u5E94\u7528\u6570\u636E", default: false }
          },
          required: ["packageName"]
        }
      },
      {
        name: "clear_app_data",
        description: "\u6E05\u9664\u5E94\u7528\u6570\u636E\uFF08\u542B\u6570\u636E\u5E93\u3001SharedPreferences\u3001\u7F13\u5B58\uFF09\u3002\u7B49\u4EF7\u4E8E\u300C\u8BBE\u7F6E\u2192\u5E94\u7528\u2192\u5B58\u50A8\u2192\u6E05\u9664\u6570\u636E\u300D\u3002\u5E38\u7528\u4E8E measure_app_launch \u7684\u51B7\u542F\u52A8\u524D\u6E05\u7406\u3002\u8FD4\u56DE JSON: {success, message}\u3002\u8017\u65F6 medium (~3-5s)\u3002\u793A\u4F8B\uFF1Aclear_app_data({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. \u5305\u540D" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["packageName"]
        }
      },
      {
        name: "stop_app",
        description: "\u5F3A\u5236\u505C\u6B62\u5E94\u7528\uFF08force-stop\uFF09\uFF0C\u4E0B\u6B21\u542F\u52A8\u4F1A\u5B8C\u6574\u8D70 Application.onCreate\u3002\u8FD4\u56DE JSON: {success, message}\u3002\u8017\u65F6 fast (~500ms)\u3002\u793A\u4F8B\uFF1Astop_app({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. \u5305\u540D" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["packageName"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 性能监控
      // ════════════════════════════════════════════════════════════
      {
        name: "performance_metrics",
        description: "\u91C7\u96C6\u8BBE\u5907/\u5E94\u7528\u6027\u80FD\u6307\u6807\uFF1ACPU \u4F7F\u7528\u7387\u3001PSS \u5185\u5B58\u3001gfxinfo FPS\u3001\u7535\u6C60\u7535\u91CF\u3001\u6E29\u5EA6\u3002packageName \u4F20\u5165\u65F6\u989D\u5916\u7ED9\u51FA\u5E94\u7528\u7EA7 PSS\u3002\u8FD4\u56DE JSON: {success, cpu, memory, fps, battery, temperature}\u3002\u8017\u65F6 medium (~2-3s, \u591A\u4E2A dumpsys \u5E76\u884C)\u3002\u793A\u4F8B\uFF1Aperformance_metrics({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "\u53EF\u9009\u3002\u5305\u540D\uFF08\u4F20\u5165\u5219\u989D\u5916\u91C7\u96C6\u5E94\u7528\u7EA7 PSS\uFF09" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      {
        name: "measure_app_launch",
        description: "\u6D4B\u91CF\u5E94\u7528\u51B7\u542F\u52A8/\u70ED\u542F\u52A8/\u9875\u9762\u8DF3\u8F6C\u8017\u65F6\uFF0C\u591A\u6B21\u91C7\u6837\uFF08\u9ED8\u8BA4 3 \u6B21\uFF09\u53D6 min/max/avg/p95\uFF0C\u5E76\u6309 TTID \u7ED9\u51FA A/B/C/D \u8BC4\u5206 + \u4F18\u5316\u5EFA\u8BAE\u3002launchType\uFF1Acold_start \u8D70 force-stop+clear data\uFF1Bwarm_start \u8D70 HOME\u2192\u91CD\u542F\u3002\u8FD4\u56DE JSON: {success, packageName, grade, statistics: {ttid, ttfd, totalTime}, results, recommendations, report}\u3002\u8017\u65F6 slow (~3-10s \xD7 iterations)\u3002\u793A\u4F8B\uFF1Ameasure_app_launch({ packageName: 'com.example.app', launchType: 'cold_start', iterations: 3 })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. \u5305\u540D" },
            launchType: { type: "string", enum: ["cold_start", "warm_start", "page_transition"], description: "\u53EF\u9009\u3002\u51B7/\u70ED/\u9875\u9762\u8DF3\u8F6C", default: "cold_start" },
            activityName: { type: "string", description: "\u53EF\u9009\u3002\u6307\u5B9A Activity\uFF08\u5168\u9650\u5B9A\u7C7B\u540D\uFF09" },
            iterations: { type: "number", description: "\u53EF\u9009\u3002\u91C7\u6837\u6B21\u6570\uFF0C\u9ED8\u8BA4 3", default: 3 }
          },
          required: ["packageName"]
        }
      },
      {
        name: "record_screen",
        description: "\u5F55\u5C4F\u5230\u672C\u5730 MP4 \u6587\u4EF6\u3002\u9ED8\u8BA4 10s\u3002\u8FD4\u56DE JSON: {success, message, path}\u3002\u8017\u65F6 slow (\u2265duration)\u3002\u793A\u4F8B\uFF1Arecord_screen({ duration: 15, outputPath: './demo.mp4' })",
        inputSchema: {
          type: "object",
          properties: {
            duration: { type: "number", description: "\u53EF\u9009\u3002\u5F55\u5236\u79D2\u6570", default: 10 },
            outputPath: { type: "string", description: "\u53EF\u9009\u3002\u8F93\u51FA\u8DEF\u5F84", default: "./screen_record.mp4" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      // ════════════════════════════════════════════════════════════
      // 设备控制（新）
      // ════════════════════════════════════════════════════════════
      {
        name: "set_orientation",
        description: "\u5F3A\u5236\u8BBE\u5907\u65CB\u8F6C\u65B9\u5411\u3002portrait=\u7AD6\u5C4F\uFF0Clandscape=\u6A2A\u5C4F\uFF0Cauto=\u8DDF\u968F\u91CD\u529B\u3002\u6CE8\u610F\uFF1A\u90E8\u5206 App \u5728 manifest \u4E2D\u9501\u5B9A\u65B9\u5411\uFF0C\u4F1A\u8986\u76D6\u6B64\u8BBE\u7F6E\u3002\u8FD4\u56DE JSON: {success, orientation, accelRotation, userRotation, hint}\u3002\u8017\u65F6 fast (~500ms)\u3002\u793A\u4F8B\uFF1Aset_orientation({ orientation: 'landscape' })",
        inputSchema: {
          type: "object",
          properties: {
            orientation: { type: "string", enum: ["portrait", "landscape", "auto"], description: "Required. \u76EE\u6807\u65B9\u5411" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["orientation"]
        }
      },
      {
        name: "set_gps",
        description: "\u6A21\u62DF GPS \u4F4D\u7F6E\uFF08\u4EC5 Android \u6A21\u62DF\u5668\u6709\u6548\uFF0C\u771F\u673A\u9700\u5F00\u542F\u300C\u5141\u8BB8\u6A21\u62DF\u4F4D\u7F6E\u300D\u5F00\u53D1\u8005\u9009\u9879\u5E76\u88C5 mock app\uFF09\u3002lat \u2208 [-90,90]\u3001lon \u2208 [-180,180]\uFF08\u6CE8\u610F emu \u547D\u4EE4\u662F lon \u5728\u524D\uFF09\u3002\u8FD4\u56DE JSON: {success, lat, lon, method, warning}\u3002\u8017\u65F6 fast (~500ms)\u3002\u793A\u4F8B\uFF1Aset_gps({ lat: 39.9042, lon: 116.4074 })",
        inputSchema: {
          type: "object",
          properties: {
            lat: { type: "number", description: "Required. \u7EAC\u5EA6\uFF08\u5341\u8FDB\u5236\u5EA6\uFF09" },
            lon: { type: "number", description: "Required. \u7ECF\u5EA6\uFF08\u5341\u8FDB\u5236\u5EA6\uFF09" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["lat", "lon"]
        }
      },
      {
        name: "animation_scale",
        description: "\u8C03\u6574\u7CFB\u7EDF\u52A8\u753B\u7F29\u653E\uFF1A0=\u5173\u95ED\u52A8\u753B\uFF08UI \u6D4B\u8BD5/\u5F55\u5C4F\u9996\u9009\uFF0C\u77AC\u95F4\u6267\u884C\uFF09\u30011=\u7CFB\u7EDF\u9ED8\u8BA4\u30012=\u6162\u901F\uFF08\u6F14\u793A/\u622A\u56FE\u7528\uFF09\u3002\u6539\u56DE 1 \u6062\u590D\u3002\u8FD4\u56DE JSON: {success, scale, applied: [{key, value, success}]}\u3002\u8017\u65F6 fast (~500ms)\u3002\u793A\u4F8B\uFF1Aanimation_scale({ scale: 0 })",
        inputSchema: {
          type: "object",
          properties: {
            scale: { type: "number", description: "Required. \u7F29\u653E\u503C [0, 10]\uFF0C\u5E38\u7528 0/0.5/1/2" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["scale"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 代码质量
      // ════════════════════════════════════════════════════════════
      {
        name: "code_quality",
        description: "\u4EE3\u7801\u8D28\u91CF\u68C0\u67E5\uFF1Aktlint \u89C4\u8303\uFF08fix=true \u65F6\u81EA\u52A8\u4FEE\u590D\uFF09\u3001\u5708\u590D\u6742\u5EA6\u3001\u4EE3\u7801\u884C\u6570\u7EDF\u8BA1\u3002\u9700\u8981 ktlint/detekt \u5728 PATH \u4E2D\u3002\u8FD4\u56DE JSON: {success, summary, issues: [...], linesOfCode}\u3002\u8017\u65F6 medium (~5-30s)\u3002\u793A\u4F8B\uFF1Acode_quality({ projectPath: '.', fix: false })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "\u53EF\u9009\u3002\u5DE5\u7A0B\u6839\u8DEF\u5F84", default: "." },
            fix: { type: "boolean", description: "\u53EF\u9009\u3002\u81EA\u52A8\u4FEE\u590D ktlint \u95EE\u9898", default: false }
          }
        }
      },
      {
        name: "run_tests",
        description: "\u8FD0\u884C\u5355\u5143\u6D4B\u8BD5 (JVM, ./gradlew test) \u6216\u4EEA\u5668\u5316\u6D4B\u8BD5 (on-device, connectedAndroidTest)\u3002type='all' \u8DD1\u4E24\u7C7B\u3002\u8FD4\u56DE JSON: {success, results: {unit: {...}, instrumented: {...}}}\u3002\u8017\u65F6 slow (1-10 \u5206\u949F)\u3002\u793A\u4F8B\uFF1Arun_tests({ type: 'unit' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "\u53EF\u9009\u3002\u5DE5\u7A0B\u6839\u8DEF\u5F84", default: "." },
            type: { type: "string", enum: ["unit", "instrumented", "all"], description: "\u53EF\u9009\u3002\u6D4B\u8BD5\u7C7B\u578B", default: "unit" },
            module: { type: "string", description: "\u53EF\u9009\u3002\u6307\u5B9A Gradle module" }
          }
        }
      },
      // ════════════════════════════════════════════════════════════
      // UI 自动化测试
      // ════════════════════════════════════════════════════════════
      {
        name: "ui_test",
        description: "\u6267\u884C\u58F0\u660E\u5F0F UI \u81EA\u52A8\u5316\u6D4B\u8BD5\uFF1Asteps \u6570\u7EC4\u4E2D\u6BCF\u9879 {action, params}\u3002actions\uFF1Atap(x,y) / swipe(x1,y1,x2,y2,duration) / input(text) / wait(ms) / screenshot()\u3002\u8FD4\u56DE JSON: {success, message, duration, screenshot}\u3002\u8017\u65F6 depends on steps\u3002\u793A\u4F8B\uFF1Aui_test({ steps: [{action: 'screenshot', params: {}}, {action: 'tap', params: {x: 500, y: 800}}] })",
        inputSchema: {
          type: "object",
          properties: {
            steps: {
              type: "array",
              description: "\u6D4B\u8BD5\u6B65\u9AA4\u5E8F\u5217",
              items: {
                type: "object",
                properties: {
                  action: { type: "string", enum: ["tap", "swipe", "input", "wait", "screenshot"], description: "Required. \u52A8\u4F5C\u7C7B\u578B" },
                  params: { type: "object", description: "Required. \u52A8\u4F5C\u53C2\u6570\uFF08tap/swipe \u9700 x,y\uFF1Binput \u9700 text\uFF1Bwait \u9700 durationMs\uFF09" }
                },
                required: ["action", "params"]
              }
            }
          },
          required: ["steps"]
        }
      },
      {
        name: "regression_test",
        description: "\u8FD0\u884C\u57FA\u7840\u56DE\u5F52\u6D4B\u8BD5\u5957\u4EF6\uFF1A\u542F\u52A8 App \u2192 \u622A\u56FE \u2192 \u9A8C\u8BC1 UI \u5C42\u7EA7\u53EF\u8BFB \u2192 Activity \u8DF3\u8F6C\u3002\u6539\u7528 ui_test\uFF1A\u82E5\u9700\u8981\u81EA\u5B9A\u4E49\u6B65\u9AA4\u3002\u8FD4\u56DE JSON: {success, passed, failed, total, results: [{name, success, message}]}\u3002\u8017\u65F6 slow (~10-15s)\u3002\u793A\u4F8B\uFF1Aregression_test({ packageName: 'com.example.app' })",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Required. \u5305\u540D" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["packageName"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 项目报告
      // ════════════════════════════════════════════════════════════
      {
        name: "project_report",
        description: "\u751F\u6210\u9879\u76EE\u7EFC\u5408\u62A5\u544A\uFF1A\u6A21\u5757\u7EDF\u8BA1\u3001Clean Architecture \u5206\u5C42\u3001\u4F9D\u8D56\u5206\u7C7B\u3001\u4EE3\u7801\u6307\u6807\u3001\u8D28\u91CF\u5EFA\u8BAE\u3002format=markdown \u8F93\u51FA\u53EF\u8BFB\u6587\u6863\uFF0C=json \u8F93\u51FA\u7ED3\u6784\u5316\u6570\u636E\u3002includePerformance=true \u65F6\u9644\u5E26\u6027\u80FD\u6307\u6807\uFF08\u9700\u4F20 packageName\uFF09\u3002\u8FD4\u56DE JSON/Markdown\u3002\u8017\u65F6 medium (~5-15s)\u3002\u793A\u4F8B\uFF1Aproject_report({ projectPath: '.', format: 'markdown' })",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "\u53EF\u9009\u3002\u5DE5\u7A0B\u6839\u8DEF\u5F84", default: "." },
            includePerformance: { type: "boolean", description: "\u53EF\u9009\u3002\u662F\u5426\u5305\u542B\u6027\u80FD\u6570\u636E", default: false },
            packageName: { type: "string", description: "\u53EF\u9009\u3002\u6027\u80FD\u6570\u636E\u5BF9\u5E94\u5305\u540D" },
            format: { type: "string", enum: ["markdown", "json"], description: "\u53EF\u9009\u3002\u8F93\u51FA\u683C\u5F0F", default: "markdown" }
          }
        }
      },
      // ════════════════════════════════════════════════════════════
      // 文件操作
      // ════════════════════════════════════════════════════════════
      {
        name: "push_file",
        description: "\u63A8\u9001\u672C\u5730\u6587\u4EF6\u5230\u8BBE\u5907\u3002\u8FD4\u56DE JSON: {success, message}\u3002\u8017\u65F6 depends on file size\u3002\u793A\u4F8B\uFF1Apush_file({ localPath: './data.json', remotePath: '/sdcard/data.json' })",
        inputSchema: {
          type: "object",
          properties: {
            localPath: { type: "string", description: "Required. \u672C\u5730\u6587\u4EF6\u8DEF\u5F84" },
            remotePath: { type: "string", description: "Required. \u8BBE\u5907\u76EE\u6807\u8DEF\u5F84" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["localPath", "remotePath"]
        }
      },
      {
        name: "pull_file",
        description: "\u4ECE\u8BBE\u5907\u62C9\u53D6\u6587\u4EF6\u5230\u672C\u5730\u3002\u8FD4\u56DE JSON: {success, message, localPath}\u3002\u8017\u65F6 depends on file size\u3002\u793A\u4F8B\uFF1Apull_file({ remotePath: '/sdcard/data.json', localPath: './data.json' })",
        inputSchema: {
          type: "object",
          properties: {
            remotePath: { type: "string", description: "Required. \u8BBE\u5907\u6587\u4EF6\u8DEF\u5F84" },
            localPath: { type: "string", description: "Required. \u672C\u5730\u4FDD\u5B58\u8DEF\u5F84" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["remotePath", "localPath"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 网络调试
      // ════════════════════════════════════════════════════════════
      {
        name: "network_state",
        description: "\u67E5\u8BE2\u8BBE\u5907\u7F51\u7EDC\u72B6\u6001\uFF1AWiFi\u3001\u79FB\u52A8\u6570\u636E\u3001\u98DE\u884C\u6A21\u5F0F\u3002\u8FD4\u56DE JSON: {success, wifi, mobile, airplaneMode}\u3002\u8017\u65F6 fast (~500ms, \u5355\u6B21 shell \u8C03\u7528\u67E5 3 \u9879)\u3002\u793A\u4F8B\uFF1Anetwork_state({})",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          }
        }
      },
      {
        name: "set_network",
        description: "\u5207\u6362\u7F51\u7EDC\u72B6\u6001\u3002type=wifi \u8D70 svc wifi\uFF1Btype=mobile \u8D70 svc data\uFF1Btype=airplane \u8D70 settings\u3002\u6539\u7528 shell_command\uFF1A\u82E5\u9700\u8981\u66F4\u7CBE\u7EC6\u63A7\u5236\u3002\u8FD4\u56DE JSON: {success, message}\u3002\u8017\u65F6 fast (~1s)\u3002\u793A\u4F8B\uFF1Aset_network({ type: 'airplane', enabled: true })",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", enum: ["wifi", "mobile", "airplane"], description: "Required. \u7F51\u7EDC\u7C7B\u578B" },
            enabled: { type: "boolean", description: "Required. \u542F\u7528\u6216\u7981\u7528" },
            serial: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u5907\u5E8F\u5217\u53F7" }
          },
          required: ["type", "enabled"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // APK 元数据（新）
      // ════════════════════════════════════════════════════════════
      {
        name: "apk_metadata",
        description: "\u89E3\u6790 APK \u7684\u5143\u6570\u636E\uFF1A\u5305\u540D\u3001\u7248\u672C\u53F7\u3001minSdk/targetSdk\u3001\u6743\u9650\u3001Activity \u5217\u8868\u3001native code\u3001\u7B7E\u540D\u4FE1\u606F\u3002\u81EA\u52A8\u68C0\u6D4B aapt2 / aapt / apkanalyzer\uFF08\u6309 Android SDK \u5DE5\u5177\u94FE\u67E5\u627E\uFF09\u3002\u4F7F\u7528\u65F6\u673A\uFF1A\u7528\u6237\u95EE\u300C\u8FD9\u4E2A APK \u662F\u4EC0\u4E48\u300D\u300C\u5B83\u8981\u4EC0\u4E48\u6743\u9650\u300D\u300C\u5B83\u80FD\u88C5\u5230 Android \u51E0\u4E0A\u300D\u3002\u8FD4\u56DE JSON: {success, tool, packageName, versionName, versionCode, minSdk, targetSdk, permissions, launchableActivity, ...}\u3002\u8017\u65F6 medium (~2-5s)\u3002\u793A\u4F8B\uFF1Aapk_metadata({ apkPath: './app-debug.apk' })",
        inputSchema: {
          type: "object",
          properties: {
            apkPath: { type: "string", description: "Required. APK \u6587\u4EF6\u8DEF\u5F84" }
          },
          required: ["apkPath"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // 设计稿 → 结构化规范（视觉 LLM 转换）
      // ════════════════════════════════════════════════════════════
      {
        name: "list_design_files",
        description: "\u5217\u51FA design \u76EE\u5F55\u4E0B\u7684\u6240\u6709\u8BBE\u8BA1\u7A3F\uFF08\u9ED8\u8BA4 ./design/\uFF0C\u53EF\u4F20 dir= \u8986\u76D6\uFF09\uFF0C\u81EA\u52A8\u8BC6\u522B .png/.jpg/.jpeg/.webp\uFF0C\u9644\u5E26\u6587\u4EF6\u540D\u2192\u9875\u540D\u6620\u5C04\u3002\u7528\u4E8E\uFF1A\u5F00\u59CB\u5B9E\u73B0\u65B0\u9875\u9762\u524D\u5148\u770B\u6709\u54EA\u4E9B\u8BBE\u8BA1\u7A3F\u53EF\u53C2\u8003\u3002\u6539\u7528 extract_design_spec\uFF1A\u62FF\u5230\u5177\u4F53\u6587\u4EF6\u8DEF\u5F84\u540E\u8C03\u7528\u3002\u8FD4\u56DE JSON: {success, dir, count, files: [{name, path, sizeKB, pageHint}]}\u3002\u8017\u65F6 fast (~10ms, \u7EAF\u6587\u4EF6\u7CFB\u7EDF)\u3002\u793A\u4F8B\uFF1Alist_design_files({}) \u6216 list_design_files({ dir: './design/v2' })",
        inputSchema: {
          type: "object",
          properties: {
            dir: { type: "string", description: "\u53EF\u9009\u3002\u8BBE\u8BA1\u7A3F\u76EE\u5F55\uFF0C\u9ED8\u8BA4 ./design" }
          }
        }
      },
      {
        name: "extract_design_spec",
        description: "\u3010\u6838\u5FC3\u5DE5\u5177\u3011\u7528 Minimax \u89C6\u89C9 LLM \u628A\u8BBE\u8BA1\u7A3F\u622A\u56FE\u8F6C\u6362\u4E3A Agent \u53EF\u8BFB\u7684\u7ED3\u6784\u5316\u89C4\u8303\uFF08JSON \u6A21\u5F0F\uFF09\uFF1A\u542B colorTokens\uFF08\u76F4\u63A5\u5582\u7ED9 Compose ColorScheme\uFF09\u3001typography\uFF08\u5B57\u53F7/\u5B57\u91CD/\u989C\u8272\uFF09\u3001layout sections\uFF08Scaffold \u7684 topBar/TabRow/BottomNav \u5212\u5206\uFF09\u3001components \u5217\u8868\uFF08\u6BCF\u5F20\u5361\u7247\u7684 kind/bounds/text/source/hasImage\uFF09\u3001bottomNav\u3001textContent\u3001interactions\u3002format=both \u540C\u65F6\u8FD4\u56DE\u4EBA\u7C7B\u53EF\u8BFB\u7684 markdown\u3002\u6539\u7528 extract_design_tokens\uFF1A\u53EA\u5173\u5FC3\u989C\u8272\u3002\u6539\u7528 extract_design_components\uFF1A\u53EA\u5173\u5FC3\u7EC4\u4EF6\u5750\u6807\u3002\u6539\u7528 design_to_compose\uFF1A\u60F3\u8981\u76F4\u63A5\u53EF\u7528\u7684\u4EE3\u7801\u3002\u8FD4\u56DE JSON\uFF08format=json|markdown|both\uFF09\u3002\u8017\u65F6 slow (vision API \u4E3B\u5BFC)\u3002\u9700 MINIMAX_API_KEY\u3002model \u53EF\u9009\uFF1AMiniMax-M3\uFF08\u9ED8\u8BA4\uFF0Cthinking-disabled\uFF0C\u6700\u5FEB\uFF09\u3001MiniMax-M2.7-highspeed\uFF08100 TPS\uFF09\u3001MiniMax-M2.7\uFF0860 TPS\uFF09\u3002\u793A\u4F8B\uFF1Aextract_design_spec({ imagePath: '\u9996\u9875-\u63A8\u8350.jpg' }) \u6216 { imagePath: '\u8BBE\u8BA1/\u65B0\u7248\u9996\u9875.png', format: 'json', model: 'MiniMax-M3' }",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. \u8BBE\u8BA1\u7A3F\u8DEF\u5F84\uFF08\u7EDD\u5BF9\u8DEF\u5F84\u3001\u76F8\u5BF9\u8DEF\u5F84\u3001\u7EAF\u6587\u4EF6\u540D\u5747\u53EF\uFF0C\u6587\u4EF6\u540D\u65F6\u4F1A\u81EA\u52A8\u5728 ./design/ \u4E0B\u67E5\u627E\uFF09" },
            format: { type: "string", enum: ["json", "markdown", "both"], description: "\u53EF\u9009\u3002\u8F93\u51FA\u683C\u5F0F\uFF1Ajson=\u7ED3\u6784\u5316\u6570\u636E\uFF08\u5582\u7ED9\u4EE3\u7801\uFF09\u3001markdown=\u4EBA\u7C7B\u9605\u8BFB\u3001both=\u540C\u65F6\u8FD4\u56DE", default: "both" },
            pageHint: { type: "string", description: "\u53EF\u9009\u3002\u9875\u9762\u540D\u63D0\u793A\uFF08\u9ED8\u8BA4\u4ECE\u6587\u4EF6\u540D\u63A8\u65AD\uFF09\uFF0C\u5E2E\u52A9 LLM \u66F4\u597D\u7406\u89E3\u4E0A\u4E0B\u6587" },
            model: { type: "string", description: "\u53EF\u9009\u3002\u89C6\u89C9\u6A21\u578B ID\uFF08\u9ED8\u8BA4 MiniMax-M3\uFF0Cthinking-disabled\uFF09\u3002\u5176\u4ED6\u53EF\u9009\uFF1AMiniMax-M2.7-highspeed\uFF08100 TPS\uFF09\u3001MiniMax-M2.7\uFF0860 TPS\uFF09" }
          },
          required: ["imagePath"]
        }
      },
      {
        name: "extract_design_tokens",
        description: "\u53EA\u62BD\u53D6\u8BBE\u8BA1\u7A3F\u4E2D\u7684\u989C\u8272 token\uFF08hex + \u7528\u9014 + \u5927\u81F4\u5360\u6BD4\uFF09\u3002\u8F93\u51FA\u53EF\u76F4\u63A5\u751F\u6210 Compose ColorScheme\uFF1Aprimary/onPrimary/background/surface/onSurface/onSurfaceVariant/outline/accent/error \u7B49 5-10 \u4E2A\u6700\u663E\u8457\u989C\u8272\u3002\u6539\u7528 extract_design_spec\uFF1A\u82E5\u8FD8\u9700\u8981\u5B57\u4F53/\u5E03\u5C40/\u7EC4\u4EF6\u3002\u8FD4\u56DE JSON: {success, source, model, tokens: {tokenName: {hex, usage, pixelPct}}}\u3002\u8017\u65F6 slow\u3002\u9700 MINIMAX_API_KEY\u3002model \u53EF\u9009\uFF1AMiniMax-M3\uFF08\u63A8\u8350\uFF09\u3001MiniMax-M2.7-highspeed\u3002\u793A\u4F8B\uFF1Aextract_design_tokens({ imagePath: '\u9996\u9875-\u63A8\u8350.jpg' })",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. \u8BBE\u8BA1\u7A3F\u8DEF\u5F84" },
            model: { type: "string", description: "\u53EF\u9009\u3002\u89C6\u89C9\u6A21\u578B ID\uFF08\u9ED8\u8BA4 MiniMax-M3\uFF09" }
          },
          required: ["imagePath"]
        }
      },
      {
        name: "extract_design_components",
        description: "\u6309\u4ECE\u4E0A\u5230\u4E0B\u987A\u5E8F\u62BD\u53D6\u8BBE\u8BA1\u7A3F\u91CC\u6240\u6709 UI \u7EC4\u4EF6\uFF08\u5361/\u6309\u94AE/\u56FE\u6807/\u6807\u7B7E/Tab/BottomNavItem\uFF09\uFF0C\u542B kind\u3001bounds\u3001title\u3001text\u3001source\u3001time\u3001hasImage\u3002\u5750\u6807\u7528\u622A\u56FE\u7684\u50CF\u7D20\u7A7A\u95F4\u3002\u6539\u7528 extract_design_spec\uFF1A\u82E5\u8FD8\u8981\u989C\u8272\u5B57\u4F53\u5E03\u5C40\u3002\u6539\u7528 dump_hierarchy\uFF1A\u82E5\u60F3\u770B\u5F53\u524D\u5B9E\u73B0\u7684\u5143\u7D20\uFF08\u800C\u4E0D\u662F\u8BBE\u8BA1\u7A3F\uFF09\u3002\u8FD4\u56DE JSON: {success, source, model, components: [{id, kind, bounds, title, text, source, time, hasImage}]}\u3002\u8017\u65F6 slow\u3002\u9700 MINIMAX_API_KEY\u3002\u793A\u4F8B\uFF1Aextract_design_components({ imagePath: '\u9996\u9875-\u63A8\u8350.jpg' })",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. \u8BBE\u8BA1\u7A3F\u8DEF\u5F84" },
            pageHint: { type: "string", description: "\u53EF\u9009\u3002\u9875\u9762\u540D\u63D0\u793A" },
            model: { type: "string", description: "\u53EF\u9009\u3002\u89C6\u89C9\u6A21\u578B ID\uFF08\u9ED8\u8BA4 MiniMax-M3\uFF09" }
          },
          required: ["imagePath"]
        }
      },
      {
        name: "design_to_compose",
        description: "\u8BBE\u8BA1\u7A3F\u76F4\u63A5\u8F6C Jetpack Compose Screen.kt \u9AA8\u67B6\uFF08Scaffold + TopAppBar + TabRow + BottomNavigationBar + LazyColumn + \u5361\u7247\u5360\u4F4D\uFF09\u3002\u6587\u672B\u7528\u300C/* === TODO NOTES === */\u300D\u6CE8\u91CA\u5217\u51FA\u672A\u5B9E\u73B0\u90E8\u5206\u3002Agent \u62FF\u5230\u540E\u53EF\u76F4\u63A5\u843D\u5230 /app/src/main/java/<package>/presentation/<page>/ \u4E0B\u7EE7\u7EED\u5F00\u53D1\u3002\u6539\u7528 extract_design_spec\uFF1A\u82E5\u8981\u7ED3\u6784\u5316\u6570\u636E\uFF08\u7528\u4E8E\u81EA\u5DF1\u5199\u4EE3\u7801\uFF09\u3002\u8FD4\u56DE JSON: {success, source, model, packageName, fileName, kotlin, notes, usage}\u3002\u8017\u65F6 slow\u3002\u9700 MINIMAX_API_KEY\u3002\u63A8\u8350\u7528 MiniMax-M3\uFF08\u4EE3\u7801\u751F\u6210\u8D28\u91CF\u9AD8\u3001thinking-disabled \u540E\u5FEB\uFF09\u3002\u793A\u4F8B\uFF1Adesign_to_compose({ imagePath: '\u9996\u9875-\u63A8\u8350.jpg', packageName: 'com.example.toutiao', model: 'MiniMax-M3' })",
        inputSchema: {
          type: "object",
          properties: {
            imagePath: { type: "string", description: "Required. \u8BBE\u8BA1\u7A3F\u8DEF\u5F84" },
            packageName: { type: "string", description: "\u53EF\u9009\u3002\u5305\u540D\uFF08\u7528\u4E8E\u5EFA\u8BAE\u843D\u76D8\u8DEF\u5F84\uFF09\uFF0C\u9ED8\u8BA4 com.example.app" },
            model: { type: "string", description: "\u53EF\u9009\u3002\u89C6\u89C9\u6A21\u578B ID\uFF08\u9ED8\u8BA4 MiniMax-M3\uFF09" }
          },
          required: ["imagePath"]
        }
      },
      // ════════════════════════════════════════════════════════════
      // PM Agent 工具（AI 产品经理协作）
      // ════════════════════════════════════════════════════════════
      {
        name: "pm_review",
        description: "\u5355\u6B21\u622A\u56FE + VLM \u5BA1\u67E5\uFF0C\u4EA7\u51FA\u7ED3\u6784\u5316 issue \u62A5\u544A\u3002\u9002\u5408\u5FEB\u901F\u5BA1\u67E5\u5355\u4E2A\u9875\u9762\u3002\u8FD4\u56DE JSON: {success, review_id, overall_rating, summary, thinking_process, issues[], positives[], next_priorities[]}\u3002\u8017\u65F6 slow (10-30s)\u3002\u793A\u4F8B\uFF1Apm_review({ target: '\u9996\u9875\u63A8\u8350' })",
        inputSchema: {
          type: "object",
          properties: {
            target: { type: "string", description: "\u53EF\u9009\u3002\u5BA1\u67E5\u76EE\u6807\u9875\u9762\u540D\u79F0\uFF0C\u9ED8\u8BA4'\u9996\u9875\u5217\u8868'" },
            focus: { type: "array", items: { type: "string" }, description: "\u53EF\u9009\u3002\u91CD\u70B9\u5173\u6CE8\u7EF4\u5EA6\u5B57\u7B26\u4E32\u6570\u7EC4" },
            screenshotPath: { type: "string", description: "\u53EF\u9009\u3002\u5DF2\u6709\u622A\u56FE\u8DEF\u5F84\uFF0C\u7701\u7565\u5219\u81EA\u52A8\u622A\u56FE" }
          }
        }
      },
      {
        name: "pm_explore",
        description: "\u591A\u6B65\u81EA\u4E3B\u63A2\u7D22\uFF086-12 \u6B65\uFF09\uFF0CPM \u81EA\u4E3B\u8C03\u5EA6\u8BBE\u5907\u64CD\u4F5C\u5DE5\u5177\uFF0C\u9010\u4E2A\u9875\u9762\u5BA1\u67E5\u540E\u6C47\u603B\u3002\u9002\u5408\u6DF1\u5EA6\u5BA1\u67E5\u3002\u8FD4\u56DE JSON: {success, explore_id, overall_rating, summary, thinking_process, issues[], positives[], steps_taken, elapsed_ms}\u3002\u8017\u65F6 slow (1-5 \u5206\u949F)\u3002\u793A\u4F8B\uFF1Apm_explore({ goal: '\u5BA1\u67E5\u9996\u9875\u8D22\u7ECF\u9891\u9053', maxSteps: 8 })",
        inputSchema: {
          type: "object",
          properties: {
            goal: { type: "string", description: "\u53EF\u9009\u3002\u63A2\u7D22\u76EE\u6807\u63CF\u8FF0\uFF0C\u9ED8\u8BA4'\u5BA1\u67E5\u5F53\u524D\u9875\u9762\u7684\u53EF\u7528\u6027\u548C\u8BBE\u8BA1'" },
            maxSteps: { type: "number", description: "\u53EF\u9009\u3002\u6700\u5927\u6B65\u6570 1-12\uFF0C\u9ED8\u8BA4 6" }
          }
        }
      },
      {
        name: "pm_compare_with_design",
        description: "\u8BBE\u8BA1\u7A3F vs \u5B9E\u73B0\u7684\u50CF\u7D20 diff + LLM \u5206\u6790\u3002\u9700\u8981 MINIMAX_API_KEY\u3002\u8FD4\u56DE JSON: {success, design, impl, pixel_diff, llm_analysis}\u3002\u8017\u65F6 slow (5-20s)\u3002\u793A\u4F8B\uFF1Apm_compare_with_design({ designPath: 'design/\u9996\u9875-\u63A8\u8350.jpg' })",
        inputSchema: {
          type: "object",
          properties: {
            designPath: { type: "string", description: "Required. \u8BBE\u8BA1\u7A3F\u8DEF\u5F84" },
            implScreenshotPath: { type: "string", description: "\u53EF\u9009\u3002\u5F53\u524D\u5B9E\u73B0\u622A\u56FE\u8DEF\u5F84\uFF0C\u7701\u7565\u5219\u81EA\u52A8\u622A\u56FE" }
          },
          required: ["designPath"]
        }
      },
      {
        name: "pm_mark_fixed",
        description: "\u6807\u8BB0 issue \u4FEE\u590D\u72B6\u6001\uFF08fixed/ignored/reopen\uFF09\u3002\u540C\u6B65\u66F4\u65B0 .pm_memory.json \u548C .pm_state.json\u3002\u8FD4\u56DE JSON: {success, action, issue_id, fixed_count, ignored_count}\u3002\u793A\u4F8B\uFF1Apm_mark_fixed({ issueId: 'ISSUE-001', action: 'fixed', note: '\u5DF2\u4FEE\u590D Tab padding' })",
        inputSchema: {
          type: "object",
          properties: {
            issueId: { type: "string", description: "Required. Issue ID" },
            action: { type: "string", enum: ["fixed", "ignored", "reopen"], description: "\u53EF\u9009\u3002\u64CD\u4F5C\u7C7B\u578B\uFF0C\u9ED8\u8BA4 fixed" },
            note: { type: "string", description: "\u53EF\u9009\u3002\u5907\u6CE8\u8BF4\u660E" }
          },
          required: ["issueId"]
        }
      },
      {
        name: "pm_discuss",
        description: "\u4E0E\u9879\u76EE PM \u8BA8\u8BBA\u4EA7\u54C1\u95EE\u9898\u3001\u8BBE\u8BA1\u89C4\u8303\u6216\u4FEE\u590D\u65B9\u6848\u3002PM \u62E5\u6709\u5B8C\u6574\u7684\u9879\u76EE\u8BB0\u5FC6\uFF08\u5386\u53F2\u5BA1\u67E5\u8BB0\u5F55\u3001\u5DF2\u4FEE\u590D\u95EE\u9898\u3001\u8BBE\u8BA1\u7A3F\u89C4\u8303\u3001\u5BF9\u8BDD\u4E0A\u4E0B\u6587\uFF09\uFF0C\u80FD\u57FA\u4E8E\u591A\u8F6E\u4E0A\u4E0B\u6587\u7ED9\u51FA\u5177\u4F53\u53EF\u6267\u884C\u7684\u5EFA\u8BAE\u3002\u9002\u5408\u5728\u6536\u5230 pm_review / pm_explore \u62A5\u544A\u540E\u8FFD\u95EE\u7EC6\u8282\uFF0C\u6216\u5728\u5F00\u53D1\u8FC7\u7A0B\u4E2D\u968F\u65F6\u54A8\u8BE2\u8BBE\u8BA1\u89C4\u8303\u3002\u8FD4\u56DE JSON: {success, answer, memory_summary}\u3002\u793A\u4F8B\uFF1Apm_discuss({ question: 'ISSUE-001 \u5E94\u8BE5\u600E\u4E48\u4FEE\uFF1F', context: '\u6B63\u5728\u4FEE\u6539 HomeScreen.kt' })",
        inputSchema: {
          type: "object",
          properties: {
            question: { type: "string", description: "Required. \u4F60\u7684\u95EE\u9898" },
            context: { type: "string", description: "\u53EF\u9009\u3002\u5F53\u524D\u5F00\u53D1\u4E0A\u4E0B\u6587" },
            include_history: { type: "boolean", description: "\u662F\u5426\u643A\u5E26\u6700\u8FD1\u5BF9\u8BDD\u5386\u53F2\uFF0C\u9ED8\u8BA4 true", default: true },
            include_screenshot: { type: "boolean", description: "\u662F\u5426\u8BA9 PM \u5148\u622A\u56FE\u5F53\u524D\u9875\u9762\u518D\u56DE\u7B54\uFF0C\u9ED8\u8BA4 false", default: false }
          },
          required: ["question"]
        }
      },
      {
        name: "pm_check",
        description: "\u9A8C\u8BC1\u6307\u5B9A issue \u662F\u5426\u5DF2\u4FEE\u590D\u3002PM \u4F1A\u622A\u56FE\u5F53\u524D\u9875\u9762\uFF0C\u5BF9\u6BD4\u8BE5 issue \u7684\u539F\u59CB\u63CF\u8FF0\uFF0C\u5224\u65AD\u662F\u5426\u4ECD\u5B58\u5728\u3002\u82E5\u4FEE\u590D\u6210\u529F\uFF0C\u81EA\u52A8\u66F4\u65B0 pm_memory.json \u4E2D\u7684 issue \u72B6\u6001\u3002\u9002\u5408\u5728 Claude Code \u4FEE\u6539\u4EE3\u7801\u540E\u5FEB\u901F\u9A8C\u8BC1\u3002\u8FD4\u56DE JSON: {success, issue_id, fixed, confidence, note, remaining_concerns, auto_marked}\u3002\u793A\u4F8B\uFF1Apm_check({ issue_id: 'ISSUE-001' })",
        inputSchema: {
          type: "object",
          properties: {
            issue_id: { type: "string", description: "Required. \u8981\u9A8C\u8BC1\u7684 issue ID" },
            target: { type: "string", description: "\u53EF\u9009\u3002\u5F53\u524D\u9875\u9762\u540D\u79F0" },
            auto_mark_fixed: { type: "boolean", description: "\u9A8C\u8BC1\u901A\u8FC7\u540E\u662F\u5426\u81EA\u52A8\u6807\u8BB0\u4E3A fixed\uFF0C\u9ED8\u8BA4 true", default: true }
          },
          required: ["issue_id"]
        }
      },
      {
        name: "pm_get_memory",
        description: "\u67E5\u8BE2 PM \u7684\u9879\u76EE\u8BB0\u5FC6\u3002\u53EF\u83B7\u53D6\u5386\u53F2\u5BA1\u67E5\u8BB0\u5F55\u3001\u5F53\u524D open issues\u3001\u5DF2\u4FEE\u590D\u95EE\u9898\u3001\u8BBE\u8BA1\u89C4\u8303\u6458\u8981\u7B49\u3002\u9002\u5408\u5728\u5F00\u59CB\u65B0\u4EFB\u52A1\u524D\u4E86\u89E3\u9879\u76EE\u5F53\u524D\u72B6\u6001\u3002\u8FD4\u56DE JSON: {success, scope, ...}\u3002\u793A\u4F8B\uFF1Apm_get_memory({ scope: 'overview' })",
        inputSchema: {
          type: "object",
          properties: {
            scope: { type: "string", enum: ["overview", "open_issues", "fixed_issues", "design_specs", "last_review", "discussions"], description: "\u67E5\u8BE2\u8303\u56F4\uFF0C\u9ED8\u8BA4 overview", default: "overview" },
            channel: { type: "string", description: "\u53EF\u9009\u3002\u6309\u9891\u9053\u8FC7\u6EE4" }
          }
        }
      },
      {
        name: "dump_ui",
        description: "UI \u5C42\u7EA7\u7ED3\u6784\u5316\u5BFC\u51FA\uFF08JSON \u683C\u5F0F\uFF09\uFF0C\u542B type/text/resource-id/bounds/clickable \u7B49\u3002\u8FD4\u56DE JSON: {success, dump_path, node_count, nodes[]}\u3002\u8017\u65F6 fast (~800ms)\u3002\u793A\u4F8B\uFF1Adump_ui({})",
        inputSchema: {
          type: "object",
          properties: {
            savePath: { type: "string", description: "\u53EF\u9009\u3002\u4FDD\u5B58\u8DEF\u5F84" }
          }
        }
      }
    ]
  };
});
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;
  log(`tool call: ${name}`);
  try {
    switch (name) {
      // 基础交互
      case "screenshot":
        return handleScreenshot(args);
      case "screenshot_region":
        return handleScreenshotRegion(args);
      case "tap":
        return handleTap(args);
      case "swipe":
        return handleSwipe(args);
      case "input_text":
        return handleInputText(args);
      case "press_key":
        return handlePressKey(args);
      // UI 层级
      case "dump_hierarchy":
        return handleDumpHierarchy(args);
      case "find_element":
        return handleFindElement(args);
      case "wait_for_element":
        return handleWaitForElement(args);
      // 构建与部署
      case "build":
        return handleBuild(args);
      case "install_and_launch":
        return handleInstallAndLaunch(args);
      case "build_deploy":
        return handleBuildDeploy(args, "full_deploy");
      // UI 验证与分析
      case "verify_ui":
        return handleVerifyUI(args);
      case "analyze_screenshot":
        return handleAnalyzeScreenshot(args);
      case "compare_screenshots":
        return handleCompareScreenshots(args);
      case "vision_action":
        return handleVisionAction(args);
      // 日志与调试
      case "get_logs":
        return handleGetLogs(args);
      case "logcat_search":
        return handleLogcatSearch(args);
      case "parse_crash":
        return handleParseCrash(args);
      case "clear_logs":
        return handleClearLogs(args);
      // 设备管理
      case "list_devices":
        return handleDeviceManagement(args, "list_devices");
      case "device_info":
        return handleDeviceManagement(args, "device_info");
      case "shell_command":
        return handleDeviceManagement(args, "shell_command");
      // 应用管理
      case "list_apps":
        return handleAppManagement(args, "list_apps");
      case "app_info":
        return handleAppManagement(args, "app_info");
      case "uninstall_app":
        return handleAppManagement(args, "uninstall_app");
      case "clear_app_data":
        return handleAppManagement(args, "clear_app_data");
      case "stop_app":
        return handleAppManagement(args, "stop_app");
      // 性能监控
      case "performance_metrics":
        return handlePerformanceMonitor(args);
      case "measure_app_launch":
        return handleMeasureAppLaunch(args);
      case "record_screen":
        return handleDeviceManagement(args, "record_screen");
      // 设备控制
      case "set_orientation":
        return handleSetOrientation(args);
      case "set_gps":
        return handleSetGps(args);
      case "animation_scale":
        return handleAnimationScale(args);
      // 代码质量
      case "code_quality":
        return handleCodeQuality(args);
      case "run_tests":
        return handleBuildDeploy(args, "run_tests");
      // UI 自动化测试
      case "ui_test":
        return handleUITest(args, "run_test");
      case "regression_test":
        return handleUITest(args, "regression");
      // 项目报告
      case "project_report":
        return handleProjectReport(args);
      // 文件操作
      case "push_file":
        return handleFileOperations(args, "push");
      case "pull_file":
        return handleFileOperations(args, "pull");
      // 网络调试
      case "network_state":
        return handleNetworkDebug(args, "get_state");
      case "set_network":
        return handleNetworkDebug(args, "set_state");
      // APK 元数据
      case "apk_metadata":
        return handleApkMetadata(args);
      // 设计稿（视觉 LLM 转换）
      case "list_design_files":
        return handleListDesignFiles(args);
      case "extract_design_spec":
        return handleExtractDesignSpec(args);
      case "extract_design_tokens":
        return handleExtractDesignTokens(args);
      case "extract_design_components":
        return handleExtractComponents(args);
      case "design_to_compose":
        return handleDesignToCompose(args);
      // PM Agent 工具
      case "pm_review":
        return handlePmReview(args);
      case "pm_explore":
        return handlePmExplore(args);
      case "pm_compare_with_design":
        return handlePmCompareWithDesign(args);
      case "pm_mark_fixed":
        return handlePmMarkFixed(args);
      case "pm_discuss":
        return handlePmDiscuss(args);
      case "pm_check":
        return handlePmCheck(args);
      case "pm_get_memory":
        return handlePmGetMemory(args);
      case "dump_ui":
        return handleDumpUi(args);
      default:
        error(`Unknown tool: ${name}`);
        return {
          isError: true,
          content: [{ type: "text", text: `Unknown tool: ${name}` }]
        };
    }
  } catch (e) {
    const err = e;
    error(`Error executing tool ${name}:`, err);
    return {
      isError: true,
      content: [{ type: "text", text: `Error: ${err.message || "Unknown error"}` }]
    };
  }
});
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  log("AndroidDev-Assist MCP Server v3.1.0 running on stdio");
  log("Capabilities: 56 tools across 17 categories: screenshot/interaction/hierarchy/build/deploy/verify/vision/logs/device/apps/performance/control/quality/test/report/design/pm-agent");
  const shutdown = async (signal) => {
    log(`Received ${signal}, shutting down gracefully...`);
    try {
      await transport.close();
      log("Transport closed");
    } catch (e) {
      error("Error during shutdown:", e);
    }
    process.exit(0);
  };
  process.on("SIGINT", () => void shutdown("SIGINT"));
  process.on("SIGTERM", () => void shutdown("SIGTERM"));
  process.on("uncaughtException", (err) => {
    error("Uncaught exception:", err);
    process.exit(1);
  });
  process.on("unhandledRejection", (reason) => {
    error("Unhandled rejection:", reason);
  });
}
main().catch((err) => {
  error("Fatal error:", err);
  process.exit(1);
});
