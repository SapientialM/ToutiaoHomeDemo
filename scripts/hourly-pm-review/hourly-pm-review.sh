#!/bin/bash
# hourly-pm-review.sh
# 任务：每个小时跑一次 PM 审查，截图 + 调 MCP 工具 review，把结果写到日志
# 用法：在 ToutiaoFeedDemo 项目根目录执行
#   bash scripts/hourly-pm-review/hourly-pm-review.sh

set -e

PROJECT_DIR="/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo"
SCRIPT_DIR="$PROJECT_DIR/scripts/hourly-pm-review"
LOG_DIR="$PROJECT_DIR/pm_reviews"
SCREENSHOT_DIR="$PROJECT_DIR/skills/screenshots"
LAUNCH_LOG="$SCRIPT_DIR/launchd.log"

cd "$PROJECT_DIR"

mkdir -p "$LOG_DIR" "$SCREENSHOT_DIR" "$SCRIPT_DIR"

# 1. 检查 adb + 设备
if ! adb devices | grep -q "device$"; then
    echo "[$(date +%Y-%m-%dT%H:%M:%S)] No ADB device, skip" >> "$LAUNCH_LOG"
    exit 0
fi

# 2. 检查 app 是否在前台
APP_RUNNING=$(adb shell dumpsys activity activities 2>/dev/null | grep -c "com.example.toutiao/.MainActivity" || true)
if [ "$APP_RUNNING" = "0" ]; then
    echo "[$(date +%Y-%m-%dT%H:%M:%S)] App not running, launching..." >> "$LAUNCH_LOG"
    adb shell am start -n com.example.toutiao/.MainActivity >/dev/null 2>&1
    sleep 3
fi

# 3. 截屏
TIMESTAMP=$(date +%s)
SHOT_PATH="$SCREENSHOT_DIR/hourly_$TIMESTAMP.png"
adb exec-out screencap -p > "$SHOT_PATH" 2>/dev/null

# 4. 调 MCP pm_review 工具（stdio JSON-RPC）
REVIEW_OUTPUT=$(MINIMAX_API_KEY="${MINIMAX_API_KEY:-sk-cp-fXF70yqVGXnviJFLtXsXqWDT2Gqs_Lq-qCC_1YXkZlBb5PYH-CZgw4HPDYYpjB6ZcZ2XySRdL6iGIotlDyssTbI15MdAP3wqhzKiqsHc56GiOnaKtn9gmc4}" \
SCREENSHOT_DIR="$SCREENSHOT_DIR" \
node -e "
const { spawn } = require('child_process');
const p = spawn('node', ['skills/dist/server.js'], {
  env: { ...process.env, MINIMAX_API_KEY: process.env.MINIMAX_API_KEY },
  stdio: ['pipe', 'pipe', 'inherit']
});
let out = '';
p.stdout.on('data', d => { out += d; });
p.stdin.write(JSON.stringify({jsonrpc:'2.0',id:1,method:'initialize',params:{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'hourly',version:'1.0'}}}) + '\n');
p.stdin.write(JSON.stringify({jsonrpc:'2.0',method:'notifications/initialized'}) + '\n');
setTimeout(() => {
  p.stdin.write(JSON.stringify({jsonrpc:'2.0',id:2,method:'tools/call',params:{name:'pm_review',arguments:{target:'首页（hourly review）',screenshotPath:'$SHOT_PATH'}}}) + '\n');
}, 500);
setTimeout(() => { p.kill('SIGTERM'); console.log(out); }, 60000);
" 2>&1 | tail -20)

# 5. 找最新 review JSON
LATEST_REVIEW=$(ls -t "$LOG_DIR"/rev-*.json 2>/dev/null | head -1)
if [ -z "$LATEST_REVIEW" ]; then
    echo "[$(date +%Y-%m-%dT%H:%M:%S)] No review JSON generated" >> "$LAUNCH_LOG"
    echo "MCP output: $REVIEW_OUTPUT" >> "$LAUNCH_LOG"
    exit 1
fi

# 6. 解析 rating + 高优先级 issues
PARSE_RESULT=$(python3 <<EOF
import json
try:
    d = json.load(open('$LATEST_REVIEW'))
    if d.get('_parseError'):
        print("ERROR|PARSE_FAIL")
    else:
        rating = d.get('overall_rating', '?')
        high_count = sum(1 for i in d.get('issues',[]) if i.get('severity')=='high')
        print(f"{rating}|{high_count}")
except Exception as e:
    print(f"ERROR|{e}")
EOF
)
RATING=$(echo "$PARSE_RESULT" | cut -d'|' -f1)
HIGH_COUNT=$(echo "$PARSE_RESULT" | cut -d'|' -f2)

# 7. 记录结果
echo "[$(date +%Y-%m-%dT%H:%M:%S)] Review: $LATEST_REVIEW rating=$RATING high_issues=$HIGH_COUNT" >> "$LAUNCH_LOG"

# 8. 如果 rating 降到 C 以下 或有 high issue，写一条"提醒"到日志（后续可扩展推送）
if [ "$RATING" = "C" ] || [ "$RATING" = "D" ] || [ "$RATING" = "ERROR" ] || [ "$HIGH_COUNT" != "0" ]; then
    echo "[$(date +%Y-%m-%dT%H:%M:%S)] ⚠️  ATTENTION: rating=$RATING, high_issues=$HIGH_COUNT" >> "$LAUNCH_LOG"
fi
