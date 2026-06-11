# PM Agent MCP 设计方案

> **项目**：ToutiaoFeedDemo — 仿今日头条信息流 Demo  
> **版本**：v1.0  
> **日期**：2026-06-10  
> **作者**：训练营项目组  

---

## 一、背景与目标

### 1.1 背景

本项目（`ToutiaoFeedDemo`）在训练营验收中面临五个核心评估维度：**还原度、技术架构、工程规范、完整度、Agent**。其中"Agent"维度要求"设计一个用于应用开发的 Agent，比如 prompt、SKILL"，并要求体现 **Vibe Coding** 能力——即"讲清楚 AI 工程化，如何让它做 Demo，整个过程和思考流程"。

当前项目已在 `skills/` 目录下沉淀了 `android-dev-assist` MCP Server（v3.1.0，44 个工具 / 15 个模块），并实现了 PM 审查相关能力：

- `pm_review`：单次截图 + VLM 审查，产出结构化 issue 报告
- `pm_explore`：多步自主探索（6-12 步），PM 自主调度设备操作工具
- `pm_compare_with_design`：设计稿 vs 实现的像素 diff + LLM 分析
- `pm_mark_fixed`：修复状态追踪
- `dump_ui`：UI 层级结构化导出

然而，这些能力目前以**独立工具**的形式存在，Claude Code（或其他开发者 Agent）需要人工解读 PM 的输出，再手动转化为开发指令。**PM 与开发者之间缺乏协议化的协作接口**。

### 1.2 目标

设计并实现一套 **PM Agent MCP 系统**，让 PM 从"工具集合"升级为 **"可与 Claude Code 对话协作的 AI 产品经理"**。核心目标：

1. **MCP 化**：PM 以标准 MCP Server 形式暴露，Claude Code 通过 tool call 与 PM 交互
2. **上下文记忆**：PM 拥有项目级记忆（历史审查、已修复问题、设计规范），支持多轮对话
3. **实时可视化**：旁路 Monitor CLI 实时展示 PM 的思考过程与工具调用链，用于演示
4. **HITL 兼容**：Human 可在关键节点介入，插入新需求或调整优先级

### 1.3 设计约束

- **不替代 Claude Code**：开发工作仍由 Claude Code 完成，PM 只负责审查、讨论、验收
- **不复刻 Dev Agent**：不另建编码 Agent，避免与 Claude Code 能力重叠
- **复用现有资产**：最大程度复用 `skills/src/tools/pm.ts`、prompts、`.pm_state.json` 等既有实现
- **与现有 MCP Server 共存**：PM tools 作为新增模块接入现有 `android-dev-assist` Server，或作为独立 Server 运行

---

## 二、术语定义

| 术语 | 定义 |
|------|------|
| **PM Agent** | AI 产品经理 Agent，负责视觉审查、需求讨论、修复验收 |
| **Dev Agent** | Claude Code，负责代码编写、构建、调试 |
| **MCP** | Model Context Protocol，Anthropic 提出的 AI 工具交互标准 |
| **Monitor CLI** | 旁路终端显示器，实时展示 PM 思考与工具调用 |
| **Memory** | PM 的项目级持久化记忆，含审查历史、issue 状态、设计规范、对话上下文 |
| **Trace** | PM 执行工具时的结构化操作日志，用于 Monitor 实时回放 |
| **HITL** | Human-In-The-Loop，人类在关键决策点介入 |

---

## 三、现有资产盘点

### 3.1 `skills/` 目录（MCP Server 相关）

| 文件/目录 | 现状 | 复用方式 |
|-----------|------|----------|
| `src/server.ts` | MCP Server v3.1.0，stdio 传输，已注册 40+ tools | PM tools 作为新增 tool 注册 |
| `src/tools/pm.ts` | 1025 行，含 `handlePmReview`、`handlePmExplore`、`handlePmCompareWithDesign`、`handlePmMarkFixed`、`handleDumpUi` | **核心复用对象**，handler 逻辑基本不变，包装为 MCP tool schema |
| `src/tools/pm.ts` 内 `PM_TOOL_REGISTRY` | PM 探索时可调用的 30+ 工具 registry | `pm_explore` 直接复用 |
| `prompts/pm_review.txt` | 2248 字节，PM 单次审查 prompt 模板 | 直接复用 |
| `prompts/pm_explore_step.txt` | 5611 字节，PM 多步探索 prompt 模板 | 直接复用 |
| `pm_session.mjs` | 59 行 MCP 客户端脚本（list / call） | 废弃或改造为 Monitor CLI 的一部分 |
| `pm_reviews/` | 历史审查报告目录，含 `explore-*.json`、`rev-*.json` | 作为 Memory 的 `reviews` 数据来源 |
| `.pm_state.json` | 修复追踪状态（`fixed: [...]`、`ignored: [...]`） | 作为 Memory 的 `fixes` 数据来源 |
| `src/utils/design-extractor.js` | VLM 调用封装（Minimax / Kimi） | `pm_discuss` 调用文本 LLM 时复用 |

### 3.2 `app/` 目录（Android 项目相关）

| 信息 | 现状 | PM Memory 关联 |
|------|------|----------------|
| 包名 | `com.example.toutiao` | `pm_explore` prompt 中已固化 |
| 主 Activity | `MainActivity` | `install_and_launch` 默认参数 |
| 设计稿 | `design/` 目录下 7 张 `.jpg` | `pm_compare_with_design` 的 `designPath` 来源 |
| 源码语言 | Kotlin，Jetpack Compose | `pm_discuss` 的 `code_hint` 需匹配 Compose 语法 |
| 频道数 | 11+（关注/推荐/热榜/深圳/小说/发现/视频/财经/军事/畅听/体育） | Memory 中按 channel 维度组织审查 |

