# WI13 Closure Audit——23-wi13-equi-join-operator.md

- Audit 日期：2026-10-03（首轮）
- Auditor：独立子 agent（fresh session，与 plan 起草会话及接管实现会话均非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `d345e4e850`（分支 add-stream-sql，工作树 clean）
- **最终裁定：FAIL（1 项 Major 代码面缺陷 MAJ-1 + 3 项 Minor 文本/证据级）——roadmap WI13 `todo` → `done` 翻转冻结，直至 MAJ-1 修复并经新一轮独立 audit 复核**

## 0. 裁定摘要

功能面与证据面基本成立：EquiJoinOperator 四态语义判别力充分、checkpoint/restore 证据真实、buildJoin 真实装配落地、A6 推翻结论结构成立、四模块实跑全绿（runtime 1222 / flow 159 / core 1665）、门禁全 0、_gen 纪律干净、无静默跳过。**唯一 Major 是 durable 通道的跨 key 修剪泄漏**：watermark 修剪/收尾循环对全部 buffer key 发起 keyed 删除，但引擎 keyed MapState 的读写按 `(namespace, currentKey)` 定 scope，循环内从不重设 currentKey——除「最后处理的那个 equiKey」外，所有 key 的 durable 条目在水印修剪后永久残留（探针实证）。这违反本轮自称的「trim 时 keyed 通道同步删除」§十 合规口径与 design §3 / javadoc 的有界状态结构性声明，且既有断言因 scope 盲区对其假绿。按审计规则，代码面缺陷冻结 roadmap 翻转。

---

## 1. 逐项审计核验

### 1.1 EquiJoinOperator 四态语义判别（审计项 1）——PASS

