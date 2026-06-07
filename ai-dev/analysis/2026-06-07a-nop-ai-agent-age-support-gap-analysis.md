# Nop AI Agent 对 AGE 支持能力差距分析

> Status: resolved
> Date: 2026-06-07
> Scope: `C:/can/nop/attractor-guided-engineering-template` 的 AGE 工作流要求，与 `ai-dev/design/nop-ai-agent/` 当前设计的匹配度
> Conclusion: `nop-ai-agent` 当前只部分覆盖了 AGE，主要停留在 `plan/skill/session` 层；若要更好支持 AGE，需要新增一层 repo-governance 能力，把 context routing、autonomy gating、independent review、backlog selection、owner-doc precedence、trajectory memory 作为一等公民，而不是把 AGE 缩减为 `agent-plan.xdef` 的一个变体。

## Context

- 本次分析目标是回答：如果 `nop-ai-agent` 想原生支持 AGE（Attractor-Guided Engineering），到底缺的是什么。
- 这里的参照物不是抽象方法论口号，而是 `attractor-guided-engineering-template` 仓库中已经落成的文件结构、工作流约束和审计机制。
- 重点不在于“能否读 Markdown plan”，而在于 agent 是否能把 AGE 的 repo-level 吸引子、轨迹、控制机制当作运行时硬约束来执行。

## AGE Template 的真实要求

### 1. AGE 的控制面不是单个 plan，而是 repo 级文档路由面

- `README.md` 明确把 attractor 放在 owner files：`docs/context/`、`docs/backlog/`、`docs/requirements/`、`docs/design/`、`docs/architecture/`，而不是 plan 本身（`C:/can/nop/attractor-guided-engineering-template/README.md:21-31`）。
- `AGENTS.md` 要求 agent 在写非 trivial 代码前必须先读 `project-context.md`、`ai-autonomy-policy.md`、`codebase-map.md`、active requirement、active owner doc（`.../AGENTS.md:11-21`, `58-71`）。
- `docs/index.md` 进一步把整个 `docs/` 树定义为 durable routing surface（`.../docs/index.md:5-18`, `21-50`）。

**含义**

- AGE 不是“有 plan 就行”。
- Agent 必须先具备 repo-level routing 能力，知道此刻该读哪组文件，哪些文件拥有事实优先级。

### 2. AGE 的 plan 是 execution contract，不是普通任务单

- `docs/plans/00-plan-authoring-and-execution-guide.md` 把 plan 定义为 scope、closure criteria、proof 的执行契约，并强制要求 `Current Baseline`、`Goals`、`Non-Goals`、`Task Route`、`Closure Gates`（`.../docs/plans/00-plan-authoring-and-execution-guide.md:5-18`, `30-45`, `106-202`）。
- 所有 created plans 都要求独立 `draft review` 和独立 `closure audit`，而且 closure 不允许由 implementing agent 自己批准（`.../docs/plans/00-plan-authoring-and-execution-guide.md:42-43`, `86-104`）。
- `docs/audits/00-audit-execution-guide.md` 把这两次独立审查定义成默认审计对象，并要求 durable evidence 落在文件里，而不是留在 chat（`.../docs/audits/00-audit-execution-guide.md:7-12`, `46-90`）。

**含义**

- AGE plan 的核心不是结构化任务树，而是“可独立审计的闭合控制面”。
- `plan` 只是其中一部分，和 `audit`、`closure evidence`、`logs` 一起才构成闭环。

### 3. AGE 的 skill 是 method selector，不是业务能力包

- 模板明确写出：skills are method selectors, not substitutes for requirements/design/architecture truth（`README.md:64-79`, `AGENTS.md:54-56`, `docs/skills/README.md:7-24`）。
- 非 trivial plan 必须记录 `Skill: <name>` 或 `Skill: none`，而且 skill 选择要先经过 route 和 owner docs（`AGENTS.md:35-37`, `134-144`; `docs/process/application-development-workflow.md:167-176`）。
- `docs/skills/README.md` 的 registry 列的是审计方法、review 方法、bug diagnosis、refactor discovery 这类“工作方法”，不是业务场景脚本（`.../docs/skills/README.md:26-42`）。

