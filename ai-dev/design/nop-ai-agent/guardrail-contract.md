# Guardrail & NoOp Baseline Contract

> Status: active
> Plan: 2056 (Guardrail Safety & NoOp Baseline)
> Last Updated: 2026-07-31

## Purpose

Document the minimum guard configuration required for production safety, and
classify every SPI interface in `nop-ai-agent` by implementation maturity.

## Minimum Production Safety Guards

The following guards **must** be non-NoOp for minimum production safety.
Constructing an engine with any of these in insecure-default mode emits a
**WARN** via `DefaultAgentEngine.warnIfInsecureDefaults()`.

| Guard | Secure Default | Insecure (WARN) | Why |
|-------|---------------|-----------------|-----|
| `IToolAccessChecker` | `DefaultToolAccessChecker` | `AllowAllToolAccessChecker` | Blocks dangerous tools (bash, write-file, etc.) |
| `IPathAccessChecker` | `DefaultPathAccessChecker` | `AllowAllPathAccessChecker` | Blocks sensitive paths (~/.ssh/, /etc/, .env) |
| `IAuditLogger` | `Slf4jAuditLogger` | `NoOpAuditLogger` | Records tool decisions; silent = no audit trail |
| `IDenialLedger` | `DefaultDenialLedger` | `NoOpDenialLedger` | Counts denials and pauses sessions on threshold |
| `IPostDenialGuard` | `DefaultPostDenialGuard` | `PassThroughPostDenialGuard` | Blocks blind retries of denied operations |
| `ISecurityLevelResolver` | `DefaultSecurityLevelResolver` | `NoOpSecurityLevelResolver` | Classifies operations by security level |
| `IPermissionMatrix` | `DefaultPermissionMatrix` | `PassThroughPermissionMatrix` | Enforces channel × level permissions |
| `IApprovalGate` | `DefaultApprovalGate` | `AutoApproveGate` | Defense-in-depth deny for RESTRICTED operations |

## SPI Interface Classification

Every SPI interface in `nop-ai-agent` is classified into one of:

- **production-ready**: production-grade implementation shipped
- **partial**: functional but non-persistent (in-memory) implementation
- **partial-with-implementation**: basic implementation that works but is not production-grade
- **no-production**: only NoOp or test-only implementation

### Production-Ready

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IRetryPolicy` | `StandardRetryPolicy` | Configurable retry with backoff |
| `ICheckpointManager` | `FileBackedCheckpointManager` / `DBCheckpointManager` | Persistent checkpoint recording |
| `ICircuitBreaker` | `ThresholdBreaker` | Configurable failure threshold |
| `ISustainer` | `SisypheanSustainer` | Configurable sustain rounds |

### Partial (In-Memory Only)

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `IWriteIntentRegistry` | `InMemoryWriteIntentRegistry` | Cross-process detection is a future successor |
| `IFencingTokenService` | `DefaultFencingTokenService` | Single-JVM; cross-process fencing is a successor |
| `IContributionRegistry` | `InMemoryContributionRegistry` | Contributions lost on JVM restart |

### Partial-With-Implementation

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `ISkillProvider` | `FileSystemSkillProvider` | Basic filesystem provider; no DB support |
| `ISkillCurator` | `LLMCurator` | Basic LLM-based curator; no rule-based mode |
| `IGoalTracker` | `SessionGoalTracker` | In-memory per-session; no persistent tracking |

### No-Production (NoOp Only)

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IBudgetProvider` | `NoOpBudgetProvider` | No production alternative exists. INFO-level awareness at construction. |

> `IContentGuardrail` no longer belongs here: a production-grade implementation
> (`PromptInjectionGuardrail`) is shipped (see `Production-Ready Content Guardrail`
> below). The shipped engine default remains `NoOpContentGuardrail` for backwards
> compatibility, so constructing an engine with the NoOp default still emits a
> **WARN** (there is now a production alternative to swap to — see
> `NoOp Awareness Design`).

