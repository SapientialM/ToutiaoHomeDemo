# ToutiaoFeedDemo 优化路线图 v2.0

> 目标：借助 MCP Skill（android-dev-assist）从还原度、细节、流畅性、技术工程化、代码规范化五个维度，系统性提升 APP 质量，为字节跳动客户端工程训练营答辩做准备。
> 
> 时间：2026-06-05 ~ 2026-06-10（6天）
> 工具：MCP Skill 31个工具（截图分析、性能测量、代码质量、UI测试等）

---

## 一、评估体系（五维评分）

| 维度 | 权重 | 评估方式 | 目标分数 |
|------|------|----------|----------|
| **还原度** | 25% | 截图对比（analyze_screenshot / compare_screenshots） | 90+ |
| **细节** | 20% | UI元素检查（verify_ui / vision_action） | 90+ |
| **流畅性** | 25% | 性能测量（measure_app_launch / performance_metrics） | 启动<1s，滑动60fps |
| **技术工程化** | 15% | CI/CD + 自动化测试 + 项目报告 | 完整流水线 |
| **代码规范化** | 15% | ktlint/detekt + 代码质量报告 | 0 error，warning<10 |

---

## 二、Phase 1：还原度与细节优化（Day 1-2）

### 目标
让 APP 视觉和交互尽可能接近真实今日头条。

### MCP 工具组合
- `screenshot` → `analyze_screenshot`：AI分析当前UI与头条的差异
- `compare_screenshots`：与真实头条截图对比
- `vision_action`：自动点击、滑动，测试交互流程
- `verify_ui`：检查颜色、文字、布局

### 任务清单

#### 1.1 首页信息流还原
- [ ] **顶部状态栏**：检查时间、电池、信号图标位置和样式
- [ ] **搜索栏**：对比热搜词样式、搜索按钮颜色、圆角大小
- [ ] **Tab栏**：下划线宽度、选中/未选中字体差异、切换动画
- [ ] **卡片布局**：
  - TextTop：置顶标签样式、来源/评论/时间排版
  - LeftTextRightImage：图片圆角、标题行数、间距
  - LargeImage：大图比例、标题位置
  - Video：播放按钮样式、时长标签、作者信息
- [ ] **底部导航**：图标选中态颜色、文字大小、红点提示

#### 1.2 交互细节
- [ ] **下拉刷新**：刷新指示器样式、触发阈值、收回动画
- [ ] **加载更多**：底部 Loading 样式、无更多数据提示
- [ ] **Tab切换**：切换时是否重置列表、是否有切换动画
- [ ] **卡片点击**：点击反馈（水波纹/高亮）、跳转详情页
- [ ] **搜索交互**：搜索历史、热搜榜、搜索结果展示

#### 1.3 其他页面
- [ ] **视频页**：列表布局、播放按钮、时长显示
- [ ] **搜索页**：搜索建议、历史记录、热门搜索
- [ ] **任务页**：任务列表、进度展示、奖励提示
- [ ] **我的页**：登录状态、菜单列表、设置入口

### 验收标准
- `analyze_screenshot` 分析报告：问题数 < 5 个
- `compare_screenshots` 与真实头条对比：相似度 > 85%

---

## 三、Phase 2：流畅性优化（Day 2-3）

### 目标
启动速度 < 1s，滑动流畅 60fps，无卡顿。

### MCP 工具组合
- `measure_app_launch`：测量冷启动/热启动速度
- `performance_metrics`：监控 CPU/内存/FPS
- `record_screen`：录屏分析滑动流畅度
- `get_logs`：检查是否有卡顿日志（Choreographer skipped frames）

### 任务清单

#### 2.1 启动速度优化
- [ ] **基线测量**：`measure_app_launch` 冷启动 3 次，记录 TTID/TTFD
- [ ] **Application优化**：
  - 检查 Application.onCreate 耗时
  - 延迟初始化非必要组件（Hilt、Timber等）
  - 使用 ContentProvider 或 Startup 库优化启动流程
- [ ] **MainActivity优化**：
  - 减少首次布局层级
  - 延迟加载非首屏数据
  - 使用 SplashScreen API
