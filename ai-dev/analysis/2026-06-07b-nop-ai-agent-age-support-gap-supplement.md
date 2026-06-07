# 补充说明：AGE 支持能力差距分析 — 独立审查共识与修正

> Status: resolved
> Date: 2026-06-07
> Parent: `ai-dev/analysis/2026-06-07a-nop-ai-agent-age-support-gap-analysis.md`
> Process: 两轮独立子 Agent 审查，直至共识收敛
> Conclusion: 主报告核心结论（新增 repo-governance 层）获得三轮一致支持；共识别 6 项需要修正/补充的细节，3 项争议已通过两轮审查收敛至明确取舍；新发现 4 项跨领域问题需纳入后续设计考量。

---

## 审查过程

| 轮次 | 审查者 | 方法 | 输出 |
|------|--------|------|------|
| Round 1 | `houyi`（Implementation-Expert） | 全量阅读主报告 + 7 份关联设计文档；按 Correctness / Completeness / Clarity / Recommendation Soundness / Disagreements / Amendments 六维审查 | 10 项具体问题 |
| Round 2 | `general`（通用 Agent） | 阅读主报告 + Round 1 审查结果；评估共识点、分歧点、遗漏点 | 6 项共识确认 + 3 项分歧裁决 + 6 项新发现 |

收敛标准：两轮审查后，所有发现点已稳定——无新分歧出现，Round 2 不再提出需要第三轮的新争议。

---

## 三轮一致同意的修正项（Consensus）

以下 6 项在主报告、Round 1、Round 2 之间达成完全一致：

### C1. Gap 6 事实修正：Skill 设计中已存在 `expectedInputs`/`expectedOutputs`

**原始问题**: 主报告第 194-195 行声称 skill 设计缺少 `requiredInputs`、`expectedOutputArtifact` 等字段。

**事实纠正**: `skill-system-design.md:80-81` 已定义 `expectedInputs: SchemaRef` 和 `expectedOutputs: SchemaRef`。报告应承认这些字段存在，然后解释为什么对 AGE 不够用：`SchemaRef` 是通用类型，AGE 需要的是指向特定 `docs/` 目录路径的工件引用（如 `docs/backlog/`、`docs/context/`），而非通用的 Schema 引用。

**一致修改方案**:
- 将 `requiredInputs` 从"缺失列表"中移除
- 改为："Skill 模型现有 `expectedInputs: SchemaRef` / `expectedOutputs: SchemaRef`，但 AGE 需要 docs-directory-typed 的输出工件引用（`expectedOutputArtifact: DocPath`），以及 `useWhen`/`doNotUseWhen`（使用条件 DSL）、`selectionBasis`（选择理由记录）、`requiresIndependentSelection`（独立裁决标记）"
- `useWhen`/`doNotUseWhen` 应至少给出 DSL 条件语法或基于文件匹配的具体示例

### C2. 缺少 Nop Delta 定制化考量

**原始问题**: 主报告提出的 ProjectContextModel、AutonomyPolicyModel 等治理层对象未考虑 Nop 平台 Delta 机制。

**一致补充**: 所有 repo-governance 对象应原生支持 Delta 定制（例如 `_delta` 目录覆盖）。这是 Nop 平台的一等公民要求，不是事后兼容性考虑。

### C3. 未讨论 `maintenance-checklist.md`

**原始问题**: 参考文献中列出 `docs/references/maintenance-checklist.md`，但正文从未引用。

**一致补充**: 建议在 ArtifactTypeRegistry（Gap 8）中将 maintenance-checklist 作为 "dated process doc" 的子类型注册，它天然参与 doc-sync 流程。

### C4. Direction A 与 Direction B 是互补关系，非替代关系

**原始问题**: 主报告将两者列为并列的两个"方向"，暗示读者需要二选一。

**一致修正**: Direction B 应作为 Direction A 内部的"术语层"子节呈现。Direction A 提供三层架构，Direction B 提供其命名约束。

### C5. P1/P2 优先级缺少评判标准

**原始问题**: 主报告直接断言 5 个 P1 + 4 个 P2，未给出分类依据。

**一致补充**: 补充一段标准定义：
- **P1**（阻止）：缺少该能力，AGE 最小可行工作流无法运行
- **P2**（降质）：缺少该能力，工作流仍可运行，但行为质量显著降低

