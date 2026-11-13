# PubSub 订阅/连接生命周期与链编排 fencing 契约（2.5 peri 批 11 项裁定）

> Status: active
> Source: WI 2.5 审计目录 `ai-dev/audits/2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`（C6-resource-lifecycle/C8-backpressure/C7-long-running/C3-async-chains/C1-shared-mutable-state 各 round + merge-adjudication §一/§三/附录 O-1 + C10-test-protection RP-1/RP-2）；批次成员表、站点行号基线与勘误登记以对应执行计划为准（本文档为最终状态文档，勘误不落此处，行号漂移以 live 为准）
> 实现锚：nop-nosql-lettuce `impl/LettucePubSubService`（subscribe synchronized :72-94 / getOrCreateConnection :96-105 / doStop :112-117 / SubscriptionEntry listeners CHM :121、addListener size==1 门 :127-132、removeListener :134-139、message 内联投递 :145-161 / PubSubSubscription.cancel :190-200、suspend :213-218、resume :221-226）；nop-orm-migration `IncrementalJobManager`（runningJobs CHM :50 / startFallbackChains 循环 :121-141[connectorName 派生 :136] / startChain :146-188[jobName :174、thread.start :184 先于赋值 :185、put :186] / stopAll :201-206 / RunningChainJob :211-244[thread volatile :214、failure :215、getFailure :231-233、stop :235-237、join :239-243]）、`OrmMigrationOrchestrator`（runIncremental :193-212 / runVerify :218-271[connector 取名 :228、awaitLagZero :231] / confirmSwitch :277-305 / completeSwitch :310-323 / abort :329-346 / stopIncrementalChains :401-409 / incrementalManager :521-531）、`MigrationJobStateStore`（DDL :32-43 / update 全列覆盖 :100-118[WHERE :114]）、`MigrationPhase`（ALLOWED :36-43 / checkTransition :56-63）、`ShardChangeEventRouter`（route relocate 分支 :40-54[空串 :42] / belongsTo :59-61）、`TargetShardMapping`（PhysicalTable :53-89 / routeRow :235-246 / routeShardValue :251-262 / mapQuerySpace :298-306）、`IncrementalLagProbe`（awaitLagZero :52-57 / snapshot :71-76 / offsetsFingerprint :81-90）；nop-db-migration `core/MigrationHistoryManager`（ensureHistoryTableExists check-then-act :72-86 / recordMigration UPDATE-then-INSERT :88-139）、`core/MigrationEngine`（启动期 :124-129、getExecutedVersions :144、recordMigration 写点 :203/:212）；nop-api-core `IMessageConsumer.onMessage` 返回 Object :26（CompletionStage 为合法取值形态）
> Related：[registry-lifecycle-contract.md](registry-lifecycle-contract.md) 裁定⑥（4.1 REGISTRY 契约域对齐注记零触碰，裁定③⑤⑨消费面）；[wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md) 裁定⑥（条件 UPDATE/租约形态菜单，裁定⑤引用不重建）；[orm-session-txn-atomicity-and-model-cache-generation.md](orm-session-txn-atomicity-and-model-cache-generation.md)（2.4 同单元先例：模型发布 volatile / 写门租约复核 / shardSelector 热替换）；[stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md)（R03 出锁投递范式，裁定②引用不重开）；R19 前案 `ai-dev/audits/` check/nosql-cdc [P2]（裁定①同站点已知未修复前案，收口时消案注记）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 2.5-C6-004（P1；←C1-011/C3-004 双并入[MA-01 闭三角]；C10-022） | cancel/suspend/resume 入 subscribe 同一服务级监视器（单锁域收敛）+ cancelled 重入守卫 + 锁内空判复查 + 订阅属主校验 + doStop 停机对账（与裁定③对账面共享）；C1-011/C3-004 吸收面同 diff 闭环；R19 前案消案注记 |
| ② | 2.5-C8-001（P1；←C3-005/C7-010[MA-02 闭三角]；C10-023） | 消费外移 = nop-nosql-lettuce 模块内专用单投递线程有界 executor（预设，零 nop-api-core 触碰）；返回值契约 = CompletionStage 有界超时告警 + 其余非 null 返回值 warn-once（不等待完成）；拒绝策略 = onConsumeFailure 类型化回调留痕（禁静默丢弃）；慢消费者隔离边界显式登记；R03 范式引用不重开；MA-02 反向钉死改写 = 七钉中唯一 :586 一钉 await 化（§5.3 逐条确认） |
| ③ | 2.5-C7-014（P1；C10-025） | 重订阅主机制 = lettuce activated() 自主 resubscribe（7.5.2.RELEASE 字节码亲读证据：`StatefulRedisPubSubConnectionImpl.activated()` 偏移 5 调 `resubscribe()` 逐 future 挂 exceptionally——F5「订阅全丢静默失联」前提证伪，服务零代做）；服务侧唯一修复面 = 可观测：连接激活事件侦听（IO 线程只投递事件至专用对账线程）+ 专用线程对注册表快照发幂等确认 SUBSCRIBE + 确认失败 WARN/计数；原「锁内全量 re-subscribe 对账」主形态废除；竞次面约束保留（确认只订注册表快照内条目，禁复活已 cancel 订阅）；与 4.1 REGISTRY 契约域对齐注记零触碰 |
| ④ | 2.5-C6-003（P1；←C1-004[MA-04]+CL-009） | thread 构造期注入 RunningChainJob（发布先于 start，预设）；stop() cancel→有界 join→超时 interrupt→句柄保留 + **退出确认复核**（isAlive 仍活则重入被拒，M4）；stopAll 汇聚逐链串行 join、等待上界编排器 setter 配置化（零 @cfg: 键）；join null 短路显式 WARN 留痕（禁静默）；与既有 stopIncrementalChains 收敛点同变更集（MA-03 绑定，既有双钉零弱化） |
| ⑤ | 2.5-C7-003（P1；←C1-005/006/009[MA-05，X-1]；C10-011） | 状态行 UPDATE 加期望相位谓词（`WHERE job_id=? AND phase=?`，预设，零 DDL 变更）+ 第二写 updated==0 类型化失败（跨进程 fencing）；编排 API 进程内 per-jobId 单飞（AtomicReference putIfAbsent fail-fast）；incrementalManager() synchronized double-check；**跨进程残余窗显式接受**（§3.5）；plan 2514 前案形态菜单引用不重建（以设计文档为准）；C1-008 文件级接缝登记不代做 |
| ⑥ | 2.5-C7-001（P1；←C3-002+CL-006/018/023[MA-06]；C10-004） | 编排器层消费 getFailure：runVerify 前置检查链 failure 非空即 gateFailed 类型化拒绝 + stopIncrementalChains 后残留 failure 复核 + manager==null 且 phase==INCREMENTAL → 类型化拒绝重入（`incremental-manager-missing-restart-reentry`）[R2 修订]；仅非增量路径才留痕跳过；probe 层零改动（RP-2 硬约束，双钉保持绿）；**硬前置 = 裁定⑨先行落地**；死链即 fail 门禁语义成文 |
| ⑦ | 2.5-C3-001（P1；←C6-001[MA-10]；C10-002） | startFallbackChains 循环 catch 后回收已启动链（逐链 stop+join 复用裁定④原语）+ 相位后翻 ABORT 显式；jobName 键派生纳入 querySpace（与裁定⑨同一单点派生函数）；runningJobs.put 改 putIfAbsent fail-fast（同名重入类型化拒绝）；X-1 与裁定⑤同 diff 协调不并案 |
| ⑧ | 2.5-C1-003（P1；CL-022 吸收；C10-021） | 跨进程互斥 = 连接级 DB 咨询锁串行化 DDL 守卫与 recordMigration 复合写段（mysql GET_LOCK / postgres pg_advisory_lock；H2 及未列方言回落条件 INSERT 单行守卫表，方言矩阵见 §3.8）；recordMigration 原子化 = 条件 INSERT 捕获唯一冲突回落 UPDATE；**RP-1 硬约束：upsert 覆盖语义保留（禁「冲突即拒绝」方向）**；写失败保留钉零弱化 |
| ⑨ | 2.5-C3-008（P1；C10-005） | connector 名派生单点函数（base/base-i 枚举清单）+ RunningChainJob 构造期携带 connectorName + runVerify 逐链探测聚合判据（M>1 全链 offsets 指纹 + ledger 单静默窗）；**qs 身份键 SSOT**（R4-20 C7-008/C3-010/C3-011 消费不重建）；全批最早落地项（先于裁定⑥） |
| ⑩ | 2.5-C3-009（P1；C10-006） | route relocate 分支 querySpace 从 mapping 正确派生（TargetShardMapping 新增 relocateTarget：单源取唯一目标；多源按 mapQuerySpace 派生，源 qs 不可得显式失败不猜测——预设，零新注入点）；FULL 侧 routeRow/routeShardValue relocate 分支同病核对登记（超 router 面登记边界不扩批） |
| ⑪ | 2.5-C7-002（P1；←CL-007/008[X-2]；C10-015） | 失败相位白名单受控自环（INCREMENTAL→INCREMENTAL、VERIFY→VERIFY，仅经既有编排 API 显式重入 + 进程内重试计数上界缺省 3，非全开）；既有六钉零改写保持绿（亲读核实无 X→X 非法断言）；自环写与裁定⑤谓词协同（期望相位=当前相位，abort 竞写时自环写显式失败）；备选 RECOVERY 相位边被拒 |
| ⑫ | 测试缝与零回退钉 | 红①–红⑪驱动器（§5.1）+ 零回退钉全列与 MA-02 await 化逐条确认（§5.3）+ C10 十二条集落名（§5.7） |

