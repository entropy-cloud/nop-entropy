# stream-core 并发正确性与资源包络契约（R2-07 批十项裁定）

> Status: active
> Last Reviewed: 2026-10-29（plan 2510 Phase 1 产出；本批唯一设计裁决权威）
> Source: plan 2510（`ai-dev/plans/2510-cra-phase8.16-r2-07-stream-core.md`）Current Baseline 十成员表 + Baseline 勘误 5 条 + draft review R1 增补（Major-1 裁定⑦锚列载体 / Major-2 裁定②误杀面 / Major-3 裁定④补偿四面 / Major-4 ⑧吸收 C3-008 / Minor-1 ①锁序审计 / Minor-2 ⑩许可保留线程行为）；发现事实源 = WI 1.1 审计目录 `ai-dev/audits/2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency/`；行号 = live `41508cded5` 回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-stream-core `source/coordinator/LocalSourceCoordinator`（enumeratorLock :59、addReader :186、handleSplitRequest :260、snapshotState :296-297、close :334）、`execution/task/TaskExecutor`（poolSize :111-116、awaitCompletion :305/:334）、`execution/ResultPartition`（enqueueWithBackpressure :345-377、queue.put :359/:373、bypass release :354 / interrupt release :362）、`environment/StreamExecutionEnvironment`（runLocal :537、awaitCompletion 调用 :574）、`connector/lookup/CachingTableLookup`（refreshPool :192-199、scanAndDispatch :211、refreshPool.execute :224）、`execution/materialization/InMemoryMaterializationPoint`（store :41）；nop-stream-runtime `ops/OpsJobManager`（submit :124、ALREADY_HOSTED :129-131、terminalAt :65/:302、close :421）、`coordinator/JobCoordinator`（deactivateToStandby :2329-2349、failureDetector :212/:510、periodicCheckpointScheduler :468）、`checkpoint/CheckpointCoordinator`（onCompletePersistSuccess :948、commit 扇出 :1004-1013）、`launch/PollingJdbcMessageService`（poller :96、:293、send :177、清理 :151、cursor bootstrap :280-290、lastSeenId 推进 :325/:333、poll 查询 :312、物化 :383-390）、`cluster/InMemoryClusterRegistry`（:44/:151-155/:190）与 `cluster/JdbcClusterRegistry`（:275、DDL :401）、`transport/RemoteGraphExecutionPlanBuilder`（:224/:668 探测面）
> Related：R01 族级 SSOT = [ledger-retention-policy.md](ledger-retention-policy.md)（裁定⑦⑧的参数/键名/形态唯一来源，本文档零复制）；[periodic-task-insulation.md](periodic-task-insulation.md)（裁定⑦⑧周期清理链的失败重臂与不断链保障）；[registry-lifecycle-contract.md](registry-lifecycle-contract.md)（REGISTRY 契约域与本批 `ClusterRegistry` 为同名异实体零站点重叠）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | C1-001（←C2-001/C3-001） | 两入口（addReader :186 / handleSplitRequest :260）收敛进 `enumeratorLock`，与 snapshotState/close 同锁；enumeratorLock 为叶锁零嵌套（锁序审计结论），锁内禁未知代码/阻塞调用；备选 mailbox 漏斗不采 |
| ② | C4-001（←C8-013） | 双守卫：Σsubtask>poolSize 提交期 typed fail-fast（误杀面取舍=采 fail-fast）+ awaitCompletion 有界等待接入 runLocal 单点（覆盖三 execute 入口），超时=abort；守卫 b 可经 `nop.stream.local.await-completion-timeout-ms` 显式禁用 |
| ③ | C4-002（←C3-002/C8-014） | 刷新派发有界队列（容量=refreshMaxConcurrency×2，与 maxEntries 正交）+ in-flight key 去重 + 满时丢弃计数限频 WARN（禁静默积压） |
| ④ | C4-009 | try/finally 全窗补偿四面（活线程池/failureDetector/CheckpointCoordinator 池组/registry 残行）逐面终局化；同 jobId 守卫=既有 ALREADY_HOSTED 登记，仅补孤儿窗口重提交防护；HA fencing 零重叠 |
| ⑤ | C7-011 | deactivateToStandby 主动 abort 在途 pending checkpoint + onCompletePersistSuccess 与 commit 扇出入口 active/fencing 复核（失败=丢弃不 commit+计数）；= 既有三层防线补第四层 |
| ⑥ | C8-011（←C4-004/C3-008） | 控制面/数据面线程池分离 + 单 poll 有界批量（`nop.stream.launch.poll-batch-size` 缺省 500，与裁定⑨ LIMIT 同参数）；bounded-offer 既有裁定维持不变 |
| ⑦ | C1-006（←C7-002） | 终态 job 定界接线 removeTaskAssignment（InMemory+JDBC 双接线，活跃追加零改动）+ JDBC 形态 A 周期清理——谓词 = `job_id ∈ 进程内 terminalAt 终态白名单`（`assigned_at` 账龄作 TTL 兜底）组合，零 DDL；face token=`assignment`；参数/键名引 R01 SSOT |
| ⑧ | C8-003（←C7-003；吸收 C3-008） | (a) msg_queue 保留=消费后可删（`id ≤ 进程内已派发最大 id` 主谓词 + retention TTL 双谓词取交；MAX(id) bootstrap 不作清理位点，重启后清理暂停为可接受语义）；(b) 后端实现 ChannelBacklogProbe（backlog=未消费行数），Builder 探测面零改动；face token=`msgqueue` |
| ⑨ | C5-010（←C8-002） | setLimit 有界拾取 + cursor 分批循环（limit=批量上限与⑥共享）；limit≤0 装配期 fail-fast；列投影不适用（payload 必需）；与 WI6 门控正交互补 |
| ⑩ | FAM-1（主 C5-001←C7-001；族成员 C8-001） | store 上界=独立配置（缺省 1024）；overflow 语义=许可保留（acquire 恒 put、store 仅 checkpoint 语义记录）；typed 溢出信号不采；TestResultPartitionOverflowBypass 假保护同 diff 改写；C9-007 零承接注记 |
| ⑪ | C10 十条验收前置 | 九个测试缝 + 七个零回退钉全列（§3.11），修复 diff 不得触碰钉内断言 |

