# WI21 Closure Audit——24-wi21-registration-invariants.md

- Audit 日期：2026-10-03（单轮，PASS）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `aefacf8bea`（分支 add-stream-sql，审计起止时工作树均 clean；wi21 实现提交为 HEAD，父提交 `4eb99407e6` 用于红态反事实）
- **最终裁定：PASS——七项完成判定全部成立，五模块全量零退化，三项红→绿反事实独立实证，门禁全 0，_gen/xdef 纪律干净，无静默跳过；4 项 Minor（无 Blocker/Major）列为收口必改清单，不阻断 roadmap 翻转（依 wi13 二轮先例：文本级/非行为缺陷项随收口修正）。翻转条件与收口动作清单见 §7**

---

## 0. 裁定摘要

功能面与证据面成立：§八 11 声明→注册表接线真实贯通（三点合并、flow builder 规范化写入、红→绿探针独立复现在 :108 恰以 plan 声称的失败文本变红）；§八 12 双门真实设防（门 A 红态「nothing was thrown」实证了修复前 2PC 无 checkpoint 静默走 `runLocal` 的危险路径，修复后 fail-fast；门 B 在 JobGraph entry 与 skeleton 两处均于 config 解引用前生效，红态 NPE vs `StreamException` 实证判别力；门 A 对声明 checkpoint 的 2PC 作业零误伤有专测）；§八 13/14/2/1 四 Proof 具名测试落点符合依赖方向且全绿；§八 9 常量值逐字不变 + 契约钉值；五模块全量（core 1668 / flow 160 / cep 381 / rocksdb 136 / runtime 1232）全部 0F/0E，与声称计数逐一相符；门禁三项全 0；`stream.xdef`/`_gen` 零改动。

**发现全部为 Minor**：MIN-1——`prepareSavepointRuntime` 的门 B 调用点位于 config 解引用之后，在其唯一触发条件（`checkpointConfig == null`）下不可达（先行 NPE），日志 :7 与 parameterized-declarations.md §6「savepoint 入口在解引用 config 前」一句失实（无静默风险：NPE 快速失败，execute 面两处门均有效）；MIN-2——plan Phase 3 三个已勾选项含未按字面交付的子声明（remote 半段新断言 / JSON 兜底分支用例 / Union#1 occurrence 断言）；MIN-3——plan 多处 Exit Criteria/ items 落后于 live（live 已满足但未勾选）；MIN-4——runtime 两个 `test-join-pipeline` fixture 为无引用死资源 + wiring 测试未使用常量。四项均不构成行为缺陷或不变量违反，处置路径见 §3。

---

## 1. 逐项审计核验

### 1.1 §八 11 声明面 + 注册表条目（审计项 1）——PASS

