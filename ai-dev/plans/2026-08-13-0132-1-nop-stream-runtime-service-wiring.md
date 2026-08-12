# 1 运行时服务注入完整性修复（ProcessingTimeService 生产接线 + CEP open NPE + PT 窗口驱动）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：2 Blocker（PT 驱动机制未定/线程安全 + TimerServiceManager 链路断点）+ 2 Major（E2E 时序 + Rule #25 单测）全部修复；round 2：2 Major（接线锚点需无条件挂 invoke() 启动处防本地路径假绿 + cleanup 验证装配需钉死 repo-observable 口径）+ 4 Minor 全部修复，verdict 可转 active）
> Source: `ai-dev/audits/2026-08-12-1217-open-audit-nop-stream-invariant-loop.md` P0-01 / P1-02（新失败族：运行时服务注入完整性）
> Related: `2026-08-13-0132-3-...`（P1-04 CEP timer 注册表，同 CEP 面，独立修复面）；`2026-08-13-0132-2-...`（checkpoint 面，独立）
> Mission: nop-stream-invariant-loop

## Purpose

把 `ProcessingTimeService` 从"仅测试注入"变成"生产运行时真实接线"：消除 `CepOperator.open()` 在无容器生产环境下必然 NPE（P0-01），并让处理时间窗口（`Tumbling/SlidingProcessingTimeWindows` + `ProcessingTimeTrigger`）与 PT cleanup timer 在生产环境真正触发（P1-02，与 P0-01 同族，一次接线两处受益）。同时在未接线环境下显式告警/失败而非静默 NPE / 静默零输出。

## Current Baseline

> 已核对 live repo（2026-08-13）。

- **`processingTimeService` 生产零接线（P0-01 核心事实，实测）**：`AbstractStreamOperator.java:34` 声明 `protected transient ProcessingTimeService processingTimeService;`，getter `:135-137` 直接返回该字段；**main 代码不存在 `setProcessingTimeService` setter**（grep 零命中）——测试经直接字段赋值（`TestPeriodicWatermarkAdvancement.java:71-72,136,186` 的 `op.processingTimeService = pts`）或 `CepTestUtils.injectProcessingTimeService` 反射注入。
- **NPE 路径（实测）**：`CepOperator.open()`（`CepOperator.java:347`）→ `registerCacheStatisticsTimer()`（`:356-368`，`NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL` 默认 30min > 0）→ `getProcessingTimeService().getCurrentProcessingTime()`（`:366`）→ null NPE。连带：`inProcessingTime()` 模式 `processElement`（`:466-471`）同 NPE；`onCacheStatisticsTimer` 定期统计死路。CEP 测试（320 个）全绿的机制 = 13 个测试类经 `CepTestUtils.injectProcessingTimeService`（`TestCepOperatorOnEventTimeStatePreservation` / `TestCepOperatorDanglingCleanup` / `TestCepOperatorCacheStatistics` / `TestCepOperatorWatermarkPersistence` / `TestCepNonKeyedEntryE2E` / `TestAfterMatchSkipStrategies` 等 6 类直调）+ 私有静态 `setProcessingTimeService` 包装器（`TestCepOperatorBasic` / `TestCepCheckpointRestoreE2E` / `TestCepOperatorStateRecovery` / `TestCepOperatorStateBackendWiring` / `TestCepOperatorTimeout` / `TestCepSkipStrategyE2E` / `TestCepPublicApiE2E` 等 7 类）注入 mock——生产路径零 E2E 覆盖。
- **PT timer 生产无驱动（P1-02 核心事实，实测）**：`HeapInternalTimerService.fireProcessingTimeTimers`（`HeapInternalTimerService.java:121-137`）生产+测试全仓调用点仅测试；`TimerServiceManager.fireProcessingTimeTimers`（`:42-59`）生产零调用者；`AbstractStreamOperator.setTimeServiceManager`（`:168`）生产零调用者。`WindowOperator.open()` `:455` 创建 `internalTimerService` 但**不调用 `registerTimerService`**（全仓仅 `ProcessOperator.java:46` 调用且带 null 守卫）——TimerServiceManager 生产链路存在断点。`WindowOperator.java:1073-1084` 对处理时间窗口注册 PT cleanup timer → 永不触发 → `windowContents` + `MergingWindowSet` 无界增长；`ProcessingTimeTrigger.java:34-38` 唯一发射路径 = PT timer 回调 → 永不发射。`WindowOperator.onProcessingTime`（`:800-824`）与 merging guard（`:630-638` 用墙钟）同样死路。
- **代码库既有的接线设计意图（实测）**：`ai-dev/design/nop-stream/mailbox-design.md` §7「奠基（未接线）」——processing-time timer 生产接线 = **timer 触发作为 mail 投递到 task 线程**（`MailboxExecutor.runLoop()` 与 `TaskMailbox` 已具备承载能力，main 代码 `runLoop()` 零调用）；`time-model-design.md` 记录了 restore-before-open 延迟应用约束（:286 引 `TestCheckpointRecovery.java:478`）——**该文档未记录 TimerServiceManager 接线方式**（接线契约留待本 plan 同步）。
- **接线点陷阱（实测，F2 裁定依据）**：`StreamTaskInvokable.setupSnapshotCallbacks` 仅在 `setBarrierTracker` 且 tracker != null 时执行（:255-259），而 `setBarrierTracker` 全仓调用点仅 `GraphModelCheckpointExecutor.java:662`（分布式 checkpoint 路径）——**本地 `env.execute()` 路径从不创建 `CheckpointBarrierTracker`、从不调用 setBarrierTracker**。因此注入/驱动启动**必须无条件挂在 `invoke()` 启动处（`operatorChain.open()` 之前）**，不得挂在 setBarrierTracker/setupSnapshotCallbacks 条件路径上，否则本地生产路径（CEP E2E 与 PT 窗口 E2E 恰好都走本地）零注入且 WARN 守卫掩盖假绿。
- **已有防御先例（实测）**：`TimestampsAndWatermarksOperator.java:82-84` 对 `processingTimeService != null` 做守卫（null 时**静默跳过** periodic watermark 注册）——该先例本身属 Rule #24 灰色形态；本 plan 的兜底守卫必须显式 WARN，不得静默跳过。
- **测试基线（实测）**：JUnit 门禁 10 类 / 102 tests / 0 failures；mjs `all` exit 0（pin 0）；全量 2833 tests / 0 failures（2026-08-12 Cycle 2 / I5 基线，working tree clean）。
- **真正剩余的 gap**：生产运行时（本地 `StreamExecutionEnvironment.execute()` 与分布式路径）没有真实 `ProcessingTimeService` 注入、没有 TimerServiceManager 生产创建/注册链路、没有 PT timer 调度驱动；CepOperator 无 null 守卫兜底；PT 窗口在无驱动环境下静默零输出（无 fail-fast）。

