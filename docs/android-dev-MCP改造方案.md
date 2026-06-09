# android-dev MCP 改造方案

> **目标**：把现有 MCP 从「工具集合」升级为「AI 产品经理」
> **截止**：6/10（周三）完成改造 + 测试，6/11（周四）提交
> **核心思路**：MCP 内部新增 PM Agent，能视觉理解 + 主动探索 + 输出结构化需求

---

## 🎯 核心改造点（按优先级）

| 优先级 | 改造项 | 工作量 | 收益 |
|---|---|---|---|
| **P0** | `dump_ui()` 工具（uiautomator dump） | 1h | 识图准确率 50%→90%+ |
| **P0** | `pm_review()` 工具（PM 审查模式） | 3h | 视频杀手锏、答辩核弹 |
| **P1** | PM 提示词（CoT + JSON 输出） | 1h | 输出质量 |
| **P1** | PM 标准库（头条首页 checklist） | 1h | PM 有判断依据 |
| **P2** | `pm_compare_with_design()` | 2h | 加分项 |
| **P2** | `pm_mark_fixed()` 修复追踪 | 1h | 闭环展示 |

---

## 📐 架构图

```
┌────────────────────────────────────────────────────┐
│  Calling Agent (开发 Agent)                         │
│  - 写代码 / 改代码                                   │
│  - 调用 MCP 工具                                    │
└────────────────────┬───────────────────────────────┘
                     │ 工具调用
                     ↓
┌────────────────────────────────────────────────────┐
│  android-dev MCP Server                             │
│                                                    │
│  ┌──────────────────────────────────────────┐      │
│  │  PM Agent (新增)                          │      │
│  │  ┌────────────┐  ┌────────────────────┐  │      │
│  │  │ uiautomator│  │ MiniMax-Vision     │  │      │
│  │  │ dump       │→ │ (CoT 推理)         │  │      │
│  │  └────────────┘  └────────────────────┘  │      │
│  │         ↓                ↓                │      │
│  │  ┌──────────────────────────────────────┐ │      │
│  │  │  结构化输出: Issue[] + positives      │ │      │
│  │  └──────────────────────────────────────┘ │      │
│  └──────────────────────────────────────────┘      │
│                                                    │
│  基础工具 (现有):                                    │
│  - screenshot() / tap() / swipe() / text()         │
└────────────────────────────────────────────────────┘
```

---

## 🛠️ P0-1: `dump_ui()` 工具

### 功能
调用 `adb shell uiautomator dump`，把 Android UI 树导出为结构化 XML。

### 代码骨架（Python）

```python
import subprocess
import tempfile
from pathlib import Path

@mcp.tool()
async def dump_ui(save_path: str = None) -> dict:
    """
    导出当前屏幕的 UI 树（结构化 XML），
    比截图更适合 LLM 理解（更快、更准、可定位）。
    """
    save_path = save_path or f"/tmp/ui_dump_{int(time.time())}.xml"
    
    # 1. 触发 dump
    result = subprocess.run(
        ["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
        capture_output=True, text=True
    )
    
    if "ERROR" in result.stdout or result.returncode != 0:
        return {"error": "dump failed", "detail": result.stdout}
    
    # 2. 拉回本地
    subprocess.run(
        ["adb", "pull", "/sdcard/ui.xml", save_path],
        capture_output=True
    )
    
    # 3. 解析为 JSON 友好结构
    tree = ET.parse(save_path)
    nodes = []
    for node in tree.iter("node"):
        nodes.append({
            "class": node.get("class"),
            "text": node.get("text"),
            "resource_id": node.get("resource-id"),
            "content_desc": node.get("content-desc"),
            "bounds": node.get("bounds"),  # [x1,y1][x2,y2]
            "clickable": node.get("clickable") == "true",
            "enabled": node.get("enabled") == "true",
        })
    
    return {
        "dump_path": save_path,
        "node_count": len(nodes),
        "nodes": nodes,
    }
```

### 解析 bounds 为坐标（辅助函数）

```python
def parse_bounds(bounds_str: str) -> tuple:
    """'[0,0][1080,1920]' -> ((0,0), (1080,1920))"""
    import re
    matches = re.findall(r'\[(\d+),(\d+)\]', bounds_str)
    if len(matches) == 2:
        return tuple(map(int, matches[0])), tuple(map(int, matches[1]))
    return None
```

---

## 🛠️ P0-2: `pm_review()` 工具 ⭐ 核心

