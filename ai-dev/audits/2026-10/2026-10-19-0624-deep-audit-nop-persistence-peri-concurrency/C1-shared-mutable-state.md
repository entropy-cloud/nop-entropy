# C1 共享可变状态与可见性 — nop-persistence-peri（WI 2.5）

## 审计范围与口径

**本批次（1/4）覆盖模块**（逐文件全量通读，非抽样）：

| 源码根 | 文件数/行数 | 触达路径 | C1 判定基调 |
|---|---|---|---|
| `nop-persistence/nop-dbtool/nop-dbtool-core` | 7f / 1,832 | DDL 差异计算链：OrmModelDiffer / OrmDbDiffer / JdbcMetaDiscovery / DataBaseUpgrader / DataBaseUpgradeInitializer | 启动期（`@PostConstruct`）与 CLI/工具期单线程触达，无竞态发现（降档口径见下） |
| `nop-persistence/nop-cdc/nop-cdc-core` | 1f / 11 | CdcConstants 空接口 | **零审计面**：`CdcConstants.java` 为空接口（仅 `public interface CdcConstants {}`，无任何成员）；同级 `nop-cdc-debezium` 为纯 pom（仅 parent + artifactId，无源码、无依赖）。两处合计零可审代码，本维度不产生发现。 |
| `nop-persistence/nop-orm-drivers/nop-orm-tdengine` | 6f / 726 | TDengine 方言与类型映射、TdEntityPersistDriver | 驱动实例字段为配置期写入、运行期只读；SQL 组装为无状态静态工具 |
| `nop-persistence/nop-orm-geo` | 12f / 700 | geo 解析器、各方言 GeometryTypeHandler | handler 为配置期写入的单例共享对象；全局注册表写入仅启动期一处 |
| `nop-persistence/nop-orm-pdm` | 9f / 1,654 | PdmModelParser（1,305 行）等 | 全部可变状态为 per-call 新建实例的线程封闭状态，两处调用点均 `new` |
| `nop-persistence/nop-orm-rpc` | 1f / 913 | RpcEntityPersistDriver 单文件 | 实例字段配置期写入合格；**异步回调跨线程变更会话状态**为本批次唯一发现（C1-001） |

**口径纪律声明**：

1. **基线状态**：`_tmp/cra-baseline-nop-persistence-peri.txt` 的 C1a/C1b/C1c 节因生成脚本的 zsh glob 错误全部为空（每个 ROOT 下均为 `no matches found: --exclude=_*.java`），无有效 grep 计数可参照。本报告按共享前缀第 10 条口径纪律，以人工全量 grep + 36 个文件逐文件通读复现数量结论，未引用任何基线计数。
2. **静态状态全量复现结果**（六根 `grep -P "static\s+(?!final)"` 排除 static 方法/类/静态导入后）：static 非 final 可变字段 **0** 处；static final 可变集合（Map/List/Set/ConcurrentHashMap 等）**0** 处（唯一 static final 引用型字段为 `TdTableMeta.SUB_TABLE_NAME_PATTERN`（`TdTableMeta.java:30`），`Pattern` 不可变，合格）；ThreadLocal **0** 处。六根中 `synchronized/volatile/Atomic*/Lock/new Thread/Executor` 亦全部为 0（C2/C4 的锁与线程池载体在本批次无源生形态）。
3. **dbtool 降档口径论证**：DDL 差异计算链的全部触达点为 (a) `DataBaseUpgradeInitializer.init()`（`@PostConstruct`，IoC 容器启动期单线程）；(b) `nop-cli` 的 `CliReverseDbCommand`（CLI 单命令进程）；(c) `nop-batch-exp` 的 `ExportDbTool`/`ImportDbTool`（批处理任务线程内新建实例、链式局部使用，无跨任务共享实例）。「仅启动期/工具期单线程触达使触发条件不存在」成立，故本批次 dbtool 链无竞态类发现；此为触发条件不存在的裁定，不是对代码形态的豁免——若未来将 Differ/Discovery 改为单例复用，需重新过 C1。
4. **发布安全判定基准**：`TdEntityPersistDriver` / `RpcEntityPersistDriver` 的实例字段（含 `@Inject` setter 注入字段）在 `EntityPersisterImpl.init()`（`nop-orm/.../EntityPersisterImpl.java:97` → `SessionFactoryImpl.createEntityPersistDriver:386-389`，prototype bean）中于容器启动期写入，persister map（`PersistEnvBuilder.java:111-129`）整体构建完成后随 session factory bean 经 IoC 容器发布给并发请求线程——配置期写、运行期读，符合平台惯例（`JdbcEntityPersistDriver` 同型，未重审平台自身实现）。
5. **集外观察（不构成 C1 发现，登记一句）**：`DataBaseUpgrader.upgrade()`（`DataBaseUpgrader.java:55-61`）按 querySpace 顺序逐库执行 DDL 升级，前库成功后库失败时无补偿/回滚语义（DDL 自动提交）——单线程顺序执行，属失败路径语义问题（F2「半个成功」），归正确性/DDL 语义评审，不入 C1 与 overlay。

**批次 2/4 覆盖声明（追加）**：`nop-persistence/nop-db-migration/src/main/java` 72f/3,877 全量逐文件通读（72 个手写文件，无 `_` 前缀文件混入），触达路径 = Liquibase 风格 DDL 迁移引擎（changeSet 执行/回滚、precondition 评估、executor 注册表 `MigrationEngine.changeExecutors` 等）。**载体复现**（grep + 逐文件通读双重）：static 非 final 可变字段 0；static final 可变集合 0（`MigrationFileScanner.CHANGE_TYPE_BY_CLASS` 为 static 块内构建的 `Collections.unmodifiableMap`、类初始化安全发布，`MigrationFileScanner.java:77-98`；`DdlSyntax.java:43-44` 的 `Set.of` 不可变；其余 static final 全为 String 常量）；ThreadLocal / synchronized / volatile / Atomic / 线程创建 / ExecutorService 均 0。模块内 `lock|Lock` 命中 0（无文件锁/DB 锁设施使用）。可变共享状态集中于：引擎实例注册表（C1-002）与 DB 历史表 check-then-act（C1-003）。**降档口径适用性**：引擎执行路径（`migrate`/`rollback`）为启动期/CLI 单线程触达，但 (a) 引擎以 singleton bean（`_vfs/nop/db-migration/beans/default.beans.xml:11` `nopMigrationEngine`）常驻宿主 IoC 容器且三张注册表暴露 public 构造后注册 API（repo 内测试即按构造后注册使用，`TestMigrationEngine.java:207,230-231,485`）→ 注册表形态不适用无条件降档（C1-002 内显式论证，降档为条件化 P2）；(b) 历史表守卫的触发条件是多进程并发启动，单 JVM 单线程论证不成立 → 不降档（C1-003 内显式论证）。**误报排除登记**：`MigrationFileScanner.loadMigration`（`:136-141`）→ `AbstractResourceParser.parseFromResource`（nop-core `AbstractResourceParser.java:73-88`）的 cp-cache 分支经 `ResourceComponentManager.loadPrecompiledObject`（`ResourceComponentManager.java:516-522`）每次 `readObject` 反序列化**新实例**，不产生跨线程共享模型对象，故 `backfillChangeTypes` 的 `change.setType`（`MigrationFileScanner.java:181`）不构成共享可变状态缺陷（候选误报已排除）。**集外观察（不构成发现/CL）**：wiring 漂移——`nopMigrationExecutor`/`nopMigrationFileScanner` bean（`default.beans.xml:15,19`）与引擎内部自建的 `executor`/`scanner` 字段互不相通；引擎内部 `MigrationExecutor executor` 字段（`MigrationEngine.java:67,115`）只写不读（执行路径 `executeChange` 的局部变量 `executor` 于 `:299-306` 遮蔽字段），注册进该字段的自定义 executor 对执行路径无效，且 `nopMigrationExecutor` bean 上注册对 `nopMigrationEngine.migrate` 同样无效——宿主按直觉接线会静默失效，属配置接线/正确性评审范围。

**批次 3/4 覆盖声明（追加）**：`nop-persistence/nop-orm-migration/src/main/java` 20f/3,462 全量逐文件通读，触达路径 = 数据搬迁五状态机流水线本体（MigrationPhase / MigrationJobState / MigrationJobStateStore / OrmMigrationOrchestrator / OrmMigrationJobStatusBizModel 运行期 biz 查询面 / IncrementalJobManager 裸线程链作业 / ShardChangeEventRouter / ShardedUpsertConsumerProvider / IncrementalLagProbe / ConsistencyVerifier / FullCopyBatchJob / SourcePositionPinner / EncryptKeyConsistencyChecker / MultiSinkTopologyProbe / CdcOffsetSeedBuilder / TargetShardMapping 等）。**降档口径**：本模块为运行期触发的多线程流水线（编排 API 调用线程 + 每链作业一条裸 `new Thread` 长驻 daemon + `@BizModel` GraphQL worker 查询面并发读），启动期单线程降档**不适用**；模块无任何"编排 API 必须单线程/单节点调用"的运行时守卫（F10）或 owner 文档契约，多 ops 线程/多节点并发触达编排 API 无防线。**载体复现**（grep + 逐文件通读双重）：static 非 final 可变字段 0；static final 可变集合 1（`MigrationPhase.ALLOWED`，`Map.of` 不可变，`MigrationPhase.java:36-43`，合格）；ThreadLocal 0；synchronized 1（`MigrationJobStateStore.java:74` 对 volatile `tableReady` 的正确 DCL，合格）；volatile 3（`MigrationJobStateStore.tableReady:50`、`OrmMigrationJobStatusBizModel.orchestrator:23`、`RunningChainJob.thread:214`）；裸 `new Thread` 1（`IncrementalJobManager.java:176`）；AtomicReference 1（`RunningChainJob.failure:215`）；ConcurrentHashMap 1（`IncrementalJobManager.runningJobs:50`）。可变共享状态集中面：编排器 5 个非 volatile 惰性组件字段（C1-008）、状态行 store 的全列覆盖式 update 无版本谓词（C1-006）、`RunningChainJob.thread` 晚于 `thread.start()` 发布（C1-004）、`SessionFactoryImpl.shardSelector` 运行期热替换无安全发布（C1-007）。**误报排除登记**：(a) 写门翻转 `OrmMigrationWriteGate.setClosed` → `AppConfig.getConfigProvider().updateConfigValue`，底层 `DefaultConfigProvider.configRefs/configValues` 为 volatile + ConcurrentHashMap（`nop-config/.../DefaultConfigProvider.java:51-52`），读取面 `CFG_ORM_MIGRATION_WRITE_GATE.get()` 可见性合格（平台设施不重审）——本批次门相关发现均为 check-then-act/保持机制缺失面（C1-010），非可见性缺陷；(b) `StreamExecutionEnvironment.getExecutionEnvironment()` 每次 `new`（`nop-stream-core/.../StreamExecutionEnvironment.java:135-137`），多链作业不共享 env 实例，无共享 DSL 状态竞态（候选误报已排除）；(c) `MultiSinkTopologyProbe.probe` 仅编译 JobGraph 不执行（`MultiSinkTopologyProbe.java:63-83`），无跨线程副作用；(d) `TargetShardMapping`/`SourcePosition`/`MigrationJobConfig`（除 `incrementalPositionP0` 单个 PREPARE 期回填字段外）构建后有效不可变且 final 字段为主，final freeze 下跨线程读取合格。

**批次 4/4 覆盖声明（追加，C1 收官批）**：`nop-persistence/nop-nosql/nop-nosql-core/src/main/java` 19f/1,083 与 `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java` 18f/2,377 全量逐文件通读，触达路径 = Redis KV/Hash/PubSub/Lock/RateLimiter/SessionStore 适配面（运行期请求触达，启动期单线程降档**不适用**；唯一启动期组件 `LettuceRedisConnectionProvider` 的配置期字段发布经 `LifeCycleSupport.status` AtomicInteger 的 `start()` CAS 链合格——`doStart` 写 `connectionSupplier`/client 字段（`LettuceRedisConnectionProvider.java:103-125`）→ `status.compareAndSet(STARTING→ACTIVE)`（`LifeCycleSupport.java:79`）→ 请求线程 `checkIsActive()` 读同一 atomic（`:62-65`）建立 happens-before，不构成发现）。**载体复现**（grep + 逐文件通读双重）：static 非 final 可变字段 0；static final 可变集合 2（`LettucePubSubService.CAPABILITIES/WARN_ONCE`，`EnumSet.of` 为可变 RegularEnumSet——CAPABILITIES 经 `getCapabilities()` 泄漏，见 C1-014）；ThreadLocal 0；裸 `new Thread`/Executor 0；主源码 Atomic 0。synchronized 3 处：`LettucePubSubService.subscribe` 方法级（`:72`）、`getOrCreateConnection` DCL（`:96-105`，volatile+sync 合格）、`LettuceMessageService.getMessageService` DCL（`:344-354`，volatile+sync 合格）；volatile 5 处：`LettuceLock.lockValue:26`（合格，I/O 线程写/调用线程读，配合服务端 Lua CAS）、`pubSubConnection:56`（DCL 内发布，合格）、`suspended/cancelled:180-181`（单标志可见性合格，复合操作缺陷归 C1-011）、`pubSubService:341`（DCL 合格）。**运行期触达证据**：nop-auth-service 以条件 bean 装配 5 个 `INosqlService` 消费者（`auth-service.beans.xml:45-141`，MFA/SMS/Email/限流/登录计数 store），KV 面经 `putExAsync`(psetex)/`putIfAbsentExAsync`(SET NX PX) 原子路径触发（`RedisMfaChallengeStore.java:93,200`、`RedisSendCodeRateLimiter.java:94`，其 javadoc 明示绕开 computeIfAbsent/getEx 语义陷阱）；PubSub 面 repo 内生产调用方 0、测试触达在册（`TestLettuceNosqlService.java:559-734` 含同 topic 多订阅者）。**已声明边界登记（不入发现）**：`LettuceLock` javadoc 明文"per-instance 单线程使用，跨线程并发 tryLock/unlock 为 undefined behavior"（`LettuceLock.java:19-23`），且 `lock(key)` 每次返回新实例，契约与工厂形态自洽——不构成缺陷。**C1 四批次收官**：批次 1（dbtool/cdc/tdengine/geo/pdm/rpc 六根）+ 批次 2（nop-db-migration 72f）+ 批次 3（nop-orm-migration 20f）+ 本批（nop-nosql 两根 37f）= 计划内全部源码根覆盖完毕，C1 维度审计关闭；全维度累计发现 C1-001..C1-014、集外线索 CL-001..CL-014。

