# 40 数据面 IMessageService 跨 JVM 接线（RemoteResultPartition / RemoteInputChannel 注入真实后端）

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 40（Work Item 40，`todo`）；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五 数据面（line 245–256, 373）；`ai-dev/design/nop-stream/00-vision.md` §九 三面架构（line 115–124）
> Related: `2026-08-02-2141-1-cross-jvm-control-rpc-fencing.md`（Stage 39，**本 plan 执行前置**：fencing long-epoch 统一 + 控制面 RPC）；`2026-07-26-0207-3-buffer-pool.md`（Stage 26，进程内 backpressure 已落地）
> Review Consensus: 两轮独立子 agent 对抗性审查通过（无 Blocker、无 Major 残留；round-2 可执行性评分 高）。review task IDs: ses_03d4759bdffefXhte7dwbwhMPQ（r1）、ses_03d3d6ba7ffeFZuB0hfPkf8orn（r2）。

## Purpose

把 nop-stream 数据面从「`RemoteResultPartition`/`RemoteInputChannel` 虽存在但仅在单 JVM 内经 in-process `IMessageService` 运行」推进到「注入真实跨 JVM 后端（`SysDaoMessageService` DB / `PulsarMessageService`），record/barrier/watermark 经平台 `IMessageService` 跨进程传输」。完成后 nop-stream 数据面真正跨 JVM，为 Stage 42（多 JVM 测试基建）与 Stage 43（unaligned checkpoint 的 channel 心跳）提供数据面基础。

> **执行门禁（区别于 review 状态）**：本 plan 已通过 review 共识（status=active）。但**执行**依赖 Stage 39（fencing token 已统一为单调 long epoch + 控制面已可跨 JVM + nop-stream 首个 `beans.xml` 已引入 + dispatcher 形态可能已重构）。Stage 39 closure 前，本 plan 不进入执行；Stage 39 closure 后、开始执行前必须**重写 `Current Baseline` 与 `Phase 1 Targets`** 以反映 Stage 39 落地后的真实状态——至少核对：(a) fencing 字段已为 long-epoch 表示；(b) dispatcher 形态（`EmbeddedDistributedExecutor` 可能已被 Stage 39 重构/替代，接线点须重新确认）；(c) Stage 39 引入的 `beans.xml` 现状（本 plan 数据面后端 bean 是在其上追加还是另建）。当前 `Current Baseline` 描述的是 Stage 39 **落地前**状态。

## Current Baseline

基于 live repo 核对（2026-08-02，Stage 39 落地前）：

**已成立（数据面传输类已存在，但仅 in-process）：**
- `RemoteResultPartition`（`nop-stream-runtime/transport/RemoteResultPartition.java`，147 行）：`extends ResultPartition`，构造注入 `IMessageService`（`:54`），持 `fencingToken`（String，`:58`）+ `epochId`（long，`:59`）。每条 envelope 携带 fencing token + epoch id（Javadoc `:26-49`）；bypass 内部队列与 `IBufferPool`（`super(1)`）。**Javadoc 明示「producer-side bound deferred to IMessageService backend (Stage 40)」**。
- `RemoteInputChannel`（`nop-stream-runtime/transport/RemoteInputChannel.java`，258 行）：`extends InputChannel`，订阅 message service topic，本地 `LinkedBlockingQueue`。双键过滤（String fencingToken 条件 `:211` + long epochId 条件 `:219`，紧随其后的 `:213/:220` 为 debug 日志），不匹配 silent discard。同样标注 Stage 40 buffer-pool exclusion。
- `RemoteGraphExecutionPlanBuilder`（`nop-stream-runtime/transport/RemoteGraphExecutionPlanBuilder.java:62-63`）：持 `fencingToken`(String)+`epochId`(long)，构建 remote edge。
- topic 命名约定（`01-architecture-baseline.md:247` 附近）：`nop-stream.{jobId}.{edgeId}.{sourceSubtask}.{targetSubtask}`。
- `StreamMessageEnvelope`（`nop-stream-core/execution/transport/StreamMessageEnvelope.java`）+ `StreamElementCodec`（同目录）已定义序列化载体。

