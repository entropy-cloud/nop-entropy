# stream-connectors 指标身份、Avro 生命周期、CEP 上界与 2PC 守卫并发契约（CRA 8.43 R4-03 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立评审记录见「评审记录」节）
> Last Reviewed: 2026-11-07
> Source: plan 2536（[2536-cra-phase8.43-r4-03-connectors-ma-envelope.md](../../plans/2536-cra-phase8.43-r4-03-connectors-ma-envelope.md)，CRA Phase 8.43 R4-03 1.2 连接器 MA 束+包络声明 G05+G09，12 工单全 P2）；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/`（[merge-adjudication.md](../../audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/merge-adjudication.md) §2.1/§2.2/§3.1/§4.4/§五 + C2-locks-sync.md C2-011/C2-005 + C7-long-running.md C7-006 + C7-round2.md C7-012/C7-013 + C4-thread-pools.md C4-002 + C5-memory-bounds.md C5-001/004/005/008/009/010 + C8-backpressure.md C8-002/009/010 + C8-round2.md C8-012/013 + C10-test-protection.md §2 侧记/§4.4）；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.4 R4-03（:118）+ §一 E1–E6；既有裁定权威 = [stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md)（2511 裁定①–⑧）、[stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)（2510 裁定①–⑪）、[stream-core-resource-envelope-metrics-boundary-and-registry-lifecycle-concurrency-contract.md](stream-core-resource-envelope-metrics-boundary-and-registry-lifecycle-concurrency-contract.md)（2535 裁定 A–D）——三份均**仅引用不重建**；范式先例 = R01 SSOT [ledger-retention-policy.md](ledger-retention-policy.md) + R03 [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6
> Owners: 裁定 A（rocksdb gauge 观测域：W-01/W-08，含与 2511 裁定① 同点位零回退）、裁定 B（avro 生命周期域：W-02/W-03，含 2511 Follow-up 装配链接入归属落款）、裁定 C（上界旋钮族与 2PC/连接器域：W-04/W-10/W-05/W-06/W-11/W-12）、裁定 D（增长治理域：W-07/W-09，含 R01 对齐解读落款与 runtime 校验接线裁定）、裁定 E（测试缝总表：红①–红⑫ 驱动器 + 零回退钉全列 + C10 五条映射）。**裁定编号 = Phase 1 起草指令分组**（按缺陷主题域：指标身份 / avro 生命周期 / 上界旋钮族 / 增长治理 / 钉总表），与本 plan Goals 预设「按模块域分组」（A=rocksdb 全域/B=cep/C=avro/D=2PC）的映射见「工单→裁定映射与分组登记」节——12 工单裁定内容一一全覆盖，plan Phase 1 Exit Criteria 各内容项的承载节在该表可定位；分组调整随本登记生效（plan 侧回填归 plan 对齐纪律前置）。文件命名裁定：`stream-connectors-metrics-identity-avro-lifecycle-cepbounds-and-2pc-guards-concurrency-contract.md`（取代 plan Goals 拟名 `...-envelope-declaration-observability-and-lifecycle-bound-...`——「metrics-identity」覆盖 W-01/W-08 gauge 身份与告警语义面、「avro-lifecycle」覆盖 W-02/W-03、「cepbounds」覆盖 W-07/W-10、「2pc-guards」覆盖 W-04/W-06/W-11/W-12；命名调整登记义务归 plan 侧，沿 2535 文件命名裁定先例）。
> 行号锚 = live 基线 `3b3df13655`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值——plan 起草基线 `fe03951544` → 本文基线 `3b3df13655` 唯一 commit 为 docs(plan) 提交，`git diff fe03951544..3b3df13655 -- nop-stream` **零 diff** 亲证（12 工单站点文件抽验锚逐行命中，见 K1）；后续漂移以 live 为准并登记于 plan 勘误。与 plan 文本不一致的 live 核证事实集中登记于「plan 输入勘正（K 节）」，裁定以本文为准。

本文是 plan 2536 全部 12 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序论证）、被否决的备选、零触碰边界。三范式/溢出语义/保留参数结论引用不复制——SSOT 在既有设计文档。

## 既有裁定引用核对（引用不重建——批次行软依赖 8.17 已解除 + 对齐纪律「既有裁定引用条款」授权）

2511 裁定①–⑧（[stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md) §3.1–§3.9）、2510 裁定①–⑪（[stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md) §3.1–§3.13）、2535 裁定 A–D（[stream-core-resource-envelope-metrics-boundary-and-registry-lifecycle-concurrency-contract.md](stream-core-resource-envelope-metrics-boundary-and-registry-lifecycle-concurrency-contract.md)）为本批可引用既有裁定，引用边界逐条确认如下；本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。

| 既有裁定 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| 2511-① gauge 锁内取值（readCurrent :99-101） | W-01/W-08 同文件同点位（RocksDBMetricsRecorder） | **红线 1**：`readCurrent` 方法本体与 gauge 闭包取参点（:83-88 亲读——闭包已改调 readCurrent、注释四行 2511 产物）零改写；W-01 的 remove 接线在既有 `synchronized close()`（:65-67）上扩展，锁纪律零改写（close 内先 `dbRef.set(null)` 后 remove——detached 语义先建立）；C10-002 改写钉（testC1_002_* 压窗）零改写为并列运行 |
| 2511-② CEP copyForSubtask round-trip | W-07/W-10 同文件（CepOperator） | **红线 2**：copyForSubtask 链与 SHAREABLE_LEAF_POOL 区（:430-457）零触碰；`TestCepOperatorSubtaskCopy`（assertSame 反转钉）与 `TestCepOperatorLateRecordsDroppedMetric` 零改写——W-07 清理点/W-10 计数器与拷贝链零交互（reconciledTimerKeys 为 transient per-subtask 字段，不入序列化面） |
| 2511-③ Avro 请求级超时+锁外建连 | W-02/W-03 同文件簇（AvroSchemaRegistryClient/AvroWireFormat） | **红线 3**：请求级 `.timeout(requestTimeout)`（:79/:138）、get→miss→锁外 fetch→`putIfAbsent` 幂等回填（:96-101）、构造期 `≤0` fail-fast、挂死钉三件（TestAvroWireFormat :264/:323/:382）零改写；2511 Non-Goals 明文「裁定③只落①超时+锁外建连，为 MA-10/MA-11 立前提不代做」——本批即承接批，承接零重开 |
| 2511-④ 2PC 保留策略（face `epochledger`，R01 SSOT 成员） | W-04 配套前提 + W-06 同文件（JdbcTwoPhaseCommitSink/FileTwoPhaseCommitSink） | **红线 4**：JDBC 保留 cleaner 区（:449-519）、file manifest 保留窗口（:90-121/:189-210 computeRetentionEpochWindow +prune）零触碰；`TestJdbcEpochLedgerRetention_C3_002`/`TestFileTwoPhaseCommitSinkManifestPrune_C3_002` 零改写；W-04 上界旋钮键名**禁入** `nop.stream.ledger.epochledger.*` face 空间（非保留参数——独立 connector 参数域，键名裁定见 C.1）；「保留窗口 ≥ 可重提交窗口」幂等守卫语义零回退 |
| 2511-⑤ 增量缺省 true+规模阈值守卫 | W-05/W-09 基线前提（RocksDBKeyedStateBackend/RocksDBSnapshotSerDe 域） | **红线 5**：`incrementalCheckpointEnabled` 缺省 true（backend :125 亲证）与 `nop.stream.rocksdb.snapshot-materialize-max-entries`（缺省 100000，NopStreamRocksdbConfigs :32 亲证）零重开零触碰；`TestRocksDBSnapshotRestore` 键数参数化驻留钉 + 全量 opt-out leg 零改写；**applyMigration 族未被 2511 流式化**（2511 落地为缺省翻转+阈值守卫）——W-05 迁移 helper 为独立新面非 2511 面重开 |
| 2511-⑥ last-emitted 位点协议 | W-12 同文件前提（DebeziumCdcSourceReader） | **红线 6**：`LastEmittedOffsetTracker` 全链（:77 起/:120-121/:196/:250/:277-282）零触碰——W-12 字节界守卫只触 queue.put 面（:362）；`TestDebeziumLastEmittedOffsets_C8_001` 零改写 |
| 2511-⑦ kafka 事务裕度 | 全批零站点交集（无 kafka 站点） | 仅键名纪律引用：`nop.stream.connector.kafka.transaction-timeout-ms` 键形为 W-04/W-11/W-12 connector 参数键形的投影先例；其生产装配点归属裁定见 B.2（kafka 工厂面零触碰）；2511 全部钉（含 kafka 裕度钉 C10-014 改写钉）零改写 |
| 2511-⑧ 测试缝与零回退钉 | 全批测试面 | **红线 7**：2511 八钉（TestRocksDBSnapshotRestore/TestStateFamilyBackendMatrix/TestJdbcTwoPhaseCommitSinkDeep/TestJdbcTwoPhaseCommitLedgerNamespace/TestFileTwoPhaseCommitSink/TestKafkaTwoPhaseCommitSinkLifecycle/TestDebeziumCdcSource 改写外/TestDebeziumCdcCheckpoint 改写外）+ 改写类新钉（压窗/反转/挂死三件/驻留/保留双钉/last-emitted/裕度）钉内断言全批 diff 零触碰；挂死注入/压窗/计数断言形态为裁定 E 新钉的形态基准 |
| 2510-①–⑪（stream-core 十项） | 全批零站点交集（core/runtime 域） | 裁定⑪ 九测试缝惯例（monitor 探针/latch/手工桩/包内可见注入缝）与裁定② 参数纪律（缺省显式+负值装配期 typed fail-fast）为全批旋钮与红钉的形态先例；其七零回退钉零触碰零重开（零文件交集——核对义务登记于裁定 E 零回退钉全列） |
| 2535-A/B/C/D（8.42 四裁定） | 全批零站点交集（core/runtime 域） | **B.2「诚实来源」论证纪律**为 W-09 速率常数形态的对齐先例（显式声明、可配置、可校准的换算基 ≠ 静默虚构换算常数——2535 B.2 否决的是无来源虚构，本批 D.2 速率常数为公开声明形态，性质区分论证见 D.2）；A.2「升级出口 typed 可见」同族语义为 W-09/W-04 typed fail-fast 的引用先例；orTimeout 禁令/闩带 ≤30s/`_tmp/` 总册/同号防混纪律由裁定 E 全盘沿用 |
| 8.41 裁定 A–F | 零站点交集 | 1.2-C10-006 rocksdb 受害侧钉（`TestRocksDBKeyedStateBackendCrossThreadOps`）零触碰零重开（rocksdb 测试树本批唯一不改的既有并发钉）；TwoPhaseCommitSinkFunction :39 区 8.41 W-03 守卫注释零触碰（W-11 机制证据面） |
| R01 SSOT（[ledger-retention-policy.md](ledger-retention-policy.md) §3.1/§3.2/§3.4/§3.6） | W-07 对齐解读落款 + W-04 键名边界 | **W-07（MA-16「R01 保留策略对齐」注记的解读落款）**：MA-16 本体 = reconciledTimerKeys（merge-adjudication §2.1 :59 权威——**in-memory transient HashSet，非持久化台账**），「R01 对齐」按清理点设计纪律解读（有界/typed/观测钩子/参数命名惯例）——**预设零 SSOT 成员行**（§3.4 为持久化台账传导表，内存集合不入表；如后续裁定认为应落行则按 §3.4 link-don't-copy 追加并登记，本批不触发）；**W-04**：上界旋钮为缓冲准入参数非保留参数，键名不入 SSOT face 空间（红线 4），零参数发明、零 SSOT diff |
| R03 范式（[stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6） | W-04 转存面合规确认 | W-04 的 saveState 转存面（Jdbc :683-697）已由 2511 核对合规（锁内仅状态机变更——buffer→pendingCommits 为纯内存 map 变更，I/O 在 commit 面）——本裁定零改写该合规结论，仅引用；W-06 锁超时不涉锁内重活外移（锁内 I/O 面 = C2-006 P3 已声明边界残留，零触碰） |

**2511 Follow-up「装配链接入」归属裁定落款**（plan 对齐纪律第三条尾款 + 勘误 6「MA-16」括注笔误形态已登记）：`nop.stream.avro.registry-request-timeout` 的生产装配点 = **WI18 预留**（K10 亲证：生产零构造点+工厂零调用——构造器 Duration 注入已承载，无生产装配点可接线；WI18 连接器接线落地时随宿主 close 链一并接线，见 B.2）；`nop.stream.connector.kafka.transaction-timeout-ms` 的生产装配点 = kafka 工厂/descriptor 参数面（**非本批站点**——nop-stream-connector-kafka 零 diff 维持；构造参数注入面已由 2511 裁定⑦ 落地，descriptor 接线归 kafka 后续批/WI 域）。该 Follow-up 随本落款收敛，无本批 diff。

## plan 输入勘正（live 亲读核证 @ `3b3df13655`，裁定以本节为准）

| # | plan 2536 表述 | live 事实（file:line @ `3b3df13655`） | 对裁定的影响 |
|---|---|---|---|
| K1 | 起草基线 `fe03951544` | 本文基线 `3b3df13655`：`fe03951544..3b3df13655` 唯一 commit 为 docs(plan) 提交（plan 2536 起草+draft review R1），`git diff fe03951544..3b3df13655 -- nop-stream` **零 diff** 亲证 | 全部行号锚零漂移；勘误 2 的 live 回读值继续有效（本裁定对 12 工单站点锚逐行抽验命中：RocksDBMetricsRecorder :26-28/:52/:65-67/:83-88/:99-101/:108-118、backend :125/:319-320/:1145、strategy :63/:96、MapState :57-79、Avro :40/:69/:120-137、CepOperator :190/:1031-1036/:1165-1173/:1380/:1392-1398/:1294-1310、Jdbc :233/:542-543/:710-758/:961/:975-991、File :107/:156/:523-536、双桥 :53/:85/:222-225/:59/:140/:362、CheckpointConfig :27-28/:64/:271-290、TwoPhaseCommitSinkFunction :187/:296-299——全部命中） |
| K2 | W-09 裁定项「estimate-num-keys prop 已有 PROP :45」未带文件限定 | `PROP_ESTIMATE_NUM_KEYS = "rocksdb.estimate-num-keys"` 位于 **RocksDBMetricsRecorder.java:45**（非 CheckpointConfig） | 估算器属性常量的唯一归属 = recorder；CheckpointConfig :45 实为 `DEFAULT_ASYNC_SNAPSHOT_THREAD_POOL_SIZE`（K3 防混）；本批 D.2 校验载体为**声明分支**（CheckpointConfig 字段），runtime-prop 估算分支不设（F10 分支绑定见 D.2——该属性常量本批零消费零触碰） |
| K3 | 勘误 2「CheckpointConfig failures 审计 :34 → live :64」 | 亲证命中：:64 = `maxConsecutiveCheckpointFailures` 字段行；:27-28 = DEFAULT_CHECKPOINT_INTERVAL/TIMEOUT（零漂移）；:45 = `DEFAULT_ASYNC_SNAPSHOT_THREAD_POOL_SIZE` | W-09 锚维持；:45 与 estimate-num-keys 无关（防混注记） |
| K4 | W-09「校验挂点[构造期 vs validateUnalignedConfig 同构 validate 方法]」载体未指明调用点 | `validateUnalignedConfig`（CheckpointConfig :271-290）的**唯一生产调用点 = runtime `GraphModelCheckpointExecutor` :229/:367**（core main 源零生产调用——grep 亲证；core 测试 TestCheckpointTransportPersistenceValidation 直调形态为测试先例）；LOCAL 执行路径无 validate 漏斗（先例既有限界） | D.2 校验方法与其同构 + 接线随先例（runtime 两调用点各并列一行——触 runtime 2 行 diff，PA 表预设允许形态「触 runtime 则为同聚合器 impl 叶，diff 分组登记」；对 plan 头部「runtime 零 diff 为预设」的**裁定级偏离登记**见 D.2——不接线即死代码，Anti-Hollow 红线） |
| K5 | W-12「桥元素」类型未核证 | 亲证：`MessageSourceReader.QueueSourceContext implements SourceFunction.SourceContext<T>`（:218 区，T 为 legacy 函数泛型——任意 Object 载体，MessageSourceFunction :160 `ctx.collect((T) msg)`）；`DebeziumCdcSourceReader.QueueSourceContext implements SourceFunction.SourceContext<ChangeEvent>`（:358 区）——ChangeEvent 载体 = `metadata/operation/before/after/key/timestamp`（before/after/key 为 `Map<String,Object>` 对象图，非原生 byte[]） | W-12 测长口径的 live 事实基础：双桥 put 点均无原生字节载体——测长=「字符串/字节载体直测下界」口径（唯一分支见 C.6），非字节主导 payload 的残余面由 owner doc 包络声明承载 |
| K6 | 错误码复用载体未指名 | `NopStreamErrors`（core，手写保留文件）亲证在位：`ERR_STREAM_CHECKPOINT_ERROR` :67 / `ERR_STREAM_STATE_ERROR` :70 / `ERR_STREAM_INVALID_ARG` :73 / `ERR_STREAM_JOB_EXECUTE_FAILED` :167 | W-06 锁超时 typed 码 = `ERR_STREAM_CHECKPOINT_ERROR`（审计 C2-005 建议原码在位）；W-04 水位越界 = `ERR_STREAM_STATE_ERROR`（C5-005/C5-010 审计建议原码在位）；W-12 超限/构造期非正值 = `ERR_STREAM_INVALID_ARG`；W-11 超时不新增码（语句超时失败经既有 SQLException→typed 传播面）。零新码预设成立（条件申报④ 零触发的直接依据） |
| K7 | W-12 形态标杆「FileSourceReader maxLineBytes」违约形态未核证 | 亲证：`DEFAULT_MAX_LINE_BYTES = 16MB`（:60）、构造参数承载（:74/:130）、违约 = **typed IOException + 解释性消息**（:368-371「fail fast instead of buffering an unbounded line; fix the input or raise the limit」） | C.6 唯一分支的形态标杆语义 = 构造参数 + 超限 typed fail-fast + 处置指引消息——W-12 同形（差异仅载体测长口径，见 K5） |
| K8 | 配置键载体惯例未核证 | 三模块键载体形态亲证：rocksdb = `NopStreamRocksdbConfigs`（AppConfig.varRef，`snapshot-materialize-max-entries` :32）；cep = `NopCepConfigs`（varRef，`nop.cep.pipeline.sharedbuffer.cache.statistics-interval` :44）；connector-jdbc = `EpochLedgerConfigs`（varRef，`nop.stream.ledger.epochledger.*`）+ 工厂 `ConnectorParamDescriptor.optional`（JdbcTwoPhaseCommitSinkConnectorFactory :42-51） | C.1（connector 参数域=构造参数+descriptor optional）、C.2（cep=varRef）、D.2（checkpoint 字段 POJO 形态=CheckpointConfig :64 先例；rocksdb 哈希旋钮=NopStreamRocksdbConfigs varRef）的载体裁定依据 |
| K9 | W-07 清理点形态依据未核证 | 亲证：`reconciledTimerKeys` = **transient**（:190——restore 后集合归零，全部 key 首次 drain 重对账一次=既有 per-instance 生命周期语义）；`resetNfaStateIfFullyTimedOut`（:1294-1310，全清判定 + `computationStates.clear()` :1310）；`elementQueueState.remove(timestamp)` 桶移除点 = drainDueBuckets 内 **:1380** | D.1 清理点锚 = resetNfaStateIfFullyTimedOut 完成全清后同点位 remove(currentKey)；幂等安全性论证的既有事实 = reconcile 只在首 drain 补登记（ledger 已空则重对账零副作用）+ transient 归零重对账为既有语义（清理点不引入新对账形态）；C.2 计数器递减锚 = :1380 桶移除点 |
| K10 | F3「两类生产零构造点」未核证工厂调用面 | 亲证强化：`new AvroWireFormat`/`new AvroSchemaRegistryClient` main **零命中**（仅 TestAvroWireFormat 14 处[F-3 勘正]）；**工厂调用面亦零命中**——`avroDeserializer(`/`avroSerializer(` main 零调用（仅 TestAvroWireFormat :405-406） | B.2「生产零构造点+生产零工厂调用」双亲证——宿主接线/装配点全部归 WI18 预留的裁定前提成立；红③ 驱动器由测试缝承载接线验证（rule 23 例外形态，plan 红③ 预设） |

## 工单→裁定映射与分组登记

| 工单（finding ID） | 裁定 | 承载节 | plan Goals 预设分组对照 |
|---|---|---|---|
| W-01（MA-09=C2-011） | A.1 | gauge tag 唯一化+close 摘除 | plan-A（rocksdb 域）同点位 |
| W-08（MA-17=C7-012） | A.2 | WARN once-guard | plan-A 同点位 |
| W-02（MA-10=C7-006②） | B.1 | 回落语义+负缓存退避 | plan-C（avro 域） |
| W-03（MA-11=C4-002） | B.2 | close 生命周期+fd 回归 | plan-C 同域 |
| W-04（MA-13=C5-005+C8-003/C5-010） | C.1 | 2PC 上界旋钮族 | plan-D（2PC 域） |
| W-10（C8-002 独立案） | C.2 | CEP 匹配缓冲上限+包络声明 | plan-B（cep 域） |
| W-05（MA-14=C5-004+C5-008/C5-009） | C.3 | 流式迁移 helper 一修盖 4 类 | plan-A（rocksdb 域） |
| W-06（MA-15=C2-005+C3-008） | C.4 | manifest 锁超时 | plan-D 同域 |
| W-11（C8-009 独立案） | C.5 | JDBC 语句级超时 | plan-D 同域 |
| W-12（C8-012 独立案） | C.6 | 桥字节界 | plan-D 同域 |
| W-07（MA-16=C5-001，C7-005 并轨消号） | D.1 | timerKeys 清理点 | plan-B（cep 域） |
| W-09（MA-18=C8-010+C8-013） | D.2 | 预算耦合守卫+哈希旋钮 | plan-A（rocksdb 域） |
| 红①–红⑫+零回退钉+C10 映射 | E | 测试缝总表 | plan-E 同位 |

## 裁定 A rocksdb gauge 观测域（W-01/W-08）

### A.1 W-01：gauge tag 唯一化 + close 摘除（MA-09=C2-011，并入 C6-010/C7-004/C3-013）

**决策**：**「站点内实例身份 tag + close 逐 Id 摘除」唯一分支**（F4 预设限定站点内形态），零 API 触面：

1. **tag 键形**：`RocksDBMetricsRecorder` 新增进程内单调序号源（static `AtomicLong RECORDER_SEQ`）；`register(MeterRegistry)` 时 6 个 gauge 的 Meter.Id 统一追加单标签 `backend`，值 = `String.valueOf(RECORDER_SEQ.incrementAndGet())`（每 recorder 实例一个序号，6 meter 共享同值）——per-subtask backend 并发注册产生互异 Id，micrometer first-wins 去重不再吞注册。tag 键形纪律与 `CepOperator` drop counter（R5-CEP-03 正例，operator/subtask 双标签）同族——本裁定为**实例身份维**（backend 无 TaskLocation/subtaskIndex 可达性亲证=F4 前提），operator/subtask 维为 follow-up 注记（见「零触碰边界」W-01 follow-up 条）。
2. **remove 载体 = Meter.Id 集**（非 Meter 引用）：`register()` 构造 6 个 Id 并持实例字段 `List<Meter.Id> registeredIds` + 持 `MeterRegistry registry` 引用（register 传入时赋值，null 安全——register 未调则 close 只置 null 不摘除）；`close()` 在既有监视器内扩展为：先 `dbRef.set(null)`（2511-① detached 语义零改写——红线 1），再逐 Id `registry.remove(id)`（幂等；Id 不存在时 remove 为 no-op）。
3. **Id 复用语义声明**：close 摘除释放 Id 空间；序号单调不复用——同进程内任意时刻存活的 recorder Id 互异，重启 rebind（close 后新 open）产生新 Id，去重与幸存 NaN 双面结构性消除。

**理由（触发时序论证）**：缺陷时序（C2-011 面①）——T1 subtask 0 任务线程 backend.open 注册 6 gauge（Id=固定名+空标签）→ T2 subtask 1 register 同 Id → micrometer 命中已有 meter 静默丢弃新 valueFunction → 1..N-1 subtask 永久不可观测；面②（零并发确定性）——任一 backend close `dbRef.set(null)` → 幸存 gauge 每次 scrape 读 null 恒 NaN → rebind 同 Id 再去重 → 进程存活期不可自愈。post-fix：T2' subtask 1 携 `backend=r2` Id 注册 → 12 Id 并存（parallelism=N 时 6N 可见）；close 后 registry 无幸存 gauge（scrape 面零 NaN 幸存——非「读 NaN」而是「meter 不存在」，监控侧从「指标恒 NaN 误判无状态」变为「meter 消失=backend 不在位」的诚实信号）。并发面：register 的 `registry.gauge(...)` 为 micrometer 原子 check-then-put（审计 C2-011 现状确认无数据竞争）——tag 唯一化后并发注册各建各 meter，零新锁；close 在既有监视器内 remove，与 scrape（readCurrent 监视器）互斥延续 2511-① 锁纪律。

**被否决的备选**：
- **TaskLocation/operator 注入链形态（F4 半径：IStateBackend+MemoryStateBackend+4 operator 调用点）**：identity 语义更富（subtask 归因）但触 core 模块 In-Scope 外文件（`IStateBackend.createKeyedStateBackend(Class)` 签名扩展或 setter + `MemoryStateBackend` + ProcessOperator:86/EquiJoinOperator:216/OverWindowOperator:155/WindowOperator:658-660 四调用点——F4 亲证半径），且 2510-① 确立的 `createKeyedStateBackend(Class)` 契约面波动；缺陷本体（Id 恒等+无摘除）实例 tag 已闭合，operator/subtask 归因是增强非本体——**不采为预设；如实施期改采须走「条件申报登记」节 F4 预登记条款（四件证据链），触面清单已在该节预登记**。
- **`identityHashCode` 实例 tag**：identityHashCode 不保证互异（可碰撞），GC 后复用产生 Id 归属歧义——单调序号确定性占优。
- **jobId tag**：backend open 期 jobId 不可达（2535 K4② 同款事实——LOCAL jobId 于 execute 期产生，装配期不可达），且跨作业重启同 jobId 复用须依赖 remove 时序——序号无此依赖。
- **Meter 引用集**（`registry.remove(Meter)`）：行为与 Id remove 等价（micrometer 按 Id 匹配），但 recorder→Meter→valueFunction→recorder 构成引用环（GC 诊断面劣化）；Id 集（String+tags 值对象）无环——Bookkeeping 更轻。
- **registry 级 removeMember**（StreamMetricsRegistries :55）：registry 级 API 移除整个 member registry——recorder 非 registry 持有者，载体文件预设零 diff 维持（plan PA 表行 1 口径）。

**零触碰边界**：`readCurrent`（:99-101）与 gauge 闭包取参点（:83-88 含 2511 注释四行）零 diff（红线 1）；`StreamMetricsRegistries`（core 载体）预设零 diff（remove 走 meter 级 API）；`TestRocksDBKeyedStateBackendCrossThreadOps`（8.41 受害侧钉）零触碰；backend 接线锚（:319-320 register/:1145 close）只承载 recorder 内部形态变化，backend 文件零 diff 为预设（tag/remove 全在 recorder 类内）；C10-002 既有压窗钉零改写并列。

### A.2 W-08：WARN once-guard（MA-17=C7-012，并入 C2-008；与 A.1 同点位一改同批——修序建议 4 绑定）

**决策**：**per-recorder per-property `Set<String>` 守卫**唯一分支：

1. 实例字段 `private final Set<String> warnedProperties = new HashSet<>();`（readProperty 全路径 synchronized——普通 HashSet 监视器内安全，零新并发原语）；`readProperty` catch 块（:108-118，WARN 点 :115）：`if (warnedProperties.add(property)) { LOG.warn(...既有消息原文...); } else { LOG.debug(同消息); }`——每 recorder 实例每属性恰一次 WARN，命中后降 debug。
2. **「告警一次」声明兑现口径**：`nop-stream.md` :91 声明（「个别属性…该 gauge 值为 NaN 并告警一次」）读作 per gauge/属性生命周期（每 gauge 实例一次）——实现兑现声明语义，**声明侧零改动为预设**；E5 核对登记该口径（如 Phase 3 判声明需精确化「每 recorder 实例每属性一次」为可选精确化，非义务）。
3. **粒度裁定理由**：per-recorder AtomicBoolean 不采（首属性失败即封口，第二属性失败被掩蔽——6 属性部分可用形态下失真）；per-process static Set 不采（跨 job 状态残留 + rebind 后新 backend 的属性失配不再告警=日志形态失明面回归——见下）。

**理由（时序与组合论证）**：缺陷时序——构建缺属性为持久性特征（javadoc 自认 varies by version）→ 每 scrape（15s）× 失败属性数条 WARN 无衰减（240 条/时、5,760 条/天——审计算术修正口径；merge-adjudication §3.1 P3 异议已记录并终裁 P2 维持，本裁定知悉该异议、判级不重开）。与 A.1 组合语义（「先刷屏后失明」消解）：post-fix scrape 只触存活 recorder 的 gauge——close 摘除后旧 recorder 零 scrape 触达（A.1 面②的「失明」变为 meter 消失），幸存 recorder 的 WARN 由 once-guard 封口为一次；rebind 后新 recorder 属新失败上下文，再告警一次=正确语义（static Set 会把 rebind 告警也吞掉——故不采 static）。WARN 率从「∝ scrape 频率×时长」收敛为「∝ backend 实例生命周期数」——有界于 job 生命周期速率。

**被否决的备选**：per-recorder `AtomicBoolean`（粒度失真见上）；static 进程级 Set（rebind 失明 + 跨 job 残留）；窗口限频（每 N 分钟一条——仍在放大，且引入计时器状态；「告警一次」声明语义是 once 非限频）。

**零触碰边界**：A.1 全部边界继承；`readProperty` 的 null 短路分支（`db == null → NaN`）与返回值语义零改写（NaN detached 语义=2511-① 面）；C7-005 型「与 C7-004 组合」历史表述随 A.1 摘除面消解——不另立工单；`TestRocksDBMetricsRecorder` 既有 4 用例与压窗钉零改写（红⑧ 为追加用例）。

## 裁定 B avro 生命周期域（W-02/W-03）

### B.1 W-02：readerSchemaFor 回落语义 + 负缓存退避（MA-10=C7-006②，并入 C3-007；2511-③ ①面的②③尾项承接）

**决策**：**「负缓存窗口内回落[F-1 补：subject-missing（HTTP 404 合法首次写竞态）类 carve-out——免 WARN 或 per-subject 生命周期一次；否则「subject 未注册稳态按窗口限频 WARN=预期信号、竞态自愈于一个 TTL」须在 E5 声明] + 限频 WARN」唯一分支**（三选一定夺）：

1. **负缓存载体**：`AvroSchemaRegistryClient` 实例字段 `ConcurrentHashMap<String, Long> readerSchemaFailures`（subject → 失效期 epoch millis）；`AvroWireFormat.readerSchemaFor`（:120-127）改形：
   - 查负缓存：命中且未过期 → **直接回落 writerSchema**（零 registry 调用、零新增 WARN——限频）；
   - miss/过期 → 清过期条目 → 调 `getLatestSchema(subject)`（既有请求级超时+锁外建连路径零改写——红线 3）：成功 = 演进读取恢复（负缓存条目清除）；失败（StreamAvroException）= 记条目（`now + ttl`）+ **WARN 一次**（每 subject 每窗口恰一次，消息含 subject 与回落语义声明）→ 回落 writerSchema。
2. **TTL 与配置键**：约定配置键 `nop.stream.avro.reader-schema-fallback-ttl`，缺省 **30000ms**（30s），`≤0` 构造期 fail-fast（2511-③ 参数纪律精确形态——不设「0=禁用」例外）；配置化经构造器 Duration 参数承载（客户端纯构造注入对象，不直读全局配置——2511-③ 同形态）。
3. **回落语义声明（owner doc :118「不静默降级」对齐方式）**：decode 侧 reader schema 演进读取在 registry 故障窗口内回落 writer schema（wire 解码成功、reader==writer），降级**非静默**（窗口边界处 WARN 一次）且**有界**（窗口内零 registry 打击=退避本体）——E5 将 :118 声明精确化为「失败面全 typed（registry 不可达、请求超时、magic、解析失败）；decode 侧 reader schema 演进读取为唯一声明回落面：负缓存窗口内回落 writer schema + 每窗口限频 WARN」。

**理由（触发时序论证）**：缺陷时序——T1 registry 故障（挂死/拒连/5xx）→ T2 每条 decode 记录走 `readerSchemaFor` → catch StreamAvroException 静默回落 → 每记录一次失败调用（吞吐塌陷零 error 信号）+ schema 演进读取静默跳过（owner doc :118 decode 侧残差亲证）。post-fix 时序——T2' 首条失败：一次 registry 调用（≤10s 超时界）→ 条目+WARN+回落；T3' 后续 N 条：负缓存命中 → 零调用零信号噪声（吞吐塌陷的**调用放大**消除，回落本身维持可用性）；T4' TTL 到期：恰一次重试探底 → registry 恢复则演进读取自动恢复（自愈出口），仍故障则重臂窗口。**为何不采纯 typed**：registry 瞬时故障（秒级抖动）会把在跑作业打成「任务失败→重启→仍失败→3 次后 job FAIL」循环——正是本 campaign 反复闭合的触界失败循环类（2511-⑦ C8-006 同型论证）；回落 writer schema 是数据兼容形态（非丢数据），可用性维持 + WARN 可见 + 窗口有界是缺陷两半（调用放大/静默）的精确闭合。**为何 typed 失败传播与负缓存不矛盾地统一在本分支**：typed 面维持于「负缓存窗口之外」——窗口边界处的每次失败仍经 `getLatestSchema` typed 抛出后在本点位显式处置（记条目+WARN+回落）——失败信号可见（非 catch 后无声），rule 24 无静默跳过（catch 块三动作显式）。

**被否决的备选**：
- **纯 typed（回落全删）**：见上——瞬时故障即作业失败循环，可用性回退大于可见性收益；owner doc :118 可经 E5 精确化对齐，无需以作业可用性为代价换取声明字面一致。
- **typed 失败传播（任务失败）+ 负缓存并存**：负缓存在任务失败语义下无消费场景（首次失败即任务终局），退避半边落空——两步语义只有本分支能同时兑现（merge-adjudication MA-10 行「typed 失败传播+负缓存退避」的统一实现=本分支：失败信号传播可见[WARN/typed 边界处置]+退避[负缓存]）。
- **TTL 缺省 5s/60s**：5s 对 registry 故障窗口（分钟级）退避不足（重试风暴半残）；60s 使恢复探测滞后放大（registry 恢复后演进读取盲区拉长）——30s 为退避收益与恢复滞后的中位点，配置化允许两侧调整。
- **指数退避**：窗口重试频次=每 TTL 一次已够稀疏；指数退避引入状态机复杂度且「告警一次/窗口」语义失配——不采。
- **负缓存复用 schemaByIdCache 载体**（C7-008 P3 相邻面）：负缓存是失败态（subject→expiry），与成功态 id→schema 缓存语义异构——分开承载，C7-008 本体零顺手修（负缓存条目基数=subject 数，与 writerSchemaIds 同阶声明有界——非 C7-008 的无界形态）。

**零触碰边界**：红线 3 全集（.timeout/putIfAbsent/挂死钉三件）；`getLatestSchema` 本体与三路径共享 send 面零 diff（负缓存只包络 readerSchemaFor 调用位）；encode 侧（registerSchema/writerSchemaIds）零触碰；C3-007（回落吞异常面）随本裁定同 diff 收敛（cra-index 随 W-02 主 ID 括注）。

### B.2 W-03：HttpClient close 生命周期（MA-11=C4-002，并入 C7-013；R08 族对批注记）

**决策**：**「AutoCloseable + 透传 + 工厂 javadoc 生命周期归属（调用方义务）；宿主接线降 WI18 预留」唯一分支**（F3 事实输入预设）：

1. `AvroSchemaRegistryClient implements AutoCloseable`：`close()` 直调 `http.close()`（JDK HttpClient AutoCloseable——Java 25 工程面）；javadoc 声明「关闭 selector 线程与连接池；幂等由 close 调用方纪律保证（红③ 循环断言覆盖）」。
2. `AvroWireFormat implements AutoCloseable`：透传 `client.close()`（wire-format 持有其构造注入的 client——单一持有者语义）；两工厂 `avroDeserializer(subject)`/`avroSerializer(subject)`（:131-137）javadoc 增补生命周期归属声明：「返回函数闭包持有本 wire-format 与其 registry client——装配持有方（job 装配/测试宿主）负责在生命周期终点调用 `AvroWireFormat.close()`；WI18 连接器接线落地时由连接器宿主 close 链接线」——**调用方义务 = 声明形态**（生产零构造点+零工厂调用亲证=K10，义务今天是文档契约非运行链路）。
3. **宿主接线 = WI18 预留裁定项**：本批零宿主 diff（Kafka 连接器零构造点——无接线点可接）；WI18 落地时宿主 close 链接线 + `registry-request-timeout` 生产装配点随同一工单承接（2511 Follow-up 归属落款见引用核对节）。红③/rule-23 接线验证由**测试缝承载**（plan 红③ 预设形态）：装配-停止循环断言证明 close 通道真闭合线程/fd，宿主接线的生产链路验证随 WI18（本批 Anti-Hollow 面 = close 通道存在且语义正确，非生产调用链——零构造点下生产调用链不存在）。
4. **fd/线程计数回归 = R07 先例复用形态**（批次行增补明文）：参照 plan 2522 `TestFileDownloadSinkClose` 形态（fd 计数 delta 有界断言），**复用形态不复制文件**（4.3 通道与本批零交集）；红③ 主断言 = selector 线程枚举 before/after 零净增（线程名形态实现期按 JDK HttpClient selector 线程名核对）+ fd delta 有界并列。

**理由（时序论证）**：缺陷时序——T1 装配线程构造 wire-format→client→HttpClient（selector daemon 线程启动）→ T2 生命周期终点无任何 close 路径 → daemon selector + keep-alive 连接表常驻至进程退出 → 每轮 Avro 作业滚动 +1 组（C4-002 口径：1 daemon selector + 短命 executor 自灭 + 连接池）。post-fix：close 通道存在 → 装配持有方按 javadoc 义务在终点关闭（WI18 落地后为宿主链自动接线）→ 线程/fd 收敛有界。**F3 的裁定含义**：缺陷今天是「潜伏面」（无生产构造点=零生产泄漏），但 wire-format 是 owner doc 声明的跨连接器格式层（WI18 为既定消费面）——修复在 WI18 落地前补齐生命周期通道，使 WI18 无从带病接线；这也是 2511-③「为其立前提」叙事的收尾半边。

**被否决的备选**：
- **本批实施宿主接线（虚构调用链）**：生产零构造点下无宿主可接——接线路径只能发明调用方=凭空造生产代码（禁）；WI18 预留是唯一诚实形态（plan F3 预设）。
- **finalizer/Cleaner 兜底**：GC 时机不确定 + daemon 线程强引用使对象常驻（泄漏本体反而抑制 Cleaner 触发）——禁路线。
- **静态共享单例 client**：改装配语义（per-registry baseUrl 隔离丧失）+ 全局生命周期（与 job 生命周期错配）——超工单面。
- **fd 回归另立专项**：批次行增补明文复用 R07 先例——形态复用即承接，不另立。

**零触碰边界**：红线 3 全集；`AvroWireFormat` encode/decode 本体与 B.1 负缓存面零交叉（同文件两工单 diff 分组登记——B.1 触 readerSchemaFor/client 负缓存字段，B.2 触类声明+close+工厂 javadoc）；连接器宿主（kafka 等）零 diff；`TestAvroWireFormat` 既有 8 用例零改写（红②③ 为追加）。

## 裁定 C 上界旋钮族与 2PC/连接器域（W-04/W-10/W-05/W-06/W-11/W-12）

### C.1 W-04：2PC sink 上界旋钮族（MA-13=C5-005+C8-003/C5-010；与 2511-④ 配套）

**决策**：**「双旋钮 × 双容器 × 双载体 typed fail-fast」唯一分支**（一单工单同盖双容器——merge-adjudication MA-13 行明文）：

1. **旋钮与缺省值**（四键，双载体同名同缺省——一致性声明同 plan W-12 双桥一致性口径）：
   - `JdbcTwoPhaseCommitSink`：`maxEpochRecords`（构造参数，缺省 **100000**，epoch 内 currentBuffer 条数上界）+ `maxPendingEpochs`（构造参数，缺省 **8**，pendingCommits 跨 epoch 堆叠上界）；
   - `FileTwoPhaseCommitSink`：`maxEpochLines`（缺省 **100000**，行缓冲上界）+ `maxPendingEpochs`（缺省 **8**，pendingCommits 描述符堆叠上界——每条目钉一个 tmp 文件，堆叠界即磁盘面界）。
   - `≤0` 构造期 typed fail-fast（`ERR_STREAM_INVALID_ARG`，ARG_NAME param——maxBatchSize :324-325 同形态）；载体 = 构造参数 + 连接器 descriptor `ConnectorParamDescriptor.optional` 声明（jdbc 工厂 :42-51 形态先例）+ jdbc builder fluent 方法；**键名归 connector 参数域，禁入 `nop.stream.ledger.*` face 空间**（红线 4——非保留参数，R01 SSOT 零 diff）。
2. **检查点位**：epoch 缓冲——`invoke` 的 `synchronized(currentBuffer)` 块内、add 前判定（与 add 同点位原子判定，防超限竞态多塞；检查零新锁——复用既有监视器）；跨 epoch 堆叠——`saveState` 转存前判定（Jdbc :683-697 区 / File :315-326 区）。
3. **超限行为 = typed fail-fast**（`ERR_STREAM_STATE_ERROR` + param：当前水位/上限/epochId/处置指引「调小 checkpoint 间隔或上调上限/排查 commit 通知链路」）——拒绝不入列/不转存，任务可见失败。

**理由（sizing 与时序论证）**：epoch 缓冲缺省 100000——maxBatchSize 缺省 1000 执行侧分段（构造校验亲证 :324-325）意味着单 epoch 合法体量以万计；100k 条 ≈ 缺省 60s interval × ~1.6k/s 的合法窗口富余，同时把「T×R 失控」从无界收敛为声明上界（审计 C5-005 触发时序口径：T=5min、R=10k/s、1KB 行 ≈3GB——旋钮把该形态变为 100k 条处 typed 拒绝而非 OOM）。pendingCommits 缺省 8——审计 C5-010 口径「正常 1-2 个」为经验值非机制保证；恢复堆叠链（restore commit 失败 retain → 每 barrier 全量重序列化正反馈）在 8 个未提交 epoch（≈8 个连续 checkpoint 未收敛 ≈8 分钟缺省）即 typed 可见——对照 2535-A.2 N=6 论证形态：显著长于瞬时抖动、显著短于无限重试，配置化允许上调（慢盘大事务部署）。**超限 fail-fast 后的状态语义**：不 clear pendingCommits、不动台账/manifest（保留链零触碰）——任务失败经恢复路径接管（subsuming commit 语义零回退，红线 4），typed param 承载最小可见性（C8-008 深度 gauge deferred 零触碰）。并发面：检查在既有 `synchronized(currentBuffer)` 内——invoke 任务线程与 saveState 转存的既有互斥延续，判定-拒绝原子（拒绝抛出发生在监视器内，add 未发生——监视器退出由异常路径自然完成，零新锁零死锁序变化）。

**被否决的备选**：
- **spill/提前 flush**（C8-003 残值建议）：spill 涉 temp 文件清理语义（与 C6-007 孤儿清扫 R4-04 面相邻——禁顺带）+ 新 fail-cleanup 契约=超工单；提前 flush 破坏 epoch 原子性（单事务语义=2PC 提交原子性本体）——均不采。
- **maxEpochBytes 字节维旋钮**：字节测长需热路径逐条测长或序列化（开销/口径发明——K5 同款「无原生字节载体」约束对 DmlOp 行 map 同样成立）；条数上界已闭合物化条数维，字节维由 owner doc 包络声明（行宽 × 条数上界换算基声明）承载——与 W-12 的差异：W-12 载体存在字符串直测口径（K7 标杆），DmlOp 行 map 无对应诚实口径。
- **旋钮挂 core 基类（TwoPhaseCommitSinkFunction）**：Kafka sink（producer 内建有界）与 Batch sink（batchSize 即 flush）各有形态——基类挂载误伤零缺陷载体（审计 C5-005 失效面枚举：仅 Jdbc/File 双面 ✗）；双子类各自承载=精确半径。
- **缺省 8→更小（如 3）**：3 个 epoch 未提交即失败对慢 durable 部署误杀面大；8 与 2535-A.2 的 6 同量级且留一档恢复裕度。

**零触碰边界**：红线 4 全集（cleaner/prune/保留窗口/幂等守卫）；`maxBatchSize` 执行侧分段语义零改写；core 基类 `finishCommit`/retain（:187-222/:296-299 机制证据）零触碰；8.41 W-03 in-flight 守卫注释（:39 区）零触碰；C2-006 P3 锁内 I/O 面（File saveState :315-326 writeLines+forcePath）零触碰；`TestJdbcConcurrencyAndBatchSegments`（largeEpochIsCommittedInSegments——大 epoch 合法提交钉）零改写为红④ 的误杀对照锚。

### C.2 W-10：CEP 匹配缓冲上限 + 包络声明（C8-002 独立案，升 P1 已驳回维持）

**决策**：**「subtask 级在途事件计数上界 typed fail-fast」唯一分支**：

1. **计数载体**：`CepOperator` 实例字段 `bufferedEvents`（transient long，单任务线程 mailbox 模型——非竞态字段零原子化）；`bufferEvent`（:1165-1173）入列前 `++bufferedEvents` 判定：`bufferedEvents > cap` → typed `StreamException`（`ERR_STREAM_STATE_ERROR` + param：key/timestamp/当前计数/上限/处置指引「检查 watermark 前进性[idle 分区]或为 pattern 添加 within/上调上限」）——拒绝不入列；**递减点 = 桶移除点**（drainDueBuckets 的 `elementQueueState.remove(timestamp)` :1380——按桶大小递减）。cap 来源 = `NopCepConfigs` 新键 `nop.cep.pipeline.match-buffer.max-events`（varRef 形态——模块 :44 先例），缺省 **100000**，`≤0` operator open 期 typed fail-fast（`ERR_STREAM_INVALID_ARG`[F-6 补名]）。
2. **subtask 级（非 per-key）定夺理由**：无界体 = elementQueueState（per-key per-timestamp 桶）+ SharedBuffer backing（events/entries MapState）的**跨 key 聚合**——subtask 级单计数器以 O(1) 成本界住聚合本体；per-key 计数需 per-key 结构（恰是本工单要界的增长形态）且不界跨 key 聚合。
3. **owner doc 包络声明文案（E5 落账）**：「事件时间 CEP 的匹配缓冲（elementQueueState + SharedBuffer backing）以 watermark 前进为消费前提——idle 分区/长周期下按 `nop.cep.pipeline.match-buffer.max-events`（缺省 100000）上界 typed fail-fast（越界 param 携带 key/计数）；推荐为 pattern 声明 `within`——无 within pattern 的部分匹配状态（SharedBuffer entries）随事件只增不减为固有语义，其增长边界=本上界；缓冲深度 gauge 归可观测性治理轨（C8-008 deferred）——越界 typed param 为最小可见性」。

**理由（触发时序论证）**：缺陷时序（C8-002）——T1 上游分区 idle → T2 merged watermark 冻结在 W → T3 每记录 `timestamp > W` 全数入 `elementQueueState` 桶 + SharedBuffer registerEvent → T4 drain 不发生 → 无界积压直至 OOM（唯一观测=30 分钟 INFO 缓存统计）。post-fix：T3' 计数越界即 typed——任务从「OOM 挂死」变为「可见失败」（恢复路径接管，与 W-04 同语义）；计数口径声明=「在途未消费缓冲事件数」（bufferEvent 增、桶消费减）——SharedBuffer backing 的增长与桶增长相关（registerEvent per buffered event）由同一闸门间接界住，counting 语义 javadoc 声明。**restore 残余声明**：counter transient——restore 后从 0 重建，恢复积压本体在计数重建前不受保护（欠计=守卫弱化非误杀）；不加 checkpoint 字段（触状态序列化 schema 面=超工单）——javadoc + owner doc 声明该残余。缺省 100000 sizing：CEP 为 Memory-only 堆内包络（审计 C5-001 口径），100k 事件 × 1KB 量级 ≈ 100MB 守卫线，配置化允许长窗口 pattern 上调。

**被否决的备选**：
- **平台 QueueOverflowPolicy 语义接入**（拒绝/降级 side-output/仅告警菜单）：「降级 side-output/丢弃」对匹配缓冲=正确性违约面（丢事件=假阴性匹配结果，比任务失败更糟）；QueueOverflowPolicy 接入需平台设施评估（2535-B.1 同款否决先例——CONTROL 丢弃语义设施缺失）——typed fail-fast 是唯一不引入正确性违约的策略。
- **per-key 上限**：见决策 2——精确半径反向（计数结构即增长面）。
- **SharedBuffer backing 直接入字节/条数界**：backing 为 MapState（keyed 状态后端承载）——界 backing 需状态后端计数钩子（跨层改造）；桶闸门在入口单点闭合同一增长链。
- **深度 gauge 同批落**：C8-008 deferred（R-G）零触碰——plan Non-Goals 明文。

**零触碰边界**：红线 2 全集（copyForSubtask/SHAREABLE_LEAF_POOL）；`processElement` 既有晚到判断分支（:1031-1036）与 `numLateRecordsDropped` 语义零改写（过载判定为**新增平行分支**，不并入晚到计数——两计数语义分立）；SharedBuffer（Guava 层 1024 slots 有界对照 + backing）零 diff 为预设（闸门在 operator 入口）；W-07 同文件零交叉（C.2 触 bufferEvent/processElement/drainDueBuckets 递减点 :1380 区；D.1 触 :190 字段与 :1392-1398/:1294 区——字段不同方法不同，diff 分组登记禁顺带）。

### C.3 W-05：流式迁移 helper 一修盖 4 状态类（MA-14=C5-004+C5-008/C5-009）

**决策**：**「AbstractRocksDBState 收敛单遍 RocksIterator 包装 helper，三入口委派」唯一分支**：

1. `AbstractRocksDBState` 新增包内静态流式迁移 helper：单遍迭代（`seekToFirst` → `isValid` → `next`）逐行 `read → migrate → put`（iterator 隐式快照语义——迭代视图为创建时快照，写回同 CF 不影响本迭代；RocksDB iterator 快照语义为审计 C5-004 已证事实），**零跨行收集列表**；Map/List/Value 三入口（`RocksDBMapState.applyMigration` :57-79 / `RocksDBListState.applyMigration` :58 起 / `AbstractRocksDBState.applyValueMigration` :68）收敛委派同一 helper（per-row 函数参数化——List 行经 deserializeList→migrate→serialize，Map 行经 readValue→migrate→serialize）。
2. **覆盖面**：helper 一处收敛覆盖 4+ 状态类（Value/Reducing/Aggregating/InternalAppending 经 applyValueMigration 委派——其调用点 AggregatingState :94-95/ReducingState :54-55 签名不变零 diff 或仅委派行随改，随组 A 登记）；`RocksDBSnapshotSerDe.restoreState` 消费方物化面（checkpoint 载体形态决定）**零触碰**（审计 C5-004 失效面枚举 △ 面——非 applyMigration 族）。
3. **验收矩阵（红⑤）**：map/list/value 三实例路径 × 迁移 round-trip 语义等价 × 堆驻留上界（无双列表收集）。

**理由（触发时序论证）**：缺陷时序（C5-008 最重形态口径）——restore 期 schema 指纹失配且已注册迁移函数 → 全 CF keys+values 双列表（Map 原始字节 2×；List 反序列化对象图 3-5×）堆内物化 → 大状态恢复 OOM → 作业无法拉起（无更早 checkpoint 可回退）。post-fix：峰值堆从「全 CF × 2~5」收敛为「单行反序列化瞬态」——冷路径（schema 演进一次性）不豁免的裁定维持（restore 期后果最重）。**与 2511-⑤ 的边界**：2511 落地为增量缺省翻转+全量 opt-out 阈值守卫（快照路径），迁移路径（applyMigration 族）未被触及——本裁定为独立新面零重开（红线 5）。分批 flush 相对单遍的取舍：单遍已闭合物化面（iterator 流式，唯一驻留=当前行），分批的「批间 flush」在无跨行状态时是无对价复杂度——不采。

**被否决的备选**：
- **分批（每 N 行 flush）**：见上——无跨行状态即无分批必要；若实施期发现迁移函数存在重入读自身 CF 的形态（现签名不暴露 CF 访问器——不存在），再评估并在 plan 登记。
- **迁移函数重入防御层**：StateMigrationFunction 契约为 `(old) → new` 纯函数（无 CF 访问器）——重入面不存在，防御层为零对价复杂度。
- **仅修 MapState/ListState 两本体**（不动 helper）：Value/Reducing/Aggregating/InternalAppending 四类经共享 helper 同样全 CF 物化（C5-009 本体）——漏修即红⑤ 矩阵不闭合；「一修盖 4」是 merge-adjudication MA-14 行明文义务。
- **C5-003（collectMap P3）顺带**：同文件不同方法——零顺手修（Deferred 登记）。

**零触碰边界**：红线 5 全集；`RocksDBSnapshotSerDe` 快照/恢复面零 diff；迁移触发链（verifySchemaCompatibility→applyMigration）语义零改写（迁移函数注册/指纹判定不动）；2511 双路径迁移不变式（schema header/fail-fast/全 null bean typed 拒绝）零回退。

### C.4 W-06：manifest 双级锁超时 + 中断响应（MA-15=C2-005+C3-008）

**决策**：**「jvmLock tryLock 期限 + OS 锁 tryLock 轮询期限，双面同一期限 typed 失败」唯一分支**：

1. **jvmLock**：`jvmLock.lock()`（:526）→ `jvmLock.tryLock(lockTimeoutMs, TimeUnit.MILLISECONDS)`——有界且可中断（tryLock(time,unit) 响应 interrupt）；`InterruptedException` 原样传播（取消信号天然传导——commit 签名 `throws Exception` 面不变）。
2. **OS 锁**：`channel.lock()`（:529 阻塞式）→ `tryLock()` 轮询循环（`FileLock` 无带超时 API——tryLock 立即返回 null/所持为标准替代，审计 C2-005 建议②）：循环 `tryLock()` + 固定步长 sleep（常量 200ms——实现细节零旋钮）至期限；期限 = 同一 `lockTimeoutMs`。**锁顺序纪律零改写**（:521 javadoc JL→FL——tryLock 循环在持 jvmLock 内进行，顺序不变；超时退出路径 unlock 对称）。
3. **期限配置**：构造参数 `manifestLockTimeoutMs`（缺省 **60000ms**），`≤0` 构造期 typed fail-fast；descriptor optional 参数声明（C.1 同载体形态）。
4. **超时 typed 码**：`StreamException(ERR_STREAM_CHECKPOINT_ERROR)`（:67 亲证在位）+ param（锁面 jvm/os、outputDir、等待时长、处置指引「检查对端 JVM 存活/锁持有者线程 dump」）——挂死注入下提交可 typed 失败（审计 C2-005 验证判据兑现）。

**理由（时序论证）**：缺陷时序（C2-005）——T_peer 对端 JVM 进入 commit 临界区持 OS 锁后在 forcePath fsync 上活而挂死 → T_self `channel.lock()` 无限阻塞（并行地本 JVM 他 subtask 在 `jvmLock.lock()` 不可中断等待）→ checkpoint 停滞→超时→重启→re-commit 再卡死循环；锁顺序正确（JL→FL 固定）非缺陷面。post-fix：T_self' 在 60s 期限内轮询 → 对端自愈则获得锁继续；对端持续挂死则 typed 失败（budget 内可见）→ checkpoint 失败路径接管（既有「超时→失败」链从「无限阻塞使超时永不触发」变为「预算内响亮失败」）。缺省 60s sizing：合法临界区时长 = fsync + manifest rewrite（秒级量级）；60s = checkpointInterval 同量级（对齐 C.5 与 checkpointTimeout 同量级论证家族），显著长于合法慢盘提交、显著短于无限阻塞。中断响应：tryLock(time,unit) 的 interruptible 性使 cancel 传导（审计面①——jvmLock.lock() 不可中断性消除）。

**被否决的备选**：
- **jvmLock 仅 lockInterruptibly（无期限）**：中断响应闭合但挂死阻塞仍在（cancel 是唯一出口=不可恢复部署形态）——期限是缺陷本体（无界等待）的闭合面。
- **OS 锁期限独立第二旋钮**：两级锁同一临界区（JL→FL 串行获取）——单期限语义完整（jvmLock 等待+OS 锁等待共享预算），双旋钮引入配比发明——不采。
- **watchdog 线程**：新线程生命周期+与 cancel 竞态——复杂度无对价。
- **MA-19（static 锁表只增）顺带**：P3 零顺手修（互斥正确键空间有界——Deferred 登记）。

**零触碰边界**：锁顺序 javadoc（:521）与获取顺序零改写；2511 保留窗口/prune 面（:90-121/:189-210/:548 区 synchronized prune）零触碰（红线 4）；AR-14 并行锁钉（TestFileTwoPhaseCommitSink 互斥钉）零改写；C2-006 P3 锁内 I/O 零触碰；红⑥ OS 锁面形态裁定见裁定 E（jvmLock 面行为红确定性 + OS 锁面白盒结构钉/变异论证分支——禁伪绿）。

### C.5 W-11：JDBC 语句级超时统一 + checkpointTimeout 盲区校正（C8-009 独立案）

**决策**：**「commit 事务内全部语句创建点统一 setQueryTimeout」唯一分支**：

1. **覆盖集**：`writeOps`（:961）、`writeInserts`（:975-991，两 executeBatch :986/:991）、`writeLedgerEntry`（:903）的每个 `PreparedStatement`/`Statement` 创建后、执行前 `setQueryTimeout(statementTimeoutSeconds)`——三路径统一（审计 C8-009 建议①）；`connection.commit()` 本体无 JDBC 语句超时可挂——其残余等待由 DataSource `socketTimeout` 部署面承载（声明形态，见 4）。
2. **缺省与配置**：构造参数 `statementTimeoutSeconds`（缺省 **60**——与 checkpointTimeout 600s 同量级对齐而低一档：语句超时界单语句执行[≤maxBatchSize=1000 行 executeBatch，秒级常态]，60s 为数量级富余；checkpointTimeout 是整个 barrier→ACK 预算，两者量级关系在 owner doc 声明），`≤0` 构造期 typed fail-fast（`ERR_STREAM_INVALID_ARG`）；builder fluent + descriptor optional 参数（C.1 同载体）。
3. **盲区校正语义**：commit 位于 checkpoint 完成通知路径（core `finishCommit` :187-222 机制证据零触碰）——checkpointTimeout（600s）只覆盖 barrier→snapshot→ACK；语句超时把「DB 挂起→无失败无指标」变为「语句预算内 typed 失败（SQLException→既有 typed 传播面）」→ epoch N+1 起的静默堆积闭合（通知线程的失败经 finishCommit 既有异常路径可见）。setQueryTimeout 调用本身抛 SQLException（驱动不支持等）→ typed 传播（显式失败优于无限等待——不做静默吞掉回退；驱动支持要求入 owner doc 部署提示）。
4. **DataSource socketTimeout = javadoc/owner doc 部署提示（声明形态）**：不做 fail-loud 探测——IJdbcTemplate 抽象下 DataSource 配置面不可可靠探测（fail-loud=误报面）；「外部依赖等待必须有界且 typed」契约（修序建议 7——MA-04[2511 已落]同族）的连接层半边以声明承载。

**理由（时序论证）**：缺陷时序（C8-009）——T1 checkpoint N ACK 完成 → T2 `finishCommit(N)` 开连接 executeBatch → T3 DB 挂起（GC 长停/半开连接）→ 无 queryTimeout=无限期套接字读 → T4 checkpoint N+1 照常 saveState，未提交 epoch 静默堆积、下游可见滞后零信号（checkpointTimeout 不触达通知路径=盲区本体）。post-fix：T3' executeBatch 在 60s 预算内抛 → typed 可见 → 恢复链接管。与 C8-004（2511 已落 HTTP 超时）判「不同」的独立性维持（不同模块/语句级机制/屏障外盲区加重面——merge-adjudication :80 判定，零重开零混淆）。并发面：setQueryTimeout 为语句创建后单线程设置（commit 执行于 core 基类通知路径单线程）——零并发面新增。

**被否决的备选**：
- **fail-loud 校验 DataSource socketTimeout**：探测面不可靠（见 4）——声明形态诚实。
- **缺省 600s（与 checkpointTimeout 等值）**：单语句预算 ≠ 全 checkpoint 预算——等值使「盲区校正」退化为「与上层同宽」（挂起仍占满 600s 才可见）；60s 是「语句粒度预算」的语义正确缺省。
- **连接级 setNetworkTimeout**：IJdbcTemplate 抽象下连接获取面暴露不足 + 与 socketTimeout 部署面重叠——声明承载。
- **core 基类挂超时**：机制证据面零触碰（基类为机制证据非站点）；语句创建点全在 sink 子类——精确半径。

**零触碰边界**：`TwoPhaseCommitSinkFunction` 全文件零 diff（:187-222 finishCommit/:296-299 retain/:39 区 8.41 守卫注释）；C.1 四旋钮与本工单同文件（JdbcTwoPhaseCommitSink）diff 分组登记零交叉（W-04 触 invoke/saveState 缓冲面，W-11 触 writeOps/writeInserts/writeLedgerEntry 语句面）；保留 cleaner 区（:449-519）零触碰——cleaner 面语句是否随设 setQueryTimeout：**零触碰预设**（cleaner 为 2511-④ 落地面，其执行频率低且挂生命周期链——不越界；如 Phase 2 判 cleaner 语句需要同设，按 plan 微调登记，不作为本裁定面）。

### C.6 W-12：push→pull 桥字节界（C8-012 独立案）

**决策**：**「双桥 maxRecordBytes 构造守卫 + 字符串/字节载体直测下界口径 typed fail-fast」唯一分支**：

1. **旋钮**：双桥构造参数 `maxRecordBytes`（缺省 **16777216 = 16MB**——对齐 `FileSourceReader.DEFAULT_MAX_LINE_BYTES` :60 族内标杆），`≤0` 构造期 typed fail-fast（`ERR_STREAM_INVALID_ARG`——构造期非正值拒绝与超限同码）；descriptor optional 参数声明（MessageSource/DebeziumCdcSource 工厂——C.1 同载体）。**双桥同缺省同行为**（一致性声明）。
2. **守卫点 = put 前单点**：`MessageSourceReader.QueueSourceContext.collect`（:222-225）与 `DebeziumCdcSourceReader.QueueSourceContext.collect`（:362）——put 前测长判定，超限 typed `StreamException(ERR_STREAM_INVALID_ARG)`（param：subtask/实测/上限/记录序，消息含处置指引「调大上限或压缩 payload」）——经既有首错 typed 通道传导（message 桥 collect 失败既有 typed 面、debezium 桥 handler 异常既有传导面零改写——守卫只新增拒绝分支不新增通道）。2511-⑥ 位点链零触碰（守卫只触 put 面——红线 6）。
3. **测长口径（K5 事实约束下的唯一诚实形态）**：内置直测——`byte[]`→`length`；`String`→`length()`（UTF-16 code units，堆驻留量级正确下界）；`Map`/`Collection`/数组/`ChangeEvent`（before/after/key 三 Map）→ **有界深度（=3）递归**直测内含 String/byte[] 内容长度之和（深度超限部分不计入）；其余类型计 0。**口径声明 = 「字符串/字节载体直测下界」**——非字节主导 payload 的残余面由 owner doc 能力矩阵包络声明承载（「payload 包络按字符串/字节载体直测下界守卫；纯对象图载体[F-2 明示：非字符串/字节载体计 0——字节界守卫对其不设防]的堆驻留以运维观测为准——深度遥测归治理轨」）。
4. **红⑫ 驱动形态 = 小 cap 注入**（maxRecordBytes=100 → 注入 >100 字节载体 record → typed）——零大内存分配的确定性红。

**理由（触发时序论证）**：缺陷时序（C8-012）——T1 大 payload 主题/宽表（S=5-10MB/行）→ T2 下游慢消费队列满 256 槽驻留 → 256×S 堆失控（S 为自由变量、双桥无检查——「有界队列」声明的字节维击穿）。post-fix：T2' 单记录超限即 typed（拒绝在 put 前——队列零污染）；条数界（256）× 字节界（16MB）双维声明闭合，族内不一致消除（file maxLineBytes/kafka fetch 字节界对照正例族全对齐）。**为什么缺省 16MB 而非更小**：多 MB 二进制事件（文档/快照行）是审计点名的合法形态（S=5-10MB）——16MB 允许其通过、拦截 10MB+ 的失控组合下限与 file 标杆一致；配置化允许下调收紧或上调放宽。**为什么 typed 而非丢弃计数**：message 桥 AT_LEAST_ONCE 声明/debezium 桥 REPLAYABLE 声明下静默丢弃=数据面违约；typed 失败=任务可见（与 file 标杆「never a silent truncation」:337-339 同语义）。

**被否决的备选**：
- **丢弃计数/降级 side-output**：见上——桥承载 checkpoint 语义，静默丢=违约。
- **字节水位队列（IBlockingQueue+QueueOverflowPolicy）**：平台队列设施接入（C8-round2 §2.1 定向核查：该单元当前零使用）——设施改造超工单且策略菜单含丢弃支（同上违约面）。
- **序列化测长（serialize-to-measure）**：热路径逐记录序列化开销+口径仍非堆驻留（序列化字节≠对象图驻留）——直测下界+声明残余更诚实。
- **无界深度递归测长**：对象图成环风险（深度 3 封顶消除）+ 深层图测长开销——有界深度是下界口径的自洽边界。

**零触碰边界**：红线 6 全集（LastEmittedOffsetTracker 全链）；`QUEUE_CAPACITY=256` 条数界与 put 阻塞背压语义零改写（守卫为 put 前新增判定）；既有首错 typed 通道（message/debezium 两桥）零改写；`TestMessageSourceFunctionThreadSafety`（10 线程 collect 面）/`TestDebeziumLastEmittedOffsets_C8_001` 零改写；BF-1（条数界不可配旋钮）零顺手修（批外登记）。

## 裁定 D 增长治理域（W-07/W-09）

### D.1 W-07：reconciledTimerKeys 清理点（MA-16=C5-001；C7-005 并轨消号；R01 对齐解读落款见引用核对节）

**决策**：**「全清位点同步 remove + 包内水位钩子」唯一分支**：

1. **清理点**：`resetNfaStateIfFullyTimedOut`（:1294-1310）完成全清（全清判定成立且 `computationStates.clear()` 执行）后同点位 `if (reconciledTimerKeys != null) reconciledTimerKeys.remove(getCurrentKey())`——与 `registeredEventTimeTimersByKey` 的「消费即删+空键删除」既有清理语义同族对称（对照正例同形）；早退分支（未全清）零改动。
2. **水位测试钩子**：包内可见 `int reconciledTimerKeysSizeForTesting()`（零公共 API 面——红⑦ 断言缝；审计 C5-001 建议「getLedgerForTesting 风格水位」）。
3. **清理条件 = 既有全清位点本体**（唯一部分匹配+无完成匹配+全部窗口已过[F-4 措辞收紧——与 :1296-1309 判定本体一致；remove 后重对账=零 delta 既有语义，行为零改动]在 resetNfaStateIfFullyTimedOut 的组合判定中达成）——不发明新条件、不引入弱引用结构、不接平台缓存。

**理由（幂等安全性与时序论证）**：集合用途 = 「每 key 一次 legacy checkpoint 对账」（:1392-1398 contains 短路 + add）；**remove 后的重对账安全性**：对账本体是把 legacy ledger 条目补登记进 timer ledger——首 drain 已清空该 key 的 legacy 条目，remove 后若 key 因新事件重入 drain，重对账为空操作（零副作用幂等）；且 `reconciledTimerKeys` 为 **transient**（K9 亲证 :190）——restore 后集合归零、全部 key 重对账一次是**既有语义**（对账幂等已被该既有形态隐证）——清理点不引入任何新对账形态，只把「实例生命周期内 once」精确为「key 生命周期内 once」（key 全清=生命周期终点，重现为新生命周期）。缺陷时序（C5-001）——每 distinct key 首次 drain 永久驻留（HashSet 条目+key 强引用），key 基数无界负载每 subtask 稳态累积 ≈32-64B/键（1 亿 key ≈ 数 GB，Memory-only 堆内包络直接侵蚀业务状态预算）→ post-fix：key 全清即收敛——集合水位上界 = 活跃 key 基数（有界于业务状态本身）。**R01 对齐解读落款**（引用核对节）：对齐面=清理点设计纪律（有界[remove 点]/观测钩子[水位]/参数命名惯例[本裁定零新参数——remove 无旋钮]）——零 SSOT 成员行。

**被否决的备选**：
- **弱引用有界结构（Guava weakKeys cache）**：weakKeys 在 key 仍被 keyed state 引用时不清（活跃期正确）、全清后 GC 才收敛——收敛时点不可判定（GC 依赖），水位钩子失真；同步 remove 在确定位点确定性收敛——优。
- **平台 CacheConfig 有界缓存**：淘汰≠清理点（淘汰-dependent 水位不可判定）+ 引入容量参数发明（R01 对齐纪律禁参数发明面）——不采。
- **close() 清空**：运行期增长本体不闭合（close 是实例终点非清理点）——不采。
- **reconcile 重入防护层**：重对账幂等（见上）——防护层零对价；「W-07 清理不得破坏 reconcile 一次性语义」的兑现方式 = 幂等论证 + 重对账空操作语义声明（非互斥锁）。

**零触碰边界**：红线 2 全集；`registeredEventTimeTimersByKey` 既有清理语义与 drainDueBuckets 主体零 diff（remove 为新增单行+钩子一行）；C.2 同文件零交叉（D.1 触 :190 字段/:1294-1310/:1392-1398 区，C.2 触 :1165-1173/:1380 递减点——:1380 与 :1392 同在 drain 链但分属不同方法不同字段，diff 分组登记禁顺带）；C7-005 同缺陷重复确证并轨消号随本工单（cra-index 随 W-07 主 ID 括注）。

### D.2 W-09：checkpoint 预算耦合守卫 + 哈希增量化旋钮（MA-18=C8-010+C8-013 双面）

**决策**：**「声明规模分支耦合校验（validateUnalignedConfig 同构）+ hashPolicy 两档旋钮」唯一分支**：

1. **耦合校验（C8-010 面）**：`CheckpointConfig` 新增字段 `snapshotDeclaredEntries`（long，缺省 **0 = 未声明 → 校验旁路**）与 `snapshotSerializeMsPerThousandEntries`（int，缺省 **1000ms/千条**）+ 新 validate 方法 `validateSnapshotBudget()`：`snapshotDeclaredEntries > 0 && snapshotDeclaredEntries × (snapshotSerializeMsPerThousandEntries / 1000.0) > checkpointTimeout × 0.5` 时 typed 拒绝（`ERR_STREAM_INVALID_ARG`，param：声明规模/速率/预算/上限/处置指引「缩状态、调大 checkpointTimeout、或确认增量缺省已开+hashPolicy=new-only」）。安全系数 0.5 常量（快照预算占超时一半，留 durable/传输余量）。
   - **F10 分支绑定声明**：红⑨ 确定性红 = **配置声明分支**（字段直调 validate——`TestCheckpointTransportPersistenceValidation` 形态先例：core 测试直调 validateUnalignedConfig）；**runtime-prop 估算分支不设**——`rocksdb.estimate-num-keys`（recorder :45 PROP，K2）为运行期 gauge 属性、构造/配置期不可用（fresh open ≈0、post-restore 才有意义且随运行漂移）——runtime-prop 分支的钉形态**零触发**（plan 红⑨ F10 预案的「runtime-prop 估算分支钉形态随裁定重推导」推导结果=不设该分支，登记于此）。
   - **速率常数的诚实来源声明**（2535-B.2 纪律对齐）：缺省 1000ms/千条为**公开声明的换算基**（javadoc/owner doc 写明口径与适用域、用户按负载校准——序列化速率随行宽/类型变化，声明常数只是缺省校准点），非静默虚构换算常数（2535-B.2 否决的是无来源、无声明、以执行机制形式复制假保证的形态——此处常数显式声明+可配置+校验旁路缺省=包络声明语义，性质不同）。
   - **两面同盖声明**：全量路径时长 ∝ 状态体积（条数换算）、增量路径时长 ∝ SST 总量（哈希带宽）——同一声明包络校验以两路径中较保守者承载（javadoc 声明两面同盖口径：declared-entries 是「状态规模上界」声明，两路径时长均随其单调增长）——缺省增量+new-only（下条）使缺省路径时长显著低于校验上限（校验为保守上界非精确预测）。
   - **挂点与接线（K4 偏离登记）**：validate 方法 = `validateUnalignedConfig` 同文件同构（:271-290 零改写，新方法并列）；**接线随先例 = `GraphModelCheckpointExecutor` 既有两调用点（:229/:367）各并列一行 `validateSnapshotBudget()`**——触 runtime 2 行 diff，按 PA 表行 9 预设允许形态（「触 runtime 则为同聚合器 impl 叶，diff 分组登记」）执行；此为对 plan 头部「runtime 零 diff 为预设」的**裁定级偏离**，偏离理由：core main 源无 validate 生产漏斗（K4——validateUnalignedConfig 亦仅 runtime executor 调用），不接线即死代码（Anti-Hollow 红线/plan rule 23）——2 行接线是先例同构的最小诚实形态；LOCAL 面无自动 validate 漏斗为先例既有限界（非本批新缺口，`TestCheckpointTransportPersistenceValidation` 直调形态为 LOCAL/单元覆盖先例）。**runtime JobCoordinatorMain 10s fallback 知悉面**：零触碰维持 watch-only（plan Deferred 预设）——JobCoordinatorMain 零 diff；该路径经 CheckpointConfig 构建时若声明了规模同样受 validate 约束（validate 挂 executor 漏斗——fallback 路径的 validate 覆盖随漏斗面，如未过漏斗则旁路=未声明旁路语义一致）。
2. **哈希增量化旋钮（C8-013 面）**：`NopStreamRocksdbConfigs` 新键 `nop.stream.rocksdb.snapshot-hash-policy`（varRef 形态——:32 先例），值域 **`full | new-only`**（String，缺省 **new-only**）：new-only = strategy 持实例字段 `previousSstIndex`（`Map<fileName, (hash,size)>`——restore 期从快照 SharedStateHandle 清单播种、每 checkpoint 后滚动更新、fresh open 首 checkpoint 退化为全哈希[无 index 可复用——正确语义]）；doSnapshot（:63-124）逐 SST 判定：命中 index 且 size 相等 → 复用既有 hash（SST 不可变+编号单调 = (name,size) 判等诚实依据——mtime 跨 checkpoint 目录不可靠不采）；miss/size 不匹配 → `sha256Hex` 全哈希并更新 index。full = 既有全量重哈希形态（opt-out 回退档）。
   - **完整性取舍声明（E5）**：new-only 将快照校验和语义从「每 checkpoint 全量重验」精确化为「新增文件验证 + 未变更文件按 SST 不可变契约复用」——文件级损坏由 native 读取层校验承载；owner doc 声明该取舍。
   - **快照耗时 gauge = C8-008 deferred 零触碰**（越界 typed param 承载最小可见性）。

**理由（触发时序论证）**：缺陷时序（两 face 共同失效层）——状态增长越预算 → 「checkpoint 超时→3 次失败→FAIL→重启→重演」不可自愈循环（重启不缩状态体积）；2511-⑤ 翻转后**增量重哈希=默认路径主面**（backend :125 true 亲证）——C8-013 的「逃生门自身携带同类时长缩放」（每 SST sha256Hex ∝ 总量非增量）是默认路径的支配时长项。post-fix：①声明包络用户显式声明状态规模时，配置组合越界在部署期 typed 拒绝（validateUnalignedConfig 同构先例——「配置裕度 fail-fast 惯例」C8-010 现状确认在仓）；②new-only 使增量路径哈希时长从 O(总量) 收敛为 O(增量)——默认路径支配项消除，60s/600s 预算的 10× 裕度对增量形态重新成立。并发面：strategy 单任务线程 barrier 内同步执行（既有语义）——previousSstIndex 为单线程字段零同步原语；validate 为配置期单点调用零并发面。

**被否决的备选**：
- **runtime-prop 估算分支（open 期 estimate-num-keys 校验）**：F10 推导=不设（见 1）——fresh open ≈0 无校验意义、post-restore 校验点=restore 已完成后（失败循环首轮已发生）、且估值随运行漂移（校验一次不约束后续）。
- **sampled 哈希档**：抽样完整性=概率化验收（抽中才验）——无诚实完整性语义；两档已覆盖取舍空间。
- **校验缺省强制（无声明即按估算默认拒绝）**：无声明时框架虚构规模=2535-B.2 同款「假精确」——未声明旁路+owner doc「未声明规模则无时长包络承诺」声明是诚实形态（opt-in 守卫与 checkpointInterval 等配置同类）。
- **JobCoordinatorMain fallback 本体改动**：plan Non-Goal（runtime 移交知悉面维持）——零触碰。
- **快照耗时 gauge**：C8-008 deferred 零触碰。

**零触碰边界**：红线 5 全集（incrementalCheckpointEnabled 缺省/snapshot-materialize-max-entries/validateUnalignedConfig 本体零改写）；`TestRocksDBSnapshotRestore`（驻留钉+opt-out leg）/`TestRocksDBIncrementalLifecycleAndIntegrity`（taskLocalCheckpointDirsStayBounded 包络钩子）零改写；pruneOldCheckpoints K=2 滚动保留零触碰；`TestRocksDBKeyedStateBackendCrossThreadOps` 零触碰；runtime 面 = GraphModelCheckpointExecutor 两行接线之外零 diff（JobCoordinatorMain/其余 runtime 零触碰）。

## 裁定 E 测试缝总表（红①–红⑫ + 零回退钉全列 + C10 五条映射）

### 红绿判据纪律（先红后绿，E2——本批无 C10 验收前置，红①–红⑫ 为 E2 泛在义务承载）

全批测试零 mockito 新增使用（六模块 pom 零 mockito；debezium pom :114-118 声明未用=F1 订正维持零使用）——观测一律 JDK 并发原语 + latch + 手工桩 + 包内可见注入缝（2510-⑪ 九缝形态惯例）。**orTimeout 假绿陷阱纪律（2535 全盘沿用）**：future 完成性断言只允许同步 `get(timeout)` 捕获 TimeoutException 判红，禁用 `orTimeout`/`completeOnTimeout`。**闩带上界 ≤30s 且收尾必放闸**。红绿快照落 `_tmp/` 总册 + 逐 trial 日志。测试类注释显式归属 CRA 8.43 R4-03（同号防混——8.43-W-xx ≠ 8.41-W-xx ≠ 8.42-W-xx ≠ 8.17 finding 直呼）。W-06 OS 锁面按结构钉/变异论证分支留痕禁伪绿（2533 红⑥/2534 W-01 先例形态）。

### 产品侧测试缝（S1–S12）

| 缝 | 形态 | 覆盖钉 |
|---|---|---|
| S1 | recorder `backend` tag 值 + `registeredIds`（包内可见或行为断言经 composite 枚举） | 红① |
| S2 | recorder `warnedProperties`（行为断言为主——ListAppender 计数） | 红⑧ |
| S3 | client/wire-format `close()`（公共 API 即缝）+ 线程枚举/fd 计数（R07 形态） | 红③ |
| S4 | 负缓存条目 + fake registry 请求计数器（TestAvroWireFormat 既有 fake registry 形态扩展） | 红② |
| S5 | sink 构造参数小值注入（maxEpochRecords/maxPendingEpochs/maxEpochLines 小值即缝，零产品缝新增） | 红④ |
| S6 | 迁移 helper 白盒（包内静态方法直调/键数参数化驻留探针——TestRocksDBStateMigration 基座） | 红⑤ |
| S7 | `MANIFEST_JVM_LOCKS` 包内可见取锁（他线程持锁注入）[F-5 登记可见性放宽触面——S7 缝形态，2510-⑪ 包内注入缝先例；站点文件触面登记防越界歧义]+ `manifestLockTimeoutMs` 小值注入 | 红⑥ |
| S8 | `reconciledTimerKeysSizeForTesting()`（包内水位钩子） | 红⑦ |
| S9 | `snapshotDeclaredEntries` 字段直调 `validateSnapshotBudget()`（TestCheckpointTransportPersistenceValidation 形态） | 红⑨ |
| S10 | `nop.cep.pipeline.match-buffer.max-events` 测试态覆盖 + `bufferedEvents` 行为断言 | 红⑩ |
| S11 | 语句包装捕获（手工桩 Statement 记 setQueryTimeout 调用）+ `statementTimeoutSeconds` 小值注入 | 红⑪ |
| S12 | 双桥 `maxRecordBytes` 小值注入（=100）+ 构造期非值拒绝 | 红⑫ |

### 红①–红⑫ 驱动器总表

| 钉 | 工单（并入成员） | 驱动器形态 | pre-fix 红判据 | 伪绿防线 | 闩带 |
|---|---|---|---|---|---|
| 红① | W-01（C6-010/C7-004/C3-013） | TestRocksDBMetricsRecorder 扩展：(a) 双 recorder 同 composite 注册 → 断言 6 meter 名 × 2 个 `backend` tag 值=12 Id 全部可见；(b) recorder.close → composite 枚举断言该 recorder 6 Id 全部摘除；(c) 既有压窗钉（testC1_002_*）零改写并列运行 | (a) pre-fix 同 Id first-wins 去重→6 Id（1/N 可见）=红；(b) pre-fix close 无 remove→6 Id 幸存=红（**当前即红，确定性**） | 双 leg 齐备（注册可见+摘除生效）防单面假绿；变异=移除 tag（去重回归红）与移除 remove（幸存红）各自独立必红 | ≤5s（单线程确定性） |
| 红② | W-02（C3-007） | TestAvroWireFormat 扩展（S4）：失败注入（挂死或拒连）→ (a) 首 decode：回落 writerSchema 断言 + WARN 恰一次（ListAppender）+ 负缓存条目在位；(b) TTL 窗口内 N 次 decode：registry 请求计数零增 + WARN 零增；(c) TTL 过期：恰一次重试（计数+1）——成功 leg 演进读取恢复（round-trip 断言） | pre-fix 每记录一次失败调用（N 次注入 N 次调用）+ 零 WARN =红（**当前即红，确定性**） | 请求计数器断言（非日志断言单依赖）；限频断言（窗口内零新增 WARN）防「回落但每条 WARN」退化；挂死钉三件（:264/:323/:382）零改写并列 | ≤10s |
| 红③ | W-03（C7-013） | 新 TestAvroSchemaRegistryClientLifecycle（S3）：N=5 轮构造 wire-format+client → close → 断言 selector 线程枚举 before/after 零净增 + fd 计数 delta 有界（R07 先例形态参照[plan 2522 TestFileDownloadSinkClose 形态]——复用形态不复制文件）；close 幂等（双 close 不抛） | pre-fix 无 close 通道 → N 轮构造（不 close）selector 线程净增 N =红（**当前即红，确定性**） | 线程枚举（Thread 名/增量计数）+ fd 双面；close 存在性为 post-fix 编译面（pre-fix 红证据=泄漏 leg）；rule-23 接线验证由本缝承载（K10——生产零调用点，宿主接线 WI18 预留） | ≤10s |
| 红④ | W-04（C8-003/C5-010） | S5 小上限注入双载体：(a) maxEpochRecords=3/maxEpochLines=3 → 注入 4 条 → invoke 期 typed 拒绝（param 水位断言）；(b) maxPendingEpochs=2 → 多 epoch saveState 不提交 → 第 3 次 saveState typed 拒绝（Jdbc+File 双载体）；构造期 ≤0 拒绝断言 | pre-fix 无旋钮无拒绝分支 → 上限注入后缓冲继续增长/转存继续 =红（**当前即红，确定性**） | 双容器双载体四 leg 齐备防「旋钮存在但单容器未盖」；保留钉（TestJdbcEpochLedgerRetention_C3_002/ManifestPrune_C3_002）与 largeEpochIsCommittedInSegments（合法大 epoch 钉）零改写并列防误杀退化 | ≤10s |
| 红⑤ | W-05（C5-008/C5-009） | S6：map/list/value 三实例 N 键（大 N 参数化）迁移 → 断言（a）迁移 round-trip 语义等价（既有钉零改写并列）；（b）堆驻留有界/白盒无双列表收集（helper 路径结构断言） | pre-fix 双列表全 CF 收集 → 键数参数化驻留随 N 线性超界 =红（**当前即红，确定性**） | 三实例矩阵齐备防「helper 存在但入口未收敛」（C5-009 第 1 轮误标教训的测试面镜像）；TestRocksDBStateMigration round-trip 零改写并列 | ≤30s |
| 红⑥ | W-06（C3-008） | S7：(a) jvmLock 面——他线程持 jvmLock 不释放 → commit 在注入预算内 typed 失败（非无限阻塞）+ 中断响应断言（等待中 interrupt → 传播）；(b) OS 锁面——白盒结构钉（withManifestLock 内 OS 锁获取经 tryLock 期限循环的包内 seam 断言）+ **变异论证**（换回 channel.lock() → 结构钉红/期限消失）留痕禁伪绿（2533 红⑥/2534 W-01 先例形态） | (a) pre-fix 无超时 → 测试超时红线承载无界阻塞 =红（**当前即红，确定性**——测试 timeout 即红证据）；(b) OS 面外部进程注入难 → 结构钉+变异分支 | (a) typed 断言+中断双断言；(b) 变异探针留痕（恢复后 assert 目标行存在——R5 探针纪律）；AR-14 互斥钉/ManifestPrune 零改写并列防「超时破坏互斥」退化 | ≤15s |
| 红⑦ | W-07（C7-005 并轨消号） | S8：key 事件注入→drain→windowTime 过期触发 resetNfaStateIfFullyTimedOut 全清 → 断言 `reconciledTimerKeysSizeForTesting()==0`；多 key 序列逐个收敛断言；重现 key 重对账幂等断言（收敛后同 key 再 drain 零副作用） | pre-fix 单调增长 → 全清后 size 仍 ≥1 =红（**当前即红，确定性**） | 水位钩子行为断言（非字段反射单依赖）；TestCepOperatorLedgerDrain/TestCepOperatorDanglingCleanup 既有语义零改写并列防「清理破坏 drain」退化；remove 后重对账幂等 leg 防过度清理 | ≤10s |
| 红⑧ | W-08（C2-008） | S2：属性恒抛注入（不存在属性名——getAggregatedLongProperty 抛 RocksDBException 真实路径）→ N=5 次 scrape → 断言 WARN 恰一次（ListAppender 计数）+ 后续 debug 级 | pre-fix N 条 WARN =红（**当前即红，确定性**） | 计数断言恰一次（防 once-guard 把首条也吞=零 WARN 假绿——断言 ≥1 且 ≤1）；压窗钉零改写并列 | ≤5s |
| 红⑨ | W-09（C8-013） | S9：配置组合直调 validateSnapshotBudget——(a) declared × rate > timeout×0.5 → typed 拒绝（F10：**确定性红绑定配置声明分支**）；(b) 合法组合 assertDoesNotThrow；(c) 未声明（0）旁路断言；runtime 接线核对 = GraphModelCheckpointExecutor 调用点结构核对（grep/断言登记） | pre-fix 方法不存在 =结构红（编译面——红证据=基线 grep validateSnapshotBudget 零命中+接线零命中留痕）；(a) 形态 post-fix 直调可红可绿按配置 | (a)(b)(c) 三 leg 齐备防「守卫存在但全拒绝/全放行」；2511 缺省/阈值守卫钉（TestRocksDBSnapshotRestore opt-out leg 等）零改写并列；两面同盖 = (a) 组合声明论证留痕（声明包络对两路径同盖口径 javadoc 断言） | 零（同步直调） |
| 红⑩ | W-10 | S10：cap 小值测试态覆盖 → 事件注入超 cap → typed 拒绝（param key/timestamp/计数断言）+ cap-1 通过边界 leg + 桶消费后计数递减断言（:1380 递减点行为验证） | pre-fix 无分支 → 超限事件成功入列 =红（**当前即红，确定性**） | 边界 leg（cap-1 通过）防「守卫全拒绝」假绿；递减 leg 防「计数只增=误杀正常负载」退化；TestCepOperatorMultiKeyWatermark 基座零改写并列 | ≤10s |
| 红⑪ | W-11 | S11：(a) 白盒——手工桩 Statement 捕获：commit 链执行后断言 setQueryTimeout 调用数 = 语句创建数（writeOps/writeInserts/writeLedgerEntry 全覆盖）；(b) `statementTimeoutSeconds ≤0` 构造期拒绝断言；(c) 语句超时到期 typed 传播 leg（小值注入 + 慢语句桩） | pre-fix setQueryTimeout 零命中（模块 grep 亲证）=红（**当前即红，确定性**——(a) 计数=0 vs 语句数>0） | 计数断言（覆盖率）+ 行为 leg（超时真传播）双面防「白盒通过但运行时未生效」；保留钉/恢复幂等钉（TestJdbcTwoPhaseCommitSinkDeep）零改写并列 | ≤10s |
| 红⑫ | W-12 | S12：双桥 maxRecordBytes=100 → 注入 >100 字节载体 record（String/ChangeEvent 大 String 值）→ typed 拒绝（param 断言）+ cap-1 通过 + 构造期 0/-1 拒绝断言（双桥）；既有首错 typed 通道零改写并列 | pre-fix 无守卫 → 超限 record 成功入队 =红（**当前即红，确定性**） | 双桥同钉（单桥修=红）；字节载体 leg（String 直测口径的行为验证）+ 构造拒绝 leg；TestDebeziumLastEmittedOffsets_C8_001 零改写并列（守卫只触 put 面） | ≤10s |

### 零回退钉全列

- **基线签名（plan 勘误 4，2026-11-07 亲跑 @ `fe03951544`——`3b3df13655` 零 nop-stream diff 继续有效[K1]）**：`./mvnw test -pl nop-stream` 分解通道已执行 **4439 tests / 0F / 0E / 20S**（core 2003 0F0E 1S / cep 386 / flow 315 / connector-jdbc 66 / debezium 79 0F0E 6S / batch 62 / kafka 26 / rocksdb 143 / connector 106 / runtime 1253 0F0E 13S 结果完整但 docker 双 E2E 慢跳过触发 fork 300s 超时 BUILD FAILURE——2534/2535 已知面③ 精确复现；format-avro/sql/fraud-example 反应器跳过 Phase 3 补齐）。零回退实证 = 各模块 tests/F/E/S 签名比对（20S 归属逐一核对）+ 新钉增量。
- **2511-⑧ 全部钉（红线 7）**：压窗钉（testC1_002_*）、TestCepOperatorSubtaskCopy 反转钉、挂死三件（TestAvroWireFormat :264/:323/:382）、保留双钉（TestJdbcEpochLedgerRetention_C3_002/TestFileTwoPhaseCommitSinkManifestPrune_C3_002）、驻留钉（TestRocksDBSnapshotRestore 键数参数化+opt-out leg）、last-emitted 钉（TestDebeziumLastEmittedOffsets_C8_001）、裕度钉（TestKafkaTwoPhaseCommitSinkLifecycle 属性断言）、TestDebeziumCdcSource/TestDebeziumCdcCheckpoint 改写外用例——钉内断言全批 diff 零触碰。
- **8.41 受害侧钉**：`TestRocksDBKeyedStateBackendCrossThreadOps` 零触碰零重开（rocksdb 测试树唯一不改的既有并发钉）。
- **2510-⑪ 七钉**（零文件交集核对义务）：TestCachingTableLookup/TestJobCoordinatorPerTaskFailure/TestInMemoryClusterRegistryAttemptHistory/TestResultPartitionDeadlock/TestMultiJvmExactlyOnceRecovery/TestLaunchMainWiring/TestJobCoordinatorRecoveryConcurrency——零触碰（E3 通道覆盖核对，非本批 diff 面）。
- **既有锚点名（本批零回退锚）**：TestRocksDBMetricsRecorder / TestRocksDBSnapshotRestore / TestRocksDBStateMigration / TestStateFamilyBackendMatrix / TestRocksDBIncrementalLifecycleAndIntegrity / TestRocksDBStateTtl / TestCepOperatorSubtaskCopy / TestCepOperatorLedgerDrain / TestCepOperatorDanglingCleanup / TestCepEventTimeTimerRegistry / TestCepOperatorMultiKeyWatermark / TestCepOperatorLateRecordsDroppedMetric / TestAvroWireFormat / TestJdbcEpochLedgerRetention_C3_002 / TestJdbcConcurrencyAndBatchSegments / TestJdbcTwoPhaseCommitSinkDeep / TestJdbcTwoPhaseCommitLedgerNamespace / TestFileTwoPhaseCommitSink / TestFileTwoPhaseCommitSinkManifestPrune_C3_002 / TestMessageSourceFunctionLifecycle / TestMessageSourceFunctionThreadSafety / TestDebeziumLastEmittedOffsets_C8_001 / TestDebeziumCdcSource / TestDebeziumCdcCheckpoint / TestLockable——钉内断言零改写（红钉为追加用例形态，既有用例只增不改）。
- **已知环境面集（E3 噪声源预案——plan 勘误 4/5 沿用）**：面① nop-core TestResourceCacheInvalidationChain 负载 flake（solo 三联先例）；面② runtime F1 TestStreamConnectorRegistryE2E（solo 三联绿归因在案——禁静默重跑；本轮零复现）；面③ runtime docker 双 E2E 慢跳过/fork 超时（已现形亲证）；面④ debezium 6S 环境门控（TestDebeziumReal* 等）；面⑤ format-avro/sql/fraud-example 反应器跳过（Phase 3 补齐）；面⑥ E1 在线模式 nop-ai-translate 面（预计不现形）。

### C10 五条映射（对齐纪律第一条——2511 改写钉承载面核对，同 diff 禁拆分）

| C10 条目 | 2511 改写钉（承载面） | 本批关联工单 | 零改写核对面 |
|---|---|---|---|
| C10-002 | testC1_002_* 压窗钉（close×scrape 交错+锁内取参白盒） | W-01/W-08（红①⑧ 并列运行） | 取参点/锁纪律/压窗断言零 diff；「finally close 后不复查 meter 摘除」缺口（C10-test-protection :127 失效面枚举）= 红①(b) leg 承载闭合——改写钉本体零改写，缺口由新钉补齐 |
| C10-003 | TestCepOperatorSubtaskCopy（assertSame 反转+ctx 探针） | W-07/W-10（红线 2 并列） | 拷贝链断言零 diff——W-07/W-10 与拷贝链零交互的并列验证 |
| C10-008 | 挂死钉三件（:264/:323/:382） | W-02/W-03（红②③ 并列） | 挂死注入/超时 typed/锁限时释放断言零 diff——B.1 负缓存不改写三件（挂死 leg 在 encode 面）；B.2 close 通道为新增面 |
| C10-012 | TestRocksDBSnapshotRestore 键数参数化驻留钉 + opt-out leg | W-05/W-09（红⑤⑨ 并列） | round-trip/驻留/opt-out 断言零 diff——W-05 迁移路径为独立面（applyMigration 族≠快照面）；W-09 零触碰缺省与阈值 |
| C10-013 | TestDebeziumLastEmittedOffsets_C8_001（键形/typed 判据/min 重放） | W-12（红⑫ 并列） | 位点链断言零 diff——W-12 只触 put 面（守卫在位点提取之后的队列入口） |

其余红④⑥⑦⑩⑪ 为无 C10 号工单（merge-adjudication §4.4 注 :229——P1 之外各族暂无 C10 登记）——E2 泛在钉承载，形态见驱动器总表。

## 条件申报登记（禁静默扩权——预设全部不触发，逐面亲读依据）

①**`*-api` 契约叶 → 预设不触发**：12 工单 import 面亲读——`io.nop.api.core` 仅 NopException/错误码/`AppConfig.varRef` 使用面引用零 diff；IStateBackend/TwoPhaseCommitSinkFunction/CheckpointConfig/StreamMetricsRegistries 均为 nop-stream-core 模块内 impl 叶（非 `*-api` 模块）。②**orm/DB 承载 → 预设不触发**：W-04 上界旋钮为内存/配置面（plan 预设禁选维持）；七模块 `model/*.orm.xml` 零触碰。③**平台设施本体 → 预设不触发**：QueueOverflowPolicy 不采（C.2 备选否决——申报③ 最大候选面直接消除）；HighWatermarkSemaphore 不接线（C5-005 审计建议不采——条数上界自持）；CacheConfig 不采（D.1 备选否决）；micrometer 为使用面 API（gauge/remove）。④**`_` 生成物/pom 结构 → 预设不触发**：零新错误码（K6——四码全部复用在位码）；新测试落既有测试树；新配置键落既有 Configs 接口/构造参数（零新文件承载设施）；cep `model/_gen/` 生成目录零触碰（F2）；`_vfs` 注册文件零触碰。
⑤**F4 注入链分支预登记条款（本批特有）**：如实施期裁定需从 A.1 站点内形态改采**注入链形态**（backend tag 取 operator/subtask 身份），触面 = `IStateBackend.createKeyedStateBackend` 签名扩展（或等价 tag 传递 setter）+ `MemoryStateBackend` 对应实现 + **4 个 operator 调用点**（ProcessOperator :86 / EquiJoinOperator :216 / OverWindowOperator :155 / WindowOperator :658-660——F4 亲证半径，全部 nop-stream-core impl 叶）。该触面为 core 模块内 In-Scope 外文件（非 PA 升格——非 `*-api`/非平台设施），须按四件证据链重新申报：plan 追加申报 + 本文档 A.1 专节修订 + draft review 记录追加 + 回归测试（新增 tag 归因断言）——**未申报前实施=禁**；预设不触发（A.1 站点内形态已闭合缺陷本体）。任一条件申报触发 → 按 AGENTS.md 四件证据链走完方可实施。

## 零触碰边界总表（8.17 产物面 / 8.41-8.42 产物面 / 8.44 成员 / P3×9 / C8-008 gauge 面 / cep `_gen/`）

- **8.17 产物面（同文件跨批——禁顺带逐面，plan 对齐纪律第四条全清单继承）**：RocksDBMetricsRecorder 锁内取参面（readCurrent :99-101 + 闭包注释 :83-88）——本批触 tags/remove/once-guard 三面（A.1/A.2）；Avro 双文件超时+锁外建连面（:79/:96-101/:138/:61-66）——本批触 readerSchemaFor/负缓存/close 三面（B.1/B.2）；Jdbc+File 保留 cleaner/prune/保留窗口面（:449-519/:90-121/:189-210/:548 区）——本批触缓冲/堆叠上界与锁等待面（C.1/C.4）；CepOperator copyForSubtask+SHAREABLE_LEAF_POOL 面（:430-457 区）——本批触清理点/计数器面（D.1/C.2）；RocksDBKeyedStateBackend 增量缺省+阈值守卫面（:125/NopStreamRocksdbConfigs :32）——本批触哈希策略/校验面（D.2）；DebeziumCdcSourceReader last-emitted 位点链（:77 起）——本批只触 put 面（C.6）。**修至任一面禁顺带触碰他面**。
- **8.41/8.42 产物面**：零文件交集（core/runtime 域——除 D.2 两行接线为 PA 表预设允许形态）；8.41 受害侧钉零触碰；8.42 全部钉（红①–红⑤ 驱动器+E2 泛在钉×2）零触碰（零站点交集——钉文件核对义务登记于裁定 E）。
- **8.44 成员（G07+G08 11 项 + C10-015）**：R4-04 域零触碰——同 owner doc 后续批（C6-007 孤儿清扫/C6-011 Files.walk/C3-005/006/010-012/C6-005/006/011/C7-003/010 零顺手修）。
- **P3×9**：C7-008（Avro schemaByIdCache 上界——B.1 负缓存面相邻，零顺手修）；C2-006（file saveState 锁内 I/O——C.1/C.4 同文件已声明边界残留零触碰）；C2-007（FileSourceReader 监视器 I/O——零交集）；MA-19（MANIFEST_JVM_LOCKS 静态锁表——C.4 同载体零触碰）；MA-20（CEP counter 标签残留——W-01 tag 正例相邻面零触碰）；C5-003（RocksDBMapState collectMap——C.3 同文件不同方法零触碰）；C6-013（CepOperator close 链 suppressed——D.1 同文件零触碰）；C6-014（deleteIfExistsQuiet 空 catch——零交集）；C7-011（kafka checkConnection 声明——零交集）。执行期意外触及禁顺带——四件证据链申报或回退。
- **C8-008 深度遥测面（G10 deferred→R-G）**：W-04 pendingCommits 深度 gauge / W-09 快照耗时 gauge / W-10 缓冲深度 gauge 建议面零触碰——三处 typed fail-fast 的错误 param 承载最小可见性（plan Non-Goals/R-G 边界条款）。
- **cep `_gen/`**：`nop-stream-cep` main 树 `model/_gen/` 生成目录（4+ `_Cep*Model.java`）零触碰（F2 订正维持）。
- **G06/G10/C10-016**：MA-12 条化激活（G06 deferred）/C8-011 sendTimeout（G10）/C10-016（`TestBatchLoaderSourceFunction` 零触碰）——全部维持 plan Non-Goals。
- **runtime 面**：JobCoordinatorMain 本体零 diff（watch-only 维持）；GraphModelCheckpointExecutor 仅 D.2 两行接线（diff 分组登记）；runtime 其余产品源码零 diff。
- **批外模块**：nop-stream-core 其余文件（除 CheckpointConfig/StreamMetricsRegistries[预设零 diff]/NopStreamErrors[零新码=零 diff]/GraphModelCheckpointExecutor[D.2 两行]）/flow/sql/fraud-example/kafka 产品源码零触碰（kafka 仅 2511 Follow-up 归属落款=零 diff）。

## 端到端与接线验证义务（rule 22/23——随 Phase 2 落证）

- **端到端（rule 22——plan Phase 2 checklist 三链）**：≥1 条 CEP 链路覆盖 W-07/W-10 组合面（事件注入→key 全清→timerKeys 收敛 + 缓冲上限注入→typed 越界——从 processElement 入口到过载出口）；≥1 条 2PC 链路覆盖 W-04/W-11 组合行为（写入多 epoch→上限注入 typed→commit 超时契约——从 invoke 到 commit 终局；gated 真库测试不强制入 CI，本地留证）；≥1 条 Avro 装配-停止链路覆盖 W-02/W-03（registry 失败注入→typed/负缓存→close→线程/fd 收敛——从 encode/decode 入口到生命周期终点）随红②③钉承载。
- **接线验证（rule 23——十三项）**：tags 唯一化真生效（红① a）；close 摘除真生效（红① b）；负缓存真生效（红② b 计数）；close 链真生效（红③——测试缝承载[K10]）；上界真生效（红④）；流式真生效（红⑤）；锁超时真生效（红⑥ a）；清理点真生效（红⑦）；once-guard 真生效（红⑧）；校验真生效（红⑨ a + runtime 两行接线结构核对）；过载真生效（红⑩）；语句超时真生效（红⑪ a+c）；字节界真生效（红⑫）。

## 批外新发现登记（亲读发现即登记，禁瞒）

- **BF-1（P3 量级——缓冲旋钮族相邻面，本批零处理）**：`MessageSourceReader`/`DebeziumCdcSourceReader` 的 `QUEUE_CAPACITY=256` 为硬编码常量非可配旋钮（:53/:59 亲证）——条数维与字节维（C.6 本批落地面）不对称：字节界可配而条数界固定。归属：连接器参数面后续批/R-G 候选登记——不在 12 工单面内，禁顺带。
- **BF-2（信息性——验证限界登记，非缺陷）**：`validateUnalignedConfig` 的 LOCAL 执行路径无自动 validate 漏斗（K4——唯一生产调用点为 runtime executor）——W-09 `validateSnapshotBudget` 沿先例同限界（LOCAL 面经测试直调/用户显式调用覆盖）。该限界为先例既有限界非本批新缺口；如后续批认为 LOCAL 需 validate 漏斗，归 core 执行链后续治理。
- 核证范围 = 12 工单站点文件全量抽验（K1 锚清单）+ 4 配置载体文件（K8）+ NopStreamErrors + ChangeEvent/MessageSourceFunction 载体类型（K5）+ FileSourceReader 标杆（K7）+ TestAvroWireFormat/TestCheckpointTransportPersistenceValidation 测试形态；除 BF-1/BF-2 外无批外缺陷。

## 评审记录

- **closure audit 勘误注记（2026-11-07 @ `fb067084da`，agent_ec84fc86 APPROVED with corrections）**：①W-06 中断处置形态=重置中断标志+typed `ERR_STREAM_CHECKPOINT_ERROR`（裁定 C.4「原样传播」字面偏离——取消信号经中断标志+typed 双通道保留，行为等价且合项目错误码约定，红⑥ 断言按 typed 写）；②W-12 payload 包络落点=nop-stream.md 行为块（plan Phase 3 承诺的 connectors.md 能力矩阵未动——落点偏离登记）；③K10 测试构造点计数勘正 15→14 随评审 F-3 已落。
- **设计评审 R1**（2026-11-07，agent_ffdca052 fresh session 只读 @ `3b3df13655`，live 抽验 70+ 处）：**PASS-with-revisions——0 Blocker + 0 Major + 6 Minor，全部随轮落盘，无需 R2 放行 Phase 2**。核证通过面：K1–K10 亲读命中（K10 一处计数勘正 15→14[F-3]）；七源引用忠实（2511/2510/2535/R01 SSOT/merge-adjudication/batch-proposal/owner docs——零回退红线落款工单对应无越界）；裁定 A–E 全部成立（含 C.4 双级锁 60s 期限/C.3 单遍 RocksIterator 钉序/D.2 声明分支校验+new-only 缺省+runtime 两行接线偏离合规[K4 先例同构+PA row 9 预设授权通道]）；rule 14 合规；条件申报①–⑤亲证零触发。三风险专项结论：①C.6 下界口径足够（serialize-to-measure 否决正确；E5 措辞补 F-2 明示）②D.2 偏离合规非守卫空壳（先例同构+opt-in 语义+生产路径真实生效）③B.1 两步义务满足且时序可测（审计建议③ 双选项菜单内+红② 三 leg 确定性）。修订 F-1~F-6 随轮落盘。

- **设计评审 R1**：（预留——独立评审子 agent fresh session 只读完成后回填：结论/修订清单/live 抽验规模/想象性分析三风险点结论）
