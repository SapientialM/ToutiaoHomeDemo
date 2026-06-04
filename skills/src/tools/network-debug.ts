import { getNetworkState, setNetworkState } from "../utils/adb-enhanced.js";
import { log } from "../utils/logger.js";

export async function handleNetworkDebug(
  args: Record<string, unknown>,
  action: string
) {
  switch (action) {
    case "get_state": {
      const { serial } = args as { serial?: string };
      const state = await getNetworkState(serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            network: state,
          }, null, 2),
        }],
      };
    }
    
    case "set_state": {
      const { type, enabled, serial } = args as {
        type: "wifi" | "mobile" | "airplane";
        enabled: boolean;
        serial?: string;
      };
      
      const result = await setNetworkState(type, enabled, serial);
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
        content: [{ type: "text", text: `Unknown network debug action: ${action}` }],
      };
  }
}