### C6. Gap 6 提议字段未定义实现形态

**原始问题**: `useWhen`、`doNotUseWhen`、`selectionBasis`、`requiresIndependentSelection` 只有名字，没有类型、语法或示例。

**一致修正**: 至少要用 prose 说明每个字段的预期形态：
- `useWhen`: 自然语言条件 + 可选的 DSL 条件表达式
- `doNotUseWhen`: 同上，否定条件
- `selectionBasis`: 事后选择理由记录（字符串 + 可选的证据文件引用）
- `requiresIndependentSelection`: 布尔值，表示是否需要独立审查者参与 skill 选择

---

## 争议项裁决

### D1. 否决方案 1 是否过于绝对？

| 立场 | 观点 |
|------|------|
| Round 1 | "继续扩充 agent-plan.xdef 字段" → 过于绝对，因为解决 Plan representation 问题确实需要改 agent-plan.xdef |
| Round 2 | 原文"理解为"已隐含"只是"的意思，不存在过度否定 |
| **最终裁决** | **采纳 Round 2 立场：主报告表述充分。** 理由：原文使用"继续扩充 agent-plan.xdef 字段"，重点在"继续扩充"（只加字段）而非"禁止触碰"。Plan dual-layer 模型确实需要改 agent-plan.xdef 来添加 ExecutionPlanState 结构，这不等同于"继续扩充"既有字段。否决方案原意是反对"用加字段代替架构层"的简化策略，而非反对所有修改。 |

### D2. Gap 9（Stale-Doc）应为 P1 还是 P2？

| 立场 | 观点 |
|------|------|
| Round 1 | 应升级为 P1，因为文档新鲜度在工作流第一个周期即参与判断 |
| Round 2 | 保持 P2。核心新鲜度门控已隐式包含在 Gap 2（ExecutionGateEvaluator）中，Gap 9 是分类系统化，属于 P2 精细化 |
| **最终裁决** | **保持 P2。** 理由：Gap 2 的 `ExecutionGateEvaluator` 输入已包含 `owner doc freshness`，新鲜度门控的核心语义在 P1 已有。Gap 9 提议的 `BaselineFreshness`、`DriftClassification`、`LegacyModePolicy` 是分类模型和标准化的进阶能力，适合 P2。 |

### D3. 跨 Session Backlog 并发是否应纳入分析？

| 立场 | 观点 |
|------|------|
| Round 1 | 应纳入——两个并行 session 修改同一文件的问题 |
| Round 2 | 超出范围——AGE 模板自身未定义跨 Session 冲突解决，且分析的目标是 AGE 模板支持，不是多 Session 生产部署 |
| **最终裁决** | **Round 2 更合理：不纳入正文，仅在 Open Questions 中提及。** 理由：主报告范围声明为 AGE template 要求 vs nop-ai-agent 匹配度，AGE 模板未定义跨 Session 冲突解决。该问题更适合作为后续 multi-session 设计的独立议题。 |

---

## 新发现项（Round 2 首次提出，未在 Round 1 中出现）

### N1. Gap 1 与 Gap 3 存在语义重叠

ProjectContextModel（Gap 1，启动前该读哪些文件）与 RouteTask Phase（Gap 3，先找 owner doc 再找方法）有显著重叠。两者本质上是同一件事——context-aware task routing——的两个切面。建议合并或至少注明"ProjectContextModel 输出作为 RouteTask 的输入"的串联关系。

### N2. 缺少实现工作量估计

主报告对所有 Gap 标注了 P1/P2 优先级，但没有相对工作量评估。Gap 3（RouteTask phase）和 Gap 1（ProjectContextModel）很可能大量重叠，实现其中一个可能自然包含另一个。"P1"不告诉实现者先做什么。

### N3. 缺少向后兼容性讨论

添加 mandatory RouteTask phase 会改变现有 agent 的执行模型。如果已有 nop-ai-agent 用户依赖当前 plan/skill/session 工作流，增加治理层需要一个 opt-in 模式或迁移路径。主报告未处理这一点。

### N4. 缺少"AGE 支持"的操作性定义

主报告从未明确定义"nop-ai-agent 支持 AGE"意味着什么——是跑通一个完整 AGE 工作流？还是原生强制执行所有 AGE 规则？没有这个定义，差距清单缺少共同的衡量标准。

