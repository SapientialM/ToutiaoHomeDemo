import { execAsyncWithTimeout } from "./exec.js";
import { log, error } from "./logger.js";

/**
 * 构建和部署工具
 * 支持多渠道打包、签名管理、版本控制
 */

export interface BuildConfig {
  projectPath: string;
  variant: "debug" | "release";
  flavor?: string;
  buildType?: string;
}

export interface BuildResult {
  success: boolean;
  apkPath?: string;
  aabPath?: string;
  buildTime: number;
  error?: string;
  warnings?: string[];
}

export interface SigningConfig {
  storeFile: string;
  storePassword: string;
  keyAlias: string;
  keyPassword: string;
}

/**
 * 构建APK
 */
export async function buildApk(config: BuildConfig): Promise<BuildResult> {
  const start = Date.now();
  const { projectPath, variant, flavor } = config;
  
  let gradleTask = "assemble";
  if (flavor) {
    gradleTask += flavor.charAt(0).toUpperCase() + flavor.slice(1);
  }
  gradleTask += variant.charAt(0).toUpperCase() + variant.slice(1);
  
  log(`Building: ./gradlew ${gradleTask}`);
  
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ${gradleTask}`,
      { cwd: projectPath, timeout: 300000 }
    );
    
    const combinedOutput = stdout + stderr;
    
    // Extract APK path
    const apkMatch = combinedOutput.match(/outputs\/apk\/[^\s]+\.apk/);
    const apkPath = apkMatch ? `${projectPath}/app/build/${apkMatch[0]}` : undefined;
    
    // Extract warnings
    const warnings = combinedOutput
      .split("\n")
      .filter((line) => line.includes("warning") || line.includes("Warning"));
    
    return {
      success: true,
      apkPath,
      buildTime: Date.now() - start,
      warnings: warnings.length > 0 ? warnings : undefined,
    };
  } catch (e) {
    const err = e as Error & { stderr?: string };
    return {
      success: false,
      buildTime: Date.now() - start,
      error: err.stderr || err.message || "Build failed",
    };
  }
}

/**
 * 构建AAB (Android App Bundle)
 */
export async function buildAab(config: BuildConfig): Promise<BuildResult> {
  const start = Date.now();
  const { projectPath, variant, flavor } = config;
  
  let gradleTask = "bundle";
  if (flavor) {
    gradleTask += flavor.charAt(0).toUpperCase() + flavor.slice(1);
  }
  gradleTask += variant.charAt(0).toUpperCase() + variant.slice(1);
  
  log(`Building AAB: ./gradlew ${gradleTask}`);
  
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ${gradleTask}`,
      { cwd: projectPath, timeout: 300000 }
    );
    
    const combinedOutput = stdout + stderr;
    const aabMatch = combinedOutput.match(/outputs\/bundle\/[^\s]+\.aab/);
    const aabPath = aabMatch ? `${projectPath}/app/build/${aabMatch[0]}` : undefined;
    
    return {
      success: true,
      aabPath,
      buildTime: Date.now() - start,
    };
  } catch (e) {
    const err = e as Error & { stderr?: string };
    return {
      success: false,
      buildTime: Date.now() - start,
      error: err.stderr || err.message || "AAB build failed",
    };
  }
}

/**
 * 清理构建缓存
 */
export async function cleanBuild(projectPath: string = "."): Promise<{ success: boolean; message: string }> {
  try {
    await execAsyncWithTimeout(
      `./gradlew clean`,
      { cwd: projectPath, timeout: 120000 }
    );
    return { success: true, message: "Build cache cleaned" };
  } catch (e) {
    const err = e as Error;
    return { success: false, message: err.message || "Clean failed" };
  }
}

/**
 * 运行单元测试
 */