### 3.3 现有 Prompt 模板关键信息

`pm_review.txt` 已要求 VLM 输出严格 JSON：

```json
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
```

`pm_explore_step.txt` 已包含 Y 坐标导航地图、自救协议、工具调用格式等成熟设计。

---

## 四、总体架构

### 4.1 角色与边界

```
+-------------------------------------------------------------+
|  Human (开发者 / 训练营答辩者)                                |
|  ---------------------------------------------------------  |
|  * 在 Claude Code 中编写代码、下指令                          |
|  * 在 Monitor CLI 中观察 PM 状态                             |
|  * 在关键节点通过 Claude Code 或 Monitor 介入决策             |
+-------------------------------------------------------------+
         |                                    ^
         | MCP Protocol                       | fs.watch / tail
         v                                    |
+-------------------------------------------------------------+
|  PM MCP Server (Node.js)                                    |
|  ---------------------------------------------------------  |
|  Tools:                                                      |
|   * pm_review      -- 单次审查                                |
|   * pm_explore     -- 多步自主探索                            |
|   * pm_discuss     -- 基于记忆上下文的讨论（新增）              |
|   * pm_check       -- 验证指定 issue 是否修复（新增）           |
|   * pm_compare     -- 设计稿对比                              |
|   * pm_mark_fixed  -- 标记修复状态                            |
|   * dump_ui        -- UI 层级导出                             |
|                                                              |
|  Memory: pm_memory.json + discussion_history.json            |
|  Trace:  pm_trace.jsonl（每步结构化日志）                     |
+-------------------------------------------------------------+
         |
         | 设备操作 / VLM 调用
         v
+-------------------------------------------------------------+
|  Android 模拟器 / 真机 (com.example.toutiao)                  |
|  * screenshot / tap / swipe / dump_ui                        |
|  * install_and_launch / build                                |
+-------------------------------------------------------------+

旁路显示（独立进程）：
+-------------------------------------------------------------+
|  PM Monitor CLI (Python textual)                            |
|  * 读取 pm_trace.jsonl                                       |
|  * 实时格式化展示 PM 思考、工具调用、issue 状态               |
|  * 可选：Human Override 输入（暂停/继续/插入需求）            |
+-------------------------------------------------------------+
```

### 4.2 设计原则

1. **PM 不驱动 Dev**：PM 只输出审查意见和讨论回复，不直接修改代码。Claude Code 看到 PM 的输出后，由 Human 或 Claude 自主决定如何行动。
2. **Memory 在 Server 端**：所有项目记忆（审查历史、issue 状态、设计规范、对话上下文）由 PM MCP Server 维护，Claude Code 通过 tool call 按需读取。
3. **Trace 是旁路**：PM 执行工具时写结构化日志到 `pm_trace.jsonl`，Monitor CLI 异步读取展示，不影响主流程性能。
4. **协议最小化**：PM 与 Claude Code 之间只传递结构化 JSON，不依赖自然语言协议外的隐式约定。

---

## 五、PM MCP Server 详细设计

### 5.1 Tool 清单

#### P0：现有 Tool 迁移（复用 `pm.ts` handler）

| Tool | 来源 | 变更 |
|------|------|------|
| `pm_review` | `handlePmReview` | **无逻辑变更**，仅需在 `server.ts` 的 `ListToolsRequestSchema` 中注册 schema |
| `pm_explore` | `handlePmExplore` | **无逻辑变更**，同上 |
| `pm_compare_with_design` | `handlePmCompareWithDesign` | **无逻辑变更**，同上 |
| `pm_mark_fixed` | `handlePmMarkFixed` | **无逻辑变更**，同上 |
| `dump_ui` | `handleDumpUi` | **无逻辑变更**，同上 |

> **注**：当前 `skills/src/server.ts` 的 tool list 中**尚未注册**上述 PM tools（`grep pm_review server.ts` 无结果）。`pm_session.mjs` 注释提到"Spawn fresh MCP server (with pm_review...)"，暗示存在单独编译入口或条件分支。本方案要求将 PM tools **正式注册到主 Server**，或作为独立子 Server 通过 `stdio` 并联。

#### P1：新增 Tool 设计

##### 5.1.1 `pm_discuss` -- PM 讨论（核心新增）

**定位**：Claude Code 与 PM 的"聊天接口"。PM 基于项目记忆和对话历史，回答产品相关问题。

**MCP Schema**：

```typescript
{
  name: "pm_discuss",
  description: "与项目 PM 讨论产品问题、设计规范或修复方案。PM 拥有完整的项目记忆（历史审查记录、已修复问题、设计稿规范、对话上下文），能基于多轮上下文给出具体可执行的建议。适合在收到 pm_review / pm_explore 报告后追问细节，或在开发过程中随时咨询设计规范。",
  inputSchema: {
    type: "object",
    properties: {
      question: {
        type: "string",
        description: "你的问题。例如：'ISSUE-001 应该怎么修？'、'设计稿要求 Tab 栏选中字体多大？'、'我改了 padding，你帮我看看对不对'"
      },
      context: {
        type: "string",
        description: "可选。当前开发上下文，帮助 PM 更精准回答。例如：'正在修改 HomeScreen.kt 的 ScrollableTabRow'、'刚提交了 FinanceStockIndexCard.kt 的修改'"
      },
      include_history: {
        type: "boolean",
        description: "是否携带最近对话历史（默认 true）。设为 false 可获得更独立的回答",
        default: true
      },
      include_screenshot: {
        type: "boolean",
        description: "是否让 PM 先截图当前页面再回答（默认 false）。适合问'现在看起来对吗'这类问题",
        default: false
      }
    },
    required: ["question"]
  }
}
```

