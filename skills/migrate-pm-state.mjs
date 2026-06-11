#!/usr/bin/env node
/**
 * 一次性迁移脚本：将 .pm_state.json 迁移到 .pm_memory.json
 * Usage: node migrate-pm-state.mjs
 */

import fs from "node:fs";
import path from "node:path";

const STATE_PATH = "./.pm_state.json";
const MEMORY_PATH = "./.pm_memory.json";

function scanDesignFiles() {
  const designDir = path.resolve("..", "design");
  if (!fs.existsSync(designDir)) return [];
  return fs.readdirSync(designDir)
    .filter(f => f.endsWith(".jpg") || f.endsWith(".png") || f.endsWith(".jpeg") || f.endsWith(".webp"))
    .map(f => path.join("design", f));
}

function main() {
  console.log("Migrating .pm_state.json -> .pm_memory.json...\n");

  const old = fs.existsSync(STATE_PATH)
    ? JSON.parse(fs.readFileSync(STATE_PATH, "utf-8"))
    : { fixed: [], ignored: [] };

  const designSources = scanDesignFiles();
  console.log(`Found ${designSources.length} design files`);

  let memory;
  if (fs.existsSync(MEMORY_PATH)) {
    console.log("Existing .pm_memory.json found, merging...");
    memory = JSON.parse(fs.readFileSync(MEMORY_PATH, "utf-8"));
  } else {
    memory = {
      project: {
        name: "ToutiaoFeedDemo",
        package_name: "com.example.toutiao",
        main_activity: "MainActivity",
        version: "1.0.0",
      },
      design_specs: {
        sources: designSources,
        tokens: {},
      },
      reviews: [],
      issue_counter: 0,
      current_focus: {
        channel: "recommend",
        page: "首页推荐",
        last_review_id: null,
      },
    };
  }

  // Merge old fixed issues as a synthetic review
  if (old.fixed && old.fixed.length > 0) {
    const reviewId = `rev-migrated-${Date.now()}`;
    const migratedIssues = old.fixed.map((f, idx) => ({
      issue_id: f.issue_id.startsWith("ISSUE-") ? f.issue_id : `MIGRATED-${String(idx + 1).padStart(3, "0")}`,
      severity: "medium",
      category: "ui_bug",
      description: f.note || "Migrated from .pm_state.json",
      location: "",
      design_ref: "",
      status: "fixed",
      fixed_at: f.fixed_at,
      verified_by: "migration",
      verified_at: new Date().toISOString(),
    }));

    memory.reviews.push({
      review_id: reviewId,
      timestamp: new Date().toISOString(),
      tool: "migration",
      target: "历史修复记录",
      overall_rating: "B",
      issues: migratedIssues,
      positives: [],
    });

    memory.issue_counter = Math.max(memory.issue_counter, migratedIssues.length);
    console.log(`Migrated ${migratedIssues.length} fixed issues`);
  }

  if (old.ignored && old.ignored.length > 0) {
    console.log(`Note: ${old.ignored.length} ignored issues (not migrated, manual review recommended)`);
  }

  // Update design sources if empty
  if (memory.design_specs.sources.length === 0 && designSources.length > 0) {
    memory.design_specs.sources = designSources;
  }

  fs.writeFileSync(MEMORY_PATH, JSON.stringify(memory, null, 2), "utf-8");
  console.log(`\nDone! Written to ${MEMORY_PATH}`);
  console.log(`Reviews: ${memory.reviews.length}`);
  console.log(`Issue counter: ${memory.issue_counter}`);
}

main();
