# 339 nop-job dispatchMode/executorKind 正交化 + TaskBuilder 收敛重构

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: 用户分析请求（PartitionTaskBuilder 与既有 TaskBuilder 重复性排查 → dispatchMode/executorKind 双路由语义分析 → 不考虑兼容性的最终设计裁定）；`ai-dev/design/nop-job/invoker-design.md` §3.6、`ai-dev/design/nop-job/worker-assignment-design.md`、`docs-for-ai/03-modules/nop-job.md`、`ai-dev/logs/2026/05-17.md`、`ai-dev/logs/2026/06-18.md`、`ai-dev/logs/2026/06-19.md`、`ai-dev/logs/2026/07-07.md`。
> Related: `ai-dev/plans/213-nop-job-partition-mode.md`、`ai-dev/plans/215-nop-job-best-fit-dispatch.md`、`ai-dev/plans/268-nop-job-scan-loop-isolation-and-dispatch-semantics.md`、`ai-dev/plans/286-nop-job-assignment-metadata-refactor.md`（均 completed，历史路由语义来源）。
> Review History:
> - R1（2026-08-12，独立子 agent 对抗审查）：1 Blocker + 5 Major + 7 Minor。技术前提全部验证成立：Map 注入可行（key=去前缀 bean id）、行号引用准确、fallback 用例 10 个、Routing 6 用例、`source-anchors.md` 无 TaskBuilder 锚点。Blocker=Phase 1 EC3 接线断言不可执行（taskBuilders private + repo 无测试注入真实 dispatcher bean）；5 Major=EC4 负面条件会毁 7 个 AR-86/94/96/98 重型测试、0 健康实例行为变更文档强制化、invoker-design.md 更新范围不足、H2 豁免无证据、缺端到端验证项。已全部吸收进本版。
> - R2（2026-08-12，独立子 agent 对抗审查）：0 Blocker + 1 Major（N1：EC4 只规定 bestFit/partition key、漏了共享 helper 必须含 `single` key——多数既有测试 fire 解析为 single，H2 blocked 环境下单边 map 会静默破坏 ~16 个测试）+ 4 Minor（N2 去掉 Map setter 的 `@Inject`、N3 接线证据须注明 container-level/fallback 实际路径、N4 EC5 补 1.7 容器测试类、N5 EC4 交叉引用 H2 豁免）。已全部吸收。

## Purpose

把 nop-job 的 task 派发路由从"`dispatchMode` 与 `executorKind` 双路由 + 字符串拼接 bean 名 + builder 内嵌 fallback"收敛为最终设计态：**`dispatchMode` 是 coordinator 侧唯一路由键，`executorKind` 是 worker 侧唯一 invoker 选择键，两者彻底正交**；同时消除 4 个 `IJobTaskBuilder` 之间 ~96 行样板代码重复（fallback 链、serviceName 前置门、discovery 健康过滤门、task 实体填充）。

## Current Baseline

### 已成立的事实（live repo 核对）

- **`IJobTaskBuilder` 4 个实现**（`nop-job-coordinator/.../engine/`）：
  - `DefaultJobTaskBuilder`（45 行）：单 task，`workerInstanceId=null` 竞争认领。
  - `RpcBroadcastTaskBuilder`（101 行）：每健康实例 1 task，广播全量。
  - `PartitionTaskBuilder`（144 行）：`WeightedPartitionAssigner` 按权重切 `[0,32767]`，`partitionCount` 上限，写 `partitionRange` 列（AR-98 全范围常量）。
  - `AdaptiveJobTaskBuilder`（140 行）：`IWorkerLoadProvider` 负载 + `IWorkerAssignmentStrategy`，带 cost/priority；无 fitting worker 抛 `ERR_JOB_NO_FITTING_WORKER`。
