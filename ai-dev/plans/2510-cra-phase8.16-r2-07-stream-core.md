# 2510 CRA Phase 8.16 — R2-07 1.1 stream-core 批（并发正确性 + 资源包络，10 项）

> Plan Status: completed
> Draft Review: R1 agent_1ee7fa99 @ 2026-10-28（0 Blocker、4 Major、2 Minor；基线 16 组抽查 14 PASS/2 DRIFT 均为引用类）——Major-1 裁定⑦锚列载体[TASK_ASSIGNMENT_TABLE 无终态时刻列且 IF NOT EXISTS 不加列 → 定夺清理谓词锚 `assigned_at` + 终态 job 定界 join，不新增 DDL]；Major-2 裁定②三入口锚订正[三 execute 漏斗 runLocal 单点，接入单点即覆盖] + Σsubtask>poolSize fail-fast 误杀合法有界小数据作业列为必须裁决项；Major-3 裁定④补偿面扩全[活线程池+failureDetector+CheckpointCoordinator 池组+registry 四面 try/finally 全窗] + 同 jobId 守卫为既有登记[仅补孤儿窗口]；Major-4 吸收成员补 C3-008；Minor×2（enumeratorLock 锁序审计、FAM-1 许可保留线程行为钉死）——修订全量应用，转 active
> Draft Review: R2 agent_97f80f0e @ 2026-10-29（第 1 轮 FAIL[2 Major/0 Blocker/4 Minor]：Major-1 裁定⑦ join 右侧无事实源 → 改组合谓词[terminalAt 终态白名单主谓词 + assigned_at 账龄 TTL 兜底 + 活跃 job anti 排除，anti-join 活协调器表被拒]；Major-2 裁定⑧ MAX(id) bootstrap 误定性「保守回退」→ 清理位点钉死=进程内 per-topic lastSeenId 已派发推进值、bootstrap 显式排除、重启后清理保守暂停；Minor×4 全闭合[终态接线锚 OpsJobManager:302 单点+终态集 CANCEL/DRAIN/SUSPEND、②⑩交叠运维声明、face token assignment/msgqueue、锚点勘误 :224/:345-377/:354/:362]——第 2 轮复审 6/6 CLOSED，总裁定 PASS，0 立项残点[消费→已派发措辞建议归 Phase 3 owner doc]）
> Last Reviewed: 2026-10-29
> Source: roadmap 工作项 8.16（R2-07，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L158，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-07 行（L81 全文：finding ID 清单 10 项[1.1-C1-001；C4-001；C4-002；C4-009；C7-011；C8-011；C1-006；C8-003；C5-010；FAM-1] / 模块与验证命令 nop-stream `./mvnw test -pl nop-stream -am` / Owner Doc=`docs-for-ai/03-modules/nop-stream.md` + `docs-for-ai/03-modules/nop-stream-user-guide.md` / PA=无 / 依赖「无前置（可并行）；C1-006/C8-003 保留纪律对齐 R1-07（R01）；C5-010/FAM-1 对齐 R02 三范式」/ exit criteria 增补：**快照加锁；LOCAL 池互等死锁解除；OpsJobManager 提交窗口补偿；msg_queue/分配账本上限（R01）；materialization store 背压修复（FAM-1 ←C7-001/C8-001）**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.1 审计目录 `ai-dev/audits/2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency/`（C1-shared-mutable-state.md C1-001 :86 / C1-006 :239；C4-thread-pools.md C4-001 :58 / C4-002 :84 / C4-009 :294；C7-long-running.md C7-011 :363；C8-backpressure.md C8-003 :124 / C8-011 :452 / C8-001 :47；C5-memory-bounds.md C5-010 :308 / C5-001 :39；merge-adjudication.md FAM-1 族表 :30/:51/:105、P1 清单 :178；C10-test-protection.md M1–M11 映射 :53-63、C10-001~009 :127-199、C10-020 :298 及复核限定 :437），逐条回读路径见 Current Baseline；执行基线 @ `41508cded5`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.15 已 done，plan 2495–2509 均已完成或进入收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-28 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2502（R1-07，completed——**R01 族级 SSOT（关键）**：`ai-dev/design/concurrency/ledger-retention-policy.md` 为保留策略一处定稿权威（统一参数集七项 §3.1 + 触发形态菜单四态判据 §3.3 + 配置键命名 §3.2），其 §二明示「后续 13 项族成员（R2-07/R2-08/…）共享同一套保留语义」——本批 C1-006/C8-003 的保留纪律只做该文档的成员传导行，参数与形态不得批内另发明，参数修订只走该文档）；plan 2496（R1-01，completed——`ai-dev/design/concurrency/periodic-task-insulation.md` 周期任务绝缘层：本批 C1-006/C8-003 周期清理链的失败重臂与不断链保障引用其契约）；`ai-dev/design/concurrency/registry-lifecycle-contract.md`（R4-24 契约，completed 设计——REGISTRY 生命周期契约站点在 nop-message-debezium `NopStreamOffsetBackingStore`，与本批零站点重叠；本批 WI 1.1 的 `SideOutputRegistry`（C1-002，R4-01 成员）与 `ClusterRegistry`（C1-006，本批）为不同注册表，Phase 1 裁定时作「同名异实体」辨析注记防混淆）；**下游**：R4-01（roadmap 8.41，15 项流核并发正确性束）与 R4-02（8.42，7 项资源包络+FAM-2 束）均软依赖 8.16（L183/L184）——本批为同模块 P1 先行批，批末须回写就绪注记；**并行协同**：R2-08（1.2 连接器批，同属 nop-stream 门禁）——零共享站点（连接器模块 vs core/runtime），仅门禁命令同聚合
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-07 行授权列）。live 回读勘误申报：本批 10 成员站点全部位于 `nop-stream/nop-stream-core` 与 `nop-stream/nop-stream-runtime` 的非生成物 main 源码，live 回读未发现受保护面触发（不触 `nop-core/`/`nop-xlang/` 框架核心、不触 `model/*.orm.xml`[JdbcClusterRegistry 用 SQL API 非 ORM 模型改动]、不做 `_` 前缀生成物改动）；若 Phase 1 裁定引入平台公共落点（如把 poller 分面或保留清理载体上收 `nop-kernel`/`nop-api-core`）则照 2507–2509 先例在该裁定项内批内闭环申报 plan-first 后方可实施。本批不触碰 R4-01/R4-02 的 22 项批外成员本体。

## Purpose

