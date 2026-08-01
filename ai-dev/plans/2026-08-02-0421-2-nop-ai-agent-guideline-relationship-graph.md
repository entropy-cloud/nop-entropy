# W5-2 Guideline 依赖/排除关系图 — Guardrail 规则关系建模

> Plan Status: active
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W5-2；`ai-dev/design/nop-ai-agent/guardrail-contract.md` §增量 2；`ai-dev/analysis/agent-survey/2026-08-01-parlant-conversation-control-analysis.md`
> Related: `2026-08-02-0421-1`（W5-1 GuardrailTestSuite，同 W5 组但正交：W5-1 是 test-time 验收，W5-2 是 runtime 规则关系建模）；`2026-08-02-0900-3`（W5-3 BAIL，已 completed）
> Mission: nop-ai-agent-harness-evolution
> Work Item: W5-2

## Purpose

把 nop guardrail 从**线性检查链**升级为**规则关系图**：规则间建模依赖（命中 A 自动拉入 B → 上下文收敛）与排除（命中 A 排除 C → 上下文收窄）关系，使规则靠**结构**收敛而非靠 LLM 注意力或隐式 prompt 约束。对标 parlant `RelationalResolver`，适用于企业合规复杂规则集（多规则冲突时靠结构决策）。

## Current Baseline

（2026-08-02 live repo 核对）

