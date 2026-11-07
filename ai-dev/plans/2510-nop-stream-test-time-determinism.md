# 2510 nop-stream 测试时间确定化（runtime / core / connector-* / fraud-example / flow）

> Plan Status: completed
> Last Reviewed: 2026-11-07
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-4；口径沿用 plan 2507 Phase 1 入册的「禁止依赖敏感时间等待」规则
> Related: `ai-dev/plans/2507-debug-subsystem-test-time-determinism.md`（口径先例）；`ai-dev/plans/2508-shared-deterministic-time-test-tools.md`（共享工具 owner）；`ai-dev/plans/2509-test-time-waits-guard.md`（守卫与基线）
> 编号说明：沿用仓库当前 4 位序列（最近为 2509）。
> Review: R1 对抗审查（独立子 agent，87 次工具调用实测核对）6 Major + 6 Minor，本稿逐条修订（F1 ManualClock 先例证伪更正为首次接线；F2 gated 面验证口径与 mutation 探针默认运行约束；F3 残余预算按实测分类重算并具名预裁定；F4 Docker/Kafka 门控 13+3 处具名承接；F5 main 盘点扩至 MemoryKeyedStateBackend/SourceRateLimiter/调度器注入面；F6 TestAwait 5 副本事实更正 + 归并入 scope）。R2 复审全部确认解决，新增 5 Minor（降幅算术、消号纪律、探针限定、调度面拆分、canonical 方法删除显式项）已修订，判定 READY 可执行（同一独立子 agent 两轮）。
> 验证环境：所有 mvn 命令带 `-Dmaven.repo.local=$PWD/.m2-repo`，下文以 `<repo>` 代指。
> 跨平台约束（用户 2026-11-07 指令）：本 plan 全部测试改造为纯 JDK / JUnit 构造；不引入 OS 专有 API、路径分隔符拼接、shell 外部进程；既有 multijvm 真实 JVM 拉起面（ProcessBuilder）保持既有跨平台形态，只改其时间等待写法。

## Purpose

把 nop-stream 各模块（runtime / core / connector-debezium / fraud-example / connector / connector-batch / flow）的单元测试，从「靠真实时间流逝与固定等待」改为「测试显式驱动虚拟时间、跨线程交接显式同步」，使测试结果与机器负载无关；同时把守卫基线的 nop-stream 切面收缩到逐处裁定的允许形态。

## Current Baseline

- 守卫基线（`ai-dev/tools/test-time-waits-baseline.json`，plan 2509 落地）nop-stream 切面：**255 处 / 120 文件 / 7 模块**——runtime 144 处 / 74 文件（SLEEP_THEN_ASSERT 15、POLL_LOOP 78、SIMULATED_WORK 4、OTHER 47）；core 48 处 / 18 文件（9/29/1/9）；connector-debezium 24 处 / 9 文件；fraud-example 22 处 / 9 文件；connector 10 处 / 7 文件；connector-batch 5 处 / 2 文件；flow 2 处 / 1 文件。最大单文件：TestAsyncSnapshotPipeline 10、TestProcessingTimeServiceDriver 10、TestCachingTableLookup 9、TestTaskManager 8、TestMultiJvmCoordinatorFailover 8、TestMultiInputBarrierAlignment 8。
- 生产时间读取面：
  - `ProcessingTimeServiceDriver`（core/execution）已读 `CoreMetrics.currentTimeMillis()`（:119/:147）——测试可直接注册可推进时钟；驱动线程 `Thread.sleep(tickMs)` 为生产内部轮询（允许形态）。
  - `CachingTableLookup`（core/connector/lookup）读 `System.nanoTime()`（:139/:216/:245）——TTL 判定未走 CoreMetrics，测试无法虚拟化（需迁移）。
  - runtime main 源 `System.currentTimeMillis` 命中 3 文件：`PollingJdbcMessageService`、`RemoteResultPartition`、`JobCoordinatorMain`（逐处核对是否为被测行为的时间判定）；`CoreMetrics` 在 runtime/core main 源已有 32 文件使用。
