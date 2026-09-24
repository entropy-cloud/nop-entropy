# 08 引擎热路径分配治理（抑制尾/一致性检查/kind 过滤/哈希编码）

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings P2/P3/P4/P5/P7）
> Related: 07-cli-ecosystem-user-visible-defects.md、nop-lint/docs/perf-baseline.md、ai-dev/design/nop-lint/11-performance-profiles.md

## Purpose

消除引擎每文件热路径上"每节点一次"的隐性疾病：抑制尾逐节点 `toLowerCase()` 分配、meta-var 一致性检查重复 `children()`、kind 索引装箱、捕获环境三重防御拷贝、SHA-256 十六进制编码走 `String.format`。全部为行为不变的机械优化，以 JMH before/after + JFR 分配剖析验证。

## Current Baseline

- 基线（2026-09-25 本机）：`engineLint` 0.002 s/op / 1.95 MB/op、`matchAllPatterns` 0.001 s/op / 797 KB/op、`parseAndMatch` 0.001 s/op / 1.29 MB/op（`_tmp/lint-bench-result.txt`，bench 语料 ~60 行）。
- `CommentSuppressionScanner.collectComments`（suppress/CommentSuppressionScanner.java:192-201）：对树上每节点 `node.isExtra() || node.kind().toLowerCase().contains("comment")`——每节点一个新 String；bench 语料太小使其在 JMH 中不可见，万行文件 ≈ 万次分配/文件，`--fix` multipass ×10 重复。
- `NodeExactEquality.isExact`（pattern/NodeExactEquality.java:19-42）：`a.children().isEmpty() && b.children().isEmpty()` 后又各调一次 `children()`——同一节点对 4 次 cursor 重建（perf-baseline 已证每次 children() 是 cursor 祖先链重建+物化）；递归无深度上界（同模块 `ReferentMatcher.MAX_EXPANSION_DEPTH` 有 64 层防护，此处不对称）。
- `KindIndex.collect`（engine/KindIndex.java:18-25）返回 `HashSet<Integer>`，`node.kindId()` >127 时每节点一个 Integer；`CompiledRule.canMatchKinds(Iterable<Integer>)`（engine/CompiledRule.java:839-851）对每规则 O(R×K) 装箱迭代。调用面收敛：RuleSetRunner.java:117/156 单点。
- `MetaVarEnv.multiCaptures()`（pattern/MetaVarEnv.java:92-101）：LinkedHashMap 拷贝→每值 `List.copyOf`→外层 `Map.copyOf` 三层拷贝；`singleCaptures()` 只有一层，不对称。
- `RuleResultCache.sha256`（cli/RuleResultCache.java:194-205）：每字节 `String.format("%02x", b)`（每文件哈希 64 次 format）+ 每调用 `MessageDigest.getInstance`；`BaselineEngine.fingerprint`（suppress/BaselineEngine.java:48-54）每诊断一次 `getInstance`。同文件 `runFingerprint` 用 `.reduce((a,b)->a+","+b)` 拼接（应 `String.join`）。
- `Fixer.merge`（fix/Fixer.java:41-55）O(n²) 重叠扫——贪心优先级契约（声明序先到先得）使排序线性化不可行，本 plan 只补 javadoc 规模注记，不改行为。
- JMH 基建：`nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`（LintBenchmarks + LintBenchmarkRunner + BenchmarkSmokeTest 防腐坏门禁），`nop-lint/bench/run-benchmarks.sh`。
- `XmlTagKinds` javadoc 引用 `CompiledRule.canMatchKinds`（xml/XmlTagKinds.java:12）。

## Goals

- 抑制尾注释节点判定零分配（每节点不再创建 String）。
- `NodeExactEquality` 每节点对 `children()` 调用 4→2，且带显式深度防护（fail-closed）。
- kind 索引全程无装箱：`collect` 产出排序 int[]，`canMatchKinds` 排序数组二分/归并判定。
- `multiCaptures()` 单次拷贝 + 不可变视图；`singleCaptures()` 风格对齐。
- SHA-256 hex 编码零 `String.format`（`HexFormat`），`MessageDigest` 热点站点免逐次 provider 查找。
- 新增大语料 JMH 基准使每节点分配项可观测；全部改动经 before/after 对照 + JFR 分配剖析证实无回归。

