# stream-core 资源包络、指标边界与注册表生命周期并发契约（CRA 8.42 R4-02 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立 draft review 见评审记录节）
> Last Reviewed: 2026-11-07
> Source: plan 2535（[2535-cra-phase8.42-r4-02-stream-envelope.md](../../plans/2535-cra-phase8.42-r4-02-stream-envelope.md)，CRA Phase 8.42 R4-02 1.1 资源包络+FAM-2 束 G02+G03，7 工单全 P2）；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency/`（[merge-adjudication.md](../../audits/2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency/merge-adjudication.md) §一/§四/§五 + C5-memory-bounds.md + C8-backpressure.md + C1-shared-mutable-state.md + C7-long-running.md + C10-test-protection.md）；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.4 R4-02 + §一 E1–E6；范式先例 = 8.41 前驱设计文档；既有裁定权威 = [stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)（裁定①–⑪）与 [stream-core-concurrency-correctness-commit-path-and-observability-hygiene-concurrency-contract.md](stream-core-concurrency-correctness-commit-path-and-observability-hygiene-concurrency-contract.md)（8.41 裁定 A–F）——两份均**仅引用不重建**
> Owners: 裁定 A（runtime 提交/运维面域：W-01/W-03/W-06，其中 W-06=裁定 A.3 承载任务书「C8-009 zombie 清理」裁定项）、裁定 B（core 控制面与包络域：W-02/W-04）、裁定 C（数据面确认与 side-output 生命周期域：W-05/W-07）、裁定 D（测试缝总表：红①–红⑤+E2 泛在钉×2+零回退钉全列+C10 五条映射——对应任务书「裁定 E」）。裁定编号沿 plan 2535 Goals 分组；文件命名裁定：`stream-core-resource-envelope-metrics-boundary-and-registry-lifecycle-concurrency-contract.md`（取代 plan Goals 拟名 `...-message-confirmation-and-sideoutput-lifecycle-...`——「metrics-boundary」覆盖 W-06 taskDeployed 观测失真面、「registry-lifecycle」覆盖 W-07 主面；命名调整登记义务归 plan 侧）
> 行号锚 = live 基线 `336eadd084`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值——与 plan 起草基线 `91373f8dde` 之间唯一 commit 为 docs(plan) 提交，`git diff 91373f8dde..336eadd084 -- nop-stream` 零 diff（7 工单全部站点文件逐行亲读核对），后续漂移以 live 为准。与 plan 文本不一致的 live 核证事实集中登记于「plan 输入勘正」节，plan 侧回写归 plan 对齐纪律执行前置。

本文是 plan 2535 全部 7 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序论证）、被否决的备选、零触碰边界。三范式与溢出语义结论引用不复制——SSOT 在两份既有设计文档。

## 既有裁定引用核对（引用不重建——roadmap :184 Deps 列授权）

裁定①–⑪（[stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md) §3.1–§3.11）与 8.41 裁定 A–F（[stream-core-concurrency-correctness-commit-path-and-observability-hygiene-concurrency-contract.md](stream-core-concurrency-correctness-commit-path-and-observability-hygiene-concurrency-contract.md)）为本批可引用既有裁定，引用边界逐条确认如下；本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。

| 既有裁定 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| ① enumerator 三态锁纪律 | 无直接站点（LocalSourceCoordinator 零触碰） | 仅作「叶锁零嵌套 + 锁内禁未知代码/阻塞调用」纪律先例引用（裁定 A.3 清理时序与 B.1 去抖载体均不引入新锁） |
| ② LOCAL 双守卫（参数纪律） | W-01 限额键 / W-03 升级阈值 /（W-04 无新增运行时参数——声明形态） | **红线 1**：新配置键一律「缺省显式 + 负值装配期 typed fail-fast（0 值语义逐键定义——F-7b 措辞对齐裁定② 精确形态）」——`nop.stream.ops.http.max-body-bytes`（裁定 A.1）与 `nop.stream.checkpoint.commit-retry-escalation-threshold`（裁定 A.2）按此形态落地 |
| ③ CachingTableLookup 有界化 | 无站点交集（connector 域零触碰） | 仅作「满时丢弃计数限频 WARN」形态先例引用 |
| ④ 提交窗口全窗补偿 | 无站点交集（OpsJobManager.submit 零触碰） | 引用其「补偿自身失败 ERROR 显式可见不吞」纪律（裁定 A.3 清理失败不吞、A.2 升级出口失败不吞） |
| ⑤ standby 降级 + 层4 复核 | W-03 同文件（CheckpointCoordinator） | **红线 2**：abort 门控判定本体零改写——`failedCommitParticipants.containsKey` 门控两站点（:1223-1224/:1455-1456）与「条目存在期终端标记不删」语义零回退；裁定 A.2 的升级出口只经既有移除路径（:1876-1878）达成条目终局化，不新增门控旁路 |
| ⑥ poller 控制面/数据面分面 | W-05（PollingJdbcMessageService `pollExecutorFor` :568-570） | **红线 3**：分面线程模型零回退——裁定 C.1 的 topic 前缀判定与其同构（共享 `nop-stream.data.` 前缀事实），分面调度零 diff |
| ⑦ 账本终态清理接线 | W-07 终态锚同型先例引用（OpsJobManager stop C1-006 清理锚 :356-366 区） | **红线 4**：`terminalAt.put`（:354）与 C1-006 清理锚零触碰——裁定 C.2 采 per-execution 实例化后终态接线锚零 diff（结构化回收取代显式 remove 接线，见 C.2 生命周期裁定） |
| ⑧ msg_queue 保留 + 门控 | W-05 同文件（cleanup 位点/ChannelBacklogProbe 零触碰） | 引用其「清理失败显式 ERROR + 调度不断链」语义——W-05 不动清理链 |
| ⑨ 读路径 setLimit 分批 | W-05 同文件（:588-595 查询块） | **红线 5**：LIMIT 分批查询语句（:592-595）与 cursor 分批推进主路径零回退——裁定 C.1 只改失败行确认语义，cursor 正常推进、批量上界、bootstrap（:547-554）零 diff |
| ⑩ FAM-1 溢出语义 | 本批零 FAM-1 站点（ResultPartition/InMemoryMaterializationPoint 零触碰） | 溢出语义按引用处理不重开体；C9-007 零承接注记维持 |
| ⑪ 测试缝与零回退钉 | 全批测试面 | **红线 6**：七零回退钉（TestCachingTableLookup/TestJobCoordinatorPerTaskFailure/TestInMemoryClusterRegistryAttemptHistory/TestResultPartitionDeadlock/TestMultiJvmExactlyOnceRecovery/TestLaunchMainScopeWiring/TestJobCoordinatorRecoveryConcurrency）钉内断言全批 diff 零触碰；九测试缝形态惯例（monitor 探针/latch seam/手工桩/包内可见注入缝）为本批裁定 D 新钉的形态基准 |
| 8.41-A/B（core 原语/控制面域） | 无站点交集（BasicTypeInfo/InputGate/SourceReaderOperator/InMemoryClusterRegistry/JobCoordinator 零触碰） | 引用边界 = 「已修面零重开」——B.1 去抖载体对齐 B.1(B.1)「站点局部对象无跨线程共享状态」AtomicBoolean 形态先例 |
| 8.41-C（提交通知域统一设计） | W-03 同文件（commitFanoutExecutor 面） | **红线 7**：C.2 分窗口 handler + 溢出补投 deque + CommitFanoutTask drain 结构零触碰——裁定 A.2 的升级动作执行点在 fanout 任务体内（retryFailedCommits 调用点 :1146 之后段），不改任务包装/排空结构；TestCommitFanoutOffMonitor2326 pin 零触碰 |
| 8.41-D（快照与生命周期域） | W-01 同文件（StreamOpsHttpServer） | **红线 8**：W-09 state handler 面（handleKeyedStateQuery :243-269 区 + keyedStateSnapshot 交接链）零触碰——裁定 A.1 的 413 分支为 handleSubmit 方法体内新增分支，与 handler 分派（:262-268 三态形态）互不越界 |
| 8.41-E（观测卫生域） | W-05 同文件（抑制接线）+ W-03 同文件（retry WARN 站点） | **红线 9**：三态抑制器 12 站接线与恢复信号语义零回退——裁定 C.1 数据面升级动作**追加**在抑制记录之后不动三态结构；裁定 A.2 升级出口为一次性终局事件走单发日志点（不入抑制器）；PeriodicFailureSuppressor 类零 diff |
| 8.41-F（测试缝总表） | 红①–红⑧+E2 泛在钉×7 | 钉内断言全批 diff 零触碰；orTimeout 禁令/闩带 ≤30s/`_tmp/` 总册纪律由裁定 D 全盘沿用 |

**三范式/溢出语义引用面声明**：本批无 R02 三范式直接消费成员（W-04 裁定为声明形态，无读路径改动）——三范式/溢出语义按引用处理不重开体；SSOT = 裁定⑨⑩ 与 ledger-retention-policy.md。

## plan 输入勘正（live 亲读核证 @ `336eadd084`，裁定以本节为准）

| # | plan 2535 表述 | live 事实（file:line @ `336eadd084`） | 对裁定的影响 |
|---|---|---|---|
| K1 | （无表述） | plan 起草基线 `91373f8dde` → 本文基线 `336eadd084` 之间唯一 commit 为 docs(plan) 提交，`git diff 91373f8dde..336eadd084 -- nop-stream` **零 diff**（9 站点文件+2 读点文件亲读核对） | 全部行号锚零漂移；勘误 2 的 live 回读值继续有效 |
| K2 | W-03 区间锚「notify :1826-1841 / retry :1843-1894」 | notifyParticipantsFinishCommit 实际 **:1826-1838**（successMap put :1827、入账 :1835、catch 收于 :1837）；retryFailedCommits 实际 **:1840-1892**（空检查 :1841、移除路径 :1876-1878、防提前删注释 :1886-1888、原位替换 :1889、CLEARED 信号 :1881-1884） | 方法右界含下方法签名行/空检查行的 ±2 计入偏差；机制与站点集零漂移，裁定锚定以本文值为准 |
| K3 | F2 表述「threading 需触 ~16 构造点」的载体未指明 | `new ChainingOutput(` 在 main **仅 1 处**（StreamTaskInvokable:388；测试树 46 处）；「~16 构造点」实为 `new StreamTaskInvokable(` 构造链——main **13 处**（GraphExecutionPlan 6 处 :683-696 + RemoteGraphExecutionPlanBuilder 6 处 :410-416 + JobGraphGenerator:532，全 impl 叶） | 裁定 C.2 的波及面按「invokable 构造链 13 main 处 + ChainingOutput 1 处」落账；测试树 ChainingOutput 构造点 46 处登记为红⑤ 改造面 |
| K4 | W-07 两读点「job 身份当前不可达」（方向正确，补精确事实） | ① StreamTaskInvokable **零 jobId 字段**（taskMetrics 经 setTaskMetrics 后置注入且 LOCAL 路径零注入亲证）；② LOCAL jobId = `StorageJobIds.sanitizeJobId(jobName)` 在 **execute 期**产生（PartitionedPlanGenerator :68[F-6 微漂订正]）——装配期不可达；③ **关键新增事实：task-local 一级查找的生产零填充**——`registerSideOutputConsumer` main 全仓**零调用方**（仅 ChainingOutput:67/:510 定义与测试调用），生产 side-output 路由 **100% 走全局注册表 fallback**（ChainingOutput:117-123 / StreamTaskInvokable.sideOutputConsumerFor :1228-1233） | 裁定 C.2 据此否决复合键（读点身份传递链无自然载体）并采 per-execution 实例化；「SideOutputDataStream 持 env 引用」（DataStreamImpl.environment :74 亲证）为实例化注册的自然锚 |
| K5 | W-05「typed 失败通道复用 `WireDecodeFailureAware`/`RemoteInputChannel` 既有面（参照零改动）」 | **通道载体需要一处微增**：DB 后端数据面订阅方 = `DataPlaneMessageServiceAdapter.AdaptingConsumer`（:90/:110-152）——该 wrapper **只实现 IMessageConsumer，不实现 WireDecodeFailureAware**（aware 检查只对其 `inner`=EnvelopeConsumer 做 :134/:143）；`javaDeserialize` 失败发生在 PollingJdbcMessageService :599（行未达 adapter 消费者），adapter 的 wire 解码拦截点对 DB 后端的 payload 损坏**结构性不可达** | 裁定 C.1 通道载体 = `AdaptingConsumer` 增补 `implements WireDecodeFailureAware`（委托 inner-aware，非 aware 时维持既有 LOG.warn+discard fallback）——对 plan「参照零改动」表述的**显式偏离登记**（一处接口声明+一委托方法，文件其余零 diff）；偏离理由：不做此增补则 DB 路径 typed 升级无可达载体，唯一替代（向 poll 循环外抛）造成 poison 行 wedge 的无界静默缺口（比缺陷本体更重，见 C.1 备选否决） |
| K6 | 「TestStreamOpsHttpServer 无 413/限量断言（live grep 零命中亲证）」 | 复核成立（413/maxBody/Content-Length 三词 grep 零命中）；`NopStreamErrors` 最新码 = `ERR_STREAM_STATE_QUERY_TIMEOUT`（:910，8.41 先例）——新增码格式/落点参照在案；文件 916 行手写保留文件 | C10-017 缺口维持；裁定 A.1 新码 `ERR_STREAM_REQUEST_BODY_TOO_LARGE`、A.2 新码 `ERR_STREAM_COMMIT_RETRY_ESCALATED`、C.1 新码 `ERR_STREAM_DATA_PLANE_RECORD_FAILED` 均落该文件尾部（手写保留文件——条件申报④零触发） |
| K7 | W-04「预设② 提交期静态验算」 | **预设② 的两个可行性前提 live 亲读不成立**：① `estimatedItemSize` 在 plan 模型**零来源**——`buildEdgeConfigs`（DeploymentPlanGenerator :83-90）对全部 edge 一律 `EdgeConfig.defaultConfig()`，全仓无任何字节估算点；② `DeploymentPlan` 构造点全仓 **~20+**（main 3 + 测试 17+，含 8.41 钉文件 TestStandbyDegradeCancelsInFlightPersist 等）——字段删除形态波及钉文件代码行 | **裁定 B.2 偏离 plan 预设、采形态①（声明降级）**——偏离论证见 B.2 备选否决；本节登记为裁定级偏离（plan 侧以本文为准，沿 2534 W-15 偏离登记先例） |
| K8 | 勘误 2 各站点行号 | 逐点复核命中（TaskManager :731-742/:744-759/:768/:772-779/:991/:995-1002/:1026-1030 清理体/:1338；PollingJdbcMessageService :588-595/:596-626[行循环 :596-623]；CC :353/:356/:1223-1224/:1455-1456/:1827/:1835；StreamOpsHttpServer :127/:399/:405/:407-413/:424-427；Driver :44/:118-132/:123；Mailbox :40-41/:54-72 零漂移；SideOutputRegistry :32/:39-51/:54-62/:65-69/:72-75 零漂移；SideOutputDataStream :90；MemoryBudget 族 :18-47/:77/:59/:45/:79 零漂移） | 核证范围登记：9 站点文件 + 2 读点文件 + 接线锚 2 文件 + NopStreamErrors + PeriodicFailureSuppressor + WireDecodeFailureAware/RemoteInputChannel/DataPlaneMessageServiceAdapter + StreamOpsConfig + JobSubmissionSpec 亲读 |

## 裁定 A runtime 提交/运维面域（W-01/W-03/W-06）

### A.1 W-01：handleSubmit 请求体上界预检 + typed 413（C5-004，并入 C8-008）

**决策**：**双检形态**（Content-Length 预检 + 有界读取兜底）唯一分支，handleSubmit 方法体内单点闭合：

1. **配置键**：`nop.stream.ops.http.max-body-bytes`（StreamOpsConfig 新增 `KEY_MAX_BODY_BYTES`，解析链沿用 `props.apply` 既有形态），缺省 **1048576**（1MB）；**≤0 装配期 typed fail-fast**（红线 1 参数纪律，对齐裁定②/8.41 全部阈值先例）。
2. **读取序列**：`handleSubmit`（:399）在 `readAllBytes`（:405）前插入——① 读取 `Content-Length` 头：存在且为合法整数且 **> limit → 立即 413（零 body 读取）**；② 头缺失/非法/chunked → 有界读取 `exchange.getRequestBody().readNBytes(limit + 1)`，**读满 limit+1 字节 → 413**（读取即刻中止）；③ 未超限 → 既有 parse（:407-413）→ 校验 → submit → 201/409/400 分流（:415-427）零改写。
3. **typed 码与状态**：新增 `ERR_STREAM_REQUEST_BODY_TOO_LARGE`（K6），`sendError(exchange, 413, "SUBMIT_BODY_TOO_LARGE", ...)`——413 分派形态与既有 ALREADY_HOSTED→409（:424-427）结构对齐（错误码等值比较，非消息嗅探）。

**理由（有界化与时序论证）**：pre-fix 峰值 ≈3×body（byte[]→String→JSON 树三份，C5-004 审计口径）；post-fix 峰值 ≤ 2×(limit+1) 字节（一次有界 byte[] + 一次 String 转换）且解析树仅在合法 body 上构建——单请求内存上界从无界收敛为 ~2MB（缺省）常数。并发面：4 worker 共享堆（:127 fixed 4），双检把「合法调用方单请求打爆堆」面闭合——T1 攻击/误用方声明超大 CL → 零读取快速拒绝（worker 占用微秒级）；T2 chunked 慢滴流 → 读取至 limit+1 即断。残余面声明：慢速客户端仍可占住单 worker 至 limit+1 字节读完——worker 池有界（4）、单体读取量有界（~1MB）、运维面队头阻塞为低速占用非内存风险——登记为已接受（全端点 body 治理候选维持 plan Non-Blocking Follow-up 登记）。限额 sizing：JobSubmissionSpec 三字段（jobId/pipelineFactoryClass/params Map，runtime ops :26-28 亲证）——params 承载管道配置键值，1MB 覆盖合法 payload 约 3 个数量级富余（C5-004 审计论证采纳）；配置化允许上调防误杀（W-01 误杀风险登记的处置）。

**被否决的备选**：
- **仅 Content-Length 预检**：chunked/无 CL 请求结构性绕过（读取路径无守卫回退到无界）——审计明文点名的绕过面，红① 防线要求「chunked 路径不绕过」，不可采。
- **仅有界读取（无 CL 预检）**：合法声明超大 body 也须白读 limit 字节才能拒绝，且「零读取拒绝」负向断言（红① 主判据之一）失去构造载体；CL 预检是「读取前拒绝」语义的自然实现。
- **限额缺省 4MB / 64KB**：4MB 无 sizing 依据（三字段 spec 1MB 已富余）；64KB 对大 params 管道存在误杀面（W-01 误杀风险登记）。
- **常量硬限（无配置化）**：违反红线 1 参数纪律形态惯例（8.16 裁定②⑥⑨与 8.41 全部新阈值均配置化 + ≤0 fail-fast）。

**零触碰边界**：8.41 W-09 state handler 面（:243-269 区）零触碰（红线 8）；其余端点（/metrics、list、detail、stop、checkpoints、threaddump）零 body 读取（C8-008 失效面枚举亲证维持）零触碰；`authorized()` 认证门（:163-169，先于 handler 执行）零触碰——413 分支位于认证之后，与 401 语义无交互；`TestStreamOpsAuthAndClassLoading` 零回退。

### A.2 W-03：failedCommitParticipants 有界账本 + 升级出口（C5-006，AR-10 残留）

**决策**：**「重试轮次计数 + 连续 N 轮失败升级出口（注销 + typed failJob）」唯一分支**——审计建议两支中采 (a)（出口支），(b)（仅重试最近 K epoch）不采（见备选）：

1. **轮次计数载体**：CheckpointCoordinator 新增实例字段 `commitRetryFailureCounts`（`Map<CheckpointParticipant, Integer>`，package-private 供测试——`:351-356` 既有 package-private 账本先例形态）；`retryFailedCommits`（:1840-1892）内——参与者重试成功或其条目被移除时计数清零；重试仍失败时递增。
2. **升级出口触发**：同一参与者**连续**失败计数 ≥ N（配置键 `nop.stream.checkpoint.commit-retry-escalation-threshold`，缺省 **6**，≤0 装配期 fail-fast——红线 1）：本轮重试循环内从 `participants` 注销（`removeParticipant` 既有通道 ：441-443）→ 后续轮 :1852 `contains` 判定失败 → stillFailing 空 → **:1876-1878 既有移除路径成为必然出口**（条目移除 + successMap remove + CLEARED 信号 ：1881-1884 依既有语义发出）。
3. **出口动作 = 注销 + failJob typed**：新增 `ERR_STREAM_COMMIT_RETRY_ESCALATED`（K6）；升级回调 `setCommitRetryEscalationHandler(Consumer<Throwable>)` 由 JobCoordinator 在 abortHandler 接线同点位（:2711 邻域）接线至 `failJob(StreamException(ERR_STREAM_COMMIT_RETRY_ESCALATED)...)`（JobCoordinator:848 既有通道，幂等——already terminal 走既有 WARN 分支 ：854）。**执行时序唯一形态：升级动作在 retryFailedCommits 迭代收尾之后统一触发**（迭代内仅收集升级清单）——避免迭代中途终态化协调器与 fanout 任务体交叠；failJob 从 fanout 线程调用自身终态化在 8.41 C.2 always-inline 形态下不丢通知（shutdown 窗口 inline 语义）。
4. **epoch 数上限不引入**：出口已使条目增长在 N 轮内终局化（单参与者 6 条目量级；多参与者各自独立计数，账本上界 ≈ 参与者数 × N）；failJob → coordinator 终态 → close 清理（failedCommitParticipants.clear() :2007）兜底。

**理由（触发时序与组合论证）**：缺陷时序——T1 参与者进入「finishCommit 通知持续抛异常而任务本体未失败」态 → T2 每 checkpoint 完成入账 1 条（:1835）+ 30s 周期 ≈2880 条/天单调增长，且每条目在每次全表扫描中反复重试（放大）；post-fix——第 6 轮连续失败即注销+typed 升级，条目终局于 :1876 移除路径，重试放大与堆积同步终止。N=6 论证：2PC sink 的 finishCommit 失败多为瞬时 I/O 抖动（下一 epoch subsuming commit 兜底），6 轮 = 6 个 checkpoint 周期（缺省 30s ≈ 3 分钟）恢复窗口，显著长于瞬时抖动、显著短于无限重试；配置化允许下调（激进升级）或上调（保守重试）。**红线组合论证**：① abort 门控（:1223-1224/:1455-1456）零改写——门控判定本体（containsKey）与「条目存在期不删终端标记」语义完全保留，升级仅通过既有移除路径达成条目终局（红线 2）；② 原位替换注释（:1886-1889）与 stillFailing 非空路径零改写；③ 8.41 W-13 site ④ 抑制器（:1861-1886）零触碰——升级场景时序：第 N 轮重试失败（抑制器按既有三态记录）→ 注销 → 本轮 stillFailing 空 → 条目移除 → CLEARED 信号恰发一次（:1881-1884「ledger 空表」恢复语义在升级场景为真）→ 迭代收尾后 failJob 单发 ERROR 日志（一次性终局事件，不入抑制器）——抑制器停在不活跃态，无悬挂恢复信号。

**被否决的备选**：
- **审计建议 (b)「仅重试最近 K 个 epoch」**：只截断扫描面不提供出口——被截断的旧条目仍在 map 中（abort 门控 containsKey 依旧命中、successMap 终端标记依旧钉住），移除条件（:1876）对它们永不达成，账本无界本体未闭合；作为独立方案不采。
- **仅上限（epoch 数截断）无出口**：同 (b)——截断不等于终局化，条目仍在。
- **出口 = 静默注销（不 failJob）**：任务本体未失败时静默注销使该参与者永久缺席后续 checkpoint 提交且零可观测——违反「补偿自身失败 ERROR 显式可见不吞」纪律（裁定④ 引用边界）；typed failJob 使恢复路径接管，与 :1082-1089 reportAssignmentFailure「让恢复行动起来」契约同构。
- **出口阈值按绝对条目数（触发全局 failJob）**：多参与者场景一个卡顿参与者即可达全局阈值，误杀面大；per-participant 连续计数把升级精确归因到持续失败者。

**零触碰边界**：abort 门控两站点判定行零 diff；原位替换注释与语句零 diff；W-13 site ④ 抑制器接线与 CLEARED 信号零 diff；8.41 C.2 的 commitFanoutExecutor 分窗口结构/CommitFanoutTask/溢出 deque 零触碰（红线 7）；`TestCheckpointAbortMarkerCleanup` anti-hollow pin（「marker 不得提前清除」）零改写；`TestCheckpointCoordinatorRaceCondition` 零触碰；恢复语义（重试成功→移除+CLEARED）零改写。

### A.3 W-06 / C8-009：两处 submit 拒绝 zombie 清理 + taskDeployed 后置

**决策**（C8-009 剩余面——8.41 批域边界「其余面零触碰」的解冻承接；与 8.41 红① 正交边界显式画法见第 5 条）：

1. **面①（receiveAssignment submit catch :772-779）**：catch 内在 `reportAssignmentFailure` 之前补清理三件——`runningTasks.remove(taskKey, runningTask)`（条件 remove）+ `runningTask.cancel()` + `semaphoreReleased.compareAndSet(false, true)` 成立才 `capacitySemaphore.release()`——**逐句照抄同方法 build-failure 清理形态**（:1026-1030，并发安全先例：条件 remove 防误删交叠替换的继任条目，CAS 防双释放）。
2. **面②（deployTask submit catch :995-1002）**：同形态三件清理，然后保留既有 `throw rejectDeploy(...)`（typed 上报已达语义零改写）。
3. **清理时序唯一分支 = catch 内、throw/return 之前**：不采 finally——finally 需 success 标志区分正常/拒绝路径，而 submit 失败是单点拒绝出口（无其他分支可达），finally 复杂度无对价。
4. **taskDeployed 后置**：两处 `nodeMetrics.taskDeployed()`（:768/:991）移到 `runningTask.setFuture(future)` 之后（submit 成功后单点）——从未运行的 zombie 不再计入已部署指标；语义声明：build-failure 路径（:1020-1035）发生在 submit 成功之后，其任务曾被 executor 接受（将经 invokableLatch 超时失败），计入 deployed 属可接受观测语义，`reportDeployFailure` 通道（:1065-1072）零改写。
5. **与 8.41 红① 的正交边界画法（显式）**：8.41 W-02 compute 世代分派（:731-742）与 rejectedIncumbent 分支（:744-759）= C8-009 的 duplicate 上报半面（8.41 红① 绿侧语义本体）——**已修，本批验证登记不重修**：红④ 驱动器对 duplicate 面只做 8.41 既有断言零改写核对（TestConcurrencyWindows2326 variant b/c + `TestTaskManager.testDuplicateAssignmentIgnored` :189-201「不挤位」），**不新增重复钉**；本批改动面收敛于两 submit catch 窗口（:772-786 / :993-1004）+ 两 taskDeployed 语句位移（:768/:991）——compute 区/rejectedIncumbent 分支/fencingRotated 与 staleRejected 分支（:959-982，其 release+typed 语义本就正确亲证）零 diff。
6. **deployTask 异常传播面调用方核对**：rejectDeploy 已是既有通道（staleRejected/fencingRotated/build-failure 三分支今日即 throw），EmbeddedDistributedExecutor/RpcDistributedExecutor 的既有处置（传播为 FAILED 报告）零新增面；rejectDeploy 自身（:1054+「always throws」契约）零改写。

**理由（时序论证）**：zombie 形态——submit 抛 RejectedExecutionException 时 `runningTasks` 已含条目（compute 已插入）、许可已占（tryAcquire 成功）、`RunningTask.run` 永不执行 → 终态释放（RunningTask finally 释放路径）不可达 → 每 zombie 钉 1 许可 + 1 条目。自愈链断裂论证：remote 路径（deployTask）靠下次同槽重部署位移（selectDisplacedTask older-or-equal 语义）有界自愈；in-process 路径（receiveAssignment）无位移逻辑，其自愈重部署恰被 8.41 已修的 duplicate 拒绝分支拦截（typed 上报正确、但 zombie 条目使该槽位 capacity 恒 -1 直至 epoch 轮换/stop）。post-fix：拒绝即三件清理，许可会计与登记面回到 pre-submit 状态，观测面（taskDeployed）不含 zombie。

**被否决的备选**：
- **finally 清理**：见第 3 条，复杂度无对价。
- **zombie 计数对冲（taskDeployed 前置保留 + 拒绝时 taskUndeployed 对冲）**：引入第二计数通道且「从未部署的任务先计再减」的观测语义失真窗口保留；后置是根因修复。
- **面② 改「清理后不 throw、转 reportAssignmentFailure+return」**：deployTask 是同步 RPC，调用方期待 typed 异常（既有三分支均 throw）——改 return 破坏调用方契约面。

**零触碰边界**：8.41 W-02 compute 语义（:731-742 世代分派/严格不等比较/installInvokable epoch 校验）零回退零触碰；`TestTaskManager.testDuplicateAssignmentIgnored` 断言面零触碰；8.41 红① 断言（TestConcurrencyWindows2326）零改写；`TestFencingTokenRejection` 零触碰；`forceTaskExecutorShutdownForTest`（:1338）钩子本体零改写（红④ 构造载体）。

## 裁定 B core 控制面与包络域（W-02/W-04）

### B.1 W-02：ProcessingTimeServiceDriver 投递去抖（C5-005，并入 C7-004/C8-004）

**决策**：**driver 自持 `AtomicBoolean timerFireInFlight` 去抖**唯一分支（审计建议① 的 AtomicBoolean 变体）：

1. driver 实例字段 `timerFireInFlight`（包内可见——红② 观测缝）；`run()`（:118-132）tick 循环内 `due` 判定后：`if (!timerFireInFlight.compareAndSet(false, true)) skip`（在途即跳过本 tick 投递）；CAS 成功才 `mailbox.put(Mail.control(wrapped, "processing-time-timer-fire"))`（:123）。
2. **清零载体**：投递的 runnable 包裹 `try { fireDueTimersOnTaskThread(); } finally { timerFireInFlight.set(false); }`——清零发生在任务线程执行点（fire 完成即清），异常路径（fire fail-fast 上抛杀任务）同样清零。
3. **mailbox 结构零改动**：TaskMailbox 双 CLQ/put closed 静默丢弃/优先级语义零 diff（既有行为登记 (g) 零回退）；owner doc :24 timer fire fail-fast 语义零回退（fire 异常仍传播杀任务——去抖不改变触发语义）。

**理由（时序论证）**：缺陷时序——任务线程阻塞在数据面背压点（`queue.put`/send，慢 sink 为正常态）数小时，timer 持续 due → driver 每 100ms tick 投一封新 Mail（同 runnable，Mail 对象+CLQ 节点每次新建）→ ≈864,000 封/天慢性堆增长，且每封执行时 TreeMap 多为空（纯冗余）。post-fix 时序——T1 driver put（flag=true，volatile store）→ T2 任务线程 take 执行（mailbox 队列序建立 HB）→ finally 清零（volatile store）→ T3 driver 下一 tick 读 flag（volatile load）：due 仍真则重投。**任意阻塞时长在途 ≤1 封**（红② 断言）；**触发延迟上界 = 1 tick（100ms）**——fire 是幂等 catch-up 读（`fireDueTimers(now)` 读 TreeMap 全部到期项，tick 采样语义既有），去抖不丢触发：due 持续为真直到 fire 执行，跳过的 tick 由后续 tick 补投。陈旧读自愈：driver 读到陈旧 true 的最坏后果 = 多跳过数个 tick，fire 延迟仍 ≤ tick 量级，无丢失路径。closed-drop 残余面声明：mailbox close 后 put 静默丢弃 → flag 永不清零 → driver 不再投递——良性：driver 为 single-shot（StreamTaskInvokable invoke() finally shutdown :723-727），close 与 shutdown 同终态窗口，flag 状态随 driver 消亡；登记为已接受语义（javadoc）。

**被否决的备选**：
- **mailbox 深度查询（`size()==0` 才投）**：深度含全部 mail 类型——barrier/cancel 在途即抑制 timer 投递，timer 触发被无关控制面 mail 无界期推迟（barrier 派发慢时 timer 饿死）——语义污染，不可采。
- **TaskMailbox 接入平台 QueueOverflowPolicy（审计建议②）**：CONTROL 类「满时丢重复」需要 mail 等价性/descriptor 设施（TaskMailbox 零此类设施，引入即结构改造），且 CONTROL 丢弃语义对 cancel/barrier 类单发关键 mail 不可接受；去抖已闭合唯一高速率投递源（审计失效面枚举：barrier 每 checkpoint 一封、cancel/abort 单次——其余源全部低频单发），建议② 收益为零、风险为正——不采。
- **TaskMailbox 新增 `hasPending(descriptor)` 扫描 API**：CLQ O(n) 每 tick 扫描 + descriptor 字段新增——复杂度高于 AtomicBoolean 且语义等价。
- **mailbox 无界本体强行统一接入容量上界**：去抖闭合 10Hz 源后，「非 timer 源无界投递」假设不可达（失效面枚举在案）——**登记为 watch-only residual**（plan Deferred 节「裁定落定后状态固定」条款兑现：W-02 mailbox 无界本体残余面 = watch-only residual，Successor Required=no；若未来出现高速率第二投递源再立项）。

**零触碰边界**：TaskMailbox 全文件零 diff；`fireDueTimersOnTaskThread` 的 fail-fast 传播语义零改写（:146-158）；「未到期不发 mail」「shutdown 后不发」既有保护（TestProcessingTimeServiceDriver :115/:158）零改写；tick 采样语义与 DEFAULT_TICK_MS=100 零改写。

### B.2 W-04：MemoryBudget 死配置处置（C5-012，并入 C8-015；FAM-3 相邻注记）

**决策**：**形态①（声明降级）——javadoc 契约声明 + `@Deprecated` 机械载体 + owner doc 包络声明落账**（**偏离 plan 预设②**——偏离的 live 依据与论证见 K7 与备选否决）：

1. `MemoryBudget` 类 javadoc 声明「**RECORD-ONLY, NOT ENFORCED**：本预算对象当前无任何执行机制（无 Monitor、无提交期验算、无运行时约束）；真实内存上界由元素制设施（IBufferPool 65536 元素缺省、EdgeConfig.queueCapacity）与状态工作视图驻堆决定；禁止用于容量规划（包络声明见 owner doc『规模与内存包络』节）」+ **`@Deprecated(since = "8.42")`（forRemoval = false）**——待设计 §9.2 执行者真正落地时移除注记。`getAllocation`（:44-46）javadoc 同步。
2. `DeploymentPlan.getMemoryBudget()`（:77）javadoc 同步声明 record-only。
3. `IBufferPool` :27-31 单位冲突 javadoc 零改写（既有自认声明——与声明形态互补一致）。
4. **owner doc（E5）**：`docs-for-ai/03-modules/nop-stream.md` 新增「规模与内存包络」节（C5 审计文档缺陷附议采纳）：真实上界的元素制声明 + checkpoint 序列化峰值倍数注记（引用 C5-002 审计口径）+ materialization store 无界注记（引用 C5-001 deferred 批外面）。
5. **`state-management-design.md` §9 状态注记**（Phase 3 落账，新增登记面）：§9.1/§9.2 补一行状态标注「声明保留、执行者未实现（CRA 8.42 裁定 B.2）」——消除 stale promise（AGENTS.md docs 冲突同任务修复条款）。
6. **E2 钉 = 结构钉**：反射断言 `MemoryBudget.class.isAnnotationPresent(Deprecated.class)`（core flow 包测试——pre-fix 红 / post-fix 绿，确定性）+ 变异/回退论证（移除注记/删除声明 → 钉红；声明删除无运行时行为面可测——文档级缺陷的验收物即声明本体，结构论证留痕禁伪绿）+ owner doc 包络节 grep 留痕归 closure 核对。

**理由（live 事实链）**：缺陷本体 = 「声明存在而守卫缺失」的容量规划假保证（getter 零调用方 + Monitor 零实现 + 三处硬编码 64MB 亲证）——修复目标 = **消除假保证**，非补执行者。声明降级以最低成本达成：每处引用点（编译器 deprecation warning）与文档双通道声明「此数字不可用于规划」，真实包络由 owner doc 如实声明。**FAM-3 统一裁决注记（立案分离不入修复面——merge-adjudication :32/:165）**：FAM-3（C8-010 receiveWindow/packetSize 已裁定边界 + C8-012 远程 queueCapacity 硬编码）与本裁定的家族姿态统一为「**声明保留 + 执行者登记 successor**」——C8-010 的 WI14 已裁定边界（user-guide 既有注记）与 B.2 声明降级同构；本批对 FAM-3 零产品 diff，修复本体归 R4+ 后续批。

**被否决的备选**：
- **形态②（提交期静态验算 fail-fast——plan 预设，偏离）**：两个前提 live 不成立（K7）——① estimatedItemSize 零来源：`buildEdgeConfigs` 全部 `EdgeConfig.defaultConfig()`，无任何字节估算点，fail-fast 阈值必须建立在**虚构的换算常数**上——以执行机制的形式复制「假保证」本体（假精确获得强制力，比声明缺失更糟）；② 缺省 64MB 预算与真实包络偏差数量级（C5-001 口径 1K rec/s×1KB ≈ 86GB/天 vs 64MB），门槛值无诚实来源；诚实的字节核算需新增 estimate 基建（出批范围）。
- **形态③（HighWatermarkSemaphore 水位告警监控子集）**：与 P3 **C8-006** 的接线面（BufferPool 用量 gauge/mailbox 深度 gauge——`getGlobalUsage` 零消费即其登记本体）正面冲突——P3 相邻面零触碰边界（Non-Goals）禁止本批接线；且监控不消除容量规划假保证（规划者仍面对无执行力的 64MB）。
- **形态①-移除变体（删 MemoryBudget / 删 DeploymentPlan 字段）**：DeploymentPlan 构造点全仓 ~20+（K7②——含 8.41 钉文件的构造语句行），签名变更波及钉文件代码行——违反「keep diffs minimal」与钉零触碰纪律；且删除与 FAM-3「声明保留」家族姿态不一致。

**零触碰边界**：`MemoryBudget` 字段/构造/`defaultLocalBudget` 三分逻辑（:36-41）与三处硬编码调用点（DefaultDeploymentPlanProvider:59 / DeploymentPlanGenerator:45/:79）零 diff；`DeploymentPlan` 结构零 diff；`TestFlowControl` 既有断言零改写（新增结构钉为独立用例）；FAM-3/C8-006/C5-001/C5-002/C5-003 各自本体零触碰。

## 裁定 C 数据面确认与 side-output 生命周期域（W-05/W-07）

### C.1 W-05：poll 失败行 typed 传播（C8-005，并入 C3-004）

**决策**：

1. **数据面/控制面分界 = topic 前缀判定，与裁定⑥ 同构**：新增共享常量 `DATA_TOPIC_PREFIX = "nop-stream.data."` 与 `private static boolean isDataPlaneTopic(String)`（与 `isNoBacklogTopic` :577-579 / `pollExecutorFor` :568-570 同形同点位；`pollExecutorFor` 改引共享常量——一行等值重构，语义零变化，红线 3 零回退）。
2. **typed 失败通道载体 = 订阅方 `instanceof WireDecodeFailureAware` 直调**（经 K5 微增使通道可达——`AdaptingConsumer` 增补 `implements WireDecodeFailureAware`，委托 inner-aware，inner 非 aware 时维持其既有 LOG.warn+discard fallback；对该增补的偏离登记见 K5，文件其余零 diff）：
   - **面①（反序列化失败 :600-604）数据面**：既有 `LOG.warn` 保留（该点不在 8.41 抑制覆盖集，行为见批外登记 BF-1）→ 追加 `if (isDataPlaneTopic(topic) && consumer instanceof WireDecodeFailureAware aware) aware.onWireDecodeFailure(topic, row.payload, e)` → `lastSeenId.set(row.id)` 语句零改动（通道已终态，订阅将随任务失败取消，cursor 前移无消费后果；保留语句使数据面/控制面共用代码路径 diff 最小）。
   - **面②（consumer.onMessage 抛异常 :606-622）数据面**：8.41 抑制器三态记录保留（红线 9，日志量零变化）→ 追加同型升级判定（aware → `onWireDecodeFailure(topic, message, e)`——语义：记录无法被消费 = typed 通道失败，绝不静默缺口）→ :622 cursor 语句零改动。
   - 语义锚：与 `RemoteInputChannel.onWireDecodeFailure`（:593-603「a data-plane consumer must never silently lose a record」typed 失败通道）同族闭合——DB 后端的 payload 损坏与 wire 解码损坏升为同一失败语义。
3. **fallback 子项（F8——非 WireDecodeFailureAware 消费者）**：数据面 + 非 aware 消费者 → 于 cursor 前移**之前**抛 `StreamException(ERR_STREAM_DATA_PLANE_RECORD_FAILED, e)`（新码，K6）→ 外层既有 catch（:635-646，W-13 site ①）捕获抑制记录 → 本轮中断、cursor 未前移 → 下轮重试同一行。**声明语义 = poison 行 fail-loud wedge**：记录永不静默消失（guide #24），失败持续可见（抑制器限频），恢复需运维清除损行——写入类 javadoc Limitations 同步改写 + owner doc。「无消费者」不可能态声明：`subscribe` 契约要求非空 consumer（subscribe(:402) 入口 IllegalArgumentException（:405-407 订阅期拒绝）[F-8 措辞订正] 同型可见失败）——核证义务随 impl 落。
4. **控制面行处置 = 维持 warn+skip+cursor 前移**（:600-604/:606-622 的非数据面路径零行为变化）——RPC 请求有调用方超时兜底（审计口径）；控制面若也升级 wedge 将钉死节点控制通道（deploy/cancel/ACK 全部停摆，且 W-13 site ① 的「poll 成功一次即 CLEARED」恢复信号永不来）——控制面维持是红线 3/9 的组合要求。
5. **at-least-once Limitations 同步改写**（E5 面）：类 javadoc Limitations（:72-77 区）增补「数据面失败行（反序列化失败/消费异常）升级 typed 通道失败（aware 消费者）或 poison 行 fail-loud wedge（非 aware 消费者）——数据面记录永不静默跳过；控制面行维持 warn+skip+前进（RPC 调用方超时兜底）」。

**理由（触发时序论证）**：缺陷时序——T1 存储损坏/版本漂移使某行 payload 不可反序列化 → T2 poller 轮询到该行 → warn + `lastSeenId.set(row.id)` + continue → 该记录对订阅方永久消失，watermark/屏障/ACK 照常（静默缺口），cursor 已越过无恢复重放入口。post-fix（aware 主时序，在库唯一数据面订阅方 EnvelopeConsumer 经 AdaptingConsumer 即 aware）：T2' 升级直调 → 通道 `finished=true` + 终端哨兵入队 → reader 读取即 typed 失败 → 任务失败可见 → supervision 恢复路径接管（与 wire 层 decode 失败同型）。「never silently lose a record」契约在两层（wire 层既有/payload 层本批）闭合。本地/嵌入式模式（LocalMessageService）不经本类——本裁定面 = JDBC launch 后端。

**被否决的备选**：
- **控制面行也 typed/wedge**：见第 4 条——节点控制通道钉死不可接受。
- **数据面非 aware 消费者也前移 skip（维持现状+日志）**：违反 guide #24 静默跳过禁令——即缺陷本体。
- **死信队列/重试计数表**：新表新基建（FAM-2 级复杂度），非最小修复，出批。
- **adapter 层拦截（把 javaDeserialize 迁入 DataPlaneMessageServiceAdapter）**：adapter 职责是 wire envelope 重建，DB 后端反序列化在 backend 内部先行发生——迁入即传输层结构重排，超工单面；`instanceof` 直调 + AdaptingConsumer 增补是等价可达性的最小形态（K5）。
- **数据面 poison 行无脑重投（非 aware 也 throw 但 cursor 前移）**：cursor 前移后下轮不再见该行——throw 变成一次性日志，静默缺口回归。

**零触碰边界**：:588-595 LIMIT 查询块零 diff（红线 5）；:608-620 抑制器接线与 :626-634 恢复信号零 diff（红线 9）；:622 cursor 语句零 diff；cursor bootstrap（:547-554）与 `isNoBacklogTopic` 语义零触碰；`RemoteInputChannel`/`WireDecodeFailureAware` 接口本体零 diff（仅 AdaptingConsumer 一处接口声明+委托方法——K5 偏离[+F-9：WireDecodeFailureAware 语义放宽条款——接口 javadoc 自述 wire-level，本批同一通道承载 payload 级失败（面①反序列化/面②消费失败）而接口本体零 diff——放宽使用登记 owner doc E5]登记面）；`PeriodicFailureSuppressor` 零 diff；裁定⑧ 清理链零触碰。

### C.2 W-07：FAM-2 SideOutputRegistry 作用域/生命周期（C1-002+C5-007+C7-006 三员一族）

**决策**：三选一定夺 = **per-execution registry 实例**（第三支），具体形态：

1. **键形/容器裁定**：`SideOutputRegistry` 从 static CHM 类改为**实例类**（保留类名），实例挂 `StreamExecutionEnvironment`（env 实例字段 + `getSideOutputRegistry()` 访问器）——**一个执行上下文一个 registry 实例**：LOCAL/embedded 每次 `runLocal` 一个 env = 一个作用域；REST 每 jobId 的 factory build 一个 env = 一 job 一作用域（`ClusterPipelineFactory.buildPipeline(String jobId, ...)` 按 jobId 构建，per-job env 为工厂实现契约，接线验证义务见下）。**注册时点 job 身份可达性冲突（F2）的消解方式：作用域键 = env 实例身份而非 jobId 字符串**——装配期 env 已存在（`SideOutputDataStream` 经 `DataStreamImpl.environment` 可达亲证），无需等待 execute 期 jobId 产生；**注册点不迁移**（`SideOutputDataStream.sink` :90 仍在装配期注册，目标从 static map 改为 env registry）。
2. **读点身份传递链**：LOCAL/embedded 路径——`GraphExecutionPlan.build`（:569[F-6 微漂订正] runLocal 调用点）注入 env registry → plan 持有 → StreamTaskInvokable 构造点注入 registry 引用（13 main 构造点全触，全 impl 叶——K3 订正后的真实波及面）→ ChainingOutput 经 invokable 获得引用（:388 单点）。两读点的**全局 fallback 改查实例 registry**（ChainingOutput :117-123 / StreamTaskInvokable.sideOutputConsumerFor :1228-1233）；**task-local 一级查找零改动**（生产本就零填充——K4③，两级结构保持）。分进程远程路径[设计评审 F-3 分形态声明：分进程远程=与现状逐语义一致；**embedded 同 JVM 形态（EmbeddedDistributedExecutor 经 RemoteGraphExecutionPlanBuilder :52/:292）=显式收紧**——现状 static fallback 使 sink 闭包注册在同 JVM 可命中，post-fix 注入 null→fail-fast；全仓无「sink 闭包×embedded 远程执行」组合使用（在库测试均走 LOCAL 或直接接线）——语义变化登记 owner doc E5（JVM-local→execution-context-local）：RemoteGraphExecutionPlanBuilder 构建的 invokable 注入 null/无 registry——读点 miss → 既有 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` fail-fast，与现状逐语义一致（side-output 本就 JVM-local，远程 JC/TM 分进程下注册表不可达为既有语义——本裁定将其收紧为 execution-context-local，非放宽）。
3. **生命周期裁定（「终态 remove 接线」的结构化满足——对 merge-adjudication FAM-2 行双层义务的落定）**：注册容器生命周期 = 执行上下文生命周期——作业终态（runLocal 返回 / coordinator 终态）后 env+plan+invokables+registry 整体不可达，条目随对象图回收，**static 残留结构性消除**。义务满足声明：**复合键义务** = 作用域隔离（env 实例物理隔离强于复合键——复合键仍共享 static map 跨 job 条目共存，实例化则连共存面都消除）；**终态接线义务** = 容器寿命有界化（结构化 remove 优于「终态点批量调 remove」——无遗漏锚风险：任何执行路径的上下文对象消亡即回收，REST/LOCAL/多作业宿主三形态自然全覆盖，不依赖每个终态锚都记得接线）。**OpsJobManager.stop（:342-366 区）与 shutdownLocalExecution（:709-723，含 SourceCoordinatorRegistry.unregister 先例）零触碰**——既有终态锚零 diff。`remove(tagId)` 保留为实例上的显式注销 API；`clear()` 改实例方法（测试 isolation helper 语义保留）；static 门面移除（测试 teardown 调用点（实际仅 1 处 clear 调用[F-10 订正]）随测试树改造）。
4. **同 tag 跨作业顶替歧义消除声明**：两 job 同名 tag → 各自 env registry 各自条目 → 互不可见 → `CONSUMERS.put` replace 语义仅存在于「同作用域内同 tag 重注册」（单 job 内重注册替换语义零变化）——跨 job 错投路径结构性消除（非告警、非校验，是键空间物理分离）。
5. **波及面声明**：main = SideOutputRegistry（类形态改造）+ SideOutputDataStream（注册点 1 处）+ StreamExecutionEnvironment（字段+访问器）+ GraphExecutionPlan（registry 传递——以 build 链注入/等价 setter，形态随 impl 在「registry 于 invokable 构造期可达」契约内定）+ StreamTaskInvokable（字段 + 13 构造点）+ ChainingOutput（字段 + 1 构造点 + 读点）——全部 impl 叶，零 api 叶、零平台设施；测试树 = ChainingOutput 构造点 46 处 + SideOutputRegistry.clear 调用点改造——**本批最大 diff 工单，登记在案**。

**理由（触发时序论证）**：缺陷时序（C1-002）——T1 作业 A 构建管线注册 `tagA→consumerA`（static map）；T2 同 JVM 作业 B 同名 tag 注册 `put` 静默顶替（无 job 维度校验无告警）；T3 A 的生产任务每条 tagged 记录查表命中 consumerB → **A 的数据持续错投进 B 的 sink**（正确性违约，零日志）；生命周期层（C5-007/C7-006）——作业终态后 `remove` 零生产调用方，条目连同 consumer 闭包强引用整条算子链永驻 static map，多作业宿主按提交次数单调累积。post-fix：T2' B 注册进 B 自己的 env registry → A 的读点（A 的 invokable 持 A 的 registry）不可见 B 条目 → 顶替不可能；终态后整图回收。生产路由事实（K4③：task-local 生产零填充、100% fallback）使实例化是**唯一同时闭合两层的最小改造**——任何保留 static map 的方案（复合键）都必须再补终态锚接线，改造面更大且留回归静态缺陷的窗口。

**被否决的备选**：
- **jobId+tagId 复合键（plan 修复方向第一支）**：三重劣势——① 注册时点 LOCAL jobId 不可达（K4②），必须把注册迁移到 execute/plan-build 期（迁移复杂度与实例化相同而收益不增）；② 读点 job 身份传递链无自然载体（StreamTaskInvokable 零 jobId 字段、LOCAL 零 taskMetrics 注入——K4①），需另行发明传递链（13 构造点之外再加身份字段）；③ static map 保留 → 终态 remove 接线义务仍需 OpsJobManager/runLocal/嵌入式宿主显式锚（三形态逐一接线，遗漏即回归），泄漏残留窗口结构存在——严格劣于实例化。
- **键源替代（装配期可用身份做键——如 plan 指纹/句柄 token）**：装配期唯一稳定身份就是 env 实例（=实例化的别名）；fingerprint 键在重复执行同一 plan 时跨执行共键（隔离失效）——不成立。
- **仅注册迁移（execute 期注册，保留裸 tagId static 键）**：只解决时序不解决作用域——跨 job 顶替维持（正确性层不闭合），不合 FAM-2 双层义务。
- **维持 static + 仅终态 remove 接线**：生命周期层闭合、正确性层（顶替）不闭合——不合 FAM-2；且顶替是族内正确性违约主体，不可只修一半。

**零触碰边界**：task-local 两级查找结构零改动（一级 map 填充语义含测试用法零回退）；`OutputTag` 相等语义（id-based）零改动；`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` fail-fast 分支语义零改写（miss 时仍 fail-fast）；OpsJobManager/shutdownLocalExecution/SourceCoordinatorRegistry 先例零触碰（红线 4）；既有单 job 功能链路 `TestSideOutputChainingE2E（:225/:386/:504 区直接构造 ChainingOutput/invokable——构造行触改、断言零改写[F-10 注]）`/`TestWindowedStreamSideOutputLateData` 断言零改写（其经新链路继续绿 = rule 22 端到端接线载体）。

## 裁定 D 测试缝总表（红①–红⑤ + E2 泛在钉×2 + 零回退钉全列 + C10 五条映射）

### 红绿判据纪律（先红后绿，E2）

全批测试零 mockito（两模块 pom 亲读维持）——观测一律 JDK 并发原语 + latch + 手工桩 + 包内可见注入缝（裁定⑪ 九缝形态惯例）。**orTimeout 假绿陷阱纪律**：future 完成性断言只允许同步 `get(timeout)`，捕获 TimeoutException 即判红；禁用 `orTimeout`/`completeOnTimeout`。全部闩带上界 ≤30s 且收尾必放闸。红绿快照落 `_tmp/` 总册 + 逐 trial 日志。测试类注释显式归属 CRA 8.42 R4-02（同号防混——8.42-W-xx ≠ 8.41-W-xx）。

### 产品侧测试缝（S1–S7）

| 缝 | 形态 | 覆盖钉 |
|---|---|---|
| S1 | StreamOpsConfig `nop.stream.ops.http.max-body-bytes` 配置键（小限额注入即缝，零产品可见性新增） | 红① |
| S2 | driver `timerFireInFlight` 包内可见字段（行为断言为主，字段缝为辅） | 红② |
| S3 | CheckpointCoordinator `commitRetryFailureCounts` + `setCommitRetryEscalationHandler`（package-private，:351-356 先例形态）；notifyParticipantsFinishCommit/retryFailedCommits 已 package-private 可直调 | 红③ |
| S4 | 既有钩子零新增：`forceTaskExecutorShutdownForTest`（:1338）+ TaskNodeMetrics 计数读面 | 红④ |
| S5 | env `getSideOutputRegistry()` 访问器（公共 API 即缝）——双 env 构造零产品缝 | 红⑤ |
| S6 | PollingJdbcMessageService 失败行注入（H2 内存表直写损坏 payload；Recording aware consumer 双层验证[直连 + 经 adapter 委托]） | E2 泛在钉 W-05 |
| S7 | MemoryBudget `@Deprecated` 反射断言（core flow 包测试） | E2 泛在钉 W-04 |

### 红①–红⑤ 驱动器总表

| 钉 | 成员（C10） | 驱动器形态 | pre-fix 红判据 | 伪绿防线 |
|---|---|---|---|---|
| 红① | W-01（C10-017） | TestStreamOpsHttpServer 扩展：S1 配置限额 1KB → (a) POST 2KB body 且 CL=2KB → 断言 **HTTP 413 + `ERR_STREAM_REQUEST_BODY_TOO_LARGE`** 且零读取零解析；(b) chunked 变体（无 CL 头，实体 >1KB）→ 413；(c) 合法小 body → 201 既有断言零改写。pre-fix：同请求得 400（parse 失败）或 201 → 确定性红 | 确定性（当前即红） | 断言状态字面 413 + 错误码常量等值（禁消息文本 substring 嗅探）；(a)(b) 双变体防「预检存在但 chunked 绕过」退化；变异探针 = 移除预检分支必红。闩带：HTTP 客户端超时 ≤5s |
| 红② | W-02（C10-021） | TestProcessingTimeServiceDriver 扩展（改造基座）：注册持续到期 timer + **不 drain** mailbox + driver 跑 ≥3 tick（tickMs 构造参数注入小值）→ 断言 `mailbox.size() ≤ 1`（在途上界）→ drain 一次断言回调恰好触发一次（幂等 catch-up）。pre-fix：3 tick → 3 封 → 红 | 确定性（当前即红） | 负向窗口用 TestAwait.staysTrue（不新增裸 sleep——C10 复核 :95 既有形态不扩散）；断言 fire 恰一次防「去抖把 fire 也去了」伪绿；变异 = 移除 CAS 守卫必红。闩带 ≤5s |
| 红③ | W-03（C10-018） | FailingParticipant（finishCommit 恒抛 stub，`addParticipant` 注入）+ 连续 M=N+2 轮「完成→重试」周期（package-private 直调 notifyParticipantsFinishCommit + retryFailedCommits，全同步）→ 逐轮断言条目计数有界；M 轮后断言：participants 不含该参与者（注销）+ `failedCommitParticipants` 空（:1876 路径终局）+ escalation 回调恰好一次（Recording）+ CLEARED 抑制信号路径零改写核对。pre-fix：M 轮后条目 = M 且参与者仍在、无出口 → 红 | 确定性（当前即红） | **逐轮**条目计数断言（非仅终态断言——防「只验出口不验上界」伪绿）；变异 = 移除升级出口 → 条目单调增必红；既有 anti-hollow pin（TestCheckpointAbortMarkerCleanup）零改写为并列断言。闩带：零（全同步直调，登记零闩带形态） |
| 红④ | W-06（C10-019） | TestTaskManager 扩展：`forceTaskExecutorShutdownForTest` 拒绝态 → (a) receiveAssignment → 断言 `getRunningTaskCount()==0` + `availablePermits()==CAPACITY` + taskDeployed 计数不含 zombie；(b) deployTask 同型（Recording coordinatorRpcService 经 setCoordinatorRpcService 注入断言 rejectDeploy FAILED 报告到达——CC-17 既有通道）。pre-fix：count==1 + permits==CAPACITY-1 → 红 | 确定性（当前即红） | 三断言齐备（条目/许可/指标）防单面假绿；8.41 红① 断言（variant b/c）零改写核对为并列登记（duplicate 上报面不重修不重复钉——裁定 A.3 第 5 条）；变异 = 移除 catch 清理必红。闩带：零（同步） |
| 红⑤ | W-07（C10-022） | 双 env 驱动器（FAM-2 族 ID 承载——串台+滞留双断言面）：envA/envB 各注册同名 tag 不同收集 sink → envA 产 tagged 记录 → 断言仅 sinkA 收到、sinkB 零收到（互不错投，双向）；终态滞留：envA 执行完成后断言 `envA.getSideOutputRegistry()` 跨 env 隔离+同宿主新 env 同 tag 旧 consumer 不复活[设计评审 F-4 订正——终态腿原「条目清空/不可达」无行为观测面（结构化回收无显式 remove+env executed-once 守卫 :396-397 使「同 env 新执行」结构不可能）；改为行为断言] + 同 env 新执行同 tag 旧 consumer 不复活。pre-fix（static）：B 收到 A 的记录（顶替）→ 红 | 确定性（当前即红） | 双向断言（A→A only 且 B→B only）防单向假绿；「GC 可达性」不作断言（不可靠）——行为断言（条目清空+不复活）承载；变异 = 恢复 static map 必红；既有 TestSideOutputChainingE2E/TestWindowedStreamSideOutputLateData 零改写并列运行。闩带 ≤30s（LOCAL execute） |

### E2 泛在钉×2（无 C10 号 2 工单）

| 工单 | 钉形态 | 分类 |
|---|---|---|
| W-04 | S7 结构钉：反射断言 `MemoryBudget.class.isAnnotationPresent(Deprecated.class)`（pre-fix 红——确定性）+ 变异/回退论证（移除注记 → 钉红；声明删除无运行时行为面——文档级缺陷验收物即声明本体，论证留痕禁伪绿）+ owner doc 包络节 grep 留痕归 closure | 编译期结构钉 + 论证 |
| W-05 | S6 损坏行注入：数据面 topic 预置坏 payload 行 → (a) aware 消费者断言 `onWireDecodeFailure` 到达 + 记录不静默消失（含经 AdaptingConsumer 委托的集成变体）；(b) 非 aware 变体断言 wedge（cursor 不前移 + site ① 失败可见）；(c) 控制面边界断言：控制 topic 损坏行维持 warn+skip+cursor 前移（负向窗口）。pre-fix：无任何调用、记录静默消失 → 红 | 确定性红 |

### 零回退钉全列

- **基线签名（勘误 4，2026-11-07 亲跑）**：`./mvnw test -pl nop-stream` 分解通道 = 已执行 **4424 tests / 0F / 0E / 20S**（core 1999 0F0E 1S / cep 386 / flow 315 / connector-jdbc 66 / debezium 79 0F0E 6S / batch 62 / kafka 26 / rocksdb 143 / connector 106 / runtime 1242 0F0E 13S 结果完整但 docker 双 E2E 慢跳过触发 fork 300s 超时 BUILD FAILURE——2534 已知面③ 精确复现，等价通道/排除通道验收按 E3 判据）；format-avro/sql/fraud-example 反应器跳过——Phase 3 补齐覆盖后其签名作为首验基线登记。零回退实证 = 各模块 tests/F/E/S 数量与归属签名比对（20S 归属逐一核对）+ 新钉增量。
- **裁定⑪ 七钉**（8.16）：TestCachingTableLookup / TestJobCoordinatorPerTaskFailure / TestInMemoryClusterRegistryAttemptHistory / TestResultPartitionDeadlock / TestMultiJvmExactlyOnceRecovery / TestLaunchMainScopeWiring / TestJobCoordinatorRecoveryConcurrency——钉内断言全批 diff 零触碰。
- **2510 新钉二**：TestStandbyDegradeCancelsInFlightPersist（裁定⑤ 层4）/ TestTaskAssignmentTerminalCleanup（裁定⑦——W-07 终态锚零触碰的钉面）。
- **8.41 全部钉**：红①–红⑧ 驱动器（TestConcurrencyWindows2326/TestTwoPhaseCommitSinkFunction 并发改写面/TestCommitFanoutOffMonitor2326/TestCheckpointBarrierTrackerConcurrency 面/端点级红⑤/红⑥ 线程退出/红⑦ stop 窗口/TestMicrometerStreamTaskMetrics 红⑧）+ E2 泛在钉×7 + 治理 pin（CC-01/CC-02/R5-CC-10[TestTaskManagerCommitQueueBound]/CC-04/CC-17[TestFencingTokenRejection]）——钉内断言全批 diff 零触碰。
- **既有锚点名（本批零回退锚）**：TestTaskMailbox（W-02 零回退锚）/ TestProcessingTimeServiceDriver（「未到期不发」「shutdown 不发」——红② 改造基座+既有保护零改写）/ TestTaskProcessingTimeService / TestStreamTaskInvokableProcessingTimeWiring（W-02 组合面）/ TestFlowControl（W-04 既有断言零改写）/ TestStreamOpsHttpServer（201/400/409 既有断言零改写——红① 只增不改）/ TestCheckpointAbortMarkerCleanup（W-03 anti-hollow pin）/ TestCheckpointCoordinator / TestCheckpointCoordinatorRaceCondition / TestTaskManager.testDuplicateAssignmentIgnored（:189-201「不挤位」——W-06 零触碰）/ TestConcurrencyWindows2326（8.41 红① 夹具——零改写核对）/ TestFencingTokenRejection / TestStreamOpsAuthAndClassLoading（W-01 认证面零回退）/ TestOpsRestLifecycleE2E / TestOpsJobManagerSubmitCompensation（裁定④ 钉——W-07/W-01 零回退）/ TestSideOutputChainingE2E / TestWindowedStreamSideOutputLateData（W-07 功能链路零改写）。
- **已知环境面集（勘误 4/5，E3 噪声源预案）**：面① nop-core TestResourceCacheInvalidationChain 负载 flake（solo 三联先例）；面② runtime F1 TestStreamConnectorRegistryE2E（solo 三联绿归因在案——禁静默重跑）；面③ runtime docker 双 E2E 慢跳过/fork 超时（本轮基线已现形亲证）；面④ debezium 6S 环境门控；面⑤ format-avro/sql/fraud-example 反应器跳过（Phase 3 补齐）。

### C10 五条映射（对齐纪律第一条——同 diff 落地，禁先修本体后补测拆分）

| C10 条目 | 红 | 工单 | 断言面 |
|---|---|---|---|
| C10-017 | 红① | W-01 | 超限 typed 413 拒绝且不进入 JSON 解析（CL+chunked 双变体） |
| C10-021 | 红② | W-02 | 持续 due × 阻塞安全点下在途 ≤1 + fire 幂等恰一次 |
| C10-018 | 红③ | W-03 | 账本条目有界/参与者经升级出口移除/出口 typed 可见（anti-hollow pin 零改写并列） |
| C10-019 | 红④ | W-06 | 拒绝态零残留 + 许可会计归位 + taskDeployed 不含 zombie（duplicate 上报面=8.41 断言零改写核对） |
| C10-022 | 红⑤ | W-07 | 跨 job 同名 tag 互不错投 + 终态注册清空（FAM-2 族 ID 承载——串台+滞留双断言面） |

## 条件申报登记（禁静默扩权——预设全部不触发，逐面亲读依据）

①**`*-api` 契约叶 → 预设不触发**：7 工单 import 面亲读——`io.nop.api.core` 仅 CoreMetrics/NopException/`@Internal`/`@DataBean` 使用面引用，零 diff；SideOutputRegistry/TaskMailbox/CheckpointCoordinator/TaskManager 均为模块内 impl 叶。②**orm/DB 承载 → 预设不触发**：W-04 裁定声明形态零 DB 面；W-05 失败行注入为测试树 H2；两模块 `model/*.orm.xml` find 零命中承 plan 亲证。③**平台设施本体 → 预设不触发**：QueueOverflowPolicy **不采**（裁定 B.1 备选否决——申报③ 的最大候选面直接消除）；micrometer/HighWatermarkSemaphore 不接线（裁定 B.2 形态③ 否决）；`com.sun.net.httpserver` 为 JDK 使用面；AdaptingConsumer 增补为 runtime 模块内类（非平台设施）。④**`_` 生成物/pom 结构 → 预设不触发**：三个新错误码落 `NopStreamErrors` 手写保留文件（916 行亲读，非生成物——8.16/8.41 增补先例）；新测试落既有测试树；SideOutputRegistry 实例化为模块内既有类形态改造非新模块；`_vfs/nop/stream/_module` 零触碰。任一触发 → 按 AGENTS.md 四件证据链（plan 追加申报 + 本文档专节修订 + draft review 记录追加 + 回归测试）重新申报后方可实施。

## 零触碰边界总表（8.41 产物面/8.16 产物面/P3 相邻面）

- **8.41 产物面（同文件跨批——禁顺带逐面）**：TaskManager W-02 compute 区（:731-742）+ rejectedIncumbent 分支（:744-759）+ installInvokable 校验；StreamOpsHttpServer W-09 state handler 面（:243-269 区）；CheckpointCoordinator W-12 grace window 面 + W-13 site ④ 抑制器（:1861-1886）+ C.2 commitFanout 分窗口结构；PollingJdbcMessageService W-13 抑制接线（:608-620/:626-648）；红①–红⑧+E2 泛在钉×7 断言面。8.41 裁定 A–F 如需修订走其自身文档修订，本批不回写不重开。
- **8.16 产物面**：裁定⑥ 分面（pollExecutorFor :568-570——W-05 仅改常量引用，调度语义零 diff）/裁定⑧ 清理位点与 ChannelBacklogProbe/裁定⑨ LIMIT 分批（:588-595）——W-05 的红线 3/5；裁定⑩ FAM-1 溢出语义零站点。
- **P3 相邻面（同站点/同主题防混——全零触碰）**：FAM-3（C8-010/C8-012——仅裁定 B.2 统一姿态注记，零产品 diff）；C9-007（materialization 持锁拷贝——裁定⑩ 零承接注记维持）；C4-003（StreamOpsHttpServer stop 不关 executor——W-01 同文件不同方法面零触碰）；C8-006（过载可观测四缺口 + queueCapacity≤0 静默回落——W-04 同主题面零触碰）；C7-013（.tmp 残留零交集）；C1-009/C2-010/C2-008（2534 已登记级维持）。执行期如意外触及禁顺带——四件证据链申报或回退。

## 批外新发现登记（亲读发现即登记，禁瞒）

- **BF-1（P3 量级——日志治理族，非 7 工单缺陷本体，本批零处理）**：PollingJdbcMessageService 反序列化失败日志点（:601，`LOG.warn` 直发）**不在 8.41 W-13 三态抑制覆盖集**（12 站含 poll 整体失败①与 consumer 失败⑤，不含 deserialize-failed 行级分支）——存储损坏 + 大积压场景下每损坏行一条 warn 的放大面。本批数据面升级后 poison 行走 typed 通道（放大被任务失败终止）；控制面维持 warn+skip 的放大面维持现状。归属：R-G 日志治理轨或 8.41 E.1 覆盖集扩展候选登记——不在本批 7 工单面内，禁顺带。
- 核证范围 = 9 站点文件 + 2 读点文件 + 接线锚 2 文件 + NopStreamErrors + PeriodicFailureSuppressor + WireDecodeFailureAware/RemoteInputChannel/DataPlaneMessageServiceAdapter + StreamOpsConfig + JobSubmissionSpec + PartitionedPlanGenerator/StorageJobIds 调用链 + TestStreamOpsHttpServer/TestTaskManager 抽验；K1–K8 均为工单域内勘正/补强，除 BF-1 外无批外缺陷。

## 端到端与接线验证义务（rule 22/23——随 Phase 2 落证）

- **端到端（rule 22）**：≥1 条 REST 提交全链（POST /jobs 合法 body→201→作业运行→stop→同宿主重新提交同 tag 旧 consumer 不复活[设计评审 F-4 订正——由红⑤ 行为断言承载]——覆盖 W-01/W-07 组合面）；≥1 条 checkpoint 提交链（participant 失败→入账→重试→升级出口或恢复——覆盖 W-03 从 finishCommit 通知到账本终局）；≥1 条数据面链路（损坏行→typed 失败→任务可见失败——覆盖 W-05，随红⑤/E2 钉承载）。
- **接线验证（rule 23）**：上界预检真生效（红①）；去抖真生效（红②）；升级出口真生效（红③——含 escalation 回调经 JobCoordinator 接线到 failJob 的调用链断言）；清理真生效（红④）；复合键→实例隔离+终态回收真生效（红⑤ + REST 工厂 per-job env 的接线核对）；typed 传播真生效（E2 泛在钉 W-05——含 AdaptingConsumer 委托层验证）。

## 评审记录

- **设计评审 R1**：待独立评审子 agent（fresh session 只读）执行——本节于评审通过后回填（plan 2535 Phase 1 第 2 项；迭代对抗性审查模板含想象性分析 + live 抽验 + 既有裁定引用逐条对照）。