## Goals

- 生产执行路径为算子注入真实 `ProcessingTimeService`，接线点 + 调度驱动完整：`CepOperator.open()` 生产路径不再 NPE（P0-01 关闭）。
- 处理时间窗口在生产环境真正发射 + PT cleanup timer 真正触发，窗口状态不再无界增长（P1-02 关闭）。
- 接线遵循 `mailbox-design.md` §7 设计意图：**PT timer 回调在 task 线程执行（经 mailbox 投递）**，驱动线程只负责投递触发请求，不与 `processElement` 并发写共享状态。
- CepOperator 在服务缺失时 null 守卫 + 明确 WARN（不静默 NPE、不静默跳过）；PT 窗口/CEP 处理时间模式在无驱动环境下显式失败或明确告警。
- 补生产路径 E2E（不经 mock 注入）+ 新 PTS 实现自身的 focused 单测（Rule #25）。
- 全量回归绿 + 既有门禁零命中（既有 JUnit 门禁 10 类 / 102 tests + mjs `all` exit 0 不得回退）。

## Non-Goals

- **不做 CEP timer 注册表（`registeredEventTimeTimers`）触发机制改造**——P1-04 属独立修复面（open() 初始化对称 + 删除/触发语义），派 `2026-08-13-0132-3`。
- **不新增/不改既有不变式门禁语义**；新族（服务接线完整性）的"生产 wiring 存在性"门禁沉淀属 roadmap Loop Rule 派生候选，登记 Follow-up Backlog（Cycle 3 / I1 候选），本 plan 不落地门禁扩展。
- **不改 `ProcessingTimeService` / `HeapInternalTimerService` / `TimerServiceManager` 公共接口签名**（除非实现修复必需的最小扩展，且不得破坏既有测试基线）。
- **不涉及 checkpoint / 恢复路径**（派 `2026-08-13-0132-2`）。
- **不做 `TimestampsAndWatermarksOperator` 的 periodic watermark 接线改造**（其 null 守卫路径若维持现状需记录为已知形态，不扩大修复面）。

## Scope

### In Scope

- 生产运行时 `ProcessingTimeService` 注入 + PT 调度驱动（mailbox 投递语义）：接线点 = 任务 invokable 生命周期（`StreamTaskInvokable` 启动路径，与 `setupSnapshotCallbacks` 同级或等价生产接线位置）；驱动 = 调度线程将 PT 触发请求投递到 task 线程 mailbox，task 线程执行 `fireProcessingTimeTimers`。
- TimerServiceManager 生产链路补齐：生产创建 manager + `setTimeServiceManager` 接线 + **`WindowOperator.open()` 增加 `registerTimerService`**（镜像 `ProcessOperator.java:45-47` 的 null 守卫模式）——四环节全部生产可达。
- CepOperator `registerCacheStatisticsTimer` null 守卫 + WARN 兜底（不静默跳过）；`inProcessingTime()` 模式 `processElement` 在服务缺失时显式失败。
- 处理时间窗口发射 + cleanup 的端到端验证（`TumblingProcessingTimeWindows` + `ProcessingTimeTrigger` 生产路径）。
- CEP 生产路径 E2E（open 不 NPE + 事件处理 + 处理时间模式 processElement 路径）。
- 新 PTS 实现/驱动组件的 focused 单测（Rule #25：调度/触发/取消/回调异常策略/shutdown）。
- 测试：生产接线点存在性验证（非注入 mock 的 E2E）+ 既有测试保持绿。
- 文档收口：`docs-for-ai/04-reference/source-anchors.md`（STRM-037 CEP 特性锚点如与接线状态不符则同步）+ `mailbox-design.md` §7「未接线」状态更新为"已接线"（如适用）+ `ai-dev/logs/`。

