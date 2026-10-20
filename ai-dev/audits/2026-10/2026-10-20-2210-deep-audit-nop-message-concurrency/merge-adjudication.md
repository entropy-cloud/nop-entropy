# 单元内合并裁定 — nop-message（WI 4.1）

- 裁定人：WI 4.1 独立合并裁定员（未参与本单元初审/深挖/复核任一轮）
- 日期：2026-10-20
- 基线：commit 1407fc426e（concurrency-audit-remediation 分支），file:line 以各维度文件 2026-10-20 live code 实测为准
- 裁定对象：五维度复核后最终保留 **44 条**（C1×12 / C3×10 / C5×6 / C6×7 / C8×9）+ 维度集外线索 **CL×27** + C10 缺保护登记 **12 条**（含 1 项覆盖闭合处置 3 行挂靠）

---

## 一、裁定口径与输入

### 1. 输入清单

| 输入 | 用途 |
|---|---|
| C1-shared-mutable-state.md / C3-async-chains.md / C5-memory-bounds.md / C6-resource-lifecycle.md / C8-backpressure.md 的「## 最终保留项」+「## 维度复核结论」节 | 44 条保留发现、复核裁定、逐卷同 diff 修复批建议、跨单元对照表态 |
| C1-round2 / C3-round2 / C5-round2 / C6-round2 / C8-round2 | 新增条目（C1-012、C3-009/010、C5-005/006、C6-007、C8-009）、扩展面（扩展 C3-002/005/007、清单行 7/13、扩展 C6-005/C8-005/C8-008）、CL-025/026/027 |
| C10-test-protection.md（含复核结论） | 保护力矩阵 44 行、反向钉死 5 处（4 硬 + 1 软）、缺保护登记 12 条、覆盖闭合处置（C3-004/C3-005/C5-006 挂靠） |
| dispatch-log.md | Phase 2–5 全程记录、主登记建议网、跨单元移交落点（WI 2.3 CL-022、WI 2.5 REGISTRY 本体） |
| `ai-dev/audits/cra-index.md` | WI 1.1–3.4 P1 台账、CL 台账、各单元合并计数口径（跨单元查重与先例对齐） |

### 2. 裁定规则

1. **三态判定，恰好一处**：①缺陷族（≥2 条同根因同修复点 → 主登记吸收成员，产出 1 工单）；②独立立案（1 发现 = 1 工单）；③watch-only（不立案，仅台账观察）。
2. **复核已登记裁定为强制输入，不再重裁**：C8-001→C3-001 主登记（getResult 无上界）；C1-006→C3-003；C1-011→C6-005；C3-002↔C8-004 同屏障两极性同 diff（两 P1 配对）；C1-001 与 WI 1.2 C3-006 同案双轨（本体面修复落点在本单元）；REGISTRY 三面分层（C1-002 并发面 / C5-001 内存面 / C6-004 清理路径面，四方含 WI 2.5 消费面互斥无重复工单）+ C5-001+C6-004 同 diff 族（stop-clear vs TTL 一次裁决）；同体三面族（C5-003/C6-001/C8-003 Local executor；C6-002/C3-003/CL-019 DebeziumMessageSource subscribe 站点；C6-003/C1-007 Kafka doSubscribe 同段；C8-006/C8-009 reply 改点；C5-004/C5-005 Pulsar 配置面对称）。
3. 各卷「同 diff 修复批建议」为族划定依据；与复核登记不一致时以复核登记为准。
4. C10 反向钉死（4 硬 1 软）作为对应工单的**强制联动义务**登记（修复 diff 必须先改测试）。
5. 计数纪律：族成员只计一次；工单严重度取成员最高复核后严重度；判级分歧不改变落位，只登记 §五。
6. CL 逐条处置（并入工单 / 独立线索 / 驳回 / 移交）；C10 缺保护登记逐条映射随本体工单。

### 3. 计数归属勘定

44 条逐条复核后严重度合计 **P1×4 / P2×26 / P3×14**；dispatch-log 派发口径 P2×25/P3×15 与之差 1，为 **C8-001**（复核降级 P1→P2 后并入 C3-001 主登记，按归属计入 C3 卷）的计数归属差异——C10 卷已显式裁定，本裁定以逐条口径为准，逐条落位见 §六。

---

## 二、缺陷族结构（编号 MA-01 起）

> 10 族吸收 26 条，产出 10 工单。每族给出成员、根因、修复点（同 diff 范围）与协调义务。

### MA-01 消费等待无上界族（getResult 无上界）

- **成员**：C3-001（P2，**主登记**，失效面超集：Kafka:356/:516、Pulsar:333/:248、Local:216 五等待点 + DLQ 面⑥）+ C8-001（P2，复核降级并入；独有增量 = F5 活性盲区/isHealthy、Kafka 成员僵尸化、Pulsar Exclusive 冻结细节）。
- **根因**：`FutureHelper.getResult → syncGet` 全链无 timeout 被用作消费路径唯一无上界等待点；模块有界停止设计（MSG-F-54/wakeup/pollTimeout）不覆盖该等待点。
- **修复点（同 diff）**：新增 `consumeAwaitTimeout` 有界包装（复用 `FutureHelper.waitResult(future, timeout)`）+ 超时按消息级失败路径处置（Kafka seek 重投 / Pulsar nack / Local 留证丢弃）+ run 循环活性暴露消除 isActive 误报。
- **协调**：与 WI 3.1 MA-11（DBMessageService 同 SPI 同病）契约对齐——超时上界成为 IMessageService 消费面约定（§五-6）；7.1 分歧：C8-001 P1↔P2（若升档两案同批）。

### MA-02 Debezium 事件屏障两极性族（毒事件 endpoint 统一设计）

