# 单元内合并裁定 — nop-stream-core-runtime

- 裁定人：独立合并裁定子代理（2026-10-14）。
- 输入：本目录 9 个维度文件（C1/C2/C3/C4/C5/C7/C8/C9/C10）的「## 维度复核结论」与「## 最终保留项」。
- 口径：三态判定 = **同一缺陷**（同根因同失效层，多维度重复登记 → 取主条目，其余标 `→ 并入`）/ **同族相邻**（同根因不同失效层 → 合并为 `FAM-<序号>` 族条目，修复验证须覆盖各失效面）/ **不同**（根因或失效层实质不同 → 各自立案）。
- 依据纪律：只依据各维度复核结论与最终保留项（各维复核均已逐行核验 live code 并互相指认重复，本裁定未发现与复核结论冲突的事实，无需推翻）；不修改 9 个维度文件。
- 严重度口径：合并条目取主条目复核后严重度；仅对存在分歧的合并组给出终裁并注明一句理由（见第三节）。

## 一、裁定表

| 判定 | 主条目/族 ID | 成员 ID | 根因 | 失效层 | 理由一句 |
|---|---|---|---|---|---|
| 同一缺陷 | C1-001 | C2-001→并入、C3-001→并入 | LocalSourceCoordinator 枚举器被 reader 任务线程锁外直调（addReader:186 / handleSplitRequest:260 无锁 vs snapshotState:297 持锁） | core source 协调器并发正确性（双份分配/份额丢失/快照撕裂/CME，含 persist 线程交叉） | 三维同文件同入口同机制登记，C2 侧重 persist 受害者、C3 侧重调用方实证，取编号最小且最完整的 C1-001 为主 |
| 同一缺陷 | C1-005 | C2-007→并入 | TwoPhaseCommitSinkFunction.finishCommit 判存→commit→remove 三步跨锁 TOCTOU | 2PC 提交路径原子性（同 epoch 双重提交，靠 sink 幂等兜底） | 同方法同非原子性；C2-007 为同缺陷的 P3 窄口径（自标"调用方串行被打破才可达"），其 restoreFromEpoch 面已含于 C1-005 失效面枚举 |
| 同一缺陷 | C1-006 | C7-002→并入（C7-019 随迁） | removeTaskAssignment 生产零调用方，JDBC+内存任务分配账本 append-only | 持久化账本面（跨重启）＋内存账本面（随进程寿命） | 同 API 同账本同层；C7-019（evictExpiredNodes 未接线）为同族"死清理 API"兄弟面，随 C7-002 并入修复批 |
| 同一缺陷 | C1-008 | C2-002→并入 | SourceReaderOperator.reader 非 volatile＋判空-入队跨监视器＋一次性 flush → 迟到 split 滞留 preOpenSplits | split 投递可见性/原子性（理论风险，库内因"自投递单线程"不可达） | C2 复核已明示"合并裁定应并入 C1-008"；同缺陷同层 |
| 同一缺陷 | C2-003 | C3-003→并入 | commit-fanout 执行器（1 线程+LBQ(1000)+CallerRuns）饱和时把 sink commit I/O 拉回 CC 监视器内并打破串行提交契约 | checkpoint 提交扇出治理（监视器内 I/O＋串行假设破裂＋无饱和打点） | 双方复核互相指认为跨维度重复；三处锁内 execute 事实一致 |
| 同一缺陷 | C4-001 | C8-013→并入 | LOCAL TaskExecutor 固定池=核数＋无界队列＋长驻任务阻塞有界输出队列 → 池内互等死锁 | LOCAL 执行面（确定性静默永久挂死、三层检测全盲） | 同根因同失效层（r5 CC-05 已知未修复）；严重度终裁见第三节 |
| 同一缺陷 | C4-002 | C3-002→并入、C8-014→并入 | CachingTableLookup 刷新无 in-flight 去重＋newFixedThreadPool 无界队列 | connector 刷新调度面（delegate 慢/挂死 → 积压无界＋重复刷新 I/O 放大正反馈） | 三维同类三登记，C4-002 量化最完整（OOM 候选，P1）取为主 |
| 同一缺陷 | C4-007 | C3-006→并入、C7-008→并入、C8-007→并入 | WebhookAlertChannel.delivered 无界 CopyOnWriteArrayList 只增不删（test observability 字段无生产区分） | 告警观测面内存慢性累积（附 C3-006 的 close 不 drain pending 次要面） | 四维同字段同机制；终裁 P3（见第三节） |
| 同一缺陷 | C5-004 | C8-008→并入 | StreamOpsHttpServer.handleSubmit readAllBytes 无 Content-Length 预检/限量 | 运维面无界输入（单请求 ≈3× body 峰值击穿 coordinator 堆） | 同端点同读取点同缺陷 |
| 同一缺陷 | C5-005 | C7-004→并入、C8-004→并入 | timer-fire mail 无在途去抖/coalescing＋TaskMailbox 双 CLQ 无界 | 控制面邮箱（背压期 10Hz 无界积压＋解除后陈旧 mail 风暴） | 三维同机制同层，修复点同一（driver 去抖） |
| 同一缺陷 | C5-010 | C8-002→并入 | PollingJdbcMessageService.poll 无 LIMIT，readRows 全量物化积压 | launch 消息读面（单 poll 峰值线性于积压 → OOM；含 cursor=0 引导） | 同查询点同物化点；C5-010 另含 WI6 门控不可达分析，最完整 |
| 同一缺陷 | C5-011 | C7-016→并入 | LocalSourceCoordinator.finishedSplits write-only 无界集合（零消费者、零删除） | source 协调器死增长面（P3） | 同容器同机制（C7-016 补充 close 不清理面） |
| 同一缺陷 | C5-012 | C8-015→并入 | MemoryBudget write-only 死配置（getMemoryBudget/getAllocation 零调用方、Monitor 与三档策略零实现） | 部署计划级内存包络声明无执行者 | 同根因同层；C5-012 含设计文档 §9 对照更完整；与 FAM-3 同主题不同字段族（见 FAM-3 行备注） |
| 同一缺陷 | C7-012 | C5-008→并入 | 三个算子 counter 以 identityHashCode 做标签且无任何移除路径（EquiJoin:185/OverWindow:130/WindowOperator:587） | 算子指标基数（每次重部署净增 meter，永不复用永不清理） | 同三处注册点同机制；C7-012 定性与失效面更完整（P2） |
| 同一缺陷 | C8-003 | C7-003→并入 | nop_stream_msg_queue INSERT-only 无运行时保留（clearTopics test-only 零调用）＋launch 后端无 ChannelBacklogProbe 致 WI6 门控恒不可用 | launch 持久化账本面（磁盘无界）＋生产者流控面（门控结构性失效） | C7-003 为同账本窄口径（仅增长面）；门控面保留于 C8-003 |
| 同一缺陷 | C8-005 | C3-004→并入 | poll 循环失败行（反序列化失败/消费异常）一律 skip＋cursor 前移，无重投/typed 失败/死信 | launch 消息确认语义（数据面静默缺记录，at-most-once） | 同一代码块（:319-334）两分支同根因同层；终裁 P2（见第三节） |
| 同一缺陷 | C8-011 | C4-004→并入、C3-008→并入 | launch 共享 2 线程 poller 承载全部订阅＋内联串行派发（慢进度每行≤10s × 无界批量） | 控制面派发饥饿谱系（时延抖动 → 时效退化 → 无界饥饿/checkpoint 功能性失效） | 同池同机制的三档递进登记；取最深失效层 C8-011 为主，终裁 P1（见第三节） |
| 同族相邻 | FAM-1 | C5-001（主，C7-001→并入）＋C8-001（族成员） | materialization 边 overflow-bypass：InMemoryMaterializationPoint.store append-only 无界＋队列满即释放许可改道 store-only | 内存层（无界增长→OOM：C5-001/C7-001）＋背压/语义层（IBufferPool 上界被架空＋活动运行期记录静默不可见：C8-001） | 同根因（store 无界＋bypass 写入口）两层后果；修复须同时覆盖截断/淘汰契约、许可保留或 typed 溢出信号、运行期可见性，并同步改写假保护的 TestResultPartitionOverflowBypass（C10-005） |
| 同族相邻 | FAM-2 | C1-002＋C5-007＋C7-006 | SideOutputRegistry 进程级 static 表以裸 tagId 为键且 remove 零生产接线 | 正确性层（跨 job 同名 tag 静默错投：C1-002）＋生命周期层（终态注册滞留、闭包钉住算子链：C5-007/C7-006 同层重复） | 同根因（键作用域＋生命周期双缺失）异层；修复须同时覆盖 job 复合键与终态 remove 接线；C10-022 改指 FAM-2 |
| 同族相邻 | FAM-3 | C8-010＋C8-012 | G27 后流控参数的声明-实现契约缺口 | 字段级（receiveWindow/packetSize 零消费，已裁定边界：C8-010）＋transport 装配级（远程 queueCapacity 硬编码 1024 静默忽略：C8-012） | 同根因（流控参数族无执行者）异层；与 C5-012（MemoryBudget，plan 级）属同主题相邻——处置可统一裁决（声明保留 or 补执行者），立案分离 |
| 同族相邻 | FAM-4 | C4-008＋C4-010 | 线程命名违反 NopStreamThreadFactory"前缀标识组件与实例"契约 | 固定裸名（processing-time-driver / polling-jdbc-msg：C4-008）＋per-factory 前缀跨实例碰撞（stream-task-executor-N：C4-010） | 同根因（命名契约）不同命中机制；一批修复覆盖全部违规调用点 |
| 不同 | C4-005（独立保留） | — | barrier fanout per-node 单线程执行器无界队列 | 节点控制面（挂死 send 下队列缓增＋该节点 barrier 静默失效靠 checkpoint 超时兜底） | 线索核实：C2-010 为同 map 的创建-关闭竞态（不同根因）；C8 第 3 轮已核查该池未立案（one-way 契约）——无他维重复，独立立案 |
| 不同 | C1-004 与 C8-009（各自立案） | — | 同在 TaskManager.receiveAssignment 段但缺失面正交：C1-004 缺 fencing epoch 比较（含 installInvokable 无校验）；C8-009 duplicate 分支无 FAILED 上报＋两处 submit 拒绝不清理许可/登记 | 恢复期 epoch 守卫 vs 单向 RPC 上报契约/资源清理 | 同代码区不同缺陷，修复需同批协调但立案分离 |
| 不同 | C2-008（独立保留） | — | ReaderChannel.drainPending 出队与投递分离（批序契约缺口） | split 批顺序（P3，库内单线程投递下不可达） | 与 C1-001 同类不同根因（互斥 vs 保序），独立立案 |
| 不同 | C2-010（独立保留） | — | barrierFanoutExecutors 创建-关闭竞态（逃逸执行器泄漏＋已关闭执行器 REE） | 停机生命周期面（P3） | 见 C4-005 行；与 C4-005 不同根因不同层 |
| 不同 | C7-007 与 C7-012（各自立案） | — | C7-007 为 releaseHolder 置空不删 meter（释放不完整）；C7-012 为无释放＋不稳定标签 | 同为 metric 基数层但类与机制不同 | 同主题修复批（metrics 生命周期），不构成同根因，不合并 |
| 不同 | C4-012 与 C2-003（各自立案） | — | shutdown 竞速 isShutdown 分支静默丢通知 vs 饱和 CallerRuns 持锁内联 | 同执行器族不同失效窗口（停机竞速 vs 饱和回退） | 修复（统一 always-inline/typed 拒绝＋计数）建议一并设计，缺陷分离 |
| 不同 | C5-010 / C8-011 与 C8-003（各自立案） | — | 读侧批量无界（OOM）/派发饥饿 vs 写侧账本无界（磁盘） | 同一类的三个独立失效面 | C8-011 复核已明示根因相互独立；三案并列保留 |