- `IContentGuardrail`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/IContentGuardrail.java`）是单一 guardrail SPI：`check(direction, content, ctx) → GuardrailResult`。**无"规则"抽象、无"规则集"概念、无规则间关系建模**。
- `PromptInjectionGuardrail`（同包）是 production 实现，内部用 4 个**硬编码正则**（prompt_override / role_hijack / exfiltration / invisible_char）顺序扫描——这是"线性检查链"的典型形态，规则间无显式关系。它带 `GuardrailMode`（OFF/REPORT/ENFORCE）三态：OFF 跳过、REPORT 命中只 WARN 放行、ENFORCE 命中 Block。
- `GuardrailResult` 三态：Pass / Block(reason) / Modify(content)。
- guardrail 在 `io.nop.ai.agent.engine.AgentPromptAssembly`（`engine` 包，非 prompt 包）两处被调用（INPUT `:79` + OUTPUT `:305`）；`AgentPromptAssembly` 持 `IContentGuardrail` 字段（`:58`），运行时由 `ReActAgentExecutor`（`checkInputGuardrail`/`checkOutputGuardrail`）经装配链 `Builder.contentGuardrail → DefaultAgentEngineConfig → AgentExecutorResolver → AgentPromptAssembly` 驱动。
- `guardrail-contract.md` §增量 2 是本计划的设计来源（待落地），描述了 `GuardrailRule` 增 dependsOn/excludes。
- 底层 DAG 能力 `io.nop.core.model.graph.dag.Dag.containsLoop()` 提供环检测（注：nop-task 的 `GraphStepAnalyzer` 虽有环检测但其 `analyze(IGraphTaskStepModel)` 强绑 task 域，**不可直接复用于 guardrail rule 图**；本计划复用的是底层 `Dag.containsLoop()` 能力，非 `GraphStepAnalyzer` 类）。
- **已识别的 pre-existing contract drift**（须诚实裁定）：`IContentGuardrail.java:6-11` `@apiNote` 与 `guardrail-contract.md` SPI 表称"无 production-grade 实现 / No production alternative exists"，但 `PromptInjectionGuardrail` 存在且自标 Production-grade——**注释/文档与 shipped 实现矛盾**。本计划编辑同一 `guardrail-contract.md` 时须一并修正（Phase 1）。
- **缺口**：guardrail 规则无声明式模型（散落在 Java 正则里）、无规则集组装、无关系图评估器、无关系解析器。当前所有规则无差别全量评估。

## Goals

- 引入声明式 `GuardrailRule` 模型（规则定义 + 关系：dependsOn / excludes）。
- 引入规则关系图评估器（`RuleGraphResolver`）：给定命中规则，计算活跃规则集（dependsOn 传递闭包拉入 + excludes 移除），靠结构收敛而非全量评估。
- 引入环检测（dependsOn 图成环时 fail-loud，复用底层 `io.nop.core.model.graph.dag.Dag.containsLoop()` 能力）。
- 提供组合式 `IContentGuardrail` 实现，用关系图驱动规则评估，且与既有 `PromptInjectionGuardrail` / 线性链**共存**（零回归：不替换既有 guardrail）。
- 适用场景验证：企业合规复杂规则集（规则冲突时靠 excludes/dependsOn 结构决策）。

## Non-Goals

- **不替换既有 `IContentGuardrail` SPI 或 `PromptInjectionGuardrail`**——关系图 guardrail 是新增组合实现，与既有线性链共存（可由消费方选择装配）。
- **不做 LLM 驱动的规则匹配评估**（parlant 用 LLM 评估哪些规则命中当前上下文）——首版规则匹配是确定性的（正则/结构化条件，类既有 PromptInjectionGuardrail）。LLM 匹配留 successor。
- **不做"仅命中规则注入上下文"**（parlant Guideline 匹配引擎动态裁剪 system prompt）——这是上下文工程范畴（W4 域），不是 guardrail 规则关系范畴。本计划只做"规则评估集的结构收敛"。
- **不做 guardrail 测试验收**——那是 W5-1（`2026-08-02-0421-1`）的职责。但本计划的新增 guardrail 可被 W5-1 套件验收（互补不重叠）。
- **不引入外部依赖**（parlant 是 Python，不引入；仅吸收 RelationalResolver 语义，以 nop 原生方式实现）。

## Scope

### In Scope

- 声明式 `GuardrailRule` 模型（rule id + 匹配条件 + dependsOn + excludes + 结果动作 Block/Modify）。
- `RuleGraphResolver`：命中规则集 → 活跃规则集（dependsOn 传递闭包 + excludes 移除）+ 环检测（fail-loud）。
- 组合式关系图 guardrail（`IContentGuardrail` 实现，用 resolver 驱动规则评估）。
- 声明式规则集配置形态（可 Delta 定制）。
- 端到端：规则集配置 → resolver 求解活跃集 → 规则评估 → 聚合 `GuardrailResult`。

### Out Of Scope

- 既有 guardrail 替换 / SPI 改动（见 Non-Goals，共存不替换）。
- LLM 驱动规则匹配 / 上下文动态裁剪（successor）。
- guardrail 验收套件（W5-1 域）。

## Execution Plan

### Phase 1 - 设计裁定与契约定义

Status: planned
Targets: `ai-dev/design/nop-ai-agent/guardrail-contract.md` §增量 2（由"增量设计"升级为 final 设计）

- Item Types: `Decision | Fix`

- [ ] **Decision A：GuardrailRule 声明式模型形态**。裁定 rule 的声明式载体——是否新增 xdef（如 `guardrail-rules.xdef`）还是 Java 配置模型 + 外部资源文件。裁定 rule 字段（id / 匹配条件形态：正则 or 结构化 / direction / 结果动作 Block|Modify / dependsOn 列表 / excludes 列表 / threatClass 标签）。裁定与既有 `PromptInjectionGuardrail` 硬编码规则的关系（可否把现有 4 条规则迁移为声明式 rule，还是新增并存）。回写 `guardrail-contract.md` §增量 2。
- [ ] **Decision B：关系语义求值模型**。裁定 dependsOn / excludes 的求值顺序与传递性。必须覆盖以下语义清单（Phase 1 逐条裁定，消除 resolver 与聚合器的编码歧义）：
  - dependsOn 是否传递闭包（A→B→C，命中 A 拉入 B 和 C）。
  - **dependsOn 拉入的规则在"规则评估"阶段是否参与内容判定**（核心语义）：parlant 的 guideline 是 condition-action，dependsOn 拉入 B 是为执行 B 的 action；但 nop guardrail rule 是"匹配-判定"，被拉入的 B 若不命中内容则返回 Pass——须裁定 dependsOn 的真正语义是"拉入 B 参与 B 自身的内容判定"（B 可能 Block，扩展检测面）还是"拉入 B 仅为让 B 的 excludes 生效"（收窄，B 本身不判定）。这决定 resolver 输出的活跃集元素携带"规则 id + 待评估标志"还是仅"规则 id"。
  - excludes 是否对**初始命中集**成员生效（能否移除靠内容匹配命中的规则），还是仅移除 dependsOn 传递拉入的。
  - excludes 是否传递（A excludes B，B excludes C → A 是否间接 excludes C）。
  - 被拉入的规则自身带 excludes 时如何级联。
  - dependsOn 与 excludes 冲突时谁优先（excludes 收窄应胜过 dependsOn 扩展，须明示裁定）。
  回写 `guardrail-contract.md` §增量 2。
- [ ] **Decision C：环检测与 fail-loud 策略**。裁定 dependsOn 图成环时的行为——fail-loud（抛异常拒绝加载规则集）。环检测复用底层 `io.nop.core.model.graph.dag.Dag.containsLoop()` 能力（**非** nop-task `GraphStepAnalyzer`，后者强绑 task 域不可直接复用，见 Current Baseline）。裁定 excludes 成环是否也检测（excludes 环一般无意义但须裁定）。回写设计。
- [ ] **Decision D：组合式 guardrail 集成形态**。裁定关系图 guardrail 如何作为 `IContentGuardrail` 实现共存——是新增一个 `RuleGraphGuardrail implements IContentGuardrail`，还是新增 `IRuleSet` 组合层被既有 guardrail 消费。裁定零回归策略（默认不装配关系图 guardrail 时，行为等价今日）。回写设计。
- [ ] **Decision E：规则评估结果聚合**。裁定多个活跃规则各自返回 Block/Modify/Pass 时如何聚合为单一 `GuardrailResult`——Block 优先（任一 Block 即 Block）？Modify 链式应用（A Modify 后 B 再 Modify）？冲突规则（A Block、B Modify 同内容）的裁定策略。**须裁定与既有 `GuardrailMode`（OFF/REPORT/ENFORCE）的关系**：新的 rule-based guardrail 是自带独立 mode 语义，还是复用既有 GuardrailMode（rule 返 Block 但 guardrail 处于 REPORT 模式时只 WARN 放行）。回写设计。
- [ ] **Decision F：规则集配置组装与 Delta**。裁定规则集如何组装与 Delta 定制——单文件 vs 多文件合并；Delta 叠加时 dependsOn/excludes 是合并还是覆盖。**autonomy 约束声明**：若裁定采用新 xdef（如 `guardrail-rules.xdef`）形态，新增 xdef schema 不修改既有 schema，属 `nop-xdefs` 常规扩展（非 Protected Area 的"修改既有生成管线"）；若裁定需修改既有 xdef 或 codegen 模板，则升级为 plan-first Protected Area 处理。回写设计。
- [ ] **修正 pre-existing contract drift**（Current Baseline 已识别）：修正 `guardrail-contract.md` SPI 表对 `IContentGuardrail` 的 "No production alternative exists" 描述（承认 `PromptInjectionGuardrail`）+ 修正 `IContentGuardrail.java` `@apiNote` 与代码矛盾的措辞（不改行为，仅注释准确性）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 6 条 Decision（A–F）全部裁定并回写 `guardrail-contract.md` §增量 2（status 升级为 final）；Decision B 的语义清单（dependsOn 拉入规则是否参与判定 / excludes 对初始命中集 / 传递性 / 级联 / 冲突优先级）逐条裁定
- [ ] 设计文档描述的 rule 模型 / resolver 求值模型（含 Decision B 完整语义清单）/ 环检测（Dag.containsLoop）/ 集成形态 / 聚合策略（含 GuardrailMode 关系）能被 Phase 2 直接编码（I/O 契约明确）
- [ ] pre-existing contract drift（SPI 表 / @apiNote）已修正，design doc 与代码不再矛盾
- [ ] No owner-doc update required beyond `guardrail-contract.md`（本 Phase 仅设计裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 规则模型与关系图解析器实现

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/`（新增 rule/relation 包）；按 Decision A 裁定的声明式载体