**含义**

- AGE skill 的核心不是 prompt 注入，而是“选择工作方法，并记录选择依据”。
- 这与 `nop-ai-agent` 当前偏向 runtime activation 的 skill 设计不是一回事。

### 4. AGE 有明确 autonomy / blocker / protected-area 控制

- `docs/context/ai-autonomy-policy.md` 把 autonomy level、reviewer availability、protected areas、backlog selection 规则都写成 durable policy（`.../docs/context/ai-autonomy-policy.md:13-35`, `37-58`, `60-86`）。
- `project-context.md` 还把 documentation freshness、active plan、active backlog item、current blocker 放在最短上下文快照里（`.../docs/context/project-context.md:17-31`）。
- `docs/backlog/README.md` 规定只有满足 readiness invariants 的条目才能被 agent 自主选择执行（`.../docs/backlog/README.md:15-29`, `56-62`）。

**含义**

- AGE agent 不是“用户一说就自动执行”的无限自治体。
- 它必须先解释 repo 中的 autonomy policy，判断自己是否允许动手，是否必须先计划、先提问、先阻塞。

### 5. AGE 的轨迹记忆是分类型持久化的，不是单一 session memory

- 模板把 `logs`、`testing`、`bugs`、`analysis`、`retrospectives`、`lessons` 明确拆成不同目录和职责（`README.md:149-173`, `218-230`; `docs/index.md:80-118`）。
- `source-of-truth-and-precedence.md` 明确区分：stable truth 在 owner docs，execution 在 plans/logs，history and diagnosis 在 bugs/audits/testing/retrospectives（`.../docs/context/source-of-truth-and-precedence.md:99-157`）。
- `document-naming-and-timeliness.md` 则把 stable docs 与 dated records 的命名规则系统化（`.../docs/references/document-naming-and-timeliness.md:9-50`, `101-170`）。

**含义**

- AGE 的 memory 不是“把一切都塞进对话历史”或单个 session store。
- 它要求 agent 能理解并维护多种持久化 artifact，各自承担不同类型的轨迹信息。

## 与 Nop AI Agent 的差距

### 1. P1: 缺少一等公民的 Repo Context / Routing 模型

**现状**

- `nop-ai-agent` 文档基本聚焦在 `agent.xdef`、`agent-plan.xdef`、tool DSL、session、hook、skill、multi-agent（`ai-dev/design/nop-ai-agent/README.md:42-83`）。
- 在当前设计集中，几乎没有针对 `project-context`、`codebase-map`、`source-of-truth-and-precedence`、`backlog` 的运行时对象或路由引擎定义。

**问题**

- 这意味着 `nop-ai-agent` 目前更像“执行器框架”，而不是“AGE repo operator”。
- 它能运行一个 agent，但还不能先判断“此时应该读哪些 owner docs、当前 active requirement 是谁、documentation freshness 是否允许动手”。

**建议**

- 新增 repo-governance 层对象：
- `ProjectContextModel`
- `OwnerDocRoute`
- `SourceOfTruthPolicy`
- `CodebaseMapRoute`
- `BacklogItemModel`
- 把“启动前该读哪些文件”从 prompt 工程提升为显式运行时前置阶段。

### 2. P1: 缺少 Autonomy Policy Interpreter

**现状**

- `nop-ai-agent` 当前设计几乎没有与 `implement | plan-first | ask-first | research-only | blocked` 对应的正式运行时语义。
- 也没有 reviewer availability、protected area、documentation freshness、blocker state 的对象模型。

**问题**

- 这导致 `nop-ai-agent` 无法原生执行 AGE 的“先判定能不能做，再决定怎么做”。
- 现在这些判断更多依赖外部使用者或 prompt 约束，不是 agent runtime 的程序级边界。

**建议**

