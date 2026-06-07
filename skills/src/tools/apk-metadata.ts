import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout } from "../utils/exec.js";

/**
 * APK 元数据查询：包名/版本/权限/Activity/签名（用 aapt / aapt2 / apkanalyzer 任一可用）
 * 用例：用户说"看看这个 APK 是什么版本" / "它申请了哪些权限"
 */
export async function handleApkMetadata(args: Record<string, unknown>) {
  try {
    const apkPath = args.apkPath as string;
    if (!apkPath) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "apkPath is required",
      }) }] };
    }

    // 优先 aapt2 → aapt → apkanalyzer
    const tool = await detectApkTool();
    if (!tool) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "No APK introspection tool found. Set AAPT2_PATH / AAPT_PATH env or install via Android SDK build-tools.",
        hint: "Android SDK build-tools 30+ ships with aapt2. Or use apkanalyzer from cmdline-tools.",
      }) }] };
    }

    const { stdout, stderr } = await execAsyncWithTimeout(
      `${tool} dump badging "${apkPath}"`,
      { timeout: 20000 }
    );

    const meta = parseBadging(stdout);
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        tool,
        ...meta,
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("apk_metadata failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

async function detectApkTool(): Promise<string | null> {
  const candidates = [
    process.env.AAPT2_PATH,
    process.env.AAPT_PATH,
    "aapt2",
    "aapt",
  ].filter(Boolean) as string[];

  for (const c of candidates) {
    try {
      await execAsyncWithTimeout(`${c} version`, { timeout: 2000 });
      return c;
    } catch { /* try next */ }
  }
  return null;
}

function parseBadging(text: string) {
  const get = (key: string): string | null => {
    const m = text.match(new RegExp(`^${key}:\\s*'?([^'\\n]+)'?`, "m"));
    return m ? m[1].trim() : null;
  };
  const all = (key: string): string[] => {
    const re = new RegExp(`^${key}:\\s*'?([^'\\n]+)'?`, "gm");
    const out: string[] = [];
    let m: RegExpExecArray | null;
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
    densities: all("densities"),
  };
}