### 共享可变状态清单表（五类载体全量盘点）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|---|
| S1 | static 非 final 字段 | 无 | — | — | — | 六根 grep 复现为 0 |
| S2 | static final 可变集合 | 无（`TdTableMeta.java:30` 为不可变 Pattern） | — | — | — | 合格 |
| S3 | 全局注册表写入 | `GeometryObjectHelper.register()`（`util/GeometryObjectHelper.java:15-19`）→ `SysConverterRegistry`（ConcurrentHashMap + volatile，`nop-api-core/.../SysConverterRegistry.java:36-38`） | 唯一调用方 `OrmGeoCoreInitializer.initialize()`（启动期单线程） | 全平台转换热路径 | CHM；guard 查 namedConverters、注册同时写 named+type 两 map，幂等一致 | 合格（启动期单调用方，check-then-act 无并发对手方） |
| S4 | ThreadLocal | 无直接使用；`TdSqlHelper.java:148,185` 读 `ContextProvider.currentTenantId()` | ORM 调用线程 | 同线程 | `FutureHelper.futureCall` 为调用线程内联执行（`FutureHelper.java:122-129`），无跨线程读取 | 合格 |
| S5a | singleton 实例字段（tdengine） | `TdEntityPersistDriver.java:44-51`（tableMeta/jdbcTemplate/entityModel/env/querySpace/dialect/binders，均非 final） | `init()`（`TdEntityPersistDriver.java:54-62`）启动期 | 并发请求线程 | prototype bean per persister + 容器启动期发布（见口径 4） | 合格（与 Jdbc 驱动同型） |
| S5b | singleton 实例字段（rpc） | `RpcEntityPersistDriver.java:58-63`（entityModel/querySpace/rpcServiceInvoker/serviceName/ormTemplate） | setter 注入 + `init()`（L80-83）启动期 | 并发请求线程 | 同上 | 字段发布合格；**运行期回调跨线程问题见 C1-001** |
| S5c | singleton 实例字段（geo） | `Db2GeometryTypeHandler.java:23`（srid）、`OracleGeometryTypeHandler.java:27`（typeFactory，构造器赋值） | IoC 配置期 | 并发请求线程（handler 经 dialect 共享） | 配置期写、运行期只读 | 合格（非 final 但有效不可变；若改为运行期可变需加守卫） |
| S5d | singleton 实例字段（启动期 bean） | `DataBaseUpgradeInitializer.java:30-33`、`H2GisInitializer.java:27-30` | `@PostConstruct`/`init()` 启动期 | 无运行期读者 | 启动期单线程 | 合格 |
| S6 | per-call 可变对象（线程封闭） | `PdmModelParser.java:149-161`（11 个实例 map/字段，含 `dialect`，L156 非 final）；`OrmDbDiffer.java:57-61`（shardSelector 为 fluent 非 final）；`JdbcMetaDiscovery.java:50-63`（6 配置字段）；`DataBaseMeta.java:34-36`（tables TreeMap + ormModel） | 每次调用新建实例 | 同线程内 | 实例逃逸分析：全部调用点均 `new`（`PdmModelLoader.java:16`、`PdmModelHelper.java:22`、`CliGenOrmCommand.java:53`、`DataBaseUpgrader.java:85,127`、`ExportDbTool.java:219`、`ImportDbTool.java:160`、`OrmDbDiffer.java:63-69` 工厂），无跨线程共享 | 合格 + **脆弱性备注**：`PdmModelParser` 的线程安全性完全依赖「每次解析 new 实例」这一未成文的调用约定（类 javadoc 无线程约束声明）；未来若改为单例/缓存复用，`doParseResource` 对 `this.dialect` 的覆盖写（`PdmModelParser.java:166`）与 11 个 map 将跨解析互相污染（解析 A 的表进了解析 B 的模型）。建议在类 javadoc 显式声明 per-instance 单次使用契约 |

## 发现

### [C1-001] RpcEntityPersistDriver 在 RPC 传输线程上变更 ORM 会话/实体状态，偏离同族驱动的调用线程内联契约且无运行时守卫

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:261-286`（loadAsync）、`:368-411`（batchLoadAsync）；对照 `:58-63`、`:456-465`（bindEntity→internalAssemble）、`:548-578`（bindToManyRelation→orm_beginLoad/orm_internalAdd/orm_endLoad）
- **证据片段**:
  ```java
  // RpcEntityPersistDriver.java:270-285 —— thenAccept 回调直接变更 session/entity，
  // 该回调运行在完成 invokeAsync future 的线程（RPC 客户端响应线程）上
  return invokeRpc(operationName, data, selection).thenAccept(response -> {
      checkResponse(response);
      List<Map<String, Object>> list = (List<Map<String, Object>>) response.getData();
      Map<String, Object> result = findMatchedRow(entity, list);
      if (result != null) {
          bindEntity(entity, result, propIds, session);   // → session.internalAssemble(...)
          ...
      } else {
          session.markMissing(entity);
      }
  });
  ```
  ```java
  // 同族兄弟（同接口实现）均在调用线程内联执行同一体：
  // TdEntityPersistDriver.java:84-95 —— FutureHelper.futureCall 为同步内联（FutureHelper.java:122-129 直接 task.call()）
  // JdbcEntityPersistDriver.java:127,190 —— 同为 futureCall 内联
  return FutureHelper.futureCall(() -> {
      return jdbc().executeQuery(sql, ds -> { ... session.internalAssemble(entity, values, propIds); ... });
  });
  ```
- **严重程度**: P2（交错序列可具体化；破坏性后果依赖 nop-orm 会话内部实现，本单元内不可完全证实，按证据标准第 4 条标注「理论风险，未具体化（后果面）」降档 P2）
- **并发场景**: HTTP worker 线程（ORM 会话属主）与 RPC 客户端响应线程（数量与 `EntityPersisterImpl._batchLoad` 的 chunk 数同阶，`nop-orm/.../EntityPersisterImpl.java:209-214` 分 chunk 后经 `FutureHelper.bothSuccess` 组合，各 chunk 回调可在不同响应线程并发执行）
- **触发时序**: T1：请求线程调用批量加载，待载实体数 > `nop.orm.default-entity-batch-load-size`（默认 1000，`OrmConfigs.java:32-34`），`_batchLoad` 拆为 chunk A、chunk B，`driver.batchLoadAsync` 各返回一个真实异步 future（本驱动不经 futureCall 内联）；T2：RPC 响应线程 R1 执行 chunk A 的 thenAccept，进入 `bindEntity` → `session.internalAssemble(entity_i,...)`（`OrmSessionImpl.java:368-390`，读 persister map、写 entity 内部值与状态）；同时 T2：响应线程 R2 执行 chunk B 的 thenAccept，对 `entity_j` 调用同一 session 的 `internalAssemble`/`markMissing` → R1、R2 未加任何同步地并发进入同一会话对象；当前不产生可见损坏仅因 (a) 两 chunk 实体集不相交、(b) 会话内共享触点（persister map 只读、metrics 假定原子）恰好安全——这三点均无契约或守卫保证。同线程内时序：会话属主在 future 完成前不得触碰会话这一前提，也无任何机制强制（同步路径靠 syncGet 阻塞凑巧成立，异步路径完全依赖上游组合纪律）。
- **现状**: 三个 `IEntityPersistDriver` 实现（Jdbc/Td/Rpc）中，唯有 Rpc 驱动把会话/实体变更交给外部线程执行；接口 javadoc（`nop-orm/.../IEntityPersistDriver.java:17-20`）无线程契约声明，本驱动亦无属主线程守卫或 fail-fast（F10 并发守卫缺失形态）。
- **风险**: 一旦 (a) 实体跨 chunk 重复（上游分片语义变化）、(b) RPC 客户端引入重试/超时双完成、或 (c) 上游改为真正异步消费 future，即从「凑巧安全」变为实体状态/会话簿记数据竞争；且因无守卫，违规形态发生时无任何可观测失败点。
- **建议**: 优先对齐同族模式——回调仅 `complete` 结果，实体装配移回调用线程（或用 `FutureHelper.futureCall` 包装同步执行体）；若保留跨线程回调，则 (1) 在 `IEntityPersistDriver` javadoc 写明「internalAssemble/markMissing 允许在 driver 线程调用」的平台契约，(2) 在 `bindEntity`/`bindToManyRelation` 入口加会话属主校验或改用平台设施（`io.nop.commons.concurrent` 的序列化执行器）把回调归队到属主线程。
- **失效面枚举**（F3 同族清扫）：本文件 9 个回调注册点逐一核对——`loadAsync` L270（跨线程，本条主对象）、`batchLoadAsync` L378（跨线程，本条主对象）、`findFirst` L632、`findList` L705、`findAll` L751、`count` L811、`exists` L839、`delete` L867、`update` L896（后 7 处回调同样在 RPC 线程执行 `checkResponse`/`OrmAssembly.assemble`→`internalAssemble`，但调用方随即 `FutureHelper.syncGet` 阻塞等待，属主线程被泊停，交错被时序排除，现状安全——依赖的是上游 syncGet 这一未声明前提）；同族兄弟实现：`TdEntityPersistDriver.loadAsync` L84 / `batchLoadAsync` L178（futureCall 内联，安全）、`JdbcEntityPersistDriver.loadAsync` L127 / `batchLoadAsync` L190 / `batchExecuteAsync` L236（内联，安全）。全仓无其他 `IEntityPersistDriver` 实现类。
- **信心水平**: 很可能（跨线程形态与同族偏离为代码级确定；破坏性后果面未具体化）
- **误报排除**: 非「无状态单例被并发调用」（驱动持有可变会话引用且回调跨线程）；非「synchronized/CHM 原语使用」类误报——本条对象是复合契约（谁在哪个线程变更会话状态）与守卫缺失，且已按证据标准对后果面降档。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现

### [C1-002] MigrationEngine 三张裸 HashMap 注册表暴露运行期 public 注册 API 且无并发守卫（F10）：注册与迁移执行并发时为数据竞态，新注册项无 happens-before 可见性保证

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:68-70,109-121,280,299-306`；同族 `core/MigrationExecutor.java:28-36,76`、`executor/DbTypeFilterExecutor.java:37-59,100`
- **证据片段**:
  ```java
  // MigrationEngine.java:68-70 —— 裸 HashMap，无并发容器、无锁、无 volatile
  private final Map<String, IChangeExecutor> changeExecutors = new HashMap<>();
  private final Map<Class<?>, IPreconditionChecker> preconditionCheckers = new HashMap<>();
  private DbTypeFilterExecutor dbTypeFilterExecutor;
  // MigrationEngine.java:113-121 —— public 注册 API，行内注释明示"构造后注册"为预期用法
  public void registerExecutor(String changeType, IChangeExecutor executor) {
      changeExecutors.put(changeType, executor);
      this.executor.registerExecutor(changeType, executor);
      // dbTypeFilter dispatches nested changes through its own executor table,
      // so executors registered after construction must be visible there too
      if (dbTypeFilterExecutor != null) {
          dbTypeFilterExecutor.registerExecutor(changeType, executor);
      }
  }
  // MigrationEngine.java:299 —— migrate() 执行线程对同一 map 的裸读
  IChangeExecutor executor = changeExecutors.get(changeType);
  ```
- **严重程度**: P2（交错序列可具体化，但对手方是宿主动态注册行为——repo 内生产代码无并发调用方；注册表常驻运行期容器故不适用无条件豁免，按条件化降档记 P2）
- **并发场景**: 宿主应用线程 A（运行期调用 singleton bean `nopMigrationEngine` 的 `registerExecutor`/`registerPreconditionChecker`——`default.beans.xml:11` 将引擎注册为常驻单例，构造后注册是代码注释与测试共同确认的既定扩展点）与线程 B（迁移执行线程调用 `migrate()`/`rollback()`）
- **触发时序**: T1：线程 A 调 `registerExecutor("customX", e)` → `changeExecutors.put` 触发 resize/树化；同时 T2：线程 B 在 `executeChange` 执行 `changeExecutors.get("sql")` → 裸 HashMap 并发 put/get 为未定义行为：B 可读到 null（对内置已注册类型抛 `ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE`）、可读到撕裂的 bin，极端实现下 get 死循环；即使无 resize，put 与 get 之间也无 happens-before → B 可能持续读到陈旧 null（注册成功但对执行线程永不可见）。同型交错适用于 `preconditionCheckers.get`（`MigrationEngine.java:280`）与 `MigrationExecutor.executors`（字段更弱：非 final 且 `setExecutors` 运行期可整体替换引用，`MigrationExecutor.java:30-32`）
- **现状**: 注册 API 与执行路径之间无任何同步、守卫或线程契约声明（无 javadoc 写明"注册完成后方可调用 migrate/rollback"），顺序完全依赖宿主自觉（F10：无运行时 fail-fast 守卫，且此处连契约声明都缺失）
- **风险**: 动态注册场景下表现为偶发 unknown-change-type（fail-fast 可见但难排查）或自定义 executor 静默不生效（不可见）；HashMap 结构损坏则引擎整体不可用，且因引擎为常驻单例，损坏跨迁移批次持续存在
- **建议**: 三张注册表改用 `ConcurrentHashMap`（put/get 原子并建立发布屏障，读路径保持无锁）；或提供批量 `registerExecutors(Map)` 初始化接口 + javadoc 声明"启动期注册完成后方可 migrate/rollback"契约，并在 `migrate()` 入口以注册冻结标志 fail-fast；平台内可对照 `SysConverterRegistry`（nop-api-core，CHM + volatile 双 map）的既有模式
- **失效面枚举**（F3 同族清扫）: ① `MigrationEngine.changeExecutors`（`:68` 定义/`:114` 写/`:299` 读）——本条主对象；② `MigrationEngine.preconditionCheckers`（`:69`/`:110` 写/`:280` 读）——同型，须同修；③ `MigrationEngine.dbTypeFilterExecutor`（`:70` 非 final，构造期 `:94` 赋值，容器安全发布后只读）——合格，无需改；④ `MigrationExecutor.executors`（`:28` 非 final + `:30` setExecutors 可整体换引用 + `:76` 裸读）——同族最弱形态，与 ①② 同修；⑤ `DbTypeFilterExecutor.executors`（`:37` final HashMap + `:47` put + `:55` putAll + `:100` 裸读）——同型，同修；⑥ 引擎内部 `MigrationExecutor executor` 字段（`:67`/`:115`）只写不读——`executeChange` 的局部变量 `executor`（`:299`）遮蔽字段，注册进字段的自定义 executor 对执行无效（漂移缺陷已登记口径节集外观察，非并发问题）。全模块 grep 复核无其他可变注册表
- **信心水平**: 很可能（代码形态与同族清单为代码级确定；触发依赖宿主动态注册这一未实证行为）
- **误报排除**: 非"无状态单例被并发调用"（对象持有可变注册表且公开变更 API）；非"低频路径粗粒度同步的优雅性"类合理形态——此处是零同步且 API 语义鼓励构造后注册。降档口径已显式论证：引擎 singleton bean 常驻宿主运行期容器、注册表为宿主可达扩展点，不属"仅启动期单线程触达触发条件不存在"的完全豁免形态；但 repo 内无并发调用方实证，触发链依赖宿主行为，故按证据标准第 4 条记 P2 理论风险（时序已具体化，降档仅针对对手方存在性）
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同族：nop-persistence-rw-CL-415、nop-stream-orchestration-thin-C1-001）