落点 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java`（429 行，hash/window 双形态）。

- **配对**：`processElement` :152-165 → `emitMatches` :233-248 遍历对侧 buffer 的 (ts,seq) 有序键（`sortedTimestamps`，PerKeyOrderedBuffer.java:92-100），窗口形态跳过异窗条目 :237-239，逐条 `output.collect` 并双侧翻转 `matched` 旗标 :241-242 + 对侧 durable 副本同步重写 :246；到达记录自身 durable 副本在循环后写入时已带旗标（:163-164 在 `emitMatches` 之后）。
- **补齐侧判别**：`isCompletionSide` :273-284——LEFT 只补 L 侧、RIGHT 只补 R 侧、FULL 双侧、INNER 恒 false；`completeUnmatched` :250-257 只发射 `!matched` 条目。`JoinType.isOuter()`（JoinType.java:26-28）的消费在 `isCompletionSide` 的分支结构中成立（INNER 短路返回 false）。
- **TestEquiJoinHashSemantics 7 用例判别力（反事实推演）**：
  - *补齐在 watermark 而非到达时*：`leftCompletesOnlyAtWatermark` :84-89——喂入 L 侧 a(10) 后断言输出为空 :85，`wm(50)` 后才断言 `J|a|null` :89。若实现改为到达即补，:85 以 actual `["J|a|null"]` vs expected `[]` 失败；若实现永不补齐，:89 以 actual `[]` 失败。双侧判别成立。
  - *matched 旗标抑制重复补发*：同用例 :90-96——y(60)/z(70) 配对后 `wm(100)` 断言输出仍恰为 2 条 :95-96。若旗标失效，y 会在水印处被再补发 `J|y|null`，actual 变 3 条，:95 以列表内容失配失败。判别成立。
  - *INNER 零补齐*：`innerEmitsEagerPairsOnly` :76 与 `isOuterConsumedDrivesCompletionBranch` :165-178（同输入下 INNER `[]` vs FULL `["J|a|null"]` 对照）——若 INNER 误入补齐分支，两处以 actual 含 `J|a|null` 失败。
  - *RIGHT/FULL*：`rightCompletesOnlyAtWatermark` :105-121（:108 到达不补、:110 水印补 `J|null|b`、:114 L 侧不被补、:120 matched 不重补）；`fullCompletesBothUnmatchedSides` :124-132（双侧各恰一条，:131 以 size 4 vs 2 判别重复补发）。
  - *越界不配对（修剪语义）*：`leftCompletesOnlyAtWatermark` :93 断言恰 `["J|a|null","J|y|z"]`——若 a 未被 `wm(50)` 修剪，后到的 z 会与残留的 a 配出 `J|a|z`，:93 失配。修剪语义经由此断言间接判别。
  - *跨 key durable 同步删除断言存在 scope 盲区*：`innerEmitsEagerPairsOnly` :77-78 断言 `bufferedCount()==0` 与 `keyedBufferEntries()==0`——后者按 currentKey scope 读取（见 MAJ-1），通过不能证明全 scope 干净（假绿，详见 §3 MAJ-1(d)）。工作视图断言 :77 本身真实判别。
- **TestEquiJoinWindowTimeout 4 用例**见 §1.6；**checkpoint 2 用例**见 §1.3。隔离实跑 7/7、4/4、2/2 全绿（§2）。

### 1.2 §十 绑定合规与 PerKeyOrderedBuffer 复用（审计项 2）——部分 FAIL（MAJ-1）

roadmap :66 绑定句原文核验：「§十 明确拒绝『自管 HashMap 做窗口状态』，故 WI12 的每 key 有序缓冲必须落在 namespace-based keyed state 上（归 WI12 完成判定）」——join 缓冲同规则（join-operator.md §5 复用义务表 + §3「双侧缓冲以 keyed state 承载……join 缓冲同规则」）。

- **持久化权威 = keyed MapState：PASS**。`open()` 自建 keyed backend（EquiJoinOperator.java:110-113，`stateBackend.createKeyedStateBackend(Object.class)` + `applyPendingRestoreState()`，ProcessOperator 先例形态）；`bufferState` 绑定 `rawKeyedBackend().getMapState(new MapStateDescriptor("equi-join-buffer", ...))` :115-118；逐元素 durable copy（`persistCurrent` :311-320，元素到达 :164 与 matched 翻转 :246 两个写入点）；snapshotState 经 `super.snapshotState(context)` 携带 keyed lineage（:122-128，AbstractStreamOperator.java:307-312 以 `"keyed-state"` 名放入 `putKeyedState`），工作视图另经 operator-state 传输（:126）。
- **角色标注如实：PASS（一处注释陈旧，MIN-1）**。字段 `buffer` 注释「working view」/`bufferState` 注释「durable copy」:86-89，javadoc :57-70 完整声明双通道设计与「keyed MapState reads are scoped to the CURRENT key」的引擎事实。但 :86 括注「(rebuilt from keyed state on restore)」与实际不符——restoreState :143-148 从 operator-state 传输恢复工作视图，不从 keyed state 重建（javadoc :68-70 的表述才是准确口径）。
- **trim 时 keyed 通道同步删除：FAIL（MAJ-1）**。`trimToWatermark` :290-292 / `dropAll` :294-296 → `removeDurable` :298-309 确实逐条 `bufferState.remove(durableKey)`，但调用发生在 watermark 循环 `completeAndTrimHash` :187-194 / `fireReachedWindows` :208-215 内——循环遍历**全部** buffer key（跨多个 equiKey），而引擎 `MemoryMapState.remove` 按 `backend.getTypedNamespaceAndKey()`（= `(namespace, currentKey)`，MemoryMapState.java:68-70、:123-128）定 scope；currentKey 停留在最后处理的元素 key（`processElement` :155 唯一设置点）。**非 current equiKey 的 durable 删除全部静默 no-op**。探针实证见 §3 MAJ-1。
- **PerKeyOrderedBuffer 真实复用（git mv 而非另建）：PASS**。`git show d345e4e850 --name-status -M` 判定 `R099 nop-stream/.../runtime/operators/windowing/PerKeyOrderedBuffer.java → nop-stream/.../core/common/buffer/PerKeyOrderedBuffer.java`（相似度 99%，仅 package 声明变更）；`git log --follow` 历史连续贯穿 WI12 四个 commit（66ef34f979→5ed2b57750→ec9b6af5eb→d345e4e850）。runtime/windowing 旧路径无残留副本。OverWindowOperator 及 `TestPerKeyOrderedBuffer` 同步改 import（WI12 隔离 12/12 绿，§2）。

### 1.3 checkpoint/restore 证据（审计项 3）——PASS

`TestEquiJoinWithCheckpoint` 2 用例隔离实跑 2/2 绿。

- **keyed lineage 断言形式真实判别**：:75-87 遍历 `snapshot.getKeyedStates().values()`，要求存在 `StateSnapshot` 且其 `getStates().keySet()` 含 `equi-join-buffer`。若 `open()` 未自建 keyed backend 或未创建 MapState，backend 的 states 表无此名（AbstractStreamOperator.java:307-313 仅在 `keyedStateBackend != null` 且快照非空时放入），断言以 `joinStateInLineage == false` 失败。判别成立。
- **restore 后 setCurrentKey 读 durable 条目**：:90-98——op2.restoreState 后 `setCurrentKey("k")` 再断言 `keyedBufferEntries()==1`。由于引擎 keyed 读取按 current key 定 scope（MemoryMapState.java:92-96），漏掉 setCurrentKey 该断言将得 0 而失败——断言形态正确。
- **join 跨界连续**：:100-102 restore 后喂 R 侧 b(20) 与 restore 前缓冲的 L 侧 a(10) 配对出 `J|a|b`；随后 `wm(100)` 清态断言 :104-107。
- **durable matched 旗标判别力**：`matchedFlagSurvivesRestoreNoDoubleCompletion` :111-139——restore 后 `keyedAnyMatched()`（EquiJoinOperator.java:399-409，直读 keyed durable 条目）断言 true :130：若 `emitMatches` 缺少对侧 durable 重写（:246），restore 后 durable 旗标为 false，:130 失败。随后 `wm(100)` 断言零补发 :136：若工作视图恢复丢旗标，a 会被补发 `J|a|null`，:136 失败。双通道各自判别。
- **OverWindowOperator 孤儿句柄同步修复：PASS 且机制属实**。`MemoryStateSerDe.restoreState` 确实 `states.clear()`（:286）后按名重建 state 对象（`states.put(stateName, stateObj)` :381、`new MemoryMapState<>` :409）——`open()` 期获取的句柄在 restore 后成为孤儿，经其写入绕开快照谱系。修复形态：两算子均在 `super.restoreState(...)` 之后重新 `getMapState` 重绑（EquiJoinOperator.java:139-142；OverWindowOperator.java:125-126，commit d345e4e850 对该文件 +8 行）。顺序正确（重绑在 backend 重建之后）。WI12 既有测试不破坏（§1.9）。

### 1.4 buildJoin 真实装配（审计项 4）——PASS

落点 `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java`。

- **占位移除**：全文件 5 处 `ERR_STREAM_NOT_IMPLEMENTED`（:141 未知 transform 类型、:177 `<window>` parallelism、:704/:716 `<custom>` params/source、:793 sideOutput）均非 join；`buildJoin` :487-551 为完整实现。路由 `:117 return buildJoin(...)` 无占位分支。
- **拓扑装配**：:543-550——`left.map(JoinSideTagFunction(true, leftKeys)).union(right.map(JoinSideTagFunction(false, rightKeys)))` → `.keyBy(JoinSideKeySelector.INSTANCE)` → `keyed.transform("Join:"+id, UnknownTypeInformation, new EquiJoinOperator(joinType, windowDuration, timeout))` → `applyDeclaredParallelism`。与设计 §3「union → keyBy(joinKey) → process」一致。
- **自连接按声明边计数（不去重）**：:500-508 遍历 `model.getEdges()` 收集 `to==t.id && from∈upstreamIds` 的**声明边**（非去重 upstream 集合），`:509-515` 恰两条否则 fail-fast（`ERR_STREAM_INVALID_ARG` 带 resolved 数）；声明边序定左右（`ordered.get(0)`/`get(1)` :516-517）。WI8d 完成判定的自连接正例由 `TestParameterizedJoinModel.selfJoinTopologyWalksValidationToRealJoinBuild`（:218-235）覆盖——同一 source 两条边构建成功。
- **WI8d 校验未被绕过**：`StreamModelDslBuilder.validateJoinDeclarations`（:492-576）八项俱在——joinRef 存在 :496-503、joinType :505-512、键集非空 :514-521、键数相等 :522-529、恰两条声明边 :535-543、上游可解析 :545-551、window join 限 INNER/LEFT :560-567、timeout 依赖窗口且格式合法 :568-575；HASH 边禁入 join 在 `validateEdgeDeclarations` :457-464（`ERR_STREAM_EDGE_HASH_REDUNDANT`，"the join keys are declared by the join spec's leftKeyExprs/rightKeyExprs"）。buildJoin 无重复校验、直接消费（设计 §2 口径）。
- **键表达式编译 fail-fast**：`compileJoinKeyExprs` :570-592——逗号拆分、`XLang.newCompileTool().allowUnregisteredScopeVar(true)`（event 绑定，与 `<keyBy keyExpr>` 同款）逐条 `compileSimpleExpr`，异常包装为 `ERR_STREAM_INVALID_ARG` 带侧别与表达式文本 fail-fast。flow 零 SQL 依赖（仅 io.nop.xlang 引用）。

### 1.5 DSL→运行时端到端与 A6（审计项 5）——PASS（A6 证据边界见 MIN-3）

`TestEquiJoinParallelismInvariant`（flow，3 用例）隔离实跑 3/3 绿。

- **完整链路**：`dslJoinPipelineProducesMatchedPairs` :84-104——`test-join-pipeline.stream.xml`（声明 joins/joinSpec INNER + 双 source bean + `<join joinRef>` + sink + 三条 FORWARD 边，`_vfs/nop/stream/test/`）经 `DslModelParser` → `StreamModelDslBuilder.build()` → `env.execute` → 从 BeanContainer 取 `joinCollectingSink` 断言恰 2 条：`J|k1:a|k1:y` 与 `J|k2:b|k2:x`（:102-103）。从 `.stream.xml` 文本到 sink 输出无断点。
- **A6 三观察核验**：① `KeyGroupAssignment.assignToKeyGroup` 确为 `(key, maxParallelism)` 纯函数（KeyGroupAssignment.java:101，静态方法、HashHelper 摘要 + 模运算），测试 :107-118 钉住确定性/值相等/退化；② 引擎按键派发机制核实——`KeyExtractingOutput.processElement` :40-44 经 KeySelector 提键并 `setCurrentKey` 后转发（单线程执行面），`MemoryKeyedStateBackend.routeKey` :554-564 经同一 assignToKeyGroup 分 key group——路由函数不感知来源侧；③ 非对称 parallelism（source 2/1 + join 2）构建通过 :121-129（build-only，未执行——注释明示 plan 13 的一 JVM 一 execute 限制）。
- **「推翻」结论评估**：A6 原命题是「共置**依赖** keyBy 自身显式 parallelism」（必要性断言）。推翻该命题只需证明不声明 keyBy parallelism 时共置仍成立——结构论证成立（两侧先 merge 进单一 union 顶点、再经唯一 keyBy 分区变换，路由自变量只有 key；per-side parallelism 只决定 union 顶点的入度通道数，不进入路由函数）。E2E（INNER 全 1 并行）+ 路由纯函数测试 + 非对称构建测试支撑该论证。证据边界：无 parallelism>1 的 join 流水线**执行**（MIN-3）。结论：推翻成立，回写措辞建议记录该边界。
- **roadmap 现状**：WI13 行仍 `todo`（:261 附近）、A6 行（Assumptions §6）仍为旧文本——Phase 2 未执行，属预期态，列为本 audit 的待办而非缺陷（见 §6 翻转条件）。

### 1.6 窗口 join（审计项 6）——PASS

`TestEquiJoinWindowTimeout` 4 用例隔离实跑 4/4 绿。

- **windowEnd+timeout 收尾**：`completionDeliveredAtWindowEndPlusTimeout` :87-98——`wm(12)` 时 12 < 10+5 断言未触发且条目仍在（:92-93，若实现无视 timeout 在 windowEnd 即触发，此处 actual 含 `J|a|null` 失败）；`wm(15)` 触发补齐并清态（:94-97）。实现侧 `isLateForWindow` :200-202 与 `fireReachedWindows` :208-215 的 `winStart+duration+timeout` 判据一致。
- **跨窗不配**：`crossWindowRecordsNeverMatch` :76-84——同 key 异窗（ts 1 vs 15）零输出，双窗触发后零态。若异窗误配，:80 失败。
- **迟 records 见即弃**：`lateRecordDroppedOnSight` :101-111——窗收尾后喂 ts 5 的迟记录，无第二次补发（:109）、无状态残留（:110）。若缺 `isLateForWindow` 守卫，迟记录会入缓冲并在 `wm(40)` 被 `fireReachedWindows` 再次收尾补发 `J|late|null`，:109 以 actual 含该条失败。
- **INNER/LEFT 构造期限制未被运行时破坏**：runtime 窗口用例仅用 INNER/LEFT；RIGHT/FULL 窗口 join 在 `validateJoinDeclarations` :560-567 构造期拒绝（本次未改动，WI8d 测试仍在 flow 全量 159 内通过）。
- 有界状态：`fireWindow` :218-227 双侧整窗 `dropAll`——durable 删除同受 MAJ-1 的 scope 问题影响（跨 equiKey 场景），同条修复路径覆盖。

### 1.7 测试实跑（审计项 7）——PASS（计数 reconciliation 见下）

原始日志存 `_tmp/audit-wi13/`（runtime-join-isolation.log / flow-join-isolation.log / runtime-full.log / wi12-isolation.log / flow-full.log / core-full.log / chain-status.txt）。

| 运行 | 结果 |
|---|---|
| runtime 隔离 `TestEquiJoinHashSemantics,TestEquiJoinWindowTimeout,TestEquiJoinWithCheckpoint` | 7+4+2 = **13/13 绿**，EXIT 0 |
| flow 隔离 `TestEquiJoinParallelismInvariant` | **3/3 绿**，EXIT 0 |
| runtime 全量 `./mvnw test -pl nop-stream/nop-stream-runtime` | **Tests run: 1222, Failures: 0, Errors: 0, Skipped: 10**（skip 既有），EXIT 0 |
| WI12 三类隔离 `TestPerKeyOrderedBuffer,TestAnalysisWindowEventTime,TestE2EOverWindowWithCheckpoint` | 8+3+1 = **12/12 绿**，EXIT 0 |
| flow 全量 | **Tests run: 159, Failures: 0, Errors: 0**，EXIT 0 |
| core 全量 | **Tests run: 1665, Failures: 0, Errors: 0, Skipped: 1**，EXIT 0 |

**计数 reconciliation（1221 vs 1222）**：WI12 收口基线 1208。runtime join 新增实为 **14** 用例（HashSemantics 7 + WindowTimeout 4 + WithCheckpoint 2 + runtime 版 `TestEquiJoinParallelismInvariant` 1——后者与 flow 版同名异包，位于 runtime/operators/join，断言 union→keyBy 路由不变量）。1208+14=**1222**，与实测一致。plan/日志的「+13=1221」口径漏计了 runtime 版 A6 用例的 1 条，非测试漂移。flow 159、core 1665 与声称完全一致。

### 1.8 门禁（审计项 8）——PASS

- `node ai-dev/tools/check-doc-links.mjs --strict`：**退出码 0**（0 errors，3 warnings 全部为 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md` 既存断链，与本轮无关）。
- `node ai-dev/tools/check-nop-stream-invariants.mjs sync`：**OK**，退出码 0。
- `node ai-dev/tools/scan-hollow-implementations.mjs --module core|flow|runtime --severity high`：三模块**全 0**。

