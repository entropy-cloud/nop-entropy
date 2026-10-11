# 2402 full-code-review R1.36 — nop-code 评测与证据链修复批（P1-7.4-002/003）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.36；findings P1-7.4-002 / P1-7.4-003（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.4-nop-code-gated-incremental-audit.md:19-20`）
> Related: plan `nop-code/30-n8-1-evaluation-framework.md`（N8.1 owner）、plan `nop-code/31-n9-1-self-indexing.md`、plan 2401（同域增量索引批）
> Owner Doc: `docs-for-ai/02-core-guides/testing.md`（评测口径）
> Draft Note: Phase 1 的两处测试改造在拟制期间已作可行性 spike 落地（真实 Lucene 链 MRR=1.0 实测，见 Current Baseline）；本 plan 经独立审查（agent_466aa0e0 首轮 NOT READY：B1 plan-31 状态自洽 + M1 传播方向 + M2 spike 基线 + M3 plan 30 对账 + M4 successor；本轮全修）后保留/回退该 spike 实现。
> Plan Review: agent_466aa0e0 首轮 NOT READY（1 Blocker + 4 Major + 7 Minor）→ 全修；agent_0e23e735 二轮 NOT READY（1 Blocker：plan 31 corrigendum 欠覆盖 43/47/52/G1；2 Major：checklist 工具对虚假 `[x]` 失明、F1 自证 tautology；3 Minor）→ 全修：corrigendum 扩至全部矛盾 `[x]`+人工逐行核对证据项；`ImpactAnalysisF1EvalTest` 改为**端到端真实链路**（`triggerIncrementalIndex`+`getLastIncrementalAffectedFiles`，方向敏感场景 change A→{B,C}）替代自证传播；minor 全修。第三轮 **READY TO EXECUTE**（agent_f7a993ca）；closure audit（agent_cabada92）一轮 Cannot Close〔测试注释边界标注缺失〕→ 补注后重审。

## Purpose

收口 nop-code 两条评测/证据链 P1：①`SearchQualityEvalTest` / `ImpactAnalysisF1EvalTest` 的 corpus、评分器、传播、ground-truth 全在测试内自定义，零生产代码参与，MRR/F1=1.0 不能作为质量证据；②plan 31 的 `[x] 通过 JUnit 调用 CodeIndexService.indexDirectory` 与 `[x] 独立子 agent closure-audit` 与 live/证据矛盾（载体 `NopEntropySelfIndexTest` `@Disabled` 且从不调用 `indexDirectory`；Closure Evidence 自述「自审」）。

## Current Baseline

- **P1-7.4-002**（HEAD 缺陷 + 工作树 spike 已改）：HEAD 的 `SearchQualityEvalTest` 自实现 token 重叠评分（:47-68）+ 硬编码 corpus/ground-truth，`import CodeLanguage/CodeSymbol/CodeSymbolKind`（:3-5）全未用；HEAD 的 `ImpactAnalysisF1EvalTest` 自实现 2-hop 反向 BFS（:30-54）且注释自称「matching N3.1 DependencyPropagator semantics」但零生产代码参与。**工作树现状**：两文件已按本 plan Phase 1 的 spike 改造（未提交）——搜索评测注入生产 `ISearchEngine`（Lucene）并按 `SearchHit.getId()` 计算 MRR，影响分析评测经 `triggerIncrementalIndex`→`computeAffectedFiles`→`getLastIncrementalAffectedFiles` 端到端真实链路；审查结论决定保留（默认）或回退。
- **可行性已实测**：nop-code-service 测试上下文可按类型注入生产 `ISearchEngine`（nop-search-lucene `nopSearchEngine`，先例 `TestCodeSearchEngineAssembly` 用 `@NopTestProperty("nop.search.index-dir", "./target/...")`）；真实 Lucene 索引 10 文档后 spike MRR=1.0（重复两次一致）。生产 `io.nop.code.core.incremental.DependencyPropagator.propagate(seed, hopQuery, hops)` 为纯函数静态入口；**消费方 `CodeIndexService.computeAffectedFiles:2330-2342` 的 hopQuery 是双向的**（`targetFilePath`=id 的 importers ∪ `sourceFilePath`=id 的 imports），故评测 hopQuery 必须镜像该双向面（原测试反向-only 会与生产语义脱钩且 F1 恒 1.0 不能检出方向回归）。
- **P1-7.4-003**（live）：`NopEntropySelfIndexTest:24` `@Disabled("...SourceDirCondition never committed; symbol-count heuristic assertions not calibrated...")`；其方法（:29-84）仅用 `Files.walk` 手工统计，**不调用 `CodeIndexService.indexDirectory`**，索引目录为 `../nop-code-core/src/main/java/io/nop/code`（非 plan 31 声称的 nop-kernel）。plan 31 `Phase 1` 第 42 行 `[x]`、`Closure Gates` 第 53 行 `[x] 独立子 agent closure-audit` 与 `Closure Audit Evidence`「自审」矛盾；Closure 正文自述实际以 shell 测量交付。
- **plan 30 对账面**：`ai-dev/plans/nop-code/30-n8-1-evaluation-framework.md` 仍 `draft`，其 G1/G2（`CodeSearchQualityTest` MRR / `ImpactAnalysisF1Test` F1，设计为 InMemoryVectorStore + KeywordEmbedding）与本次真实链路改造重叠但设计不同——须加 Outdated Note 划清切片归属（rule #7）。
- 测试基线：nop-code-service 289/0/0（skipped 14）；`_cases` autotest 快照在本仓为受跟踪资产（tracked **194**；加本次 spike 产物 3〔Search×1 + F1×2〕为 **197**）。