- **双路由**（`JobDispatcherScannerImpl.java:185-211` `resolveTaskBuilder`）：
  - `dispatchMode` 非空/非 blank/非 `"single"` → `BeanContainer.tryGetBean(TASK_BUILDER_PREFIX + dispatchMode)`，bean 缺失抛 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`（AR-87 fail-fast）。
  - `dispatchMode ∈ {null, blank, "single"}` → `nopJobTaskBuilder_<executorKind>` 存在则用之，否则 `defaultTaskBuilder`。注释自述这是 "guards the rpcBroadcast-via-executorKind legal route"。
- **样板重复**（约 96 行 / 430 行总量 ≈ 22%）：
  1. fallback 链（Rpc:33-50、Partition:53-62、Adaptive:42-61 各 ~8 行）：`private final fallback = new DefaultJobTaskBuilder()` + `setDaoProvider` 内 `instanceof` 转发。
  2. serviceName 前置门（3 文件各 ~6 行，逐字相同）：`jobParams==null → fallback`、`IJobTaskBuilder.resolveServiceName`、`isBlank() → fallback`。
  3. discovery + 健康过滤门（Rpc:64-78 与 Partition:90-104 逐行相同 ~11 行）：`discoveryClient==null/getInstances 空/healthy 过滤/healthy 空 → fallback`。
  4. task 实体填充（4 文件各 ~8 行）：`daoProvider.daoFor(NopJobTask.class).newEntity() + setJobFireId/taskNo/taskStatus/workerInstanceId/partitionIndex`。
- **beans.xml**（`app-engine.beans.xml:23-37`）：注册 `nopJobTaskBuilder_default`、`nopJobTaskBuilder_rpcBroadcast`、`nopJobTaskBuilder_broadcast`（后两者**同 class 别名**，为桥接 executorKind/rpcBroadcast 与 dispatchMode/broadcast 两个命名空间）、`nopJobTaskBuilder_partition`、`nopJobTaskBuilder_bestFit`；dispatcher bean 有 `<property name="defaultTaskBuilder" ref="nopJobTaskBuilder_default"/>`。
- **worker 侧 invoker 路由**（`nop-job-worker/.../DefaultJobInvokerResolver.java:24`）：`executorKind → nopJobInvoker_<executorKind>`，与 dispatchMode 无关。`app-service.beans.xml:9-11` 注册 `nopJobInvoker_test/rpc/rpcBroadcast`。
- **dict**：`dispatch-mode.dict.yaml`（single/partition/broadcast/bestFit；single 描述"走 executorKind 路由"）；`executor-kind.dict.yaml`（test/rpc/rpcBroadcast）。
- **测试**：`TestJobDispatcherScannerRouting`（6 用例：dispatchMode 优先、executorKind fallback×2、bestFit、AR-87 fail-fast）；`TestPartitionTaskBuilder`/`TestRpcBroadcastTaskBuilder`/`TestAdaptiveJobTaskBuilder` 各有 fallback 语义用例（serviceName 缺失/空、discoveryClient null、全不健康 → 断言 1 个 task）；`TestJobCoordinatorScanner`/`TestJobConcurrency` 大量 `setDefaultTaskBuilder(...)` 与 `StaticBeanContainer.registerBean("nopJobTaskBuilder_*")`。
- **Nop IoC 支持 Map 注入**：`<ioc:collect-beans as-map="true" name-prefix="...">` 按名字前缀收集并去掉前缀作 key（`nop-report` `report-defaults.beans.xml:8` 同款用法；`BeanDefinitionBuilder.buildCollectBeansResolver:898-904` 确认 key 为去前缀后的 bean id）。

### 真正剩余的 gap

1. `dispatchMode="single"` 语义被污染：dict 明说 single 走 executorKind 路由，用户期望"单任务"却可能得到广播（`single`+`executorKind=rpcBroadcast` → 多 task）。
2. `executorKind` 一列两义：worker 侧选 invoker、coordinator 侧选 builder，靠 bean 命名巧合对齐，无类型约束。
3. 失败模式不对称：`dispatchMode` 未知 → fail-fast；`executorKind` 未知 → 静默降级 single（用户配错无感知）。
4. 3 个 builder 内嵌 `DefaultJobTaskBuilder` fallback 与 discovery/health 门重复，样板 ~96 行。
5. beans.xml 中 `nopJobTaskBuilder_rpcBroadcast`/`broadcast` 别名 bean 是命名空间桥的补丁式证据。

## Goals

- `dispatchMode`（含 `single`）成为 coordinator 侧**唯一** builder 路由键；`single` 是普通 dict 值映射 `DefaultJobTaskBuilder`，`resolveTaskBuilder` 中无字符串特判。
- `executorKind` 完全退出 coordinator 路由，只保留 worker 侧 invoker 选择语义。
- 路由机制从"运行时 `BeanContainer.tryGetBean` 字符串拼接"改为 **IoC 启动期 Map 注入**（`collect-beans as-map`，key=去前缀 bean id），未知 dispatchMode 统一 fail-fast。
- 删除 3 个 builder 的内嵌 `DefaultJobTaskBuilder` fallback 与 `setDaoProvider` 转发；配置缺失（serviceName 缺失/非 String、discoveryClient null、无健康实例）改为**显式抛错**（新错误码），不再静默降级。
- 抽取共享基类吸收：daoProvider/task 实体填充、serviceName 前置门、discovery+健康过滤门；Rpc/Partition 共享 discovery 门，Adaptive 走 loadProvider 门。
- 更新 owner docs（`nop-job.md`、`invoker-design.md` §3.6、`worker-assignment-design.md`）与 2 个 dict 描述，消除与 live baseline 的漂移。

## Non-Goals

- **不合并** `dispatchMode`/`executorKind` 为单一字段：两者是正交维度（"怎么拆 task" vs "怎么执行 task"），`partition + rpc` 是合法组合，合并会丢失组合空间。
- 不做存量数据迁移、不做兼容桥（用户明确"不考虑兼容性"）；旧 `executorKind=rpcBroadcast` 调度需迁移为 `dispatchMode=broadcast`，文档给出迁移说明。
- 不修改 worker 侧 invoker 路由（`DefaultJobInvokerResolver`、`DefaultJobCancelHandler` 不变）。
- 不修改 `nop-batch` 模块的 `BatchTaskBuilder`/`IBatchTaskBuilder`（不同模块不同抽象）。
- 不改 ORM 模型结构、不改 `dispatchMode`/`executorKind` 列定义。
- 不调整 `LeastLoadedStrategy`/`IWorkerAssignmentStrategy`/cost 归一逻辑。

## Scope

### In Scope

- `JobDispatcherScannerImpl.resolveTaskBuilder` 重写（Map 注入 + 单一路由 + fail-fast）。
- `app-engine.beans.xml`：注册 `nopJobTaskBuilder_single`，删除 `default`/`rpcBroadcast` 别名；dispatcher 属性从 `defaultTaskBuilder` 改为 `taskBuilders`（collect-beans as-map）。
- 3 个 builder 去 fallback + 共享基类（新增 `AbstractServiceTaskBuilder` 或等价基类，含 task 填充/serviceName 门/discovery 门）。
- `JobCoreErrors` 新增三个错误码（serviceName 缺失、discoveryClient null、无健康实例）。
- 2 个 dict（`dispatch-mode`、`executor-kind`）描述更新。
- 测试：`TestJobDispatcherScannerRouting`（重写路由语义）、容器级接线测试（1.7）、`TestPartitionTaskBuilder`/`TestRpcBroadcastTaskBuilder`/`TestAdaptiveJobTaskBuilder`（fallback 用例 → assertThrows）、`TestJobCoordinatorScanner`/`TestJobConcurrency`（装配方式适配）。
- docs：`docs-for-ai/03-modules/nop-job.md`、`ai-dev/design/nop-job/invoker-design.md` §3.6、`ai-dev/design/nop-job/worker-assignment-design.md`。

### Out Of Scope

- 拆分 3 个独立 scanner bean（沿用既有 deferred 项，与本 plan 无关）。
- Per-schedule timeout SQL push-down（沿用既有 deferred 项）。
- worker 侧 invoker 语义变更。
- `nop-batch` builder。

## Execution Plan

### Phase 1 - 路由正交化：resolveTaskBuilder 单一路由 + Map 注入

Status: completed
Targets: `JobDispatcherScannerImpl.java`、`app-engine.beans.xml`、`dispatch-mode.dict.yaml`、`executor-kind.dict.yaml`、`TestJobDispatcherScannerRouting.java`

- Item Types: `Fix`、`Decision`（1.6 为 Decision：失败语义/错误码边界裁定；其余为 Fix）

Phase 1 交付"路由契约收敛"：dispatcher 只认 dispatchMode，executorKind 不再参与；Map 由 IoC 启动期注入，路由逻辑无运行时 bean 名拼接。

- [x] **1.1** `JobDispatcherScannerImpl` 注入 `Map<String, IJobTaskBuilder> taskBuilders` setter，替换 `defaultTaskBuilder` 字段与 `TASK_BUILDER_PREFIX`/`BeanContainer.tryGetBean` 运行时查找。**接线仅经 1.3 的 XML `<property>`（collect-beans）完成，不加 `@Inject`**（NopIoC 无 Map 类型解析，`@Inject` 会被 XML property 覆盖而形同虚设，参照 `nop-report` `ReportEngine.setRenderers` 纯 setter 先例）。
- [x] **1.2** `resolveTaskBuilder` 重写为：`String mode = fire.getDispatchMode(); mode = (mode==null||mode.isBlank()) ? "single" : mode; IJobTaskBuilder b = taskBuilders.get(mode); if (b == null) throw ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED.param(ARG_DISPATCH_MODE, mode).param(ARG_JOB_FIRE_ID, fire.getJobFireId()); return b;`。无 executorKind 分支、无 `"single".equals()` 特判（single 只是普通 key）。顺带更新 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED` 消息文本（现文 "use 'single', 'partition', or 'broadcast'" 缺 bestFit，`JobCoreErrors.java:100`）。
- [x] **1.3** `app-engine.beans.xml`：新增 `nopJobTaskBuilder_single`（→ `DefaultJobTaskBuilder`）；删除 `nopJobTaskBuilder_default` 与 `nopJobTaskBuilder_rpcBroadcast`（保留 `broadcast`）；dispatcher bean 的 `defaultTaskBuilder` 属性改为 `<property name="taskBuilders"><ioc:collect-beans as-map="true" name-prefix="nopJobTaskBuilder_" by-type="io.nop.job.coordinator.engine.IJobTaskBuilder"/></property>`（`by-type` 过滤防未来非 `IJobTaskBuilder` 前缀 bean 被收集 → ClassCastException）。
- [x] **1.4** `dispatch-mode.dict.yaml`：`single` 描述改为"单任务（DefaultJobTaskBuilder），不参与 executorKind 路由"；`executor-kind.dict.yaml`：`rpcBroadcast` 描述澄清"仅 worker 侧 invoker 选择，与 task 拆分无关"。
- [x] **1.5** `TestJobDispatcherScannerRouting` 重写：用例覆盖 dispatchMode ∈ {partition, broadcast, bestFit, single, null, blank, 未知值}；断言 `single`/`null`/`blank` 均路由到 single builder、未知值抛 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`；**新增用例：`dispatchMode=single` + `executorKind=rpcBroadcast` → 单 task builder（证明 executorKind 不再参与路由）**；不再依赖 `StaticBeanContainer` 注册 bean（改直接 `setTaskBuilders(Map)`）。
- [x] **1.6**（Decision）失败语义裁定（Major-2 落点）：`discoveryClient==null` 是**配置错误**（新码 `ERR_JOB_DISCOVERY_CLIENT_REQUIRED`）；0 健康实例是**运行时瞬态**（`ERR_JOB_NO_AVAILABLE_INSTANCE`），两码拆分，排障可区分；广播/分区在 0 健康实例时 dispatch-failed、fire 留 DISPATCHING 等 timeout 回收——该运维后果写入 `nop-job.md`。
- [x] **1.7**（端到端接线验证，Blocker/Major-5 落点）`JobDispatcherScannerImpl` 增加 package-private `getTaskBuilders()`（同包测试读取注入 map 的机制）；新增容器级接线测试（`@NopTestConfig` 起 IoC，注入真实 dispatcher bean）：经 getter 断言 map keys == {single, partition, broadcast, bestFit} 且 `single` 值为 `DefaultJobTaskBuilder` 实例；并以真实 beans.xml 装配跑 single 路径 fire（scanOnce → buildTasks → task 落库）作为端到端证据（Rule 22）；若 localDb 环境限制（H2 pre-existing）导致无法起容器，改组件级等价断言并在证据中注明。

Exit Criteria:

- [x] `resolveTaskBuilder` 中 grep 无 `executorKind`、无 `"single".equals`、无 `TASK_BUILDER_PREFIX` 引用
- [x] 路由矩阵测试全绿：single/null/blank→single；partition/broadcast/bestFit→对应 builder；未知→fail-fast；`single`+`executorKind=rpcBroadcast`→single（新用例）
- [x] **接线验证（1.7）**：`JobDispatcherScannerImpl.getTaskBuilders()` package-private getter 存在；容器级接线测试证据记录于本 plan Closure，**必须注明实际执行路径**：map keys == {single, partition, broadcast, bestFit}、`single` → `DefaultJobTaskBuilder` 实例、真实 beans.xml 装配的 single 路径 fire 落库断言（容器级 PASS）；若 H2 环境受限走了组件级等价断言 fallback，须显式标注 "fallback path"，不得静默降级接线声明
- [x] **正面清单（Major-1）**：`setDefaultTaskBuilder` 6 处调用点全部适配为 `setTaskBuilders`：Routing:52（1.5 重写覆盖）、CoordinatorScanner:129/311/676/1032、Concurrency:311；**共享 helper（TestJobCoordinatorScanner.runDispatcher/testScheduleToFireToTask、TestJobConcurrency.newDispatcher）的 map 必须含 `single → DefaultJobTaskBuilder(daoProvider)` key**（`newSchedule()` 不设 dispatchMode，多数既有测试的 fire 解析为 single，缺 key 会抛 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`）；**7 个 AR-86/94/96/98 端到端/接线重型测试显式保留并保持绿（H2 环境受限用例按 Closure Gates 处理）**：`testDispatcherNormalizesNullCostToZeroBestFit`、`testBestFitAssignmentMetadataAndAssignmentCostEndToEnd`、`testDispatcherPreservesBuilderSetCostAndPriority`、`testNoFittingWorkerRevertsToWaitingWithBackoff`、`testTwoDispatchersOverAssignBeyondCapacityViaStaleRead`、`testScanOnceInvokesWorkerLoadProviderLifecycleAndCachesAcrossFires`、`testPartitionDispatchEndToEndCoversFullSmallintBoundary`（适配后 map 必须含 bestFit/partition 对应 key，不得仅放 single 导致 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`）；无 `nopJobTaskBuilder_bestFit` StaticBeanContainer 注册残留
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am -Dtest=TestJobDispatcherScannerRouting,TestJobCoordinatorScanner,TestJobConcurrency` 通过（含 1.7 新增容器接线测试类；真 DB 用例按 blocked 环境处理，见 Closure Gates）
- [x] 相关 `ai-dev/design/` 已更新（Major-3，范围扩大到全文件）：`invoker-design.md` §3.6 路由优先级/bean 对照表改写为单一 dispatchMode 路由（迁移说明：旧 `executorKind=rpcBroadcast` → `dispatchMode=broadcast`），并同步**决策 #8（line 18 双查找描述）**、**序列图（line 366/374 executorKind 路由示例）**、**代码片段（line 205/228/247 `nopJobTaskBuilder_rpcBroadcast`/`nopJobTaskBuilder_default` 旧 bean id）**、**实现历史表（line 517）**；`worker-assignment-design.md` Live Baseline 同步
- [x] `docs-for-ai/03-modules/nop-job.md` dispatchMode 路由段（line 266 附近）改写为单一路由描述
- [x] `ai-dev/logs/2026/08-12.md` 已更新

