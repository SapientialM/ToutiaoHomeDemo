#!/usr/bin/env python3
"""
Toutiao PM Monitor CLI
旁路显示器：实时读取 pm_trace.jsonl，展示 PM 思考与工具调用链

Usage:
    cd skills/monitor && python main.py
    # 或
    cd skills && python monitor/main.py
"""

import asyncio
import json
import os
import sys
from datetime import datetime
from pathlib import Path

from textual.app import App, ComposeResult
from textual.containers import Vertical
from textual.reactive import reactive
from textual.widgets import DataTable, Footer, Header, Log, Static

# 锚定到脚本所在目录（不依赖 CWD，避免用户从项目根直接 `python skills/monitor/main.py`
# 时 Path("../xxx") 解析到错的地方）
# 脚本位置：<project_root>/skills/monitor/main.py
# project_root = main.py 父目录的父目录
SCRIPT_DIR = Path(__file__).parent.resolve()
PROJECT_ROOT = SCRIPT_DIR.parent.parent

# MCP server (skills/dist/server.js) 用 ./pm_trace.jsonl 写到「启动时的 CWD」，
# 一般从项目根启动，所以落在 <project_root>/pm_trace.jsonl。
# Monitor 的默认路径锚定到脚本位置推导出的 PROJECT_ROOT，与 MCP server 一致。
TRACE_PATH = Path(os.environ.get("PM_TRACE_PATH", str(PROJECT_ROOT / "pm_trace.jsonl"))).resolve()
MEMORY_PATH = Path(os.environ.get("PM_MEMORY_PATH", str(PROJECT_ROOT / ".pm_memory.json"))).resolve()