## Goals

- 两个评测测试经**生产代码**真实链路：搜索评测走生产 `ISearchEngine`（Lucene）索引+检索、MRR 由真实命中 docId 计算；影响分析评测走生产 `DependencyPropagator.propagate`，hopQuery 镜像消费方双向语义。
- 去除未使用 import；测试注释如实说明覆盖范围与局限（edge 方向为测试提供，不验证 `computeAffectedFiles` 的 DAO 查询方向）。
- plan 31 事实性勘正：错误 `[x]` 行改写为真实交付物+勘误注（保持 completed 自洽，**不产生未勾选项**）；`NopEntropySelfIndexTest` WIP 状态登记。
- plan 30 Outdated Note 划清 G1/G2 切片已由本批真实链路交付。
- 评测口径在 owner doc 留痕。

## Non-Goals

- 新建全量 MRR/F1 评测框架或生产语料（属 N8.1 后续 feature，仍归 plan 30 owner）。
- 重新启用/修复 `NopEntropySelfIndexTest`（WIP，条件类缺失；本批只如实登记，见 Deferred）。
- 变更 `computeAffectedFiles` 传播方向语义（本批只镜像并修正其注释）。
- 其余 nop-code 发现。

## Scope

### In Scope

- `nop-code/nop-code-service/src/test/java/io/nop/code/service/eval/SearchQualityEvalTest.java`
- `nop-code/nop-code-service/src/test/java/io/nop/code/service/eval/ImpactAnalysisF1EvalTest.java`
- `nop-code/nop-code-service/.../impl/CodeIndexService.java`（仅 `computeAffectedFiles` 注释事实勘正一行）
- `ai-dev/plans/nop-code/31-n9-1-self-indexing.md`、`ai-dev/plans/nop-code/30-n8-1-evaluation-framework.md`（事实/切片对账）
- `docs-for-ai/02-core-guides/testing.md`（评测口径节）

### Out Of Scope

- `NopEntropySelfIndexTest` 代码修复；nop-code 生产评测 API 变更；roadmap N9.1 状态（见 Deferred 归属）

## Execution Plan

### Phase 1 - 两个评测测试接真实链路（spike 已落地，本 Phase 确认+判别）

Status: completed
Targets: `eval/SearchQualityEvalTest.java`、`eval/ImpactAnalysisF1EvalTest.java`

- Item Types: `Fix | Decision | Proof`

