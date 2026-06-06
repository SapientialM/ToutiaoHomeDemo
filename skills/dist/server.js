// src/server.ts
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";

// src/utils/adb.ts
import { exec as execCb } from "child_process";
import { promisify } from "util";

// src/utils/logger.ts
var LOG_PREFIX = "[android-dev-assist]";
function log(...args) {
  console.error(LOG_PREFIX, ...args);
}
function error(...args) {
  console.error(LOG_PREFIX, "[ERROR]", ...args);
}

// src/utils/adb.ts
var execAsync = promisify(execCb);
var SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || "./screenshots";
var deviceChecked = false;
var deviceAvailable = false;
async function checkDevice() {
  if (deviceChecked) {
    return {
      available: deviceAvailable,
      message: deviceAvailable ? "Device ready" : "No device available"
    };
  }
  try {
    const { stdout } = await execAsync("adb devices", { timeout: 1e4 });
    const lines = stdout.trim().split("\n").slice(1);
    const devices = lines.map((line) => line.trim()).filter((line) => line.length > 0 && line.includes("device")).map((line) => line.split("	")[0]);
    if (devices.length === 0) {
      deviceAvailable = false;
      deviceChecked = true;
      return { available: false, message: "No Android device connected. Please connect a device or start an emulator." };
    }
    const readyDevice = lines.find((line) => line.includes("	device"));
    if (!readyDevice) {
      deviceAvailable = false;
      deviceChecked = true;
      return { available: false, message: "Device found but not ready (may be unauthorized or offline)." };
    }
    deviceAvailable = true;
    deviceChecked = true;
    log(`Device ready: ${readyDevice.split("	")[0]}`);
    return { available: true, message: `Device ready: ${readyDevice.split("	")[0]}` };
  } catch (e) {
    deviceAvailable = false;
    deviceChecked = true;
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
  const timestamp = Date.now();
  const filename = `screenshot_${timestamp}.png`;
  const localPath = savePath || `${SCREENSHOT_DIR}/${filename}`;
  await adbExec("shell screencap -p /sdcard/screen.png");
  await adbExec(`pull /sdcard/screen.png "${localPath}"`);
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
async function installApk(apkPath) {
  await adbExec(`install -r "${apkPath}"`);
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

// src/utils/exec.ts
import { exec as execCb2 } from "child_process";
import { promisify as promisify2 } from "util";
import { existsSync } from "fs";
var execAsync2 = promisify2(execCb2);
async function fileExists(path3) {
  return existsSync(path3);
}
async function execAsyncWithTimeout(command, options = {}) {
  const timeout = options.timeout || 3e4;
  log(`exec: ${command} (timeout: ${timeout}ms)`);
  try {
    const result = await execAsync2(command, {
      cwd: options.cwd,
      timeout,
      maxBuffer: 1024 * 1024
      // 1MB buffer
    });
    return {
      stdout: result.stdout || "",
      stderr: result.stderr || ""
    };
  } catch (e) {
    const err = e;
    if (err.killed) {
      throw new Error(`Command timed out after ${timeout}ms: ${command}`);
    }
    return {
      stdout: err.stdout || "",
      stderr: err.stderr || err.message || ""
    };
  }
}
async function spawnCommand(command, args, options = {}) {
  const { spawn } = await import("child_process");
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      shell: true
    });
    let stdout = "";
    let stderr = "";
    let timedOut = false;
    const timeout = setTimeout(() => {
      timedOut = true;
      child.kill("SIGTERM");
      reject(new Error(`Command timed out after ${options.timeout || 3e4}ms`));
    }, options.timeout || 3e4);
    child.stdout?.on("data", (data) => {
      stdout += data.toString();
    });
    child.stderr?.on("data", (data) => {
      stderr += data.toString();
    });
    child.on("close", (code) => {
      clearTimeout(timeout);
      if (!timedOut) {
        resolve({
          stdout,
          stderr,
          exitCode: code || 0
        });
      }
    });
    child.on("error", (err) => {
      clearTimeout(timeout);
      if (!timedOut) {
        reject(err);
      }
    });
  });
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
import { readFileSync, writeFileSync, mkdirSync } from "fs";
function ensureDir(dir) {
  mkdirSync(dir, { recursive: true });
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
    const sharp = (await import("sharp")).default;
    const pixelmatch = (await import("pixelmatch")).default;
    const { PNG } = await import("pngjs");
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
    writeFileSync(diffPath, PNG.sync.write(diff));
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
    const sharp = (await import("sharp")).default;
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
async function handleGetLogs(args) {
  try {
    const packageName = args.packageName;
    const filter = args.filter ?? "crash";
    const lines = args.lines ?? 50;
    let cmd;
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
    const { stdout: output } = await execAsyncWithTimeout(cmd, { timeout: 1e4 });
    let appRunning = null;
    let pid = null;
    if (packageName) {
      try {
        const { stdout } = await execAsyncWithTimeout(`adb shell pidof ${packageName}`, { timeout: 5e3 });
        pid = stdout.trim() || null;
        appRunning = !!pid;
      } catch {
        appRunning = false;
      }
    }
    const logLines = output.trim() ? output.trim().split("\n") : [];
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        filter,
        lines: logLines.length,
        appRunning,
        pid,
        logs: logLines
      }) }]
    };
  } catch (e) {
    const err = e;
    error("get_logs failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

// src/tools/analyze.ts
import path2 from "path";
import { fileURLToPath } from "url";

// src/tools/vision-analyze.ts
import OpenAI from "openai";
import fs2 from "fs";
import path from "path";
var client = null;
function getClient() {
  if (!client) {
    const apiKey = process.env.MOONSHOT_API_KEY;
    if (!apiKey) {
      throw new Error("MOONSHOT_API_KEY not set in environment");
    }
    client = new OpenAI({
      apiKey,
      baseURL: "https://api.moonshot.cn/v1"
    });
  }
  return client;
}
async function resizeForApiAsync(imagePath) {
  const resizedPath = imagePath.replace(/\.(png|jpg|jpeg)$/, "_resized.$1");
  try {
    const cmd = `python3 -c "
from PIL import Image
img = Image.open('${imagePath}')
w, h = img.size
if max(w, h) > 320:
    ratio = 320 / max(w, h)
    img = img.resize((int(w*ratio), int(h*ratio)), Image.LANCZOS)
    img.save('${resizedPath}')
"`;
    await execAsyncWithTimeout(cmd, { timeout: 1e4 });
    return fs2.existsSync(resizedPath) ? resizedPath : imagePath;
  } catch {
    return imagePath;
  }
}
function cleanupResizedFile(resizedPath, originalPath) {
  if (resizedPath !== originalPath) {
    try {
      fs2.unlinkSync(resizedPath);
    } catch {
    }
  }
}
async function analyzeWithVision(imagePath, prompt, systemPrompt) {
  if (!fs2.existsSync(imagePath)) {
    throw new Error(`File not found: ${imagePath}`);
  }
  const resizedPath = await resizeForApiAsync(imagePath);
  let readPath;
  try {
    readPath = fs2.existsSync(resizedPath) ? resizedPath : imagePath;
    const imageBuffer = fs2.readFileSync(readPath);
    const base64Image = imageBuffer.toString("base64");
    const ext = path.extname(readPath).slice(1) || "png";
    const mimeType = ext === "jpg" || ext === "jpeg" ? "image/jpeg" : "image/png";
    const imageUrl = `data:${mimeType};base64,${base64Image}`;
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
    const response = await getClient().chat.completions.create(
      {
        model: "kimi-k2.6",
        messages: [
          {
            role: "system",
            content: systemPrompt || "You are an Android UI/UX expert. Always give specific, actionable feedback with exact measurements and Compose code suggestions."
          },
          {
            role: "user",
            content: [
              { type: "image_url", image_url: { url: imageUrl } },
              { type: "text", text: prompt || defaultPrompt }
            ]
          }
        ],
        max_tokens: 4096,
        temperature: 1
      },
      { timeout: 12e4 }
    );
    const msg = response.choices[0]?.message;
    const content = msg?.content;
    if (!content) {
      throw new Error("Vision model returned empty response");
    }
    return content;
  } finally {
    cleanupResizedFile(resizedPath, imagePath);
  }
}
async function compareWithVision(baselinePath, currentPath, prompt) {
  if (!fs2.existsSync(baselinePath)) {
    throw new Error(`Baseline not found: ${baselinePath}`);
  }
  if (!fs2.existsSync(currentPath)) {
    throw new Error(`Current not found: ${currentPath}`);
  }
  const encode = (p) => {
    const readPath = fs2.existsSync(p) ? p : p;
    const buf = fs2.readFileSync(readPath);
    const ext = path.extname(readPath).slice(1) || "png";
    const mime = ext === "jpg" || ext === "jpeg" ? "image/jpeg" : "image/png";
    return `data:${mime};base64,${buf.toString("base64")}`;
  };
  const defaultPrompt = `Compare these two Android screenshots. The first is the design/baseline, the second is the current implementation.

Focus on:
1. Layout differences (position, spacing, sizing)
2. Color differences (especially the header red and bottom nav)
3. Content differences (more/less cards, different text)
4. Specific issues introduced in the current version

For each difference, state whether it's acceptable or needs fixing, and suggest exact Compose code changes.`;
  const response = await getClient().chat.completions.create(
    {
      model: "kimi-k2.6",
      messages: [
        {
          role: "system",
          content: "You are an Android UI testing expert. Compare screenshots precisely and give actionable feedback."
        },
        {
          role: "user",
          content: [
            { type: "text", text: "Here is the baseline/design screenshot:" },
            { type: "image_url", image_url: { url: encode(baselinePath) } },
            { type: "text", text: "Here is the current implementation screenshot:" },
            { type: "image_url", image_url: { url: encode(currentPath) } },
            { type: "text", text: prompt || defaultPrompt }
          ]
        }
      ],
      max_tokens: 4096,
      temperature: 1
    },
    { timeout: 12e4 }
  );
  const msg = response.choices[0]?.message;
  return msg?.content || "Comparison failed";
}

// src/tools/analyze.ts
import { execSync } from "child_process";
function getPythonScriptPath() {
  const currentDir = path2.dirname(fileURLToPath(import.meta.url));
  const candidates = [
    path2.resolve(currentDir, "..", "src", "tools", "analyze_image.py"),
    path2.resolve(currentDir, "..", "..", "src", "tools", "analyze_image.py"),
    path2.resolve(process.cwd(), "src", "tools", "analyze_image.py"),
    path2.resolve(process.cwd(), "skills", "src", "tools", "analyze_image.py")
  ];
  for (const c of candidates) {
    try {
      execSync(`test -f "${c}"`, { timeout: 1e3 });
      return c;
    } catch {
      continue;
    }
  }
  return path2.resolve(currentDir, "..", "src", "tools", "analyze_image.py");
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
    for (const device of devices) {
      try {
        const { stdout: version } = await execAsyncWithTimeout(
          `adb -s ${device.serial} shell getprop ro.build.version.release`,
          { timeout: 5e3 }
        );
        device.androidVersion = version.trim();
        const { stdout: sdk } = await execAsyncWithTimeout(
          `adb -s ${device.serial} shell getprop ro.build.version.sdk`,
          { timeout: 5e3 }
        );
        device.sdkVersion = sdk.trim();
        const { stdout: resolution } = await execAsyncWithTimeout(
          `adb -s ${device.serial} shell wm size`,
          { timeout: 5e3 }
        );
        const resMatch = resolution.match(/(\d+x\d+)/);
        device.screenResolution = resMatch ? resMatch[1] : void 0;
        const { stdout: density } = await execAsyncWithTimeout(
          `adb -s ${device.serial} shell wm density`,
          { timeout: 5e3 }
        );
        const densityMatch = density.match(/(\d+)dpi/);
        device.density = densityMatch ? densityMatch[1] : void 0;
      } catch (e) {
        error(`Failed to get device info for ${device.serial}:`, e);
      }
    }
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
async function installApk2(apkPath, serial, options = {}) {
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
        try {
          const { stdout: info } = await execAsyncWithTimeout(
            serial ? `adb -s ${serial} shell dumpsys package ${packageName} | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime|dataDir"` : `adb shell dumpsys package ${packageName} | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime|dataDir"`,
            { timeout: 5e3 }
          );
          const versionNameMatch = info.match(/versionName=([^\s]+)/);
          const versionCodeMatch = info.match(/versionCode=(\d+)/);
          const firstInstallMatch = info.match(/firstInstallTime=([^\s]+)/);
          const lastUpdateMatch = info.match(/lastUpdateTime=([^\s]+)/);
          const dataDirMatch = info.match(/dataDir=([^\s]+)/);
          apps.push({
            packageName,
            versionName: versionNameMatch ? versionNameMatch[1] : "unknown",
            versionCode: versionCodeMatch ? versionCodeMatch[1] : "unknown",
            firstInstallTime: firstInstallMatch ? firstInstallMatch[1] : "unknown",
            lastUpdateTime: lastUpdateMatch ? lastUpdateMatch[1] : "unknown",
            dataDir: dataDirMatch ? dataDirMatch[1] : "unknown"
          });
        } catch {
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
    const cmd = serial ? `adb -s ${serial} shell` : "adb shell";
    const { stdout: wifi } = await execAsyncWithTimeout(
      `${cmd} "settings get global wifi_on"`,
      { timeout: 5e3 }
    );
    const { stdout: mobile } = await execAsyncWithTimeout(
      `${cmd} "settings get global mobile_data"`,
      { timeout: 5e3 }
    );
    const { stdout: airplane } = await execAsyncWithTimeout(
      `${cmd} "settings get global airplane_mode_on"`,
      { timeout: 5e3 }
    );
    return {
      wifi: wifi.trim() === "1",
      mobile: mobile.trim() === "1",
      airplaneMode: airplane.trim() === "1"
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
    await execAsyncWithTimeout(
      'adb shell "uiautomator dump /sdcard/window_dump.xml"',
      { timeout: 1e4 }
    );
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "cat /sdcard/window_dump.xml"',
      { timeout: 1e4 }
    );
    const elements = [];
    const nodeRegex = /<node[^>]*\/>/g;
    let match;
    while ((match = nodeRegex.exec(stdout)) !== null) {
      const node = match[0];
      const boundsMatch = node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
      const textMatch = node.match(/text="([^"]*)"/);
      const classMatch = node.match(/class="([^"]*)"/);
      const clickableMatch = node.match(/clickable="([^"]*)"/);
      const resourceMatch = node.match(/resource-id="([^"]*)"/);
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
  } catch (e) {
    error("Failed to get UI hierarchy:", e);
    return [];
  }
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
          const path3 = `./screenshots/test_${timestamp}.png`;
          await execAsyncWithTimeout(
            'adb shell "screencap -p /sdcard/screen.png"',
            { timeout: 1e4 }
          );
          await execAsyncWithTimeout(
            `adb pull /sdcard/screen.png "${path3}"`,
            { timeout: 1e4 }
          );
          screenshots.push(path3);
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
      const installResult = await installApk2(buildResult.apkPath, serial, { reinstall: true });
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
        `adb logcat -d | grep -i "displayed.*${packageName}"`,
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
  if (launchType === "cold_start") {
    await forceStopApp(packageName);
    await clearAppData2(packageName);
  } else {
    await execAsyncWithTimeout("adb shell input keyevent 3", { timeout: 5e3 });
    await new Promise((resolve) => setTimeout(resolve, 1e3));
  }
  await execAsyncWithTimeout("adb logcat -c", { timeout: 5e3 });
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
  const p95Index = Math.ceil(sorted.length * 0.95) - 1;
  const p95 = sorted[Math.max(0, p95Index)];
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

// src/server.ts
var server = new Server(
  {
    name: "android-dev-assist",
    version: "2.0.0"
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
      // ===== 基础交互工具 =====
      {
        name: "screenshot",
        description: "Take a screenshot of the Android emulator or device via ADB",
        inputSchema: {
          type: "object",
          properties: {
            savePath: { type: "string", description: "Optional custom save path for the screenshot" }
          }
        }
      },
      {
        name: "tap",
        description: "Tap on screen at specified coordinates",
        inputSchema: {
          type: "object",
          properties: {
            x: { type: "number", description: "X coordinate" },
            y: { type: "number", description: "Y coordinate" }
          },
          required: ["x", "y"]
        }
      },
      {
        name: "swipe",
        description: "Swipe from start to end coordinates",
        inputSchema: {
          type: "object",
          properties: {
            x1: { type: "number", description: "Start X" },
            y1: { type: "number", description: "Start Y" },
            x2: { type: "number", description: "End X" },
            y2: { type: "number", description: "End Y" },
            duration: { type: "number", description: "Swipe duration in ms", default: 300 }
          },
          required: ["x1", "y1", "x2", "y2"]
        }
      },
      {
        name: "input_text",
        description: "Input text on the Android device. Note: spaces will be automatically escaped as %s",
        inputSchema: {
          type: "object",
          properties: {
            text: { type: "string", description: "Text to input (spaces will be auto-escaped as %s)" }
          },
          required: ["text"]
        }
      },
      {
        name: "press_key",
        description: "Press a hardware key (HOME, BACK, ENTER, MENU, POWER, VOLUME_UP, VOLUME_DOWN, DEL)",
        inputSchema: {
          type: "object",
          properties: {
            key: { type: "string", description: "Key name or keycode number" }
          },
          required: ["key"]
        }
      },
      // ===== 构建与部署 =====
      {
        name: "build",
        description: "Build the Android project with Gradle. Supports debug/release variants and custom flavors.",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            variant: { type: "string", description: "Build variant (debug/release)", default: "debug" },
            flavor: { type: "string", description: "Product flavor (optional)" }
          }
        }
      },
      {
        name: "install_and_launch",
        description: "Install APK and launch an app on the device. If apkPath is omitted, will only launch the already-installed app",
        inputSchema: {
          type: "object",
          properties: {
            apkPath: { type: "string", description: "Path to APK file (optional - omit if app is already installed to just launch)" },
            packageName: { type: "string", description: "Android package name" },
            activity: { type: "string", description: "Activity to launch (optional)" },
            serial: { type: "string", description: "Device serial (optional, for multiple devices)" }
          },
          required: ["packageName"]
        }
      },
      {
        name: "build_deploy",
        description: "Complete build and deploy pipeline: clean, build, install, launch",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            variant: { type: "string", description: "Build variant", default: "debug" },
            packageName: { type: "string", description: "Package name to launch after install" },
            autoLaunch: { type: "boolean", description: "Auto launch after install", default: true }
          },
          required: ["packageName"]
        }
      },
      // ===== UI验证与分析 =====
      {
        name: "verify_ui",
        description: "Verify UI by comparing screenshots, checking color, or OCR text detection. Type 'compare' requires baselinePath and currentPath; Type 'color' requires checkColor, x, y; Type 'ocr' requires checkText",
        inputSchema: {
          type: "object",
          properties: {
            type: {
              type: "string",
              enum: ["compare", "color", "ocr"],
              description: "Verification type: compare (screenshot comparison), color (pixel color check), ocr (text recognition)"
            },
            baselinePath: { type: "string", description: "Baseline image path (required for 'compare' mode)" },
            currentPath: { type: "string", description: "Current screenshot path (required for 'compare' mode)" },
            checkText: { type: "string", description: "Expected text to find (required for 'ocr' mode)" },
            checkColor: { type: "string", description: "Expected hex color e.g. '#FF0000' (required for 'color' mode)" },
            x: { type: "number", description: "X coordinate for color check (required for 'color' mode)" },
            y: { type: "number", description: "Y coordinate for color check (required for 'color' mode)" }
          },
          required: ["type"]
        }
      },
      {
        name: "analyze_screenshot",
        description: "3-stage screenshot analysis: (1) Python PIL pixel measurements, (2) Kimi k2.6 vision AI for visual understanding, (3) precise card-by-card verification. Returns comprehensive UI report with issues and suggestions.",
        inputSchema: {
          type: "object",
          properties: {
            filePath: { type: "string", description: "Path to the screenshot PNG file" },
            prompt: { type: "string", description: "Optional custom analysis focus (e.g. 'check card spacing')" }
          },
          required: ["filePath"]
        }
      },
      {
        name: "compare_screenshots",
        description: "Compare two screenshots (baseline vs current) using Kimi k2.6 vision AI. Detects layout differences, color mismatches, and regressions.",
        inputSchema: {
          type: "object",
          properties: {
            baselinePath: { type: "string", description: "Path to baseline/design screenshot" },
            currentPath: { type: "string", description: "Path to current implementation screenshot" },
            prompt: { type: "string", description: "Optional custom comparison focus" }
          },
          required: ["baselinePath", "currentPath"]
        }
      },
      // ===== 日志与调试 =====
      {
        name: "get_logs",
        description: "Fetch Android device logs (logcat). Filters for crash/fatal/exception by default. Use 'all' filter to see all logs for a package.",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Filter logs by package name (e.g. com.example.toutiao)" },
            filter: { type: "string", description: "Log filter: 'crash' (default) for errors/exceptions, 'all' for all logs", default: "crash" },
            lines: { type: "number", description: "Max lines to return", default: 50 },
            serial: { type: "string", description: "Device serial (optional)" }
          }
        }
      },
      {
        name: "clear_logs",
        description: "Clear device log buffer",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial (optional)" }
          }
        }
      },
      // ===== 设备管理 =====
      {
        name: "list_devices",
        description: "List all connected Android devices with detailed information (model, Android version, resolution, DPI, serial, state)",
        inputSchema: {
          type: "object",
          properties: {}
        }
      },
      {
        name: "device_info",
        description: "Get detailed information about a specific device (properties from getprop, screen resolution, DPI, battery status, CPU info, memory info)",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial number" }
          },
          required: ["serial"]
        }
      },
      {
        name: "shell_command",
        description: "Execute a shell command on the device",
        inputSchema: {
          type: "object",
          properties: {
            command: { type: "string", description: "Shell command to execute" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["command"]
        }
      },
      // ===== 应用管理 =====
      {
        name: "list_apps",
        description: "List installed applications on the device. By default shows only third-party apps (excluding system apps)",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial (optional)" },
            system: { type: "boolean", description: "Include system apps (default: false)" },
            thirdParty: { type: "boolean", description: "Show only third-party apps i.e. exclude system apps (default: true)" }
          }
        }
      },
      {
        name: "app_info",
        description: "Get detailed information about a specific app (version name/code, install time, data directory, permissions, enabled state)",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["packageName"]
        }
      },
      {
        name: "uninstall_app",
        description: "Uninstall an application",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to uninstall" },
            serial: { type: "string", description: "Device serial (optional)" },
            keepData: { type: "boolean", description: "Keep app data", default: false }
          },
          required: ["packageName"]
        }
      },
      {
        name: "clear_app_data",
        description: "Clear application data and cache",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["packageName"]
        }
      },
      {
        name: "stop_app",
        description: "Force stop an application",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["packageName"]
        }
      },
      // ===== 性能监控 =====
      {
        name: "performance_metrics",
        description: "Collect device performance metrics: CPU, memory, FPS, battery, temperature",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to monitor (optional)" },
            serial: { type: "string", description: "Device serial (optional)" }
          }
        }
      },
      {
        name: "measure_app_launch",
        description: "Measure app launch speed (cold start / warm start / page transition). Reports TTID, TTFD, TotalTime with statistics and grade.",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to measure" },
            launchType: { type: "string", enum: ["cold_start", "warm_start", "page_transition"], description: "Launch type", default: "cold_start" },
            activityName: { type: "string", description: "Specific activity to launch (optional, e.g. com.example.MainActivity)" },
            iterations: { type: "number", description: "Number of measurements to average", default: 3 }
          },
          required: ["packageName"]
        }
      },
      {
        name: "record_screen",
        description: "Record device screen for specified duration",
        inputSchema: {
          type: "object",
          properties: {
            duration: { type: "number", description: "Recording duration in seconds", default: 10 },
            outputPath: { type: "string", description: "Output file path", default: "./screen_record.mp4" },
            serial: { type: "string", description: "Device serial (optional)" }
          }
        }
      },
      // ===== 代码质量 =====
      {
        name: "code_quality",
        description: "Run code quality checks: ktlint style check, cyclomatic complexity analysis, and line count statistics. Optionally auto-fix ktlint issues",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            fix: { type: "boolean", description: "Auto-fix ktlint style issues (default: false)", default: false }
          }
        }
      },
      {
        name: "run_tests",
        description: "Run unit tests (JVM tests) or instrumented tests (on-device tests) via Gradle",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            type: { type: "string", description: "Test type: unit (JVM unit tests) / instrumented (on-device tests) / all", default: "unit" },
            module: { type: "string", description: "Specific module to test (optional)" }
          }
        }
      },
      // ===== UI自动化测试 =====
      {
        name: "ui_test",
        description: "Run UI automation test with a sequence of steps. Each step has an action (tap/swipe/input/wait/screenshot) and params (coordinates, text, duration, etc.)",
        inputSchema: {
          type: "object",
          properties: {
            steps: {
              type: "array",
              description: "Test steps array. Each step: { action: 'tap'|'swipe'|'input'|'wait'|'screenshot', params: { x, y, text, duration, ... } }",
              items: {
                type: "object",
                properties: {
                  action: { type: "string", enum: ["tap", "swipe", "input", "wait", "screenshot"], description: "Action type: tap (click at x,y), swipe (drag from x1,y1 to x2,y2), input (type text), wait (pause in ms), screenshot (capture screen)" },
                  params: { type: "object", description: "Action parameters: tap/swipe need x,y; input needs text; wait needs durationMs; screenshot needs no params" }
                }
              }
            }
          },
          required: ["steps"]
        }
      },
      {
        name: "regression_test",
        description: "Run regression test suite: launch app \u2192 take screenshot \u2192 verify UI hierarchy structure, checking basic functionality is working",
        inputSchema: {
          type: "object",
          properties: {
            packageName: { type: "string", description: "Package name to test" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["packageName"]
        }
      },
      // ===== 项目报告 =====
      {
        name: "project_report",
        description: "Generate comprehensive project report with metrics, quality, architecture analysis",
        inputSchema: {
          type: "object",
          properties: {
            projectPath: { type: "string", description: "Path to Android project", default: "." },
            includePerformance: { type: "boolean", description: "Include performance metrics", default: false },
            packageName: { type: "string", description: "Package name for performance monitoring" },
            format: { type: "string", description: "Output format: markdown/json", default: "markdown" }
          }
        }
      },
      // ===== 文件操作 =====
      {
        name: "push_file",
        description: "Push file to device",
        inputSchema: {
          type: "object",
          properties: {
            localPath: { type: "string", description: "Local file path" },
            remotePath: { type: "string", description: "Remote destination path" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["localPath", "remotePath"]
        }
      },
      {
        name: "pull_file",
        description: "Pull file from device",
        inputSchema: {
          type: "object",
          properties: {
            remotePath: { type: "string", description: "Remote file path" },
            localPath: { type: "string", description: "Local destination path" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["remotePath", "localPath"]
        }
      },
      // ===== 视觉驱动交互 =====
      {
        name: "vision_action",
        description: "Use vision AI (Kimi k2.6) to locate and interact with UI elements by natural language description. Screenshots the app, asks the vision model to find the target element and return precise coordinates, executes the action (tap/swipe/input), and captures a confirmation screenshot. Supports multi-step sequences via the 'prompts' array.",
        inputSchema: {
          type: "object",
          properties: {
            prompt: { type: "string", description: "Natural language instruction, e.g. 'tap the \u89C6\u9891 tab in bottom nav' or 'swipe down to scroll'" },
            prompts: { type: "array", items: { type: "string" }, description: "Multiple instructions for sequential actions (each step uses the previous step's after-screenshot as its before-screenshot)" },
            beforeScreenshot: { type: "string", description: "Optional path to an existing screenshot to use instead of taking a new one" }
          }
        }
      },
      // ===== 网络调试 =====
      {
        name: "network_state",
        description: "Get device network state (WiFi, mobile, airplane mode)",
        inputSchema: {
          type: "object",
          properties: {
            serial: { type: "string", description: "Device serial (optional)" }
          }
        }
      },
      {
        name: "set_network",
        description: "Set device network state",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", enum: ["wifi", "mobile", "airplane"], description: "Network type" },
            enabled: { type: "boolean", description: "Enable or disable" },
            serial: { type: "string", description: "Device serial (optional)" }
          },
          required: ["type", "enabled"]
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
      case "tap":
        return handleTap(args);
      case "swipe":
        return handleSwipe(args);
      case "input_text":
        return handleInputText(args);
      case "press_key":
        return handlePressKey(args);
      // 构建与部署
      case "build":
        return handleBuild(args);
      case "install_and_launch":
        return handleInstallAndLaunch(args);
      case "build_deploy":
        return handleBuildDeploy(args);
      // UI验证与分析
      case "verify_ui":
        return handleVerifyUI(args);
      case "analyze_screenshot":
        return handleAnalyzeScreenshot(args);
      case "compare_screenshots":
        return handleCompareScreenshots(args);
      // 日志与调试
      case "get_logs":
        return handleGetLogs(args);
      case "clear_logs":
        return handleDeviceManagement(args, "clear_logs");
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
      // 代码质量
      case "code_quality":
        return handleCodeQuality(args);
      case "run_tests":
        return handleBuildDeploy(args, "run_tests");
      // UI自动化测试
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
      // 视觉驱动交互
      case "vision_action":
        return handleVisionAction(args);
      // 网络调试
      case "network_state":
        return handleNetworkDebug(args, "get_state");
      case "set_network":
        return handleNetworkDebug(args, "set_state");
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
  log("AndroidDev-Assist MCP Server v2.0.0 running on stdio");
  log("Capabilities: device management, app management, performance monitoring, code quality, UI testing, project reporting");
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