### 功能
启动一次 PM 审查，返回结构化的问题清单。

### 代码骨架

```python
import json
from datetime import datetime
from pathlib import Path

REVIEW_DIR = Path("./pm_reviews")
REVIEW_DIR.mkdir(exist_ok=True)

@mcp.tool()
async def pm_review(
    target: str = "首页列表",
    focus: list[str] = None,
    compare_design: str = None
) -> dict:
    """
    AI 产品经理审查模式。
    
    Args:
        target: 审查目标（如"首页列表"、"下拉刷新"、"加载更多"）
        focus: 重点关注维度（如 ["ui_bug", "performance"]）
        compare_design: 设计稿路径（可选，做 diff）
    
    Returns:
        结构化审查报告
    """
    review_id = f"rev-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
    
    # Step 1: 收集证据
    screenshot_path = await _take_screenshot()
    ui_dump = await dump_ui()
    
    # Step 2: 加载 PM 标准库
    checklist = _load_checklist("pm_checklist_toutiao.md")
    
    # Step 3: 构建 CoT 提示词
    prompt = _build_pm_prompt(target, focus, checklist, ui_dump)
    
    # Step 4: 调用 VLM
    raw_response = await _call_vision(screenshot_path, prompt)
    
    # Step 5: 解析 + 持久化
    review = {
        "review_id": review_id,
        "timestamp": datetime.now().isoformat(),
        "target": target,
        "screenshot": screenshot_path,
        "ui_dump_summary": {
            "node_count": ui_dump.get("node_count"),
            "texts": [n["text"] for n in ui_dump.get("nodes", []) if n["text"]],
        },
        **raw_response,
    }
    
    # 存盘（视频演示用）
    save_path = REVIEW_DIR / f"{review_id}.json"
    save_path.write_text(json.dumps(review, ensure_ascii=False, indent=2))
    
    return review


async def _take_screenshot() -> str:
    """截图并返回路径"""
    path = f"/tmp/pm_screenshot_{int(time.time())}.png"
    subprocess.run(["adb", "exec-out", "screencap", "-p"], 
                   stdout=open(path, "wb"))
    return path


def _load_checklist(filename: str) -> str:
    """加载 PM 标准库"""
    path = Path(f"./{filename}")
    if path.exists():
        return path.read_text()
    return ""


async def _call_vision(image_path: str, prompt: str) -> dict:
    """
    调用 MiniMax-V3 Vision。
    
    注：根据实际 API 调整 message 格式。
    """
    import base64
    
    with open(image_path, "rb") as f:
        image_b64 = base64.b64encode(f.read()).decode()
    
    # ⬇️ 替换为你的 MiniMax-V3 API 调用
    response = await minimax_vision_client.chat.completions.create(
        model="MiniMax-V3",
        messages=[
            {"role": "system", "content": "你是一位资深 Android 产品经理..."},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{image_b64}"}}
                ]
            }
        ],
        response_format={"type": "json_object"},  # 强制 JSON 输出
    )
    
    return json.loads(response.choices[0].message.content)
```

---

## 📝 PM 提示词（核心 CoT）

放在 `prompts/pm_review.txt`：

```markdown
# 角色
你是一位资深的 Android 产品经理，正在审查一个仿今日头条 APP 的首页列表实现。
你拥有 10 年移动端经验，对 Material Design 规范、新闻类应用的用户体验、
性能优化有深刻理解。

# 当前任务
审查"${target}"的实现质量，找出"看起来对、其实有问题"的地方。
不只看表象，要思考：
- 用户实际使用时会遇到什么问题？
- 这个实现是否符合头条/新闻类 APP 的行业最佳实践？
- 性能、可用性、可访问性有没有明显问题？

# 重点关注维度
${focus_or_default}

# PM 审查标准库（参考）
${checklist}

# 当前 UI 树关键信息
${ui_dump_summary}

# 思考步骤（请严格按顺序）
1. **先整体扫一遍**：描述屏幕状态、加载情况、布局感受
2. **列出可疑点**：找到 3-7 个"看起来不对"的地方
3. **深挖每个问题**：
   - 严重程度（high/medium/low）
   - 类别（ui_bug/ux/performance/logic/spec_deviation）
   - 位置（哪个区域/哪个元素）
   - 现状 + 应有状态
   - 具体可执行的修复建议（最好给到 code hint）
4. **列出 1-3 个做得好的地方**（用于正向反馈）
5. **输出 JSON**

# 输出格式（严格遵守）
{
  "overall_rating": "A/B/C/D",
  "summary": "一句话整体评价",
  "thinking_process": "你的思考过程（视频里要展示）",
  "issues": [
    {
      "id": "ISSUE-001",
      "severity": "high|medium|low",
      "category": "ui_bug|ux|performance|logic|spec_deviation",
      "location": "屏幕区域 / 元素描述",
      "current_state": "现在的实现",
      "expected_state": "应有的实现",
      "suggestion": "具体修复建议",
      "code_hint": "代码层面的提示（可选）",
      "estimated_effort": "10min/30min/1h/2h"
    }
  ],
  "positives": [
    {"item": "做得好的点", "evidence": "证据/截图位置"}
  ],
  "next_priorities": ["ISSUE-001", "ISSUE-003"]
}

# 重要原则
- **少而精**：3-5 个 issue 比 15 个流水账好
- **具体可执行**：不要说"优化性能"，说"在 NewsCard Composable 中加 Modifier.placeholder()"
- **说人话**：用工程师能秒懂的语言
- **不要客气**：发现明显问题就直说
```