### Production-Ready Content Guardrail

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IContentGuardrail` | `PromptInjectionGuardrail` | Regex-based detection of prompt_override / role_hijack / exfiltration / invisible_char (OpenSquilla taxonomy). Modes OFF/REPORT/ENFORCE. Shipped engine default is still `NoOpContentGuardrail`; `PromptInjectionGuardrail` is wired explicitly via `setContentGuardrail()`. |

## NoOp Awareness Design

Two checks fire at construction when NoOp-only defaults are detected. Their log
levels differ because a production alternative now exists for one but not the
other:

1. **NoOpContentGuardrail** → **WARN** (`AgentStartupWarnings.warnIfInsecureDefaults`):
   "No production implementation available for IContentGuardrail — content safety
   is not enforced." WARN is correct here because a production alternative
   (`PromptInjectionGuardrail`) **does** exist to swap to, so the warning has a
   remediation path.
2. **NoOpBudgetProvider** → **INFO**: "No production implementation available for
   IBudgetProvider — execution budget is unlimited." INFO remains correct here
   because no production alternative exists to swap to — WARN would create noise
   with no remediation path.

## NoOpSessionTakeoverLock Design Rationale

`NoOpSessionTakeoverLock` is **excluded** from INFO/WARN checks. Single-process
deployments rely on the engine's in-process `runningExecutions.putIfAbsent`
guard (plan 197). The takeover lock is incremental capability (NoOp → engine
walks existing path), not a security downgrade. The design decision is recorded
in the `NoOpSessionTakeoverLock` javadoc (lines 338-341).

## Recommended Minimum Guard Configuration for Production

```java
DefaultAgentEngine engine = DefaultAgentEngine.builder(chatService, toolManager)
    .toolAccessChecker(new DefaultToolAccessChecker())
    .pathAccessChecker(new DefaultPathAccessChecker())
    .auditLogger(new Slf4jAuditLogger())  // or custom DB logger
    .approvalGate(new DefaultApprovalGate())
    .securityLevelResolver(new DefaultSecurityLevelResolver())
    .permissionMatrix(new DefaultPermissionMatrix())
    .denialLedger(new DefaultDenialLedger())
    .postDenialGuard(new DefaultPostDenialGuard())
    .build();
```

All defaults above are already the engine's shipped defaults (constructor /
Builder), so the builder call without overrides satisfies the minimum
production guard configuration.

---

## 外部调研驱动的增量设计（2026-08-01：Guardrail 测试闭环 / Guideline 关系图 / BAIL）

> 来源：agent-survey（promptfoo Plugin+Grader / parlant Guideline 关系图+BAIL）。nop 已有运行时 guardrail 执行（security 6 层 + ContentOrigin），本节补"验收"与"关系建模"两个缺失维度。

### 增量 1：Guardrail 测试闭环（Plugin + Grader 分离）— final

> **Status: final**（W5-1，2026-08-02）。7 条裁定 A–G 已固化，接口契约可直接编码。

nop 有 guardrail 执行但无系统化测试。本节交付一个 **test-time 组件**（`GuardrailTestSuite`，非运行时），对标 promptfoo 的 Plugin（生成攻击）+ Grader（rubric 打分）分离范式，对既有 `IContentGuardrail` 做红队验收。组件树：

```
guardrail-test（测试时组件，非运行时；不调用真实 LLM / 真实 agent session）：
  ├── AttackPlugin（声明式语料提供者，返回 AttackCase 列表）
  │   ├── 分类语料：prompt_injection / role_hijack / exfiltration / jailbreak /
  │   │              hallucination / invisible_char / privilege_escalation / 行业垂直集
  │   └── AttackTransform（策略层装饰器）：base64 / crescendo 二次变换（绕过简单防御）
  ├── GuardrailGrader（确定性 rubric 判定：GuardrailResult 三态 + expectedBehavior → 判定态）
  └── GuardrailTestReport（可度量、可回归：拦截率 / 漏报率 / 误报率 / per-category）
