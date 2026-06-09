import { execSync } from "node:child_process";

function detectStartPhase(delegates) {
  const config = delegates.config;
  try {
    const output = execSync("node ai-dev/tools/check-plan-status.mjs", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    });
    const activeMatch = output.match(/Active:\s*(\d+)/);
    const activeCount = activeMatch ? parseInt(activeMatch[1], 10) : 0;

    if (activeCount > 0) return "execute";

    try {
      const roadmaps = execSync(
        `ls ${config.projectRoot}/ai-dev/design/*${config.moduleName}*/*roadmap* ${config.projectRoot}/ai-dev/design/*roadmap*${config.moduleName}* 2>/dev/null || true`,
        { encoding: "utf8", timeout: 5_000 },
      ).trim();
      if (roadmaps) return "roadmap";
    } catch {}

    const auditsDir = `${config.projectRoot}/ai-dev/audits`;
    const recentAudit = execSync(
      `ls -td ${auditsDir}/*${config.moduleName}* 2>/dev/null | head -1`,
      { encoding: "utf8", timeout: 5_000 },
    ).trim();
    if (recentAudit) return "plan";

    return "audit";
  } catch {
    return "roadmap";
  }
}

export function createGoalDriverFlow() {
  return {
    name: "goal-driver",
    maxTotalSteps: 80,
    maxCycleVisits: 15,

    entry: "DETECT_START",

    steps: {
      DETECT_START: {
        type: "script",
        run: (delegates) => detectStartPhase(delegates),
        transitions: {
          execute: { goto: "HEALTH_CHECK" },
          roadmap: { goto: "HEALTH_CHECK" },
          plan: { goto: "HEALTH_CHECK" },
          audit: { goto: "HEALTH_CHECK" },
        },
      },

      HEALTH_CHECK: {
        type: "tool",
        command: "./mvnw clean install",
        timeout: 1_800_000,
        transitions: {
          pass: { goto: "ROADMAP_CHECK" },
          fail: { goto: "FIX_BUILD" },
        },
        onError: { goto: "FIX_BUILD" },
      },

      FIX_BUILD: {
        type: "agent",
        prompt: "修复模块 {module} 的编译错误。运行 ./mvnw clean install -pl {module} -am -T 1C，修复所有编译错误使构建通过。",
        resultTag: "HEALTH_STATUS",
        onUnknownMaxRetries: 2,
        transitions: {
          fixed: { goto: "ROADMAP_CHECK" },
          failed: { done: "failed" },
        },
        onError: { done: "failed" },
      },

      ROADMAP_CHECK: {
        type: "agent",
        prompt: `请检查模块 {module} 的路线图，选出 **唯一一个** 最应该现在做的工作项。

步骤：
1. 查找 ai-dev/design/ 下与 {module} 相关的 *roadmap*.md 文件
2. 读取路线图全文，特别关注 §2"当前状态"、§3"规划优先级指引"、§4"工作项清单"和 §5"技术债"
3. 找到所有标记为 ❌ 或 ⚠️ 的工作项
4. 对每个未完成项，用 grep/glob 检查代码库中是否已实际存在实现（排除 _gen 生成代码）
5. 按以下优先级从高到低选择 **唯一一个** 最紧迫的工作项：
   - P0 技术债（构建/加载失败类）
   - 前置层（Layer 0）阻塞项
   - Layer 1 核心接口
   - 更高层的扩展
   - 同层内选依赖最少的（被其他项依赖最多的优先）
6. 输出选择结果

如果没有未实现项（所有 ❌ 都已实际实现）：<ROADMAP_RESULT>complete</ROADMAP_RESULT>
如果有未实现项：<ROADMAP_RESULT>pending</ROADMAP_RESULT>
<NEXT_ITEM id="工作项编号" layer="层级" priority="P0|P1|P2">选中理由和现状描述</NEXT_ITEM>
<ROADMAP_ITEMS><item id="编号" priority="P0|P1|P2|P3">所有未实现项摘要</item></ROADMAP_ITEMS>`,
        resultTag: "ROADMAP_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          pending: { goto: "PLAN_DRAFT", append: true },
          complete: { goto: "DEEP_AUDIT" },
        },
        onError: { goto: "DEEP_AUDIT" },
      },

      PLAN_DRAFT: {
        type: "agent",
        prompt: `请为模块 {module} 拟定一个开发计划。每个计划只包含 **一个** 工作项。

来源判断：
- 如果上一步 ROADMAP_CHECK 提供了 <NEXT_ITEM>，基于该工作项拟定计划
- 如果有审计发现（ai-dev/audits/），基于审计发现拟定
- 两者都有时优先 NEXT_ITEM
- 都没有则不创建计划

硬性要求：
1. 阅读并遵循 ai-dev/plans/00-plan-authoring-and-execution-guide.md
2. 每个计划 **只包含一个工作项**，不要打包多个
3. 计划文件名格式：{YYYY}-{MM}-{DD}-{NNN}-{slug}.md，放在 ai-dev/plans/ 下
4. 文件开头必须包含以下格式（check-plan-status.mjs 依赖此格式解析状态）：

\`\`\`markdown
> **Plan Status**: active
> **Module**: {module}
> **Work Item**: L0-1 (或对应的工作项编号)

# 计划标题
\`\`\`

5. 合理划分 Phase（可执行的、有明确 Exit Criteria 的增量）
6. 明确写出 Exit Criteria（可验证的条件，如"文件存在"、"测试通过"、"编译通过"）

输出 <PLAN_RESULT>created</PLAN_RESULT> 或 <PLAN_RESULT>none</PLAN_RESULT>。`,
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
        prompt: `你是一个独立的计划审查者。请审查刚刚创建的计划。

审查维度（必须全部检查）：
1. **想象性分析**：想象自己严格执行计划，找出设计到代码之间的断层
2. **格式完整性**：是否遵循 plan guide 模板？必填字段是否齐全？
3. **内容合理性**：Goals/Non-Goals 是否清晰？Phase 划分是否合理？
4. **引用准确性**：引用的文件路径是否在仓库中存在？代码位置是否一致？

每条发现包含 severity (Blocker/Major/Minor)。
无 Blocker 且无 Major 时才算通过。

输出 <AUDIT_RESULT>approved</AUDIT_RESULT> 或 <AUDIT_RESULT>issues</AUDIT_RESULT>
并在 issues 时输出：
<ISSUES><item severity="Blocker|Major|Minor">问题描述</item></ISSUES>`,
        resultTag: "AUDIT_RESULT",
        maxRetries: 3,
        onUnknownMaxRetries: 2,
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: {
            retry: "PLAN_DRAFT",
            maxRetries: 3,
            append: { template: "\n\n以下是计划审查的反馈，请据此修改计划：\n${output}" },
          },
        },
        onError: { retry: "PLAN_DRAFT", maxRetries: 2 },
        onMaxRetries: { goto: "EXECUTE", append: { template: "\n\n⚠️ 计划未通过自动审计（已尝试多轮），但以下问题需要执行中注意：\n${output}" } },
      },

      EXECUTE: {
        type: "agent",
        prompt: `请执行模块 {module} 的活跃计划，**完整执行整个计划直到结束**。

执行步骤：
1. 运行 node ai-dev/tools/check-plan-status.mjs 获取活跃计划列表
2. 选择 Active 列表中的第一个计划
3. 读取计划文件，跳过已标记 [x] 的 Phase，按顺序执行所有 [ ] Phase
4. 每完成一个 Phase：
   a. 运行 ./mvnw test -pl {module} -am -T 1C 确认测试通过
   b. 将该 Phase 在计划文件中标记为 [x]
   c. 用 nop-git-master skill 提交代码（commit message 包含工作项编号）
5. 所有 Phase 完成后：
   a. 将计划的 Plan Status 更新为 completed
   b. 读取计划中的工作项编号，在路线图文件（ai-dev/design/*{module}*/*roadmap*.md）中将该工作项从 ❌ 改为 ✅

如果执行中途中断或失败也没关系——计划自身记录了进度（[x]/[ ]），下次重新执行时会从断点继续。
不要试图节省步骤，完整执行每一个未完成的 Phase。

输出 <EXECUTE_RESULT>success</EXECUTE_RESULT> 或 <EXECUTE_RESULT>failed</EXECUTE_RESULT>。`,
        resultTag: "EXECUTE_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          success: { goto: "CLOSURE_AUDIT" },
          failed: { goto: "CLOSURE_AUDIT" },
        },
        onError: { goto: "CLOSURE_AUDIT" },
      },

      CLOSURE_AUDIT: {
        type: "agent",
        prompt: `你是一个独立验证者，没有参与过之前的计划执行。请验证模块 {module} 的计划是否真正完成。

步骤：
1. 读取 ai-dev/plans/ 下最新的活跃计划
2. 逐条检查每个 Phase 的 Exit Criteria（用 grep/glob/读取文件来验证，不要相信计划中的 [x] 标记）
3. 检查 Anti-Hollow：新增组件在运行时被调用，无空方法体/静默跳过
4. 读取路线图文件（ai-dev/design/*{module}*/*roadmap*.md），确认已完成工作项的状态标记正确

如果所有 Exit Criteria 已满足：
- 确认路线图中对应工作项已标记为 ✅（如果没标记，补上）
- 确认计划的 Plan Status 已更新为 completed
<CLOSURE_RESULT>complete</CLOSURE_RESULT>

如果有未满足的 Exit Criteria：
<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>
<REMAINING><item>具体未完成项描述</item></REMAINING>`,
        resultTag: "CLOSURE_RESULT",
        maxRetries: 5,
        onUnknownMaxRetries: 2,
        transitions: {
          complete: { goto: "BUILD_VERIFY" },
          incomplete: {
            retry: "EXECUTE",
            maxRetries: 5,
            append: { extract: "REMAINING", template: "\n\n以下是未完成项，请继续：\n${output}" },
          },
        },
        onError: {
          retry: "EXECUTE",
          maxRetries: 3,
          append: { template: "\n\nclosure audit 子进程被 kill，请重新检查计划 Exit Criteria" },
        },
        onMaxRetries: { goto: "BUILD_VERIFY" },
      },

      BUILD_VERIFY: {
        type: "tool",
        command: "./mvnw clean install",
        timeout: 1_800_000,
        transitions: {
          pass: { goto: "ROADMAP_CHECK" },
          fail: { retry: "EXECUTE", maxRetries: 3 },
        },
        onError: { retry: "EXECUTE", maxRetries: 2 },
      },

      DEEP_AUDIT: {
        type: "agent",
        prompt: `请对模块 {module} 执行多维度深度审计。阅读并遵循 ai-dev/skills/deep-audit-prompts.md。结果写入 ai-dev/audits/{DATE}-deep-audit-{module}/。

输出 <AUDIT_RESULT>clean</AUDIT_RESULT> 或 <AUDIT_RESULT>issues</AUDIT_RESULT>。`,
        resultTag: "AUDIT_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "PLAN_DRAFT" },
          clean: { goto: "ADVERSARIAL" },
        },
        onError: { goto: "PLAN_DRAFT" },
      },

      ADVERSARIAL: {
        type: "agent",
        prompt: `请对模块 {module} 执行对抗性审查。阅读并遵循 ai-dev/skills/open-ended-adversarial-review-prompt.md。结果写入 ai-dev/audits/{DATE}-adversarial-review-{module}/。

输出 <ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT> 或 <ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>。`,
        resultTag: "ADVERSARIAL_RESULT",
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "PLAN_DRAFT" },
          clean: { done: "completed" },
        },
        onError: { done: "completed" },
      },
    },
  };
}
