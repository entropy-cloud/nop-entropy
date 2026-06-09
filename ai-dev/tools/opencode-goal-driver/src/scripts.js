import { execSync } from "node:child_process";
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

export function checkPendingPlans(delegates) {
  const config = delegates.config;
  try {
    const output = execSync("node ai-dev/tools/check-plan-status.mjs", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    });
    const activeMatch = output.match(/Active:\s*(\d+)/);
    const activeCount = activeMatch ? parseInt(activeMatch[1], 10) : 0;

    if (activeCount === 0) {
      return { marker: "no_plans" };
    }

    const planFiles = [];
    const lines = output.match(/^  (.+\.md)\s*→\s*(.+)$/gm);
    if (lines) {
      for (const l of lines) {
        const m = l.match(/^  (.+\.md)\s*→\s*(.+)$/);
        if (m) planFiles.push(`ai-dev/plans/${m[1]}`);
      }
    }

    if (planFiles.length === 0) {
      return { marker: "no_plans", vars: { activePlanCount: String(activeCount) } };
    }

    return {
      marker: "has_plans",
      vars: {
        activePlanCount: String(activeCount),
        activePlanFiles: planFiles,
      },
    };
  } catch {
    return { marker: "no_plans" };
  }
}

export function readPlanStatus(delegates) {
  const planFile = delegates.vars?.currentItem || delegates.vars?.planFile;
  if (!planFile) return { marker: "draft" };

  const projectRoot = delegates.config?.projectRoot || ".";
  const fullPath = resolve(projectRoot, planFile);

  try {
    const content = readFileSync(fullPath, "utf8");
    const statusMatch = content.match(/\*\*Plan Status\*\*:\s*(\w+)/);
    const status = statusMatch ? statusMatch[1].toLowerCase().trim() : "draft";
    return {
      marker: status,
      vars: { planFile, planStatus: status },
    };
  } catch {
    return { marker: "draft", vars: { planFile } };
  }
}

export function setPlanStatus(delegates, args) {
  const planFile = delegates.vars?.planFile || delegates.vars?.currentItem;
  const newStatus = args?.status || "active";
  if (!planFile) return { marker: "error", vars: { error: "no planFile" } };

  const projectRoot = delegates.config?.projectRoot || ".";
  const fullPath = resolve(projectRoot, planFile);

  try {
    let content = readFileSync(fullPath, "utf8");
    const re = /\*\*Plan Status\*\*:\s*\w+/;
    if (!re.test(content)) {
      return { marker: "error", vars: { error: "Plan Status field not found in " + planFile } };
    }
    content = content.replace(re, `**Plan Status**: ${newStatus}`);
    writeFileSync(fullPath, content, "utf8");
    return { marker: "ok", vars: { planFile, planStatus: newStatus } };
  } catch (e) {
    return { marker: "error", vars: { error: e.message } };
  }
}

export function updateRoadmap(delegates) {
  const planFile = delegates.vars?.planFile || delegates.vars?.currentItem;
  if (!planFile) return { marker: "skipped" };

  const projectRoot = delegates.config?.projectRoot || ".";
  const planPath = resolve(projectRoot, planFile);

  try {
    const content = readFileSync(planPath, "utf8");
    const workItemMatch = content.match(/\*\*Work Item\*\*:\s*(\S+)/);
    if (!workItemMatch) return { marker: "skipped", vars: { reason: "no Work Item field" } };

    const workItemId = workItemMatch[1];
    const module = delegates.vars?.module || "";

    const designDir = resolve(projectRoot, "ai-dev/design");
    let roadmapUpdated = false;

    const dirs = readdirSync(designDir, { withFileTypes: true });
    for (const d of dirs) {
      if (!d.isDirectory()) continue;
      const subFiles = readdirSync(resolve(designDir, d.name));
      for (const f of subFiles) {
        if (!f.includes("roadmap")) continue;
        const rf = resolve(designDir, d.name, f);
        try {
          let rc = readFileSync(rf, "utf8");
          const re = new RegExp(`(${workItemId}[^❌✅]*?)❌`);
          if (re.test(rc)) {
            rc = rc.replace(re, `$1✅`);
            writeFileSync(rf, rc, "utf8");
            roadmapUpdated = true;
          }
        } catch {}
      }
    }

    return {
      marker: roadmapUpdated ? "updated" : "skipped",
      vars: { workItemId, roadmapUpdated: String(roadmapUpdated) },
    };
  } catch (e) {
    return { marker: "error", vars: { error: e.message } };
  }
}

export function gitCommit(delegates, args) {
  const config = delegates.config;
  const message = args?.message || "chore: auto-commit";
  try {
    const diffStat = execSync("git diff --stat HEAD", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    }).trim();

    if (!diffStat) {
      return { marker: "nothing", vars: { commitHash: "none" } };
    }

    const diffFiles = execSync("git diff --name-only HEAD", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    }).trim();

    const stagedFiles = execSync("git diff --cached --name-only", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    }).trim();

    if (!diffFiles && !stagedFiles) {
      return { marker: "nothing", vars: { commitHash: "none" } };
    }

    execSync("git add -A", { cwd: config.projectRoot, timeout: 30_000 });

    try {
      execSync(`git commit -m "${message}" --no-verify`, {
        cwd: config.projectRoot,
        timeout: 60_000,
      });
    } catch (commitErr) {
      return {
        marker: "error",
        vars: { commitError: commitErr.stderr?.toString() || commitErr.message },
      };
    }

    const hash = execSync("git rev-parse --short HEAD", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 10_000,
    }).trim();

    return { marker: "committed", vars: { commitHash: hash } };
  } catch (e) {
    return { marker: "error", vars: { commitError: e.message } };
  }
}