关闭 R2-07 批的 10 项已确认发现（P1×10，全部 open）。核心结果面有四：①**流核并发正确性**——`LocalSourceCoordinator` enumerator 跨线程三态锁纪律收敛（C1-001，快照加锁）、LOCAL 执行池互等死锁解除（C4-001）、`CachingTableLookup` 刷新池有界化+去重（C4-002）、`OpsJobManager` 提交失败窗口孤儿协调器补偿（C4-009）、standby 降级僵尸第二写者消除（C7-011）、launch poller 控制面/数据面饥饿解除（C8-011）。②**R01 保留纪律成员传导**——`ClusterRegistry` 分配账本清理接线（C1-006）与 `nop_stream_msg_queue` INSERT-only 账本（C8-003）按 plan 2502 R01 族级 SSOT 落成员行，零参数发明。③**R02 三范式对齐**——`PollingJdbcMessageService` 无 LIMIT 全量物化（C5-010）与 FAM-1 materialization store 无界+overflow-bypass 背压旁路（FAM-1 主 C5-001←C7-001/C8-001）按「setLimit+fail-fast / 分批 / 列投影」三范式选型收敛，FAM-1 含全矩阵唯一假保护测试 `TestResultPartitionOverflowBypass` 同 diff 改写义务（C10-005）。批末 roadmap 8.16 → done，cra-index 十行台账收敛，R4-01/R4-02 软依赖解除。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-07 = 10 项成员（P1×10——merge-adjudication :178 P1 清单全列本批）；cra-index 台账十行独立（L135 C1-001、L136 C1-006、L137 C4-001、L138 C4-002、L139 C4-009、L140 C5-010、L141 C7-011、L142 C8-003、L143 C8-011、L144 FAM-1，均 open、批次列均=R2-07）；roadmap 8.16 行 Status=todo。前置：无前置（可并行），plan 2495–2509 前序批均收口——基线门禁已满足。
- **吸收成员（随主条目同 diff，不单列工单）**：C4-001←C8-013；C4-002←C3-002/C8-014；C8-011←C4-004/C3-008；C5-010←C8-002；C8-003←C7-003；FAM-1←C7-001（并入主 C5-001）+C8-001（族成员）；C1-001←C2-001/C3-001（同站点锁纪律面）；C1-006←C7-002（记账侧）。cra-index 十行回填时以主 ID 收敛、吸收成员括注，不整行多 ID 重复立项。
- **C10 验收前置入批集（勘误 4 定集）**：C10-001（C1-001/C2-001/C3-001 面）、C10-002（C4-001/C8-013 面）、C10-003（C4-002/C3-002/C8-014 面）、C10-004（C4-009 面）、C10-005（C5-001/C7-001/C8-001 面，**假保护改写义务**——`TestResultPartitionOverflowBypass` 现把 bypass 缺陷行为钉为交付语义，修复会使其红，必须同 diff 改写，C10-test-protection M7 判「全矩阵唯一假保护」）、C10-006（C5-010/C8-002 面）、C10-007（C8-003/C7-003 面）、C10-008（C8-011/C4-004 面）、C10-009（C7-011 面）、**C10-020（C1-006/C7-002 面——注意非 C10-001~009 顺序号；复核附限定 :437/:410：接线面「正确形态清理」无测试红即缺保护成立，但 naive 全清会红 `TestJobCoordinatorPerTaskFailure:111-126` 的 +1 pin 与 attempt 单调保留 pin，接线设计须终态 job 定界）**。共 10 条，随本体同 diff 落地（E2）。