这些新发现不改变主报告的核心结论，但应在后续设计中纳入考量。

---

## 修正后的差距汇总

以下是在两轮审查调整后的最终差距画像。标记 `[修正]` 表示相比主报告有实质性调整。

| # | 名称 | 原优先级 | 修正后优先级 | 核心变化 |
|---|------|----------|-------------|---------|
| 1 | Repo Context / Routing 模型 | P1 | P1 | 注明与 Gap 3 的串联关系 [新发现 N1] |
| 2 | Autonomy Policy Interpreter | P1 | P1 | — |
| 3 | Task Routing Stage 0 | P1 | P1 | 注明接收 ProjectContextModel 输出 [N1] |
| 4 | Independent Review Protocol | P1 | P1 | — |
| 5 | Plan Dual-Layer Model | P1 | P1 (延续) | 标记为设计评审的延续，非新发现 |
| 6 | Skill Method-Selector 模型 | P2 | P2 | 修正：承认 expectedInputs/expectedOutputs 已存在 [C1]；明确 useWhen 等字段形态 [C6] |
| 7 | Backlog / Work Item 选择 | P2 | P2 | 跨 Session 并发移入 Open Questions [D3] |
| 8 | Artifact Taxonomy | P2 | P2 | 加入 maintenance-checklist.md 子类型 [C3] |
| 9 | Stale-Doc / Legacy Mode | P2 | P2 | 裁决保持 P2，注明与 Gap 2 的关联 [D2] |

---

## 补充建议

### 对 Direction A 的修正（纳入 Delta 兼容性 + 术语层 + 向后兼容）

```
Layer 1: Repo Governance（支持 Delta 定制）
├── ProjectContextModel
├── SourceOfTruthPolicy
├── AutonomyPolicyModel（带 fresh/stale 门控）
├── BacklogItemModel
├── ArtifactTypeRegistry（含 maintenance-checklist 子类型）
└── ReviewPolicyModel

Layer 2: AGE Workflow Engine
├── RouteTask（输入：ProjectContextModel）
├── SelectSkillMethod（带独立裁决选项）
├── DraftPlanOrUpdateRequirement
├── RequestIndependentDraftReview
├── ImplementSlice
├── RunVerification
├── RequestClosureAudit
└── SyncDocsAndLogs（含 artifact taxonomy 路由）

Layer 2.5: Backward Compatibility Adapter
├── LegacyModeToggle（opt-in AGE governance）
└── Plan Upgrade Path（旧→新 Plan 模型迁移）

Layer 3: 现有 ReAct / Tool / Session / Skill / Hook
```

### 对 Open Questions 的增补

在主报告已列出的 3 个 Open Questions 之外，补充：

- [ ] Gap 1 和 Gap 3 在实现时是否应合并为同一个 "Context-Aware Task Routing" 组件？合并可以降低实现门槛，但会失去职责分离的清晰度。
- [ ] 向后兼容策略：是否所有 agent 默认启用 AGE governance layer，还是需要显式配置 `age-governance="enabled"`？
- [ ] 治理层对象如何处理 AGE 模板版本迁移？ArtifactTypeRegistry 是否需要 version 字段？

---

## 审查质量评估

| 维度 | 评分 | 说明 |
|------|------|------|
| Round 1 覆盖度 | 9/10 | 发现了最主要的事实错误（Gap 6）和 4 类遗漏；唯一的覆盖率盲点是未讨论 Gap 1 与 Gap 3 的重叠 |
| Round 2 覆盖度 | 9/10 | 发现了 Round 1 未提及的实现与兼容性维度；对 Round 1 的阅读有两处过度更正（否决方案 D1、跨 Session D3） |
| 共识收敛速度 | 快 | 两轮后稳定，无需要第三轮的新争议 |
| 对主报告的修订影响力 | 高 | 6 项修正达成一致通过；4 项新发现已被纳入后续设计考量 |

---

---

## 引擎级 AGE 治理方案（方向 C —— 审查后新增的补充方向）

> 主报告提出了方向 A（三层叠加）和方向 B（术语分离），两者都把 AGE 控制面定位在 **LLM 侧**：LLM 需要理解 AGE 规则并自愿遵守。方向 C 基于同日后续讨论提出，把控制面定位在 **引擎侧**：引擎原生执行 governance 规则，LLM 是被编排的参与者。以下分析在审查流程完成后追加，不作为对主报告或审查结论的修正，而是作为独立的补充方向。