> 其余 40 条经全量交叉核对无跨维度重合（各维复核的现状归属与历史审计对照已排除既有立项），独立立案，清单见第四节。

## 二、已知疑似重合线索逐条核实结果

| 线索 | 裁定 |
|---|---|
| C1-001 ≈ C2-001 ≈ C3-001 | 同一缺陷 → 主条目 C1-001 |
| C2-003 ≡ C3-003 | 同一缺陷 → 主条目 C2-003 |
| C2-002 / C2-008 ≈ C1-008 | C2-002 ≡ C1-008（并入）；C2-008 不同（批序契约，独立保留） |
| C5-001 ≈ C7-001 ≈ C8-001 | C7-001 ≡ C5-001（并入）；C8-001 与之同族相邻 → FAM-1 |
| C5-010 ≡ C8-002 | 同一缺陷 → 主条目 C5-010 |
| C8-003 ≈ C7-003 | 同一缺陷 → 主条目 C8-003 |
| C1-006 ≡ C7-002 | 同一缺陷 → 主条目 C1-006（C7-019 随迁） |
| C5-012 ≡ C8-015 | 同一缺陷 → 主条目 C5-012 |
| C4-002 ≈ C3-002 ≈ C8-014 | 同一缺陷 → 主条目 C4-002 |
| C7-004 ≈ C8-004 ≈ C5-005 | 同一缺陷 → 主条目 C5-005 |
| C4-005 ≈ ? | 不同——无他维重复登记（C2-010 为同池不同根因；C8 第 3 轮核查未立案），独立保留 |
| 自行发现的重合（裁定表未列线索） | C4-001≡C8-013、C1-005≡C2-007、C5-004≡C8-008、C4-007 组（4 维）、C8-005≡C3-004、C8-011 组（3 维）、C5-011≡C7-016、C7-012≡C5-008、C1-002/C5-007/C7-006 族、C8-010/C8-012 族、C4-008/C4-010 族 |