- **成员**：C3-002（P1，存活极性：handleDebeziumEvent 无屏障 → 转换异常炸流 + started 撒谎 + 零 listener 接线）+ C8-004（P1，投递极性：dispatchEvent 吞异常 → engine 推进 offset 静默丢变更流）。round2 扩展（parseKey CCE :112-115、ts_ms 裸转换 :59）与 CL-011（零接线面）、CL-012（parseKey NPE 面）随单，不另编号。
- **根因**：同一事件投递屏障缺 per-event 隔离与失败会计——无屏障则单事件炸流，纯吞屏障则静默丢事件（两极同禁）。
- **修复点（同一 diff）**：per-event try/catch + 毒事件 failure endpoint（跳过 + WARN 计数 + ConsumeFailure 派发）+ 引擎终态死亡反映到 started/健康探针 + DebeziumMessageSource 启动时接线 failure listener。
- **判级**：两成员 P1 均保留、配对裁定；「nop-stream 主路径 capture 自愈 / 每任务新建实例，受损面 = 直接嵌入方」同口径注记随单（§五-3）。

### MA-03 REGISTRY 生命周期族（NopStreamOffsetBackingStore）

- **成员**：C5-001（P2，**主登记**：静态账本单调增长、stop 不回收、独立嵌入路径零清理入口）+ C6-004（P2：清理调用点不在模块生命周期内 + stale-resume 语义）。CL-005（clearConnector 无人保证调用）随单。
- **根因**：REGISTRY 的 put（forConnector/configure/ensureBound 三点）与 remove（clearConnector 一点）无生命周期配对；stop 语义与回收语义脱钩。
- **修复点（同 diff，强制一次性落地）**：stop 侧配对清理（或 evictIdle/TTL）+ 契约取舍一次裁决（stop-clear vs TTL vs 条件移除，对「同名重启续传」期望影响相反，不得两单各修一半）+ owner doc 补记生命周期契约 + 驻留诊断指标。
- **协调**：C1-002（并发摘除竞态/守卫缺失面）独立立案但与本族共享同一次协议裁决（§三-1）；软钉 #5（setOffsets REPLACE 契约）纳入同一裁决面；WI 2.5 消费面（C3-008 键错位 / CL-010 watch / WI 2.5 C1-005 同名双引擎）维持互斥分层（§七-2）。

### MA-04 Local 重投 executor 三面族（同一 executor：内存/生命周期/拓扑）

- **成员**：C5-003（P2：未决消息堆驻留无上界 + delay=0 自旋面归本条）+ C6-001（P2：调度器只建不关、bean 无 destroy 挂钩）+ C8-003（P2：单线程全局串行化点 + 用户 handler 内联）。CL-001/CL-008/CL-009/CL-016（四面同点线索）随单。
- **根因**：`nop-local-message-redelivery` 单线程调度器同时承担延迟账本（无界 DelayedWorkQueue）、执行拓扑（全局串行 + 内联用户代码）与生命周期（无 shutdown 路径）三重职责。
- **修复点（一个 diff 批次）**：destroy 钩子（shutdownNow + beans.xml 挂 destroy-method）+ 有界待决队列（平台 `IBlockingQueue`+`QueueOverflowPolicy` 或每订阅上限对齐 maxConsumeAttempts）+ 「到点投递信号 / handler 转入有界工作队列」拓扑隔离 + pending 深度 gauge + delay=0 最小间隔节流。
- **约束**：C6-001 复核已裁——不采 `GlobalExecutors.globalTimer()` 方案（WI 2.1 C4-001 已立案共享定时器病，§七-6）。
- **协调**：C3-006（suspended 红投丢弃，独立工单）与 C8-005（最小延迟下限，独立工单）同文件/同机制面，落地批次协调。

### MA-05 DebeziumMessageSource subscribe 站点族（put-then-start + 交叉竞态，三面归一）

- **成员**：C6-002（P1：先 put 后启动，失败路径幽灵订阅不回滚——确定性错误路径）+ C3-003（P2，**站点主登记**：锁外 map 写 + 锁内 started 检查，交错 A 订阅静默丢失 / 交错 B stop 后引擎复活）+ C1-006（P2，并入 C3-003 交错 A 子面：cancel 的 remove→isEmpty→stop 非原子细节）。CL-019（overlay 同点登记）随单。
- **根因**：订阅注册写操作在生命周期锁外执行、注册在成败判定之前生效、stop 后 subscribe 无拒绝路径——同站点三个机制面（竞态/确定性错误路径/订阅-取消竞窗）。
- **修复点（同站点一个 diff 批次）**：`subscriptions.put` 移至 `startEngineIfNeeded` 成功之后（或 catch 中 remove 后重抛）+ subscribe/cancel 的「增删 + 空判定 + 启停」复合操作统一入 `this` 锁 + stop 后 subscribe 抛 typed 拒绝。
- **判级**：保留 C6-002 独立 P1 判级与触发窗收窄注记（幽灵投递须后续启动成功；条目数受失败重试约束；生产主路径结构性免疫），判级口径与 C3-002/C8-004 配对留 7.1（§五-3）。

### MA-06 destroy/stop 窗口守卫失效族（Kafka/Pulsar 服务装载与 doStop 无互斥）

