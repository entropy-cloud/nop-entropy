export const STEP_NAMES = {
  RESUME_CHECK: "resume-check",
  FIX_BUILD: "fix-build",
  DEEP_AUDIT: "deep-audit",
  ADVERSARIAL: "adversarial-review",
  PLAN: "plan",
  EXECUTE: "execute",
  CLOSURE_AUDIT: "closure-audit",
};

function defaultStepConfigs(moduleName) {
  return {
    [STEP_NAMES.RESUME_CHECK]: {
      label: "智能起点判断",
      command: `请检查模块 ${moduleName} 的当前状态，判断 goal driver 应从哪个阶段开始执行。

检查步骤：
1. 运行 \`node ai-dev/tools/check-plan-status.mjs\` 获取所有未完成计划的列表和状态
2. 检查 ai-dev/audits/ 下最新的审计目录，判断是否已有未处理的审计发现
3. 如果有未完成的计划（Status 不是 completed/superseded/cancelled）→ 应跳到执行阶段
4. 如果有审计发现但无对应计划 → 应跳到计划阶段
5. 如果都没有 → 从头开始（审计阶段）

判断标准（按优先级）：
- 有未完成计划 → <START_PHASE>execute</START_PHASE>
- 有审计发现但无计划 → <START_PHASE>plan</START_PHASE>
- 审计干净或无审计 → <START_PHASE>audit</START_PHASE>

同时输出未完成计划的列表（如果有）：
<PLAN_LIST>
plan文件名1
plan文件名2
</PLAN_LIST>

只输出 <START_PHASE> 标签和可选的 <PLAN_LIST>，不要其他内容。`,
      system: "",
      resultTag: "START_PHASE",
      markerValues: { AUDIT: "audit", PLAN: "plan", EXECUTE: "execute" },
    },

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

输出 <AUDIT_RESULT>clean</AUDIT_RESULT>（无 P0-P2 问题且无收益明显的 P3）或 <AUDIT_RESULT>issues</AUDIT_RESULT>（存在 P0-P2 问题，或存在收益明显的 P3）。`,
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
      command: `请按顺序执行模块 ${moduleName} 的未完成修复计划。

执行步骤：
1. 运行 \`node ai-dev/tools/check-plan-status.mjs\` 获取所有未完成计划列表
2. **跳过 Status 为 deferred 的计划**
3. 按计划编号从小到大顺序执行
4. 对每个计划：读取计划文件，**跳过已标记 [x] 的 Phase，只执行 [ ] 的 Phase**
5. 阅读并遵循 ai-dev/skills/plan-closure-audit-prompt.md
6. 每次改动后运行测试并提交 git
7. 执行完一个计划后，继续执行下一个未完成计划
8. 所有计划执行完毕后输出最终结果

输出 <EXECUTE_RESULT>success</EXECUTE_RESULT>（所有计划执行成功）或 <EXECUTE_RESULT>failed</EXECUTE_RESULT>（执行失败）。`,
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
    [STEP_NAMES.RESUME_CHECK]:  { ...make("START_PHASE", "audit"), markerValues: { AUDIT: "audit", PLAN: "plan", EXECUTE: "execute" } },
    [STEP_NAMES.FIX_BUILD]:      { ...make("HEALTH_STATUS", "fixed"),     markerValues: { FIXED: "fixed", FAILED: "failed" } },
    [STEP_NAMES.DEEP_AUDIT]:     { ...make("AUDIT_RESULT", "clean"),      markerValues: { CLEAN: "clean", ISSUES: "issues" } },
    [STEP_NAMES.ADVERSARIAL]:    { ...make("ADVERSARIAL_RESULT", "clean"),markerValues: { CLEAN: "clean", ISSUES: "issues" } },
    [STEP_NAMES.PLAN]:           { ...make("PLAN_RESULT", "none"),        markerValues: { CREATED: "created", NONE: "none" } },
    [STEP_NAMES.EXECUTE]:        { ...make("EXECUTE_RESULT", "success"),  markerValues: { SUCCESS: "success", FAILED: "failed" } },
    [STEP_NAMES.CLOSURE_AUDIT]:  { ...make("CLOSURE_RESULT", "complete"), markerValues: { COMPLETE: "complete", INCOMPLETE: "incomplete" } },
  };
}

export function createStepConfigs(config) {
  return config.testMode ? testStepConfigs() : defaultStepConfigs(config.moduleName);
}

