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
      // Step 1: 智能修复所有单元测试错误
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
        prompt: "模块 {module} 的测试修复失败。请重新分析测试失败根因，使用更保守的修复策略。如果某些测试的预期行为确实需要更新，更新测试并说明理由。运行 ./mvnw test -pl {module} -am -T 1C 验证。输出 <TEST_RESULT>fixed</TEST_RESULT> 或 <TEST_RESULT>failed</TEST_RESULT>。",
        resultTag: "TEST_RESULT",
        onUnknownMaxRetries: 1,
        transitions: {
          fixed: { goto: "CHECK_PENDING_PLANS" },
          failed: { done: "tests_failed" },
        },
        onError: { done: "tests_failed" },
      },

      // ═══════════════════════════════════════
      // Step 2: 循环完成所有未完成的计划
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
        prompt: "验证刚执行的计划是否真正完成。读取 ai-dev/plans/ 中刚更新的计划文件，检查 Exit Criteria 是否全部满足。运行 ./mvnw test -pl {module} -am -T 1C 确认测试通过。输出 <VERIFY_RESULT>complete</VERIFY_RESULT> 或 <VERIFY_RESULT>incomplete</VERIFY_RESULT>。incomplete 时附带 <REMAINING>未完成项</REMAINING>。",
        resultTag: "VERIFY_RESULT",
        maxRetries: 3,
        transitions: {
          complete: { goto: "COMMIT_PENDING_PLAN" },
          incomplete: {
            retry: "EXECUTE_PENDING_PLAN",
            maxRetries: 3,
            append: { extract: "REMAINING", template: "\n\n以下项仍需完成：\n${output}" },
          },
        },
        onError: { retry: "EXECUTE_PENDING_PLAN", maxRetries: 2 },
        onMaxRetries: { goto: "COMMIT_PENDING_PLAN" },
      },

      COMMIT_PENDING_PLAN: {
        type: "script",
        run: (delegates) => gitCommit(delegates, "plan", "feat({module}): 完成待执行计划"),
        transitions: {
          committed: { goto: "CHECK_PENDING_PLANS" },
          nothing: { goto: "CHECK_PENDING_PLANS" },
        },
      },

      // ═══════════════════════════════════════
      // Step 3: 检查 roadmap → 拟制计划
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
      // Step 4: 审核拟制的计划
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
            append: { template: "\n\n计划审查反馈：\n${output}" },
          },
        },
        onError: { retry: "PLAN_DRAFT", maxRetries: 2 },
        onMaxRetries: { goto: "EXECUTE_PLAN", append: { template: "\n\n⚠️ 计划未通过审计（多轮尝试），执行时请注意：\n${output}" } },
      },

      // ═══════════════════════════════════════
      // Step 5: 执行计划
      // Step 6: 审核计划完成 + 6.1 自动提交
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
            append: { extract: "REMAINING", template: "\n\n未完成项：\n${output}" },
          },
        },
        onError: {
          retry: "EXECUTE_PLAN",
          maxRetries: 3,
          append: { template: "\n\nclosure audit 子进程被 kill，请重新检查 Exit Criteria" },
        },
        onMaxRetries: { goto: "PLAN_COMMIT" },
      },

      PLAN_COMMIT: {
        type: "script",
        run: (delegates) => gitCommit(delegates, "plan", "feat({module}): 完成计划执行"),
        transitions: {
          committed: { goto: "NEEDS_DEEP_AUDIT" },
          nothing: { goto: "NEEDS_DEEP_AUDIT" },
        },
      },

      // ═══════════════════════════════════════
      // Step 7: 智能判断是否需要深度审计
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
      // Step 8: 执行智能深度审计
      // ═══════════════════════════════════════
      DEEP_AUDIT: {
        type: "agent",
        prompt: "对模块 {module} 执行多维度深度审计。阅读 ai-dev/skills/deep-audit-prompts.md。结果写入 ai-dev/audits/{DATE}-deep-audit-{module}/。输出 <AUDIT_RESULT>clean</AUDIT_RESULT> 或 <AUDIT_RESULT>issues</AUDIT_RESULT>。",
        resultTag: "AUDIT_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "ADVERSARIAL" },
          clean: { goto: "ADVERSARIAL" },
        },
        onError: { goto: "ADVERSARIAL" },
      },

      // ═══════════════════════════════════════
      // Step 9: 开放式对抗性审计
      // ═══════════════════════════════════════
      ADVERSARIAL: {
        type: "agent",
        prompt: "对模块 {module} 执行对抗性审查。阅读 ai-dev/skills/open-ended-adversarial-review-prompt.md。结果写入 ai-dev/audits/{DATE}-adversarial-review-{module}/。输出 <ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT> 或 <ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>。",
        resultTag: "ADVERSARIAL_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "AUDIT_PLAN_DRAFT" },
          clean: { done: "completed" },
        },
        onError: { done: "completed" },
      },

      // ═══════════════════════════════════════
      // Step 10-14: 审计结果 → 拟制计划 → 审核 → 执行 → 提交
      // ═══════════════════════════════════════
      AUDIT_PLAN_DRAFT: {
        type: "agent",
        prompt: "根据审计结果为模块 {module} 拟制修复计划。审计结果：{steps.ADVERSARIAL.text}\n\n读取 ai-dev/plans/00-plan-authoring-and-execution-guide.md 了解计划格式，将计划文件写入 ai-dev/plans/。输出 <PLAN_RESULT>created</PLAN_RESULT> 或 <PLAN_RESULT>none</PLAN_RESULT>。",
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
            append: { template: "\n\n计划审查反馈：\n${output}" },
          },
        },
        onError: { retry: "AUDIT_PLAN_DRAFT", maxRetries: 2 },
        onMaxRetries: { goto: "AUDIT_EXECUTE", append: { template: "\n\n⚠️ 审计修复计划未通过审查，执行时请注意：\n${output}" } },
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
            append: { extract: "REMAINING", template: "\n\n未完成项：\n${output}" },
          },
        },
        onError: {
          retry: "AUDIT_EXECUTE",
          maxRetries: 2,
          append: { template: "\n\n审计计划执行验证失败，请重新检查" },
        },
        onMaxRetries: { goto: "AUDIT_COMMIT" },
      },

      AUDIT_COMMIT: {
        type: "script",
        run: (delegates) => gitCommit(delegates, "audit", "fix({module}): 审计问题修复"),
        transitions: {
          committed: { goto: "FIX_TESTS" },
          nothing: { goto: "FIX_TESTS" },
        },
      },
    },
  };
}