- **载体（core/model）**：`StreamComponentEntry`（`@DataBean`、Serializable、registry/id/attributes 三字段，attributes 描述性键值）与 `StreamComponents.declarativeRegistries`（LinkedHashMap 嵌套）；`addDeclarativeEntry`（null/空 id 抛 `ERR_STREAM_NULL_ARG`，:295-301）、`mergeDeclarativeRegistries`（last-wins 合并，:309-317）、`hasDeclarativeRegistry`/`getDeclarativeEntry`（:319-327）全实实现，非壳。
- **累积 + 三点合并（core env）**：`declareRegistry`（:114-119，null/empty 安全返回）累积于 `declaredRegistries`；`mergeDeclaredRegistries` 在三处调用——`execute()` :384-385（compilePlans 后并入 jobGraph 附着模型）、`buildStreamModel()` :699（env 侧模型）、`buildJobGraph()` :763-764（分布式发射路径的 graph 附着模型）。日志所称「双点合并」= env 附着 + graph 附着两类口径，与实现一致（graph 附着含 execute 与 buildJobGraph 两个物理点）。
- **flow builder 规范化写入**：`declareComponentRegistries`（StreamModelDslBuilder.java:197-232）在 `failFastOnUnsupportedRegistries()` 之后、`buildTransforms(env)` 之前（:182-185）把 aggregators（fnId/expr/schemaId）、joins（joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout）、schemas（fields `name:type` 逗号拼接）逐条 `env.declareRegistry` 写入，消费 flow 模型既有 getter。与 plan Phase 1 顺序声明一致。
- **合并点口径未被破坏**：graph 附着模型的 requirements 填充（`StreamGraphGenerator.detectRequirements` :192-202 → :141 `setStreamModel`）保持既有路径，WI21 未改 detectRequirements；`TestStreamModelPopulation` 在 core 全量 1668 内零退化。
- **wiring 测试（flow）判别力**：`TestStreamComponentsRegistryWiring.declaredRegistriesFlowIntoStreamComponents` 走 DSL 文件（`wi21-registry-wiring.stream.xml`）→ `DslModelParser` → `StreamModelDslBuilder.build()` → `buildJobGraph()` → **graph 附着模型** components，断言三注册表条目在场且 attributes 逐键全对（:108-132；schema 字段断言 `k:string,v:bigint` 与 fixture 的 `bigint` 类型一致）。
- **红→绿独立实证（探针 A）**：`git checkout aefacf8bea~1 -- StreamModelDslBuilder.java` 后隔离实跑——**1/1 红，失败点恰为 :108 `aggregators registry must reach StreamComponents (§八 11) ==> expected: <true> but was: <false>`**，与 plan Exit Criteria 声称的红文本（"aggregators registry must reach == false"）逐字相符；恢复 HEAD 后 1/1 绿。判别力成立（失败发生在第一个注册表断言，证明修复前声明面 parsed 后即被丢弃）。
- **attributes 语义正确性**：builder 侧写入键集合与测试断言键集合一一对应；`StreamComponentEntry` 构造器对 attributes 做防御性拷贝（:40-42）。

### 1.2 §八 12 双阶段（审计项 2）——PASS（MIN-1：门 B 第三调用点无效）

