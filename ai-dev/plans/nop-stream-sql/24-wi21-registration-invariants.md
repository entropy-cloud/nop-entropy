# 24 WI21 注册与接线覆盖设计不变量

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI21 行、Purpose 条款表 §八 1 2 9 11 12 13 14）、`ai-dev/design/nop-stream/00-vision.md` §八 与 §六 #1 :86 授权条款、`ai-dev/design/nop-stream/parameterized-declarations.md`
> Related: `ai-dev/plans/nop-stream-sql/23-wi13-equi-join-operator.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
> Revision: r2——对抗性审查（3 Blocker + 8 Major）后重写：测试落点按依赖方向重排（flow/runtime/rocksdb）、§八 12 二道门改为「core execute() 2PC 无 checkpoint 门 + runtime skeleton 防御门」并如实记录第一道对派生 requirement 的空转、remote 侧显式裁定降级、稳定性测试改双 build 不 execute。

## Purpose

把本 roadmap 新增组件钉进平台注册与校验面：StreamComponents 注册表收录三新注册表条目并贯通 DSL→注册表通路；StreamRequirement 获得运行时第二道校验（含 2PC sink 无 checkpoint 的危险静默路径设防）；§八 1/2/9/13/14 以具名测试钉住既有机制。七项各有具名测试（roadmap WI21 完成判定）。

## Current Baseline（2026-10-03 Explore 盘点 + 对抗性审查复核，file:line 实测）

- **§八 11 部分满足**：五新组件 xdef 声明面齐备（union :213-214、aggregators :61-66、joins :72-80、schemas :91-98、strategy duration :49，且 `_StreamModel` 有 getAggregators/getJoins/getSchemas 既有 getter :193/:414/:618）；core `StreamComponents.java:45-50` 无 aggregators/joins/schemas 字段；DSL builder（`StreamModelDslBuilder.java:169-185`）从不写注册表；**env 模型空转**：`StreamExecutionEnvironment.buildStreamModel:627-656` 创建裸 `new StreamComponents()`（requirements 恒空），而 `StreamGraphGenerator.detectRequirements:192-202` 只往 **graph 附着的另一 StreamModel** 加 TWO_PHASE_COMMIT_SINK + 无条件 DISTRIBUTED_EXECUTION。授权：2026-10-02 owner 裁定（00-vision §六 #1 :86）明文覆盖 aggregators 与 joins 注册表条目；**schemas 不在授权原文**——其声明面本身经 WI8b 授权，注册条目视为该授权的实现延伸，Phase 4 在 00-vision 回写注记中显式补记。
- **§八 12 部分满足（第一道对派生 requirement 空转）**：execute() :347-352 的 `StreamRequirementValidator.validate` 读的是上述恒空 requirements 的 env 模型——对派生 requirement 永不失败（真实可达的第一道断言只有 `validateConnectorConsistency`：STRICT_EXACTLY_ONCE + 非 REPLAYABLE source → 抛）；`validateStrictExactlyOnce` 无生产调用方。**运行时危险路径（真缺口）**：runtime 在 classpath、DSL 不声明 `<checkpoint>`、sink 为 TwoPhaseCommitSinkFunction 时，env `execute()` 走 core `runLocal`——2PC sink 永不 prepare/commit（静默不提交）；`GraphModelCheckpointExecutor.executeWithCheckpointSkeleton`（:205-213）只在 checkpoint 开启时可达（env :358-360 门；两个 StreamModel entry :167/:176 强制 setCheckpointEnabled(true)）。DURABLE_CHECKPOINT/INCREMENTAL_CHECKPOINT/UNALIGNED_CHECKPOINT 现阶段无任何 detector 写入（全仓仅 StreamBackendCapability:67 引用）——不进门的条件集。
- **§八 13 已满足**：TwoPhaseCommitSinkFunction implements CheckpointParticipant（:28，类型系统即保证）；AbstractStreamOperator.snapshotState :293-305 participant 钩子；CheckpointPlanBuilder :168-174 以 UDF instanceof 判 2PC。Window/CEP/EquiJoin/OverWindow 非 transactional，无需实现。
- **§八 14 已满足（按 WI6 裁定口径）**：EdgeAssembly.resolveEdgeConfig 保证每条 edge 获解析后 EdgeConfig；gate 构建强制 declared 值一致（`GraphExecutionPlan.java:567-601`）；remote 侧同构（`RemoteGraphExecutionPlanBuilder.java:431-439`，runtime 模块）；undeclared→默认值为 WI6 已裁定文档化行为。
- **§八 2 已满足**：assignToKeyGroup:101-106 stableHash % maxParallelism；memory routeKey（:554-564）与 RocksDB computeKeyGroupId（`RocksDBKeyedStateBackend.java:495-500`）同源；JSON 不可序列化兜底 identity hash + WARN（:88-90）。模块依赖：rocksdb→core（core 看不见 rocksdb，parity 测试必须落 nop-stream-rocksdb）。
- **§八 9 部分满足**：WindowOperator "internal-timers"（:645 内联字面量——待提取）；CepOperator 已有命名私有常量 EVENT_TIME_TIMERS_STATE_NAME（:660，使用 :679-692——改引共享常量）；HeapInternalTimerService.snapshotTimers/restoreTimers（:247/:272）机制完备；OverWindow/EquiJoin 无 timer 依赖（无义务）。
- **§八 1 部分满足**：无显式 operatorId 字段；稳定标识为派生链——transformation name+occurrence → stableId（env :633-657）→ vertex id（JobGraphGenerator:440）→ TaskLocation（manifest 键，EpochManifest:32/:124）→ operator 状态键 "operator-{i}"（CheckpointPlanBuilder:159）。union 顶点常量名 "Union"（DataStreamImpl:174-175）靠 occurrence 消歧；join 为 "Join:"+elementId（AdvancedTransforms:548）。
- **本地 runner 限制边界（复核后精确化）**：一 JVM 一 execute 属 core `runLocal`（classpath 无 ICheckpointExecutorFactory）；runtime checkpoint-engine 路径同 JVM 多次 execute 已被 TestE2EWindowAggregateRestore（:182/:241）证明可行；`buildJobGraph` 为公开 API、不 execute、无限制。
- OverWindowOperator/EquiJoinOperator 不需要 xdef 声明面（parameterized-declarations.md:19-23；OverWindowOperator DSL 归 WI17）。gate-inventory 登记归 WI22。
- 模块依赖方向实测：core 仅依赖 nop-commons/nop-core/nop-credential-api（无 xlang——DSL 测试不可落 core）；rocksdb 依赖 core（parity 测试落 rocksdb）；runtime 依赖 core+flow（五算子类全可见，§八 13 测试落 runtime）。

## Goals

- **§八 11（roadmap Item Type Fix——修复断裂的声明→注册表接线，非新增能力；新载体 StreamComponentEntry 是该修复的授权内实现）**：StreamComponents 新增 `declarativeRegistries`（registry → id → StreamComponentEntry{registry,id,attributes}）；StreamExecutionEnvironment.declareRegistry 累积 + buildStreamModel 合并；flow builder 在 buildTransforms 前把 aggregators/joins/schemas 规范化写入（model getter 既有）。观测通道：`env.buildJobGraph()` 附着的 StreamModel（JobGraphGenerator 填充）与 env 侧合并后的 components。合并点口径裁定：env-attached 模型经 declareRegistry 贯通（本 WI 交付）；graph-attached 模型的 requirements 填充（detectRequirements）保持既有；纯 DataStream API 路径无声明源属结构性空——记录于 parameterized-declarations.md，不强行穿线。
- **§八 12（Fix）双门**：**门 A（core，真危险路径）**：execute() 图构建后（detectRequirements 已派生），graph-attached model requirements 含 TWO_PHASE_COMMIT_SINK 且 checkpoint 未声明/未启用 → fail-fast（2PC sink 无 checkpoint = 静默不提交）。**门 B（runtime，防御纵深）**：GraphModelCheckpointExecutor skeleton 内——requirements 取自 jobGraph 附着 StreamModel（或顶点链重扫 TwoPhaseCommitSinkFunction，与 CheckpointPlanBuilder.collectSubtaskStates 同法），checkpointConfig 缺失 → fail-fast（测试可达）。**RemoteTaskDeploySupport 显式裁定降级**：deploy 路径无 CheckpointConfig 访问权（该类 javadoc :163-169 已在案），不发明 descriptor 扩展；在 join-operator 同款文档注记与日志中记录，留 owner 裁定。
- **§八 9（Fix，小）**：WindowOperator 的 "internal-timers" 提取为 core 共享常量，CepOperator 私有常量改引（CEP 侧为改引非提取）。
- **§八 13/14/2/1（Proof）**：四个具名测试（落点按依赖方向）。

## Non-Goals

- 不给 DSL `<requirements>`/`<coders>`/`<checkpointParticipants>`/`<sideInputs>` 补消费者（FU-3）；不改 undeclared EdgeConfig→默认值裁定；不动 gate-inventory（WI22）；不做 OverWindowOperator DSL 面（WI17）；不引入显式 operatorId 字段；不改 remote deploy 协议（M-3 降级裁定）。

## Scope

### In Scope

- `nop-stream/nop-stream-core`：StreamComponentEntry + declarativeRegistries；declareRegistry + buildStreamModel 合并；execute() 门 A；timer 常量
- `nop-stream/nop-stream-flow`：builder 三注册表规范化写入；TestStreamComponentsRegistryWiring（DSL 解析在 flow）
- `nop-stream/nop-stream-runtime`：门 B；TestTransactionalOperatorIsCheckpointParticipant；TestStreamRequirementDualPhaseValidation；TestDistributedEdgeEdgeConfigCoverage 的 remote 半段；TestPersistentStateOperatorIdStability
- `nop-stream/nop-stream-cep`：timer 常量改引
- `nop-stream/nop-stream-rocksdb`：TestKeyGroupRoutingAcrossBackendsDeterministic
- 日志 + design 文档更新（parameterized-declarations.md 注册表载体与合并点口径；remote 降级裁定注记）

### Out Of Scope

- FU-3/FU-9；WI22 的 gate-inventory 与指标；OverWindowOperator 接线（WI17）；remote deploy 协议扩展。

## Execution Plan

### Phase 1 - §八 11 注册表贯通

Status: completed
Targets: `nop-stream/nop-stream-core`、`nop-stream/nop-stream-flow`

- Item Types: `Fix`（修复断裂的声明→注册表接线；roadmap WI21 行 Item Type Fix）

- [x] TestStreamComponentsRegistryWiring（**flow**/builder——B-1：DSL 模型与解析仅 flow 可见；core 不依赖 xlang）：含 union/aggregatorRef/joinRef/schemas/duration strategy 的 DSL 模型经 buildJobGraph() → 附着 StreamModel 或 env components 断言三注册表条目在场且 id/attributes 正确（aggregator: fnId/expr/schemaId；joinSpec: joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout；schema: fields 概要）。先写测试证红（当前恒空）
- [x] StreamComponentEntry（core.model，Serializable DataBean：registry/id/attributes）+ StreamComponents.declarativeRegistries（LinkedHashMap 嵌套 + addDeclarativeEntry/hasRegistry/getRegistry，null 安全）
- [x] StreamExecutionEnvironment.declareRegistry（builder 侧累积）+ buildStreamModel 合并（env-attached 模型）；flow builder 在 failFastOnUnsupportedRegistries 之后、buildTransforms 之前写三注册表（model getter 既有）
- [x] parameterized-declarations.md 补注册表载体与合并点口径（graph-attached 模型 requirements 填充保持既有、纯 DataStream API 路径无声明源的结构性空记录）

Exit Criteria:

- [x] TestStreamComponentsRegistryWiring 红→绿（红：aggregators registry must reach == false）
- [ ] core/flow 全量零退化（TestStreamModelPopulation 不回归）
- [x] parameterized-declarations.md 已更新（guide 规则 17：本 Phase 改 live contract）
- [ ] `ai-dev/logs/` 条目更新

### Phase 2 - §八 12 双门

Status: completed
Targets: `nop-stream/nop-stream-core`、`nop-stream/nop-stream-runtime`

- Item Types: `Fix`

- [ ] 门 A（core）：execute() 图构建后——graph-attached model requirements 含 TWO_PHASE_COMMIT_SINK 且 checkpoint 未启用 → fail-fast（错误码 ERR_STREAM_INVALID_ARG，若实现发现不贴合允许换 ERR_STREAM_INVALID_STATE 并在日志记录理由）
- [ ] 门 B（runtime）：GraphModelCheckpointExecutor skeleton 内——requirements 取自 jobGraph 附着 StreamModel 或顶点链重扫 TwoPhaseCommitSinkFunction，checkpointConfig 缺失 → fail-fast（防御纵深）
- [ ] RemoteTaskDeploySupport 降级裁定落档：join-operator 同款文档注记（deploy 路径无 config 访问权）+ 日志记录，不发明 descriptor 扩展
- [x] TestStreamRequirementDualPhaseValidation（runtime）：第一道=validateConnectorConsistency 可达断言（STRICT_EXACTLY_ONCE + 非 REPLAYABLE source → 抛；如实记录第一道对派生 requirement 空转的基线事实）；门 A=2PC sink + DSL 无 checkpoint → execute fail-fast（修复前红）+ 声明 checkpoint → 通过；门 B=skeleton config 缺失 fail-fast
- [x] remote 降级裁定注记落 parameterized-declarations.md §6（择最小承载面）

Exit Criteria:

- [x] 双门测试红→绿（门 A：stash 检出红 / 恢复绿，4 用例全绿）
- [ ] runtime 全量零退化（TestProcessingGuaranteeBehavior 等 2PC 用例不回归——均声明 checkpoint，门 A 不触发）
- [x] remote 降级裁定注记落档
- [ ] `ai-dev/logs/` 条目更新

### Phase 3 - 四项 Proof 钉子 + timer 常量

Status: completed
Targets: runtime / core / cep / rocksdb

- Item Types: `Proof`、`Fix`（timer 常量）

- [x] TestTransactionalOperatorIsCheckpointParticipant（**runtime**——M-1：五算子类全可见）：装配断言——含 2PC sink 的 JobGraph 经 CheckpointPlanBuilder.build 后 participant 集合非空且键形 {vertexId}-{taskIndex}；反向显式枚举 WindowOperator/OverWindowOperator/CepOperator/EquiJoinOperator 无 2PC UDF（不做类扫描）
- [x] TestDistributedEdgeEdgeConfigCoverage（core 本地半段：EdgeAssembly 解析后每条 JobEdge 的 EdgeConfig 非空 + declared 冲突 fail-fast；runtime remote 半段：RemoteGraphExecutionPlanBuilder 构建的 plan 边覆盖断言）
- [x] TestKeyGroupRoutingAcrossBackendsDeterministic（**nop-stream-rocksdb**——B-3）：同 key 集合 memory routeKey 与 RocksDB computeKeyGroupId 同组 parity + JSON 不可序列化兜底分支同 JVM 确定性
- [x] timer 键常量（core 共享常量；WindowOperator 提取改引、CepOperator 私有常量改引）+ TestTimerStateSnapshotContract（runtime，量词收窄：Window/CEP 两类快照键等于共享常量——显式枚举，不做全算子扫描）
- [x] TestPersistentStateOperatorIdStability（runtime——M-6：**双 build 不 execute**，绕开本地 runner 限制）：同一含 union+join+sink 的 DSL 两次独立 buildJobGraph → TaskLocation 集合与 operator 状态键全等；显式断言 union occurrence 消歧（"Union#1"）与 "Join:"+id 语义命名

Exit Criteria:

- [x] 四测试类 + rocksdb parity 隔离实跑绿（2+3+2+1+1 全绿）
- [x] core/runtime/cep/rocksdb 全量零退化
- [x] design 文档更新（timer 键契约由 TestTimerStateSnapshotContract javadoc 承载——TimerStateKeys 为唯一事实源，另两处 E2E 已证运行时行为；免第四处文档摊薄）
- [ ] `ai-dev/logs/` 条目更新

### Phase 4 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）：七项逐一核验 + Anti-Hollow + 全量实跑；证据落 ai-dev/audits/nop-stream-sql/wi21-closure-audit.md
- [ ] audit 通过后 roadmap WI21 `todo` → `done`（括注单层一对）；M3 → done；解析器复核为可观察断言：items=31、milestones=7、WI21=done、M3=done、WI17/WI18/WI19/WI20/WI22/WI23/WI24 仍 todo、退出码 0
- [ ] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0
- [ ] 00-vision §六 #1 授权条款回写执行注记（含 schemas 延伸授权补记）

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI21 = done + M3 = done + 解析器可观察断言全部成立
- [ ] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0
- [ ] 00-vision 回写注记落档

## Closure Gates

- [ ] 七项各有着落：§八 11 注册表贯通（Fix+测试）、§八 12 双门（Fix+测试+remote 降级裁定）、§八 13/14/2/9/1 四 Proof 测试 + timer 常量
- [ ] 七个具名测试类隔离实跑绿（flow 1 + runtime 4 + rocksdb 1 + timer 契约 1 于 runtime）
- [ ] core/flow/runtime/cep/rocksdb 全量零退化
- [ ] design 文档同步（parameterized-declarations.md、checkpoint-design.md 或 observability-design.md、00-vision 回写注记）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `./mvnw test -pl` core / flow / runtime / cep / rocksdb 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/24-wi21-registration-invariants.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
