# stream-connectors 并发正确性与资源包络契约（R2-08 批八项裁定）

> Status: active
> Last Reviewed: 2026-10-29（plan 2511 Phase 1 产出；本批唯一设计裁决权威）
> Source: plan 2511（`ai-dev/plans/2511-cra-phase8.17-r2-08-connectors.md`）Current Baseline 七成员表 + Baseline 勘误 5 条；发现事实源 = WI 1.2 审计目录 `ai-dev/audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/`；行号 = live `c1674460dd` 回读值（本 Phase 2026-10-29 亲读复核，执行期漂移以 live 为准并登记于 plan 勘误）
> 实现锚：nop-stream-rocksdb `metrics/RocksDBMetricsRecorder`（gauge 闭包 :83、`readProperty` :91、close :65-67、dbRef :52）、`RocksDBSnapshotSerDe`（snapshotState :84、snapshotMapState :186、snapshotKeyedState :284）+ `RocksDBKeyedStateBackend`（incrementalCheckpointEnabled :119、snapshotState 分派 :827-835）；nop-stream-cep `operator/CepOperator`（copyForSubtask :329-340、createNFA 调用 :607）、`nfa/NFA`（open :193-204、close :206-214）、`nfa/compiler/NFACompiler`（NFAFactoryImpl :1097、createNFA :1119-1121）、`pattern/conditions/RichIterativeCondition`（runtimeContext :47-53）+ `RichCompositeIterativeCondition`（传播 :56-74）；nop-stream-format-avro `AvroSchemaRegistryClient`（connectTimeout :48、computeIfAbsent :69、http.send :116）+ `AvroWireFormat`（encode :54）；nop-stream-connector-jdbc `JdbcTwoPhaseCommitSink`（writeLedgerEntry :852-861、abort :748-752、DDL :795-810、列常量 :118-121）；nop-stream-connector `file/FileTwoPhaseCommitSink`（updateManifestAtomically 调用点 :346/:374、方法体 :560、manifest 键 `N.sK`、AR-14 锁 :96）；nop-stream-connector-debezium `DebeziumCdcSourceReader`（QUEUE_CAPACITY :57、snapshotState :255-278、withOffsetMap :273）+ legacy `DebeziumCdcSourceFunction`（snapshotState :431 起）；nop-stream-connector-kafka `KafkaTwoPhaseCommitSink`（newProducer :142-158、事务守卫 :149-156、saveState commit 段 :207-215）+ nop-stream-core `CheckpointConfig`（DEFAULT_CHECKPOINT_INTERVAL :27）
> Related：R01 族级 SSOT = [ledger-retention-policy.md](ledger-retention-policy.md)（裁定④的参数/键名/形态唯一来源，本文档零复制参数值）；R03 范式先例 = [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6（裁定③锁内 HTTP 外移的成员投影来源）；[registry-lifecycle-contract.md](registry-lifecycle-contract.md)（REGISTRY 契约域与裁定⑥的边界注记）；上游先例 = [stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)（R2-07，裁定④的 face token 前例 `assignment`/`msgqueue` 不复制）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | C1-002（←C2-001/C6-004） | gauge 闭包改调新增 `synchronized readCurrent(property)`——取 `dbRef.get()` 移入监视器，读时非 null 即 native 未关闭、null 返 NaN；依赖既有 close 顺序（recorder.close :65 `dbRef.set(null)` 先于 backend db.close），零新锁 |
| ② | C1-001（←C1-005/C1-006） | `copyForSubtask` 对 `nfaFactory`+userFunction 做序列化 round-trip 深拷贝——每 subtask 私有 NFA 条件图，三面（ctx 槽位/open×N/交叉 close）一次封闭；`TestCepOperatorSubtaskCopy` assertSame 反转随同 diff |
| ③ | C2-002（←C3-003/C4-003/C6-008/C8-004/C7-006①） | R03 §3.6 成员投影：每 `HttpRequest` 加请求级 `.timeout()`（配置键 `nop.stream.avro.registry-request-timeout`，缺省 10s）+ mapping function 出锁（get→miss→锁外 fetch→`putIfAbsent` 幂等回填），三路径全覆盖；`StreamAvroException` typed 失败维持 |
| ④ | C3-002（←C5-006/C7-001/C8-007/C7-007①） | R01 SSOT 成员传导：face token=`epochledger`（JDBC 台账与 file manifest 同 face）；JDBC=形态 A 周期清理，锚列 `committed_at`；manifest=形态 B commit 顺带裁剪，epoch 号账龄窗口经既有 checkpointInterval 换算；abort 不触台账语义维持；参数零发明 |
| ⑤ | C5-002（←C8-005） | 增量路径缺省化（`incrementalCheckpointEnabled` 缺省 true）+ 残留全量路径规模阈值 typed 拒绝兜底（`nop.stream.rocksdb.snapshot-materialize-max-entries`，缺省 100000）；恢复面双形态兼容，`TestRocksDBSnapshotRestore` 保持绿 |
| ⑥ | C8-001（独立案） | per-record offset 暴露为预设默认分支：reader 发射点从 `ChangeEvent.metadata` 特化字段（lsn→commitLsn→binlogPosition+binlogFile 优先级序）提取位点维护 per-server last-emitted 位点表，`snapshotState` 抬位点合成的引擎 offset map 而非引擎 store 快照；restore 跨 split 取 min 保守重放；位点不可得 = typed 失败非回退；`NopStreamOffsetBackingStore` 零改动（R4-24 对齐注记） |
| ⑦ | C8-006（独立案） | `newProducer()` 显式缺省 `transaction.timeout.ms=900000`（配置键 `nop.stream.connector.kafka.transaction-timeout-ms`）+ 构造期耦合校验 `checkpointInterval × 3 ≤ transaction.timeout.ms` typed fail-fast 拒配；上界受 broker `transaction.max.timeout.ms` 声明约束 |
| ⑧ | C10 七条验收前置 | 七个测试缝 + 八个零回退钉全列（§3.8），六处假保护/缺陷载体测试同 diff 改写（新断言形态逐条落名），修复 diff 不触碰改写外钉内断言 |

## 二、背景与动机

R2-08 批七项 P1 覆盖三类缺陷面：**并发正确性**（gauge 闭包锁外取 native 引用的 SIGSEGV 竞速窗、并行 CEP 共享编译态条件图的原地变异、Avro registry `computeIfAbsent` bin 锁内无超时 HTTP）、**持久化无界增长与丢失面**（2PC 台账/manifest 只增不删、Debezium checkpoint offset 领先已发射记录的 ≤256 条静默丢失）、**资源包络与触界失败循环**（RocksDB 全量 checkpoint 整库物化进堆、Kafka 事务存活期与 checkpoint 间隔零裕度）。

三类面被绑为一批（batch-proposal §二.2 R2-08 行），因其共享同一验证通道（`./mvnw test -pl nop-stream -am`）与 owner doc，且保留纪律成员（C3-002）必须按 R01 族级 SSOT 传导、锁内 HTTP 成员（C2-002）必须按 R03 范式投影——分散裁决会造成族内参数/范式分叉。draft review 勘误 5 条已并入 Baseline（行号微漂移、C5-007 跨单元边界注记、C3-014 residual 注记义务归 Phase 4）。本文档一次裁定八项，使 Phase 2 实施只有一种合法形态。

## 成员 → 裁定映射

| # | 成员（判级） | 面 | 裁定 | 修复位（live : 行） | 回归测试（缝，§3.8） |
|---|---|---|---|---|---|
| 1 | 1.2-C1-002（P1，MA-02 主；C2-001/C6-004 吸收） | gauge 闭包锁外取 dbRef（native UAF/SIGSEGV 窗口） | ① | RocksDBMetricsRecorder :83 闭包 + 新增 `readCurrent` | 缝 1（C10-002 改写） |
| 2 | 1.2-C1-001（P1，MA-03 主；C1-005/C1-006 吸收；r4 R5-CEP-05 双登记） | 并行 CEP 共享条件图原地变异（ctx 错配/open×N/交叉 close） | ② | CepOperator :329-340 + NFACompiler/NFA 拷贝链 | 缝 2（C10-003 改写） |
| 3 | 1.2-C2-002（P1，MA-04 主；C3-003/C4-003/C6-008/C8-004/C7-006① 吸收） | computeIfAbsent bin 锁内无超时 HTTP（挂死+锁毒化） | ③ | AvroSchemaRegistryClient :69/:116 + AvroWireFormat :54 | 缝 3（C10-008 改写） |
| 4 | 1.2-C3-002（P1，MA-05 主；C5-006/C7-001/C8-007/C7-007① 吸收） | 2PC 台账/manifest 只增不删（O(E) 放大） | ④ | JdbcTwoPhaseCommitSink :852-861/:748-752 + FileTwoPhaseCommitSink :346/:374/:560 | 缝 4（C10-007 改写，双面同盖） |
| 5 | 1.2-C5-002（P1，MA-08 主；C8-005 吸收） | 全量 checkpoint 整库物化进堆（2-5× 放大 OOM 面） | ⑤ | RocksDBKeyedStateBackend :119/:827 + RocksDBSnapshotSerDe :186/:284 | 缝 5（C10-012 改写） |
| 6 | 1.2-C8-001（P1，独立案） | checkpoint offset 领先已发射记录（≤256 条静默丢失） | ⑥ | DebeziumCdcSourceReader :255-278 + legacy :431 | 缝 6（C10-013 双改写） |
| 7 | 1.2-C8-006（P1，独立案） | 事务存活期与 checkpoint 间隔零裕度（60s==60s 触界失败循环） | ⑦ | KafkaTwoPhaseCommitSink :142-158/:207-215 | 缝 7（C10-014 新建） |
| 8 | C10 七条验收前置 | 假保护/缺陷载体改写 + 零回退钉 | ⑧ | 测试树（§3.8 两表） | 缝 1–7 全覆盖 |

## 三、核心设计契约

### 3.1 裁定①：gauge 锁内取值（C1-002，SIGSEGV 窗口封闭）

- **唯一结论**：`RocksDBMetricsRecorder` 新增 `public synchronized double readCurrent(String property)`（监视器内 `readProperty(dbRef.get(), property)`），gauge 闭包 :83 改调 `ref -> readCurrent(property)`——`dbRef` 的求值点从闭包求值时（锁外）移入监视器内。monitor 内读到非 null 引用时，close 的 `dbRef.set(null)`（:66，同一监视器）尚未发生，native handle 尚未关闭；读到 null 返 NaN（detached 语义）。
- **close 竞速语义（成文）**：正确性依赖既有 close 顺序——backend close 先调 `recorder.close()`（置 null）再 `db.close()`；两步都发生在 scrape 线程可能持有的同一监视器之外或之内均安全：scrape 持锁期间 close 阻塞等待，拿到锁时引用已为 null 返 NaN；close 持锁期间 scrape 阻塞等待，拿到锁时 handle 已关但引用为 null 零 native 触达。窗口内任意交错不存在「取旧引用→close→用旧引用」路径。零新锁、零新原子变量。
- **备选不采**：`readProperty` 内消费 AtomicReference + 异常兜 NaN——「取引用」与「用引用」仍在锁外两步，异常兜底只覆盖已抛出的 InvalidHandle 而非 native 段崩溃（SIGSEGV 不经 Java 异常路径），封闭不完整。

### 3.2 裁定②：CEP copyForSubtask 三面封闭（C1-001）

- **唯一结论（MA-03 三面一次）**：`CepOperator.copyForSubtask()`（:329-340）对 `nfaFactory`（:333）与 `getUserFunction()`（:335）做序列化 round-trip 深拷贝（`serializationRoundTripCopy` 同款手段）后传入副本构造——每 subtask 的 `NFAFactoryImpl.createNFA()`（NFACompiler :1119-1121）从私有 factory 创建私有 `states` 图，三面同时封闭：
  - **面 1（ctx 槽位，C1-001/C1-005）**：条件对象随拷贝私有，`NFA.open()`（:193-204）经 `RichCompositeIterativeCondition` 传播（:56-57）对各 subtask 自己的条件实例 `setFunctionRuntimeContext`——runtimeContext last-writer-wins 消除，`RichIterativeCondition.runtimeContext`（:47-53）非 volatile 在实例私有化后无害（单 subtask 单线程访问），零 volatile 追加。
  - **面 2（open×N，C1-006）**：条件 `openFunction` 各 subtask 各自一次（私有实例），N 次泄漏消除。
  - **面 3（交叉 close，C1-006）**：一个 subtask 的 `NFA.close()`（:206-214）只关闭自己的条件资源，首个 close 全关面消除。
- **序列化约束前提核验**：`NFAFactory` 已 Serializable；round-trip 前提 = 模式条件对象（含用户 `IterativeCondition` lambda 与 `RichCompositeIterativeCondition` 及嵌套数组）可序列化——实施时在 `copyForSubtask` 对不可序列化条件 typed 失败（`StreamException` 指引条件实现 Serializable），不做静默共享回退。**测试侧已知形态**：`TestCepOperatorLateRecordsDroppedMetric#twoParallelInstancesDoNotCrossCount`（:124-125 调 copyForSubtask）的 fixture 用匿名 `new PatternProcessFunction<>(){...}`（:96-100）捕获非 Serializable 的 JUnit 测试实例——round-trip 落地后该用例必抛 NotSerializableException，属测试侧非产品缺陷，随同 diff 将 fixture 改为 static 命名类或静态 lambda（不改测试断言语义）。
- **代价评估**：每 subtask 一次、启动窗内一次性（copyForSubtask 仅部署展开时调用 :347/:360），运行期零开销；`nfa` 实例本身仍每 operator `createNFA()`（:607）各自创建，不重复拷贝。**部署期行为变更声明**：round-trip typed 失败对存量非 Serializable 用户函数/条件是一次部署期行为变更（原共享形态可运行）——与 CEP 管线 spec 对函数/条件的 Serializable 要求本就互证（跨进程部署序列化面同一约束），风险低；存量用例的适配形态见 §3.8 影响面清单。
- **r4 R5-CEP-05 双登记消号注记**：本裁定落地后 cra-index L146 行与 r4 登记一并消号，不重复立项。
- **备选不采**：NFA.open/close 只对本实例新建条件包装执行生命周期——需在 NFA 层引入条件包装/注册簿机制，改动面大于一次拷贝且共享图的其他读路径（:139/:144/:153）仍跨 subtask 可见。

### 3.3 裁定③：Avro registry 请求级超时 + 锁外建连（C2-002，MA-04=R03 显式裁定项）

- **范式投影声明**：本裁定 = [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6「锁内仅状态机变更、I/O 出锁」在 HTTP 客户端形态的成员投影——CHM bin 临界区（`computeIfAbsent` :69）内只允许 map 状态变更，HTTP I/O 全部出锁。
- **超时（根治 F5 挂死）**：`http.send`（:116，getSchemaById/getLatestSchema/registerSchema 三路径共用）所发每个 `HttpRequest` 加请求级 `.timeout(Duration)`；配置键 `nop.stream.avro.registry-request-timeout`，缺省 **10s**（与既有 connectTimeout :48 同量级），`≤0` 装配期 fail-fast；配置化经构造器 Duration 参数承载（客户端为纯构造注入对象，不直读全局配置）。
- **锁外建连形态**：`getSchemaById` 的 `computeIfAbsent(id, this::fetchSchemaById)`（:69）拆为 get→miss→**锁外** fetch→`putIfAbsent` 幂等回填（putIfAbsent 失败即取已存值，并发回填无损）；`AvroWireFormat.encode` :54 的 mapping function 随之不再持 bin 锁执行 POST+GET。禁 I/O 留在 CHM bin 临界区立为客户端实现契约条款。
- **typed 失败维持**：registry 失败 `StreamAvroException` 维持（超时到期抛出经既有 catch 包装路径）；owner doc :114「失败面全 typed」声明在挂死形态修复后重新成立。
- **族边界注记**：与 R4-24 外部依赖超时契约族（C8-009 JDBC 语句级/C8-011 message sendTimeout）互注对齐、不代裁；MA-10（C7-006② 回落/负缓存）与 MA-11（HttpClient close）为本文件簇 R4-03 后续工单，本裁定只落超时+锁外建连，为其立前提。

### 3.4 裁定④：2PC 保留策略（C3-002，MA-05=R01 SSOT 成员传导显式裁定项）

- **face token 钉死 = `epochledger`**（键形 `nop.stream.ledger.epochledger.*`）：JDBC 台账与 file manifest 是同一账本语义（epoch 提交台账）的两载体，共享同一保留窗约束（**保留窗 ≥ 可重提交 epoch 窗口**，防误删未到期行踩坏恢复幂等守卫——SSOT §3.1 第 4 项同源），单 face 防双载体参数分叉；与 plan 2510 已占用的 `assignment`/`msgqueue` face 不复制不重名。参数/键名/形态判据全部引 [ledger-retention-policy.md](ledger-retention-policy.md) §3.1/§3.2/§3.3，**本文档零复制参数值、零参数发明**；成员传导行已追加至其 §3.4，参数修订只走该文档。
- **载体一面：JDBC 台账（nop-stream-connector-jdbc）**：
  - 锚列 = `committed_at`（writeLedgerEntry :852-861 第 4 参时间戳列，列常量 :121；写入后不被业务回改的账龄事实列），谓词 = `committed_at < now − retention`——台账行仅在 commit 成功路径写入（INSERT 即成功终态），无在态谓词追加。
  - 清理载体 = **形态 A 周期清理**（机器速率写入面，SSOT 裁定②必须 A）：挂 JDBC 模块生命周期链（`LifeCycleSupport.doStart` 形态，先例 = `SysDaoMessageService.cleanupExpiredEvents`），delete-batch-size 有界拾取主键集 + `IN` 集批删；周期链回归断言调度器真实调用清理体（防 1.6-C7-005 空壳先例）。
  - **abort 不触台账语义维持**：`abort(epochId)`（:748-752）仅 `getPendingCommits().remove`，数据从未写入故台账无行——本裁定零改写。
- **载体二面：file manifest（nop-stream-connector）**：
  - 账龄谓词 = manifest 键编码的 epoch 号账龄——`epoch(键) < 当前提交 epoch − 保留 epoch 窗口`，保留 epoch 窗口 = `ceil(retention-days × 86400000ms ÷ checkpointIntervalMs)`（向上取整防少留）；checkpointIntervalMs 为既有 `CheckpointConfig` 值经部署装配链传入（非新参数），retention-days 引 SSOT face 键——零第八参数。**换算基声明**：换算基 = 装配时传入的 interval 常量（sink 生命周期内固定，不动态重读）；跨运行 interval 变更时已裁剪/已保留集不回溯重算——变更后新窗口仅约束后续裁剪轮，历史偏差为可接受语义（窗口偏差方向由 ceil 与 retention 缺省 30 天的量级差吸收）。
  - 裁剪载体 = **形态 B commit 顺带裁剪**（SSOT §3.3 形态 B 判据满足：写入路径天然周期性触达[每 epoch commit 已在 AR-14 锁 :96 内全量 load/rewrite :346/:374→:560] + 条目保留窗短于台账 TTL 面；manifest 为 Properties 文件非 DB 表，形态 A 周期链无宿主）：裁剪并入既有 rewrite，复杂度从 O(全部 epoch) 收敛为 O(保留集)。
  - **restoredKeys 守卫面**：R6 CON-01 恢复集（:110-114）语义零改写——裁剪只删超保留窗老键，保留窗 ≥ 恢复重提交窗口保证不误删可恢复 epoch。
- **非裸 DELETE 守卫**：两面清理均锚定谓词 + 幂等（重复触发无害）；`TestJdbcTwoPhaseCommitSinkDeep` 恢复幂等钉零回退；清理失败 ERROR + 计数可见非吞掉（SSOT §3.1 第 5 项包络）。
- **键常量归属**：两载体为兄弟模块（nop-stream-connector-jdbc 不依赖 nop-stream-connector，pom 亲读），按 SSOT §3.1 第 6 项「键常量归属清理载体所在模块」——JDBC 面三键常量落 nop-stream-connector-jdbc 新建类型化 Configs 接口；manifest 面只消费 retention 投影（经装配传入的换算窗口，不直读键），nop-stream-connector 零新增键消费。

### 3.5 裁定⑤：RocksDB checkpoint 物化包络收敛（C5-002）

- **预设默认分支 = (c)+(b) 组合：增量路径缺省化 + 全量残留面阈值 typed 拒绝兜底**：
  - `RocksDBKeyedStateBackend.incrementalCheckpointEnabled`（:119）缺省翻转为 **true**——增量 SST 路径已实现（:828 分派），缺省化使「状态超出堆」包络内默认路径不再整库反序列化物化为堆内对象图（`snapshotKeyedState` :284 / `snapshotMapState` :186 的全量驻留面退为 opt-in）。
  - 显式选择全量路径（opt-out）时，物化面加规模阈值守卫：`nop.stream.rocksdb.snapshot-materialize-max-entries`（缺省 100000，`≤0`=禁用守卫显式自担），超阈值 typed `StreamException` 指引回增量——显式失败优于 OOM（plan 选项 (b) 承载全量 opt-out 残留面，非纯文档声明）。
- **恢复面双形态兼容声明**：`snapshotState()` 分派（:827-835）已双形态；恢复路径须同时接受全量与增量 StateSnapshot（round-trip 语义等价），`TestRocksDBSnapshotRestore` 保持绿为验收钉。
- **owner doc 数值包络声明随批（E5）**：缺省增量、全量 opt-out 阈值与指引表述归 Phase 3 统一落账。
- **时长维边界注记**：checkpoint 时长/延迟维由 C8-010（R4-03）分立承接，本裁定零触碰。

### 3.6 裁定⑥：checkpoint offset 对齐（C8-001，per-record last-emitted 位点协议）

- **两分支一次定夺 = 采 per-record offset 暴露**：reader 在发射点（`pollNext` :209-234 dequeue 后、向下游 emit 前）从事件携带的 `ChangeEventMetadata`（`io.nop.message.debezium.ChangeEvent` 的 metadata 字段；位点载体 = 连接器特化字段 `binlogPosition`/`binlogFile`/`lsn`/`commitLsn`，ChangeEventMetadata.java :42-57，无通用 partition/offset 访问器）提取位点，维护 last-emitted 位点表；`snapshotState`（:255-278）的 `split.withOffsetMap(offsets)`（:273）改为抬入 **last-emitted 位点合成的引擎 offset map** 而非引擎 offset store 当前值（:262 经 embeddedFunction 间接取 store 快照，DebeziumCdcSourceFunction :440-447 `store.getOffsets()`）——快照位点恒 ≤ 已发射边界，≤256 条（QUEUE_CAPACITY :57 + handler 在途 1 条）静默丢失窗口结构性消除。
- **位点键形（唯一结论）**：按连接器类型取 metadata 特化字段，优先级序 = `lsn`（PostgreSQL）→ `commitLsn`（SQL Server）→ `binlogPosition + binlogFile` 组合（MySQL，两字段须同时在位）；同 server 同连接器类型下键形稳定，比较在数值域内进行（同 binlogFile 内比 pos）。**null 面逐字段判据**：`metadata == null`（DebeziumEventConverter.java :69-95 `parseMetadata` source 缺失即返 null，两构造路径均可能落 null）或所选键形字段缺失/不全（MySQL 缺 binlogFile 或 binlogPosition 任一）= typed 失败（`StreamException` 指明 split 与事件序），不回退旧 offset 不静默跳过（rule 24）。
- **per-split → per-server 合成/还原规则（唯一结论）**：引擎位点语义为 per-server/单流全局（非 per-table——`DebeziumTableSplit` 类 javadoc :32-38 自认「instance-level copy、restore takes the first one、per-table attribution deliberately NOT attempted」），故 last-emitted 表**以 server 标识（metadata.serverName）为键、不按表拆分**——同一 reader 的全部 split 共享同一 server 级位点；`snapshotState` 将其合成为引擎 offset map 形态（partition key = serverName，value = 连接器特化位点字段，`NopStreamOffsetBackingStore.toSerializable` 兼容格式）写入每个 split（保持既有「同 map 副本」分发语义零改写）；**restore 取各 offset-carrying split 位点的数值最小值**（同 binlogFile 内 min pos / min lsn / min commitLsn）保守重放——跨 split 取 min 保证不越过任一 split 视角的已发射边界，宁可重复不丢失，取代既有「取第一个」的任意性。
- **恢复重放语义**：恢复自 last-emitted 位点起重放，标准 checkpoint 界内 at-least-once；下游幂等不再作为正确性前提声明（owner doc :130 REPLAYABLE 表述随批精确化，归 Phase 3）。
- **落点边界**：修复全部落 connector-debezium 侧（reader + 事件通道）；`NopStreamOffsetBackingStore` 与 nop-message-debezium 引擎 commit 策略本体**零改动**——读取面只消费 `ChangeEvent.metadata` 特化字段、只按既有序列化格式写回，不触 backing store 生命周期契约；按 [registry-lifecycle-contract.md](registry-lifecycle-contract.md) 作 R4-24 对齐注记（非代裁）。
- **legacy 面随修**：`DebeziumCdcSourceFunction.snapshotState`（:431 起，store 直取 :440-447）同机制小窗口（collect 直连，偏斜 ≈1 条）随同一裁定修复，不单列。
- **备选不采**：恢复期安全重叠重放 + 下游幂等声明——位点精度外包给下游契约，REPLAYABLE 声明的成立依赖未观测的下游行为；位点字段原生随事件可取，reader 侧自洽闭合。per-table 位点拆分不采——引擎位点无 per-table 粒度，伪造 per-table 键即越界 nop-message-debezium 引擎域。


### 3.7 裁定⑦：Kafka 2PC 事务裕度守卫（C8-006）

- **缺省值定夺 = 900000ms**：`newProducer()`（:142-158）显式设 `transaction.timeout.ms=900000`（缺省恰等于 broker `transaction.max.timeout.ms` 缺省上界 900s——零 broker 配置即可用）；配置键 `nop.stream.connector.kafka.transaction-timeout-ms`（`≤0` 装配期 fail-fast）。相对 `DEFAULT_CHECKPOINT_INTERVAL=60000ms`（CheckpointConfig :27）为 15 倍裕度；Flink 同构场景默认 1h 需 broker 上界配合，不采（缺省不得依赖 broker 重配置）。
- **耦合校验 = 构造期 typed fail-fast 拒配**：sink 构造链接收 checkpointIntervalMs（部署装配传入，缺省 `CheckpointConfig.DEFAULT_CHECKPOINT_INTERVAL`），校验 `checkpointIntervalMs × 3 > transaction.timeout.ms` 时抛 `StreamException` 拒配（含两值与处置指引：下调 interval 或上调 timeout ≤ broker max）——**用户显式配置冲突行为 = 拒配，不告警放行**（一次定夺；rule 24 同源）。安全系数 3 覆盖「interval + 快照时长」的存活期超界形态。
- **上界声明**：配置值 > broker `transaction.max.timeout.ms` 时 `initTransactions()` 运行期失败——owner doc kafka-2pc 行耦合声明（E5，归 Phase 3）写明上界约束，本批不做 broker 探测。
- 既有 `TestKafkaTwoPhaseCommitSinkLifecycle` 调用序列/transactional.id 守卫钉零回退，新增属性断言不弱化既有；**零回退钉 #6 触碰边界注记**：checkpointIntervalMs 构造参数引入允许对该测试的 sink 构造调用点做适配性触碰（签名适配），断言零回改。

### 3.8 裁定⑧：测试缝与零回退钉

| # | 测试缝 | 覆盖裁定 | 断言焦点 |
|---|--------|---------|---------|
| 1 | close×scrape 双线程交错压窗器 + 锁内取参白盒断言 | ① | 修复前取参点在锁外（交错窗口存在红）；修复后 close 窗口 scrape 返 NaN 零 UAF、取参点在监视器内 |
| 2 | parallelism≥2 并行 open 交错驱动 + keyedStateStore 探针 | ② | 各 subtask NFA 条件图实例不同、ctx 各自正确；assertSame 反转留痕 |
| 3 | 挂死注入 HttpServer（不响应不拒连） | ③ | 修复前测试超时红线承载无界挂起；修复后请求级超时 typed `StreamAvroException` + bin 锁限时释放（并发 encode 不互阻） |
| 4 | 多 epoch 台账/manifest 写入器 + 保留窗口注错缝 | ④ | 修复前行数/键数无界红；修复后过期清理 + 未到期保留 + 幂等 + 形态 A 调度真实调用（防 C7-005 空壳）；manifest 与台账双面同盖 |
| 5 | 键数参数化 + 堆驻留探针 | ⑤ | 修复前全量物化堆驻留随键数超上界红；修复后缺省增量驻留有界、全量 opt-out 超阈值 typed 拒绝 |
| 6 | 队列偏斜注入 + kill-recover 竞速缝 | ⑥ | 修复前快照 offset > last-emitted 红；修复后 snapshot offset == last-emitted + 恢复重放在途 K 条不丢 |
| 7 | producer 属性白盒断言 + 配置冲突注入 | ⑦ | 缺省 `transaction.timeout.ms` 在位；`interval × 3 > timeout` 注入 typed fail-fast |

**零回退钉（八个，修复 diff 零触碰改写外断言；钉零回改逐条以 git diff 为空核对）**：

1. `TestRocksDBSnapshotRestore`（⑤ round-trip 语义等价钉——改写类 C10-012 仅追加参数化与驻留断言，既有 round-trip 断言零改写）
2. `TestStateFamilyBackendMatrix`（⑤ 状态族 round-trip 面）
3. `TestJdbcTwoPhaseCommitSinkDeep`（④ 恢复幂等关键钉）
4. `TestJdbcTwoPhaseCommitLedgerNamespace`（④ namespace 键面）
5. `TestFileTwoPhaseCommitSink`（④ manifest 面 + AR-14 并行锁钉）
6. `TestKafkaTwoPhaseCommitSinkLifecycle`（⑦ 调用序列/transactional.id 守卫钉；构造调用点允许适配性触碰、断言零回改——见 §3.7）
7. `TestDebeziumCdcSource` 改写外其余用例（⑥ 恢复链钉）
8. `TestDebeziumCdcCheckpoint` 改写外其余用例（⑥ 恢复链钉）；`TestBatchLoaderSourceFunction` 零触碰（1.2-C10-016 归 R-G，Non-Goal）

**六处假保护/缺陷载体测试同 diff 改写（新断言形态逐条）**：

| 测试 | 现断言形态（live : 行） | 缺陷 | 新断言形态 |
|------|----------------------|------|-----------|
| `TestRocksDBMetricsRecorder#testCloseDetachesGaugesFromNativeHandle` :113-130 | 全单线程顺序断言（close 后 NaN） | 假保护（C10-002） | 追加双线程 close×scrape 压窗（多轮交错）；白盒断言 gauge 取参经 `readCurrent`（锁内）；交错全程零崩溃 + 压窗期 NaN 合法；close 后 NaN 断言保留 |
| `TestCepOperatorSubtaskCopy` :43-44 | `assertSame(operator.getUserFunction(), copy.getUserFunction())` 钉住共享 | 缺陷载体（C10-003） | 反转为 `assertNotSame`（userFunction 与 NFA states 图实例均不同）+ 双 subtask 并行 open 后 keyedStateStore 探针各持各自 runtimeContext；旧断言消号理由 = 共享即缺陷本体 |
| `TestJdbcConcurrencyAndBatchSegments#countLedgerRows` :159 | 仅写入存在性断言 | 假保护（C10-007） | 扩为保留策略断言集：多 epoch 写入→清理后行数有界（高水位保留）/未到期行保留/幂等守卫（重复清理零误删）；既有 segmented commit 断言保留 |
| `TestAvroWireFormat#registryUnreachableFailsTyped` :239 | 快速拒连（127.0.0.1:1）注入 | 假保护（C10-008） | 改为挂死注入（本地 HttpServer accept 后不响应）→ 请求级超时 typed `StreamAvroException` + 并发 encode 线程不互阻（bin 锁限时释放断言）；快速拒连 case 保留为快速失败面 |
| `TestRocksDBSnapshotRestore` | round-trip 功能面、规模零断言 | 假保护（C10-012） | 追加键数参数化（小/大两档）+ 堆驻留上界断言（缺省增量路径驻留不随键数线性增长）；round-trip 等价断言零改写 |
| `TestRocksDBSnapshotRestore`（全量 opt-out leg） | 既有参数化仅覆盖缺省增量路径 | 覆盖清零风险 | 追加全量 opt-out 参数化 leg：阈值下强制全量 round-trip 等价 + 阈上 typed 拒绝——防增量缺省化后全量路径零覆盖（与缝 5 同载体执行） |
| `TestDebeziumCdcSource` :166-174 + `TestDebeziumCdcCheckpoint` :288-291 | 前者断言 offset 无条件抬入每个 split 快照（钉住缺陷载体）；后者显式 kill 后快照回避偏斜窗口（测试自吞竞争） | C10-013 | 前者反转 = snapshot offset == last-emitted（引擎 store 领先而快照不领先）；后者快照时点改回竞速窗内（kill 前）+ 恢复重放补齐在途 K 条不丢断言；旧断言消号理由 = 无条件抬入即 ≤256 丢失面本体 |

**随同 diff 影响面（非改写非钉，登记防漏）**：`TestCepOperatorLateRecordsDroppedMetric#twoParallelInstancesDoNotCrossCount`（:124-125 调 copyForSubtask）——裁定② round-trip 落地后其匿名 PatternProcessFunction fixture（:96-100，捕获非 Serializable 测试实例）必抛 NotSerializableException，随同 diff 将 fixture 改 static 命名类或静态 lambda，断言语义零改写。

端到端验证义务（rule 22，随 Phase 2 落证）：≥1 条 2PC 链路 E2E（多 epoch 写入 → checkpoint/恢复幂等 → 保留清理生效 → 作业恢复正确，覆盖裁定④⑦组合行为）；≥1 条 Debezium 链路（offset 快照 → kill-recover → 重放零丢失，覆盖裁定⑥）。

**接线验证义务（rule 23，随 Phase 2 落证）**：形态 A 清理体被生产生命周期链真实调用（裁定④ JDBC 面，防 C7-005 空壳）；last-emitted 位点表真实被 `snapshotState` 消费（裁定⑥，非仅字段存在）；锁外 fetch 路径真实被 encode/decode 调用（裁定③）；`transaction.timeout.ms` 缺省与耦合校验在部署装配链真实生效（裁定⑦）。

**无静默跳过义务（rule 24）**：裁定③超时到期 typed 失败而非静默重试；裁定⑥位点不可得显式失败而非回退旧 offset；裁定⑦配置冲突拒配而非告警放行；裁定④清理失败 ERROR + 计数可见非吞掉（SSOT §3.1 第 5 项包络）；新增公共分支无空方法体/TODO。

### 3.9 实施边界与落账去向

- **Protected Area 声明**：本批站点全部位于 connectors 族模块（nop-stream-rocksdb/cep/format-avro/connector-jdbc/connector/connector-debezium/connector-kafka）的非生成物 main 源码；不触 `nop-core/`/`nop-xlang/` 框架核心、不触 `model/*.orm.xml`、不做 `_` 前缀生成物改动（batch-proposal §二.2 R2-08 行 PA=无）。若实施中裁定引入平台公共落点（外部依赖超时契约设施上收 nop-kernel/nop-api-core，或 2PC 保留清理载体上收平台层），照 2507–2510 先例在对应裁定项内批内闭环申报 plan-first 后方可实施。
- **批内吸收成员**：C1-002←C2-001/C6-004、C1-001←C1-005/C1-006（含 r4 R5-CEP-05 双登记消号）、C2-002←C3-003/C4-003/C6-008/C8-004/C7-006①、C3-002←C5-006/C7-001/C8-007/C7-007①、C5-002←C8-005——全部随主条目同 diff，不单列工单；cra-index 回填以主 ID 收敛 + 括注；C7-006②（MA-10）与 C7-007 非 manifest 面不在本批（merge-adjudication §3.4 已消号归并）。
- **C10 验收前置七条**（C10-002/003/007/008/012/013/014）随本体同 diff 落地；1.2-C10-016（`TestBatchLoaderSourceFunction` 无界自旋）归治理批 R-G，本批零触碰。
- **先红后绿义务（E2）**：红①–红⑦与 §3.8 测试缝一一对应，修复前红证据（交错压窗/assertSame 反转/挂死超时红线/无界行数/堆驻留超界/offset 领先/缺省属性缺失）随执行落 `_tmp/` 留痕；变异或回退论证可替代先红，须登记。
- **零回退纪律**：§3.8 八钉零触碰（改写类仅按上表追加/反转，改写外断言零回改）；`nop-stream-core` `CheckpointConfig`（:27）仅读——裁定⑦不改 core 缺省 interval，零 core diff 为预期形态。
- **owner doc 同步**：`docs-for-ai/03-modules/nop-stream-connectors.md`（Avro 请求级超时与锁外加载表述、2PC 保留策略 ≤3 行摘要节[以 ledger-retention-policy.md 为准]、Debezium offset 语义、kafka-2pc 事务超时耦合声明、rocksdb/cep 模块表述缺口裁定[补节或登记 drift]）+ `docs-for-ai/04-reference/source-anchors.md`——**全部归 Phase 3 统一落账**，本 Phase（设计文档）不触发 owner doc 变更。
- **批外零承接**：R4-03 十二项（MA-09/10/11/13–18 + C8-002/C8-009/C8-012）、R4-04 十一项、R4-24 同案双轨（C3-006）、1.2-C10-016——本批零顺手修；本批落地后向 roadmap 8.43/8.44 行作「8.17 就绪」注记（Phase 4，R4-03 可引用裁定③④为既有裁定）。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|----------|
| 裁定①备选：`readProperty` 内消费 AtomicReference + 异常兜 NaN | 取引用与用引用仍两步锁外；SIGSEGV 不经 Java 异常路径，异常兜底封闭不完整 |
| 裁定②备选：NFA.open/close 只对本实例新建条件包装执行生命周期 | 需条件包装/注册簿机制，改动面大于一次拷贝；共享图其余读路径（:139/:144/:153）仍跨 subtask 可见 |
| 裁定②备选：round-trip 仅 userFunction 不拷 factory | 条件图经 factory 共享，三面只闭一面（C1-005/C1-006 残留） |
| 裁定③备选：仅加请求超时不外移 mapping function | bin 锁内 10s I/O 仍串行化同 bin 全部 key 的 encode（锁毒化降级为限时而非消除） |
| 裁定④备选：jdbc 台账与 manifest 分 face | 两载体同一账本语义与同一保留窗约束（≥ 可重提交窗口）；分 face 引入参数分叉面，违背 SSOT 一处定稿 |
| 裁定④备选：manifest 也用形态 A 周期清理 | manifest 为 Properties 文件非 DB 表，sink 无生命周期调度宿主；commit 路径天然每 epoch 触达 AR-14 锁内 rewrite，形态 B 判据满足且零新增调度链 |
| 裁定④备选：manifest 键直接引入存储时间戳值 | 改 manifest 值形状触碰恢复兼容面；epoch 号账龄经既有 checkpointInterval 换算已精确且零格式变更 |
| 裁定⑤备选：流式写出（迭代器入 SegmentStore 分段） | StateSnapshot 携分段句柄需跨 checkpoint storage 契约扩展，改动面最大；增量路径已实现且经矩阵测试，缺省化以最小 diff 达成同一包络目标 |
| 裁定⑤备选：纯文档声明全量路径包络限制 | 缺陷本体是物化行为，零代码收口违反裁定项前提（plan 明示禁止） |
| 裁定⑥备选：恢复期安全重叠重放 + 下游幂等声明 | 位点精度外包给下游未观测契约；per-record 位点 Debezium 事件原生可取，reader 侧自洽闭合 |
| 裁定⑦备选：缺省 1h（Flink 同构） | 超 broker 缺省上界 900s，缺省即要求 broker 重配置；900s = broker 上界零配置可用且 15 倍裕度足够 |
| 裁定⑦备选：配置冲突告警放行 | 零裕度触界失败循环正是缺陷本体；告警放行把必然触界留作运行期惊喜，违 rule 24 |

## 五、与已有设计的关系

- [ledger-retention-policy.md](ledger-retention-policy.md)：R01 族级 SSOT——裁定④的参数/键名/形态唯一来源，成员传导行落其 §3.4（face token=`epochledger`）；本文档零复制参数值；参数修订只走该文档。
- [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6：R03 锁内重活外移范式——裁定③为其 HTTP 客户端形态成员投影。
- [registry-lifecycle-contract.md](registry-lifecycle-contract.md)：REGISTRY 域（nop-message-debezium `NopStreamOffsetBackingStore`）——裁定⑥零触碰该契约面，仅作对齐注记；C3-006 同案双轨归 R4-24，本批零承接。
- 上游 [stream-core-concurrency-and-resource-envelope.md](stream-core-concurrency-and-resource-envelope.md)（plan 2510）：同验证通道与 SSOT 传导先例（face token 前例），零共享站点零回退其钉。
- 下游：R4-03（roadmap 8.43，MA-10/MA-11 可引用裁定③前提、MA-16/MA-13 可引用裁定④）与 R4-04（8.44）软依赖本批，引用本文档结论为既有裁定不重开体；就绪注记归 plan 2511 Phase 4。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-stream-connectors.md`（Avro 超时/锁外加载、2PC 保留策略 ≤3 行摘要节[以 SSOT 为准]、Debezium offset 语义、kafka-2pc 事务超时耦合、rocksdb/cep 表述缺口裁定）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