- **门 A（core `execute()`）**：`StreamExecutionEnvironment.java:386-398`——`compilePlans` 之后（graph 附着模型 requirements 已派生：`StreamGraphGenerator` :141 附着 → :166 `detectRequirements` → :197 对 2PC sink 加 `TWO_PHASE_COMMIT_SINK`；`JobGraphGenerator` :167-169 透传给 JobGraph）、`buildPartitionedPlan` 之前。触发条件 `requirements.contains(TWO_PHASE_COMMIT_SINK) && !checkpointingDeclared && checkpointExecutorFactory == null`，抛 `ERR_STREAM_INVALID_STATE` 带「never commit」危险说明。
- **门 A 触发条件精确性（不误伤声明 checkpoint 的 2PC 作业）**：键控在 `checkpointingDeclared` 而非 `checkpointConfig.isCheckpointEnabled()`——后者默认 true（F-06 注释 :92-101 在案），不能区分声明与否；DSL `<checkpoint>` 经 `applyCheckpointConfig`（:248-250）漏斗到 `enableCheckpointing()`（:183-188，置 `checkpointingDeclared=true`）。专测 `gateATwoPhaseSinkWithDeclaredCheckpointPasses`（DualPhase :106-114）——声明后走 checkpoint engine 完整执行通过。core-only classpath 的声明作业在 `requireCheckpointExecutorFactory`（:237-253）得到独立 typed 错误而非门 A，路径互不误伤。
- **门 A 红→绿独立实证（探针 C）**：检出父提交 `StreamExecutionEnvironment.java` + 重装 core 快照后隔离实跑——**`gateATwoPhaseSinkWithoutCheckpointFailsFast` :91 红：`Expected StreamException to be thrown, but nothing was thrown`**——精确证实修复前「DSL 不声明 checkpoint + 2PC sink → `runLocal` 静默不提交」的危险路径真实存在且无任何报错；恢复 HEAD 重装后 4/4 绿。
- **门 B（runtime）调用点实测**：三处——JobGraph entry `executeWithCheckpoint(JobGraph,...)` :126（**第一语句**，先于 `resolveJobId`/`resolvePipelineId` 解引用 ✓）；`executeWithCheckpointSkeleton` :219（**先于** `validateUnalignedConfig`/`resolveBarrierAlignment` ✓）；`prepareSavepointRuntime` :352（**位于** `validateUnalignedConfig()` :349、`resolveBarrierAlignment` :350、`getBarrierAlignmentTimeout()` :351 三处解引用**之后** ✗——见 §3 MIN-1）。
- **门 B 发现规则双源**：附着模型 requirements（`jobGraph.getStreamModel()`）+ 顶点链重扫 `hasTwoPhaseCommitSinkOnGraph`（`AbstractUdfStreamOperator.getUserFunction() instanceof TwoPhaseCommitSinkFunction`，与 `CheckpointPlanBuilder` 的 2PC 判定同法），JobGraph-only 入口亦有防御。
- **门 B 红态实证（探针 B）**：检出父提交 `GraphModelCheckpointExecutor.java` 隔离实跑——**`gateBRefuses2PCGraphWithoutCheckpointConfig` :127 红：期望 `StreamException` 实得 `NullPointerException`（`CheckpointConfig.getJobId()` on null）**——同时实证了「解引用前」这一位置要求的判别意义：无门时解引用点先 NPE；恢复后 4/4 绿。该红态机理同时是 MIN-1 的成因证据（`prepareSavepointRuntime` 内 :349/:350 的解引用同样会先 NPE，:352 的门在其唯一抛出条件下不可达）。
- **2PC 既有用例零退化**：runtime 全量 1232/0F/0E（含 `TestE2EWindowAggregateRestore` 4/4 等）；`TestProcessingGuaranteeBehavior`（core）2/2、`TestProcessingGuarantee` 4/4 均在 core 全量 1668 内绿（该两类落 core 非 runtime，任务书「runtime 全量实跑」的口径已按实际落点在两个模块全量中分别核验）。

### 1.3 §八 13 transactional operator = CheckpointParticipant（审计项 3）——PASS

`TestTransactionalOperatorIsCheckpointParticipant`（runtime/checkpoint，2 用例，隔离绿）：

- **装配断言**：`v1`（parallelism=2）的 2PC vertex 经 `CheckpointPlanBuilder.build` 后 `getCheckpointParticipants()` 恰 2 条且含 **`v1-0` 与 `v1-1`**（:88-99，`{vertexId}-{taskIndex}` 键形逐字断言）。
- **类级反证（显式枚举非反射扫描）**：`CheckpointParticipant.class.isAssignableFrom(...)` 对 `WindowOperator`/`OverWindowOperator`/`EquiJoinOperator`/`CepOperator`（Class.forName 加载，classpath 缺失即 `IllegalStateException` fail-fast）/`AbstractStreamOperator` 基类全部 `assertFalse`（:102-125）；正向类型断言 `TwoPhaseCommitSinkFunction` implements `CheckpointParticipant`（:120-121）——类型系统 + 装配 + 反证三层闭合，§八 13 判定成立。

### 1.4 §八 14 分布式 edge 显式 EdgeConfig（审计项 4）——PASS（口径采认，附 MIN-2a 文本修正）

`TestDistributedEdgeEdgeConfigCoverage`（core/execution，3 用例，隔离 3/3 绿）：