- **成员**（5 条，C1 卷修复批次①「同根归批」+ C6 卷同段协调）：C1-003（P2：Pulsar doSubscribe checkIsActive 位于首次 client 解引用之后，类型化拒绝实际 NPE）+ C1-004（P2：Pulsar producer 缓存 TOCTOU——销毁后残留孤儿 producer）+ C1-005（P2：Kafka getProducer 复合守卫对非 volatile 字段双读 NPE）+ C1-007（P2：Kafka doSubscribe 全程无生命周期守卫 + add-after-clear 订阅泄漏）+ C6-003（P2 理论面：Kafka doSubscribe 构造后裸奔段无外层 catch，Pulsar WI8 形态未清扫）。CL-014（= C1-003 同站点守卫次序面）随单。
- **根因**：destroy/stop 窗口内 send/subscribe 装载路径的复合守卫失效（守卫缺位 / 次序错位 / 非原子双读 / 装载与 clear 无互斥）——F10/F8 destroy-window 家族。
- **修复点（同 diff 集，按成员落点）**：入口 checkIsActive 前置（Pulsar :431/:434 交换；Kafka doSubscribe 补守卫）+ 装载与 clear 共享服务级锁或 doStop 二次排空 + Kafka 复制 Pulsar WI8 外层 catch 形态 + getProducer 单次局部读（`producer` 对称声明 volatile）。
- **排除**：C8-007 与 C1-004 同站点异机制（过载串行化 vs 生命周期竞态），独立立案不并入。

### MA-07 Kafka reply 发送预算族（管道化/期限化同改点）

- **成员**：C8-006（P2：批量 reply 逐条同步 `ctx.send` 串行占用 poll 预算）+ C8-009（P2：消费线程承接 max.block.ms=60s 共享缓冲满溢单点阻塞）。CL-022（本单元 docs-bug 线索：F-39 裁定 vs kafka-clients 实际行为）随单定案。
- **根因**：消费线程作为共享 producer 调用方与发送面预算（RTT × 批大小 / buffer.memory 满溢阻塞）之间无隔离与无联动声明——两根因不同（串行累加 vs 单点阻塞）但修复收敛同一「reply 管道化 + 期限化」改点。
- **修复点（同 diff 批次）**：批量 reply 改 `sendAsync` 收集 future + 批末 `waitAll`（对齐 Pulsar :277/:284 形态）+ 消费线程路径期限化（低 max.block.ms 或独立 producer 实例）+ owner doc 三参数联动约束声明（`buffer.memory ÷ 平均消息大小 × ack 时延 ≪ max.poll.interval.ms`）+ buffer 满溢探针测试（复用 CL-022 建议形态，顺带定案 docs-bug）。

### MA-08 Pulsar 缓冲配置面对称族（消费/生产双端旋钮缺位）

- **成员**：C5-004（P3：消费端 receiverQueueSize 无配置面 + 批路径缺省 BatchReceivePolicy 缺位）+ C5-005（P3：生产端 maxPendingMessages/blockIfQueueFull 无配置面，per-topic 常驻 producer 双队列）。
- **根因**：Pulsar 客户端缺省值构成事实上界，但模块配置面缺对肿瘤旋钮——与 Kafka 对称面（maxPollRecords / buffer.memory 可配）形成容量验算不可控盲区。
- **修复点（同 diff 批次）**：`PulsarConsumerConfig` 增 receiverQueueSize、`PulsarProducerConfig` 增 maxPendingMessages/blockIfQueueFull + builder 映射 captor 断言 + 批路径缺省显式 BatchReceivePolicy + owner doc 补 per-consumer/per-producer 验算公式与 ack-topic 基数翻倍提示。
- **强制联动**：反向钉死 #2——`batchReceivePolicyNotWiredWhenUnconfigured`（实际 :442-456）把缺位钉为契约，修复须先改写该测试。

### MA-09 DebeziumEngineWrapper 生命周期族（quiesce/死亡回收/一次性契约）

- **成员**：C6-005（P3，**主登记**：stop 三缺口——不 join、isRunning 过早翻转、一次性无 fail-fast；(d) start REE 毒化窗；扩展面：dispatchEvent 挂死使 stop 静默不可达成）+ C1-011（P3，并入：一次性状态机无复位 + startRequested 先于可失败构建置位 + 失败后「already running」误导）+ C6-007（P3：引擎异常终局死亡路径 engine 对象永不 close，AutoCloseable 契约缺位）。CL-013（wrapper 一次性线索，C6-005 立案出处）随单。
- **根因**：start/stop 对称性停留在「触发关闭」层面——缺「等待静默」（quiesce/join）、缺死亡路径回收（close 只存在于 stop 路径）、缺失败复位与 one-shot 契约声明。
- **修复点（同批）**：start 记录 worker Future（或 latch），stop close 后限时 join + 超时升级 stuck WARN（对齐 Kafka/Pulsar `consume-thread-stuck` 形态）+ worker catch/finally 补幂等 `engine.close()`（死亡即回收）+ start 失败 catch 复位 startRequested + start-after-stop typed 拒绝或 javadoc 显式 one-shot 契约。
- **跨单元**：WI 1.2 C3-014/MA-07（停止契约）为 nop-stream 使用面双轨互补，修复批次对齐（§七-3）。

### MA-10 FAILURE_LISTENERS 注册表协议族（同表两面）

- **成员**：C1-001（P2：replace + 无条件 remove 复合协议无世代校验——同名 connector 交叠重启吞终局失败监听；本体面修复落点）+ C5-002（P3：stop 与注册表生命周期不对称——stop 不注销、javadoc 义务性文字非守卫）。
- **根因**：`FAILURE_LISTENERS` 单键寻址的注册协议缺归属判定与 stop 配对回收（当前唯一生产调用方 finally 注销合规，缺陷为守卫缺位 + 条件性泄漏）。
- **修复点（同批）**：键升维 `(connectorName, owner 实例标识)` 或两参条件 `remove(key, listener)` + 注册返回句柄供注销 + stop 内补 unregister 兜底（或 auto-close 句柄）+ owner doc 记「静态表必须有 stop 配对回收」约定。
- **跨单元**：与 WI 1.2 C3-006 同案双轨互挂（使用面/本体面），严重度对齐先案 P2，P1↔P2 留 7.1（§五-1、§七-4）。

---

## 三、独立立案清单

> 18 条 → 18 工单。工单身份沿用 Finding ID（`nop-message-` 前缀，cra-index 全局唯一格式）。