### Out Of Scope

- CEP `registeredEventTimeTimers` 注册表触发/恢复对称性（P1-04 → plan 3）。
- checkpoint / 恢复 / 区域重启（P0-02 / P0-03 / P1-01 / P1-05 → plan 2）。
- "生产 wiring 存在性"不变式门禁沉淀（roadmap Loop Rule 派生候选 → Follow-up Backlog）。
- 既有门禁内容改写（只增不弱化）。
- `TimestampsAndWatermarksOperator` periodic watermark 行为改造（维持现状，只记录）。

## Execution Plan

### Phase 1 - PT 驱动架构裁定 + 生产运行时接线（含 TimerServiceManager 全链路）

Status: completed
Targets: `nop-stream/nop-stream-core/.../execution/StreamTaskInvokable.java`（生产接线点）；`nop-stream/nop-stream-core/.../operators/AbstractStreamOperator.java`（setter/字段；getter 语义不变）；`nop-stream/nop-stream-core/.../operators/TimerServiceManager.java` / `HeapInternalTimerService.java`（驱动侧）；`nop-stream/nop-stream-runtime/.../windowing/WindowOperator.java`（:455 后补 registerTimerService）

- Item Types: `Decision | Fix | Proof`
- [x] **架构裁定（Decision）**：裁定结果见下方「Phase 1 执行记录」§A——回调线程 = task 线程（mailbox 投递）；驱动线程 = daemon + invoke() finally shutdown；触发粒度 = 周期 tick（100ms）+ volatile due 检查（非空跑）；回调异常策略 = PTS 直接回调 fail-fast（任务可见失败）+ TimerServiceManager 保留既有 per-service catch+log（TestTimerServiceManagerRobustness 覆盖）。
- [x] **接线（Fix）**：`StreamTaskInvokable` 生产接线落地——PTS/TSM 对象在**构造函数**（`setupProcessingTimeServices()`）无条件注入算子链（先于任何 `operatorChain.open()`——实测 `SubtaskTask.run()`/`Task.run()` 在 `invoke()` 之前就 open 链，接线挂 invoke() 内会在 CepOperator.open() 之后才生效，F2 前提修正见执行记录 §B）；驱动线程在 `invoke()` 启动处无条件 start、finally 中 shutdown（`startProcessingTimeDriver`/`stopProcessingTimeDriver`）。不依赖 `setBarrierTracker`/`setupSnapshotCallbacks` 条件路径。grep 证据：main 代码生产注入调用点 = `StreamTaskInvokable.java:431 setupProcessingTimeServices`（构造函数 4 处调用）+ `invoke():378` 驱动启动。
- [x] **接线验证证据口径（Proof，防假绿）**：`TestStreamTaskInvokableProcessingTimeWiring.testNonCheckpointInvokableInjectsProcessingTimeServices`——**非 checkpoint 作业**（无 setBarrierTracker、无 tracker）invoke 后：PTS/TSM 在 open() 内可见（`ptsAtOpen`）、与 invokable 同一实例、TSM 注册表大小 == 1（`numTimerServices()` 运行时注册证据）、PT timer 经驱动全链路触发。
- [x] **TimerServiceManager 全链路（Fix）**：四环节全部生产可达——(a) 生产创建 manager：`StreamTaskInvokable` 构造函数；(b) `setTimeServiceManager` 生产接线：同处对全部算子；(c) `WindowOperator.open()` 增加 `registerTimerService`（镜像 `ProcessOperator.java:45-47` null 守卫）；(d) 驱动 → `fireProcessingTimeTimers`：`ProcessingTimeServiceDriver.fireDueTimersOnTaskThread`（mail 动作，task 线程执行）。grep 生产命中 + 运行时注册证据在案。
- [x] **兜底守卫（Fix）**：`CepOperator.registerCacheStatisticsTimer` 在 `getProcessingTimeService() == null` 时守卫 + **WARN**（`TestCepProductionExecutionE2E.testCepOpenWithoutServiceDoesNotNpe` 钉死不 NPE）；`inProcessingTime()` 模式 `processElement` 服务缺失时抛 `StreamException`（`ERR_STREAM_STATE_ERROR` + ARG_DETAIL 说明，`testCepProcessingTimeProcessElementFailsExplicitlyWithoutService` 钉死）。
- [x] **新组件单测（Proof，Rule #25）**：`TestTaskProcessingTimeService`（8 用例：注册/触发/回调时间戳/取消/取消后 fire/Re-arm/异常 fail-fast/墙钟）+ `TestProcessingTimeServiceDriver`（9 用例：mail 投递回调线程==task 线程、drain 触发、无 timer 无 mail、未到期无 mail、daemon、shutdown 后不触发、start 幂等、manager timer 触发、null 拒绝）。
- [x] **无静默跳过（Proof，Rule #24）**：新注入路径无空实现——驱动因到期检查空跑不投 mail（非 no-op 实现）；`TimerRegistrationFuture.get()` 未完成时抛 `IllegalStateException`（非占位返回）；环境无服务时显式告警/失败（CepOperator WARN + fail-fast、WindowOperator PT 窗口 WARN）。
- [x] **接线验证（Proof，Rule #23）**：调用链 execute → GraphExecutionPlan.build → StreamTaskInvokable 构造函数 → 算子 setProcessingTimeService/setTimeServiceManager → open() 消费——测试证据：`ptsAtOpen`/`tsmAtOpen` 非空 + `numTimerServices()==1` + env 生产 E2E（CEP 两模式）open 不 NPE。
- [x] **端到端验证（Proof，Rule #22）**：`TestCepProductionExecutionE2E`——事件时间 + 处理时间两种模式的 CEP 作业走完整生产执行路径（`env.execute()`，无任何 mock 注入），open() 不 NPE、事件处理、sink 输出完整走通。全仓已有 env 测试先例（`TestWindowOperatorUnificationE2E` 等），E2E 层级裁定 = env 全栈。
- [x] 回归：既有 cep 测试（含经 `CepTestUtils.injectProcessingTimeService` 注入的 7 个测试类）全绿——接线后 mock 注入路径不破坏（`./mvnw test -pl nop-stream -am -T 1C` 全绿：core 1448 / runtime 807 / cep 324 / 其余模块 0 失败）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 架构裁定记录在案（回调线程 = task 线程 mailbox 投递；驱动线程生命周期 + shutdown 语义）——见 Phase 1 执行记录 §A
- [x] 生产代码存在真实接线调用点，且调用链在运行时连通（**含非 checkpoint 作业本地路径注入生效断言**——接线验证在案）——`TestStreamTaskInvokableProcessingTimeWiring`
- [x] TimerServiceManager 生产链路四环节全部落地（创建 / setTimeServiceManager / WindowOperator registerTimerService / 驱动 fireProcessingTimeTimers）——grep 生产命中 + 运行时注册证据（`numTimerServices()` 断言）
- [x] 接线点位于 invoke() 启动处（operatorChain.open() 之前），不依赖 setBarrierTracker/setupSnapshotCallbacks 条件路径（code review 证据）——注入 = 构造函数（先于任何 open，含 SubtaskTask 预 open）；驱动启动 = invoke() 起始。F2 前提修正（SubtaskTask 先 open 后 invoke）见执行记录 §B
- [x] `CepOperator.open()` 生产路径（无 mock 注入）E2E 全绿：open 不 NPE、事件处理、sink 输出完整走通——`TestCepProductionExecutionE2E`（事件时间 + 处理时间两模式）
- [x] `registerCacheStatisticsTimer` null 守卫 + WARN 在案（服务缺失时不 NPE、不静默跳过）——`testCepOpenWithoutServiceDoesNotNpe`
- [x] `inProcessingTime()` 模式服务缺失时显式失败（不静默跳过）——`testCepProcessingTimeProcessElementFailsExplicitlyWithoutService`
- [x] 新 PTS 实现/驱动组件 focused 单测绿（Rule #25）——`TestTaskProcessingTimeService`（8）+ `TestProcessingTimeServiceDriver`（9）
- [x] **端到端验证**：CEP 作业从入口到 sink 完整路径已验证（不经测试注入）——env 全栈 E2E 两模式
- [x] **接线验证**：ProcessingTimeService 注入点被生产执行路径实际调用 + `registerTimerService` 运行时注册证据——`ptsAtOpen`/`numTimerServices` 断言
- [x] **无静默跳过**：新代码无空方法体 / 静默零输出作为正常实现；无法驱动的环境显式告警——PTS/驱动/守卫均有显式行为（WARN / fail-fast / IllegalStateException）
- [x] 既有 cep 测试（7 个注入类 + 其余）与 core 测试全绿，无回退——全量 `-pl nop-stream -am` 0 失败
- [x] `source-anchors.md` STRM-037 或 `mailbox-design.md` §7 已同步（接线状态变更需记录）——两处均已更新（见「文档同步」节）
- [x] `ai-dev/logs/` 对应日期条目已更新——`ai-dev/logs/2026/08-13.md` 追加本 plan 收口条目

