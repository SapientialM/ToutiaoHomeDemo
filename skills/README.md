# Android Dev Assist — MCP Server

> 这是 `ToutiaoFeedDemo` 项目的 **MCP Server**（Model Context Protocol），为 Claude Code 提供 Android 开发工具集 + **PM Agent**（AI 产品经理）能力。

## 快速启动

```bash
cd skills
npm install          # 首次
npm run build        # 编译 TypeScript → dist/
node dist/server.js  # 启动 MCP Server（stdio 模式）
```

Monitor CLI（旁路展示 PM 思考过程，另开终端）：
```bash
cd skills
npm run monitor      # 或 cd monitor && python3 main.py
```

## 项目定位

```
Claude Code (Dev Agent)  ←──MCP──→  android-dev-assist Server  ←──ADB/VLM──→  Android 模拟器/真机
                                              │
                                              ↓
                                    PM Agent (pm_review/pm_explore/pm_discuss/pm_check)
                                              │
                                              ↓
                                    .pm_memory.json (项目记忆)
                                    pm_trace.jsonl (旁路日志)
```

**设计原则**：
- PM 不替代 Claude Code：PM 只输出审查意见和讨论回复，不直接改代码
- Memory 在 Server 端：所有项目记忆由 PM MCP Server 维护
- Trace 是旁路：不影响主流程性能，Monitor CLI 异步读取

## 文件结构

```
skills/
├── src/server.ts              # MCP Server 入口（注册 57 个 tools）
├── src/tools/
│   ├── pm.ts                  # PM Agent 核心（review/explore/discuss/check/memory）
│   ├── interaction.ts         # tap/swipe/input_text/press_key
│   ├── screenshot.ts          # screenshot
│   ├── screenshot-region.ts   # screenshot_region
│   ├── hierarchy.ts           # dump_hierarchy/find_element/wait_for_element
│   ├── analyze.ts             # analyze_screenshot（VLM 分析）
│   ├── compare.ts             # compare_screenshots（设计稿对比）
│   ├── vision-action.ts       # vision_action（自然语言驱动 UI）
│   ├── design-spec.ts         # extract_design_spec/tokens/components/design_to_compose
│   ├── build-deploy.ts        # build/install_and_launch/build_deploy
│   ├── logs.ts                # get_logs/clear_logs
│   ├── logcat-search.ts       # logcat_search/parse_crash
│   ├── device-management.ts   # list_devices/device_info/shell_command/record_screen
│   ├── app-management.ts      # list_apps/app_info/uninstall/clear_data/stop_app
│   ├── performance-monitor.ts # performance_metrics/measure_app_launch
│   ├── device-control.ts      # set_orientation/set_gps/animation_scale
│   ├── code-quality.ts        # code_quality
│   ├── ui-test.ts             # ui_test/regression_test
│   ├── project-report.ts      # project_report
│   ├── file-operations.ts     # push_file/pull_file
│   ├── network-debug.ts       # network_state/set_network
│   ├── apk-metadata.ts        # apk_metadata
│   ├── verify.ts              # verify_ui
│   └── launch-speed.ts        # measure_app_launch
├── src/utils/
│   ├── design-extractor.ts    # VLM/LLM 调用封装（Minimax）
│   ├── adb.ts                 # ADB 封装
│   └── ...
├── prompts/
│   ├── pm_review.txt          # pm_review VLM prompt
│   ├── pm_explore_step.txt    # pm_explore 每步 VLM prompt
│   ├── pm_discuss.txt         # pm_discuss system prompt 模板
│   └── pm_check.txt           # pm_check system prompt 模板
├── monitor/
│   └── main.py                # Monitor CLI（textual TUI）
├── dist/                      # 编译产物（npm run build 生成）
├── pm_reviews/                # 历史审查报告（explore-*.json / rev-*.json）
├── .pm_memory.json            # 项目记忆（审查历史、issue、设计规范）⭐
├── .pm_discussions.json       # 对话历史
├── pm_trace.jsonl             # 旁路 trace 日志（Monitor 读取）⭐
├── .pm_state.json             # 旧版修复状态（兼容保留）
├── migrate-pm-state.mjs       # .pm_state.json → .pm_memory.json 迁移脚本
└── package.json
```

## PM Agent Tool 清单

### 现有 Tool（P0）

| Tool | 用途 | 示例 |
|------|------|------|
| `pm_review` | 单次截图+VLM审查 | `pm_review({ target: "首页推荐" })` |
| `pm_explore` | 多步自主探索（6-12步） | `pm_explore({ goal: "审查首页财经", maxSteps: 8 })` |
| `pm_compare_with_design` | 设计稿 vs 实现对比 | `pm_compare_with_design({ designPath: "design/首页-推荐.jpg" })` |
| `pm_mark_fixed` | 标记 issue 状态 | `pm_mark_fixed({ issueId: "ISSUE-001", action: "fixed" })` |
| `dump_ui` | UI 层级结构化导出 | `dump_ui({})` |