```

> 替代旧草案中的 `└── Nunjucks 式 rubric 模板（Java: TemplateRenderer）` 行。
> `TemplateRenderer` 在 nop-ai 中不存在；首版按裁定 C 采用结构化确定性判定，
> rubric 模板留 successor。

#### 裁定 A — 组件归属与模块边界

`GuardrailTestSuite` 放在 **`nop-ai-agent` main source 的 `io.nop.ai.agent.guardrail.test` 包**（非独立模块、非 `src/test`）。理由：

- 它是测试时库，需被消费方（含下游模块的测试）引用——main source 中的测试时库是既定惯例（类 JUnit support lib）；放在 `src/test` 则不能跨模块引用（test 类不进入发布产物）。
- 不污染运行时类路径语义：运行时执行路径（`AgentPromptAssembly` 的 INPUT/OUTPUT 调用点）只调 `IContentGuardrail.check()`，从不引用 `guardrail.test` 包；该包是纯验收工具。
- 与 `nop-ai-agent-eval-design.md`（草案）正交：eval 走真实 agent session + LLM-judge 测 ReAct 行为；本套件对 `IContentGuardrail.check()` 做纯函数式拦截验收（无 LLM、无 session）。两者目标不同、共存不冲突。

#### 裁定 B — AttackPlugin 契约形态（声明式语料提供者）

AttackPlugin 是**声明式语料提供者**：返回静态 `AttackCase` 列表（确定性回归）。LLM 动态生成留 successor（已在 Deferred 裁定为 non-blocking）。

`AttackCase` 数据模型字段（契约）：

| 字段 | 类型 | 语义 |
|------|------|------|
| `id` | String | 稳定用例标识（如 `pi-001`），用于断言与回归快照 |
| `category` | String | 攻击类别（如 `prompt_injection`、`role_hijack`） |
| `threatClass` | String | OWASP LLM 威胁分类标签（如 `LLM01_prompt_injection`），用于 per-category 度量 |
| `payload` | String | 攻击/对照文本本身 |
| `direction` | `GuardrailDirection` | 测试方向（`INPUT` / `OUTPUT`），默认 `INPUT`；既支持用户注入也支持 LLM 回显 |
| `expectedBehavior` | `ExpectedBehavior` | 期望：`BLOCK`（攻击应被拦截）/ `PASS`（良性应被放行） |
| `description` | String | 可选人类可读说明 |
| `transform` | String | 可选已施加变换标记（`base64` / `crescendo`），base 用例为 null |

`AttackPlugin` SPI 契约：`name()` → 插件标识；`cases()` → 该插件提供的 `AttackCase` 列表（确定性、可重复）。

#### 裁定 C — GuardrailGrader 确定性 rubric 判定（结构化，非模板）

Grader 对 `GuardrailResult`（Pass/Block/Modify 三态）+ `AttackCase.expectedBehavior` 做**确定性结构化判定**，不使用模板渲染：

| expectedBehavior | 实际 GuardrailResult | 判定 |
|------------------|----------------------|------|
| `BLOCK`（攻击） | `BlockResult` | `PASS`（正确拦截） |
| `BLOCK`（攻击） | `PassResult` | `FAIL`（漏报 leak） |
| `BLOCK`（攻击） | `ModifyResult` | `PARTIAL`（改写式拦截，半通过） |
| `PASS`（良性） | `PassResult` | `PASS`（正确放行） |
| `PASS`（良性） | `BlockResult` | `FAIL`（误报 false positive） |
| `PASS`（良性） | `ModifyResult` | `PARTIAL`（修改了良性内容） |

`GuardrailGrader` SPI 契约：`grade(AttackCase, GuardrailResult) → GradeResult`。`GradeResult` 字段：`verdict`（`PASS`/`FAIL`/`PARTIAL`）、`caseId`、`actual`（实际 GuardrailResult）、`reason`。默认实现 `DefaultGuardrailGrader` 按上表判定。rubric 模板（Nunjucks/`TemplateRenderer`）留 successor。

#### 裁定 D — GuardrailTestReport 度量维度（可度量、首版非硬门禁）

`GuardrailTestReport`（不可变结果对象）度量集：

| 度量 | 定义 |
|------|------|
| `blockRate`（拦截率） | 攻击被 Block 的比例（`BLOCK` 期望中 verdict=PASS 的比例） |
| `leakRate`（漏报率） | 攻击被放行的比例（`BLOCK` 期望中 verdict=FAIL 的比例） |
| `falsePositiveRate`（误报率） | 良性被 Block 的比例（`PASS` 期望中 verdict=FAIL 的比例） |
| per-`category` / per-`threatClass` | 分类别同上三率（用于定位哪类威胁拦截薄弱） |
| per-case `CaseResult` 详情 | 每条用例的 verdict + actual，供回归快照 |

**CI 门禁形态裁定**：首版为**可度量记录**（report 提供具体数值可断言），不内置硬 fail-fast 阈值。门禁阈值策略由消费方裁定（见 Non-Blocking Follow-ups 的 CI 门禁自动化）。

#### 裁定 E — 策略层 payload 变换（AttackCase 装饰器）

策略层（base64 / crescendo）实现为 `AttackTransform` 装饰器：`name()` → 变换名；`apply(AttackCase) → AttackCase`，产出变换后变体（新 payload、`expectedBehavior` 不变——变换不改变攻击性质 → 仍应 Block、`transform` 标记设为变换名）。变换后变体**走完整 guardrail→grader 链**（不旁路），验证 guardrail 对变换后攻击的鲁棒性。

#### 裁定 F — 语料库组织形式（外部声明式 YAML）

60+ 攻击用例以**外部声明式 YAML 语料文件**存储（`src/main/resources` 下按类别组织，可 git diff、可 Delta 定制、确定性回归）。`CorpusLoader` 用 nop 既有工具（`ResourceHelper` + `JsonTool.parseYaml`，与 `FileSystemSkillProvider` 同构）加载 YAML → `AttackCase` 列表。YAML 根为语料列表，每个元素字段对齐 `AttackCase`（裁定 B）。

#### 裁定 G — SPI 契约汇总（可直接编码）

四个组件的 I/O 契约（消除 Phase 1→Phase 2 断层，Phase 2 executor 不需自行发明 SPI 形态）：

| 组件 | 入口 | 输入 → 输出 |
|------|------|-------------|
| `AttackPlugin` | `cases()` | 无参 → `List<AttackCase>`（确定性语料） |
| `AttackTransform` | `apply(case)` | `AttackCase` → `AttackCase`（变换变体，expectedBehavior 不变） |
| `GuardrailGrader` | `grade(case, result)` | `(AttackCase, GuardrailResult)` → `GradeResult`（确定性判定） |
| `GuardrailTestSuite` | `run(...)` | `(IContentGuardrail, List<AttackPlugin>, List<AttackTransform>, AgentExecutionContext)` → `GuardrailTestReport` |
| `GuardrailTestReport` | （不可变值对象） | 度量字段见裁定 D |

**Orchestrator 数据流**（端到端）：Plugin 造攻击（+ Transform 生成变体）→ 调既有 `IContentGuardrail.check(direction, payload, ctx)` → Grader 判定 → 聚合 `GuardrailTestReport`。**无静默跳过**：guardrail 返回 null 抛错（非跳过该用例）；不调用真实 LLM / 真实 agent session（guardrail 是纯函数式检查）。

- 复用 promptfoo 60+ 攻击类型为测试语料库（声明式静态形态）
- 与运行时 guardrail 的关系：Plugin 造攻击 → nop guardrail 拦截 → Grader 判定拦截效果
- 形成"建设（运行时，已有）+ 验收（本套件）"闭环（执行已有，补验收）

### 增量 2：Guideline 依赖/排除关系图（规则关系建模）— final

> **Status: final**（W5-2，2026-08-02）。六条裁定 A–F 已固化，I/O 契约可直接编码。

nop guardrail 当前是线性检查链（规则无差别全量评估，规则间无显式关系）。增加规则间关系（对标 parlant `RelationalResolver`），使规则靠**结构**收敛而非靠 LLM 注意力或隐式 prompt 约束：

```
GuardrailRule（声明式规则定义，含关系）：
  - dependsOn: 命中 A 自动拉入 B（依赖收敛：扩展评估面）
  - excludes:  命中 A 排除 C（排除收窄：结构决策）
