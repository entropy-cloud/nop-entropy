# sys-event 架构设计

**日期**：2026-07-07（更新于 2026-07-09）
**范围**：`nop-sys` 的事件存储与消费模型，涉及 `nop-sys.orm.xml`、`SysDaoMessageService`、广播事件 `findNext` 时间窗口消费、普通事件分区并行消费
**状态**：active baseline
**关联**：`../nop-job/worker-assignment-design.md`、`../../docs-for-ai/03-modules/nop-sys.md`、`../../docs-for-ai/03-modules/nop-batch.md`

---

## 一、设计结论

1. `sys-event` 必须拆成两条独立链路，且广播与普通事件的消费逻辑应拆分到各自独立的处理器类中：
   - **广播事件**：append-only 事件流表 + `findNext` keyset pagination + 内存游标 + 时间窗口过滤。不持久化消费进度，不做分布式锁。
   - **普通事件**：共享队列 + `partitionIndex` 分区归属 + lease + 状态迁移。存储与状态跟踪按"共享队列 + 分区串行"建模。

2. 广播事件与普通事件**不能继续共用 `nop_sys_event` 一张表作为唯一真源**。广播事件需要独立的 append-only 事件流表。

3. 广播事件采用 **outbox + `findNext` 时间窗口** 模型：
   - 事件流表只追加，不在成功消费后修改事件行。
   - 每台服务器在内存中维护上次处理的最后一条实体，通过 `IEntityDao.findNext(lastEntity, filter, null, limit)` 做 keyset pagination，按 PK (`eventId`) 单调推进。
   - 过滤条件限定时间窗口：`eventTime >= startTime AND eventTime <= now`，其中 `startTime = clock.getMinCurrentTimeMillis() - startGap`。
   - 重启后内存游标丢失，从时间窗口起点重新消费，业务自行处理重复。
   - 消费失败不阻塞后续事件，记录错误日志后跳过。

4. 普通事件保留共享队列语义，但显式提升 `partitionIndex` 为第一等公民：
   - 发布时稳定计算 `partitionIndex`，同一业务键必须稳定落同一分区。
   - 消费时按 `partitionIndex` 切分 worker 负责范围，**分区内严格串行，分区间允许并行**。
   - 普通事件不追求全局顺序，只追求"同一分区内顺序"。

5. 广播消费处理器（`BroadcastEventProcessor`）与普通事件消费处理器应各自独立成类，`SysDaoMessageService` 作为 `IMessageService` 门面，负责发送路由、订阅注册和生命周期管理，将两类消费逻辑委托给各自的处理器。

6. `sys-event` 对 `nop-batch` 的正确复用边界是：**`nop-sys-dao` 只提供事件存储与触发原语，普通事件的 batch 扫描触发器放在反向依赖 `nop-sys` 的扩展模块中**：
   - **复用**：`StringHelper.shortHash`、`IntRangeBean.shortRange()`、`IntRangeSet`、`WeightedPartitionAssigner`、`PartitionDispatchLoaderProvider/PartitionDispatchQueue`，以及通过 batch trigger 周期触发一次扫描的运行模式。
   - **边界**：`nop-sys-dao` 内不直接依赖 `nop-batch`；batch 只作为外部触发执行机制存在，event row 状态机、lease、retry/reschedule 仍保留在 `sys-event` 自己的存储与处理原语中。

7. 这次重构的目标不是引入 Kafka 级别的外部 MQ，而是在现有数据库事务发布模型上，把**广播实用性**和**普通事件并行性**补成两个清晰的本地基线。

---

## 二、背景与动机

### 2.1 当前 live baseline（2026-07-09）