### 核心思路

- 定义一个 `governance` JSON（或 XDSL），描述文件加载时机、路由条件、前置/后置约束、审计门控
- Agent 引擎直接解析并强制执行，**不由 LLM 解释**
- LLM 只在引擎编排的窗口内操作（选 task、写代码、写文档），不参与控制流判断
- 本质上就是把 AGENTS.md 的路由规则从"agent 应遵守的 prompt"变成"引擎保证执行的配置"

### 关键机制示例

| 控制点 | JSON 表示 | 引擎行为 |
|--------|-----------|---------|
| 启动时必须加载 project-context | `on_init: {load: ["docs/context/project-context.md"]}` | init phase 强制读入 context，未完成则拒绝进入 reasoning |
| 推理前必须先路由 | `before_reasoning: {route: true}` | route 结果（owner docs、skill、protected area）注入 context 后再允许 LLM 推理 |
| 关闭前必须有审计证据 | `on_before_close: {require: "docs/audits/closure-*.md"}` | 文件不存在或内容不含 approval 签名则拒绝 close |
| 自审自批禁止 | `on_review: {require_independent: true}` | 引擎检查 reviewer identity ≠ plan creator identity |
| 文件按类型路由 | `artifact: {log: "ai-dev/logs/", bug: "ai-dev/bugs/"}` | 写文件时 engine 按 artifact type 强制路由 |

### 对主报告 9 个 Gap 的重新评估

在方向 C 下，主报告的 LLM-侧 9 个 Gap 的意义会发生根本变化：

| # | Gap | 方向 A/B（LLM 理解规则） | 方向 C（引擎执行规则） | 变化 |
|---|-----|-------------------------|----------------------|------|
| 1 | Repo Context / Routing 模型 | 需要 ProjectContextModel 让 LLM 理解 | 引擎内置路由逻辑，LLM 无需理解 | **消失**——路由由引擎配置驱动，LLM 只消费注入后的 context |
| 2 | Autonomy Policy Interpreter | 需要 LLM 面的 interpreter + 模型 | 引擎面的 ExecutionGateEvaluator 即可 | **降级**——不需要 LLM-level 模型，只需要引擎的 gate 函数 |
| 3 | Task Routing Stage 0 | 需要独立 Stage 0 概念 | `before_reasoning` hook 携带路由参数 | **降级**——不需要新阶段，在现有 hook 生命周期中加入路由参数即可 |
| 4 | Independent Review Protocol | 需要 ReviewPolicyModel + LLM 理解"独立" | 引擎禁止同一 session 自批 | **大幅降级**——状态层面的身份比较即可，不需要 LLM 理解 |
| 5 | Plan Dual-Layer Model | 需要明确 ProjectPlanDoc vs ExecutionPlanState | 可在 JSON 中用 `plan.mode: "age-standard"` 做配置级区分 | **简化**——不需要完整双层模型，只需要一个配置字段 + 对应的引擎行为 |
| 6 | Skill Method-Selector 模型 | 需要在 skill 中增加 useWhen/selectionBasis | skill 选择可由 engine 在 route phase 完成，LLM 只执行 | **转移**——skill 路由从 LLM 决策变为引擎决策 |
| 7 | Backlog / Work Item 选择 | 需要 BacklogRow 模型让 LLM 自主选 | 引擎加载 backlog + filter readiness，LLM 从候选列表选 | **降级**——不需要 WorkItem 对象的全部字段，只需要一个 pick-one 接口 |
| 8 | Artifact Taxonomy | 需要 ArtifactTypeRegistry 让 LLM 分类 | 引擎按目标目录推断类型，或在 JSON 写固定映射 | **简化**——不需要 LLM 面的 taxonomy 理解 |
| 9 | Stale-Doc / Legacy Mode | 需要 LLM 理解 freshness 等级 | 引擎在 route phase 判断，注入 freshness 标签 | **转移**——LLM 只需消费标签，不需要分类逻辑 |

### 关键设计问题

#### Q1: Schema 归谁管？

建议定义为 `agent-governance.xdef`，不是手写 JSON。XDSL 自动生成 JSON，且继承 Nop 的 Delta 定制能力。

