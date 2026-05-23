# AGENT.md — ToutiaoFeedDemo

> 今日头条首页信息流 Demo | 字节跳动客户端工程训练营
> 给 AI Agent 的开发约束与协作协议

---

## 1. 项目定位

仿今日头条首页信息流 Android 应用，用于展示 MVI + Clean Architecture + Jetpack Compose 的现代 Android 开发能力。

**核心目标**：还原头条首页浏览体验，体现工程架构素养，为答辩提供技术展示载体。

---

## 2. 技术栈约束（不可偏离）

| 类别 | 技术 | 备注 |
|------|------|------|
| 语言 | Kotlin | 必须使用 Kotlin，禁止 Java |
| UI | Jetpack Compose + Material3 | 禁止使用 XML View 体系（除 Manifest/资源文件） |
| 架构 | MVI + Clean Architecture | 单向数据流，UiState/UiEvent 均为 sealed class |
| 状态管理 | StateFlow | 禁止使用 LiveData |
| 依赖注入 | Hilt + KSP | 编译期注入，禁止手动创建工厂 |
| 网络 | Retrofit2 + OkHttp3 + Kotlinx Serialization | 禁止使用 Gson |
| 图片 | Coil Compose | 禁止使用 Glide |
| 数据库 | Room + KSP | 禁止使用 raw SQLite |
| 分页 | Paging3 + RemoteMediator | 混合网络+本地分页方案 |
| 构建 | Gradle Kotlin DSL + Version Catalog | 依赖版本统一在 libs.versions.toml |

---

## 3. 架构约束

### 3.1 分层依赖规则

```
Presentation (Screen/ViewModel) → Domain (Model/Repository接口)
                                         ↑
                                    Data (Repository实现/API/DAO)
```

- **Domain 层**：纯 Kotlin，不依赖任何 Android 框架
- **Data 层**：实现 Domain 的 Repository 接口，内部可依赖 Android SDK
- **Presentation 层**：持有 ViewModel，ViewModel 持有 Repository
- **严禁**：Presentation 直接引用 Data 层的类；Domain 引用 Android 框架

### 3.2 包结构

```
com.example.toutiao/
├── domain/model/          # FeedCard 密封类（4种子类型）
├── domain/repository/     # NewsRepository 接口
├── data/remote/api/       # Retrofit API 接口
├── data/remote/dto/       # Kotlinx Serialization DTO
├── data/local/entity/     # Room Entity
├── data/local/dao/        # Room DAO
├── data/local/database/   # AppDatabase
├── data/repository/       # NewsRepositoryImpl
├── data/mapper/           # DTO ↔ Entity ↔ Domain 转换
├── presentation/home/     # HomeScreen/ViewModel/UiState/UiEvent/components
├── di/                    # Hilt Module（Network/Database/Repository）
└── ui/theme/              # Compose Theme
```

### 3.3 MVI 契约

```kotlin
// ViewModel 暴露：
val uiState: StateFlow<HomeUiState>    // 唯一状态出口
fun onEvent(event: HomeUiEvent)        // 唯一事件入口

// UiState 必须完整描述 UI 所需的所有状态：
sealed class HomeUiState {
    Loading                            // 首次加载
    Success(feedItems, isRefreshing, isLoadingMore, hasMore, currentTab)
    Error(message, retryable)          // 错误状态
    Empty                              // 空数据
}
```

---

## 4. 代码规范

- 遵循 Kotlin 官方编码规范（`kotlin.code.style=official`）
- 禁止使用 `!!` 强制解包，必须使用 `?.let` / `?:` 安全调用
- 函数体不超过 30 行，Complex 组件拆分为子 Composable
- 数据类使用 `data class`，接口方法不超过 5 个
- 禁止在 Composable 中发起网络请求或数据库操作，所有副作用在 ViewModel/LaunchedEffect 中处理
- Compose 预览函数单独提取，不混入业务 Composable
- `LazyColumn` 的 `items` 必须指定 `key`

---

## 5. 功能边界

### 5.1 必须实现

- 4 种卡片类型渲染（TextTop / LeftTextRightImage / LargeImage / Video）
- 顶部 Tab 切换（至少推荐、热榜两个频道）
- 下拉刷新 + 加载更多
- Room 本地缓存 + 离线展示
- Loading / Empty / Error 状态切换

### 5.2 明确不做

- 用户登录/注册
- 新闻详情页（仅预留点击事件）
- 视频播放（仅封面 + 播放按钮 UI）
- 评论/点赞/分享
- 推送通知

---

## 6. Git 规范

- 分支命名：`feature/<功能名>` 或 `fix/<问题描述>`
- Commit 信息：中文描述，格式 `<类型>: <简述>`，如 `feat: 添加TextTopCard组件`
- 禁止提交 `.gradle/`、`build/`、`.idea/`、`local.properties`、`*.keystore`
- 禁止提交含密钥、Token 的内容

---

## 7. 构建与运行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 清理构建
./gradlew clean assembleDebug

# 运行单元测试
./gradlew test

# 运行 Android 仪器化测试
./gradlew connectedAndroidTest
```

- 最低 SDK：API 26（Android 8.0）
- 目标 SDK：API 36
- APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

---

## 8. Agent 协作协议

### 8.1 开发流程

1. **接到任务后**：先阅读相关现有代码，理解当前实现
2. **修改代码前**：明确影响范围，列出需改动的文件
3. **代码改动**：实现功能 → 跑通编译 → 修复 lint 问题
4. **完成后**：更新 `docs/04_项目进度文档.md` 的日报和周报

### 8.2 质量门禁

- 每次代码修改后必须执行 `./gradlew assembleDebug` 确保编译通过
- 新功能必须使用 MVI 模式（UiState/UiEvent/ViewModel）
- 新增依赖必须在 `libs.versions.toml` 中声明版本
- 禁止在未明确要求时引入新第三方库

### 8.3 文档约定

- 进度记录：`docs/04_项目进度文档.md`
- 需求参考：`docs/01_需求分析文档.md`
- 技术设计：`docs/02_技术设计文档.md`
- 不要创建额外的 .md 文档，除非用户明确要求

### 8.4 禁忌事项

- 禁止修改 `.gitignore` 中已排除的规则
- 禁止修改 Gradle Wrapper 版本
- 禁止在生产代码中使用 `TODO()` 会导致崩溃的占位符（用注释 `// TODO:` 代替）
- 禁止使用 `@Suppress` 注解绕过编译检查
- 禁止在 ViewModel 中持有 Context/View 引用
