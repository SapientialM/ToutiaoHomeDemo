import { handleScreenshot } from "../tools/screenshot.js";
import { handleAnalyzeScreenshot } from "../tools/analyze.js";
import fs from "fs";

async function main() {
  console.log("=== 1. Taking Screenshot ===");
  const screenshotResult = await handleScreenshot({});
  console.log(JSON.stringify(screenshotResult, null, 2));
  
  if (!screenshotResult.isError) {
    const pathMatch = screenshotResult.content[0].text.match(/Screenshot saved to (.+)/);
    if (pathMatch) {
      const screenshotPath = pathMatch[1];
      console.log("\n=== 2. Analyzing Screenshot ===");
      const analyzeResult = await handleAnalyzeScreenshot({ filePath: screenshotPath });
      console.log(analyzeResult.content[0].text);
      
      // Save report
      fs.writeFileSync("test_report.txt", analyzeResult.content[0].text);
      console.log("\nReport saved to: test_report.txt");
    }
  }
}

main().catch(console.error);