## 二、背景与动机

R2-07 批十个成员覆盖两类缺陷面：**并发正确性**（enumerator 无锁直调、LOCAL 池互等死锁、提交窗口孤儿协调器、standby 第二写者、控制面饥饿）与**资源包络**（刷新池无界、两条 INSERT-only/append-only 无界账本、读路径全量物化、materialization store 无界 + 背压旁路）。

两类面被绑为一批（batch-proposal §二.2 R2-07 行），因其共享同一验证通道（`./mvnw test -pl nop-stream -am`）与 owner doc，且保留纪律两成员（C1-006/C8-003）必须按 R01 族级 SSOT 传导、读路径两成员（C5-010/FAM-1）必须按 R02 三范式选型——分散裁决会造成族内参数/语义分叉。draft review R1 增补的五个必须裁决项（⑦锚列载体、②误杀面、④补偿四面、⑧吸收 C3-008、①锁序审计与⑩许可保留线程行为）在本批一次定夺，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：enumerator 三态锁纪律收敛（C1-001）

- **收敛范围**：`registerReader` 内 `enumerator.addReader(subtaskIndex)`（:186，现锁外）与 `ReaderChannel.requestSplits` 内 `owner.enumerator.handleSplitRequest(...)`（:260，现无锁）全部移入 `enumeratorLock`，与既有 `snapshotState`（:297）/`close`（:334）同锁——enumerator 全部状态变更与快照读取单锁互斥，快照原子性由「快照与两入口互斥」达成（并行 source 启动不再出现双份分配/表份额丢失/快照撕裂）。
- **锁内纪律**：`addReader`/`handleSplitRequest` 声明为纯内存状态变更（enumerator 实现契约条款，入 javadoc）；锁内禁 I/O、禁用户函数回调、禁再入 coordinator 其他监视器。
- **锁序审计结论（Minor-1）**：`enumeratorLock` 为叶锁——registerReader 调用链（reader 任务启动线程）与 requestSplits 调用链（reader channel 线程）进入时不持有 coordinator `recoveryLock`、`jobs` 监视器或任何其他锁；snapshotState/close 侧（coordinator 线程）同样无嵌套持有进入。「enumeratorLock 不得嵌套于任何外层锁内获取」立为宿主契约，实施以断言缝（§3.11 缝 1）验证零死锁序。
- **备选不采**：每 coordinator 单线程 mailbox 漏斗——enumerator 现实现无内部阻塞点，锁方案已满足；漏斗引入跨线程投递与背压问题，复杂度无对价。仅当实施中证实 enumerator 实现内部存在阻塞点（锁方案不足）时，方可在 plan 登记后改采漏斗。