- 增加 `AutonomyPolicyModel` 和 `ExecutionGateEvaluator`：
- 输入：active work item、owner doc freshness、protected area hit、reviewer availability。
- 输出：`allow-implement | require-plan | require-ask | research-only | blocked`。
- 这应优先于 ReAct 主循环，而不是在中途通过 prompt 提醒模型自律。

### 3. P1: 缺少 Task Routing 阶段，AGE workflow 目前被压扁成“直接进入 plan/act”

**现状**

- AGE 模板要求先 classify task type，再 route owner docs，再选择 skill，再决定是否需要 plan（`AGENTS.md:23-38`; `docs/process/application-development-workflow.md:167-180`）。
- `nop-ai-agent` 当前没有独立的 route phase；Plan DSL 里虽然有 `Task Route` 相关 authoring 方向，但更像 plan 内容的一部分，不是独立运行时阶段（`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md:94-95`）。

**问题**

- 没有 route phase，就很难支撑 AGE 的“先找 owner doc，再找方法，再决定执行路径”。
- 这会让 plan、skill、tool 过早承担本应属于 task routing 的责任。

**建议**

- 明确在 engine 中增加 Stage 0：`RouteTask`。
- 输出至少包括：
- `taskType`
- `ownerDocs`
- `requiredContextFiles`
- `planRequired`
- `candidateSkills`
- `autonomyDecision`

### 4. P1: 缺少 Independent Review Protocol，当前只有 plan/closure 字段，没有 reviewer 机制

**现状**

- `nop-ai-agent-plan-dsl.md` 确实开始吸收 AGE 的 `reviewedAt`、`closure.auditEvidence`、hard contract / closure blocking 语义（`.../nop-ai-agent-plan-dsl.md:113-116`, `277-283`, `287-314`）。
- 但当前设计里没有完整 reviewer protocol：谁算独立 reviewer、怎样证明 independent、什么时候允许 cold replay、何时必须 subagent/human。
- 相关能力在设计集里几乎只零散出现于 actor vision 中的 `reviewer actor` 暗示，不是稳定 contract（`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md:212`）。

**问题**

- AGE 的 plan draft review / closure audit 不是单纯字段写入，而是明确的独立控制机制。
- 没有 reviewer protocol，`closure.auditEvidence` 很容易退化成“实现 agent 自己写一句已经完成”。

**建议**

- 定义 `ReviewPolicyModel`：
- reviewer availability
- independent reviewer kinds: `human | subagent | none`
- `cold replay` 的允许边界
- 哪些 scope 永远不能自审关闭
- 把 draft review / closure audit 建模为一等 workflow step，而不是 plan 中的附属文本。

### 5. P1: 当前 Plan 设计只吸收了 AGE 的结构外壳，还没有吸收 AGE 的 execution-contract 生态

**现状**

- `nop-ai-agent-plan-dsl.md` 已经吸收了不少 AGE 结构，例如 `goal/nonGoals/currentBaseline/successCriteria/closure/validationChecklist`（`.../nop-ai-agent-plan-dsl.md:72-92`）。
- 但昨天的审查已经指出：`ProjectPlanDoc`、`ExecutionPlanState`、`DelegationSpec` 仍然混在一起（`ai-dev/analysis/2026-06-07-nop-ai-agent-design-doc-review.md:77-136`）。

**问题**

- AGE 的 plan 不是只要“能转成 XML”就够了。
- 它还要求：
- 与 audit 配合
- 与 log 配合
- 与 autonomy 配合
- 与 owner-doc precedence 配合
- 与 backlog readiness 配合
- 当前 `nop-ai-agent` 还没有把这些配套对象和 plan 串成一个稳定生态。

**建议**

- 采用双层模型：
- `ProjectPlanDoc`：AGE Markdown artifact，面向 repo durable memory。
- `ExecutionPlanState`：runtime hard-contract snapshot，供 engine 判定 closure/blocking。
- 不要再把 AGE 整体支持压缩成“扩展 `agent-plan.xdef` 字段”。

### 6. P2: Skill 系统方向偏“runtime activation”，还没覆盖 AGE 的“method selection + required input/output contract”

