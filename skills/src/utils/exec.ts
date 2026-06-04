import { exec as execCb } from "node:child_process";
import { promisify } from "node:util";
import { log, error } from "./logger.js";
import { existsSync } from "node:fs";

const execAsync = promisify(execCb);

/**
 * Check if file exists
 */
export async function fileExists(path: string): Promise<boolean> {
  return existsSync(path);
}

/**
 * Execute command with timeout support
 */
export async function execAsyncWithTimeout(
  command: string,
  options: { cwd?: string; timeout?: number } = {}
): Promise<{ stdout: string; stderr: string }> {
  const timeout = options.timeout || 30000;
  
  log(`exec: ${command} (timeout: ${timeout}ms)`);
  
  try {
    const result = await execAsync(command, {
      cwd: options.cwd,
      timeout,
      maxBuffer: 1024 * 1024, // 1MB buffer
    });
    
    return {
      stdout: result.stdout || "",
      stderr: result.stderr || "",
    };
  } catch (e) {
    const err = e as Error & { stdout?: string; stderr?: string; killed?: boolean };
    
    if (err.killed) {
      throw new Error(`Command timed out after ${timeout}ms: ${command}`);
    }
    
    // Return stdout/stderr even on error (some commands exit non-zero but have useful output)
    return {
      stdout: err.stdout || "",
      stderr: err.stderr || err.message || "",
    };
  }
}

/**
 * Spawn command and stream output
 */
export async function spawnCommand(
  command: string,
  args: string[],
  options: { cwd?: string; timeout?: number } = {}
): Promise<{ stdout: string; stderr: string; exitCode: number }> {
  const { spawn } = await import("node:child_process");
  
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      shell: true,
    });
    
    let stdout = "";
    let stderr = "";
    let timedOut = false;
    
    const timeout = setTimeout(() => {
      timedOut = true;
      child.kill("SIGTERM");
      reject(new Error(`Command timed out after ${options.timeout || 30000}ms`));
    }, options.timeout || 30000);
    
    child.stdout?.on("data", (data) => {
      stdout += data.toString();
    });
    
    child.stderr?.on("data", (data) => {
      stderr += data.toString();
    });
    
    child.on("close", (code) => {
      clearTimeout(timeout);
      if (!timedOut) {
        resolve({
          stdout,
          stderr,
          exitCode: code || 0,
        });
      }
    });
    
    child.on("error", (err) => {
      clearTimeout(timeout);
      if (!timedOut) {
        reject(err);
      }
    });
  });
}