- `SysDaoMessageService.sendAsync()` / `sendMultiAsync()` 按 topic 语义分流：广播消息（`bro-` 前缀）写 `NopSysBroadcastEvent`，普通消息写 `NopSysEvent`。
- 广播消费 `processBroadcastEvent()` 当前围绕 `NopSysBroadcastCursor` 做 subscriber cursor + lease。这一设计在实际使用中存在根本性语义错配（见 2.2），本设计裁定改为 `findNext` + 时间窗口 + 内存游标模型。
- 普通事件消费 `processNonBroadcastEvent()` 已具备 partition 扫描 + lease claim + `ConsumeLater` 重排队的基本语义。
- 广播与普通事件消费逻辑当前混在 `SysDaoMessageService` 一个 722 行的类中，本设计裁定拆分为独立的处理器类。

### 2.2 广播 cursor 模型为什么不够

当前广播消费围绕 `NopSysBroadcastCursor`（subscriberId + topic → lastConsumedEventId + lease）建模。这一设计存在三个根本性问题：

#### 问题一：广播场景的 subscriberId 无法稳定绑定到"每台服务器"

广播的核心用例是**缓存失效、状态同步**等 fan-out 通知：每台服务器都需要独立处理每条广播事件。这意味着每台服务器需要自己的消费进度。

- 如果 `subscriberId` 绑定到 `AppConfig.hostId()`，由于 `hostId()` 在未配置时为随机 UUID（每次启动不同），重启后会产生全新游标，`lastConsumedEventId=null`，从 eventId=0 全量回放历史事件——对临时性广播事件（缓存失效）完全无意义。
- 如果 `subscriberId` 固定为业务名称（如 `subscribeName="cache-refresher"`），则多台服务器共享一个游标，退化成竞争消费，只有一台机器处理，不是真正的广播。
- 即使 `subscriberId` 绑定到稳定的实例标识（如配置的 pod name），持久化 cursor 也会积累垃圾记录（每次部署变更都可能留下无用游标），且对缓存失效这类 ephemeral 语义来说，持久化进度是过度设计。

#### 问题二：eventId-based cursor 假设从头消费

持久化 cursor 以 `lastConsumedEventId` 单调推进，新 cursor 从 `eventId=0` 开始。但广播事件的典型场景不需要历史回放——服务器重启后只需要"启动时间附近"的事件，更老的事件早已过期（缓存已被后续失效覆盖，或服务启动时全量加载）。

#### 问题三：lease + 失败阻塞与广播语义冲突

当前设计中，subscriber 处理失败时 cursor 不推进，后续事件被阻塞。但广播事件（尤其是缓存失效）是 fire-and-forget 语义——一条失效失败不应阻塞后续失效，否则会导致缓存一致性问题扩大化。

### 2.3 普通事件侧的缺口

当前普通事件语义有两个缺口：

1. **并发模型过弱**：只有简单轮询 + optimistic update，没有"分区归属、worker 负责范围、分区内串行"的显式模型。
2. **`partitionIndex` 语义未闭合**：发布端有时按 biz key hash 写入，有时随机写入；消费端又不按 partition range 拉取，导致字段存在但不构成契约。

---

## 三、核心设计

### 3.1 统一原则：发布仍然走数据库事务 outbox

两类事件都保留当前最有价值的性质：**事件写入与业务数据更新在同一数据库事务中提交**。这也是 `SysDaoMessageService` 相比外部 MQ 的核心工程价值。

因此重构只改变“落库后的存储模型和消费模型”，不改变“业务侧调用 `IMessageService` 进行同事务发布”的总路线。

### 3.2 广播事件：append-only 事件流 + `findNext` 时间窗口消费

#### 3.2.1 数据模型

广播只需要一类持久化对象：

1. **Broadcast Event Stream**（`NopSysBroadcastEvent`）
   - 一行代表一次已经提交的广播事件。
   - 关键语义字段：`eventId`（PK，自增）、`eventTopic`、`eventTime`、`eventName`、`eventHeaders`、`eventData`、`bizKey`、`bizObjName`、`bizDate`。
   - 行只追加，除 TTL 清理外不更新状态。