### 1.9 _gen 纪律与卫生（审计项 9）——PASS

- `git status --porcelain` 空（审计探针与日志均落 `_tmp/audit-wi13/`，不污染工作树）。
- `git diff ecd421c3b5..HEAD` 对 `_gen/`、`_*.xml`、`_*.java`、`_*.xmeta` 生成物**零改动**（diff 中唯一 `_` 前缀命中为 `_vfs/nop/stream/test/test-join-pipeline*.xml` 测试夹具，属手写测试资源非生成物）；`stream.xdef` 本轮**零改动**（WI13 不触 xdef，合规）。
- 新增/改动 join 相关文件（EquiJoinOperator、JoinSideRecord、JoinMatch、JoinSideTagFunction、JoinSideKeySelector、flow/testing 三辅助类）grep `TODO|FIXME|System.out` **零命中**。

### 1.10 plan/日志一致性（审计项 10）——日志 PASS；plan 文本不一致（MIN-2）

- **plan 23 Phase 1 勾选与 live 一致**：Phase 1 四项全勾、Exit Criteria 四项全勾、Status completed；Phase 2 全未勾（待审计态=本报告）。**但** Phase 1 第二项与 Deferred 节仍为草稿会话的「successor 路由 / NOT_IMPLEMENTED 占位保留」叙述，与 live 的 buildJoin 真实装配（§1.4）矛盾——plan 23 文件系 commit `d345e4e850` 新增（118 行全新增），接管后的交付形态未回写该叙述；Phase 1 的测试计数（12 用例）与 exit criteria（runtime 1220）亦与 live（runtime join 13 + flow 3；1222）不符。属文本级不一致，Phase 2 收口前必须一致化（plan guide 文本一致性硬规则）。
- **ai-dev/logs/2026/10-03.md WI13 条目逐句对照 live**：接管声明（:5）、三条裁定（:6，①AbstractStreamOperator+processWatermark 形态与代码一致；②git-mv 上移 R099 证实；③§7 收口语义与代码一致）、实现描述（:7）、孤儿句柄发现与修复（:8，MemoryStateSerDe 重建机制实读证实、setCurrentKey 前置的断言口径证实）、四类用例计数（:9，16/16 = runtime 13 + flow 3 口径成立；草稿语义相反之说与 :84-89 断言相符）、A6 结论（:10，§1.5 评估成立）、WI8d 契约演进（:11，更名合理——旧断言的 NOT_IMPLEMENTED 占位正是本 WI 移除的交付物，新断言仍走全量校验路径且升级为真实装配证明）、实测计数（:12，1222/159/1665/门禁全数与实跑一致）、doc-sync（:13，join-operator.md 实现锚点已含全部新落点）。**日志无失实**。