### Phase 2 - 处理时间窗口生产驱动 + 端到端验证

Status: completed
Targets: `nop-stream/nop-stream-runtime/.../windowing/WindowOperator.java`（PT cleanup 路径 :1073-1084）；`nop-stream` E2E 测试；`ai-dev/design/nop-stream/time-model-design.md`（如接线契约需更新）

- Item Types: `Fix | Proof`
- [x] **驱动验证（Proof）**：`TestProcessingTimeWindowProductionE2E.testProcessingTimeWindowsFireThroughProductionDriver`——`TumblingProcessingTimeWindows` + 默认 trigger 作业走生产路径（env.execute + 真实驱动），窗口实际触发并有 sink 输出断言（非仅 grep 命中驱动代码）。验收证据 = WindowOperator 的 PT timer 实际触发（输出非空 + 全元素恰好计入一次 + 多窗口触发）。
- [x] **cleanup（Proof）**：`TestProcessingTimeWindowProductionE2E.testProcessingTimeWindowCleanupClearsState`——invokable 级生产装配（直接构造 `StreamTaskInvokable` 跑 invoke，不经 mock，等价生产执行路径）：作业完成后 `internalTimerService.numProcessingTimeTimers()==0`（protected 可及）+ `window-contents` map 对全部已创建窗口 namespace 空。时序设计：慢源 41ms 节拍 / 300ms 窗口（互质避免边界贴合）+ 末尾静默期（`StreamSourceOperator.run()` 的收尾 drain 触发最后一个窗口 fire+cleanup）。
- [x] **端到端验证（Proof，Rule #22）**：PT 窗口作业从入口 → 窗口发射 → sink 输出完整走通（`env.addSource` 慢源 + keyBy + `WindowedStreamImpl` + aggregate + sink）。时序设计显式处理：慢速源（41ms 有节奏发射）+ 窗口尺寸/驱动节奏匹配，确保 PT 到点发生在作业存活期；对比 `TestProcessingTimeWindowIntegration`（自建推进器，不经 WindowOperator）——本项走生产路径（真实驱动，非 mock 推进器）。
- [x] **无静默跳过（Proof，Rule #24）**：PT 窗口在无驱动环境下显式告警——`WindowOperator.open()` 对非事件时间 assigner 且 `getProcessingTimeService()==null` 时 WARN（"will never fire"），`testProcessingTimeWindowWithoutDriverWarnsExplicitly`（Logback ListAppender 断言 WARN 出现）钉死；生产接线保证驱动恒存在。
- [x] 回归：既有窗口测试全绿（含 `TestProcessingTimeWindowIntegration` / `TestWindowOperatorCorrectness` / `TestWindowOperatorUnificationE2E` 等）——全量 `-pl nop-stream -am -T 1C` 0 失败。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] WindowOperator 的 PT timer 实际触发有测试证据（驱动链全环节连通，非仅 grep 命中）——env E2E 输出断言 + invokable 级 `numProcessingTimeTimers()==0`
- [x] **端到端验证**：PT 窗口作业从入口到 sink 完整发射路径已验证（生产驱动，非 mock 推进器；时序设计（慢源/节奏匹配）在案）——`testProcessingTimeWindowsFireThroughProductionDriver`
- [x] PT cleanup timer 触发，窗口状态清理有测试证据（invokable 级装配：`internalTimerService.numProcessingTimeTimers()==0` + `window-contents` map 空，无界增长解除）——`testProcessingTimeWindowCleanupClearsState`
- [x] 无驱动环境下 PT 窗口显式失败/告警而非静默零输出（无静默跳过）——`testProcessingTimeWindowWithoutDriverWarnsExplicitly`
- [x] 既有窗口相关测试全绿——全量回归 0 失败
- [x] `time-model-design.md` 接线契约已同步——已更新（「文档同步」节 §C2；驱动链路变更：mailbox 投递 + TimerServiceManager 接线四环节 + nextProcessingTimeTimer volatile 字段）
- [x] `ai-dev/logs/` 对应日期条目已更新——`ai-dev/logs/2026/08-13.md` 追加本 plan 收口条目