**不再需要 `NopSysBroadcastCursor` 表。** 广播消费进度不持久化到数据库，而是通过 `IEntityDao.findNext()` 的 keyset pagination 在内存中维护。
#### 3.2.2 `findNext` keyset pagination 机制

`IEntityDao.findNext(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit)` 是 DAO 层提供的 keyset pagination 方法：

- **`lastEntity`**：上一批处理的最后一条实体，作为游标。`null` 时从头开始。
- **filter**：附加 WHERE 条件（如 `eventTopic = ?`、`eventTime >= ?`）。
- **orderBy**：排序字段（PK 列总是被追加，保证全序）。
- 对于单主键实体（`NopSysBroadcastEvent` 的 PK 为 `eventId`），生成的 SQL 为：

```sql
SELECT o FROM NopSysBroadcastEvent o
WHERE o.eventTopic = ?
  AND o.eventTime >= ?     -- startTime
  AND o.eventTime <= ?     -- now (maxEstimatedTime)
  AND o.eventId > ?        -- lastEntity.eventId
ORDER BY o.eventId
LIMIT ?
```

- `lastEntity = null` 时，`eventId > ?` 条件不生成，从 filter 匹配的第一条开始。
- 这不是 OFFSET 分页，没有深翻页性能退化问题。

#### 3.2.3 消费模型与时间窗口

每台服务器独立运行广播消费，不做分布式协调：

```text
on startup:
    startTime = clock.getMinCurrentTimeMillis() - startGap
    lastEvent[topic] = null   // 内存游标，重启后重置

each cycle:
    for each broadcast topic:
        filter = eventTopic = topic
               AND eventTime >= startTime
               AND eventTime <= clock.getMaxCurrentTimeMillis()
        batch = dao.findNext(lastEvent[topic], filter, null, fetchSize)

        for event in batch:
            try:
                invoke listener(event)
            catch Exception:
                log error, do NOT block

        if batch not empty:
            lastEvent[topic] = batch.last()
```

关键设计决策：

1. **内存游标**：`lastEvent[topic]` 仅存在于 JVM 内存中，防止运行期间重复消费同一批事件。重启后丢失，从时间窗口起点重新消费。
2. **时间窗口下限**（`startTime`）：启动时设定为 `clock.getMinCurrentTimeMillis() - startGap`。`startGap`（默认 5 秒）补偿应用与数据库之间的时钟偏差，确保不遗漏启动前刚提交的事件。重启后自然跳过历史事件。
3. **时间窗口上限**（`clock.getMaxCurrentTimeMillis()`）：只消费已提交的事件，避免读到未完成事务的脏数据。
4. **PK 排序**：`eventId` 自增，与插入顺序一致，近似等于 `eventTime` 顺序。无需额外指定 orderBy。
5. **失败不阻塞**：消费异常时记录日志后继续处理下一条事件，不暂停、不重试、不阻塞后续事件。广播事件的 fire-and-forget 语义优先于单条事件的可靠投递。
6. **业务幂等**：重启后时间窗口内的事件可能被重复消费，业务 listener 必须容忍重复（如缓存失效天然幂等）。

#### 3.2.4 为什么不持久化消费进度

广播事件的核心用例（缓存失效、状态同步）具有以下特征：

- **临时性**：一条缓存失效事件在产生后很快失去意义——后续的失效会覆盖它，或缓存条目自然过期。
- **全量重建**：服务器启动时通常全量加载状态（如缓存预热），不需要回放历史广播事件。
- **幂等性**：重复消费同一条广播事件不会产生错误结果（缓存多失效一次无副作用）。

因此持久化消费进度（cursor 表）带来的复杂性远大于其价值：
- 不需要"不遗漏历史"的保证，因为历史事件本身无意义。
- 不需要分布式锁，因为每台服务器独立消费。
- 不需要 cursor 表的运维和清理。

系统提供的语义是 **at-least-once + 时间窗口**：重启后可能重复消费窗口内事件，但绝不会遗漏启动后产生的新事件。

