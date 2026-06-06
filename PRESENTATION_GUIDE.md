# 答辩准备指南

## 项目亮点总结

### 1. 技术栈先进性
- **Kotlin 2.2.10** + **Jetpack Compose** (BOM 2026.02.01)
- **MVI + Clean Architecture** 现代架构
- **Paging3 + RemoteMediator** 官方分页方案
- **Room + Hilt + KSP** 编译期依赖注入
- **Kotlinx Serialization** 替代 Gson
- **Coil** Kotlin 优先图片加载

### 2. 工程架构亮点
```
Presentation (Compose + MVI ViewModel)
    ↕ StateFlow<UiState> / UiEvent
Domain (FeedCard sealed class + Repository接口)
    ↕
Data (RepositoryImpl → RemoteDataSource接口)
    ↕
├─ MockDataSource (Demo数据)
└─ RealRemoteDataSource (生产环境)
    ↕
Room (PagingSource + RemoteMediator)
```

### 3. 核心功能实现
- ✅ 4种卡片类型：TextTop / LeftTextRightImage / LargeImage / Video
- ✅ 6个频道Tab：关注/推荐/热榜/新时代/小说/视频
- ✅ 下拉刷新 + 加载更多（Paging3自动处理）
- ✅ Room本地缓存 + 离线展示
- ✅ 置顶排序策略（日期降序 → 置顶降序 → 时间降序）
- ✅ 搜索功能（真实Mock数据匹配）
- ✅ 5个底部Tab界面（视频/搜索/任务/我的）

### 4. 代码质量
- 单向数据流（UiState/UiEvent/ViewModel）
- 接口抽象（RemoteDataSource可切换Mock/Real）
- 三层数据转换（DTO → Entity → Domain）
- 全链路日志（Timber）
- Compose Preview多状态

## 答辩演示脚本（5分钟）

### 开场（30秒）
"大家好，我开发的是仿今日头条首页信息流Demo，采用Kotlin + Jetpack Compose + MVI架构。"

### 功能演示（3分钟）
1. **首页展示**（30秒）
   - 展示4种卡片类型
   - 顶部天气栏、搜索栏、Tab栏
   - 下拉刷新效果

2. **频道切换**（30秒）
   - 切换推荐/热榜/视频/新时代等Tab
   - 每个Tab独立数据
   - 滚动位置不残留

3. **加载更多**（30秒）
   - 滑动到底部自动加载
   - Loading Footer显示

4. **搜索功能**（30秒）
   - 点击搜索栏
   - 输入关键词
   - 展示搜索结果

5. **底部Tab**（30秒）
   - 视频Tab：视频列表
   - 搜索Tab：热搜榜
   - 任务Tab：任务中心
   - 我的Tab：个人中心

### 技术亮点（1分30秒）
1. **MVI架构**：单向数据流，状态集中管理
2. **Paging3**：RemoteMediator写入Room，PagingSource读取
3. **Clean Architecture**：Domain层纯Kotlin，不依赖Android
4. **Mock数据源**：支持延迟/错误模拟，便于调试

## 常见问题准备

### Q1: 为什么选择MVI而不是MVVM？
A: MVI强调单向数据流和不可变状态，适合复杂信息流场景。所有UI变化都通过UiState驱动，易于追踪和调试。

### Q2: Paging3的RemoteMediator工作原理？
A: RemoteMediator在网络请求成功后写入Room，PagingSource感知Room变化自动刷新UI。实现了离线缓存和自动分页。

### Q3: 如何处理频道切换时的数据隔离？
A: 使用key(currentTab)包裹Paging3数据收集，Tab切换时Compose丢弃旧子树，新子树从Loading态开始，避免旧数据闪现。

### Q4: Mock数据源如何设计？
A: 实现RemoteDataSource接口，从assets加载1421条真实新闻，按频道过滤、排序、分页。支持DebugControls模拟延迟和错误。

### Q5: 卡片类型如何分发？
A: 使用FeedCard密封类 + when表达式，根据type字段分发到对应组件，类型安全且易于扩展。

## 评委可能问的技术细节

1. **Compose重组优化**：使用@Immutable、LazyColumn指定key、避免Composable中直接计算
2. **Room双表设计**：feed_items + remote_keys，支持分页状态持久化
3. **图片加载策略**：Coil内存缓存50MB，自动尺寸裁剪
4. **错误处理**：统一错误状态，支持重试机制
5. **性能优化**：首屏加载≤1.5s，滑动帧率≥55fps

## 注意事项

1. **实机演示**：确保设备电量充足，提前安装APK
2. **网络环境**：准备离线演示（Room缓存）
3. **代码展示**：提前打开关键文件，快速跳转
4. **时间控制**：严格控制在5分钟内
5. **自信表达**：强调技术选型的合理性和架构设计的思考