### 10 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `41508cded5` 2026-10-28 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.1-C1-001（P1；←C2-001/C3-001） | `LocalSourceCoordinator.java`（nop-stream-core `source/coordinator`） | `registerReader` 内 `enumerator.addReader(subtaskIndex)` :186 **锁外**；`ReaderChannel.requestSplits` → `owner.enumerator.handleSplitRequest(...)` :260 **无锁**；对照 `snapshotState` :297 / `close` :334 均 `synchronized(enumeratorLock)`——三态锁纪律并存 | reader 任务线程无锁直调 enumerator（addReader/handleSplitRequest 裸奔），与声明单线程契约矛盾：并行 source 启动即双份分配/表份额丢失/快照撕裂 | 裁定①：快照加锁——预设默认分支 = 两入口收敛进 `enumeratorLock`（最小修复）；备选「每 coordinator 单线程 mailbox 漏斗」须论证锁方案不满足后方可采 |
| 2 | 1.1-C4-001（P1；←C8-013） | `TaskExecutor.java` :116 + `ResultPartition.java` :359/:373 + `StreamExecutionEnvironment.java` :574（nop-stream-core `execution`） | `newFixedThreadPool(poolSize=CPU 核数)` :116；`queue.put(element)` 阻塞语义 :359/:373；`awaitCompletion()` 无超时重载 :574 | LOCAL 拓扑子任务总数>核数且任一边达队列容量（默认 1024）→ 池内生产者/消费者互等死锁，主线程永久阻塞，无守卫/超时/恢复 | 裁定②：死锁解除——预设默认分支 = 提交前守卫（任务总数>poolSize → typed fail-fast）+ awaitCompletion 有界等待接入三入口（runLocal/checkpointed/savepoint）；run/饱和语义一次定夺 |
| 3 | 1.1-C4-002（P1；←C3-002/C8-014） | `CachingTableLookup.java`（nop-stream-core `connector/lookup`） | 后台刷新 `scheduler.scheduleWithFixedDelay(this::scanAndDispatch, ...)` :205（审计区间 :193-253）：刷新队列无界 + 无 in-flight 去重 | delegate 挂死时每扫描轮为全部老化 key 重复入队 → 无界增长（OOM 候选）+ 降级 DB 重复探测 | 裁定③：刷新池有界队列 + in-flight key 去重（同一 key 未完成不重复入队）+ 慢 delegate 降级行为显式化（fail-loud 或丢弃+计数，禁静默积压） |
| 4 | 1.1-C4-009（P1） | `OpsJobManager.java`（nop-stream-runtime `ops`） | `synchronized submit` :124；提交序列内失败窗口（审计 :188-194 活线程池已启动未登记、:424-433 清理缺位） | submit 中途失败 → 孤儿 coordinator（活线程池泄漏+治理不可见）；同 jobId 重提交 → 双活协调器 | 裁定④：提交窗口补偿——失败路径回滚（shutdown 已启动执行器 + registry 摘除）+ 同 jobId 幂等/拒绝守卫；与 HA fencing epoch 面零重叠声明 |
| 5 | 1.1-C7-011（P1） | `JobCoordinator.java` :2329 + `CheckpointCoordinator.java` :948/:1004-1013（nop-stream-runtime `coordinator`/`checkpoint`） | `deactivateToStandby` :2329 仅翻 `active` 标志（:2346 注释自认「in-flight 不 commit 靠 collectAck 门控 :1016」）；`onCompletePersistSuccess` :948 完成/提交扇出路径（:1004-1013 commit executor）无 active/epoch 复核 | standby 降级不取消在途 checkpoint persist——persist 完成后仍扇出 commit，僵尸 standby 成为 checkpoint 持久平面第二写者 | 裁定⑤：降级取消在途——预设默认分支 = `deactivateToStandby` 触发在途 checkpoint abort/取消 + `onCompletePersistSuccess` 与 commit 扇出入口补 active/fencing 复核；依赖既有 collectAck 门控的关系成文 |
| 6 | 1.1-C8-011（P1；←C4-004） | `PollingJdbcMessageService.java`（nop-stream-runtime `launch`） | `newScheduledThreadPool(2, ...)` :96；每订阅 `scheduleWithFixedDelay(this::poll,...)` :293 共享 2 线程；`poll()` :305 单 poll 内联串行派发全部物化行（:311-314 查询、:383-390 物化） | 慢进度数据面积压以「每行 ≤10s × 无界批量」垄断全部 2 个派发线程——控制面（barrier/deploy/cancel/上报）无界期饥饿；bounded-offer 裁定只覆盖零进度情形 | 裁定⑥：poller 分面——预设默认分支 = 控制面/数据面线程池分离 + 单 poll 有界批量（与 C5-010 LIMIT 同参数协同）；备选单池优先级化须论证分离不可行后方可采 |
| 7 | 1.1-C1-006（P1；←C7-002） | `InMemoryClusterRegistry.java` :44/:151-155/:190 + `JdbcClusterRegistry.java` :275（nop-stream-runtime `cluster`） | `taskAssignmentHistory` :44 append-only（:151-155 每次 assign 追加永不裁剪）；`removeTaskAssignment` :190 生产代码零调用方；JDBC DELETE :275 存在未接线 | 任务分配账本只增不删：内存 map 随进程寿命、JDBC 分配表跨重启单调累积（作业数×槽位×尝试数无上界） | 裁定⑦（**R01 对齐显式裁定项**）：按 `ledger-retention-policy.md` 成员传导——终态 job 清理接线 `removeTaskAssignment`（C10-020 限定：终态定界，非 naive 全清）+ JDBC 平面形态 A 周期清理（七项参数/键名/锚列引 SSOT §3.1/§3.2，批内零参数发明） |
| 8 | 1.1-C8-003（P1；←C7-003） | `PollingJdbcMessageService.java` :177/:151 + `RemoteGraphExecutionPlanBuilder.java` :224/:668（runtime `launch` + `transport`） | `send` :177 INSERT-only 每数据行一记录；:151 清理仅 test helper；门控探测 :224/:668-674——launch 后端不实现 `ChannelBacklogProbe` → WI6 生产者门控恒不可达 | `nop_stream_msg_queue` INSERT-only 无界账本（无 TTL/清理路径）+ 门控能力缺失双面 | 裁定⑧（**R01 对齐显式裁定项**）：msg_queue 保留清理按 R01 形态 A（参数引 SSOT）+ 后端实现 `ChannelBacklogProbe`（owner doc :298 既有 `-Dnop.stream.data.db.backlog.limit` 消费面已在，缺的是后端能力半边）——两半边一次裁决禁各修一半 |
| 9 | 1.1-C5-010（P1；←C8-002） | `PollingJdbcMessageService.java` :311-314/:383-390（runtime `launch`） | `SELECT id, payload ... WHERE topic=? AND id>? ORDER BY id` :311-314 **无 LIMIT**；:383-390 全部行（含完整 byte[] payload）先物化 ArrayList 再逐条分发 | 单次 poll 峰值内存线性于 DB 积压（全量物化 backlog OOM 面）；该后端无 backlog 探测能力、WI6 门控不可达（后者归裁定⑧） | 裁定⑨（**R02 三范式对齐显式裁定项**）：读路径三范式选型（`inventory-ledgers.md` #17：setLimit+fail-fast / 分批 / 列投影）——预设默认分支 = setLimit 有界拾取 + 分批循环（cursor 推进天然支持分批），列投影面（payload 必需）不适用声明；limit 参数与溢出行为 fail-fast 成文 |
| 10 | 1.1-FAM-1（P1；主 C5-001←C7-001、族成员 C8-001） | `InMemoryMaterializationPoint.java` :41 + `ResultPartition.java` :346-376（nop-stream-core `execution`/`materialization`） | `store = new ArrayList<>()` :41 全量驻留无截断/淘汰；`enqueueWithBackpressure` 池+materialization 象限 `queue.offer` 失败（:351/:370）改道 store-only——IBufferPool 上界被架空、活动运行期记录静默不可见 | materialization 边 overflow-bypass：store 无界增长（线性于吞吐×时长→OOM）+ 队列满即释放许可改道 store-only（背压旁路+运行期记录对消费者静默跳过） | 裁定⑩（**R02 三范式对齐显式裁定项**）：FAM-1 族修复一次裁定（merge-adjudication :30 三要求）——截断/淘汰契约 + 许可保留或 typed 溢出信号（禁静默 bypass）+ 运行期可见性；**假保护测试 `TestResultPartitionOverflowBypass` 同 diff 改写义务（C10-005）随本裁定成文**；C9-007（持锁 O(n) 拷贝，R4+）不承接、本批修复自然缩小 n 注记 |

### 平台设施与测试资产盘点（live 实读 @ `41508cded5`）