#### 3.2.5 与"可靠顺序广播"的关系

本设计明确放弃"持久化 subscriber cursor 保证严格不遗漏"的广播语义。如果业务场景确实需要：
- **跨重启不遗漏任何历史事件**——应使用专业 MQ（Kafka 等），而不是数据库 outbox。
- **严格顺序消费**——广播事件按 `eventId` 顺序投递，但失败不阻塞、不重试，因此不保证严格顺序处理成功。

数据库 outbox 广播的定位是**轻量级 fan-out 通知**，不是替代 MQ 的可靠消息总线。

#### 3.2.6 处理器拆分

广播消费逻辑应从 `SysDaoMessageService` 中独立为 `BroadcastEventProcessor` 类。拆分动机：

- 广播与非广播在状态管理、并发控制、失败处理上几乎没有共用逻辑，仅共享基础设施（daoProvider、timer、hostId）。
- 当前 `SysDaoMessageService` 已达 722 行，两类逻辑混在一起降低可读性和可测试性。
- 拆分后 `SysDaoMessageService` 作为 `IMessageService` 门面，负责发送路由（`bro-` 前缀 → 广播）、订阅注册和生命周期管理，将消费委托给各自的处理器。

### 3.3 普通事件：共享队列 + 分区串行

#### 3.3.1 数据模型

普通事件保留“单行代表单条待处理消息”的队列模型，但从语义上把它明确成 **Simple Event Queue**。关键字段包括：

- `eventId`
- `topic`
- `partitionIndex`
- `eventStatus`（WAITING / CLAIMED / PROCESSED / FAILED）
- `scheduleTime`
- `processTime`
- `retryTimes`
- `leaseOwner`
- `leaseExpireTime`

与广播的关键区别：

- 广播需要多个订阅者各自的 cursor，因此“事件流”和“订阅进度”分表。
- 普通事件只需要一个 consumer group 处理一次，因此**消息行本身就可以承载处理状态**。

#### 3.3.2 `partitionIndex` 的契约

`partitionIndex` 必须从“可选字段”提升为强契约：

1. 同一业务顺序键必须稳定映射到同一 `partitionIndex`。
2. 同一 `partitionIndex` 内事件必须按 `eventId` 或 `processTime,eventId` 串行处理。
3. 分区数量固定在 short-hash 值域 `[0, 32766]`，与 `nop-job` / `nop-batch` 的现有约定保持一致。

发布规则裁定：

- 如果消息带 `bizObjName + bizKey`，则 `partitionIndex = StringHelper.shortHash(bizObjName + '|' + bizKey)`。
- 如果消息声明了更明确的顺序键，则优先使用该顺序键。
- **禁止再用随机值作为普通事件默认分区策略**，因为随机值会打散同键顺序。
- 没有任何顺序键的消息才允许退化为 topic 级 hash 或固定缺省分区，但这类消息同时也意味着“不要求同键顺序”。

#### 3.3.3 消费模型

普通事件的 worker 分配直接复用 `nop-job` 已经论证过的分区分配基线：

- 全量值域：`IntRangeBean.shortRange()`
- 集群切分：`WeightedPartitionAssigner`
- worker 持有：`IntRangeSet`
- 查询过滤：`partitionIndex BETWEEN range.offset AND range.getLast()`

也就是：

```text
coordinator / resolver:
    assign IntRangeSet to each event worker

worker scan:
    fetch WAITING events
    where partitionIndex in myRange
      and scheduleTime <= now
    order by processTime asc, eventId asc

for each partition:
    only one in-flight event at a time
```

核心约束不是“一个 worker 只拿一个分区”，而是：

- **一个分区同一时刻只能有一个 active consumer**
- **一个 worker 可以持有多个分区范围**
- **不同分区可以并行**

#### 3.3.4 分区内顺序如何保证

普通事件不要求全局 FIFO，只要求分区内 FIFO。这里应直接对齐 `nop-batch` 的成熟做法：