## 二、背景与动机

2.5 peri 批 11 项 P1 覆盖 nop-persistence 三模块四类缺陷面：**PubSub 订阅链断链三单**（订阅管理竞态绕过单监视器使订阅静默失效或复活双投递、消费回调在共享 Redis IO 线程内联执行使慢消费者队头阻塞断链且返回值契约被丢弃、断线重连后 lettuce 自主重订阅但服务侧恢复零留痕失败零告警[勘误后收窄口径，见 §3.3]）、**链停止协议不可验证**（thread 晚发布 join 空转、stopAll 仅 cancel 不等待）、**链编排零 fencing**（状态行全列覆盖无谓词使 abort×confirmSwitch 背离终态、编排入口零单飞、惰性管理器双实例、链死亡不可见、部分启动无补偿、jobName/connector/route 三处键派生错位、状态机零自环使 fix-forward 不可达）、**迁移历史表非原子守卫**（check-then-act DDL 竞态 + FAILED 脏行覆盖 success 行永久重试循环）。本文档一次裁定 11 项 + 测试缝，使实施只有一种合法形态；PubSub 三单（裁定①②③）为同一连接/订阅生命周期的统一设计，链编排七单（裁定④⑤⑥⑦⑨⑩⑪）共享 qs 身份键 SSOT 与停止协议原语。

## 三、核心设计契约

### 3.1 裁定①：订阅管理监视器化（C6-004）

- **单锁域收敛 = 服务实例 monitor（`synchronized(LettucePubSubService.this)`）**：subscribe 既有 synchronized（:72）为锚，cancel/suspend/resume（:190-226）的全部共享态变更（entry.listeners 增删、`isEmpty` 判定、`unsubscribe`、`subscriptions.remove`、suspended/cancelled 翻转）收敛入同一锁域——三步 check-then-act（removeListener → isEmpty → unsubscribe+remove）在锁内原子化，锁内空判复查消除 addListener×removeListener 交错下 `size()==1` 门误判（addListener 的 connection.addListener 泄漏）。suspend/resume 的 suspended/cancelled 翻转同样入锁（volatile 字段保留作锁外读面 isSuspended/isCancelled）。
- **cancelled 重入守卫（锁内）**：cancel 后对同一 subscription 的 suspend/resume/cancel 重入为显式 no-op + 留痕（resume 对 cancelled 恒拒绝——既有 :222 判定保留并入锁）；cancelled 置位与 removeListener 同锁内完成，消除「cancelled=true 但 listener 仍在投递」窗。
- **订阅属主校验**：PubSubSubscription 携带自身 listener 引用，cancel/suspend/resume 仅作用于**本 subscription 的 listener**（结构性已成立——entry.removeListener(listener) 以持有引用为参）；裁定将「属主」契约显式化为：一个 IMessageSubscription 实例只管理自己 subscribe 时注册的 listener，不提供按 topic 取消他方订阅的路径；取消整个 topic = 逐 subscription cancel（最后一个属主 cancel 触发锁内 unsubscribe）。
- **doStop 停机对账**：doStop（:112-117）改为锁内遍历 subscriptions 注册表逐 topic `unsubscribe` + 清空注册表，再 close 连接——停机后 Redis 侧订阅注册与进程内注册表一致，无残留半订阅（与裁定③对账面共享同一「注册表=SSOT」不变式）。
- **C1-011/C3-004 吸收面闭环**：C1-011（check-then-act 复合非原子）由锁内原子化承载；C3-004（suspend/resume 绕监视器复活双投递）由同锁域 + cancelled 守卫承载——两者随同一 diff 闭合，不单列工单。R19 前案（check/nosql-cdc [P2] 同站点）收口时在 cra-index C6-004 行消案注记。
- **锁域/线程域硬条款（M1，一次写死）**：(a) **lettuce IO/event-loop 线程禁取服务监视器、禁发阻塞 Redis 命令、禁执行消费者回调**——`SubscriptionEntry.message` 仅入队（裁定②）、裁定③激活事件回调只投递专用线程，全路径硬条款；(b) **监视器内阻塞 Redis 往返（subscribe :82 / unsubscribe :196）选定 = 保留 + 显式接受队头阻塞代价**：订阅管理为运维低频操作，监视器竞争面仅并发订阅管理调用、不含投递路径（投递已外移）；往返受命令超时预算常量（缺省 5s，经 lettuce sync 超时采纳）约束，超时异常走 subscribe 既有回滚路径（:83-89 锁内登记回退）。备选（锁内登记 + 锁外发送 + 锁内终态复核）被拒：unsubscribe 锁外发送窗与同 topic 并发 subscribe 登记交错产生「取消在途×新订阅已登记→Redis 侧已退订而注册表有活条目」复活态，终态复核 + 补偿重发的复杂度与残余面劣于显式预算接受。

### 3.2 裁定②：消费外移与返回值契约（C8-001）