### 1.11 无静默跳过（审计项 11）——PASS

- EquiJoinOperator 全方法实实现（429 行逐行读毕）：无 stub、无 return null 占位；`copyForSubtask` 返回新实例（WI12 B-2 模式）:99-103。
- JoinSideTagFunction（单键裸值/多键有序复合列表 :51-62）、JoinSideKeySelector（直读预计算 equiKey :27-30）全实现。
- fail-fast 分支显式抛错：state 读写异常包装 `StreamException(ERR_STREAM_STATE_ERROR)` + detail（:372-376）；键表达式编译失败（:583-589）；非恰两边（:509-515）；spec 缺失（:492-498）；窗口 join 缺 duration（:528-536）；`parseDurationMillis` 空串/非法格式抛 `ERR_STREAM_INVALID_ARG`（AdvancedTransforms.java:314-317 及数值解析分支）。hollow 扫描三模块全 0 佐证。

### 1.12 WI12 波及面（审计项 12）——PASS

- 修复本身正确（§1.3）；WI12 三测试类隔离实跑 **12/12 绿**（TestPerKeyOrderedBuffer 8、TestAnalysisWindowEventTime 3、TestE2EOverWindowWithCheckpoint 1），且包含于 runtime 全量 1222。
- 波及面澄清：OverWindowOperator 自身**从不调用 setCurrentKey**（grep 零命中）——其全部 keyed 条目居于单一常量 scope，entry key 以 `key\0ts\0seq` 前缀消歧，故其水印清理循环（OverWindowOperator.java:165-190）不受跨 key scope 问题影响；**MAJ-1 为 EquiJoinOperator 独有形态**（逐记录 `setCurrentKey(rec.getEquiKey())` :155 是 keyed 路由正确性所必需，缺陷在于 watermark 循环未按 buffer key 重设）。