1. **先做 partition dispatch，再做 chunk/process**。`nop-batch` 的 `PartitionDispatchLoaderProvider` / `AsyncFetchPartitionDispatchLoaderProvider` 会先按 `partitionFn` 把记录拆到多个 `PartitionDispatchQueue.PartitionQueue`，然后保证“相同 partition 的记录不会同时由两个线程处理”。
2. **同一 partition 的顺序保证来自 worker 内微队列，不来自 event row `CLAIMED`**。`PartitionDispatchQueue` 用 `threadId + queue` 维护 partition 归属，只有当前负责该 partition 的线程会继续消费它；同一 partition 可以在不同时间切换线程，但不会并发执行。
3. **`sys-event` 只应复用这层执行模式，不应照搬 batch chunk 语义**。也就是说，普通事件应把“同一 partition 只能顺序执行”建模成执行器行为，而不是数据库里额外新增 `CLAIMED` 状态或 partition 占用表。

因此普通事件的实现约束应修正为：

1. 抢占任务时必须先按 `assignedPartitions` 过滤，再把候选事件派发到 partition 微队列。
2. 若某条事件处理失败，平台层默认将其改写为未来 `scheduleTime` / `retryTimes` 后重新入队，而不是在执行器层引入 partition head blocking。
3. worker 侧的并行度单位应是“分区”，而不是“单事件”或“数据库 claim 行”。

伪代码：

```text
for each owned partition P in parallel:
    loop:
        claim earliest WAITING event in partition P
        process it
        if success:
            mark PROCESSED
        else if retryable:
            reschedule same row
        else:
            mark FAILED or reschedule by business policy
```

这本质上是“每个 partition 上的消息优先落到同一 worker 微队列中执行”，执行模式应借鉴 `nop-batch` 的 `PartitionDispatchQueue`；但失败后的是否延后、是否放弃、是否补偿由消息处理逻辑自己决定，而不是平台层强制 head blocking。

### 3.4 与 nop-batch 的关系：普通事件可借 batch 外壳，但确认语义仍归事件层

#### 3.4.1 可以直接复用的部分

`nop-batch` 已经沉淀出的、与 `sys-event` 高度同构的能力有：

1. `partitionIndexField` + `partitionRange` 的概念模型
2. `StringHelper.shortHash` 对业务键稳定映射到 short-range
3. `IntRangeBean` / `IntRangeSet` 的表达与序列化
4. `WeightedPartitionAssigner` 的按权重切分
5. ORM/JDBC loader 根据 `partitionRange` 自动加 `BETWEEN` 过滤的约定
6. `PartitionDispatchLoaderProvider` / `AsyncFetchPartitionDispatchLoaderProvider` 这类“partition 先派发、队列内顺序执行”的执行模式
7. 通过 `nop-job` 或 `IScheduledExecutor` 把一次扫描周期包装成可重复触发执行的外层调度方式

这些都应该直接复用，避免重复发明“分区值域、range 格式、worker 切分算法、周期扫描调度壳”。

#### 3.4.2 不应直接复用的部分

`nop-batch` 中不适合直接搬到 `sys-event` 的，不是“批量抓取”这种优化手法，也不是“定时触发一次扫描”这种外层壳，而是把 **chunk completion 当作正确性边界** 的那部分语义：

1. **Chunk completion 不是唯一事件确认边界**：
   - 普通事件的运行时结果应落到每条 event row 的状态更新上，例如成功改 `PROCESSED`，失败写 `retryTime` / `retryTimes` 后等待下轮扫描。
   - 广播事件不跟踪逐条确认状态（fire-and-forget），因此 chunk completion 对广播完全没有意义。
   - 因此一个 chunk 可以作为批量 flush / 批量提交的优化单位，但不能只记录 chunk 成败而丢掉逐条 event outcome。
