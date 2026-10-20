# C8 深挖第 2 轮 — nop-message（WI 4.1）

- 审计维度：C8（背压、流控与过载行为）· 深挖第 2 轮（合并批 C1/C3/C5/C6/C8 之 C8 分卷）
- 日期：2026-10-20
- 盲审声明：同 C1-round2.md。CL 线索统一流水续接（第 1 轮止于 CL-024，本轮 C3 分卷用 CL-025/CL-026，本文件用 CL-027）。CL-022 的 docs-bug 面（F-39 裁定 vs kafka-clients 实际行为）按指令不重复，仅作为本卷分析输入引用。

## 深挖范围与方法

按派发指令对第 1 轮 8 条之外的四个定向面复核：

| 定向面 | 复核对象 | 结论 |
|---|---|---|
| Pulsar 发送端 maxPendingMessages/blockIfQueueFull 语义 | 全模块 grep = 0 处装配（客户端缺省 1000/false → fail-fast）；fail-fast 拒绝面在 reply 路径的传导（`PulsarConsumeTask` waitAll :257/:284 → 整批 nack） | 拒绝→整批 nack→重投有 run-loop 退避兜底（1s→30s）且批量"无 per-message 归因"为 javadoc 已声明边界——语义面维持第 1 轮边界清单第 5 行结论；**配置面缺位归 C5-005 立案**，本卷不重复 |
| Kafka producer buffer.memory/max.block.ms 行为（CL-022 docs-bug 面不重复） | `KafkaProducerConfig.java:31-34`（32MB/60s 缺省）、`KafkaMessageService.sendAsync:273-289`（prod.send 同步段）、消费线程作为 producer 调用方的全部点 | CL-022 已覆盖"同步抛 vs callback"的 docs 面；本轮新立**消费线程承接 max.block.ms 阻塞的预算侵蚀面** → 立案 C8-009 |
| Debezium 内部队列（max.queue.size/max.batch.size）与 dispatchEvent 消费速度耦合 | `DebeziumEngineConfig:158-175`（队列键不在钉定族、可上调）、`DebeziumMessageSource.dispatchEvent:155-163`（串行 fan-out）、`DebeziumEngineWrapper.stop:206-212` | 内存验算维归 **C5-006 立案**；速度耦合的停止语义面（handler 挂死使 close 无法达成静默）→ **并入「扩展 C6-005」** |
| ChannelBacklogProbe 的积压探测语义 | `ChannelBacklogProbe.java:10-34`（接口 javadoc：best-effort、可被调用方缓存、"implementers must keep the query cheap"） | 模块内 0 实现 0 调用（实现方 nop-sys-dao、消费方 stream runtime 均集外）——nop-message 面无过载行为可审，examined-empty；探测精度/缓存 TTL 契约需实现方模块自审 |

生产-消费边界清单 7 条与已裁定边界 7 条（第 1 轮口径节）逐条重对：无新增自建队列、无信号量/限流新使用点、`QueueOverflowPolicy` 适用面结论不变（仍仅 Local 延迟队列一处，C8-003 已立案）。

## 新增发现

