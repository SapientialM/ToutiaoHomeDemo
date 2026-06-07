import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout } from "../utils/exec.js";

/**
 * 屏幕方向控制
 * 用例：测试横屏布局 / 验证不同方向的 UI 表现
 */
export async function handleSetOrientation(args: Record<string, unknown>) {
  try {
    const orientation = (args.orientation as string) ?? "portrait"; // portrait | landscape | auto
    const serial = args.serial as string | undefined;
    const serialFlag = serial ? `-s ${serial} ` : "";

    let rotationValue: number;
    let userRotation: number;
    let accelRotation: number;

    switch (orientation) {
      case "portrait":
        rotationValue = 0;
        userRotation = 0;
        accelRotation = 0;
        break;
      case "landscape":
        rotationValue = 1;
        userRotation = 1;
        accelRotation = 0;
        break;
      case "auto":
        rotationValue = 0;
        userRotation = 0;
        accelRotation = 1;
        break;
      default:
        return { isError: true, content: [{ type: "text", text: JSON.stringify({
          success: false,
          error: `Unknown orientation: ${orientation}. Use portrait / landscape / auto`,
        }) }] };
    }

    await execAsyncWithTimeout(
      `adb ${serialFlag}shell settings put system accelerometer_rotation ${accelRotation}`,
      { timeout: 5000 }
    );
    await execAsyncWithTimeout(
      `adb ${serialFlag}shell settings put system user_rotation ${userRotation}`,
      { timeout: 5000 }
    );

    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        orientation,
        accelRotation,
        userRotation,
        hint: "Some apps override orientation via Activity manifest. If rotation did not change, that app locks orientation.",
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("set_orientation failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * GPS 位置模拟（仅模拟器有效）
 * 用例：测试位置相关功能（附近的人、地图、天气）
 */
export async function handleSetGps(args: Record<string, unknown>) {
  try {
    const lat = args.lat as number | undefined;
    const lon = args.lon as number | undefined;
    const serial = args.serial as string | undefined;

    if (lat === undefined || lon === undefined) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "lat and lon are required (decimal degrees, e.g. 39.9042 / 116.4074 for Beijing)",
      }) }] };
    }

    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "lat must be [-90, 90], lon must be [-180, 180]",
      }) }] };
    }

    // emulator: adb emu geo fix LON LAT (注意：longitude 在前)
    const emuCmd = serial ? `adb -s ${serial} emu geo fix ${lon} ${lat}` : `adb emu geo fix ${lon} ${lat}`;
    const { stderr: emuErr } = await execAsyncWithTimeout(emuCmd, { timeout: 5000 }).catch((e) => {
      throw new Error("GPS mocking requires Android Emulator. Real devices need 'mock location' app + developer option enabled.");
    });

    // 真机路径：通过 location manager 设置 mock location（需要 app 已注册为 mock provider）
    // 这里用 broadcast / settings put 兜底
    return {
      content: [{ type: "text", text: JSON.stringify({
        success: true,
        lat, lon,
        method: serial ? `emu geo fix on ${serial}` : "emu geo fix (default device)",
        warning: emuErr || "If GPS unchanged, ensure you're on an Android Emulator (not physical device).",
      }) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("set_gps failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}

/**
 * 动画缩放设置：0 = 关闭动画（最快，调试首选），1 = 系统默认
 * 用例：UI 自动化调试 / 录屏 / 性能测试
 */
export async function handleAnimationScale(args: Record<string, unknown>) {
  try {
    const scale = args.scale as number | undefined;
    const serial = args.serial as string | undefined;
    const serialFlag = serial ? `-s ${serial} ` : "";

    if (scale === undefined || scale < 0 || scale > 10) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        success: false,
        error: "scale must be a number in [0, 10]. Common values: 0 (off), 0.5, 1 (default), 2 (slow).",
      }) }] };
    }

    const keys = ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"];
    const results: Array<{ key: string; value: number; success: boolean }> = [];
    for (const k of keys) {
      try {
        await execAsyncWithTimeout(
          `adb ${serialFlag}shell settings put global ${k} ${scale}`,
          { timeout: 5000 }
        );
        results.push({ key: k, value: scale, success: true });
      } catch {
        results.push({ key: k, value: scale, success: false });
      }
    }

    const allOk = results.every((r) => r.success);
    return {
      ...(allOk ? {} : { isError: true }),
      content: [{ type: "text", text: JSON.stringify({
        success: allOk,
        scale,
        applied: results,
        hint: "0 = animations off (UI tests run instantly). Set back to 1 to restore.",
      }, null, 2) }],
    };
  } catch (e: unknown) {
    const err = e as Error;
    error("animation_scale failed:", err);
    return { isError: true, content: [{ type: "text", text: JSON.stringify({ success: false, error: err.message }) }] };
  }
}
