import { exec as execCb } from "node:child_process";
import { promisify } from "node:util";
import { log, error } from "./logger.js";

const execAsync = promisify(execCb);
const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || "./screenshots";

let deviceChecked = false;
let deviceAvailable = false;

/**
 * Check if an Android device is connected and ready.
 * Caches the result for subsequent calls.
 */
export async function checkDevice(): Promise<{ available: boolean; message: string }> {
  if (deviceChecked) {
    return {
      available: deviceAvailable,
      message: deviceAvailable ? "Device ready" : "No device available",
    };
  }

  try {
    const { stdout } = await execAsync("adb devices", { timeout: 10000 });
    const lines = stdout.trim().split("\n").slice(1); // Skip "List of devices attached"
    const devices = lines
      .map((line) => line.trim())
      .filter((line) => line.length > 0 && line.includes("device"))
      .map((line) => line.split("\t")[0]);

    if (devices.length === 0) {
      deviceAvailable = false;
      deviceChecked = true;
      return { available: false, message: "No Android device connected. Please connect a device or start an emulator." };
    }

    // Check if device is in "device" state (not unauthorized or offline)
    const readyDevice = lines.find((line) => line.includes("\tdevice"));
    if (!readyDevice) {
      deviceAvailable = false;
      deviceChecked = true;
      return { available: false, message: "Device found but not ready (may be unauthorized or offline)." };
    }

    deviceAvailable = true;
    deviceChecked = true;
    log(`Device ready: ${readyDevice.split("\t")[0]}`);
    return { available: true, message: `Device ready: ${readyDevice.split("\t")[0]}` };
  } catch (e) {
    deviceAvailable = false;
    deviceChecked = true;
    return { available: false, message: `ADB check failed: ${e}` };
  }
}

/**
 * Reset device check cache (useful after device reconnect).
 */
export function resetDeviceCheck(): void {
  deviceChecked = false;
  deviceAvailable = false;
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

export async function screenshot(savePath?: string): Promise<{ path: string; timestamp: number }> {
  const timestamp = Date.now();
  const filename = `screenshot_${timestamp}.png`;
  const localPath = savePath || `${SCREENSHOT_DIR}/${filename}`;

  await adbExec("shell screencap -p /sdcard/screen.png");
  await adbExec(`pull /sdcard/screen.png "${localPath}"`);

  log(`screenshot saved to ${localPath}`);
  return { path: localPath, timestamp };
}

export async function getDeviceState(): Promise<string> {
  return adbExec("get-state");
}

export async function listDevices(): Promise<string> {
  return adbExec("devices");
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

export async function installApk(apkPath: string): Promise<void> {
  await adbExec(`install -r "${apkPath}"`);
}

export async function launchApp(packageName: string, activity?: string): Promise<void> {
  const component = activity ? `${packageName}/${activity}` : packageName;
  await adbExec(`shell am start -n ${component}`);
}
