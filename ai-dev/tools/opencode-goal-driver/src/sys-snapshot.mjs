#!/usr/bin/env node
/**
 * sys-snapshot.mjs — System resource snapshot for goal-driver diagnostics.
 *
 * Records a point-in-time snapshot of system resource consumption to a CSV-like
 * log file, designed to be called between goal-driver steps to build a timeline
 * of resource usage leading up to any crash.
 *
 * Cross-platform: works on macOS, Linux, and Windows 11 (including Git Bash).
 * Platform-specific metrics (macOS kernel zones, memory_pressure) are skipped
 * on platforms where they are unavailable.
 *
 * Usage:
 *   node ai-dev/tools/opencode-goal-driver/src/sys-snapshot.mjs <runDir> [label]
 *
 * Output: <runDir>/sys-snapshot.log (append mode, one JSON line per snapshot)
 *         <runDir>/sys-snapshot.csv  (tabular summary for quick scanning)
 */

import { appendFileSync, statSync, statfsSync } from "node:fs";
import { hostname, loadavg, totalmem, freemem } from "node:os";
import { safeExec, IS_WIN32, IS_MACOS, getAllProcesses } from "./platform.mjs";

function toGB(bytes) {
  return Math.round(bytes / 1024 / 1024 / 1024 * 10) / 10;
}

function parseLoadAvg() {
  const [m1, m5, m15] = loadavg();
  return { "1min": m1, "5min": m5, "15min": m15 };
}

function parseVmStat() {
  if (IS_WIN32) {
    const total = totalmem();
    const free = freemem();
    return {
      total_GB: toGB(total),
      free_GB: toGB(free),
      used_GB: toGB(total - free),
      apparentFree_GB: toGB(free),
    };
  }

  const raw = safeExec("vm_stat");
  if (!raw) return {};
  const PAGE_SIZE = IS_MACOS ? 16384 : 4096;
  const get = (key) => {
    const m = raw.match(new RegExp(`${key}:\\s+(\\d+)`));
    return m ? parseInt(m[1], 10) : 0;
  };
  const free = get("Pages free");
  const active = get("Pages active");
  const inactive = get("Pages inactive");
  const speculative = get("Pages speculative");
  const wired = get("Pages wired down");
  const compressed = get("Pages occupied by compressor");
  const compressorStored = get("Pages stored in compressor");
  const swapins = get("Swapins");
  const swapouts = get("Swapouts");
  const pagesToGB = (pages) => toGB(pages * PAGE_SIZE);
  return {
    free_GB: pagesToGB(free),
    active_GB: pagesToGB(active),
    inactive_GB: pagesToGB(inactive),
    speculative_GB: pagesToGB(speculative),
    wired_GB: pagesToGB(wired),
    compressed_GB: pagesToGB(compressed),
    compressorStored_GB: pagesToGB(compressorStored),
    swapins,
    swapouts,
    used_GB: pagesToGB(active + wired + compressed + speculative),
    apparentFree_GB: pagesToGB(free + inactive),
  };
}

function getTopProcesses(limit, allProcs) {
  if (IS_WIN32) {
    const sorted = [...allProcs].sort((a, b) => b.rss_kb - a.rss_kb);
    return sorted.slice(0, limit).map((p) => ({
      pid: p.pid,
      rss_mb: Math.round((p.rss_kb / 1024) * 10) / 10,
      cpu_pct: 0,
      elapsed: "",
      name: p.name.slice(0, 50),
    }));
  }

  const raw = safeExec("ps -eo pid,rss,%cpu,etime,comm -r");
  if (!raw) return [];
  const lines = raw.split("\n").slice(1);
  return lines.slice(0, limit).map((line) => {
    const parts = line.trim().split(/\s+/);
    return {
      pid: parseInt(parts[0], 10),
      rss_mb: Math.round((parseInt(parts[1], 10) / 1024) * 10) / 10,
      cpu_pct: parseFloat(parts[2]),
      elapsed: parts[3],
      name: parts.slice(4).join(" ").slice(0, 50),
    };
  });
}

const COHORT_PATTERNS = {
  opencode: /opencode/i,
  java: /java|javac|mvnw|maven|org\.apache\.maven|plexus\.classworlds/i,
  node: /(^|[\\/])node/i,
  idea: /IntelliJ|idea/i,
  docker: /docker|com\.docker/i,
  mysqld: /mysqld|mysql/i,
  python: /python/i,
};

function computeCohorts(allProcs) {
  const result = {};
  for (const [name, regex] of Object.entries(COHORT_PATTERNS)) {
    const matching = allProcs.filter((p) => regex.test(p.cmd));
    const rssKB = matching.reduce((s, p) => s + p.rss_kb, 0);
    result[name] = { rss_mb: Math.round((rssKB / 1024) * 10) / 10, count: matching.length };
  }
  return result;
}

function computeTotalRSS(allProcs) {
  const totalKB = allProcs.reduce((s, p) => s + p.rss_kb, 0);
  return Math.round((totalKB / 1024 / 1024) * 10) / 10;
}