---

## 📋 PM 标准库（头条首页 checklist）

放在 `pm_checklist_toutiao.md`：

```markdown
# 仿今日头条首页 - PM 审查 Checklist

## 一、数据加载
- [ ] 首屏数据能否成功加载
- [ ] 加载失败时是否有错误提示（不是空白）
- [ ] 加载中是否有 loading 状态
- [ ] 加载是否流畅（不是卡顿几秒才显示）
- [ ] 网络异常时是否有友好提示

## 二、卡片展示
- [ ] 卡片内容是否完整（标题、来源、评论数、时间）
- [ ] 图片是否能正常显示
- [ ] 图片加载中是否有占位图
- [ ] 图片加载失败是否有 fallback
- [ ] 卡片间距是否合理
- [ ] 字体大小、行高是否符合阅读习惯
- [ ] 来源/时间戳格式是否一致

## 三、交互
- [ ] 下拉刷新是否可用
- [ ] 下拉刷新是否有视觉反馈
- [ ] 上拉加载更多是否可用
- [ ] 加载更多时是否有 loading 提示
- [ ] 卡片点击是否有响应
- [ ] 长按是否有合适反馈
- [ ] 滚动是否流畅（无明显卡顿）

## 四、状态管理
- [ ] loading / error / success / empty 四种状态是否齐全
- [ ] 状态切换是否平滑
- [ ] 异常状态是否给用户出路（重试按钮）

## 五、视觉规范
- [ ] 颜色使用是否一致
- [ ] 字号是否分级别（标题/副标题/正文/辅助）
- [ ] 是否有不必要的分割线
- [ ] 暗色模式适配（如有）

## 六、架构与代码（如果能看到代码）
- [ ] 数据流是否单向、可追踪
- [ ] 状态管理是否集中
- [ ] 关键逻辑是否有注释

## 七、特殊加分项
- [ ] 是否实现 Room/SQLite 数据库存储
- [ ] 是否支持离线浏览
- [ ] 是否有预加载（prefetch）
- [ ] 是否有图片缓存策略
```

---

## 🛠️ P2: `pm_compare_with_design()`（加分项）

```python
@mcp.tool()
async def pm_compare_with_design(
    design_path: str,
    impl_screenshot: str = None
) -> dict:
    """
    对比设计稿与当前实现，输出差异报告。
    """
    impl_screenshot = impl_screenshot or await _take_screenshot()
    
    # Step 1: 像素 diff
    diff_result = _pixel_diff(design_path, impl_screenshot)
    
    # Step 2: 视觉 LLM 分析
    prompt = f"""
    设计稿：{design_path}
    当前实现：{impl_screenshot}
    像素 diff 区域：{diff_result['diff_regions']}
    
    请分析：
    1. 哪些差异是 critical（影响功能或视觉一致性）
    2. 哪些差异是 acceptable（实现合理）
    3. 给出修复优先级
    """
    
    analysis = await _call_vision(impl_screenshot, prompt)
    return {"pixel_diff": diff_result, "llm_analysis": analysis}


def _pixel_diff(img1_path: str, img2_path: str) -> dict:
    """用 pixelmatch 做像素 diff"""
    # pip install pixelmatch pillow
    from PIL import Image
    import pixelmatch
    from pixelmatch.contrib.PIL import pixelmatch as pil_pixelmatch
    
    img1 = Image.open(img1_path).convert("RGB")
    img2 = Image.open(img2_path).convert("RGB")
    
    if img1.size != img2.size:
        img2 = img2.resize(img1.size)
    
    diff_img = Image.new("RGB", img1.size)
    mismatched = pil_pixelmatch(img1, img2, diff_img, threshold=0.1)
    
    return {
        "mismatched_pixels": mismatched,
        "total_pixels": img1.size[0] * img1.size[1],
        "diff_ratio": mismatched / (img1.size[0] * img1.size[1]),
        "diff_image": "/tmp/diff.png",
    }
```