### [C8-009] 消费线程作为 Kafka producer 的调用方承接 max.block.ms 级同步阻塞——reply/DLQ 路径在发送缓冲耗尽时的批预算崩塌无隔离

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:273-289`（`prod.send(record, callback)` 同步段）；`nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaProducerConfig.java:31-34`（bufferMemory=32MB / maxBlockMs=60000 缺省）；消费线程调用点 `KafkaConsumeTask.java:385-390`（单条 reply）、`:537-541`（批量 reply）、`:689-695`（DLQ 发布，Terminate 端点与失败端点共用）
- **证据片段**:
  ```java
  // KafkaMessageService.sendAsync —— 消费线程内联执行
  try {
      prod.send(record, (metadata, exception) -> { ... });
      // kafka-clients：accumulator 满（buffer.memory 32MB 耗尽）时 RecordAccumulator
      // .append 以 maxTimeToBlockMs=60s 同步阻塞调用线程（CL-022 已证：满溢为同步抛
      // BufferExhaustedException，无论阻塞后成功还是超时，阻塞本身先行发生）
  // 消费线程调用点：KafkaConsumeTask 批量 reply（ctx.send = syncGet(sendAsync)）
  } else if (response instanceof Acknowledge) {
      ctx.send(service.getAckTopic(record.topic()), ...);   // 消费线程阻塞点
  ```
- **严重程度**: P2
- **并发场景**: 单一消费线程（持有 poll/max.poll.interval.ms 预算）作为共享 producer 的同步调用方 vs producer 内部 sender 线程（broker ack 慢/不可用时未投递记录堆积，32MB accumulator 渐满）。
- **触发时序**: T1 reply-topic/DLQ-topic 持续慢 ack（broker 退化、下游消费组停滞）→ producer in-flight + 未投递记录累积至 32MB 满 → T2 消费线程处理下一批，逐条 `ctx.send` → `prod.send` 内部在 accumulator 上**同步阻塞至多 60s/条**（max.block.ms 缺省）→ 500 条批的第一条即用尽 max.poll.interval.ms=300s 预算 → 心跳线程 LeaveGroup、分区移交 → rewind 重投 → T3 新一轮 poll 面对同样满载的 accumulator 重复 T2——组级 rebalance 风暴 + 批预算崩塌，与 C8-006（RTT 退化面）同症状但根因不同：C8-006 是逐条串行 RTT 累加，本条是**共享缓冲满溢的 60s 级单点阻塞**，即使批内仅 1 条 reply 也足以触发。
- **现状**: `max.block.ms`/`buffer.memory` 均可配（KafkaProducerConfig），但其取值与消费侧 `max.poll.interval.ms`（KafkaConsumerConfig，另一配置对象）**无联动声明**——javadoc（:48-53）只声明了 handler 耗时与 deferAfterBatch 的预算关系，未声明发送面阻塞消耗同一预算；DLQ 路径（:689-695）同样裸承该阻塞且失败端点 javadoc（:686-687）仅声明"每条 sync 发布计入 max.poll.interval.ms 预算"，未计及 buffer 满时的 60s 放大。
- **风险**: reply-topic 的过载传导为整组 rebalance 风暴（波及同组全部成员），且毒化是自持的（重投加重发送积压）；运维无法从现有配置面预见该阈值（决定量 = buffer.memory ÷ 消息大小 + ack 时延，跨两个配置对象）。
- **建议**: 消费线程的 reply/DLQ 发布改走带期限的入口——`sendAsync` 前 `producer` 侧配低 `max.block.ms`（或为消费线程路径提供独立 producer 实例与独立 buffer.memory）；最低成本方案：owner doc 声明三参数联动约束（`buffer.memory ÷ 平均消息大小 × ack 时延 ≪ max.poll.interval.ms`）并在文档校验清单给出演算式；与 C8-006 的"批量 reply 管道化"修复同批落地（waitAll 形态同时收窄单点阻塞暴露面）。
- **失效面枚举**（F3：消费线程调用 producer 的全部点逐一）: ① 单条 reply（:385/:389）✗ 阻塞面在位；② 批量 reply（:537/:540）✗（C8-006 串行面叠加）；③ DLQ 发布/Terminate 端点（:694）✗；④ 失败端点 DLQ（runFailureEndpoint:660 → publishToDeadLetterIfConfigured）✗；⑤ Pulsar 对照面 —— blockIfQueueFull=false 缺省 fail-fast，`sendAsync` 立即拒绝不阻塞消费线程 ✓（其 nack 重投放大面已由批量"无 per-message 归因"声明覆盖）；⑥ Local 对照 —— 无 broker 发送面，`ConsumeContext.send` 为同线程递归（无缓冲阻塞）✓。
- **信心水平**: 确定（max.block.ms 同步阻塞为 kafka-clients 既有行为，CL-022 的探针测试形态可直接复用定案）
- **误报排除**: 非 CL-022 重复——CL-022 裁定的是"满溢后走同步抛还是 callback"的 docs 事实，本条不依赖该分歧的任一结论：无论抛还是 callback 路由，**阻塞本身（至多 60s）都发生在抛出/回调之前**，消费线程预算侵蚀独立成立；非 C8-006 重复——C8-006 根因是逐条串行 RTT，本条根因是共享缓冲满溢的单点阻塞，触发条件与修复入口均不同。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（prod.send :274 同步段、bufferMemory=32MB/maxBlockMs=60s 缺省（KafkaProducerConfig.java:33-38）、消费线程调用点 :385-390/:537-541/:694 逐点回码确认；「阻塞独立于 CL-022 分歧成立」的论证核验通过——RecordAccumulator.append 先阻塞至 maxTimeToBlockMs 再抛/回调；与 C8-006 根因互异、修复同改点的判定成立）

## 扩展已立案条目（第 1 轮）

### 扩展 C8-005（Pulsar ConsumeLater 的无预算重投面重裁——轮 1 ✓ 判定范围修正）

第 1 轮 C8-005 失效面 ③④ 将 Pulsar `reconsumeLater`（单条 :336-338 / 批量 reconsumeLaterAsync :272-275）判为 ✓（"有网络往返…无纯自旋"）。该判定只排除了 CPU 纯自旋，未覆盖 C8-005 的核心主张"无预算闭环"：`ConsumeLater` 非失败信号，`failureAttempts` 不计数、WI22 端点不可达、run-loop 退避不升级（`dispatched=true` 被置位）→ handler 持续返回 `ConsumeLater(0)` 时消息以 broker 往返频率无限弹跳（每轮：receive → handler → reconsumeLater(0) → broker 重投），CPU/网络/broker 负载持续燃烧且**无任何机制退出或告警**。与 Kafka 面（seek 本地缓冲，P2）、Local 面（堆驻留 + 调度线程自旋，C5-003 已立）构成三后端同根族——Pulsar 侧后果最轻（无堆驻留、无 HOL），随 C8-005 计不另编号，其修复（最小生效延迟下限 + 连续 defer 计数）应三后端一并落地。

### 扩展 C8-008（Pulsar 单条失败计数的失效面修正——轮 1 ③ ✓ → ✗）

第 1 轮 C8-008 失效面 ③ 记"Pulsar 单条（:334/:370）✓"。本轮复核发现 `consume()` 的 catch（:361-381）中，`isPoisoned(attempt)` 命中时走 `runFailureEndpoint`（:366-368）后 **`return true`，位于 :370 `incrementFailed()` 之前**——毒消息终结（失败预算耗尽的最高信号价值事件）恰恰不进 `failed` 计数；对照 Kafka 单条路径 :396-399 的 `incrementFailed()` 在 isPoisoned 判定**之前**执行 ✓。两后端口径漂移方向恰好相反于直觉（罕见重事件漏计），加剧 C8-008 已立的"过载不可见"面；修复随 C8-008 的指标补齐一并处理（Pulsar :366 分支补 `incrementFailed`）。

### 扩展 C6-005（dispatchEvent 消费速度耦合的停止语义面）

`DebeziumMessageSource.dispatchEvent:155-163` 串行同步 fan-out：订阅者 handler 在引擎 worker 线程内联执行且无等待上界——handler 永久挂死（同步 RPC 无超时、锁死锁）时 `dispatchEvent` 不返回 → `engine.run()` 停留在用户回调内 → `DebeziumEngineWrapper.stop()` 的 `engine.close()`（:206-212）只能置停止标志、**无法中断已进入用户代码的回调** → stop() 返回但 worker 线程与 binlog 连接无限期滞留（C6-005 的"不 join"在此场景下从"收尾窗口缺口"放大为"停止不可达成"）。与 C6-005(a)(b) 同根（缺 quiesce/join 机制），随其计；修复形态收敛：start 记录 worker Future，stop 后限时 join + 超时升级为可观测的 stuck WARN（对齐 Kafka/Pulsar cancel 的 `consume-thread-stuck` 形态）。

## 维度集外线索（overlay）

### [nop-message-CL-027]（C8）Kafka 单条 ConsumeLater 端点的批端点残留未声明——批路径 F-08 javadoc 的同族声明缺单条副本

`KafkaConsumeTask.java:360-369`：单条路径 ConsumeLater 直接 `return dispatched`，跳过尾部 `commitSync()`（:421-425）——同 poll 轮内**早于**延迟记录的已派发/已 reply 记录不提交，进程内凭 consumer 位置继续（不重投），但 restart/rebalance 后从已提交位点重投并**重复 reply**。批路径 javadoc（:471-476，F-08）显式声明了同一残留（"Records replied BEFORE the break point may still be re-delivered and re-replied... unavoidable at-least-once residue"），单条路径的 MSG-F-09 注释（:361-366）只声明了"端点后不派发"，未声明端点**前**记录的重 reply 面。at-least-once 内合法，纯声明缺口；建议在 :360-369 注释补一行指向 F-08 同族语义即可。

## 继续判定

**终止信号。** 依据：本轮新增仅 C8-009（P2×1，根因单一——消费线程与共享 producer 缓冲之间无预算隔离声明，修复与 C8-006 收敛于同一"reply 管道化/期限化"改点）；三项扩展均为对第 1 轮条目的失效面修正或同根并入，无新增独立根因；四个指令面中 Pulsar 发送端语义、ChannelBacklogProbe 语义两面维持第 1 轮已裁定边界/examined-empty，Debezium 队列耦合已分拆归位（内存维 C5-006、停止维扩展 C6-005）。第 1 轮 8 条 + 本轮 1 条 + 3 项扩展后，生产-消费边界 7 条两遍覆盖，无 P0/P1 新增且各新面均收敛到既有修复改点，无需第 3 轮。

## 复核结论（WI 4.1 独立复核，2026-10-20）

- **C8-009 复核保留（P2）**：状态行已写于该条目末尾。本轮终止信号判定成立——新增仅 1 条 P2、四指令面两面维持边界/examined-empty、Debezium 面已分拆归位，C8 维度两轮覆盖可收口的裁定成立。
- 三项扩展逐项核验通过：① 扩展 C8-005（Pulsar reconsumeLater 无预算弹跳随主条目计）——轮 1 失效面 ③④ ✓ 判定范围修正的理由成立（「无网络往返」只排除纯自旋，未覆盖预算主张），吸收正确；② 扩展 C8-008（Pulsar 毒消息 endpoint :366-368 return 先于 :370 incrementFailed）——回码确认，随 C8-008 修复；③ 扩展 C6-005（dispatchEvent 挂死使 stop 静默不可达成）——同根并入正确，与 C6 卷 quiesce/join 修复收敛于同一改点。
- CL-027（单条 ConsumeLater 端点批残留声明缺口，at-least-once 内合法纯声明缺口）核验成立：`:360-369` 无 commitSync、批路径 F-08 javadoc :471-476 在位，处置（补一行注释指向 F-08 同族语义）适当。
- 本维度复核全量结论（判定统计/跨单元对照/最终保留项）见 `C8-backpressure.md`「维度复核结论」节：9 条 = 保留 8（含 C8-001 标注并入 C3-001 主登记）+ 降级 1（C8-001 P1→P2 同案对齐），零驳回，最终 P1×2/P2×6/P3×1。
