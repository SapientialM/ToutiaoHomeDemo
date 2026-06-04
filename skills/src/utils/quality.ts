import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";

/**
 * 代码质量分析工具
 * 支持ktlint、代码复杂度、重复代码检测
 */

export interface CodeQualityReport {
  ktlintIssues: KtlintIssue[];
  complexityMetrics: ComplexityMetrics;
  duplicateCode: DuplicateCodeBlock[];
  totalFiles: number;
  totalLines: number;
  timestamp: number;
}

export interface KtlintIssue {
  file: string;
  line: number;
  column: number;
  message: string;
  rule: string;
  severity: "error" | "warning";
}

export interface ComplexityMetrics {
  averageComplexity: number;
  maxComplexity: number;
  maxComplexityFile: string;
  filesOverThreshold: number;
}

export interface DuplicateCodeBlock {
  file1: string;
  file2: string;
  lines1: { start: number; end: number };
  lines2: { start: number; end: number };
  similarity: number;
}

/**
 * 运行ktlint检查
 */
export async function runKtlint(projectPath: string = "."): Promise<KtlintIssue[]> {
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ktlintCheck`,
      { cwd: projectPath, timeout: 120000 }
    );
    
    // ktlint输出格式: file.kt:line:column:message
    const output = stdout + stderr;
    const issues: KtlintIssue[] = [];
    
    const lines = output.split("\n");
    for (const line of lines) {
      const match = line.match(/^(.+):(\d+):(\d+):\s*(.+)$/);
      if (match) {
        issues.push({
          file: match[1],
          line: parseInt(match[2]),
          column: parseInt(match[3]),
          message: match[4],
          rule: "ktlint",
          severity: line.includes("error") ? "error" : "warning",
        });
      }
    }
    
    log(`ktlint found ${issues.length} issues`);
    return issues;
  } catch (e) {
    // ktlint returns non-zero when issues found
    const err = e as Error & { stdout?: string; stderr?: string };
    const output = (err.stdout || "") + (err.stderr || "");
    
    const issues: KtlintIssue[] = [];
    const lines = output.split("\n");
    for (const line of lines) {
      const match = line.match(/^(.+):(\d+):(\d+):\s*(.+)$/);
      if (match) {
        issues.push({
          file: match[1],
          line: parseInt(match[2]),
          column: parseInt(match[3]),
          message: match[4],
          rule: "ktlint",
          severity: line.includes("error") ? "error" : "warning",
        });
      }
    }
    
    log(`ktlint found ${issues.length} issues`);
    return issues;
  }
}

/**
 * 自动修复ktlint问题
 */
export async function runKtlintFormat(projectPath: string = "."): Promise<{ success: boolean; fixed: number; message: string }> {
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ktlintFormat`,
      { cwd: projectPath, timeout: 120000 }
    );
    
    const output = stdout + stderr;
    const fixedMatch = output.match(/(\d+) file\(s\) formatted/);
    const fixed = fixedMatch ? parseInt(fixedMatch[1]) : 0;
    
    return {
      success: true,
      fixed,
      message: `Formatted ${fixed} files`,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      fixed: 0,
      message: err.message || "ktlint format failed",
    };
  }
}

/**
 * 分析代码复杂度
 * 使用简单的圈复杂度估算
 */
export async function analyzeComplexity(
  projectPath: string = ".",
  filePattern: string = "**/*.kt"
): Promise<ComplexityMetrics> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" | head -50`,
      { timeout: 10000 }
    );
    
    const files = stdout.split("\n").filter((f) => f.length > 0);
    let totalComplexity = 0;
    let maxComplexity = 0;
    let maxComplexityFile = "";
    let filesOverThreshold = 0;
    
    for (const file of files) {
      try {
        const { stdout: content } = await execAsyncWithTimeout(
          `cat "${file}"`,
          { timeout: 5000 }
        );
        
        // Simple complexity estimation: count branches
        const branches = (
          content.match(/\bif\b/g) || []
        ).length + (
          content.match(/\bwhen\b/g) || []
        ).length + (
          content.match(/\bfor\b/g) || []
        ).length + (
          content.match(/\bwhile\b/g) || []
        ).length + (
          content.match(/\breturn\b/g) || []
        ).length;
        
        const complexity = branches + 1;
        totalComplexity += complexity;
        
        if (complexity > maxComplexity) {
          maxComplexity = complexity;
          maxComplexityFile = file;
        }
        
        if (complexity > 10) {
          filesOverThreshold++;
        }
      } catch {
        // Skip unreadable files
      }
    }
    
    const averageComplexity = files.length > 0 ? totalComplexity / files.length : 0;
    
    return {
      averageComplexity: Math.round(averageComplexity * 10) / 10,
      maxComplexity,
      maxComplexityFile,
      filesOverThreshold,
    };
  } catch (e) {
    error("Failed to analyze complexity:", e);
    return {
      averageComplexity: 0,
      maxComplexity: 0,
      maxComplexityFile: "",
      filesOverThreshold: 0,
    };
  }
}

/**
 * 统计代码行数
 */
export async function countLines(projectPath: string = "."): Promise<{ totalFiles: number; totalLines: number }> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" | wc -l`,
      { timeout: 10000 }
    );
    const totalFiles = parseInt(stdout.trim()) || 0;
    
    const { stdout: linesOutput } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" -exec cat {} + | wc -l`,
      { timeout: 10000 }
    );
    const totalLines = parseInt(linesOutput.trim()) || 0;
    
    return { totalFiles, totalLines };
  } catch (e) {
    error("Failed to count lines:", e);
    return { totalFiles: 0, totalLines: 0 };
  }
}

/**
 * 生成完整代码质量报告
 */
export async function generateQualityReport(
  projectPath: string = "."
): Promise<CodeQualityReport> {
  log("Generating code quality report...");
  
  const [ktlintIssues, complexityMetrics, lineCounts] = await Promise.all([
    runKtlint(projectPath).catch(() => []),
    analyzeComplexity(projectPath),
    countLines(projectPath),
  ]);
  
  return {
    ktlintIssues,
    complexityMetrics,
    duplicateCode: [], // Would need more sophisticated analysis
    totalFiles: lineCounts.totalFiles,
    totalLines: lineCounts.totalLines,
    timestamp: Date.now(),
  };
}

/**
 * 格式化质量报告
 */
export function formatQualityReport(report: CodeQualityReport): string {
  const lines = [
    "═══════════════════════════════════════════",
    "📋 CODE QUALITY REPORT",
    "═══════════════════════════════════════════",
    `🕐 Time: ${new Date(report.timestamp).toLocaleString()}`,
    `📁 Files: ${report.totalFiles}`,
    `📝 Lines: ${report.totalLines}`,
    "",
    "🔍 Ktlint Issues:",
    report.ktlintIssues.length === 0
      ? "   ✅ No issues found"
      : report.ktlintIssues.map((i) => `   ⚠️  ${i.file}:${i.line}:${i.column} - ${i.message}`).join("\n"),
    "",
    "📊 Complexity Metrics:",
    `   Average: ${report.complexityMetrics.averageComplexity}`,
    `   Max: ${report.complexityMetrics.maxComplexity} (${report.complexityMetrics.maxComplexityFile})`,
    `   Files over threshold: ${report.complexityMetrics.filesOverThreshold}`,
    "═══════════════════════════════════════════",
  ];
  
  return lines.join("\n");
}