- **执行器落点定夺（未决一）= nop-nosql-lettuce 模块内专用 executor（预设分支采纳）**：`LettucePubSubService` 持有一个实例私有 `ThreadPoolExecutor`——**单投递线程**（线程名前缀 `nop-lettuce-pubsub-consumer`，保持与 Redis 单连接投递序一致的 FIFO 序）、**队列有界**（缺省 1000）、生命周期随 doStart/doStop。**nop-api-core 平台设施形态被拒**：R03 出锁投递范式（stream-buffer 先例，引用不重开）已证明模块内 executor 足以闭合「IO 线程内联」缺陷；IMessageService/IMessageConsumer SPI（nop-api-core）零签名变更，平台设施无新增能力需求，引入即触发 plan-first 申报且无对应收益。**头部条件申报条款保留备用**（预设不触）：若实施中确需在 `io.nop.api.core.*` 新增平台工具/接口，须先在执行计划 PA 行 + 本文档修订 + draft review 申报并列全 SPI 回归面（IMessageService 全实现 live grep 全列）方可实施。
- **投递形态**：`SubscriptionEntry.message`（:145-161）改为**仅入队**——IO 线程构造投递任务（携带 channel/message/listeners 快照）提交 executor 后立即返回；消费回调（onMessage/onConsumeFailure）在投递线程执行。listeners 迭代快照在入队时取（锁外 CHM 快照即可，与裁定①锁域无嵌套）。
- **返回值契约定夺 = 有界超时告警（不等待完成）**：onMessage 返回 `CompletionStage` 时，投递任务挂有界观测（缺省常量 60s）——超时未完成 WARN 留痕（不取消、不重试、不阻塞）；同步返回非 null 且非 CompletionStage（ConsumeLater/响应消息形态）在 fire-and-forget 传输无消费语义，warn-once 显式登记（沿本服务 D1 warn-once 先例）。备选「等待完成」被拒：投递线程内同步等待复活慢消费者队头阻塞（同线程后续任务被阻塞），与外移目标自相矛盾。**能力边界声明（owner doc PubSub 纪律节落点）**：Redis Pub/Sub 无消费组无持久化，ack 语义不可承载——返回值仅作可观测告警面，可靠投递应选 nop-message 持久化通道。
- **拒绝策略显式（禁静默丢弃，rule 24）**：队列满拒绝时执行 `consumer.onConsumeFailure(new ConsumeFailure(channel, channel, message, 0, rejectCause))` 类型化回调留痕（复用既有 D6 层一路径），绝不静默吞弃。**内联豁免面声明**：拒绝回调在 IO 线程内联执行（队列满时无法再入队）——该内联为登记豁免，契约约束 = `onConsumeFailure` 实现方不得阻塞/发起 Redis 调用（仅日志/计数语义；IMessageConsumer 默认实现 WARN + bridge 已合规，owner doc 纪律节成文）。**慢消费者隔离判据**：队列高水位（缺省 80%）周期 WARN（可观测指标见 §3.12）；**跨 topic 慢消费者隔离登记为非目标**——单投递线程保序但慢消费者仍延迟他 topic 投递（不再断链丢消息），per-topic 队列隔离属后续扩展接缝，不在本批。
- **停机排空语义（定夺）**：doStop 顺序 = 先停订阅入口（裁定①停机对账，注册表清空后无新消息入队）→ executor `shutdown()` + **排空残余队列**：队列内未投递消息逐条经 `onConsumeFailure(ConsumeFailure(..., stopped))` 留痕后丢弃（禁静默消失）→ `awaitTermination(有界)` → 连接 close。**60s 完成观测定时器载体（定夺）** = 平台 `globalTimer`（nop-commons executor 设施，只消费零触碰——pool-facility-hardening 契约域消费面）：投递任务内对 CompletionStage 挂 `whenComplete` + globalTimer 调度一次性超时检查任务（完成即取消），不新增服务内调度线程。
- **MA-02 反向钉死改写义务履行方案**：见 §5.3 七钉逐条确认——唯一改写钉 = `testPubSub_UnsubscribeStopsReceiving`（:586），其余六钉零改写；语义对照随同 diff 留痕。

### 3.3 裁定③：重订阅恢复可观测（C7-014，F5 前提证伪收窄）

- **B1 前提复核（字节码亲读证据，推翻原审计 F5）**：本机 `~/.m2` lettuce-core 7.5.2.RELEASE jar `javap` 反编译亲读——`StatefulRedisPubSubConnectionImpl.activated()` 偏移 5 直接调用 `resubscribe()`，后者对端点全部 channel/ssubscribe/pattern 订阅重发 `async().subscribe(...)`/`ssubscribe(...)` 并逐 future 挂 `exceptionally` 内联处理器。**断线重连后重订阅由 lettuce 自主完成，「订阅全丢静默失联直至重启」前提不成立**；原「锁内全量 re-subscribe 对账」主形态废除（对账重做 lettuce 已做的事，且与服务监视器构成 IO 线程锁交互面）。
- **收窄后唯一结论 = 恢复可观测（服务侧零代做重订阅）**：
  - **连接激活事件侦听**：连接激活/重建事件经 provider 面的 lettuce 事件通道（或等价回调）侦听；**硬条款（M1 线程域）**：事件回调跑在 lettuce IO/event-loop 线程上，只允许把「激活事件」投递至服务专用对账线程（单线程，随服务生命周期），**禁止在 IO/event-loop 线程取服务监视器、发阻塞 Redis 命令或执行消费者回调**（`SubscriptionEntry.message` 仅入队已合规，成文为全路径硬条款）。
  - **幂等确认 SUBSCRIBE**：专用对账线程收到激活事件后，取服务监视器拷贝注册表快照（锁内只读拷贝），**锁外**对快照内逐 topic 发 `sync().subscribe(topic)` 确认——Redis SUBSCRIBE 幂等（已订阅通道再订阅仅确认语义，零双投递），确认失败（抛错/超时）即 `nop.lettuce.pubsub.resubscribe-failed` WARN + 失败计数。**为什么不由服务挂 activated() 内部 future**：`activated()` 返回 void、resubscribe future List 为内部局部变量经 exceptionally 内联消费，外部不可钩挂（字节码亲读）；幂等确认是唯一可从服务侧落地的失败观测通道。
  - **竞次面约束保留**：确认只订**监视器内拷贝的注册表快照条目**——已 cancel 订阅在 cancel 时已锁内移除，结构性不复活；确认 SUBSCRIBE 在锁外发送，与裁定①监视器零阻塞交互（拷贝-释放-发送三段）。
  - **residual 登记**：lettuce exceptionally 内部失败路径为 DEBUG 级不可钩挂——传输层边界 residual（服务侧幂等确认失败告警为其上层补偿观测）；确认窗内到达的消息由重连后端点续投，不因确认动作丢失（SUBSCRIBE 幂等）。另：cancel×断线窗竞态可产生 Redis 侧幽灵通道（UNSUBSCRIBE ack 丢失于断线窗，重连 activated() 全量 resubscribe 复活已移除通道；无 listener 零回调零双投递，连接 close 兜底）——接受不处置[R2 watch-only 成文化]，确认 SUBSCRIBE 只订快照条目不清幽灵，实施者不得代清。
- **原备选（心跳对账）与原主形态（锁内全量 re-subscribe 对账）均废除**：前者常驻探测负载无收益，后者与 activated() 自主恢复重复建设且引入 IO 线程/监视器交互面。
- **与 4.1 REGISTRY 契约域零触碰声明**：本裁定只涉 lettuce PubSub 连接生命周期，`NopStreamOffsetBackingStore` REGISTRY 契约（registry-lifecycle-contract.md 裁定⑥）对齐注记关系，零代码触碰。

### 3.4 裁定④：链停止协议（C6-003）

- **发布序修正 = thread 构造期注入（预设）**：`RunningChainJob` 构造函数增加 `Thread` 参数，线程对象在 `start()` 之前构造并注入（`running.thread = thread` 字段直写赋值移除）——句柄发布先于线程启动，`join` 无 stale-null 空转窗。备选（start 前字段赋值）被拒：构造期注入使「未发布即未启动」由类型系统承载，赋值遗漏面结构性消除。
- **stop() 四段式 + 退出确认（M4）**：`source.cancel()` → `thread.join(上界)` → 超时 `thread.interrupt()` → **句柄保留至线程退出**（stop 不置 null 不移除，线程终止由 join 返回或中断后自然退出承载）→ **退出确认复核**：interrupt 后有界再等（宽限 join，短上界）后 `isAlive()` 复核——仍存活即链线程未确认退出，句柄存留并置「未退出」标记，后续编排重入（裁定⑪自环 runIncremental 重入前的链状态检查、runVerify 死链检查同域）**gateFailed 类型化拒绝重入** + WARN 留痕——防半终止链与新链双写同目标表（Debezium engine 半终止态的下游后果拦截）。变异探针纪律适用（恢复路径真实可执行断言，归 Phase 2）。
- **stopAll 汇聚 = 逐链串行 join、等待上界配置化（m5）**：stopAll 遍历 runningJobs 逐链执行五段式 stop，全部 join 完成后才 `runningJobs.clear()`（:205 现状「仅 cancel 立即 clear」废止）；单链 join 上界由编排器 setter 注入（缺省常量 10_000ms；**零新 `@cfg:` 配置键**）；**总时间预算登记**：串行 join 下 stopAll 总上界 = 单链上界 × runningJobs.size()（含宽限），链数 = 目标分片数（量级小），串行最简实现显式接受（并行 join 备选被拒：增加汇聚同步面，收益不抵复杂度）。
- **join null 短路显式化（禁静默）**：thread==null（构造异常未达 start 的残余态）时 join 路径显式 WARN 留痕「chain thread handle missing」，不再静默跳过；stop 语义不受影响（cancel 面照常执行）。
- **MA-03 同变更集绑定**：本裁定与既有 `stopIncrementalChains` 收敛点（编排器 :401-409 字段直读不惰性建管理器 + `TestMigrationSwitchStopsChains` 双钉 :76/:104）同一变更集承载——原语（五段式 stop 含退出确认）与调用点（confirmSwitch :303 / completeSwitch :317 / abort :337）一批修；既有双钉零弱化为同变更集约束面。

