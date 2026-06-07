/**
 * Vision 端到端基准测试 —— Minimax 多模型对比
 *
 * 测试矩阵：model × tool，统计：
 * - 耗时、响应 token、JSON 解析质量
 *
 * 运行：npm run test:vision-bench
 * 跳过：未设 MINIMAX_API_KEY 时自动 skip
 */
import { config as loadEnv } from "dotenv";
loadEnv();

import { describe, it } from "vitest";
import {
  handleListDesignFiles,
  handleExtractDesignSpec,
  handleExtractDesignTokens,
  handleExtractComponents,
  handleDesignToCompose,
} from "../tools/design-spec.js";
import fs from "node:fs";
import path from "node:path";

const DESIGN_DIR = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/design";
const TEST_IMAGE = path.join(DESIGN_DIR, "首页-推荐.jpg");

function roughTokenCount(text: string): number {
  const cjk = (text.match(/[一-鿿]/g) || []).length;
  const other = text.length - cjk;
  return Math.ceil(cjk * 0.75 + other / 4);
}

interface BenchResult {
  test: string;
  totalMs: number;
  responseTokens: number;
  parseOk: boolean;
  jsonShape?: string;
  preview: string;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function benchOne(
  testName: string,
  fn: () => Promise<{ text: string; parsed?: unknown }>
): Promise<BenchResult> {
  const t0 = Date.now();
  try {
    const r = await fn();
    const text = r.text || "";
    const parsedShape = r.parsed ? Object.keys(r.parsed as object).join(",").slice(0, 100) : undefined;
    return {
      test: testName,
      totalMs: Date.now() - t0,
      responseTokens: roughTokenCount(text),
      parseOk: r.parsed !== undefined,
      jsonShape: parsedShape,
      preview: text.slice(0, 250).replace(/\n/g, " "),
    };
  } catch (e: unknown) {
    return {
      test: testName,
      totalMs: Date.now() - t0,
      responseTokens: 0,
      parseOk: false,
      preview: `ERROR: ${(e as Error).message}`,
    };
  }
}

describe("Vision API benchmark", () => {
  const hasMinimax = !!process.env.MINIMAX_API_KEY;
  const activeProvider = process.env.VISION_PROVIDER || "minimax";

  if (!hasMinimax) {
    it.skip("No MINIMAX_API_KEY set, skipping", () => {});
    return;
  }

  it("runs full benchmark matrix", async () => {
    if (!fs.existsSync(TEST_IMAGE)) {
      console.log(`❌ Test image not found: ${TEST_IMAGE}`);
      return;
    }

    const testMatrix: Array<{ model: string }> = [
      { model: "MiniMax-M3" },                // thinking-disabled, 最快
      { model: "MiniMax-M2.7-highspeed" },   // 100 TPS
      { model: "MiniMax-M2.7" },              // 60 TPS baseline
    ];

    console.log("\n═══════════════════════════════════════════════════════════════");
    console.log("🧪 VISION API BENCHMARK — Minimax multi-model comparison");
    console.log("═══════════════════════════════════════════════════════════════");
    console.log(`📐 Test image: ${TEST_IMAGE} (${Math.round(fs.statSync(TEST_IMAGE).size / 1024)}KB)`);
    console.log(`🔑 MINIMAX_API_KEY: ${hasMinimax ? "yes" : "no"}  active=${activeProvider}`);
    console.log("");

    // ── list_design_files (no vision API) ──
    const t0 = Date.now();
    const listRes = await handleListDesignFiles({ dir: DESIGN_DIR });
    const listText = listRes.content[0].text;
    const listParsed = JSON.parse(listText) as { count: number; files: { name: string }[] };
    console.log(`【0】 list_design_files  (no API, baseline)`);
    console.log(`    耗时：${Date.now() - t0}ms`);
    console.log(`    输出：${listParsed.count} 个设计稿`);
    console.log("");

    const all: BenchResult[] = [];

    for (const cfg of testMatrix) {
      console.log(`\n── minimax/${cfg.model} ──`);

      // 1. extract_design_spec (json)
      const r1 = await benchOne(`[minimax/${cfg.model}] extract_design_spec(json)`, async () => {
        const r = await handleExtractDesignSpec({ imagePath: TEST_IMAGE, format: "json", model: cfg.model });
        return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
      });
      console.log(`    ① extract_design_spec(json): ${r1.totalMs}ms, ~${r1.responseTokens} tok, parse=${r1.parseOk ? "✅" : "❌"}`);
      if (r1.jsonShape) console.log(`       keys: ${r1.jsonShape}`);
      console.log(`       preview: ${r1.preview}...`);
      all.push(r1);
      await sleep(2000);

      // 2. extract_design_tokens
      const r2 = await benchOne(`[minimax/${cfg.model}] extract_design_tokens`, async () => {
        const r = await handleExtractDesignTokens({ imagePath: TEST_IMAGE, model: cfg.model });
        return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
      });
      console.log(`    ② extract_design_tokens:     ${r2.totalMs}ms, ~${r2.responseTokens} tok, parse=${r2.parseOk ? "✅" : "❌"}`);
      console.log(`       preview: ${r2.preview}...`);
      all.push(r2);
      await sleep(2000);

      // 3. extract_design_components
      const r3 = await benchOne(`[minimax/${cfg.model}] extract_design_components`, async () => {
        const r = await handleExtractComponents({ imagePath: TEST_IMAGE, model: cfg.model });
        return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
      });
      console.log(`    ③ extract_design_components: ${r3.totalMs}ms, ~${r3.responseTokens} tok, parse=${r3.parseOk ? "✅" : "❌"}`);
      if (r3.jsonShape) console.log(`       keys: ${r3.jsonShape}`);
      console.log(`       preview: ${r3.preview}...`);
      all.push(r3);
      await sleep(2000);
    }

    // ── 汇总 ──
    console.log("\n═══════════════════════════════════════════════════════════════");
    console.log("📊 SUMMARY — by model");
    console.log("═══════════════════════════════════════════════════════════════");

    const byModel: Record<string, BenchResult[]> = {};
    for (const r of all) {
      const key = r.test.split("]")[0] + "]";
      if (!byModel[key]) byModel[key] = [];
      byModel[key].push(r);
    }

    const summary: Array<{ model: string; avgMs: number; totalTokens: number; parseOk: number; total: number }> = [];
    for (const [model, results] of Object.entries(byModel)) {
      const avgMs = results.reduce((s, r) => s + r.totalMs, 0) / results.length;
      const totalTokens = results.reduce((s, r) => s + r.responseTokens, 0);
      const ok = results.filter((r) => r.parseOk).length;
      summary.push({ model, avgMs, totalTokens, parseOk: ok, total: results.length });
    }

    summary.sort((a, b) => a.avgMs - b.avgMs);
    for (const s of summary) {
      console.log(
        `  ${s.model.padEnd(34)} avg=${s.avgMs.toFixed(0).padStart(5)}ms  ` +
        `tokens=${s.totalTokens.toString().padStart(5)}  ` +
        `parse=${s.parseOk}/${s.total}`
      );
    }

    // ── 详细耗时分布 ──
    console.log("\n耗时分布：");
    all.sort((a, b) => b.totalMs - a.totalMs).forEach((r) => {
      const bar = "█".repeat(Math.min(60, Math.round(r.totalMs / 1000)));
      console.log(`  ${r.test.padEnd(45)} ${String(r.totalMs).padStart(6)}ms ${bar}`);
    });

    // ── 输出优化建议 ──
    console.log("\n═══════════════════════════════════════════════════════════════");
    console.log("💡 RECOMMENDATIONS");
    console.log("═══════════════════════════════════════════════════════════════");
    const fastest = summary[0];
    if (fastest) {
      console.log(`最快模型：${fastest.model}（${fastest.avgMs.toFixed(0)}ms 平均）`);
      console.log(`推荐在 server.ts 中将 VISION_PROVIDER 默认值改为最快模型的提供商。`);
    }
    const mostReliable = [...summary].sort((a, b) => b.parseOk / b.total - a.parseOk / a.total)[0];
    if (mostReliable) {
      console.log(`最可靠模型：${mostReliable.model}（${mostReliable.parseOk}/${mostReliable.total} 解析成功）`);
    }
  }, 600000); // 10 分钟超时
});