- 测试基建既有缝：
  - `TestAwait`（testsupport 条件等待工具）：**5 份逐字相同的副本**（core / runtime / connector / connector-debezium / connector-batch 各一）；`until`（条件等待，60s 挂死保护）/ `untilThreadParked` / `untilThreadSettles` 为允许形态；**`elapsed(what, ms)`（纯时间流逝窗口）与 `staysTrue(what, inv, windowMs)`（「X 毫秒内未发生」观察窗）属禁止形态**，调用点全仓 32 处（runtime 18 > core 14）需改造或逐处裁定；fraud-example / flow 目前无 TestAwait。
  - `MockInternalTimerService`（core/test）：算子级虚拟定时器服务（processing/event time 可控推进）。
  - **nop-stream 目前无任何 `ManualClock` / `CoreMetrics.registerClock` 消费**（grep 0 命中）——本 plan 是首次接线；既有消费先例在 plan 2508 的 nop-sys-dao / nop-biz-auth-core / 两调试模块。`CoreMetrics.nanoTime()` 存在（CoreMetrics.java:72），`ManualClock.nanoTime()` 走虚拟毫秒线。`CoreMetrics` 时钟是 JVM 全局量：surefire forkCount=4 / reuseForks=true / parallel=classes 下同一 fork 内测试类串行，注册时钟必须逐测试注册、`@AfterEach` 恢复 `CoreMetrics.defaultClock()`，漏恢复会冻结同 fork 后续所有类。
  - nop-stream-core 已导出 test-jar（共享测试 harness 先例），runtime test-jar 同。
- 全 reactor 基线：2026-11-07 plan 2508 收口时全量 BUILD SUCCESS、0 失败 0 错误。
- 守卫：`check-test-time-waits.mjs` 已入 CI；本 plan 收口必须对其 nop-stream 切面完成消号（`--update` 同批）。

## Goals

- nop-stream 测试源不再出现四类依赖（固定 sleep 作同步/等时间流逝、墙钟 deadline/TTL 作被测条件、真实毫秒定时器作被测条件、耗时阈值断言）；判定用时间读取一律经 CoreMetrics（或算子级 MockInternalTimerService），测试经 `ManualClock` / `MockInternalTimerService` / 显式同步驱动。
- 被测行为的决定性时间读取（TTL、deadline、宽限、定时触发）在 main 源经 CoreMetrics，默认时钟行为不变。
- 守卫基线 nop-stream 切面消号至「逐处裁定的允许形态」残余，总降幅 ≥ 90%（255 → ≤ 25 残余）。残余构成按实测分类预裁定：TestAwait canonical 副本内部轮询 3（5 副本归并后仅存 1 份）、MultiJvmTestSupport / MiniStreamCluster / TestMiniStreamClusterProcessSpawn 的进程管理窗口 ≤ 8（逐处具名裁定）、测试主动中断的模拟耗时 ≤ 6（TestAsyncSnapshotPipeline 2、TestIdlePeriodProcessingTimeWindowE2E 2、TestInputGateMarkFinishedChannelRace 1、TestDirectoryFileSourceFunction 1）等；每残余处 file:line + 形态 + 理由记录于 logs。
- 全 reactor 测试保持 0 失败 0 错误。

## Non-Goals

- 不改 nop-stream 生产行为的语义（默认时钟与默认定时器不变；只把判定性时间读取改经 CoreMetrics / 可注入）。
- 不处理 nop-stream 之外的模块（WI-5 ~ WI-8 承接）。
- 不重构 nop-stream 架构（checkpoint 协调器、传输面等结构性重构不在本 plan；只动测试与时间读取点）。
- 不把 nop-stream 测试迁到 nop-autotest 录制/回放体系。

## Scope

### In Scope

- `nop-stream/nop-stream-core`、`nop-stream-runtime`、`nop-stream-connector`、`nop-stream-connector-batch`、`nop-stream-connector-debezium`、`nop-stream-fraud-example`、`nop-stream-flow` 的测试源与上述判定性 main 时间读取点。
- 上述模块 pom 增加 `nop-commons` test-jar 测试依赖（消费共享 ManualClock/ManualScheduler）。
- `docs-for-ai/02-core-guides/testing.md` 若有涉及（如 TestAwait 形态说明）的小幅同步；`ai-dev/logs/` 逐文件裁定账本。
- 守卫基线 nop-stream 切面消号（`--update` 同批）。

### Out Of Scope

- 其他模块（WI-5 ~ WI-8）；守卫脚本本身（WI-3 已收口）。
- nop-stream 生产逻辑重构、性能优化、结构治理（独立 roadmap）。
- `nop-benchmark-stream`（JMH 基准，非单元测试）。

## 改造手册（各 Phase 共用的模式判定表）