**内部实现流程**：

```
1. 加载 pm_memory.json（项目级记忆）
2. 加载 discussion_history.json（最近 N 轮对话，默认 N=10）
3. 若 include_screenshot=true：
   - 调用现有 screenshot() 获取当前页面截图
   - 将截图 base64 作为 visual context 传入
4. 构造 system prompt：
   "你是 ToutiaoFeedDemo 的 AI 产品经理。项目记忆：{memory}。对话历史：{history}。
    回答要求：
    1. 基于项目记忆回答，不要编造设计规范
    2. 涉及代码修改时，给出具体文件路径、行号、修改前后对比
    3. 引用设计稿时，说明具体哪张图、哪个区域
    4. 如果用户上传了截图（include_screenshot=true），先描述截图状态，再回答问题
    5. 不确定时明确说'项目记忆中没有相关信息'，不要猜测
    6. 用工程师能秒懂的语言，避免冗长背景介绍"
5. 调用文本 LLM（复用 design-extractor.js 的 callVisionLlm 或纯文本接口）
6. 将 (question, answer) 追加到 discussion_history.json
7. 返回 answer
```

**与现有 VLM 的复用**：

- 若 `include_screenshot=false`：调用纯文本 LLM（Minimax-M2.7 / Kimi-k2.6），速度快、成本低
- 若 `include_screenshot=true`：调用视觉 LLM（Minimax-M3），复用现有 `callVisionLlm` + `smartResizeForVision`

##### 5.1.2 `pm_check` -- 修复验证（新增）

**定位**：Claude Code 修改代码后，让 PM 重新审查特定 issue 是否已修复。

**MCP Schema**：

```typescript
{
  name: "pm_check",
  description: "验证指定 issue 是否已修复。PM 会截图当前页面，对比该 issue 的原始描述，判断是否仍存在。若修复成功，自动更新 pm_memory.json 中的 issue 状态。适合在 Claude Code 修改代码后快速验证。",
  inputSchema: {
    type: "object",
    properties: {
      issue_id: {
        type: "string",
        description: "Required. 要验证的 issue ID，例如 'ISSUE-001'"
      },
      target: {
        type: "string",
        description: "可选。当前页面名称，例如 '首页推荐'、'首页财经'。不传则 PM 默认审查上一次审查的页面"
      },
      auto_mark_fixed: {
        type: "boolean",
        description: "验证通过后是否自动标记为 fixed（默认 true）",
        default: true
      }
    },
    required: ["issue_id"]
  }
}
```

**内部实现流程**：

```
1. 从 pm_memory.json 查找 issue_id 对应的原始描述
2. 若找不到 -- 返回错误
3. 截图当前页面（复用 _takeScreenshot）
4. 构造 prompt：
   "这是 issue 的原始描述：{issue}。请审查当前截图，判断该问题是否已修复。
    输出 JSON：{fixed: boolean, confidence: high/medium/low, note: string}"
5. 调用 VLM（复用 _callVision）
6. 若 fixed=true 且 auto_mark_fixed=true：
   - 更新 pm_memory.json：issue.status = "fixed"
   - 追加到 fixes 数组
   - 写 pm_trace.jsonl
7. 返回验证结果
```

##### 5.1.3 `pm_get_memory` -- 记忆查询（新增，可选）

**定位**：Claude Code 主动查询 PM 的当前记忆，了解历史审查和已修复问题。

```typescript
{
  name: "pm_get_memory",
  description: "查询 PM 的项目记忆。可获取历史审查记录、当前 open issues、已修复问题、设计规范摘要等。适合在开始新任务前了解项目当前状态。",
  inputSchema: {
    type: "object",
    properties: {
      scope: {
        type: "string",
        enum: ["overview", "open_issues", "fixed_issues", "design_specs", "last_review", "discussions"],
        description: "查询范围",
        default: "overview"
      },
      channel: {
        type: "string",
        description: "可选。按频道过滤，例如 'recommend'、'finance'"
      }
    }
  }
}
```

### 5.2 Memory 系统设计

#### 5.2.1 存储位置

| 文件 | 路径 | 说明 |
|------|------|------|
| 项目记忆主文件 | `skills/.pm_memory.json` | 核心记忆，Git 追踪（设计规范、issue 历史） |
| 对话历史 | `skills/.pm_discussions.json` | 敏感/可丢弃，Git 忽略（或只保留最近 50 轮） |
| 操作日志 | `skills/pm_trace.jsonl` | 实时追加，Git 忽略，Monitor CLI 读取 |
| 审查报告 | `skills/pm_reviews/*.json` | 已有，继续复用 |

#### 5.2.2 `pm_memory.json` Schema

