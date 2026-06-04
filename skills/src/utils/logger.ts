const LOG_PREFIX = "[android-dev-assist]";

export function log(...args: unknown[]) {
  // stderr to avoid interfering with MCP stdio protocol
  console.error(LOG_PREFIX, ...args);
}

export function error(...args: unknown[]) {
  console.error(LOG_PREFIX, "[ERROR]", ...args);
}