- **两级解析**：`declaredEdgeConfigSurvivesResolution`——JobEdge 载体优先（`EdgeAssembly.resolveEdgeConfig`，声明值 777/333 原样返回）；`deploymentPlanConfigResolvedByEdgeKey`——DeploymentPlan `edgeConfigs` map 按 RAW `"A->B"` 键解析（888 值断言）。
- **线性计划全任务 gate**：`everyTaskOfMaterializedPlanGetsAGate`——A→B→C 无声明的线性管线物化后 B/C 的 `getInputGate()` 均 non-null（默认配置兜底，WI6 裁定口径）。
- **declared 冲突 fail-fast**：未在新测试内复写，由既有 `TestMultiEdgeGateConfigConsistency`（core，值等/值冲突矩阵）承载——新测试 javadoc 显式指向（:34-35）；生产门在 `GraphExecutionPlan` gate build（plan baseline :19 引 :567-601）。
- **remote 侧口径采认**：`RemoteGraphExecutionPlanBuilder`（runtime/transport）:431-439 确有同构校验（多输入边配置值冲突抛 `ERR_STREAM_INVALID_ARG`，undeclared 向 declared 让渡），经同一 `EdgeAssembly.resolveEdgeConfig` 收敛；既有 `TestRemotePlanTopicLegality` :191-196 断言 remote 侧 resolveEdgeConfig 的键解析。**评估：可接受**——§八 14 的机制本体（WI6 已裁定）是既有满足项，WI21 义务为 Proof 钉子，plan Current Baseline :19 已声明该口径；但 Phase 3 item 文本「runtime remote 半段：……边覆盖断言」承诺了新断言而未交付，列为 MIN-2a 文本修正项。

### 1.5 §八 2 KeyGroup 确定性路由（审计项 5）——PASS（附 MIN-2b）

`TestKeyGroupRoutingAcrossBackendsDeterministic`（rocksdb，2 用例，隔离 2/2 绿）：

- **跨后端 parity**：5 键（含空串与 `中文键`）逐一断言 `RocksDBKeyGroupRouting...computeKeyGroupId(key) == KeyGroupAssignment.assignToKeyGroup(key, 8)`（:34-48）。memory 侧 `routeKey`（MemoryKeyedStateBackend.java:554-564）实读确认委托同一 `assignToKeyGroup` 纯函数（:562），故 rocks-vs-function parity 即跨后端 parity，结构成立。
- **mp=1/4/32 逐级**：每级独立 backend 实例（构造器固定 job-global mp，符合真实作业形态），parity 断言逐键成立（:51-68）。
- **MIN-2b**：plan Phase 3 item 3 的「+ JSON 不可序列化兜底分支同 JVM 确定性」未交付——新测试无该分支用例，全仓亦无（`TestKeyRoutingOwnershipParity` 覆盖 JSON 可序列化分支与 enum-by-name，不触 `KeyGroupAssignment.stableHash` :86-96 的 identity-hash fallback）。兜底分支自述为「non-production inputs / JVM-variable」（KeyGroupAssignment.java:88-95），不影响 §八 2 主命题（跨后端确定性）的钉住，但 plan 已勾项与交付不符，见 §3。

### 1.6 §八 9 timer state 共享常量（审计项 6）——PASS

- **值不变（durable 兼容）**：`TimerStateKeys.WINDOW_INTERNAL_TIMERS = "internal-timers"`、`CEP_EVENT_TIME_TIMERS = "cep-event-time-timers"`——与历史字面量逐字相同。
- **三处改引**：WindowOperator `putOperatorState`（:646）与 `getOperatorState`（:680）两处、CepOperator `EVENT_TIME_TIMERS_STATE_NAME`（:661）一处，diff 仅替换字面量为常量引用，无其它改动。
- **钉值契约测试**：`TestTimerStateSnapshotContract`（runtime/checkpoint，隔离绿）——两常量钉值 + 互不相等断言（防共用键互读 payload）；javadoc 声明 TimerStateKeys 为唯一事实源、运行时行为由 `TestTimerCheckpointRestoreE2E`/`TestCepCheckpointRestoreE2E` 承载——两类 E2E 均在 runtime 全量 1232 内零退化（免第四处文档摊薄的裁定成立）。