---

## 🛠️ P2: `pm_mark_fixed()` 修复追踪

```python
@mcp.tool()
async def pm_mark_fixed(issue_id: str, note: str = "") -> dict:
    """
    标记某个 issue 已修复。
    下次 pm_review 时会跳过该问题。
    """
    # 维护一个 .pm_state.json
    state_path = Path("./.pm_state.json")
    state = json.loads(state_path.read_text()) if state_path.exists() else {"fixed": []}
    
    state["fixed"].append({
        "issue_id": issue_id,
        "note": note,
        "fixed_at": datetime.now().isoformat(),
    })
    state_path.write_text(json.dumps(state, ensure_ascii=False, indent=2))
    
    return {"status": "ok", "fixed_count": len(state["fixed"])}
```

---

## ✅ 测试 checklist

### 功能测试

| 测试项 | 怎么测 | 预期 |
|---|---|---|
| dump_ui 能跑 | `dump_ui()` 调用 | 返回非空 nodes |
| pm_review 启动 | 调 `pm_review("首页列表")` | 3-5 issues + positives |
| 输出是合法 JSON | 看返回 | 能 parse |
| 截图清晰 | 看 `/tmp/pm_screenshot_*.png` | 不是黑屏 |
| VLM 推理合理 | 看 thinking_process | 有逻辑 |

### 验收标准（视频演示用）

- [ ] MCP 启动后能列出所有工具（含 `pm_review`）
- [ ] 调 `pm_review()` 后 5s 内返回
- [ ] 返回的 issues 至少有 1 个 high
- [ ] 至少 1 个 issue 有 `code_hint`
- [ ] 日志能看到 LLM 的 thinking_process

---

## 🎬 视频演示剧本（4:00 - 4:30）

```
[画面：调起 pm_review()]

旁白："我自己开发自己审查——这是我的 AI 产品经理模式"

[画面：MCP 内部日志滚动]
- 截图
- uiautomator dump
- 调用 VLM
- "AI 思考中..." + 展示 thinking_process

[画面：返回的 JSON 渲染成卡片]
"它发现了 3 个问题："
  - 卡片 #3 图片没占位图 (high)
  - 加载状态切换突兀 (medium)
  - 时间戳格式不统一 (low)

[画面：我根据建议改代码]
"我根据这个改 5 分钟"

[画面：再调 pm_review()]
"再让它审一遍"
[画面：issue 状态变化]
"问题 #1 修复了 ✅，新发现 #4：..."
```

**这一段就是闭环，是答辩的最大亮点**。

---

## 📦 交付物清单

| 文件 | 用途 | 截止 |
|---|---|---|
| `tools/dump_ui.py` | UI 树导出工具 | 6/8 晚 |
| `tools/pm_review.py` | PM 审查核心 | 6/9 白天 |
| `prompts/pm_review.txt` | PM 提示词 | 6/9 白天 |
| `pm_checklist_toutiao.md` | 审查标准库 | 6/9 白天 |
| `pm_reviews/` | 历史审查记录 | 持续 |
| `tools/pm_compare_with_design.py` | 设计对比（可选） | 6/10 |
| 视频演示片段 | 录屏 | 6/10 晚 |

---

## 🚨 风险 & 兜底

| 风险 | 兜底 |
|---|---|
| VLM 返回不是合法 JSON | 加 retry 3 次 + fallback 提示词 |
| uiautomator dump 失败 | 截图 + OCR 兜底 |
| PM 输出废话太多 | 提示词强调"少而精" + few-shot example |
| 时间来不及 | **P0 优先**，P2 全砍 |

---

**核心理念**：**让 MCP 有了"判断力"**，不再是 adb 胶水层，而是一个有产品视角的 AI 协作伙伴。
**最大价值**：**视频里展示"AI 开发者 + AI 产品经理"协作的闭环**，这是 99% 学员做不到的。
