import { buildApk, buildAab, cleanBuild, runUnitTests, runInstrumentedTests, getApkInfo } from "../utils/build-deploy.js";
import { installApk, startApp } from "../utils/adb-enhanced.js";
import { log } from "../utils/logger.js";

export async function handleBuildDeploy(
  args: Record<string, unknown>,
  action?: string
) {
  switch (action || "build") {
    case "build": {
      const { projectPath = ".", variant = "debug", flavor } = args as {
        projectPath?: string;
        variant?: "debug" | "release";
        flavor?: string;
      };
      
      const result = await buildApk({ projectPath, variant, flavor });
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            apkPath: result.apkPath,
            buildTime: result.buildTime,
            warnings: result.warnings,
            error: result.error,
          }, null, 2),
        }],
      };
    }
    
    case "build_aab": {
      const { projectPath = ".", variant = "release", flavor } = args as {
        projectPath?: string;
        variant?: "debug" | "release";
        flavor?: string;
      };
      
      const result = await buildAab({ projectPath, variant, flavor });
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            aabPath: result.aabPath,
            buildTime: result.buildTime,
            error: result.error,
          }, null, 2),
        }],
      };
    }
    
    case "clean": {
      const { projectPath = "." } = args as { projectPath?: string };
      const result = await cleanBuild(projectPath);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2),
        }],
      };
    }
    
    case "run_tests": {
      const { projectPath = ".", type = "unit", module } = args as {
        projectPath?: string;
        type?: "unit" | "instrumented" | "all";
        module?: string;
      };
      
      const results: Record<string, unknown> = {};
      
      if (type === "unit" || type === "all") {
        const unitResult = await runUnitTests(projectPath, module);
        results.unit = unitResult;
      }
      
      if (type === "instrumented" || type === "all") {
        const instResult = await runInstrumentedTests(projectPath);
        results.instrumented = instResult;
      }
      
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: Object.values(results).every((r: any) => r.success),
            results,
          }, null, 2),
        }],
      };
    }
    
    case "apk_info": {
      const { apkPath } = args as { apkPath: string };
      const info = await getApkInfo(apkPath);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            info,
          }, null, 2),
        }],
      };
    }
    
    case "full_deploy": {
      const {
        projectPath = ".",
        variant = "debug",
        packageName,
        autoLaunch = true,
        serial,
      } = args as {
        projectPath?: string;
        variant?: "debug" | "release";
        packageName: string;
        autoLaunch?: boolean;
        serial?: string;
      };
      
      // 1. Clean
      log("Step 1: Cleaning build...");
      await cleanBuild(projectPath);
      
      // 2. Build
      log("Step 2: Building APK...");
      const buildResult = await buildApk({ projectPath, variant });
      
      if (!buildResult.success || !buildResult.apkPath) {
        return {
          isError: true,
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              stage: "build",
              error: buildResult.error,
            }),
          }],
        };
      }
      
      // 3. Install
      log("Step 3: Installing APK...");
      const installResult = await installApk(buildResult.apkPath, serial, { reinstall: true });
      
      if (!installResult.success) {
        return {
          isError: true,
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              stage: "install",
              error: installResult.message,
            }),
          }],
        };
      }
      
      // 4. Launch
      if (autoLaunch) {
        log("Step 4: Launching app...");
        await startApp(packageName, undefined, serial);
      }
      
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            apkPath: buildResult.apkPath,
            buildTime: buildResult.buildTime,
            installed: true,
            launched: autoLaunch,
          }, null, 2),
        }],
      };
    }
    
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown build/deploy action: ${action}` }],
      };
  }
}