### 3.2 裁定②：LOCAL 池互等死锁解除（C4-001）

- **守卫 a（提交期）**：`Σsubtask > poolSize` 时 typed fail-fast（`StreamException` + 专用错误码，消息带 Σsubtask/poolSize 与处置指引：下调并行度或上调 poolSize）。`poolSize` 来自 `newFixedThreadPool`（TaskExecutor :111-116）。
- **守卫 b（运行期）**：`awaitCompletion` 有界等待接入 `runLocal` 单点（:537——execute :363/:367 与 savepoint 链 :617/:635 的唯一 LOCAL 漏斗，接入单点即覆盖三 execute 入口，draft review Major-2 入口锚订正）；既有 `awaitCompletion(timeout, unit)` 重载（TaskExecutor :334）为现成承载体。
- **超时配置**：`nop.stream.local.await-completion-timeout-ms`，缺省 300000；`0` = 显式禁用守卫 b（仅留守卫 a，服务长跑 LOCAL 开发场景）；负值装配期 fail-fast。
- **超时行为 = abort**：cancel 全部 task future + 关停执行器 + typed 异常上报——不悬挂、不静默、不部分残留。
- **误杀面取舍裁决（Major-2，唯一结论）**：fail-fast 确会误杀「队列不填满即跑完」的合法有界小数据作业，本裁定**采 fail-fast**——互等死锁是时序依赖的静默永久悬挂，危害无界且不可诊断；误杀以快速类型化失败呈现，损失有界、指引明确。备选（池扩容 / 无界队列 / 仅阻塞边守卫）的完整拒绝理由见 §四。
- **行为回归面评估**：Σsubtask ≤ poolSize 的既有 LOCAL 作业零变化；`TestResultPartitionDeadlock` 负向窗口（容量内阻塞/恢复）与守卫 a 阈值域不相交，零冲突核验成立。

### 3.3 裁定③：刷新派发有界化 + 去重（C4-002）

- **有界队列**：`scanAndDispatch` 对老化 key 的派发从 `refreshPool.execute`（:224，fixed pool 默认无界 LBQ）改为有界队列派发；容量 = `refreshMaxConcurrency × 2`（并发上限参数族推导）。
- **容量与 maxEntries 关系**：两者语义正交成文——`maxEntries` 界缓存条目数（既有逐出语义 :155-158），派发容量界在途刷新任务数；不共用、不联动。
- **in-flight 去重**：同 key 在「已入队未完成」期间不重复入队（in-flight set，完成时移除）；未入队的老化 key 由下一扫描轮自然补扫。
- **慢 delegate 降级显式化**：队列满即丢弃本轮派发 + 计数指标 + 限频 WARN——禁静默无界积压；数据不丢（key 保持老化态，后续轮重派），降级只表现为刷新延迟，delegate 挂死不再产生 OOM 候选与重复探测。
- 既有并发上限双断言（`TestCachingTableLookup` 强保护面，C10-003 指认）零回退。

### 3.4 裁定④：提交窗口 try/finally 全窗补偿（C4-009）

补偿覆盖**四面泄漏面**（Major-3 定夺，非仅执行器+registry 两面）：

| 泄漏面 | live 锚 | 补偿动作 |
|--------|--------|---------|
| 活线程池 | JobCoordinator `failureDetector`（:510）/ `periodicCheckpointScheduler`（:468） | coordinator 走终态 stop（内部 shutdownNow 各调度器） |
| failureDetector 永生 | :212/:510 | 同上——终态 stop 路径覆盖，不得残留独立调度线程 |
| CheckpointCoordinator 池组 | `commitFanoutExecutor`（CheckpointCoordinator :227/:1087） | 终态清理覆盖 commit 扇出执行器 |
| registry 残行 | `jobs` / `terminalAt` / 外部登记 | OPS 侧确保零条目残留；补偿自身失败 ERROR 显式可见不吞 |