2. **Processor/Consumer DSL 不是事件 listener 契约本身**：普通事件可以在实现层被包装成"扫描一批待处理事件的 batch"，但 listener 注册、topic 路由、retry 语义仍应由 `sys-event` 自己定义，而不是暴露成 batch DSL。
3. **Batch task / record result 不是必要中心模型**：普通事件不应直接复用 `NopBatchTask` / `NopBatchRecordResult` 作为运行时真源；这些对象围绕"任务实例 + chunk 结果"组织，而普通事件是常驻共享队列。
4. **saveState / completedIndex 恢复不是事件恢复主模型**：广播恢复通过时间窗口 + `findNext` 内存游标实现，普通事件恢复通过队列状态与重试字段，而不是 batch 的 completedIndex。
5. **不直接复用 `nop-task` 承载整个 sys-event 生命周期**：可以用 `nop-job` 或 `IScheduledExecutor` 周期拉起一次普通事件扫描，但不应把 `sys-event` 整体重定义成长期运行的 batch task graph。

#### 3.4.3 可以复用的执行优化

下面这些 chunk-like 能力对 `sys-event` 是有价值的，可以在不引入 batch 语义耦合的前提下复用或抽象出来：

1. **Windowed fetch / 一次抓取 N 条**：减少高频轮询和单条查询开销。
2. **Bounded session reuse**：在单次扫描窗口、单次预取批次或单分区短周期内复用 session 与一级缓存，但不能把 session 无限拉长为常驻状态。
3. **批量时间窗口扫描**：围绕 `scheduleTime <= now` 做批量拉取与排序，降低时间条件扫描成本。
4. **小批顺序执行 helper**：允许实现层复用通用的小批执行器，但 ack/cursor 推进仍必须回到 event / partition prefix 语义。

#### 3.4.4 设计裁定

因此裁定为：

- **普通事件可以实现成“周期调度的 batch 扫描器”**，外层由 `nop-job` 或 `IScheduledExecutor.scheduleWithFixedDelay()` 触发一轮扫描。
- **应直接借鉴 `nop-batch` 已证明可行的 partition dispatch + 微队列顺序执行模式**。
- **可以复用执行 helper，也可以把一批 event 的状态更新做成 chunk flush，但不能只剩 chunk 成败、丢掉逐条 event outcome。**
- **抽取或共用“分区基础设施”“周期扫描壳”和“partition dispatch 执行模式”即可，事件层自己保有消息语义、重试语义和监听器模型。**

如果后续代码层要复用实现，优先抽象以下轻量接口，而不是复用整个 batch runtime：

- `IPartitionRangeResolver`
- `IPartitionAssignmentProvider`
- `IPartitionedScannerQueryBuilder`
- `IWindowedEventFetcher`
- `IBoundedSessionExecutor`

### 3.5 广播与普通事件的表设计边界

最终形成两类表，而不是"一张事件表打所有语义补丁"：

1. **普通事件表**（`NopSysEvent`）
   - 面向 shared queue
   - 主关注点：分区、lease、重试、状态迁移

2. **广播事件流表**（`NopSysBroadcastEvent`）
   - 面向 append-only event stream
   - 主关注点：顺序扫描、TTL、payload 保存

**不再需要广播订阅游标表。** 广播消费进度通过 `findNext` 的内存游标维护，重启后从时间窗口起点重新消费，业务自行处理重复。

### 3.6 失败、重试与阻塞策略

#### 广播事件

- 消费失败**不阻塞**后续事件。
- 单条事件处理异常时记录错误日志后跳过，立即继续处理下一条。
- 不重试、不暂停、不阻塞——广播事件的 fire-and-forget 语义优先于单条事件的可靠投递。
- 业务 listener 如需更强保证，应自行实现幂等和补偿逻辑。

#### 普通事件

- 某条事件失败后，默认通过更新 `scheduleTime + retryTimes` 延迟重试，而不是在平台层引入 partition head blocking。
- 业务如果确实要求"后续消息不能越过前序失败消息"，应在 listener / 业务状态机层自行判定并返回重试，而不是把该约束固化到基础设施层。
- lease 字段仍需要补齐，避免 worker 崩溃后长期悬空。