- **异步完成等待**（等状态/结果/落盘）→ 显式同步：`CountDownLatch`、`Future.get(timeout)`、`FutureHelper.waitUntil`、`TestAwait.until(条件)`（条件必须是确定性事实，禁止以时间流逝为条件）；「仍未发生」断言必须先取得对方已在新时刻重新求值的结构证据（读取计数增长、消息计数、状态标志）再断言。
- **deadline / TTL / 宽限 / 限频 / 定时触发语义** → 生产判定读取经 `CoreMetrics.currentTimeMillis()` / `CoreMetrics.nanoTime()`；测试注册 `ManualClock`（owner test-jar）显式 `advance()` 越过截止点；算子定时器用 `MockInternalTimerService` 推进；组件接受注入 `ScheduledExecutorService` 的用 `ManualScheduler`。**时钟卫生**：`CoreMetrics.registerClock(...)` 在 `@BeforeEach`（或用例开头）注册，`@AfterEach` try/finally 恢复 `CoreMetrics.defaultClock()`——时钟是 JVM 全局量，漏恢复冻结同 fork 后续全部类。**时间戳陷阱**：改造后的测试注册/到期时刻一律从虚拟时钟线取（`ManualClock.peekMillis()` 相对量或固定基准常量），不得再写 `System.currentTimeMillis() + delta`。
- **gated 外部依赖面（multijvm / Docker debezium / Kafka broker）的确定性边界**：虚拟时钟无法跨越 spawned-JVM / 真实外部服务边界——此类测试的确定性化只指「消除任意固定窗口、改条件等待」，租约到期等真实时长语义保留墙钟并在裁定中具名记录；验证方式 = 编译 + 门开启跑一次（环境可用时）或编译 + 逐文件裁定记录（环境不可用时）。
- **墙钟间隔作被测条件**（如 watermark 周期）→ 虚拟时钟推进 + 推进前/推进后双断言（接线验证）。
- **模拟耗时**（SIMULATED_WORK）→ 仅当测试主动中断结束（interrupt / close）时允许保留，逐处记录；否则改 latch / 条件等待。
- **时间戳错开 / 步骤间隔**（OTHER）→ 相对时间 seed（基准常量 ± delta）、结构性排序断言，不用真实睡眠分隔。
- **`TestAwait.elapsed` / `staysTrue` 调用点**：默认消灭（改为上述模式）；如个别场景确需保留窗口语义，逐处裁定并写明「为何虚拟时间不可达」。全部调用点清零后，从 canonical TestAwait 删除这两个方法（防静默复用），只保留条件等待三方法。
- **TestAwait 归并（in scope）**：canonical 实现定为 nop-stream-core 测试源（随既有 core test-jar 导出），其余 4 副本删除、改 test-jar 依赖；fraud-example / flow 经 nop-stream-core test-jar 消费。

## Execution Plan

### Phase 1 - 生产判定性时间读取收敛与工具接线

Status: completed
Targets: `nop-stream/nop-stream-core/src/main`（CachingTableLookup 等）、`nop-stream/nop-stream-runtime/src/main`（PollingJdbcMessageService / RemoteResultPartition / JobCoordinatorMain 等逐处核对）、7 模块 pom

- Item Types: `Fix`, `Decision`

- [x] 全量盘点 7 模块 main 源时间读取（`System.currentTimeMillis` / `System.nanoTime` / `CoreMetrics` 现状）逐处列表，裁定三类：测试需虚拟化（迁移 CoreMetrics）/ 仅诊断日志（保留，记录）/ 真实生命周期（保留，记录）。盘点必须显式覆盖（对账评审 R1 实测）：`MemoryKeyedStateBackend:237` 的 nanoTime TTL sweep 门（TestMemoryStateTtlSweep 的前置）、`SourceRateLimiter:63/:88/:104` 节流判定，以及 `ScheduledExecutorService` 注入面（11 个 main 文件：16 处 executor 构造 + 10 处固定节奏调度点，含 CheckpointCoordinator:321 timeoutScheduler、TaskManager:260 heartbeatExecutor 现不可注入）——盘点表每条映射到消费它的测试（或「无测试消费」裁定）。
- [x] `CachingTableLookup` TTL 判定 `System.nanoTime()` → `CoreMetrics.nanoTime()`（默认时钟下行为不变）。
- [x] runtime 3 个 main 命中文件逐处裁定并落实（迁移或记录保留理由）。
- [x] 需要消费共享工具（ManualClock/ManualScheduler）与 canonical TestAwait 的模块 pom 加 `nop-commons` test-jar / `nop-stream-core` test-jar 测试依赖（对齐 plan 2508 消费方式）——**每个消费模块的硬前置**，先于该模块测试改造。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] main 源时间读取盘点表（文件:行 → 裁定）写入 `ai-dev/logs/2026/11-07.md`；迁移点默认时钟行为不变（既有测试全绿）。
- [x] `./mvnw <repo> test -pl nop-stream/nop-stream-core -am` 全绿。
- [x] `./mvnw <repo> compile -pl nop-stream/nop-stream-runtime -am` 通过（Phase 1 亦改 runtime main 与各模块 pom，不能只验证 core）。
- [x] No owner-doc update required（不改文档化行为）。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 2 - nop-stream-core 测试改造（48 处 / 18 文件）

