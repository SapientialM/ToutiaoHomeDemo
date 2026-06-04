import { pushFile, pullFile } from "../utils/adb-enhanced.js";
import { log } from "../utils/logger.js";

export async function handleFileOperations(
  args: Record<string, unknown>,
  action: string
) {
  switch (action) {
    case "push": {
      const { localPath, remotePath, serial } = args as {
        localPath: string;
        remotePath: string;
        serial?: string;
      };
      
      const result = await pushFile(localPath, remotePath, serial);
      return {
        content: [{
          type: "text",
          text: JSON.stringify(result, null, 2),
        }],
      };
    }
    
    case "pull": {
      const { remotePath, localPath, serial } = args as {
        remotePath: string;
        localPath: string;
        serial?: string;
      };
      
      const result = await pullFile(remotePath, localPath, serial);
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
        content: [{ type: "text", text: `Unknown file operation: ${action}` }],
      };
  }
}
