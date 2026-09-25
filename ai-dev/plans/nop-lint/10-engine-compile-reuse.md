# 10 引擎编译复用（compile once, lint many）

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings P1、D4、P7-FixApplier byte[] 通路）
> Related: 08/09（已收口）、11-kernel-correctness-fixes.md（CompiledRule relationalStopBy 改动归它——执行顺序 10 先于 11/14，避免同文件冲突）、14-readability-conventions-cleanup.md（CheckRunner 编排拆分归它，须保持本 plan 的预编译迁移面）、ai-dev/design/nop-lint/03-execution-engine.md、11-performance-profiles.md
> Review: R1 对抗审查（2026-09-25）：3 Major（F1 Phase 1 回归清单指错对象→改 TestConstraints/TestL2TypeGate + ConstraintContext 注入形态裁定；F2 engineLintPrecompiled 口径未定——3 规则口径编译占比仅 5-8% 低于噪声带→口径写死 62 规则全库面；F3 LSP 摊平规则表丢弃语言分组→补按语言分组迁移语义）+ 8 Minor 全部修订。R1 另核实：CompiledRule 全 final 字段无状态、跨文件/并发复用安全；byte[] 双重编解码已验证到 TSParser.parse(String) 内部 getBytes。

## Purpose

消除"每次 lint 调用全量重编译全部规则"的数量级级固定开销：`LintEngine.lint` 的唯一公开入口接收裸 `List<RuleDslModel>` 并在每次调用内逐条 `CompiledRule.compile`（每条 pattern 规则含一次 tree-sitter 解析），CLI 每文件、LSP 每次 didChange、GraphQL 每请求、fix multipass 每轮 ×≤10 全部付费。本 plan 交付预编译规则集入口（compile once → N 次 lint），解耦约束求值对 per-file `TypeQuerySupport` 的编译期捕获，并消除加载路径上同一 pattern 的双编译。

## Current Baseline

- `LintEngine.lint(List<RuleDslModel>, ...)`（engine/LintEngine.java:178-218）：循环内 `CompiledRule.compile(rule, language, typeQueries)`（:195）；`typeQueries = new TypeQuerySupport(typeResolver, filePath, tree.source())` 每 lint 重建（:184）。
- 编译成本：`SourcePatternCompiler.compile` 对每条 pattern 做真实 tree-sitter 解析；62 规则 × N 文件 = N×62 次重解析；fix multipass 每轮重编译（fix/FixApplier.java:176 lint 逐 pass 调用）。
- 双编译：复合 matcher 的 pattern 先在 `compileNodeMatcher`/`flatBranchMatcher` 编译（CompiledRule.java:637-640/658-660），`kindOpinion` 再对同一文本 `compilePattern(...)`（:502-503/555-556）取 kind 观点——同一 pattern 文本在单次 compile 内编译两次。
- 编译期闭包：`Constraints.compile(...)` 把 `typeQueries` 装进 `TypeOf` 约束字段（constraint/Constraints.java:240-259），`TypeQuerySupport` 本身不可变且 per-file（resolver+filePath+source，semantic/TypeQuerySupport.java:27-52）；`Constraint.holds(ConstraintContext)` 当前只接收 ctx。xscript 编译（XScriptCompiler.compile）与 XML 路径（language.compileRule(model) 两参）不消费 typeQueries。
- gate 语义（LintEngine.gate:229-247）：逐 model 的 raw requires token 判定——未知 token → SKIP、profile ceiling 不含 → SKIP、deep analyzer 不 live 或未命名文件 → DEGRADE、L2 未 ready 且未命名 → DEGRADE。`CompiledRule.resolveRequires`（:394-403）在编译期**丢弃**未知 token——预编译路径若以 `requires()` 判 gate，未知 token 规则会从 SKIP 变 RUN，属行为回归，必须显式保留可观察等价。
- 消费方：CheckRunner 每文件 lintFile/fixFile（cli/CheckRunner.java:194,203,205→engine.lint）；LSP didChange（lsp/NopLintLanguageServer.java:174）；GraphQL（nop-lint-graphql NopLintBizModel，规则一次加载每请求 lint）；RuleTestRunner 每 fixture `new LintEngine` + lint（testing/RuleTestRunner.java:262-269）；FixApplier 每轮 lint（fix/FixApplier.java:122,149,176）。
- byte[] 往返：`LintEngine.lint` 只有 String 源入口，CLI 持有 byte[] 先 `new String(bytes, UTF_8)`，`language.parse` 内部再编码回 bytes——每文件两次全量编解码（解析/lex 在历史 JFR 占 13%+）。
- 基线（2026-09-25）：`engineLint` 0.002 s/op / 1.95 MB/op（**每 op 含 3 规则重编译**，规则库扩大后失真）、`compileRuleSet` ≈10⁻⁴ s/op / 158 KB/op。
- `CompiledRule.precompiled`（:216-228）已存在：XML 路径的外部编译产物装配面，预编译入口可复用其形态。