- **R01 族级 SSOT**（`ai-dev/design/concurrency/ledger-retention-policy.md`，plan 2502 产出）：统一参数集七项（retention-days 缺省 30 / cleanup-interval 缺省 1h 下限 60s / delete-batch-size 缺省 1000 / DB 表不设行上限逐出唯一判据 TTL / fail-fast 两层包络 / 键名 `nop.<module>.ledger[.<face>].*` / 时间锚列逐表显式）+ 形态 A 周期清理为机器速率写入面必须 + 周期链挂 `@PostConstruct`/`LifeCycleSupport.doStart` 且回归断言调度真实调用（防 1.6-C7-005 空壳先例）——C1-006/C8-003 传导只落成员行，禁另发明。
- **R02 三范式**（`inventory-ledgers.md` #17）：无界物化/无上界读族纪律 = setLimit+fail-fast / 分批 / 列投影 三选一按站点形态裁——C5-010/FAM-1 为该族 1.1 成员，Phase 1 选型一次定夺。
- **既有测试资产（回归门禁与测试基建）**：`TestSourceApiRouting`/`TestSourceEnumeratorManifestWiring`（C1-001 面，现全单线程）、`TestTaskExecutor`（C4-001 面无 Σsubtask>cores 拓扑）、`TestCachingTableLookup`（并发上限双断言在位=回退钉；积压面无断言）、`TestOpsRestLifecycleE2E`/`TestGovernanceSweepContainment`（C4-009 全成功路径）、`TestJobCoordinatorRecoveryConcurrency`/`TestJobCoordinatorStandbyStateMachine`（C7-011 只钉拒绝新 trigger）、`TestInMemoryClusterRegistryAttemptHistory`（C1-006 API 面 pin=会红资产）+ `TestJobCoordinatorPerTaskFailure:111-126`（history +1 pin——naive 全清会红，接线设计约束）、`TestMultiJvmExactlyOnceRecovery`（C5-010/C8-003 真实传输链）、`TestResultPartitionDeadlock`/`TestResultPartitionOverflowBypass`（后者**假保护**=C10-005 改写本体）、`TestLaunchMainScopeWiring`（C8-011 接线面）。
- **owner doc 现状**：`docs-for-ai/03-modules/nop-stream.md` 已有治理配置节（:225-226 两条 ops retention-minutes）、背压门控节（:298 backlog.limit 消费面）、观察面（:325 `nop_stream_msg_queue` INSERT-only 对照代理表述——C8-003 修复后须同步）；`nop-stream-user-guide.md` 存在，为本批 owner doc 第二落点。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live，零实质漂移）**：C1-001 :186/:260/:297/:334 ✓；C4-001 :116/:359/:373/:574 ✓；C4-002 :205（审计区间 :193-253 ✓）；C4-009 :124 ✓（审计细分 :188-194/:424-433 为区间引用，执行时以 live 重读为准）；C7-011 :2329/:2346/:1016/:948/:1004-1013 ✓；C8-011 :96/:293/:305 ✓；C1-006 :44/:151-155/:190（live removeTaskAssignment :190-191，审计 :143-169 为区间）/:275 ✓；C8-003 :177/:151 ✓/:224/:668-674 ✓；C5-010 :311-314（live SQL 行 :312）/ :383-390 ✓；FAM-1 :41/:346-376/:351/:370 ✓。
2. **对照面路径勘误（C8-003）**：审计引 `RemoteGraphExecutionPlanBuilder.java:224-237,668-674` 未带目录且上下文易误读为 `launch/`——live 实际位于 `nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java`（行号 :224/:668-674 本身吻合）。以本勘误路径为准。
3. **C10 归属勘误**：C1-006 的验收前置为 **C10-020**（C10-test-protection :298），非 C10-001~009 顺序号位；且 C10-020 复核附限定（:437/:410）——「接线缺失」判定成立但 naive 全清接线会红既有 +1/单调 pin，接线必须终态 job 定界。入批 C10 集 = C10-001~009 + C10-020 共 10 条。
4. **吸收成员对齐**：cra-index 十行括注的 ← 吸收成员（C8-013/C3-002/C8-014/C4-004/C3-008/C8-002/C7-003/C7-001/C8-001/C2-001/C3-001/C7-002）[draft review Major-4 补 C3-008]随主条目同 diff 收敛，Phase 4 回填以主 ID 行收敛+括注，不单独立行。
5. **任务输入「增补开头被截断」核对**：batch-proposal L81 增补列实为完整五段（快照加锁；LOCAL 池互等死锁解除；OpsJobManager 提交窗口补偿；msg_queue/分配账本上限（R01）；materialization store 背压修复（FAM-1 ←C7-001/C8-001）），已全文采纳入 Closure Gates；无截断丢失项。

## Goals

