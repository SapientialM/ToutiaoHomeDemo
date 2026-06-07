/**
 * Vision 端到端基准测试 —— Minimax 多模型 + Kimi 对比
 *
 * 测试矩阵：provider × model × tool，统计：
 * - 耗时、响应 token、JSON 解析质量、输出 token 质量
 *
 * 运行：npm run test:vision-bench
 * 跳过：未设 MINIMAX_API_KEY / MOONSHOT_API_KEY 时自动 skip
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
  const hasKimi = !!process.env.MOONSHOT_API_KEY;
  const hasMinimax = !!process.env.MINIMAX_API_KEY;
  const activeProvider = process.env.VISION_PROVIDER || "minimax";

  if (!hasKimi && !hasMinimax) {
    it.skip("No vision LLM API key set, skipping", () => {});
    return;
  }

  it("runs full benchmark matrix", async () => {
    if (!fs.existsSync(TEST_IMAGE)) {
      console.log(`❌ Test image not found: ${TEST_IMAGE}`);
      return;
    }

    // Kimi 模式已废弃（耗时 100-150s/调用，JSON 解析还需 fallback reasoning_content），跳过整个测试
    // 用 RUN_VISION_BENCH_WITH_KIMI=1 强制启用 Kimi benchmark
    const includeKimi = process.env.RUN_VISION_BENCH_WITH_KIMI === "1" && hasKimi;

    const testMatrix: Array<{ provider: "minimax" | "kimi"; model: string; skip?: boolean }> = [
      { provider: "minimax", model: "MiniMax-M3" },                // thinking-disabled, 最快
      { provider: "minimax", model: "MiniMax-M2.7-highspeed" },   // 100 TPS
      { provider: "minimax", model: "MiniMax-M2.7" },              // 60 TPS baseline
      { provider: "kimi", model: "kimi-k2.6", skip: !includeKimi },  // legacy, opt-in
    ];

    console.log("\n═══════════════════════════════════════════════════════════════");
    console.log("🧪 VISION API BENCHMARK — multi-model comparison");
    console.log("═══════════════════════════════════════════════════════════════");
    console.log(`📐 Test image: ${TEST_IMAGE} (${Math.round(fs.statSync(TEST_IMAGE).size / 1024)}KB)`);
    console.log(`🔑 API: minimax=${hasMinimax ? "yes" : "no"}  kimi=${hasKimi ? "yes (skipped, set RUN_VISION_BENCH_WITH_KIMI=1 to enable)" : "no"}  active=${activeProvider}`);
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

    // 对每个模型跑 extract_design_spec(format=json) 和 extract_design_components
    const all: BenchResult[] = [];

    for (const cfg of testMatrix) {
      if (cfg.skip) {
        console.log(`⏭️  跳过 ${cfg.provider}/${cfg.model}（API key 未配置）`);
        continue;
      }
      // kimi provider 在这次 benchmark 中以独立 baseURL 跑（不能用 minimax 的 URL）
      if (cfg.provider === "kimi") {
        console.log(`\n── ${cfg.provider}/${cfg.model} ──`);
        const r1 = await benchOne(`[${cfg.provider}/${cfg.model}] extract_design_spec(json)`, async () => {
          const r = await handleExtractDesignSpec({ imagePath: TEST_IMAGE, format: "json", model: cfg.model, provider: "kimi" });
          return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
        });
        console.log(`    ① extract_design_spec(json): ${r1.totalMs}ms, ~${r1.responseTokens} tok, parse=${r1.parseOk ? "✅" : "❌"}`);
        if (r1.jsonShape) console.log(`       keys: ${r1.jsonShape}`);
        console.log(`       preview: ${r1.preview}...`);
        all.push(r1);
        await sleep(2000);

        const r2 = await benchOne(`[${cfg.provider}/${cfg.model}] extract_design_tokens`, async () => {
          const r = await handleExtractDesignTokens({ imagePath: TEST_IMAGE, model: cfg.model, provider: "kimi" });
          return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
        });
        console.log(`    ② extract_design_tokens:     ${r2.totalMs}ms, ~${r2.responseTokens} tok, parse=${r2.parseOk ? "✅" : "❌"}`);
        console.log(`       preview: ${r2.preview}...`);
        all.push(r2);
        await sleep(2000);

        const r3 = await benchOne(`[${cfg.provider}/${cfg.model}] extract_design_components`, async () => {
          const r = await handleExtractComponents({ imagePath: TEST_IMAGE, model: cfg.model, provider: "kimi" });
          return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
        });
        console.log(`    ③ extract_design_components: ${r3.totalMs}ms, ~${r3.responseTokens} tok, parse=${r3.parseOk ? "✅" : "❌"}`);
        if (r3.jsonShape) console.log(`       keys: ${r3.jsonShape}`);
        console.log(`       preview: ${r3.preview}...`);
        all.push(r3);
        continue;
      }

      console.log(`\n── ${cfg.provider}/${cfg.model} ──`);

      // 1. extract_design_spec (json)
      const r1 = await benchOne(`[${cfg.provider}/${cfg.model}] extract_design_spec(json)`, async () => {
        const r = await handleExtractDesignSpec({ imagePath: TEST_IMAGE, format: "json", model: cfg.model, provider: cfg.provider });
        return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
      });
      console.log(`    ① extract_design_spec(json): ${r1.totalMs}ms, ~${r1.responseTokens} tok, parse=${r1.parseOk ? "✅" : "❌"}`);
      if (r1.jsonShape) console.log(`       keys: ${r1.jsonShape}`);
      console.log(`       preview: ${r1.preview}...`);
      all.push(r1);
      await sleep(2000);

      // 2. extract_design_tokens
      const r2 = await benchOne(`[${cfg.provider}/${cfg.model}] extract_design_tokens`, async () => {
        const r = await handleExtractDesignTokens({ imagePath: TEST_IMAGE, model: cfg.model, provider: cfg.provider });
        return { text: r.content[0].text, parsed: r.isError ? undefined : JSON.parse(r.content[0].text) };
      });
      console.log(`    ② extract_design_tokens:     ${r2.totalMs}ms, ~${r2.responseTokens} tok, parse=${r2.parseOk ? "✅" : "❌"}`);
      console.log(`       preview: ${r2.preview}...`);
      all.push(r2);
      await sleep(2000);

      // 3. extract_design_components
      const r3 = await benchOne(`[${cfg.provider}/${cfg.model}] extract_design_components`, async () => {
        const r = await handleExtractComponents({ imagePath: TEST_IMAGE, model: cfg.model, provider: cfg.provider });
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

    const summary: Array<{ model: string; avgMs: number; totalTokens: number; parseOk: number; total: number; speedupVsKimi: string }> = [];
    const kimiAvg = byModel["[kimi/kimi-k2.6]"]?.reduce((s, r) => s + r.totalMs, 0) / (byModel["[kimi/kimi-k2.6]"]?.length || 1);

    for (const [model, results] of Object.entries(byModel)) {
      const avgMs = results.reduce((s, r) => s + r.totalMs, 0) / results.length;
      const totalTokens = results.reduce((s, r) => s + r.responseTokens, 0);
      const ok = results.filter((r) => r.parseOk).length;
      const speedup = kimiAvg > 0 ? (kimiAvg / avgMs).toFixed(1) + "x" : "n/a";
      summary.push({ model, avgMs, totalTokens, parseOk: ok, total: results.length, speedupVsKimi: speedup });
    }

    summary.sort((a, b) => a.avgMs - b.avgMs);
    for (const s of summary) {
      console.log(
        `  ${s.model.padEnd(34)} avg=${s.avgMs.toFixed(0).padStart(5)}ms  ` +
        `tokens=${s.totalTokens.toString().padStart(5)}  ` +
        `parse=${s.parseOk}/${s.total}  ` +
        `speedup=${s.speedupVsKimi}`
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
