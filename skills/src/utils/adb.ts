import { exec as execCb, spawn as spawnCb } from "node:child_process";
import { promisify } from "node:util";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { log, error } from "./logger.js";

const execAsync = promisify(execCb);
const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || "./screenshots";

// 设备可用性缓存 + 时间戳，避免断连后无法感知
let deviceChecked = false;
let deviceAvailable = false;
let lastCheckAt = 0;
const DEVICE_CHECK_TTL_MS = 10_000; // 10s 后自动重新检查

function invalidateDeviceCheck() {
  deviceChecked = false;
  deviceAvailable = false;
  lastCheckAt = 0;
}

export async function checkDevice(force = false): Promise<{ available: boolean; message: string }> {
  const now = Date.now();
  if (!force && deviceChecked && now - lastCheckAt < DEVICE_CHECK_TTL_MS) {
    return {
      available: deviceAvailable,
      message: deviceAvailable ? "Device ready" : "No device available",
    };
  }

  try {
    const { stdout } = await execAsync("adb devices", { timeout: 5000 });
    const lines = stdout.trim().split("\n").slice(1);
    const devices = lines
      .map((line) => line.trim())
      .filter((line) => line.length > 0 && line.includes("device"))
      .map((line) => line.split("\t")[0]);

    if (devices.length === 0) {
      deviceAvailable = false;
      deviceChecked = true;
      lastCheckAt = now;
      return { available: false, message: "No Android device connected. Please connect a device or start an emulator." };
    }

    const readyDevice = lines.find((line) => line.includes("\tdevice"));
    if (!readyDevice) {
      deviceAvailable = false;
      deviceChecked = true;
      lastCheckAt = now;
      return { available: false, message: "Device found but not ready (may be unauthorized or offline)." };
    }

    deviceAvailable = true;
    deviceChecked = true;
    lastCheckAt = now;
    log(`Device ready: ${readyDevice.split("\t")[0]}`);
    return { available: true, message: `Device ready: ${readyDevice.split("\t")[0]}` };
  } catch (e) {
    deviceAvailable = false;
    deviceChecked = true;
    lastCheckAt = now;
    return { available: false, message: `ADB check failed: ${e}` };
  }
}

export function resetDeviceCheck(): void {
  invalidateDeviceCheck();
}

export async function adbExec(args: string): Promise<string> {
  const deviceStatus = await checkDevice();
  if (!deviceStatus.available) {
    throw new Error(deviceStatus.message);
  }

  const cmd = `adb ${args}`;
  log(`exec: ${cmd}`);
  const { stdout, stderr } = await execAsync(cmd, { timeout: 30000 });
  if (stderr) log(`stderr: ${stderr}`);
  return stdout.trim();
}

/**
 * 截屏优化：单次 ADB 往返，screencap 输出直接 stream 到本地文件，
 * 避免「写到 /sdcard → pull 回来」两次往返（典型提速 40-60%）。
 */
export async function screenshot(savePath?: string): Promise<{ path: string; timestamp: number }> {
  const deviceStatus = await checkDevice();
  if (!deviceStatus.available) throw new Error(deviceStatus.message);

  const timestamp = Date.now();
  const filename = `screenshot_${timestamp}.png`;
  const localPath = savePath || `${SCREENSHOT_DIR}/${filename}`;

  try { mkdirSync(dirname(localPath) || ".", { recursive: true }); } catch { /* exists */ }

  await new Promise<void>((resolve, reject) => {
    const child = spawnCb("adb", ["exec-out", "screencap", "-p"]);
    const chunks: Buffer[] = [];
    let settled = false;

    const finish = (err?: Error) => {
      if (settled) return;
      settled = true;
      if (err) return reject(err);
      try {
        writeFileSync(localPath, Buffer.concat(chunks));
        resolve();
      } catch (e) { reject(e as Error); }
    };

    child.stdout?.on("data", (c: Buffer) => chunks.push(c));
    child.stderr?.on("data", (c: Buffer) => log(`screencap stderr: ${c.toString().slice(0, 200)}`));
    child.on("error", finish);
    child.on("close", (code: number | null) => code === 0 || code === null ? finish() : finish(new Error(`screencap exited ${code}`)));

    setTimeout(() => finish(new Error("screenshot timeout (15s)")), 15_000);
  });

  log(`screenshot saved to ${localPath}`);
  return { path: localPath, timestamp };
}

export async function getDeviceState(): Promise<string> {
  return adbExec("get-state");
}

export async function tap(x: number, y: number): Promise<void> {
  await adbExec(`shell input tap ${x} ${y}`);
}

export async function swipe(x1: number, y1: number, x2: number, y2: number, duration = 300): Promise<void> {
  await adbExec(`shell input swipe ${x1} ${y1} ${x2} ${y2} ${duration}`);
}

export async function inputText(text: string): Promise<void> {
  const escaped = text.replace(/ /g, "%s");
  await adbExec(`shell input text "${escaped}"`);
}

export async function pressKey(key: string): Promise<void> {
  const keyMap: Record<string, number> = {
    HOME: 3,
    BACK: 4,
    ENTER: 66,
    MENU: 82,
    POWER: 26,
    VOLUME_UP: 24,
    VOLUME_DOWN: 25,
    DEL: 67,
  };
  const code = keyMap[key.toUpperCase()] ?? parseInt(key);
  await adbExec(`shell input keyevent ${code}`);
}

export async function launchApp(packageName: string, activity?: string): Promise<void> {
  const component = activity ? `${packageName}/${activity}` : packageName;
  await adbExec(`shell am start -n ${component}`);
}

// 监听设备断开（按需）—— 暴露给需要感知断连的调用方
export function onDeviceDisconnect(handler: () => void): void {
  const watcher = spawnCb("adb", ["track-devices"]);
  let buffer = "";
  watcher.stdout?.on("data", (c: Buffer) => {
    buffer += c.toString();
    if (buffer.includes("\n") && /offline|disconnected/.test(buffer)) {
      invalidateDeviceCheck();
      handler();
      buffer = "";
    }
  });
}