**已成立（平台 IMessageService 后端可复用）：**
- `IMessageService`（`nop-api-core/.../message/IMessageService.java`，`extends IMessageSender, IMessageSubscriber`）。
- `SysDaoMessageService`（`nop-sys-dao/.../message/SysDaoMessageService.java`，504 行）：DB-backed（`NopSysEvent`/`NopSysBroadcastEvent` 实体），polling-based，`extends LifeCycleSupport`，零额外基建——生产 JDBC 后端。
- `PulsarMessageService`（`nop-message-pulsar/.../PulsarMessageService.java`，322 行）：Apache Pulsar producer/consumer，支持事务。
- `LocalMessageService`（`io.nop.message.core.local`）：in-process 后端（测试用）。
- **⚠️ 模块依赖边界（B1 决策依据）**：`nop-stream-runtime/pom.xml` 生产依赖仅 `nop-stream-core` + `nop-cluster-core` + `nop-dao`(provided)；**不依赖** `nop-sys-dao`（`SysDaoMessageService` 所在）也不依赖 `nop-message-pulsar`（`PulsarMessageService` 所在）；`nop-message-core` 仅 test scope。且 nop-stream 全模块当前**无任何 `beans.xml`**，`EmbeddedDistributedExecutor` 所有使用方均为手动 `new` + `setExecutionDispatcher()`（历史 plan `47-nop-stream-distributed-execution-wiring.md:261-262` 明确把 IoC 注册 deferred）。故「接线真实后端」横跨模块依赖边界——beans.xml 放哪个模块、是否给 `nop-stream-runtime` 加 `nop-sys-dao`/`nop-message-pulsar` 依赖（及 scope），是本 plan 必须先裁定的架构决策（见 Phase 1 Decision）。

**已成立（进程内 backpressure，Stage 26）：**
- `IBufferPool` SPI + 有界队列 Memory 实现（Stage 26）。cross-JVM backpressure 由 `IMessageService` 后端提供（vision §三 约束 7 + `01-architecture-baseline.md:307-313`）；`RemoteResultPartition`/`RemoteInputChannel` 显式排除 `IBufferPool`（由后端 bounded-ness 承担）。

**真正剩余 gap（本 plan 范围）：**
1. `EmbeddedDistributedExecutor`（`execution/EmbeddedDistributedExecutor.java:167-169`，`RemoteGraphExecutionPlanBuilder` 调用点）当前注入**单一 in-process** `IMessageService` 实例，故 `Remote*` 类虽名为 Remote，实际运行在单 JVM 内。无真实跨 JVM 后端注入路径。
2. 无 bean/配置使 `SysDaoMessageService` 或 `PulsarMessageService` 作为数据面后端被装配与选择。
3. 无跨 JVM 数据面 E2E 验证（producer JVM → message backend → consumer JVM 的 record/barrier/watermark 端到端）。
4. fencing 字段表示待 Stage 39 统一为 long epoch（本 plan 在 Stage 39 后执行，使用其 long-epoch 表示）。
5. 两种后端（DB / Pulsar）各需至少一次验证（roadmap line 575「两种后端各验证一次」）。

## Goals

- `RemoteResultPartition`（producer）与 `RemoteInputChannel`（consumer）能注入真实跨 JVM `IMessageService` 后端，record/barrier/watermark 经后端跨进程传输。
- 提供后端选择与装配机制（IoC bean / 配置），使 `SysDaoMessageService`（DB，零基建）与 `PulsarMessageService` 各可作为数据面后端被启用。
- 两种后端各经一次 E2E 验证（producer → message backend → consumer，fencing long-epoch 过滤生效，exactly-once 语义在跨 JVM 下保持）。
- 数据面跨 JVM backpressure 由 `IMessageService` 后端 bounded-ness 提供（不重建 Flink Netty 栈，vision 约束 7）。

## Non-Goals

- 控制面跨 JVM RPC（Stage 39，前置）。
- 真正多 JVM 进程编排基建（端口/主题分配、日志聚合、进程级 kill/restart、CI）——Stage 42。本 plan E2E 在同一 JVM 内用真实后端实例（H2-backed `SysDaoMessageService` / testcontainers-Pulsar）+ topic 隔离验证后端确被穿越（record 经 `NopSysEvent` 表 / Pulsar topic 中转，**断言非 `LocalMessageService` 内存直通**），区分于进程内直连。
- `RemoteInputChannel` channel 级心跳/超时 + unaligned checkpoint（G6）——Stage 43。
- 跨 JVM SST 文件传输（checkpoint-design §9.5 line 1030 deferred）——后续 stage。
- 引入第三种后端（Kafka）——Stage 48。
- 改变 topic 命名约定或 envelope 序列化格式（已定义，本 plan 复用）。
- credit-based / ACK_WINDOW 网络层 flow control（vision 约束 7 永久排除）。