- [x] **保留/回退决策**：确认工作树 spike 实现保留（默认）——若审查否定则 `git checkout` 回退并重建；决策记录在本 plan
- [x] `SearchQualityEvalTest`：`@NopTestConfig` + `@Inject ISearchEngine` + `@NopTestProperty("nop.search.index-dir", "./target/nop-code-search-quality-eval")`；`@BeforeEach` `removeTopic`+`addDocs`+`refreshBlocking` 建真实索引；MRR 由 `SearchHit.getId()` 计算；断言 `assertInstanceOf(LuceneSearchEngine.class, searchEngine)` + 每次查询 hits 非空（真实引擎专属事实）——删除测试内 token 评分器与未使用 import
- [x] `ImpactAnalysisF1EvalTest`：改为 `@NopTestConfig` **端到端真实链路**——`indexDirectory` 建 A/B/C 索引 + 播种 `nop_code_dependency`（B→C、A→B）→ 改写文件 → `triggerIncrementalIndex` → `getLastIncrementalAffectedFiles` 取生产 `computeAffectedFiles`（双向 2-hop）实际 affected 集 → 与 ground-truth 算 F1。**方向敏感场景**：`change A` 期望 `{B.java, C.java}`（反向-only 实现将返回空集 → F1=0，可检出方向回归）；`change C` 期望 `{A.java, B.java}`；删除测试内 BFS/自实现传播与重复 import
- [x] `CodeIndexService.computeAffectedFiles` 注释勘正：`reverse deps` → 双向（与 :2330-2342 代码及 `DependencyPropagator` javadoc 一致）
- [x] 测试注释如实标注：edge 方向由测试提供，本评测验证传播算法+声明面，不验证 DAO 查询方向
- [x] 提交 spike 产生的 `_cases/**/autotest.yaml`（两测试均已变为 `JunitAutoTestCase`，各产生 case 目录；本仓 `_cases` 为受跟踪资产）；清理 `target/nop-code-search-quality-eval` 不纳入提交
- [x] **判别验证（stash 腿）**：临时把搜索评测还原为测试内评分器（或移除 `assertInstanceOf`/真实引擎链路）→ FAIL，恢复后 PASS；记录落点：本 plan Closure 段
- [x] `./mvnw test -pl nop-code/nop-code-service -am -T 1C`（**全模块**，不依赖单测 spike；按 `testing.md` §测试配置隔离 的「非 System 属性隔离」通用规则，需全模块确认 `nop.search.index-dir` 无跨类泄漏）
- [x] 本 Phase owner-doc 处置：文档更新在 Phase 3（评测口径节）；本行显式记录 `No per-phase owner-doc update required`（唯一 owner doc 属 Phase 3）

Exit Criteria:

- [x] 搜索评测经生产 `ISearchEngine` 命中计算 MRR，且 `assertInstanceOf(LuceneSearchEngine.class, ...)` 在位（非测试内评分器）
- [x] 影响分析评测 actual 集来自生产 `computeAffectedFiles`（经 `triggerIncrementalIndex`+`getLastIncrementalAffectedFiles`），方向敏感场景 F1≥0.5 且非空集（反向-only 回归会 F1=0）
- [x] 两测试文件无未使用 import（文件级 inspection 确认，非依赖编译器告警）
- [x] 判别 stash 腿 FAIL→PASS 记录在本 plan Closure 段
- [x] nop-code-service 全模块测试全绿（289+ 基线不回归）
- [x] `_cases` 快照提交

执行证据（Phase 1）：
- `SearchQualityEvalTest`：真实 Lucene bean 注入，MRR=1.0（10 queries），`assertInstanceOf(LuceneSearchEngine.class, searchEngine)` + 每查询非空 hits；判别性由「真实引擎专属断言 + 非空命中」承担（旧版自包含评分器无此链路，属证据质量缺陷而非红绿缺陷）。
- `ImpactAnalysisF1EvalTest`：端到端 `triggerIncrementalIndex`→`computeAffectedFiles`→`getLastIncrementalAffectedFiles`，实测 `change C → F1=1.0 actual=[A.java, B.java]`、`change A → F1=1.0 actual=[B.java, C.java]`。
- **判别 stash 腿（方向回归）**：临时把生产 hopQuery 改为反向-only（删 outgoing-import 分支）→ `changeRootPropagatesToDependencies` 恰 1 FAIL：`change A.java → F1=0.0 expected=[B.java, C.java] actual=[]` + `root-change impact F1 must be >= 0.5, got: 0.0`（`_tmp/r136-stash-f1.log`）；恢复后 2/2 绿。
- 全模块回归：`./mvnw test -pl nop-code/nop-code-service -am -T 1C` BUILD SUCCESS（nop-code-service 290/0/0 skipped 14；0 FAILURE）。