| # | 工单 | 严重度 | 一句话 + 修复点 | 协调义务 |
|---|---|---|---|---|
| 1 | nop-message-C1-002 | P2 | REGISTRY 摘除竞态/守卫缺失面：clearConnector 无条件 remove 可静默分裂活实例写入——条件移除或引入 epoch | 与 MA-03 共享同一次契约裁决；软钉 #5（setOffsets REPLACE，TestNopStreamOffsetBackingStore:89-97）纳入裁决面 |
| 2 | nop-message-C1-008 | P3 | Local cancel 裸 get NPE + getConsumers 活 map 外泄构成跨模块隐式契约（SysDao:617）——:80 判空 + 显式查询方法替代外泄 | **反向钉死 #4 强制联动**：修复同时触及 SysDaoMessageService.java:617 与 3 个测试（TestSysDaoMessageService:374、TestLocalMessageService:70/:117）；CL-015/CL-020 并入本单 |
| 3 | nop-message-C1-009 | P3 | Pulsar suspend/resume 调用方线程直呼 consumer.pause/resume，偏离 Kafka WI20 已裁定意图标志模式，暂停非即时生效——对齐 Kafka 模式或 owner doc 补最终一致语义 | C10-012 随单（Pulsar suspend 零测试）；修复若采用「照抄 Kafka」须以新测试区分两模式 |
| 4 | nop-message-C1-010 | P3 | cancelReason 非 volatile 且写序反置（volatile 写在 plain 写之前），「见 cancelled 即见 reason」不成立——交换写序或声明 volatile | 全仓零读者，单行修复，随 C10-011 契约完备批补可见性测试 |
| 5 | nop-message-C1-012 | P2（理论面） | 配置 bean 集合字段活引用外泄且装配后可变（in-repo 零运行期写者）——装配终点 `Map.copyOf` 冻结 + getter 不可变视图 + addExtraProperty 装配期专用 | **反向钉死 #3 强制联动**：先改写 TestDebeziumEngineProperties :181/:187/:207/:218 四处活 map setup；TestDebeziumConfigSerializable 序列化 bless 面同批复核 |
| 6 | nop-message-C3-004 | P2 | Pulsar sendAsync 无同步异常包装（Kafka F-39 兄弟漏修）：同步抛违约 CompletionStage 契约——逐字对照 Kafka 形态 completeExceptionally | **反向钉死 #1 强制联动**：改写 sendAfterDestroyIsTypedRejectedNotNpe（实际 :459-467）同步抛断言；评审防「为保测试绿放弃修复」 |
| 7 | nop-message-C3-005 | P2 | Pulsar 批量 ack/reply 并发推进与单条/Kafka 排序分叉 + ConsumeLater 批内语义两后端分叉——改 per-message 先 reply 后 ack 或补齐声明 | CL-023（整批 nack 已应答前缀重复应答声明）与 CL-026（catch 清理 lastFailureTopic 一行补齐）并入本单；与 C10-008 挂靠保护面 |
| 8 | nop-message-C3-006 | P2 | Local 红投火发时 suspended 即永久丢弃延迟消息（javadoc 契约只豁免 cancelled）——suspended 改重调度（或挂起累积 resume flush）+ javadoc 放弃矩阵 | 与 MA-04 同文件（scheduleRedelivery）落地批次协调 |
| 9 | nop-message-C3-007 | P3 | orTimeout ghost 语义已声明（WI 2.3 CL-022 落点闭环工件）；残留①零配置无上界的 javadoc 显式声明 | 残留②（sent 计数点位）本次裁定归并 C8-008 工单修复；本单为跨单元移交闭环的修复工件 |
| 10 | nop-message-C3-008 | P3 | Kafka commitSync 失败仅 WARN 吞 + dispatched=true 抑制退避——计入 consecutiveErrors 交由既有 backoff/failure dispatch + 失败计数指标 | 随 C10-011 批补 commitSync 失败注入测试 |
| 11 | nop-message-C3-009 | P2 | 全模块 7 处异常屏障 Exception-only，Error 逃逸致订阅僵尸（isActive 误报）+ Debezium 终局通知被绕过——屏障改 catch Throwable 并走失败上报路径 | 修复归属对齐 WI 2.1 C3-001 平台绝缘层批次（§七-5）；判级随族内统一可升格（§五-4）；Debezium 面与 MA-02 同点位协调 |
| 12 | nop-message-C3-010 | P3 | Pulsar 批量对 onMessageBatch 返回 null 隐式 NPE（Kafka 显式 IllegalStateException）——前置显式契约 + javadoc 补行 | 随 C10-011 批两后端 null 契约钉 |
| 13 | nop-message-C5-006 | P3 | Debezium 引擎队列仅有事件数上界且可经 extraProperties 上调，字节维无缺省无守卫——缺省设 max.queue.size.in.bytes + 钉定族扩展（仅许下调） | 与 C1-012（配置冻结）同文件协调；C10-010 挂靠（钉定测试 :203-224 在位，字节面零测试） |
| 14 | nop-message-C6-006 | P2 | Debezium 以 action 身份作注册/注销键，同方法引用双订阅后 cancel 旧句柄停掉现任订阅引擎（WI23/MSG-F-15 同族未修，设计 §3.9 明文禁令）——WI23 同法 `subscriptions.values().remove(this)` 或改 Map<Subscription,·> | 与 MA-05 同 map/锁改造协调（可同批落地，判级独立）；随 C10-001 测试类第 4 场景 |
| 15 | nop-message-C8-002 | **P1** | Local 消费失败静默丢弃且从不派发第一层 onConsumeFailure（grep 零调用），违反设计 §3.3「无 DLQ substrate 后端仅第一层回调」契约，at-least-once 退化 at-most-once——catch 中补齐第一层回调（guarded dispatch 形态） | Local 为生产路径（SysDaoMessageService 承接 nop_sys_event）；重投与否按设计 3.9 立场显式裁决；随 C10-006 契约「失败派发」维度（现状预期红） |
| 16 | nop-message-C8-005 | P2 | Kafka ConsumeLater 无预算闭环且 delay≤0 热旋转（HOL 冻结同 assignment 全分区）——最小生效延迟下限 + 连续 defer 计数 + 退出/告警机制 | 三后端同批：Pulsar reconsumeLater 无预算弹跳扩展随本单；Local 自旋面在 MA-04（C5-003）；CL-002（defer 上限与取消时延声明）/CL-027（单条端点 F-08 残留声明一行）并入本单 |
| 17 | nop-message-C8-007 | P2 | Pulsar getProducer computeIfAbsent 锁内阻塞建连 + 失败后排队者重试放大——占位 + 异步/带超时创建（putIfAbsent future 或 createAsync+超时） | 与 C1-004（MA-06 成员）同站点异机制，修复互不遮蔽需在 diff 说明；随 C10-004 建连挂起压力测试 |
| 18 | nop-message-C8-008 | P3 | 过载可观测缺口：批失败 incrementFailed 漏计（Kafka/Pulsar 批 catch）、Pulsar sent 前置计数、毒消息 endpoint 漏计（:366-370 return 先于 :370）、Local 四口径全缺——四处补齐 + redelivery/endpoint/dlq 计数 + 队列深度 gauge | 归并承接 C3-007 残留②与 CL-004（sent 计数点位统一）；owner doc WI30 节扩表；随 C10-008（metrics 调用点测试，两断言即可让现状转红） |

