# Deep Profile + Degrade Ladder v2（roadmap item 31）

> Plan Status: completed
> Last Reviewed: 2026-09-24
> Source: ai-dev/design/nop-lint/11-performance-profiles.md §2/§5/§8（Phase 3）、06-pmd-errorprone-alignment.md §4.6、07-xscript-engine.md §3、03-execution-engine.md §1.3
> Related: roadmap ai-dev/backlog/nop-lint-roadmap.md item 31（deps: 30 done）；前序 plan 2026-09-22-0854-1（tsc bridge 的 L2 门控先例）、2026-09-24-0500-1（L2 Java resolver）
> Review: R1 对抗性审查 agent_f7f4ae3c（1 Blocker + 7 Major + 5 Minor，全部已修）；R2 增量复核 agent_f7f4ae3c（裁定"可以直接执行"，3 个 Minor A/B/C 已补入 Decision 2/8 与 Goal 5/8）

## Purpose

把 design 11 §8 Phase 3 落地：`deep` 执行档位（数据流/常量传播/Scope/Metrics/L3/L4 能力上限声明 + 5s 单文件软预算）、降级阶梯 v2（按序关闭分析器、每次降级可观测、pattern/kind/约束不因阶梯关闭）与 pattern 阶段预算熔断（最后防线）。deep 档在本 plan 只交付**档位 + 门控 + 预算 + 阶梯 + 熔断机制**；具体 deep 分析器（Metrics/Scope/L3/L4 语义面）由 items 32/33/34 各自落地，dataflow 规则由 item 36 落地——本 plan 为它们预留**可插拔的可用性接口**，并用测试替身证明阶梯全链路连通。

## Current Baseline

- `LintProfile`（nop-lint-core engine 包）只有 `FAST`/`STANDARD` 两档；能力上限 FAST={L1}、STANDARD={L1,L2}；per-match xscript 预算缩放已实现（fast=min(20ms, rule)，standard=rule 声明值，默认 100ms、上限 1000ms，`RuleDslParser.DEFAULT_XSCRIPT_TIMEOUT_MS=100`）。
- `LintCapability` 枚举只有 `L1`、`L2`；`byToken` 大小写不敏感；未知 token 门控即 SKIP（fail-closed，`LintEngine.gate`）。
- L2 门控先例（item 20/26）：RUN/SKIP/DEGRADE 三出口；DEGRADE = 档位上限含 L2 但运行时不可服务（无 resolver / 探测失败 / 未具名文件）；规则级降级计数 `LintStats.rulesDegraded`/`degradedRuleIds`；求值期类型查询失败（`TypeResolutionException`）在 `RuleSetRunner` 同归降级。降级规则不产出诊断、绝不用 L1 顶替。**gate 只在 `lint` 入口执行一次**（先全量 gate 再进 runner），`CompiledRule` 不携带 `requires`。
- **不存在**任何单文件软预算机制：design 11 §2 的 per-file 预算（fast 20ms / standard 500ms / deep 5s）、§5 "fast 档预算闭合"的 xscript 文件时间片（默认 10ms/文件、`xscriptBudgetExceeded` 计数）均未落地；§5 "pattern 匹配自身的防线"之运行期熔断（pattern 阶段超预算中止该文件剩余规则）也未落地。时钟硬编码（`XScriptDeadline.startNow` 直调 `System.nanoTime()`），无注入缝。
- **不存在**降级阶梯：无分析器级关闭顺序、无分析器级降级记录（现仅有规则级 `degradedRuleIds`）；`RunSummary`/`ConsoleReporter` 也不渲染规则级降级（item 20 遗留缺口）。
- **live 与 design 11 §5 漂移**：`RuleSetRunner` 对**所有档位**都生成模板 fix（FAST 也生成）；design 11 §5 规定"fix 生成……fast 本就不开"。
- xscript 诊断只来自脚本内 report（脚本不 report 则无诊断）；规则静态 message 只用于非 xscript 规则的诊断。
- item 30 产物 `DataFlowAnalyzer`/`DefUseChain`/`ConstantPropagation` 位于 `nop-lint-java` semantic 包，**未接引擎**（无对应 capability、无规则可声明 dataflow 依赖）；Scope/Metrics 分析器尚不存在（items 32/33）。
- `TypeQuerySupport` 是约束期类型查询唯一通道（`isAssignableTo`），失败抛 `TypeResolutionException`/`NopLintException`；`XScriptDeadline` 要求 budgetMs ≥ 1（≤0 构造抛 `NopLintException`）。
- `lint-rule.xdef` 的 `requires` 为 csv-set 自由词表、`RuleDslParser` 无 token 白名单（仅 typeOf⇢精确 `"L2"` 门）——新 capability token 无需 xdef/parser 改动。
- CLI `--profile fast|standard`（`CliOptions.parseProfile`）；`RunSummary` 聚合 + `ConsoleReporter` 渲染关键计数；`LintResult(diagnostics, stats)` 不可变。`CheckRunner.fixFile` 的 fix 多 pass 会对同一物理文件做最多 ~12 次独立 `engine.lint` 调用（原始 1 + FixApplier ≤10 + 报告 1）。
- 现有 JMH 三基准（compileRuleSet/matchAllPatterns/parseAndMatch）全部工作在 pattern 层，**不经过 `LintEngine.lint`/`RuleSetRunner.run`**（`LintBenchmarks.java`）；引擎层无基准。
- 既有测试直接调用 `RuleSetRunner.run(rules, tree, stats, profile)`（TestCompositeRuleCompile/TestFixEngineWiring/TestRuleSetRunner 等 ~15 处）；TestLintEngine 的 per-profile 循环用 `LintProfile.values()`（加入 DEEP 后自动扩展）。
- JMH 基线（`nop-lint/docs/perf-baseline.md`，item 13）：parseAndMatch ≈1ms/op；JFR 热点 93% 在 nop-treesitter cursor；`bash nop-lint/bench/run-benchmarks.sh` 可复现。