```
xdef → agent-governance.xdef
实例 → 项目级 governance 配置（可被 delta 覆盖）
运行时 → engine 读 xdef 实例，生成不可变 governance 约束表
```

#### Q2: 动作是"工具调用"还是"生命周期指令"？

需要区分两个通道：

| 通道 | 执行者 | 示例 | 是否对 LLM 可见 |
|------|--------|------|----------------|
| `governance.hooks` | 引擎内部 | load file、route、check freshness、assert audit evidence | 否（LLM 不知情） |
| `agent.tools` | LLM 调用 | edit file、read doc、call-agent | 是 |

LLM 不应能通过 tool 调用绕过 governance hooks。这不是限制 LLM，而是保护 AGE 约束不被"绕过去"。

#### Q3: 配置是项目级还是 session 级？

双层设计：

- **项目级**：`age-governance.xdef`（或 `age-governance.json`）放在项目根目录，定义默认规则
- **Session 级 override**：通过 Nop Delta 机制覆盖部分字段。例如 CI 环境可关闭 `require_independent_review`（因为 reviewer 不在线）

#### Q4: 引擎如何知道当前在哪一步？

需要引擎维护一个 `governance lifecycle state`：

```
INIT → ROUTE → PLAN_REVIEW → IMPLEMENT → VERIFY → CLOSURE_AUDIT → SYNC
```

每个阶段对应一个 governance hook 集合。引擎根据当前 state 决定哪些 hooks 必须执行、哪些可选、哪些禁止。LLM 不感知这个 state machine——引擎在背后推进。

### 方向 C 与方向 A/B 的关系

| 维度 | A/B（LLM 理解 AGE） | C（引擎执行 AGE） |
|------|---------------------|-------------------|
| 控制面位置 | LLM | 引擎 |
| 实现量 | 大——6 个新 runtime 对象 | 小——扩展 hook 生命周期 + 一个 xdef schema |
| 可靠性 | 中——LLM 可能绕过 | 高——引擎是硬约束 |
| 灵活性 | 高——LLM 可灵活解释 | 中——动作由配置定义，超出配置的行为需扩展 |
| 对已有设计的复用度 | 低——大部分是新对象 | 高——agent.xdef 的 `on event` 可直接映射 |
| 对 Nop 哲学契合度 | 中 | **高**——正是 DSL→engine 的模式 |

**两者不是二选一**。方向 C 适合做**第一实现路径**（引擎保证 AGE 约束不能被绕过），方向 A/B 适合做 **LLM 面的补充**（当 LLM 需要理解"为什么被阻止"时）。

推荐的实施顺序：
1. 先实现方向 C（engine-level governance），获得 AGE 约束的强保证
2. 在方向 C 的 governance error message 中自然积累方向 A/B 的 LLM-facing 解释层
3. 只在 LLM 需要解释性理解时才引入方向 A/B 的模型对象

### 否决的合并路线

- ~~把 direction C 压缩进 direction A 的 Layer 2 作为"又一个 engine 组件"~~。原因：方向 C 改变了控制面放置假设，不是 A 的三层叠加内部可表达的。方向 A 假设 LLM 位于控制面顶部，方向 C 把控制面放在引擎中。两者是 design-level 差异，不是 layer 归属。
- ~~把 direction C 作为 agent-plan.xdef 的扩展字段~~。原因：governance hooks 是引擎级生命周期指令，不是 plan 的内容字段。混入 plan 会重新引入"LLM 是否遵守 plan"的可绕过性。

---

## 方向 D: AGE 作为一个 Skill（审查后独立设计）

> 此方向受"agent 是通用机制，AGE 是一个 skill"思路启发，独立设计而成。与前三个方向的根本区别在于：**不新增任何引擎机制，全复用现有 hook + skill + tool 体系。** AGE 不是一个"层"（A）、不是一个"术语约束"（B）、不是一个"引擎扩展"（C）——它只是一个 skill 实例。

### 设计原则