### 3.5 裁定⑤：状态行相位谓词 + 编排单飞/fencing（C7-003）

- **谓词形态定夺 = 期望相位谓词（预设，零 DDL 变更）**：`MigrationJobStateStore.update`（:100-118）签名增期望相位参（或等价期望态快照），UPDATE WHERE 变 `job_id=? AND phase=?`——编排器每次写前捕获 state.getPhase() 为期望相位，`updated==0` 经**二次 load 消歧**（m4）显式失败：UPDATE 失败后编排器 `store().load(jobId)` 复读——行缺失 → `ERR_MIG_JOB_NOT_FOUND` 语义；行在且相位≠期望 → **跨进程 fencing 拒绝**（类型化异常 + 期望/实际相位参数，禁吞，rule 24）；行在且相位==期望（理论不可达，防御分支）→ 显式重试一次 UPDATE 后仍失败即抛——abort（VERIFY 期望）×confirmSwitch（VERIFY 期望→写 SWITCH）并发时恰好一写成功、另一写 updated==0 显式失败——终态背离窗口闭合。**备选（乐观版本列）被拒**：需 DDL 变更（自治表加列 + 既有行迁移）且对主导缺陷面（abort×confirmSwitch 跨相位背离）无超出相位谓词的保护增益；版本列唯一增益在「同相位并发写」序化，该面由进程内单飞承载（下条）。**plan 2514 前案（WfTaskScanner fencing 形态菜单：条件 UPDATE/rowsAffected==1/租约/列集约束）引用不重建**——本裁定为其条件 UPDATE/rowsAffected==1 分支在状态行站点的直接实例化，以本文档为该站点形态权威。
- **编排 API 进程内单飞**：编排入口（runIncremental/runVerify/confirmSwitch/completeSwitch/abort，resume 同域）以 per-jobId 单飞域互斥（`ConcurrentHashMap<String, AtomicReference<Boolean>>` 或等价 putIfAbsent fail-fast）——同 jobId 两入口并发时败者类型化拒绝（禁排队等待：编排 API 为运维显式低频调用，排队掩盖误用）；跨 jobId 零互斥。`incrementalManager()`（:521-531）惰性访问器改 synchronized double-check（C1-005/C1-009 吸收面），`mapping()`/`fullJob()`/`verifier()` 同型惰性访问器同 diff 一并收敛（同文件同机制邻接面）。
- **跨进程双跑残余窗显式接受（未决二）**：进程内单飞 + 相位谓词将跨节点双跑窗从「无条件双跑双链」收窄为「两节点同相位同窗口并发推进且各自通过谓词」——**该残余窗显式接受为 residual**，理由四条：(1) 编排 API 为运维显式触发的低频操作，双节点同窗口人为并发推进非常态路径；(2) 收窄需跨节点互斥原语（咨询锁/租约），与裁定⑧（C1-003）的互斥选型属同一租约族对齐面，本批引用不重建、不重复建设第二套跨进程互斥；(3) 谓词拒绝已把背离后果收敛为显式失败（第二写 updated==0 抛错），无双写静默语义；(4) 链作业双启动另受裁定⑦ putIfAbsent（进程内注册表层）部分拦截。后续如需收窄，走租约族对齐选型并修订本文档（不得静默降级谓词为告警）；closure audit 抽查该 residual 登记。
- **C1-008 文件级接缝登记**：C1-008（R4-20）full_task_name 写点（executeFull :378 同文件）不属本批缺陷面，本批改动不代做；同 diff 义务登记为文件级接缝由 R4-20 承接。
- **X-1 同 diff 协调（与裁定⑦，不并案）**：`runningJobs` 注册表共享站点——裁定⑤单飞/putIfAbsent 改造与裁定⑦回滚回收/jobName 键派生一批设计；两工单台账独立收敛。

### 3.6 裁定⑥：死链 fail 门禁（C7-001）

- **预设 = 编排器层消费 getFailure**：(a) runVerify 前置检查——编排器从 incrementalManager 取运行链快照（裁定⑨新增快照访问面），任一链 `getFailure()` 非空即 `gateFailed` 类型化拒绝（gate 标识 `incremental-chain-failed:<jobName>`），VERIFY 放行不再可能越过死亡链；(b) stopIncrementalChains（:401-409）后逐链残留 failure 复核——非空 WARN 留痕（停止协议异常终止可观测）；(c) **manager==null 且状态行 phase==INCREMENTAL 时 runVerify 类型化拒绝**（gate 标识 `incremental-manager-missing-restart-reentry`，要求调用方显式 resume 或重入 runIncremental 重建链后再 VERIFY）——重启场景「进程内句柄消亡 + lag 观测量平凡通过」重开死链洞的面闭合（M2）；manager==null 且 phase 非 INCREMENTAL（无增量形态 FULL→VERIFY 路径）才显式留痕跳过该检查（LOG.info 登记跳过原因——合法路径，非静默）。
- **RP-2 硬约束 = probe 层零改动**：`IncrementalLagProbe` 的 lag 语义（冻结=追平）与两钉（`TestIncrementalLagProbe#testLagZeroWhenNothingAdvances` :42 / `testLagZeroWithStaticOffsetsAndLedger` :90）零回改；本裁定不向 probe 注入链活性句柄（该备选触发 RP-2 改写义务，被拒）。唯一允许的 probe 触碰见裁定⑨聚合入口（加法式、钉语义零变化，§3.9）。
- **硬前置 = 裁定⑨先行**：M>1 下键错位未修时 lag 观测量恒空，门禁修复无效——执行次序写死：裁定⑨ 单独先行或与⑥同变更集且⑨变更在前。
- **吸收面**：C3-002（fail-fast 契约 javadoc 有实现无）、CL-006/018/023 随本裁定同 diff 闭环；死链即 fail 门禁语义随 owner doc「ORM 迁移编排并发契约」节落账（Phase 3）。

### 3.7 裁定⑦：部分启动补偿与 jobName 键族（C3-001）

- **部分启动补偿**：startFallbackChains 循环（:134-139）catch 单链启动异常后**回收已启动链**（逐链五段式 stop——复用裁定④原语，有界 join）+ 相位后翻显式（编排器将状态行落 ABORT + abortReason 留痕，沿 INCREMENTAL→ABORT 既有合法边），再类型化抛出——已启动链滞留为孤儿不可停的面闭合。
- **jobName 键派生纳入 querySpace**：jobName（:174）改为 `"orm-migration-incremental-" + jobId + "-" + target.getQuerySpace() + "-" + target.getTableName()`，与 connector 名派生同属**qs 身份键单点派生函数**（裁定⑨同一模块内单点）——多 querySpace 同名目标表撞键覆盖消除。
- **runningJobs putIfAbsent fail-fast**：startChain（:186）put 改 putIfAbsent，非 null 返回（同名重入）即类型化拒绝并回收本次已建资源——同 jobName 二次启动静默顶替旧句柄（孤儿链不可停且双写同目标表）的面闭合；与裁定⑤单飞面协同（X-1）。
- **C6-001 吸收面**：put 覆盖单点随本裁定闭合。**X-1 不并案声明**：与裁定⑤台账独立收敛、cra-index 各行括注「同 diff 协调（X-1）」。