## Phase 1 执行记录（2026-08-13）

### §A 架构裁定（Decision 项落档）

- **回调执行线程**：task 线程（mailbox 投递）。驱动线程只投递触发请求（control mail），task 线程在安全点（source `collect()` / `processInputGate` 循环顶）drain 并执行 `fireProcessingTimeTimers`——遵循 `mailbox-design.md` §7 设计意图，避免与 `processElement` 并发写 `triggerContext`/`keyedStateBackend.currentKey`/`output` 的数据竞争。测试证据：`TestProcessingTimeServiceDriver.testCallbackRunsOnTaskThreadViaMail`（回调线程 == mail 消费线程）+ `TestStreamTaskInvokableProcessingTimeWiring.testTimerCallbackRunsOnTaskThread`（回调线程 == invoke 线程）。
- **驱动线程生命周期**：daemon + 任务 `invoke()` finally 中 shutdown（`stopProcessingTimeDriver`）。对齐既有 heartbeat/checkpoint 调度线程纪律：TaskManager heartbeat 只读 `getLastProgressTime`（volatile）、checkpoint barrier 走 InputGate、驱动只投递 mail——三者无共享状态冲突。`start()` 幂等（`TestProcessingTimeServiceDriver.testStartIsIdempotent`）；shutdown 后不触发（`testShutdownStopsFiring`）。
- **驱动触发粒度**：周期 tick（默认 100ms）+ 到期检查，**非空跑**——驱动不扫描 timer 表（跨线程不安全），只读两个 volatile due 标志（`TaskProcessingTimeService.nextTimerTimestamp` / `HeapInternalTimerService.nextProcessingTimeTimer`）；有到期 timer 才投 mail（`testNoMailWhenNothingDue` / `testNoMailWhenTimerNotYetDue`）。`fireProcessingTimeTimers(now)` 按 timestamp<=now 全量 fire 语义正确（`HeapInternalTimerService` 既有实现）。
- **回调异常策略**：① PTS 直接回调（CepOperator cache-stats / CEP PT 事件 / periodic watermark）——**fail-fast**：异常经 `ProcessingTimeCallbackException` 包装向上传播，mail 动作抛出让任务可见失败（理由：静默吞异常 = 数据静默丢失；CEP 内部 wrapper 本就以 `StreamException` fail-fast；`TestTaskProcessingTimeService.testCallbackExceptionFailsFast` 钉死）。② TimerServiceManager 路径——**保留既有** per-service catch + `LOG.error` 继续（`TimerServiceManager.java:42-50` 既有形态，`TestTimerServiceManagerRobustness` 覆盖），理由：该契约已测试钉死且 per-service 隔离语义合理，不扩大变更面。
- **驱动触发粒度裁定补充**：mail 动作内先 fire PTS 再 fire TimerServiceManager；TSM 调用包 catch（其自身已 per-service catch，此 catch 为意外尾部）。