1. **Agent 引擎不感知 AGE。** 它只知道 ReAct 循环、hook 生命周期、tool 调用、skill 注册。没有 AGE 概念、没有 governance 模型、没有 autonomy gate。
2. **AGE 是一个 skill。** 就像 `code-review-skill` 或 `bug-diagnosis-skill` 一样注册。它的特别之处仅在于它挂接了引擎的 hook 点来实现 governance 逻辑。
3. **Skill 从 `docs/` 下读取分析结果。** 一个名为 `docs/age-analysis.json`（或 .xdef）的文件，由项目初始化工具或人工维护，描述项目的文件路由、owner docs、autonomy 策略、artifact 分类规则。
4. **Hook 是全部分界线。** 如果 hook 系统不支持某个控制语义（如阻塞 closure），则 AGE-skill 无法实现它。不需要额外引擎机制。

### 运行时架构

```
┌─────────────────────────────────────────────┐
│  Agent Engine（通用，不感知 AGE）              │
│                                              │
│  ReAct Loop          Hook Registry           │
│  Tool System         Skill Registry          │
│  Session/Context     Multi-agent dispatch     │
├─────────────────────────────────────────────┤
│  ▲ hooks: on_init / before_reasoning /       │
│  │         after_acting / before_summary /    │
│  │         on_error / before_close           │
│  └────────────────────────────────────────── │
│                                              │
│  AGE-skill（就是一个普通的 skill）              │
│                                              │
│  1. on_init    → 读 docs/age-analysis.json    │
│  2. before_reasoning → 路由 + autonomy 门控  │
│  3. after_acting     → 文件按类目路由          │
│  4. before_summary   → 写日志，检查进度         │
│  5. on_error         → 写 bug 记录             │
│  6. before_close     → 检查 closure gate      │
└─────────────────────────────────────────────┘
```

### `docs/age-analysis.json` 的结构设计

这是整个 AGE-skill 的内容来源。它不是静态配置，而是**项目分析结果**——可以通过工具扫描项目结构生成，也可以人工维护。

```json
{
  "schema": "age-analysis/v1",
  "project": {
    "name": "nop-entropy",
    "root": "."
  },
  "context": {
    "on_init_load": [
      "docs/context/project-context.md",
      "docs/context/ai-autonomy-policy.md",
      "docs/context/codebase-map.md"
    ]
  },
  "routing": {
    "enabled": true,
    "task_types": {
      "service": { "owner_docs": ["docs/design/service-layer.md"] },
      "orm": { "owner_docs": ["docs/design/orm-model.md"] },
      "api": { "owner_docs": ["docs/design/api-graphql.md"] },
      "core": { "owner_docs": ["docs/design/xlang-basics.md"], "plan_required": true }
    }
  },
  "autonomy": {
    "default_level": "plan-first",
    "overrides": [
      { "area": "model/*.orm.xml", "level": "ask-first" },
      { "area": "nop-*-api/**", "level": "ask-first" },
      { "area": "nop-core/**", "level": "plan-first" }
    ],
    "reviewer_available": true
  },
  "artifacts": {
    "plan": { "path": "ai-dev/plans/{date}-{title}.md", "type": "dated" },
    "log": { "path": "ai-dev/logs/{date}.md", "type": "dated" },
    "bug": { "path": "ai-dev/bugs/{date}-{title}.md", "type": "dated" },
    "analysis": { "path": "ai-dev/analysis/{date}-{title}.md", "type": "dated" },
    "audit": { "path": "docs/audits/{date}-{title}.md", "type": "dated" },
    "design": { "path": "ai-dev/design/{subsystem}/{title}.md", "type": "stable" }
  },
  "closure": {
    "require_audit_evidence": true,
    "require_independent_review": true,
    "evidence_glob": "docs/audits/closure-*.md",
    "blocking_if_no_reviewer": true
  }
}
```

### Skill 各 Hook 的行为设计

#### `on_init` — 加载分析结果

- Skill 从 `.opencode/skills/age-skill/` 加载自身定义
- 读取 `docs/age-analysis.json`，构建内存中的 governance 状态表
- 将 `on_init_load` 中的文件注入 session context

#### `before_reasoning` — 路由 + 门控

```
1. 解析用户输入或当前 task type
2. 从 routing.task_types 找到 owner_docs
3. 加载 owner_docs 到 context
4. 检查 autonomy level：
   - research-only → 注入只读指令，阻止写操作
   - ask-first → 注入"必须先提问"的约束
   - plan-first → 检查是否有 plan，无则阻止推理
   - implement → 正常放行
5. 检查 protected area 匹配
```

