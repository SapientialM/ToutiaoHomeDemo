import {
  listInstalledApps,
  uninstallApp,
  clearAppData,
  stopApp,
} from "../utils/adb-enhanced.js";
import { log } from "../utils/logger.js";

export async function handleAppManagement(
  args: Record<string, unknown>,
  action: string
) {
  switch (action) {
    case "list_apps": {
      const { serial, system = false, thirdParty = true } = args as {
        serial?: string;
        system?: boolean;
        thirdParty?: boolean;
      };
      const apps = await listInstalledApps(serial, { system, thirdParty });
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            appCount: apps.length,
            apps: apps.map((a) => ({
              packageName: a.packageName,
              versionName: a.versionName,
              versionCode: a.versionCode,
            })),
          }, null, 2),
        }],
      };
    }
    
    case "app_info": {
      const { packageName, serial } = args as { packageName: string; serial?: string };
      const apps = await listInstalledApps(serial);
      const app = apps.find((a) => a.packageName === packageName);
      
      if (!app) {
        return {
          isError: true,
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              message: `App ${packageName} not found`,
            }),
          }],
        };
      }
      
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            app,
          }, null, 2),
        }],
      };
    }
    
    case "uninstall_app": {
      const { packageName, serial, keepData = false } = args as {
        packageName: string;
        serial?: string;
        keepData?: boolean;
      };
      const result = await uninstallApp(packageName, serial, keepData);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2),
        }],
      };
    }
    
    case "clear_app_data": {
      const { packageName, serial } = args as { packageName: string; serial?: string };
      const result = await clearAppData(packageName, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2),
        }],
      };
    }
    
    case "stop_app": {
      const { packageName, serial } = args as { packageName: string; serial?: string };
      const result = await stopApp(packageName, serial);
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
        content: [{ type: "text", text: `Unknown app management action: ${action}` }],
      };
  }
}