```json
{
  "$schema": "./pm_memory_schema.json",
  "project": {
    "name": "ToutiaoFeedDemo",
    "package_name": "com.example.toutiao",
    "main_activity": "MainActivity",
    "version": "1.0.0"
  },
  "design_specs": {
    "sources": [
      "design/首页-推荐.jpg",
      "design/首页-财经.jpg",
      "design/视频-精选.jpg",
      "design/商城界面.jpg"
    ],
    "tokens": {
      "tab_indicator_padding": "6dp",
      "tab_selected_font": "16sp Bold",
      "tab_unselected_font": "15sp Regular",
      "tab_selected_color": "#FFD81E06",
      "card_spacing": "8dp",
      "title_font_large": "17sp Medium",
      "title_font_normal": "16sp Regular",
      "stock_index_number_font": "22sp Bold",
      "bottom_nav_height": "56dp"
    }
  },
  "reviews": [
    {
      "review_id": "rev-1780981887429",
      "timestamp": "2026-06-10T14:30:00Z",
      "tool": "pm_explore",
      "target": "首页推荐",
      "channel": "recommend",
      "overall_rating": "B",
      "issues": [
        {
          "issue_id": "ISSUE-001",
          "severity": "high",
          "category": "ui_bug",
          "description": "Tab 下划线 padding 8dp，应为 6dp",
          "location": "HomeScreen.kt:ScrollableTabRow",
          "design_ref": "design/首页-推荐.jpg",
          "status": "fixed",
          "fixed_at": "2026-06-10T14:35:00Z",
          "verified_by": "pm_check",
          "verified_at": "2026-06-10T14:36:00Z"
        }
      ],
      "positives": [
        {
          "item": "频道差异化渲染完整",
          "evidence": "热榜/财经/军事等频道均有独立组件"
        }
      ]
    }
  ],
  "issue_counter": 1,
  "current_focus": {
    "channel": "recommend",
    "page": "首页推荐",
    "last_review_id": "rev-1780981887429"
  }
}
```

> **与现有 `.pm_state.json` 的关系**：现有 `.pm_state.json` 只记录 `fixed` 和 `ignored` 列表，格式较简单。本方案将其**升级合并**为 `.pm_memory.json`，保留原有数据并扩展频道、设计规范、审查历史等维度。迁移脚本一次性执行。

#### 5.2.3 `discussion_history.json` Schema

```json
{
  "sessions": [
    {
      "session_id": "sess-1780982000000",
      "started_at": "2026-06-10T14:30:00Z",
      "context": {
        "current_file": "HomeScreen.kt",
        "current_channel": "recommend"
      },
      "messages": [
        {
          "role": "claude",
          "content": "帮我看看首页推荐有什么问题",
          "time": "14:30:00"
        },
        {
          "role": "pm_tool",
          "tool": "pm_explore",
          "result_summary": "发现 3 个 issue",
          "time": "14:32:00"
        },
        {
          "role": "claude",
          "content": "ISSUE-001 怎么修？",
          "time": "14:33:00"
        },
        {
          "role": "pm",
          "content": "改 ScrollableTabRow indicator padding 从 8dp 到 6dp...",
          "time": "14:33:05"
        }
      ]
    }
  ]
}
```

### 5.3 Trace 系统设计

#### 5.3.1 输出格式（JSON Lines）

每条 trace 是一行独立 JSON，便于 Monitor CLI 用 `readline` 或 `tail -f` 流式读取。

```json
{"ts":"2026-06-10T14:32:01.123Z","type":"tool_call","tool":"pm_explore","args":{"goal":"审查首页推荐频道"},"session_id":"sess-1780982000000"}
{"ts":"2026-06-10T14:32:02.456Z","type":"step","step":1,"total_steps":6,"action":"screenshot","detail":"/tmp/pm_step_1.png","session_id":"sess-1780982000000"}
{"ts":"2026-06-10T14:32:03.789Z","type":"step","step":1,"action":"dump_ui","detail":"127 nodes, 34 clickable","session_id":"sess-1780982000000"}
{"ts":"2026-06-10T14:32:05.012Z","type":"vlm_think","step":1,"thought":"决定下一步: tap(540, 1850) 查看视频页","model":"MiniMax-M3","tokens_in":1200,"tokens_out":80,"latency_ms":2100,"session_id":"sess-1780982000000"}
{"ts":"2026-06-10T14:32:06.345Z","type":"step","step":2,"action":"tap","detail":"x=540, y=1850, result=success","session_id":"sess-1780982000000"}
{"ts":"2026-06-10T14:32:45.678Z","type":"done","tool":"pm_explore","overall_rating":"B","issues_found":3,"issues":[{"id":"ISSUE-001","severity":"high"}],"positives_found":2,"elapsed_ms":44000,"session_id":"sess-1780982000000"}
{"ts":"2026-06-10T14:32:46.901Z","type":"memory_update","action":"append_review","review_id":"rev-1780981887429","session_id":"sess-1780982000000"}
```

#### 5.3.2 Trace 写入方式

在 `pm.ts` 的现有 handler 中插入 `logTrace()` 调用：

```typescript
// pm.ts 内部新增
import { appendFileSync } from "node:fs";

const TRACE_PATH = process.env.PM_TRACE_PATH || "./pm_trace.jsonl";

function logTrace(event: TraceEvent) {
  const line = JSON.stringify({ ...event, ts: new Date().toISOString() });
  appendFileSync(TRACE_PATH, line + "\n");
}
```

**不影响现有逻辑**：`logTrace` 是副作用，失败不阻塞主流程。

### 5.4 Prompt 工程设计

#### 5.4.1 `pm_discuss` System Prompt 模板

文件位置：`skills/prompts/pm_discuss.txt`