### [C1-003] 迁移历史表全部守卫为非原子 check-then-act 且无跨进程互斥：多节点并发启动下 DDL 重复执行，FAILED 记录与实态背离形成永久重试失败循环

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:144-187`（已执行版本 TOCTOU）；`core/MigrationHistoryManager.java:72-86`（建表 TOCTOU）、`:88-139`（UPDATE-then-INSERT 竞态）、`:53`（仅装成功行）、`:228-237`（version 主键）
- **证据片段**:
  ```java
  // MigrationEngine.java:144,165,187 —— 快照读 → 判定 → 执行，三步之间无任何互斥
  Set<String> executedVersions = localHistoryManager.getExecutedVersions();  // 快照
  ...
  if (executedVersions.contains(version)) { ... continue; }                  // 判定
  executeAndRecord(migration, context, localHistoryManager, result);         // 执行+记账
  // MigrationHistoryManager.java:116-124 —— 两个并发写者都 UPDATE 0 行后都走 INSERT
  if (updated > 0) { return; }
  String sql = "INSERT INTO " + TABLE_NAME +
      " (version, description, type, checksum, installed_on, execution_time, success, installed_by)" +
      " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
  ```
- **严重程度**: P1（罕见但可具体化的竞态：明确触发链，后果为迁移永久卡死/启动失败）
- **并发场景**: 两个及以上 JVM 进程的启动迁移线程——K8s 滚动发布/HA 双节点同时启动、或副本重启风暴下对同一数据库并发执行 `engine.migrate()`，共享 `nop_db_migration_history` 表；模块内 `lock|Lock` grep 为 0，无文件锁、无 DB 咨询锁、无租约
- **触发时序**: T1：节点 A 读 executedVersions（V2 不在）→ 执行 V2 的 createTable DDL（自动提交）；T2：节点 B 在 A 的 recordMigration 提交前读 executedVersions（V2 仍不在）→ 也执行 V2 DDL → "table already exists" 异常 → B 记 FAILED 行。更坏分支：A、B 的 executeMigration 均成功 → 双方 recordMigration 的 UPDATE 均影响 0 行 → 双方 INSERT → 后写方撞 `version` 主键（`:229` PRIMARY KEY）→ 异常自 executeAndRecord 抛出；若先落库的是对方的行，本节点的记账 UPDATE 会把对方成功行覆盖为 success=false → 净效果：DDL 已生效但历史行 success=false → 下轮启动 executedVersions 只装成功行（`:53 WHERE success = ?`）→ V2 重执行 → CREATE TABLE 再失败 → 每次启动循环失败直至人工清表
- **现状**: 模块作者已考虑崩溃恢复（`recordMigration` 注释明确讨论"进程死亡"场景）但对并发写者零防护：判定-执行-记账三步全部基于无互斥的表快照；Flyway/Liquibase 以 DB 锁解决的同族问题在此无对应机制
- **风险**: 多副本部署下迁移要么 fail-fast 阻断启动，要么留下与真实 DDL 状态背离的 FAILED 脏行；脏行导致"每次启动必失败"的卡死循环，需人工介入；DDL 自动提交使失败点之前的变更无法收回（半个成功的横切面另登记 CL-003）
- **建议**: 迁移执行入口获取跨进程排他租约——`io.nop.commons.concurrent.lock.LocalFileLock` 仅单机有效，DB 场景应采用数据库层互斥（`SELECT ... FOR UPDATE` 锁 history 表锚行 / PostgreSQL `pg_advisory_lock` / MySQL `GET_LOCK`）包住整个 migrate 循环；最小修为：recordMigration 的 INSERT 捕获主键冲突后回读重试（升级为冲突感知 upsert），并在 executedVersions 判定通过后、DDL 执行前对 version 行做条件 INSERT 占位（把判定与占位并为一步）
- **失效面枚举**（F3 同族清扫）: ① `ensureHistoryTableExists`（`MigrationHistoryManager.java:72-86`，tableExists→CREATE TABLE，双节点同建表一方失败）——同族，`CREATE TABLE` 加 `IF NOT EXISTS` 或纳入锁范围；② `migrate` 的 contains→executeAndRecord（`MigrationEngine.java:165/187`）——本条主对象；③ `recordMigration` UPDATE-then-INSERT（`MigrationHistoryManager.java:88-139`）——本条主对象；④ `getMigrationByVersion`+checksum 比对（`MigrationEngine.java:166-183`）——读到的 recorded 行可能正被另一节点覆写，checksum 判定基于中间态（同族，锁内自然闭合）；⑤ `rollback` 的 removeMigrationRecord（`MigrationEngine.java:376`）——与并发 recordMigration 竞态（回滚删行 vs 另一节点 INSERT 同 version，同族）；⑥ `tableExists` 连接级探测（`MigrationHistoryManager.java:197-219`）——只读，无独立问题
- **信心水平**: 很可能（交错序列与后果链代码级完整；实际触发取决于部署形态）
- **误报排除**: 非"低频管理路径粗粒度同步属合理"的校准形态——这里是零互斥而非粗粒度；不适用降档口径：降档前提"仅启动期单线程触达"在多进程并发启动这一部署常态下不成立，且模块无任何单节点约束的声明或守卫（docs-for-ai `model-first-development.md:327` 仅声明该模块负责版本化迁移，无并发契约）；也非已声明边界——无 owner 文档声明"必须单节点运行迁移，守卫由部署方负责"
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同族对照 WI 1.5 C7-001 / WI 1.6 C3-003 fencing·租约族，不同对象不并案）

### [C1-004] RunningChainJob.thread 晚于 thread.start() 发布且停止协议无终止确认：join() 存在读 null 空转竞态，ABORT 可在链作业仍在写目标表时继续推进

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:174-188`（startChain 发布时序：`:184` start、`:185` 赋值）、`:235-243`（stop/join）、`:201-206`（stopAll 无等待）
- **证据片段**:
  ```java
  // IncrementalJobManager.java:176-186 —— 先 start 后发布句柄字段
  Thread thread = new Thread(() -> {
      try { env.execute(jobName); }
      catch (Throwable e) { running.failure.set(e); }
  }, jobName);
  thread.setDaemon(true);
  thread.start();
  running.thread = thread;          // :185 晚于 start 的普通字段写
  runningJobs.put(jobName, running);
  // :239-243 —— join 对 null 静默跳过
  public void join(long millis) throws InterruptedException {
      Thread t = thread;
      if (t != null) t.join(millis);
  }
  ```
- **严重程度**: P1（取消路径上可具体化的竞态 + 停止协议语义缺口）
- **并发场景**: startChain 的编排 API 调用线程（ops 宿主线程）vs `runIncremental` 返回句柄的消费线程（宿主 ABORT/关停逻辑、编排器 stopAll 同线程后续动作）；链作业 daemon 线程本身（env.execute 消费 CDC 并经 jdbc-2pc sink 写目标分片表）
- **触发时序**: T1：startChain 执行到 `:184 thread.start()`（链线程已可运行，env.execute 开始消费源库事件并向目标表 upsert），尚未执行 `:185 running.thread = thread`；T2：编排器 abort() → stopAll() → job.stop()（source.cancel() 仅置取消标志后返回）→ 宿主对返回句柄调 join()：`t == null` → 立即返回 → 调用方认定"链已停止"；交叠后果：编排器随后 `:307-308` 重开写门（abort 语义"开门即回滚"），而链线程仍在消费/写入目标表——增量写入越过 ABORT 点继续落库，与状态行 ABORT（"旧配置未动无损"）背离
- **现状**: `thread` 字段虽声明 volatile（`:214`），但赋值发生在 `thread.start()` 之后且与 join 读侧无任何先后约束——start 前 join 到的永远是 null；同时 stop()/stopAll() 均 join 不等待、不校验 run 循环实际退出（cancel 是异步请求，不是终止确认），"cancel source → run 循环退出"（类 javadoc `:41-42`）的时序契约无实现承载
- **风险**: ABORT/阶段推进在增量链未终止时即开门或落终态，目标表出现管道残留写入；对正在切换的搬迁作业，这些"幽灵写入"绕过全部阶段门禁，VERIFY 结论与 SWITCH 后的读路由都可能漏掉/错算这批行
- **建议**: (1) `running.thread = thread` 移到 `thread.start()` 之前（final 化 RunningChainJob 内 thread 或经构造器传入后单次 volatile 写，消除发布窗口）；(2) stopAll 收敛为"cancel → join(超时) → 校验 !isAlive → 未死则登记 failure"，复用平台 `io.nop.commons.concurrent.thread.ThreadHelper`/`NamedThreadFactory` 管理线程生命周期，替代裸 `new Thread`
- **失效面枚举**（F3 同族清扫）: ① `startChain:184-185` 的发布时序——本条主对象；② `join():239-243` 的 null 短路——同族，随 ① 修复闭合；③ `stopAll():201-206` 无等待清理——同族（无终止确认面），独立登记 CL-009；④ `RunningChainJob.failure:215` 只写不读——同族（失败不可见面），独立登记 CL-006；⑤ 模块内其余跨线程发布点：startChain 内 sink/图对象经 `Thread.start()` 的 happens-before 边安全发布（`:171-184`），合格；`OrmMigrationJobStatusBizModel.orchestrator:23` volatile 合格——无其他同族兄弟
- **信心水平**: 确定（代码级交错完整；join 的实际触达依赖宿主对返回句柄的使用，但 stopAll 无等待是本模块内确定事实）
- **误报排除**: 非"volatile/ConcurrentHashMap 原语使用"校准形态——这里是复合操作时序（start 后发布、join 读 null）；非低频管理路径粗粒度同步——这是取消路径协议缺口而非优雅性问题；RunningChainJob 是 runIncremental 的公开返回值，"宿主 stop-then-join"是该句柄的当然用法而非假想调用方
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现

### [C1-005] runIncremental 阶段转移 check-then-act 非原子且 runningJobs.put 静默覆盖：并发双启产生孤儿增量链并踩踏共享 offset registry

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:185-204`（`:186` requireState 读 → `:191` checkTransition → `:196-197` setPhase+update → `:200` startFallbackChains）；`IncrementalJobManager.java:186`（put 覆盖）、`:121-141`（startFallbackChains 循环）、`:160-166`（initializeState 清 registry）；`nop-message-debezium/.../NopStreamOffsetBackingStore.java:59-60,101-103,189-205`（共享 REGISTRY + REPLACE 语义）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:186-201 —— 判定与执行隔着两次 DB 往返，无互斥无 CAS
  MigrationJobState state = requireState(jobId);                    // :186 快照读
  ...
  state.getPhase().checkTransition(jobId, MigrationPhase.INCREMENTAL); // :191 判定
  state.setPhase(MigrationPhase.INCREMENTAL);
  store().update(state);                                            // :197 落库
  ...
  List<...> jobs = manager.startFallbackChains(jobId, config, ...); // :200 执行
  // IncrementalJobManager.java:186 —— 同名 jobName 静默覆盖旧句柄
  runningJobs.put(jobName, running);
  ```
- **严重程度**: P1（罕见但可具体化的竞态；后果为不可停止的重复数据管道）
- **并发场景**: 两个编排 API 调用线程（同 JVM 双 ops 请求，或双节点对同一共享状态行 `nop_orm_migration_job` 各自 resume/runIncremental）；链作业线程随后并发写目标表与共享 offset registry
- **触发时序**: T1：线程 A runIncremental 读 phase=FULL（`:186`），通过 checkTransition；T2：线程 B（第二次编排调用或另一节点 resume 后 runIncremental）在 A 的 `:197` update 提交前同样读到 phase=FULL → 也通过；交叠后果：A、B 都置 INCREMENTAL 落库（后写覆盖前写，无版本谓词）并各自 startFallbackChains——同 jobId 派生同名 jobName（`IncrementalJobManager.java:174`），B 的 `runningJobs.put` 覆盖 A 的句柄 → A 整组链线程脱离注册表，stopAll 永远停不到；且 B 的 startChain → `source.initializeState(null)`（`:161`）按首跑路径 `clearConnector` + 种子化 P0（`NopStreamOffsetBackingStore.java:101-103,189-205` REPLACE），把 A 组**运行中** connector 的共享 offset map 清空重置——两组引擎以同名 connector 并发消费同一源库、双写同一目标表，A 组下一 checkpoint 快照到的是被踩踏后的 P0 种子
- **现状**: 阶段判定（DB 快照 checkTransition）、落库（全列 UPDATE）、执行（启动链）三步间无任何原子性保障：行级无 `WHERE phase=<expected>` 条件更新、无版本列、编排器无进程内 mutex、跨节点无租约/咨询锁（模块内 `lock|Lock` grep 为 0）；`runningJobs` 是 ConcurrentHashMap 但 put 语义是"最后写者赢"，双启检测缺失
- **风险**: 孤儿链在 ABORT/SWITCH 后继续消费源库增量并写目标分片表（不可停止）；共享 offset map 被重置导致从 P0 重复重放（epoch ledger 只能去重部分重复提交，双引擎各自 epoch 序列无法互认）；状态行表现为正常 INCREMENTAL，运维无感知
- **建议**: (1) 状态行转移改条件更新：`UPDATE ... SET phase=:to WHERE job_id=:id AND phase=:from`，update 返回 0 行即门禁失败（把 checkTransition 与落库并为一步原子 CAS）；(2) 编排 API 入口加进程内互斥（`synchronized` 编排器级锁或平台 `IResourceLockManager`）+ 跨节点 DB 咨询锁（同 C1-003 建议）；(3) `startChain` 前对 `runningJobs.putIfAbsent(jobName, ...)` 非空即 fail-fast（ERR_MIG_CONFIG_INVALID 系），禁止静默覆盖
- **失效面枚举**（F3 同族清扫）: 六个阶段推进 API 的 check-then-act 全清单——① `runIncremental:186-200` 本条主对象（执行面含长驻线程启动 + registry 踩踏，最重）；② `confirmSwitch:258-272` 同族（双 confirm 双执行 flipShardConfig+rebuildRouting，flip 对 AppConfig 幂等、selector 重建幂等，但与 abort 交错见 C1-006）；③ `runVerify:211-229` 同族（双 verify 幂等执行但状态行互踩，归 C1-006）；④ `runFull:176-179` 同族（FULL 可重入由 keyed upsert 幂等兜底，`executeFull:341-346` 重复置 FULL 无害，风险低）；⑤ `completeSwitch:280-287` 同族（双 DONE 幂等，风险低）；⑥ `abort:297-311` 同族（isTerminal 快检 + checkTransition 后 setPhase，双 abort 幂等，但与 confirmSwitch/completeSwitch 交错即丢更新，归 C1-006）；⑦ `runningJobs.put:186` 静默覆盖——本条主对象伴随面
- **信心水平**: 确定（交错序列与 registry 踩踏链代码级完整；触发依赖编排 API 被并发调用或双节点共享状态行，模块对此零防护）
- **误报排除**: 非平台设施重审——竞态载体（状态行 UPDATE 语义、runningJobs、编排器入口）全部在本模块；非"有界声明存在"——无任何单调用契约声明；不适用启动期降档（运行期多线程流水线，批次口径已裁定）
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（关联 WI 1.3 C3-008 同一 offset 存储设施；offset 踩踏子断言经复核修正——A 引擎 store 持旧 map 直接引用不被清空，主链成立）