- **同 jobId 守卫 = 既有登记**：`ERR_STREAM_JOB_ALREADY_HOSTED`（:129-131）维持原语义，本批不重复立项；仅补**孤儿窗口**（coordinator.start()/assignTasks() :186-187 后、jobs.put :191 前失败）后的重提交防护——补偿在 synchronized submit 内同步完成，submit 抛出时孤儿已终局化，重提交天然安全，无第二活协调器。
- **HA fencing epoch 零重叠声明**：fencing token 由 JobCoordinator 激活路径签发；补偿发生在该协调器自身失败终局化路径上，不改写任何 fencing 判定/epoch 比较逻辑。

### 3.5 裁定⑤：standby 降级取消在途 persist（C7-011）

- **主动 abort**：`deactivateToStandby`（:2329）在翻 `active` 后主动 abort 在途 pending checkpoint——现注释（:2346-2349）自认「in-flight 不 commit 靠 collectAck 门控 + 超时/新 leader abort」的被动兜底被补强为主动终局化。
- **扇出入口复核**：`onCompletePersistSuccess`（:948）入口与 commit 扇出提交点（:1004-1013，`commitFanoutExecutor` 派发前）补 active/fencing 复核——降级后 persist 才完成的在途 checkpoint，复核失败 = 丢弃不 commit + 失败计数，僵尸 standby 不再成为 checkpoint 持久平面第二写者。
- **与既有三层防线的关系 = 补第四层**：collectAck 门控（层 1，既有）→ pending 超时（层 2，既有）→ 新 leader abort（层 3，既有）→ 降级主动 abort + 扇出入口复核（层 4，本批）。层 4 不改写层 1–3 的任何判定语义，只闭合「persist 已完成、扇出未开始」的残余窗口。

### 3.6 裁定⑥：poller 控制面/数据面分面（C8-011）

- **线程分离**：`PollingJdbcMessageService` 既有共享 2 线程池（:96，`newScheduledThreadPool(2)` 承载每订阅 `scheduleWithFixedDelay(this::poll)` :293）拆分为控制面/数据面两组——数据面 poll/派发留在数据面池，控制面消息（部署/取消/协调指令）走独立调度线程；慢数据面积压不再以「每行 × 无界批量」垄断控制面。
- **单 poll 有界批量**：单次 poll 物化行数 ≤ `nop.stream.launch.poll-batch-size`（缺省 **500**，≤0 装配期 fail-fast），与裁定⑨ LIMIT 共享同一参数——一处定义两处消费。
- **bounded-offer 既有裁定维持不变注记**：零进度情形的 offer 背压维持；本裁定补「有进度慢消费」情形的批量上界，两个情形域互补不重叠。

### 3.7 裁定⑦：分配账本清理接线（C1-006，R01 成员传导）

- **终态定界接线**：终态 job 迁移路径接线 `removeTaskAssignment`（InMemory :190 / JDBC :275 双实现同步接线，:190-191/:275-290 现生产零调用方）；活跃 job 追加语义零改动——`TestJobCoordinatorPerTaskFailure:111-126` 的 +1 pin 与 attempt 单调 pin 保持绿（终态定界清理，非 naive 全清，C10-020 限定）。
- **终态接线唯一锚（Minor-1）**：`OpsJobManager.terminalAt.put(jobId, ...)`（:302，`stop` 内 `coordinator.terminate(mode)` 之后单点）——终态集 = `JobTerminationMode` 的 **CANCEL / DRAIN / SUSPEND** 三值（live 枚举亲读核对；`EXPORT_SAVEPOINT` 保持作业运行，不入 terminalAt、不触发清理）。submit 重提交路径的 `terminalAt.remove`（:195）与 remove(:313)、governance sweep 保留期修剪（:402-408）为既有语义零改写。
- **周期清理谓词右侧事实源裁决（Major-1，唯一结论）**：live 仅 COORDINATOR/NODE/TASK_ASSIGNMENT 三表，**无耐久终态 job 表**——终态唯一载体是 OpsJobManager 进程内 `terminalAt` map（:65，重启即失）。故周期清理谓词 = **组合谓词**：`job_id ∈ 当前进程 terminalAt 终态白名单（终态且非活跃）`（主谓词，精确但不跨重启）**OR `assigned_at` 账龄超过 R01 retention TTL**（兜底谓词，覆盖崩溃重启后白名单丢失的孤儿行）。**崩溃孤儿行清理边界声明**：重启后 terminalAt 白名单重建前，孤儿分配行由 TTL 兜底谓词在保留期到达后清理——滞后但不漏。**活跃 job 保护谓词**：两支均须 anti-join 排除当前活跃 job 集（`jobs` 中 running 条目）——`assigned_at` 账龄谓词对长跑活跃 job 的行同样生效是预期行为（活跃 job 的分配行随终态主谓词或其自身超长 TTL 处理），防误删的硬保护是「活跃 job 集排除」而非白名单。**anti-join 活协调器表被拒声明**：不采用「join 活跃协调器登记表定界可删集」形态——failover 重注册窗口内活跃集瞬时不完整，会误删活跃 job 的 attempt 历史，破坏单调保留（+1 pin 回退面）。
- **双实现接线形态**：
  - InMemory：终态迁移点按 jobId 前缀批量摘除 `taskAssignmentHistory`（:44）对应键——仅终态 job 触发，`recordAttempt`（:151-155）追加路径零改动。
  - JDBC：终态迁移点按 `job_id` 谓词 DELETE（复用既有 `removeTaskAssignment` 语句形态 :275-290）；周期清理载体与调度见下条。