这个 hook 的行为在 skill 内定义。引擎只看 hook 的返回值——如果 hook 返回 `{block: true, reason: "..."}`，引擎就停止当前循环。

#### `after_acting` — 文件路由

- Skill 拦截工具调用结果中的文件写入操作
- 根据 `artifacts` 映射判断目标目录
- 如 log→`ai-dev/logs/`、bug→`ai-dev/bugs/`
- 如果写入路径与映射冲突，skill 可以重定向或报错

#### `before_summary` — 日志与进度

- 将当前 session 的关键事件写入 artifact 中定义的 log 文件
- 检查当前 plan 的进度，更新 plan 文件

#### `on_error` — Bug 记录

- 当 agent 报告错误时，skill 将错误详情按 bug artifact 格式写入 `ai-dev/bugs/`

#### `before_close` — Closure Gate

```
1. 检查是否有未完成的 plan
2. 如果有，检查 closure.require_audit_evidence：
   - true → 搜索 evidence_glob，确认文件存在且内容含审核签名
   - 不满足 → 返回 {block: true, reason: "缺少 closure audit 证据"}
3. 检查 require_independent_review：
   - true → 检查 review 记录中的 agent_id 是否 ≠ 当前 session 的 agent_id
   - 不满足 → 返回 {block: true, reason: "需要独立审查者审批"}
```

### 对 9 个 Gap 的重新评估

| # | Gap | 方向 D 的覆盖方式 | 是否需引擎改动 |
|---|-----|------------------|-------------|
| 1 | Repo Routing | skill 在 `before_reasoning` 中根据 `age-analysis.json` 的 routing 表加载文件 | 否 |
| 2 | Autonomy Policy | skill 在 `before_reasoning` 中解释 autonomy 规则并返回 block 信号 | 否（引擎需支持 hook 返回 block） |
| 3 | Task Routing | skill 在 `before_reasoning` 中分类 task、加载 owner docs | 否 |
| 4 | Independent Review | skill 在 `before_close` 中检查 reviewer identity | 否（引擎需提供 agent_id 访问） |
| 5 | Plan Dual-Layer | skill 将 plan 作为 artifact 管理，不要求 engine 理解 plan 结构 | 否 |
| 6 | Skill Method-Selector | AGE-skill 本身就是"方法选择器"，它读取 analysis.json 决定当前用哪种方法 | 否 |
| 7 | Backlog Selection | `age-analysis.json` 的 backlog 条目在 `before_reasoning` 中被 skill 加载为候选 | 否 |
| 8 | Artifact Taxonomy | `age-analysis.json` 的 artifacts 表定义了分类，skill 在 `after_acting` 中执行 | 否 |
| 9 | Stale-Doc Handling | skill 在 `on_init` 中检查文件时间戳，标记 freshness | 否 |

**只有一个隐含依赖**: 引擎的 hook 系统必须支持 "block" 语义——即 hook 可以阻止后续的 ReAct 循环或 close 操作。如果当前 hook 设计只支持"在事件前后做额外操作"（如打日志、注入上下文），但不支持"阻止事件发生"，则方向 D 的 closure gate 和 autonomy gating 无法实现。

检查当前设计：`02-execution-model.md` 的 hook 列表中没有定义 hook 的返回值契约。`nop-ai-agent-hook-skill-engine.md` 提到 hook 可以"修改 context、注入 prompt、拦截 tool call"，但没有明确是否支持"阻止执行"。如果引擎的 hook 机制支持类似 `{action: "proceed"}` vs `{action: "block", reason: "..."}` 的返回值，方向 D 可完整工作。

### 方向 D 的边界条件

**能做什么**：
- 所有 AGE 的路由、门控、审计检查都可以在 skill 内实现
- 项目通过更换 skill 即可切换开发方法论（AGE / Scrum / 瀑布 / 自由模式）
- 多个 skill 可同时注册（一个负责 governance，一个负责业务能力）
- `age-analysis.json` 可被 Nop Delta 覆盖，实现项目级定制

**不能做什么（需要引擎配合）**：
- Hook 的 block 语义：当前 hook 设计未定义"hook 能否阻止执行"。如果引擎只把 hook 当作"旁路监听"，方向 D 就无法实现强制门控
- Agent identity 访问：独立审查需要知道"当前 agent 是谁"，current design 需要暴露 session.agentId 或类似信息给 hook
- 文件写入拦截：`after_acting` hook 需要能检查 tool write 的目标路径并重定向——这要求 hook 能访问 tool call 的参数和结果