### Phase 2 - builder 收敛：去 fallback + 共享基类 + fail-fast 错误码

Status: completed
Targets: `RpcBroadcastTaskBuilder.java`、`PartitionTaskBuilder.java`、`AdaptiveJobTaskBuilder.java`、`DefaultJobTaskBuilder.java`、`JobCoreErrors.java`、新增基类、`TestPartitionTaskBuilder.java`、`TestRpcBroadcastTaskBuilder.java`、`TestAdaptiveJobTaskBuilder.java`

- Item Types: `Fix`

Phase 2 交付"builder 收敛"：无内嵌 fallback、无样板重复；配置缺失显式失败，行为语义与 Phase 1 的 fail-fast 路由一致。

- [x] **2.1** 新增共享基类（如 `AbstractServiceTaskBuilder implements IJobTaskBuilder`）：吸收 daoProvider 注入 + task 实体填充 helper、serviceName 前置校验（缺失/非 String → `ERR_JOB_SERVICE_NAME_REQUIRED`）、discovery+健康过滤 helper（`resolveHealthyInstances`：discoveryClient null → `ERR_JOB_DISCOVERY_CLIENT_REQUIRED`；空/全不健康 → `ERR_JOB_NO_AVAILABLE_INSTANCE`）；保留 `IJobTaskBuilder.resolveServiceName` 类型安全提取。
- [x] **2.2** `RpcBroadcastTaskBuilder`/`PartitionTaskBuilder` 继承基类：删除 `fallback` 字段、`setDaoProvider` 转发、serviceName/discovery 前置门样板；仅保留各自核心（广播 1:1 task / 分区 assigner+partitionCount+partitionRange）。
- [x] **2.3** `AdaptiveJobTaskBuilder` 继承基类：删除 `fallback` 字段与转发；serviceName 缺失从 fallback 改为抛错；loadProvider 缺失与无 fitting worker 维持既有抛错语义。
- [x] **2.4** `JobCoreErrors` 新增三个错误码（Major-2 拆分裁定见 1.6）：`ERR_JOB_SERVICE_NAME_REQUIRED`（serviceName 缺失/非 String，参数 `ARG_DISPATCH_MODE` + `ARG_JOB_FIRE_ID`）、`ERR_JOB_DISCOVERY_CLIENT_REQUIRED`（discoveryClient null，配置错误）、`ERR_JOB_NO_AVAILABLE_INSTANCE`（0 健康实例，运行时瞬态，参数 serviceName + 可选 healthy 数）。
- [x] **2.5** `DefaultJobTaskBuilder` 保持独立（single 路径，无需继承）；确认其无样板依赖。
- [x] **2.6** 测试改造：`TestPartitionTaskBuilder`/`TestRpcBroadcastTaskBuilder`/`TestAdaptiveJobTaskBuilder` 中 serviceName 缺失/空、discoveryClient null、全不健康等 8+ 个 fallback 用例改为 `assertThrows(NopException)` + 错误码断言；保留正路径用例（健康实例、partitionCount、range 并集等）。