- **锚列载体（Major-1 定夺）**：JDBC 分配表（`nop_stream_task_assignment`）形态 A 周期清理按上述组合谓词实施，锚列 = `assigned_at`（写入后不被业务回改的账龄事实列，兜底谓词载体）——**不新增 DDL**：TASK_ASSIGNMENT_TABLE 无终态迁移时刻列，且 `CREATE TABLE IF NOT EXISTS`（:401）不为存量部署加列；终态时刻新列若确需须先裁定 DDL migration 方案并在 plan 登记后方可采。
- **SSOT 关系与 face token（Minor-3）**：保留期/周期/批删上限/键名/形态全部引用 [ledger-retention-policy.md](ledger-retention-policy.md) §3.1/§3.2（本文档零复制参数值）；成员传导行已追加至其 §3.4。face token 钉死 = **`assignment`**（键形 `nop.stream.ledger.assignment.*`，与 SSOT §3.2 `nop.sys.ledger.lock/changelog` 多 face 命名惯例一致）。
- **同名异实体辨析注记**：本裁定的 `ClusterRegistry`（nop-stream-runtime cluster）与 `SideOutputRegistry`（1.1-C1-002，R4-01 成员）、以及 [registry-lifecycle-contract.md](registry-lifecycle-contract.md) 的 REGISTRY（nop-message-debezium `NopStreamOffsetBackingStore`）三者互不同实体；本批零触碰后两者。

### 3.8 裁定⑧：msg_queue 保留 + 门控补齐（C8-003，两半边一次裁决禁各修一半）

- **(a) 保留清理——queue 语义张力裁决 = 消费后可删，清理位点载体一次定夺（Major-2）**：live `lastSeenId`（:252）仅由派发循环真实推进（:325/:333），是唯一表达「确曾派发」的事实——**清理位点 = 进程内 per-topic 已派发最大 id（lastSeenId 的派发推进值），主谓词 = `id ≤ 该位点`**；辅以 R01 retention TTL 双谓词取交（锚列 = 自增主键 `id`，INSERT 序即账龄序），TTL 不独立启用删除。**MAX(id) bootstrap 不作清理位点（显式排除）**：订阅启动的 MAX(id) bootstrap（:280-290，仅 COORDINATOR uplink no-backlog topic）语义是「声明跳过存量历史」，非「确曾消费」——若用作删除位点则 id≤位点恒真、TTL 成唯一约束，停滞未消费行超保留期即被误删，与消费后可删裁决矛盾。
- **重启清理暂停窗口 = 可接受语义（显式声明）**：`lastSeenId` 为进程内 AtomicLong、无持久化——进程崩溃重启后位点归零重建，重建到历史水位之前清理谓词无可删集、**周期清理保守暂停**（宁可滞后清理也不误删）；重启后重新派发推进位点，清理自然恢复。滞后清理的账本增长窗口有界于「重启频率 × 派发推进速率」，且未消费积压增长面本就归 WI6 生产者门控（职责分离：retention 不删未派发行，backpressure 不发未消费增量）。**TTL 纯账龄独立删除维持被拒**。
- **(b) 门控半边（吸收 C3-008 随主条目同 diff）**：`PollingJdbcMessageService` 实现 `ChannelBacklogProbe`，backlog 口径 = **未消费行数**（`COUNT(*) WHERE topic=? AND id > cursor`，cursor 同为派发推进值），使 `RemoteGraphExecutionPlanBuilder` 既有探测面（:224/:668，零改动）对 DB 通道真实可达；「configured but no capability」诚实降级日志形态维持，DB 部署落地后不再出现。
- **载体与参数（face token，Minor-3）**：`send` INSERT-only（:177-187）本体零改动；现有 test-only 清理（:151）升格为生产周期载体（形态 A），参数/键名全部引 R01 SSOT；face token 钉死 = **`msgqueue`**（键形 `nop.stream.ledger.msgqueue.*`）。owner doc :298/:325 消费面表述同步归 Phase 3 统一落账。