## 三、严重度分歧终裁

1. **C8-013（r5 CC-05）与 C4-001 的 P1/P2 分歧 → 终裁 P1**（两案已合并为同一主条目 C4-001）。理由：确定性触发——常规 `parallelism(N>1) × 多 vertex` 配置即可达（非罕见竞态前提），后果为宿主线程静默永久挂死且三层检测面（无超时 awaitCompletion、CREATED 状态不可见、区域重启同池排队）全盲；本 campaign C4/C8 两维复核独立维持 P1，r5 的 P2 加权（"拓扑规模配置前提"）属正常使用前提，不改变"负载上升必然触发且零可观测"的 P1 性质。
2. **C4-001 与 r5 复核的 P2 分歧 → 终裁 P1**：与上条为同一缺陷的同一分歧，合并后统一为 P1。
3. **合并组内严重度归一（非线索指定，按本 campaign 复核证据裁定）**：
   - C4-007 组（4 维登记 P3/P2/P3/P3）→ **P3**：3/4 维度复核定 P3（低速慢性累积、条目小、无正确性后果）；C4-007 的 P2 依据（CoW 全量复制写放大）以无界为前提属循环论证，取多数与后果量级。
   - C8-005（P2）← C3-004（P3）→ **P2**：C3-004 的"测试基础设施"降级理由被 C8 复核核实的 main-scope 事实（feb189859b / plan 2328，owner doc 生产入口装配）推翻，数据面静默缺记录按生产口径定 P2。
   - C8-011（P1）← C4-004（P2）、C3-008（P3）→ **P1**：同一共享池缺陷的三档递进登记，最深失效层（checkpoint 全周期中止＋恢复正反馈）经 C8 复核维持 P1；修复（池隔离＋LIMIT＋占用预算）同一处改动闭合三层。