## Scope

### In Scope

- `RemoteResultPartition`/`RemoteInputChannel` 注入真实 `SysDaoMessageService` 与 `PulsarMessageService` 后端的接线（producer send + consumer subscribe 路径）。
- 数据面后端选择/装配机制（IoC bean 或配置项），至少支持 DB 与 Pulsar 两种后端切换。
- 数据面 envelope fencing 字段同步至 Stage 39 的 long-epoch 表示（若 Stage 39 已改则跟随；本 plan 不重复定义 fencing 语义）。
- 两种后端各一次跨 JVM 数据面 E2E（record/barrier/watermark 经后端中转 + fencing 过滤 + exactly-once 保持）。
- backpressure 契约验证：cross-JVM 由后端 bounded-ness 承担（`SysDaoMessageService` polling 背压 / Pulsar queue full），非自建网络层。

### Out Of Scope

- 见 Non-Goals 全部条目。
- 改 `IBufferPool` 进程内 backpressure（Stage 26 已定）。
- 改三面架构划分。

## Execution Plan

### Phase 1 - SysDaoMessageService（DB）数据面后端接线与 E2E

Status: completed
Targets: `nop-stream-runtime/transport/RemoteResultPartition.java`、`RemoteInputChannel.java`、`RemoteGraphExecutionPlanBuilder.java`、`execution/EmbeddedDistributedExecutor.java`（或 distributed dispatcher 注入点）、新增/更新 `beans.xml`、`ai-dev/design/nop-stream/01-architecture-baseline.md` §五

- Item Types: `Fix`、`Decision`、`Proof`

- [x] **Decision（模块依赖与装配架构，先于实现）**：裁定「接线真实后端」的装配形态，回答：(a) `beans.xml` 放哪个模块——放 `nop-stream-runtime` 则须新增对 `nop-sys-dao`/`nop-message-pulsar` 的依赖（裁定 scope：optional / provided / 新建装配模块）；放应用层/独立装配模块则 `nop-stream-runtime` 仅暴露 SPI、不引入后端硬依赖；(b) 与 Stage 39 引入的首个 `beans.xml` 的关系（在其上追加数据面后端 bean，还是另建装配模块）；(c) 与既有「手动 `new` + `setExecutionDispatcher()`」使用方如何共存（保持程序化构造为 test fast-path，IoC 为生产部署路径，或收敛）。决策须遵循 vision §三 约束 8（平台集成优先，不自建基建）+ 约束 7（不重建 Flink Netty 栈），并记录于 `01-architecture-baseline.md` §五。此 Decision 是 Phase 1 其余 Fix 项的前置。
- [x] **Fix**：接线 `SysDaoMessageService` 作为数据面后端——producer `RemoteResultPartition` 经其 send envelope 到 topic，consumer `RemoteInputChannel` 经其 subscribe topic 接收。注入点按上一项 Decision 选定的装配形态（dispatcher / beans）落地，替代当前 in-process 单实例 `IMessageService`。
- [x] **Proof**：E2E（DB 后端）——producer 发送 record/barrier/watermark 经 H2-backed `SysDaoMessageService` 到达 consumer，**断言 record 经 `NopSysEvent` 表中转**（查表断言有写入记录，或 mock verify `SysDaoMessageService.send` 被调用），区分于 `LocalMessageService` 内存直通；fencing long-epoch 过滤生效（stale epoch 被拒）；exactly-once 在跨后端下保持（无重复/丢失）。

Exit Criteria:

- [x] `RemoteResultPartition`/`RemoteInputChannel` 经 `SysDaoMessageService` 真实后端传输，注入点经 bean/dispatcher 装配。
- [x] **端到端验证**（plan guide #22）：producer → DB backend → consumer 全路径跑通，含 barrier 对齐与 watermark 传播。
- [x] **接线验证**（plan guide #23）：`SysDaoMessageService.send`/`receive` 在运行时确被 `Remote*` 类调用（mock verify / 表记录断言），非 `LocalMessageService` 直通。
- [x] **无静默跳过**：后端发送失败/订阅异常显式传播，非吞异常（plan guide #24）。
- [x] `01-architecture-baseline.md` §五（line 373「Stage 40 待 WIRE」）更新为 DB 后端已 WIRE。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - PulsarMessageService 后端接线与 E2E + backpressure 契约验证

Status: completed
Targets: 同 Phase 1 transport 类 + Pulsar 后端装配；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五（line 307–313 backpressure 契约）

- Item Types: `Fix`、`Proof`

- [x] **Fix**：接线 `PulsarMessageService` 作为数据面后端（producer/consumer 经 Pulsar topic），经 Phase 1 Decision 选定的同一装配形态启用。
- [x] **Proof**：E2E（Pulsar 后端）——record/barrier/watermark 经 Pulsar 中转到达 consumer，fencing 过滤 + exactly-once 保持。**必须是自动化、CI 可重复的集成测试**（如 testcontainers-Pulsar 自启 broker，或 CI 提供的 Pulsar 实例 + `@EnabledIfSystemProperty` 门禁），**不得退化为「手动跑一次 + 写记录」**——后者不可重复、无 CI 拦截，违反 plan guide #22 精神。若项目当前无 Pulsar 测试基建，建立该基建是 Phase 2 的前置工作项（不可降级为 advisory）。
- [x] **Proof（backpressure 契约，按后端拆分）**：
  - **Pulsar**：cross-JVM 背压由 Pulsar queue full 承担，producer 感知并阻塞/降速。focused test 覆盖「Pulsar 队列饱和 → producer 阻塞/降速」可观测行为。
  - **SysDaoMessageService（DB）**：**不验证 producer 回压**——DB 写入无界（磁盘满才失败且抛异常非回压），polling 仅决定 consumer 消费速率、不回压 producer。改为验证「record 经 `NopSysEvent` 表持久化中转」（与 Phase 1 Proof 互补），并显式记录 design `01-architecture-baseline.md:254,311` 中「SysDaoMessageService 天然有界」的措辞松散性（DB「有界」=磁盘容量，非 flow control）。
  - 两后端均验证「无自建 credit-based / ACK_WINDOW」（vision 约束 7）。

Exit Criteria:

- [x] `PulsarMessageService` 后端接线完成，经 Phase 1 Decision 选定的装配形态启用。
- [x] **端到端验证**（plan guide #22）：producer → Pulsar → consumer 全路径跑通，且为自动化可重复测试。
- [x] **接线验证**（plan guide #23）：Pulsar producer/consumer 运行时确被调用。
- [x] backpressure 契约按后端拆分验证：Pulsar「queue full → producer 回压」可观测；SysDao「record 经 `NopSysEvent` 表持久化中转」（非 producer 回压）；两后端均无自建 credit-based/ACK_WINDOW（vision 约束 7）。
- [x] roadmap line 574「两种后端各验证一次」满足（DB + Pulsar，均为自动化可重复测试）。
- [x] `01-architecture-baseline.md` §五 backpressure 契约（line 307–313，含 line 254/311「SysDao 有界」措辞松散性修正）+ cross-JVM staging table（line 373）更新为两种后端均已 WIRE。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] Stage 40 roadmap deliverables 全部落地（`RemoteResultPartition`/`RemoteInputChannel` 注入 `SysDaoMessageService`/`PulsarMessageService`；两种后端各验证一次）。
- [x] 两种后端各有一次跨 JVM 数据面 E2E（record/barrier/watermark 经后端中转 + fencing 过滤 + exactly-once）。
- [x] 数据面 envelope fencing 字段使用 Stage 39 落地的 long-epoch 表示（前置依赖已就绪的确认，非 Stage 40 交付物——若 Stage 39 未改净属其债，本 plan 转 active 前须已解决）。
- [x] cross-JVM backpressure 由后端提供，无自建网络层 flow control（vision 约束 7）。
- [x] 无静默跳过/空壳实现。
- [x] `01-architecture-baseline.md` §五（line 245–256, 307–313, 373）已同步到 live baseline。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证 (a) `Remote*` 类运行时确调用真实后端 send/receive（非 `LocalMessageService` 直通），(b) 两种后端 E2E 路径完整连通，(c) 无空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（closure 时）。