**如果引擎不配合 block 语义，方向 D 退化为方向 A/B**——skill 只能"建议"AGE 规则，不能"强制"规则。AGE 变成 prompt-level 约束，失去 control 意义。

### 方向 D 的否决依据

- ✅ 否决"需要新引擎机制"：方向 D 证明现有 hook + skill 体系足以表达 AGE——唯一的新要求是 hook block 语义，但那是 hook 系统本身的完善，不是 AGE 特有的。
- ✅ 否决"AGE = plan extension"：方向 D 中 AGE 是一个 skill，plan 只是 skill 管理的一个 artifact。
- ✅ 否决"AGE = prompt injection"：方向 D 中 AGE 规则在 skill 代码中执行，不在 prompt 中。
- ❌ 否决"AGE = engine governance layer"（方向 C）：方向 D 比方向 C 更轻，不需要 engine 理解 governance 概念。但如果 hook 系统不支持 block，方向 C 反而更容易实施（因为 engine 需要自己执行 governance）。

### 与方向 A/B/C 的综合对比

| 维度 | A (LLM理解) | B (术语分离) | C (引擎治理) | **D (Skill 治理)** |
|------|-------------|-------------|-------------|-------------------|
| 引擎改动量 | 大 | 小 | 中 | **最小** |
| 可靠性 | 低 | 低 | 高 | **中-高**（依赖 hook block） |
| AGE 作为可选功能 | ❌ 需要引擎感知 | ❌ 需要引擎感知 | ❌ 需要引擎感知 | **✅ 只是一个 skill** |
| 其他方法论支持 | 需重设计 | 需重设计 | 需重设计 | **换 skill 即可** |
| 对 Nop 哲学契合 | 中 | 中 | 高 | **最高** |
| 实现风险 | 高 | 低 | 中 | **最低** |
| Hook block 依赖 | 否 | 否 | 否 | 是 |

### 实施建议

方向 D 是 A/B/C 中最轻、最灵活的。推荐路线：

**Phase 0**: 确认 engine hook 系统是否支持 block 语义。如果不支持，在 hook registry / invocation 层增加 `HookResult { action: "proceed" | "block" | "redirect", reason?: string }`。

**Phase 1**: 实现 `age-skill`，覆盖 `on_init` + `before_reasoning` + `after_acting`（不依赖 block 的 subset）。此时 skill 是"建议型"AGE。

**Phase 2**: 接入 block 语义。`before_reasoning` 的 autonomy 门控和 `before_close` 的 closure gate 变为强约束。skill 从"建议型"升级为"强制型"。

**Phase 3**: 编写 `age-analysis.json` 的自动生成工具（分析项目结构 → 输出 JSON），降低接入成本。

### 与现有设计文档的关系

方向 D 只需确认以下现有机制是否可用：

| 需要的能力 | 当前状态 | 来源 |
|-----------|---------|------|
| Skill 注册 hook | ✅ 已设计 | `skill-system-design.md:134-186` |
| Hook 事件点 | ✅ 已设计 | `02-execution-model.md:79-95` |
| Skill 读取外部文件 | ⚠️ 需确认 skill 能否读项目文件 | 未明确 |
| Hook block 语义 | ❌ 未定义 | 需补充 |
| Agent identity | ⚠️ 需暴露 | 未明确 |
| 文件写入拦截 | ❌ 未定义 | 需补充 |

**方向 D 的结论**：如果以上 6 个能力中的后 3 个得到补充，AGE 可以在不修改 agent 引擎核心、不新增任何 AGE 专属机制的前提下，通过一个 skill + 一个 JSON 分析文件完整实现。这是所有方向中最"Nop"的——组合现有机制，不发明新概念。

---

## References
- `ai-dev/analysis/2026-06-07-nop-ai-agent-design-doc-review.md`（前述设计评审）
- `ai-dev/design/nop-ai-agent/README.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/design/nop-ai-agent/skill-system-design.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`
- Round 1 审查输出（task_id: ses_15d9de9afffeAglfr9XnbO7m0z）
- Round 2 审查输出（task_id: ses_15d9be413ffeh5qwZ5JyPNJKyE）
