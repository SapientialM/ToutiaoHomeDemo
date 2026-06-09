// Spawn fresh MCP server (with pm_review / pm_mark_fixed / dump_ui / pm_compare_with_design)
// and use it as a client. Persists conversation logs to /tmp/pm_session.log so we can
// re-read them across multiple Bash invocations.
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { appendFileSync, writeFileSync, readFileSync, existsSync } from "node:fs";

const SESSION_LOG = "/tmp/pm_session.log";

function logLine(...args) {
  const line = args.map(a => typeof a === "string" ? a : JSON.stringify(a, null, 2)).join(" ");
  console.log(line);
  try { appendFileSync(SESSION_LOG, line + "\n"); } catch {}
}

const transport = new StdioClientTransport({
  command: "node",
  args: ["./dist/server.js"],
  env: { ...process.env, MCP_QUIET: "1" },
});
const client = new Client({ name: "pm-session", version: "0.1.0" }, { capabilities: {} });
await client.connect(transport);

const cmd = process.argv[2];

try {
  if (cmd === "list") {
    const tools = await client.listTools();
    const names = tools.tools.map(t => t.name);
    logLine("TOOLS_COUNT:", names.length);
    logLine("PM_TOOLS:", JSON.stringify(names.filter(n => n.startsWith("pm_") || n === "dump_ui")));
  } else if (cmd === "call") {
    const name = process.argv[3];
    const args = process.argv[4] ? JSON.parse(process.argv[4]) : {};
    const timeoutMs = parseInt(process.argv[5] || "180000");
    logLine(`>>> ${name}`, JSON.stringify(args));
    const t0 = Date.now();
    const res = await client.callTool({ name, arguments: args }, undefined, { timeout: timeoutMs });
    logLine(`<<< ${name} (${Date.now() - t0}ms)`);
    const text = res.content[0]?.text || "";
    // For pm_review, persist the full review to a file so we can re-read later
    if (name === "pm_review" && res.content[0]?.text) {
      try {
        const obj = JSON.parse(res.content[0].text);
        if (obj.review_file) {
          logLine("REVIEW_FILE:", obj.review_file);
        }
      } catch {}
    }
    logLine(text);
  } else {
    logLine("Usage: pm_session.mjs <list|call> [name] [args-json] [timeoutMs]");
  }
} catch (e) {
  logLine("ERROR:", e.message);
  process.exitCode = 1;
} finally {
  await client.close();
}