**现状**

- `skill-system-design.md` 的重点是 skill registry、matching、prompt/tools/hooks 装配（`.../skill-system-design.md:134-186`）。
- 这更像“运行时增强能力选择”，而不是 AGE 模板里的“工作方法路由器”。

**问题**

- AGE skill 的关键字段其实是：什么时候用、何时不用、需要哪些输入、期望输出落到哪个 docs 目录、如果多个 skill 都可用时由谁裁决。
- 当前 `nop-ai-agent` skill 设计缺少这些 method-level routing contract。

**建议**

- 在 Skill 模型中增加 AGE 维度：
- `useWhen`
- `doNotUseWhen`
- `requiredInputs`
- `expectedOutputArtifact`
- `selectionBasis`
- `requiresIndependentSelection`
- 同时把当前 skill source-of-truth 先收口，否则无法稳定承担 AGE method registry 的角色。

### 7. P2: 缺少 Backlog / Work Item 选择模型，无法支持“continue work”式 AGE 自主续跑

**现状**

- AGE 模板把 `docs/backlog/README.md` 作为 agent 自主选任务的入口，并要求 readiness invariant 和 autonomy label 先满足（`.../docs/backlog/README.md:15-29`, `56-62`; `ai-autonomy-policy.md:78-86`）。
- `nop-ai-agent` 当前没有 backlog 或 work-item selection 的正式模型。

**问题**

- 这会让 agent 的“继续干活”能力始终依赖 chat 指令或外部调度，而不能基于 repo durable state 自主选择下一步。

**建议**

- 引入 `WorkItem` / `BacklogRow` 模型：
- requirement path
- owner doc path
- plan path
- autonomy label
- blocker
- readiness
- last checked
- 再让 `continue` / `pick-next` 这类行为基于 backlog policy 做程序级选择。

### 8. P2: 缺少 Dated Artifact / Stable Owner Doc 的统一 artifact taxonomy

**现状**

- AGE 明确区分 stable owner docs 与 dated records（`document-naming-and-timeliness.md:9-50`, `101-170`）。
- `nop-ai-agent` 当前虽然知道 plan、session、analysis、log 等概念，但没有统一 artifact taxonomy 来描述“什么是 stable baseline，什么是 dated trajectory memory”。

**问题**

- 没有 artifact taxonomy，agent 只能把文件当文件，而不能把它们当不同信息职责的持久化对象。
- 这会削弱 AGE 的 attractor / trajectory 分离能力。

**建议**

- 增加 `ArtifactTypeRegistry`：
- stable owner docs
- dated process docs
- execution contract docs
- audit records
- diagnosis records
- repo map / context docs
- 并让 route、memory、closure、doc-sync 都依赖这个 registry。

### 9. P2: 缺少 Stale-Doc / Legacy-Mode 处理流程

**现状**

- AGE 模板对 stale docs、unknown freshness、legacy conflict 有明确规则：先 research 或 plan-first，对冲突进行 drift classification（`source-of-truth-and-precedence.md:143-152`; `project-context.md:15-31`; `ai-autonomy-policy.md:45-58`）。
- `nop-ai-agent` 当前没有对应的 baseline freshness / drift classification 流程对象。

**问题**

- 一旦 owner docs 与 live code 冲突，agent 不知道应该进入哪种模式，只能继续依赖人工提示或 prompt 习惯。

**建议**

- 增加 `BaselineFreshness`、`DriftClassification`、`LegacyModePolicy`：
- `fresh | partially_stale | stale | unknown`
- `implementation_drift | doc_drift | intentional_legacy`
- 它们应直接影响 autonomy decision 和 planning requirement。

## 推荐架构方向

### Direction A: 在现有 Agent Engine 之上增加 AGE Governance Layer