Status: completed
Targets: `nop-stream/nop-stream-core/src/test`（TestProcessingTimeServiceDriver、TestCachingTableLookup、TestAwait.elapsed/staysTrue 调用面、TestStreamSourceOperator 等）

- Item Types: `Fix`

- [x] `TestProcessingTimeServiceDriver`（10 处）：注册 `ManualClock`，fire / 不 fire / shutdown 后不 fire 全部由时钟推进驱动；线程启动/停止等待改 `TestAwait.until` 条件等待；去掉「shutdown 后 sleep(60) 观察」类窗口（以驱动线程已终止 + mailbox 快照结构断言替代，负向断言前先给驱动线程重新求值机会的显式同步）。
- [x] `TestCachingTableLookup`（9 处）：TTL 过期/未过期由 `ManualClock` 推进（配合 Phase 1 nanoTime 迁移）；加载并发等待改条件等待；包私有构造器注入 `ManualScheduler` 驱动后台扫描（单一虚拟时间线）。
- [x] core 全部 `TestAwait.elapsed` / `staysTrue` 调用点改造（Phase 3 batch C 复核全仓 0 残留）。
- [x] 其余文件逐文件改造（独立实现子 agent 批 1，per-file 账本 `_tmp/wi4-core-batch1-report.md`）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 守卫 core 切面 = TestAwait canonical 内部轮询 + TestTaskLifecycle 中断收尾模拟耗时（允许形态，已入 logs）；其余 0 命中。
- [x] core 全部 `elapsed` / `staysTrue` 调用点 0 残留（batch C 全仓复核）。
- [x] `./mvnw <repo> test -pl nop-stream/nop-stream-core -am` 全绿（1968 用例 0 失败，batch C 后复跑再证）。
- [x] 接线验证（推进前/推进后断言）+ 变异探针 2 组（processing-time fire 4 红、TTL 过期 1 红）撤行为即红、恢复 grep 校验后复跑绿。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 3 - nop-stream-runtime 测试改造（144 处 / 74 文件，分两批）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test`（A 批：checkpoint / execution / operators / cluster / coordinator；B 批：taskmanager / multijvm / transport / launch / connector / deploy / ops / 其余）

- Item Types: `Fix`

- [x] **守卫消号纪律**：A2 批触发 `--module` 整仓覆盖缺陷并已修复（合并语义）；Phase 5 全仓 `--update` 终局对账完成。
- [x] A 批逐文件改造：checkpoint 18 文件 39 处→0（A1）、execution+taskmanager 14 文件 32 处→0（A2）；宽限 / 心跳 / TTL 类虚拟时钟化（per-file 账本 `_tmp/wi4-runtime-batchA1-report.md` / `_tmp/wi4-runtime-batchA2-report.md`）。
- [x] B 批逐文件改造：multijvm/coordinator/cluster/transport 23 文件 40 处→0（per-file 账本 `_tmp/wi4-runtime-batchB-report.md`；multijvm 拉起面保持既有形态，等待改条件等待，真实时长语义具名裁定）。
- [x] runtime 侧 `TestAwait.elapsed` / `staysTrue` 调用点清零（batch C，含前批漏改 12 处）。
- [x] **canonical TestAwait 收敛**：runtime 本地副本删除（26 文件切 import），32 调用点全仓清零，canonical `elapsed` / `staysTrue` 方法已删除（grep 复核 0 残留）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 守卫 runtime 切面 = 3 处具名裁定（gated 子 JVM 源 pacing、REST 慢源中断收尾、BatchLoader 真实 barrier 节拍，已入 logs）。
- [x] 各批测试全绿（A1 75×3 轮、A2 65、B 89 + 门控 8、C 21 类 + core 1968 复跑）。
- [x] **gated 面验证**：门开启跑 multijvm 套件 8 用例全绿（-Dnop.stream.test.multi-jvm.enabled=true）；条件等待改造 + 真实时长语义具名裁定；虚拟时钟不可跨进程边界口径落实。
- [x] 接线验证 + 变异探针 2 组（TTL sweep 门 1 红、send-gate deadline 1 红）撤行为即红、恢复 grep 校验后复跑绿。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/2026/11-07.md` 已更新（每文件裁定账本在 _tmp 批报告，logs 收录摘要与残余清单）。

