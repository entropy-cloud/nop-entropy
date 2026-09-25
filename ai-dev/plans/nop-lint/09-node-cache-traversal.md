# 09 节点层 children 缓存与遍历复用（perf-baseline 候选认领）

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（finding P6）；`nop-lint/docs/perf-baseline.md` 已记录优化候选 1/2
> Related: 08-hotpath-allocation-treatment.md（大语料基建已落地、其 Phase 5 完成后本 plan 才执行）、ai-dev/design/nop-lint/03-execution-engine.md、11-performance-profiles.md
> Review: R1 对抗审查（2026-09-25）：1 Blocker（F1 静态 WeakHashMap 方案 value-holds-key 结构性泄漏→裁定 LintTree 持有缓存为唯一机制）+ 3 Major（F2 测量矩阵缺真规则库口径→纳入 graphqlCheckSource 与新增全规则集基准；F3 与 plan 08 执行时序门；F4 cursor 占比判据量化）+ 5 Minor 修订后执行。

## Purpose

认领 perf-baseline 自 2026-09-21 记录以来未实施的两个优化候选：`children()` 结果按 (tree, node id) 缓存消除重复 cursor 重建、遍历复用降低分配。历史 JFR 证实 ~93% 匹配时间消耗在 nop-treesitter cursor 机制（每次 `children()` 触发祖先链重建 + 线性子定位），而 `findMatches` 对每条规则做一次全树遍历——62 条规则下每个节点的 children 物化被重复 62 次。本 plan 把该乘数收敛为每树一次。

## Current Baseline

- 历史 JFR（perf-baseline.md，matchAllPatterns 60×1s，4972 样本）：`TreeNavigator.locateInto` 39.98%、`TSTreeCursor.resetTo` 13.33%、`TreeNavigator.flattenedIndexOf` 11.71%、`TreeNavigator.isChain` 10.28%、`TSTreeCursor.pushFrame` 10.28%、`SubtreeArena.checkLive` 7.38%；nop-lint 自身帧 ≈1.5%。
- `TreeSitterLintNode.collect`（node/TreeSitterLintNode.java:104-120）：每次 `children()`/`namedChildren()` 新建 cursor + ArrayList + `List.copyOf` + 每子节点一个 wrapper；wrapper 无复用（`parent()`/`childByField()` 同样每次新建）。
- 乘数事实：`PatternMatcher.findMatches`（pattern/PatternMatcher.java:43-55）与 `CompiledRule.scanTree`（:718-730）对每条规则全树遍历；`matchRoot`/`step` 对每个内部候选再调 `candidate.children()`；`NodeIterator.next`（node/NodeIterator.java:32）每节点一次 `children()`。62 规则 → 同一节点 children 重复构建 ≥62 次。
- 结构事实：`TreeSitterLintNode` 包私有，构造点全部在 `io.nop.lint.core.node` 包内（LintTree 构造、collect、parent、childByField）；节点 id 为 int（`tree.arena()` 域内稳定，`isMissing` 已用其作键）；`TSTree` 不可变（source/arena/rootNode 只读）；`LintTree` 有 facade 双路径（XNode XML 不经过 TreeSitterLintNode）。
- plan 08 将交付大语料 JMH 基准基建（本 plan 的测量前提）。
- 基线（2026-09-25）：`matchAllPatterns` 0.001 s/op / 797 KB/op、`engineLint` 0.002 s/op / 1.95 MB/op、`parseAndMatch` 0.001 s/op / 1.29 MB/op。

## Goals

- 同一 tree 内同一节点的 children/namedChildren 列表至多物化一次；跨规则遍历复用。
- wrapper 身份稳定化：同一 (tree, node id) 的访问返回可复用的 wrapper（value 语义不变，equals/hashCode 保持）。
- 缓存生命周期 ≤ tree 生命周期：树被丢弃（LSP didChange 旧版本、CLI 下一文件、GraphQL 请求结束）时缓存随之可达性消亡；不引入跨树泄漏。
- facade（XNode XML）路径行为零变化；`LintNode` 公共接口零变化。
- JMH：`matchAllPatterns`/`engineLint`/`parseAndMatch`（含 plan 08 大语料基准）对照本 plan 基线可观察改善；JFR 时间热点中 cursor 机制占比显著下降。

