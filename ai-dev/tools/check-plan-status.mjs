#!/usr/bin/env node

import { readdirSync, readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const plansDir = resolve(__dirname, "../plans");

function getPlanStatus(filePath) {
  try {
    const text = readFileSync(filePath, "utf8");
    const patterns = [
      /^>\s*\*{0,2}Plan Status\*{0,2}:\s*(.+)$/m,
      /^>\s*\*{0,2}Status\*{0,2}:\s*(.+)$/m,
    ];
    for (const re of patterns) {
      const m = text.match(re);
      if (m) {
        let val = m[1].trim();
        val = val.replace(/^\*{1,2}(.+?)\*{1,2}.*$/, "$1");
        return val.toLowerCase();
      }
    }
    return "unknown";
  } catch {
    return "error";
  }
}

function isActive(status) {
  return !["completed", "superseded", "cancelled", "deferred"].includes(status);
}

const files = readdirSync(plansDir)
  .filter(f => f.endsWith(".md") && f !== "00-plan-authoring-and-execution-guide.md")
  .sort();

const plans = [];
for (const f of files) {
  const status = getPlanStatus(resolve(plansDir, f));
  plans.push({ file: f, status, active: isActive(status) });
}

const activePlans = plans.filter(p => p.active);
const completedPlans = plans.filter(p => !p.active);

console.log(`Total plans: ${plans.length}`);
console.log(`Active: ${activePlans.length}`);
console.log(`Completed: ${completedPlans.length}`);
console.log("");

if (activePlans.length > 0) {
  console.log("=== ACTIVE PLANS (按编号排序) ===");
  for (const p of activePlans) {
    console.log(`  ${p.file} → ${p.status}`);
  }
}

console.log("");
console.log("=== RECENT COMPLETED (last 5) ===");
for (const p of completedPlans.slice(-5)) {
  console.log(`  ${p.file} → ${p.status}`);
}