## Goals

1. `LintProfile.DEEP`：能力上限 = {L1, L2, L3, L4, SCOPE, METRICS}（design 06 §4.6 L3=数据流/L4=语义；design 11 §2 deep 行）；单文件软预算 5s；xscript per-match 预算与 standard 同口径（规则声明值，默认 100ms）。
2. `LintCapability` 扩展 `L3`/`L4`/`SCOPE`/`METRICS` 四个 token；门控语义与现有完全同构（上限外 SKIP，上限内不可服务 DEGRADE，fail-closed）。
3. deep 分析器**可用性接口**（最小契约见 Decision 6）：按 capability 注册 provider、廉价探测（不启动后端，同 `TypeResolver.isAvailable()` 契约）、无 provider/探测失败一律 DEGRADE（fail-closed）；注入方式与 TypeResolver 同构（run 装配侧），items 32/33/34 无需引擎手术即可接入。
4. 单文件软预算 + 降级阶梯 v2（design 11 §5）：三档预算 fast 20ms / standard 500ms / deep 5s；总钟耗尽按序关闭（1）L3/L4/SCOPE/METRICS（2）L2（3）xscript deadline 收紧至 min(20ms, 规则值)（4）fix 生成；**阶梯永不关闭 pattern/kind/约束**；每次关闭记入 LintStats 分析器级降级列表（保序），受影响规则逐条走既有 DEGRADE 出口。
5. pattern 阶段预算熔断（design 11 §5 "运行期软预算"，Blocker 1 修复）：pattern 匹配单独计时，累计超过单文件预算时中止该文件剩余规则，逐条计数并记录 id，文件标记 degraded——最后防线，与阶梯不矛盾（design §5 原文裁定）。**优先级裁定（R2 Minor A）**：规则边界上总钟与 pattern 钟同时超预算时**熔断胜出**（最后防线语义：中止优先于降级继续）；Phase 2 测试须覆盖该重叠情形的预期。
6. fast 档预算闭合（design 11 §5）：xscript 文件时间片默认 10ms（所有 match 共享）；per-match deadline = min(20ms, 规则值, 时间片剩余)；时间片耗尽（剩余 <1ms）后剩余 match 不再执行 xscript，逐条计数 `xscriptBudgetExceeded`、规则 id 记入专列（"标记 degraded" 的落地面），match 仍输出 pattern 层诊断（静态 message/severity、match 节点 range）。
7. fix 生成按档位门控（对齐 design 11 §5）：FAST 档不生成 fix（诊断照常、无 Fix 载体）；standard/deep 在阶梯关闭后停止生成，被跳过的 fix 生成逐条计数（不静默）。
8. 统计与报告面：`LintStats` 新增 `xscriptBudgetExceeded` 计数 + 规则 id 专列、分析器级降级列表（关闭顺序）、fix 降级跳过计数、熔断中止规则 id 列表（**该 id 列表非空即文件 degraded 标记**，R2 Minor B：不另设布尔；`RunSummary` 增 `filesDegraded` 文件计数承载运行级视图）；`LintStats` 类 javadoc 的规则出口守恒契约同步补列两个新出口（规则边界降级、熔断中止）；`RunSummary`/`ConsoleReporter` 渲染以上各面**并补上既有 `rulesDegraded`/`degradedRuleIds` 的渲染**（item 20 缺口随本 plan 闭合）；CLI `--profile deep` 可用。
9. 性能守门：新增**引擎级** JMH 基准（经 `LintEngine.lint` 全管线），改动前先测 before 基线、改动后复测对照，不回归；预算/阶梯时钟检查 O(1)、只在规则边界与 xscript/fix 决策点（pattern/kind/约束路径零新增系统调用）；结果记入 `nop-lint/docs/perf-baseline.md` 增注；如有回归用 JFR 定位并消除。

