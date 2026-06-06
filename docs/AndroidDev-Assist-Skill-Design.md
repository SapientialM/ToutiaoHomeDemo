# Android 开发 AI 辅助工具 Skill 设计方案

## 项目概述

**项目名称**: AndroidDev-Assist Skill (暂定)
**目标**: 为 OpenCode + 飞书桥接开发一个 Android 开发辅助 MCP Skill，实现 AI 自动截图、UI 验证、交互操作，辅助完成字节跳动客户端工程训练营项目。

**核心价值**:
- 开发阶段：AI 修改代码后自动截图 Compose Preview / 模拟器运行效果
- 验证阶段：自动对比设计稿与实际 UI，发现差异
- 答辩加分：展示 AI 辅助开发的完整闭环工作流

---

## 一、功能设计

### 1.1 核心功能模块

```
┌─────────────────────────────────────────────────────────────┐
│                    AndroidDev-Assist Skill                   │
├─────────────────────────────────────────────────────────────┤
│  Module 1: 截图模块 (Screenshot)                             │
│    ├─ capture_preview()     # Compose Preview 截图           │
│    ├─ capture_emulator()    # 模拟器/真机 ADB 截图           │
│    └─ capture_layout()      # Layout Inspector 截图         │
│                                                              │
│  Module 2: 交互模块 (Interaction)                            │
│    ├─ tap(x, y)             # 点击坐标                      │
│    ├─ swipe(x1,y1,x2,y2)    # 滑动                          │
│    ├─ input(text)           # 输入文字                      │
│    └─ press(key)            # 按键 (Home/Back/Enter)         │
│                                                              │
│  Module 3: 构建模块 (Build)                                  │
│    ├─ build()               # 执行 gradle assembleDebug     │
│    ├─ install()             # adb install                   │
│    └─ launch(package)       # 启动指定 Activity              │
│                                                              │
│  Module 4: 验证模块 (Verify)                                 │
│    ├─ compare(baseline, current)  # 图片对比               │
│    ├─ ocr_check(text)       # OCR 检查文字存在              │
│    └─ color_check(x,y,hex)  # 检查指定位置颜色              │
│                                                              │
│  Module 5: 报告模块 (Report)                                │
│    ├─ generate_report()     # 生成测试报告                 │
│    └─ send_to_feishu()      # 发送到飞书                   │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 功能详细设计

#### 1.2.1 截图模块

**capture_emulator() - ADB 截图**
```javascript
async function capture_emulator() {
  const timestamp = Date.now();
  const filename = `screenshot_${timestamp}.png`;
  const localPath = `./screenshots/${filename}`;

  // 1. 截图到设备
  await exec('adb shell screencap -p /sdcard/screen.png');
  // 2. 拉到本地
  await exec(`adb pull /sdcard/screen.png ${localPath}`);
  // 3. 可选：压缩/裁剪
  // 4. 返回路径
  return { path: localPath, timestamp };
}
```

**capture_preview() - Compose Preview 截图**
```javascript
// 方案 A: 通过 AS 的 Layout Inspector
// 方案 B: 通过 Compose Preview 的 screenshot 测试 API
// 方案 C: 运行时截图（最稳定，作为 fallback）
```

#### 1.2.2 交互模块

```javascript
// 坐标点击
async function tap(x, y) {
  await exec(`adb shell input tap ${x} ${y}`);
  return { success: true, action: 'tap', x, y };
}

// 滑动
async function swipe(x1, y1, x2, y2, duration = 300) {
  await exec(`adb shell input swipe ${x1} ${y1} ${x2} ${y2} ${duration}`);
  return { success: true };
}

// 输入文字（自动处理空格和特殊字符）
async function input_text(text) {
  // ADB input text 对特殊字符支持不好，需要转义
  const escaped = text.replace(/ /g, '%s');
  await exec(`adb shell input text "${escaped}"`);
  return { success: true };
}

