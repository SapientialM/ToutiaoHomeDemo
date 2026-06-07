import { log, error } from "../utils/logger.js";
import { getUIHierarchy, findElementByText, type UIElement } from "../utils/ui-test.js";
import { execAsyncWithTimeout } from "../utils/exec.js";

/**
 * 导出 UI 层级为 JSON（供 Agent 推断可点击元素、文本、坐标）
 * 内部使用 uiautomator dump，结构稳定可靠
 */
export async function handleDumpHierarchy(args: Record<string, unknown>) {
  try {
    const includeRaw = Boolean(args.includeRaw);
    const elements = await getUIHierarchy();
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        count: elements.length,
        elements: elements.map(serializeElement),
        ...(includeRaw ? { hint: "Use find_element to locate specific elements by text/resource-id" } : {}),
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("dump_hierarchy failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 按 text / resource-id / class 查找元素，返回中心坐标
 * Agent 在需要点击/截图某个元素但不知道坐标时使用
 */
export async function handleFindElement(args: Record<string, unknown>) {
  try {
    const text = args.text as string | undefined;
    const resourceId = args.resourceId as string | undefined;
    const className = args.className as string | undefined;
    const exact = Boolean(args.exact);

    if (!text && !resourceId && !className) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "At least one of text / resourceId / className is required",
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
          hint: "Element not visible. Use wait_for_element to poll, or take a fresh screenshot to verify state.",
        }) }],
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
        all: matched.map(withCenter),
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("find_element failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 轮询等待元素出现 / 消失
 * 异步 UI（网络加载、动画）必备 —— 避免 Agent 在元素未就绪时盲目操作
 */
export async function handleWaitForElement(args: Record<string, unknown>) {
  try {
    const text = args.text as string | undefined;
    const resourceId = args.resourceId as string | undefined;
    const timeoutMs = (args.timeoutMs as number) ?? 10000;
    const intervalMs = Math.max(100, (args.intervalMs as number) ?? 500);
    const expect = (args.expect as string) ?? "appear";

    if (!text && !resourceId) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "text or resourceId required",
      }) }] };
    }

    const start = Date.now();
    let lastPoll: UIElement[] = [];
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
            element: withCenter(matched[0]),
          }) }],
        };
      }
      if (expect === "disappear" && !isPresent) {
        return {
          content: [{ type: "text", text: JSON.stringify({
            success: true,
            found: false,
            waitedMs: Date.now() - start,
          }) }],
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
        hint: expect === "appear" ? "Element did not appear within timeout" : "Element still visible after timeout",
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("wait_for_element failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

function serializeElement(el: UIElement) {
  return {
    type: el.type,
    text: el.text,
    resourceId: el.resourceId,
    clickable: el.clickable,
    bounds: el.bounds,
  };
}

function withCenter(el: UIElement) {
  return {
    ...serializeElement(el),
    center: {
      x: Math.round(el.bounds.x + el.bounds.width / 2),
      y: Math.round(el.bounds.y + el.bounds.height / 2),
    },
  };
}
