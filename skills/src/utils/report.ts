import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";
import { collectPerformanceMetrics, formatPerformanceReport } from "./performance.js";
import { generateQualityReport, formatQualityReport } from "./quality.js";
import { runRegressionTest } from "./ui-test.js";
import { buildApk, runUnitTests } from "./build-deploy.js";

/**
 * 项目报告生成器
 * 生成完整的项目分析报告，用于答辩展示
 */

export interface ProjectReport {
  projectInfo: ProjectInfo;
  codeMetrics: CodeMetrics;
  qualityReport: string;
  performanceReport?: string;
  testResults: TestResults;
  architectureAnalysis: ArchitectureAnalysis;
  recommendations: string[];
  generatedAt: number;
}

export interface ProjectInfo {
  name: string;
  packageName: string;
  version: string;
  buildToolsVersion: string;
  compileSdk: string;
  minSdk: string;
  targetSdk: string;
  dependencies: DependencyInfo[];
}

export interface DependencyInfo {
  name: string;
  version: string;
  category: "ui" | "network" | "database" | "di" | "testing" | "other";
}

export interface CodeMetrics {
  totalFiles: number;
  totalLines: number;
  kotlinFiles: number;
  composeFiles: number;
  averageFileLength: number;
  commentRatio: number;
}

export interface TestResults {
  unitTests: {
    passed: number;
    failed: number;
    skipped: number;
    coverage?: number;
  };
  uiTests: {
    passed: number;
    failed: number;
    total: number;
  };
}

export interface ArchitectureAnalysis {
  pattern: string;
  layers: LayerInfo[];
  dependenciesClean: boolean;
  issues: string[];
}

export interface LayerInfo {
  name: string;
  fileCount: number;
  responsibility: string;
}

/**
 * 解析build.gradle.kts获取项目信息
 */
export async function parseProjectInfo(projectPath: string): Promise<ProjectInfo> {
  try {
    // Read build.gradle.kts
    const { stdout: buildGradle } = await execAsyncWithTimeout(
      `cat "${projectPath}/app/build.gradle.kts"`,
      { timeout: 10000 }
    );
    
    // Extract basic info
    const namespaceMatch = buildGradle.match(/namespace\s*=\s*"([^"]+)"/);
    const versionMatch = buildGradle.match(/versionName\s*=\s*"([^"]+)"/);
    const compileSdkMatch = buildGradle.match(/compileSdk\s*=\s*(\d+)/);
    const minSdkMatch = buildGradle.match(/minSdk\s*=\s*(\d+)/);
    const targetSdkMatch = buildGradle.match(/targetSdk\s*=\s*(\d+)/);
    
    // Parse dependencies
    const dependencies: DependencyInfo[] = [];
    const depRegex = /implementation\("([^"]+)"\)/g;
    let match;
    while ((match = depRegex.exec(buildGradle)) !== null) {
      const dep = match[1];
      const [name, version] = dep.split(":").slice(0, 2);
      
      let category: DependencyInfo["category"] = "other";
      if (name.includes("compose") || name.includes("material")) category = "ui";
      else if (name.includes("retrofit") || name.includes("okhttp")) category = "network";
      else if (name.includes("room")) category = "database";
      else if (name.includes("hilt")) category = "di";
      else if (name.includes("test") || name.includes("junit")) category = "testing";
      
      dependencies.push({ name, version: version || "unknown", category });
    }
    
    return {
      name: namespaceMatch ? namespaceMatch[1].split(".").pop() || "unknown" : "unknown",
      packageName: namespaceMatch ? namespaceMatch[1] : "unknown",
      version: versionMatch ? versionMatch[1] : "unknown",
      buildToolsVersion: "unknown",
      compileSdk: compileSdkMatch ? compileSdkMatch[1] : "unknown",
      minSdk: minSdkMatch ? minSdkMatch[1] : "unknown",
      targetSdk: targetSdkMatch ? targetSdkMatch[1] : "unknown",
      dependencies,
    };
  } catch (e) {
    error("Failed to parse project info:", e);
    return {
      name: "unknown",
      packageName: "unknown",
      version: "unknown",
      buildToolsVersion: "unknown",
      compileSdk: "unknown",
      minSdk: "unknown",
      targetSdk: "unknown",
      dependencies: [],
    };
  }
}

/**
 * 分析代码指标
 */