### 1.7 §八 1 持久状态稳定 operatorId（审计项 7）——PASS（附 MIN-2c）

`TestPersistentStateOperatorIdStability`（runtime/execution，1 用例，隔离绿）：

- **双 build 不 execute**：同一 DSL 两次独立 parse + `buildJobGraph("wi21-stability")`（绕开本地 runner 一 JVM 一 execute 限制，public build path 无此限制）。
- **身份全等断言**：顶点 id→name TreeMap 全等（:96-98）——id 即 `buildStreamModel` :685-696 的 name+occurrence 派生 stableId，向下游派生 TaskLocation（manifest 键）与 `operator-{i}` 状态键（CheckpointPlanBuilder:159），结构等价于 plan 所称「TaskLocation 集合与 operator 状态键全等」（字面交付为顶点 id/name + fingerprint，见 MIN-2c）；`StreamModelFingerprint` 全等（:109-111，恢复验证的输入）；**`"Join:j"` 语义名经链名存活**断言（:103-105，实跑输出形态 `KeyBy -> Join:j -> Sink`，与日志 :8 记载一致）。
- **MIN-2c**：plan item 5 的「显式断言 union occurrence 消歧（`Union#1`）」未交付——fixture（`wi21-stability.stream.xml`）无 `<union>` 变换（join 内部自带 left.union(right)），测试无 `Union#1` 断言；测试 javadoc 声称 "a union topology" 与 fixture 不符。

---

## 2. 实跑证据汇总

原始日志存 `_tmp/audit-wi21/`（五模块全量、四组隔离、红/绿探针与重装日志、门禁输出）。