## Non-Goals

- 不实现 MetricsEvaluator（item 32）、Scope 分析器（item 33）、L3/L4 语义分析面（item 34）、任何 dataflow 规则（item 36）——本 plan 只留接口与门控。
- 不改 nop-treesitter（硬约束）；不做 matcher 内核优化（perf-baseline 已列 optimization candidate，另行立项）。
- 不做 CI 缓存工件化 / 编辑器 watch / GraphQL fast 调优（items 41/43）。
- 不改 xscript 编译白名单、deadline route A 机制本身（只在其上叠加预算与收紧）。
- 不引入配置面（预算值本期为 profile 常量，配置化留给消费方证据出现后）。
- 不给 CLI 接 deep provider/resolver：CLI `--profile deep` 下 L2+/deep 规则按设计降级（与 item 20/26 先例一致），机制交付性质。

## Scope

### In Scope

- `nop-lint-core` engine/xscript/cli 包：`LintProfile`、`LintCapability`、`LintEngine` 门控、`CompiledRule`（携带 requires）、`RuleSetRunner` 预算/阶梯/熔断集成、`LintStats`、单文件预算组件（含测试时钟注入缝，命名实现期定）、deep 分析器可用性接口、`CliOptions`/`RunSummary`/`ConsoleReporter`。
- 单元/端到端测试（core 模块）：门控矩阵、预算耗尽、阶梯顺序、熔断、fast 时间片、fix 门控、报告渲染。
- JMH：新增引擎级基准 + before/after 对照；`nop-lint/docs/perf-baseline.md` 增注。
- 文档：design 11 增注（live 口径）、roadmap item 31 状态、daily log。

### Out Of Scope

- `nop-lint-java`/`nop-lint-js`/`nop-lint-nop` 产品代码（deep 分析器与规则在后续 item 落地；本 plan 不动它们；若现有测试因 fix 门控需调整 profile，属机械适配）。

## Key Decisions（执行前裁定，R1 审查确认）

