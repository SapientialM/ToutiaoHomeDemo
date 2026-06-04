import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { log } from "../utils/logger.js";

function ensureDir(dir: string) {
  mkdirSync(dir, { recursive: true });
}

export async function handleVerifyUI(args: Record<string, unknown>) {
  const { type, baselinePath, currentPath, checkText, checkColor, x, y } = args as {
    type: string;
    baselinePath?: string;
    currentPath?: string;
    checkText?: string;
    checkColor?: string;
    x?: number;
    y?: number;
  };

  switch (type) {
    case "compare": {
      if (!baselinePath || !currentPath) {
        return {
          isError: true,
          content: [{ type: "text", text: "compare requires baselinePath and currentPath" }],
        };
      }
      return handleCompare(baselinePath, currentPath);
    }
    case "color": {
      if (!currentPath || x === undefined || y === undefined || !checkColor) {
        return {
          isError: true,
          content: [{ type: "text", text: "color check requires currentPath, x, y, checkColor" }],
        };
      }
      return handleColorCheck(currentPath, x, y, checkColor);
    }
    case "ocr": {
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({
              note: "OCR requires tesseract.js which is not bundled. Install with: npm install tesseract.js",
              checkText,
            }),
          },
        ],
      };
    }
    default:
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown verify type: ${type}` }],
      };
  }
}

async function handleCompare(baselinePath: string, currentPath: string) {
  try {
    const sharp = (await import("sharp")).default;
    const pixelmatch = (await import("pixelmatch")).default;
    const { PNG } = await import("pngjs");

    const baselineBuf = readFileSync(baselinePath);
    const currentBuf = readFileSync(currentPath);

    const baselinePng = PNG.sync.read(baselineBuf);
    const currentPng = PNG.sync.read(currentBuf);

    const { width, height } = baselinePng;
    const diff = new PNG({ width, height });

    const diffPixels = pixelmatch(baselinePng.data, currentPng.data, diff.data, width, height, {
      threshold: 0.1,
      includeAA: true,
    });

    const diffPercentage = (diffPixels / (width * height)) * 100;
    const diffDir = "./reports";
    ensureDir(diffDir);
    const diffPath = `${diffDir}/diff_${Date.now()}.png`;
    writeFileSync(diffPath, PNG.sync.write(diff));

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({
            diffPixels,
            diffPercentage: parseFloat(diffPercentage.toFixed(2)),
            isMatch: diffPercentage < 1.0,
            diffImagePath: diffPath,
          }),
        },
      ],
    };
  } catch (e: unknown) {
    const err = e as Error;
    return {
      isError: true,
      content: [{ type: "text", text: `Compare failed: ${err.message}` }],
    };
  }
}

async function handleColorCheck(
  screenshotPath: string,
  x: number,
  y: number,
  expectedHex: string,
) {
  try {
    const sharp = (await import("sharp")).default;
    const buffer = await sharp(screenshotPath)
      .extract({ left: Math.round(x), top: Math.round(y), width: 1, height: 1 })
      .raw()
      .toBuffer();
    const data = buffer as Buffer;

    const [r, g, b] = data;
    const actualHex = `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({
            match: actualHex.toLowerCase() === expectedHex.toLowerCase(),
            expected: expectedHex,
            actual: actualHex,
            x,
            y,
          }),
        },
      ],
    };
  } catch (e: unknown) {
    const err = e as Error;
    return {
      isError: true,
      content: [{ type: "text", text: `Color check failed: ${err.message}` }],
    };
  }
}
