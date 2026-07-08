# sys-event 架构设计

**日期**：2026-07-07
**范围**：`nop-sys` 的事件存储与消费模型，涉及 `nop-sys.orm.xml`、`SysDaoMessageService`、广播订阅方状态持久化、普通事件分区并行消费
**状态**：active baseline
**关联**：`../nop-job/worker-assignment-design.md`、`../../docs-for-ai/03-modules/nop-sys.md`、`../../docs-for-ai/03-modules/nop-batch.md`

---

## 一、设计结论

1. `sys-event` 必须拆成两条独立链路：
   - **广播事件**：一个事件会被多个订阅实例各自消费，存储与状态跟踪必须按“事件流 + 订阅游标”建模。
   - **普通事件**：一个事件只应被某一个 consumer group 中的一个 worker 处理，存储与状态跟踪必须按“共享队列 + 分区串行”建模。

2. 广播事件与普通事件**不能继续共用 `nop_sys_event` 一张表作为唯一真源**。当前 `isBroadcast` 只是在同表上分支处理，无法给广播语义提供独立的可靠性约束，也迫使普通事件扫描与广播扫描共享索引和字段语义。

3. 广播事件采用 **outbox + subscriber cursor** 模型：
   - 事件流表只追加，不在成功消费后修改事件行。
   - 每个广播订阅者持久化自己的消费游标与 lease。
   - “按顺序执行且不遗漏”的语义绑定到“单个 subscriber 对单个 topic 分区按 eventId 串行推进 cursor”。

4. 普通事件保留共享队列语义，但显式提升 `partitionIndex` 为第一等公民：
   - 发布时稳定计算 `partitionIndex`，同一业务键必须稳定落同一分区。
   - 消费时按 `partitionIndex` 切分 worker 负责范围，**分区内严格串行，分区间允许并行**。
   - 普通事件不追求全局顺序，只追求“同一分区内顺序”。

5. `sys-event` 对 `nop-batch` 的正确复用边界是：**`nop-sys-dao` 只提供事件存储与触发原语，普通事件的 batch 扫描触发器放在反向依赖 `nop-sys` 的扩展模块中**：
   - **复用**：`StringHelper.shortHash`、`IntRangeBean.shortRange()`、`IntRangeSet`、`WeightedPartitionAssigner`、`PartitionDispatchLoaderProvider/PartitionDispatchQueue`，以及通过 batch trigger 周期触发一次扫描的运行模式。
   - **边界**：`nop-sys-dao` 内不直接依赖 `nop-batch`；batch 只作为外部触发执行机制存在，event row 状态机、lease、retry/reschedule 仍保留在 `sys-event` 自己的存储与处理原语中。

6. 这次重构的目标不是引入 Kafka 级别的外部 MQ，而是在现有数据库事务发布模型上，把**广播可靠性**和**普通事件并行性**补成两个清晰的本地基线。

---

## 二、背景与动机

### 2.1 当前 live baseline（2026-07-08）

- `SysDaoMessageService.sendAsync()` / `sendMultiAsync()` 现在按 topic 语义分流：广播消息写 `NopSysBroadcastEvent`，普通消息写 `NopSysEvent`。
- `NopSysEvent` 目前仍是普通事件队列表，但 row-level `CLAIMED` 并不是目标架构；普通事件的最终目标语义应回到 partition ownership + worker 内微队列顺序执行，而不是把“正在处理”状态下沉到 event row。
- 广播消费 `processBroadcastEvent()` 现在围绕 `NopSysBroadcastCursor` 做 subscriber cursor + lease，重启恢复真源来自持久化 `lastConsumedEventId`，不再依赖 JVM 内存游标。
- 普通事件消费 `processNonBroadcastEvent()` 当前实现仍在向目标语义收敛中；设计基线要求它最终对齐 `nop-batch` 的 partition dispatch 模式：先决定 worker 负责的 `IntRangeSet`，再在 worker 内按 partition 微队列顺序执行，不直接把 partition ownership 建模成 event row `CLAIMED`。
- focused regression tests 已覆盖发送路由、稳定分区键、广播 cursor 推进/失败阻塞，以及普通事件失败后通过 `ConsumeLater` 重排队的基线。

### 2.2 当前设计为什么不够

#### 广播侧

当前广播语义存在三个结构性缺口：

1. **游标不持久化**：`lastBroadcastEvent` 是 JVM 内存状态，不是系统真源。进程重启、扩缩容、部署漂移后无法证明“不遗漏”。
2. **多实例重复/跳过边界模糊**：当前每个实例都各扫一遍广播表，但没有“订阅者身份 + 游标 + lease”的持久协作模型。
3. **事件行被复用为消费状态**：广播本质是“一条事件被多个订阅者独立推进”，单行 `eventStatus` 无法表达多订阅者进度。

#### 普通事件侧

当前普通事件语义也有两个缺口：