## 四、C10 缺保护登记关联 ID 重映射

25 条 C10 登记中，13 条的关联 ID 指向被并入条目，改指如下（其余 12 条关联 ID 不变）：

| C10 条目 | 原关联 ID | 改指 |
|---|---|---|
| C10-001 | C1-001/C2-001/C3-001 | C1-001 |
| C10-002 | C4-001/C8-013 | C4-001 |
| C10-003 | C4-002/C3-002/C8-014 | C4-002 |
| C10-005 | C5-001/C7-001/C8-001 | **FAM-1**（C5-001/C8-001） |
| C10-006 | C5-010/C8-002 | C5-010 |
| C10-007 | C8-003/C7-003 | C8-003 |
| C10-008 | C8-011/C4-004 | C8-011 |
| C10-011 | C1-005/C2-007 | C1-005 |
| C10-012 | C2-003/C3-003 | C2-003 |
| C10-017 | C5-004/C8-008 | C5-004 |
| C10-020 | C1-006/C7-002 | C1-006 |
| C10-021 | C7-004/C8-004/C5-005 | C5-005 |
| C10-022 | C1-002/C5-007/C7-006 | **FAM-2** |

## 五、合并后缺陷清单

### P1（10 条）

| ID/族ID | 严重度 | 标题 | 失效面清单 | 原成员 ID |
|---|---|---|---|---|
| C1-001 | P1 | SplitEnumerator 跨线程无锁直调：addReader/handleSplitRequest 绕开 enumeratorLock，并行 source 启动即可双份分配/份额丢失/快照撕裂 | core source 协调器并发面（reader 任务线程 × checkpoint persist 线程；在库受害者 Debezium/BatchLoader/File 枚举器） | ←C2-001、C3-001 |
| C1-006 | P1 | 任务分配尝试账本只增不删：removeTaskAssignment 零生产调用方，JDBC 表跨重启＋内存 map 随进程线性累积 | 持久化账本面（JdbcClusterRegistry）＋内存账本面（InMemoryClusterRegistry）；随迁兄弟面：evictExpiredNodes 未接线 | ←C7-002（含 C7-019） |
| C4-001 | P1 | LOCAL TaskExecutor 池=核数＋无界队列：Σsubtask>核数时上游背压×下游排队互等死锁，静默永久挂死、三层检测全盲 | LOCAL 执行面（runLocal / checkpointed LOCAL / savepoint 三入口；awaitCompletion 无超时；CREATED 状态检测盲区；终裁 P1） | ←C8-013 |
| C4-002 | P1 | CachingTableLookup 刷新池无界队列且无 in-flight 去重：delegate 挂死时每轮全量重入队 → 无界增长（OOM 候选）＋降级 DB 重复探测 | connector 刷新调度面（scan→refreshPool 边界；休眠能力类） | ←C3-002、C8-014 |
| C4-009 | P1 | OpsJobManager.submit 失败窗口孤儿协调器：活线程池泄漏＋治理路径不可见＋同 jobId 重提交双活协调器 | ops 装配面（submit 无 try/finally 补偿；failureDetector 永生；CheckpointCoordinator 池组；registry 残行） | 独立 |
| C5-010 | P1 | PollingJdbcMessageService.poll 无 LIMIT 全量物化积压：单 poll 峰值线性于积压可达 OOM（cursor=0 引导放大） | launch 消息读面（OOM）＋WI6 门控可达性（该后端无 ChannelBacklogProbe） | ←C8-002 |
| C7-011 | P1 | standby 降级不取消在途 checkpoint persist：完成/提交扇出无 active/fencing 复核，僵尸 standby 成持久平面第二写者 | checkpoint 持久平面（双写者、跨写者错配、虚假完成事件/重复提交扇出） | 独立 |
| C8-003 | P1 | nop_stream_msg_queue INSERT-only 无界账本（唯一删除路径 test-only 零调用）＋WI6 门控对该后端恒不可用 | launch 持久化账本面（磁盘耗尽→send 失败循环）＋生产者流控面（门控诚实 no-op） | ←C7-003 |
| C8-011 | P1 | launch 共享 2 线程 poller：慢进度数据面积压（每行≤10s×无界批量）垄断派发线程，控制面（barrier/deploy/cancel/ACK）无界期饥饿 → checkpoint 全周期中止＋恢复正反馈 | launch 派发调度面（数据/控制无隔离；sync persist 内联放大器；终裁 P1） | ←C4-004、C3-008 |
| FAM-1 | P1 | materialization 边 overflow-bypass 族：store 无界增长＋池背压被旁路＋活动运行期记录静默不可见 | 内存层（C5-001/C7-001：无截断/淘汰契约，线性于吞吐×时长→OOM）＋背压/语义层（C8-001：permit 释放改道 store-only、运行期记录对消费者不可见）＋假保护测试面（TestResultPartitionOverflowBypass 须同步改写） | C5-001（主，←C7-001）＋C8-001 |