### 新增 Tool（P1）⭐

| Tool | 用途 | 示例 |
|------|------|------|
| `pm_discuss` | **与 PM 讨论产品问题/设计规范** | `pm_discuss({ question: "Tab padding 应该是多少？", context: "正在改 HomeScreen.kt" })` |
| `pm_check` | **验证 issue 是否已修复** | `pm_check({ issue_id: "ISSUE-001", auto_mark_fixed: true })` |
| `pm_get_memory` | **查询 PM 项目记忆** | `pm_get_memory({ scope: "open_issues" })` |

### 完整闭环工作流

```
1. pm_explore({goal:"审查首页财经"})     → 返回 issues=[ISSUE-001, ISSUE-002]
2. pm_discuss({question:"ISSUE-001 怎么修？"})  → 返回具体修改建议
3. [Claude Code 修改代码]
4. pm_check({issue_id:"ISSUE-001"})      → 返回 fixed=true, auto_mark_fixed 更新 memory
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MINIMAX_API_KEY` | — | **必填**。VLM/LLM API Key |
| `VISION_MODEL` | `MiniMax-M3` | 视觉模型（pm_review/pm_explore/pm_check） |
| `TEXT_MODEL` | `MiniMax-M2.7` | 文本模型（pm_discuss） |
| `PM_TRACE_PATH` | `./pm_trace.jsonl` | Trace 日志路径 |
| `PM_MEMORY_PATH` | `./.pm_memory.json` | Memory 文件路径 |
| `PM_DISCUSSION_PATH` | `./.pm_discussions.json` | 对话历史路径 |
| `PM_CHECKLIST_PATH` | `./pm_checklist_toutiao.md` | PM 审查标准库 |
| `PM_PROMPT_PATH` | `./skills/prompts/pm_review.txt` | pm_review prompt |
| `PM_EXPLORE_PROMPT_PATH` | `./skills/prompts/pm_explore_step.txt` | pm_explore prompt |

## Monitor CLI

**定位**：旁路终端显示器，实时展示 PM 思考过程与工具调用链。

```bash
# 方式 1
cd skills/monitor && python3 main.py

# 方式 2
cd skills && npm run monitor
```

界面布局：
- **LIVE TRACE**：实时 trace 流（tool_call / step / vlm_think / done / memory_update）
- **OPEN ISSUES**：当前未修复 issue 表格（按 severity 着色）
- **MEMORY SNAPSHOT**：Reviews / Fixed / Open / Focus 统计

快捷键：`q` 退出

## Memory Schema

`.pm_memory.json` 核心结构：

```json
{
  "project": { "name": "ToutiaoFeedDemo", "package_name": "com.example.toutiao" },
  "design_specs": {
    "sources": ["design/首页-推荐.jpg", ...],
    "tokens": { "tab_indicator_padding": "6dp", ... }
  },
  "reviews": [
    {
      "review_id": "rev-xxx",
      "tool": "pm_explore",
      "target": "首页推荐",
      "overall_rating": "B",
      "issues": [
        {
          "issue_id": "ISSUE-001",
          "severity": "high",
          "status": "open|fixed|ignored",
          "description": "..."
        }
      ],
      "positives": [...]
    }
  ],
  "issue_counter": 1,
  "current_focus": { "channel": "recommend", "page": "首页推荐" }
}
```

## 开发规范

### 新增 Tool
1. 在 `src/tools/<module>.ts` 实现 handler
2. 在 `src/server.ts` **两处**注册：
   - `ListToolsRequestSchema` 的 `tools` 数组里加 schema
   - `CallToolRequestSchema` 的 switch 里加分支
3. 如果 handler 属于 PM 系列，在 `pm.ts` 中实现，并在 `pm.ts` 入口处加 `logTrace`

### 修改 PM Tool
- `pm.ts` 里的 handler 逻辑变更后，**同步检查** `.pm_memory.json` 的读写逻辑
- Trace 日志是旁路：失败不阻塞主流程，用 `try { logTrace(...) } catch { /* ignore */ }`

### 构建
```bash
npm run build    # tsup 编译，输出到 dist/
npm run typecheck # tsc --noEmit 类型检查
```

## 注意事项

- **不要提交敏感信息**：`.env`、真实 API Key、产物 `dist/` 都不应提交
- **Trace 文件**：`pm_trace.jsonl` 实时追加、体积会增长，已加入 `.gitignore`
- **Memory 文件**：`.pm_memory.json` 包含项目级记忆，建议 Git 追踪（不含敏感信息）
- **对话历史**：`.pm_discussions.json` 可能包含敏感内容，已加入 `.gitignore`