- [ ] **验证**：优化后冷启动 TTID < 1000ms

#### 2.2 滑动流畅度优化
- [ ] **FPS监控**：滑动时 `performance_metrics` 检查 FPS
- [ ] **Compose优化**：
  - 检查重组次数（@Stable/@Immutable）
  - 避免在 LazyColumn item 中创建对象
  - 使用 remember/derivedStateOf 缓存计算
- [ ] **图片优化**：
  - 限制图片尺寸（不超过屏幕宽度）
  - 使用 Coil 的 memoryCachePolicy
  - 占位图和错误图
- [ ] **验证**：滑动时 FPS > 55，无掉帧

#### 2.3 内存优化
- [ ] **内存泄漏检查**：`performance_metrics` 监控内存增长
- [ ] **大对象检测**：检查是否有 Bitmap 未回收
- [ ] **验证**：连续使用 10 分钟，内存增长 < 20MB

### 验收标准
- 冷启动 TTID < 1000ms（A级评分）
- 滑动 FPS > 55
- 内存稳定，无泄漏

---

## 四、Phase 3：技术工程化（Day 4-5）

### 目标
建立完整的 CI/CD 流水线和自动化测试体系。

### MCP 工具组合
- `build_deploy`：一键构建部署
- `code_quality`：代码质量检查
- `run_tests`：运行单元测试
- `ui_test`：UI自动化测试
- `regression_test`：回归测试
- `project_report`：生成项目报告

### 任务清单

#### 3.1 GitHub 工作流搭建
- [ ] **分支保护**：
  - main 分支禁止直接 push
  - PR 必须至少 1 个 Review Approval
  - 强制通过 CI 检查才能合并
- [ ] **PR模板**：包含"改动说明/影响范围/测试方式/截图"
- [ ] **Issue模板**：Bug Report / Feature Request

#### 3.2 GitHub Actions CI/CD
- [ ] **CI流水线**（.github/workflows/ci.yml）：
  - 触发条件：PR、push 到 main
  - 步骤：
    1. Checkout 代码
    2. 设置 JDK 17
    3. `./gradlew assembleDebug`
    4. `./gradlew ktlintCheck`（如已配置）
    5. `./gradlew test`
    6. 上传 APK 产物
- [ ] **Release流水线**：
  - 自动打 Tag（v1.0.0）
  - 生成 CHANGELOG.md
  - 发布 GitHub Release（含 APK）

#### 3.3 自动化测试
- [ ] **单元测试**：
  - ViewModel 测试（Mock Repository）
  - Mapper 测试（DTO ↔ Entity ↔ Domain）
  - Repository 测试（Mock DataSource）
- [ ] **UI测试**：
  - `ui_test`：首页加载 → Tab切换 → 下拉刷新 → 加载更多
  - `regression_test`：每次提交后自动运行
- [ ] **性能测试**：
  - `measure_app_launch`：每次 Release 前测量启动速度
  - `performance_metrics`：检查内存和 FPS

#### 3.4 项目报告
- [ ] `project_report`：生成项目综合报告
- [ ] 包含：代码行数、架构分析、依赖统计、质量评分

### 验收标准
- PR 必须通过 CI 才能合并
- 每次 push 自动运行测试
- Release 自动打 Tag + 生成 CHANGELOG

---

## 五、Phase 4：代码规范化（Day 5-6）

### 目标
代码风格统一，无规范错误，架构清晰。

### MCP 工具组合
- `code_quality`：ktlint 检查 + 复杂度分析
- `project_report`：架构分析
- `get_logs`：检查编译日志

### 任务清单

#### 4.1 ktlint 配置
- [ ] 添加 ktlint Gradle 插件
- [ ] 配置 .editorconfig（与官方 Kotlin 规范一致）
- [ ] 运行 `code_quality --fix` 自动修复问题
- [ ] 手动修复剩余问题

#### 4.2 detekt 配置
- [ ] 添加 detekt Gradle 插件
- [ ] 配置 detekt.yml（复杂度阈值、命名规范）
- [ ] 运行 detekt，修复所有 error
- [ ] 控制 warning < 10 个