### Phase 4 - connector-* / fraud-example / flow 测试改造（63 处 / 28 文件）

Status: completed
Targets: `nop-stream-connector-debezium`（24/9）、`nop-stream-fraud-example`（22/9）、`nop-stream-connector`（10/7）、`nop-stream-connector-batch`（5/2）、`nop-stream-flow`（2/1）

- Item Types: `Fix`

- [x] connector-debezium：非门控 4 文件全清（deadline 轮询→条件等待、stub 运行环→latch、mock emitter pacing→可中断 park）。
- [x] **Docker 门控真库套件（13 处）**：条件等待机械化完成；虚拟时钟不适用已执行；验证 = 编译 + probe-skip 实证（环境探针未过，按裁定记录）；1 处具名残余（RealMysqlParallel mis-route 观察窗）。
- [x] **Kafka 门控套件**：TestKafkaConnectorExactlyOnceE2E 同口径条件等待处理（batch C，probe-skip 裁定路径）。
- [x] fraud-example：非门控 4 文件清零；gated 多 JVM harness 6 helpers 条件等待化（9 处具名残余 = chaos 编排/节流被测语义/记录轮换窗口）。
- [x] connector / connector-batch / flow：逐文件改造（10→0、5→0、2→1）；connector/connector-batch/debezium 的 3 份本地 TestAwait 副本删除（canonical 收敛完成）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 5 模块守卫权威清单核对：非门控 0 命中；残余 11 处全部具名裁定入 logs（fraud 9 + debezium 1 + flow 1）。
- [x] 5 模块触碰类测试全绿（debezium 39、connector 20、batch 12、flow 3、fraud 34 = 108 用例 0 失败）+ test-compile -am 5 模块 SUCCESS。
- [x] 变异探针 1 组（refresh dispatch 3 红）撤行为即红、恢复 grep 校验后复跑绿。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 5 - 守卫基线消号与全量门

Status: completed
Targets: `ai-dev/tools/test-time-waits-baseline.json`、全 reactor

- Item Types: `Proof`

- [x] `check-test-time-waits.mjs --module nop-stream` 残余核对 = 16 处具名裁定集合（fraud 9 gated + runtime 3 + core 2 + debezium 1 + flow 1）；全仓 `--update` 消号（521→282，全仓 diff 只含 nop-stream 减量；A2 批发现的 `--module` 整仓覆盖缺陷已修复为合并语义）。
- [x] 负载下稳定性证明：满载 3 轮 core+runtime 合跑 BUILD SUCCESS ×3（`_tmp/wi4-load2-round{1,2,3}.log`）；首轮 3 轮暴露的 6 个负载敏感用例已根因修复（`_tmp/wi4-loadfix-report.md`）；burner 无残留。
- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` BUILD SUCCESS、456 模块 SUCCESS、0 失败 0 错误（`_tmp/wi4-full-test.log`）。
- [x] roadmap WI-4 → done；`ai-dev/logs/` 收口条目（含基线降幅 255→16、残余清单指针）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 基线消号证据写入 logs（255→16，降幅 93.7%）；全仓 check exit 0。
- [x] 负载稳定性 3 轮证据写入 logs。
- [x] 全 reactor 0 失败 0 错误证据写入 logs。
- [x] roadmap 状态块已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 7 模块测试源四类依赖清零（残余 16 处逐处具名裁定记录于 logs 账本）
- [x] 判定性 main 时间读取已收敛 CoreMetrics（14 处迁移 / 保留处逐条裁定；默认时钟行为不变，全量测试绿）
- [x] 守卫基线 nop-stream 切面消号完成（255→16，降幅 93.7%，≤ 25 ✓；构成 = gated 真实语义 + 中断收尾模拟耗时 + canonical 工具内部轮询，逐处具名于 logs；其中 fraud gated 9 处为 Phase 4 执行期识别的同族 gated 面，构成口径经 closure audit 复核）
- [x] 变异探针 5 组证据齐全（fire 投递 4 红、TTL 回源 1 红、sweep 门 1 红、send-gate 1 红、refresh dispatch 3 红；恢复 grep 校验 + 复跑绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：虚拟时钟推进在生产判定路径被消费（变异探针 5 组即接线证明）；`scan-hollow --severity high` core/runtime exit 0
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor，BUILD SUCCESS 456 模块）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2510-nop-stream-test-time-determinism.md --strict` 退出 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] **跨平台核查**：改造面为纯 JDK/JUnit 构造（ManualClock/ManualScheduler/TestAwait 均 JDK-only；multijvm ProcessBuilder 拉起面保持既有形态；负载 burner 纯 Java）；结论入 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- WI-5 ~ WI-8 各模块清欠（roadmap 承接）。
- nop-stream 基线残余中「测试主动中断的模拟耗时」若后续可统一为中断友好的工具方法，由后续治理项评估（optimization candidate）。