### P2（27 条）

| ID/族ID | 严重度 | 标题 | 失效面清单 | 原成员 ID |
|---|---|---|---|---|
| C1-003 | P2 | BasicTypeInfo.INSTANCES plain static HashMap，of() 无同步检查-然后-行动 | 类型解析并发面（结构损坏＋重复实例行为分叉） | 独立 |
| C1-004 | P2 | TaskManager 两阶段分配路径缺 epoch 守卫（receiveAssignment 重复槽位分支＋installInvokable） | 恢复期槽位守卫面（deployTask 已治理，此为残留缺口） | 独立 |
| C1-005 | P2 | finishCommit 的 contains→commit→remove TOCTOU：并发通知/重试下同 epoch 双重提交 | 2PC 提交原子性面（基类对 13+ 子类无幂等保证；含 restoreFromEpoch 同构面） | ←C2-007 |
| C1-007 | P2 | InputGate 水位 plain long[]/boolean[] 被抓取线程无同步读取（撕裂＋陈旧，观测面理论风险） | 指标抓取交叉面 | 独立 |
| C1-008 | P2 | reader 非 volatile＋判空-入队跨监视器：迟到 split 永久滞留 preOpenSplits（理论风险） | split 投递可见性面（跨线程枚举器假设下静默缺数据） | ←C2-002 |
| C2-003 | P2 | commit-fanout CallerRuns 饱和把 sink commit I/O 拉回 CC 监视器并打破串行提交契约（无饱和打点） | checkpoint 提交扇出治理面（三处锁内 execute；与 C4-012 停机窗口为同池不同缺陷） | ←C3-003 |
| C2-004 | P2 | InMemoryClusterRegistry.taskAssignmentHistory 写侧持锁、读路径不加锁迭代 | registry 读同步面（门禁违规；生产被 recoveryLock 串行，理论风险） | 独立 |
| C2-005 | P2 | CheckpointBarrierTracker.triggerCheckpoint 持监视器直通 injectBarrier（回退路径快照序列化在锁内） | barrier tracker 锁范围面（终局/回退 checkpoint 的 ACK 串行化） | 独立 |
| C3-009 | P2 | queryable-state REST 读面对运行中任务 keyed 状态无同步跨线程快照（CME/撕裂/漏读） | ops 查询面（http worker × 任务线程；handler 宽 catch 仅 StreamException） | 独立 |
| C4-005 | P2 | per-node barrier fanout 单线程执行器无界队列：节点 RPC 挂死时队列单调增长、该节点 barrier 静默失效 | JC 控制面扇出治理面 | 独立 |
| C4-006 | P2 | LOCAL 取消路径不对称：中断后 finally 仅 graceful shutdown 不 cancel 子任务 → 消费者线程持连接无限自旋 | LOCAL 关闭路径面（与 C4-001 同域不同缺陷） | 独立 |
| C4-012 | P2 | stop() 竞速窗口静默丢弃 2PC commit 通知：isShutdown 分支丢弃但计数/日志/javadoc 声称 caller-run，同族三档不一致 | TM/CC 停机竞速面（与 C2-003 同池不同失效窗口） | 独立 |
| C5-002 | P2 | checkpoint 序列化链 freeze 深拷贝＋重复 form 转换＋checksum 多轮往返，峰值 ≈ 状态体积 4–8× | checkpoint 序列化峰值面（多作业宿主线性叠加） | 独立 |
| C5-003 | P2 | join/over-window/reduce 工作视图全量驻堆，RocksDB 声明包络对这些算子不可达 | 状态规模包络面（实现-声明错配） | 独立 |
| C5-004 | P2 | /jobs 提交端点无大小上限 readAllBytes：单请求 ≈3× body 峰值击穿 coordinator 堆 | 运维面无界输入 | ←C8-008 |
| C5-005 | P2 | timer fire mail 无去抖＋TaskMailbox 无界：背压期 ~10 条/s 慢性累积，解除后陈旧 mail 风暴 | 控制面邮箱面（driver 去抖缺失） | ←C7-004、C8-004 |
| C5-006 | P2 | failedCommitParticipants/checkpointSuccessMap 在参与者持续失败时按 checkpoint 频率累积，无上限无升级出口 | coordinator 提交重试账本面（AR-10 残留） | 独立 |
| C5-012 | P2 | MemoryBudget write-only 死配置：设计 §9 的 Monitor 与三档超预算策略零实现，字节级包络无执行机制 | 部署计划内存包络面 | ←C8-015 |
| C7-005 | P2 | 持续失败路径周期性错误日志无抑制（5+4 点），故障期日志放大占满日志面 | 日志治理面（含 C7-014 并入的 ⑥-⑨ 与 StreamMetricsReporter 补遗） | 独立（C7-014 已在维内并入） |
| C7-007 | P2 | MicrometerStreamTaskMetrics.releaseHolder 只置空不删条目/meter：jobId churn 下每作业净增 ~11 meter 永驻 | 任务指标生命周期面（与 C7-012 同主题不同类，分案） | 独立 |
| C7-012 | P2 | 三个算子 counter 以 identityHashCode 做标签且无移除路径：每次重部署净增 meter 永不复用 | 算子指标基数面 | ←C5-008 |
| C8-005 | P2 | poll 失败行（反序列化失败/消费异常）一律 skip＋cursor 前移：数据面静默缺记录（终裁 P2） | launch 消息确认语义面 | ←C3-004 |
| C8-009 | P2 | duplicate 分配静默吞没（无 FAILED 上报）＋两处 submit 拒绝不清理许可/登记（zombie） | TM 上报契约/资源清理面（CC-17 残留；与 C1-004 同区不同缺陷） | 独立 |
| C9-001 | P2 | FieldAggregationReducer 每记录全量反射拷贝 accumulator（构造器/Field 数组每记录重解析） | 聚合热路径性能面 | 独立 |
| C9-003 | P2 | CachingTableLookup 驱逐路径在同步 per-record 热路径上做 O(maxEntries) 全表扫描 | 维表查找热路径性能面 | 独立 |
| C9-004 | P2 | FLIP-27 源循环空闲时 Thread.yield() 纯自旋烧核（与 InputGate parkNanos 形态不一致） | SOURCE 空闲策略面 | 独立 |
| FAM-2 | P2 | SideOutputRegistry 作用域/生命周期族：跨 job 同名 tag 静默错投＋终态注册滞留钉住算子链 | 正确性层（C1-002 错投）＋生命周期层（C5-007/C7-006 内存驻留＋语义污染）；修复须同时覆盖复合键与 remove 接线 | C1-002＋C5-007＋C7-006 |