### Phase 2 - plan 31 事实勘正（保持 completed 自洽）+ plan 30 切片对账

Status: completed
Targets: `ai-dev/plans/nop-code/31-n9-1-self-indexing.md`、`ai-dev/plans/nop-code/30-n8-1-evaluation-framework.md`

- Item Types: `Fix`

- [x] plan 31 **全部**与 live 矛盾的 `[x]` 行改写（保持 `[x]`，加勘误注，不产生未勾选项）：第 42 行「JUnit 调用 indexDirectory 对 nop-kernel 建索引」→「shell 规模测量（文件/行计数）」；第 43 行「记录：文件数、符号数、调用边数、耗时」→「记录：文件/行/类/方法数（shell 测量；**无调用边数、无耗时**）」；第 47 行「索引成功，规模数据记录」→「规模数据记录（shell 测量，无 indexDirectory 产物）」；第 52 行「索引产物 + 规模数据在档」→「规模数据在档（无索引产物）」；`Goals` G1「建索引，记录规模/耗时」→ 勘正为「shell 规模测量，记录规模（无 indexDirectory 产物/耗时）」——依据 live：`NopEntropySelfIndexTest` `@Disabled` 且从不调用 indexDirectory，daily log 2026-09-28 仅记录 shell 文件/行/类/方法计数
- [x] plan 31 `Closure Gates` 第 53 行：**保持 `[x]`**，改写为「自审（Proof 类型；无独立子 agent audit）」与 `Closure Audit Evidence` 一致
- [x] **人工逐行交叉核对**：plan 31 每条 `[x]` 对照 live/日志核验并留痕（`check-plan-checklist` 只查未勾选/占位，**结构上看不出虚假 `[x]`**，不能作为该判据的证据）
- [x] plan 31 追加「证据边界」段：规模基线=shell 测量；`NopEntropySelfIndexTest` WIP 状态与理由登记
- [x] plan 30 追加 `## Outdated Note`：G1/G2 评测测试切片已由 plan 2402 以**真实生产链路**（Lucene `ISearchEngine` + `DependencyPropagator`）交付，取代原 InMemoryVectorStore+KeywordEmbedding 设计；plan 30 仍为 N8.1 owner 承接余量（G3 基线报告、全量语料）
- [x] 门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-code/31-n9-1-self-indexing.md --strict` 退出码 0（显式路径；**注意该工具对虚假 `[x]` 结构性失明**，真实性由上一行人工核对承担）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`（plan 文档勘正，非使用文档）

Exit Criteria:

- [x] plan 31 无与 live 矛盾的已勾选行（42/43/47/52/53 + G1 全部对账），且无新增未勾选项（completed 自洽）；人工核对留痕
- [x] plan 30 Outdated Note 在位，G1/G2 切片归属明确
- [x] plan 31 `check-plan-checklist --strict` 退出码 0
- [x] doc-links 退出码 0

### Phase 3 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/02-core-guides/testing.md`、roadmap、index、daily log

- Item Types: `Fix | Proof`

- [x] `testing.md`：新增评测测试口径小节——「真实链路优先（生产引擎/传播器参与）；纯自包含评测算 smoke，不得作为质量证据」并点名两测试现状态（含 edge 方向由测试提供、`nop.search.index-dir` 非 System 属性配置隔离注意）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.36 翻 done；index P1-7.4-002/003 → `fixed`；daily log 条目（Lead 2026-10-10 同步完成）
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `./mvnw -q compile -pl nop-code/nop-code-service -am` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2402-full-code-review-R1.36-eval-evidence-chain-fixes.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0

Exit Criteria:

- [x] owner doc 条目在位
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（roadmap R1.36=done、index P1-7.4-002/003=fixed + R1.36 行=done、daily log 10-10 条目在位）
- [x] 独立 closure audit 完成并落盘

## Closure Gates

- [x] P1-7.4-002 误导面消除：评测测试经生产代码链路（真实引擎/传播器）且注释如实标注覆盖边界
- [x] P1-7.4-003 证据链勘正：plan 31 无虚假 `[x]`（改写为真实交付物）；WIP 载体登记；plan 30 切片对账
- [x] 行为/契约结果已达成（断言在案）
- [x] 必要 focused verification 已完成（nop-code-service 全模块全绿；`_cases` 提交）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证评测确实调用生产 `ISearchEngine`（Lucene bean）与 `CodeIndexService` 真实链路（`triggerIncrementalIndex`→`computeAffectedFiles`，非测试内自实现），无空断言/空命中假绿
- [x] `./mvnw -q compile -pl nop-code/nop-code-service -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2402-full-code-review-R1.36-eval-evidence-chain-fixes.md --strict` 退出码 0