- Item Types: `Proof`

- [ ] 按 Decision A 落地 `GuardrailRule` 声明式模型（id + 匹配条件 + direction + 结果动作 + dependsOn + excludes）
- [ ] 按 Decision B 落地 `RuleGraphResolver`：命中规则 → dependsOn 传递闭包拉入 → excludes 移除 → 活跃规则集（excludes 优先于 dependsOn 扩展，按裁定 B）
- [ ] 按 Decision C 落地环检测（dependsOn 图成环 fail-loud，抛异常含环路径描述）
- [ ] 按 Decision E 落地规则结果聚合器（Block 优先 / Modify 链式 / 冲突裁定，按裁定 E）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `GuardrailRule` / `RuleGraphResolver` / 聚合器均有对应单元测试（Minimum Rules #25），覆盖：dependsOn 传递闭包 / excludes 移除 / excludes 优先于 dependsOn / 环检测 fail-loud / 多规则聚合（Block 优先 + Modify 链式 + 冲突）
- [ ] **无静默跳过**（Minimum Rules #24）：环检测命中时抛异常非返回空集；resolver 遇到 excludes 与 dependsOn 冲突按裁定显式处理（非静默忽略）
- [ ] resolver 输出的活跃规则集是确定性的（相同命中集 → 相同活跃集），可被断言
- [ ] No owner-doc update required beyond Phase 1 已回写的设计
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 组合式 Guardrail 集成与端到端验证

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/`；`nop-ai/nop-ai-agent/src/test/...`

- Item Types: `Proof`

- [ ] 按 Decision D 落地组合式关系图 guardrail（`RuleGraphGuardrail implements IContentGuardrail` 或裁定 D 选定的形态），经 resolver 驱动规则评估
- [ ] 按 Decision F 落地规则集配置组装与 Delta 定制能力
- [ ] **端到端验证**（Minimum Rules #22）：一个集成测试从"规则集配置加载 → resolver 求解活跃集（dependsOn 拉入 + excludes 移除）→ 规则评估 → 聚合为单一 `GuardrailResult`"完整跑通
- [ ] **接线验证**（Minimum Rules #23）：测试证明关系图 guardrail 经真实装配链（`Builder.contentGuardrail` → `DefaultAgentEngineConfig` → `AgentExecutorResolver` → `AgentPromptAssembly`）被注入，且 `AgentPromptAssembly` 在 INPUT/OUTPUT 两处确实调用了它（运行时连通，非只类型存在）；至少一个集成测试从 `ReActAgentExecutor` 入口（经 `AgentPromptAssembly.checkInputGuardrail`/`checkOutputGuardrail`）到 resolver + 规则评估完整走通；resolver 确实在运行时被调用求解活跃集
- [ ] 零回归验证：不装配关系图 guardrail 时（default），`AgentPromptAssembly` 行为等价今日（既有 guardrail 不变）
- [ ] 企业合规场景样例：构造一组有 dependsOn/excludes 关系的规则（如"金融交易规则 excludes 一般对话规则"），验证结构收敛效果

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 组合式关系图 guardrail 完整落地，作为 `IContentGuardrail` 实现可被装配
- [ ] **端到端验证**：集成测试完整跑通 配置→resolver→评估→聚合→`GuardrailResult`，活跃集求解结果被断言（Anti-Hollow：不是只验证组件存在）
- [ ] **接线验证**：`AgentPromptAssembly` 调用链在装配关系图 guardrail 时确实走到 resolver + 规则评估（非旁路）
- [ ] 零回归：既有 `PromptInjectionGuardrail` 行为不变（既有 guardrail 测试全过）；default 不装配时行为等价今日
- [ ] 企业合规场景样例落地，结构收敛效果可观测（dependsOn 拉入 / excludes 移除改变评估结果）
- [ ] `guardrail-contract.md` §增量 2 与落地一致（无新 drift）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 声明式 `GuardrailRule` 模型（含 dependsOn/excludes）完整落地
- [ ] `RuleGraphResolver`（传递闭包 + excludes 移除 + 环检测）完整落地
- [ ] 组合式关系图 guardrail 作为 `IContentGuardrail` 实现可装配
- [ ] 端到端路径完整连通（配置→resolver→评估→聚合→`GuardrailResult`）
- [ ] 零回归：既有 guardrail 执行路径不变，default 行为等价今日
- [ ] 无运行时 guardrail SPI 被破坏性改动（共存不替换）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs（`guardrail-contract.md` §增量 2）已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）关系图 guardrail 运行时确实被 `AgentPromptAssembly` 调用，（b）resolver 确实求解活跃集，（c）聚合 `GuardrailResult` 来自真实规则评估（非硬编码），无空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Minimum Rules #26）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码 0（Minimum Rules #22/#24，When Closing step 5b）
- [ ] backlog `nop-ai-agent-harness-evolution-roadmap.md` W5-2 标记 done

## Deferred But Adjudicated

### LLM 驱动的规则匹配评估

- Classification: `optimization candidate`
- Why Not Blocking Closure: parlant 用 LLM 评估哪些规则命中当前上下文（动态、上下文感知）。但 nop 首版规则匹配是确定性的（正则/结构化条件，类既有 PromptInjectionGuardrail），满足"规则关系图结构收敛"的核心价值（靠结构而非 LLM 注意力）。LLM 匹配是非确定性的增量扩展，不阻断"关系图建模成立"。
- Successor Required: yes
- Successor Path: W5 后续 successor plan（LLM 规则匹配）

## Non-Blocking Follow-ups

- 将既有 `PromptInjectionGuardrail` 4 条硬编码规则迁移为声明式 rule（首版新增并存即可，迁移是治理优化）
- 规则集的版本化与跨 agent 共享（首版单 agent 装配成立即可）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

<<独立 closure audit 后填写>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