export async function runUnitTests(
  projectPath: string = ".",
  module?: string
): Promise<{
  success: boolean;
  passed: number;
  failed: number;
  skipped: number;
  report?: string;
}> {
  try {
    const task = module ? `${module}:test` : "test";
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew ${task}`,
      { cwd: projectPath, timeout: 300000 }
    );
    
    const output = stdout + stderr;
    
    // Parse test results
    const passedMatch = output.match(/(\d+) tests? completed/);
    const failedMatch = output.match(/(\d+) failed/);
    const skippedMatch = output.match(/(\d+) skipped/);
    
    const passed = passedMatch ? parseInt(passedMatch[1]) : 0;
    const failed = failedMatch ? parseInt(failedMatch[1]) : 0;
    const skipped = skippedMatch ? parseInt(skippedMatch[1]) : 0;
    
    return {
      success: failed === 0,
      passed,
      failed,
      skipped,
      report: output.includes("BUILD SUCCESSFUL") ? "All tests passed" : "Some tests failed",
    };
  } catch (e) {
    const err = e as Error & { stdout?: string; stderr?: string };
    const output = (err.stdout || "") + (err.stderr || "");
    
    return {
      success: false,
      passed: 0,
      failed: 0,
      skipped: 0,
      report: output || err.message || "Test execution failed",
    };
  }
}

/**
 * 运行仪器化测试
 */
export async function runInstrumentedTests(
  projectPath: string = "."
): Promise<{
  success: boolean;
  passed: number;
  failed: number;
  message: string;
}> {
  try {
    const { stdout, stderr } = await execAsyncWithTimeout(
      `./gradlew connectedAndroidTest`,
      { cwd: projectPath, timeout: 600000 }
    );
    
    const output = stdout + stderr;
    
    return {
      success: output.includes("BUILD SUCCESSFUL"),
      passed: 0,
      failed: 0,
      message: output.includes("BUILD SUCCESSFUL")
        ? "Instrumented tests passed"
        : "Instrumented tests failed",
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      passed: 0,
      failed: 0,
      message: err.message || "Instrumented test execution failed",
    };
  }
}

/**
 * 签名APK
 */
export async function signApk(
  apkPath: string,
  signingConfig: SigningConfig
): Promise<{ success: boolean; signedApkPath?: string; error?: string }> {
  try {
    const outputPath = apkPath.replace(".apk", "-signed.apk");
    
    await execAsyncWithTimeout(
      `jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 ` +
        `-keystore "${signingConfig.storeFile}" ` +
        `-storepass "${signingConfig.storePassword}" ` +
        `"${apkPath}" "${signingConfig.keyAlias}"`,
      { timeout: 60000 }
    );
    
    // Verify signature
    await execAsyncWithTimeout(
      `jarsigner -verify -verbose "${apkPath}"`,
      { timeout: 30000 }
    );
    
    return {
      success: true,
      signedApkPath: outputPath,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      error: err.message || "Signing failed",
    };
  }
}

/**
 * 对齐APK (zipalign)
 */
export async function alignApk(
  apkPath: string
): Promise<{ success: boolean; alignedPath?: string; error?: string }> {
  try {
    const outputPath = apkPath.replace(".apk", "-aligned.apk");
    
    await execAsyncWithTimeout(
      `zipalign -v 4 "${apkPath}" "${outputPath}"`,
      { timeout: 60000 }
    );
    
    return {
      success: true,
      alignedPath: outputPath,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      error: err.message || "Alignment failed",
    };
  }
}

/**
 * 获取APK信息
 */
export async function getApkInfo(apkPath: string): Promise<{
  packageName: string;
  versionName: string;
  versionCode: string;
  minSdk: string;
  targetSdk: string;
}> {
  try {
    const { stdout } = await execAsyncWithTimeout(
      `aapt dump badging "${apkPath}"`,
      { timeout: 30000 }
    );
    
    const packageMatch = stdout.match(/package: name='([^']+)'/);
    const versionNameMatch = stdout.match(/versionName='([^']+)'/);
    const versionCodeMatch = stdout.match(/versionCode='([^']+)'/);
    const sdkMatch = stdout.match(/sdkVersion:'([^']+)'/);
    const targetSdkMatch = stdout.match(/targetSdkVersion:'([^']+)'/);
    
    return {
      packageName: packageMatch ? packageMatch[1] : "unknown",
      versionName: versionNameMatch ? versionNameMatch[1] : "unknown",
      versionCode: versionCodeMatch ? versionCodeMatch[1] : "unknown",
      minSdk: sdkMatch ? sdkMatch[1] : "unknown",
      targetSdk: targetSdkMatch ? targetSdkMatch[1] : "unknown",
    };
  } catch (e) {
    error("Failed to get APK info:", e);
    return {
      packageName: "unknown",
      versionName: "unknown",
      versionCode: "unknown",
      minSdk: "unknown",
      targetSdk: "unknown",
    };
  }
}

/**
 * 完整发布流程
 */
export async function releaseBuild(
  projectPath: string,
  signingConfig: SigningConfig
): Promise<{
  success: boolean;
  apkPath?: string;
  buildTime: number;
  error?: string;
}> {
  const start = Date.now();
  
  try {
    // 1. Clean
    await cleanBuild(projectPath);
    
    // 2. Build release
    const buildResult = await buildApk({
      projectPath,
      variant: "release",
    });
    
    if (!buildResult.success || !buildResult.apkPath) {
      throw new Error(buildResult.error || "Build failed");
    }
    
    // 3. Sign
    const signResult = await signApk(buildResult.apkPath, signingConfig);
    if (!signResult.success) {
      throw new Error(signResult.error || "Signing failed");
    }
    
    // 4. Align
    const alignResult = await alignApk(signResult.signedApkPath!);
    if (!alignResult.success) {
      throw new Error(alignResult.error || "Alignment failed");
    }
    
    return {
      success: true,
      apkPath: alignResult.alignedPath,
      buildTime: Date.now() - start,
    };
  } catch (e) {
    const err = e as Error;
    return {
      success: false,
      buildTime: Date.now() - start,
      error: err.message,
    };
  }
}