```text
# 角色
你是 ToutiaoFeedDemo（仿今日头条信息流 Demo）的 AI 产品经理。
项目包名：com.example.toutiao，技术栈：Kotlin + Jetpack Compose + MVI + Clean Architecture。

# 项目记忆（动态注入）
${pm_memory_summary}

# 最近对话历史（动态注入）
${discussion_history}

# 当前开发上下文（动态注入）
${context}

# 回答原则
1. 基于项目记忆回答。设计规范以 design/ 目录下的截图为准，不要编造。
2. 涉及代码修改时，给出具体文件路径、Composable 名称、属性名和修改值。
3. 引用设计稿时，说明图片文件名和视觉区域（如"顶部 Tab 栏"）。
4. 如果用户上传了截图（include_screenshot=true），先描述截图状态，再回答问题。
5. 不确定时明确说"项目记忆中没有相关信息"，不要猜测。
6. 用工程师能秒懂的语言，避免冗长背景介绍。
```

#### 5.4.2 `pm_check` System Prompt 模板

文件位置：`skills/prompts/pm_check.txt`

```text
# 角色
你是 ToutiaoFeedDemo 的 AI 产品经理，正在验证一个 issue 是否已被修复。

# 原始 Issue 描述（动态注入）
${issue_json}

# 当前页面截图（动态注入，base64）
[图片]

# 任务
审查当前截图，判断上述 issue 是否已修复。

# 输出格式（严格 JSON）
{
  "fixed": true/false,
  "confidence": "high/medium/low",
  "note": "判断理由，1-2 句话",
  "remaining_concerns": "如果 fixed=true 但有残留问题，列出；否则为空字符串"
}
```

---

## 六、Monitor CLI 详细设计

### 6.1 定位

Monitor CLI 是**旁路显示器**，不参与业务逻辑，只负责：
1. 实时读取 `pm_trace.jsonl`
2. 格式化展示 PM 的思考、工具调用、issue 状态
3. 可选：接收 Human 的简单控制指令（暂停 / 继续 / 标记）

### 6.2 技术选型

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| Python `textual` | 现代化、React-like、组件丰富、文档好 | 需 Python 环境 | **推荐**，训练营环境已有 Python |
| Node.js `blessed` | 同语言（Node.js），无需额外环境 | 较老旧，维护少，API 不友好 | 备选 |
| 纯 `stdout` print | 最简单，零依赖 | 无 TUI 效果，演示不够震撼 | 仅 fallback |

**选定：Python `textual`**。`textual` 支持：
- 实时数据表格（issue 列表）
- 滚动日志（trace 流）
- 颜色主题（high=红 / medium=黄 / low=绿）
- 键盘快捷键（`q` 退出、`p` 暂停、`r` 刷新）

### 6.3 界面布局

```
+-------------------------------------------------------------+
| Toutiao PM Monitor v0.1          Device: emulator-5554      |
+-------------------------------------------------------------+
| LIVE TRACE                                                  |
| ---------------------------------------------------------- |
| [14:32:01] tool_call  Claude -> pm_explore(首页推荐)       |
| [14:32:02] step 1/6   screenshot -> /tmp/pm_s1.png         |
| [14:32:03] step 1/6   dump_ui -> 127 nodes                 |
| [14:32:05] vlm_think  tap(540,1850) "看视频页"              |
|            model=MiniMax-M3  latency=2.1s                  |
| [14:32:06] step 2/6   tap -> success                       |
| [14:32:07] step 2/6   screenshot -> /tmp/pm_s2.png         |
| ...                                                         |
| [14:32:45] done       rating=B  issues=3  positives=2      |
| [14:32:46] memory     3 new issues appended                |
+-------------------------------------------------------------+
| OPEN ISSUES (3)                                             |
| ---------------------------------------------------------- |
| high   ISSUE-001  Tab padding 8dp->6dp   HomeScreen.kt     |
| medium ISSUE-002  时间格式未对齐         Formatters.kt     |
| low    ISSUE-003  搜索栏缺热搜词         HomeScreen.kt     |
+-------------------------------------------------------------+
| MEMORY SNAPSHOT                                             |
| ---------------------------------------------------------- |
| Reviews: 12  |  Fixed: 8  |  Open: 3  |  Discussions: 5    |
| Focus: 首页推荐  |  Last: 14:32:45                            |
+-------------------------------------------------------------+
| [p]暂停  [r]刷新  [q]退出  up/down滚动  [1-3]查看详情      |
+-------------------------------------------------------------+
```

### 6.4 实现要点

```python
# monitor/main.py (概念代码)
from textual.app import App, ComposeResult
from textual.widgets import Header, Footer, DataTable, Log, Static
from textual.reactive import reactive
import asyncio, json, os

TRACE_PATH = "../pm_trace.jsonl"

class PMMonitor(App):
    CSS_PATH = "monitor.tcss"
    issues = reactive([])
    trace_lines = reactive([])

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        yield Log(id="trace_log")
        yield DataTable(id="issue_table")
        yield Static(id="status_bar")
        yield Footer()

    async def on_mount(self) -> None:
        self.title = "Toutiao PM Monitor"
        asyncio.create_task(self.watch_trace())

    async def watch_trace(self):
        with open(TRACE_PATH, "r") as f:
            f.seek(0, 2)  # 跳到末尾
            while True:
                line = f.readline()
                if line:
                    event = json.loads(line)
                    self.process_event(event)
                else:
                    await asyncio.sleep(0.1)

    def process_event(self, event):
        if event["type"] == "done":
            self.issues = event.get("issues", [])
        # 追加到日志显示...
```

### 6.5 启动方式

```bash
# 方式 1：独立终端窗口
$ cd skills/monitor && python -m textual run main.py

# 方式 2：与 MCP Server 同目录
$ cd skills && python monitor/main.py &
$ node dist/server.js        # 启动 MCP Server

# 方式 3：npm script
$ cd skills && npm run monitor
```

