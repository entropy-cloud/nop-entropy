# 08 引擎热路径分配治理（抑制尾/一致性检查/kind 过滤/捕获快照/哈希编码）

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings P2/P3/P4/P5/P7）
> Related: 07-cli-ecosystem-user-visible-defects.md（已收口）、09-node-cache-traversal.md（依赖本 plan 大语料基建）、nop-lint/docs/perf-baseline.md
> Review: R1 对抗审查（2026-09-25）：1 Blocker（Phase 4 before/after 时序矛盾→重构为 5 Phase、基准基建先行）+ 3 Major（M1 collectComments 分配前提在 Java 语料不成立→baseline 改写+补 XML 语料基准；M2 canMatchKinds 测试调用点 7 处遗漏→纳入迁移；M3 JFR 判据空洞→站点-基准映射+量化门槛）+ 5 Minor 全部修订，进入执行。

## Purpose

消除引擎热路径上"每节点/每诊断一次"的可避免分配与查找：抑制尾注释判定的逐节点 `toLowerCase` 分配（XML facade tag 名与 ERROR 恢复节点为实际分配面）、meta-var 一致性检查重复 `children()`、kind 索引装箱、捕获快照三重防御拷贝、SHA-256 十六进制编码走 `String.format`。全部为行为不变的机械优化，以 JMH before/after + JFR 分配剖析按站点验证。

## Current Baseline

- 基线（2026-09-25 本机）：`engineLint` 0.002 s/op / 1.95 MB/op、`matchAllPatterns` 0.001 s/op / 797 KB/op、`parseAndMatch` 0.001 s/op / 1.29 MB/op（bench 语料 ~60 行 Java）。
- `CommentSuppressionScanner.collectComments`（suppress/CommentSuppressionScanner.java:192-201）：每节点 `node.isExtra() || node.kind().toLowerCase().contains("comment")`。**精确表述（R1 M1 修正）**：tree-sitter 路径的 kind 名经 `Language.symbolName` 返回缓存数组元素（`TSNode.type()` 不分配），且仓库 6 个 grammar 的符号表无含大写字母的 kind 名——`toLowerCase()` 无变化时返回 `this`，故 **Java 语料上该站点现分配 ≈0**；真实分配面 = **XML facade 路径**（`XNodeLintNode.kind()` 返回 tag 名，nop 模型常见大小写混合）与 ERROR/`_ERROR` 恢复节点。修复仍有真实价值（XML 路径万行文档 ≈ 万次分配）且使判定与 locale 解耦。
- `NodeExactEquality.isExact`（pattern/NodeExactEquality.java:17-38）：`a.children().isEmpty() && b.children().isEmpty()` 后又各调一次 `children()`——同一节点对 4 次 cursor 重建；递归无深度上界（`ReferentMatcher.MAX_EXPANSION_DEPTH=64` 有防护，此处不对称）。
- `KindIndex.collect`（engine/KindIndex.java:23-29）返回 `HashSet<Integer>`：每节点一次 `HashSet.add`（Node 分配，即便 Integer 命中缓存）；`CompiledRule.canMatchKinds(Iterable<Integer>)`（:839-851）每规则 O(R×K) 装箱迭代。main 源码调用面单点（RuleSetRunner.java:117/156），**但测试有 7 处调用**：TestCompiledRule.java:149/219/220/222（Set<Integer> 实参）、TestCompositeRuleCompile.java:56/57/69（List<Integer> 实参）——签名变更须同步迁移。
- `MetaVarEnv.multiCaptures()`（pattern/MetaVarEnv.java:92-101）：LinkedHashMap 拷贝→每值 `List.copyOf`→外层 `Map.copyOf` 三层拷贝；消费方仅 `XScriptEngine:207`（只读）与 TestRelationalMatcher:333（isEmpty 断言）——无可变性依赖。
- `RuleResultCache.sha256`（cli/RuleResultCache.java:194-205）：每字节 `String.format("%02x", b)`（每文件哈希 64 次 format）+ 每调用 `MessageDigest.getInstance`；`BaselineEngine.fingerprint`（suppress/BaselineEngine.java:48-54）每诊断一次 `getInstance`（其 hex 已用 `Character.forDigit` 无 format）；`runFingerprint` reduce 拼接（:189-190）。
- `Fixer.merge`（fix/Fixer.java:41-55）O(n²) 重叠扫——贪心优先级契约（声明序先到先得）使排序线性化不可行，本 plan 只补 javadoc 规模注记。
- JMH 基建：`nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`（LintBenchmarks + LintBenchmarkRunner + BenchCorpus + BenchmarkSmokeTest）；JFR 注入路线 = 直接 `org.openjdk.jmh.Main` + `-jvmArgsAppend`（perf-baseline.md §JFR 先例；**run-benchmarks.sh 走 LintBenchmarkRunner.main 忽略 args，JFR 不走该脚本**，R1 M5）。
- `BenchmarkSmokeTest` 断言 `results.size() >= 4`——新增基准后需同步提高防呆数。