1. **并发模型过弱**：只有简单轮询 + optimistic update，没有“分区归属、worker 负责范围、分区内串行”的显式模型。
2. **`partitionIndex` 语义未闭合**：发布端有时按 biz key hash 写入，有时随机写入；消费端又不按 partition range 拉取，导致字段存在但不构成契约。

---

## 三、核心设计

### 3.1 统一原则：发布仍然走数据库事务 outbox

两类事件都保留当前最有价值的性质：**事件写入与业务数据更新在同一数据库事务中提交**。这也是 `SysDaoMessageService` 相比外部 MQ 的核心工程价值。

因此重构只改变“落库后的存储模型和消费模型”，不改变“业务侧调用 `IMessageService` 进行同事务发布”的总路线。

### 3.2 广播事件：事件流与订阅游标分离

#### 3.2.1 数据模型

广播至少拆成两类持久对象：

1. **Broadcast Event Stream**
   - 一行代表一次已经提交的广播事件。
   - 关键语义字段：`eventId`、`topic`、`eventTime`、`payload`、`headers`、`bizKey`、`bizObjName`。
   - 行只追加，除 TTL 清理外不更新状态。

2. **Broadcast Subscriber Cursor**
   - 一行代表“某个订阅者对某个 topic/stream 分区的消费进度”。
   - 关键语义字段：`subscriberId`、`topic`、`lastConsumedEventId`、`leaseOwner`、`leaseExpireTime`、`lastConsumeTime`、`lastError`。

这里的 `subscriberId` 不是进程随机值，而是**稳定的订阅身份**。它至少要能区分：

- 哪个逻辑订阅者（listener bean / consumer name）
- 哪个实例副本在执行该订阅者（仅体现在 leaseOwner，不体现在 subscriberId 本身）

#### 3.2.2 顺序与不遗漏保证

广播的可靠语义定义为：

> 对于固定的 `subscriberId + topic`，系统按 `eventId` 升序处理事件。只有当 `eventId = N` 的处理结果被确认成功后，`lastConsumedEventId` 才能推进到 `N`。重启、抢占、重试之后，订阅者会从持久化 cursor 的下一个 eventId 继续，因此不会遗漏；最多允许重复处理最后一个未确认成功的事件。

也就是：

- **顺序保证**：来自 `lastConsumedEventId -> next eventId` 的串行推进
- **不遗漏保证**：来自“cursor 持久化后再推进”
- **故障语义**：至少一次，不是恰好一次

#### 3.2.3 单订阅者执行算法

伪代码：

```text
loop for subscriberId + topic:
    claim cursor row by lease
    load cursor.lastConsumedEventId = X
    fetch next events where eventId > X order by eventId asc limit N

    for event in events:
        invoke listener(event)
        if success:
            update cursor.lastConsumedEventId = event.eventId
            update cursor.lastConsumeTime
        else:
            record lastError
            stop loop for this subscriber
```

关键约束：

1. **一个 `subscriberId + topic` 在任一时刻只允许一个 lease owner 执行**。
2. **cursor 只能单调递增**，不允许回退。
3. **cursor 更新与“本次 listener 成功”绑定**；未成功不推进。
4. **同一 subscriber 不做多线程并发拉取**；广播顺序优先于吞吐。

#### 3.2.4 广播如何支持多实例和扩缩容

广播需要区分两层身份：

1. **逻辑订阅者身份**：谁应该看到这类广播。
2. **运行实例身份**：当前由哪台机器代替这个逻辑订阅者执行。

设计裁定：

- `subscriberId` 应绑定到**逻辑订阅者**，例如 `serviceName:listenerBean:topic`。
- `leaseOwner` 绑定到**当前实例**，例如 `instanceId`。
- 同一服务横向扩容时，多个实例竞争同一个 `subscriberId` 的 lease，但最终只有一个实例持有。这样可以避免“一组完全同构的服务副本都各消费一次同一广播”造成意外倍增。

如果未来确实需要“每个实例都收到一次”的广播，必须作为**不同语义**单独建模，例如 instance-scoped broadcast，而不能复用当前的 service-scoped reliable broadcast。

#### 3.2.5 为什么不能继续只靠 `bro-` topic + 内存游标

因为这只能表达“我现在想扫哪些 topic”，表达不了：

- 某个 subscriber 已经处理到哪条
- 某个实例是否仍持有执行权
- 某次重启之后应该从哪条恢复
- 某个 subscriber 卡在错误上时是否阻塞后续事件

这些都是广播“按顺序且不遗漏”所必需的持久事实。

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
   - 广播事件的确认边界仍是“单 event + subscriber cursor 连续成功前缀”。
   - 因此一个 chunk 可以作为批量 flush / 批量提交的优化单位，但不能只记录 chunk 成败而丢掉逐条 event outcome。