| # | 运行 | 结果 |
|---|---|---|
| E1 | runtime 全量 `./mvnw test -pl nop-stream/nop-stream-runtime` | **Tests run: 1232, Failures: 0, Errors: 0, Skipped: 10**（skip 既有），EXIT 0——与声称 1232 相符 |
| E2 | flow 全量 | **160 / 0F / 0E**，EXIT 0——与声称相符 |
| E3 | core 全量 | **1668 / 0F / 0E / 1 skip**，EXIT 0——与声称相符 |
| E4 | cep 全量 | **381 / 0F / 0E**，EXIT 0——与声称相符（本 WI 对 cep 仅常量改引，无新增测试） |
| E5 | rocksdb 全量 | **136 / 0F / 0E**，EXIT 0——与声称相符 |
| E6 | 七具名类隔离：flow wiring 1/1、runtime DualPhase 4/4、runtime（Participant 2 + TimerContract 1 + Stability 1）4/4、core EdgeConfigCoverage 3/3、rocksdb KeyGroupRouting 2/2 | **合计 14/14 绿**，各 EXIT 0 |
| E7 | 探针 A 红（父 builder + HEAD core） | **1/1 红** @ :108 "aggregators registry must reach … <false>"（与 plan 声称红文本逐字同） |
| E8 | 探针 A 绿（恢复 HEAD） | 1/1 绿 |
| E9 | 探针 B 红（父 executor） | **4 跑 1 红** @ :127 expected `StreamException` but was `NullPointerException` |
| E10 | 探针 B 绿（恢复 HEAD） | 4/4 绿 |
| E11 | 探针 C 红（父 env + 重装 core 快照） | **4 跑 1 红** @ :91 "nothing was thrown"（静默路径实证） |
| E12 | 探针 C 绿（恢复 HEAD + 重装） | 4/4 绿 |
| E13 | `check-doc-links.mjs --strict` | **退出码 0**（0 errors，3 warnings 均为 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md` 既存断链，与 wi13 两轮审计所见完全相同，与本轮无关） |
| E14 | `check-nop-stream-invariants.mjs sync` | **OK**，退出码 0（OverWindowOperator/EquiJoinOperator 不在 invariant-catalog 属预期——登记义务归 WI22，roadmap :272 在案） |
| E15 | `scan-hollow-implementations.mjs --module core/flow/runtime --severity high` | 三模块 **High 0 / Critical 0** |
| E16 | `git status --porcelain`（审计起止 + 每次探针恢复后） | 全程**空**（探针 checkout 已确定性恢复并重装 HEAD core 快照） |

**计数勾稽**：runtime 1232 = wi13 二轮审计基线 1224 + 新增 8（DualPhase 4 + Participant 2 + TimerContract 1 + Stability 1）；core 1668 = 1665 + 3；flow 160 = 159 + 1；cep 381、rocksdb 136（+2）无其它测试文件变动，与「零退化」一致。

## 3. 发现（分级）

### MIN-1（代码面小缺陷 + 文本失实，收口必改，不阻断）：`prepareSavepointRuntime` 的门 B 调用点无效，且日志/design doc 对应表述失实

- **位置**：`GraphModelCheckpointExecutor.java:352`——位于 `checkpointConfig.validateUnalignedConfig()` :349、`resolveBarrierAlignment(checkpointConfig)` :350（内部 `config.getProcessingGuarantee()` :615 解引用）、`checkpointConfig.getBarrierAlignmentTimeout()` :351 之后；而 `gateTwoPhaseRequiresConfig` 唯一抛出条件是 `checkpointConfig == null`（:1551-1573）→ null config 在 :349/:350 已 NPE，:352 的门**在其唯一触发条件下不可达**（死防御）。
- **证据**：探针 B 红态显示同一类解引用（`resolveJobId` 的 `config.getJobId()`）在无门时先行 NPE——同机理适用于 `prepareSavepointRuntime` 体内；新测试 `gateBRefuses...` 只经 JobGraph entry :126（有效门），savepoint 路径无测试可达性。
- **危害评估**：**无静默路径**——2PC 图 + null config 走 savepoint 入口仍以 NPE 快速失败（先于任何执行），不产出错误输出；门 A + JobGraph entry 门 + skeleton 门三处有效门已覆盖全部 execute 面。违反的只是交付声明的精度：日志 :7「execute 与 savepoint 两入口在**解引用 config 前**经 gateTwoPhaseRequiresConfig 校验」、parameterized-declarations.md §6 同句、commit message「execute/savepoint 入口」——savepoint 半句与代码不符。
- **处置路径**：① 将 `gateTwoPhaseRequiresConfig(jobGraph, checkpointConfig)` 移至 `prepareSavepointRuntime` 方法体第一语句（:349 之前）——一行移动，现有测试形态不变；② 修正日志追记与 §6 该句措辞（或注记 savepoint 路径为「NPE 先行的等价 fail-fast」）；③ 复跑 DualPhase 隔离 4/4。

### MIN-2（plan 文本 vs 交付形态，收口必改）：Phase 3 三个已勾选项含未按字面交付的子声明

- **(a)** item 2「runtime remote 半段：RemoteGraphExecutionPlanBuilder 构建的 plan 边覆盖断言」——未新增断言；由既有生产门（:431-439）+ `TestRemotePlanTopicLegality`（键解析）+ `TestMultiEdgeGateConfigConsistency`（冲突矩阵）承载，plan baseline :19 已声明口径。机制面可接受（§1.4），item 文本须改为与交付一致（或补一个 remote plan 全边 gate 断言）。
- **(b)** item 3「+ JSON 不可序列化兜底分支同 JVM 确定性」——`TestKeyGroupRoutingAcrossBackendsDeterministic` 无该用例，全仓无该分支直接测试。收口时二选一：补一用例（非 `@DataBean` 键对象两次 `assignToKeyGroup` 同 JVM 相等），或改写 item 文本并注明兜底分支为 KeyGroupAssignment javadoc 自述的 non-production 路径。
- **(c)** item 5「显式断言 union occurrence 消歧（`Union#1`）」——fixture 无 union 变换、测试无该断言；javadoc "a union topology" 与 fixture 不符；「TaskLocation 集合与 operator 状态键全等」实以顶点 id/name 全等 + fingerprint 全等等价承载（id 为 TaskLocation/`operator-{i}` 的派生源，结构等价）。收口时二选一：fixture 加 `<union>` 并补 `Union#1` 断言（含双 union occurrence 场景更佳），或改写 item 文本。