### [C1-006] 状态行 store 全列覆盖式 UPDATE 无版本/相位谓词：并发编排 API 丢更新，ABORT 与 confirmSwitch 交错可留下"配置已翻转 + 写门已开 + 账面 ABORT"的背离终态

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/MigrationJobStateStore.java:100-118`；调用面 `OrmMigrationOrchestrator.java:197,229,238,247,268,287,311,335,346,421,425`（11 处）
- **证据片段**:
  ```java
  // MigrationJobStateStore.java:103-115 —— 以调用线程的内存快照整行覆盖，仅 WHERE job_id
  long updated = jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
          .name("orm-migration-state-update")
          .sql("UPDATE " + TABLE_NAME + " SET phase=").param(state.getPhase().name())
          .sql(",scenario=").param(state.getScenario())
          ...
          .sql(",abort_reason=").param(state.getAbortReason())
          ...
          .sql(" WHERE job_id=").param(state.getJobId())   // 无 AND phase=:expected、无 version 列
          .end());
  ```
- **严重程度**: P1（可具体化竞态，后果为状态机语义违约 + 搬迁安全承诺失效）
- **并发场景**: 同一 jobId 的两个编排 API 调用线程（同 JVM 双 ops 请求 / 双节点共享状态行）；`abort()`（问题处置）与 `confirmSwitch()`（切换推进）由不同运维动作并发触达是现实场景（一个自动巡检 abort + 一个人工 confirm）
- **触发时序**: T1：线程 A abort()：`requireState` 读得 phase=VERIFY、非终态（`:297-298`），checkTransition(VERIFY→ABORT) 通过（`:300`）；T2：线程 B confirmSwitch() 读得 verifyResult=passed（verify 已通过，`:259`）、写门 closed（`:263`）、checkTransition(VERIFY→SWITCH) 通过（`:266`）→ `:267-268` 落 SWITCH → `:271-272` flipShardConfig（`AppConfig.updateConfigValue` 翻转 `nop.orm.shard`）+ rebuildRouting（热替换 shardSelector）；交叠后果：A 的 abort 在 B 翻转后继续执行——`:307-308` 判写门 closed 即重开（写门是 JVM 全局，不区分谁关的）、`:309-311` 以自己的 VERIFY 快照整行覆盖：phase=ABORT、abortReason=reason 写入；净终态 = JVM 配置已是新分片策略 + 写门已开（业务写恢复且被新路由接管）+ 账面 ABORT（语义承诺"旧配置未动，开门即回滚无损"）——单向不可回滚点被无声越过；反向交错（A 的 update 后 B 的 `rebuildRouting:425` update 落后）则 abortReason/ABORT 被 B 的 SWITCH 快照静默抹掉
- **现状**: 状态行是编排器唯一恢复面（设计 §4.1"绝不隐式回退阶段"），但其更新原语是"调用方快照整行覆盖 + 仅主键谓词"，天然丢更新；阶段转移的原子性完全寄希望于"编排 API 不会被并发调用"这一未声明、未守卫的隐含契约（F10：无运行时 fail-fast）
- **风险**: ABORT 语义失效（配置已翻转却按未翻转回滚），VERIFY→SWITCH→DONE 账面与三处真实状态（JVM 分片配置、shardSelector、写门）背离；后续 resume 按 ABORT 行为会把"已完成切换"的作业再走一遍恢复逻辑；双节点场景每节点写门/配置各自为政，账面只剩最后落库者的视图
- **建议**: (1) 状态行加 `version` 列或以 `phase` 作乐观锁谓词（`UPDATE ... WHERE job_id=:id AND phase=:from AND update_time=:readAt`），update 影响 0 行即抛 `ERR_MIG_PHASE_TRANSITION_INVALID`（复用现有错误码语义）；(2) 编排 API 全入口收敛到单 mutex（同 C1-005 建议 2）；(3) abort 的"重开写门"动作延后到状态行 ABORT 落库成功之后（当前 `:307-308` 先开门 `:309-311` 后落库，中间崩溃即出现"门开而账面未 ABORT"窗口）
- **失效面枚举**（F3 同族清扫）: `store().update` 全部 11 个调用点逐一——① `:197`（runIncremental，见 C1-005）；② `:229`（runVerify 置 VERIFY）；③ `:238`（runVerify 失败置 verifyResult=failed）；④ `:247`（runVerify 置 passed/failed）——②③④ 同族：与并发 abort/confirm 交错互踩，同根因；⑤ `:268`（confirmSwitch 置 SWITCH）；⑥ `:287`（completeSwitch 置 DONE，与 abort 交错同 ⑤ 型）；⑦ `:311`（abort 置 ABORT，本条主对象）；⑧ `:335`（executePrepare 回填）、⑨ `:346`（executeFull 置 FULL）——同族（与 abort 交错会抹掉 abortReason，风险同型）；⑩ `:421`/`:425`（rebuildRouting 落 routeRebuild）——同族；`insert:144` 唯一调用点主键冲突即异常，无同族问题；`load/loadAll` 只读
- **信心水平**: 确定（交错序列代码级完整；触发依赖编排 API 并发触达，模块零防护）
- **误报排除**: 非平台设施重审（JdbcTemplate 使用方式问题，非其实现缺陷）；非低频路径合理粗锁——这里是零原子性而非粗粒度；非已声明边界——无 owner 文档声明"编排 API 必须串行调用"；与 C1-003（nop-db-migration 历史表 TOCTOU）同族但对象不同（该条是多进程启动迁移历史表，本条是运行期编排状态行），不构成重复登记
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同族对照 WI 1.5 C7-001 丢失更新族，不同对象不并案）

### [C1-007] rebuildRouting 热替换非 volatile 的 SessionFactoryImpl.shardSelector：请求线程可无限期读到旧路由，开门后业务写落入旧分片表；新 selector 亦非安全发布

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:397-426`（`:405/:409` setShardSelector；`:398-400` 注释宣称"替换后新查询立即生效"）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:70,194-200`（普通字段读写）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:397-400,405-409 —— 编排线程热替换
  // 单 JVM 形态：以新配置 init 新 ConfigShardSelector 并热替换 session factory 的 selector
  // （getShardSelector 每查询即时读取，替换后新查询立即生效）。...
  ConfigShardSelector selector = new ConfigShardSelector();
  selector.init();
  ((SessionFactoryImpl) sessionFactory).setShardSelector(selector);   // 无同步无 volatile
  // SessionFactoryImpl.java:70,194-196 —— 请求线程每查询普通读
  private IShardSelector shardSelector = EmptyShardSelector.INSTANCE;
  public IShardSelector getShardSelector() { return shardSelector; }
  ```
- **严重程度**: P1（可具体化竞态，后果为切换后静默写错表 = 主路径数据丢失面）
- **并发场景**: 编排线程（confirmSwitch → rebuildRouting → completeSwitch）vs 全部业务请求线程（HTTP worker 池，每条 ORM 查询/写经 `getShardSelector()` 取路由）——替换发生在写门冻结期，但**读取方在门重开后的任意时刻**仍可能读到旧值
- **触发时序**: T1：编排线程执行 `:409 setShardSelector(新selector)` → `:285` completeSwitch 重开写门；T2：某请求线程（池化、此前执行过查询、与编排线程之间无任何同步动作建立 happens-before）此刻处理 save → flush 路由判定调 `getShardSelector()`（`SessionFactoryImpl.java:194-196` 普通读）→ JMM 下允许读到旧 selector → 该行 UPSERT 落入**旧物理分片表**；交叠后果：新路由读不到该行（row 只在旧表），count/checksum 均已过闸，静默数据分叉；另 `ConfigShardSelector` 内部结构非 final、构造后经普通字段发布——竞态读者还可能看到策略表未完全构造的实例
- **现状**: 代码注释（`:398-400`）明文断言"替换后新查询立即生效"，该断言在 JMM 下不成立：字段非 volatile、读写无锁，跨线程可见性无保证；`final` freeze 不适用于此（字段与 selector 内部状态均非 final）
- **风险**: SWITCH 开门（不可回滚点）后，部分请求线程的写持续路由旧表直至其偶然发生同步动作（可能数分钟到数小时）；这是设计明令禁止开门的"半重建路由"状态（`:418-419` 注释），只是以可见性缺陷而非构造缺陷的形式出现；修复窗口依赖人工发现读写不一致
- **建议**: (1) `SessionFactoryImpl.shardSelector` 字段加 `volatile`（写少读多的热路径，开销可忽略；属 nop-orm 一行改动，Protected Area 口径下连同回归测试走 plan-first）；(2) `ConfigShardSelector` 发布前完成全部初始化并以 final 字段/不可变结构承载策略表（安全发布）；(3) rebuildRouting 完成后做一次替换可见性确认（读回比对实例同一性），失败则保持写门冻结
- **失效面枚举**（F3 同族清扫）: 本模块运行期热替换共享路由/配置的面共 2 个——① `setShardSelector`（本条主对象，仅 rebuildRouting 一处运行期调用；IoC 启动装配期的 setter 调用为单线程，合格）；② `flipShardConfig:388` 的 `AppConfig.updateConfigValue`（经 `DefaultConfigProvider.java:51-52` volatile + ConcurrentHashMap 发布，合格，已排除）；`SessionFactoryImpl` 其余可变字段（queryExecutor/globalCache/queryPlanCache 等）本模块未在运行期触碰——无其他同族兄弟
- **信心水平**: 很可能（字段非 volatile + 无锁读写是确定事实；"读到旧值"在特定硬件/时序下必然可复现，常规环境表现为偶发）
- **误报排除**: 非"平台设施自身并发实现"——缺陷锚点虽含 SessionFactoryImpl 字段声明，触发动作完全在本模块 rebuildRouting；非误报校准中"无状态单例并发调用"——该字段恰是可变共享路由；也不因写门冻结期而失效——读侧陈旧性恰在开门后才转化为数据错误
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（平台侧同案：nop-core-framework-C1-007 配置发布协议裸写裸读——复核实证写门 provider 原地裸换、HB 兜底不存在；关联 nop-persistence-rw-C1-003 非 volatile 热替换族）

### [C1-008] 编排器五个惰性组件 plain 字段 check-then-act 无守卫（F10）：并发首调重复实例化，其中 incrementalManager 有状态（链注册表），重复实例分裂 ABORT 停止面

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:120-124`（store()）、`:432-436`（mapping()）、`:438-442`（fullJob()）、`:444-454`（incrementalManager()）、`:456-460`（verifier()）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:444-454 —— 判-建-赋三步无同步无 volatile
  public IncrementalJobManager incrementalManager() {
      if (incrementalManager == null) {                 // check
          IJdbcTemplate sinkTemplate = targetJdbcTemplate;
          incrementalManager = new IncrementalJobManager(mapping(), target -> {  // act
              ...
          }, checkpointIntervalMs);
      }
      return incrementalManager;
  }
  ```
- **严重程度**: P2（可具体化但触发面窄：需两线程同时首调同一访问器；后果经由 C1-005/009 放大）
- **并发场景**: 编排 API 调用线程之间（如 runIncremental 与 abort 首次触达 `incrementalManager()`）；biz 查询线程（`OrmMigrationJobStatusBizModel.getJob` → `getState` → `store()`）与编排线程并发首调 `store()`
- **触发时序**: T1：线程 A 进 `incrementalManager()` 判空通过、正在构造（构造内含 `new ShardChangeEventRouter(mapping())` 等）；T2：线程 B 同一访问器判空仍为 null（普通字段读，无 HB）→ 也构造；交叠后果：两个 IncrementalJobManager 实例并存——A 经其启动的链登记在 A 实例的 runningJobs，B 启动的登记在 B 实例；orchestrator 字段最终持有其一，另一实例的链作业组在 ABORT/stopAll 时不可达（与 C1-009 的 stale-null 读同一后果面）；`store()` 双实例则各自可能执行 `initializeTable`（CREATE TABLE IF NOT EXISTS 幂等，危害轻）
- **现状**: 五个惰性访问器全部是教科书式 check-then-act（对照 F10 守卫缺失清单），字段非 final 非 volatile 无锁；`mapping()/fullJob()/verifier()` 产物为有效不可变/无状态对象，重复实例化仅浪费；唯独 `incrementalManager()` 产物携带 `runningJobs` 注册表，重复实例有语义后果
- **风险**: 与 C1-005 复合时，ABORT 的 stopAll 停不掉一整组增量链；单独触发时主要后果是重复建表 DDL 与对象浪费（低危）；同时该模式使"字段直读"（C1-009）成为必然诱惑——字段语义（"惰性单例"）无同步机制承载
- **建议**: 五个访问器统一收敛为构造期装配（host 装配注入，如 `setStateStore` 已有先例 `:90-92`），或最小改：字段加 volatile + synchronized 访问器（低频路径粗粒度同步符合误报校准的"合理"形态）；`incrementalManager()` 必须守卫（有状态）
- **失效面枚举**（F3 同族清扫）: ① `store():120-124`——同族，双实例幂等 DDL 兜底，低危；② `mapping():432-436`——同族，产物不可变，双实例无害（引用不同但语义等价）；③ `fullJob():438-442`——同族，产物无状态（每 run 新建 consumer，`FullCopyBatchJob.java:73-96`），双实例无害；④ `incrementalManager():444-454`——本条主对象（有状态）；⑤ `verifier():456-460`——同族，无状态，无害；对照合格形态：`MigrationJobStateStore.ensureTable:72-79` 的 volatile + synchronized DCL——同模块内已有正确范式未复用
- **信心水平**: 确定（模式事实确定；触发依赖并发首调）
- **误报排除**: 非"低频管理路径粗粒度 synchronized 合理"校准的反面——这里恰恰没有任何同步；非不可变对象误报——主对象 incrementalManager 携带可变注册表；不适用启动期降档——访问器在运行期编排路径上被触发
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同族：nop-kernel-concurrency-CL-035 惰性双检缺 volatile，watch）

### [C1-009] abort() 直读非 volatile 的 incrementalManager 字段绕过惰性访问器：跨线程 stale-null 读导致 ABORT 跳过 stopAll 且照常重开写门

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:301-305`（`:302` 直读字段）、`:306-311`（重开门 + 落 ABORT）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:301-311 —— 绕过 incrementalManager() 的普通字段读
  if (config.isIncrementalEnabled()) {
      IncrementalJobManager manager = this.incrementalManager;   // :302 非 volatile 直读
      if (manager != null)
          manager.stopAll();
  }
  // 写门若已关闭则恢复（ABORT 路径只发生在 SWITCH 翻转之前——配置未动，开门即回滚）
  if (OrmMigrationWriteGate.isClosed())
      OrmMigrationWriteGate.setClosed(false);                    // :307-308
  state.setAbortReason(reason);
  state.setPhase(MigrationPhase.ABORT);
  store().update(state);                                         // :309-311 开门在前落库在后
  ```