// 按键
async function press_key(key) {
  const keyMap = {
    'HOME': 3,
    'BACK': 4,
    'ENTER': 66,
    'MENU': 82,
    'POWER': 26
  };
  const code = keyMap[key.toUpperCase()] || key;
  await exec(`adb shell input keyevent ${code}`);
  return { success: true };
}
```

#### 1.2.3 构建模块

```javascript
async function build_project(projectPath = '.') {
  // 1. 执行构建
  const result = await exec(`cd ${projectPath} && ./gradlew assembleDebug`, {
    timeout: 300000 // 5分钟超时
  });

  // 2. 解析构建结果
  const apkPath = findApk(projectPath);

  return {
    success: result.code === 0,
    apkPath,
    buildTime: result.duration,
    logs: result.stdout
  };
}

async function install_apk(apkPath, device = null) {
  const deviceFlag = device ? `-s ${device}` : '';
  const result = await exec(`adb ${deviceFlag} install -r ${apkPath}`);
  return { success: result.code === 0 };
}

async function launch_app(packageName, activity = null) {
  const component = activity ? `${packageName}/${activity}` : packageName;
  await exec(`adb shell am start -n ${component}`);
  // 等待启动完成
  await sleep(2000);
  return { success: true };
}
```

#### 1.2.4 验证模块

```javascript
// 图片对比（使用 pixelmatch）
async function compare_screenshots(baselinePath, currentPath, threshold = 0.1) {
  const baseline = await sharp(baselinePath).raw().toBuffer();
  const current = await sharp(currentPath).raw().toBuffer();

  const { width, height } = await sharp(baselinePath).metadata();

  const diff = pixelmatch(
    baseline, current, null, 
    width, height, 
    { threshold, includeAA: true }
  );

  const diffPercentage = (diff / (width * height)) * 100;

  return {
    diffPixels: diff,
    diffPercentage,
    isMatch: diffPercentage < 1.0, // 差异小于1%认为匹配
    diffImage: `./diffs/diff_${Date.now()}.png` // 生成差异图
  };
}

// OCR 文字检查（使用 tesseract.js 或调用云端 API）
async function ocr_check(screenshotPath, expectedText) {
  const { data: { text } } = await Tesseract.recognize(screenshotPath, 'chi_sim+eng');
  const found = text.includes(expectedText);
  return { found, extractedText: text, expectedText };
}