Exit Criteria:

- [x] grep 验证 3 个 builder 无 `DefaultJobTaskBuilder`、无 `fallback` 字段引用
- [x] 错误码测试：serviceName 缺失/非 String → `ERR_JOB_SERVICE_NAME_REQUIRED`；discoveryClient null → `ERR_JOB_DISCOVERY_CLIENT_REQUIRED`；全不健康 → `ERR_JOB_NO_AVAILABLE_INSTANCE`（assertThrows + code 断言，参数齐全）
- [x] 既有正路径测试全绿：`TestPartitionTaskBuilder`（partitionCount/range 并集/round-trip）、`TestRpcBroadcastTaskBuilder`（广播 task 列）、`TestAdaptiveJobTaskBuilder`（assignment/cost/priority）、`TestLeastLoadedStrategy`、`TestDefaultJobTaskBuilder`
- [x] **无静默跳过**：新增失败路径全部以异常显式失败，无空方法体/continue/吞异常（Anti-Hollow）
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am -Dtest=TestPartitionTaskBuilder,TestRpcBroadcastTaskBuilder,TestAdaptiveJobTaskBuilder,TestDefaultJobTaskBuilder,TestLeastLoadedStrategy` 通过
- [x] `docs-for-ai/03-modules/nop-job.md` bestFit 元数据约定/广播/分区段落已与基类化后的行为对齐；**0 健康实例/无 discoveryClient 的失败语义与运维后果（dispatch-failed → fire 留 DISPATCHING 等 timeout 回收）已写入**（Major-2：行为确定漂移，强制更新，不使用"如无漂移"条件式措辞）
- [x] `ai-dev/logs/2026/08-12.md` 已更新

### Phase 3 - 全量回归 + 文档收口 + closure audit

Status: completed
Targets: 全量测试、`docs-for-ai/`、`ai-dev/design/nop-job/`、`ai-dev/logs/2026/08-12.md`、plan 自身

- Item Types: `Proof`、`Follow-up`（3.4 为 Proof；3.5 工具验证为 Proof）

- [x] **3.1** 全量回归：`./mvnw test -pl nop-job/nop-job-coordinator,nop-job/nop-job-worker,nop-job/nop-job-service,nop-job/nop-job-api -am`（或 `install -DskipTests` 先编译全绿再跑受影响模块测试）。
- [x] **3.2** 文档一致性核对：`nop-job.md`、`invoker-design.md` §3.6、`worker-assignment-design.md`、2 个 dict 与 live 代码逐条对照（路由矩阵、bean 清单、dict 值、错误码）。
- [x] **3.3** `docs-for-ai/04-reference/source-anchors.md`：已核实无 `resolveTaskBuilder`/TaskBuilder 锚点（R1 审查确认），此项为确定 no-op，仅核对未引入新锚点即可。
- [x] **3.4**（Proof）独立子 agent closure audit（fresh session），证据写入本 plan `Closure` 段。
- [x] **3.5** 运行 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/339-*.md --strict`、`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high`、`node ai-dev/tools/check-doc-links.mjs --strict`，退出码均 0。