---

## 四、watch-only 清单

**0 条（显式空）。**

- 准入校验：Phase 4 复核 **零驳回**，44 条全部具备具体触发证据（或显式标注的理论面）与可行动修复点，无一条满足 watch-only 准入口径（「无现行触发路径且修复不可行动」）。
- C1-012（理论面）与 C6-003（理论面）维持工单不降 watch：二者均有结构性修复（配置冻结 / 守卫补齐）且被 C10 反向钉死/缺保护登记牵制（#3、C10-004），降 watch 将丢失修复义务。
- 子面级 watch 登记不占本节（已在对应工单失效面内随批）：C1-002 失效面内 setOffsets REPLACE 同族登记（→ 软钉 #5，随 C1-002 工单裁决）；C3-001 失效面⑥ DLQ syncGet（producer delivery.timeout 兜底，随 MA-01 观测）；C5-003 附带 Kafka deferAfterBatch CPU 面（随 MA-04/C8-005 观测）。

---

## 五、开放裁定与 7.1 移交项

| # | 项 | 分歧 | 移交建议 |
|---|---|---|---|
| 1 | C1-001 判级（MA-10） | P1↔P2：与 WI 1.2 C3-006 同案双轨，先案 P2 为基线；升档依据 = 「任务永挂」后果 + 校准行「罕见但可具体化的竞态」 | 对齐 WI 1.2 先案；若升档两单元同批 |
| 2 | C8-001 判级（MA-01） | P1↔P2：已按 C1-001 先例降级对齐先案 P2 并入 C3-001；P1 依据 = 成员僵尸化 + Local 全 JVM 冻结放大面 | 维持 P2；若升档 MA-01 两案同批 |
| 3 | C3-002 / C6-002 / C8-004 P1 判级口径（MA-02/MA-05） | 嵌入方受损面 vs 生产主路径结构性免疫（nop-stream capture-and-rethrow 自愈 / source 每任务新建实例；仓内 main 直接嵌入仅示例树） | 三案配对裁定，口径统一后同批调整；维持 P1 依据 = F2「半个成功」无恢复语义校准行 + CDC 静默断流/静默丢变更流后果等级 |
| 4 | C3-009 升格可能 | 族内吸收：WI 2.1 C3-001 周期任务绝缘层（P1，29 站点）/ WI 1.7 C6-001（P1）为同族先例，本条维持 P2 的依据是修复归属统一绝缘层批次（避免同族双 P1 计数） | 7.1 统一族内判级时随之升格；修复 diff 归 WI 2.1 绝缘层批次，本单元站点清单（7 屏障）为其输入 |
| 5 | MA-03 REGISTRY 契约一次裁决 | stop-clear（C6-004 方案）vs TTL/evictIdle（C5-001 方案）vs 条件移除（C1-002 方案）对「同名重启续传」契约影响相反；软钉 #5 setOffsets REPLACE 契约有设计依据（checkpoint 单一事实源） | 工单前置设计裁定（非判级分歧）：一次裁决覆盖 C5-001+C6-004+C1-002 三单 + WI 2.5 消费面表态，禁止各修一半 |
| 6 | MA-01 consumeAwaitTimeout 契约对齐 | 超时上界是否成为 IMessageService 消费面约定（WI 3.1 MA-11 DBMessageService 同病） | 与 WI 3.1 MA-11 修复批次对齐契约语义；超时值纳入 maxPollIntervalMs/ackTimeout 自洽指引 |
| 7 | MA-02 毒事件 endpoint 设计 | 两极同禁（纯吞 = C8-004 面；整体炸流 = C3-002 面）的 endpoint 行为（跳过+计数+上报 vs 中止本轮换取 at-least-once） | 工单内前置设计裁定；C10-001 建议的测试场景 (2) 为两极语义的唯一锚定机会 |
| 8 | CL-024 / CL-007 归属 | CL-024（Local 红投 IContext 不传播——消费模型整体既定形态，单点修会造成后端语义分叉）；CL-007（doStop 串行 cancel N×6s 停机预算——方向安全） | CL-024 移交「消费线程 IContext 传播专题」统一裁定（7.1 指定归属）；CL-007 维持独立线索，7.1 决定是否立案 |