### 3.8 裁定⑧：迁移历史表跨进程互斥（C1-003）

- **互斥形态定夺 = 连接级 DB 咨询锁（预设）串行化 DDL 守卫与 recordMigration 复合写段**：互斥作用域 = `ensureHistoryTableExists`（:72-86 check-then-act）DDL 段 + 按版本键域的 recordMigration 写段（MigrationEngine :203/:212 写点经 manager 互斥面进入）。方言矩阵核实表（随实施核实，行为以各方言文档为准）：

| 方言 | 互斥原语 | 释放 | 获取失败语义 |
|------|---------|------|-------------|
| mysql | `GET_LOCK(锁名, 超时)`（连接级） | `RELEASE_LOCK`（同连接 finally） | 超时/失败类型化异常，启动重试面承接，非静默继续 |
| postgresql | `pg_advisory_lock(key)`（session 级，同连接） | `pg_advisory_unlock(key)`（finally） | 同上 |
| H2 及未列方言 | **条件 INSERT 单行守卫表**（独立守卫表，单行主键 + owner 标识 + timestamp 列；条件 INSERT 成功=持锁；表创建自身以 `CREATE TABLE IF NOT EXISTS` 幂等承载，无二层蛋鸡问题） | **finally 显式 DELETE 本 owner 守卫行**（带 owner 比对——只删自己插入的行）；**崩溃残留显式声明**：进程崩溃未走 finally 时守卫行残留 → 人工恢复（DELETE 残留行）——**H2 为测试方言，崩溃残留人工恢复可接受，成文登记**；生产方言（mysql/pg）走连接级咨询锁，连接断开自动释放无此残留面 | 唯一冲突=持锁方在，类型化失败重试 |

  同连接咨询锁承载前提：互斥段内全部 SQL 经同一连接执行（runWithConnection 包络）；锁获取超时值常量缺省（零 @cfg: 键）。**方言 seam 与测试面（m3）**：互斥原语抽 `IMigrationLockGuard`（acquire/release）模块内接口 seam——H2 守卫行实现离线可跑为缺省测试方言；mysql/pg 咨询锁实现离线以 seam 单测断言到 SQL 文本级，真库行为用例 docker-gated（环境在案时补跑留痕，沿勘误 9 三联口径）。
- **recordMigration 复合原子化（RP-1 硬约束内）**：保留 UPDATE→0→INSERT 顺序，INSERT 改**条件捕获唯一约束冲突**（捕获后回落 UPDATE 一次，有界）——多节点并发 UPDATE 双 0 行时恰一 INSERT 成功、败者回落 UPDATE 覆盖，终态单行无主键冲突炸裂；**upsert 覆盖语义完整保留**（`TestMigrationHistoryManager#testRecordMigrationReplacesExistingRecord` :253 RP-1 钉保持绿；`#testRecordMigrationKeepsExistingRowWhenWriteFails` :299 写失败保留面零弱化）。**「冲突即拒绝/insert-only」方向禁用**（RP-1 违反即反向钉死改写义务触发）。FAILED 脏行覆盖 success 行的永久重试循环由互斥串行化迁移执行本体消除：同版本并发执行被互斥拒绝，仅单节点执行并落账。
- **备选（方言化 upsert：INSERT ... ON DUPLICATE KEY UPDATE / ON CONFLICT）被拒**：方言矩阵分叉（H2 形态差异）+ 与互斥面重复建设；条件捕获形态方言中性。CL-022 吸收面（同位点线索）随本裁定闭环。租约族对齐选型引用声明：互斥原语与 WI 1.5/1.6 租约族为对齐关系（引用不重建，零代码触碰）。

### 3.9 裁定⑨：connector 键派生单点化（C3-008，全批最早落地）

- **派生单点**：connector 名派生（:136 `targets.size()==1 ? base : base + "-" + i`）抽模块内单点函数——输入（baseConnector, 目标数）→ 有序名清单（base 或 base-i 全枚举）；startFallbackChains 消费之，`RunningChainJob` **构造期携带 connectorName 字段**（与裁定④构造期注入 thread 同法），链注册名在句柄上可观测。
- **runVerify 逐链探测聚合判据（一次定夺）**：runVerify（:228）不再取原始 base 名直传 probe；改为编排器从 manager 运行链快照取每链实际 connectorName，**M>1 全链指纹聚合**：单静默窗内逐链取 offsets 指纹前后快照 + ledger 计数（probe `snapshot` 面复用），任一链指纹前进或 ledger 前进即 lag≠0。probe 触碰面 = **包内新增聚合入口**（既有 `awaitLagZero(String,long)` 公共签名保留为委托；两 RP-2 钉语义零变化零改写，仅登记 probe 文件加法式触碰——RP-2 条款「确需 probe 层改动则同 diff 改写并登记」的登记项，无钉改写）。编排器侧单静默窗 sleep 编排（多次快照共享一次窗口，非逐链串行 quiesce）。
- **qs 身份键 SSOT 声明（单侧）**：本裁定为 qs/connector/jobName 身份键派生的设计权威（单点函数 + 构造期携带 + 聚合判据三件套在此定稿）；R4-20 的 C7-008（offset 指纹健康断言已被本裁定推翻——「恒空指纹」非健康态）、C3-010/C3-011（键族成员）到达时**引用本裁定消费，不重建第二套键派生**；三工单不与本批合并执行。C10-005（无 X→X 自环断言）随红⑨落地。
- **单静默窗 residual 登记（m1，沿裁定⑤ §3.5 体例）**：聚合判据共享单静默窗（一次 sleep，M 链快照共享起止）——各链「窗前快照」时刻随遍历序有微小错位（非严格同刻），窗内跨越快照时刻的链事件存在理论漏检窗；该 residual 显式登记（错位量级 = 逐链快照取值毫秒级，静默窗秒级，占比可忽略），后续如需收窄走逐链独立窗（成本 = quiesce × M）须修订本文档不得静默。
- **顺序前置条款成文**：本裁定为全批最早落地项——组 B 内先于裁定⑥；Phase 2 提交序列⑨单独先行或与⑥同变更集且⑨变更在前（对齐纪律 §四.1）。

### 3.10 裁定⑩：relocate route querySpace 派生（C3-009）

- **预设 = mapping 携带派生（零新注入点）**：router 构造期已持有 mapping（IncrementalJobManager :62），`ShardChangeEventRouter.route`（:41-43）relocate 分支不再硬编码空串 `PhysicalTable("", baseTableName)`，改调 `TargetShardMapping` 新增包内 `relocateTarget(String sourceQuerySpace)`：单源单目标取 targetTables 唯一元素；多源按 `mapQuerySpace(sourceQuerySpace)`（:298-306，缺失项既有类型化失败）+ baseTableName 构造；事件源 qs 不可得（元数据缺失）显式失败不猜测。`belongsTo`（:59-61）谓词随 route 修正自然回归——增量链 filter 不再恒 false，100% 丢事件面闭合，FULL upsert 不再落回源侧 default 表。
- **FULL 侧同病核对登记**：`TargetShardMapping.routeRow`（:237）与 `routeShardValue`（:253）relocate 分支同硬编码空串——同一 `relocateTarget` 派生随同 diff 修（router 修复面内）；若 FULL 侧跨源行合并写（复核新证面）超出本派生修复面，登记边界不扩批。
- **备选（router 构造注入 target qs）被拒**：mapping 已携全部映射信息，第二注入点引入漂移风险。红⑩ = relocate 场景 route 返回含正确 querySpace 物理表 + filter 非恒 false。

### 3.11 裁定⑪：失败相位白名单受控自环（C7-002）