- Layer 1: `Repo Governance`
- `ProjectContextModel`
- `SourceOfTruthPolicy`
- `AutonomyPolicyModel`
- `BacklogItemModel`
- `ArtifactTypeRegistry`
- `ReviewPolicyModel`
- Layer 2: `AGE Workflow Engine`
- `RouteTask`
- `SelectSkillMethod`
- `DraftPlanOrUpdateRequirement`
- `RequestIndependentDraftReview`
- `ImplementSlice`
- `RunVerification`
- `RequestClosureAudit`
- `SyncDocsAndLogs`
- Layer 3: 现有 `ReAct / Tool / Session / Skill / Hook`

**优点**

- 不需要把 AGE 强行压进单个 `agent-plan.xdef`。
- 能保持 `nop-ai-agent` 作为通用执行引擎，同时允许上层 repo-governance 工作流表达 AGE。

### Direction B: 明确区分“通用 Agent Runtime”与“AGE Repo Operator”

- 通用 runtime 负责 ReAct、tool、session、skill activation、multi-agent。
- AGE operator 负责 owner-doc routing、plan lifecycle、audit lifecycle、doc-sync、next-task selection。

**优点**

- 术语更清晰。
- 能避免把所有 AGE 语义都误写成 agent DSL 字段。

## Rejected Alternatives

- 否决方案 1：把 AGE 支持理解为“继续扩充 `agent-plan.xdef` 字段”。原因：AGE 的核心不是 plan 结构，而是 repo-level governance。
- 否决方案 2：把 AGE 支持理解为“给 agent 多加几个 prompt/skill”。原因：AGE 的 autonomy、review independence、source-of-truth precedence 需要程序级控制，不能只靠 prompt 约束。
- 否决方案 3：让 `session` 直接承担所有 repo memory。原因：AGE 明确区分 owner docs、plans、logs、bugs、analysis、retrospectives 等不同 artifact 职责。

## Conclusion

- 最终结论：`nop-ai-agent` 当前只实现了 AGE 所需能力的一部分，而且主要集中在执行器侧，不在 repo governance 侧。
- 如果目标是“更好支持 AGE”，下一步最值得做的不是继续扩展 actor runtime 愿景，而是先补齐 6 个 repo-level hard contract：
- `ProjectContext / routing`
- `Autonomy policy`
- `Independent review protocol`
- `Backlog selection`
- `Plan dual-layer model`
- `Artifact taxonomy + doc-sync`
- 被否决的路线：把 AGE 简化成 `plan + skill + session` 三件套。原因：这样会丢掉 AGE 最关键的 attractor/routing/control 结构。
- 后续工作：更适合新增一份 `ai-dev/design/nop-ai-agent/nop-ai-agent-age-governance.md` 或同级文档，专门定义 AGE governance layer，而不是继续在现有 session/plan 文档中局部打补丁。

## Open Questions

- [ ] `nop-ai-agent` 是否要原生理解 AGE 模板的目录结构，还是只提供可配置的 repo-governance 抽象，让 AGE 作为其中一种 profile？
- [ ] 独立 draft review / closure audit 应该建模为专门的 reviewer actor，还是由通用 `call-agent` + `ReviewPolicyModel` 组合得到？
- [ ] `ProjectPlanDoc` 与 `ExecutionPlanState` 的 source-of-truth 最终应采用 `Markdown-as-source` 还是 `Dual-layer`？

## References

- `C:/can/nop/attractor-guided-engineering-template/README.md`
- `C:/can/nop/attractor-guided-engineering-template/AGENTS.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/index.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/context/README.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/context/project-context.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/context/codebase-map.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/context/ai-autonomy-policy.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/context/source-of-truth-and-precedence.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/backlog/README.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/process/application-development-workflow.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/plans/00-plan-authoring-and-execution-guide.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/audits/00-audit-execution-guide.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/skills/README.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/skills/plan-audit-prompt.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/skills/closure-audit-prompt.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/references/document-naming-and-timeliness.md`
- `C:/can/nop/attractor-guided-engineering-template/docs/references/maintenance-checklist.md`
- `ai-dev/design/nop-ai-agent/README.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/design/nop-ai-agent/skill-system-design.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`
- `ai-dev/analysis/2026-06-07-nop-ai-agent-design-doc-review.md`