---

## 2. 实跑证据汇总

| # | 证据 | 结果 |
|---|---|---|
| E1 | runtime join 三类隔离（13 用例） | 绿 |
| E2 | flow `TestEquiJoinParallelismInvariant` 隔离（3 用例） | 绿 |
| E3 | runtime 全量 | 1222 / 0F / 0E / 10 skip（既有） |
| E4 | WI12 三类隔离（12 用例） | 绿 |
| E5 | flow 全量 | 159 / 0F / 0E |
| E6 | core 全量 | 1665 / 0F / 0E / 1 skip |
| E7 | check-doc-links --strict | 0（3 warnings 既存可忽略） |
| E8 | check-nop-stream-invariants sync | OK |
| E9 | scan-hollow core/flow/runtime --severity high | 全 0 |
| E10 | durable-trim 探针（`_tmp/audit-wi13/DurableTrimProbe.java`，只用 target/classes 编译产物，零仓库修改） | **LEAK CONFIRMED** |
| E11 | git R099 rename / `git log --follow` 4 commit 连续 | 证实 git mv |
| E12 | git status / _gen diff / xdef diff / TODO 扫描 | 全部干净 |

**E10 探针实录**（FULL join，L 侧 k1(10) + R 侧 k2(10)，`wm(50)`）：