- **预设 = 失败相位白名单自环**：`ALLOWED`（:36-43）增两条自环边 `INCREMENTAL→INCREMENTAL`、`VERIFY→VERIFY`（SWITCH 不增——配置已翻转无重试语义；DONE/ABORT 终态不增）。自环仅经**既有编排 API 显式重入**触发（runIncremental/runVerify 对自环相位的再调用即 fix-forward 重试；不新增 API 面），带**进程内重试计数上界**（缺省 3，编排器实例内计数；超上界 gateFailed 类型化拒绝——非全开、非无限重试）。计数进程内承载可接受（自环只处理瞬时故障重试；跨重启恢复走 resume 路径非自环语义）。**备选（新增 RECOVERY 相位边）被拒**：七态扩八态 + 全量迁移矩阵钉改写，且未提供白名单自环之外的保护增益。
- **既有钉兼容硬约束（亲读核实）**：`TestMigrationPhase` 六钉（:25/:34/:40/:50/:66/:76）零改写保持绿——`testIllegalTransitionsFail`（:50-63）六条非法边均非自环边，白名单自环加入后断言不变；`testAbortReachableFromEveryActivePhase`（:40-47）ABORT 可达性不减；`testHappyPathRescale`/`testTerminalStates` 不涉自环面。CL-007/008 吸收面（相位重入/受控自环时序线索）随本裁定闭环。
- **fix-forward × 裁定⑤谓词协同**：自环写经状态行谓词（期望相位=当前自环相位）——abort 竞写将相位推离自环相位时自环写 updated==0 显式失败，自环重试不覆盖竞写终态；自环重试期间链已由裁定④原语可停（重入 runIncremental 前编排器先 stopIncrementalChains 回收旧链，**旧链句柄未确认退出（裁定④退出确认标记仍活）即 gateFailed 拒绝重入**，防半终止链双写）。
- **与 MA-10 部分启动卡死面同 diff 协调（X-2）**：裁定⑦相位后翻 ABORT 提供「部分启动失败」出路，自环提供「整链瞬时故障」出路——两出路合拢后「一次瞬时故障全量报废」面闭合。

### 3.12 守卫指标与可观测一览（键名/指标名一次定夺，零二源）

| 名称 | 形态 | 裁定 | 语义 |
|------|------|------|------|
| `nop.lettuce.pubsub.consume-queue-high-water` | WARN 日志（队列占用超缺省 80% 水位，周期抑制） | ② | 慢消费者可观测；拒绝路径另经 onConsumeFailure 类型化回调留痕 |
| `nop.lettuce.pubsub.completion-timeout` | WARN 日志（CompletionStage 60s 未完成） | ② | 返回值契约观测面（不等待不重试） |
| `nop.lettuce.pubsub.resubscribe-failed` | WARN + 失败计数 | ③ | 重连恢复幂等确认 SUBSCRIBE 失败显式化（恢复事件留痕 + 计数同面；F5 前提证伪后为唯一服务侧恢复观测通道） |
| `nop.orm.migration.chain-stop-join-timeout` | WARN 日志（join 超时已 interrupt） | ④ | 停止协议有界等待观测 |
| `nop.orm.migration.chain-not-confirmed` | gateFailed + WARN（interrupt 后 isAlive 仍活，重入被拒） | ④ | 半终止链重入拦截观测（M4） |
| `nop.orm.migration.chain-failure-gate` | gateFailed（ERR_MIG_GATE_NOT_SATISFIED，gate=`incremental-chain-failed:*` / `incremental-manager-missing-restart-reentry`） | ⑥ | 死链即 fail 门禁类型化拒绝（含重启路径 M2 分支） |
| `nop.orm.migration.running-jobs-reentry` | 类型化拒绝（NopException）+ WARN | ⑤⑦ | 单飞败者/同名重入显式拒绝留痕 |
| `nop.db.migration.guard-lock-timeout` | 类型化异常 | ⑧ | 咨询锁获取失败显式化 |
| `nop.orm.migration.fix-forward-retry-exhausted` | gateFailed | ⑪ | 自环重试上界超限显式拒绝 |

零新配置键声明：本批全部裁定不引入任何 `@cfg:` 配置键（上界/水位/超时均为常量缺省 + setter 注入面：链停止 join 上界 setter 一处）。

### 3.13 零 orm 触碰声明与两处条件申报条款

- **零 orm 触碰声明（11/11 PA 判定兑现）**：全部成员 = `src/main/java` 纯代码修复；站点分布于 nop-nosql-lettuce/nop-orm-migration/nop-db-migration 三模块，无任何 `model/*.orm.xml` 触碰、无 `_` 生成物触碰；裁定⑤谓词落 `MigrationJobStateStore` DDL 字符串既有自治表 JDBC 面（**非 ORM 源模型**），DDL 零变更（期望相位谓词不加列）。
- **条件申报条款一（裁定②消费执行器平台设施化）**：预设为 nop-nosql-lettuce 模块内私有 executor，零 nop-api-core 触碰；若实施中确需在 `io.nop.api.core.*` 新增平台工具/接口，触发 nop-api-core plan-first——须在执行计划 PA 行 + 本文档修订 + draft review 完成申报并列全 SPI 回归面（IMessageService 全实现 live grep 全列）方可实施；预设分支不触。
- **条件申报条款二（裁定⑤状态行 orm 源模型演进）**：预设期望相位谓词零 DDL 变更零 orm 触碰；若裁定演进为乐观版本列或触任何 orm 源模型，触发 ORM 模型结构 plan-first——申报流程同上；预设分支不触。

## 四、对齐纪律条款（逐条成文）

1. **C3-008 先于 MA-06 硬序**：裁定⑨（键派生单点化）必须先于/同变更集先行落地于裁定⑥（死链 fail 门禁）——否则 M>1 下 lag 观测量恒空使门禁修复无效。执行次序写死：组 B 内 ⑨→⑥；Phase 2 提交序列 ⑨ 单独先行或与⑥同变更集且 ⑨ 变更在前。
2. **MA-04 × MA-03 同变更集**：裁定④（停止协议发布序 + 有界 join + interrupt）与既有 `stopIncrementalChains` 收敛点（编排器 :401-409，已修骨架）同一变更集承载——原语与调用点（confirmSwitch/completeSwitch/abort 停链面）一批修；`TestMigrationSwitchStopsChains` 双钉（:76/:104）保持绿为同变更集约束面。
3. **MA-05 × MA-10 同 diff 协调（X-1，不并案）**：`runningJobs` 注册表共享站点——裁定⑤单飞/putIfAbsent fail-fast 改造与裁定⑦回滚回收/jobName 键派生一批设计；两工单台账独立收敛，cra-index 各行括注「同 diff 协调（X-1）」。
4. **PubSub 三单统一设计（裁定①②③）+ C7-014 竞次面约束**：三单共享同一连接/订阅生命周期设计（失效面①②④ + 可观测面③全枚举：订阅管理竞态/IO 线程内联/断线恢复观测/停机对账）；重订阅确认只订注册表快照内条目且锁外发送，禁复活已 cancel 订阅（红③断言承载）；与 4.1 REGISTRY 契约域对齐注记零触碰。裁定①②③同时为批外 C7-009（PubSub 失效面②，R4-20）登记接缝：统一设计覆盖面与非目标边界（§3.2 慢消费者隔离边界）为其既有裁定不重开体。
5. **RP-1/RP-2 修复方向约束**：裁定⑧保留 upsert 覆盖语义（禁「冲突即拒绝」方向）；裁定⑥在编排器层消费 failure（probe 层零改动，唯一加法式聚合入口见 §3.9 且钉零改写）——两约束为硬边界，违反即反向钉死改写义务触发（若确需改钉，同 diff 改写 + 语义对照留痕）。
6. **PubSub 七钉唯一允许改写 = 裁定② await 化**：`TestLettuceNosqlService` 七钉中仅 :586 一钉允许改写（§5.3 逐条确认 + 语义对照留痕），其余六钉零改写；docker-gated 面口径：`TestLettuceNosqlService` 全类 docker-gated（:57-58）默认不跑——本批新增并发护栏钉落 mock listener 层非 docker 可跑；docker 环境在案时补跑全类留痕，不在案时以 mock 层钉 + 三联口径登记。

