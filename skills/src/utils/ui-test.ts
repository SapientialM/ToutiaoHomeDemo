import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";

/**
 * UI自动化测试工具
 * 支持截图对比、元素检测、自动化操作流程
 */

export interface UIElement {
  type: string;
  text?: string;
  bounds: { x: number; y: number; width: number; height: number };
  clickable: boolean;
  resourceId?: string;
}

export interface UITestResult {
  success: boolean;
  message: string;
  screenshot?: string;
  elements?: UIElement[];
  duration: number;
}

/**
 * 获取当前Activity名称
 */
export async function getCurrentActivity(): Promise<string> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "dumpsys window windows | grep mCurrentFocus"',
      { timeout: 10000 }
    );
    const match = stdout.match(/(\S+)\/(\S+)}/);
    return match ? `${match[1]}/${match[2]}` : "unknown";
  } catch (e) {
    error("Failed to get current activity:", e);
    return "unknown";
  }
}

/**
 * 获取UI层级结构（使用uiautomator）
 */
export async function getUIHierarchy(): Promise<UIElement[]> {
  try {
    await execAsyncWithTimeout(
      'adb shell "uiautomator dump /sdcard/window_dump.xml"',
      { timeout: 10000 }
    );
    
    const { stdout } = await execAsyncWithTimeout(
      'adb shell "cat /sdcard/window_dump.xml"',
      { timeout: 10000 }
    );
    
    // Parse XML to extract elements
    const elements: UIElement[] = [];
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
          text: textMatch ? textMatch[1] : undefined,
          bounds: {
            x: parseInt(boundsMatch[1]),
            y: parseInt(boundsMatch[2]),
            width: parseInt(boundsMatch[3]) - parseInt(boundsMatch[1]),
            height: parseInt(boundsMatch[4]) - parseInt(boundsMatch[2]),
          },
          clickable: clickableMatch ? clickableMatch[1] === "true" : false,
          resourceId: resourceMatch ? resourceMatch[1] : undefined,
        });
      }
    }
    
    return elements;
  } catch (e) {
    error("Failed to get UI hierarchy:", e);
    return [];
  }
}

/**
 * 查找包含特定文本的元素
 */
export async function findElementByText(text: string): Promise<UIElement | null> {
  const elements = await getUIHierarchy();
  return elements.find((e) => e.text?.includes(text)) || null;
}

/**
 * 点击包含特定文本的元素
 */
export async function tapByText(text: string): Promise<boolean> {
  const element = await findElementByText(text);
  if (element) {
    const centerX = element.bounds.x + element.bounds.width / 2;
    const centerY = element.bounds.y + element.bounds.height / 2;
    
    await execAsyncWithTimeout(
      `adb shell input tap ${centerX} ${centerY}`,
      { timeout: 10000 }
    );
    
    return true;
  }
  return false;
}

/**
 * 等待元素出现
 */
export async function waitForElement(
  text: string,
  timeout: number = 10000
): Promise<UIElement | null> {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const element = await findElementByText(text);
    if (element) return element;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return null;
}

/**
 * 执行自动化测试流程
 */
export async function runUITest(
  steps: Array<{
    action: "tap" | "swipe" | "input" | "wait" | "screenshot";
    params: Record<string, unknown>;
  }>
): Promise<UITestResult> {
  const start = Date.now();
  const screenshots: string[] = [];
  
  try {
    for (let i = 0; i < steps.length; i++) {
      const step = steps[i];
      log(`Executing step ${i + 1}/${steps.length}: ${step.action}`);
      
      switch (step.action) {
        case "tap": {
          const { x, y } = step.params as { x: number; y: number };
          await execAsyncWithTimeout(
            `adb shell input tap ${x} ${y}`,
            { timeout: 10000 }
          );
          break;
        }
        case "swipe": {
          const { x1, y1, x2, y2, duration = 300 } = step.params as {
            x1: number; y1: number; x2: number; y2: number; duration?: number;
          };
          await execAsyncWithTimeout(
            `adb shell input swipe ${x1} ${y1} ${x2} ${y2} ${duration}`,
            { timeout: 10000 }
          );
          break;
        }
        case "input": {
          const { text } = step.params as { text: string };
          const escaped = text.replace(/ /g, "%s");
          await execAsyncWithTimeout(
            `adb shell input text "${escaped}"`,
            { timeout: 10000 }
          );
          break;
        }
        case "wait": {
          const { ms = 1000 } = step.params as { ms?: number };
          await new Promise((resolve) => setTimeout(resolve, ms));
          break;
        }
        case "screenshot": {
          const timestamp = Date.now();
          const path = `./screenshots/test_${timestamp}.png`;
          await execAsyncWithTimeout(
            'adb shell "screencap -p /sdcard/screen.png"',
            { timeout: 10000 }
          );
          await execAsyncWithTimeout(
            `adb pull /sdcard/screen.png "${path}"`,
            { timeout: 10000 }
          );
          screenshots.push(path);
          break;
        }
      }
    }
    
    return {
      success: true,
      message: `Completed ${steps.length} steps successfully`,
      screenshot: screenshots[screenshots.length - 1],
      duration: Date.now() - start,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      message: err.message || "Test failed",
      duration: Date.now() - start,
    };
  }
}

/**
 * 执行回归测试套件
 */
export async function runRegressionTest(
  packageName: string
): Promise<{
  passed: number;
  failed: number;
  total: number;
  results: Array<{ name: string; success: boolean; message: string }>;
}> {
  const tests = [
    {
      name: "App Launch",
      test: async () => {
        await execAsyncWithTimeout(
          `adb shell am start -n ${packageName}/.MainActivity`,
          { timeout: 10000 }
        );
        await new Promise((resolve) => setTimeout(resolve, 3000));
        const activity = await getCurrentActivity();
        return activity.includes(packageName);
      },
    },
    {
      name: "Screenshot Capture",
      test: async () => {
        await execAsyncWithTimeout(
          'adb shell "screencap -p /sdcard/test_screen.png"',
          { timeout: 10000 }
        );
        return true;
      },
    },
    {
      name: "UI Hierarchy",
      test: async () => {
        const elements = await getUIHierarchy();
        return elements.length > 0;
      },
    },
  ];
  
  const results: Array<{ name: string; success: boolean; message: string }> = [];
  let passed = 0;
  let failed = 0;
  
  for (const test of tests) {
    try {
      const success = await test.test();
      results.push({
        name: test.name,
        success,
        message: success ? "Passed" : "Failed",
      });
      if (success) passed++;
      else failed++;
    } catch (e) {
      const err = e as Error;
      results.push({
        name: test.name,
        success: false,
        message: err.message || "Error",
      });
      failed++;
    }
  }
  
  return {
    passed,
    failed,
    total: tests.length,
    results,
  };
}
