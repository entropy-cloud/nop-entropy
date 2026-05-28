export const STEP_NAMES = {
  FIX_BUILD: "fix-build",
  DEEP_AUDIT: "deep-audit",
  ADVERSARIAL: "adversarial-review",
  PLAN: "plan",
  EXECUTE: "execute",
  CLOSURE_AUDIT: "closure-audit",
  EVAL: "eval",
};

function defaultStepConfigs(moduleName) {
  return {
    [STEP_NAMES.FIX_BUILD]: {
      label: "修复编译错误",
      command: `修复模块 ${moduleName} 的编译错误。运行 mvnw clean install -pl ${moduleName} -am -T 1C，修复所有编译错误使构建通过。`,
      system: "",
      resultTag: "HEALTH_STATUS",
      markerValues: { FIXED: "fixed", FAILED: "failed" },
    },

    [STEP_NAMES.DEEP_AUDIT]: {
      label: "多维度深度审计",
      command: `请对模块 ${moduleName} 执行多维度深度审计。阅读并遵循 ai-dev/skills/deep-audit-prompts.md。结果写入 ai-dev/audits/{DATE}-deep-audit-${moduleName}/。

输出 <AUDIT_RESULT>clean</AUDIT_RESULT>（无 P0/P1 问题）或 <AUDIT_RESULT>issues</AUDIT_RESULT>（存在 P0/P1 问题）。`,
      system: "",
      resultTag: "AUDIT_RESULT",
      markerValues: { CLEAN: "clean", ISSUES: "issues" },
    },

    [STEP_NAMES.ADVERSARIAL]: {
      label: "对抗性审查",
      command: `请对模块 ${moduleName} 执行对抗性审查。阅读并遵循 ai-dev/skills/open-ended-adversarial-review-prompt.md。结果写入 ai-dev/audits/{DATE}-adversarial-review-${moduleName}/。

输出 <ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT>（无新发现）或 <ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>（有新发现）。`,
      system: "",
      resultTag: "ADVERSARIAL_RESULT",
      markerValues: { CLEAN: "clean", ISSUES: "issues" },
    },

    [STEP_NAMES.PLAN]: {
      label: "拟定计划",
      command: `请为模块 ${moduleName} 的审计发现拟定修复计划。阅读并遵循 ai-dev/plans/00-plan-authoring-and-execution-guide.md 和 ai-dev/skills/plan-reviewer-prompt.md。

输出 <PLAN_RESULT>created</PLAN_RESULT>（有计划创建）或 <PLAN_RESULT>none</PLAN_RESULT>（无新计划）。`,
      system: "",
      resultTag: "PLAN_RESULT",
      markerValues: { CREATED: "created", NONE: "none" },
    },

    [STEP_NAMES.EXECUTE]: {
      label: "执行计划",
      command: `请执行模块 ${moduleName} 的修复计划。阅读并遵循 ai-dev/skills/plan-closure-audit-prompt.md。每次改动后运行测试并提交 git。

输出 <EXECUTE_RESULT>success</EXECUTE_RESULT>（执行成功）或 <EXECUTE_RESULT>failed</EXECUTE_RESULT>（执行失败）。`,
      system: "",
      resultTag: "EXECUTE_RESULT",
      markerValues: { SUCCESS: "success", FAILED: "failed" },
    },

    [STEP_NAMES.CLOSURE_AUDIT]: {
      label: "独立验证（Closure Audit）",
      command: `你是一个独立验证者，没有参与过之前的计划执行和代码编写。请验证模块 ${moduleName} 的修复计划是否真正完成。

步骤：
1. 读取 ai-dev/plans/ 下最新的活跃计划（Status 不是 completed/superseded/cancelled）
2. 逐条检查每个 Phase 的 Exit Criteria 是否在 live repo 代码中真正满足（不只是接口存在，而是行为语义已落地）
3. 阅读 ai-dev/skills/plan-closure-audit-prompt.md
4. 运行 node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict（如存在）
5. 检查 Anti-Hollow：新增组件在运行时被调用（不只是 import 存在），无空方法体/静默跳过

如果所有项目都已完成，输出：
<CLOSURE_RESULT>complete</CLOSURE_RESULT>

如果有未完成项目，输出：
<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>
<REMAINING>
  <item>具体未完成项描述（包含文件路径、期望行为、当前状态）</item>
</REMAINING>`,
      system: "",
      resultTag: "CLOSURE_RESULT",
      markerValues: { COMPLETE: "complete", INCOMPLETE: "incomplete" },
    },

    [STEP_NAMES.EVAL]: {
      label: "目标评估",
      command: `请评估模块 ${moduleName} 是否完善完成。

## 检查清单（全部满足才算 COMPLETE）
1. 构建通过：./mvnw clean install -pl ${moduleName} -am -T 1C（已验证）
2. 全量测试通过：./mvnw test -pl ${moduleName} -am -T 1C（已运行）
3. 多维度深度审计收敛：读取 ai-dev/audits/ 最新报告，连续一轮无新 P0/P1
4. 对抗性审查收敛：最新一轮无新发现
5. 所有 plan completed：检查 ai-dev/plans/ 相关 plan，确认所有 checklist 已勾选
6. 设计目标均已实现：读取 ai-dev/design/${moduleName}/README.md（如有），对照检查

输出 <EVAL_RESULT>complete</EVAL_RESULT>（目标达成）或 <EVAL_RESULT>continue</EVAL_RESULT>（需要继续）。`,
      system: "",
      resultTag: "EVAL_RESULT",
      markerValues: { COMPLETE: "complete", CONTINUE: "continue" },
    },
  };
}

function testStepConfigs() {
  const make = (tag, value) => ({
    command: `输出 <${tag}>${value}</${tag}>`,
    system: "",
    resultTag: tag,
    markerValues: { DEFAULT: value },
  });

  return {
    [STEP_NAMES.FIX_BUILD]:      { ...make("HEALTH_STATUS", "fixed"),     markerValues: { FIXED: "fixed", FAILED: "failed" } },
    [STEP_NAMES.DEEP_AUDIT]:     { ...make("AUDIT_RESULT", "clean"),      markerValues: { CLEAN: "clean", ISSUES: "issues" } },
    [STEP_NAMES.ADVERSARIAL]:    { ...make("ADVERSARIAL_RESULT", "clean"),markerValues: { CLEAN: "clean", ISSUES: "issues" } },
    [STEP_NAMES.PLAN]:           { ...make("PLAN_RESULT", "none"),        markerValues: { CREATED: "created", NONE: "none" } },
    [STEP_NAMES.EXECUTE]:        { ...make("EXECUTE_RESULT", "success"),  markerValues: { SUCCESS: "success", FAILED: "failed" } },
    [STEP_NAMES.CLOSURE_AUDIT]:  { ...make("CLOSURE_RESULT", "complete"), markerValues: { COMPLETE: "complete", INCOMPLETE: "incomplete" } },
    [STEP_NAMES.EVAL]:           { ...make("EVAL_RESULT", "complete"),    markerValues: { COMPLETE: "complete", CONTINUE: "continue" } },
  };
}

export function createStepConfigs(config) {
  return config.testMode ? testStepConfigs() : defaultStepConfigs(config.moduleName);
}