- **严重程度**: P1（可具体化竞态；ABORT 的核心安全承诺——停链、保旧配置——双双失效）
- **并发场景**: runIncremental 所在线程 A（创建 manager 并启动链）与 abort 所在线程 B（另一 ops 请求/巡检自动化）；二者对 orchestrator 单例字段无任何同步边
- **触发时序**: T1：线程 A runIncremental → `incrementalManager()`（`:199`）构造 manager 并启动链（manager 字段已被 A 写入）；T2：线程 B abort() 在 `:302` 直读字段——普通读与 A 的普通写之间无 happens-before，JMM 允许 B 读到 **null**（或旧值）→ 跳过 stopAll → `:307-308` 写门判 closed 即重开 → `:309-311` 落 ABORT；交叠后果：增量链组仍在消费源库、写目标表，而写门已开、账面已 ABORT——ABORT 的"停掉增量 + 回到旧策略无损"双承诺同时失效，链写入成为越过 ABORT 点的幽灵增量
- **现状**: 全类唯一一处绕过惰性访问器的字段直读（其余调用点均走 `incrementalManager()` 等访问器）；`abort` 的作者显然知道字段可能为 null（null 检查存在）但把它当作"未启动增量"处理，而实际语义是"我可能看不见已启动的增量"——把可见性缺陷误编码为业务分支
- **风险**: 与 C1-004（停止协议无终止确认）复合：即便 stopAll 被正确调用，cancel 也是异步请求；本条使 ABORT 在 manager 可见性层面就失去停止机会；幽灵增量落库后，后续按 ABORT 语义重跑 resume/start 将面对"已按新路由写过数据"的库面
- **建议**: `:302` 改走 `incrementalManager()` 访问器（惰性创建一个无链 manager，stopAll 幂等无害）；配合 C1-008 的 volatile/同步改造后此问题自然闭合；开门动作（`:307-308`）延后到 stopAll 完成确认 + ABORT 落库之后（顺序与 C1-006 建议 3 一致）
- **失效面枚举**（F3 同族清扫）: "绕过惰性访问器直读字段"全类 grep——① `abort:302` 读 `this.incrementalManager`——本条主对象，全模块唯一一处；其余 `runIncremental:199`（走访问器）、`store()/mapping()/fullJob()/verifier()` 调用点（全部走访问器）均合格；`OrmMigrationJobStatusBizModel:34` 的 `this.orchestrator` 直读为 volatile 字段，合格——无其他同族兄弟
- **信心水平**: 很可能（可见性失败是 JMM 允许且真实可发生；单 ops 串行调用场景下不触发）
- **误报排除**: 非"private 可变字段未被注入"类形态误报（共享前缀并发语境第 1 条）——该字段有真实跨线程写者；非平台设施重审；不因"低频管理操作"豁免——ABORT 正是问题处置主路径，其可靠性不应依赖调用时序运气
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现

### [C1-010] 写门/门禁检查与阶段动作之间无保持机制：isClosed 检查点是瞬时读，冻结承诺贯穿整个 VERIFY/SWITCH 生命周期却无持有者

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:214-218`（runVerify 门禁 1）、`:263-265`（confirmSwitch 门禁）、`:279-290`（completeSwitch 无条件开门）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmMigrationWriteGate.java:41-65`（全局静态配置键读写，无租约/持有者概念）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:214-226 —— 检查一次，此后校验执行数分钟，无再确认
  if (!OrmMigrationWriteGate.isClosed()) {          // :215 瞬时检查
      throw gateFailed(jobId, state.getPhase(), "write-gate must be closed before VERIFY" ...);
  }
  ...
  if (!probe.awaitLagZero(connectorName, config.getQuiesceSeconds())) { ... }  // :223 静默窗
  // :285 —— completeSwitch 开门不复查门处于 closed 期间发生的任何翻转
  OrmMigrationWriteGate.setClosed(false);
  ```
- **严重程度**: P2（需外部动作在窗口内翻转门才触发；检查-执行窗口客观存在且无防护）
- **并发场景**: 编排线程（runVerify 校验执行期、confirmSwitch→completeSwitch 之间）vs 任何能写运行时配置的宿主代码/运维通道（`nop.orm.migration.write-gate` 是普通平面配置键，`setClosed` 是 public static，无权限面、无持有者、无租约）
- **触发时序**: T1：编排线程 `:215` 检查 isClosed()==true 通过，进入 lag 静默观测 + 三级校验（分钟级窗口）；T2：窗口内另一运维通道/宿主代码执行 `setClosed(false)`（或改配置键）——无任何机制阻止或告警；交叠后果：源端写恢复，VERIFY 在漂移数据上进行，count/checksum 结论对已过时快照成立，"passed" 落账；completeSwitch `:285` 开门时也不复查门的状态历史——冻结承诺被无声破坏
- **现状**: 门设计为"单一配置点，原子生效"（翻转可见性合格，见批次口径误报排除 a），但原子翻转 ≠ 原子保持：runVerify/confirmSwitch 把"检查时刻为 closed"当作"未来 N 分钟仍为 closed"使用；这是典型 check-then-act 跨长窗口形态，模块对窗口内的翻转对手方零感知
- **风险**: 校验结论失真（passed 掩盖窗口期新增写入的失配）；confirmSwitch/completeSwitch 在门已被他人打开时照常推进；多操作者（人工 + 自动化）协同场景下无仲裁
- **建议**: 门语义升级为持有式：编排器进入 VERIFY/SWITCH 冻结期时记录门 token（配置值 + 读取时间戳），阶段动作（校验完成、confirm、complete）前复核 token 未变（值比对即可，配置键本身可作版本）；或最低成本——lag 静默窗后、校验结束前各再查一次 isClosed 并落状态行留痕，失配即 gateFailed
- **失效面枚举**（F3 同族清扫）: `isClosed()` 读点全清单——① `runVerify:215`（本条主对象，窗口最长）；② `confirmSwitch:263`（本条伴随，窗口 = flip+rebuild 时长）；③ `abort:307`（读后写，窗口极短，仅与并发 completeSwitch 交错有意义，归 C1-006）；`setClosed` 写点全清单——`completeSwitch:285`、`abort:308`（编排器自身 2 处）+ 外部任意调用方（无守卫即本条主题）；读侧消费面 `OrmSessionImpl` flush 族在平台侧（不重审）
- **信心水平**: 确定（窗口与无保持机制是代码级事实；触发依赖窗口内外部翻转动作）
- **误报排除**: 非门翻转可见性问题（已核 DefaultConfigProvider volatile，见口径误报排除 a）；非"已声明边界"——设计要求"写停止关必须已关闭"是硬门禁（`:214-215` 注释引 M3），但对"保持 closed"无任何机制或文档化部署约束；不是要求加分布式锁的过度设计——单 JVM 内值比对即可闭合
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（勘误：本条误报排除 a 的「门翻转可见性合格」经复核证伪——运行期读取走 usedRefs 常驻 DefaultConfigReference 的普通字段 provider，原地裸换无 volatile 边，该面归平台同案 nop-core-framework-C1-007；本条「无保持机制」判定不变）

### [C1-011] LettucePubSubService 订阅生命周期复合操作与 subscribe 监视器不对称：cancel/suspend/resume 无同步地多步变更共享 entry/注册表/Redis 通道，交错可致新订阅静默失效或已取消订阅复活双投递

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:72-94`（subscribe，方法级 synchronized）、`:190-200`（cancel，无同步）、`:213-218`（suspend）、`:221-226`（resume）、`:127-139`（SubscriptionEntry.addListener/removeListener）、`:58`（subscriptions CHM）；接口契约 `nop-api-core/.../IMessageSubscription.java:10-20`（裸接口，无线程契约声明）
- **证据片段**:
  ```java
  // LettucePubSubService.java:190-199 —— cancel 三步无锁：改 entry.listeners → 退订 Redis 通道 → 摘注册表
  public void cancel() {
      cancelled = true;
      entry.removeListener(listener);          // CHM remove；listeners 变空
      if (entry.isEmpty()) {                   // 瞬时检查
          StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
          if (conn != null) { conn.sync().unsubscribe(topic); }   // 通道级退订
          subscriptions.remove(topic, entry);  // 整 entry 摘除
      }
  }
  // :221-226 —— resume 对 volatile 标志 check-then-act，随后 addListener 重挂连接监听器
  public void resume() {
      if (suspended && !cancelled) { suspended = false; entry.addListener(listener); }
  }
  ```
- **严重程度**: P1（罕见但可具体化的竞态，有明确触发链；后果为新订阅静默失效/已取消消费者复活收消息，全部无错误信号）
- **并发场景**: 同一 topic 的多个订阅者管理线程——`subscribe` 在服务实例监视器内执行，而 `PubSubSubscription.cancel/suspend/resume`（`service.lock(topic)` 工厂每次返回新句柄，句柄方法在各自属主线程调用）完全绕过该监视器；消息投递线程（Lettuce netty event loop，经 `SubscriptionEntry.message`）并发迭代 `listeners`。多消费者同 topic + 消费者动态增退正是该类的主用例（`SubscriptionEntry.listeners` 为 CHM 即为多消费者设计，测试 `TestLettuceNosqlService.java:615-643` 覆盖同 topic 双订阅）
- **触发时序**: （i）cancel×subscribe 致新订阅静默死亡——T1：订阅者 A 的管理线程 cancel()：`removeListener(A)` → listeners 清空 → `isEmpty()` 判 true；T2：同 topic 新订阅者 B 的线程进入 subscribe（持服务监视器）：`subscriptions.get(topic)` 在 T1 的 `subscriptions.remove` 前命中旧 entry → 走 else 分支 `entry.addListener(B)`（listeners={B}，size==1 → 向连接重挂 entry 监听器）；T1 继续：`conn.sync().unsubscribe(topic)` 把 B 刚订阅的通道退订 + `subscriptions.remove(topic, entry)` 把含 B 的 entry 从注册表摘除 → B 持有未取消、却永远收不到消息的订阅（通道已退订 + entry 不可达），无任何异常；（ii）cancel×resume 复活已取消订阅——T2'：resume 线程读 `suspended==true && cancelled==false`（T1 的 volatile 写尚未被其观测，JMM 允许）→ `addListener` 把 zombie entry 重挂为连接监听器；若 T1 的 isEmpty 判定已在挂载前发生 → 通道被退订、entry 被摘表，此后任意线程重新 `subscribe(topic)` 建新 entry 并重订通道 → 连接把消息分发给**新旧两个** entry（均按 topic 过滤命中）→ 已 cancel 的消费者恢复收消息且与新消费者双份投递
- **现状**: `subscribe` 用方法级 synchronized 保护"查注册表→挂监听器→SUBSCRIBE"的复合序列（含失败回滚 `:83-89`），但对称的退订/挂起/恢复序列完全无同步：volatile 只保证单标志可见性，"isEmpty 检查 → unsubscribe → 摘表"三步与"判空 → addListener"之间无任何互斥或 CAS；`IMessageSubscription` 接口与实现 javadoc 均未声明"调用方须外部同步"（F10 守卫缺失 + 契约缺失双重）
- **风险**: 消息面静默失效——订阅方以为在收、实际永久失联（monitoring 无感知）；或退订失效后已取消消费者持续收到敏感业务消息（越权接收面）；同 topic 双代订阅并存时每条消息投递两次，下游非幂等消费即重复处理；Redis 侧还可能残留已 detach 的通道订阅（subscribe×自取消交错时 SUBSCRIBE 在回滚窗后成功、entry 已摘表，无人再退订）
- **建议**: 把订阅生命周期操作收敛到服务实例监视器内——`PubSubSubscription` 持有外层 service 引用，`cancel/suspend/resume` 改为 `synchronized (service)`（与 subscribe 同锁，重入安全），把 `listeners` 变更、`isEmpty` 判定、`unsubscribe`、`subscriptions.remove` 纳入同一临界区；`suspended/cancelled` 在锁内退化为普通 boolean；或引入 per-topic 锁 + 以 `subscriptions.compute` 原子化"摘表/挂表"判定
- **失效面枚举**（F3 同族清扫）: 本文件订阅生命周期全方法清单——① `subscribe:72-94`（有同步，合格形态对照）；② `cancel:190-200`（本条主对象）；③ `suspend:213-218`（同族：`if (!suspended)` check-then-act + removeListener，与 ② 组合才成害，随锁内化闭合）；④ `resume:221-226`（本条主对象，交错 (ii)）；⑤ `isSuspended/isCancelled:202-210`（纯读，锁内化后自然合格）；⑥ `SubscriptionEntry.addListener/removeListener:127-139`（CHM 单操作原子，size==1/isEmpty 判定是复合面，随 ②③④ 锁内化闭合）；⑦ `doStop:112-117` 关闭连接不清 `pubSubConnection`/`subscriptions`——生命周期残留面，独立登记 CL-013。同族 IMessageService 实现（跨模块对照，不重审）：`PollingJdbcMessageService.Subscription`、`DataPlaneMessageServiceAdapter`、`DBMessageService`、`TdMessageService` 的 cancel/suspend/resume 契约一致性建议复核阶段抽查
- **信心水平**: 确定（交错序列与三步无锁事实代码级完整；实际触发依赖宿主以多线程方式管理同 topic 订阅，此为该适配器设计的正常用法）
- **误报排除**: 非"CHM/volatile 原语使用"类误报——缺陷在复合操作时序而非容器原语；非"无状态单例并发调用"——`subscriptions`/`listeners`/Redis 通道均为跨线程共享可变状态；非已声明边界——`IMessageSubscription` 及实现均无线程契约声明（已核 `IMessageService.java` 全文与实现类 javadoc）；降档口径已显式论证：repo 内生产调用方虽为 0，但多订阅者/动态增退是该 CHM 结构承载的一等用例（非 C1-002 式"宿主可选扩展点"），故不按对手方存在性降档，仅按部署触达记 P1（非 P0：当前 repo 无生产调用方，非主路径必触）
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现

### [C1-012] computeIfAbsent 全族为 GET-then-SET 非原子 check-then-act：违反 ICache 明文的"仅有一个结果生效"契约，且模块内 setnx/Lua 原子原语在册未用（F10）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:62-70`（computeIfAbsent）、`:156-166`（computeIfAbsentAsync）、`impl/LettuceHashOperations.java:43-52,55-57`（hash 族同型）、`:115-118`（getAndSetAsync HGET→HSET）；适配面 `nop-nosql-core/.../cache/NosqlCache.java:90-92`（覆写 ICache.computeIfAbsent 委托非原子实现）；契约锚点 `nop-kernel/nop-commons/.../cache/ICache.java:34-43,78-83`
- **证据片段**:
  ```java
  // LettuceMessageService.java:63-68 —— 远端共享态上的判-算-写三步，无事务/Lua/CAS
  RedisClusterCommands<String, Object> commands = sync();
  Object value = commands.get(key);            // 检查
  if (value == null) {
      value = mappingFunction.apply(key);      // 计算（任意耗时）
      commands.set(key, value);                // 无条件覆盖写（非 setnx）
  }
  // ICache.java:36-39 —— NosqlCache 所实现接口的明文契约
  // 写入采用putIfAbsent的原子语义：并发竞争时只有一个线程计算出的值会被写入，
  // 其余线程返回先写入者的值。……但仅有一个结果生效。
  ```