export async function analyzeCodeMetrics(projectPath: string): Promise<CodeMetrics> {
  try {
    // Count Kotlin files
    const { stdout: kotlinFiles } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" | wc -l`,
      { timeout: 10000 }
    );
    
    // Count Compose files (files with @Composable)
    const { stdout: composeFiles } = await execAsyncWithTimeout(
      `grep -r "@Composable" ${projectPath}/app/src --include="*.kt" -l | wc -l`,
      { timeout: 10000 }
    );
    
    // Count total lines
    const { stdout: totalLines } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" -exec cat {} + | wc -l`,
      { timeout: 10000 }
    );
    
    // Count comment lines
    const { stdout: commentLines } = await execAsyncWithTimeout(
      `find ${projectPath}/app/src -name "*.kt" -exec cat {} + | grep -c "//\\|/\\*"`,
      { timeout: 10000 }
    );
    
    const totalFiles = parseInt(kotlinFiles.trim()) || 0;
    const composeCount = parseInt(composeFiles.trim()) || 0;
    const lines = parseInt(totalLines.trim()) || 0;
    const comments = parseInt(commentLines.trim()) || 0;
    
    return {
      totalFiles,
      totalLines: lines,
      kotlinFiles: totalFiles,
      composeFiles: composeCount,
      averageFileLength: totalFiles > 0 ? Math.round(lines / totalFiles) : 0,
      commentRatio: lines > 0 ? Math.round((comments / lines) * 100) : 0,
    };
  } catch (e) {
    error("Failed to analyze code metrics:", e);
    return {
      totalFiles: 0,
      totalLines: 0,
      kotlinFiles: 0,
      composeFiles: 0,
      averageFileLength: 0,
      commentRatio: 0,
    };
  }
}

/**
 * 分析架构分层
 */
export async function analyzeArchitecture(projectPath: string): Promise<ArchitectureAnalysis> {
  const layers: LayerInfo[] = [];
  const issues: string[] = [];
  
  try {
    // Check for Clean Architecture layers
    const layerChecks = [
      { name: "Presentation", path: "presentation", responsibility: "UI and ViewModel" },
      { name: "Domain", path: "domain", responsibility: "Business logic and models" },
      { name: "Data", path: "data", responsibility: "Data sources and repositories" },
      { name: "DI", path: "di", responsibility: "Dependency injection" },
    ];
    
    for (const layer of layerChecks) {
      try {
        const { stdout } = await execAsyncWithTimeout(
          `find ${projectPath}/app/src -type d -name "${layer.path}" | wc -l`,
          { timeout: 5000 }
        );
        
        const count = parseInt(stdout.trim()) || 0;
        if (count > 0) {
          const { stdout: fileCount } = await execAsyncWithTimeout(
            `find ${projectPath}/app/src -path "*/${layer.path}/*.kt" | wc -l`,
            { timeout: 5000 }
          );
          
          layers.push({
            name: layer.name,
            fileCount: parseInt(fileCount.trim()) || 0,
            responsibility: layer.responsibility,
          });
        } else {
          issues.push(`Missing ${layer.name} layer (${layer.path})`);
        }
      } catch {
        issues.push(`Failed to analyze ${layer.name} layer`);
      }
    }
    
    // Check for architecture violations
    try {
      const { stdout: violations } = await execAsyncWithTimeout(
        `grep -r "import android" ${projectPath}/app/src/main/java/com/example/toutiao/domain --include="*.kt" | wc -l`,
        { timeout: 5000 }
      );
      
      const violationCount = parseInt(violations.trim()) || 0;
      if (violationCount > 0) {
        issues.push(`Found ${violationCount} Android imports in Domain layer`);
      }
    } catch {
      // Domain layer might not exist
    }
    
    return {
      pattern: layers.length >= 3 ? "Clean Architecture + MVI" : "Unknown",
      layers,
      dependenciesClean: issues.length === 0,
      issues,
    };
  } catch (e) {
    error("Failed to analyze architecture:", e);
    return {
      pattern: "Unknown",
      layers,
      dependenciesClean: false,
      issues: ["Failed to analyze architecture"],
    };
  }
}

/**
 * 生成完整项目报告
 */