class PMMonitor(App):
    """PM Monitor TUI App"""

    CSS_PATH = "monitor.tcss"

    # Reactive state
    issues = reactive(list)
    trace_lines = reactive(list)
    memory_stats = reactive(str)

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        yield Log(id="trace_log", highlight=True)
        yield DataTable(id="issue_table")
        yield Static(id="status_bar")
        yield Footer()

    def on_mount(self) -> None:
        self.title = "Toutiao PM Monitor"
        self.sub_title = f"Trace: {TRACE_PATH}"

        # Init issue table
        table = self.query_one("#issue_table", DataTable)
        table.add_columns("Severity", "ID", "Description", "Location")
        table.zebra_stripes = True
        table.cursor_type = "row"

        # Init status bar
        self.memory_stats = "Waiting for trace..."

        # Start background watchers
        self.watch_trace_task = asyncio.create_task(self.watch_trace())
        self.watch_memory_task = asyncio.create_task(self.watch_memory())

    async def watch_trace(self):
        """Tail -f pm_trace.jsonl with async polling"""
        log_widget = self.query_one("#trace_log", Log)
        log_widget.write_line("[dim]Waiting for trace events...[/dim]")

        # Wait for file to exist
        while not TRACE_PATH.exists():
            await asyncio.sleep(0.5)

        with open(TRACE_PATH, "r") as f:
            # Seek to end
            f.seek(0, 2)
            while True:
                line = f.readline()
                if line:
                    try:
                        event = json.loads(line.strip())
                        self.process_event(event, log_widget)
                    except json.JSONDecodeError:
                        log_widget.write_line(f"[red]Malformed: {line[:80]}[/red]")
                else:
                    await asyncio.sleep(0.1)

    async def watch_memory(self):
        """Poll .pm_memory.json periodically"""
        while True:
            self.refresh_memory_stats()
            await asyncio.sleep(2.0)

    def process_event(self, event: dict, log_widget: Log):
        """Process a single trace event and update UI"""
        ts = event.get("ts", "")
        ts_short = ts.split("T")[1][:8] if "T" in ts else ts
        etype = event.get("type", "unknown")

        color_map = {
            "tool_call": "cyan",
            "step": "blue",
            "vlm_think": "magenta",
            "done": "green",
            "memory_update": "yellow",
            "error": "red",
            "discuss": "bright_cyan",
            "check": "bright_green",
        }
        color = color_map.get(etype, "white")

        # Format log line based on event type
        if etype == "tool_call":
            tool = event.get("tool", "")
            args = event.get("args", {})
            arg_str = json.dumps(args, ensure_ascii=False)[:60]
            line = f"[{ts_short}] [{color}]{etype:12}[/] {tool}({arg_str})"
        elif etype == "step":
            step = event.get("step", 0)
            total = event.get("total_steps", 0)
            action = event.get("action", "")
            detail = event.get("detail", "")
            line = f"[{ts_short}] [{color}]{etype:12}[/] step {step}/{total} {action} → {detail}"
        elif etype == "vlm_think":
            thought = event.get("thought", "")
            latency = event.get("latency_ms", 0)
            line = f"[{ts_short}] [{color}]{etype:12}[/] {thought} (lat={latency}ms)"
        elif etype == "done":
            rating = event.get("overall_rating", "?")
            issues_found = event.get("issues_found", 0)
            elapsed = event.get("elapsed_ms", 0)
            line = f"[{ts_short}] [{color}]{etype:12}[/] rating={rating} issues={issues_found} elapsed={elapsed}ms"
            # Update issues from done event if present
            if "issues" in event:
                self.issues = event["issues"]
        elif etype == "memory_update":
            action = event.get("action", "")
            detail = event.get("detail", "")
            line = f"[{ts_short}] [{color}]{etype:12}[/] {action} {detail}"
        elif etype == "error":
            tool = event.get("tool", "")
            detail = event.get("detail", "")
            line = f"[{ts_short}] [{color}]{etype:12}[/] {tool}: {detail}"
        elif etype == "discuss":
            detail = event.get("detail", "")
            line = f"[{ts_short}] [{color}]{etype:12}[/] {detail}"
        elif etype == "check":
            detail = event.get("detail", "")
            line = f"[{ts_short}] [{color}]{etype:12}[/] {detail}"
        else:
            line = f"[{ts_short}] [{color}]{etype:12}[/] {json.dumps(event, ensure_ascii=False)[:80]}"

        log_widget.write_line(line)
        self.refresh_memory_stats()

    def refresh_memory_stats(self):
        """Read .pm_memory.json and update status bar + issue table"""
        stats = []
        issues = []

        if MEMORY_PATH.exists():
            try:
                with open(MEMORY_PATH, "r") as f:
                    memory = json.load(f)
                reviews = memory.get("reviews", [])
                all_issues = []
                for r in reviews:
                    for issue in r.get("issues", []):
                        issue["_review_id"] = r.get("review_id", "")
                        all_issues.append(issue)

                open_count = sum(1 for i in all_issues if i.get("status") == "open")
                fixed_count = sum(1 for i in all_issues if i.get("status") == "fixed")
                ignored_count = sum(1 for i in all_issues if i.get("status") == "ignored")
                focus = memory.get("current_focus", {})

                stats = [
                    f"Reviews: {len(reviews)}",
                    f"Fixed: {fixed_count}",
                    f"Open: {open_count}",
                    f"Ignored: {ignored_count}",
                    f"Focus: {focus.get('page', '—')}",
                ]
                issues = [i for i in all_issues if i.get("status") == "open"]
            except Exception as e:
                stats = [f"Memory read error: {e}"]
        else:
            stats = ["Memory: not found"]

        # Update status bar
        status = self.query_one("#status_bar", Static)
        status.update("  |  ".join(stats))

        # Update issue table
        table = self.query_one("#issue_table", DataTable)
        table.clear()
        for issue in issues[:20]:
            sev = issue.get("severity", "medium")
            iid = issue.get("issue_id", "—")
            desc = issue.get("description", "")[:40]
            loc = issue.get("location", "—")[:30]
            style = f"severity-{sev}"
            table.add_row(sev.upper(), iid, desc, loc, label=style)

    def action_quit(self):
        """Override quit to clean up tasks"""
        if hasattr(self, "watch_trace_task"):
            self.watch_trace_task.cancel()
        if hasattr(self, "watch_memory_task"):
            self.watch_memory_task.cancel()
        self.exit()


if __name__ == "__main__":
    app = PMMonitor()
    app.run()