## Non-Goals

- 不做 children() 结果缓存/单游标遍历（plan 09 的结构优化）。
- 不改 StopBy/ellipsis 探测的 env.clone 语义（语义承重，优化需 design；归 Deferred 观察项）。
- 不改 Fixer.merge 贪心契约与行为（仅注记）。
- 不触碰 TSQuery 冻结、nop-treesitter 既有类、fail-closed 降级语义。
- 不优化 nop-xlang xscript 执行内部。

## Scope

### In Scope

- `nop-lint-core`：suppress/CommentSuppressionScanner、pattern/NodeExactEquality、engine/KindIndex、engine/CompiledRule（canMatchKinds 签名）、engine/RuleSetRunner（调用点）、pattern/MetaVarEnv、cli/RuleResultCache、suppress/BaselineEngine、fix/Fixer（javadoc）、bench 新基准。
- JMH 前后对照 + JFR（时间热点 + 分配两口径）+ perf-baseline.md 增注。

### Out Of Scope

- LintEngine API 形状与 FixApplier byte[] 通路（plan 09）。
- 规则语义、诊断文本、统计口径（LintStats 字段不动）。

## Execution Plan

### Phase 1 - 抑制尾与一致性检查零分配化（Fix）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/suppress/CommentSuppressionScanner.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/NodeExactEquality.java`

- Item Types: `Fix`

- [ ] `collectComments` 的注释 kind 判定改为零分配等价实现（大小写不敏感 contains 语义严格保持，先 `isExtra()` 短路；禁止逐节点 `toLowerCase`/`new String`）
- [ ] `NodeExactEquality.isExact`：`children()` 结果局部化（每节点对 2 次），判定顺序与语义不变
- [ ] `isExact` 增加显式深度防护（超限抛 `NopLintException`，防护常量与 `ReferentMatcher` 展开帽同量级并注明），fail-closed 不静默截断
- [ ] 新增/扩展焦点测试：注释判定大小写混合形态（`Comment`/`comment`/语法自定义 kind）结果与旧实现一致；深链结构触发深度防护抛异常而非 StackOverflow

Exit Criteria:

- [ ] `collectComments` 路径代码可观察为零逐节点分配（无 toLowerCase/new String）；抑制语义测试全绿（TestCommentSuppression 501 行不回退）
- [ ] isExact 深度防护测试红转绿；既有 TestCompiledRule/TestConstraintEngineEndToEnd 全绿
- [ ] `No owner-doc update required`（行为语义不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - kind 索引与捕获环境无装箱化（Fix）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/KindIndex.java`、`engine/CompiledRule.java`、`engine/RuleSetRunner.java`、`pattern/MetaVarEnv.java`

- Item Types: `Fix`

- [ ] `KindIndex.collect` 返回排序去重 `int[]`（收集期无 Integer 装箱）；`RuleSetRunner` 调用点同步
- [ ] `CompiledRule.canMatchKinds` 改收排序 `int[]`（排序数组二分或线性归并；`targetKindIds` 构造期已排序可直接用）；模块内唯一外部引用 `XmlTagKinds` javadoc 同步；public 签名变更在 javadoc 注明排序前置条件
- [ ] `MetaVarEnv.multiCaptures()` 收敛为单次拷贝 + 不可变视图（只读快照契约不变，javadoc 语义保持）；`singleCaptures()` 风格一致
- [ ] 焦点测试：canMatchKinds 命中/未命中/空 opinion 三态与旧实现等价（现有 TestCompiledRule 覆盖面核对）；multiCaptures 只读性测试（写视图抛 UnsupportedOperationException）

Exit Criteria:

- [ ] kind 过滤链路无 `Set<Integer>`/`Integer` 装箱（代码可观察）；`rulesKindFiltered` 计数行为不变
- [ ] 既有引擎测试全绿；新增等价性/只读性断言落地
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 哈希编码与摘要查找（Fix）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/RuleResultCache.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/suppress/BaselineEngine.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/Fixer.java`

