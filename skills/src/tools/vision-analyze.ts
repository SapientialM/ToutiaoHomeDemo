import OpenAI from "openai";
import fs from "fs";
import path from "path";
import { log, error } from "../utils/logger.js";
import { execAsyncWithTimeout } from "../utils/exec.js";

let client: OpenAI | null = null;

function getClient(): OpenAI {
  if (!client) {
    const apiKey = process.env.MOONSHOT_API_KEY;
    if (!apiKey) {
      throw new Error("MOONSHOT_API_KEY not set in environment");
    }
    client = new OpenAI({
      apiKey,
      baseURL: "https://api.moonshot.cn/v1",
    });
  }
  return client;
}

/**
 * Resize image for API upload with proper cleanup.
 */
async function resizeForApiAsync(imagePath: string): Promise<string> {
  const resizedPath = imagePath.replace(/\.(png|jpg|jpeg)$/, "_resized.$1");
  try {
    const cmd = `python3 -c "
from PIL import Image
img = Image.open('${imagePath}')
w, h = img.size
if max(w, h) > 320:
    ratio = 320 / max(w, h)
    img = img.resize((int(w*ratio), int(h*ratio)), Image.LANCZOS)
    img.save('${resizedPath}')
"`;
    await execAsyncWithTimeout(cmd, { timeout: 10000 });
    return fs.existsSync(resizedPath) ? resizedPath : imagePath;
  } catch {
    return imagePath;
  }
}

/**
 * Clean up a temporary resized file.
 */
function cleanupResizedFile(resizedPath: string, originalPath: string): void {
  if (resizedPath !== originalPath) {
    try {
      fs.unlinkSync(resizedPath);
    } catch {
      // Ignore cleanup errors
    }
  }
}

export async function analyzeWithVision(
  imagePath: string,
  prompt?: string,
  systemPrompt?: string,
): Promise<string> {
  if (!fs.existsSync(imagePath)) {
    throw new Error(`File not found: ${imagePath}`);
  }

  // Resize to max 320px on the longest side for faster API response
  const resizedPath = await resizeForApiAsync(imagePath);

  let readPath: string;
  try {
    readPath = fs.existsSync(resizedPath) ? resizedPath : imagePath;
    const imageBuffer = fs.readFileSync(readPath);
    const base64Image = imageBuffer.toString("base64");
    const ext = path.extname(readPath).slice(1) || "png";
    const mimeType = ext === "jpg" || ext === "jpeg" ? "image/jpeg" : "image/png";
    const imageUrl = `data:${mimeType};base64,${base64Image}`;

    const defaultPrompt = `You are an Android UI expert. Analyze this screenshot and report:

## Layout
- What type of screen is this? (news feed, detail page, etc.)
- Are there alignment/spacing issues? Be specific about which element and by how much.
- Is the content density appropriate?

## Components  
- How many cards/items are visible? List each one.
- Are images loading correctly?
- Is text readable (font size, contrast, truncation)?

## Issues
List every UI problem you see, with:
- Exact location/which component
- What's wrong
- Suggested fix (include specific dp/px/sp values for Jetpack Compose)

## Design Match
- Does the actual implementation match the expected news-feed design?
- Header: is the red correct? (#FF5757)
- Bottom nav: white background? selected tab indicator (#171E38)?
- Cards: proper spacing between them?

Be very specific and quantitative. Measure approximate padding/margins using the screen dimensions as reference.`;

    const response = await getClient().chat.completions.create(
      {
        model: "kimi-k2.6",
        messages: [
          {
            role: "system",
            content: systemPrompt || "You are an Android UI/UX expert. Always give specific, actionable feedback with exact measurements and Compose code suggestions.",
          },
          {
            role: "user",
            content: [
              { type: "image_url", image_url: { url: imageUrl } },
              { type: "text", text: prompt || defaultPrompt },
            ],
          },
        ],
        max_tokens: 4096,
        temperature: 1.0,
      },
      { timeout: 120000 }
    );

    const msg = response.choices[0]?.message;
    const content = msg?.content;
    if (!content) {
      throw new Error("Vision model returned empty response");
    }
    return content;
  } finally {
    // Always clean up resized temp file
    cleanupResizedFile(resizedPath, imagePath);
  }
}

export async function compareWithVision(baselinePath: string, currentPath: string, prompt?: string): Promise<string> {
  if (!fs.existsSync(baselinePath)) {
    throw new Error(`Baseline not found: ${baselinePath}`);
  }
  if (!fs.existsSync(currentPath)) {
    throw new Error(`Current not found: ${currentPath}`);
  }

  const encode = (p: string) => {
    const readPath = fs.existsSync(p) ? p : p;
    const buf = fs.readFileSync(readPath);
    const ext = path.extname(readPath).slice(1) || "png";
    const mime = ext === "jpg" || ext === "jpeg" ? "image/jpeg" : "image/png";
    return `data:${mime};base64,${buf.toString("base64")}`;
  };

  const defaultPrompt = `Compare these two Android screenshots. The first is the design/baseline, the second is the current implementation.

Focus on:
1. Layout differences (position, spacing, sizing)
2. Color differences (especially the header red and bottom nav)
3. Content differences (more/less cards, different text)
4. Specific issues introduced in the current version

For each difference, state whether it's acceptable or needs fixing, and suggest exact Compose code changes.`;

  const response = await getClient().chat.completions.create(
    {
      model: "kimi-k2.6",
      messages: [
        {
          role: "system",
          content: "You are an Android UI testing expert. Compare screenshots precisely and give actionable feedback.",
        },
        {
          role: "user",
          content: [
            { type: "text", text: "Here is the baseline/design screenshot:" },
            { type: "image_url", image_url: { url: encode(baselinePath) } },
            { type: "text", text: "Here is the current implementation screenshot:" },
            { type: "image_url", image_url: { url: encode(currentPath) } },
            { type: "text", text: prompt || defaultPrompt },
          ],
        },
      ],
      max_tokens: 4096,
      temperature: 1.0,
    },
    { timeout: 120000 }
  );

  const msg = response.choices[0]?.message;
  return msg?.content || "Comparison failed";
}