export async function generateProjectReport(
  projectPath: string,
  options: {
    includePerformance?: boolean;
    packageName?: string;
  } = {}
): Promise<ProjectReport> {
  log("Generating comprehensive project report...");
  
  const [projectInfo, codeMetrics, qualityReport, testResults, architecture] = await Promise.all([
    parseProjectInfo(projectPath),
    analyzeCodeMetrics(projectPath),
    generateQualityReport(projectPath).catch(() => ({
      ktlintIssues: [],
      complexityMetrics: { averageComplexity: 0, maxComplexity: 0, maxComplexityFile: "", filesOverThreshold: 0 },
      duplicateCode: [],
      totalFiles: 0,
      totalLines: 0,
      timestamp: Date.now(),
    })),
    runUnitTests(projectPath).catch(() => ({
      success: false,
      passed: 0,
      failed: 0,
      skipped: 0,
    })),
    analyzeArchitecture(projectPath),
  ]);
  
  let performanceReport: string | undefined;
  if (options.includePerformance && options.packageName) {
    try {
      const metrics = await collectPerformanceMetrics(options.packageName);
      performanceReport = formatPerformanceReport(metrics);
    } catch (e) {
      error("Failed to collect performance metrics:", e);
    }
  }
  
  // Generate recommendations
  const recommendations: string[] = [];
  
  if (codeMetrics.commentRatio < 5) {
    recommendations.push("Consider adding more code comments for complex logic");
  }
  
  if (qualityReport.ktlintIssues.length > 0) {
    recommendations.push(`Fix ${qualityReport.ktlintIssues.length} ktlint issues`);
  }
  
  if (!architecture.dependenciesClean) {
    recommendations.push("Review architecture layer dependencies");
  }
  
  if (testResults.passed === 0 && testResults.failed === 0) {
    recommendations.push("Add unit tests to improve code coverage");
  }
  
  if (codeMetrics.composeFiles === 0) {
    recommendations.push("Consider migrating to Jetpack Compose for modern UI");
  }
  
  return {
    projectInfo,
    codeMetrics,
    qualityReport: formatQualityReport(qualityReport),
    performanceReport,
    testResults: {
      unitTests: {
        passed: testResults.passed,
        failed: testResults.failed,
        skipped: testResults.skipped,
      },
      uiTests: {
        passed: 0,
        failed: 0,
        total: 0,
      },
    },
    architectureAnalysis: architecture,
    recommendations,
    generatedAt: Date.now(),
  };
}

/**
 * 格式化项目报告为Markdown
 */
export function formatReportAsMarkdown(report: ProjectReport): string {
  const lines = [
    "# 📱 Android Project Report",
    "",
    `Generated: ${new Date(report.generatedAt).toLocaleString()}`,
    "",
    "## 📋 Project Information",
    "",
    `| Property | Value |`,
    `|----------|-------|`,
    `| Name | ${report.projectInfo.name} |`,
    `| Package | ${report.projectInfo.packageName} |`,
    `| Version | ${report.projectInfo.version} |`,
    `| Compile SDK | API ${report.projectInfo.compileSdk} |`,
    `| Min SDK | API ${report.projectInfo.minSdk} |`,
    `| Target SDK | API ${report.projectInfo.targetSdk} |`,
    "",
    "## 📊 Code Metrics",
    "",
    `| Metric | Value |`,
    `|--------|-------|`,
    `| Total Files | ${report.codeMetrics.totalFiles} |`,
    `| Total Lines | ${report.codeMetrics.totalLines} |`,
    `| Kotlin Files | ${report.codeMetrics.kotlinFiles} |`,
    `| Compose Files | ${report.codeMetrics.composeFiles} |`,
    `| Avg File Length | ${report.codeMetrics.averageFileLength} lines |`,
    `| Comment Ratio | ${report.codeMetrics.commentRatio}% |`,
    "",
    "## 🏗️ Architecture",
    "",
    `Pattern: **${report.architectureAnalysis.pattern}**`,
    "",
    `| Layer | Files | Responsibility |`,
    `|-------|-------|----------------|`,
    ...report.architectureAnalysis.layers.map(
      (l) => `| ${l.name} | ${l.fileCount} | ${l.responsibility} |`
    ),
    "",
    report.architectureAnalysis.dependenciesClean
      ? "✅ Dependencies are clean"
      : "⚠️ Architecture issues found:",
    ...report.architectureAnalysis.issues.map((i) => `- ${i}`),
    "",
    "## 🧪 Test Results",
    "",
    `| Type | Passed | Failed | Skipped |`,
    `|------|--------|--------|---------|`,
    `| Unit Tests | ${report.testResults.unitTests.passed} | ${report.testResults.unitTests.failed} | ${report.testResults.unitTests.skipped} |`,
    `| UI Tests | ${report.testResults.uiTests.passed} | ${report.testResults.uiTests.failed} | ${report.testResults.uiTests.total} |`,
    "",
    "## 📦 Dependencies",
    "",
    `| Name | Version | Category |`,
    `|------|---------|----------|`,
    ...report.projectInfo.dependencies.map(
      (d) => `| ${d.name} | ${d.version} | ${d.category} |`
    ),
    "",
    "## 💡 Recommendations",
    "",
    report.recommendations.length > 0
      ? report.recommendations.map((r) => `- ${r}`).join("\n")
      : "✅ No issues found!",
    "",
  ];
  
  if (report.performanceReport) {
    lines.push(
      "## 📈 Performance",
      "",
      "```",
      report.performanceReport,
      "```",
      ""
    );
  }
  
  lines.push(
    "---",
    "",
    "*Generated by AndroidDev-Assist MCP Server*"
  );
  
  return lines.join("\n");
}