#### 4.3 架构检查
- [ ] **分层检查**：
  - Domain 层不依赖 Android 框架
  - Presentation 层不直接引用 Data 层
  - Data 层实现 Domain 接口
- [ ] **依赖检查**：
  - 无循环依赖
  - 无未使用的依赖
- [ ] **命名规范**：
  - 类名 PascalCase
  - 函数/变量 camelCase
  - 常量 UPPER_SNAKE_CASE

#### 4.4 文档完善
- [ ] README.md：更新项目截图、功能列表、构建指南
- [ ] AGENTS.md：更新最新状态
- [ ] 技术文档：补充架构图、数据流图
- [ ] 答辩准备：PPT、演示脚本、常见问题

### 验收标准
- `code_quality`：0 error，warning < 10
- `project_report`：架构评分 A
- 编译无警告

---

## 六、执行计划（甘特图）

```
Day 1 (6/5)  Day 2 (6/6)  Day 3 (6/7)  Day 4 (6/8)  Day 5 (6/9)  Day 6 (6/10)
├───────────┼───────────┼───────────┼───────────┼───────────┼───────────┤
│ Phase 1   │ Phase 1   │ Phase 2   │ Phase 3   │ Phase 3   │ Phase 4   │
│ 还原度    │ 细节优化  │ 流畅性    │ 工程化    │ 工程化    │ 规范化    │
│ + 截图    │ + 交互    │ + 性能    │ + CI/CD   │ + 测试    │ + 文档    │
├───────────┼───────────┼───────────┼───────────┼───────────┼───────────┤
│ screenshot│ vision_   │ measure_  │ GitHub    │ ui_test   │ ktlint    │
│ analyze_  │ action    │ app_launch│ Actions   │ regression│ detekt    │
│ screenshot│ verify_ui │ perform-  │ setup     │ _test     │ code_     │
│ compare_  │           │ ance_     │           │           │ quality   │
│ screenshots│          │ metrics   │           │           │           │
└───────────┴───────────┴───────────┴───────────┴───────────┴───────────┘
```

---

## 七、每日检查清单

### Day 1 检查点
- [ ] 首页截图与头条对比完成
- [ ] analyze_screenshot 报告生成
- [ ] 问题清单整理（优先级排序）

### Day 2 检查点
- [ ] 交互细节测试完成（vision_action）
- [ ] 所有页面截图留存
- [ ] Phase 1 验收通过

### Day 3 检查点
- [ ] 启动速度基线测量完成
- [ ] 优化后启动速度 < 1s
- [ ] FPS 测试通过

### Day 4 检查点
- [ ] GitHub Actions 配置完成
- [ ] CI 流水线运行成功
- [ ] 分支保护启用

### Day 5 检查点
- [ ] UI 自动化测试编写完成
- [ ] 回归测试通过
- [ ] 项目报告生成

### Day 6 检查点
- [ ] ktlint/detekt 0 error
- [ ] 文档全部更新
- [ ] 答辩材料准备完毕

---

## 八、风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| MCP 工具执行失败 | 无法自动化评估 | 准备手动检查清单作为备选 |
| 性能优化效果不明显 | 启动速度仍 > 1s | 聚焦 Application.onCreate 延迟初始化 |
| CI/CD 配置复杂 | 时间不够 | 使用 GitHub Actions 模板，简化配置 |
| ktlint 问题太多 | 无法全部修复 | 先修复 error，warning 分批次处理 |

---

## 九、验收标准（最终交付）

### 必须完成
- [ ] 还原度：analyze_screenshot 问题 < 5 个
- [ ] 流畅性：冷启动 < 1s，滑动 FPS > 55
- [ ] 工程化：CI/CD 流水线运行成功
- [ ] 规范化：ktlint 0 error

### 加分项
- [ ] 热启动 < 200ms
- [ ] UI 自动化测试覆盖主要流程
- [ ] 项目报告评分 A
- [ ] GitHub Release 发布

---

*文档版本：v2.0*
*最后更新：2026-06-05*
*作者：AndroidDev-Assist Team*