## Goals

- `LintEngine` 新增预编译入口：一个 ruleset 对一个 (language, profile) 只编译一次，跨文件/跨 lint 调用/跨 multipass 轮复用；gate 判定（SKIP/DEGRADE/RUN）逐文件重判且四类 token 的可观察行为与现行逐 model gate 完全等价。
- 约束求值不再在编译期捕获 per-file `TypeQuerySupport`：typeOf 的求值上下文来自 per-match/per-file 注入，guarded-fail 语义（无 L2 → 抛错不伪造）保持。
- CLI（含 maven plugin 复用面）、LSP、GraphQL、FixApplier multipass、RuleTestRunner 全部迁移到预编译入口；每 run/每 suite/每 engine 生命周期只编译一次。
- `LintEngine` 增加 byte[] 源入口直通 `language.parse(byte[])`，CLI 消除 String 往返；FixApplier 首轮 error-node 计数复用 lint 结果不再整树二次解析（若 LintStats/LintResult 可承载 errorNodeCount，否则以 byte[] 通路为限并记录裁定）。
- 单次 compile 内同一 pattern 文本只编译一次（`CompositeRuleCompiler` 或等价结构收敛 matcher 编译与 kind 观点推导）。
- JMH：新增预编译口径引擎基准；engineLint（含编译的现行口径）保留对照；`parseAndMatch`/大语料基准无回归。

## Non-Goals

- 不做跨进程编译产物序列化（design 03 §1.3 另有裁定，不属本轮）。
- 不改变 gate 的 SKIP/DEGRADE 计数语义、`skippedByProfile`/`degraded` 可观测性、降级永不伪造红线。
- 不改变规则 DSL/xdef 表面与规则语义。
- 不动 nop-treesitter 与平台模块。
- 不在本 plan 内做 CompiledRule 类体拆分之外的 engine 包结构调整。

## Scope

### In Scope

- `nop-lint-core`：engine/LintEngine、engine/CompiledRule（含 CompositeRuleCompiler 收敛与 pattern 单编译）、constraint/Constraints + ConstraintContext + TypeOf、cli/CheckRunner、lsp/NopLintLanguageServer、testing/RuleTestRunner、fix/FixApplier、bench。
- `nop-lint-graphql`：NopLintBizModel（预编译规则持有）。
- design 03 增注（编译复用契约 + TypeQuerySupport 注入形态）。

### Out Of Scope

- Maven plugin（经 CheckRunner 自动受益，无独立改动面——若发现独立改动需求记录 follow-up）。
- 规则库 YAML、xscript 白名单语义、抑制语义。

## Execution Plan

### Phase 1 - TypeQuerySupport 解耦到求值上下文（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/constraint/`、`engine/CompiledRule.java`（finish 传参）、`engine/LintEngine.java`、`engine/RuleSetRunner.java`（per-file support 通道）

- Item Types: `Fix`