```
post-watermark outputs (working view): [J|a|null, J|null|b]
working view bufferedCount=0
keyedBufferEntries() (current-key scoped) = 0        ← currentKey=k2 的 scope
durable scope [...key=k2] entries=[]
durable scope [...key=k1] entries=[k1 L 10 0]        ← 已修剪条目残留
TOTAL durable entries across ALL scopes = 1
PROBE RESULT: LEAK CONFIRMED
after new k1 element: keyedBufferEntries()=2 (currentKey=k1)  ← 残留 + 新条目累积
```

## 3. 发现（分级）

### MAJ-1（代码面，收口阻断）：watermark 修剪/收尾的 durable 删除跨 key scope 失效

- **位置**：`EquiJoinOperator.completeAndTrimHash` :187-194、`fireReachedWindows`/`fireWindow` :208-227 → `removeDurable` :298-309。
- **机理**：引擎 keyed MapState 读写按 `(namespace, currentKey)` 定 scope（MemoryMapState.java:68-70、:92-128、:137-140）；watermark 循环遍历全部 buffer key（跨 equiKey）但从不 `setCurrentKey(equiKeyOf(key))`，currentKey 停留在最后一个处理元素的 equiKey（:155）→ 非 current equiKey 的 `bufferState.remove(...)` 静默 no-op。
- **实证**：§2 E10 探针——修剪后 k1 scope 残留已修剪条目，且后续新元素使该 scope 持续累积。
- **后果**：(a) durable 通道随 distinct equiKey 基数无界增长，推翻 design §3 / javadoc :44-47「no unbounded hash buffer / bounded state」对 durable 副本的声明（工作视图有界性成立，durable 不成立）；(b) 违反本轮 §十 合规口径「trim 时 keyed 通道同步删除」——同步删除仅对最后 current 的 key 有效；(c) checkpoint 快照/restore 谱系携带陈旧条目（无输出腐化：配对/补齐读工作视图，restore 计算态走 operator-state 传输，探针与 checkpoint 测试均未出现错误输出）；(d) `TestEquiJoinHashSemantics:78` 的 `keyedBufferEntries()==0` 断言因 scope 盲区对跨 key 泄漏假绿。
- **处置路径**：① `completeAndTrimHash`/`fireReachedWindows` 循环内按 buffer key 重设 currentKey（`setCurrentKey(equiKeyOf(key))`）后再做 complete/trim 的 keyed 删除；② 新增判别性回归断言——多 equiKey 场景下逐 key `setCurrentKey` 后断言 `keyedBufferEntries()==0`（或全 scope 总数探针式断言），hash 与 window 双形态各一；③ 重跑 E1-E9。