- Item Types: `Fix`

- [ ] `RuleResultCache.sha256` 十六进制编码改 `HexFormat.of().formatHex`；摘要实例热点站点（RuleResultCache、BaselineEngine.fingerprint）免逐次 `getInstance`（ThreadLocal 原型或等价线程安全复用——MessageDigest 非线程安全，禁止共享实例）
- [ ] `runFingerprint` 的 reduce 拼接改 `String.join`
- [ ] `Fixer.merge` javadoc 补规模注记（O(n²) 最坏形态与贪心契约原因，判定不改行为）
- [ ] 焦点测试：sha256/fingerprint 输出与旧实现逐字一致（黄金值断言），线程并发调用正确性测试

Exit Criteria:

- [ ] 编码路径无 `String.format`；输出哈希值黄金断言不变（缓存兼容性：`--cache` 旧缓存文件在新代码下命中行为由指纹逐字一致保证）
- [ ] 并发正确性测试落地；既有 cache/baseline 测试全绿
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - JMH/JFR 验证与基线增注（Proof）

Status: planned
Targets: `nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`、`nop-lint/docs/perf-baseline.md`

- Item Types: `Proof`

- [ ] 新增大语料基准（合成 ~2000 行源码常量，覆盖抑制尾/kind 过滤/一致性检查路径，命名与现有 LintBenchmarks 风格一致），并入 run-benchmarks.sh 与 BenchmarkSmokeTest 防腐坏面
- [ ] Phase 1-3 合入前后各跑一轮 JMH（至少 engineLint/parseAndMatch/matchAllPatterns + 新大语料基准，同 fork/warmup 口径），数字记录进 perf-baseline.md 增注
- [ ] 大语料基准 JFR 分配剖析 before/after 各一次（`-jvmArgsAppend StartFlightRecording` 注入 JMH forked JVM，`jfr view` 分配热点对照）；确认 Phase 1 目标站点退出分配热点榜或显著下降
- [ ] 若任一基准回归 >10%（超出噪声），回滚对应改动并在增注记录原因——不许带回归合入

Exit Criteria:

- [ ] JMH before/after 数字表写入 perf-baseline.md 增注（含环境、fork 口径、语料说明）
- [ ] JFR 分配剖析 before/after 对照结论写入增注（目标站点：collectComments、children、KindIndex、multiCaptures、sha256）
- [ ] 无 >10% 回归（或已回滚并记录）
- [ ] `BenchmarkSmokeTest` 含新基准防呆（零匹配/零工作防转）；`./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 6 个优化站点落地且行为语义不变（等价性测试背书）
- [ ] JMH before/after 对照完成，无回归（perf-baseline.md 增注为证）
- [ ] JFR 分配剖析 before/after 对照完成，目标站点改善可观察（增注为证）
- [ ] 无 in-scope live defect 被降级；StopBy 探测克隆项已按 Anti-Slacking 规则归入 Deferred But Adjudicated
- [ ] owner doc（perf-baseline.md）已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：每个优化点的调用链连通（collectComments←SuppressionFilter.evaluate←LintEngine.lint；isExact←MetaVarEnv 重绑定；KindIndex←RuleSetRunner.run）；无空方法体/静默跳过/吞异常
- [ ] `./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0

## Deferred But Adjudicated

### StopBy/ellipsis 探测逐节点 env.clone 优化

- Classification: `optimization candidate`
- Why Not Blocking Closure: env.clone 是 lookahead 回滚语义的承重机制，"无捕获快速路径/写时复制"需改 NodeMatcher 匹配契约（design 层改动）；当前 JFR 时间热点榜未见 env.clone 入榜，收益未证实
- Successor Required: `no`
- Successor Path: 若后续 JFR 分配剖析显示 probe clone 进入热点榜，在新 plan 立项并附 design 增补

## Non-Blocking Follow-ups

- Fixer.merge 线性化（需先解决贪心契约的区间树方案，当前规模收益不足）。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