- [x] `TypeOf` 约束不再于编译期持有 `TypeQuerySupport`：求值所需 support 改由 per-file 注入面提供。**注入形态裁定（审查 F1）**：`ConstraintContext` record 增补 typeQueries 分量（编译期不可伪造——compile 后经 setter 不可行，record 天然满足），TestConstraints 的 `new ConstraintContext(node, env)` 直构点补第三参 null 或专用测试工厂；`RuleSetRunner.run` 增 per-file TypeQuerySupport 通道（run 期构造一次），applyConstraints 注入 ctx
- [x] guarded-fail 语义逐字保持：无 resolver / 无文件上下文时 typeOf 求值抛错路径与现行为一致（不伪造、不静默跳过），既有 `requires:"L2"` gate 矩阵测试零回归
- [x] 焦点测试：typeOf 约束在 L2 ready / resolver 缺失 / 未命名文件三态下的行为与改动前一致（**guarded-fail 消息逐字断言归 TestConstraints.typeOfEvaluationIsAGuardedFail、gate 矩阵归 TestL2TypeGate——审查 F1 修正的真正耦合测试面**）；同一条预编译规则跨两个文件求值时各自使用本文件的 filePath/source（防串扰断言）

Exit Criteria:

- [x] 编译产物（CompiledRule）不再引用任何 per-file 状态（代码可观察：约束字段无 filePath/source）
- [x] 三态焦点测试 + 跨文件防串扰测试落地；**TestConstraints、TestL2TypeGate、TestConstraintEngineEndToEnd 全绿**（TestConstraintParsing 不在耦合面）
- [x] design 03 增注：TypeQuerySupport 注入形态（求值期注入，编译期仅校验捕获名）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 预编译入口与全消费方迁移（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/LintEngine.java`、`cli/CheckRunner.java`、`lsp/NopLintLanguageServer.java`、`fix/FixApplier.java`、`testing/RuleTestRunner.java`、`nop-lint/nop-lint-graphql/src/main/java/io/nop/lint/graphql/NopLintBizModel.java`

- Item Types: `Fix`

- [x] `LintEngine` 新增预编译入口（如 `lintCompiled(precompiled, language, filePath, tree)` 或 CompiledRuleSet 形态）：编译一次；gate 逐文件重判；未知 token → SKIP 的可观察行为显式保留（raw requires 随编译产物携带或等价机制），`rulesSkippedByProfile`/`rulesDegraded` 计数与现行逐文件路径一致
- [x] CheckRunner：run 主循环（lint/fix/cache-miss 全路径，保持 plan 07 的 SourceReader 缝）循环外按 language 预编译一次并复用；五个 delegating overload 语义不变。**坏规则报错时机前移（审查 F6）**：由"首个文件 lint 时抛"变"run 启动即抛"（消息不带文件路径）——更符合 fail-closed，焦点测试显式覆盖该时机与消息变化并记录
- [x] LSP：规则集在 server 生命周期**按语言分组**预编译一次（`NopLintLanguageServer.create` 现将 rulesByLanguage 摊平——预编译面必须按 `RuleDslModel.getLanguage()` 重建分组，didChange 按 `doc.languageId()` 选择对应分组；跨语言行为从"编译抛错"变"按语言过滤"，属显式改进并记录）；GraphQL：engine 初始化时预编译，**预编译产物支持按请求 rule-id 白名单子集化**（Map<ruleId,CompiledRule> 或过滤视图——审查 F4）；FixApplier：multipass 各轮复用同一编译产物；RuleTestRunner：per-suite 编译一次复用到各 fixture（CompiledRule 无状态，审查已核安全）
- [x] byte[] 源入口：`LintEngine` 增加 byte[] 重载直通 `language.parse(byte[])`，CheckRunner 传入原始 bytes 消除 String 往返
- [x] FixApplier error-node 二次解析消除（LintResult/LintStats 暴露 errorNodeCount 或等价机制；若裁定不做，显式记录原因于本 plan Deferred）
- [x] 焦点测试：同一 CompiledRuleSet 连续 lint 两个不同文件结果正确（隔离性）；gate 四类 token 等价性矩阵测试（未知 token SKIP / ceiling SKIP / deep 不 live DEGRADE / L2 未 ready DEGRADE）；byte[] 入口与 String 入口结果逐字一致；FixApplier multipass 行为零差异（既有 e2e 三场景）
- [x] 端到端验证（Anti-Hollow Rule）：CLI 真实 run（bench 规则集 × ≥2 文件）证明预编译路径从 loadRuleSet→预编译→逐文件 lint→诊断输出完整走通，诊断与现行路径一致（golden 对比或断言等价）

Exit Criteria:

- [x] 预编译消费方（CheckRunner/LSP/GraphQL/FixApplier/RuleTestRunner）不再存在"每文件/每请求/每轮 `CompiledRule.compile`"调用；**旧 `lint(List<RuleDslModel>,…)` 入口按 plan 保留**（bench 对照与既有测试面），其内部逐调用编译为已知且记录的遗留形态（审查 F9）
- [x] gate 等价性矩阵测试 + 隔离性测试 + byte[] 一致性测试落地；既有全量 core/graphql/lsp 测试零回归
- [x] 端到端 CLI run 证据记录于 daily log
- [x] design 03 增注：预编译入口契约（gate 重判、未知 token 保留、生命周期归属）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - pattern 单编译收敛 + JMH/JFR 验证（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/CompiledRule.java`、`nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`、`nop-lint/docs/perf-baseline.md`