### MIN-1（文本）：EquiJoinOperator.java:86 字段注释陈旧

`buffer` 字段注释「(rebuilt from keyed state on restore)」与实际 restore 通道不符（operator-state 传输，:143-148；javadoc :66-70 为准确口径）。随 MAJ-1 顺手修正。

### MIN-2（文本/流程）：plan 23 草稿叙述与 live 交付形态不一致

Phase 1 第二项 + Exit Criteria 第二项 + Deferred 节仍是「successor 路由 / NOT_IMPLEMENTED 占位保留」，live 已是真实装配（更优结果，符合 Closure Gates 第二项与本轮审计项 4 的口径）；测试计数（12 用例、1220）与 live（13+3、1222）不符。Phase 2 勾选与 Status → completed 之前必须一致化。

### MIN-3（证据精度）：A6 推翻无 parallelism>1 的执行级证据

非对称用例 build-only（一 JVM 一 execute 限制，代码注释已如实标注）；执行的 E2E 与路由测试均在默认/声明并行度 1 下。结构论证（§1.5）成立，推翻命题（必要性断言）成立；roadmap A6 行回写（Phase 2 待办）建议措辞记录「结构 + 构建 + 纯函数」证据边界，不声明「非对称执行验证」。

### 待办（预期态，非缺陷）

