import { execSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";

function checkPendingPlans(delegates) {
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

function gitCommit(delegates, scope, message) {
  const config = delegates.config;
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

export function createGoalDriverFlow() {
  return {
    name: "goal-driver",
    maxTotalSteps: 120,
    maxCycleVisits: 20,

    entry: "FIX_TESTS",

    markerAliases: {
      "已创建": "created",
      "已批准": "approved",
      "有问题": "issues",
      "无": "none",
      "完成": "complete",
      "已完成": "complete",
      "未完成": "incomplete",
      "成功": "success",
      "失败": "failed",
      "修复": "fixed",
      "无错误": "no_errors",
      "通过": "approved",
      "待处理": "pending",
      "干净": "clean",
      "需要": "needed",
      "不需要": "not_needed",
    },

    steps: {
      // ═══════════════════════════════════════
      // Step 1: Smart-fix all unit test errors
      // ═══════════════════════════════════════
      FIX_TESTS: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/fix-tests.md",
        resultTag: "TEST_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          fixed: { goto: "CHECK_PENDING_PLANS" },
          no_errors: { goto: "CHECK_PENDING_PLANS" },
          failed: { goto: "FIX_TESTS_RECOVERY" },
        },
        onError: { retry: "FIX_TESTS", maxRetries: 2 },
      },

      FIX_TESTS_RECOVERY: {
        type: "agent",
        prompt: "Test fix for module {module} failed. Re-analyze test failures with a more conservative strategy. If test expectations genuinely need updating, update them with a clear explanation. Run ./mvnw test -pl {module} -am -T 1C to verify. Output <TEST_RESULT>fixed</TEST_RESULT> or <TEST_RESULT>failed</TEST_RESULT>.",
        resultTag: "TEST_RESULT",
        onUnknownMaxRetries: 1,
        transitions: {
          fixed: { goto: "CHECK_PENDING_PLANS" },
          failed: { done: "tests_failed" },
        },
        onError: { done: "tests_failed" },
      },

      // ═══════════════════════════════════════
      // Step 2: Loop through all pending plans
      // ═══════════════════════════════════════
      CHECK_PENDING_PLANS: {
        type: "script",
        run: (delegates) => checkPendingPlans(delegates),
        transitions: {
          has_plans: { goto: "EXECUTE_PENDING_PLAN" },
          no_plans: { goto: "ROADMAP_CHECK" },
        },
      },

      EXECUTE_PENDING_PLAN: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/execute-pending-plan.md",
        resultTag: "PLAN_EXEC_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          success: { goto: "VERIFY_PENDING_PLAN" },
          partial: { goto: "VERIFY_PENDING_PLAN" },
          failed: { goto: "VERIFY_PENDING_PLAN" },
        },
        onError: { retry: "EXECUTE_PENDING_PLAN", maxRetries: 2 },
      },

      VERIFY_PENDING_PLAN: {
        type: "agent",
        prompt: "Verify that the just-executed plan is truly complete. Read the updated plan file in ai-dev/plans/, check all Exit Criteria are satisfied. Run ./mvnw test -pl {module} -am -T 1C to confirm tests pass. Output <VERIFY_RESULT>complete</VERIFY_RESULT> or <VERIFY_RESULT>incomplete</VERIFY_RESULT>. If incomplete, include <REMAINING>items still pending</REMAINING>.",
        resultTag: "VERIFY_RESULT",
        maxRetries: 3,
        transitions: {
          complete: { goto: "COMMIT_PENDING_PLAN" },
          incomplete: {
            retry: "EXECUTE_PENDING_PLAN",
            maxRetries: 3,
            append: { extract: "REMAINING", template: "\n\nItems still pending:\n${output}" },
          },
        },
        onError: { retry: "EXECUTE_PENDING_PLAN", maxRetries: 2 },
        onMaxRetries: { goto: "COMMIT_PENDING_PLAN" },
      },

      COMMIT_PENDING_PLAN: {
        type: "script",
        run: (delegates) => gitCommit(delegates, "plan", "feat({module}): complete pending plan execution"),
        transitions: {
          committed: { goto: "CHECK_PENDING_PLANS" },
          nothing: { goto: "CHECK_PENDING_PLANS" },
        },
      },

      // ═══════════════════════════════════════
      // Step 3: Check roadmap -> draft plan
      // ═══════════════════════════════════════
      ROADMAP_CHECK: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/roadmap-check.md",
        resultTag: "ROADMAP_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          pending: { goto: "PLAN_DRAFT", append: true },
          complete: { goto: "NEEDS_DEEP_AUDIT" },
        },
        onError: { goto: "NEEDS_DEEP_AUDIT" },
      },

      // ═══════════════════════════════════════
      // Step 4: Audit drafted plan
      // ═══════════════════════════════════════
      PLAN_DRAFT: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/plan-draft.md",
        resultTag: "PLAN_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          created: { goto: "PLAN_AUDIT" },
          none: { goto: "ROADMAP_CHECK" },
        },
        onError: { goto: "ROADMAP_CHECK" },
      },

      PLAN_AUDIT: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/plan-audit.md",
        resultTag: "AUDIT_RESULT",
        maxRetries: 3,
        onUnknownMaxRetries: 2,
        transitions: {
          approved: { goto: "EXECUTE_PLAN" },
          issues: {
            retry: "PLAN_DRAFT",
            maxRetries: 3,
            append: { template: "\n\nPlan audit feedback:\n${output}" },
          },
        },
        onError: { retry: "PLAN_DRAFT", maxRetries: 2 },
        onMaxRetries: { goto: "EXECUTE_PLAN", append: { template: "\n\nWARNING: Plan did not pass audit after multiple rounds. Proceed with caution:\n${output}" } },
      },

      // ═══════════════════════════════════════
      // Step 5: Execute plan
      // Step 6: Verify plan completion + 6.1 auto-commit
      // ═══════════════════════════════════════
      EXECUTE_PLAN: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/execute-plan.md",
        resultTag: "EXECUTE_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          success: { goto: "PLAN_CLOSURE" },
          failed: { goto: "PLAN_CLOSURE" },
        },
        onError: { goto: "PLAN_CLOSURE" },
      },

      PLAN_CLOSURE: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/closure-audit-v2.md",
        resultTag: "CLOSURE_RESULT",
        maxRetries: 5,
        onUnknownMaxRetries: 2,
        transitions: {
          complete: { goto: "PLAN_COMMIT" },
          incomplete: {
            retry: "EXECUTE_PLAN",
            maxRetries: 5,
            append: { extract: "REMAINING", template: "\n\nItems still pending:\n${output}" },
          },
        },
        onError: {
          retry: "EXECUTE_PLAN",
          maxRetries: 3,
          append: { template: "\n\nClosure audit subprocess was killed. Please re-check Exit Criteria." },
        },
        onMaxRetries: { goto: "PLAN_COMMIT" },
      },

      PLAN_COMMIT: {
        type: "script",
        run: (delegates) => gitCommit(delegates, "plan", "feat({module}): complete plan execution"),
        transitions: {
          committed: { goto: "NEEDS_DEEP_AUDIT" },
          nothing: { goto: "NEEDS_DEEP_AUDIT" },
        },
      },

      // ═══════════════════════════════════════
      // Step 7: Smart-judge whether deep audit is needed
      // ═══════════════════════════════════════
      NEEDS_DEEP_AUDIT: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/needs-deep-audit.md",
        resultTag: "DEEP_AUDIT_NEEDED",
        onUnknownMaxRetries: 2,
        transitions: {
          needed: { goto: "DEEP_AUDIT" },
          not_needed: { done: "completed" },
        },
        onError: { done: "completed" },
      },

      // ═══════════════════════════════════════
      // Step 8: Execute smart deep audit
      // ═══════════════════════════════════════
      DEEP_AUDIT: {
        type: "agent",
        prompt: "Run a multi-dimensional deep audit on module {module}. Read and follow ai-dev/skills/deep-audit-prompts.md. Write results to ai-dev/audits/{DATE}-deep-audit-{module}/. Output <AUDIT_RESULT>clean</AUDIT_RESULT> or <AUDIT_RESULT>issues</AUDIT_RESULT>.",
        resultTag: "AUDIT_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "ADVERSARIAL" },
          clean: { goto: "ADVERSARIAL" },
        },
        onError: { goto: "ADVERSARIAL" },
      },

      // ═══════════════════════════════════════
      // Step 9: Open-ended adversarial review
      // ═══════════════════════════════════════
      ADVERSARIAL: {
        type: "agent",
        prompt: "Run an adversarial review on module {module}. Read and follow ai-dev/skills/open-ended-adversarial-review-prompt.md. Write results to ai-dev/audits/{DATE}-adversarial-review-{module}/. Output <ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT> or <ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>.",
        resultTag: "ADVERSARIAL_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "AUDIT_PLAN_DRAFT" },
          clean: { done: "completed" },
        },
        onError: { done: "completed" },
      },

      // ═══════════════════════════════════════
      // Step 10-14: Audit findings -> plan -> audit -> execute -> commit
      // ═══════════════════════════════════════
      AUDIT_PLAN_DRAFT: {
        type: "agent",
        prompt: "Draft a remediation plan for module {module} based on audit findings. Audit results: {steps.ADVERSARIAL.text}\n\nRead ai-dev/plans/00-plan-authoring-and-execution-guide.md for plan format. Write the plan file to ai-dev/plans/. Output <PLAN_RESULT>created</PLAN_RESULT> or <PLAN_RESULT>none</PLAN_RESULT>.",
        resultTag: "PLAN_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          created: { goto: "AUDIT_PLAN_AUDIT" },
          none: { goto: "FIX_TESTS" },
        },
        onError: { goto: "FIX_TESTS" },
      },

      AUDIT_PLAN_AUDIT: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/plan-audit.md",
        resultTag: "AUDIT_RESULT",
        maxRetries: 3,
        onUnknownMaxRetries: 2,
        transitions: {
          approved: { goto: "AUDIT_EXECUTE" },
          issues: {
            retry: "AUDIT_PLAN_DRAFT",
            maxRetries: 3,
            append: { template: "\n\nPlan audit feedback:\n${output}" },
          },
        },
        onError: { retry: "AUDIT_PLAN_DRAFT", maxRetries: 2 },
        onMaxRetries: { goto: "AUDIT_EXECUTE", append: { template: "\n\nWARNING: Audit remediation plan did not pass review. Proceed with caution:\n${output}" } },
      },

      AUDIT_EXECUTE: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/execute-plan.md",
        resultTag: "EXECUTE_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          success: { goto: "AUDIT_CLOSURE" },
          failed: { goto: "AUDIT_CLOSURE" },
        },
        onError: { goto: "AUDIT_CLOSURE" },
      },

      AUDIT_CLOSURE: {
        type: "agent",
        promptFile: "ai-dev/tools/opencode-goal-driver/prompts/closure-audit-v2.md",
        resultTag: "CLOSURE_RESULT",
        maxRetries: 3,
        onUnknownMaxRetries: 2,
        transitions: {
          complete: { goto: "AUDIT_COMMIT" },
          incomplete: {
            retry: "AUDIT_EXECUTE",
            maxRetries: 3,
            append: { extract: "REMAINING", template: "\n\nItems still pending:\n${output}" },
          },
        },
        onError: {
          retry: "AUDIT_EXECUTE",
          maxRetries: 2,
          append: { template: "\n\nAudit plan execution verification failed. Please re-check." },
        },
        onMaxRetries: { goto: "AUDIT_COMMIT" },
      },

      AUDIT_COMMIT: {
        type: "script",
        run: (delegates) => gitCommit(delegates, "audit", "fix({module}): audit finding remediation"),
        transitions: {
          committed: { goto: "FIX_TESTS" },
          nothing: { goto: "FIX_TESTS" },
        },
      },
    },
  };
}