---

## 七、交互时序设计

### 7.1 场景 A：审查 -> 讨论 -> 修复 -> 验证（完整闭环）

```
Claude Code                          PM MCP Server                    Android Device
     |                                    |                                |
     | 1 pm_explore({goal:"审查首页财经"})  |                                |
     | ---------------------------------> |                                |
     |                                    | 2 screenshot()                 |
     |                                    | --------------------------->   |
     |                                    | <---------------------------   |
     |                                    | 3 dump_ui()                    |
     |                                    | ... (多步循环)                  |
     |                                    |                                |
     | <----------------------------------| 4 返回：overall_rating=B,       |
     |                                    |    issues=[ISSUE-001,002,003]  |
     |                                    |                                |
     | 5 "ISSUE-001 的股票指数数字        |                                |
     |    应该怎么显示？"                  |                                |
     |                                    |                                |
     | 6 pm_discuss({                     |                                |
     |      question:"...",                |                                |
     |      context:"刚收到 pm_explore    |                                |
     |               报告，在看财经频道"    |                                |
     |    })                              |                                |
     | ---------------------------------> |                                |
     |                                    | 7 加载 memory -> 构造 prompt   |
     |                                    | -> 调文本 LLM                  |
     | <----------------------------------| 8 返回："应为 22sp Bold..."     |
     |                                    |                                |
     | 9 /edit FinanceStockIndexCard.kt   |                                |
     |    "把数字改为 22sp Bold"           |                                |
     |                                    |                                |
     | 10 pm_check({issue_id:"ISSUE-001"})|                                |
     | ---------------------------------> |                                |
     |                                    | 11 screenshot -> VLM 对比       |
     | <----------------------------------| 12 返回：fixed=true,            |
     |                                    |    auto_mark_fixed 更新 memory |
     |                                    |                                |

旁路：Monitor CLI 实时显示 2~12 的每一步
```

### 7.2 场景 B：开发中随时咨询设计规范

```
Claude: pm_discuss({
  question: "热榜的爆/热/新/辟谣分别用什么颜色？",
  context: "正在写 HotListView.kt 的 HotBadge 枚举"
})

PM: 根据 design/首页-推荐.jpg 和历史审查记录：
     热榜序号标签有 5 种状态：
     1. 爆 -- 深红底白字
     2. 热 -- 红底白字
     3. 新 -- 绿底白字
     4. 辟谣 -- 蓝底白字
     5. 普通序号（4 以后）-- 灰底黑字
     参考实现：HotListView.kt 中 HotBadge 枚举，已定义这 5 种。
```

### 7.3 场景 C：HITL 介入（Human 插入新需求）

```
Monitor: [p]暂停 [i]插入需求 [q]退出
Human 按 [i]
输入: "等等，先别修 UI。加一个新功能：商城页顶部加一个'官方商城'标签，
 参考设计稿 design/商城界面.jpg 的左上角。"

Monitor 将新需求写入 pm_trace.jsonl 的 special 通道。

Claude Code（下一轮 tool call）：
pm_discuss({question:"Human 插入了一个新需求：商城页加官方商城标签，请描述具体要求"})

PM: 建议：
    1. 位置：顶部搜索栏左侧
    2. 样式：红底白字圆角矩形，文字"官方商城"
    3. 字体：13sp Medium
    4. 文件：新建 MallOfficialBadge.kt
```

> **注**：HITL 在本方案中**不通过 MCP 协议实现**（MCP 是 Claude<->Server 双向，Human 没有直接通道）。HITL 通过 Monitor CLI 的本地输入实现，Monitor 将 Human 输入写入 `pm_trace.jsonl` 的 `human_input` 类型事件，Claude Code 在后续 tool call 中通过 `pm_discuss` 或 `pm_get_memory` 读取到 Human 的介入内容。

---

## 八、与现有系统集成方案

### 8.1 与 `skills/src/server.ts` 的集成

**方案 1：同 Server 注册（推荐）**

在 `server.ts` 的 `ListToolsRequestSchema` handler 中，新增 PM tools：

```typescript
// server.ts 中新增 import
import {
  handlePmReview,
  handlePmExplore,
  handlePmCompareWithDesign,
  handlePmMarkFixed,
  handleDumpUi,
  handlePmDiscuss,      // 新增
  handlePmCheck,        // 新增
  handlePmGetMemory,    // 新增
} from "./tools/pm.js";

// ListToolsRequestSchema 返回的 tools 数组中追加
{
  name: "pm_discuss",
  description: "...",
  inputSchema: { ... }
},
{
  name: "pm_check",
  description: "...",
  inputSchema: { ... }
},
// ... 其他 PM tools

// CallToolRequestSchema 中追加分支
if (name === "pm_discuss") return handlePmDiscuss(args);
if (name === "pm_check") return handlePmCheck(args);
```

**方案 2：独立 PM Server（stdio 并联）**

若 `server.ts` 已有 40+ tools，体积较大，可将 PM tools 拆分为独立 Server：

```typescript
// skills/src/pm-server.ts
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
const pmServer = new Server(
  { name: "android-dev-pm", version: "0.1.0" },
  { capabilities: { tools: {} } }
);
// 只注册 PM 相关 tools
```

Claude Code 的 MCP 配置中同时连接两个 Server：

```json
{
  "mcpServers": {
    "android-dev-assist": {
      "command": "node",
      "args": ["./dist/server.js"]
    },
    "android-dev-pm": {
      "command": "node",
      "args": ["./dist/pm-server.js"]
    }
  }
}
```