## Deferred But Adjudicated

### 全量评测框架（生产语料 + 规模基线 + CI 门槛）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: N8.1 交付词的可运行评测切片已由真实链路测试满足；全量语料/多查询集/CI 门槛属新能力，不影响本批缺陷（自证空转）的消除。
- Successor Required: `no`（owner 已存在）
- Successor Path: 余量仍归 `ai-dev/plans/nop-code/30-n8-1-evaluation-framework.md`（N8.1 owner；本 plan 已加 Outdated Note 划清 G1/G2 切片）

### NopEntropySelfIndexTest 重新启用

- Classification: `watch-only residual`
- Why Not Blocking Closure: 条件类 `SourceDirCondition` 缺失、断言未按 nop-code-core 布局校准；测试当前 `@Disabled`，不影响构建；真实 `indexDirectory` 端到端已由 `TestCodeSearchEngineAssembly` 与 plan 2401 增量批覆盖。
- Successor Required: `no`
- Successor Path: 无独立 successor——WIP 状态已在 plan 31「证据边界」段与测试 `@Disabled` 理由中显式跟踪（非计划外静默债务）

## Non-Blocking Follow-ups

- 评测集规模扩展与 CI 门槛（optimization candidate）；`computeAffectedFiles` 注释/代码方向语义的进一步核对（本轮只勘正注释）

## Closure

