import { tap, swipe, inputText, pressKey, installApk, launchApp } from "../utils/adb.js";
import { log } from "../utils/logger.js";
import { spawnCommand } from "../utils/exec.js";

export async function handleTap(args: Record<string, unknown>) {
  const { x, y } = args as { x: number; y: number };
  await tap(x, y);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "tap", x, y }) }],
  };
}

export async function handleSwipe(args: Record<string, unknown>) {
  const { x1, y1, x2, y2, duration = 300 } = args as {
    x1: number; y1: number; x2: number; y2: number; duration?: number;
  };
  await swipe(x1, y1, x2, y2, duration as number);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "swipe" }) }],
  };
}

export async function handleInputText(args: Record<string, unknown>) {
  const { text } = args as { text: string };
  await inputText(text);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "input_text", text }) }],
  };
}

export async function handlePressKey(args: Record<string, unknown>) {
  const { key } = args as { key: string };
  await pressKey(key);
  return {
    isError: false,
    content: [{ type: "text", text: JSON.stringify({ success: true, action: "press_key", key }) }],
  };
}

export async function handleBuild(args: Record<string, unknown>) {
  const { projectPath = ".", variant = "debug" } = args as {
    projectPath?: string; variant?: string;
  };
  const start = Date.now();
  const gradlew = variant === "release" ? "assembleRelease" : "assembleDebug";

  log(`build: ./gradlew ${gradlew} in ${projectPath}`);

  try {
    const { stdout, stderr, exitCode } = await spawnCommand("./gradlew", [gradlew], {
      cwd: projectPath,
      timeout: 180000, // 3 minutes max
    });

    if (exitCode !== 0) {
      return {
        isError: true,
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: `Gradle exited with code ${exitCode}`,
            stderr: stderr.slice(0, 2000),
            buildTime: Date.now() - start,
          }),
        }],
      };
    }

    const combinedOutput = stdout + stderr;
    const apkPath = combinedOutput.match(/outputs\/apk\/[^\s]+\.apk/)?.[0] || "";
    return {
      content: [{
        type: "text",
        text: JSON.stringify({ success: true, apkPath, buildTime: Date.now() - start, variant }),
      }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({ success: false, error: err.message || "build failed", buildTime: Date.now() - start }),
      }],
    };
  }
}

export async function handleInstallAndLaunch(args: Record<string, unknown>) {
  const { apkPath, packageName, activity } = args as {
    apkPath?: string; packageName: string; activity?: string;
  };

  if (apkPath) {
    await installApk(apkPath);
  }
  await launchApp(packageName, activity);

  return {
    content: [{
      type: "text",
      text: JSON.stringify({ success: true, action: "install_and_launch", packageName }),
    }],
  };
}