### §B F2 接线前提修正（live repo 核对）

- **事实修正**：`SubtaskTask.run()`（:104 `openOperatorChains()`）与 `Task.run()`（:191）都在 `invoke()` 之前打开 operator chain——接线若只挂在 `invoke()` 启动处，`CepOperator.open()` 已先以 null 服务运行（首次 open 仍 NPE 或走 WARN 兜底，接线无效）。
- **落地方案**：服务对象注入 = **构造函数**（`setupProcessingTimeServices()`，无条件，先于任何 open——含 SubtaskTask/Task 预 open、env.execute 本地路径、checkpoint 路径、SupervisionLoop 重建路径全部覆盖）；驱动线程启动 = `invoke()` 起始（无条件，先于 invoke 内 open），shutdown = invoke finally。构造即注入不会泄漏线程（驱动未启动）；驱动只在线程启动后投 mail。
- **对 Exit Criteria 的影响**：「接线点位于 invoke() 启动处」按意图满足 = 无条件 + 先于 operatorChain.open() + 不依赖 setBarrierTracker/setupSnapshotCallbacks 条件路径；注入的具体代码位置为构造函数（比计划字面位置更早），理由如上记录。驱动启动/停止严格位于 invoke() 启动处/finally。
- **grep 证据**：`setProcessingTimeService` main 生产调用点 = `StreamTaskInvokable.setupProcessingTimeServices`（构造函数 4 路径）+ 测试注入类保持全绿（`CepTestUtils.injectProcessingTimeService` 反射路径不受影响——字段仍 protected transient）。

### §C cleanup 断言口径说明

- 计划原文断言 `numProcessingTimeTimers()==0`——经实现验证**可达成**：慢源末尾静默期 + `StreamSourceOperator.run()` 收尾 `drainControlMails()`（:241）在最后一个窗口 end 之后仍有一次 task 线程 drain，驱动到期 mail 被该 drain 消费，最后一个窗口 fire+cleanup 完成 → 0 timer。实现按计划口径落地，无需放宽。

## 文档同步（2026-08-13）

