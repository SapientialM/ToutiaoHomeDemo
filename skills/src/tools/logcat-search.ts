import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout } from "../utils/exec.js";
import { safePidof } from "./logs.js";

/**
 * logcat 关键词/正则搜索
 * 比 get_logs 更强大：支持正则、按包名、按 tag、按严重度、按行数限制
 */
export async function handleLogcatSearch(args: Record<string, unknown>) {
  try {
    const pattern = args.pattern as string | undefined;
    const packageName = args.packageName as string | undefined;
    const tag = args.tag as string | undefined;
    const level = (args.level as string) ?? "I"; // V/D/I/W/E
    const maxLines = (args.maxLines as number) ?? 200;
    const serial = args.serial as string | undefined;

    if (!pattern && !tag) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "At least one of pattern (regex) or tag is required",
      }) }] };
    }

    const serialFlag = serial ? `-s ${serial} ` : "";
    // 构建 logcat tag/level 过滤
    const tagFilter = tag ? `-s ${tag}:${level}` : `-s *:${level}`;
    // 行数限制 + 可选包名 pid 过滤
    const pid = packageName ? await safePidof(packageName) : null;
    const pidFlag = pid ? `--pid=${pid}` : "";
    // pattern 是正则，调用方负责转义
    const grep = pattern ? ` | grep -E ${shellQuote(pattern)}` : "";

    const cmd = `adb ${serialFlag}logcat -d ${tagFilter} -t ${maxLines} ${pidFlag}${grep}`.replace(/\s+/g, " ");
    log(`logcat_search: ${cmd}`);

    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 8000 });
    const lines = stdout.trim() ? stdout.trim().split("\n") : [];

    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        matched: lines.length,
        pattern: pattern || null,
        tag: tag || null,
        level,
        appRunning: pid ? true : (packageName ? false : null),
        lines: lines.slice(0, maxLines),
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("logcat_search failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 从 logcat 提取并解析崩溃 / ANR 堆栈
 * 用例：用户说"看看刚才是不是崩了" / "为什么启动失败"
 */
export async function handleParseCrash(args: Record<string, unknown>) {
  try {
    const packageName = args.packageName as string | undefined;
    const lookbackSec = (args.lookbackSec as number) ?? 300;
    const serial = args.serial as string | undefined;

    const serialFlag = serial ? `-s ${serial} ` : "";
    const pkgFilter = packageName ? ` | grep -i ${shellQuote(packageName)}` : "";

    // 拉取 E 级别 + ANR 痕迹（AndroidRuntime、ActivityManager ANR、DEBUG）
    const cmd = `adb ${serialFlag}logcat -d -s AndroidRuntime:E ActivityManager:E DEBUG:E System.err:W *:S -t 2000${pkgFilter}`;
    log(`parse_crash: ${cmd}`);

    const { stdout } = await execAsyncWithTimeout(cmd, { timeout: 10000 });
    const raw = stdout.trim();

    if (!raw) {
      return {
        content: [{ type: "text", text: JSON.stringify({
          success: true,
          crashes: 0,
          hint: "No crash/ANR records found. Try lookbackSec larger or check if filter is too strict.",
        }) }],
      };
    }

    const groups = groupCrashes(raw);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        crashCount: groups.length,
        crashes: groups,
        rawLineCount: raw.split("\n").length,
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("parse_crash failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// ── 内部工具 ──────────────────────────────────────

function shellQuote(s: string): string {
  // 给单引号字符串加边界，防止空格 / 单引号破坏 shell
  return `'${s.replace(/'/g, "'\\''")}'`;
}

interface CrashGroup {
  type: "java_exception" | "anr" | "native_crash" | "fatal";
  timestamp?: string;
  process?: string;
  exception?: string;
  message?: string;
  stack: string[];
  raw: string;
}

/**
 * 把 logcat 输出按崩溃事件分组
 * - Java 异常: 以 "FATAL EXCEPTION" 开头到下一条事件
 * - ANR: "ANR in <process>" 块
 * - Native: "DEBUG/tombstoned" 或 "signal 6/11"
 */
function groupCrashes(raw: string): CrashGroup[] {
  const lines = raw.split("\n");
  const groups: CrashGroup[] = [];
  let current: CrashGroup | null = null;

  const push = () => {
    if (current && current.stack.length > 0) groups.push(current);
    current = null;
  };

  for (const line of lines) {
    // Java 异常起始
    const fatal = line.match(/^(\S+\s+\S+).*FATAL EXCEPTION.*:?\s*(.*)$/);
    if (fatal) {
      push();
      current = {
        type: "java_exception",
        timestamp: fatal[1],
        exception: "FATAL",
        message: fatal[2] || undefined,
        stack: [line],
        raw: line,
      };
      continue;
    }

    // ANR 起始
    const anr = line.match(/^(\S+\s+\S+).*ANR in (\S+)/);
    if (anr) {
      push();
      current = {
        type: "anr",
        timestamp: anr[1],
        process: anr[2],
        message: "ANR detected",
        stack: [line],
        raw: line,
      };
      continue;
    }

    // Native crash (signal/tombstone)
    const native = line.match(/^(\S+\s+\S+).*signal\s+(\d+)\s+\(SIG(SEGV|ABRT|ILL|FPE|BUS)\)/);
    if (native) {
      push();
      current = {
        type: "native_crash",
        timestamp: native[1],
        message: native[0].split(":").slice(1).join(":").trim(),
        stack: [line],
        raw: line,
      };
      continue;
    }

    if (current) {
      current.stack.push(line);
      current.raw += "\n" + line;
    } else {
      // 未识别的 fatal 行：单行事件
      if (/(FATAL|AndroidRuntime|DEBUG\s|tombstone|signal\s\d+)/i.test(line)) {
        current = {
          type: "fatal",
          stack: [line],
          raw: line,
        };
      }
    }

    // 块结束（下一个时间戳开头或空行）
    if (current && /^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+[A-Z]/.test(line) && current.stack.length > 1 && line !== current.stack[0]) {
      // 下一条事件开始，推入当前
      push();
    }
  }
  push();

  return groups.map((g) => ({
    ...g,
    // 截取前 30 行 stack 防止输出爆炸
    stack: g.stack.slice(0, 30),
  }));
}