## 五、裁定⑫：测试缝与零回退钉

### 5.1 红①–红⑪驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 |
|----|------|---------|-------------------|
| ① | C6-004 | N 线程 subscribe×cancel/suspend/resume 压力交错（mock listener 层非 docker 可跑） | 孤儿订阅/复活双投递/addListener 泄漏红 → 监视器串行 + 属主校验 + 停机对账绿（C10-022） |
| ② | C8-001 | 投递线程名断言 + 慢消费者阻塞时其他 topic 继续投递 + 返回值 CompletionStage 观测 | IO 线程内联阻塞红 → 外移投递 + 有界超时告警绿（C10-023） |
| ③ | C7-014 | 连接重建模拟 + 恢复事件零留痕断言 + 幂等确认 SUBSCRIBE 失败注入 | 可观测缺失红（无恢复留痕/无失败告警）→ 恢复留痕 + 失败 WARN/计数绿；已 cancel 订阅不复活断言保留；订阅丢失断言废除（F5 前提证伪，勘误 10）（C10-025） |
| ④ | C6-003 | start 前置句柄观测 + stopAll 后线程存活断言 + interrupt 后未退出重入被拒断言 | join 空转/滞留红 → 构造期发布 + 有界 join + interrupt + 退出确认复核拒绝重入绿（C10-003/C10-010） |
| ⑤ | C7-003 | abort×confirmSwitch 并发后状态行终态断言 + 双写 fencing | 全列覆盖背离红 → 相位谓词拒绝 + 第二写 updated==0 显式失败绿（C10-011） |
| ⑥ | C7-001 | failure 置位后 runVerify 拒绝断言 + 重启路径用例（manager==null 且 phase==INCREMENTAL 拒绝） | lag=0 放行死链红 → gateFailed 绿（含 restart-reentry 拒绝分支）；probe 双钉保持绿（C10-004） |
| ⑦ | C3-001 | 第 i 链启动失败后已启动链回收断言 + 同名重入 | 孤儿链滞留红 → 回收绿；putIfAbsent 拒绝绿（C10-002） |
| ⑧ | C1-003 | 并发 ensureHistoryTableExists DDL 幂等 + 并发 recordMigration 无脏行覆盖 | 竞态红 → 互斥 + 条件捕获绿；RP-1 钉保持绿（C10-021） |
| ⑨ | C3-008 | M=2 场景 offsets 指纹非空断言 | 恒空平凡通过红 → 逐链聚合绿（C10-005） |
| ⑩ | C3-009 | relocate 场景 route 返回含正确 querySpace + filter 非恒 false | 空串丢事件红 → mapping 派生绿（C10-006） |
| ⑪ | C7-002 | INCREMENTAL 失败→受控自环重入成功路径 + 非法自环仍拒绝 + 上界超限拒绝 | 卡死红 → 白名单自环绿（C10-015） |

### 5.2 测试资产落位与 docker-gated 规避

新增/扩展测试类按站点归位：nop-nosql-lettuce（TestLettucePubSubConcurrency 新建——红①②③载体，**mock `RedisPubSubListener` 层，非 docker 可跑**；既有 `TestLettuceNosqlService` 七钉保持）、nop-orm-migration（TestIncrementalJobManagerStopProtocol 扩展红④、TestMigrationJobStateStoreFencing 新建红⑤、TestChainFailureGate 新建红⑥、TestStartFallbackChainsCompensation 新建红⑦、TestConnectorKeyDerivation 扩展红⑨、TestShardChangeEventRouter 扩展红⑩、TestMigrationPhase 扩展自环白名单钉、TestMigrationPipelineE2E/AbstractMigrationE2ETestCase 端到端载体）、nop-db-migration（TestMigrationHistoryManager 扩展红⑧）。补测类声明：新增断言即新钉、缺陷态红→修复后绿；红绿快照落 `_tmp/` 并登记。

### 5.3 零回退钉全列 + MA-02 await 化逐条确认（未决三）

**零回退钉全列**（钉零回改逐条 git diff 为空核对；唯一允许改写 = MA-02 await 化 :586 一钉）：

- `TestLettuceNosqlService` PubSub 七钉（:558/:586/:614/:705/:739/:768/:803）——**逐条确认**：

| 钉 | 断言形态 | 外移后语义 | 处置 |
|----|---------|-----------|------|
| :558 testPubSub_SendAndReceive | latch.await(5s) + 收到的内容/条数 | await 型，外移不影响判据 | **零改写** |
| :586 testPubSub_UnsubscribeStopsReceiving | sleep(200) 型计数相等断言「cancel 后不再收到」 | cancel 前已入队消息可在 cancel 完成后仍投递（in-flight 面），严格相等断言时序化失效 | **唯一改写钉**：改为「cancel 完成后新 send 经 await 静默窗计数不增 + in-flight 至多投递 cancel 前已入队消息」断言形态，语义对照同 diff 留痕 |
| :614 testPubSub_MultipleSubscribers | sleep(500) + ≥1 容忍型 | 容忍型下界判据外移后仍成立 | **零改写** |
| :705 testPubSub_NoCrossTopicDelivery | latch.await + sleep + isEmpty | B 无消息入队，isEmpty 成立 | **零改写** |
| :739 testPubSub_CancelDoesNotAffectOtherSubscribers | isCancelled 同步断言 + latch.await | isCancelled 锁内同步翻转，await 判据不变 | **零改写** |
| :768 testPubSub_ResumeAfterCancelDoesNotResurrect | isCancelled + count==0（无前置 send，无 in-flight） | 判据不变 | **零改写** |
| :803 testPubSub_ConsumeContextSendAsyncPublishes | latch.await（reply 链） | 经 executor 投递，await 判据不变 | **零改写** |

- `TestMigrationSwitchStopsChains` 双钉（:76/:104，plan 2472 收敛点）——零弱化（MA-03 同变更集约束面）。
- `TestIncrementalLagProbe` 双钉（:42/:90，RP-2）——零改写（probe 加法式聚合入口不触其语义，§3.9）。
- `TestMigrationHistoryManager#testRecordMigrationReplacesExistingRecord`（:253，RP-1）与 `#testRecordMigrationKeepsExistingRowWhenWriteFails`（:299）——零弱化。
- `TestMigrationPhase` 六钉（:25/:34/:40/:50/:66/:76）——零改写（裁定⑪亲读核实兼容）。
- `TestWriteGateWindowRecheck` / `TestShardSelectorHotSwapVisibility`（2507 收口钉）——零触碰。
- `TestCdcOffsetSeedBuilder` / `TestSourcePositionPinner` / `TestConsistencyCanonical` / `TestMultiSinkTopologyProbe`（邻接钉）——零回退。
- 上游环境抖动按执行计划三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕，不得静默重跑或跳过。

### 5.4 组 A–D 落位与同变更集绑定（Phase 2 提交纪律对齐）

| 组 | 成员（裁定） | 模块 | 同变更集绑定 |
|----|-------------|------|-------------|
| A | C6-004（①）+ C8-001（②）+ C7-014（③） | nop-nosql-lettuce | ①②③同一连接/订阅生命周期变更集（单锁域/executor/对账共享设计）；C10-022/023/025 随同 diff |
| B | C3-008（⑨，全组最先）→ C7-001（⑥，硬前置⑨）+ C6-003（④，MA-03 同变更集）+ C7-003（⑤）+ C7-002（⑪，与⑤谓词协同） | nop-orm-migration | ⑨先于⑥；④与 MA-03 收敛点同变更集；⑤⑪谓词面同变更集 |
| C | C3-001（⑦）+ C3-009（⑩） | nop-orm-migration | ⑦与⑤同 diff 协调（X-1）；⑩与⑨键族统一 |
| D | C1-003（⑧） | nop-db-migration | RP-1 约束；独立提交序列 |