- **`ai-dev/design/nop-stream/mailbox-design.md` §7**：「未接线」条目更新为「已接线」——PT timer 触发经 mail 投递到 task 线程（`ProcessingTimeServiceDriver` + `TaskProcessingTimeService` + `StreamTaskInvokable` 接线），回调在 task 线程执行；`MailboxExecutor.runLoop()` 未用于生产（任务主循环内联 drain，与设计原文一致）。
- **`ai-dev/design/nop-stream/time-model-design.md`**：新增 TimerServiceManager 生产接线契约（创建/注入在 `StreamTaskInvokable` 构造函数、`WindowOperator.open()` registerTimerService、驱动经 mailbox 投递 fire、`nextProcessingTimeTimer` volatile 跨线程只读契约）；restore-before-open 约束不变。
- **`docs-for-ai/04-reference/source-anchors.md` STRM-037**：同步接线状态——`registerCacheStatisticsTimer` 现经生产注入的 `ProcessingTimeService` 注册（null 守卫 + WARN 兜底），驱动经 mailbox 投递在 task 线程触发。
- **`ai-dev/logs/2026/08-13.md`**：追加本 plan 收口条目（见 logs）。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P0-01（CepOperator open NPE）已修复：生产路径 E2E 全绿 + null 守卫+WARN 兜底在案
- [x] P1-02（PT 窗口永不触发 + 状态无界）已修复：生产驱动 E2E 全绿 + cleanup 验证在案
- [x] 无被静默降级到 deferred / follow-up 的 in-scope live defect（两个 P0/P1 均以 Fix 落地）
- [x] 接线完整性：注入点 + TimerServiceManager 四环节运行时调用连通（非仅类型存在）
- [x] 无静默跳过：新代码路径无空实现 / 静默零输出；兜底守卫显式 WARN / 显式失败
- [x] 必要 focused verification 完成（先红后绿证据在案：修复前 NPE/零输出可复现，修复后绿）——红：open-audit P0-01 机制证据（`getProcessingTimeService()` null → NPE 逐行可溯）+ 本批次复现（PT 模式 env 作业接线缺失时显式失败）；绿：`TestCepProductionExecutionE2E` / `TestProcessingTimeWindowProductionE2E` 全绿
- [x] 受影响 owner docs 已同步（`source-anchors.md` / `mailbox-design.md` / `time-model-design.md`）——见「文档同步」节
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（含 Anti-Hollow 检查）——fresh session `ses_008b10897ffeLByJ4LApM8FwkS`，Phase 1/2 Exit Criteria 与 Closure Gates 逐条 PASS（1 项门禁表述修正：hollow 扫描按既有基线 findings 集判据，plan 文本已修正），evidence 见 Closure 段
- [x] `./mvnw compile` (`-pl nop-stream -am`)
- [x] `./mvnw test -pl nop-stream -am -T 1C`——全绿（core 1448 / runtime 807 / cep 324 / rocksdb 83 / connector 系列 / flow / fraud-example，0 失败）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0（closure 时）——已实测 exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high`——**findings 集与既有基线一致（判据，沿 Cycle 2 / I1/I4 惯例）**：工具对既有基线 14 项 high findings（11× 有意 `UnsupportedOperationException` fail-fast + 3× 注释性 no-op：GroupPattern/RuntimeContext/StreamingRuntimeContext/FunctionUtils/Trigger/DemoKeyedStateStore/FileTwoPhaseCommitSink/RocksDBIncrementalRestore）恒 exit 1（工具无豁免机制），**本 plan 新增/修改代码零新增 finding**；判据 = findings 集与基线一致而非退出码（与 Cycle 2 / I1 closure 钉定的 `34aed42c1` 判据相同）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（既有门禁零命中）——已实测 exit 0（output-contract 注册表 4 处行号已同步）
- [x] checkstyle / 代码规范检查通过——待 closure audit 后最终确认（`./mvnw checkstyle:check` 在 closure 阶段运行）

## Deferred But Adjudicated

### "生产 wiring 存在性"不变式门禁沉淀（Cycle 3 / I1 派生候选）

- Classification: `out-of-scope improvement`（roadmap Loop Rule 派生候选，非本 plan 门禁扩展）
- Why Not Blocking Closure: 新族门禁沉淀属 roadmap Loop Rule 流程（Cycle 3 / I1），本 plan 的 Fix 已用端到端测试 + 接线验证锁定行为，不依赖新门禁落地；本批次计划收尾时在 `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Follow-up Backlog 实际登记该条目（Cycle 3 / I1 派生候选），保持触发可追溯。
- Successor Required: `yes`（roadmap Loop Rule 派生时）
- Successor Path: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Follow-up Backlog 新增条目（"生产 wiring 存在性"门禁沉淀候选，本批次登记）

### `TimestampsAndWatermarksOperator` 的静默跳过守卫形态（:82-84）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该算子的 periodic watermark 接线面不在本 plan 修复范围（Non-Goals 已声明）；接线后其 `processingTimeService` 不再为 null，现有守卫自然失效但语义不破坏。不扩大修复面以控制 diff。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 新族（运行时服务注入完整性）其余服务（如 checkpoint 服务、watermark 服务）是否存在同族"仅测试注入"实例——复探时评估（已登记 backlog 候选）。

## Closure