function getKernelZoneInfo() {
  if (!IS_MACOS) return null;
  const raw = safeExec("zprint 2>/dev/null", { timeout: 5_000 });
  if (!raw) return null;
  const zones = {};
  for (const line of raw.split("\n")) {
    const m = line.match(/^(\S+)\s+(\d+)\s+(\d+)\s+(\d+)\s+/);
    if (m) {
      const name = m[1];
      const elemSize = parseInt(m[2], 10);
      const count = parseInt(m[3], 10);
      if (name.startsWith("kalloc.")) {
        zones[name] = { count, elem_size: elemSize, total_mb: Math.round((count * elemSize / 1024 / 1024) * 10) / 10 };
      }
    }
  }
  const kalloc1024 = zones["kalloc.1024"];
  if (kalloc1024) {
    return { kalloc_1024_count: kalloc1024.count, kalloc_1024_mb: kalloc1024.total_mb };
  }
  return null;
}

function getDiskFree(path) {
  try {
    const stats = statfsSync(path);
    return { avail_GB: toGB(stats.bavail * stats.bsize) };
  } catch {
    return {};
  }
}

function getMemoryPressureLevel() {
  if (!IS_MACOS) return "";
  const raw = safeExec("memory_pressure -Q");
  const m = raw.match(/System-wide memory free percentage:\s*(\d+)%/);
  if (!m) return "";
  return `${100 - parseInt(m[1], 10)}%`;
}

function snapshot(runDir, label = "") {
  const now = new Date();
  const tsISO = now.toISOString();
  const tsLocal = now.toLocaleString("en-US", { hour12: false });

  const allProcs = getAllProcesses();

  const snap = {
    ts: tsISO,
    tsLocal,
    label,
    platform: process.platform,
    hostname: hostname(),
    loadAvg: parseLoadAvg(),
    vm: parseVmStat(),
    totalRSS_GB: computeTotalRSS(allProcs),
    processCount: allProcs.length,
    cohorts: computeCohorts(allProcs),
    kernelZone: getKernelZoneInfo(),
    memPressure: getMemoryPressureLevel(),
    disk: getDiskFree(runDir),
    topProcs: getTopProcesses(10, allProcs),
  };

  const jsonlFile = `${runDir}/sys-snapshot.log`;
  const csvFile = `${runDir}/sys-snapshot.csv`;

  appendFileSync(jsonlFile, JSON.stringify(snap) + "\n");

  const csvLine = [
    tsLocal,
    label,
    snap.loadAvg["1min"] ?? "",
    snap.loadAvg["5min"] ?? "",
    snap.loadAvg["15min"] ?? "",
    snap.vm.free_GB ?? "",
    snap.vm.used_GB ?? "",
    snap.vm.apparentFree_GB ?? "",
    snap.vm.compressed_GB ?? "",
    snap.vm.swapins ?? 0,
    snap.vm.swapouts ?? 0,
    snap.totalRSS_GB,
    snap.processCount,
    snap.cohorts.opencode?.rss_mb ?? "",
    snap.cohorts.opencode?.count ?? "",
    snap.cohorts.java?.rss_mb ?? "",
    snap.cohorts.java?.count ?? "",
    snap.cohorts.node?.rss_mb ?? "",
    snap.cohorts.node?.count ?? "",
    snap.cohorts.idea?.rss_mb ?? "",
    snap.cohorts.docker?.rss_mb ?? "",
    snap.disk?.avail_GB ?? "",
    snap.memPressure,
  ].join("\t");

  const csvHeader = "#ts\tlabel\tload1\tload5\tload15\tfreeGB\tusedGB\tappFreeGB\tcompressGB\tswapins\tswapouts\ttotalRSS_GB\tnprocs\topencodeMB\topencodeN\tjavaMB\tjavaN\tnodeMB\tnodeN\tideaMB\tdockerMB\tdiskAvailGB\tmemPressure\n";

  try {
    statSync(csvFile);
  } catch {
    appendFileSync(csvFile, csvHeader);
  }
  appendFileSync(csvFile, csvLine + "\n");

  const top3 = snap.topProcs.slice(0, 3).map(p => `${p.name.slice(0,20)}:${p.rss_mb}MB`).join(" ");
  const oc = snap.cohorts.opencode;
  const jvm = snap.cohorts.java;
  const kz = snap.kernelZone;
  process.stderr.write(
    `  [sysmon] ${tsLocal} load=${snap.loadAvg["1min"]} free=${snap.vm.free_GB}GB/${snap.vm.apparentFree_GB}GB ` +
    `totalRSS=${snap.totalRSS_GB}GB oc=${oc?.rss_mb}MB(${oc?.count}) jvm=${jvm?.rss_mb}MB(${jvm?.count}) ` +
    `kalloc1024=${kz ? `${kz.kalloc_1024_mb}MB(${kz.kalloc_1024_count})` : "?"} ` +
    `procs=${snap.processCount} pressure=${snap.memPressure || "?"} top=[${top3}]\n`
  );

  return snap;
}

const runDir = process.argv[2];
const label = process.argv[3] || "";

if (import.meta.url === `file://${process.argv[1]}`) {
  if (!runDir) {
    console.error("Usage: sys-snapshot.mjs <runDir> [label]");
    process.exit(1);
  }
  snapshot(runDir, label);
}

export { snapshot };