### MIN-3（流程/文本）：plan 24 多处勾选落后于 live（guide 规则 18/19）

live 已满足但未勾选：Phase 1 Exit Criteria「core/flow 全量零退化」「`ai-dev/logs/` 条目更新」；Phase 2 items「门 A」「门 B」「RemoteTaskDeploySupport 降级裁定落档」与 Exit Criteria「runtime 全量零退化」「logs」；Phase 3 Exit Criteria「logs」。上述各项经本轮实跑/实读均已成立（logs 条目在 10-03.md :3-10 实际存在）。属「顶部 Status: completed、内部仍未勾选」的矛盾形态，Phase 4 收口时统一勾选/一致化即可。另日志 :9「（各含新增）」对 cep 不成立（cep 无新增测试），随 MIN-1 措辞修正一并注记。

### MIN-4（卫生）：死资源与未使用常量

- `nop-stream-runtime/src/test/resources/_vfs/nop/stream/test/test-join-pipeline.beans.xml` 与 `.stream.xml`：runtime 测试源码零引用（内容 grep 全仓核对），系从 flow 复制的未使用副本。收口时删除，或留注用途。
- `TestStreamComponentsRegistryWiring.REGISTRY_MODEL`（:63-93）：约 30 行内联 DSL 字符串全文件零引用。收口时删除。

### 待办（预期态，非缺陷）

- roadmap WI21 行仍 `todo`（:254）、M3 仍 `todo`（:255）——Phase 4 回写动作本身在 PASS 后执行。
- 00-vision §六 #1 :86 授权原文核实：明文覆盖 aggregators 与 joins 注册表条目（(b) 款「聚合声明面（aggregators）、join 声明面（joins）」），**schemas 不在授权原文**——plan 将其作为 WI8b 声明面授权的实现延伸处理并在 Phase 4 排入回写注记（含 schemas 补记），本轮审计时回写尚未发生，属 plan :124 声明的预期态，列为收口动作而非缺陷。

## 4. 无静默跳过检查

- `declareRegistry`/`mergeDeclaredRegistries`/`addDeclarativeEntry`/`mergeDeclarativeRegistries`/`gateTwoPhaseRequiresConfig`/`hasTwoPhaseCommitSinkOnGraph`/`declareComponentRegistries` 逐方法实读，全部实实现，无 stub/no-op/吞异常；`declareRegistry` 对 null/empty 的静默返回属注册便利 API 的良性边界（`StreamComponents` 侧同型操作则 fail-fast，分层一致）。
- 门 A/门 B 抛错均 `ERR_STREAM_INVALID_STATE` 带 complete 危险说明与修复指引；门 B 双源发现（requirements + 顶点链重扫）保证 JobGraph-only 入口不漏检。
- 新增 12 个 Java 文件 grep `TODO|FIXME|System.out` **零命中**；hollow 扫描三模块 high/critical 全 0（E15）。
- 端到端链路连通（Anti-Hollow 规则）：DSL 声明 → `StreamModelDslBuilder.declareComponentRegistries` → `env.declareRegistry` → `buildJobGraph` 合并 → 测试从 graph 附着模型读回断言（探针 A 证明该链在无 builder 写入时断言变红，非 vacuous）；门 A 链：DSL 2PC sink → detectRequirements → execute() gate → 抛（探针 C 双向证明）。

## 5. git/_gen 纪律