roadmap WI13 行仍 `todo`、A6 行仍旧文本——Phase 2 未执行所致；随翻转一并处理。

## 4. 无静默跳过检查

见 §1.11：全部通过，无 stub、无静默吞错、fail-fast 分支齐备且抛错带码。

## 5. 结论

WI13 的语义面、装配面、证据面、纪律面均达到收口水准，日志如实、WI8d 契约演进正当、WI12 波及修复正确。**但 MAJ-1 是代码面缺陷**：durable keyed 通道的修剪同步删除在跨 equiKey 场景整体失效，既有断言因 scope 盲区不可见。按审计规则（代码面缺陷 → 冻结 roadmap 翻转），本轮裁定 **FAIL**。

## 6. roadmap WI13 翻转解锁条件

`todo` → `done`（括注单层一对）+ A6 回写 + `parseRoadmapMarkdown` 31+7 复核，在以下条件**全部**满足后解锁：

1. MAJ-1 修复：watermark 循环按 buffer key 重设 currentKey 后再做 keyed 删除（hash + window 双路径）；
2. 新增跨 key durable 清理的判别性回归断言（多 equiKey、逐 key scope 断言），并能证明修复前该断言红；
3. 重跑并全绿：join 具名类隔离（runtime 13 + flow 3）、runtime/flow/core 三模块全量、门禁三项（doc-links --strict / invariants sync / hollow --severity high）；
4. 文本收敛：MIN-1 注释修正、MIN-2 plan 23 Phase 1/Deferred/计数与 live 一致化、MIN-3 纳入 A6 回写措辞；
5. 新一轮独立 closure audit（fresh session）复核上述各项后 PASS；
6. 随后按 plan Phase 2 完成勾选、Closure 段回填（引用两轮 audit 证据）、`check-plan-checklist --strict` 0、`check-doc-links --strict` 0。