## Closure

Status Note: nop-stream 7 模块测试时间确定化完成：255 处时间等待消号至 16 处具名裁定残余（降幅 93.7%），四类禁止写法在非门控面清零；判定性生产时间读取收敛 CoreMetrics（14 处迁移）；共享工具经 test-jar 首次接入 nop-stream 且 TestAwait 归并为单一 canonical（elapsed/staysTrue 删除）；满载 3 轮稳定性证明 + 全 reactor 456 模块 BUILD SUCCESS。独立 closure audit 复核全部 Phase 与 Closure Gate 后 APPROVED（3 Minor 均为措辞/记账级，已同批修正）。

Completed: 2026-11-07

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现；各批实现由独立实现子 agent 承担，draft R1/R2 审查为另一独立子 agent）
- Evidence:
  - Phase 1：6 个迁移文件现场 grep 证实（nanoTime×7 / currentTimeMillis×7）；保留裁定表逐行核对（行号与 live 一致）；7 模块 pom test-jar 依赖与 plan 逐项一致。
  - Phase 2–4：`--module nop-stream` 实测残余 = 16（fraud 9 / runtime 3 / core 2 / debezium 1 / flow 1，11 文件）；残余抽查 4 处全部为允许形态（canonical 轮询 / gated 具名裁定 / 中断收尾模拟耗时）；改造测试断言强度对照 HEAD 3 例（无削弱、无删除；TestRemoteResultPartitionBackpressureGate 的阈值断言以更强结构证据替换）。
  - TestAwait 归并：`class TestAwait` 全 nop-stream 恰 1 处（canonical）；elapsed(/staysTrue( 0 命中；canonical 仅剩条件等待三方法。
  - Phase 5：基线 diff vs HEAD = 纯 nop-stream 减量（110 条目删除、非 nop-stream 总量 266→266 不变）；负载证明 3 轮绿 + 6 处负载敏感用例根因修复（`_tmp/wi4-loadfix-report.md`）；全 reactor log BUILD SUCCESS / 456 SUCCESS / 0 FAILURE 行（审计员现场复核日志，未重跑）。
  - 变异探针 5 组红→恢复（grep 校验）→绿记录于 logs；生产文件无探针残留（grep probe* 0 命中）。
  - 守卫 `--module --update` 合并语义现场读码复核（保留前缀外条目、覆盖前缀内、重算 totals）。
  - 跨平台：5 个改造文件 diff 无 Runtime.exec/ProcessBuilder/File.separator 新增；multijvm 拉起面仅等待形式变化；`scan-hollow --module nop-stream --severity high` exit 0（覆盖 core/runtime 的超集核查）。
  - 文本一致性：5 Phase 全 completed、Closure Gates 除审计门外全勾、`check-plan-checklist.mjs --strict` exit 0、`check-doc-links.mjs --strict` 0 errors、logs 与 plan 一致。
  - 审计发现处理：Minor#1 日志「26 文件」→「28 文件」已修正；Minor#2 跨平台结论指针已补入 logs；Minor#3 为基线 totals 观察项（无需动作）。
  - 审计判定：**APPROVED**。

Follow-up:

- WI-5 ~ WI-8 各模块清欠（roadmap 承接，每 plan 收口对其切面 `--update` 同批消号）。
- 中断收尾模拟耗时的统一工具方法（optimization candidate，非阻塞）。
- 无剩余 plan-owned work。