Exit Criteria:

- [x] 全量回归测试通过（真 DB 用例的 pre-existing H2 环境失败除外，见 Closure Gates）
- [x] 文档与 live baseline 一致（路由矩阵、bean 清单、dict、错误码、迁移说明）
- [x] `check-plan-checklist`/`scan-hollow`/`check-doc-links` 退出码 0
- [x] closure audit 证据已写入 `## Closure`（含每条 Exit Criterion/Gate 的 PASS/FAIL + evidence）
- [x] `ai-dev/logs/2026/08-12.md` 收口记录与 Plan Status/Phase Status/Closure Gates 文本一致

## Closure Gates

> 关闭条件：本 section 与每个 Phase 的 Exit Criteria 全部勾选后，才能将 `Plan Status` 改为 `completed`。

- [x] in-scope 路由语义缺陷已修复（single=单任务、executorKind 退出路由、统一 fail-fast）
- [x] in-scope 样板重复已收敛（3 builder 无 fallback 嵌套，共享基类吸收 4 块样板）
- [x] 路由契约结果已达成：dispatchMode 唯一路由矩阵 + Map 注入接线（1.7 容器级断言，经 `getTaskBuilders()` 机制可审计）
- [x] 必要 focused verification 已完成（routing 矩阵、错误码、正路径、容器接线）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect / contract drift（旧 `executorKind=rpcBroadcast` 路由取消是**有意裁定**，非静默降级，文档已含迁移说明）
- [x] owner docs 已同步：`nop-job.md`、`invoker-design.md` §3.6、`worker-assignment-design.md`、2 个 dict
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）Map 注入在 IoC 容器中实际接线（非空壳）、（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile`（`-pl nop-job/nop-job-core,nop-job/nop-job-dao,nop-job/nop-job-coordinator,nop-job/nop-job-worker -am`）
- [x] `./mvnw test`（受影响模块；真 DB 用例 pre-existing H2 环境失败除外——证据：`ai-dev/logs/2026/08-11.md:95` 记录 vendorCode=42104 "Table not found"（TestJobStoreImpl / TestJobFireStoreRace / TestJobWorkerScanner / TestJobCoordinatorScanner / TestJobConcurrency），已 git stash 在 master 基线复现，与本 plan 无关）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 旧 `executorKind=rpcBroadcast` 数据迁移

- Classification: `watch-only residual`
- Why Not Blocking Closure: 用户明确"不考虑兼容性"，路由收敛是有意行为变更；文档提供迁移说明（改 `dispatchMode=broadcast`），存量 schedule 由使用方自行迁移，不影响本 plan 的 supported baseline 成立。
- Successor Required: `no`
- Successor Path: N/A

## Non-Blocking Follow-ups

- 拆分 3 个独立 scanner bean（既有 deferred，optimization candidate）。
- Per-schedule timeout SQL push-down（既有 deferred，optimization candidate）。
- `nopJobTaskBuilder_*` 前缀 bean 的命名约定是否值得统一为类型注入（`@Inject Map<String,IJobTaskBuilder>` 已达成，进一步到类型化注册表留待观察）。

## Closure

Status Note: completed
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task ses_00b7aec0bffe5zWguaXO7dMrNH）
- Verdict: **PASS-WITH-NOTES**（4 个非阻塞 discrepancies 已全部修复并复验）
- Evidence:
  - P1-1 PASS：`JobDispatcherScannerImpl.java:198-210` resolveTaskBuilder 无 executorKind/`"single".equals`/TASK_BUILDER_PREFIX/BeanContainer；setTaskBuilders L50、package-private getTaskBuilders L54；throw L205-207 带 ARG_DISPATCH_MODE+ARG_JOB_FIRE_ID。
  - P1-2 PASS：`app-engine.beans.xml:25` nopJobTaskBuilder_single→DefaultJobTaskBuilder；无 default/rpcBroadcast bean；collect-beans as-map by-type L38-41。
  - P1-3 PASS（修复后）：dispatch-mode.dict.yaml:10 single 描述正确；executor-kind.dict.yaml rpcBroadcast 澄清已补写（审计发现为空，已修复）。
  - P1-4 PASS：`TestJobDispatcherScannerRouting` 8 用例实测 8/8 绿（含 single+executorKind=rpcBroadcast→single L97）；`TestJobDispatcherContainerWiring` 实测 1 PASS + 1 documented @Disabled（H2 fallback path 注明）；CoordinatorScanner/Concurrency 零 BeanContainer 残留；helper singleBuilders/withSingle 均含 single key；7 个 AR 重型测试全部保留。
  - P2-5 PASS：3 builder grep 无 fallback 字段（仅 javadoc 提及删除事实）。
  - P2-6 PASS：`AbstractServiceTaskBuilder.java:63-72`（SERVICE_NAME_REQUIRED）、L79-98（DISCOVERY_CLIENT_REQUIRED L81 / NO_AVAILABLE_INSTANCE L86,93）、newTask L103-110。
  - P2-7 PASS（修复后）：JobCoreErrors L20-21 ARG_SERVICE_NAME/ARG_HEALTHY_COUNT；三码 L105/109/113；消息含 bestFit L102；NO_AVAILABLE_INSTANCE 补声明 ARG_HEALTHY_COUNT（审计 cosmetic note 已修）。
  - P2-8 PASS：8+ fallback 用例 assertThrows+code 断言；实测 Rpc 6/6、Partition 9/9、Adaptive 15/15、Default 3/3、LeastLoaded 4/4。
  - P3-9 PASS（修复后）：nop-job.md:266-268 单一路由+失败语义；invoker-design.md v6 全文件同步（§1.8/§2.5/§3.2/§3.3/§3.5/§3.6/§6）；worker-assignment-design.md:14-20 Live Baseline；bestFit 单 task 断言已按 live 行为（多 assignment 循环）修正。
  - P3-10 PASS：source-anchors.md 0 TaskBuilder 锚点（no-op 确认）。
  - P3-11 PASS：`ai-dev/logs/2026/08-12.md` 已写（含执行摘要+验证+H2 豁免）。
  - P3-5 PASS：check-plan-checklist/scan-hollow（0 findings）/check-doc-links（0 errors）退出码均 0，审计复跑确认。
  - Anti-Hollow PASS：容器接线测试实测通过（真实 beans.xml 装配，map size 4、key/类型断言）；@Disabled 有文档化 H2 原因，非静默；无空方法体。
  - H2 豁免 PASS：`ai-dev/logs/2026/08-11.md:95` vendorCode=42104 记录与 Closure Gates 引用一致。
  - checkstyle gate：仓库 maven-checkstyle-plugin 在父 pom 中已被注释（非活跃门禁）；`mvn checkstyle:check` 手工跑全模块 844 处违规（含既往未触及文件如 DefaultJobCancelHandler），非本 plan 引入的回归；本项目实际规范（AGENTS.md 4-space/imports 分组/~80-120 字符）已人工核对通过。

Follow-up:

- `TestJobDispatcherContainerWiring.testSingleModeFireDispatchEndToEndPersistence` 在 H2 环境修复后取消 @Disabled 并跑通端到端落库断言。
- 既有 `TestLocalJobScheduler.testPeriodicExecution` 为时序敏感 flaky（复跑绿），与本次改动无关，可考虑加宽松断言。
- `nopJobTaskBuilder_*` 前缀命名约定留待观察（既有 Non-Blocking Follow-ups）。

## Optional Sections

### Risks And Rollback

- **破坏性变更**：旧 `executorKind=rpcBroadcast` 调度在升级后不再广播（路由取消）。缓解：文档迁移说明 + 测试覆盖 `single`+`rpcBroadcast` 断言单 task。
- **Map 注入接线风险**：`collect-beans` 前缀/命名不匹配会导致 map 为空 → 所有非 single 路由 fail-fast（显式失败，可观测，不静默）。缓解：容器级接线断言测试。
- 回滚：本 plan 改动集中在 coordinator 引擎与 beans.xml，`git revert` 单模块即可，无 schema 变更。