---

## 六、权威汇总表（合并前 44 条 → 合并后 28 工单）

### 逐条落位表

| 发现 | 复核后严重度 | 落位 | 工单严重度 |
|---|---|---|---|
| C1-001 | P2 | MA-10（本体面修复落点；与 WI 1.2 C3-006 双轨互挂） | P2 |
| C1-002 | P2 | 独立（与 MA-03 共享契约裁决） | P2 |
| C1-003 | P2 | MA-06 | P2 |
| C1-004 | P2 | MA-06 | P2 |
| C1-005 | P2 | MA-06 | P2 |
| C1-006 | P2 | MA-05（并入 C3-003 交错 A） | P1 |
| C1-007 | P2 | MA-06 | P2 |
| C1-008 | P3 | 独立 | P3 |
| C1-009 | P3 | 独立 | P3 |
| C1-010 | P3 | 独立 | P3 |
| C1-011 | P3 | MA-09（并入 C6-005 主登记） | P3 |
| C1-012 | P2（理论面） | 独立 | P2 |
| C3-001 | P2 | MA-01（主登记，吸收 C8-001） | P2 |
| C3-002 | P1 | MA-02（存活极性） | P1 |
| C3-003 | P2 | MA-05（站点主登记） | P1 |
| C3-004 | P2 | 独立 | P2 |
| C3-005 | P2 | 独立 | P2 |
| C3-006 | P2 | 独立 | P2 |
| C3-007 | P3 | 独立（WI 2.3 CL-022 闭环工件；残留②归 C8-008） | P3 |
| C3-008 | P3 | 独立 | P3 |
| C3-009 | P2 | 独立（修复归属 WI 2.1 绝缘层批次） | P2 |
| C3-010 | P3 | 独立 | P3 |
| C5-001 | P2 | MA-03（主登记，内存演化面） | P2 |
| C5-002 | P3 | MA-10 | P2 |
| C5-003 | P2 | MA-04 | P2 |
| C5-004 | P3 | MA-08 | P3 |
| C5-005 | P3 | MA-08 | P3 |
| C5-006 | P3 | 独立 | P3 |
| C6-001 | P2 | MA-04 | P2 |
| C6-002 | P1 | MA-05（put-then-start 面，P1 判级保留） | P1 |
| C6-003 | P2（理论面） | MA-06 | P2 |
| C6-004 | P2 | MA-03（清理路径面，同 diff） | P2 |
| C6-005 | P3 | MA-09（主登记，吸收 C1-011） | P3 |
| C6-006 | P2 | 独立 | P2 |
| C6-007 | P3 | MA-09 | P3 |
| C8-001 | P2 | MA-01（降级并入 C3-001） | P2 |
| C8-002 | P1 | 独立 | P1 |
| C8-003 | P2 | MA-04 | P2 |
| C8-004 | P1 | MA-02（投递极性，与 C3-002 配对） | P1 |
| C8-005 | P2 | 独立 | P2 |
| C8-006 | P2 | MA-07 | P2 |
| C8-007 | P2 | 独立（与 C1-004 同站点异机制） | P2 |
| C8-008 | P3 | 独立（承接 C3-007 残留②/CL-004） | P3 |
| C8-009 | P2 | MA-07 | P2 |

### 统计与无遗漏声明

- **合并前 44 条 → 合并后 28 工单**：缺陷族 10（MA-01…MA-10）吸收 26 条 + 独立立案 18 + watch-only 0。26 + 18 + 0 = **44**，逐条落位无遗漏、无重复计数（族成员计一次）。
- **工单严重度分布**：P0×0（即时通道显式空执行——本单元无 P0 发现）/ **P1×3**（MA-02、MA-05、独立 C8-002）/ **P2×15**（MA-01/03/04/06/07/10 + C1-002、C1-012、C3-004、C3-005、C3-006、C3-009、C6-006、C8-005、C8-007）/ **P3×10**（MA-08、MA-09 + C1-008、C1-009、C1-010、C3-007、C3-008、C3-010、C5-006、C8-008）。
- **逐条严重度对账**：P1×4（C3-002/C8-004→MA-02、C6-002→MA-05、C8-002 独立）/ P2×26 / P3×14，与五卷复核判定表合计一致；与派发口径 P2×25/P3×15 的差 1 = C8-001 计数归属（§一.3）。
- **吸收关系**（复核登记确认 + 本次落位）：C1-006→C3-003、C1-011→C6-005、C8-001→C3-001 三条并入；C3-002↔C8-004 一对配对；CL-019/011/012/013/014/015/016/019/020/001/005/008/009/004/002/022/023/026/027 等线索随单（§八）。
- **进入 Phase 8 批量修复面**：P1×3 工单；P2×15 按 P2 裁决台账流程处置（cra-index §P2 台账 7.1 逐项裁决）；P3×10 按最小 diff 批次归并（MA-08/MA-09/各独立 P3 可与同文件 P2 工单同 PR 顺带）。

---

## 七、跨单元对账

> 对 WI 1.1–3.4 台账（cra-index.md）逐条比对结论：**cra-index 零同站点重复立项**——五卷复核均已完成 P1 台账逐条比对，本裁定复核确认；WI 1.6 C8-001（扫描器停摆）/ WI 2.1 C3-001（周期任务绝缘层）为本单元 C3-009/C8-001 的同名异站点，无关联。跨单元关联共 **8 处**：