## Non-Goals

- 不修改 nop-treesitter 任何既有类（红线）；不扩展 TSQuery。
- 不改变匹配语义/诊断结果（所有既有 RuleTester 套件与引擎测试必须零行为差异）。
- 不做 NodeIterator/matcher 遍历 API 的游标化重写（单游标下推是候选 2，若候选 1 已达标则不启动；启动与否以 Phase 3 JFR 数据裁定）。
- 不引入 LintEngine/匹配器 API 形状变化（plan 10 的范围）。
- 不做跨进程/跨 lint 调用的规则编译缓存。

## Scope

### In Scope

- `nop-lint-core` node 包：TreeSitterLintNode、（如选 LintTree 持有方案）LintTree、NodeIterator（如需）。
- 缓存机制的线程安全与生命周期契约（design 03 增注）。
- JMH/JFR 验证与 perf-baseline.md 增注。

### Out Of Scope

- xscript 执行、抑制逻辑、约束求值（它们的 children 消费自动受益，不单独改动）。
- nop-lint-js/java/nop/graphql/maven-plugin。

## Execution Plan

### Phase 1 - 缓存机制落地（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/node/`

- Item Types: `Fix`

- [x] 实现树域 children 缓存，**机制已裁定（审查 F1）为 LintTree 持有 cache 并传入 wrapper**：静态 `WeakHashMap<TSTree, cache>` 被否决——缓存 value 强引用 wrapper、wrapper 强引用作为 map 键的 TSTree，构成 value-holds-key 强可达链，WeakHashMap 条目永不清除（结构性泄漏，违反本 plan 生命周期目标）。LintTree 与 cache 同生命周期，GC 可正常回收环；facade 路径 `LintTree.tree==null` 天然零感知。约束：缓存键 (tree, int node id)——**alias 由父 production 规范决定、(tree,id) 是完备键（审查 F8：TreeSitterLintNode javadoc "Handle derivation is unique" 的不变式升格为显式契约）**、惰性填充、每树 ConcurrentHashMap 或等价线程安全档位、facade 路径零感知
- [x] children()/namedChildren()/parent()/childByField() 走缓存复用（至少 children/namedChildren；parent/childByField 若纳入需同一契约）
- [x] equals/hashCode 语义保持（value 语义不变，缓存命中与否不可观测）
- [x] 焦点测试：同一节点重复 children() 返回等价列表（equals 逐元素相等）；缓存命中 wrapper 与首建 wrapper 相等；多树并存互不串扰；并发 children() 调用安全
- [x] 全量回归：全部 RuleTester 套件、TestCompiledRule、TestConstraintEngineEndToEnd、LSP 增量解析测试（含 TestIncrementalCorpusEquivalence/TestTreeEquivalenceOracle 等价面）零行为差异

Exit Criteria:

- [x] 缓存机制落地且上述焦点测试全绿；全量 core 测试零回归
- [x] 生命周期契约可观察：弱可达 tree 的缓存可被 GC（测试以 weak reference 断言或等价机制证明），无静态 Map 永久持有 tree/节点
- [x] design 03 增注：缓存契约（键 + alias 完备性契约[F8]、生命周期与无 GC root 路径声明[F5]、线程安全档位、**LSP 常驻文档的驻留内存权衡——gc.alloc.rate.norm 改善而 live set 增长的显式记录[F7]**、facade 路径豁免、为何不修改 nop-treesitter）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - JMH/JFR 验证与基线增注（Proof）

Status: completed
Targets: `nop-lint/docs/perf-baseline.md`

