# 2511 CRA Phase 8.17 — R2-08 1.2 连接器批（并发正确性 + 资源包络，7 项）

> Plan Status: active
> Design Review: R2 agent_ee958fe0 @ 2026-10-29（第 1 轮 FAIL[2 Major/0 Blocker/6 Minor]：Major-1 裁定② 未簿记测试必红面[TestCepOperatorLateRecordsDroppedMetric 匿名 fixture]→§3.8 随同 diff 影响面登记；Major-2 裁定⑥ ChangeEvent 无 partition 访问器、位点载体实为 ChangeEventMetadata 特化字段可 null + per-split 粒度未定夺→位点键形 lsn→commitLsn→binlog 优先级序+逐字段 typed 判据+per-server 键形+restore 取 min 保守重放；Minor×8 全闭合[SSOT ceil、换算基声明、SnapshotRestore 全量 opt-out leg、:262 间接锚、钉#6 构造适配触碰注记、部署期行为变更声明]——第 2 轮复审 8/8 CLOSED PASS，probe(a)(b) 均通过[restore-min 只放大重复面不放大丢失面；copyForSubtask 测试调用方全仓穷尽仅 2 处均在簿记]）
> Draft Review: agent_96b5498c @ 2026-10-29（PASS，仅 6 Minor——行号微漂移 :91/:336/:273/:431 已修入、C5-007 跨单元边界注记、C3-014 residual 注记义务入 Phase 4；基线 14 组抽查全吻合、R03 §3.6 引用亲读核实、结构完备性对照 plan guide 与先例 2510 全在位）
> Last Reviewed: 2026-10-29
> Source: roadmap 工作项 8.17（R2-08，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L159，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-08 行（L82 全文：finding ID 清单 7 项[1.2-C1-002；C1-001；C2-002；C3-002；C5-002；C8-001；C8-006] / 模块与验证命令 nop-stream `./mvnw test -pl nop-stream -am` / Owner Doc=`docs-for-ai/03-modules/nop-stream-connectors.md` / PA=无 / 依赖「无前置（可并行）；MA-05 保留策略与 R1-07 同设计；MA-04 R03 锁内 HTTP 外移」/ exit criteria 增补：**gauge 锁外 dbRef 修复（SIGSEGV 窗口）；CEP copyForSubtask 三面封闭；Avro registry 请求超时+锁外建连；2PC 保留策略（R01）；checkpoint offset 对齐（≤256 条丢失面）；2PC 事务裕度守卫**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.2 审计目录 `ai-dev/audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/`（C1-shared-mutable-state.md C1-001 :53 / C1-002 :85；C2-locks-sync.md C2-002 :110；C3-async-chains.md C3-002 :85；C5-memory-bounds.md C5-002 :91；C8-backpressure.md C8-001 :50 / C8-006 :274；C10-test-protection.md 映射表 :55-75 + C10-002 :107 / C10-003 :132 / C10-007 :211 / C10-008 :234 / C10-012 :318 / C10-013 :334 / C10-014 :363；merge-adjudication.md 族表 MA-02 :45 / MA-03 :46 / MA-04 :47 / MA-05 :48 / MA-08 :51、独立案 C8-001 :70 / C8-006 :71、P1 清单 :140-154、修序建议 :235-241、C10 映射 :213-225），逐条回读路径见 Current Baseline；执行基线 @ `c1674460dd`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.16 已 done，plan 2495–2510 均已完成或进入收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-29 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2502（R1-07，completed——**R01 族级 SSOT（关键）**：`ai-dev/design/concurrency/ledger-retention-policy.md` 为保留策略一处定稿权威（统一参数集七项 §3.1 + 配置键命名 §3.2 + 触发形态菜单 §3.3 + 成员传导表 §3.4 + 参数修订纪律 §3.6），其 §二明示 R2-08 为族成员批次——本批 C3-002（MA-05）的 2PC 保留策略只做该文档的成员传导行，face 自定、零参数发明，参数修订只走该文档）；plan 2509（R2-06，completed——**R03 范式先例**：`ai-dev/design/concurrency/stream-buffer-unification-and-failover-flow-concurrency.md` §3.6 裁定⑥「锁内重活外移」= 锁内仅状态机变更、I/O/投递出锁 + 信号序保证——本批 C2-002（MA-04）锁内 HTTP 外移引用该范式）；plan 2510（R2-07，completed——同上游 nop-stream 门禁先例，其裁定①–⑪与三范式结论可供引用不重开体；其 R01 成员行已占 `assignment`/`msgqueue` 两 face token，本批 face 另定不复制参数）；plan 2498/2503（R1-03/R1-08，completed——WI 1.2 同域已修成员 C4-004/C3-009、C6-001/C3-014/C3-004，本批零重做、零回退其测试锚）；`ai-dev/design/concurrency/registry-lifecycle-contract.md`（R4-24 实现契约，设计已 done、实现未启动——REGISTRY 生命周期契约站点含 nop-message-debezium `NopStreamOffsetBackingStore`，与本批 C8-001 的 offset 面相邻：本批零 REGISTRY 生命周期实现，若裁定需触 offset backing store 契约面须作对齐注记而非代裁）；**下游**：R4-03（roadmap 8.43，12 项连接器 MA 束+包络声明）与 R4-04（8.44，11 项并发正确性+失败路径泄漏束）均软依赖 8.17（L185/L186）——本批为同模块 P1 先行批，批末须回写就绪注记（含 MA-16=R01 保留策略族成员、MA-13 与本批 C3-002 保留策略配套的对齐交付物）；**同案双轨**：1.2-C3-006（R4-04 成员）与 4.1-MA-10 同案双轨同批归 R4-24（family-clustering §四#9）——本批零承接；**并行协同**：R2-09（1.3 编排薄层批，同属 nop-stream 门禁）——零共享站点，仅门禁命令同聚合
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-08 行授权列）。connectors 族模块（nop-stream-cep/rocksdb/format-avro/connector{,-jdbc,-kafka,-debezium,-batch}）均非 Protected Area；live 回读勘误申报：本批 7 成员站点全部位于上述模块的非生成物 main 源码，不触 `nop-core/`/`nop-xlang/` 框架核心、不触 `model/*.orm.xml`、不做 `_` 前缀生成物改动；若 Phase 1 裁定引入平台公共落点（如把外部依赖超时契约设施上收 `nop-kernel`/`nop-api-core`，或 2PC 保留清理载体上收平台层）则照 2507–2510 先例在该裁定项内批内闭环申报 plan-first 后方可实施。本批不触碰 R4-03/R4-04 的 23 项批外成员本体。

## Purpose

关闭 R2-08 批的 7 项已确认发现（P1×7，全部 open，cra-index 台账行 L145/L146/L147/L148/L151/L153/L154）。核心结果面有六：①**gauge SIGSEGV 窗口封闭**——`RocksDBMetricsRecorder` gauge 闭包锁外取 `dbRef` 的 native use-after-free 竞态消除（C1-002，MA-02 主条目←C2-001/C6-004）。②**CEP copyForSubtask 三面封闭**——并行 CEP 共享编译态 NFA 条件图的 runtimeContext last-writer-wins / open N 次 / 交叉 close 一次修复（C1-001，MA-03 主条目←C1-005/C1-006，含 r4 R5-CEP-05 已知未修复双登记消号）。③**Avro registry 请求超时+锁外建连**——`computeIfAbsent` bin 锁内无超时 HTTP 的挂死+锁毒化面消除（C2-002，MA-04 主条目，五向族←C3-003/C4-003/C6-008/C8-004/C7-006①超时面）。④**2PC 保留策略（R01 成员传导）**——JDBC 台账 `stream_epoch_ledger_v2` 只增不删 + file manifest O(E) 放大按 `ledger-retention-policy.md` 落成员行（C3-002，MA-05 主条目←C5-006/C7-001/C8-007/C7-007①manifest 面）。⑤**checkpoint offset 对齐（≤256 条丢失面）**——Debezium FLIP-27 reader 快照 offset 领先已发射记录的静默丢失窗口封闭（C8-001）。⑥**2PC 事务裕度守卫**——Kafka 2PC 事务存活期与 checkpoint 间隔零裕度的触界失败循环消除（C8-006）；外加⑦**RocksDB 默认全量 JSON checkpoint 整库物化进堆**的包络收敛（C5-002，MA-08 主条目←C8-005，无批增补专段、经 E2 通用门禁+C10-012 承载——见 Baseline 勘误 2）。批末 roadmap 8.17 → done，cra-index 七行台账收敛，R4-03/R4-04 软依赖解除。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-08 = 7 项成员（P1×7——merge-adjudication :140-154 P1 清单中除已修的 C6-001[MA-01，plan 2462]、C4-004[MA-06，plan 2498]、C3-014[MA-07，plan 2503]、C3-004[plan 2503] 外全部在本批）；cra-index 台账七行独立（L145 C1-002、L146 C1-001、L147 C2-002、L148 C3-002、L151 C5-002、L153 C8-001、L154 C8-006，均 open、批次列均=R2-08）；roadmap 8.17 行 Status=todo。前置：无前置（可并行），plan 2495–2510 前序批均收口——基线门禁已满足。
- **吸收成员（随主条目同 diff，不单列工单）**：C1-002←C2-001/C6-004（gauge 同实例族，MA-02）；C1-001←C1-005（SideCollector ctx 槽位跨 subtask 错路由）/C1-006（udf open×N 泄漏+首个 close 全关）——MA-03 三面单修复（`copyForSubtask` 一并序列化 round-trip）；C2-002←C3-003/C4-003/C6-008/C8-004（超时面五向族，MA-04）+C7-006①（超时面并入，MA-10 的②回落/负缓存面不在本批）；C3-002←C5-006/C7-001/C8-007（四向族，MA-05 终裁 P1）+C7-007①（manifest 面归本族，C7-007 ID 消号）；C5-002←C8-005（barrier 同步自背压角度，MA-08；时长维由 C8-010[R4-03]分立承接）。cra-index 回填时以主 ID 收敛、吸收成员括注，不整行多 ID 重复立项。
- **C10 验收前置入批集（7 条，随本体同 diff 落地[E2]）**：C10-002（C1-002 族——`TestRocksDBMetricsRecorder#testCloseDetachesGaugesFromNativeHandle:113-130` **假保护**[全单线程顺序断言，3 行修复后照常绿]，改写=双线程 close×scrape 压窗+白盒断言取参点在锁内）；C10-003（C1-001——`TestCepOperatorSubtaskCopy` 对 NFA states 图/条件对象零断言**条件会红**[仅 udf 面经 ：43 反转]，改写=NFA 图实例不同断言+keyedStateStore 探针，assertSame udf 反转随 MA-03 合并修复）；C10-007（C3-002 四向族——`TestJdbcConcurrencyAndBatchSegments#countLedgerRows:159` **假保护**[写入存在性有断言、保留/清理面零断言]，改写=保留策略断言集[高水位保留/过期删除/幂等守卫+行数上界]）；C10-008（C2-002 五向族——`TestAvroWireFormat` **假保护**[全快速成功路径，`registryUnreachableFailsTyped:239` 用快速拒连非挂死注入]，改写=挂死注入 typed 失败+锁限时释放）；C10-012（C5-002 族——`TestRocksDBSnapshotRestore` **假保护**[round-trip 功能面依赖物化行为但规模零断言]，改写=键数参数化+堆驻留上界断言）；C10-013（C8-001——`TestDebeziumCdcSource` :166-174 **钉住缺陷载体**[断言 offset 无条件抬入每个 split 快照] + `TestDebeziumCdcCheckpoint:288-291` **测试自吞竞争**[显式把快照移到 kill 之后回避偏斜窗口]，双改写=snapshot offset==last-emitted+恢复重放在途 K 条）；C10-014（C8-006——kafka 测试目录 `transaction.timeout` 零命中**无测试**，新建=producer 属性面断言+耦合校验 fail-fast）。勘误：1.2-C10-016（`TestBatchLoaderSourceFunction:250` 无界自旋，测试自身缺陷）为 G91 无本体条目→治理批 R-G，本批零承接；C10-015 随 1.2-C3-011 归 R4-04。

### 7 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `c1674460dd` 2026-10-29 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.2-C1-002（P1；MA-02 主，←C2-001/C6-004） | `RocksDBMetricsRecorder.java`（nop-stream-rocksdb `metrics`） | gauge 闭包 `registry.gauge(..., dbRef, ref -> readProperty(ref.get(), property))` :82-84——`ref.get()` 在进入 synchronized 前求值；`readProperty` :91 起 synchronized null 守卫挡不住「取旧引用→close→用旧引用」交错；关联 `RocksDBKeyedStateBackend.java` close 顺序（recorder.close→db.close，gauge 无摘除点） | RocksDB metrics gauge 闭包在 synchronized 之外取 `dbRef.get()`，close 窗口内 scrape 线程可对已关闭 native handle 调 `getAggregatedLongProperty`（native UAF，javadoc 自认 SIGSEGV） | 裁定①：把取值移进监视器——新增锁内 `readCurrent(property)`（= `readProperty(dbRef.get(), property)`），gauge 闭包改调之（monitor 内读到非 null 时 native 尚未关闭、读到 null 返 NaN，依赖既有 close 顺序，零新锁）；备选等价形态（`readProperty` 内消费 AtomicReference + 异常兜 NaN）须论证锁内取值不足后方可采；C10-002 假保护改写随同 diff |
| 2 | 1.2-C1-001（P1；MA-03 主，←C1-005/C1-006；已知未修复←r4 R5-CEP-05） | `CepOperator.java`（nop-stream-cep `operator`）+ `NFACompiler.java`/`NFA.java`（`nfa`/`nfa/compiler`）+ `RichIterativeCondition.java`/`RichCompositeIterativeCondition.java`（`pattern/conditions`） | `copyForSubtask()` :329-340 对 `nfaFactory`（:333）与 `getUserFunction()`（:335）引用透传；`NFAFactoryImpl.createNFA()` :1119-1121 各 subtask 的 NFA 共享同一 `states`；`NFA.open()` :193-204 / `close()` :206-214 对共享 State 图内嵌条件原地 `setFunctionRuntimeContext`/`openFunction`/`closeFunction`；`RichIterativeCondition.runtimeContext` 非 volatile :47-53（`RichCompositeIterativeCondition` 传播覆盖嵌套条件） | 并行 CEP（parallelism≥2）共享编译态 State 图，Rich 条件对象被每个 subtask 的 NFA.open()/close() 原地变异——runtimeContext last-writer-wins（静默读错键控状态）+ 条件 open N 次 + 一个 subtask 的 close 关闭全部 subtask 条件资源 | 裁定②（MA-03 三面一次）：预设默认分支 = `CepOperator.copyForSubtask()` 对 `nfaFactory`+userFunction 做序列化 round-trip 深拷贝（NFAFactory 已 Serializable，模式已有 `serializationRoundTripCopy` 同款手段），条件对象随 subtask 私有，一次封闭 C1-001/C1-005/C1-006 三面；备选「NFA.open/close 只对本实例新建条件包装执行生命周期」须论证 round-trip 代价不可接受后方可采；`TestCepOperatorSubtaskCopy` 改写随同 diff（C10-003，assertSame 反转） |
| 3 | 1.2-C2-002（P1；MA-04 主，←C3-003/C4-003/C6-008/C8-004/C7-006①） | `AvroSchemaRegistryClient.java` + `AvroWireFormat.java`（nop-stream-format-avro） | `new HttpClient...connectTimeout(10s)` :48 仅连接超时无请求级超时；`getSchemaById` :68-69 `computeIfAbsent(id, this::fetchSchemaById)` 持 CHM bin 锁做 HTTP；`http.send` :116（readJsonResponse，getSchemaById/getLatestSchema/registerSchema 三路径共用）无 `.timeout()`；`AvroWireFormat.encode` :55-60 mapping function 内 POST+GET | Avro Schema Registry 客户端在 computeIfAbsent 内做无请求级超时 HTTP I/O——registry 挂死时任务线程永久阻塞并毒化共享 bin 锁，跨 subtask 不可见挂死（F5：无异常无日志 checkpoint 全停） | 裁定③（**MA-04=R03 锁内 HTTP 外移范式显式裁定项**，引 plan 2509 `stream-buffer-unification` §3.6「锁内仅状态机、I/O 出锁」先例）：①每 `HttpRequest` 加请求级 `.timeout(Duration)`（缺省值 Phase 1 定夺，覆盖 :116 全部路径，根治 F5）；②mapping function 移出临界区——锁外 fetch→`putIfAbsent` 幂等回填（或 get→miss→fetch→put），禁 I/O 留在 CHM bin 临界区；③registry 失败 typed `StreamAvroException` 维持（owner doc :114「失败面全 typed」声明在挂死形态修复后重新成立）；C10-008 挂死注入改写随同 diff；与 R4-24「外部依赖超时契约」工单族（C8-009 JDBC 语句级/C8-011 message sendTimeout，merge-adjudication 修序建议 7）互注对齐、不代裁 |
| 4 | 1.2-C3-002（P1；MA-05 主，←C5-006/C7-001/C8-007/C7-007①） | `JdbcTwoPhaseCommitSink.java`（nop-stream-connector-jdbc）+ `FileTwoPhaseCommitSink.java`（nop-stream-connector） | `writeLedgerEntry` :852-861 唯一台账写路径 INSERT（第 4 参时间戳列）；`abort(epochId)` :748-752 仅 `getPendingCommits().remove` 不触台账行；file 面：`updateManifestAtomically` 调用点 :346/:374 每 epoch 追加 manifest 键从不移除（每 commit 全量 load/rewrite O(全部 epoch) 放大） | 2PC 持久化登记只增不删：JDBC 台账每成功 checkpoint 每 subtask 确定性一行（30s 间隔 ≈2880 行/天/subtask）无任何 DELETE/TTL/prune；file manifest 每 epoch 追加一键 + O(E) 放大 | 裁定④（**MA-05=R01 保留策略 SSOT 成员传导显式裁定项**）：按 `ledger-retention-policy.md` §3.4 追加成员传导行——§3.1 七项参数/§3.2 键名投影 `nop.stream.ledger.<face>.*`（face token 本批另定[如 `epochledger`；jdbc 台账与 file manifest 是否分 face 由 Phase 1 按载体形态裁定]，**不复制 plan 2510 的 assignment/msgqueue face、零参数发明**，参数修订只走 SSOT）；锚列逐面显式落名（JDBC 台账=写入口第 4 参时间戳列，谓词含终态/高水位限定；manifest=键编码 epoch 号的账龄谓词，非 DB 表的参数适用性与裁剪载体[commit 成功路径顺带 vs 周期形态 A]按 SSOT §3.3 判据一次裁定）；**验证须同盖台账清理与 manifest 裁剪两面**（MA-05 裁定原文）；C10-007 假保护改写随同 diff；保留窗口 ≥ 可重提交 epoch 窗口（防踩坏恢复幂等守卫——误删未到期行=正确性危害，SSOT §3.1 第 4 项同源） |
| 5 | 1.2-C5-002（P1；MA-08 主，←C8-005） | `RocksDBSnapshotSerDe.java` + `RocksDBKeyedStateBackend.java`（nop-stream-rocksdb） | `snapshotState` 入口 :84；`snapshotMapState` :186-（grouped 双 Map 全量驻留）/`snapshotKeyedState` :284-（entries 逐值 deserialize 全量驻留）；`incrementalCheckpointEnabled=false` 缺省 :119（javadoc 自认 full-scan remains the default）；backend 缺省分派 `snapshotState()` :827 | RocksDB 默认全量 JSON checkpoint 路径把整个状态库逐值反序列化物化为堆内对象图（典型 2-5×序列化字节放大）——「状态超出堆」功能包络内默认路径的周期性 OOM 面；增量 SST 路径已实现但 opt-in 缺省关 | 裁定⑤：三选一一次定夺——(a) `snapshotKeyedState`/`snapshotMapState` 流式写出（迭代器逐 entry 序列化入 SegmentStore 分段，`StateSnapshot` 携分段句柄）；(b) 规模阈值策略（预估超阈值 typed 拒绝或强制增量，显式失败优于 OOM）；(c) 增量路径缺省化/文档强制（owner doc 声明包络与开关取舍）；预设默认分支与组合取舍 Phase 1 裁定（不得为「纯文档声明」零代码收口——缺陷本体是物化行为）；C10-012 键数参数化+堆驻留上界断言随同 diff；owner doc 数值包络声明随批（E5） |
| 6 | 1.2-C8-001（P1；独立案） | `DebeziumCdcSourceReader.java`（nop-stream-connector-debezium）+ legacy `DebeziumCdcSourceFunction.java` | `QUEUE_CAPACITY=256` :57（引擎→reader 有界队列）；`snapshotState` :255-278 直抬引擎侧 offset（:262 取 store 快照、:274 `split.withOffsetMap(offsets)` 无任何「已发射位点」回退/台账）；legacy function `snapshotState` :376-391 同机制小窗口（collect 直连，偏斜 ≈1 条）随①一并修复 | Debezium reader 的 checkpoint offset 可领先已发射记录（引擎 handler 返回即推进 offset store[DebeziumEngineWrapper `.notifying` 形态]，队列在途 ≤256+handler 在途 1 条）——崩溃恢复静默丢失 ≤256 条/检查点，REPLAYABLE 声明下的数据面语义违约 | 裁定⑥：reader 侧 last-emitted 位点协议——预设默认分支 = 维护已发射位点并在 `snapshotState` 将 offset 回退/截断到已发射边界（参照 Kafka 侧 `markConsumed` 在 collect 返回后的正确顺序；ChangeEvent 不携 per-record offset 时的 per-record offset 暴露面或恢复期安全重叠重放+下游幂等声明，一次定夺）；修复落点优先 reader/connector-debezium 侧，**不越界改 nop-message-debezium 引擎 commit 策略本体**（若确需触 `NopStreamOffsetBackingStore` 契约面则作 R4-24 registry-lifecycle-contract 对齐注记，不代裁 REGISTRY 生命周期）；C10-013 双改写（缺陷载体断言反转+kill 后快照的竞态回避改回竞速窗内断言）随同 diff；owner doc :130 REPLAYABLE 表述同步 |
| 7 | 1.2-C8-006（P1；独立案） | `KafkaTwoPhaseCommitSink.java`（nop-stream-connector-kafka）+ `CheckpointConfig.java`（nop-stream-core `checkpoint`） | `newProducer()` :142-158 不设 `transaction.timeout.ms`（仅守卫 transactional.id :152-156）；事务自首个 `invoke` 开启 :177-183 至下一 barrier `saveState` 才 commit+begin(next) fused :207-215；core `DEFAULT_CHECKPOINT_INTERVAL=60000L` :27 == broker 默认 transaction.timeout.ms 60s | Kafka 2PC epoch 事务存活期与 checkpoint 间隔耦合零裕度（60s==60s，事务年龄=interval+快照时长>60s 恒成立）——持续负载下必然触界进入「typed 失败→重启→超限→3 次后 job FAIL」循环 | 裁定⑦：①`newProducer()` 显式缺省 `transaction.timeout.ms`（≥数倍 checkpoint 间隔，取值上界受 broker `transaction.max.timeout.ms`=900s 约束的声明一次定夺，对照 Flink 同构场景默认 1h）；②部署校验/构造校验增加「checkpointInterval × 安全系数 ≤ transaction.timeout.ms」typed fail-fast（用户显式配置冲突时拒配不静默）；③owner doc kafka-2pc 行耦合声明（E5）；C10-014 新建断言随同 diff；merge-adjudication 修序建议 1 的「与 P0 C6-001 同批评估」已随 plan 2462 落地，本批为该建议的收尾半边 |

### 平台设施与测试资产盘点（live 实读 @ `c1674460dd`）

- **R01 族级 SSOT**（`ai-dev/design/concurrency/ledger-retention-policy.md`，plan 2502 产出 + plan 2510 追加两成员行）：统一参数集七项（retention-days 缺省 30 / cleanup-interval 缺省 1h 下限 60s / delete-batch-size 缺省 1000 / DB 表不设行上限逐出唯一判据 TTL / fail-fast 两层包络 / 键名 `nop.<module>.ledger[.<face>].*` / 时间锚列逐表显式）+ 形态 A 周期清理为机器速率写入面必须 + 周期链挂 `@PostConstruct`/`LifeCycleSupport.doStart` 且回归断言调度真实调用（防 1.6-C7-005 空壳先例）——C3-002 传导只落成员行，禁另发明。
- **R03 范式**（`ai-dev/design/concurrency/stream-buffer-unification-and-failover-flow-concurrency.md` §3.6，plan 2509 产出）：锁内重活外移 = 锁内仅状态机变更、I/O/投递出锁 + 出锁段信号序/identity 复检——C2-002 的「请求超时 + 锁外建连」为该范式在 HTTP 客户端形态的成员投影。
- **既有测试资产（回归门禁与改写对象）**：`TestRocksDBMetricsRecorder`（C10-002 假保护改写本体）、`TestRocksDBSnapshotRestore`/`TestStateFamilyBackendMatrix`（C5-002 round-trip 面回归钉+改写）、`TestCepOperatorSubtaskCopy`（C10-003 改写本体）、`TestAvroWireFormat`（C10-008 假保护改写本体）、`TestJdbcConcurrencyAndBatchSegments`/`TestJdbcTwoPhaseCommitLedgerNamespace`/`TestJdbcTwoPhaseCommitSinkDeep`（C3-002 面：前者改写、后两者恢复幂等钉——保留清理不得踩坏）、`TestFileTwoPhaseCommitSink`（manifest 面 + AR-14 并行锁回归钉）、`TestKafkaTwoPhaseCommitSinkLifecycle`（C8-006 调用序列/transactional.id 守卫钉——新增属性断言不弱化既有）、`TestDebeziumCdcSource`/`TestDebeziumCdcCheckpoint`（C10-013 双改写本体+恢复链钉）、gated `TestKafkaConnectorExactlyOnceE2E`/`TestParallel2PcJdbcE2E`（E2E 面）。
- **owner doc 现状**（`docs-for-ai/03-modules/nop-stream-connectors.md`，185 行，2026-10-29 亲读）：模块清单节 :7-14 仅列 connector/jdbc/debezium/batch 四行（kafka 由 :83「Kafka 原生连接器」节覆盖、avro 由 :107「格式层」节覆盖；**rocksdb 与 cep 的模块级表述缺位**——C1-002/C1-001/C5-002 修复后按需补节或登记 drift）；格式层 Avro 节 :107-117（:114「失败面全 typed」声明——C2-002 挂死形态下不成立，修复后补请求级超时与锁外加载表述）；能力矩阵节 :119-134（file/jdbc 2PC 行 :125/:128、Debezium REPLAYABLE 行 :130、kafka-2pc 行 :132 EXPERIMENTAL 豁免说明 :35——C8-001 修复后 offset 语义、C8-006 修复后事务超时耦合、C3-002 修复后台账/manifest 保留策略表述随批同步）；Debezium 引擎线程模型 :136-144 与停止契约 :145-157 两节（C4-004/C3-014 已修面，本批零改动）；恢复验证锚点索引 :175-184。**无保留策略节**——C3-002 落地后新增（以 `ledger-retention-policy.md` 为准的 ≤3 行摘要）。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `c1674460dd`，漂移均为引用粒度级、零实质漂移）**：C1-002 gauge 闭包审计区间 :83-101→live gauge 调用 :82-84、`readProperty` :91 起 ✓；C1-001 `copyForSubtask` 审计 :328-339→live :329-340 ✓、`NFA.open/close` 审计区间 :193-213→live open :193-204 / close :206-214 ✓、`createNFA` :1119-1121 ✓、`RichIterativeCondition` :47-53 ✓；C2-002 :48/:68-69/:116/:41/:55-60 零漂移 ✓；C3-002 :852-861/:748-752 零漂移 ✓（manifest 面：审计引方法体区间 :560-595，live 调用点 :346/:374，执行时以 live 重读方法体为准）；C5-002 `snapshotState` 入口 :84 ✓、`snapshotMapState` 审计区间 :199-251→live 方法头 :186、`snapshotKeyedState` 审计区间 :309-349→live 方法头 :284（区间为方法体，头行前移）✓、backend :119/:827 ✓；C8-001 :57/:68/:255-278（审计 :56-57/:255-279）✓、legacy function `snapshotState` live :431 起（审计 :376-391 为陈旧锚，以 live 重读为准） 未逐行复核（执行时 live 重读）；C8-006 方法漂移：`newProducer` 审计 :134-148→live :142-158、`ensureTransactionOpen` 审计 :167-173→live :158-175、invoke/saveState 审计 :185-214→live :177-215（saveState commit 段 :207-215）✓、`CheckpointConfig` :27 ✓。
2. **批增补覆盖核对**：batch-proposal R2-08 行增补共六段，对七成员中的六项（C1-002/C1-001/C2-002/C3-002/C8-001/C8-006）逐一有专段；**C5-002 无专段增补**——其收口经 §一 E2 通用门禁（强制并发回归测试+C10-012 同 diff）与裁定⑤承载，已在 Closure Gates 显式列项，无丢失项。
3. **C1-001 现状勘误（防双计）**：该发现与 r4（2026-09-30 质量审计）R5-CEP-05（P3 旧口径）同机制双登记——本 campaign 二轮复核维持 P1（并发口径触发时序已具体化到行级），cra-index L146 行已带「已知未修复←r4 R5-CEP-05」注记；本批修复后两登记一并消号，不重复立项。
4. **吸收成员对齐**：cra-index 七行括注的 ← 吸收成员（C2-001/C6-004/C1-005/C1-006/C3-003/C4-003/C6-008/C8-004/C7-006①/C5-006/C7-001/C8-007/C7-007①/C8-005）随主条目同 diff 收敛，Phase 4 回填以主 ID 行收敛+括注，不单独立行；C7-006 拆分保留的②面（回落/负缓存，MA-10）与 C7-007 的非 manifest 面已消号归并（merge-adjudication §3.4），不在本批。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-stream -am` 存在 nop-stream-connector-debezium 测试的 docker fork timeout 环境抖动（plan 2510 E3 第 1/2 轮即因该抖动重跑，非产品缺陷）。批内验证失败时按**「stash 基线同红 / solo 绿」三联裁定**处理：①`git stash` 后在基线重跑确认同红（环境项）；②单独重跑该测试确认绿（非本批改动引入）；③恢复改动复跑全量绿——三步留痕登记后方可继续，不得静默重跑或跳过。

## Goals

- Phase 1 设计裁定文档一次裁决七项裁定（独立 draft review 通过），无临场发明空间：①C1-002 gauge 锁内取值；②C1-001 copyForSubtask round-trip 三面封闭；③C2-002 R03 锁外建连+请求级超时（MA-04 对齐）；④C3-002 R01 保留策略成员传导（MA-05 对齐，双面同盖）；⑤C5-002 物化包络收敛形态；⑥C8-001 last-emitted 位点协议；⑦C8-006 事务超时缺省+耦合守卫——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 7 项成员全部修复并各带回归测试（rule 25）；C10 七条验收前置随本体同 diff 落地（含 C10-002/007/008/012/013 五处假保护/缺陷载体测试同 diff 改写）。
- 关键工单先红后绿或变异/回退论证留痕（E2）：红① gauge SIGSEGV 窗口红（close×scrape 交错压窗+锁内取参白盒断言，修复前窗口存在红）；红② copyForSubtask 三面红（并行 open 交错 ctx 错配断言+NFA 图实例/keyedStateStore 探针+assertSame 反转）；红③ 超时红（挂死注入——修复前以测试超时红线承载无界挂起，修复后 typed 失败+锁限时释放）；红④ 2PC 保留红（多 epoch 写入行数无界红→修复后保留清理+未到期保留+幂等守卫零回退，台账与 manifest 双面）；红⑤ offset 丢失红（队列偏斜注入，修复前快照 offset>last-emitted 红→修复后相等+恢复重放断言）；红⑥ 物化红（键数参数化堆驻留上界，修复前超界红）；红⑦ 裕度守卫红（缺省 producer 属性无 transaction.timeout.ms 断言红+耦合校验 typed fail-fast）。
- 对齐纪律显式守约（本批两条）：**MA-05（C3-002）= `ledger-retention-policy.md` 成员传导**——face 自定、零参数发明、SSOT §3.4 追加成员行（link, don't copy）、周期链接线断言防空壳、参数修订只走 SSOT；**MA-04（C2-002）= R03 锁内 HTTP 外移范式**——`stream-buffer-unification` §3.6 先例的成员投影（请求级超时+锁外建连），与 R4-24 外部依赖超时契约族互注不代裁。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-stream-connectors.md`（Avro 失败面表述、2PC 保留策略节新增、Debezium offset 语义、kafka-2pc 事务超时耦合、rocksdb/cep 表述缺口裁定）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-03（8.43）/R4-04（8.44）回写软依赖就绪注记（含 MA-16 保留策略族成员与 MA-13 配套可引用本批 C3-002 裁定④的声明、R4-24 的 C3-006 同案双轨不由本批承接的边界声明）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-03 成员**（G05+G09，12 项：1.2-MA-09/MA-10/MA-11/MA-13/MA-14/MA-15/MA-16/MA-17/MA-18 + 1.2-C8-002/C8-009/C8-012）及其 C10 面——软依赖本批，本批零承接零顺手修；特别地 **MA-10**（C7-006② Avro 回落/负缓存）与 **MA-11**（HttpClient close）为本批 C2-002 同文件簇后续工单（merge-adjudication 修序建议 2「一计划三工单」的②③）——本批裁定③只落①超时+锁外建连，为 MA-10/MA-11 立前提不代做。
- **不做 R4-04 成员**（G07+G08，11 项：1.2-C3-005/C3-006/C3-010/C3-011/C3-012 + 1.2-C6-005/C6-006/C6-007/C6-011/C7-003/C7-010）——同上；**1.2-C3-006 与 4.1-MA-10 同案双轨（R4-24，family-clustering §四#9）本批零承接声明**（failure listener 静态注册表串号面不触碰）。
- **不做 1.2-C10-016**（无界自旋测试缺陷，G91）——归治理批 R-G，本批零触碰 `TestBatchLoaderSourceFunction`。
- 不触碰 R4-24 实现域（REGISTRY 生命周期契约/NopStreamOffsetBackingStore 生命周期——C8-001 仅作对齐注记）、nop-message-debezium 引擎 commit 策略本体、`model/*.orm.xml`、`_` 前缀生成物；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-stream/nop-stream-rocksdb`：`metrics/RocksDBMetricsRecorder`（C1-002）、`RocksDBSnapshotSerDe` + `RocksDBKeyedStateBackend`（C5-002）+ 测试。
- `nop-stream/nop-stream-cep`：`operator/CepOperator` + `nfa/compiler/NFACompiler` + `nfa/NFA`（C1-001/MA-03 三面）+ 测试。
- `nop-stream/nop-stream-format-avro`：`AvroSchemaRegistryClient` + `AvroWireFormat`（C2-002）+ 测试。
- `nop-stream/nop-stream-connector-jdbc`：`JdbcTwoPhaseCommitSink` 台账保留面（C3-002）+ 测试。
- `nop-stream/nop-stream-connector`：`FileTwoPhaseCommitSink` manifest 裁剪面（C3-002②）+ 测试。
- `nop-stream/nop-stream-connector-debezium`：`DebeziumCdcSourceReader`（C8-001，legacy function 随面）+ 测试。
- `nop-stream/nop-stream-connector-kafka`：`KafkaTwoPhaseCommitSink`（C8-006）+ 测试；`nop-stream/nop-stream-core` `CheckpointConfig` 仅读（缺省值常量引用面，如裁定不改 core 缺省则零 diff）。
- 设计文档：`ai-dev/design/concurrency/stream-connectors-concurrency-and-envelope.md`（裁定①–⑦+测试缝+零回退钉，最终状态写法）；R01 SSOT `ledger-retention-policy.md` 增补成员传导行（裁定④——仅成员行追加，不改七项参数）；Owner doc：`docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/04-reference/source-anchors.md`。

### Out Of Scope

- Non-Goals 所列全部项；R4-03/R4-04/R4-24 及后续批次工单；nop-stream-runtime/core 的 R2-07 已修面（零回退引用）；nop-kernel/nop-api-core 平台落点（除非裁定触发 PA 申报流程）。

## Execution Plan

### Phase 1 - 连接器并发正确性与资源包络设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/stream-connectors-concurrency-and-envelope.md`（新建）；`ai-dev/design/concurrency/ledger-retention-policy.md`（成员传导行追加）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下七项裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① C1-002 gauge 锁内取值**：预设默认分支 = 新增 `synchronized readCurrent(property)`（锁内 `dbRef.get()`）+ gauge 闭包改调；与既有 close 顺序（recorder.close→db.close）的配对关系成文；备选 AtomicReference+异常兜 NaN 须论证前者不足后方可采。
  - [x] **裁定② C1-001 copyForSubtask 三面封闭**：预设默认分支 = round-trip 深拷贝 `nfaFactory`+userFunction（C1-001/C1-005/C1-006 一次封闭）；round-trip 代价评估（每 subtask 一次、启动窗）与序列化约束（条件对象 Serializable 前提核验）成文；`RichCompositeIterativeCondition` 传播面随拷贝自然私有声明；r4 R5-CEP-05 双登记消号注记。
  - [x] **裁定③ C2-002 锁外建连+请求级超时（MA-04=R03 对齐显式裁定项）**：超时缺省值定夺（含配置化与否）+ mapping function 出锁形态（锁外 fetch→putIfAbsent 幂等）+ 三路径（getSchemaById/getLatestSchema/registerSchema）全覆盖声明 + `StreamAvroException` typed 失败维持；与 MA-10/MA-11（R4-03）及 R4-24 外部依赖超时契约的边界注记（本批只落①面）。
  - [x] **裁定④ C3-002 R01 保留策略成员传导（MA-05 对齐显式裁定项）**：SSOT §3.4 追加成员行——face token 定夺（jdbc 台账/file manifest 分合）+ 两面锚列/谓词显式落名（台账=写入口时间戳列+守卫语义保留窗 ≥ 可重提交窗口；manifest=epoch 键账龄+裁剪载体[commit 顺带 vs 形态 A 周期]按 SSOT §3.3 判据裁定）+ 非裸 DELETE 守卫（幂等恢复不踩坏，`TestJdbcTwoPhaseCommitSinkDeep` 恢复幂等钉零回退）+ 本文档不复制参数值（link, don't copy）。
  - [x] **裁定⑤ C5-002 物化包络收敛**：三选一（流式写出/阈值 typed 拒绝/增量缺省化）预设默认分支一次定夺，组合取舍与 owner doc 数值包络声明成文；不得以纯文档声明零代码收口。
  - [x] **裁定⑥ C8-001 last-emitted 位点协议**：预设默认分支 = reader 侧已发射位点回退/截断；per-record offset 暴露路径（经 `DebeziumSourceSupport`/engine wrapper）vs 恢复期安全重叠重放+下游幂等声明，一次定夺；落点边界（connector-debezium 侧优先，不越界 nop-message-debezium 引擎 commit 策略本体；触 offset backing store 契约面则 R4-24 对齐注记）成文；C10-013 双改写方案。
  - [x] **裁定⑦ C8-006 事务超时缺省+耦合守卫**：`transaction.timeout.ms` 缺省值定夺（≥数倍 checkpoint 间隔、≤broker max 900s 声明）+ 耦合校验 typed fail-fast 的挂点（构造期/checkConnection）+ 用户显式配置冲突行为（拒配 vs 告警，一次定夺）。
  - [x] **裁定⑧ 测试缝与零回退钉清单**：测试缝 = close×scrape 交错压窗器（①）、并行 open 交错驱动（②）、挂死注入 HttpServer（③）、多 epoch 台账/manifest 写入器+保留窗口注错缝（④）、键数参数化+堆驻留探针（⑤）、队列偏斜注入+kill-recover 竞速缝（⑥）、producer 属性白盒断言+配置冲突注入（⑦）；零回退钉 = `TestRocksDBSnapshotRestore`、`TestStateFamilyBackendMatrix`、`TestJdbcTwoPhaseCommitSinkDeep`、`TestJdbcTwoPhaseCommitLedgerNamespace`、`TestFileTwoPhaseCommitSink`（AR-14 并行锁钉）、`TestKafkaTwoPhaseCommitSinkLifecycle`、`TestDebeziumCdcSource`（改写外其余用例）、`TestDebeziumCdcCheckpoint`（改写外其余用例）全列，改写类（C10-002/003/007/008/012/013）标注「改写后新断言即新钉、旧断言消号理由」。
- [x] `ledger-retention-policy.md` 成员传导行追加（裁定④）——仅追加成员行，不改七项参数与形态判据；若执行中发现参数确需修订，修订只走该文档并在本 plan 登记
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑧全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定④含 R01 SSOT 传导行落账证据（SSOT diff 仅为成员行追加，face token 与参数零发明）；裁定③含 R03 范式引用与族边界注记；裁定⑧含零回退钉全列与改写类消号理由
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 7 成员实现 + 回归测试（按模块/子域四组）

Status: planned
Targets: `nop-stream/nop-stream-rocksdb/src/`、`nop-stream/nop-stream-cep/src/`、`nop-stream/nop-stream-format-avro/src/`、`nop-stream/nop-stream-connector-jdbc/src/`、`nop-stream/nop-stream-connector/src/`（file 面）、`nop-stream/nop-stream-connector-debezium/src/`、`nop-stream/nop-stream-connector-kafka/src/`——各自 main + test 树

- Item Types: `Fix`（7 项均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 七条验收前置）

- [ ] 组 rocksdb：C1-002（gauge 锁内取值，按裁定①）+ C5-002（物化包络，按裁定⑤）
- [ ] 组 cep+avro：C1-001（copyForSubtask round-trip 三面，按裁定②）+ C2-002（超时+锁外建连，按裁定③）
- [ ] 组 2pc：C3-002（JDBC 台账+file manifest 保留，按裁定④）+ C8-006（事务超时+耦合守卫，按裁定⑦）
- [ ] 组 debezium：C8-001（last-emitted 位点，按裁定⑥）
- [ ] 回归测试（rule 25 显式覆盖 + C10 七条同 diff，先红后绿或变异/回退论证留痕）：
  - [ ] C1-002/C10-002 红①：close×scrape 交错压窗——修复前取参点在锁外（白盒断言红）→ 修复后取参点在监视器内、close 窗口 scrape 返 NaN 零 UAF 路径
  - [ ] C1-001/C10-003 红②：parallelism≥2 并行 open 交错——修复前共享条件 runtimeContext 错配（keyedStateStore 探针断言红）→ 修复后各 subtask 条件图实例不同、ctx 各自正确；`TestCepOperatorSubtaskCopy` assertSame 反转留痕
  - [ ] C2-002/C10-008 红③：挂死注入 registry——修复前请求无界挂起（测试超时红线承载）→ 修复后请求级超时 typed `StreamAvroException` + bin 锁限时释放（并发 encode 不互阻断言）
  - [ ] C3-002/C10-007 红④：多 epoch 写入——修复前台账行数/manifest 键数无界红 → 修复后过期行/键清理（台账与 manifest 双面）+ 未到期/未到期终态保留 + 恢复幂等守卫零回退（`TestJdbcTwoPhaseCommitSinkDeep` 保持绿）+ 清理体生产接线断言（若裁定④选形态 A——防 C7-005 空壳）
  - [ ] C5-002/C10-012 红⑥：键数参数化——修复前堆驻留随键数超上界红 → 修复后驻留有界（按裁定⑤形态断言）+ round-trip 语义等价零回退（`TestRocksDBSnapshotRestore` 保持绿）
  - [ ] C8-001/C10-013 红⑤：队列偏斜注入 + kill-recover——修复前快照 offset>已发射位点（断言红）→ 修复后 snapshot offset==last-emitted + 恢复重放在途 K 条不丢；`TestDebeziumCdcSource` 缺陷载体断言反转、`TestDebeziumCdcCheckpoint` 快照时点改回竞速窗内留痕
  - [ ] C8-006/C10-014 红⑦：缺省 producer 属性断言红（无 transaction.timeout.ms）→ 修复后缺省值在位 + checkpointInterval×系数>超时的配置注入 typed fail-fast
- [ ] **端到端验证**（rule 22）：≥1 条 2PC 链路 E2E（jdbc-2pc 或 kafka-2pc：写入多 epoch → checkpoint/恢复幂等 → 保留清理生效 → 作业恢复正确——覆盖裁定④⑦组合行为）；≥1 条 Debezium/连接器链路（offset 快照 → kill-recover → 重放零丢失，覆盖裁定⑥；gated 真库/broker 测试不强制入 CI，本地留证）
- [ ] **接线验证**（rule 23）：若裁定④含形态 A 周期清理——清理体被生产生命周期链真实调用断言（防空壳）；裁定⑥位点协议真实被 `snapshotState` 消费断言（非仅字段存在）；裁定③锁外路径真实被 encode/decode 调用断言
- [ ] **无静默跳过**（rule 24）：C2-002 超时 typed 失败而非静默重试；C8-006 配置冲突拒配而非告警放行（按裁定⑦）；C3-002 清理失败 ERROR+计数可见非吞掉（SSOT §3.1 第 5 项包络）；C8-001 位点不可得时显式失败而非回退旧 offset；新增公共分支无空方法体/TODO
- [ ] **提交纪律**：设计文档（+R01 SSOT 传导行）先行单独提交；按组提交——rocksdb / cep+avro / 2pc / debezium，C10 测试与对应本体同一 commit；组间无耦合时可独立提交序列
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 7 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 七条同 diff 落地（含五处假保护/缺陷载体改写）
- [ ] 必红证据（E2）：红①–红⑦全部先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：零回退钉清单全部保持绿（改写类以外钉零回改逐条 git diff 为空核对；`TestJdbcTwoPhaseCommitSinkDeep`/`TestFileTwoPhaseCommitSink`/`TestKafkaTwoPhaseCommitSinkLifecycle` 为恢复幂等/并行锁/调用序列关键钉）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-stream -am` BUILD SUCCESS（debezium docker fork timeout 环境抖动按 Baseline 勘误 5 三联裁定处理并留痕）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: `nop-stream` 聚合全量测试；`docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2510 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-stream -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`、`_` 前缀生成物、R4-03/R4-04/R4-24 批外成员站点、nop-message-debezium 引擎 commit 策略本体（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`nop-stream-connectors.md`——新增 2PC 保留策略节（以 `ledger-retention-policy.md` 为准的 ≤3 行摘要）、Avro 格式层节补请求级超时与锁外加载表述（:114 失败面声明修复后成立性）、能力矩阵Debezium 行 offset 语义同步（:130）、kafka-2pc 行事务超时耦合声明（:132/:35）、rocksdb/cep 模块表述缺口裁定（补节或登记 drift→按 docs 维护规则处理）；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-stream-connectors.md 保留策略/Avro/offset/事务超时各节更新 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/生成物/批外成员站点/nop-message-debezium 契约域）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.17 行、cra-index 台账、R4-03/R4-04 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（锁内取值/拷贝私有/锁外建连/保留清理/位点回退/守卫拒配运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填（顺带在 C3-014 行注记 residual 归属防歧义——plan 2503 已修但状态列因 MA-09 残余保持 open）：L145/L146/L147/L148/L151/L153/L154 七行状态收敛（open → fixed，注记 plan 2511；吸收成员括注随主行；C1-001 行 r4 R5-CEP-05 双登记消号注记）；C10 七条收敛注记（改写类注明假保护消号）；roadmap 8.17 行 Status → done；R4-03（8.43）/R4-04（8.44）软依赖 8.17 的就绪注记（沿 2510 对 R4-01/R4-02 先例——含 MA-16/MA-13 可引用裁定④、MA-10/MA-11 可引用裁定③前提、C3-006 同案双轨归 R4-24 边界声明）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 7 行收敛 + C10 七条注记、roadmap 8.17 = done、R4-03/R4-04 就绪注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 7 项成员全部修复：C1-002（gauge 锁内取值）、C1-001（copyForSubtask 三面封闭）、C2-002（请求超时+锁外建连）、C3-002（2PC 保留双面）、C5-002（物化包络收敛）、C8-001（offset 位点对齐）、C8-006（事务裕度守卫）
- [ ] batch-proposal R2-08 行 exit criteria 增补六段逐条达成并留证：①**gauge 锁外 dbRef 修复（SIGSEGV 窗口）**（交错压窗+锁内取参断言）；②**CEP copyForSubtask 三面封闭**（三面[条件图/ctx 槽位/udf 生命周期]断言+assertSame 反转）；③**Avro registry 请求超时+锁外建连**（挂死注入 typed+锁限时释放）；④**2PC 保留策略（R01）**（台账+manifest 双面保留清理按 SSOT 落地+SSOT 成员传导行在案+恢复幂等钉零回退）；⑤**checkpoint offset 对齐（≤256 条丢失面）**（偏斜注入+offset==last-emitted+恢复重放断言）；⑥**2PC 事务裕度守卫**（缺省超时在位+耦合校验 fail-fast）。C5-002 经 E2 通用+C10-012 承载（勘误 2 对应项）
- [ ] 对齐纪律守约：C3-002 保留参数/face/锚列与 `ledger-retention-policy.md` 零分叉（SSOT diff 仅为成员行追加，face 自定不复制 2510 两 face）；C2-002 锁外建连+请求超时与 R03 范式（stream-buffer-unification §3.6）同构声明在案
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑦均有先红后绿或变异/回退论证（E2）；C10 七条同 diff 落地（五处改写对照留痕）
- [ ] 设计文档裁定①–⑧成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-stream -am` BUILD SUCCESS（E1/E3；debezium 环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-stream-connectors.md + source-anchors.md（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）锁内取值/拷贝私有/锁外建连/保留清理接线/位点回退/守卫拒配运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-stream 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 7 行收敛 + roadmap 8.17 → done + R4-03/R4-04 就绪注记（E6）

## Deferred But Adjudicated

### R4-03 成员（G05+G09 连接器 MA 束+包络声明 12 项：1.2-MA-09/MA-10/MA-11/MA-13/MA-14/MA-15/MA-16/MA-17/MA-18 + 1.2-C8-002/C8-009/C8-012）

- Classification: `out-of-scope improvement`（batch-proposal R4-03 行显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-03 批 plan（roadmap 8.43，软依赖 8.17）
- Why Not Blocking Closure: 判级对齐零重复——cra-index P2 台账该 12 项批次列=R4-03；本批已收敛同单元 P1 主体，R4-03 实施以本批后的 live 形态为基线，且可引用本批裁定③（MA-10/MA-11 前提）与裁定④（MA-16 保留策略族成员/MA-13 配套）为既有裁定不重开体。

### R4-04 成员（G07+G08 并发正确性+失败路径泄漏束 11 项：1.2-C3-005/C3-006/C3-010/C3-011/C3-012 + 1.2-C6-005/C6-006/C6-007/C6-011/C7-003/C7-010）及其 C10-015

- Classification: `out-of-scope improvement`（batch-proposal R4-04 行显式安排）；Successor Required: `yes`；Successor Path: R4-04 批 plan（roadmap 8.44，软依赖 8.17）；C3-006 本体面归 R4-24 同案双轨（4.1-MA-10）
- Why Not Blocking Closure: 同上零重复——C3-006 失败监听静态注册表串号面本批零触碰；C10-015 随 C3-011 归 R4-04。

### 1.2-C10-016（TestBatchLoaderSourceFunction 无界自旋，测试自身缺陷）

- Classification: `out-of-scope improvement`（batch-proposal §五 G91 无本体 C10 条目→治理批 R-G）；Successor Required: `yes`；Successor Path: R-G 治理批
- Why Not Blocking Closure: 无在案缺陷本体（测试基建项）；本批零改动 `TestBatchLoaderSourceFunction`，登记于 Non-Goals。

## Non-Blocking Follow-ups

- **R4-03/R4-04 对接注记**：本批落地后向 roadmap 8.43/8.44 行作「8.17 就绪」完成注记（软依赖解除落点）；R4-03 实施引用裁定③④为既有裁定，R4-04/R4-24 的 C3-006 同案双轨边界已在 cra-index 注记。
- **裁定③超时缺省值与裁定⑦ transaction.timeout.ms 缺省值的运行期调优观测**：缺省值经本批测试面定夺后，生产负载下的最优值归运维观测（owner doc 运维节留观测指引即可，不阻塞 closure）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent，fresh session）
- Evidence: （每条 Exit Criterion 与 Closure Gate 的验证结果；`check-plan-checklist.mjs --strict` 退出码；Anti-Hollow 检查结果；scan-hollow 退出码）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