### P3（24 条）

| ID/族ID | 严重度 | 标题 | 失效面清单 | 原成员 ID |
|---|---|---|---|---|
| C1-009 | P3 | DbBacklogGauges 缺兄弟 ChannelQueueGauges 的 close 释放：关闭后 gauge 冻结非零并强引用死分区 | gauge 生命周期面（D2c 不对称） | 独立 |
| C1-010 | P3 | InMemoryClusterRegistry lease 双 CHM 拆分裂读（getNodeLease 生产零调用方，实际暴露面仅 getActiveNodes） | registry 读一致性面（自愈） | 独立 |
| C1-011 | P3 | EngineMetrics.GAUGE_STATE_REFS 对 checkpoint-size gauge 半接线（构造器从不 add、remove 恒 no-op） | 指标保留链死代码面 | 独立 |
| C2-008 | P3 | ReaderChannel.drainPending 出队与投递分离，批间无序契约缺口 | split 批顺序契约面 | 独立 |
| C2-009 | P3 | CheckpointMetrics.snapshot() 装饰性 synchronized（10 个写点均不持该监视器），多字段快照必然撕裂 | 运维观测快照面 | 独立 |
| C2-010 | P3 | barrierFanoutExecutors 创建-关闭竞态：逃逸执行器泄漏＋已关闭执行器 REE（tick catch 兜底） | JC 停机生命周期面 | 独立 |
| C2-011 | P3 | ResultPartition.drainBufferedElements 吞 EOS 哨兵不归还（与 RemoteInputChannel 放回防御不对称），无界 read() 潜伏挂死 | 捕获/读路径协议面（当前无生产受害调用方） | 独立 |
| C3-005 | P3 | PollingJdbcMessageService.sendAsync 伪异步：INSERT 同步执行于调用方线程，异常绕过 CompletionStage 通道 | SPI 异步契约面 | 独立 |
| C3-007 | P3 | CachingTableLookup.Entry.loadNanos 非 volatile 跨线程读写（良性自愈，理论风险） | 缓存字段可见性面 | 独立 |
| C4-003 | P3 | StreamOpsHttpServer.stop() 不关闭请求 executor（每轮 start/stop 泄漏 ≤4 daemon 线程）＋请求队列无界 | ops server 关闭配对面（r6 CC-05 已知） | 独立 |
| C4-007 | P3 | WebhookAlertChannel.delivered 无界只增不删（终裁 P3；close 不 drain pending 面随迁） | 告警观测面内存慢性累积 | ←C3-006、C7-008、C8-007 |
| C4-011 | P3 | CachingTableLookup.scanAndDispatch 无全包容 catch：违反模块"整迭代守卫"不变式（F9 潜伏面，理论风险） | 周期任务异常围栏面（r5 CC-07 漏修兄弟点） | 独立 |
| C4-013 | P3 | persistExecutor 无同步惰性初始化：当前交错经论证不可达，第三脱监视器调用方不在书面论证内（防回归缺口） | 惰性创建契约面（理论风险） | 独立 |
| C5-011 | P3 | LocalSourceCoordinator.finishedSplits write-only 无界集合（零消费者零删除） | source 协调器死增长面 | ←C7-016 |
| C7-009 | P3 | 周期调度循环仅捕 Exception：Error 逃逸致检测/注入面静默永久死亡且无活性看门狗（含 C7-017 并入的 core 侧更弱形态 ⑧） | 检测器自身存活面（理论风险） | 独立（C7-017 已在维内并入） |
| C7-010 | P3 | 租约/停顿/冷却全用墙钟：跳变下批量误判/漏检＋NTP 隐性依赖未声明（含 C7-018 并入的 TTL 前跳数据丢失方向 ⑧⑨） | 时钟语义面（理论风险） | 独立（C7-018 已在维内并入） |
| C7-013 | P3 | LocalFileCheckpointStorage 崩溃残留 .tmp 不被任何列举/保留/剪枝命中（含 C7-015 并入的 .sst.tmp-* 段文件面） | 本地平面崩溃卫生面 | 独立（C7-015 已在维内并入） |
| C8-006 | P3 | 过载可观测四缺口（BufferPool 用量/mailbox 深度/告警丢弃/mat store 大小）＋queueCapacity≤0 静默回落 | 过载观测面 | 独立 |
| C9-006 | P3 | 字符串 wire 后端对同一记录值双重 JSON 编码/解码 | wire 序列化冗余面（Stage 40 Non-Goal，演进候选） | 独立 |
| C9-007 | P3 | InMemoryMaterializationPoint 每记录同步 write 与恢复 replay（持锁 O(n) 拷贝）互斥 | 物化锁争用面（已声明 correctness-first 边界；FAM-1 修复将同步缩小 n） | 独立 |
| C9-008 | P3 | StreamElementCodec 解码路径每记录重跑 23 前缀白名单校验（缓存只覆盖 forName 半边） | 解码热路径性能面（与 C9-006 同链不同缺陷，可同批修） | 独立 |
| C9-009 | P3 | WindowOperator active-window registry 每记录重建 paneKey 复合字符串+分配 ActiveWindow 探针对象 | 窗口注册热路径性能面（windowNamespace 单槽 memo 先例的漏修同型兄弟） | 独立 |
| FAM-3 | P3 | 流控参数声明-实现契约族：G27 后参数无执行者 | 字段级（C8-010：receiveWindow/packetSize 零消费，已裁定边界）＋transport 装配级（C8-012：远程 queueCapacity 硬编码忽略）；与 C5-012（plan 级 MemoryBudget）同主题相邻，处置统一裁决 | C8-010＋C8-012 |
| FAM-4 | P3 | 线程命名契约族：违反"前缀标识组件与实例"约定 | 固定裸名（C4-008）＋per-factory 跨实例碰撞（C4-010）；一批修复覆盖全部违规点 | C4-008＋C4-010 |