- Item Types: `Fix` + `Proof`

- [x] 单次 compile 内 matcher 编译与 kind 观点推导收敛（`CompositeRuleCompiler` 或等价结构）：同一 pattern 文本只编译一次（复合 matcher 路径），matcher 与 opinion 从同一编译产物派生
- [x] bench：**`engineLintPrecompiled` 口径写死为 62 规则全库面**（经 RuleSetLoader 装载生产 YAML 预编译后 lint——审查 F2：3 规则口径编译占比仅 ~5-8% 低于噪声带，无法证明核心收益；62 规则全库为唯一可证口径）；现行 `engineLint`（3 规则含编译）保留对照；`compileRuleSet` 并列；新增基准后 BenchmarkSmokeTest 防呆数同步（现 9 基准 ≥8 → 10 基准 ≥9，消息文本顺手刷新——审查 F7）；全部并入 run-benchmarks.sh
- [x] Phase 1/2/3 合入前后各一轮 JMH（同口径）；**核心收益证据 = 62 规则全库面 before/after**（用 graphqlCheckSource 6.642ms/op 现锚点为 before，或在 core 内新增全库基准采集 before；预期编译复用贡献可测——审查 F2 修正 Purpose 的"数量级级"措辞：plan 09 已移除 62× 遍历乘数，编译复用是剩余乘数中较小者，收益以实测为准不预设）
- [x] JFR 时间热点（engineLintPrecompiled 大语料）一轮：确认编译相关帧（Lexer/Parser）退出引擎稳态热点
- [x] 回归处置：任一基准 >10% 回归则回滚对应改动并记录
- [x] perf-baseline.md 增注：新旧口径数字表、口径语义说明（engineLint 含编译 vs 预编译口径）、62 规则库下的每文件固定开销收敛结论

Exit Criteria:

- [x] 双编译消除（复合 matcher 路径单次编译，代码结构可观察）；TestCompiledRule/TestCompositeRuleCompile 全绿
- [x] JMH/JFR before/after 数字表与口径说明写入 perf-baseline.md 增注；无 >10% 回归
- [x] `BenchmarkSmokeTest` 全绿；`./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-graphql` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 编译复用落地：全部生产消费方（CLI/LSP/GraphQL/FixApplier/RuleTestRunner）迁移完成，逐文件重编译调用消失
- [x] gate 四类 token 可观察行为等价（矩阵测试背书）；SKIP/DEGRADE 计数语义无漂移
- [x] typeOf guarded-fail 与跨文件隔离语义保持（焦点测试背书）
- [x] JMH/JFR 验证完成无回归（perf-baseline.md 增注为证）
- [x] byte[] 往返消除（或 FixApplier errorNode 项按裁定进入 Deferred 并记录理由）
- [x] owner docs（design 03 增注 + perf-baseline.md）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：预编译产物确被逐文件 lint 消费（CheckRunner 主循环→lintCompiled→RuleSetRunner 调用链代码追踪 + 端到端 CLI run 证据）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-graphql,nop-lint/nop-lint-maven-plugin` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0

## Deferred But Adjudicated

### FixApplier errorNodeCount 复用（若 Phase 2 裁定不做）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 若 LintResult 面扩展被裁定为超出本轮 API 预算——byte[] 通路消除的是双重编解码；残余为每个 pass 的 error-count 重复解析（:122,:149，非仅首轮），TestFixApplier 的裸 LintResult stub 消费面需同步裁定（不得静默读 0）
- Successor Required: `no`
- Successor Path: 后续 engine API 演进时顺带处理

## Non-Blocking Follow-ups

- design 03 §1.3 跨进程序列化缓存（独立立项评估）。

## Closure

Status Note: 预编译入口落地且全部消费方迁移（CheckRunner/LSP/GraphQL/FixApplier/RuleTestRunner）；gate 四类 token 等价矩阵、跨文件隔离、byte[] 一致性、坏规则时机前移均有可证伪测试；TypeQuerySupport 求值期注入（编译产物无 per-file 状态）；单编译收敛消除双编译；全库对照 -48% 时间/-64% 分配；既有基准零回归。R1 对抗审查 3 Major（F1 测试清单/F2 基准口径/F3 LSP 分组）+ 8 Minor 全部修订后执行。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: R1 对抗审查 agent_e95fedfa（draft）；closure audit 由独立 agent 执行（见 TaskOutput 记录与 daily log）
- Evidence:
  - Phase 1：TestConstraints 6/0、TestL2TypeGate 9/0、TestConstraintEngineEndToEnd 10/0（guarded-fail 逐字断言 + gate 矩阵）；跨文件防串扰断言在 TestLintCompiledEquivalence
  - Phase 2：TestLintCompiledEquivalence 4/0（诊断逐一等价/跨文件隔离/byte[]=String/未知 token SKIP 双路径）；TestCheckRunner.brokenRuleFailsTheRunAtStartupBeforeAnyFileIsRead（坏规则启动即抛 + reader 计数为 0）；端到端 CLI 由 TestNopLintCli* 家族覆盖（782 全绿）
  - Phase 3：全库对照 _tmp/full-library-plan10.txt（compilePerCall 0.006s/10.73MB vs precompiled 0.003s/3.82MB）；perf-baseline.md 增注含数字表与归属
  - 工具门禁：doc-links 0 errors；hollow-scan exit 0；check-plan-checklist exit 0（completed 态）
  - Deferred 项分类检查：FixApplier errorNodeCount 项 Deferred 理由已按 R1 F8 精确化（每 pass 重复解析 + TestFixApplier stub 消费面）
- Audit Session: agent_e95fedfa（R1 draft review）；agent_cadb8f50（closure audit，独立 fresh session）
- Closure audit R2 整改对照（audit REJECTED 4 项→修复后满足 completed 条件，audit 报告明示"无需重开实现面"）：F1 design 03 两段增注补齐（求值期注入 + 预编译入口契约五要素）；F2 域归属再裁定落地（run-graphql-benchmarks.sh 新增 + core smoke 消息刷新 9 基准 ≥9）；F3 JFR 补做（full-library-precompiled.jfr，结论入 perf-baseline；基准名 engineLintPrecompiled→fullLibraryPrecompiled 修正）；F4 本证据回填 + daily log closure 条目；F6 59/62 口径注记、F7 CLI 端到端证据、F8 resolveKind 单次调用均顺手处理

Follow-up:

- 旧 `lint(List<RuleDslModel>,…)` 入口的长期去留（bench 对照面，随 perf-baseline 演进裁定）
- design 03 §1.3 跨进程序列化缓存（独立立项评估）