- **严重程度**: P2（交错序列可具体化；当前 repo 内生产调用方为 0——auth 各 store javadoc 明示绕开该面——按 C1-002 同款条件化降档记 P2）
- **并发场景**: 多个请求线程（可跨节点）对同一 key 首次 computeIfAbsent——缓存击穿/预取场景的常态并发；异步变体中 mappingFunction 还被调度到 Lettuce netty 完成线程执行（`computeIfAbsentAsync:157` 的 thenCompose）
- **触发时序**: T1：线程 A `get(key)` 得 null → 进入 mappingFunction（耗时加载）；T2：线程 B 同 key `get` 亦得 null（A 的 SET 未落地）→ 也进入 mappingFunction → A `set(k, vA)`、B `set(k, vB)` 先后落地，后写覆盖前写；交叠后果：A 返回 vA、B 返回 vB——同一 key 同一时刻两个调用方拿到**不同值**，远端终值为后写者，先写者的新值被静默丢弃（丢失更新）；若第三方在 A 的 get 与 set 之间 `put` 了更新值，A 的过期计算结果还会把它覆盖回退。hash 族 `getAndSetAsync:115-118`（HGET→HSET 两步模拟 HGETSET）同型：并发方在两步间的写入被无回读覆盖
- **现状**: 同类 `putIfAbsent`（`:194-196` setnx、hash `:105-107` hsetnx）与四个 Lua CAS 脚本（`REMOVE_IF_MATCH`/`PUT_IF_ABSENT_OR_MATCH`/`HASH_REMOVE_IF_MATCH`/`SESSION_SET_FIELD`，`RedisScripts.java:22-27`）证明原子原语在模块内是既有惯例，唯独 computeIfAbsent 族未收口；`NosqlCache` 更是把 ICache 的契约合格默认实现（`ICache.java:44-58`，putIfAbsent+重试）**主动覆写**为违约委托（`NosqlCache.java:90-92`）
- **风险**: 一旦宿主把 `NosqlCache` 当普通 `ICache` 使用（该类的存在目的），LocalCache 语义与 NosqlCache 语义在并发首载下不可互换——依赖"仅一个结果生效"的调用方（如以 computeIfAbsent 做单飞初始化/去重票据）将出现双值竞态；跨节点部署下窗口随 RTT 放大，非本机纳秒级
- **建议**: computeIfAbsent 族改用同模块既有原子原语——同步版以 `putIfAbsent`（setnx）重写并按 `ICache.java:54-57` 契约返回先写者值（`setnx` 失败后回读）；或仿 `PUT_IF_ABSENT_OR_MATCH` 增加"GET-or-SET"Lua 脚本一次往返完成判-算-写中可服务端化的部分（loader 仍需客户端执行，但写入端以 `set nx` 收口）；`NosqlCache.computeIfAbsent` 恢复 ICache 默认实现或按上述原子原语实现；`getAndSetAsync` hash 版改用 GETSET 等价 Lua/HGETSET 脚本
- **失效面枚举**（F3 同族清扫）: computeIfAbsent/读改写复合族全清单——① `LettuceMessageService.computeIfAbsent:62-70`（本条主对象）；② `computeIfAbsentAsync:156-166`（同族 + loader 在 I/O 线程执行，后者独立登记 CL-012）；③ `LettuceHashOperations.computeIfAbsentAsync:43-52`（同族）；④ `LettuceHashOperations.computeIfAbsent:55-57`（委托 ③）；⑤ `NosqlCache.computeIfAbsent:90-92`（ICache 违约面，本条主对象）；⑥ `NosqlCache.computeIfAbsentAsync:63-66`（委托 ②）；⑦ `LettuceHashOperations.getAndSetAsync:115-118`（HGET→HSET 丢失更新窗，同族）；对照原子合格形态：string `getAndSet:199-201`（服务端 GETSET 原子）、`putIfAbsent:194-196`/hash `:105-107`（NX 原子）、`removeIfMatch` 族 Lua（`:209-212`/`hash:136-140`）、`putIfAbsentOrMatchEx` 族 Lua（`:295-299`/`hash:220-226`）——同模块一成一败对照鲜明
- **信心水平**: 确定（竞态形态与契约文本均为代码级事实；降档仅针对"当前无生产调用方"的触发面）
- **误报排除**: 非"Redis 单命令原子性"误报——缺陷恰在多命令组合层；非"本地 check-then-act 需要分布式锁"的过度设计诉求——同模块 setnx/Lua 已给出无锁原子收口路径；非已声明边界——`IAsyncMap.computeIfAbsent` 虽无 javadoc 契约，但 `NosqlCache` 所实现的 `ICache.computeIfAbsent` 契约是明文强语义（`ICache.java:36-39`），且默认实现即合格，覆写为违约版本无任何注释声明意图
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（关联 nop-persistence-rw-CL-403 同 API 面相邻、缺陷形态不同）

### [C1-013] hash/session 族"写-TTL 分离"两步写无原子性：PEXPIRE 丢失或进程在两步间死亡即产生无 TTL 永久键（F2 半个成功），string 键同族已用 psetex/SET NX PX 原子收口而 hash 族未对齐

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceHashOperations.java:194-197`（putExAsync HSET→PEXPIRE）、`:210-217`（putIfAbsentExAsync HSETNX→PEXPIRE）、`:229-237`（getAndSetExAsync HGET→HSET→PEXPIRE）、`impl/LettuceSessionStore.java:62-73`（setAsync HMSET→PEXPIRE）
- **证据片段**:
  ```java
  // LettuceHashOperations.java:210-216 —— HSETNX 成功后再发 PEXPIRE，两命令非原子
  return async().hsetnx(key, field, value).thenCompose(set -> {
      if (set && timeout >= 0) {
          return async().pexpire(key, timeout).thenApply(v -> true);  // 若此步丢失/进程死亡
      }
      return CompletableFuture.completedFuture(set);
  });
  // 对照同模块 string 键原子形态（LettuceMessageService.java:288-291）：SET key val NX PX timeout 一步完成
  ```
- **严重程度**: P2（失败路径可具体化：两步间连接断开/进程死亡 → 键永久滞留；当前 hash/session 族 repo 内生产调用方为 0，条件化降档记 P2）
- **并发场景**: 调用线程与 Redis 连接的异步完成线程（两步经 thenCompose 串联）；"对手方"主要是故障（连接断、pexpire 请求丢失、进程在两步间被 kill）而非并发写者——但无 TTL 键一旦产生，会影响后续所有并发读者/写者的过期判定
- **触发时序**: T1：调用线程 `hsetnx(key, f, v)` 成功（键已存在、无 TTL）；T2：连接在该线程发出 `pexpire` 前断开（或进程崩溃/完成线程池阻塞时命令未发出）→ 键以无 TTL 形态永久滞留 Redis；`putIfAbsentExAsync` 版本即教科书"SETNX 后未 EXPIRE"分布式锁死锁形态；`putExAsync:194-197`/`setAsync:62-73`（HMSET→PEXPIRE）同型——对会话存储即"会话永不过期"，对限流占位即"当日配额键永不释放"（若被 auth 类消费者误用）
- **现状**: 模块对 string 键已全线原子化（`putExAsync`→psetex `:269-271`、`putIfAbsentExAsync`→SET NX PX `:284-292`），且对 session 单字段写已专门做 `SESSION_SET_FIELD` Lua 并注释明示动机（"writing a field into a non-existent (e.g. expired) session key would resurrect it as a TTL-less hash"，`LettuceSessionStore.java:83-84`）——作者已认知 TTL-less 复活危害，但整键级的四个两步写入口未按同一标准收口（F3 同族漏修形态）
- **风险**: 故障注入（网络抖动、发布重启）下慢性积累永不过期键：内存无界增长（F4）+ 过期语义违约（本应过期的会话/票据/占位永续有效——若承载鉴权票据即安全面）；无 TTL 键还会被同 key 后续 `setFieldAsync` 的 exists 守卫误判为"活会话"，进一步固化
- **建议**: 四个入口统一 Lua 化（HSET+PEXPIRE / HSETNX+PEXPIRE / HMSET+PEXPIRE 合并为单脚本，仿在册的 `HASH_PUT_IF_ABSENT_OR_MATCH` 与 `SESSION_SET_FIELD` 模式，`RedisScripts.java` 增补即可）；过渡最小修为两步间失败路径补偿（pexpire 失败/异常时 DEL 刚写入的键）并在 javadoc 声明残留风险
- **失效面枚举**（F3 同族清扫）: 写-TTL 分离族全清单——① `LettuceHashOperations.putExAsync:194-197`（本条主对象）；② `putIfAbsentExAsync:210-217`（本条主对象，最重——SETNX 无过期死锁形态）；③ `getAndSetExAsync:229-237`（三步链，任一后续步丢失均滞留）；④ `LettuceSessionStore.setAsync:62-73`（本条主对象，会话面）；对照原子合格形态：string `putExAsync:269-271`（psetex）、string `putIfAbsentExAsync:284-292`（SET NX PX）、`session setFieldAsync:81-89`（Lua）、`touchAsync:97-100`（单命令 PEXPIRE）、hash `getExAsync:200-207`（HGET→PEXPIRE 两步，但首步为读、失败只滞留 TTL 不滞留键，残余风险降一档——登记为观察项不单列）；`getAndSetExAsync` string 版 `:302-309`（SET GET EX 原子，对照合格）
- **信心水平**: 确定（两步序列与故障窗代码级事实；触发依赖连接故障/进程死亡，概率低但后果持久）
- **误报排除**: 非"Redis 两命令顺序执行通常没问题"的合理性辩护对象——本条是持久化副作用的无原子性提交，属 F2 明列的"多步写出部分成功"定向检查项；非已声明边界——接口 javadoc（`INosqlKeyValueOperations.java:29-34`）对 putExAsync 族承诺"写入值并在 timeout 毫秒后过期"，无 TTL 残留直接违约；降档论证：生产调用方 0（auth 走 string 原子路径），故 P2 而非 P1
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜已知未修复（同案：2026-05-20-deep-audit-nop-nosql-post-plan23 D03-02——session setAsync HMSET+PEXPIRE 非原子当时已登记 HIGH 且未修，当前 live code 两步形态未变；hash 族 putExAsync/putIfAbsentExAsync/getAndSetExAsync 三入口为本轮新面）

### [C1-014] static final 可变 EnumSet（CAPABILITIES）经 public getCapabilities() 泄漏共享可变静态集合，偏离同接口平台默认实现的 per-call 拷贝范式

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:41-42`（CAPABILITIES 定义）、`:49-52`（getCapabilities 返回共享引用）；平台范式对照 `nop-kernel/nop-api-core/.../message/IMessageService.java:25-28`
- **证据片段**:
  ```java
  // LettucePubSubService.java:41-42,49-52 —— EnumSet.of 返回可变 RegularEnumSet，直接暴露
  private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
          MessageCapability.SEND, MessageCapability.SUBSCRIBE);
  @Override
  public Set<MessageCapability> getCapabilities() {
      return CAPABILITIES;   // 共享静态可变集合经 public getter 逃逸
  }
  // IMessageService.java:25-28 —— 平台默认实现每次返回新 EnumSet（per-call 拷贝，无共享面）
  default Set<MessageCapability> getCapabilities() {
      return EnumSet.of(MessageCapability.SEND, ...);
  }
  ```
- **严重程度**: P3（防御性缺陷：仓库内现有消费方 `MessageOptionsValidator.check`（`MessageOptionsValidator.java:81-94`）对返回集只读（contains），无现实篡改者；理论风险，未具体化）
- **并发场景**: 任意持有 `IMessageService` 引用的宿主线程调用 `getCapabilities()` 拿到全 JVM 唯一实例；若任何代码对其 add/remove（`Set` 契约允许），变更对所有线程立即共享且无同步；并发读（validator 的 contains）与写（宿主 add）之间无任何同步机制
- **触发时序**: 理论风险，未具体化——需宿主代码主动修改返回的 Set 才触发（当前仓库无此调用方，故按证据标准第 4 条降 P3）；一旦发生：T1 宿主 `service.getCapabilities().add(WARN_DELAYED)` → 全局能力裁定面被静默放宽（所有后续 validateSend/validateSubscribe 判定变化）；T2 并发请求线程 validator contains 读取无 happens-before，结果不可预期
- **现状**: `WARN_ONCE`（`:45-47`）同为可变 EnumSet 但未泄漏（仅以参数传入 validator，validator 只读），唯 CAPABILITIES 经 getter 逃逸；`final` 只冻结引用不冻结集合内容，"static final"此处提供的是假性不可变暗示
- **风险**: 宿主或未来代码把返回值当私有集合修改（如"基于该后端能力集派生子集"时误用 add）→ 全局裁定面被静默篡改且跨线程可见性无保证；排查面极大（篡改点与受害点分离）
- **建议**: `CAPABILITIES` 改 `Collections.unmodifiableSet(EnumSet.of(...))`（或 Java 25 下 `Set.of`）；`getCapabilities()` 返回防御性拷贝 `EnumSet.copyOf(CAPABILITIES)` 以对齐平台默认实现范式；`WARN_ONCE` 一并 unmodifiable 化（防未来被透传暴露）
- **失效面枚举**（F3 同族清扫）: 两根 static final 引用型字段全清单——① `CAPABILITIES:41-42`（本条主对象，泄漏）；② `WARN_ONCE:45-47`（同源可变，未泄漏，随 ① 修复一并加固）；同族对照：`TdTableMeta.SUB_TABLE_NAME_PATTERN`（批次 1 已核，Pattern 不可变，合格）；nop-nosql 两根内 grep 复核无其他 static 集合
- **信心水平**: 确定（泄漏事实与可变性为代码级确定；危害触发依赖假想篡改者，已如实降档）
- **误报排除**: 非"static final + EnumSet 原语使用即合格"的误报校准形态——校准排除的是原语本身的合理使用，本条对象是可变共享静态经 public API 逃逸这一复合暴露面；非把不可变误判为可变——`EnumSet.of` 的返回类型 RegularEnumSet 的 add/remove 为 public 且 `Set` 视角完全可用
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现（与 C1-015 合并为同一「共享集合只读出口」修复检查项）

## 维度集外线索（overlay C2/C4/C5/C9）

### nop-persistence-peri-CL-001 RpcEntityPersistDriver 全部同步方法无限期阻塞等待且 RPC 调用不携带取消令牌（F5 挂死形态）