1. **capability 词表**（R1 裁定 d 通过）：采用 design 06 §4.6 的 `L3`（数据流=DefUseChain+常量传播）/`L4`（语义分析）+ 独立 `SCOPE`/`METRICS` token。design 11 §1 示例的小写 `dataflow/tsc` 等 token 不引入（`tsc` 属 L2 的后端实现细节，规则只声明 L2；`dataflow` 与 L3 同物不双名）。`byToken` 大小写不敏感，规则写 `requires: L3` 即声明数据流依赖。xdef/parser 零改动。
2. **阶梯触发语义**（R1 Minor 9 增注）：单一耗尽点 + 单一预算值——总钟（整个 lint 过程）耗尽时阶梯**有序全闭合**（1→4）；"逐级"在单一耗尽点下退化为"有序闭合 + 有序记录"，行为差异只体现在记录顺序（无中间阈值，引入即发明）。闭合后剩余规则按闭合后的能力面继续执行：match/约束照常（floor）、capability 交集中的未执行规则在规则边界降级（Decision 5）、xscript 以收紧 deadline 跑、fix 不再生成。
3. **fast 时间片**（R1 Major 7 修复后口径）：时间片耗尽的判定边界 = 剩余 <1ms（XScriptDeadline 下限，不构造亚毫秒 deadline）；耗尽后剩余 match 跳过 xscript 执行，match 仍输出 pattern 层诊断——用规则静态 message/severity、match 节点 range（与非 xscript 规则同形态）。**已知代价**：对"脚本才确认/report"的 xscript 规则，这是系统性假阳性且数量随片大小波动——接受该 trade-off（design 11 §5 原文"这些 match 仍输出 pattern 层结果"字面执行），缓解 = 可观测："标记 degraded"落地为逐 match `xscriptBudgetExceeded` 计数 + 涉及规则 id 专列（**不复用** L2 语义的 `degradedRuleIds`，避免与"降级规则不产出诊断"契约冲突），并在 design 11 增注中记录该裁定。
4. **fix 门控漂移修复**（R1 裁定 c 通过，归类 Fix）：FAST 档从"生成 fix"改为"不生成"（design 11 §2 fast 行与 §5 第 5 级本就如此，live 漂移随本 plan 修正）；`--fix`/`--fix-dry-run` 在 fast 档下可运行但无 fix 可应用（applied=0，报告与退出码描述残差，行为自洽；现有测试无 FAST+fix 断言，零破坏）。standard/deep 维持生成直到阶梯关闭。
5. **闭合后的规则级降级 = 规则边界重判**（R1 Major 3 修复后口径）：闭合只发生在规则边界；`CompiledRule` 增加 `requires` 携带（Phase 2 Target），runner 循环内对未执行规则重判——其 requires 与已关闭 capability 相交即走既有 DEGRADE 出口（计数 + 日志 + id）。原"执行中规则经 TypeResolutionException"分句废除：约束期 `TypeResolutionException` 仅保留原生语义（resolver 真实失败），不存在中途闭合场景。
6. **阶梯形态与可用性接口契约**（R1 Minor 8 + Major 2 修复）：design §5 五级 → 实现四级（L4 并入第 1 级"deep 专属昂贵分析器"；tsc 与 Java solver 合并为一个 L2 级——规则面只声明 L2，无后端区分），增注记录该形态差。可用性接口最小契约：**(a)** 按 capability 注册 provider；**(b)** 探测廉价、无副作用（绝不启动后端，同 `TypeResolver.isAvailable()`）；**(c)** 无 provider 或探测失败 = DEGRADE（计数 + 日志），绝不假装可用；**(d)** capability 名即降级记录 id；**(e)** 注入与 TypeResolver 同构（run 装配侧传入），items 32/33/34 只注册 provider 不改引擎。
7. **预算口径与聚合**（R1 Major 4 修复）：预算 = **每 `engine.lint()` 调用**一份（"单文件软预算"的精确含义；`--fix` 多 pass 对同一物理文件多次 lint，每 pass 新预算、各 pass 如实报告自身降级，汇总计数为 pass 之和——如实非虚胖）。运行级聚合：分析器级降级 id 列表按首次出现序 union（文件级保序在 per-file stats；run 级为 first-seen 序），计数求和。
8. **时钟注入缝**（R1 Major 5 修复）：预算组件持有单调时钟（默认 `System.nanoTime()`），`LintEngine` 提供包私有测试构造重载注入 `LongSupplier`——公共 API 不变；确定性测试用假时钟推进（阶梯/熔断/时间片全部可确定性触发），不依赖 sleep/真实计时。**双时间域说明（R2 Minor C）**：in-script deadline 强制（`LintDeadlineExecutor`）保持真实时钟、不接假时钟；假时钟测试须以真实 `nanoTime()` 对齐起步、只在断言点跳变；deadline 类断言（收紧后超时计数）用真实慢脚本（死循环 + 收紧后 1ms 预算）确定性触发，不用假时钟驱动。

## Execution Plan