// 颜色检查
async function color_check(screenshotPath, x, y, expectedHex) {
  const { data } = await sharp(screenshotPath)
    .extract({ left: x, top: y, width: 1, height: 1 })
    .raw()
    .toBuffer();

  const [r, g, b] = data;
  const actualHex = `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;

  return {
    match: actualHex.toLowerCase() === expectedHex.toLowerCase(),
    expected: expectedHex,
    actual: actualHex,
    x, y
  };
}
```

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户 (飞书/终端)                        │
│                   "帮我写个登录页面，截图看看"               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   OpenCode Agent                             │
│  ├─ 理解需求 → 编写代码                                     │
│  ├─ 调用 MCP Tool → android-dev-assist                      │
│  └─ 接收结果 → 回复用户                                     │
└──────────────────────┬──────────────────────────────────────┘
                       │ MCP Protocol (stdio/sse)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              AndroidDev-Assist MCP Server                    │
│  ├─ Tool Router                                              │
│  ├─ Screenshot Manager                                       │
│  ├─ ADB Controller                                           │
│  ├─ Build Manager                                            │
│  ├─ Verify Engine                                            │
│  └─ Report Generator                                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
┌─────────────────┐      ┌─────────────────┐
│   ADB / Android  │      │   Gradle / AS    │
│   Emulator/Device │      │   Build System   │
└─────────────────┘      └─────────────────┘
```

### 2.2 MCP Server 设计

```javascript
// server.js - MCP Server 核心

const { Server } = require('@modelcontextprotocol/sdk/server/index.js');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio.js');

const server = new Server({
  name: 'android-dev-assist',
  version: '1.0.0'
}, {
  capabilities: {
    tools: {}
  }
});

// 注册 Tools
server.setRequestHandler('tools/list', async () => {
  return {
    tools: [
      {
        name: 'screenshot',
        description: 'Take a screenshot of the Android emulator or device',
        inputSchema: {
          type: 'object',
          properties: {
            type: { 
              type: 'string', 
              enum: ['emulator', 'preview', 'layout'],
              description: 'Screenshot type'
            },
            savePath: { type: 'string', description: 'Optional save path' }
          }
        }
      },
      {
        name: 'tap',
        description: 'Tap on screen at specified coordinates',
        inputSchema: {
          type: 'object',
          properties: {
            x: { type: 'number' },
            y: { type: 'number' }
          },
          required: ['x', 'y']
        }
      },
      {
        name: 'swipe',
        description: 'Swipe from start to end coordinates',
        inputSchema: {
          type: 'object',
          properties: {
            x1: { type: 'number' },
            y1: { type: 'number' },
            x2: { type: 'number' },
            y2: { type: 'number' },
            duration: { type: 'number', default: 300 }
          },
          required: ['x1', 'y1', 'x2', 'y2']
        }
      },
      {
        name: 'input_text',
        description: 'Input text on the device',
        inputSchema: {
          type: 'object',
          properties: {
            text: { type: 'string' }
          },
          required: ['text']
        }
      },
      {
        name: 'build',
        description: 'Build the Android project',
        inputSchema: {
          type: 'object',
          properties: {
            projectPath: { type: 'string', default: '.' },
            variant: { type: 'string', default: 'debug' }
          }
        }
      },
      {
        name: 'install_and_launch',
        description: 'Install APK and launch app',
        inputSchema: {
          type: 'object',
          properties: {
            apkPath: { type: 'string' },
            packageName: { type: 'string' }
          },
          required: ['packageName']
        }
      },
      {
        name: 'verify_ui',
        description: 'Verify UI against baseline or check text/color',
        inputSchema: {
          type: 'object',
          properties: {
            type: { 
              type: 'string', 
              enum: ['compare', 'ocr', 'color'],
              description: 'Verification type'
            },
            baselinePath: { type: 'string' },
            checkText: { type: 'string' },
            checkColor: { type: 'string' },
            x: { type: 'number' },
            y: { type: 'number' }
          }
        }
      }
    ]
  };
});

// 处理 Tool 调用
server.setRequestHandler('tools/call', async (request) => {
  const { name, arguments: args } = request.params;

  switch (name) {
    case 'screenshot':
      return await handleScreenshot(args);
    case 'tap':
      return await handleTap(args);
    case 'swipe':
      return await handleSwipe(args);
    case 'input_text':
      return await handleInputText(args);
    case 'build':
      return await handleBuild(args);
    case 'install_and_launch':
      return await handleInstallAndLaunch(args);
    case 'verify_ui':
      return await handleVerifyUI(args);
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
});

// 启动
const transport = new StdioServerTransport();
server.connect(transport);
console.error('AndroidDev-Assist MCP Server running on stdio');
```

---

## 三、OpenCode 集成配置

### 3.1 配置文件

```json
// ~/.config/opencode/opencode.json
{
  "$schema": "https://opencode.ai/config.json",
  "model": "kimi/kimi-for-coding",
  "provider": {
    "kimi": {
      "npm": "@ai-sdk/anthropic",
      "options": {
        "baseURL": "https://api.kimi.com/coding/v1",
        "apiKey": "sk-kimi-..."
      },
      "models": {
        "kimi-for-coding": {}
      }
    }
  },
  "mcpServers": {
    "android-dev-assist": {
      "command": "node",
      "args": ["/path/to/android-dev-assist/dist/server.js"],
      "env": {
        "ANDROID_HOME": "/Users/cm/Library/Android/sdk",
        "ADB_DEVICE": "emulator-5554"
      }
    }
  },
  "agents": {
    "default": {
      "model": "kimi/kimi-for-coding",
      "systemPrompt": "你是一个 Android 开发专家。你可以使用 android-dev-assist 工具来截图、操作设备和验证 UI。开发过程中要主动截图验证效果。"
    }
  }
}
```

### 3.2 飞书桥接中的使用

用户在飞书中发送：
```
帮我写一个登录页面，包含用户名和密码输入框，一个登录按钮。
写完后截图看看效果，如果按钮太靠下就往上调一点。
```

OpenCode 的执行流程：
1. 编写 Compose UI 代码（LoginScreen.kt）
2. 调用 `build` tool 构建项目
3. 调用 `install_and_launch` 安装并启动
4. 调用 `screenshot` 截图
5. 分析截图，发现按钮位置问题
6. 修改代码调整位置
7. 再次构建 → 安装 → 截图
8. 确认效果 OK，回复用户最终结果

---

## 四、项目结构

```
android-dev-assist/
├── README.md
├── package.json
├── tsconfig.json
├── src/
│   ├── server.ts              # MCP Server 入口
│   ├── tools/
│   │   ├── screenshot.ts      # 截图工具
│   │   ├── interaction.ts     # 交互工具
│   │   ├── build.ts           # 构建工具
│   │   └── verify.ts          # 验证工具
│   ├── utils/
│   │   ├── adb.ts             # ADB 封装
│   │   ├── gradle.ts          # Gradle 封装
│   │   ├── image.ts           # 图片处理
│   │   └── logger.ts          # 日志
│   └── types/
│       └── index.ts           # 类型定义
├── dist/                      # 编译输出
├── screenshots/               # 截图保存目录
├── baselines/                 # 基线图片目录
└── reports/                   # 报告输出目录
```

---

## 五、执行计划（9天）

### Day 1: 环境搭建与验证（6月3日）

**目标**: 确认所有依赖可用，跑通 ADB 截图

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 确认 Android Studio + 模拟器状态 | `adb devices` 有输出 |
| 下午 | 写第一个截图脚本 | `screenshot.sh` 能生成 PNG |
| 晚上 | 测试飞书回传截图 | 飞书收到截图消息 |

**检查点**:
```bash
adb devices
# 预期输出: emulator-5554 device

./scripts/screenshot.sh
# 预期: 生成 screenshots/test.png
```

### Day 2: MCP Server 骨架（6月4日）

**目标**: 搭建 MCP Server，注册第一个 Tool

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 初始化项目，安装依赖 | `package.json`, `tsconfig.json` |
| 下午 | 实现 `screenshot` Tool | OpenCode 能调用并返回路径 |
| 晚上 | 集成到 OpenCode 配置 | `opencode.json` 添加 mcpServers |

**检查点**:
```bash
# 在 OpenCode 中测试
/screenshot
# 预期: 返回截图路径
```

### Day 3: 核心 Tools 完成（6月5日）

**目标**: tap, swipe, input_text, build, install_and_launch

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 实现交互 Tools | tap, swipe, input_text |
| 下午 | 实现构建和安装 Tools | build, install_and_launch |
| 晚上 | 联调测试 | 完整流程: 截图 → 点击 → 截图 |

### Day 4: 训练营题目开发 + Skill 辅助（6月6日）

**目标**: 用 Skill 辅助完成训练营第一个页面

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 开发首页列表页面 | Compose UI 代码 |
| 下午 | 用 Skill 截图验证 | 3-5 张截图迭代记录 |
| 晚上 | 调整优化 | 效果 OK 的页面 |

### Day 5: 训练营题目开发 + Skill 辅助（6月7日）

**目标**: 继续开发，积累更多使用场景

| 时间 | 任务 | 产出 |
|------|------|------|
| 全天 | 开发详情页/其他页面 | 更多截图验证素材 |
| 晚上 | 整理使用心得 | 记录哪些功能最有用 |

### Day 6: 验证模块 + 报告生成（6月8日）

**目标**: 实现 UI 对比验证

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 实现 compare_screenshots | 能生成差异图 |
| 下午 | 实现 OCR 文字检查 | 能验证页面文字 |
| 晚上 | 实现报告生成 | Markdown/HTML 报告 |

### Day 7: 打磨与演示准备（6月9日）

**目标**: 准备 3-5 个演示场景

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 设计演示脚本 | 5 个场景文档 |
| 下午 | 彩排演示 | 每个场景 2-3 分钟 |
| 晚上 | 准备 fallback | 录屏备份 |

**演示场景示例**:
1. **场景1**: AI 写登录页 → 截图 → 发现按钮太靠下 → 调整 → 再截图确认
2. **场景2**: AI 写列表页 → 截图 → OCR 检查"今日头条"标题存在
3. **场景3**: 对比验证 → 设计稿 vs 实际截图 → 发现颜色差异
4. **场景4**: 交互测试 → 点击登录 → 输入账号密码 → 截图验证跳转
5. **场景5**: 完整流程 → 修改代码 → 构建 → 安装 → 截图 → 报告

### Day 8: 文档与答辩 PPT（6月10日）

**目标**: 完成 README 和答辩材料

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 写项目 README | GitHub 仓库文档 |
| 下午 | 做答辩 PPT | 10-15 页 |
| 晚上 | 完整彩排 | 时间控制在 10 分钟内 |

### Day 9: 最终彩排 + 休息（6月11日答辩）

**目标**: 稳定演示，心态调整

| 时间 | 任务 | 产出 |
|------|------|------|
| 上午 | 最终彩排 | 流畅演示 |
| 下午 | 检查所有环境 | 模拟器、AS、OpenCode、飞书 |
| 晚上 | 早点休息 | 答辩状态 |

---

## 六、技术栈

| 层级 | 技术 | 用途 |
|------|------|------|
| 运行环境 | Node.js 20+ | MCP Server |
| 语言 | TypeScript | 类型安全 |
| MCP SDK | `@modelcontextprotocol/sdk` | 协议实现 |
| 图片处理 | sharp | 截图处理、对比 |
| 图片对比 | pixelmatch | UI 差异检测 |
| OCR | tesseract.js | 文字识别 |
| 构建 | tsup / esbuild | 打包 |
| ADB | Android SDK | 设备控制 |

---

## 七、风险与应对

| 风险 | 概率 | 应对 |
|------|------|------|
| ADB 连接不稳定 | 中 | 准备 `adb kill-server && adb start-server` 脚本 |
| 模拟器启动慢 | 高 | 保持模拟器常开，不用时暂停 |
| 构建时间过长 | 中 | 使用 Gradle Daemon，增量构建 |
| 截图尺寸超限 | 低 | 压缩截图到 1920x1080 |
| 飞书桥接断开 | 低 | 准备本地终端演示 fallback |
| 答辩时间不够 | 中 | 准备录屏，现场只展示关键步骤 |

---

## 八、加分点设计

### 8.1 技术亮点
- **MCP 协议原生实现**: 展示对 AI 工具链生态的理解
- **自动化闭环**: 代码 → 构建 → 安装 → 截图 → 验证 → 报告
- **多模态验证**: 截图对比 + OCR + 颜色检查

### 8.2 产品亮点
- **移动端开发体验**: 手机截图直接回传到飞书
- **实时反馈**: AI 改完代码立即看到效果
- **质量保障**: 自动 UI 验证防止回归

### 8.3 演示亮点
- **现场感**: 飞书消息实时收到截图
- **迭代感**: 截图对比展示改进过程
- **专业感**: 生成测试报告展示工程素养

---

## 九、快速启动脚本

```bash
#!/bin/bash
# start-android-dev-assist.sh

# 1. 检查环境
echo "Checking environment..."
adb devices | grep -q "device$" || {
  echo "Error: No Android device connected"
  exit 1
}

# 2. 启动 OpenCode Server
echo "Starting OpenCode Server..."
export OPENCODE_SERVER_PORT=4096
opencode serve &
OPENCODE_PID=$!

# 3. 启动 MCP Server
echo "Starting AndroidDev-Assist MCP Server..."
cd /path/to/android-dev-assist
node dist/server.js &
MCP_PID=$!

# 4. 启动飞书桥接
echo "Starting Feishu Bridge..."
opencode-lark &
BRIDGE_PID=$!

echo "All services started!"
echo "OpenCode: http://localhost:4096"
echo "Press Ctrl+C to stop all"

# 5. 清理函数
cleanup() {
  echo "Stopping services..."
  kill $BRIDGE_PID $MCP_PID $OPENCODE_PID 2>/dev/null
  exit 0
}
trap cleanup INT

wait
```

---

## 十、验收标准

| 检查项 | 标准 | 验收方式 |
|--------|------|---------|
| 截图功能 | 3秒内返回截图 | 飞书发送 /screenshot |
| 交互功能 | 点击后1秒内响应 | 飞书发送 /tap 100 200 |
| 构建功能 | 增量构建 < 30秒 | 飞书发送 /build |
| 验证功能 | 对比结果准确 | 提供设计稿和实际截图 |
| 完整流程 | 代码修改到截图 < 2分钟 | 现场演示 |
| 稳定性 | 连续演示5次不失败 | 彩排验证 |

---

**文档版本**: v1.0
**创建时间**: 2026-06-02
**作者**: AI Assistant + cm