Status Note: P0-01（CepOperator open 生产路径 NPE）+ P1-02（PT 窗口永不触发 + 状态无界）以 Fix 落地：ProcessingTimeService 生产接线（StreamTaskInvokable 构造函数注入 + ProcessingTimeServiceDriver mailbox 投递驱动）+ TimerServiceManager 四环节生产连通 + CepOperator/WindowOperator 兜底守卫 + 生产路径 E2E（CEP 双模式 + PT 窗口 + cleanup 验证）+ 新组件 focused 单测（Rule #25）。两个 Phase 的 Exit Criteria 与 Closure Gates 全部勾选；全量回归绿；既有门禁零命中（mjs all exit 0）；独立子 agent closure-audit 通过（除 1 项门禁表述修正——hollow 扫描按既有基线 findings 集判据，已修正本 plan 文本）。文档同步完成（mailbox-design §7 / time-model-design §10.3 / source-anchors STRM-037 / logs）。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，`ses_008b10897ffeLByJ4LApM8FwkS`）
- Evidence:
  - **Phase 1 Exit Criteria 逐条 PASS**（独立复核 live code）：① 接线 = `StreamTaskInvokable.java:139,151,166,180` 四构造函数调 `setupProcessingTimeServices()`（:437-438 注入），驱动 start 于 `invoke()` 起始（:470）、finally shutdown（:487）；② TSM 四环节 = 生产创建（:433）/ setTimeServiceManager（:438 → `AbstractStreamOperator.java:172`）/ `WindowOperator.java:461-463` registerTimerService（null 守卫，镜像 ProcessOperator:45-47）/ 驱动 fire（`ProcessingTimeServiceDriver.java:140` → `TimerServiceManager.java:55-63`）；③ 接线不在 setBarrierTracker 条件路径（setBarrierTracker :284-292 只调 setupSnapshotCallbacks + wireMailboxToHeadSource；setupProcessingTimeServices 仅构造函数调用）；④ CepOperator 守卫 = `CepOperator.java:366-373`（null → WARN）+ `:471-480`（PT 模式显式 StreamException）；⑤ 新组件无空壳（fireDueTimers 真实 fire 循环 / driver run 真实逻辑 / TimerRegistrationFuture.get() 未完成抛 IllegalStateException）；⑥ 5 个测试类存在且实质（8+9+3+4+3）；⑦ WindowOperator PT 无服务 WARN（`WindowOperator.java:470-475`）。
  - **Phase 2 Exit Criteria 逐条 PASS**：PT timer 实际触发（env E2E 输出断言 + cleanup 测试 `numProcessingTimeTimers()==0`）；E2E 入口→sink（慢源 + keyBy + WindowedStreamImpl + aggregate + sink）；cleanup 状态清空（invokable 级 `:212` timer==0 + `:223-227` window-contents map 空）；无驱动 WARN 断言（ListAppender `:238-249`）；既有窗口测试全绿（全量 core 1448 / runtime 808 / cep 324，0 失败——audit 独立复跑确认）；time-model-design §10.3 已同步；logs 已更新。
  - **Closure Gates**：P0-01/P1-02 修复 PASS（守卫+显式失败+生产 E2E）；无静默降级 PASS（两个 P0/P1 均 Fix；Deferred 仅 out-of-scope improvement + watch-only residual）；接线完整性 PASS（四环节运行时连通 + numTimerServices 断言）；无静默跳过 PASS（新代码无空实现；WARN/fail-fast/IllegalStateException）；先红后绿 PASS（红 = open-audit P0-01 机制证据；绿 = 5 测试类 27/27 独立复跑）；owner docs 同步 PASS（mailbox-design §7 已接线 / time-model-design §10.3 / source-anchors STRM-037 / logs）；`./mvnw compile`+`test -pl nop-stream -am -T 1C` PASS（audit 独立复跑 BUILD SUCCESS 0 失败）；`check-plan-checklist.mjs --strict` exit 0（audit 独立复跑）；`check-nop-stream-invariants.mjs all` exit 0（audit 独立复跑）；`checkstyle:check -Pqa -pl nop-stream -am` BUILD SUCCESS（audit 独立复跑）；`scan-hollow-implementations.mjs --severity high`——audit 实测 exit 1（既有基线 14 项 high findings 恒有，工具无豁免机制），findings 集与本 plan 无关（14 项全部位于未改动既有文件），按 Cycle 2 / I1 钉定判据（`34aed42c1`）= findings 集一致 → PASS（plan 文本已修正为 findings 集判据）。
  - `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0（closure 后复跑确认）
  - Anti-Hollow 检查：端到端调用链追踪 = execute → GraphExecutionPlan.build → StreamTaskInvokable（构造函数注入）→ operator open（CepOperator/WindowOperator 消费 PTS/TSM）→ driver mail → task 线程 fire；`scan-hollow-implementations.mjs` findings 集与基线一致（无新增）；新组件无空方法体/静默跳过
  - Deferred 项分类检查：无 in-scope live defect 被降级（两 P0/P1 均 Fix 落地；Deferred = out-of-scope improvement（门禁沉淀候选，roadmap 已登记）+ watch-only residual（TimestampsAndWatermarksOperator 守卫））
- Audit Session: `ses_008b10897ffeLByJ4LApM8FwkS`

Follow-up:

- no remaining plan-owned work
- 非阻塞：同族其余服务（checkpoint/watermark 服务）"仅测试注入"实例复探（roadmap 已登记）；"生产 wiring 存在性"门禁沉淀 = Cycle 3 / I1 派生候选（roadmap Follow-up Backlog 已登记，Status todo）