**结论**：推荐**方案 1**（同 Server），因为 PM tools 依赖现有 `pm.ts` 中的 VLM 调用、截图、设备操作等基础设施，同进程复用最简单。

### 8.2 与 `pm.ts` 的集成

在 `pm.ts` 中：

1. **保留所有现有 handler**：`handlePmReview`、`handlePmExplore`、`handlePmCompareWithDesign`、`handlePmMarkFixed`、`handleDumpUi` 逻辑不变
2. **新增 handler**：`handlePmDiscuss`、`handlePmCheck`、`handlePmGetMemory`
3. **插入 Trace**：在每个 handler 入口和关键步骤调用 `logTrace()`
4. **接入 Memory**：新增 `loadPmMemory()`、`savePmMemory()` 辅助函数

### 8.3 与 `.pm_state.json` 的迁移

现有 `.pm_state.json`：

```json
{
  "fixed": [{"issue_id": "...", "note": "...", "fixed_at": "..."}],
  "ignored": []
}
```

迁移到 `.pm_memory.json`：

```bash
# 一次性迁移脚本（Node.js）
node -e "
const fs = require('fs');
const old = JSON.parse(fs.readFileSync('.pm_state.json', 'utf-8'));
const memory = {
  project: { name: 'ToutiaoFeedDemo', package_name: 'com.example.toutiao' },
  design_specs: { sources: [...], tokens: {...} },
  reviews: [],
  issue_counter: old.fixed.length,
  current_focus: { channel: 'recommend', page: '首页推荐' }
};
fs.writeFileSync('.pm_memory.json', JSON.stringify(memory, null, 2));
console.log('Migrated:', old.fixed.length, 'fixed issues');
"
```

### 8.4 与 `design/` 目录的关联

PM Memory 中的 `design_specs.sources` 自动扫描 `design/` 目录：

```typescript
function scanDesignFiles(): string[] {
  const designDir = path.resolve(_findProjectRoot(), "design");
  return fs.readdirSync(designDir)
    .filter(f => f.endsWith(".jpg") || f.endsWith(".png"))
    .map(f => path.join("design", f));
}
```

`pm_compare_with_design` 的 `designPath` 参数优先从 Memory 中匹配（模糊匹配文件名）。

---

## 九、演示剧本（训练营验收用）

### 9.1 剧本 A：完整闭环（3 分钟，首选）

**画面布局**：左侧 Claude Code 终端，右侧 PM Monitor CLI。

| 时间 | Claude Code（左） | PM Monitor（右） |
|------|-------------------|------------------|
| 0:00 | `> pm_explore({goal:"审查首页财经频道"})` | `[14:32:01] tool_call pm_explore` |
| 0:05 | 等待... | `[14:32:02] step 1/6 screenshot` |
| 0:10 |  | `[14:32:03] dump_ui -> 134 nodes` |
| 0:15 |  | `[14:32:05] vlm_think "发现股票指数区域"` |
| 0:30 | 返回：3 issues found | `[14:32:45] done rating=B issues=3` |
| 0:35 | `> ISSUE-001 的股票指数数字应该怎么显示？` |  |
| 0:40 | `> pm_discuss({question:"...", context:"..."})` | `[14:32:46] tool_call pm_discuss` |
| 0:45 | 返回："应为 22sp Bold..." | `[14:32:48] text_llm -> 22sp Bold` |
| 0:50 | `> /edit FinanceStockIndexCard.kt` |  |
| 0:55 | （Claude 修改代码，保存文件） | `[14:32:50] file_change: FinanceStockIndexCard.kt` |
| 1:00 | `> pm_check({issue_id:"ISSUE-001"})` | `[14:32:51] tool_call pm_check` |
| 1:05 |  | `[14:32:52] screenshot` |
| 1:10 | 返回：fixed=true, confidence=high | `[14:32:54] ISSUE-001 verified fixed` |
| 1:15 |  | `[14:32:55] memory updated` |
| 1:20 | `> 还有别的问题吗？` |  |

**讲解词要点**：
- "这不是一次性的工具调用，而是**持续协作**。PM 有记忆，记得之前审查过什么、修好了什么。"
- "Claude Code 不需要理解设计稿，它问 PM，PM 基于 design/ 目录的截图给出规范。"
- "Monitor 旁路显示让整个过程透明，这是 AI 工程化的关键——**可观测**。"

### 9.2 剧本 B：设计规范咨询（1 分钟）

**场景**：Claude Code 在写热榜频道，不确定徽标颜色。

```
Claude: pm_discuss({
  question: "热榜的爆/热/新/辟谣分别用什么颜色？",
  context: "正在写 HotListView.kt 的 HotBadge 枚举"
})

PM: 根据 design/首页-推荐.jpg 和历史审查：
     - 爆：#FFD81E06 深红底白字
     - 热：#FFFF4444 红底白字
     - 新：#FF4CAF50 绿底白字
     - 辟谣：#FF2196F3 蓝底白字
     参考已有实现：HotListView.kt 中 HotBadge 枚举已定义，可复用。
```

### 9.3 剧本 C：HITL 插入新需求（1 分钟）

**场景**：Human 在 Monitor 中看到 PM 在审查商城页，突然想加一个新功能。

```
Monitor: [p]暂停 [i]插入需求 [q]退出
Human 按 [i]
输入: "商城页加个'领券'按钮，参考淘宝风格，红底白字"

Claude Code（下一轮）：
pm_discuss({question:"Human 插入需求：商城页加领券按钮，有什么建议？"})

PM: 建议：
    1. 位置：顶部搜索栏右侧或商品卡片右下角
    2. 样式：红底白字圆角矩形（与头条主色一致）
    3. 尺寸：宽 72dp x 高 28dp，文字 12sp Bold
    4. 文件：新建 MallCouponButton.kt
```