### 3.7 迁移约束

迁移需要遵守以下硬约束：

1. 已有同事务发布能力不能丢。
2. 广播消费进度不持久化到数据库；通过 `findNext` 内存游标 + 时间窗口维护。
3. 普通事件发布端必须停止随机分区默认值。
4. 普通事件 worker 需要明确的 partition ownership；当前最小 live baseline 通过 `assignedPartitions + row lease + partition scan` 落地，而不是额外的 ownership 表。
5. 文档与代码都要明确：系统提供的是**至少一次**语义，业务 listener 如需抗重复必须做幂等。
6. 广播消费逻辑应独立为 `BroadcastEventProcessor` 类，与普通事件处理器分离。

---

## 四、拒绝了什么

1. **继续共用一张 `nop_sys_event` 表，只是在 `isBroadcast` 分支上继续打补丁**：这会把"共享队列"和"广播事件流"两种根本不同的状态机继续揉在一起。

2. **广播直接按每实例 fan-out 为多条普通事件**：这会让广播事件数量与实例数线性膨胀，并把消费进度隐藏成消息复制，扩缩容和实例漂移时更难校正。

3. **广播持久化 subscriber cursor + lease 模型**：`hostId()` 在未配置时为随机 UUID，无法稳定绑定到每台服务器；固定 subscriberId 又退化为竞争消费。持久化 cursor 对缓存失效等临时性事件是过度设计，且 cursor 表会积累垃圾记录。已改为 `findNext` 内存游标 + 时间窗口。

4. **广播消费失败时阻塞后续事件**：广播事件是 fire-and-forget 语义，一条失败不应阻塞后续事件。阻塞会导致缓存一致性问题扩大化。

5. **普通事件继续随机写 `partitionIndex`**：随机分布提升了均衡性，但破坏了同键顺序，不能作为默认策略。

6. **直接把事件确认语义简化成"只看 chunk 成败"**：普通事件可以借 batch 的扫描与分区执行外壳，但必须保留逐条 event outcome；不能因为外层长得像 batch，就只剩 chunk runtime 状态。

7. **在平台层引入 partition head blocking 作为默认机制**：这会把失败重试、补偿和业务乱序容忍度固化到基础设施层，反而放大复杂度。默认策略应是处理失败后改写 `scheduleTime` 重新投递，由业务逻辑自己决定如何保证幂等和顺序。

8. **把目标做成恰好一次**：在当前数据库 outbox + listener 回调模型下，exactly-once 成本过高，也不符合平台其他模块的默认语义。owner-doc 明确坚持 at-least-once + 幂等 listener。

---

## 五、与已有设计的关系

- 与 `docs-for-ai/03-modules/nop-sys.md` 的关系：后者描述使用者视角的 `nop-sys` 能力；本设计补的是平台开发侧的广播消费模型（`findNext` + 时间窗口）与普通事件并发语义基线。
- 与 `docs-for-ai/03-modules/nop-batch.md` 的关系：`sys-event` 中普通事件可借用 batch 的周期扫描外壳、partition range 约定、worker 切分思想以及 chunk flush 优化，但不把事件结果简化成只看 chunk 成败。
- 与 `ai-dev/design/nop-job/worker-assignment-design.md` 的关系：普通事件的 worker ownership、`IntRangeSet`、`WeightedPartitionAssigner`、short-range 值域直接沿用该文档已经论证过的分区基线，但失败后默认是重设调度时间再次扫描，而不是在基础设施层固化 head blocking。
- 与 `IEntityDao.findNext()` 的关系：广播消费复用 DAO 层的 keyset pagination 能力（`findNext(lastEntity, filter, orderBy, limit)`），不重新实现分页逻辑。该方法对单主键实体生成 `WHERE pk > lastEntity.pk ORDER BY pk LIMIT N`，`lastEntity = null` 时从头开始。