| # | 关联 | 处置 |
|---|---|---|
| 1 | MA-01 ↔ WI 3.1 MA-11（DBMessageService 同 SPI 消费等待病） | 同契约异实现家族关联，consumeAwaitTimeout 契约对齐（§五-6），非重复立项 |
| 2 | MA-03 + C1-002 ↔ WI 2.5 消费面（C3-008 lag 探测键错位独立缺陷 / CL-010 watch 候选 / WI 2.5 C1-005 同名双引擎 P1） | 三面分层 + 消费面四方互斥成立；本单元为 REGISTRY **本体落点**（dispatch-log 预登记承接正确）；WI 2.5 CL-010 维持台账 watch |
| 3 | MA-09 ↔ WI 1.2 C3-014/MA-07（Debezium 停止契约无终结保证） | 使用面（nop-stream）/ 本体面（本单元 wrapper）双轨互补，修复批次对齐，非重复 |
| 4 | MA-10 ↔ WI 1.2 C3-006（FAILURE_LISTENERS replace+无条件 remove 同一缺陷） | 同案双轨互挂：本体面修复落点本单元，先案 P2 为严重度基线，P1↔P2 留 7.1；WI 1.2 C4 卷「严格配对」结论仅对泄漏面成立，与竞态面不冲突 |
| 5 | C3-009 ↔ WI 2.1 C3-001（周期任务平台异常绝缘层，P1） | 同族先例（含 WI 1.7 C6-001 Error 逃逸）；修复归属统一绝缘层批次，7 站点清单随单移交；族内判级统一时可升格（§五-4） |
| 6 | MA-04 ↔ WI 2.1 C4-001（globalTimer 死配置 + 共享定时器阻塞放大，P1） | 修复约束：C6-001 原建议「复用 globalTimer」不采，改专用可销毁 executor / 平台 IThreadPoolExecutor |
| 7 | C3-007 ↔ WI 2.3 CL-022（orTimeout 移交） | **移交落点闭环确认**：独立 grep 复现主代码恰 2 站点（kafka:313 / pulsar:273 + javadoc 1 行），三点疑问（取消传播/orTimeout 后行为/重试交互）落回已声明边界与 client 兜底，残留两点降 P3（本单 + C8-008），无未落点 |
| 8 | CL-003/CL-006/CL-010/CL-018（globalWorker 长驻占用，缺省池容 30）↔ WI 1.2 C4-004/MA-06 | 同面跨单元：本单元 overlay 不重复立案（复核确认正确），4 条线索移交 WI 1.2 MA-06 工单（观测子项随 CL-003） |

---

## 八、CL 线索处置表（CL×27 逐条落位）

> 处置统计：**并入工单 18 / 独立线索 4（含 1 条并线）/ 移交 5 / 驳回 0**。

| 线索 | 来源卷 | 处置 | 落位 / 依据 |
|---|---|---|---|
| CL-001 | C1 | 并入工单 | MA-04（= C6-001 同面：redelivery 调度器无关停路径） |
| CL-002 | C1 | 并入工单 | C8-005 工单（deferAfterBatch 无上限分片 sleep 与取消时延声明，delay 边界治理同批） |
| CL-003 | C1 | 移交 | WI 1.2 C4-004/MA-06（globalWorker 排队可观测性，占用本体面在 WI 1.2） |
| CL-004 | C1 | 并入工单 | C8-008（= 其失效面⑤：Pulsar sent 前置计数口径，两处同一事实） |
| CL-005 | C3 | 并入工单 | MA-03（= C6-004 清理调用点缺失面） |
| CL-006 | C3 | 移交 | WI 1.2 C4-004/MA-06（复核已确认「同面不重复立案」正确） |
| CL-007 | C3 | 独立线索 | doStop 串行 cancel N×6s 停机预算（方向安全，F-27 有意排序；7.1 决定立案与否，§五-8） |
| CL-008 | C3 | 并入工单 | MA-04（= C6-001/CL-009 同点） |
| CL-009 | C5 | 并入工单 | MA-04（C6-001 立案出处线索） |
| CL-010 | C5 | 移交 | WI 1.2 C4-004/MA-06（池容 30 容量数字随 MA-06；WI 2.5 侧 watch 身份维持） |
| CL-011 | C5 | 并入工单 | MA-02（= C3-002 零 listener 接线面） |
| CL-012 | C5 | 并入工单 | MA-02（round2 已并入扩展 C3-002：parseKey 抛点） |
| CL-013 | C5 | 并入工单 | MA-09（C6-005 立案出处线索） |
| CL-014 | C6 | 并入工单 | MA-06（= C1-003 同站点守卫次序面） |
| CL-015 | C6 | 并入工单 | C1-008（NPE 面同点；topic 键残留面随其修复建议 `consumers.remove(topic, list)` 一并落地） |
| CL-016 | C6 | 并入工单 | MA-04（= C5-003 内存面同点） |
| CL-017 | C6 | 独立线索 | Pulsar failureAttempts 在 maxConsumeAttempts=0（声明受支持）下只增不删（条件面 P3 级；C5 卷复核对照 3 建议与清单行 5/6 合读） |
| CL-018 | C6 | 移交 | WI 1.2 C4-004/MA-06（engine.run 独占 globalWorker 同面） |
| CL-019 | C8 | 并入工单 | MA-05（subscribe 站点三面之一，复核登记「三面归一」输入） |
| CL-020 | C8 | 并入工单 | C1-008（同一 NPE 面，逐字同点） |
| CL-021 | C8 | 独立线索 | 并入 CL-017 同一线索（Kafka 侧因 seek 钉键有界——同模式不同结局，修复时一并核对） |
| CL-022 | C8 | 并入工单 | MA-07（F-39 裁定 vs kafka-clients 行为的 docs-bug——随 max.block.ms 探针测试定案，走 docs-for-ai docs-bug 流程） |
| CL-023 | C8 | 并入工单 | C3-005（整批 nack 已应答前缀重复应答——at-least-once 内合法，声明补记随其 javadoc 批） |
| CL-024 | C8 | 移交 | 消费线程 IContext 传播专题（其自述：单点修会造成后端间语义分叉，7.1 指定归属，§五-8） |
| CL-025 | C3-round2 | 独立线索 | MessageConsumeFailureBridge.submit 无等待上界/无 pending 可观测（契约「辅助通道永不抛」在位，缺口纯观测性；随 C7 复核轨） |
| CL-026 | C3-round2 | 并入工单 | C3-005（catch 清理 lastFailureTopic 一行补齐，同方法顺带） |
| CL-027 | C8-round2 | 并入工单 | C8-005（单条 ConsumeLater 端点 F-08 同族声明缺副本——补一行注释指向 F-08） |