## 六、统计

| 指标 | 数值 |
|---|---|
| 合并前条目数（9 维度最终保留项合计） | **90**（C1×11、C2×10、C3×9、C4×13、C5×11、C7×14、C8×15、C9×7；C7 维内已并入的 C7-014/015/017/018/019 不重复计数；C10 的 25 条缺保护登记为保护面条目，仅做 ID 重映射不参与合并） |
| 合并后条目数 | **61**（同一缺陷主条目 17＋缺陷族 4＋独立条目 40） |
| 同一缺陷并入数 | **24**（23 条跨维度并入 16 个主条目＋C7-001 并入 FAM-1 主条目 C5-001） |
| 同族合并数 | **4 族**（FAM-1/2/3/4，10 个条目 ID → 4 个族条目，族内净减 6，其中 C7-001 已计入并入数） |
| 净减条目 | 29 |
| 合并后严重度分布 | P1×10、P2×27、P3×24 |
| P1 清单 | C1-001、C1-006、C4-001（终裁 P1）、C4-002、C4-009、C5-010、C7-011、C8-003、C8-011（终裁 P1）、FAM-1 |
| 严重度分歧终裁 | C8-013/C4-001 vs r5 CC-05 → **P1**（确定性静默永久死锁＋三层检测全盲，常规配置可达）；组内归一：C4-007 组 → P3（多数口径）、C8-005 ← C3-004 → P2（main-scope 事实推翻测试设施降级）、C8-011 ← C4-004/C3-008 → P1（最深失效层维持） |
