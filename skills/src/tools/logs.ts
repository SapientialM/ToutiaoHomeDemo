import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout } from "../utils/exec.js";

export async function handleGetLogs(args: Record<string, unknown>) {
  try {
    const packageName = args.packageName as string | undefined;
    const filter = (args.filter as string) ?? "crash";
    const lines = (args.lines as number) ?? 50;

    let cmd: string;
    if (filter === "all" && packageName) {
      cmd = `adb logcat -d --pid=$(adb shell pidof ${packageName} 2>/dev/null) 2>/dev/null | tail -${lines}`;
    } else if (filter === "all") {
      cmd = `adb logcat -d | tail -${lines}`;
    } else if (packageName) {
      cmd = `adb logcat -d | grep -i "crash\\|fatal\\|exception\\|${packageName}" | tail -${lines}`;
    } else {
      cmd = `adb logcat -d | grep -i "crash\\|fatal\\|exception\\|AndroidRuntime" | tail -${lines}`;
    }

    log(`get_logs: ${cmd}`);
    const { stdout: output } = await execAsyncWithTimeout(cmd, { timeout: 10000 });

    let appRunning: boolean | null = null;
    let pid: string | null = null;
    if (packageName) {
      try {
        const { stdout } = await execAsyncWithTimeout(`adb shell pidof ${packageName}`, { timeout: 5000 });
        pid = stdout.trim() || null;
        appRunning = !!pid;
      } catch { appRunning = false; }
    }

    const logLines = output.trim() ? output.trim().split("\n") : [];

    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        filter,
        lines: logLines.length,
        appRunning,
        pid,
        logs: logLines,
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("get_logs failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