### 5.5 端到端验证义务（rule 22，归 Phase 2）

- **链一（编排全链，覆盖裁定④⑤⑥⑨⑪）**：runIncremental（键派生清单登记）→ 链 failure 注入 → 死链 fail 门禁拒绝 → fix-forward 受控自环重入 → confirmSwitch 收敛停链（有界 join 断言）→ completeSwitch 版本谓词拒绝越权写。
- **链二（PubSub 生命周期，覆盖裁定①②③）**：subscribe → 慢消费者并发 → 外移投递不断链 → 断线重连恢复留痕与幂等确认 → cancel 属主校验 → doStop 对账（含停机排空留痕）。
- **链三（守卫场景，覆盖裁定⑦⑧）**：并发迁移启动 DDL 幂等 + recordMigration 无脏行覆盖 + startFallbackChains 中途失败回收。

### 5.6 锁序总表（全批新增锁域一致性核对）

| 锁域 | 裁定 | 允许嵌套 | 禁止 |
|------|------|---------|------|
| LettucePubSubService 服务级 monitor | ①②③ | 投递任务在锁外 executor 执行（message 仅入队）；doStop 对账与裁定③快照拷贝共享本锁；subscribe/unsubscribe 阻塞往返保留（超时预算 5s 显式接受，§3.1） | 锁内等待 executor 任务；锁内执行消费者回调；③确认 SUBSCRIBE 在锁内发送（拷贝-释放-发送三段） |
| 投递 executor 单线程 | ② | 无 | 投递线程内同步等待 CompletionStage（有界观测不阻塞） |
| runningJobs CHM bin 锁 | ④⑤⑦ | 无（putIfAbsent 失败路径锁外抛错） | bin 锁内做 join/网络等待 |
| 编排器 per-jobId 单飞域 | ⑤⑥⑦⑪ | 可嵌套 stopIncrementalChains（有界 join）；可取 stateStore ensureTable synchronized | 嵌套取另一 jobId 单飞锁（防 ABBA）；单飞域内重新进入同 jobId 入口 |
| MigrationJobStateStore ensureTable synchronized(this) | ⑤（既有） | 无 | — |
| DB 咨询锁（跨进程，连接级） | ⑧ | 互斥段内同连接 SQL | 互斥段内长事务/外部回调 |

跨域无环论证：编排器单飞域内 stopIncrementalChains 的 join 有界（裁定④），runningJobs bin 锁瞬时；lettuce 服务锁与编排器域分属模块零交集；咨询锁为最外层跨进程原语、内不含任何进程锁获取——无环成立，closure audit 抽查临界区边界。

### 5.7 C10 集落名（随本体同 diff，E2）

**成员面 12 条**：C10-022（红①）、C10-023（红②，含 MA-02 改写履行注记）、C10-025（红③）、C10-003 + C10-010（红④）、C10-011（红⑤）、C10-004（红⑥，RP-2 履行注记）、C10-002（红⑦）、C10-021（红⑧，RP-1 履行注记）、C10-005（红⑨）、C10-006（红⑩）、C10-015（红⑪）——半边口径以执行计划勘误为准（本批仅承载 11 主 ID 对应成员面）。接线与无静默跳过义务（rule 23/24）：外移 executor 真实承载投递（线程名/计数证据，非仅 executor 存在）、版本谓词真实拦截越权 UPDATE（updated==0 路径断言）、getFailure 真实被门禁消费（零调用方现状反转）、派生函数真实被 runVerify 与 startFallbackChains 双入口消费、join null 显式留痕、第二写失败显式抛错、同名重入显式拒绝、拒绝策略类型化回调、新增公共分支零空方法体/TODO。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① 操作级锁（仅 cancel 内部段） | suspend/resume 与 subscribe 交错面复刻残留；服务级单锁域一次闭合 |
| ① 按 topic 全局取消 API | 属主边界模糊化；逐 subscription cancel + 锁内 unsubscribe 已承载 |
| ② nop-api-core 平台投递设施 | SPI 零签名变更预设 + 模块内 executor 已闭合；平台化触发申报无能力增益（条件申报条款保留出口） |
| ② 投递线程内等待 CompletionStage 完成 | 复活慢消费者队头阻塞与外移目标自相矛盾；有界超时告警保观测不保阻塞 |
| ② 无界队列 | OOM 轨迹；有界 + 类型化拒绝为唯一合法形态 |
| ③ 心跳对账 | 常驻探测负载 + 重建瞬间静默窗不闭合；注册表 SSOT 对账零新增通道 |
| ④ stop 前字段赋值（非构造期注入） | 「未发布即未启动」不由类型系统承载，赋值遗漏面残留 |
| ⑤ 乐观版本列 | DDL 变更 + 对主导跨相位背离面无超出相位谓词的增益（同相位面由单飞承载） |
| ⑤ 单飞败者排队等待 | 编排 API 为运维显式低频调用，排队掩盖误用；fail-fast 语义最窄 |
| ⑤ 跨节点 per-jobId 咨询锁收窄残余窗 | 与裁定⑧互斥选型同族，本批引用不重建、不重复建设第二套跨进程互斥（residual 显式接受，§3.5） |
| ⑥ probe 层活性句柄 | 触发 RP-2 改写义务；编排器层消费零 probe 触碰闭合 |
| ⑧ 方言化 upsert（ON DUPLICATE KEY/ON CONFLICT） | 方言矩阵分叉 + 与互斥面重复建设；条件捕获形态方言中性且保 upsert |
| ⑧ 冲突即拒绝/insert-only | RP-1 硬约束禁用方向（反向钉死改写义务触发面） |
| ⑪ RECOVERY 相位边 | 七态扩八态 + 全量矩阵钉改写，无白名单自环之外的保护增益 |
| ⑪ 全相位自环开放 | 非受控重试语义；白名单 + 显式重入 + 计数上界为最窄闭合 |

## 七、与已有设计的关系

- [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md) 裁定⑥：条件 UPDATE/rowsAffected==1/租约/列集约束形态菜单——裁定⑤为其状态行站点实例化，引用不重建；batch 域 restartTime stale 接管先例同族（fencing 范式样板）。
- [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md)：R03 出锁投递范式——裁定②消费其形态（引用不重开），落点为 lettuce 模块内 executor。
- [registry-lifecycle-contract.md](registry-lifecycle-contract.md) 裁定⑥：4.1 REGISTRY 契约域——裁定③⑤⑨对齐注记零触碰关系（NopStreamOffsetBackingStore 为裁定⑨只读消费面）。
- [orm-session-txn-atomicity-and-model-cache-generation.md](orm-session-txn-atomicity-and-model-cache-generation.md)：2.4 同单元先例——写门租约复核（本批零触碰沿用面）、shardSelector volatile 热替换（confirmSwitch 既有路径零回改）。
- **下游 SSOT**：R4-20（8.60）到达时 (a) C7-008/C3-010/C3-011 引用裁定⑨消费 qs 身份键派生不重建（C7-008「offset 指纹健康」子断言已被裁定⑨推翻，修复须按逐链聚合口径对齐）；(b) C1-008 文件级接缝（executeFull 写点）不代做义务确认（§3.5）；(c) C7-009（PubSub 失效面②）引用裁定①②③统一设计接缝不重开（§四.4）。R4-32（C7-013）以本批后 live 形态为基线。就绪注记归执行计划收口段。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/02-core-guides/model-first-development.md`（「ORM 迁移编排并发契约」节：阶段迁移 fencing/停止协议可验证性/死链 fail 门禁语义/fix-forward 受控自环契约/qs 身份键派生契约[SSOT 指向本文档裁定⑨] + 迁移历史表守卫契约句 + PubSub 消息投递线程纪律契约节[fire-and-forget 能力边界邻接、消费外移与返回值契约、重连重订阅对账] + 源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