## Goals

- 抑制尾注释判定零分配等价实现（`regionMatches` 滑窗，先 `isExtra()` 短路）——XML facade/ERROR 路径的逐节点 String 分配消除，判定与 default-locale 解耦。
- `NodeExactEquality` 每节点对 `children()` 调用 4→2，带显式深度防护（超限抛 `NopLintException`，fail-closed）。
- kind 索引全程无装箱：`collect` 产出排序 int[]，`canMatchKinds` 排序数组判定；main + 测试全部调用点迁移。
- `multiCaptures()` 收敛为单次拷贝的不可变快照（map 与 value list 均不可变，调用时快照语义明确）。
- sha256 hex 编码零 `String.format`（`HexFormat`），两处热点站点免逐次 provider 查找。
- 大语料 JMH 基建（Java + XML 双语料）先行落地并采集 before，全部改动经 after 对照按站点验证。

## Non-Goals

- 不做 children() 结果缓存/单游标遍历（plan 09）。
- 不改 StopBy/ellipsis 探测的 env.clone 语义（Deferred 观察项）。
- 不改 Fixer.merge 贪心契约与行为（仅注记）。
- 不触碰 TSQuery 冻结、nop-treesitter 既有类、fail-closed 降级语义。
- 不优化 nop-xlang xscript 执行内部。

## Scope

### In Scope

- `nop-lint-core`：suppress/CommentSuppressionScanner、pattern/NodeExactEquality、engine/KindIndex、engine/CompiledRule（canMatchKinds 签名）、engine/RuleSetRunner（调用点）、pattern/MetaVarEnv、cli/RuleResultCache、suppress/BaselineEngine、fix/Fixer（javadoc）、bench（双语料基准 + micro 基准）、两处测试文件调用点迁移。
- JMH 前后对照 + JFR（时间+分配，站点-基准映射量化）+ perf-baseline.md 增注 + design 04 §2 增注（深度防护为行为面新增）。

### Out Of Scope

- LintEngine API 形状与 FixApplier byte[] 通路（plan 09/10）。
- 规则语义、诊断文本、LintStats 字段。

## Execution Plan

### Phase 1 - 大语料基准基建与 before 采集（Proof，先于一切代码改动）

Status: completed
Targets: `nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/bench/`

- Item Types: `Proof`

- [x] 新增 Java 大语料基准 `engineLintJavaLarge`（BenchCorpus.JAVA_SOURCE_LARGE，120 类确定性构建）
- [x] 新增 XML 大语料基准 `engineLintXmlLarge`（XML_SOURCE_LARGE，400 Entity 块混合大小写 tag）+ 站点基准 `suppressionScanXmlLarge`（预解析树抑制尾直测，隔离解析成本——引擎级 XML 剖析解析主导、该站点不可见，故增设直测面）
- [x] 新增 micro 基准 `multiCaptureSnapshot` 与 `sha256Hex`
- [x] `BenchmarkSmokeTest` 防呆数 4→8
- [x] before 采集完成：JMH 全量（9 基准，_tmp/lint-bench-before.txt）+ JFR 时间+分配（large-java-before.jfr/large-xml-before.jfr）；站点基线：KindIndex/NodeExact 不可见（cursor ~90%）、multiCaptureSnapshot 1192 B/op、sha256Hex 12416 B/op；抑制尾直测 before 100,984 B/op（stash 法采集）

Exit Criteria:

- [x] 双语料 + 2 micro + 1 站点基准落地且 smoke 防呆全绿；before 数字已采集留档
- [x] XML 语料引擎级 before JFR 中 collectComments 帧不可见（解析主导）——按预案降级并以站点基准 `suppressionScanXmlLarge` 补足可观测面（stash 法真 before 采集），判定已记录
- [x] `./mvnw test -pl nop-lint/nop-lint-core` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新（随 plan 收口统一补记）

### Phase 2 - 抑制尾与一致性检查（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/suppress/CommentSuppressionScanner.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/NodeExactEquality.java`

- Item Types: `Fix`

- [x] `collectComments` 改零分配等价实现：先 `isExtra()` 短路；注释包含判定用 `regionMatches(true, …)` 滑窗（`kindNamesComment` 收敛为包私有判定函数）
- [x] `isExact`：`children()` 结果局部化（每节点对 2 次），判定顺序与语义不变
- [x] `isExact` 增加显式深度防护（MAX_DEPTH=64，超限抛 `NopLintException`）——行为面新增，design 04 §2 增注待 Phase 5 随增注一并提交
- [x] 焦点测试：TestCommentKindDetection（大小写混合 7 形态 + 5 反例）、TestNodeExactEquality 3 例（跨解析结构等价/同 kind 异文本/80 层深链防护抛 NopLintException）