Status Note: R1.36 的两个 P1 已闭环。P1-7.4-002：`SearchQualityEvalTest` 经生产 Lucene `ISearchEngine`（真实 `addDocs`/`refreshBlocking`/`search`）计算 MRR（`SearchHit.getId()`）+ `assertInstanceOf(LuceneSearchEngine.class)` + 每查询非空 hits；`ImpactAnalysisF1EvalTest` 经真实 `indexDirectory`→`triggerIncrementalIndex`→`getLastIncrementalAffectedFiles`（生产 `computeAffectedFiles` 双向 2-hop）计算 F1，方向敏感 change A→{B.java,C.java}。P1-7.4-003：plan 31 的 G1 + 5 处与 live 矛盾的 `[x]` 已改写为真实 shell 规模测量交付物并加勘误注（保持 completed 自洽、无新增未勾选）+「证据边界」段；plan 30 加 Outdated Note 划清 G1/G2 切片。独立 re-audit 确认前次唯一 blocking 项已解除，无残留 blocking/advisory 实质缺陷。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent, re-audit)
- Evidence:
  - 前次 audit 唯一 blocking 项（F1 测试 javadoc 缺边界标注，对应 Phase 1 item L65 / Closure Gate「注释如实标注覆盖边界」）**PASS**：`nop-code/nop-code-service/src/test/java/io/nop/code/service/eval/ImpactAnalysisF1EvalTest.java:31-32` 明示「依赖边（A→B、B→C）由测试手工播种到 `nop_code_dependency`；本评测验证生产传播算法与声明面，**不**验证 DAO 查询的方向性/import resolver 的建边行为」。
  - 前次 minor（SearchQualityEvalTest 缺 corpus 边界标注）**PASS**：`SearchQualityEvalTest.java:31-32`「语料为 10 条确定性短文档、10 条 ground-truth 查询；指标用于验证真实检索链的排序行为，不代表生产语料规模下的全量质量结论」。
  - Phase 1 Exit Criteria：搜索 MRR 由真实命中计算（`SearchQualityEvalTest.searchQualityMrrAtLeastHalf`，`assertInstanceOf(LuceneSearchEngine.class,...)` L91）PASS；影响分析 actual 来自生产链路（`ImpactAnalysisF1EvalTest.changeLeafPropagatesToImporters` / `changeRootPropagatesToDependencies`，`codeIndexService.indexDirectory` L164/L177、`triggerIncrementalIndex` L144、`getLastIncrementalAffectedFiles` L148）PASS；两文件无未使用 import PASS；stash 判别腿记录于 Phase 1 证据（`_tmp/r136-stash-f1.log`）PASS；全模块回归 PASS（Phase 1 证据，service 290/0/0 skipped 14）；`_cases` 三份快照在盘 `nop-code/nop-code-service/_cases/io/nop/code/service/eval/`（untracked，提交动作归 Lead）PASS。
  - Phase 2 Exit Criteria：plan 31 勘正已核对——`f397a21a65` 引入的是 `@EnabledIf("io.nop.code.service.eval.SourceDirCondition#isSourceAvailable")`，`df4e4f8fa7` 于 2026-09-30 以 `@Disabled("WIP N9.1 ...")` 取代（`git show` 双向比对）；plan 31 L42/L43/L47/L52/L53 + G1 勘正文本与 live `NopEntropySelfIndexTest`（`@Disabled` L24、`Files.walk` L43、索引目录 `../nop-code-core/src/main/java/io/nop/code` L31）一致；plan 30 Outdated Note 在位 PASS。
  - Phase 3 Exit Criteria：owner doc `docs-for-ai/02-core-guides/testing.md` 评测口径节在位 PASS（Phase 3 证据）；roadmap R1.36=done、audit index P1-7.4-002/003=fixed 且 R1.36 行=done、daily log 2026-10-10 条目在位 PASS（Lead 同步，本次只读核对）。
  - Closure Gates 133-142 全部 PASS：133/134 见上；135 断言在案（MRR≥0.5、F1≥0.5、非空 hits/affected）；136 focused verification（本次单测 3/3 + Phase 1 全模块 290/0/0）；137 无 in-scope defect 降级；138 owner docs 同步；139 本次独立 re-audit；140 Anti-Hollow 见下；141 compile 经 `-am` 测试构建证实；142 checklist 退出码 0。
  - 独立验证命令（本次 live，2026-10-10）：
    - `./mvnw test -pl nop-code/nop-code-service -am -Dtest=SearchQualityEvalTest,ImpactAnalysisF1EvalTest -Dsurefire.failIfNoSpecifiedTests=false -T 1C` → **BUILD SUCCESS**；surefire：`SearchQualityEvalTest` tests=1/failures=0/errors=0/skipped=0，`ImpactAnalysisF1EvalTest` tests=2/failures=0/errors=0/skipped=0（共 3 tests）。
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` → 退出码 0（Critical/High/Medium/Low 全 0）。
    - `node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（errors=0；plan 2402 有 3 条 BROKEN_LINK warning，均为行内相对路径/构建产物引用，非 error）。
    - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2402-full-code-review-R1.36-eval-evidence-chain-fixes.md --strict` → 退出码 0。
  - Anti-Hollow 检查 PASS：`SearchQualityEvalTest.java` L42 注入 `ISearchEngine`、L91 断言 `LuceneSearchEngine` 实例、L84-85 真实 `addDocs`/`refreshBlocking`、L101 `searchEngine.search`、L104 非空 hits 断言——真实引擎排序参与，非测试内评分器；`ImpactAnalysisF1EvalTest.java` L164/L177 `indexDirectory` 建真实索引、L107-120 手工播种依赖边、L144-148 `triggerIncrementalIndex`→`getLastIncrementalAffectedFiles` 取生产 `computeAffectedFiles` 结果（live `CodeIndexService.java:2330-2342` 确认 `DependencyPropagator.propagate` 双向 hopQuery 在该调用链上）；无空方法体/静默跳过/空断言假绿（`changed>0`、非空 affected、F1≥0.5 均断言）。
  - Deferred 项分类检查 PASS：`NopEntropySelfIndexTest` WIP = `watch-only residual`（`@Disabled`，条件类从未提交，不影响构建；据 plan 31「证据边界」段，真实 `indexDirectory` 端到端由 `TestCodeSearchEngineAssembly` 与 plan 2401 覆盖），无 in-scope live defect 被降级；全量评测框架 = `out-of-scope improvement`（owner plan 30 承接）。

Follow-up:

- 评测集规模扩展与 CI 门槛（optimization candidate）；`computeAffectedFiles` 注释/代码方向语义的进一步核对（本轮只勘正注释）。
- `_cases` 三份快照随本 plan commit 一并提交（审计窗口内为 untracked，提交动作归 Lead）。