---

## 九、C10 缺保护登记映射（12 条随本体工单）

> C10 缺保护登记与本体工单分开计数（P2×9/P3×3），修复义务挂靠如下。反向钉死 5 处（4 硬钉 C3-004/C5-004/C1-012/C1-008 外泄面 + 1 软钉 C1-002 setOffsets 子面）为对应工单**强制前置联动**（先改测试再修本体）。C10-001 为缺保护升档首选候选（被保护本体含 P1×3，§五-3 裁定时联动评估）。

| C10 登记 | 严重度 | 保护本体落位 | 测试义务挂靠工单 |
|---|---|---|---|
| C10-001 DebeziumMessageSource 全类零测试 | P2 | MA-02（C3-002/C8-004）、MA-05（C6-002/C3-003+C1-006）、C6-006 | 新建 TestDebeziumMessageSource 四场景（幽灵订阅/毒事件两极/并发 subscribe×stop/同引用双订阅注销），随 MA-02+MA-05 修复批交付 |
| C10-002 wrapper 启停/通知协议零测试 | P2 | MA-10（C1-001/C5-002）、MA-09（C6-005+C1-011/C6-007）、C3-009 Debezium 面 | 竞态测试（两线程 register/unregister×并发 notify）+ 嵌入式 start 失败/quiesce/Error 必达测试，随 MA-10+MA-09 |
| C10-003 REGISTRY 生命周期与竞态无保护 | P2 | MA-03（C5-001/C6-004）、C1-002 | 生命周期/竞态/stale-resume 意向三测试，随 MA-03 契约裁决后落地 |
| C10-004 destroy 窗口守卫族无交错测试 | P2 | MA-06（C1-003/004/005/007/C6-003）、C8-007；**覆盖闭合挂靠：C3-004** | 每后端 sendAsync×init/destroy、subscribe×destroy、建连挂起三组压力测试；C3-004 负向断言随批 + 反向钉死 #1 改写 |
| C10-005 挂死/Error 僵尸面无保护 | P2 | MA-01（C3-001+C8-001）、C3-009 | 永不完成 future / StackOverflowError 注入 / Local 挂死变体三测试，随 MA-01+C3-009 |
| C10-006 Local 失败闭环与生命周期无保护 | P2 | C8-002、MA-04（C6-001/C8-003/C5-003 行为面）、C3-006、C1-008 | 契约骨架新增「失败派发」维度（Local 现状预期红）+ 生命周期/组合/并发压力测试；反向钉死 #4 触雷预警 |
| C10-007 ConsumeLater 预算闭环无保护 | P2 | C8-005（三后端扩展随单） | delay∈{0,-5,200} 参数化矩阵 + 有限轮自旋测试（MockConsumer 驱动 run 循环） |
| C10-008 reply 预算与 metrics 口径无保护 | P2 | MA-07（C8-006/C8-009）、C8-008；**覆盖闭合挂靠：C3-005** | metrics 调用点两断言（批失败 failedCount==1、cancel 拒绝 sentCount==0，现状预期红）+ reply 管道化耗时断言 + max.block.ms 探针 |
| C10-009 Pulsar 预取/缓冲配置面无保护且反向钉死 | P3 | MA-08（C5-004/C5-005） | batchReceivePolicy 缺省断言（**改写反向钉死 #2**）+ 三字段 builder captor 断言 |
| C10-010 配置冻结面无保护且活 map 反向依赖 | P2 | C1-012；**覆盖闭合挂靠：C5-006**（钉定族同在 TestDebeziumEngineProperties） | 先改 4 处 setup（反向钉死 #3）→ 冻结断言 → 并发写压力锚 |
| C10-011 消费失败可见性/契约完备无保护 | P3 | C3-008、C3-010、C1-010、C3-007 残留① | commitSync 失败注入 / 两后端 null 契约钉 / 可见性测试 / 双缺省负向断言 |
| C10-012 Pulsar suspend 生效语义无保护 | P3 | C1-009 | 复制 Kafka suspendStateAppliedByConsumeThreadAndReversedOnResume 形态（按 C1-009 最终裁定模式） |

- **覆盖闭合处置落定**（C10 复核唯一 Major 项，不新增独立严重度）：C3-004 → 挂靠 C10-004 失效面枚举；C3-005 → 挂靠 C10-008；C5-006 → 挂靠 C10-010。44 行矩阵对账零差异维持。
- **行号勘误采用**：修复工单引用 C10 证据行号以 C10 复核结论 §2 修正项为准（TestPulsarServiceLifecycle :167-202/:138-164/:416-439/:442-456 等）。

---

## 裁定收口

- 44 条保留发现 → **28 工单**（10 缺陷族吸收 26 条 + 18 独立 + 0 watch-only），P0×0 / P1×3 / P2×15 / P3×10；逐条落位无遗漏、无重复计数。
- 跨单元关联 8 处单列（零同站点重复立项）；判级分歧 4 项 + 设计裁决 4 项登记 §五 移交 7.1。
- CL×27 全部落位（并入工单 18 / 独立线索 4 / 移交 5 / 驳回 0）；C10 缺保护 12 条全部映射随本体工单，覆盖闭合 3 行挂靠落定，反向钉死 5 处列为强制联动义务。