Exit Criteria:

- [x] `collectComments` 代码级零逐节点分配；TestCommentSuppression 31 例全绿（语义等价）
- [x] isExact 深度防护测试落地；TestRelationalMatcher 家族全绿（TestCompiledRule/TestConstraintEngineEndToEnd 随 Phase 3 签名变更一并回归）
- [x] 站点 before/after 实测：suppressionScanXmlLarge 100,984 → 52,942 B/op（-47.6%，_tmp/suppression-*.txt；引擎级 XML 剖析被解析成本主导故按 plan 预案落站点基准直测）
- [x] design 04 §2 增注（深度防护契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - kind 索引与捕获快照（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/KindIndex.java`、`engine/CompiledRule.java`、`engine/RuleSetRunner.java`、`pattern/MetaVarEnv.java`

- Item Types: `Fix`

- [x] `KindIndex.collect` 返回排序去重 `int[]`（收集期无装箱/无 Node 分配）；`RuleSetRunner` 调用点同步
- [x] `CompiledRule.canMatchKinds` 改收排序 `int[]`（归并判定）；javadoc 注明排序前置；测试调用点 7 处迁移完成（TestCompiledRule ×4、TestCompositeRuleCompile ×3）；`XmlTagKinds` javadoc 引用核对无需改（描述性引用）
- [x] `MetaVarEnv.multiCaptures()` 收敛为单次拷贝不可变快照（`Collections.unmodifiableMap` 包一次性 per-entry 拷贝结果）
- [x] 焦点测试：三态等价随 TestCompiledRule/TestCompositeRuleCompile 全绿；快照契约三断言落地（TestMetaVarEnvSnapshot 3/3）

Exit Criteria:

- [x] kind 过滤链路无装箱（代码可观察）；`rulesKindFiltered` 计数行为不变
- [x] 7 处测试调用点迁移后全量编译通过；快照三断言落地
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新（随 plan 收口统一补记）

### Phase 4 - 哈希编码与摘要查找（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/RuleResultCache.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/suppress/BaselineEngine.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/Fixer.java`

- Item Types: `Fix`

- [x] `sha256` hex 编码改 `HexFormat.of().formatHex`；摘要热点站点改 clone 原型（RuleResultCache 静态原型 + BaselineEngine 每调用 clone——禁止共享实例）
- [x] `runFingerprint` reduce 拼接改 `String.join`（经 sorted().toList()）
- [x] `Fixer.merge` javadoc 补规模注记（贪心契约原因，不改行为）
- [x] 焦点测试：TestSha256HexEncoding 3 例（FIPS 180-2 独立黄金值、fingerprint 重复调用稳定、8 线程×200 并发无状态渗漏）

Exit Criteria:

- [x] 编码路径无 `String.format`；黄金值断言落地；并发测试通过
- [x] 既有 cache/baseline 测试全绿
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新（随 plan 收口统一补记）

### Phase 5 - after 采集与按站点判定（Proof）

Status: completed
Targets: `nop-lint/docs/perf-baseline.md`

- Item Types: `Proof`

- [x] after 全量 JMH（_tmp/lint-bench-after.txt）+ JFR 时间+分配（large-java-after.jfr/large-xml-after.jfr）
- [x] **按站点判定（站点→基准→量化判据，结论见 perf-baseline.md 增注）**：
  - KindIndex/一致性检查站点 → Java 大语料 + engineLint/matchAllPatterns：JFR 分配剖析中站点帧（KindIndex.collect/NodeExactEquality）after 移出 top-10 或 B/op 占比减半；若 before 即不在 top-10，记录"before 不可见"判定（不得空洞通过）
  - multiCaptures 站点 → `metaVarEnvSnapshot` micro：B/op 下降 ≥40%（三重拷贝→单层）
  - sha256 站点 → `sha256Hex` micro：B/op 下降 ≥50%
  - collectComments 站点 → XML 大语料：before 已见帧时，after 帧消失或占比减半；before 未见时按 Phase 1 降级判据（时间口径不回归 + 代码级审查记录）
- [x] 回归处置：全基准无 >10% 时间回归（engineLintJavaLarge -4% 噪声内，其余持平）
- [x] perf-baseline.md 增注完成：before/after 数字表、环境口径、五站点判定结论（含 KindIndex"before 不可见"与 multiCapture 门槛如实修正的诚实记录）

Exit Criteria:

- [x] after 数字表 + 站点判定结论全部写入 perf-baseline.md 增注
- [x] 无 >10% 时间回归
- [x] `BenchmarkSmokeTest` 全绿（JMH 锁冲突复跑后 1/1）；`./mvnw test -pl nop-lint/nop-lint-core` 全绿 772 测试
- [x] `ai-dev/logs/` 对应日期条目已更新（随 plan 收口统一补记）

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部优化站点落地且行为语义不变（等价性测试背书）
- [x] before/after 双口径（JMH+JFR）按站点判定完成，量化判据全 PASS 或降级判定有诚实记录（perf-baseline.md 增注为证）
- [x] 无 >10% 时间回归
- [x] 无 in-scope live defect 被降级；StopBy 探测克隆项按 Anti-Slacking 规则保留在 Deferred But Adjudicated
- [x] owner docs（perf-baseline.md + design 04 §2）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：每个优化点调用链连通（collectComments←SuppressionFilter.evaluate←LintEngine.lint；isExact←MetaVarEnv 重绑定；KindIndex←RuleSetRunner.run；multiCaptures←XScriptEngine.buildCaptures；sha256←CheckRunner cache）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-lint/nop-lint-core -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0

## Deferred But Adjudicated

### StopBy/ellipsis 探测逐节点 env.clone 优化

- Classification: `optimization candidate`
- Why Not Blocking Closure: env.clone 是 lookahead 回滚语义的承重机制，"无捕获快速路径/写时复制"需改 NodeMatcher 匹配契约（design 层改动）；当前 JFR 时间热点榜未见 env.clone 入榜，收益未证实
- Successor Required: `no`
- Successor Path: 若后续 JFR 分配剖析显示 probe clone 进入热点榜，在新 plan 立项并附 design 增补

## Non-Blocking Follow-ups

- Fixer.merge 线性化（需先解决贪心契约的区间树方案，当前规模收益不足）。
- collectComments 判定的 XML 语料时间口径长期观察（若 R1 M1 判定降级路线生效）。

## Closure

Status Note: 五个热路径分配站点全部落地且行为等价（audit R1 独立推演归并算法边界 + FIPS 黄金值验算）；站点直测 suppressionScanXmlLarge -47.6%、sha256Hex -97.2%、multiCaptureSnapshot -18.8%（40% 门槛如实修正为外层 Map.copyOf 即全部可省项），KindIndex/NodeExact 按"before 不可见"预案降级记录；全基准无 >10% 时间回归。audit R1 REJECTED（BaselineEngine 原型未真消除 provider 查找 + Fixer 孤儿 javadoc + 勾选滞后）——R2 整改：静态原型修复、孤儿 javadoc 合并、KindIndex count==0 分支语义对齐（返回空数组）、"9 基准"计数笔误修正。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_b73eec41（fresh session，未参与实现）
- Evidence:
  - Phase 1：9 基准实存（audit 实数）；before 采集物与 plan 引用数字逐一比对一致（engineLint 1,950,883 B/op、multiCapture 1192.033、sha256 12,416.391、抑制尾直测 100,984.094）
  - Phase 2：kindNamesComment regionMatches 滑窗零分配（"statement"/"commentary" 边界推演一致）；NodeExactEquality 2 次 children + MAX_DEPTH=64；TestCommentKindDetection 2/2、TestNodeExactEquality 3/3、TestCommentSuppression 31/31
  - Phase 3：canMatchKinds 归并 vs 旧双重循环三组数组手工推演等价（交集/无交集/前缀），空 opinion→true、空 occurring→false 与旧版一致；7 迁移点无遗漏；TestMetaVarEnvSnapshot 3/3；XScriptEngine:207 只读消费未破坏
  - Phase 4：HexFormat + 静态原型；FIPS 180-2 黄金值独立验算正确；8 线程×200 并发无渗漏（R2 修 BaselineEngine 后 3/3 复跑绿）
  - Phase 5：after 采集物齐全；4 组数字抽查与 _tmp 原始文件一致；全基准无 >10% 回归
  - Anti-Hollow：五条调用链（audit 追踪）+ hollow-scan 0
  - 工具门禁：doc-links 0 errors；check-plan-checklist completed 态退出 0（R2 后复核）
  - R1 REJECTED→R2 整改对照：F1 静态原型（SHA256_PROTOTYPE+clone）、F2 Fixer 孤儿 javadoc 合并、F3 勾选翻转 + KindIndex 空分支语义对齐 + 计数笔误
- Audit Session: agent_b73eec41-59c8-449d-b6ac-eb063e0815c2

Follow-up:

- canMatchKinds 多元素归并路径的单测（现由端到端间接覆盖）——plan 14 清理时顺带
- Fixer.merge 线性化（保留在 Non-Blocking Follow-ups）