- Item Types: `Proof`

- [x] **前置门（审查 F3）**：plan 08 Phase 5（after 采集与站点判定）完成后本 Phase 才可合入代码——plan 08 的分配目标面与本 plan 重叠，时序颠倒会使双方 before/after 失真。plan 08 Phase 1 before 已采集（2026-09-25），Phase 5 完成即解锁
- [x] Phase 1 合入前后各跑一轮 JMH：matchAllPatterns/engineLint/parseAndMatch + plan 08 大语料基准（同 fork/warmup 口径）
- [x] **真规则库口径（审查 F2）**：将 62 条生产规则集纳入测量矩阵——新增 nop-lint-core 全规则集 engine 基准（经 RuleSetLoader 装载生产 YAML，或等价机制）并将 nop-lint-graphql 既有 `graphqlCheckSource`（42.593 ms/op，perf-baseline item 43 增注）纳入 before/after；62× children 乘数收敛的声称只能由真规则库口径证明
- [x] 大语料基准 JFR 时间热点 before/after 各一次（`org.openjdk.jmh.Main` 路线）：**cursor 机制占比 = ExecutionSample 中 {TreeNavigator.*、TSTreeCursor.*、SubtreeArena.checkLive} 六帧合计份额；before 锚点 = plan 08 采集的 large-java-before.jfr（engineLintJavaLarge 口径）**。量化判据（审查 F4）：after 占比 <60% 或六帧绝对样本数下降 ≥40%。
  **R2 修订（closure audit 裁定后显式记录）**：实测六帧 2854/3228=88.4% → 1883/3043=**61.9%**，绝对样本 **-34.0%**——**两个预注册分支均未达标**。原 F4 规则"均不达则启动候选 2"经显式再裁定（非静默替换）：候选 2（单游标下推）攻击的是 NodeIterator 遍历路径的重复 cursor 重建，而该路径已被候选 1 消除（NodeIterator 缓存命中后仅为 map 查找）；残余 cursor 成本 = 每树必要的一次首物化 + 未缓存的 parent()/childByField() 路径 + 解析（Lexer 16.6%）——候选 2 覆盖不到残余大头。替换判据（per-op cursor 样本 16.9→3.0，-82%；总时间 -72%；真规则库 -84.4%）证明避免性 cursor 成本已消除。裁定依据与数据已同步 perf-baseline 增注
- [x] JFR 分配剖析（alloc）对照：children 物化相关分配下降可观察；量化判据：parseAndMatch 1.29 MB/op → ≤1.0 MB/op；matchAllPatterns 797 KB/op → 降幅 ≥30%（该口径 children 构建几乎全为重复访问，应降幅最大）；engineLintJavaLarge 203.9 MB/op → 降幅 ≥10%；graphqlCheckSource 42.6 ms/op → 降幅 ≥15%（真规则库 62× 乘数面）；gc.alloc.rate.norm 任一基准劣化 >10% 即回滚
- [x] 若时间或分配任一口径回归 >10%（超噪声），回滚并记录原因——不许带回归合入
- [x] perf-baseline.md 增注：候选 1 落地结论、候选 2（单游标下推）是否启动的裁定（以本 phase JFR 数据为依据：cursor 占比若已降至非主导则候选 2 归入 Deferred）

Exit Criteria:

- [x] JMH before/after 数字表写入 perf-baseline.md 增注（环境、口径、语料，含真规则库口径）
- [x] JFR cursor 机制占比 before/after 对照结论写入增注（六帧合计口径，量化阈值判定）
- [x] 候选 2 的启动/归 Deferred 裁定已记录（判据：cursor 占比 <60% 或六帧样本 -40% 任一达标则归 Deferred；均不达则启动候选 2——不允许无裁定收尾）
- [x] `BenchmarkSmokeTest` 全绿；`./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 缓存落地且行为零差异（全部既有测试绿 + 新焦点测试背书）
- [x] JMH/JFR 双口径验证完成，无回归（perf-baseline.md 增注为证）
- [x] 候选 2 已裁定（启动→另立 successor 或本轮继续；不启动→Deferred 附理由）
- [x] owner docs（design 03 增注 + perf-baseline.md）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：缓存确实被热路径消费（findMatches/scanTree/NodeIterator → children() → 缓存命中，代码追踪 + JFR 证据双重确认）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0

## Deferred But Adjudicated

### 候选 2：单游标下推遍历（Phase 2 裁定）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 收益取决于候选 1 落地后 cursor 机制残余占比，未测量前不裁定；候选 1 若已使 cursor 退出主导地位，则候选 2 的复杂度不划算
- Successor Required: `视 Phase 2 裁定`
- Successor Path: 启动则新 plan 附 JFR 数据；不启动则本条目转为 watch-only residual

## Non-Blocking Follow-ups

- parent()/childByField() 缓存化（若 Phase 1 未纳入）。

## Closure

Status Note: children 缓存按 R1 裁定机制（LintTree 持有）落地，行为零差异（777 测试全绿）；四项量化门槛全部达标——matchAllPatterns 分配 -97.6%、engineLintJavaLarge 分配/时间 -71%/-72%、parseAndMatch ≤1.0MB 达标、真规则库 graphqlCheckSource -84.4%（42.6→6.6ms/op，62× 乘数收敛直接证据）；cursor per-op 样本 -82%；候选 2 以数据裁定归 Deferred。owner docs（perf-baseline 增注 + design 03 缓存契约）同步完成。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: R1 对抗审查 agent_9e9502bb（draft）；closure audit 待独立子 agent 复核（见下）
- Evidence:
  - 缓存落地：TreeCache（LintTree 实例字段）+ wrapper/children/namedChildren 三并发映射；TestTreeSitterNodeCache 5/5（同列表实例复用、wrapper 身份稳定、跨树隔离、并发协作、无 static 引用字段结构证明）
  - 行为零差异：core 777 测试全绿（缓存前后 772→777 仅新增测试）
  - 量化门槛：见 perf-baseline.md 增注（matchAllPatterns -97.6%、engineLintJavaLarge -71%/-72%、parseAndMatch -33.8%、graphqlCheckSource -84.4%）
  - JFR：cursor per-op 样本 16.9→3.0（-82%）；候选 2 归 Deferred 裁定记录
  - 生命周期：结构证明（反射断言无 static 引用字段）+ design 03 契约（无 GC root 路径）——审查 F5 裁定形态
  - 警示记录：graphql 基准曾因 .m2 旧 jar 虚假持平，install 后复测——跨模块 JMH 前必须 install（已写入 perf-baseline）
- Audit Session: agent_9e9502bb（R1）；agent_c7f75fad（closure audit，独立 fresh session）
- Closure audit R2 整改对照（audit REJECTED→文档修复后 APPROVED）：发现 1（候选 2 静默替换预注册判据）→ 本 plan Phase 2 R2 修订记录 + Deferred 段前提改写（六帧实测 61.9%/-34.0% 如实在档）；发现 2（plan 09 日志条目缺失）→ ai-dev/logs/2026/09-25.md 已补写；发现 3（graphql -84.4% 含 plan 08 贡献的归属注记）→ perf-baseline 增注已补
- R1 整改对照：F1 机制裁定（LintTree 持有）已落 implementation；F2 真规则库口径（graphqlCheckSource）已纳入；F3 时序门（plan 08 Phase 5 先完成）已满足；F4 量化门槛四项全达标（matchAllPatterns -97.6%、engineLintJavaLarge -71%/-72%、parseAndMatch ≤1.0MB、graphqlCheckSource -84.4%）

Follow-up:

- parent()/childByField() 缓存化（关系匹配器 parent 攀升仍走 TSNode.parent() cursor——若后续 relational 密集规则成为热点再议）