### Phase 1 - Deep 档位 + capability 扩展 + 可用性接口

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/`（LintProfile/LintCapability/LintEngine + 可用性接口新文件）、`cli/CliOptions.java`、`nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/engine/`

- Item Types: `Decision | Proof`

- [x] `LintProfile` 增 `DEEP`：能力上限 {L1,L2,L3,L4,SCOPE,METRICS}；单文件软预算常量 fast 20ms / standard 500ms / deep 5s（profile 携带预算值，语义见 design 11 §2）
- [x] `LintCapability` 增 `L3`/`L4`/`SCOPE`/`METRICS`；javadoc 记录词表裁定（Decision 1）与 fail-closed 语义
- [x] deep 分析器可用性接口（Decision 6 契约 a–e）+ LintEngine 门控扩展：deep 分析器无 provider/探测失败 → 上限内也 DEGRADE；L2 路径行为不变（既有测试零改动通过）
- [x] CLI `--profile deep`（parse + usage 文案 + 未知值报错文案更新）
- [x] 测试：三档 × requires token 门控矩阵（含 L3 规则 standard=SKIP、deep 无 provider=DEGRADE、deep 有 provider=RUN）、CLI deep 解析、未知 token 仍 fail-closed

Exit Criteria:

- [x] 门控矩阵测试全绿：同一 L3-requires 规则在 fast/standard 计入 `skippedByProfile`、在 deep 无 provider 计入 `rulesDegraded`、注入测试 provider 后 RUN
- [x] L2 既有门控测试（TestL2TypeGate 等）零改动通过（行为兼容证明）；TestLintEngine 的 `values()` 循环兼容 DEEP（无 resolver 时 DEEP 对 L2 规则 DEGRADE）
- [x] CLI 接受 `--profile deep`，未知档位值仍抛 `NopLintException`（fail-closed 不回归）
- [x] **无静默跳过**：可用性接口无 provider 时显式 DEGRADE（计数+日志），不存在"当可用处理"分支
- [x] Owner-doc：design 11 §2 增注 deep 档 live 口径（含 capability 词表裁定 Decision 1/6）
- [x] `ai-dev/logs/2026/09-24.md` 条目更新

### Phase 2 - 单文件预算 + 降级阶梯 v2 + 熔断 + fast 时间片

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/`（RuleSetRunner/LintStats/CompiledRule/新预算组件）、`xscript/`（仅 deadline 计算消费点）、`cli/RunSummary.java`、`cli/ConsoleReporter.java`、engine 测试

- Item Types: `Fix | Decision | Proof`

- [x] 单文件预算组件（Decision 7/8）：profile 总预算 + fast 10ms xscript 时间片 + pattern 阶段独立计时 + 可注入时钟；`LintEngine.lint` 构建、传入 `RuleSetRunner.run`（签名变更，测试调用点机械适配并逐个列出）；时钟检查只在规则边界与 xscript/fix 决策点
- [x] 阶梯有序闭合（Decision 2/6）：总钟耗尽 → 1→4 保序闭合、分析器级降级列表逐级记录（每级每 lint 调用至多一条）；闭合后规则边界重判（Decision 5，CompiledRule 携带 requires）
- [x] pattern 熔断（Goal 5）：pattern 阶段累计超预算 → 中止该文件剩余规则、逐条 id 计数、文件 degraded 标记；总钟 vs pattern 钟的双路径 + 重叠时熔断胜出（R2 Minor A）测试证明
- [x] xscript 集成：per-match deadline = min(规则值, 档位上限, 阶梯态/时间片剩余)；fast 时间片 <1ms 边界（Decision 3）；耗尽后跳过执行、`xscriptBudgetExceeded` 逐条计数 + 规则 id 专列 + pattern 层诊断输出
- [x] fix 门控（Decision 4）：FAST 不生成 Fix 载体；standard/deep 阶梯第 4 级关闭后停止生成并计数；诊断本体不受影响
- [x] `LintStats` 新增：`xscriptBudgetExceeded` + 规则 id 专列、分析器级降级列表、fix 降级跳过计数、熔断中止规则 id 列表；`toString` 同步
- [x] `RunSummary` 聚合（Decision 7：id first-seen union、计数求和、filesDegraded）+ `ConsoleReporter` 渲染新增各面 + **补渲染既有 `rulesDegraded`/`degradedRuleIds`**（Goal 8）
- [x] 测试：假时钟驱动的预算耗尽全链路、阶梯顺序断言、熔断触发断言、fast 时间片跳过+输出断言、fix 门控三档断言、stats/报告渲染断言（`TestBudgetDegradeLadder` 9 例）

Exit Criteria:

- [x] **端到端验证**：`LintEngine.lint` 单调用内——规则集含 deep-requires + L2 + xscript + fix + 纯 pattern 规则，假时钟分阶段推进：阶梯按 1→4 保序闭合、deep/L2 规则边界降级计数、xscript 规则以收紧 deadline 继续产出、fix 停止生成且计数、熔断中止剩余规则且 id 可见、纯 pattern/kind/约束规则在熔断前全程正常产出（floor 证明）
- [x] **接线验证**：阶梯/时间片状态被 xscript deadline 计算与 fix 生成决策真实消费（行为断言：收紧后死循环脚本以 20ms 真实时钟中止且墙钟 <500ms、fix 载体消失、时间片耗尽后 match 无脚本仍出诊断）
- [x] fast 时间片：耗尽后剩余 match 的 `xscriptBudgetExceeded` 计数 = 跳过数、规则 id 专列非空、诊断仍产出（design 11 §5 字面契约 + Decision 3 形态）
- [x] **无静默跳过**：每一类降级（分析器关闭/时间片跳过/fix 跳过/熔断中止）都有计数或 id 列表；纯 run 级计数援引 `xscriptTimedOutMatches` 先例口径（R1 Minor 12 修订：计数可归属到事件类型）
- [x] 既有引擎测试**行为断言**零改动通过（core 709/0 全量绿）；`RuleSetRunner.run` 签名适配的测试调用点 = TestRuleSetRunner 3 处 + TestCompositeRuleCompile 11 处 + TestFixEngineWiring 2 处（均显式 `FileBudget.withoutLimits()`，R1 Minor 10 修订）；TestConsoleReporter 3 条期望串随渲染面扩展更新（degraded/budgetExceeded 字段）
- [x] Owner-doc：design 11 §5 增注阶梯 v2 + 熔断 + 时间片 live 口径（Decision 2/3/5/6/7）
- [x] `ai-dev/logs/2026/09-24.md` 条目更新

### Phase 3 - 引擎级基准 + 性能守门 + 全量回归 + 收口

Status: completed
Targets: `nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`、`nop-lint/docs/perf-baseline.md`、`nop-lint/bench/`、roadmap、全模块测试

- Item Types: `Proof | Follow-up`

- [x] 新增引擎级 JMH 基准 `engineLint`（经 `LintEngine.lint` 全管线：gate→kind 过滤→匹配→预算边界检查→抑制尾→stats，bench 语料 + 3 条旗舰规则）；**改动合入前先测 before 基线**（pre-Phase-2 worktree @44ed78f9b2 实测，R1 Major 6 修复）
- [x] after 复测四基准，与 before/perf-baseline.md 基线对照：engineLint 0.002±0.001 s/op 前后一致、三 pattern 基准不变（量级零回归）；数字写入 perf-baseline.md 增注（含环境与日期）
- [x] JFR 热点复核（engineLint 60×1s 录制）：热点全在 nop-treesitter 层，预算帧未入热点榜——无回归，"回归则消除"分支未触发（裁定记录于 perf-baseline.md 增注）
- [x] 全量回归：`./mvnw -pl nop-lint/nop-lint-core test` 709/0、`./mvnw -pl nop-lint/nop-lint-nop test` 26/0、`./mvnw -pl nop-lint/nop-lint-java test` 70/0 全绿；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] BenchmarkSmokeTest 防基准腐坏门禁更新（≥4 基准，含 engineLint）并通过

Exit Criteria:

- [x] perf-baseline.md 增注包含引擎级基准 before/after 数字与对照结论 + 三基准 after 对照
- [x] 全量测试绿 + doc-links 0 错误
- [x] roadmap item 31 → done；M5 依赖矩阵更新（30✓ 31✓）（closure audit 通过后执行）
- [x] `ai-dev/logs/2026/09-24.md` 收口记录（Phase 3 条目随 closure 一并写入）

## Closure Gates