2. **Processor/Consumer DSL 不是事件 listener 契约本身**：普通事件可以在实现层被包装成“扫描一批待处理事件的 batch”，但 listener 注册、topic 路由、cursor / retry 语义仍应由 `sys-event` 自己定义，而不是暴露成 batch DSL。
3. **Batch task / record result 不是必要中心模型**：普通事件不应直接复用 `NopBatchTask` / `NopBatchRecordResult` 作为运行时真源；这些对象围绕“任务实例 + chunk 结果”组织，而普通事件是常驻共享队列。
4. **saveState / completedIndex 恢复不是事件恢复主模型**：广播恢复首先看 cursor，普通事件恢复首先看队列状态与重试字段，而不是 batch 的 completedIndex。
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

建议最终形成三类表，而不是“一张事件表打所有语义补丁”：

1. **普通事件表**
   - 面向 shared queue
   - 主关注点：分区、lease、重试、状态迁移

2. **广播事件流表**
   - 面向 append-only event stream
   - 主关注点：顺序扫描、TTL、payload 保存

3. **广播订阅游标表**
   - 面向 subscriber progress
   - 主关注点：cursor、lease、错误状态

是否保留现有 `NopSysEvent` 名称不是核心问题；核心问题是**语义上必须拆成这三类对象**。如果为了迁移平滑，第一阶段也可以先保留旧表名给普通事件，同时新增广播专用表与 cursor 表，但最终 owner-doc 基线必须按三类对象思考，而不是按 `isBroadcast` thinking。

### 3.6 失败、重试与阻塞策略

#### 广播事件

- 某个 subscriber 处理失败，只阻塞**它自己的 cursor**。
- 其他 subscriber 的 cursor 可独立前进。
- 同一 subscriber 对该 topic 后续事件默认不得越过失败事件，否则会破坏顺序语义。

#### 普通事件

- 某条事件失败后，默认通过更新 `scheduleTime + retryTimes` 延迟重试，而不是在平台层引入 partition head blocking。
- 业务如果确实要求“后续消息不能越过前序失败消息”，应在 listener / 业务状态机层自行判定并返回重试，而不是把该约束固化到基础设施层。
- lease 字段仍需要补齐，避免 worker 崩溃后长期悬空。

### 3.7 迁移约束

迁移需要遵守以下硬约束：

1. 已有同事务发布能力不能丢。
2. 广播不能再依赖 JVM 内存游标。
3. 普通事件发布端必须停止随机分区默认值。
4. 普通事件 worker 需要明确的 partition ownership；当前最小 live baseline 通过 `assignedPartitions + row lease + partition scan` 落地，而不是额外的 ownership 表。
5. 文档与代码都要明确：系统提供的是**至少一次**语义，业务 listener 如需抗重复必须做幂等。

---

## 四、拒绝了什么

1. **继续共用一张 `nop_sys_event` 表，只是在 `isBroadcast` 分支上继续打补丁**：这会把“共享队列”和“广播事件流”两种根本不同的状态机继续揉在一起，无法清晰建模 subscriber cursor。

2. **广播直接按每实例 fan-out 为多条普通事件**：这会让广播事件数量与实例数线性膨胀，并把“订阅者进度”隐藏成消息复制，扩缩容和实例漂移时更难校正。

3. **广播允许同一 subscriber 并行处理多条事件**：吞吐会更高，但无法保证严格顺序推进 cursor。

4. **普通事件继续随机写 `partitionIndex`**：随机分布提升了均衡性，但破坏了同键顺序，不能作为默认策略。

5. **直接把事件确认语义简化成“只看 chunk 成败”**：普通事件可以借 batch 的扫描与分区执行外壳，但必须保留逐条 event outcome；不能因为外层长得像 batch，就只剩 chunk runtime 状态。

6. **在平台层引入 partition head blocking 作为默认机制**：这会把失败重试、补偿和业务乱序容忍度固化到基础设施层，反而放大复杂度。默认策略应是处理失败后改写 `scheduleTime` 重新投递，由业务逻辑自己决定如何保证幂等和顺序。

7. **把目标做成恰好一次**：在当前数据库 outbox + listener 回调模型下，exactly-once 成本过高，也不符合平台其他模块的默认语义。owner-doc 明确坚持 at-least-once + 幂等 listener。

---

## 五、与已有设计的关系

- 与 `docs-for-ai/03-modules/nop-sys.md` 的关系：后者当前只描述“分区扫描、支持延迟事件”的使用层摘要；本设计补的是平台开发侧的可靠性与并发语义基线。实现落地后，`nop-sys.md` 需要同步更新为“广播 / 普通事件分离”。
- 与 `docs-for-ai/03-modules/nop-batch.md` 的关系：`sys-event` 中普通事件可借用 batch 的周期扫描外壳、partition range 约定、worker 切分思想以及 chunk flush 优化，但不把事件结果简化成只看 chunk 成败。
- 与 `ai-dev/design/nop-job/worker-assignment-design.md` 的关系：普通事件的 worker ownership、`IntRangeSet`、`WeightedPartitionAssigner`、short-range 值域直接沿用该文档已经论证过的分区基线，但失败后默认是重设调度时间再次扫描，而不是在基础设施层固化 head blocking。