---

## 十、实施路径

### Phase 1：基础设施（2h）

- [ ] 创建 `.pm_memory.json` Schema 与迁移脚本
- [ ] 在 `pm.ts` 中新增 `logTrace()` 与 `pm_trace.jsonl` 写入
- [ ] 在 `pm.ts` 中新增 `loadPmMemory()` / `savePmMemory()` 辅助函数
- [ ] 新建 `skills/prompts/pm_discuss.txt`、`pm_check.txt`

### Phase 2：核心 Tool 实现（3h）

- [ ] 实现 `handlePmDiscuss`（文本 LLM 调用 + Memory 加载 + History 维护）
- [ ] 实现 `handlePmCheck`（截图 + VLM 对比 + 自动标记 fixed）
- [ ] 实现 `handlePmGetMemory`（按 scope 查询 Memory）
- [ ] 在 `server.ts` 中注册上述 tools

### Phase 3：Monitor CLI（2h）

- [ ] 新建 `skills/monitor/` 目录
- [ ] 用 `textual` 实现基础界面（Log + DataTable + StatusBar）
- [ ] 实现 `watch_trace()` 异步读取 `pm_trace.jsonl`
- [ ] 实现事件处理与界面刷新

### Phase 4：集成测试（2h）

- [ ] 端到端测试：Claude Code -> pm_explore -> pm_discuss -> 代码修改 -> pm_check
- [ ] Monitor CLI 实时显示验证
- [ ] HITL 插入需求验证

### Phase 5：演示打磨（2h）

- [ ] 固化 3 个演示剧本
- [ ] 调整 prompt 使输出更稳定
- [ ] 准备讲解词

**总计：约 11 小时，可分 2 天完成。**

---

## 十一、风险评估

| 风险 | 影响 | 概率 | 应对 |
|------|------|------|------|
| VLM 输出不稳定（JSON 解析失败） | PM 返回格式错误，Claude Code 无法理解 | 中 | 复用 `pm.ts` 中已成熟的 `_parseJsonFromVision`，加 retry |
| `pm_discuss` 上下文过长超限 | LLM token 超限，讨论中断 | 中 | History 限制 N=10 轮，Memory 做摘要压缩 |
| Monitor CLI 读取 trace 延迟 | 演示时显示不同步 | 低 | `appendFileSync` + `readline` 是毫秒级，可接受 |
| Claude Code 不主动调用 PM tools | 演示时 Claude 不用 PM | 中 | 在 Claude Code 的 project 级 prompt 中注入"优先调用 pm_review/pm_discuss" |
| 文件系统监控失效 | 代码修改后 PM 无法自动感知 | 低 | fallback：Claude Code 手动调用 `pm_check` |

---

## 十二、附录

### A. 文件结构（新增与变更）

```
skills/
├── src/
│   ├── server.ts                    # 变更：注册 PM tools
│   ├── tools/
│   │   ├── pm.ts                    # 变更：新增 discuss/check/memory + trace
│   │   └── ...
│   └── ...
├── prompts/
│   ├── pm_review.txt                # 已有
│   ├── pm_explore_step.txt          # 已有
│   ├── pm_discuss.txt               # 新增
│   └── pm_check.txt                 # 新增
├── monitor/                         # 新增
│   ├── main.py
│   ├── monitor.tcss
│   └── requirements.txt
├── pm_reviews/                      # 已有
│   ├── explore-*.json
│   └── rev-*.json
├── .pm_state.json                   # 废弃（迁移后删除）
├── .pm_memory.json                  # 新增（核心记忆）
├── .pm_discussions.json             # 新增（对话历史）
├── pm_trace.jsonl                   # 新增（操作日志，gitignore）
└── pm_session.mjs                   # 废弃（功能被 MCP Server 替代）
```

### B. Claude Code MCP 配置示例

```json
{
  "mcpServers": {
    "android-dev-assist": {
      "command": "node",
      "args": ["/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/skills/dist/server.js"],
      "env": {
        "MINIMAX_API_KEY": "sk-...",
        "PM_CHECKLIST_PATH": "./pm_checklist_toutiao.md",
        "PM_TRACE_PATH": "./pm_trace.jsonl"
      }
    }
  }
}
```

### C. 环境变量清单

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MINIMAX_API_KEY` | -- | VLM / LLM API Key |
| `PM_CHECKLIST_PATH` | `./pm_checklist_toutiao.md` | PM 审查标准库路径 |
| `PM_PROMPT_PATH` | `./skills/prompts/pm_review.txt` | pm_review prompt 路径 |
| `PM_EXPLORE_PROMPT_PATH` | `./skills/prompts/pm_explore_step.txt` | pm_explore prompt 路径 |
| `PM_TRACE_PATH` | `./pm_trace.jsonl` | Trace 日志路径 |
| `PM_MEMORY_PATH` | `./.pm_memory.json` | Memory 文件路径 |
| `PM_DISCUSSION_PATH` | `./.pm_discussions.json` | 对话历史路径 |
| `VISION_MODEL` | `MiniMax-M3` | 视觉模型 |
| `TEXT_MODEL` | `MiniMax-M2.7` | 文本模型（pm_discuss 用）|

---

*本文档与 `AGENTS.md`、`docs/02_技术设计文档.md` 互补。若描述冲突，以本文档为准。*
