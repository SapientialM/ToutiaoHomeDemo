import { listDevices, getDeviceDetails, shellCommand, clearLogs, recordScreen } from "../utils/adb-enhanced.js";
import { log } from "../utils/logger.js";

export async function handleDeviceManagement(
  args: Record<string, unknown>,
  action: string
) {
  switch (action) {
    case "list_devices": {
      const devices = await listDevices();
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            deviceCount: devices.length,
            devices: devices.map((d) => ({
              serial: d.serial,
              state: d.state,
              model: d.model,
              androidVersion: d.androidVersion,
              sdkVersion: d.sdkVersion,
              screenResolution: d.screenResolution,
              density: d.density,
            })),
          }, null, 2),
        }],
      };
    }
    
    case "device_info": {
      const { serial } = args as { serial: string };
      const details = await getDeviceDetails(serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            serial,
            details,
          }, null, 2),
        }],
      };
    }
    
    case "shell_command": {
      const { command, serial } = args as { command: string; serial?: string };
      const result = await shellCommand(command, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: result.success,
            output: result.output,
          }, null, 2),
        }],
      };
    }
    
    case "clear_logs": {
      const { serial } = args as { serial?: string };
      const result = await clearLogs(serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2),
        }],
      };
    }
    
    case "record_screen": {
      const { duration = 10, outputPath = "./screen_record.mp4", serial } = args as {
        duration?: number;
        outputPath?: string;
        serial?: string;
      };
      const result = await recordScreen(duration, outputPath, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2),
        }],
      };
    }
    
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown device management action: ${action}` }],
      };
  }
}
