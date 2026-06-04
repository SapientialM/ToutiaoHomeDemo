import { generateQualityReport, formatQualityReport, runKtlintFormat } from "../utils/quality.js";
import { log } from "../utils/logger.js";

export async function handleCodeQuality(args: Record<string, unknown>) {
  const { projectPath = ".", fix = false } = args as { projectPath?: string; fix?: boolean };
  
  try {
    if (fix) {
      const fixResult = await runKtlintFormat(projectPath);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: fixResult.success,
            action: "ktlint_format",
            fixed: fixResult.fixed,
            message: fixResult.message,
          }, null, 2),
        }],
      };
    }
    
    const report = await generateQualityReport(projectPath);
    const formattedReport = formatQualityReport(report);
    
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          report: formattedReport,
          summary: {
            totalFiles: report.totalFiles,
            totalLines: report.totalLines,
            ktlintIssues: report.ktlintIssues.length,
            averageComplexity: report.complexityMetrics.averageComplexity,
            maxComplexity: report.complexityMetrics.maxComplexity,
            filesOverThreshold: report.complexityMetrics.filesOverThreshold,
          },
        }, null, 2),
      }],
    };
  } catch (e) {
    const err = e as Error;
    return {
      isError: true,
      content: [{
        type: "text",
        text: JSON.stringify({
          success: false,
          error: err.message || "Code quality check failed",
        }),
      }],
    };
  }
}
