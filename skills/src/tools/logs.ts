import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout } from "../utils/exec.js";
import { clearLogs } from "../utils/adb-enhanced.js";

const DEFAULT_LINES = 50;
const CRASH_TAGS = ["AndroidRuntime:E", "ActivityManager:E", "DEBUG:E", "System.err:W", "*:S"];

export async function safePidof(packageName: string): Promise<string | null> {
  try {
    const { stdout } = await execAsyncWithTimeout(`adb shell pidof -s ${packageName}`, { timeout: 3000 });
    const pid = stdout.trim();
    return pid || null;
  } catch {
    return null;
  }
}

export async function handleGetLogs(args: Record<string, unknown>) {
  try {
    const packageName = args.packageName as string | undefined;
    const filter = (args.filter as string) ?? "crash";
    const lines = (args.lines as number) ?? DEFAULT_LINES;
    const serial = args.serial as string | undefined;

    const serialFlag = serial ? `-s ${serial}` : "";
    let cmd: string;
    let mode: string;

    if (filter === "all" && packageName) {
      const pid = await safePidof(packageName);
      if (pid) {
        cmd = `adb ${serialFlag} logcat -d --pid=${pid} -t ${lines}`;
        mode = `all+pid(${pid})`;
      } else {
        cmd = `adb ${serialFlag} logcat -d -t ${lines}`;
        mode = "all (app not running, showing global recent)";
      }
    } else if (filter === "all") {
      cmd = `adb ${serialFlag} logcat -d -t ${lines}`;
      mode = "all";
    } else {
      // crash: use native tag filter to avoid dumping entire buffer
      cmd = `adb ${serialFlag} logcat -d -s ${CRASH_TAGS.join(" ")} -t ${lines}`;
      mode = "crash";
    }

    log(`get_logs: ${cmd}`);
    const { stdout: output, stderr } = await execAsyncWithTimeout(cmd, { timeout: 8000 });

    const logLines = output.trim() ? output.trim().split("\n") : [];

    let appRunning: boolean | null = null;
    let pid: string | null = null;
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
        stderr: stderr || undefined,
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("get_logs failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

export async function handleClearLogs(args: Record<string, unknown>) {
  try {
    const serial = args.serial as string | undefined;
    const result = await clearLogs(serial);
    return {
      content: [{ type: "text", text: JSON.stringify({ success: result.success, message: result.message }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("clear_logs failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
