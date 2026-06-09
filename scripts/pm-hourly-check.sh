#!/bin/bash
# scripts/pm-hourly-check.sh
# 每小时自动跑的 PM 审查：检查 APP 当前状态 + 报告新 issues
# 适用于 launchd / cron / 手动 bash
#
# 用法：
#   ./scripts/pm-hourly-check.sh                  # 审查首页（默认）
#   ./scripts/pm-hourly-check.sh --goal "..."    # 自定义目标
#   ./scripts/pm-hourly-check.sh --max-steps 8   # 自定义步数
#
# Cron 范例（每个小时第 5 分钟跑一次）：
#   5 * * * * /Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/scripts/pm-hourly-check.sh >> /tmp/pm-hourly.log 2>&1

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SKILLS_DIR="$PROJECT_DIR/skills"
LOG_DIR="$SKILLS_DIR/pm_reviews"

# 参数解析
GOAL="审查仿今日头条首页（推荐频道）的当前状态：检查 4 作者 + 3 新闻 + Tab 渐变 + 浮卡是否正常显示 + 是否还有空白 Tab"
MAX_STEPS=6
TIMEOUT_MS=300000

while [[ $# -gt 0 ]]; do
  case "$1" in
    --goal) GOAL="$2"; shift 2 ;;
    --max-steps) MAX_STEPS="$2"; shift 2 ;;
    --timeout) TIMEOUT_MS="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

# 前置检查
if ! command -v adb &> /dev/null; then
  echo "[$(date +%H:%M:%S)] ❌ adb not in PATH"
  exit 1
fi

if ! adb get-state &>/dev/null; then
  echo "[$(date +%H:%M:%S)] ⚠️  No device connected — skipping this hour"
  exit 0
fi

mkdir -p "$LOG_DIR"

# 启动 PM session
cd "$SKILLS_DIR"

# 启动 app（确保在 toutiao 而不是 launcher）
adb shell am start -n com.example.toutiao/.MainActivity > /dev/null 2>&1 || true
sleep 3

# 跑 PM review
ARGS=$(node -e "console.log(JSON.stringify({goal: process.argv[1], maxSteps: parseInt(process.argv[2])}))" "$GOAL" "$MAX_STEPS")

echo "[$(date +%H:%M:%S)] 🔍 PM hourly check: goal=\"$GOAL\""

if ! node pm_session.mjs call pm_review "$ARGS" "$TIMEOUT_MS" > "$LOG_DIR/.hourly.tmp" 2>&1; then
  echo "[$(date +%H:%M:%S)] ❌ pm_review failed, see $LOG_DIR/.hourly.tmp"
  exit 1
fi

# 提取 rating + issues 数量 + 写入日志
RATING=$(grep -o '"overall_rating": "[^"]*"' "$LOG_DIR/.hourly.tmp" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
ISSUE_COUNT=$(grep -o '"id": "ISSUE-' "$LOG_DIR/.hourly.tmp" | wc -l | tr -d ' ')
LATEST_REVIEW=$(ls -t "$LOG_DIR"/rev-*.json 2>/dev/null | head -1)

echo "[$(date +%H:%M:%S)] ✅ rating=$RATING, issues=$ISSUE_COUNT, review_file=$LATEST_REVIEW"

# 清理临时文件
rm -f "$LOG_DIR/.hourly.tmp"
