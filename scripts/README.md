# scripts/README.md — 自动化脚本说明

## `pm-hourly-check.sh` — 每小时 PM 审查

**目的**：每小时自动跑一次 `pm_review`，验证 APP 当前状态、检查 UI 是否有回归。

### 用法

```bash
# 默认：审查首页，6 步 timeout 300s
./scripts/pm-hourly-check.sh

# 自定义目标
./scripts/pm-hourly-check.sh --goal "切底部 5 Tab 并截图核对内容" --max-steps 8
```

### 安装为 launchd 定时任务（macOS 推荐）

```bash
# 安装
cp scripts/com.example.toutiao.pm-hourly.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.example.toutiao.pm-hourly.plist

# 验证
launchctl list | grep toutiao
tail -f /tmp/pm-hourly.log

# 卸载
launchctl unload ~/Library/LaunchAgents/com.example.toutiao.pm-hourly.plist
```

### 安装为 cron（Linux/兼容）

```bash
# 每个小时第 5 分钟跑一次
crontab -e
# 添加：
5 * * * * /Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/scripts/pm-hourly-check.sh >> /tmp/pm-hourly.log 2>&1
```

### 输出

- 每次执行生成 `skills/pm_reviews/rev-<timestamp>.json`
- 简略日志写到 `stdout`（或 launchd 的 `StandardOutPath=/tmp/pm-hourly.log`）
- 完整 trace + 截图保留在 `pm_reviews/explore-<id>/` 目录
- 最新一条用 `ls -t skills/pm_reviews/rev-*.json | head -1` 查看

### 前置条件

1. Android 模拟器/真机已连接（adb devices 显示 device）
2. `adb` 在 PATH（macOS 装 Android Studio 后软链到 `/opt/homebrew/bin/adb`）
3. `MINIMAX_API_KEY` 在 `~/.zshrc` 或 `skills/.env` 中
4. `Toutiao app` 已安装（`adb install -r app/build/outputs/apk/debug/app-debug.apk`）