### 3.9 裁定⑨：读路径 setLimit + 分批（C5-010，R02 三范式选型）

- **选型唯一结论 = setLimit 有界拾取 + 分批循环**：单 poll 查询（:312，现无 LIMIT）加 LIMIT（= 裁定⑥批量上限，同参数），cursor（`id >`）推进分批循环直到无剩余；物化面（:383-390）单批 ≤ limit。
- **fail-fast 边**：`limit ≤ 0` 装配期 fail-fast（复用裁定⑥的参数校验，一处校验两处消费）。
- **列投影不适用声明**：payload 全列必需，R02 三范式第三支排除。
- **与 WI6 门控组合行为**：门控是生产者侧准入（积压超限停止 send），LIMIT 是消费者侧读上界，正交且同时生效——门控触发时生产者停发、消费者按批量推进清空积压，合力闭合单 poll 全量物化 OOM 面。
- 与族内成员（R3-03 W-04、R3-09 W-01 等）同口径（R02 三范式，`inventory-ledgers.md` #17），不另发明。

### 3.10 裁定⑩：FAM-1 族一次裁决

| 面 | 唯一结论 |
|----|---------|
| (a) store 上界来源 | **独立配置** `nop.stream.materialization.store-max-entries`（缺省 1024，≤0 装配期 fail-fast）——不锚 bufferPool 容量族：store 是 checkpoint 恢复记录平面，上界与逐边可配的 queue 容量解耦，进程级保守上界独立成文 |
| (b) overflow 语义 | **许可保留（Minor-2 钉死）**：pool+materialization 象限 `queue.offer` 失败不再 `release()` 改道 store-only（bypass release :354 消除）——acquire 恒 `put`（与非物化路径同形，阻塞背压回归，IBufferPool 上界恢复约束力），store 仅承担 checkpoint 语义记录（恢复回放平面），不引入自旋/阻塞变体。**运维声明（②×⑩交叠）**：守卫 b 经 `nop.stream.local.await-completion-timeout-ms=0` 显式禁用时，materialization 象限回归阻塞 put——消费者停摆引发的时序型互等死锁风险由运维自担（禁用守卫 b 即显式接受该面；守卫 a 恒在不受影响） |
| (b') typed 溢出信号 | **不采**：许可保留后该象限经阻塞 put 不再产生溢出事件；typed 信号唯一可能触发点是 store 超独立上界——该处直接 fail-loud typed 异常即可，第二信号通道无消费场景。禁静默 bypass 维持 |
| (c) 运行期可见性 | 溢出 bypass 消除后全部元素经 queue 到达消费者，运行期记录零静默跳过；store 回放面仅服务 checkpoint 恢复 |
| (d) 假保护改写 | `TestResultPartitionOverflowBypass` 同 diff 改写（C10-005）：现断言把 bypass 钉为交付语义（producer 不阻塞完成 :82 + store 收全部溢出元素 :94）；新断言 = queue 满 + pool + materialization 时 producer **阻塞**（非静默完成）、permit 不被释放（acquire 计数差断言）、store 尺寸 ≤ 上界、消费者 drain 后 producer 解阻且元素序列 exactly-once 到达；既有 `noMaterialization_writeBlocksWhenFull_zeroRegression`（:115-138）断言零改写 |
| (e) C9-007 邻接注记 | 本批修复使 store 有界缩小 n，持锁 O(n) 拷贝争用面自然收敛；本体零承接，归 R4+ |

### 3.11 裁定⑪：测试缝与零回退钉

| # | 测试缝 | 覆盖裁定 | 断言焦点 |
|---|--------|---------|---------|
| 1 | 多线程交叠驱动器 | ① | registerReader/requestSplits × snapshotState 交错，零双份分配零撕裂 |
| 2 | Σsubtask>cores 拓扑构造器 | ② | 提交期 typed fail-fast；有界等待超时 abort |
| 3 | 可控慢 delegate 桩 | ③ | 多扫描轮队列深度有界 + 同 key 单在途 + 丢弃计数 |
| 4 | submit 注错桩 | ④ | 四面零孤儿（池终止/registry 空/重提交安全） |
| 5 | 在途 persist 竞速缝 | ⑤ | 降级×persist 完成时序，零 commit + 计数 |
| 6 | 慢消费数据面桩 + 控制面时延探针 | ⑥ | 控制面时延有界 + 单 poll 批量上界 |
| 7 | 终态 job 接线断言缝 | ⑦ | 终态清理生效 + 活跃 job 追加零改动（+1 pin 对照） |
| 8 | 积压注入缝 | ⑧⑨ | 单 poll ≤ limit 分批清空；probe 返回未消费行数；门控真实可达 |
| 9 | 假保护改写对照缝 | ⑩ | 改写前后红绿对照留痕（C10-005） |

零回退钉（七个，修复 diff 零触碰断言；钉零回改项逐条以 git diff 为空核对）：

1. `TestCachingTableLookup` 并发上限双断言（C10-003 指认的强保护面）
2. `TestJobCoordinatorPerTaskFailure` +1 pin（:111-126，C1-006 接线约束）
3. `TestInMemoryClusterRegistryAttemptHistory`（C1-006 API 面 pin）
4. `TestResultPartitionDeadlock`（C4-001 负向窗口）
5. `TestMultiJvmExactlyOnceRecovery`（C5-010/C8-003 真实传输链）
6. `TestLaunchMainScopeWiring`（C8-011 接线面）
7. `TestJobCoordinatorRecoveryConcurrency`（C7-011 面）

端到端验证义务（rule 22，随 Phase 2 落证）：≥1 条 LOCAL `env.execute()` 全链测试（addSource → 算子 → sink，含 materialization 边——出口点 = sink 收到元素序列 exactly-once 且作业正常终止，覆盖裁定②⑩组合行为）；≥1 条 launch/multijvm 链路测试（数据面 → 控制面消息 → checkpoint 完整走通，覆盖裁定⑥⑧⑨与降级路径）。

### 3.12 与既有防线及设施契约的关系

- **checkpoint 防线四层**：见 §3.5——层 4 为本批新增，既有三层零改写。
- **WI6 门控链**：`RemoteGraphExecutionPlanBuilder` 探测面（能力查找 + `nop.stream.data.db.backlog.limit` 消费）零改动；本批只补后端能力半边（§3.8(b)）。
- **周期清理链设施契约**：⑦⑧的周期载体同时满足 [periodic-task-insulation.md](periodic-task-insulation.md)（失败重臂、不断链、拒绝终态可见）与 R01 SSOT 裁定②防空壳条款（回归断言调度器真实调用清理体，防 1.6-C7-005 空壳先例）。
- **接线验证义务（rule 23）**：`removeTaskAssignment` 真实被终态路径调用；`ChannelBacklogProbe` 真实被 WI6 门控探测；形态 A 清理调度真实挂接生命周期钩子——三项随 Phase 2 落证。

### 3.13 实施边界与落账去向

- **Protected Area 声明**：本批站点全部位于 nop-stream-core / nop-stream-runtime 非生成物 main 源码；不触 `nop-core/`/`nop-xlang/`、不触 `model/*.orm.xml`（JdbcClusterRegistry 用 SQL API 非 ORM 模型改动）、不做 `_` 前缀生成物改动（batch-proposal §二.2 R2-07 行 PA=无）。若实施中裁定引入平台公共落点，照 2507–2509 先例在对应裁定项内批内闭环申报 plan-first。
- **批内吸收成员**：C4-001←C8-013、C4-002←C3-002/C8-014、C8-011←C4-004/C3-008、C5-010←C8-002、C8-003←C7-003、FAM-1←C7-001/C8-001、C1-001←C2-001/C3-001、C1-006←C7-002——全部随主条目同 diff，不单列工单；cra-index 回填以主 ID 收敛 + 括注。
- **C10 验收前置十条**（C10-001~009 + C10-020）随本体同 diff 落地；C10-020 附复核限定（终态定界防 +1 pin 回退，见 §3.7）。
- **owner doc 同步**：`docs-for-ai/03-modules/nop-stream.md`（保留成员行、门控能力 :298、观察面 :325、并发契约节按裁定①②⑤落笔）+ `nop-stream-user-guide.md` + `docs-for-ai/04-reference/source-anchors.md`——**全部归 Phase 3 统一落账**，本 Phase（设计文档）不触发 owner doc 变更。
- **批外零承接**：R4-01（15 项）、R4-02（7 项）、R2-08（连接器批）、`SideOutputRegistry`（1.1-C1-002）、C9-007——本批零顺手修；本批落地后向 roadmap 8.41/8.42 行作「8.16 就绪」注记（Phase 4）。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|----------|
| 裁定②备选：LOCAL 池扩容至 Σsubtask | 线程数随拓扑无界增长，破坏 LOCAL 资源包络；死锁面只是被线程富余掩盖 |
| 裁定②备选：无界队列 | 死锁换 OOM；失败被推迟到内存耗尽时以更难诊断的形态出现 |
| 裁定②备选：仅阻塞边守卫 | 用户函数间接阻塞不可静态完备判定；漏判 = 静默悬挂回归 |
| 裁定①备选：mailbox 单线程漏斗 | enumerator 现实现无内部阻塞点，锁方案已满足；漏斗引入跨线程投递与背压，复杂度无对价 |
| 裁定⑧备选：TTL 纯账龄独立删除 msg_queue | 消费停滞期误删未消费行 = 数据丢失；retention 职责域是已消费终态行，未消费积压归 backpressure |
| 裁定⑦备选：新增终态迁移时刻 DDL 列 | 存量部署 `CREATE TABLE IF NOT EXISTS` 不加列，需 migration 方案另批裁定；terminalAt 白名单 + `assigned_at` TTL 兜底组合谓词零 DDL 已闭合 |
| 裁定⑦备选：anti-join 活跃协调器登记表定界可删集 | failover 重注册窗口内活跃集瞬时不完整，会误删活跃 job 的 attempt 历史，破坏单调保留（+1 pin 回退面）——活跃保护改为「活跃 job 集排除」谓词 |
| 裁定⑩备选：typed 溢出信号通道 | 许可保留 + 阻塞 put 后溢出事件结构性不存在；store 超上界处直接 fail-loud 即可，第二信号通道无消费场景 |
| 裁定⑩备选：store 上界锚 bufferPool 容量族 | store 是恢复记录平面、queue 是逐边传输缓冲，语义域不同；解耦上界避免逐边配置牵动恢复面 |
| 裁定⑨备选：仅列投影 | payload 全列必需，投影不适用；仅列投影也无法闭合全量物化面 |

## 五、与已有设计的关系

- [ledger-retention-policy.md](ledger-retention-policy.md)：R01 族级 SSOT——裁定⑦⑧的参数/键名/形态唯一来源，成员传导行落其 §3.4；本文档零复制参数值；参数修订只走该文档。
- [periodic-task-insulation.md](periodic-task-insulation.md)：⑦⑧周期清理链的绝缘契约（失败可见、重臂、拒绝终态）。
- [registry-lifecycle-contract.md](registry-lifecycle-contract.md)：REGISTRY 域（nop-message-debezium）与本批 `ClusterRegistry` 同名异实体，零站点重叠；辨析注记在裁定⑦落定。
- 下游：R4-01/R4-02（roadmap 8.41/8.42）软依赖本批，引用裁定②⑨⑩的守卫/三范式/溢出语义结论为既有裁定，不重开体裁决；R2-08（连接器批）零共享站点，仅门禁命令同聚合。
- owner doc 落点（Phase 3 统一落账）：`docs-for-ai/03-modules/nop-stream.md`（保留成员行、门控能力、背压契约、观察面 :325 同步、并发契约节）、`nop-stream-user-guide.md`（运维可见行为变化）、`docs-for-ai/04-reference/source-anchors.md`（按实际）。
