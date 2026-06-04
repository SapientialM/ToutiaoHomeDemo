import { generateProjectReport, formatReportAsMarkdown } from "../utils/report.js";
import { log } from "../utils/logger.js";

export async function handleProjectReport(args: Record<string, unknown>) {
  const {
    projectPath = ".",
    includePerformance = false,
    packageName,
    format = "markdown",
  } = args as {
    projectPath?: string;
    includePerformance?: boolean;
    packageName?: string;
    format?: "markdown" | "json";
  };
  
  try {
    log("Generating project report...");
    const report = await generateProjectReport(projectPath, {
      includePerformance,
      packageName,
    });
    
    if (format === "markdown") {
      const markdown = formatReportAsMarkdown(report);
      return {
        content: [{
          type: "text",
          text: markdown,
        }],
      };
    }
    
    return {
      content: [{
        type: "text",
        text: JSON.stringify({
          success: true,
          report: {
            projectInfo: report.projectInfo,
            codeMetrics: report.codeMetrics,
            testResults: report.testResults,
            architectureAnalysis: report.architectureAnalysis,
            recommendations: report.recommendations,
            generatedAt: report.generatedAt,
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
          error: err.message || "Failed to generate project report",
        }),
      }],
    };
  }
}
