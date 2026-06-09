import { execSync } from "node:child_process";

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

    const activePlans = output.match(/^  (.+\.md)\s*→\s*(.+)$/gm);
    const planList = activePlans
      ? activePlans.map(l => {
          const m = l.match(/^  (.+\.md)\s*→\s*(.+)$/);
          return m ? `${m[1]} (status: ${m[2]})` : l;
        }).join("\n")
      : `${activeCount} active plans`;

    return {
      marker: "has_plans",
      vars: { activePlanCount: String(activeCount), activePlanList: planList },
    };
  } catch {
    return { marker: "no_plans" };
  }
}

export function gitCommit(delegates, args) {
  const config = delegates.config;
  const message = args.message || "chore: auto-commit";
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
    execSync(`git commit -m "${message}" --no-verify`, {
      cwd: config.projectRoot,
      timeout: 60_000,
    });

    const hash = execSync("git rev-parse --short HEAD", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 10_000,
    }).trim();

    return { marker: "committed", vars: { commitHash: hash } };
  } catch (e) {
    return { marker: "nothing", vars: { commitError: e.message } };
  }
}
