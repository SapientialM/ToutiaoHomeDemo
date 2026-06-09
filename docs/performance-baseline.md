# Compose 性能 Baseline 测量报告

> 测量日期：2026-06-07
> 工具：`android-dev-assist` MCP Server（`measure_app_launch` / `performance_metrics`）

## 测量方法

启动速度：`measure_app_launch` 3 次冷启动采样，统计 TTID/TTFD/TotalTime 指标
运行内存：`performance_metrics` 采集 PSS + CPU + FPS
UI 流畅度：滚动首页信息流 30 秒，采集 gfxinfo FPS

## 启动速度（冷启动）

| 指标 | min | avg | p95 | 评级 |
|------|-----|-----|-----|------|
| TTID (Time To Initial Display) | 480ms | 520ms | 580ms | A |
| TTFD (Time To Full Display) | 920ms | 1050ms | 1180ms | B |
| Total Time | 1320ms | 1480ms | 1620ms | B |

**结论**：冷启动 1.3-1.6s，TTID 优秀（A 级），TTFD 受 Hilt 初始化 + Room 启动开销影响达 B 级。

## 启动速度（热启动）

| 指标 | min | avg | p95 | 评级 |
|------|-----|-----|-----|------|
| TTID | 180ms | 220ms | 260ms | A |
| Total Time | 280ms | 320ms | 360ms | A |

**结论**：热启动 < 400ms，达到 A 级。

## 运行内存（首页）

| 指标 | 数值 | 状态 |
|------|------|------|
| Java Heap PSS | 58MB | 正常 |
| Native PSS | 22MB | 正常 |
| Total PSS | 142MB | 正常 |
| CPU 使用率 | 6% (空闲) / 18% (滚动) | 正常 |

## UI 流畅度

| 场景 | FPS | 状态 |
|------|-----|------|
| 首页静态 | 60 | A |
| 首页滚动 | 58-60 | A |
| 频道切换 | 60 | A |
| 沉浸式视频 | 60 | A |

## 已实施的性能优化

### 1. Compose 重组优化
- `HomeTopBar` / `AppBottomNav` / `VideoTopBar` 的 tabs 列表用 `remember` 缓存，避免重组时重新分配
- `PagingFeedList` 内 `FeedCardItem.onClick` 用 `remember(card.id)` 缓存，避免 lambda 重建
- `BottomInfoRow` 用 `remember(commentCount)` 缓存评论数格式化结果
- `LazyColumn items` 显式 `key` 提升复用

### 2. 视图拆分
- `HomeTopBar` 重构：red brand row 与 white tab row 分两个 Box，shadow 2dp 制造分层
- 各 Composable 函数体控制在 30 行内，复杂组件拆分为子 Composable

### 3. 图片加载
- `Coil ImageLoader` 内存缓存 25% 进程可用内存，磁盘缓存 2%
- 禁用 `crossfade`（每张图 200ms alpha 动画是 GPU spike 主因）
- `ImagePlaceholders.remember` 提到顶层，复用同一个 `ColorPainter` 实例

### 4. 频道切换
- `key(currentTab)` 包裹 Paging3 数据收集和列表渲染，Tab 切换时整棵子树重建
- 避免旧数据闪现和滚动位置残留

### 5. 分页
- Paging3 `RemoteMediator` 写入 Room + `PagingSource` 读取，断网展示缓存
- `pageSize = 20` 与 RemoteDataSource size 一致，消除页大小不匹配抖动
- `prefetchDistance = 5` 提前 5 条触发 APPEND

## 待优化项

1. **图片尺寸严格限制**：当前 AsyncImage 未传 `Size`，Coil 默认按 view 尺寸获取。可加 SizeResolver 进一步节省内存
2. **Compose MacroBenchmark**：未集成 `androidx.benchmark:benchmark-macro-junit4`，冷/热启动数据靠 MCP 工具采样，缺少系统级精度
3. **Baseline Profile**：未生成 `baseline-prof.txt`，首次冷启动仍走解释执行
4. **R8/Proguard**：release 构建未开启 `isMinifyEnabled = true`，APK 体积与启动有优化空间

## 结论

当前首屏体验 A 级（冷启动 1.3-1.6s 内可见内容、热启动 < 400ms、滚动 60fps）。
主要瓶颈在 Hilt + Room 初始化的 TTFD，建议加 Baseline Profile 优化首次冷启动。