- [x] design 11 §2/§5/§8 Phase 3 要求的 deep 档 + 阶梯 v2 + 熔断 + fast 时间片行为全部落地且 live 与增注一致
- [x] 全部 in-scope 漂移修复（fast fix 门控）完成，无 live defect 遗留
- [x] 阶梯/熔断全链路端到端测试存在且绿（含 floor 永不因阶梯关闭证明、熔断最后防线证明）
- [x] 三档预算值、capability 词表、时间片语义、阶梯四级形态与 design 11 的裁定关系全部记录在案（Decision 1/2/3/6/7）
- [x] 无被静默降级的 in-scope 项；deferred 仅为 watch-only/optimization 类
- [x] owner docs（design 11 增注、perf-baseline 增注）与 live 一致
- [x] 独立子 agent closure audit 完成并写入本 plan Closure 段
- [x] **Anti-Hollow Check**：阶梯/熔断/时间片机制被运行时真实消费（xscript deadline/fix 门控/pattern 中止行为差异断言）；可用性接口有测试 provider 消费证明；无空方法体/no-op
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test` 通过（core 709/0；-Dtest 定向套件与本日全量报告覆盖）
- [x] `./mvnw -pl nop-lint/nop-lint-nop -am test` 通过（26/0；java 70/0）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出码 0（0 findings）

## Deferred But Adjudicated

### matcher 内核优化（children 缓存等 perf-baseline 候选）

- Classification: `optimization candidate`
- Why Not Blocking Closure: JFR 已证 93% 热点在 nop-treesitter cursor 层且当前 ≈1ms/文件对 fast 20ms 预算余量 ~20×，不构成任何档位预算违约；本 plan 的预算机制不改变该结论
- Successor Required: `no`（perf-baseline.md 已记录候选与触发条件，按证据触发）

## Non-Blocking Follow-ups

- 预算值配置化（profile 常量 → 运行参数）：无消费方证据，等 editor/CI 场景反馈
- 阶梯部分闭合阈值（预算分数触发单级关闭）：design 无阈值数字，单一耗尽点裁定下无消费方证据；等 deep 实测画像（items 32–36 落地后）

## Closure

Status Note: deep 档位 + capability 词表（L3/L4/SCOPE/METRICS）+ AnalyzerAvailability 探针门控 + 单文件预算（双钟）+ 降级阶梯 v2（有序闭合 + 规则边界重判）+ pattern 熔断（优先级胜出）+ fast 10ms xscript 时间片 + fix 门控漂移修复 + 引擎级 JMH 基准与性能守门全部落地；三个 Phase 的 Exit Criteria 与全部 Closure Gates 勾选完毕，独立子 agent closure audit APPROVED，roadmap item 31 已翻 done。
Completed: 2026-09-24

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_3547dea9-a7aa-407d-a24b-543faeebe6a2（fresh session，与实现者不同 task）
- Evidence:
  - Phase 1（5/5 PASS）：门控矩阵（TestDeepProfileGate 10/0 实测绿）；L2 既有测试零改动（git show 44ed78f9b2 证 TestL2TypeGate 未触碰）；CLI deep + fail-closed；无 provider 显式 DEGRADE；design 11 §2 增注。
  - Phase 2（7/7 PASS）：端到端假时钟全链路（阶梯序 [L3,L2,xscript,fix]、边界重判、熔断胜出、时间片跳过+双诊断、fix 载体消失+计数）；接线验证（tightened-deadline 测试真实时钟死循环 + 墙钟<500ms，日志实证 budgetMs=20）；签名适配调用点与 commit 31f863b0ce --stat 精确吻合；design 11 §5 增注。
  - Phase 3（4/4 PASS）：perf-baseline.md 增注 engineLint before/after 0.002±0.001 s/op 一致 + JFR 热点（预算帧未入榜）；core 709/0 + nop 26/0 + java 70/0；BenchmarkSmokeTest ≥4 通过；roadmap 翻转随 closure 执行。
  - 工具门禁独立重跑：check-plan-checklist --strict exit=0；scan-hollow-implementations --module nop-lint-core --severity high exit=0（0 findings）；check-doc-links --strict exit=0。
  - Anti-Hollow：阶梯/熔断/时间片/探针四条机制均有可证伪的行为断言（非接口存在性）；全部新方法有实体逻辑。
  - Deferred 项分类检查：matcher 优化（optimization candidate）+ 预算配置化/部分闭合阈值（follow-up）均真实 non-blocking，无 in-scope live defect 被降级。

Follow-up:

- RunSummary 运行级聚合新面（filesDegraded、degradedAnalyzers union）与 ConsoleReporter 三条新行的正向路径直接测试补强（audit Minor 1；现有覆盖为 per-file LintStats 断言 + reporter 零态串）
- 预算值配置化、阶梯部分闭合阈值（见 Non-Blocking Follow-ups）；perf-baseline before 数字的独立 harness 输出未持久化（audit Minor 2，记录瑕疵，after 侧完整可复现）