```

适用：企业合规复杂规则集（多规则冲突时靠 excludes/dependsOn 结构决策，而非全量评估）。

> **共存不替换**（Non-Goals）：关系图 guardrail 是新增 `IContentGuardrail` 组合实现
> （`RuleGraphGuardrail`），与既有线性链 `PromptInjectionGuardrail` 共存。既有 SPI、
> 既有 guardrail、shipped engine default（`NoOpContentGuardrail`）均不变（零回归）。

#### 裁定 A — GuardrailRule 声明式模型形态

**载体裁定**：Java 不可变配置模型 `GuardrailRule`（`io.nop.ai.agent.guardrail.rule` 包）
+ 声明式 YAML 规则集文件（经 `RuleSetLoader` 加载，`ResourceHelper` + `JsonTool.parseYaml`，
与 `CorpusLoader`/`FileSystemSkillProvider` 同构）。**首版不引入新 xdef**——纯配置模型 +
YAML 加载是 nop 常规扩展，不触及 `nop-xdefs` schema 或 codegen 模板（autonomy 约束：
非 Protected Area）。未来若需 IDE 校验可升级为 `guardrail-rules.xdef`（升级时属
`nop-xdefs` 常规扩展，仍非 Protected Area——新增 schema 不修改既有生成管线）。

**`GuardrailRule` 字段契约**（不可变值对象）：

| 字段 | 类型 | 语义 |
|------|------|------|
| `id` | `String` | 稳定规则标识（规则集内唯一），用于 dependsOn/excludes 引用与断言 |
| `direction` | `GuardrailDirection` | 规则作用方向（`INPUT`/`OUTPUT`）；`null` 表示双向均适用 |
| `pattern` | `String`（正则） | 确定性匹配条件（正则，与既有 `PromptInjectionGuardrail` 同形态）。LLM 匹配留 successor |
| `action` | `RuleAction` | 命中后结果动作：`BLOCK`（返回 `BlockResult`）/ `MODIFY`（返回 `ModifyResult`） |
| `modifyReplacement` | `String` | `action=MODIFY` 时的替换文本；`action=BLOCK` 时为 `null` |
| `dependsOn` | `List<String>` | 命中此规则时自动拉入的规则 id 列表（依赖收敛） |
| `excludes` | `List<String>` | 命中此规则时排除的规则 id 列表（排除收窄） |
| `threatClass` | `String` | 威胁分类标签（如 `LLM01_prompt_injection`），用于 Block reason 与可观测 |
| `description` | `String` | 可选人类可读说明 |

**与既有 `PromptInjectionGuardrail` 的关系**：**新增并存，不迁移**。既有 4 条硬编码正则规则
保持原样（其执行路径零改动）。将既有规则迁移为声明式 rule 是治理优化（见 Non-Blocking
Follow-ups），不阻断"关系图建模成立"。首版声明式规则集独立于既有 guardrail 存在。

#### 裁定 B — 关系语义求值模型（resolver 核心契约）

逐条裁定（消除 resolver 编码歧义）：

1. **dependsOn 是传递闭包**：A→B→C，命中 A 拉入 B **和** C。
2. **dependsOn 拉入的规则参与其自身的内容判定**（核心语义）：nop guardrail rule 是
   "匹配-判定"模型。被 dependsOn 拉入的规则 B **会**对当前 content 做自身的 pattern 匹配；
   若 B 也命中则执行 B 的 action（Block/Modify）——**扩展检测面**（如"检测到 prompt_override
   → 同时也检查更严格的 exfiltration 规则，因为 override 常伴随 exfil"）。若 B 不命中则贡献
   Pass（无效果）。resolver 输出的活跃集元素是"规则 id"——活跃集内**所有**规则都参与评估。
3. **excludes 对初始命中集成员生效**：excludes 可移除靠内容匹配命中的规则（结构收窄覆盖内容
   匹配——这是"靠结构收敛"的核心价值，如"金融交易规则 excludes 一般对话规则"，即使一般对话
   pattern 命中也被移除）。
4. **excludes 不传递**：A excludes B、B excludes C，**不**推出 A 间接 excludes C。excludes 是
   直接连边声明，仅移除直接声明的目标。
5. **被拉入的规则自身带 excludes 时级联生效**：dependsOn 拉入 B 后，B 自身的 excludes 也生效
   （因为 B 进入活跃集，B 的 excludes 被计入排除集）。
6. **dependsOn 与 excludes 冲突时 excludes 优先**（收窄胜过扩展，安全优先）。

**确定性求解算法**（保证相同命中集 → 相同活跃集，可断言）：

```
active = 初始终命中集（pattern 命中且 direction 适用的规则）
expanded = dependsOn 传递闭包（对 active 沿 dependsOn 边 BFS/DFS 单调扩展，纯加法）
excluded = ∪ { r.excludes | r ∈ expanded }   // 来自整个 expanded 集的 excludes 并集
active = expanded − excluded                   // excludes 统一在后减去 → excludes 必胜
```

三阶段（闭包 → 排除并集 → 减）保证无 fixpoint 振荡、确定终止、excludes 必胜（被 expanded
集中任一规则 excludes 的规则一律移除，不论是否被 dependsOn 拉入）。

#### 裁定 C — 环检测与 fail-loud 策略

- **dependsOn 图成环 → fail-loud**：在规则集加载/校验阶段抛异常（`NopAiAgentException`），
  拒绝加载该规则集。环检测复用底层 `io.nop.core.model.graph.dag.Dag.containsLoop()` 能力
  （经 `DagAnalyzer.analyze()` 填充 `loopEdges` 后判定）：构造 `Dag`（合成根 → 所有规则节点，
  保证 `checkStartReachable` 不抛；dependsOn 边为 `addNextNode(rule, dep)`），异常消息含
  `dag.getLoopEdges()` 给出的环边路径。**非** nop-task `GraphStepAnalyzer`（后者强绑 task 域
  `IGraphTaskStepModel`，不可直接复用于 guardrail rule 图）。
- **excludes 图不检测环**：excludes 非传递（裁定 B-4），excludes "环"（A excludes B、B excludes
  A）语义良性（仅当两者都在 expanded 集时互相移除，无死循环、无歧义），故**不**做 fail-loud。
  自环（A excludes A）也良性（A 在 expanded 时移除自身），不检测。

#### 裁定 D — 组合式 guardrail 集成形态

新增 `RuleGraphGuardrail implements IContentGuardrail`（`io.nop.ai.agent.guardrail.rule` 包），
持 `GuardrailRuleSet`（规则集合）+ `RuleGraphResolver`（关系求解）+ `RuleResultAggregator`
（结果聚合）。

`check(direction, content, ctx)` 流程：
1. 初始终命中集 = 规则集中 `direction` 适用 且 `pattern` 匹配 content 的规则。
2. `resolver.resolve(matched)` → 活跃规则集（裁定 B 算法）。
3. 对活跃集**每条**规则做 pattern 匹配：命中 → 产 Block/Modify（按 `action`）；未命中 → Pass。
4. `aggregator.aggregate(...)` → 单一 `GuardrailResult`（裁定 E）。

**零回归策略**：`RuleGraphGuardrail` 是 opt-in（经 `Builder.contentGuardrail(new
RuleGraphGuardrail(...))` 显式装配）。shipped engine default 仍 `NoOpContentGuardrail`；
`PromptInjectionGuardrail` 执行路径不变。**不装配时行为等价今日**。

#### 裁定 E — 规则评估结果聚合

多活跃规则各自产 Pass/Block/Modify 时，聚合为单一 `GuardrailResult`：

| 场景 | 聚合结果 |
|------|---------|
| 任一活跃规则命中且 action=BLOCK | `BlockResult`（reason = 所有命中-block 规则的 threatClass/reason 拼接） |
| 无 BLOCK，有命中且 action=MODIFY | `ModifyResult`（按规则集声明顺序链式应用替换：每条 Modify 作用于上一条输出） |
| 无 BLOCK、无 MODIFY（全部 Pass） | `PassResult` |

**冲突裁定**：同一 content 上既有 BLOCK 又有 MODIFY → **BLOCK 优先**（安全优先，Block 是更严
动作）。

**与既有 `GuardrailMode`（OFF/REPORT/ENFORCE）的关系**：`RuleGraphGuardrail` **复用**
`GuardrailMode` 语义（与 `PromptInjectionGuardrail` 同构），构造器接收 `GuardrailMode`
（default `ENFORCE`）：

- `OFF` → 直接返回 `PassResult`（跳过所有规则评估）。
- `REPORT` → 规则照常评估；任一命中 BLOCK 时：LOG.warn + 返回 `PassResult`（不拦截）；命中
  MODIFY 时：返回 `ModifyResult`（替换仍生效——Modify 是修复而非拦截）。
- `ENFORCE`（default）→ 按上表聚合（Block→Block，Modify→Modify）。

#### 裁定 F — 规则集配置组装与 Delta 定制

- **组装形态**：首版一个 `GuardrailRuleSet` = 一个命名规则集（`id` + `List<GuardrailRule>`），
  从单个 YAML 文件加载（`RuleSetLoader`）。多文件合并是 successor。
- **Delta 定制**：规则集 YAML 置于 `_vfs/` 下，Delta 定制经 nop 标准 VFS 分层（高优先级 VFS
  root 覆盖同路径文件）——首版为**整文件替换**。`dependsOn`/`excludes` 的字段级 Delta 合并
  （合并 vs 覆盖）是 successor。
- **autonomy 约束声明**：首版采用纯配置模型 + YAML 加载（裁定 A），不新增 xdef、不改 codegen
  模板——非 Protected Area，`implement` autonomy。若未来升级为 `guardrail-rules.xdef`，新增
  schema 仍属 `nop-xdefs` 常规扩展（非"修改既有生成管线"），保持 `implement`；仅当需修改既有
  xdef 或 codegen 模板时才升级为 plan-first Protected Area。

#### SPI 契约汇总（可直接编码）

| 组件 | 入口 | 输入 → 输出 |
|------|------|-------------|
| `GuardrailRule` | （不可变值对象） | 字段见裁定 A |
| `GuardrailRuleSet` | （不可变值对象） | `id` + `List<GuardrailRule>` + 校验（id 唯一 + dependsOn/excludes 引用合法 + dependsOn 无环） |
| `RuleGraphResolver` | `resolve(matchedRuleIds)` | `Set<String>`（初始终命中集）→ `Set<String>`（活跃集，裁定 B 算法，确定性） |
| `RuleResultAggregator` | `aggregate(...)` | 活跃规则 + content → 单一 `GuardrailResult`（裁定 E，Block 优先 + Modify 链式） |
| `RuleSetLoader` | `load(resource)` | YAML resource → `GuardrailRuleSet`（`ResourceHelper` + `JsonTool.parseYaml`） |
| `RuleGraphGuardrail` | `check(direction, content, ctx)` | `IContentGuardrail` 实现，经 resolver + aggregator 驱动（裁定 D） |

**端到端数据流**：规则集 YAML → `RuleSetLoader` → `GuardrailRuleSet`（加载期校验：id 唯一 +
dependsOn/excludes 引用合法 + dependsOn 无环 fail-loud）→ `RuleGraphGuardrail.check()` →
初始终命中集 → `RuleGraphResolver.resolve()` → 活跃集 → 逐规则评估 → `RuleResultAggregator`
→ 单一 `GuardrailResult`。**无静默跳过**（Minimum Rules #24）：dependsOn 成环抛异常非返回空集；
resolver 冲突按裁定 B-6 显式 excludes-wins 处理。

### 增量 3：BAIL 中断语义（硬阻断）— final

> **Status: final**（W5-3 已落地，2026-08-02）。详细架构决策见 `nop-ai-agent-middleware-design.md` §5.4。

nop 拦截是"改写/拒绝"，增加 BAIL 语义（对标 parlant hooks.py + grok-build GateKind Stop）：

- `BailResult`（`HookResult` 第四态）：中断并**不作用**于当前响应。仅在 `POST_REASONING` / `POST_CALL` 两个 POST 点有效。
- 与 nop 现有 middleware 的关系：作为 middleware 链可返回的硬阻断决策态。区别于：
  - `VetoResult`（PRE 侧拒绝、core 不执行）——POST 侧 core 已执行，Veto 语义不适用
  - `GuardrailResult.ModifyResult`（改写式拦截、修改响应内容）——改写是放行，BAIL 是不作用
  - `promptAssembly.checkOutputGuardrail`（硬编码丢弃、仅 promptAssembly 可触发）——BAIL 是**通用机制**（任意中间件可返回），`checkOutputGuardrail` 作为内置快速路径共存（§5.4 裁定 A）

#### POST_REASONING vs POST_CALL BAIL 语义

| 触发点 | 语义 | 效果 |
|--------|------|------|
| `POST_REASONING` | 不作用于此轮响应 + 重新提示 | 跳过该轮 tool_calls 执行 + 不视为最终答案 + `continue`（消耗 iteration）+ per-request bail cap（超限 fail-loud） |
| `POST_CALL` | 结果阻断状态 | `AgentExecutionResult.bailReason` 标记阻断；流式已发 chunk 不可撤回（显式限制）；`EXECUTION_COMPLETED` payload 加 `guardrailBlocked` 标记 |

消息/checkpoint/session-store 在 POST_REASONING 前已落账——BAIL 是"不作用 + 重新提示"，非"未发生"（与 `checkOutputGuardrail` 同边界）。

### 与 hook-skill-engine 的边界

- guardrail-contract：规则定义 + 测试闭环 + 关系建模（本篇）
- hook-skill-engine：12 生命周期点 + skill 加载（已存在）
- 关系：BAIL 由 hook 生命周期点触发，规则评估在 security 层