- Phase 1 设计裁定文档一次裁决十项裁定（独立 draft review 通过），无临场发明空间：①C1-001 快照加锁口径；②C4-001 LOCAL 死锁解除形态；③C4-002 刷新池有界+去重；④C4-009 提交窗口补偿；⑤C7-011 降级取消在途 persist；⑥C8-011 poller 分面；⑦C1-006 R01 成员传导（终态清理接线+JDBC 周期清理）；⑧C8-003 R01 成员传导+ChannelBacklogProbe 补齐（两半边一次裁决）；⑨C5-010 R02 三范式选型（setLimit+分批预设）；⑩FAM-1 族一次裁决（淘汰契约+许可/typed 溢出+可见性+假保护改写）+ 测试缝与零回退钉清单。
- 10 项成员全部修复并各带回归测试（rule 25）；C10 十条验收前置随本体同 diff 落地（含 C10-005 假保护同 diff 改写）。
- 关键工单先红后绿或变异/回退论证留痕（E2）：C1-001（双份分配/撕裂红）、C4-001（互等死锁红）、C4-002（无界队列红）、C4-009（孤儿/双活红）、C7-011（第二写者红）、C8-011（控制面饥饿红）、C8-003/C1-006（无界账本红）、C5-010（全量物化红）、FAM-1（bypass 背压旁路红——含假保护测试改写前后对照）。
- R01/R02 对齐纪律守约：C1-006/C8-003 保留参数/形态/键名全部引用 `ledger-retention-policy.md`，批内零参数发明；C5-010/FAM-1 三范式选型在裁定⑨⑩成文并注明与族内其他成员（R3-03 W-04、R3-09 W-01 等）同口径。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-stream.md`（保留策略成员行、门控能力、背压契约、观察面同步）+ `docs-for-ai/03-modules/nop-stream-user-guide.md`（运维可见行为变化）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-01 成员**：G01 的 15 项（1.1-C1-003/C1-004/C1-005/C1-007/C1-008/C2-003/C2-004/C2-005/C3-009/C4-005/C4-006/C4-012/C7-005/C7-007/C7-012）及其 C10-010~016/023——软依赖本批，本批零承接零顺手修。
- **不做 R4-02 成员**：G02+G03 的 7 项（1.1-C5-004/C5-005/C5-006/C5-012/C8-005/C8-009/FAM-2）及其 C10-017~019/021/022——同上。
- **不做 R2-08（1.2 连接器批）成员**：MA-05 保留策略等归 R2-08 与 R1-07 同设计，本批零触碰 nop-stream-connectors。
- **不做 `SideOutputRegistry`（1.1-C1-002，R4-01）**：与本批 C1-006 `ClusterRegistry` 同名异实体，辨析注记归裁定⑦，零产品改动。
- 不触碰 `model/*.orm.xml`、`_` 前缀生成物、nop-message-debezium REGISTRY（R4-24 契约域）、R4+ 批次工单；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-stream/nop-stream-core`：`source/coordinator/LocalSourceCoordinator`（C1-001）、`execution/task/TaskExecutor` + `execution/ResultPartition` + `environment/StreamExecutionEnvironment`（C4-001/FAM-1）、`connector/lookup/CachingTableLookup`（C4-002）、`execution/materialization/InMemoryMaterializationPoint`（FAM-1）+ 测试。
- `nop-stream/nop-stream-runtime`：`ops/OpsJobManager`（C4-009）、`coordinator/JobCoordinator` + `checkpoint/CheckpointCoordinator`（C7-011）、`launch/PollingJdbcMessageService`（C8-011/C8-003/C5-010）、`cluster/InMemoryClusterRegistry` + `JdbcClusterRegistry`（C1-006）+ `transport/RemoteGraphExecutionPlanBuilder`（C8-003 门控半边）+ 测试。
- 设计文档：`ai-dev/design/concurrency/stream-core-concurrency-and-resource-envelope.md`（裁定①–⑩，最终状态写法）；R01 SSOT `ledger-retention-policy.md` 增补两条成员传导行（⑦⑧——仅成员行追加，不改七项参数）；Owner doc：`nop-stream.md`、`nop-stream-user-guide.md`、`source-anchors.md`。

### Out Of Scope

- Non-Goals 所列全部项；R4-01/R4-02/R2-08 及后续批次工单；C9-007（物化锁争用，FAM-1 邻接面，仅注记）；nop-kernel/nop-api-core 平台落点（除非裁定触发 PA 申报流程）。

## Execution Plan

### Phase 1 - stream-core 并发正确性与资源包络设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/stream-core-concurrency-and-resource-envelope.md`（新建）；`ai-dev/design/concurrency/ledger-retention-policy.md`（成员传导行追加）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下十项裁定，每条唯一预设默认分支：
  - [x] **裁定① C1-001 快照加锁**：预设默认分支 = `registerReader` 的 `addReader`（:186）移入 `enumeratorLock` + `ReaderChannel.requestSplits`（:260）包同一锁（锁内禁未知代码/阻塞调用声明）；备选单线程 mailbox 漏斗须论证锁方案不足（如 enumerator 实现内部阻塞）后方可采；快照原子性（snapshotState 与两入口互斥）达成声明。；**锁序审计（draft review Minor-1）**：enumeratorLock 不得嵌套于调用方已持锁内——Phase 1 核验 registerReader/requestSplits 调用方锁序（与 snapshotState 所在 coordinator 线程的锁序对）
  - [x] **裁定② C4-001 LOCAL 死锁解除**：预设默认分支 = 双守卫——提交前 `Σsubtask > poolSize` typed fail-fast（`NopException`+错误码）+ `awaitCompletion` 有界等待接入 `StreamExecutionEnvironment` 三入口（:543/:275/:391 形态）；超时后行为（abort 语义 vs 快速失败）一次定夺；与 `TestResultPartitionDeadlock` 既有负向窗口零冲突核验。；**入口锚订正（draft review Major-2）**：execute/execute(jobName)/executeWithSavepoint 三入口全部漏斗进 `runLocal` 单点（:543/:574 唯一 awaitCompletion）——有界等待接入 runLocal 单点即覆盖三入口；**误杀面裁定项（必须裁决）**：`Σsubtask > poolSize` typed fail-fast 会误杀「队列不填满即跑完」的合法有界小数据作业——备选（LOCAL 池扩容至 Σsubtask / 无界链表队列 / 仅阻塞边守卫）与 fail-fast 的取舍在 Phase 1 设计一次定夺，行为回归面评估成文
  - [x] **裁定③ C4-002 刷新池有界+去重**：预设默认分支 = 刷新派发有界队列（容量语义与既有 maxEntries 关系成文）+ in-flight key 集去重（未完成 key 不重复入队）+ delegate 挂死降级显式化（丢弃+计数指标，禁无界积压）；与既有并发上限双断言（C10-003 指认的强保护面）零回退。
  - [x] **裁定④ C4-009 提交窗口补偿**：预设默认分支 = submit 序列失败路径补偿（已启动执行器 shutdown + registry 条目摘除 + 类型化上报）+ 同 jobId 二次提交守卫（拒绝 vs 幂等返回一次定夺）；与 HA fencing epoch 机制零重叠声明。；**补偿面扩全（draft review Major-3 定夺）**：泄漏面 = 活线程池 + failureDetector 永生 + CheckpointCoordinator 池组 + registry 残行（merge-adjudication 原文）——补偿清单覆盖 try/finally 全窗四面逐面留证，非仅执行器+registry；**同 jobId 守卫为既有登记**（:129 `ERR_STREAM_JOB_ALREADY_HOSTED` 已存在）——本批仅补孤儿窗口（失败未 put）后的重提交防护，非重复立项
  - [x] **裁定⑤ C7-011 降级取消在途**：预设默认分支 = `deactivateToStandby` 主动 abort 在途 pending checkpoint + `onCompletePersistSuccess`（:948）与 commit 扇出（:1004-1013）入口 active/epoch 复核（复核失败 = 丢弃不 commit + 计数）；与 collectAck 门控（:1016）+ 新 leader abort 的既有三层防线关系成文（补第四层而非重写）。
  - [x] **裁定⑥ C8-011 poller 分面**：预设默认分支 = 控制面/数据面线程资源分离（控制面不被数据面 poll 占满）+ 单 poll 有界批量（与裁定⑨ LIMIT 同参数协同、批量上限缺省值定夺）；bounded-offer 既有裁定（零进度情形）维持不变注记。
  - [x] **裁定⑦ C1-006 R01 成员传导**：终态 job 路径接线 `removeTaskAssignment`（InMemory+JDBC 双实现；C10-020 限定——终态 job 定界清理，活跃 job 追加语义零改动，防 +1 pin 回退）+ JDBC 分配表形态 A 周期清理（`ledger-retention-policy.md` §3.1 七项参数/§3.2 键名/锚列逐表落名[分配表锚列=终态迁移时刻]）——**成员传导行追加至 R01 SSOT §3.4 传导表，本文档不复制参数值（link, don't copy）**；`SideOutputRegistry` 同名异实体辨析注记。；**锚列载体（draft review Major-1 定夺）**：TASK_ASSIGNMENT_TABLE 现无终态迁移时刻列且 `CREATE TABLE IF NOT EXISTS` 不为存量部署加列——默认分支 = **清理谓词锚 `assigned_at` + 终态 job 定界（关联 job 终态记录 join 定界）**，不新增 DDL 列；终态时刻新列若确需须先裁定 DDL migration 方案并在 plan 登记后方可采
  - [x] **裁定⑧ C8-003 R01 成员传导 + 门控补齐（两半边禁各修一半）**：(a) `nop_stream_msg_queue` 保留清理按 R01 形态 A（锚列=INSERT 时刻/消费位点谓词关系一次定夺——queue 语义为消费后可删 vs TTL 纯账龄，须显式裁定并防误删未消费行）；(b) 后端实现 `ChannelBacklogProbe`（backlog 口径=未消费行数）使 WI6 门控（`RemoteGraphExecutionPlanBuilder` :224/:668 探测面零改动）对 DB 通道真实可达；owner doc :298/:325 消费面表述同步。
  - [x] **裁定⑨ C5-010 R02 三范式选型**：预设默认分支 = **setLimit 有界拾取 + 分批循环**（单 poll LIMIT=批量上限与裁定⑥共享；cursor 推进分批直到无剩余或让度）；fail-fast 边（limit≤0 装配期拒绝）+ 与 WI6 门控组合行为（limit 与 backlog limit 的关系）成文；列投影不适用声明（payload 全列必需）。
  - [x] **裁定⑩ FAM-1 族一次裁决**：(a) store 截断/淘汰契约（上界来源=bufferPool 容量族 or 独立配置，一次定夺）；(b) overflow 语义——预设默认分支 = **许可保留**（queue 满不释放 permit、store 仅作 checkpoint 语义记录，背压回归）备选 typed 溢出信号（fail-loud onError），取舍定夺；禁静默 bypass 维持；(c) 运行期记录可见性恢复（消费者可读 store 记录）；(d) **`TestResultPartitionOverflowBypass` 假保护同 diff 改写方案**（新断言=修复后语义，C10-005）；(e) C9-007 邻接注记（本批缩小 n，本体零承接）。；**许可保留线程行为（draft review Minor-2 钉死）**：保留 permit 后 = acquire 恒 `put`（与非物化路径同形），store 仅 checkpoint 语义记录——不引入自旋/阻塞变体
  - [x] **裁定⑪ 测试缝与零回退钉清单**：测试缝 = 多线程交叠驱动器（C1-001 两线程 registerReader/requestSplits vs snapshotState 交错）、Σsubtask>cores 拓扑构造器（C4-001）、可控慢 delegate 桩（C4-002）、submit 注错桩（C4-009）、在途 persist 竞速缝（C7-011）、慢消费数据面桩+控制面时延探针（C8-011）、终态 job 接线断言缝（C1-006）、积压注入缝（C5-010/C8-003）、假保护改写对照（FAM-1）；零回退钉 = `TestCachingTableLookup` 并发上限双断言、`TestJobCoordinatorPerTaskFailure` +1 pin（C1-006 接线约束）、`TestInMemoryClusterRegistryAttemptHistory`、`TestResultPartitionDeadlock`、`TestMultiJvmExactlyOnceRecovery`、`TestLaunchMainScopeWiring`、`TestJobCoordinatorRecoveryConcurrency` 全列。
- [x] `ledger-retention-policy.md` 成员传导行追加（裁定⑦⑧）——仅追加成员行，不改七项参数与形态判据；若执行中发现参数确需修订，修订只走该文档并在本 plan 登记
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑪全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定⑦⑧含 R01 SSOT 传导行落账证据（SSOT 文件 diff 仅为成员行追加）；裁定⑨⑩含 R02 三范式选型与族内同口径声明；裁定⑩含假保护改写方案；裁定⑪含零回退钉全列
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 10 成员实现 + 回归测试（stream-core 组 + stream-runtime 组）

Status: done
Targets: `nop-stream/nop-stream-core/src/`（source/coordinator、execution、connector/lookup、environment）与 `nop-stream/nop-stream-runtime/src/`（ops、coordinator、checkpoint、launch、cluster、transport）——各自 main + test 树

- Item Types: `Fix`（10 项均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 十条验收前置）

- [x] 组 core（nop-stream-core）：C1-001（快照加锁）、C4-001（死锁解除守卫）、C4-002（刷新池有界+去重）、FAM-1（store 上界+overflow 语义+可见性）——全部按裁定①②③⑩实施
- [x] 组 runtime（nop-stream-runtime）：C4-009（提交窗口补偿）、C7-011（降级取消在途）、C8-011（poller 分面）、C1-006（清理接线+周期清理）、C8-003（msg_queue 保留+ChannelBacklogProbe）、C5-010（LIMIT+分批）——全部按裁定④–⑨实施
- [x] 回归测试（rule 25 显式覆盖 + C10 十条同 diff，先红后绿或变异/回退论证留痕）：
  - [x] C1-001/C10-001 红①：并行度≥2 交叠驱动（registerReader×2 + snapshotState 并发）——修复前 enumerator 内部状态竞争（双份分配/计数错乱断言红）→ 修复后快照与分配互斥、零双份零丢失
  - [x] C4-001/C10-002 红②：Σsubtask>poolSize + 阻塞 sink 拓扑——修复前 `awaitCompletion` 永久挂死（测试超时红）→ 修复后提交期 typed fail-fast（或有界等待+abort，按裁定②断言）
  - [x] C4-002/C10-003 红③：慢 delegate 桩 + 多扫描轮——修复前队列深度随轮数线性增长红 → 修复后深度有界 + 同 key 无重复在途（in-flight 去重断言）
  - [x] C4-009/C10-004 红④：submit 中途注错——修复前线程池存活泄漏+registry 残留红 → 修复后零孤儿（池终止+registry 空+重提交行为按裁定④断言）
  - [x] C7-011/C10-009 红⑤：standby 降级×在途 persist 竞速——修复前 persist 完成后 commit 扇出仍发生（第二写者断言红）→ 修复后降级后零 commit（active/epoch 复核断言）
  - [x] C8-011/C10-008 红⑥：数据面积压桩——修复前控制面消息时延无界（探针断言红）→ 修复后控制面时延有界（分面+批量上界断言）
  - [x] C1-006/C10-020 红⑦：多尝试 job 终态——修复前账本条目随尝试单调存留红 → 修复后终态清理生效且活跃 job 追加语义零改动（+1 pin 与单调 pin 保持绿的对照断言）
  - [x] C8-003/C10-007 红⑧：长跑写入——修复前表行数无界红 → 修复后保留清理生效（形态 A 调度真实调用断言，rule 23 接线）+ `ChannelBacklogProbe` 门控从「启动声明禁用」变「真实可达」（探测返回 backlog>0 断言）
  - [x] C5-010/C10-006 红⑨：积压>limit 注入——修复前单 poll 物化量=全积压红 → 修复后单 poll 物化 ≤ limit 且分批推进至清空（cursor 正确性断言）
  - [x] FAM-1/C10-005 红⑩：queue 满+materialization 象限——修复前 permit 被释放、store 无界、记录静默跳过（`TestResultPartitionOverflowBypass` 旧断言形态）→ 修复后背压回归/typed 溢出（按裁定⑩）+ store 有界 + 记录可见；**假保护测试同 diff 改写前后红绿对照留痕**
- [x] **端到端验证**（rule 22）：≥1 条 LOCAL `env.execute()` 全链测试（`addSource` → 算子 → sink，含 materialization 边——出口点=sink 收到元素序列 exactly-once 且作业正常终止，覆盖裁定②⑩组合行为）；≥1 条 launch/multijvm 链路测试（`PollingJdbcMessageService` 数据面 → 控制面消息 → checkpoint 完整走通，覆盖裁定⑥⑧⑨与 C7-011 降级路径）
- [x] **接线验证**（rule 23）：`removeTaskAssignment` 真实被终态路径调用断言（非 import 自证）；`ChannelBacklogProbe` 真实被 WI6 门控探测调用断言；形态 A 清理调度真实挂接生命周期钩子断言（R01 裁定②防空壳条款）
- [x] **无静默跳过**（rule 24）：C4-001 fail-fast 而非静默排队；C4-002 降级显式计数而非吞掉；FAM-1 溢出语义显式（许可保留或 typed 信号）而非静默 bypass；C4-009 补偿失败显式上报；新增公共分支无空方法体/TODO
- [x] **提交纪律**：设计文档（+R01 SSOT 传导行）先行单独提交；按模块分组提交——**组 core**（C1-001/C4-001/C4-002/FAM-1+假保护改写）、**组 runtime**（其余六成员），C10 测试与对应本体同一 commit；两组无耦合时可独立提交序列
- [x] `ai-dev/logs/` 对应日期条目已更新
- 偏差：裁定⑩(a) store 缺省上界经组合门禁 live 证据（TestRegionRestartInternalEdgeE2E N1 保证）修订为未配置不设上界+WARN 档位、显式配置启用 fail-loud；理由与语义见设计文档 §3.10(a) 修订

Exit Criteria:

- [x] 10 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 十条同 diff 落地（含 C10-005 假保护改写）
- [x] 必红证据（E2）：红①–红⑩全部先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [x] 零回退实证：`TestCachingTableLookup`、`TestJobCoordinatorPerTaskFailure`、`TestInMemoryClusterRegistryAttemptHistory`、`TestResultPartitionDeadlock`、`TestMultiJvmExactlyOnceRecovery`、`TestLaunchMainScopeWiring`、`TestJobCoordinatorRecoveryConcurrency` 全绿（钉零回改项逐条 git diff 为空核对）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [x] `./mvnw test -pl nop-stream -am` BUILD SUCCESS；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: done
Targets: `nop-stream` 聚合全量测试；`docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/03-modules/nop-stream-user-guide.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [x] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2509 Phase 3 先例）
- [x] 批行验证命令（E3）：`./mvnw test -pl nop-stream -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`、`_` 前缀生成物、nop-stream-connectors（R2-08 域）、R4-01/R4-02 批外成员站点（`git diff --name-only` 核对登记）
- [x] Owner doc 更新：`nop-stream.md`——保留策略成员行（C1-006/C8-003，注明以 `ledger-retention-policy.md` 为准的 ≤3 行摘要）、backlog 门控节（:298 DB 通道能力变化）、观察面（:325 INSERT-only 表述同步）、并发契约节（ enumerator 锁纪律/LOCAL 池守卫/降级语义，按裁定①②⑤落笔）+ 源码锚点更新；`nop-stream-user-guide.md` 运维可见行为变化（fail-fast 守卫、保留配置键、门控可达）；`source-anchors.md` 新增锚点（按实际）
- [x] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [x] nop-stream.md 保留/门控/观察/契约节更新 + nop-stream-user-guide.md 行为同步 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [x] 零触碰确认登记（orm.xml/生成物/connectors/批外成员站点）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: done
Targets: 本 plan、roadmap 8.16 行、cra-index 台账、R4-01/R4-02 就绪注记

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（锁收敛/守卫/补偿/复核/分面/接线清理/门控探测/分批运行时真实生效、无空方法体/静默吞异常）
- [x] cra-index 台账回填：L135–L144 十行状态收敛（open → fixed，注记 plan 2510；吸收成员括注随主行）；C10 十条收敛注记（C10-005 注明假保护改写）；roadmap 8.16 行 Status → done；R4-01（L183）/R4-02（L184）软依赖 8.16 的就绪注记（沿 2509 对 R4-16 先例）
- [x] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [x] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [x] cra-index 10 行收敛 + C10 十条注记、roadmap 8.16 = done、R4-01/R4-02 就绪注记完成
- [x] 双门禁退出码 0 登记在案
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 10 项成员全部修复：C1-001（快照加锁）、C4-001（死锁解除）、C4-002（有界+去重）、C4-009（提交窗口补偿）、C7-011（降级取消在途）、C8-011（poller 分面）、C1-006（清理接线）、C8-003（保留+门控补齐）、C5-010（LIMIT+分批）、FAM-1（store 上界+背压语义+可见性）
- [x] batch-proposal R2-07 行 exit criteria 增补五条逐条达成并留证：①**快照加锁**（enumerator 三入口同锁断言）；②**LOCAL 池互等死锁解除**（Σsubtask>cores 拓扑 fail-fast/有界等待断言）；③**OpsJobManager 提交窗口补偿**（注错零孤儿断言）；④**msg_queue/分配账本上限（R01）**（两账本保留清理按 SSOT 落地+调度接线断言+SSOT 成员传导行在案）；⑤**materialization store 背压修复（FAM-1）**（bypass 消除断言+假保护测试改写对照）
- [x] 对齐纪律守约：C1-006/C8-003 保留参数/形态/键名与 `ledger-retention-policy.md` 零分叉（SSOT diff 仅为成员行追加）；C5-010/FAM-1 R02 三范式选型成文且与族内成员同口径注记在案
- [x] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑩均有先红后绿或变异/回退论证（E2）；C10 十条同 diff 落地
- [x] 设计文档裁定①–⑪成文且经独立 draft review；既有钉（TestCachingTableLookup/PerTaskFailure +1 pin/AttemptHistory/ResultPartitionDeadlock/MultiJvm/LaunchMainScopeWiring/RecoveryConcurrency）零回退实证
- [x] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-stream -am` BUILD SUCCESS（E1/E3）
- [x] Owner doc 更新完成：nop-stream.md + nop-stream-user-guide.md + source-anchors.md（E5）
- [x] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [x] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）锁收敛/守卫/补偿/复核/分面/清理接线/门控探测/分批运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile` + `./mvnw test`（nop-stream 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] cra-index 台账 10 行收敛 + roadmap 8.16 → done + R4-01/R4-02 就绪注记（E6）

## Deferred But Adjudicated

### R4-01 成员（G01 流核并发正确性束 15 项：1.1-C1-003/C1-004/C1-005/C1-007/C1-008/C2-003/C2-004/C2-005/C3-009/C4-005/C4-006/C4-012/C7-005/C7-007/C7-012）及其 C10-010~016/023

- Classification: `out-of-scope improvement`（batch-proposal R4-01 行显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-01 批 plan（roadmap 8.41，软依赖 8.16）
- Why Not Blocking Closure: 判级对齐零重复——cra-index 台账中该 15 项批次列=R4-01（P2-G01 行 :385）；本批已收敛同单元 P1 主体，R4-01 实施以本批后的 live 形态为基线。

### R4-02 成员（G02+G03 资源包络+FAM-2 束 7 项：1.1-C5-004/C5-005/C5-006/C5-012/C8-005/C8-009/FAM-2）及其 C10-017~019/021/022

- Classification: `out-of-scope improvement`（batch-proposal R4-02 行显式安排）；Successor Required: `yes`；Successor Path: R4-02 批 plan（roadmap 8.42，软依赖 8.16）
- Why Not Blocking Closure: 同上零重复——FAM-2 与本批 FAM-1 为不同族；本批裁定⑨⑩的 R02 三范式与 overflow 语义结论可供 R4-02 引用（不重开体裁决）。

### 1.1-C9-007（InMemoryMaterializationPoint 持锁 O(n) 同步 write/replay 互斥，P3）

- Classification: `watch-only residual`（merge-adjudication :162 判「独立——FAM-1 修复将同步缩小 n」）；Successor Required: `no`（R4+ 复核）
- Why Not Blocking Closure: correctness-first 已声明边界；本批 FAM-1 修复后 store 有界使 n 缩小，争用面自然收敛，性能优化归 R4+ 批次。

## Non-Blocking Follow-ups

- **R4-01/R4-02 对接注记**：本批落地后向 roadmap 8.41/8.42 行作「8.16 就绪」完成注记（软依赖解除落点）；R4-02 实施引用裁定⑨⑩三范式与溢出语义结论为既有裁定。
- **裁定⑥批量上限参数与裁定⑨ LIMIT 参数的运行期调优观测**：缺省值经本批测试面定夺后，生产负载下的最优值归运维观测（owner doc 运维节留观测指引即可，不阻塞 closure）。
- no remaining plan-owned work（复核 @ 2026-10-29：上方三条均为跨批/运维事项，无本 plan 未竟工作）

## Closure

Status Note: 10 项成员（C1-001/C1-006/C4-001/C4-002/C4-009/C5-010/C7-011/C8-003/C8-011/FAM-1）全部 landed 并经独立 closure audit APPROVED；红①–红⑩留痕在案；C10 十条同 diff；SSOT 零参数分叉；裁定⑩(a) 经 live 证据（TestRegionRestartInternalEdgeE2E N1 保证）修订为「store 未配置无上界+档位 WARN、显式配置 fail-loud」并三件套登记（设计文档 §3.10(a) 原地修订 + plan Phase 2 偏差登记 + 测试覆盖）。
Completed: 2026-10-29

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子代理 agent_d4dcef50（fresh session，@ 2026-10-29，HEAD=16e13004c7），总裁定 **APPROVED**
- Evidence: Phase 1 4/4 PASS（SSOT diff 仅为 §3.4 两行成员追加，git show 53af64481f 核对）；Phase 2 checklist+exit criteria 全 PASS（红①–红⑩快照 `_tmp/red-green-2510-{core,runtime}-*.log` 在案且抽查真红；rule 22 E2E=TestLocalExecuteEndToEndC4Fam+multijvm E2E log；rule 23 接线=OpsJobManager:355-362/:409-442、PollingJdbcMessageService:83/:187/:330、RemoteGraphExecutionPlanBuilder 探测面零改动核对；rule 24 零 TODO/空 catch）；Phase 3 实质全 PASS（owner doc 节、check-doc-links EXIT=0、scan-hollow high=0 EXIT=0、零触碰核对）；十成员 Anti-Hollow 逐条 live 亲读实证全 PASS（锁收敛/守卫/补偿/复核/分面/接线清理/门控探测/分批运行时真实生效）；零回退钉独立复跑 18 类 57 tests 全绿（`_tmp/2510-audit-{core,runtime}-pins.log`）；Deferred 三项均为批前裁定批外项。
- 门禁记录：E1 `./mvnw clean install -T 1C -DskipTests` EXIT=0（授权支路沿 2497–2509 先例）；E3 `./mvnw test -pl nop-stream -am` 第 3 轮 EXIT=0（r1/r2 因 nop-stream-connector-debezium docker fork timeout 环境抖动重跑，R2-08 域零触碰；快照 log 因 -q 截断无 BUILD 尾行，以审计独立复跑 57 tests EXIT=0 补强）；`node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` EXIT=0 high=0；check-plan-checklist 退出码见下方回填条目。
- 台账回填：cra-index 十行 open→fixed（plan 2510 注记，吸收成员随主行括注）；roadmap 8.16→done；R4-01（8.41）/R4-02（8.42）就绪注记（软依赖解除+既有裁定可引用）。
- 提交链：53af64481f（Phase 1 设计）→ fc0a6521eb（组 core）→ a4da58970a（组 runtime）→ 16e13004c7（Phase 3 owner docs）→ 本收口提交。

Follow-up:

- R4-01/R4-02 实施时引用设计文档裁定①–⑪（含三范式选型与溢出语义结论）为既有裁定，不重开体裁决（roadmap 8.41/8.42 行已注记）。
- 裁定⑥⑨共享参数 `nop.stream.launch.poll-batch-size`（缺省 500）生产负载调优观测归运维（owner doc 运维节已有指引）。
- debezium connector docker fork timeout 环境抖动（E3 r1/r2）归 R2-08 域与测试环境，非本批缺陷。
