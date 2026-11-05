# stream-core 并发正确性、提交路径与可观测性卫生契约（CRA 8.41 R4-01 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立 draft review 见评审记录节）
> Last Reviewed: 2026-11-06
> Source: plan 2534（[2534-cra-phase8.41-r4-01-stream-core.md](../../plans/2534-cra-phase8.41-r4-01-stream-core.md)，CRA Phase 8.41 R4-01 1.1 流核并发正确性束 G01，15 工单全 P2）；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency/`（merge-adjudication §一/§四/§五 + C1/C2/C3/C4/C7/C10 六维度文件）与跨单元 `ai-dev/audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/C10-test-protection.md`（1.2-C10-006 rocksdb 受害侧）；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.4 R4-01 + §一 E1–E6；范式先例 [peripheral-js-engine-lifecycle-rejection-and-idea-plugin-debug-channel-concurrency-contract.md](peripheral-js-engine-lifecycle-rejection-and-idea-plugin-debug-channel-concurrency-contract.md)（输入勘正 + 裁定逐条[决策/理由/被否决备选/零触碰边界] + 测试缝总表 + 评审记录结构）；既有裁定权威 [stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)（裁定①–⑪，本文仅引用不重建）
> Owners: 裁定 A（core 原语域：W-01/W-04/W-05）、裁定 B（runtime 控制面域：W-02/W-07/W-10）、裁定 C（提交通知域：W-03/W-06/W-12 统一设计）、裁定 D（快照与生命周期域：W-08/W-09/W-11）、裁定 E（观测卫生域：W-13/W-14/W-15）、裁定 F（测试缝总表）

本文是 plan 2534 全部 15 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序论证）、被否决的备选、零触碰边界。行号以 live 基线 `563c501566`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值为锚——与 plan 2534 起草基线 `f4185e1a12` 相比本批站点文件零源码触碰（`git diff f4185e1a12..563c501566 -- nop-stream` 零 diff，15 站点文件逐行亲读核对），后续漂移以 live 为准。与 plan 文本不一致的 live 核证事实集中登记于「plan 输入勘正」节，plan 侧回写归 plan 对齐纪律执行前置。

## 既有裁定引用核对（引用不重建——roadmap :183 Deps 列授权）

裁定①–⑪（[stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md) §3.1–§3.11）为本批可引用既有裁定，引用边界逐条确认如下；本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。

| 既有裁定 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| ① enumerator 三态锁纪律 | 无直接站点（LocalSourceCoordinator 零触碰） | 引用其「叶锁零嵌套 + 锁内禁未知代码/阻塞调用」纪律作为裁定 D（W-08 出锁）与裁定 B（W-07 读锁）的锁纪律先例 |
| ② LOCAL 双守卫 | W-11（StreamExecutionEnvironment :609-620 守卫 b 本体） | **红线 1**：守卫 b 有界等待（`nop.stream.local.await-completion-timeout-ms`，缺省 300000/:80 注释段、:642-656 解析链）与超时 abort 语义零回退——裁定 D.3 与其配套（守卫 b 管「正常完成等待」、W-11 管「取消后线程收尾」，互补不重叠） |
| ③ CachingTableLookup 有界化 | 无站点交集（connector 域零触碰） | 仅作「有界队列 + 满时丢弃计数限频 WARN」形态先例引用（裁定 B.3 对齐其禁静默积压纪律） |
| ④ 提交窗口全窗补偿 | 无站点交集（OpsJobManager 零触碰） | 引用其「补偿自身失败 ERROR 显式可见不吞」纪律（裁定 E 抑制改造不吞异常，只限频） |
| ⑤ standby 降级 + 层4 复核 | W-06/W-12（CheckpointCoordinator 同文件） | **红线 2**：onCompletePersistSuccess 入口复核（:970-984）与 commit 扇出提交点复核（:1036 `activeAtDispatch` + :1042-1048 `leaderActive`）语义零回退——裁定 C.2 的分窗口 handler 只改「任务体在哪条线程执行」，不改「任务体是否执行/复核谓词」；TestStandbyDegradeCancelsInFlightPersist 钉零触碰 |
| ⑥ poller 分面 | W-13（PollingJdbcMessageService :573/:578 在数据面 poll 路径） | **红线 3**：控制面/数据面分面后的线程模型为 W-13 抑制改造的组合前提——裁定 E.1 的抑制器为站点局部对象（无跨线程共享状态），分面语义零回退 |
| ⑦ 账本终态清理接线 | W-07（InMemoryClusterRegistry 同文件） | **红线 4**：removeTaskAssignment（:190-192）终态接线语义、:191 读者持旧引用无害面、removeJobTaskAssignments prefix 清理段（:194-208，8.16 产物）零回退——裁定 B.2 读侧改造零触碰三者；TestTaskAssignmentTerminalCleanup 钉零触碰 |
| ⑧ msg_queue 保留 + 门控 | W-13 补遗（PollingJdbcMessageService :228-235 cleanup-fail 日志点为裁定⑧形态 A 产物） | 引用其「清理失败显式 ERROR + 可见计数 + 调度不断链」语义——W-13 抑制只改日志量、不改计数与不断链结构 |
| ⑨ 读路径 setLimit 分批 / ⑩ FAM-1 许可保留 | 无站点交集 | 三范式/溢出语义结论按引用处理不重开体——本批无 R02 三范式直接消费成员；W-15 稳定键形态如需对齐按引用处理 |
| ⑪ 测试缝与零回退钉 | 全批测试面 | **红线 5**：七零回退钉（TestCachingTableLookup/TestJobCoordinatorPerTaskFailure/TestInMemoryClusterRegistryAttemptHistory[=W-07 零回退锚]/TestResultPartitionDeadlock/TestMultiJvmExactlyOnceRecovery/TestLaunchMainScopeWiring/TestJobCoordinatorRecoveryConcurrency[W-10 同文件零回退锚]）钉内断言全批 diff 零触碰；九测试缝的形态惯例（monitor 探针/latch seam/手工桩）为本批裁定 F 新钉的形态基准 |

## plan 输入勘正（live 亲读核证，裁定以本节为准）

| # | plan 2534 表述 | live 事实（file:line @ `563c501566`） | 对裁定的影响 |
|---|---|---|---|
| K1 | 勘误 3「live grep StreamMetricsReporter 无 error/warn 日志点」 | `StreamMetricsReporter.report()` 的 catch 分支存在周期性失败 WARN（:119，`scheduleAtFixedRate` :79 每 interval 节奏、`e.toString()` 无堆栈、无抑制）——merge-adjudication §五 C7-005 行「含 StreamMetricsReporter 补遗」的注记**属实**；同文件 :100 为 stop 路径一次性 WARN（非周期，不入覆盖集） | 裁定 E.1 覆盖集并入该点（W-13 家族第 11 点）；「如核实为零命中则登记勘正」的两分支取前者落定 |
| K2 | W-02 站点表仅列 :565-570 重复槽位分支 | TaskManager.receiveAssignment 的静默忽略面含**两分支**：:565-570 containsKey 前检（:566/:567 WARN+release+return）+ :585-589 putIfAbsent 竞速败者分支（:586-588 `existing != null` → release+return，**零日志零上报**）——同一 C1-004 机制的两个站点 | 裁定 B.1 以单一 compute 收敛两分支（天然并合），不新增工单 |
| K3 | 若干站点行号 ±1~3 微漂移 | StreamOpsHttpServer setExecutor = :126-127（plan :127-128）、catch(StreamException) 分派 = :257-260（plan :256）；MicrometerStreamTaskMetrics.releaseHolder = :133-139（plan :133-137）；StreamExecutionEnvironment.shutdownLocalExecution = :665-673（plan :665-676）；SourceReaderOperator offerBarrier 直通分支 = :524-532/:533-542（plan :525-534/:535-546）、injectBarrier = :556-569（plan :555-575） | 机制与站点集零漂移；本文锚定以 live 值为准 |
| K4 | （无表述） | 其余 10 站点文件锚点逐行命中零漂移：BasicTypeInfo :18/:38-42/:44-51、TwoPhaseCommitSinkFunction :176-205（:192/:193/:196/:198/:201-203）+restoreFromEpoch :234-277、InputGate :118/:201/:532/:561/:573/:598、CheckpointBarrierTracker :111/:124/:131/:133/:140-141/:143、TaskExecutor :165-169/:196-199/:222-225/:379-384、SubtaskTask cancel :148、StreamTaskInvokable :119/:497-499/:613-638/:820-828、MemoryKeyedStateBackend :109/:634-636、MemoryStateSerDe :143-147/:328-341、MemoryValueState :25、EquiJoin :183-185、OverWindow :129-131、WindowOperator :583-592/:630-635/:820-828、TaskManager :149-159/:162/:301/:310-318/:367/:455/:529-609/:617-631/:997-1013/:1065-1084、CheckpointCoordinator :1037/:1090/:1106/:1133-1142/:1184/:1579-1611/:1643-1688、InMemoryClusterRegistry :44/:151-166/:172-179/:181-187/:190-192/:194-208、JobCoordinator :159/:723-736/:983/:1231/:1304-1329/:2944-2951/:2957、PollingJdbcMessageService :228-235/:573/:578、TaskManagerMain :209/:216、RetentionCleaner :186-190、TaskCheckpointWiring :273-287、JdbcLeaderElector :139-144 | 核证范围登记：15 站点文件 + W-13 并入面 3 文件 + 对照先例 6 文件（EngineMetrics/SupervisionLoop/StreamElementCodec/TaskMailbox/Mail/TaskNodeMetrics）全部亲读 |

## 裁定 A core 原语域（W-01/W-04/W-05）

### A.1 W-01：BasicTypeInfo 类型注册表并发化

**决策**：`INSTANCES`（:18）改 `ConcurrentHashMap`；`of()`（:44-51）改 `computeIfAbsent` 单调用点闭合。`register()`（:38-42）静态初始化路径语义零改动（九内建型填充在类初始化锁内单线程执行，CHM put 与 plain put 在该路径下语义等价）。

**理由（可见性与时序论证）**：与同模块 `StreamElementCodec.DECODED_VALUE_TYPES`（`execution/transport/StreamElementCodec.java:52`，CHM+computeIfAbsent 既有正确形态）同型。computeIfAbsent 的 JDK 契约保证「整个调用原子执行、映射函数对同一 key 至多应用一次」——同 key 并发懒注册只可能产生一个实例，双实例行为分叉在类型层面不可能；异 key 并发（`of(CustomA)` × `of(CustomB)`）由 CHM 分段原子性承载，HashMap 并发扩容结构损坏（进程级全局污染）被消除。触发时序：T1/T2 并发调 `of` 未命中 → T1/T2 各自进入 compute → CHM 保证映射函数互斥执行 → 单实例发布。

**被否决的备选**：
- **同步双检**（synchronized + 双检 + put）：需要额外锁对象与两次读；双检 + 直接 put 在 check 与 put 之间仍存在覆盖写窗口（后到线程的 put 覆盖先到实例，产生瞬时双实例可见性）；若改 putIfAbsent 则丢弃返回值后又需二次读——形态比 computeIfAbsent 复杂且无增量收益。computeIfAbsent 一行闭合且有引擎内先例。
- **`synchronized` 修饰 of() 整方法**：类型解析服务序列化热路径，全局串行化无对价。
- **预注册全部用户类**（消灭懒注册）：注册时机归调用方，不可枚举，非修复形态。

**零触碰边界**：九内建型常量（:20-28）、`register()`、`getSerializer()`/`getTypeClass()`/`toString()` 零改动；`BasicTypeInfo` 不新增 equals/hashCode 重写（实例身份语义维持，本次裁定不改写其序列化身份契约）。

### A.2 W-04：InputGate 水位数组原子化

**决策**：`currentWatermarks`（:118）改 `AtomicLongArray`；`channelIdle`（:201）维持 `boolean[]` **零改动**。读入口 `getCurrentWatermark`（:532）/:561/:573/:598 与任务线程内部读写点（:357 初始化/:644 单通道写/:1222-1226 watermark 更新/:1303-1307 内部聚合）全部改经 AtomicLongArray get/set。

**理由（热路径写面论证）**：long 元素撕裂是 JLS 17.7 定义的缺陷本体（64 位写可分裂为半写），AtomicLongArray 元素读为 volatile-load 语义，单元素撕裂不可能。任务线程热路径写从 plain store 变 volatile store：写频率为每通道每 watermark 更新一次，x86 上 volatile store 为普通 store 加 storeLoad 屏障（纳秒级）、ARM 上为 release-store（近零成本）——对齐数据面不可测量级；对照备选 synchronized 快照拷贝：每次抓取分配新数组且与任务线程写互斥，抓取延迟与垃圾成本均更高。`channelIdle` 维持零改动的论证：boolean 非 64 位类型，数组元素写天然原子（无撕裂面）；陈旧读的后果限单次抓取 idle 判定滞后、下次抓取自愈——与审计「陈旧无害面维持零改动」一致。跨元素一致性声明：getCurrentWatermark/getMaxActiveChannelWatermark 的多元素聚合读仍为各元素独立原子读（非全数组快照）——「单元素撕裂」已消除，「聚合时刻跨元素不完全一致」为观测快照固有语义（与 C2-009 既有 P3 口径同类），升格为全数组快照需锁化热路径写，收益不抵成本，登记为已接受语义。

**被否决的备选**：
- **synchronized 快照拷贝**：抓取侧与任务线程互斥放大抓取时延 + 每次抓取分配数组；本读取入口同时服务任务线程内部聚合（:1303-1307），锁化波及热路径。
- **channelIdle 一并 AtomicIntegerArray/CHM 化**：无撕裂面，纯加复杂度。
- **volatile long[] 包装类**（`volatile Long[]` 或自定义 holders）：每元素对象头开销 + 装箱，热路径不可接受。

**零触碰边界**：数组语义（初始 `Long.MIN_VALUE`、idle 排除合并、`lastAcceptedBarrierIds` :188-191 任务线程单写者 plain 数组豁免）零改动；`MicrometerStreamTaskMetrics` 抓取链（:197-218 mergedWatermarkOrNaN/channelLagOrNaN）零改动（经既有读入口透明受益）。

### A.3 W-05：SourceReaderOperator.reader 可见性闭合

**决策**：`reader` 字段（:100）加 `volatile`（单点修复）。回调 else 分支（:230/:330）维持 preOpen 缓冲形态**不采 fail-fast**。

**理由（时序论证）**：`open()` 写 `reader`（:255，volatile store）与跨线程回调读 `getReaderRaw()`（:226/:325，volatile load）之间建立 HB 边——open 完成后的回调必见非 null reader。两分支并集闭合全部交错：回调先于 open → splits 入 `preOpenSplits`，`flushPreOpenSplits`（:263 一次性调用）收走（既有主时序，既有行为登记 (g) 零回退）；回调后于 open → 直投 reader。volatile 只补「open 之后跨线程回调」的可见性缺口（C2-002 复核核证：库内 4 个在库枚举器自投递单线程使缺陷当前不可达，SPI 自定义跨线程枚举器为潜伏面），不改变缓冲/flush 结构。

**被否决的备选**：
- **else 分支改 fail-fast**：把「wire 先于 open」的合法早到 split 变成部署失败（合法主时序被误杀），与既有行为登记 (g) 冲突。
- **判空-入队并入 synchronized(preOpenSplits) 同一监视器**（C2-002 建议形态）：`reader` 写不在该锁内，锁不建立 reader 写/读的 HB 边——仍需 volatile，纯加锁无增量。
- **reader 就绪标志 + 回调侧驱动 flush**：改写一次性 flush 语义（(g) 回退）。

**零触碰边界**：`open()` 幂等守卫（:243-248）、`flushRestoredSplits`/`flushPreOpenSplits` 结构、回调安装时序（wire 期 :220-234 → open 期 :319-334 替换）、`onSplitsDelivered` volatile 发布机制零改动。

## 裁定 B runtime 控制面域（W-02/W-07/W-10）

### B.1 W-02：TaskManager 两阶段分配路径世代治理

**决策**（红① 驱动器对应绿侧语义：新代分配不被静默忽略）：

1. **重复槽位分支 = 世代分派，收敛进单一 `runningTasks.compute`**（:565-570 containsKey 前检与 :585-589 putIfAbsent 败者分支[K2]并合）：compute lambda 内读出 incumbent 比较 `getFencingEpoch()`（RunningTask :341 既有访问器，任务构造时已携带 assignment epoch :581）——
   - **incumbent.epoch < assignment.epoch（新代到达，旧代占位）→ CC-02 同款置换**：复用 deployTask compute 的 `selectDisplacedTask` 决策函数**形态**（TaskManager :750-770 置换+displacedOld 处置形状），incumbent 取消（协作取消+许可以 `semaphoreReleased` CAS 释放，同 updateFencingToken :1076-1079 形态）+ 新 RunningTask 入位 + WARN 携带两代 epoch；**硬约束[设计评审 Major-3]：分支比较为严格不等（`incumbent.getFencingEpoch() < assignment.getFencingEpoch()` 才置换）——不直接调用 `selectDisplacedTask`（其等值语义=候选置换[TestConcurrencyWindows2326 :101-105「equal generation: redeploy semantics preserved — the candidate displaces」钉死]，与本分支 `>= → 拒绝` 语义相悖），仅复用其置换+displacedOld 处置形态**；
   - **incumbent.epoch >= assignment.epoch（同代重复或旧代迟到）→ 拒绝 + reportAssignmentFailure typed 上报**（复用 :532/:548/:598 既有通道），本分配许可释放（:568 等价），incumbent 不受影响；
   - compute 败者路径（:585-589 现状静默 release+return）随并合自动获得同一分派语义（其 return-null/incumbent 分支即拒绝分支）。
2. **installInvokable（:617-631）加 epoch 相等校验**：方法新增 `long fencingEpoch` 参数（调用方 Embedded/RpcDistributedExecutor 从 assignment/descriptor 透传——同模块签名变更，impl 叶）；`runningTask.getFencingEpoch() != fencingEpoch` → **拒绝装入（不调 setInvokable）+ LOG.warn**（含期望/实际 epoch，与 :622 缺槽 WARN 同形态）。缺槽分支（:621-624）静默 return 维持现状（C8-009 域防混零触碰）。
3. **deployTask compute 内最终重验**：compute lambda（:750-770）内对 `currentFencingEpoch.get()` 与 descriptor epoch 重验（把 :686-696 入口检查推进到与槽位写入同一原子段）；`selectDisplacedTask` 决策函数本体零改动（重验是决策函数的外围前置）。
4. **updateFencingToken 窗口处置 = 接受语义显式声明**：`updateFencingToken`（:1065-1084）的 getAndSet 与 removeIf 非原子窗口（审计时序 2）由第 3 条闭合主面——deployTask compute 内重验后，`updateFencingToken` 已完成时在途旧代 deploy 的插回不可能（重验发现 descriptor.epoch < currentFencingEpoch 即拒绝）；残余面 = compute lambda 内 `currentFencingEpoch.get()` 读到 JMM 陈旧值（getAndSet 尚未对该线程可见）的纳秒窗，且 JDK CHM 同 key 节点锁使 removeIf 判定与 compute 写入互斥（要么 removeIf 先完成则 compute 重验兜住、要么 compute 先完成则 removeIf 移除）——残余仅剩「removeIf 扫描完成之后 compute 才以陈旧 fencing 判定插回」这一 JMM 层窗口，自愈路径在库（新代同槽位后续 deployTask 按 CC-02 置换 / `RunningTask.run` finally 自摘槽位）。登记为已接受残余（watch-only 语义），不引入跨三入口的全局序登记簿（复杂度与恢复路径低频性不成比例）。

**被否决的备选**：
- **新代到达也拒绝+上报**：把恢复责任推回协调器多一轮恢复循环（烧重启预算）；置换与 deployTask 的 CC-02 语义一致（同槽位旧代让位新代）——采置换。
- **同代重复也置换**：同代重复投递是恢复协议异常，置换会杀死健康 incumbent 的进行中工作（attempt 无差异）——拒绝+上报保住 incumbent 且使异常可见。
- **全局锁化 receiveAssignment+installInvokable**：两阶段协议跨两条 RPC 消息，锁不住消息间隙；复杂度无对价。
- **installInvokable 增加 typed 上报**（reportAssignmentFailure）：该方法无 assignment 对象（参数仅 job/vertex/subtask + invokable），合成 assignment 引入伪造协议面；WARN + 拒绝装入已满足「不混装」验收面。
- **updateFencingToken 全窗闭合**（跨三入口 epoch 版本化登记簿）：见第 4 条，复杂度无对价且自愈在库。

**零触碰边界**：`deployTask` CC-02 语义与 TestConcurrencyWindows2326 pin（staleGenerationCandidateDoesNotDisplaceNewerIncumbent）零改写（决策函数本体零改动）；`TestFencingTokenRejection` 零触碰；cancelTask/triggerCheckpoint/notifyCheckpointComplete 既有 epoch fail-fast（:919/:939/:997-1003）零改动；`updateFencingToken` removeIf 结构零改动。兼容性核证：`TestTaskManager.testDuplicateAssignmentIgnored`（:189-203）的可观测断言为 `getRunningTaskCount()==1`（同代重复不产生第二任务）——「拒绝+上报」语义下该断言维持绿，钉零改写（其测试名措辞与新语义的漂移不构成断言回退）。

### B.2 W-07：InMemoryClusterRegistry 读侧入锁

**决策**：`getTaskAssignment`（:172-179）与 `getAttemptHistory`（:181-187）的读侧复制/取尾操作移入 `synchronized (taskAssignmentHistory)`（与写侧 :151-166 同一监视器）。**采锁形态，不采 CopyOnWriteArrayList**。

**理由**：改动最小（两读点加同步块），直接满足仓库门禁先例「synchronized 字段的迭代点必须在锁内」；写侧 read-modify-write（monotonic attempt 检查 :157-164 依赖「读尾+追加」原子）在锁内天然成立。生产线程图不变式声明：生产接线中同键读写当前被 recoveryLock 串行（C2-004 复核核证：getAttemptHistory 唯一 main 调用点 seedAttemptCountersFromRegistryLocked 须持 recoveryLock、getTaskAssignment main 零调用点）——本修复后 registry 自身线程安全**不依赖**该假设成立（锁内互斥自足），recoveryLock 串行假设的存废与本裁定解耦；直接以该 registry 构建诊断/RPC 读取面时无 AIOOBE/未发布元素面。

**被否决的备选**：
- **CopyOnWriteArrayList**（append 少读多确契合）：写侧需改「copy→add→set」且 monotonic 检查的原子性需 CAS 循环或额外锁承载，removeTaskAssignment/prefix 清理段需适配——复杂度反升，改动面大于两读点入锁。
- **读侧快照拷贝不出锁**（volatile 读 + 防御拷贝）：ArrayList get 与并发 add 竞争的本体（旧数组引用与新 size 错配）未闭合。

**零触碰边界**：`assignTask` append 语义与 monotonic 检查零改动；`removeTaskAssignment`（:190-192，裁定⑦ 接线对象）语义零改动（读者持旧引用无害面维持）；`removeJobTaskAssignments` prefix 清理段（:194-208）零改动；`TestInMemoryClusterRegistryAttemptHistory` 钉（裁定⑪ 钉#3）零触碰。

### B.3 W-10：barrier fanout 有界队列 + 满时丢弃

**决策**：`fanoutExecutorFor`（:2944-2951）从 `newSingleThreadExecutor`（无界队列）改 `new ThreadPoolExecutor(1, 1, 0, MILLISECONDS, new LinkedBlockingQueue<>(100), 既有线程工厂, 满时丢弃 handler)`。**满时丢弃（不采有界等待）**：WARN（限频，并入裁定 E.1 抑制载体）+ 丢弃计数指标 + 该 barrier 丢弃。

**容量值定夺 = 100**：单节点 barrier 入队速率 = 1 任务/checkpointInterval（缺省秒级）——100 ≈ 100 个 checkpoint 周期的缓冲；节点 send 挂死超过 100×interval（缺省 ≈100s+）后丢弃生效，该 epoch 本将 checkpointTimeout abort 兜底（审计定级论证：语义无损）；内存上界 KB 级。与 tm-commit（1000）/terminal-report（64）治理标准一致化——runtime 侧唯一无界队列任务池清零，容量按「节点屏障缓冲」语义取中值。

**丢弃 vs 有界等待定夺 = 丢弃**：有界等待（提交线程阻塞等待入队）把 JC 触发线程（periodicCheckpointScheduler :1307+ / savepoint 终局 :2957）阻塞在挂死节点上——恰是 CC-06 fan-out 出触发线程要消除的面（:975-983 注释承诺）；丢弃使触发线程永不被单节点反压，损失=该节点该 epoch barrier 缺席（超时 abort 兜底，CHECKPOINT_ABORTED 事件可观测）。

**指标名 = `nop.stream.engine.barrierFanoutDrops.total`[设计评审 Minor-8 定稿——载体 EngineMetrics 其 javadoc :27「Meter names follow the `nop.stream.engine.*` convention」+ :34-40 七常量全部 engine.*——job.* 破坏类-层对齐[对照 TaskNodeMetrics↔`task.*`]]；owner doc 指标表同步义务登记（E5，W-10 行为变更面）。

**C2-010 防混声明（P3 零触碰）**：`barrierFanoutExecutors` 的 computeIfAbsent 结构（:159/:2945）与 stop 排水 :723-736（shutdown → await(5s) → shutdownNow）顺序零改动——本批只换 executor 构造与拒绝 handler，创建-关闭竞态面（C2-010 本体）不修不触。

**被否决的备选**：
- **有界等待**：触发线程被挂死节点反压（CC-06 语义回退）。
- **CallerRuns 回退**：饱和把 barrier 发送拉回 JC 触发线程——与 W-06 同型缺陷在本池重演（禁）。
- **容量 1000（对齐 tm-commit）**：节点挂死场景下 1000×interval 缓冲无意义（超时 abort 早已兜底），徒增内存与失效发现时延。
- **AbortPolicy + 两 execute 点 catch REE**：REE 语义是编程错误告警形态；显式 handler 单点覆盖 :983/:2957 两点，调用形态零改动。

**零触碰边界**：CC-06 per-node 串行语义（单线程执行器、epoch 有序）零回退；裁定⑪ 钉#7 `TestJobCoordinatorRecoveryConcurrency` 零触碰；:983/:2957 两 execute 点调用形态零改动（饱和处置内聚于 handler）；`TestBufferPoolWiring` 等无此池面测试零触碰。

## 裁定 C 提交通知域（W-03/W-06/W-12 统一设计——单 handler 分窗口语义）

### C.1 W-03：TwoPhaseCommitSinkFunction 基类提交守卫

**决策**：per-instance **in-flight epoch 集合 CAS 守卫**（不采单飞）。基类增加 `Set<Long> inFlightCommits = ConcurrentHashMap.newKeySet()`（per-instance 字段）；`finishCommit`（:176-205）的 per-epoch 提交序列改：`inFlightCommits.add(eid)` 失败 = 另一线程在提交中或同 epoch 在飞 → 本线程跳过该 eid（去重达成，计数/WARN 可观测）；add 成功 → `commit(eid)` 锁外执行（阻塞 I/O 出锁设计 :196 维持）→ finally `inFlightCommits.remove(eid)`。空 map else 分支（:201-203 `commit(epochId)` 直调）同守卫包裹。`restoreFromEpoch`（:234-277）的 toCommit 循环 commit（:250-262）同构收口（in-flight 守卫跨 `setPendingCommits` 换绑仍有效——集合是实例字段非 map 字段，闭合 C2-007 失效面）。

**javadoc 契约显式声明（与子类幂等兜底的语义边界）**：基类守卫保证「运行期并发双提交不可能」（fail-fast 底线）；子类 `commit` 幂等仍由子类负责——守卫不覆盖持久化重放面（restore 重放/崩溃后重复提交），jdbc-2pc 台账/file rename 的幂等兜底语义保持。与 W-06 的两层关系：W-06 修复恢复「fanout 单线程」调用方串行契约后，本守卫为其 fail-fast 底线——若未来新入口再打破串行，守卫兜住正确性；两层防线不互替（merge-adjudication :39 联动条款）。

**被否决的备选**：
- **单飞**（基类全局提交锁 / 基类内置单线程执行器）：全局串行化使多 epoch 重试互相阻塞，且执行器引入基类资源生命周期（close 语义）扩张——13+ 子类扩展契约不容。
- **synchronized(pendingCommits) 包住 commit**：I/O 进锁段（C2-007 明言更差，pendingCommits 监视器覆盖秒级 I/O）。
- **remove-then-commit**（先 remove 再 commit）：改写「commit 失败后 epoch 留在 pending 供重试/subsuming」语义（restoreFromEpoch :255-257 的 retain-for-subsuming 依赖）。
- **仅 javadoc 声明串行契约无运行时守卫**：F10 口径——契约无运行时守卫即缺陷本体。

**零触碰边界**：`saveState` 防御拷贝（:162-166）、`abort` 默认实现（:66-68）、`copyForSubtask` 契约（:129-153）、`restoreFromEpoch` 的 durable/non-durable 分派（:240-248）零改动；`TestTwoPhaseCommitSinkFunction` 9 个单线程 restore 测试零改写（C10-011 改写义务仅涉并发测试 :176-208，同 diff 改写）。

### C.2 W-06 + W-12：提交执行器分窗口复合 handler（统一设计，缺陷分离工单）

**决策**：两处执行器采**同一语义表、分窗口分支**（R1 F-2 强制——饱和窗口内联会保留 C2-003 缺陷、红③ 不可能通过）：

| 窗口 | 判定 | TM commitExecutor（:310-318） | CC commitFanoutExecutor（:1133-1142） |
|---|---|---|---|
| 正常入队 | 队列未满且未 shutdown | 入队（现状） | 入队（现状） |
| **饱和**（未 shutdown，队列满） | offer 失败 | **CallerRuns inline 维持**（R5-CC-10 钉钉死：调用方=message 派发/协调转发线程，不持全局监视器[审计失效面② 已裁定可接受]） | **溢出补投**：任务体（含闭包）offer 进有界溢出 deque（容量 1000 同队列）+ 溢出计数递增 + 限频 WARN；调用方（持 CC 监视器）立即返回，**零 I/O 零阻塞零内联**；溢出 deque 满（二次溢出）→ 丢弃 + 丢弃计数 + WARN（背底下文=该 epoch 由下一 epoch subsuming commit 或 checkpoint 超时 abort 兜底——与 C4-012 abort 路径同声明的兜底语义） |
| **shutdown** | executor.isShutdown() | **always-inline**（W-12 修复面：原 :315 `if (!executor.isShutdown())` 静默丢弃 → 无条件 `r.run()`；CC-17 先例语义） | **always-inline**（W-12 grace window 面：`CallerRunsPolicy` isShutdown 后静默丢 → 换分窗口 handler，shutdown 窗口 inline；三 execute 点 :1037/:1106/:1184 均持 CC 监视器，shutdown 终态下一次性 caller 线程执行为 CC-17 已裁定语义） |

**CC 溢出补投形态细节**：`getOrCreateCommitFanoutExecutor` 返回的执行器提交包装任务 `() -> { drainOverflow(); body.run(); }`——fanout 单线程在每个任务体前排空溢出 deque（补投任务与当期任务同线程串行，且补投先于更晚到达的当期任务执行——「retry→current-epoch order」串行契约在饱和窗口**恢复为真**）；溢出元素为完整包装 Runnable（携带各自 body，三个 execute 点形态统一承载）；补投任务执行时同样先 drain（嵌套排空收敛）。容量上限防退化：溢出 deque 满即丢弃+计数（与 W-10 丢弃形态同族），禁第二无界结构。

**理由（时序论证）**：CC 饱和触发时序——T1（fanout 线程）被慢 sink `finishCommit` 钉住 → 完成线程 T2（持 CC 监视器）在 :1037 execute，队列满 → pre-fix CallerRuns 把 `retryFailedCommits + notifyParticipantsFinishCommit` 的 sink file/JDBC I/O 内联在 CC 监视器内（checkpoint 全链停摆 + 与 T1 并发 finishCommit 击穿串行承诺）；post-fix T2 的 handler 做「溢出 offer（非阻塞，纳秒级）+ 计数 + WARN」后返回——监视器持有时长缩至登记操作，串行契约经任务边界补投恢复。补投不丢论证：溢出任务在 T1 完成当期任务后的首个任务边界被 fanout 线程执行——交付延迟有界于慢 sink 时长，交付本体无损（有界作业尾提交不 stall——typed 拒绝形态正是因该面被否决，见下）。

**与既有裁定⑤ 层4 的零回退落款（红线 2）**：分窗口 handler 只改「任务体在哪条线程执行」，不改「任务体是否执行/复核谓词」——onCompletePersistSuccess 入口复核（:970-984）与 fanout 提交点复核（:1036/:1042-1048）语义零改动；溢出补投任务体含同一层4 复核（`activeAtDispatch`/`leaderActive` 捕获语义随闭包保持）。`TestStandbyDegradeCancelsInFlightPersist` 零触碰。

**CC-01 pin 兼容论证**：`TestCommitFanoutOffMonitor2326`（latch seam + monitor 探针 + 600ms 慢 participant mutation probe）钉「commit fan-out 不在 CC 监视器内」——溢出补投全程不内联（含饱和窗口），pin 维持绿且语义加强；:944-961/:1028-1034 CC-01 注释承诺段（"NOT under this monitor / Serial execution preserves retry→current-epoch order"）在 post-fix 全窗口为真，**注释零改动**。

**审计建议① 与红③ 互斥声明（R1 F-2 落定）**：审计建议①（仅打点不改行为——CallerRuns 保留+加 coordinator 侧计数+注释修正）与红③ 首选断言（monitor 探针在饱和回退下不被阻塞）互斥——饱和内联即缺陷保留，本裁定不采①；红③ 断言由溢出补投分支承载（计数递增 + 探针不阻塞 + 补投不丢三断言齐备）。

**被否决的备选**：
- **审计建议①（仅打点）**：与红③ 互斥（上句）。
- **审计建议②（标记 + fanout 线程自旋补投）**：采其「不丢+保监视器」目标但「自旋轮询」改任务边界 drain（自旋烧 CPU 且无唤醒点）；本裁定的溢出 deque + 任务边界排空即其修正形态。
- **审计建议③（CallerRuns 分支断言告警）**：告警不改变行为，监视器仍被占用。
- **CC 饱和窗口 typed 拒绝+计数**：拒绝=该 epoch commit fan-out 未派发且无自动重试记录（failedCommitParticipants 只在 notify **失败**时登记，拒绝发生在 notify 之前）——有界作业最后一个 checkpoint 的尾提交 stall（恰是 plan 369 选择记录明言不可接受的面：「a dropped notifyCheckpointComplete is only healed by the NEXT epoch's subsuming commit」）——不采。
- **TM 饱和窗口改 typed 拒绝/补投**：R5-CC-10 钉（TestTaskManagerCommitQueueBound）钉死非 shutdown 态 CallerRuns+不丢+计数语义——零改写义务（既有行为登记 (e)）。
- **stop 排水重排**（备选②：先 taskExecutor await 后 commitExecutor shutdown+短 await 再 shutdownNow）：不闭合 CC 侧 grace window 丢弃面（该面独立存在），且改变三宿主 stop 语义——采 always-inline 后排水重排无增量收益。
- **单一 always-inline 全窗口**（plan 草案 R1 前形态、审计建议①推广形态）：CC 饱和窗口内联=缺陷保留（R1 F-2 实证否决）。

### C.3 W-12：stop 竞速形态收尾（订正清单 + 宿主序边界）

**isShutdown 分支处置**：见 C.2 表（TM 两窗口均 inline、CC 两窗口分列）。`saturatedCommitQueuePolicy` 去 :315 isShutdown 条件。

**javadoc/WARN/计数订正清单**（随 diff 落地）：
1. TaskManager :301 javadoc（"CallerRuns applies natural backpressure instead and never loses a notification"）：补「shutdown 态经 always-inline 同样不丢（C4-012 订正）」句——恢复其为真。
2. TaskManager :312-314 WARN 文本：分窗口两文本——饱和窗口维持「CallerRuns backpressure（R5-CC-10）」、shutdown 窗口改「shutdown drain: running the commit on the caller thread（C4-012）」——消除「声称 caller-run 实未执行」失真。
3. `nodeMetrics.commitCallerRun()`（:311）计数语义订正：**仅真实 inline 递增**——新形态下两窗口均 inline，故两窗口都递增（C4-012 缺陷形态=计数而未执行——消除）；R5-CC-10 钉内计数断言零改写（其断言的正是「饱和即计数」，语义收紧后仍真）。
4. CheckpointCoordinator :1674-1676 grace javadoc（"a shutdown racing a completion still delivers the epoch's commits"）：订正为「queued fan-outs drain + post-shutdown submissions deliver inline（溢出补投 deque 残余随 shutdownNow 丢弃，与队列任务同语义）」[设计评审 Minor-7 补溢出 deque 面]——原文对 pre-shutdown 已入队任务为真、对新提交为假，post-fix 全真。
5. CC 侧新增溢出/丢弃计数指标登记 owner doc 指标表（E5，与 W-10 计数行同批落账）。

**三宿主关闭顺序边界声明**：TaskManagerMain（tm.stop :209 → messageService.close :216——窗口敞开；inline 形态下窗口内提交由派发线程内联执行，可观测不丢；**宿主序零改动**）；RpcDistributedExecutor（服务先停、tm.stop 最后——窗口闭合，零改动）；EmbeddedDistributedExecutor（进程内调用、finally 序同形状，零改动）。预设 always-inline 形态下 TaskManagerMain 零 diff（备选排水重排被否决的对应面）。

**TestTaskManagerCommitQueueBound 钉（R5-CC-10）零回退论证**：钉内非 shutdown 态断言（CallerRuns 回退+不丢通知+计数/depth gauge）在新形态下全部维持——非 shutdown 饱和仍 inline 同一任务体；isShutdown 分支其零覆盖=C10-016 缺口本体（红⑦ 补钉，不动旧钉）。

## 裁定 D 快照与生命周期域（W-08/W-09/W-11）

### D.1 W-08：CheckpointBarrierTracker 登记先行、执行出锁

**决策**：`triggerCheckpoint`（:111 synchronized）拆两段——**登记段（锁内）**：duplicate 检查（:112-115）+ inFlight 登记（:124）+ barrier 构造；**执行段（锁外）**：offerBarrier 调用（:131/:140-141 两分支）移出 synchronized 块，拒绝时回滚 `inFlight.remove`（对齐 :133/:143 既有回滚形状）。operators 为空或 head 非 source 的场景（:128 条件跳过）登记即完成，零变化。快照回调与 inFlight 登记簿的互斥关系保持：登记（锁内）与 ACK 路由/完成判定（`acknowledgeOperator` :159 synchronized 锁内）互斥不变。

**理由（含死锁面论证）**：出锁后 TRK 监视器持有段 = 纯登记簿操作（LinkedHashMap put/remove + 计数），无用户回调、无 I/O——裁定① 的「锁内禁未知代码/阻塞调用」纪律在 TRK 成立（叶锁零嵌套：triggerCheckpoint 调用链不持其他监视器进入，offerBarrier 在锁外执行亦不引入嵌套序）。先到 ACK 竞态论证：offerBarrier 出锁执行期间，该 epoch ACK 可能先于 triggerCheckpoint 返回到达——inFlight 条目已登记（登记先于 offerBarrier），`routeAckToEpoch`（:161）按携带 id 命中正常路由；拒绝回滚竞态：offerBarrier 返回 false 后 remove，窗口内已到达的 ACK 在路由命中后遇条目移除 → 走既有 stale/duplicate 丢弃分支（:165-169 debug log）——trigger 返回 false、调用方不重试该 id（既有语义），无 ACK 消费者期待，可接受。缺陷面消除：`SourceReaderOperator.offerBarrier` 的 finished（:524-532）与 mailbox==null（:533-542）直通分支改在调用方线程**锁外**执行 `injectBarrier→snapshotState`（:556-569）——ACK 线程（:159 synchronized）不再被快照时长串行化；mailbox 正常路径（:543-552 Mail.control）零改动。

**被否决的备选**：
- **备选②（直通路径 WARN 暴露回退频率）**：暴露不消除 ACK 串行化本体。
- **完全异步化**（快照交独立执行器）：涉快照回调线程归属契约评估——登记为 Non-Blocking Follow-up 深化候选（plan 已登记），本批定界「出锁但仍在触发线程执行」。

**零触碰边界**：`acknowledgeOperator` 路由/校验/完成判定零改动；inFlight LinkedHashMap 结构零改动；`TestCheckpointBarrierTrackerConcurrency`/`TestSourcePullBarrierInjection` 零触碰。

### D.2 W-09：queryable-state mailbox 交接（预设① 定案）

**决策**：`TaskManager.keyedStateSnapshot`（:149-159）改 mailbox 交接——构造 `Mail.control`（Mail :54 工厂）投递 `task.invokable.getMailboxExecutor().getMailbox().put(...)`（TaskMailbox.put :54 既有公共通道，R1 F-6 订正定案：真实裁定面=载荷/结果回传/超时，非访问缝）；mail 闭包在**任务线程**执行 `invokable.keyedStateSnapshot()`（:613-638 既有方法零改动——恢复「快照与写在同一线程序」的 checkpoint 路径同款线程归属前提，既有行为登记 (j) 的前提复用），结果经 `CompletableFuture<StateSnapshot>` 回传；HTTP 线程有界等待。

**裁定面细则**：
1. **Mail 载荷**：`Mail.control(() -> { try { future.complete(invokable.keyedStateSnapshot()); } catch (Exception e) { future.completeExceptionally(e); } }, "queryable-state-snapshot-" + taskKey)`——CONTROL 优先级在任务线程安全点边界（MIDDLE/SINK 主循环顶部 / SOURCE collect 发射点）先于数据面 mail 消费。
2. **结果回传**：JDK CompletableFuture，零新设施；任务线程完成/异常完成（异常包装 typed）。
3. **超时语义**：有界等待缺省 **5000ms**，配置键 `nop.stream.ops.state-query-timeout-ms`（≤0 装配期 fail-fast，对齐裁定②/⑥参数纪律）；超时 → typed `ERR_STREAM_STATE_QUERY_TIMEOUT`（**新增错误码**落 `NopStreamErrors` 手写保留文件——非生成物，8.16 增补先例）。
4. **任务卡死面**：任务线程长 GC/用户函数阻塞 → mail 排队等待 → HTTP 有界等待后 typed 503——从「立即返回可能错误数据」变为「有界等待后 typed 拒绝」（owner doc 语义变化登记 E5）；mailbox closed（任务已停）时 `TaskMailbox.put` 静默丢弃（:54 javadoc 声明的协作语义）→ future 永不完成 → 超时兜底。前置稳态过滤：`task.isFinished()`（RunningTask :481 引用形态）为真 → 直接 typed 拒绝（复用 TASK_NOT_FOUND 404 分派，与 handler 现状语义一致）——check-then-put 间隙竞态由超时兜底（保守双保险，不为此引入第二同步点）。
5. **handler 分派扩展**：`StreamOpsHttpServer` state handler（:246-266）按异常码分派：TASK_NOT_FOUND→404（零回退）、QUERY_TIMEOUT→503、其他→500（现状）。
6. **MemoryStateSerDe 防护余量声明**：交接后 `snapshotState` 全部在任务线程执行（checkpoint 路径 :558-561 同款），SerDe 两层无锁迭代（:143-147/:328-341）的线程安全前提（与写同线程序）恢复——SerDe/MemoryKeyedStateBackend/MemoryValueState 三文件**零 diff**（交接层单点收敛）；CHM 化（备选②）不采故无「双源防护」残留。
7. **rocksdb 受害侧钉（1.2-C10-006 同 diff 义务）[设计评审 Blocker-2 定形=采受害侧修复]**：`TtlContext.timestamps`（core `TtlContext.java:57`，**live 亲读已是裸 HashMap**——put :114/:142 × sweep 迭代 :182 并发即真 CME）**HashMap→ConcurrentHashMap 单点修复**（core impl 叶单文件 diff——审计原文「双处改动（core 派发侧 + rocksdb 受害侧）」+「受害侧 CHM 化」的预期形态兑现；**工单 9 伴生面登记**，PA 表站点 +1 文件）；`TestRocksDBKeyedStateBackendCrossThreadOps` 落 nop-stream-rocksdb src/test——线程 A 持续 `setCurrentKey+put` × 线程 B 直调 `snapshotState`+`cleanupExpiredEntries`；断言无 CME/无 native 异常/快照键集 ⊆ 已写键集——pre-fix 裸容器下并发迭代=**确定性红**，post-fix CHM 弱一致迭代安全=绿（审计原形先红后绿兑现）；mailbox 编组断言**不落 rocksdb 模块**（core/rocksdb 不依赖 runtime，不可实施——runtime 侧由红⑤ 承载）；该钉保护 rocksdb 侧跨线程纪律（TTL sidecar/迭代面防重构回退为裸容器——live 已是裸容器，本修复即其防回退钉本体）；cra-index 1.2 侧收敛注记归 plan Phase 4。
8. **端点级红⑤ 落点拆分（R1 F-5 定案）**：端点级驱动器（HTTP GET 不 500）落 nop-stream-runtime src/test（端点与 TaskManager 为 runtime 类，core 不可反向引用；参照 TestKeyedStateQueryE2E 包位）；core 侧可选保留 StreamTaskInvokable/MemoryKeyedStateBackend 编组断言——裁定 F 显式记录拆分。

**被否决的备选**：
- **备选② CHM 只读视图**：每 put 包装写放大 + CHM 弱一致迭代=跨 state 撕裂保留——「返回从未存在过的状态组合」缺陷半留。
- **备选③ 忙标记+文档降级**：把隐性缺陷显式化但不修复——监控轮询常态场景查询可用性归零。
- **synchronized(backend) 锁化**：写点（MemoryValueState :25）不经任何 backend 锁，引入新锁=全写点改造，复杂度无对价。

**零触碰边界**：`keyedStateSnapshot()` :613-638 本体零改动；TaskMailbox/MailboxExecutor/Mail 设施零改动（使用面）；StreamOpsHttpServer handleSubmit 面（R4-02 域）零触碰；`TestKeyedStateQueryE2E` 既有 200/503/404 断言零改写（超时→503 为新增分支用例）；`TestKeyedStateSnapshotAccessor` 零触碰。

### D.3 W-11：LOCAL 取消路径 cancel 遍历

**决策**：`shutdownLocalExecution`（:665-673）改为四步序——① **cancel 遍历先行**：对全部 SubtaskTask 复用 `SupervisionLoop.cancelTaskWithMailbox` 配方（SupervisionLoop :711-718：`invokable.getMailboxExecutor().signalCancel()` + `task.cancel()` 中断）；② `executor.shutdownNow()`（中断未启动任务兜底）；③ `plan.closeBufferPool()`（唤醒阻塞生产者，:667 既有）；④ registry 反注册（:668-672 既有）。**遍历载体 = TaskExecutor 新增公共方法 `cancelSubtasks()`**（封装遍历 `submittedSubtaskTasks` values 快照 + 逐个 cancel + 有界终态等待），StreamExecutionEnvironment 调用之。**超时预算**：cancel 后有界等待缺省 **10000ms**（配置键 `nop.stream.local.cancel-await-timeout-ms`，**0=禁用等待、负值=装配期 fail-fast[设计评审 Minor-5 对齐裁定② 原文参数纪律]**）；预算内未终态 → WARN（列出未终态任务名）+ 继续 shutdownNow 兜底（不无限等、不抛）。

**理由（shutdownNow 次序论证）**：cancel 先行使协作取消信号（mailbox flag + wake mail）先于中断到达——消费者任务在下一安全点（50ms 轮询粒度）观察 cancel flag 走 CANCELING→CANCELED 终态（SubtaskTask :132-141 状态机收口），不依赖用户代码的中断响应点；shutdownNow 后行兜底中断未启动/不响应 mailbox 的任务。与裁定② 守卫 b 的组合行为（红线 1）：守卫 b（:613-618 超时→abort: shutdownNow + typed 异常）管「正常完成等待」、本单 finally 管「宿主中断取消后线程收尾」——两路径互斥触发且各级动作幂等（`SubtaskTask.cancel` 状态机 :148 对 CANCELING/终态返回 false、shutdownNow isShutdown CAS 幂等），守卫 b 超时路径再入 finally 的 cancel 遍历零冲突。中断取消路径（红⑥ 驱动）：宿主线程在 awaitCompletion 阻塞中被中断 → InterruptedException 传播 → finally cancel 遍历 → 任务线程预算内达 CANCELED 终态退出——50ms 轮询无终态出口无限自旋面（C4-006 本体）闭合。

**被否决的备选**：
- **反射/包内遍历 tasks 列表**：破坏封装（TaskExecutor 与 StreamExecutionEnvironment 异包，包内可见不可达）。
- **仅 shutdown() 改 shutdownNow()（不 cancel）**：中断兜底存在但丢 mailbox 协作信号——SOURCE 阻塞在非中断响应点的用户代码时中断无效，自旋面半留。
- **cancel 遍历放 finally 之外（调用方）**：runLocal 单 finally（:632-634）单点，封装进 shutdownLocalExecution 即单点覆盖。

**零触碰边界**：awaitCompletion bounded 守卫（:609-621）与其配置语义零改动（裁定② 零回退）；TaskExecutor.shutdown/shutdownNow/awaitCompletion 语义零改动（cancelSubtasks 为新增方法）；LOCAL 正常完成路径零变化（finally 对 COMPLETED 任务 cancel 幂等 no-op）——`TestEventTimeWindowE2E` 等既有 LOCAL E2E 零回退锚；runtime 取消通道（SupervisionLoop/TestFailJobCancelsAssignedTasks）零触碰。

## 裁定 E 观测卫生域（W-13/W-14/W-15）

### E.1 W-13：周期失败日志三态抑制

**决策**：抑制载体 = **per-站点三态抑制器对象**（首条全量 → 窗口聚合 → 恢复 cleared），落 runtime 模块 `io.nop.stream.runtime.support`（新增 impl 叶子包——九点+补遗全部在 runtime，core 零消费面）。每站点一个抑制器实例字段（无跨线程共享状态——每订阅/每 TM/每 coordinator 一实例，裁定⑥ 分面线程模型零回退）；实现为 AtomicLong lastLoggedAt + 窗口计数 + 状态机局部字段，非平台设施（条件申报③零触发）。

**窗口参数 = 30s**（对齐审计建议）：50ms poll 节奏下窗口聚合比 600:1、5s 心跳 6:1——聚合摘要含窗口内次数 + 最后一次异常摘要（类名+message，无堆栈）+ 站点标识；恢复 cleared 由站点天然成功信号触发（poll 成功一次/续期成功/retryFailedCommits 空表/心跳 attempt 匹配恢复/选举成功），触发接线随 impl 逐点落（六点有直接成功信号，③⑥ 两点恢复信号=心跳 attempt 恢复/onBecomeLeader 或 check 成功）。

**覆盖集全清单（11 点——裁定项）**：

| # | 站点（live 锚） | 节奏 | 级别 | 处置 |
|---|---|---|---|---|
| ① | PollingJdbcMessageService :578 poll failed | 50ms/订阅 | ERROR | 抑制 |
| ⑤ | PollingJdbcMessageService :573 consumer onMessage failed | 50ms/订阅 | ERROR | 抑制 |
| 补遗 | PollingJdbcMessageService :231 msgqueue cleanup-fail（8.16 产物） | 周期清理 | ERROR | 抑制 |
| ② | TaskManager :455 heartbeat lease renewal failed | 5s | ERROR | 抑制 |
| ③ | JobCoordinator :1231 stale-attempt WARN | 5s×子任务 | WARN | 抑制 |
| ④ | CheckpointCoordinator :1596 retry still failing | checkpoint 节奏 | WARN | 抑制 |
| ⑥ | JdbcLeaderElector :141 check-fail | 轮询 | INFO | 抑制 + **INFO→WARN 升级**（C7-014 建议随批） |
| ⑦ | TaskCheckpointWiring :273-287 barrier trigger 失败 + JobCoordinator :1322 LOCAL 同构点 | checkpoint 间隔 | WARN | 抑制 |
| ⑧ | CheckpointCoordinator :1090 onCompletePersistFailure（R1 F-3 订正锚，亲读命中） | 触发节奏 | ERROR | 抑制 |
| ⑨ | RetentionCleaner :188 cleanup failed | 完成节奏 | WARN | 抑制 |
| 补遗-核定 | StreamMetricsReporter :119 report failed（K1 勘正——plan 勘误 3 零命中表述不成立） | interval 节奏 | WARN | 抑制 |
| 12 | JobCoordinator barrier fanout 满时丢弃 WARN[设计评审 Minor-8：W-10 B.3 依赖本抑制载体] | 丢弃事件 | WARN | 抑制（恢复信号=队列回落） |

排除面：`tryBecomeLeader` DEBUG（C7-014 明文排除维持）；StreamMetricsReporter :100 stop 一次性 WARN（非周期）；:119 文本已无堆栈（`e.toString()`），抑制仍适用（量级治理）。

**测试断言形态（log capture 归裁定）**：不引入 slf4j capture 基建——抑制器为对象级可测载体：站点把抑制器实例提为包内可见字段（注入缝），E2 泛在钉 = 抑制器三态行为单元钉（首条全量/窗口内计数静默/到期聚合摘要/cleared，确定性）+ 站点接线以布点核对与变异论证留痕（「日志条数有界」语义由「窗口内输出=1 条摘要 + suppressedCount 计数器」的对象级断言承载——禁伪绿：布点核对以 grep 留痕 + 钉内断言抑制器被调用）。**既有 log-capture 测试兼容性盘点[设计评审 Minor-6]**：`TestCheckpointCoordinatorPersistFailureLog`（runtime/checkpoint，ListAppender 断言 CC "Failed checkpoint" ERROR 恰一次）正打 ⑧ 站点——单失败场景下首条全量语义兼容维持绿；**聚合摘要不得复述站点原始消息全文**（多失败场景下复述原文会破「恰一次」断言——摘要只含类名+计数+末次摘要截断），随 impl 核对留痕。

**被否决的备选**：
- **键控限速**（AtomicLong lastLoggedAt 裸门控）：降频不给量级、无 cleared 叙事——三态为审计明文首选。
- **平台级日志抑制设施**：条件申报③（平台设施本体，禁选）；平台化候选维持 plan Non-Blocking Follow-up 登记。
- **logback ListAppender 日志捕获**：引入第三方 backend 断言面（零 mockito 纪律下的新基建形态），suppressor 计数器承载同语义零基建。

### E.2 W-14：releaseHolder 释放形态（owner doc 语义变化强制裁定）

**决策**：**采「条目移除」语义**（对齐 `EngineMetrics.releaseJob`/`removeFromRegistry` 先例 :101-119）：`releaseHolder`（:133-139）改为——① holder CAS 置空保留（值面释放语义零回退，防弱引用陷阱）；② 对本实例注册的全部 meter id 逐一 `registry.remove(id)`：counters×6（recordsIn/recordsOut/recordsConsumed/recordsEmitted/idleNanos/elapsedNanos）+ timers×2（processingTime/emitTime）+ gauges×5（busy/backpressured/idle 三 ratio :90-115 + watermark/channel lag :171-178——后二者以 `watermarkGaugesRegistered` 标志判定是否已注册）；③ `TASK_HOLDERS.remove(key, holder)` **键值双参** + **重装守卫[设计评审 Major-1：generation-safe]**——live `computeIfAbsent`（:86-89）对同键重建**复用同一 AtomicReference**（不换对象），remove(key, ref) 的值恒等比对无法区分世代：交错 T2 重建（拿旧 ref、set(T2)、注册同 id meter）→ T1 release 先 remove map 条目 → T2 日后 release 时 `get(key)==null` 跳过 CAS → ref 永挂 T2 → 同 id gauge 永久读冻结值（WI16 holder 模式要防的冻结缺陷在释放序列下重开）。修正序列：CAS(this→null) 成功后 `remove(key, ref)`，随后 `if (ref.get() != null) TASK_HOLDERS.putIfAbsent(key, ref)`（重建抢先即重装回）——T2 日后 release 正常走条目 CAS。移除期间抓取线程的过渡读（meter 已 find 到、值面已 null → NaN）为短暂过渡态，单调收敛，可接受。红⑧ 钉增补 release×rebuild hammer 交错断言。

**owner doc「任务结束后 NaN」契约 → 「任务结束后条目移除（抓取序列消失）」强制裁定**：取舍=移除对齐族先例——EngineMetrics/TaskNodeMetrics/ChannelQueueGauges（D2c）全部为移除/归零形态，NaN 恒序列是该族唯一例外且审计误报排除已否定豁免方向（既有行为登记 (i)：owner doc 无 task meter 面保留声明，C7-007 立案以修复为准）。Prometheus 序列消失 vs 恒 NaN 的下游影响（依赖 NaN 哨兵的看板/告警需迁移）登记 owner doc 同步义务——**E5 硬性项**（`docs-for-ai/03-modules/nop-stream.md` NaN 契约行 :71/:76 逐行改写[设计评审 Minor-4 行号订正——:75/:77 不含该契约]，归 plan Phase 3）。单 jobId 复用场景由构造自然重建（computeIfAbsent + holder.set 既有 :86-89 语义零改动，meter 经 registry.counter 重建）。

**被否决的备选**：
- **保留 NaN 声明边界豁免**：审计误报排除已否定（登记 (i)）；基数爆炸本体（jobId churn 每作业净增 ~11 meter 永驻）不闭合。
- **仅 TASK_HOLDERS.remove 不 registry.remove**：meter id 仍驻 registry（gauge 弱引用置 null 后读 NaN）——基数爆炸半留。
- **仅 registry.remove 不 TASK_HOLDERS.remove**：AtomicReference + 旧实例强引用驻留——条目泄漏。

**零触碰边界**：构造器注册集与 gauge lambda 零改动；bindWatermarkGauges 重绑语义零改动；`TestMicrometerStreamTaskMetrics` 零触碰（注册/计数断言维持——release 面其零覆盖=C10-023 本体，红⑧ 补钉）；C1-009（DbBacklogGauges，P3）零触碰防混。

### E.3 W-15：算子 counter 稳定键 + close 移除

**决策**：
1. **键形**：三算子 counter 标签改稳定键 `(jobId|vertexId|subtask)`——EquiJoin（:183-185）/OverWindow（:129-131）的 `"operator"` 标签值从 `getSimpleName() + "@" + identityHashCode` 改 `jobId|vertexId|subtaskIndex`；WindowOperator（:630-635）的 location 键形（`jobId.pipeline.vertex`+taskIndex，既有 W-M1 路径）对齐同族语义保留，无 location 回退从 `"window@" + identityHashCode`（:586-588）改 `vertexId|subtaskIndex`（**回退身份经新增注入承载[设计评审 Minor-2：`setSubtaskIdentity` 仅存在于 SourceReaderOperator.java:135，WindowOperator/EquiJoin/OverWindow 无此设施]**——`copyForSubtask(int)` 变体注入 subtaskIndex，缺省 0/1）。**identityHashCode 形态废除**。location 承载：EquiJoin/OverWindow 经 `copyForSubtask(TaskLocation)` 变体注入 location 字段（TwoPhaseCommitSinkFunction :151-153 同款变体先例；无 location 部署路径=隔离单测，回退键承载）。
2. **同键覆盖语义 = 幂等共享**：Micrometer `registry.counter(name, tags)` 对同 id 幂等返回既有 meter——同键多实例（重部署窗口内新旧实例交叠）共享同一 counter（计数合并）；旧实例 close 后不再计数，交叠窗口计数短暂合并属重启语义的一部分。
3. **release 载体 = 算子 close() 内自行 `registry.remove`**：三算子各自在实例字段登记本次注册的 Meter.Id，close()（WindowOperator :820-828 现仅 super.close——补 remove；EquiJoin/OverWindow close 同补）逐一移除。任务异常路径覆盖：`SubtaskTask.run` finally `closeOperatorChains()`（:141-145）恒执行——close 移除点必达（LOCAL 路径）；**runtime 路径残余登记[设计评审 Minor-3]**：per-role try/finally `closeChainAndGate`（invokeSink :1003-1010/invokeMiddle :1063）覆盖正常路径，`invokeSource` 的 `operatorChain.open()`（:828）先于 try——open 中途失败窗口内已 open 算子的 meter close 不达；稳定键下重建幂等共享，泄漏有界，接受（watch-only）。**与 W-14 的共用关系（同 diff 协同防混登记）**：两工单共用 `registry.remove` 释放通道与红⑧/泛在钉协同形态，但移除点各自独立（W-14=releaseHolder 点 :133-139、W-15=算子 close 点）——同文件不同类不同缺陷，diff 分组登记（plan 防混要求）；`StreamTaskInvokable` finally :820-828 零改动。

**与 plan 表述的偏离登记**：plan W-15 修复方向写「任务 close（StreamTaskInvokable finally :824 已调用 releaseHolder 的同一位置）对本次注册 meter 逐一 registry.remove」——live 亲读裁定移除点改挂算子 close()（生命周期归属内聚：注册在 open、移除在 close；:824 finally 遍历算子需跨层访问算子内部注册面，封装破坏），同 diff 时序与验收形态不变。plan 侧登记以本节为准。

**被否决的备选**：
- **:824 finally 遍历移除**（plan 原表述）：见偏离登记——封装破坏；两移除点独立后各司其职。
- **保留 identityHashCode 仅加移除**：同键去重失败（基数爆炸本体）未修。
- **(vertexId,subtask) 无 jobId 短键**：跨 job 同 vertex id 计数合并（观测语义漂移）——对齐 W-14 holderKey 含 jobId。

**零触碰边界**：`numLateRecordsDropped` 指标名与 CepOperator 共享面零改动（owner doc 该行的进程级聚合注记随键形更新归 E5）；`JOIN_OUTPUTS`/`OVER_OUTPUTS` 指标名零改动（标签值变更的 Grafana 面影响登记 E5）；W-M1 注释随 diff 扩展覆盖面声明（既有行为登记 (k)）。

## 条件申报登记（禁静默扩权——预设全部不触发）

①`*-api` 契约叶 → 预设不触发（15 工单 import 面亲读：`io.nop.api.core` 仅 CoreMetrics/NopException/`@Internal` 使用面，零 diff）。②orm/DB 承载 → 预设不触发（三模块 `model/*.orm.xml` find 零命中亲证；JdbcClusterRegistry/CheckpointCoordinator JDBC 面用 SQL API 零触）。③平台设施本体（nop-kernel/nop-core/nop-commons/nop-api-core，含 micrometer 契约本体）→ 预设不触发（W-13 抑制器落 runtime 模块内；W-14/W-15 的 registry.remove 为 micrometer 使用面 API；Mailbox/Mail 使用面零改；StreamMetricsRegistries/TaskMailbox 模块内设施改造限 impl 叶）。④`_` 生成物或 pom 结构 → 预设不触发（`_vfs/nop/stream/_module` 零触碰；新测试落既有测试树、新错误码落 `NopStreamErrors` 手写保留文件[非生成物]、W-13 抑制器为模块内新类非新模块）。任一触发 → 按 AGENTS.md 四件证据链（plan 追加申报 + 本文档专节修订 + draft review 记录追加 + 回归测试）重新申报后方可实施。

## 批外新发现登记

无。核证范围 = 15 站点文件 + W-13 并入面 3 文件（RetentionCleaner/TaskCheckpointWiring/JdbcLeaderElector）+ StreamMetricsReporter + 对照先例 6 文件全部亲读；「plan 输入勘正」K1/K2 均为本批工单域内的覆盖集并入与站点补充，非批外缺陷。

## 批域边界

**与 8.42+（R4-02）边界**：R4-02 成员（C5-004/C5-005/C5-006/C5-012/C8-005/C8-009/FAM-2）与本批 15 站点零文件交集。同文件跨批面零触碰禁顺带：StreamOpsHttpServer handleSubmit（C5-004 面）——W-09 只触 state handler 面（:246-266）；TaskManager duplicate 分支上报——**W-02 修复在重复槽位分支引入的 typed 上报 = 红① 绿侧语义本体（W-02 自身验收面）**，不构成对 C8-009 的顺带修复；C8-009 的其余面（两处 submit 拒绝的许可/登记清理 zombie 面）与 `TestTaskManager.testDuplicateAssignmentIgnored` 的「不挤位」断言面零触碰；C8-005 poll 失败 skip/cursor 语义面（:560-570 段语句）——W-13 抑制只动日志量不动 cursor/确认语义，同方法不同语句面防混。

**R-G 边界**：本批无 owner doc 缺口声明面（nop-stream.md 存在，E5 为随批更新非缺口登记）；C10 会红/假保护横向治理路线归 R-G 登记轨——本批只做 C10 八条+1.2-C10-006 的验收前置交付（裁定 F）。

**同号防混**：本批 W-01~W-15 为 plan 2534 自建工单号（cra-index 以「工单号+finding ID」双键）；1.1-C3-009（本批 W-09，queryable-state 快照）≠ 1.3-C3-009（plan 2512 已修，REPLACE 清零）；1.1-C4-006（本批 W-11）≠ 1.4-C4-006（R4-06 域）；1.1-C7-007（本批 W-14）≠ 异单元同名号；1.1-C10-010~023 ≠ 1.2/1.3 单元 C10 同号条目（1.2-C10-006 为跨单元锚定特例）。

## 裁定 F 测试缝总表（红①–红⑧驱动器 + E2 泛在钉×7 + 零回退钉全列 + C10 映射）

### 红绿判据纪律（先红后绿，E2）

全批测试零 mockito（nop-stream-core/runtime pom 亲读零 mockito）——观测一律 JDK 并发原语 + latch + 手工桩 + 线程枚举。**orTimeout 假绿陷阱纪律**：future 完成性断言只允许同步 `get(timeout)`，捕获 TimeoutException 即判红；禁用 `orTimeout`/`completeOnTimeout`。全部闩带上界（≤30s）且收尾必放闸，防测试自身挂死。红绿快照落 `_tmp/` 总册 + 逐 trial 日志。

### 产品侧测试缝（全批共 6 处）

| 缝 | 形态 | 覆盖钉 |
|---|---|---|
| S1 | CheckpointCoordinator commitFanout 队列容量包内可见注入缝（对齐 TaskManager `commitQueueCapacity` 构造参数形态 :219） | 红③ |
| S2 | TaskExecutor.cancelSubtasks 公共方法（裁定 D.3 遍历载体——观测经线程枚举，方法即缝） | 红⑥ |
| S3 | W-09 交接缝 = 配置键 `nop.stream.ops.state-query-timeout-ms` + `task.isFinished()` 前检——端点级驱动器经公共 API 直测无缝（F-5 落点拆分：runtime 端点级主驱动 + core 侧可选编组断言） | 红⑤ |
| S4 | W-13 抑制器站点字段包内可见注入缝 | E2 泛在钉（W-13） |
| S5 | TASK_HOLDERS/myHolderKey 断言（测试落 `io.nop.stream.core.metrics` 同包）——**两成员 private→包内可见（Minor-1 登记为产品 diff）** | 红⑧ |
| S6 | IStreamTaskRpcService 手工桩（挂死 send latch——TestConcurrencyWindows2326 stub 形态） | E2 泛在钉（W-10） |

### 红①–红⑧ 驱动器总表

| 钉 | 成员（C10） | 驱动器形态 | pre-fix 红判据 |
|---|---|---|---|
| 红① | W-02（C10-010） | 复用 TestConcurrencyWindows2326 夹具（TaskManager+Recording stubs+TestAwait）。**主驱动[设计评审 Blocker-1 重定形]=变体 b（同代重复→拒绝+typed 上报）**：`updateFencingToken(N)` → `receiveAssignment(gen-N)`×2 同 key → pre-fix :566-570 静默忽略零上报=**确定性红**；post-fix compute 等值分支拒绝+`reportAssignmentFailure`（经 `setCoordinatorRpcService` :1094 注入 Recording 桩断言到达）+ incumbent 不受影响=绿。**变体 c（跨代混装）保持确定性红**：装 gen-N 槽位 → 升 fencing N+1（removeIf 槽位清空）→ 重投 gen-N+1（新槽入位）→ `installInvokable(..., epoch=N)` → pre-fix 混装 set 成功=红、post-fix 拒绝装入=绿。**置换分支降级为结构钉**（仿 CC-02 决策函数钉：incumbent.epoch<candidate.epoch → 置换语义断言）+ 登记概率压测候选（跨代僵尸态仅 in-flight 竞速可达——`updateFencingToken` removeIf :1075-1084 会取消并移除全部旧代条目，三步时序命中不了重复槽位分支；CC-02 先例 javadoc TestConcurrencyWindows2326 :70-72 明文「full race needs a latch seam」同源约束）。**[评审 R1 F-1 原形态废止]**：plan 草案三步时序（装 N→升 N+1→投 N+1）被 removeIf 拦截=伪绿，同入口检查拦截 F-1 原形态同理 | 主驱动 pre-fix :566-570 静默忽略零上报——红（确定性）；变体 c pre-fix 混装 set 成功——红（确定性）；置换结构钉为绿锚 |
| 红② | W-03（C10-011） | 两线程对**同一** epochId 并发 finishCommit，同点 latch 交叠（commit 前 latch await 互放——概率窗口确定性放大）；TestSink commit 记录断言 `committedEpochs 中该 epoch 至多出现一次` + 工作线程异常 fail-fast 上抛。**同 diff 改写** `TestTwoPhaseCommitSinkFunction` :176-208（互异 epochId `5*(i+1)` → 同 epochId；catch Throwable 置标志 → fail-fast 上抛）；9 个单线程 restore 测试零改写 | pre-fix 同 epoch 双 commit 可命中——红（概率窗口确定性放大） |
| 红③ | W-06（C10-012） | 仿 TestTaskManagerCommitQueueBound：S1 容量调小（如 2）+ 首 commit 阻塞（participant latch）+ 灌满队列 + 触发下一轮 fanout → 断言**溢出计数递增** + **monitor 探针不被阻塞**（提交线程在阈值内完成 dispatch 返回——探针范式同 TestCommitFanoutOffMonitor2326）+ 解阻后**补投任务最终执行**（不丢断言：被补投 epoch 的 notify 效果可见） | pre-fix CallerRuns 内联 monitor 探针超时——红（确定性：队列灌满即回退） |
| 红④ | W-08（C10-013） | 慢快照 operator（saveState latch 阻塞）+ 另一线程并发 ACK，断言 ACK 在快照窗口内不被阻塞；**前置构造 = mailbox==null 回退路径**（构造未接 mailboxExecutor 的 SourceReaderOperator 挂入 TRK operators——:533-542 直通分支可达，确定性构造）；monitor 探针范式 | pre-fix 快照持 TRK 监视器 ACK 阻塞至快照完成——红（确定性） |
| 红⑤ | W-09（C10-014） | **落 nop-stream-runtime src/test（F-5 定案）**：keyed 任务持续写（几百 key 循环）+ 另一线程高频 GET state 端点，断言查询要么 200 且 key 集合自洽、要么 typed 拒绝（503），**绝不 500**；rocksdb 受害侧钉（1.2-C10-006 形态）随同 diff 落 nop-stream-rocksdb（裁定 D.2 第 7 条形态） | pre-fix CME 逸出 500——红（概率窗口轮询放大） |
| 红⑥ | W-11（C10-015） | LOCAL execute 阻塞消费者任务，主线程 interrupt 后断言任务线程在超时预算（S2 预算/10000ms 上界）内退出——`Thread.getAllStackTraces`/threadExists 钩子范式（TestTaskManagerDaemon 参照）；预算经 S2 或配置键注入缩短 | pre-fix 50ms 轮询无终态出口线程滞留——红（确定性：预算内必不退出） |
| 红⑦ | W-12（C10-016） | **窗口内构造[设计评审 Major-2 重定形]**：invokable 挂 latch（阻塞于任务执行）→ 线程 S 调 `tm.stop()`（泊在 awaitTermination(5s) :371-375——shutdownNow→clear 窗口被拉长到秒级）→ 主线程在窗口内调 notifyCheckpointComplete → 断言 **TestSink commit 记录到达**（post-fix shutdown 窗口 inline；pre-fix :315 isShutdown 条件静默丢=红）；**删除「或 commitCallerRun 计数递增」备选断言**（:311 计数先于 isShutdown 判断——pre-fix 也递增，非判别性）；stop 后静默返回形态（runningTasks 已清空）不可用作驱动——@RepeatedTest 压力形态保留为第二载体 | pre-fix isShutdown 分支静默丢（窗口内通知）——红（确定性） |
| 红⑧ | W-14（C10-023） | create→release 同一 holder key，断言 `registry.find(...).tags(...).meter()` 为 null（全注册 id 逐一断言）且 TASK_HOLDERS 不再持有（S5 包内断言）——对照 TestChannelQueueGauges awaitGaugeValue 范式 | pre-fix meter 永驻——红（确定性） |

### E2 泛在钉×7（无 C10 号 7 工单）

| 工单 | 钉形态 | 分类 |
|---|---|---|
| W-01 | 并发 of() hammer（同/异 Class 双线 + latch 交叠）绿锚 + **变异/回退论证**：回退为 plain HashMap+get→put 后 hammer 无稳定红（JMM 无 HB 边即未定义行为）——结构论证留痕禁伪绿；补 computeIfAbsent 原子性结构钉（同 key 并发单实例断言——hammer 下同 key 双实例不可能即确定性断言） | 理论面（变异/回退论证分支）+ 结构钉 |
| W-04 | 快照读一致性钉：并发写 + 抓取 hammer，断言读到的 long 恒为合法写入值之一（单元素撕裂不可能即确定性断言）+ volatile 修饰结构断言；回退 plain long[] 的变异论证留痕 | 理论面（同分支）+ 结构钉 |
| W-05 | **编译期结构钉**（反射断言 `Modifier.isVolatile(reader 字段)`——确定性）+ 回调单线程时序论证留痕（preOpen 主时序零回退声明） | 编译期构造 + 论证 |
| W-07 | 读写 hammer（assignTask × getTaskAssignment/getAttemptHistory 并发）绿锚 + 锁结构论证留痕（读点入锁 grep 级核对）；概率红候选登记 | 概率红候选 + 结构论证 |
| W-10 | S6 传输桩 send 挂死（latch）+ 灌队列（100+1 次 barrier）→ 断言丢弃计数递增 + 后续 barrier 不入队（队列深度有界）+ 触发线程不阻塞——**可确定性构造红** | 确定性红 |
| W-13 | S4 注入替身：持续失败注入 → 断言窗口内全量输出=1 条摘要 + suppressedCount 有界 + 恢复后 cleared 一条（对象级确定性）；站点接线布点 grep 留痕 | 确定性（对象级） |
| W-15 | 同键 hammer（重部署模拟：同 location 重建实例 → counter 幂等共享断言）+ close 后 `registry.find` null 断言——**确定性红候选**（与红⑧ 协同形态） | 确定性红候选 |

### 零回退钉全列

- **基线签名（勘误 4，2026-11-06 亲跑）**：分解通道 `./mvnw -o test -pl nop-stream` = 已执行 4392 tests / 1F / 0E / 18S（core 1986 0F0E 1S / cep 386 / flow 315 / connector-jdbc 66 / debezium 79 0F0E 6S / batch 62 / kafka 26 / rocksdb 142 / connector 106 / runtime 1224 1F 0E 11S；format-avro/sql/fraud-example 反应器跳过——Phase 3 补齐覆盖）。1F = TestStreamConnectorRegistryE2E 负载 flake（solo 复跑 2 tests 0F0E BUILD SUCCESS 归因在案）——**F1 处置规则：并发通道复现 → solo 三联绿归因维持，禁静默重跑或跳过**。零回退实证 = 各模块 tests/F/E/S 数量与归属签名比对（18S 归属逐一核对）+ 新钉增量。
- **裁定⑪ 七钉**（[stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md) §3.11）：TestCachingTableLookup / TestJobCoordinatorPerTaskFailure / TestInMemoryClusterRegistryAttemptHistory（=W-07 零回退锚）/ TestResultPartitionDeadlock / TestMultiJvmExactlyOnceRecovery / TestLaunchMainScopeWiring / TestJobCoordinatorRecoveryConcurrency（=W-10 同文件零回退锚）——钉内断言全批 diff 零触碰。
- **2510 新钉二**：TestStandbyDegradeCancelsInFlightPersist（裁定⑤ 层4，W-06/W-12 红线 2）/ TestTaskAssignmentTerminalCleanup（裁定⑦，W-07 红线 4）。
- **治理 pin**：CC-01（TestCommitFanoutOffMonitor2326——W-06 零回退锚）/ CC-02（TestConcurrencyWindows2326.staleGenerationCandidateDoesNotDisplaceNewerIncumbent——W-02 零回退锚）/ R5-CC-10（TestTaskManagerCommitQueueBound——W-12 零回退锚）/ CC-04（TestConcurrencyWindows2326 lastTaskExit pin）/ CC-17（TestFencingTokenRejection + terminal-report 双保险面）。
- **既有锚 14 文件**：TestFencingTokenRejection、TestCheckpointBarrierTrackerConcurrency（W-08 零回退锚）、TestSourcePullBarrierInjection（W-08 零回退锚）、TestMicrometerStreamTaskMetrics（W-14 对照）、TestKeyedStateQueryE2E（W-09 零回退锚，200/503/404 断言零改写）、TestKeyedStateSnapshotAccessor、TestInMemoryClusterRegistryAttemptHistory、TestChannelQueueGauges（W-14 awaitGaugeValue 对照范式）、TestTwoPhaseCommitSinkFunction 单线程九测（零改写）、TestTaskManagerDaemon（红⑥ 驱动参照）、TestFailJobCancelsAssignedTasks（W-11 对照面）、TestEventTimeWindowE2E（W-11 既有 LOCAL E2E 零回退锚）、TestConcurrencyWindows2326（红① 夹具复用载体）、TestTaskManager（testDuplicateAssignmentIgnored 兼容性核证在案）。
- **勘误 4 五已知面集④**：nop-stream-core 恒红已解除（core 1986 全绿亲跑确认）——按基线行为处置留痕。

### C10 八条 + 1.2-C10-006 同 diff 映射

| C10 条目 | 红 | 工单 | 同 diff 义务 |
|---|---|---|---|
| C10-010 | 红① | W-02 | 三步时序驱动器 + 变体 b/c |
| C10-011 | 红② | W-03 | superficially-pass 测试同 diff 改写（互异 epoch→同 epoch+fail-fast） |
| C10-012 | 红③ | W-06 | 容量 seam+灌满+计数/探针 |
| C10-013 | 红④ | W-08 | 慢快照+并发 ACK 探针（回退路径构造） |
| C10-014 | 红⑤ | W-09 | 端点级驱动器落 runtime（F-5 拆分定案）+ rocksdb 受害侧钉 |
| C10-015 | 红⑥ | W-11 | interrupt 后线程预算内退出 |
| C10-016 | 红⑦ | W-12 | stop 后通知可观测 + @RepeatedTest |
| C10-023 | 红⑧ | W-14 | create→release find null（C1-009 关联面零触碰防混） |
| 1.2-C10-006 | （红⑤ 同 diff） | W-09 | TestRocksDBKeyedStateBackendCrossThreadOps 落 nop-stream-rocksdb 测试树（裁定 D.2 第 7 条形态落档）——跨模块同 diff 义务，cra-index 1.2 侧收敛注记归 plan Phase 4 |

八缺陷面与各自本体同 diff 落地，禁先修本体后补测的拆分交付（E2）；测试类注释显式归属 CRA 8.41 R4-01（同号异站防混）。

## 评审记录

- **设计评审 R1**（2026-11-06，独立评审子 agent agent_c68c0029 fresh session 只读 @ `563c501566`，含想象性分析 + live 抽验 ≥40 处锚点 + 既有裁定①–⑪ 引用逐条对照）：**PASS-with-revisions——0 Blocker[修订后]…原始判定 Blocker ×2 / Major ×3 / Minor ×8，全部随轮落盘，无需 R2 即可进入 Phase 2**。核证通过面：K1–K4 勘正全部亲读命中（K1 推翻 plan 勘误 3 成立）、既有裁定引用忠实无越界、裁定 A/B/C/D/E 产品侧实质成立（含 C.2 溢出补投 drain 边界推演收敛——溢出非空蕴含队列曾满蕴含后续任务边界必达）、W-15 偏离 plan :824 本体成立、`TestTaskManager.testDuplicateAssignmentIgnored` 兼容性判断正确。**Blocker-1（红① 伪绿）**：三步时序被 `updateFencingToken` removeIf（:1075-1084）拦截——步骤②已把 gen-N 槽位清空，步骤③走正常接受路径到不了重复槽位分支（前置绿证据 `TestTaskManager.testUpdateFencingTokenCancelsOldTokens` :151-166 count==0 钉死）；主驱动改判变体 b（同代重复→拒绝+上报）+ 变体 c（跨代混装）+ 置换降级结构钉。**Blocker-2（rocksdb 钉绿形态不可达）**：TtlContext.timestamps live 已是裸 HashMap，runtime 侧修复不触 rocksdb——直调形态钉恒红；采受害侧 `TtlContext.timestamps` CHM 化入批（工单 9 伴生面，审计原形先红后绿兑现）。**Major-1**：W-14 release 序列 generation-safe 修正（remove(key,ref) 值恒等无法区分世代——computeIfAbsent 复用同一 ref，release×rebuild 交错致 gauge 永久冻结；修正=remove 后 ref.get()!=null 即 putIfAbsent 重装回）+ 红⑧ 增补 hammer 交错断言。**Major-2**：红⑦ 改窗口内构造（tm.stop 后调 notify 遍历空表静默返回永红；stop 停在 awaitTermination 拉长窗口内 notify+TestSink commit 记录断言；删除 pre-fix 非判别的计数备选）。**Major-3**：B.1.1 严格不等比较硬约束（selectDisplacedTask 等值=候选置换语义与本分支拒绝语义相悖，不直接调用仅复用形态）。**Minor-1~8**：S5 可见性放宽（产品 diff 登记）/setSubtaskIdentity 归属订正/runtime open 窗口残余登记/owner doc 行号 :71/:76/超时纪律对齐裁定② 原文/既有 ListAppender 测试兼容性盘点/C.3 #4 补溢出 deque/指标名定稿 `nop.stream.engine.barrierFanoutDrops.total`+E.1 覆盖集补第 12 行。修订全部随轮落盘（本节+对应裁定行+plan 回写归主 agent）。