- `git diff 4eb99407e6..aefacf8bea` 对 `_gen/`、`_*.xml`、`_*.java`、`_*.xmeta` 生成物**零改动**；`stream.xdef` 零改动（WI21 不触 xdef——三注册表声明面已由 WI8b/c/d 交付，合规）；diff 中唯一 `_` 前缀命中为 `_vfs/.../test/*.xml` 手写测试夹具（wi13 同款先例）。
- commit `aefacf8bea` 触 25 文件（+1422/−4），全部与七项交付对应；无探针残留（探针仅存 `_tmp/audit-wi21/`，untracked，git status 干净）。

## 6. plan/日志一致性

- **plan 24**：Phase 1-3 Status completed、Phase 4 planned（待审计态）与 live 相符；Phase 1 四 items、Phase 3 五 items 勾选与 live 交付主体相符（MIN-2 三处子声明除外）；MIN-3 所列 stale 未勾项见 §3。
- **日志 10-03.md WI21 条目（:3-10）逐句对照**：:5 前置盘点与 r2 修订——与 plan Revision 头一致；:6 §八 11——「红（注册表恒空）→绿」经探针 A 实证、合并点口径与实现一致；:7 门 A「stash 检出红 → 恢复绿」经探针 C 实证（本轮用文件级 checkout 等价复现）、第一道空转基线如实记录（与 DualPhase javadoc 一致）——**唯「savepoint 两入口在解引用 config 前」半句失实（MIN-1）**；:8 五项 Proof 描述与交付逐项相符（无 JSON 兜底/Union#1 夸大——该两处夸大仅在 plan items，日志如实）；:9 计数（14 用例分布、五模块 1668/160/381/136/1232、门禁三项）与本轮实测逐一相符；:10 doc-sync 与 §6 实文一致。**日志除 :7 一句外无失实。**

## 7. 结论与翻转宣告

**PASS。** 七项完成判定全部成立且证据独立复现；无 Blocker、无 Major；MIN-1..MIN-4 均不构成行为缺陷、不变量违反或证据造假，随收口清单修正。**roadmap WI21 `todo` → `done` 翻转与 M3 → done 正式解锁。**

**实现者收口动作清单（按序执行）**：

1. **MIN-1 修复**：`prepareSavepointRuntime` 的 `gateTwoPhaseRequiresConfig` 调用移至方法体第一语句（`validateUnalignedConfig` 之前）；复跑 `TestStreamRequirementDualPhaseValidation` 4/4 + runtime 全量零退化。
2. **MIN-2 一致化**（逐项二选一：补用例或改写 plan 文本）：(a) remote 半段口径改写为「由 :431-439 生产门 + TestRemotePlanTopicLegality + TestMultiEdgeGateConfigConsistency 承载」；(b) JSON 兜底分支补用例或改写；(c) Union#1 补断言（fixture 加 union）或改写并同步修正测试 javadoc。
3. **MIN-3 勾选收敛**：plan 24 Phase 1-3 全部 stale 未勾项按 live 事实勾选；MIN-1/2 修正后如有行为/文本变化在 plan 与日志追记。
4. **MIN-4 清理**：删除 runtime 两个无引用 `test-join-pipeline` fixture 与 `REGISTRY_MODEL` 常量（或留注）。
5. roadmap WI21 行 `todo` → `done`（括注单层一对，引用本 audit 证据，含 MIN-1..MIN-4 已处置注记）；M3 → done；`parseRoadmapMarkdown` 复核 items=31、milestones=7、WI21=done、M3=done、WI17/WI18/WI19/WI20/WI22/WI23/WI24 仍 todo、退出码 0。
6. plan 24 Phase 4 勾选、Plan Status → `completed`、Closure 段回填（引用本报告）；`check-plan-checklist.mjs 24-... --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0。
7. 00-vision §六 #1 授权条款回写执行注记（含 schemas 延伸授权补记，plan :124）。
8. 当日日志追记 closure audit PASS 与收口翻转；commit。