- **位置**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:107-114`（`invokeRpc` 以 `null` cancelToken 调 `invokeAsync`）+ 7 个同步出口的 `FutureHelper.syncGet`：`:652`（findFirst）、`:723`（findList）、`:769`（findAll）、`:825`（count）、`:853`（exists）、`:881`（delete）、`:910`（update）
- **形态**: 单次 RPC 调用既无超时参数也无取消令牌，`syncGet` 无限等待；远端挂死（不返回也不断连）时调用线程（HTTP worker / ORM 会话属主）无限期占用
- **为何超出本维度**: 这是等待点无超时的挂死/线程占用问题（F5「超时是否作用于所有等待点」），不属于共享可变状态与可见性
- **建议归维**: C5（资源占用/挂死）为主，关联 C9（长时运行线程预算）；建议对 `invokeRpc` 统一接入平台取消令牌与超时配置（对照 nop-rpc-cluster 的超时设施）

### nop-persistence-peri-CL-002 loadAsync/batchLoadAsync 将无超时兜底的原始 RPC future 直接注入 ORM flush 组合链

- **位置**: `RpcEntityPersistDriver.java:261-286, 368-411`（返回 `invokeRpc(...).thenAccept(...)` 原始 future）；组合点 `nop-orm/.../EntityPersisterImpl.java:160-215`（`FutureHelper.bothSuccess` 链式组合各 chunk future）
- **形态**: 若 RPC 静默挂死（F5「不抛异常也不产出」），chunk future 永不完成，`bothSuccess` 组合的 flush future 永不 settle，持有该会话的请求线程悬挂且无任何错误信号；与 CL-001 同根（同一处缺超时/取消），但作用面是异步 flush 链而非阻塞等待
- **为何超出本维度**: 悬挂 future 与会话泄漏属资源/长时运行维度；C1 只关注其间的状态可见性（可见性本身由 CompletableFuture 完成语义保证，无问题）
- **建议归维**: C5（挂死传播面）/ C9（长时运行悬挂会话堆积）；建议在 driver 层为 load/batchLoad future 加超时兜底（`orTimeout` 或平台 `AsyncJoinType` 超时设施），并对照 C1-001 的守卫建议一并收口

### nop-persistence-peri-CL-003 migrate() failOnError=false 的"半个成功"：失败迁移照常记账后引擎继续执行后续可能依赖它的迁移（F2）

- **位置**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:205-221`（`executeAndRecord` catch 块：记 FAILED 行后仅当 `context.isFailFast() && migration.isFailOnError()`（`:218`）才抛出，否则迁移循环 `:148-188` 继续下一版本）
- **形态**: 迁移 V2 失败（其 DDL 未生效）且 `failOnError="false"` → 引擎继续执行 V3/V4；若 V3 依赖 V2 的表/列，则级联 FAILED，或 `sql` 类变更在残缺 schema 上部分生效后"成功"记账——整体处于无恢复语义的中间态（F2 多步写出部分成功）
- **为何超出本维度**: 失败路径的恢复/重试语义与状态一致性，非共享可变状态与可见性
- **建议归维**: C9（长时运行状态一致性）；建议默认首个失败即停，或将"跳过依赖闭包"显式化为策略（失败版本的后续版本按依赖序跳过并标记 SKIPPED），失败行增加阶段标记便于续跑

### nop-persistence-peri-CL-004 rollback() 反向执行部分成功后失败，无回滚簿记且历史行仍标成功（F2）

- **位置**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:366-402`（`:369-370` 反转副本后 `:372-374` 逐 change 执行；`:390-397` 失败仅记 result 内存记录；`:376` `removeMigrationRecord` 仅在全部成功后才执行）
- **形态**: 反向 5 个 change 执行到第 3 个失败 → 前 2 个回滚 DDL 已执行但无任何持久化簿记，历史表该版本行仍保持 migrate 写入的 success=true → DB 处于"半回滚"且账面与实态背离；再次 rollback 仍从 change N 重来，对已回滚对象重复执行 DROP/ALTER 而失败于"对象不存在"
- **为何超出本维度**: 失败路径恢复语义（F2），非共享可变状态
- **建议归维**: C9；建议逐 change 记录回滚进度（history 表增加 ROLLBACK 阶段状态或逐 change 删除/重记账），二次回滚从断点续跑而非从头重放

### nop-persistence-peri-CL-005 CustomChangeExecutor/CustomConditionChecker 在迁移线程内联执行任意 XPL 脚本，无超时与取消令牌（F5）

- **位置**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/CustomChangeExecutor.java:30-35`（`customChange.getImplementation().invoke(scope)`）；`precondition/CustomConditionChecker.java:30-38`（`ccp.getExpression().invoke(scope)`）
- **形态**: 迁移线程同步内联执行用户脚本；脚本内死循环或远端调用挂死 → `migrate()` 无超时参数、`invoke` 无 cancelToken，迁移线程无限期占用（F5"超时是否作用于所有等待点"——invoke 是无兜底的等待点）；异常路径尚有 fail-fast（precondition 失败抛 `ERR_DB_MIGRATION_PRECONDITION_FAILED`），挂死路径零兜底
- **为何超出本维度**: 等待点无超时的线程占用/挂死，非共享状态可见性
- **建议归维**: C5（资源占用/挂死）为主，关联 C9（迁移批次整体卡死预算）；建议为脚本执行接入平台超时设施（xlang eval 超时选项或迁移层对整个 change 执行 `orTimeout` 兜底），超时后按 failOnError 语义记账退出

### nop-persistence-peri-CL-006 增量链作业 failure AtomicReference 只写不读：链静默死亡被 lag=0 门禁误读为"已追平"（F5/F9）

- **位置**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:179-181`（裸线程 catch Throwable → `running.failure.set(e)` 后线程死亡）、`:230-233`（`getFailure` 生产代码零调用方，仅 javadoc 宣称"编排器轮询"）；`OrmMigrationOrchestrator.java:219-226`（runVerify lag 门禁不探测链存活）；`IncrementalLagProbe.java:52-57,71-90`（指纹前后比对，死亡=指纹静止=判追平）
- **形态**: 链线程崩溃（Debezium engine 异常、源库断连重试耗尽）后 failure 引用无人消费，状态行停留 INCREMENTAL；随后 runVerify 的 lag 门禁观测"offset 无前进 + ledger 无新增"——链死了恰好也满足该条件，静默死亡与追平在观测面上**不可区分**；VERIFY 在缺失增量的库面上跑 count/checksum（count 可拦截大缺口，小窗口增量缺失可双双通过），SWITCH 后数据丢失
- **为何超出本维度**: 失败传播缺失/吞异常导致的状态失真（F9）与挂死不可见（F5）属存活性与长时运行一致性，C1 只登记共享面（failure 引用本身可见性合格，AtomicReference）
- **建议归维**: C4（存活观测）+ C9（校验结论可信性）；建议 runVerify 门禁 0 前置"链存活检查"：遍历 runningJobs 校验 `getFailure()==null && thread.isAlive()`，死亡即 gateFailed；runIncremental 返回后由编排器周期轮询或注册回调

### nop-persistence-peri-CL-007 runIncremental 先落 INCREMENTAL 状态行再逐链启动：部分启动失败后阶段卡死不可重入（F2 半个成功）

- **位置**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:196-201`（`:196-197` 先 setPhase+update，`:200` 后 startFallbackChains）；`IncrementalJobManager.java:134-139`（循环逐链 startChain，中途抛出无补偿）；`OrmMigrationOrchestrator.java:160-166`（resume 对 INCREMENTAL 为 no-op）
- **形态**: M 个目标分片启动到第 k 个失败（如 CDC 配置错误、sink 建表失败）→ 前 k-1 条链已长驻运行、状态行已是 INCREMENTAL；重试 runIncremental 被 checkTransition 拒绝（INCREMENTAL→INCREMENTAL 不在白名单，`MigrationPhase.java:39`），resume 不补链，唯一出路 ABORT 后从头再来——而 ABORT 会丢弃已跑的全部全量+增量进度
- **为何超出本维度**: 失败路径恢复/重入语义（F2），非共享可变状态
- **建议归维**: C9（状态机恢复语义）为主，关联 C2（转移白名单缺同阶段重入边）；建议 INCREMENTAL 允许幂等重入（按 runningJobs 存活链去重补启），或将 startFallbackChains 做成"先全部构造成功、再统一启动"的两阶段提交形态

### nop-persistence-peri-CL-008 SWITCH 阶段 rebuild 失败后 fix-forward 路径不可达、abort 不可达，唯一出路是带失败路由开门（与 `:418-419` 注释承诺相反）

- **位置**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:417-424`（rebuildRouting catch 置 `rebuild-failed:<msg>`，注释宣称"fix-forward（修复后 confirm 重试）或回翻"）；`:266`（confirmSwitch 的 checkTransition(SWITCH→SWITCH) 非法——重试 confirm 被状态机拒绝）；`:300` + `MigrationPhase.java:41`（abort 的 SWITCH→ABORT 亦非法：`SWITCH → Set.of(DONE)`）；`:279-285`（completeSwitch 只查 phase==SWITCH，不查 routeRebuild=="rebuild-done"，可带 rebuild-failed 直接开门）
- **形态**: rebuildRouting 抛异常后作业卡在 SWITCH；注释给的两条出路全部不可达（confirm 重试被白名单拒绝、abort 被白名单拒绝、回翻无 API），而 completeSwitch 的收敛门在代码上不存在——运维只要再调一次 completeSwitch 就能在路由半重建状态重开写门，恰是注释明令禁止的状态
- **为何超出本维度**: 状态机出边设计与门禁条件缺失，非共享可变状态可见性
- **建议归维**: C2（同步/门禁原子面）+ C9（状态机可恢复性）；建议 completeSwitch 增加门禁条件 `routeRebuild=="rebuild-done"`（或 ALLOWED 增加 SWITCH→SWITCH 重入边 + confirmSwitch 幂等重试 rebuild），并补 SWITCH→ABORT 出边评估
- **关联**: 本条与 C1-006（状态行丢更新）、CL-007（不可重入）同属编排状态机的转移原子性/可恢复性族，复核时可合并处置

### nop-persistence-peri-CL-009 stopAll 仅 source.cancel() 不等待不校验终止：ABORT/completeSwitch 在链未实际退出时继续推进（F8 清理不对称的等待半边）

- **位置**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:201-206`（stopAll：循环 stop 后立即 clear，无 join/await）、`:235-237`（stop = source.cancel() 单动作）
- **形态**: cancel 是协作式取消请求，env.execute 的 run 循环退出时机不确定（Debezium engine 内部 poll/flush 周期）；stopAll 返回即被编排器当作"增量面已停"使用——abort `:307-308` 随即开门、SWITCH 收敛路径随即推进，目标表在门开后仍可能收到链的残余 upsert（C1-004 的 join 竞态使唯一的等待原语也不可靠）
- **为何超出本维度**: 资源停止的完成确认/清理对称性（F8），属存活与资源维度
- **建议归维**: C4；建议 stopAll 语义升级为"cancel → 逐链 join(超时) → 未死链登记并使 abort 失败"，或将"确认停止"作为 abort/completeSwitch 的显式门禁步骤（与 CL-006 的存活检查共用同一观测面）

### nop-persistence-peri-CL-010 NopStreamOffsetBackingStore.REGISTRY 按 connector 名只增不清：每次搬迁作业泄漏 1..M 个 registry 条目（F4/F8，轻度）

- **位置**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:59-60`（static ConcurrentHashMap REGISTRY）、`:101-103`（clearConnector 仅被 initializeState 首跑路径调用）；`IncrementalJobManager.java:174,186`（jobName 含 jobId，每作业 1..M 个 connector 名）；`stopAll:201-206`/`OrmMigrationOrchestrator.completeSwitch:279-290`（均无 registry 清理）
- **形态**: connector 名 = `mig-<jobId>[-<i>]`，每作业派生新名；DONE/ABORT 后 REGISTRY 条目（含 offset data map）与 LagProbe `forConnector` 偶发创建的空条目（`IncrementalLagProbe.java:81-90` → `NopStreamOffsetBackingStore.java:81-90` computeIfAbsent）永不清除——长寿 JVM 反复搬迁场景线性慢增长；单独危害轻（每条目为小 map），但属"只增不删账本"同族
- **为何超出本维度**: 长时运行资源增长（F4）与清理对称性（F8），非共享状态可见性
- **建议归维**: C4/C5（资源账本）；建议 stopAll/completeSwitch/abort 终态路径对本次作业的 connector 名逐个 `clearConnector`（与 CL-009 的停止确认同点收敛）

### nop-persistence-peri-CL-011 SubscriptionEntry.message 在 Lettuce netty I/O 线程内联执行全部消费者：慢消费者阻塞共享连接上所有 topic 的投递（F5/F8）

- **位置**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:145-161`（RedisPubSubAdapter 回调内同步 for 循环调 `consumer.onMessage`，异常仅转 `onConsumeFailure` 回调）；连接共享模型 `:147`（"one shared connection carries all subscriptions"注释）
- **形态**: 所有 topic 的投递共享单条 pub/sub 连接的单个 event loop 线程；任一消费者阻塞（远端调用/锁等待）即冻结全部 topic 的消息分发并可能触发连接心跳超时；消费异常被转为回调而非投递失败（F9 面——消费失败对发送方不可见，属 fire-and-forget 已声明边界，但消费者阻塞无任何兜底）
- **为何超出本维度**: 线程占用/挂死传播与吞吐面（F5），共享状态本身无可见性缺陷
- **建议归维**: C5（资源占用/事件循环饥饿）为主，关联 C9；建议消费者投递接入平台线程设施（`io.nop.commons.concurrent.executor` 的 IThreadPoolExecutor）做 I/O 线程与消费线程解耦，并为单消费者执行加超时兜底

### nop-persistence-peri-CL-012 computeIfAbsentAsync 族把 mappingFunction 调度到 Lettuce 完成线程执行任意加载逻辑：事件循环饥饿面

- **位置**: `LettuceMessageService.java:156-166`、`LettuceHashOperations.java:43-52`（thenCompose 内联 `mappingFunction.apply`）
- **形态**: 加载函数（可能含 DB 查询/远端调用）运行在 Redis 响应线程（netty event loop）上；慢加载阻塞该 event loop 上所有后续响应处理，与 CL-011 同根（I/O 线程执行用户代码）但作用面是 KV 异步路径而非 pubsub 投递
- **为何超出本维度**: 线程占用/调度策略，非共享状态可见性（值本身的可见性由 CompletableFuture 完成语义保证）
- **建议归维**: C5；建议 `thenComposeAsync(..., 自有executor)` 或改为"取回后交调用方线程计算"的两段式，加载逻辑脱离 event loop

### nop-persistence-peri-CL-013 生命周期残留：doStop 不清连接引用与订阅表，provider 重启后 pubsub 服务缓存已死连接（F8 清理不对称）

- **位置**: `LettucePubSubService.java:112-117`（doStop 关闭 `pubSubConnection` 但字段不清空、`subscriptions` 表不清、entry 与连接的监听器绑定不解）、`:96-105`（stop 后 subscribe 经 getOrCreateConnection 返回非 null 的已关闭连接）；`LettuceRedisConnectionProvider.java:128-138`（doStop 不清 client/supplier 字段；allowRestart 默认 true，`LifeCycleSupport.java:107` stop 后状态回 CREATED 允许再 start，重启后 `LettucePubSubService.pubSubConnection` 仍指向旧 client 的死连接）
- **形态**: stop→subscribe 序列在已关闭连接上先 addListener（不抛）后 sync().subscribe（抛错，subscribe 回滚路径可兜住注册表面）——主要 fail-fast 可见，但 provider restart 场景下死连接被静默复用且旧 Redis 侧订阅无人清理；与 C1-011 共用同一状态面
- **为何超出本维度**: 生命周期/清理对称性与重启语义，非并发时序缺陷
- **建议归维**: C4/C9；建议 doStop 置空 `pubSubConnection` 并逐 entry 解绑，provider 暴露重启回调供 pubsub 服务失效缓存

### nop-persistence-peri-CL-014 NosqlCache.getAsync 以 GETEX 滑动 TTL 实现只读语义：读路径携带远端写副作用，auth 侧已实证该语义冲突为禁用项