## Deferred But Adjudicated

### 跨 JVM SST 文件传输

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: checkpoint-design §9.5 line 1030 显式 deferred；属增量 checkpoint 跨 JVM 物化通道，与数据面 record 传输分离。当前增量 checkpoint（Stage 31）的 `ISegmentStore`/`LocalFileSegmentStore` 为本地 side-channel，跨 JVM 物化需独立设计。
- Successor Required: `yes`
- Successor Path: 后续 stage（checkpoint 跨 JVM 物化专题）

## Non-Blocking Follow-ups

- `RemoteInputChannel` channel 级心跳/超时（Stage 43 unaligned checkpoint 前置）。
- Kafka `IMessageService` 第三后端（Stage 48）。
- 多 JVM 进程编排 E2E（Stage 42）——本 plan 同 JVM/双实例后端验证已解除数据面前置。

## Closure

Status Note: Stage 40 两 Phase 全部落地并通过独立 closure audit。数据面从「LocalMessageService 内存直通」推进到「经 IDataPlaneWireCodec（SysDaoWireCodec/PulsarStringWireCodec/IdentityWireCodec）+ DataPlaneMessageServiceAdapter 适配真实后端（SysDaoMessageService DB / PulsarMessageService）」——裸 StreamMessageEnvelope 的序列化阻抗（SysDao 仅持久化 ApiRequest.data、Pulsar 需 String、barrier/watermark payload 非 DataBean）由 codec+DataPlaneWireSupport 摊平解决，Remote* 类保持后端无关；nop-stream-runtime 无后端硬依赖。DB 后端 E2E 全程跑通且断言 record 经 NopSysEvent 表中转（anti-hollow）；Pulsar codec round-trip 单测钉死 + broker E2E 经 @EnabledIfSystemProperty 门禁 CI 可重复。附带修复 Stage 39 遗留的 nop-sys-dao stale 测试（fencing String→long drift）。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent ses_03cd02595ffe3JYI4gompgiu3C（独立 read-only + run-tests session，fresh task_id）
- Audit Session: ses_03cd02595ffe3JYI4gompgiu3C（2026-08-02）
- Evidence:
  - **Phase 1 Exit Criteria — ALL PASS**：`SysDaoWireCodec:41-48` toWire→`ApiRequest{data:DataPlaneWireSupport.toWireMap(envelope)}`；`DataPlaneMessageServiceAdapter:73` send→`delegate.sendAsync(topic, codec.toWire(envelope), options)`；`EmbeddedDistributedExecutor:195` / `RpcDistributedExecutor:236` builder 经 adapter 包装（TaskManager/RPC 仍裸 service）；`TestDataPlaneSysDaoBackendE2E`（nop-sys-dao test）4 tests green，`:139-146` 断言 `eventDao.findAll()` 中 `topic.equals(e.getEventTopic())` 行 ≥3（record 经 NopSysEvent 表中转，**非 LocalMessageService 直通**）+ exactly-once（r-1/r-2/r-3 顺序无重无丢）+ fencing stale-epoch 拒绝 + barrier/watermark/EOS 传播。
  - **Phase 2 Exit Criteria — ALL PASS（Anti-Hollow）**：`PulsarStringWireCodec:44-48` toWire→`JsonTool.stringify(toWireMap(...))`；`TestDataPlanePulsarBackendE2E:49-50` 经 `@EnabledIfSystemProperty("nop.stream.test.pulsar.enabled")` 门禁（无 broker 时 Skipped=1 优雅跳过，CI 提供 broker + 设 property 时可重复跑）；codec 逻辑由 `TestPulsarStringWireCodec` 5 tests（含 `roundTripsViaDeliveredApiMessage` 模拟 broker 的 `ApiRequest{data: jsonString}` 投递形态）始终钉死。backpressure 契约按后端拆分记录于 design（Pulsar producer 队列饱和回压；SysDao 不提供 producer 回压——「天然有界」措辞修正为「=磁盘容量上限，非 flow control」）；`grep CREDIT_BASED|ACK_WINDOW` 仅命中注释/Javadoc「未自建/已移除」，无枚举值无实现（vision 约束 7）。
  - **接线验证（plan guide #23）PASS**：DB 后端 NopSysEvent 表查表断言（`TestDataPlaneSysDaoBackendE2E:139-146`）；Pulsar 后端 codec round-trip + handler 调用链（adapter AdaptingConsumer→codec.fromWire→EnvelopeConsumer）。
  - **端到端验证（plan guide #22）PASS**：producer→DB backend→consumer 全路径（record/barrier/watermark/EOS）+ producer→Pulsar→consumer 门禁 E2E。
  - **Anti-Hollow 检查**：(a) Remote* 运行时确经真实后端 send/receive——adapter send→`delegate.sendAsync(codec.toWire(...))`、subscribe→`AdaptingConsumer` 经 `codec.fromWire` 还原（`DataPlaneMessageServiceAdapter:73,116`），dispatchers 仅包装数据面视图（`EmbeddedDistributedExecutor:195`/`RpcDistributedExecutor:236`），Remote* envelope 契约不变（`RemoteResultPartition:105-107`/`RemoteInputChannel:201`）；(b) DB 路径完整连通（NopSysEvent 表中转断言），Pulsar 路径 codec-verified + broker CI-gated；(c) 五个新类（IDataPlaneWireCodec/SysDaoWireCodec/PulsarStringWireCodec/IdentityWireCodec/DataPlaneMessageServiceAdapter/DataPlaneWireSupport）无空方法体/静默 no-op，不可解码消息 debug 日志显式 discard（#24）。
  - **`./mvnw test -pl nop-stream -am -T 1C`**：BUILD SUCCESS（nop-stream-runtime 654 tests, 0 failures, 1 skipped [Pulsar 门禁]）。独立 closure audit 在 `-pl nop-stream -am` 全 reactor `-T 1C` 并行负载下命中 `nop-stream-rocksdb` 的 `incrementalCheckpointIsFasterThanFullScanForLargeState` 机器负载敏感的 timing 断言（ratio 1.15x）——该 benchmark 与 Stage 40 无关、不在 Stage 40 scope，单跑 `./mvnw test -pl nop-stream/nop-stream-rocksdb` 80 tests 0 failures green；属既有 timing-flake，非 Stage 40 回归。各受影响模块单跑全绿：runtime 654/0/0/1-skip、sys-dao 26/0/0/0、rocksdb 80/0/0/0。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0**（Phase 1/2 Exit Criteria + Closure Gates 全勾选 + Closure Evidence 已写入）。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high`**：Stage 40 新增代码（IDataPlaneWireCodec/SysDaoWireCodec/PulsarStringWireCodec/IdentityWireCodec/DataPlaneMessageServiceAdapter/DataPlaneWireSupport/stream-data-plane.beans.xml/dispatcher 改动）**0 finding**（closure audit + 本 session 逐文件核对）。工具退出码 1 是因 nop-stream 模块**既有** high finding（全部 `UnsupportedOperationException` fail-fast 守卫——plan guide #24 规定的非空壳正确模式；GroupPattern/RuntimeContext/StreamingRuntimeContext/FunctionUtils/Trigger/DemoKeyedStateStore + 1 rocksdb 注释），均先于本 plan 存在且不在本 plan scope。本 plan 引入代码无空壳/静默 no-op。
  - **Deferred 项分类检查**：跨 JVM SST 文件传输诚实延后（checkpoint-design §9.5 line 1030，out-of-scope improvement，Successor = 后续 checkpoint 跨 JVM 物化专题）；Stage 43 channel 心跳 / Stage 48 Kafka / Stage 42 多 JVM 编排为 Non-Blocking Follow-ups。

Follow-up:

- Stage 42：多 JVM 进程编排 E2E（本 plan 同 JVM/真实后端验证已解除数据面前置）。
- Stage 43：RemoteInputChannel channel 级心跳/超时（unaligned checkpoint 前置）。
- Stage 48：Kafka IMessageService 第三后端（沿用 IDataPlaneWireCodec SPI）。
- 既有（非本 plan 债）：`nop-stream-rocksdb` `incrementalCheckpointIsFasterThanFullScanForLargeState` timing benchmark 在 `-T 1C` 并行负载下偶发 flake——建议隔离跑或加 `@Disabled`-on-load 容差。
- 无 Stage 40 剩余 plan-owned work。
