#!/usr/bin/env node
/**
 * Crontab-based one-time task scheduler.
 *
 * Reads a YAML config, converts future timestamps to one-time cron entries.
 * Idempotent: re-running replaces managed entries by task id (via comment markers).
 *
 * Usage: node crontab-scheduler.mjs <config.yaml> [--dry-run]
 */

import { readFileSync } from "node:fs";
import { spawnSync, execSync } from "node:child_process";
import yaml from "js-yaml";

const MARKER_PREFIX = "crontab-scheduler-task:";
const MARKER_RE = new RegExp(`#\\s*${escapeRegex(MARKER_PREFIX)}\\S+$`, "m");

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function readCrontab() {
  try {
    const out = execSync("crontab -l", { encoding: "utf8", stdio: ["pipe", "pipe", "ignore"] });
    return out;
  } catch {
    return "";
  }
}

function writeCrontab(content) {
  const proc = spawnSync("crontab", [], { input: content, encoding: "utf8" });
  if (proc.error || proc.status !== 0) {
    console.error("WARNING: crontab write failed", proc.stderr?.trim() || proc.error?.message);
  }
}

function parseTime(text) {
  text = text.trim();

  // "2026-05-28 02:00"
  {
    const m = text.match(/^(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})$/);
    if (m) {
      const [, y, mo, d, h, mi] = m.map(Number);
      return new Date(y, mo - 1, d, h, mi, 0, 0);
    }
  }

  // "today 14:30" / "tomorrow 03:00"
  {
    const m = text.match(/^(today|tomorrow)\s+(\d{2}):(\d{2})$/i);
    if (m) {
      const now = new Date();
      const dt = new Date(now.getFullYear(), now.getMonth(), now.getDate(), +m[2], +m[3], 0, 0);
      if (m[1].toLowerCase() === "tomorrow") dt.setDate(dt.getDate() + 1);
      return dt;
    }
  }

  throw new Error(`Unrecognized time format: "${text}"`);
}

function toCron(dt) {
  return `${dt.getMinutes()} ${dt.getHours()} ${dt.getDate()} ${dt.getMonth() + 1} *`;
}

function removeManaged(content) {
  const lines = content.split("\n");
  const kept = [];
  let removed = 0;
  for (const line of lines) {
    if (MARKER_RE.test(line.trim())) {
      removed++;
    } else {
      kept.push(line);
    }
  }
  return [kept.join("\n"), removed];
}

function fmtDate(dt) {
  const y = dt.getFullYear();
  const mo = String(dt.getMonth() + 1).padStart(2, "0");
  const d = String(dt.getDate()).padStart(2, "0");
  const h = String(dt.getHours()).padStart(2, "0");
  const mi = String(dt.getMinutes()).padStart(2, "0");
  return `${y}-${mo}-${d} ${h}:${mi}`;
}

function main() {
  const args = process.argv.slice(2);
  if (args.length < 1 || args[0] === "-h" || args[0] === "--help") {
    console.log("Usage: node crontab-scheduler.mjs <config.yaml> [--dry-run]");
    process.exit(0);
  }

  const configPath = args[0];
  const dryRun = args.includes("--dry-run");

  let tasks;
  try {
    const raw = readFileSync(configPath, "utf8");
    tasks = yaml.load(raw)?.tasks ?? [];
  } catch (e) {
    console.error(`ERROR: cannot read config: ${e.message}`);
    process.exit(1);
  }

  const now = new Date();
  let crontabContent = readCrontab();
  const original = crontabContent;

  const [cleaned, removed] = removeManaged(crontabContent);
  crontabContent = cleaned;
  if (removed > 0) console.log(`  removed ${removed} managed entry(ies)`);

  let added = 0;
  let skipped = 0;

  for (const t of tasks) {
    const tid = t.id ?? "?";
    const timeSrc = t.time ?? t.schedule ?? t.at ?? "";
    const cmd = t.command ?? t.cmd ?? t.script ?? "";

    if (!timeSrc || !cmd) {
      console.log(`  [WARN] ${tid}: missing 'time' or 'command', skipping`);
      continue;
    }

    let dt;
    try {
      dt = parseTime(timeSrc);
    } catch (e) {
      console.log(`  [WARN] ${tid}: ${e.message}`);
      continue;
    }

    if (dt <= now) {
      console.log(`  [SKIP] ${tid}: ${timeSrc} already passed (now: ${fmtDate(now)})`);
      skipped++;
      continue;
    }

    const cron = toCron(dt);
    crontabContent += `${cron} ${cmd}  #${MARKER_PREFIX}${tid}\n`;
    console.log(`  [ADD]  ${tid}: ${timeSrc} -> "${cron} ${cmd}"`);
    added++;
  }

  if (crontabContent === original) {
    console.log("No changes to crontab.");
    return;
  }

  if (dryRun) {
    console.log(`\n--- DRY RUN: would write ${added} entries (skipped ${skipped}) ---`);
    process.stdout.write(crontabContent);
  } else {
    writeCrontab(crontabContent);
    console.log(`Crontab updated: ${added} added, ${skipped} skipped`);
  }
}

main();