- **位置**: `nop-nosql-core/.../cache/NosqlCache.java:52-59`（`getAsync` 在配置 expireAfterAccess 时走 `ops.getExAsync`）；冲突实证 `nop-auth-service/.../mfa/store/RedisMfaChallengeStore.java:29`（javadoc 明示"禁用 getExAsync/NosqlCache.getAsync（后者会 GETEX 滑动刷新，让 300s 失效）"）
- **形态**: `ICache.getAsync` 语义应为纯读，适配器实现成"读+续期"两写副作用；并发读放大（N 读 = N 次 EXPIRE 写），且调用方对"读会改 TTL"无感知——auth 用 javadoc 禁用的方式规避，说明 ICache 通用消费方接入该适配器即踩坑
- **为何超出本维度**: 接口语义/副作用设计问题，非共享可变状态与可见性
- **建议归维**: 契约正确性评审为主，关联 C9（读放大写）；建议 expireAfterAccess 未配置时行为已正确（`NosqlCache.java:54-57` fallback），应直接移除 GETEX 分支或在 ICache javadoc 声明该实现类不支持的配置项并 fail-fast

---

## 维度复核结论（WI 2.5 C1 独立复核，2026-10-18）

> 复核人：C1 维度独立复核员。逐条回到 live code 验证证据行真实性与 T1/T2 时序可具体化性；现状对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.4 台账，重点 CL-415 / 2.2 C1-009 连接面）与历史审计目录（含 `2026-05-20-deep-audit-nop-nosql-post-plan23/`）。本节不修改原发现条目，仅在条目末尾追加复核状态行。C1-015 的复核详见 C1-round2.md 复核节。

### 逐条判定表

| ID | 判定 | 最终严重度 | 现状标注 | 一句话依据 |
|---|---|---|---|---|
| C1-001 | 保留 | P2 | 新发现 | loadAsync:270-285 / batchLoadAsync:378-410 thenAccept 跨线程变更会话为代码级事实；同族 Td(:84,:178) / Jdbc(:127,:190,:236) futureCall 内联对照成立；7 个 syncGet 出口（:652..:910）核实；后果面未具体化维持 P2 |
| C1-002 | 保留 | P2 | 新发现 | :68-70 裸 HashMap + :113-121 public 注册 API + default.beans.xml:11 单例 bean + TestMigrationEngine:207,230-231,485 构造后注册用法全部核实；MigrationExecutor:28 非 final + DbTypeFilterExecutor:37/:47/:55/:100 同族核实；对手方未实证，条件化降档 P2 正确 |
| C1-003 | 保留 | P1 | 新发现 | 判定-执行-记账三步无互斥核实（MigrationEngine:144/:165/:187、HistoryManager:72-86/:88-139/:53/:228-237）；主键冲突→catch 记 FAILED→UPDATE 覆盖对方 success 行→永久重试循环在 executeAndRecord try/catch 内整链闭环；模块内 lock grep 为 0 复现；多进程启动下降档口径不适用论证成立 |
| C1-004 | 保留 | P1 | 新发现 | :176/:184/:185/:186/:214/:201-206/:235-243 逐行核实：start 先于句柄发布、join null 短路、stopAll 无等待均为代码级事实 |
| C1-005 | 保留 | P1 | 新发现 | 阶段 TOCTOU（:186/:191/:196-197）+ runningJobs.put 静默覆盖（:186）+ 同名 connector 双引擎主链成立；子断言「A 组共享 offset map 被清空重置」经复核修正——B 的 clearConnector 仅摘 REGISTRY 条目，A 引擎 store 经 volatile data 字段持旧 map 直接引用、checkpoint 快照旧 map 不受踩踏；主后果（双消费双写 + 孤儿链）不受影响 |
| C1-006 | 保留 | P1 | 新发现 | MigrationJobStateStore.update 全列覆盖仅 WHERE job_id 核实；abort(:296-313)×confirmSwitch(:257-274) 双 checkTransition 交错源码级重演，净终态「配置已翻转+门开+账面 ABORT」成立 |
| C1-007 | 保留 | P1 | 新发现 | SessionFactoryImpl:70 非 volatile + rebuildRouting:398-400「立即生效」注释违约 + ConfigShardSelector:57-60 非 final 字段 init 期赋值（非安全发布）全部核实；复核独立构造「写门配置 HB 兜底」反证假说并沿读路径证伪——CFG_ORM_MIGRATION_WRITE_GATE 静态 ref 即 usedRefs 常驻 DefaultConfigReference，其 provider 字段（DefaultConfigReference.java:19）为普通字段、AbstractConfigProvider.updateConfigValue(:89-93) 原地裸换，开门读与 setShardSelector 间无 happens-before，原发现 T2 场景成立 |
| C1-008 | 保留 | P2 | 新发现 | 五个惰性访问器（:120-124,:432-436,:438-442,:444-454,:456-460）全为 plain check-then-act 核实；触发面窄维持 P2 |
| C1-009 | 保留 | P1 | 新发现 | :302 全类唯一绕访问器直读（grep 复现）核实；并发 abort×runIncremental 下 stale-null 读 JMM 成立 |
| C1-010 | 保留 | P2 | 新发现 | 单次瞬时检查(:215)+分钟级窗口+completeSwitch:285 无条件开门核实；附带勘误：本条误报排除 a「门翻转可见性合格」经复核证伪（provider 原地裸换，见 C1-007 行），门可见性面归平台同案 nop-core-framework-C1-007，不改变本条「无保持机制」判定 |
| C1-011 | 保留 | P1 | 新发现 | subscribe synchronized(:72) 而 cancel(:190-200)/suspend(:213-218)/resume(:221-226) 完全绕行监视器核实；交错 (i) 新订阅静默死亡、(ii) 已取消订阅复活双投递均源码级重演成立；IMessageSubscription 裸接口无契约核实；2026-05-20 nosql 审计未登记本面 |
| C1-012 | 保留 | P2 | 新发现 | GET-then-SET 无条件 set(:62-70)、ICache.java:36-43 明文契约+合格默认实现、NosqlCache:90-92 违约覆写、hash 族(:43-57)与 getAndSetAsync(:115-118) 全部核实；auth 消费方走原子路径（RedisMfaChallengeStore:93,:200、RedisSendCodeRateLimiter:94）核实，条件化降档 P2 正确 |
| C1-013 | 保留 | P2 | 已知未修复 | 四个两步写入口（hash :194-197/:210-217/:229-237、session :62-73）核实；session setAsync HMSET+PEXPIRE 面与 2026-05-20 nosql 审计 D03-02（HIGH）同案且当前 live code 未收口（历史 non-atomic 注释已不存在、两步形态未变）；hash 族三入口为本轮新面；string 键 psetex/SET NX PX 对照与 SESSION_SET_FIELD Lua 动机注释（:83-84）核实 |
| C1-014 | 保留 | P3 | 新发现 | CAPABILITIES(:41-42) EnumSet 经 getter(:49-52) 逃逸核实；全仓 getCapabilities() 消费方只读（MessageOptionsValidator check/contains），无现实篡改者，P3 恰当；IMessageService 默认实现 per-call EnumSet 对照核实 |
| C1-015 | 保留 | P3 | 新发现 | TdTableMeta:34-36/:88-94 泄漏 getter 核实，全仓消费方（TdSqlHelper:79,81,82,90,128）只读，P3 恰当（详见 C1-round2.md 复核节） |

**驳回项**：无（15 条全部保留，零驳回、零待定；无条目需要代码级反证驳回）。

### 高风险条目（P1×7）子项复核说明

1. **C1-003**：补强原发现未显式写出的一环——B 侧 recordMigration 主键冲突异常发生在 `executeAndRecord` 的 try 块内，被同一 catch 捕获后记 FAILED 并 UPDATE 对方成功行为 success=false（MigrationEngine.java:206-221 try/catch 结构核实），「永久重试循环」闭环成立。
2. **C1-004 / C1-009**：行号逐一核实无误；:302 直读为全类唯一绕访问器点（grep 复现）。stopAll 无等待（:201-206）与 join null 短路（:239-243）互为放大面，与原发现失效面枚举一致。
3. **C1-005**：唯一子断言修正——`clearConnector` = `REGISTRY.remove(connectorName)`（NopStreamOffsetBackingStore.java:101-103），A 引擎 store 的 `data` volatile 字段仍指向被摘除的旧 map，运行期 offset 与 checkpoint 快照均不受 B 的 clear+REPLACE 影响；但同名 connector 双引擎并发消费/双写、`runningJobs.put` 覆盖产生孤儿链的主竞态链完整成立，维持 P1。
4. **C1-007**：本轮最重子项复核。复核员先独立构造了反证假说「请求线程开门后读门配置（volatile/CHM）可与编排线程 setShardSelector 建立 happens-before，新写必然看到新 selector」，随后沿 CFG_ORM_MIGRATION_WRITE_GATE 读路径逐层验证：`OrmConfigs.varRef` → `AppConfig.getStaticConfigReference` → `DefaultConfigProvider.makeConfigRef`（usedRefs.computeIfAbsent 返回常驻实例，DefaultConfigProvider.java:211-218）→ `DefaultConfigReference.getAssignedValue` → `provider.get()`。**provider 字段为普通字段（DefaultConfigReference.java:19），updateValue 原地裸换 provider（AbstractConfigProvider.java:89-93），读写间无任何 volatile/CHM 边**——反证不成立，原发现 T2 场景（开门后新写可能读旧 selector 落旧表）维持，P1 成立。该验证同时证伪了本审计批次口径 3a 与 C1-010 误报排除 a 的「读取面可见性合格」结论（其核查层级为 configRefs/configValues 两 volatile map，非运行期 usedRefs 路径）；门翻转可见性面与 WI 2.2 已登记的 nop-core-framework-C1-007（配置发布协议裸写裸读族）同案，平台侧修复入口。
5. **C1-011**：subscribe 持服务实例监视器而三个生命周期方法完全绕行的对称性缺口在源码级重演；交错 (i) 中 B 的 addListener 与 T1 的 isEmpty 判定/unsubscribe/摘表三步交错使新订阅永久失联且无错误信号，交错 (ii) 中 resume 读 stale cancelled 重挂 zombie entry 致双投递——均无守卫可挡。2026-05-20 nosql 审计（D01–D19）无 PubSub 订阅竞态登记，确认新发现。

### 现状标注汇总与同案命中清单

- **判定统计**：保留 15（其中维持原严重度 15、降级 0、升格 0）/ 驳回 0 / 待定 0。
- **最终严重度分布**：P0×0；P1×7（C1-003/004/005/006/007/009/011）；P2×6（C1-001/002/008/010/012/013）；P3×2（C1-014/015）。
- **现状标注分布**：新发现×14；已知未修复×1（C1-013）；已修复×0。
- **同案命中（1 条）**：C1-013 ← `2026-05-20-deep-audit-nop-nosql-post-plan23` **D03-02**（INosqlSessionStore.set HMSET+PEXPIRE 非原子，HIGH，当时仅建议补文档或 Lua，未修）。
- **跨单元同族对照（不并案，Phase 8 修复批次应对齐）**：
  - C1-002 ← nop-persistence-rw **CL-415**（SqlDataTypeMapping.register 并发无同步，watch）+ nop-stream-orchestration-thin **C1-001**（UDF 注册表公开 register，P1——其读面为逐记录热路径故判级更高）。
  - C1-003 / C1-005 / C1-006 ← WI 1.5 **C7-001**（多节点无 fencing/丢失更新）、WI 1.6 **C3-003**（租约族）同族，对象不同不并案；跨进程租约/咨询锁建议三案共用同一设施选型。
  - C1-007 ← nop-persistence-rw **C1-003**（非 volatile 热替换族）同族；门配置可见性半边归 nop-core-framework **C1-007**（配置发布协议三字段裸写裸读，族案含 C1-017）。
  - C1-008 ← nop-kernel-concurrency **CL-035**（ClassModel.beanModel 双检锁缺 volatile，watch）同族。
  - C1-012 ← nop-persistence-rw **CL-403**（cacheGet computeIfAbsent 锁内 loader）同 API 面相邻、缺陷形态不同。
  - C1-015 ← 本单元 C1-014（同族合并修复）。
- **与 WI 2.2 C1-009（JdbcConfigSource.close 竞态，连接面）比对结论**：本维度 15 条无连接生命周期类发现（本单元的连接残留面已另行登记 CL-013，属 CL 台账不入 C1 发现），与 2.2 C1-009 无同案。

## 最终保留项（复核后清单）

| ID | 标题 | 最终严重度 | 现状 | 同案指针 |
|---|---|---|---|---|
| C1-001 | RpcEntityPersistDriver 在 RPC 传输线程变更 ORM 会话/实体状态，偏离同族内联契约且无守卫 | P2 | 新发现 | — |
| C1-002 | MigrationEngine 三张裸 HashMap 注册表 public 注册 API 无并发守卫（F10） | P2 | 新发现 | nop-persistence-rw-CL-415（同族 watch）；对照 1.3 C1-001 |
| C1-003 | 迁移历史表 check-then-act 无跨进程互斥：多节点并发启动 DDL 重复执行 + FAILED 脏行永久循环 | P1 | 新发现 | 同族对照 1.5 C7-001 / 1.6 C3-003 |
| C1-004 | RunningChainJob.thread 晚于 start() 发布且停止协议无终止确认 | P1 | 新发现 | — |
| C1-005 | runIncremental 阶段 TOCTOU + runningJobs.put 静默覆盖：孤儿增量链 + 同名 connector 双引擎 | P1 | 新发现 | 关联 1.3 C3-008（同一 offset 存储设施） |
| C1-006 | 状态行全列覆盖 UPDATE 无版本谓词：abort×confirmSwitch 丢更新背离终态 | P1 | 新发现 | 同族对照 1.5 C7-001 |
| C1-007 | rebuildRouting 热替换非 volatile shardSelector：陈旧路由 + 非安全发布 | P1 | 新发现 | 平台侧 nop-core-framework-C1-007（门配置 provider 裸换）；关联 nop-persistence-rw-C1-003 |
| C1-008 | 编排器五个惰性组件 plain check-then-act 无守卫（F10） | P2 | 新发现 | nop-kernel-concurrency-CL-035（同族 watch） |
| C1-009 | abort() 直读非 volatile incrementalManager 字段：stale-null 跳过 stopAll | P1 | 新发现 | — |
| C1-010 | 写门瞬时检查无保持机制：VERIFY/SWITCH 长窗口内翻转无感知 | P2 | 新发现 | 门可见性半边归 nop-core-framework-C1-007 |
| C1-011 | LettucePubSubService 订阅生命周期与 subscribe 监视器不对称：静默失效/复活双投递 | P1 | 新发现 | — |
| C1-012 | computeIfAbsent 族 GET-then-SET 违反 ICache 明文契约，原子原语在册未用（F10） | P2 | 新发现 | 关联 nop-persistence-rw-CL-403 |
| C1-013 | hash/session 族写-TTL 分离两步写无原子性：无 TTL 永久键（F2） | P2 | 已知未修复 | 同案：2026-05-20 nosql D03-02（session 面） |
| C1-014 | static final 可变 EnumSet（CAPABILITIES）经 public getter 逃逸 | P3 | 新发现 | 与 C1-015 同族合并修复 |
| C1-015 | TdTableMeta 经 public getter 泄漏构造期可变 List（C1-014 跨模块同族） | P3 | 新发现 | 同 C1-014 合并 |
