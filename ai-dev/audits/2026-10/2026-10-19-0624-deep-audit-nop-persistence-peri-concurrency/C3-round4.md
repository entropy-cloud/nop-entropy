# C3 深挖第 4 轮（收敛轮）— nop-persistence-peri（WI 2.5）

## 深挖范围与方法

第 3 轮在 orm-migration 的 relocate 路由面连续挖出两个键/空间错位（C3-009 querySpace 维度、C3-008 名维度），本轮按任务书做**同族高发区的穷举式标识符对齐审计**：对 ShardChangeEventRouter / TargetShardMapping / SourcePositionPinner / ConsistencyVerifier / FullCopyBatchJob / IncrementalJobManager 六个目标类（外加其唯一接线方 OrmMigrationOrchestrator 与两个跨模块消费面 nop-batch `DaoBatchStateStore`、nop-dao `ShardStrategy` 的使用面锚点）的全部跨对象引用点，逐站点核对：使用了哪些标识符（jobId / connectorName / serverName / querySpace / physicalTable / entityName / shard index / jobName / taskKey）、各标识符从哪个对象派生、等值比较与容器键是否跨语义复用。

方法：逐文件精读 + grep 穷举（`new PhysicalTable`、`.equals(`、容器 `.put(`/分组键、标识符拼接位）；rescale 对照以 `ShardStrategy.buildSelection`/`buildTableName` 为同源性判据（`shardSelections()` 与 `selectShard()` 经同一 `buildSelection` 生成，`PhysicalTable` 两侧同构构造）；批身份语义以 `DaoBatchStateStore`（`(taskName,taskKey)` 唯一键 + 启动闸门）与 `JdbcPageBatchLoaderProvider`（内存 range 从 0 起页、不读 completedIndex）的实证为锚。基线只对照 `_tmp/cra-baseline-nop-persistence-peri.txt` 与本轮允许的三个 C3 文件，未触盲审禁区。

## 标识符对齐审计矩阵

| # | 站点 | 标识符及派生来源 | 判定 |
|---|---|---|---|
| 1 | `IncrementalJobManager.startFallbackChains:125-126` | baseConnector = config 裸名 或 `"mig-"+jobId` | 已立案 C3-008（探测侧裸名错位的根源侧） |
| 2 | `IncrementalJobManager.startFallbackChains:136` | connectorName = `targets.size()==1 ? base : base+"-"+i` | 注册侧正确基准（C3-008 对照侧） |
| 3 | `startFallbackChains:134-139` | connectorName↔target 配对（同一循环迭代同源取用） | 自洽 |
| 4 | `buildCdcConfig:87-90` | `DebeziumConfig.name=connectorName`；serverName 缺省=connectorName | 自洽（第 3 轮⑤裁定维持） |
| 5 | `buildCdcConfig:91-96` | database-host/database-name 单值；tableIncludeList=全部源表名（跨 qs 拼接、无 host 覆盖面校验） | **本轮 C3-012 组成面** |
| 6 | `buildCdcConfig:107` | schemaHistoryPath 后缀 `"/"+connectorName` | 自洽（与链名同源唯一化） |
| 7 | `startChain:153-157` | seed partition = serverName（=分片 connector 名）→ setInitialOffsets | 自洽（链内） |
| 8 | `startChain:170` | operator 名 `"mig-cdc-"+connectorName`（独立 env 内） | 自洽 |
| 9 | `startChain:174,186` | **jobName = jobId + target.tableName（丢 querySpace）→ runningJobs 容器键** | 同根扩展 C3-001（见下），不另立案 |
| 10 | `ShardChangeEventRouter.route:41-42` | relocate 硬编码 `("", baseTableName)` | 已立案 C3-009 |
| 11 | `belongsTo:59-61` + `IncrementalJobManager:172` | `target.equals(route(event))` 全字段等值；rescale 下 route 侧 `newStrategy.selectShard→buildSelection` 与 targets 侧 `shardSelections→buildSelection` 严格同源 | **rescale 对照面：结构自洽，无错位形态**（见下节） |
| 12 | `TargetShardMapping.rescale:138-148` | source/target 展开同源 `shardSelections()` + 同一 `buildTableName` | 自洽 |
| 13 | `TargetShardMapping.relocate:159-167` | 逐 sourceQuerySpace 1:1 展开，目标集含**同名异 qs** 物理表 | 错位族形态来源（C3-009 / 扩展 C3-001 / C3-011 均以此形态触发） |
| 14 | `TargetShardMapping.routeRow:235-246`、`routeShardValue:251-262` | relocate 返回 `("", baseTableName)` | 已立案 C3-009（②③） |
| 15 | `TargetShardMapping.mapQuerySpace:298-306` | mapped qs 派生 | 正确侧修复基准 |
| 16 | `SourcePositionPinner.pin:48-54` + `JdbcPositionProvider:59-99` | 位点查询 per-qs 路由执行（执行层正确） | 执行方自洽；其调用方的 qs 选取见 #17 |
| 17 | `OrmMigrationOrchestrator.pinP0:356-361` | **qs = `sourceQuerySpaces.get(0)` 代表性假设；与 debezium.database-host（#5）、源表全集（跨 qs）三者无任何交叉校验** | **本轮 C3-012 主对象** |
| 18 | `OrmMigrationOrchestrator.runVerify:220-222` | 探测 connectorName=裸配置名；probe ledger qs=stateQuerySpace | 已立案 C3-008 + C3-010 |
| 19 | `IncrementalLagProbe.snapshot/offsetsFingerprint:71-90` | `forConnector(裸名)` 读 registry | 已立案 C3-008 |
| 20 | `IncrementalLagProbe.countLedger/maxEpoch:92-103` | ledgerTableName 全平台共享常量 + ledgerQuerySpace=状态行空间 | 已立案 C3-010（namespace 面归 C7-008，不重复） |
| 21 | `FullCopyBatchJob.copyTable:100` | **taskKey = `jobId+":"+source.getTableName()`（丢 querySpace）→ nop-batch `(taskName,taskKey)` 身份键** | **本轮 C3-011 主对象** |
| 22 | `FullCopyBatchJob:88` | taskName = config.batchTaskName（同 entity 共享，靠 taskKey 区分） | 单表形态自洽；多 qs 形态并入 #21 |
| 23 | `FullCopyBatchJob.buildSelectSql:121-136` | 读侧 SQL qs=source.qs（与身份键解耦） | 自洽（深分页代价归 CL-025，不重复） |
| 24 | `ShardedUpsertConsumerProvider.consume:77-87` | 分组键 = PhysicalTable 全字段 equals/hashCode | 自洽（relocate 路由值错位已立案 C3-009） |
| 25 | `ShardedUpsertConsumerProvider.upsertGroup:89-101` | 写 qs=target.qs，模板=主 jdbcTemplate | 已立案 C3-009（FULL 消费点同根） |
| 26 | `ConsistencyVerifier.sumCounts:120-129` / `findPage:183-189` | 逐表自身 qs 路由 | 自洽（verifier 模板接线错位已立案 C3-009④） |
| 27 | `ConsistencyVerifier.scanHashes:157-181` | pk 规范化串跨表归并（DUP 标记 fail-loud） | 自洽 |
| 28 | `OrmMigrationOrchestrator.ensureTargetTablesReady:363-383` | target 模板 + target.qs 查表存在性 | 自洽（正确侧基准） |
| 29 | `OrmMigrationOrchestrator.incrementalManager:444-454` | sink = target 模板 + target.qs + 共享 ledgerTableName | sink 侧正确（C3-010 对照侧） |
| 30 | `OrmMigrationOrchestrator.verifier():456-459` | verifier 注入主 jdbcTemplate | 已立案 C3-009④ |
| 31 | `MigrationJobStateStore.load:120-124` | 状态行 jobId 键 + stateQuerySpace | 自洽（C3-010 建议 stateQuerySpace 保留语义） |
| 32 | `ShardChangeEventRouter.route:44-53`（rescale 支路） | shardColumnName null → `caseInsensitiveGet` 返回 null → ERR_MIG_NULL_SHARD_KEY | fail-fast 守卫在位（每事件显式失败使任务 FAILED），排除 |

### rescale 对照面（任务书第 2 项）

relocate 的错位在 rescale 下**不呈现同形态**，逐条对照：

- **C3-009（route qs 硬编码 ""）**：relocate-only。rescale 的 `belongsTo` 两侧经同一 `ShardStrategy.buildSelection`（`ShardStrategy.java:113-135`：`shardSelections()` 与 `selectShard()` 共用）+ 同一 `buildTableName` 派生，`PhysicalTable` 等值结构自洽——rescale 无部分错位形态，与 target-strategy 取值无关。
- **C3-008（connector 名后缀）**：两场景同构，M>1 即触发；rescale 1→M 是自然触发面（已在案）。
- **扩展 C3-001（jobName 丢 qs）**：rescale 在 `table-pattern` 含 `{shard}`（缺省 `{table}_{shard}`）时目标表名互异 → jobName 互异，**免疫**；触发面为 relocate 多源 qs（1:1 表名映射天然同名）与 rescale 退化 `table-pattern="{table}"`（分片只按 qs 拆分）两种形态。
- **C3-011（taskKey 丢 qs）**：同上分布——rescale 常规形态免疫，relocate 多源 qs 确定性触发。
- **C3-012（P0/订阅面）**：两场景的多服务器/多 schema 源拓扑均可触发；单服务器部署（最常见形态）三方标识自然重合，无错位。
- 结论：该错位族的统一根因模式为「**以 tableName 或裸配置名代替完整 PhysicalTable 身份派生容器键/观测键/身份键**」——凡派生处丢掉 querySpace 维度的站点（矩阵 #1/#9/#17/#18/#20/#21）全部在多 querySpace 拓扑下失效，其余站点（#3/#4/#7/#11/#24/#26/#28/#29）因全字段同源派生而自洽。修复模式可统一（完整 PhysicalTable 键单一事实源）。

### 同根扩展（不立案，随 C3-001 修复闭合）

**扩展 C3-001（jobName 容器键覆盖 → relocate 多源 qs 下单次启动即丢句柄，成功路径确定性触发）**
`IncrementalJobManager.startChain:174` 的 `jobName = "orm-migration-incremental-" + jobId + "-" + target.getTableName()` 丢掉 querySpace 维度。relocate 配 `source-query-spaces=["a","b"]`（映射 a→c、b→d）时，`TargetShardMapping.relocate:159-167` 产出目标集 `[(c,tbl),(d,tbl)]`——同名异 qs，两条链在同一 `startFallbackChains` 调用内产生**相同 jobName**，`runningJobs.put`（`:186`）后者覆盖前者：`stopAll`（`:201-206`）只能停链 b，链 a 句柄永久丢失（`startFallbackChains` 返回的 `started` 清单被编排器 `runIncremental:199-203` 丢弃，仅记日志；`RunningChainJob` 无公开 stop，map 是唯一停链通道）。与 C3-001 已载明的「同 jobId 重试覆盖」触发相比，本形态**无需任何错误或重试，成功路径一次启动即确定性触发**，后果同为不可停止的幽灵写者（abort 开写门后链 a 仍向新库 `(c,tbl)` 持续重放）。根因同源（jobName 派生非单射）、修复同源（C3-001 建议的 jobName 唯一化即闭合本形态），按「同根不立案」纪律记为扩展，不新增条目。

## 新增发现

### [C3-011] FullCopyBatchJob 批任务身份键丢 querySpace 维度：relocate 多源 querySpace 下第二张源表复制复用首批的任务行，确定性被批量启动闸门拒绝（错误归因失真）且批状态记录跨表串写

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/FullCopyBatchJob.java:100`（主对象）；`:60-62`（javadoc 契约）；`:93-94`（stateStore 注入面）；`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:205-222`（`(taskName,taskKey)` 查找）、`:131-137`（COMPLETED 启动闸门）、`:79-80`（唯一键注释，证据锚点）
- **证据片段**:
  ```java
  // FullCopyBatchJob.java:100 —— 身份键只含 tableName，丢 querySpace
  context.setTaskKey(jobId + ":" + source.getTableName());
  // :60-62 javadoc 自证意图是「源物理表」完整身份：
  //   taskName = batch-task-name，taskKey = {@code <jobId>:<源物理表>}
  // DaoBatchStateStore.java:214-219 —— 平台按 (taskName,taskKey) 唯一定位任务行
  query.addFilter(FilterBeans.eq(PROP_NAME_taskName, taskName));
  query.addFilter(FilterBeans.eq(PROP_NAME_taskKey, taskKey));
  // :131-132 —— COMPLETED 记录默认禁止再启动
  if (!Boolean.TRUE.equals(context.getAllowStartIfComplete())
          && task.getTaskStatus() == NopBatchDaoConstants.TASK_STATUS_COMPLETED) throw ...
  ```
- **严重程度**: P2（条件确定性：relocate + `source-query-spaces` 数量 >1 + 宿主注入 `DaoBatchStateStore`（javadoc 声明的 classpath 缺省接线）三条件同时成立；失效为响亮失败而非静默数据丢失）
- **并发场景**: 编排调用线程内 `FullCopyBatchJob.run:67-69` 逐源物理表顺序复制；错位是静态身份键派生缺陷，不依赖交错，但通过 nop-batch 状态行的**跨调用持久身份**起作用（第 1 张表的 COMPLETED 行成为第 2 张表的启动前置状态）。
- **触发时序**: relocate 源 qs=["a","b"]（目标 c/d，1:1 表名映射 → 源表集 `[(a,tbl),(b,tbl)]` 同名异 qs）。T1：copyTable(a,tbl) 以 taskKey=`jobId:tbl` 建批任务行，复制完成 → `saveTaskState(true,null)` 置 COMPLETED。T2：copyTable(b,tbl) 构造**完全相同**的 (taskName=`orm-migration-<Entity>`, taskKey=`jobId:tbl`) → `loadExistingTask` 命中 T1 的行 → `startExistingTask` 走到 COMPLETED 闸门 → 抛 `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_COMPLETED` → copyTable 捕获后包装为 `ERR_MIG_FULL_COPY_FAILED` 上抛，FULL 阶段失败。交叠后果：(a) 报错语义完全失真——运维看到的是"已完成的批任务不允许启动"，而 (b,tbl) 从未被复制过；(b) 若 T1 批以 FAILED 收场（如复制中途异常），闸门放行后 T2 复用同一行：execCount/计数器/completedIndex 跨两张物理表串写，批审计面（哪张表被谁复制到哪）被永久污染；(c) `resume()` 对 FULL 相位（`OrmMigrationOrchestrator:157-159`）在多 qs 形态下同样命中该闸门——断点续传承诺在此形态不可达。
- **现状**: javadoc 声明 taskKey 语义为「`<jobId>:<源物理表>`」，实现却只取 `getTableName()`——与 relocate 多源 qs 下「物理表身份 = querySpace + tableName」的模型（`PhysicalTable.equals:77` 全字段等值，本模块其余站点均按全字段）不一致；与第 3 轮 C3-009 同属「tableName-only 派生」族，但站点、失效机制（批持久身份 vs 路由谓词）与修复点均不同。
- **风险**: relocate 多数据源形态（老机房多 datasource 是 relocate 的自然部署形态）在缺省批接线下一进 FULL 即卡死，且错误码指向批量平台闸门而非真实根因，修复定位成本高；无状态注入时不触发（无身份面），故主_path（单 qs / rescale 常规分表名）无恙。
- **建议**: taskKey 改用完整物理表身份（如 `jobId + ":" + source.getQuerySpace() + ":" + source.getTableName()` 或 `PhysicalTable.toString()` 的 `qs:tbl` 形态），并与扩展 C3-001 的 jobName 唯一化共用同一「完整 PhysicalTable 键」单一事实源方法，避免两处派生再次分叉；顺带在 `copyTable` 对 (taskName,taskKey) 重复构造加防御性断言。
- **失效面枚举**（F3 同族清扫：批/容器身份键派生全站点）: ① `FullCopyBatchJob.copyTable:100`（本条主对象）；② `IncrementalJobManager.startChain:174` jobName（同族派生形态，同根扩展 C3-001，不另立案）；③ `DaoBatchStateStore.loadExistingTask:205-222`（平台身份键语义，正确侧基准，不重审平台本体）；④ `MigrationJobStateStore.load:120-124`（jobId 键，自洽，排除）；⑤ `ConsistencyVerifier`（无批身份、按 PhysicalTable 逐表扫，排除）；⑥ runFull 重复调用面——`MigrationPhase:38` FULL→FULL 非法迁移在编排层先行拒绝（响亮、归因正确），不构成本条兄弟面，排除。
- **信心水平**: 确定（身份键派生与平台唯一键/闸门行为均为代码行级可证；触发形态为三个可静态判定的配置/接线条件之合取）
- **误报排除**: 非「有界声明的平台行为」——nop-batch 的 COMPLETED 闸门语义本身正确，缺陷在本模块把两张不同物理表映射到同一身份键，属使用面错位（平台按口径不重审）；非 C3-009 重复——本条报批持久身份维度，非路由谓词；非 C3-001 重复——站点、键（taskKey vs jobName）、失效机制（批状态行 vs 内存 map）均不同。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同根：扩展 C3-001 jobName 丢 qs——按「完整 PhysicalTable 键」单一事实源统一修复）。复核：taskKey 派生（FullCopyBatchJob:100 丢 qs）、javadoc 语义自证（:60-61）、平台 (taskName,taskKey) 唯一键查找与 COMPLETED 闸门（DaoBatchStateStore:214-219/:131-137）、三条件触发面（relocate 多源 qs + stateStore 注入）逐一成立；FAILED 行复用导致 execCount/断点跨表串写的次生面成立。

### [C3-012] P0 位点、CDC 订阅面、源表全集三方标识无对齐校验：P0 钉定取 sourceQuerySpaces[0] 代表性假设，多数据源/多 schema 源拓扑下增量重放静默部分化或种子错位

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:356-361`（主对象）；`IncrementalJobManager.java:91-96`（CDC host/database 单值 + tableIncludeList 跨 qs 全集）、`:190-196`（源表名全集派生）；`SourcePositionPinner.java:70-86`（位点执行在 qs[0] 数据源上）；`docs-for-ai/02-core-guides/data-migration.md`「边界与前置条件」（无单服务器源声明，证据锚点）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:358-360 —— P0 只钉 qs[0]，无任何对齐校验
  String sourceQuerySpace = config.getSourceQuerySpaces().isEmpty()
          ? "" : config.getSourceQuerySpaces().get(0);
  return pinner.pin(jdbcTemplate, sourceQuerySpace, connectorType);
  // IncrementalJobManager.java:91-96 —— CDC 订阅面 = 单一 host + 单一 database
  cdc.setDatabaseHost(stringParam(config, "database-host"));
  cdc.setDatabaseName(stringParam(config, "database-name"));
  cdc.setTableIncludeList(tableIncludeList);   // :96 —— 却装载全部源表名（跨 qs）
  ```
- **严重程度**: P2（条件结构性：源端 querySpace 跨物理服务器/跨 schema 部署时可触发；单服务器部署三方标识自然重合无恙；失效以静默为主）
- **并发场景**: 编排线程（PREPARE 钉 P0 → INCREMENTAL 种子化）与链作业线程（按单一 host 订阅消费）；错位是三方标识的静态不对齐，不依赖交错。
- **触发时序**: relocate 源 qs=["oldA","oldB"] 分属两台物理机（或 rescale 旧策略 `query-space-pattern` 跨两台机/两个 schema）。T1：PREPARE `pinP0` 在 oldA 数据源上 `SHOW MASTER STATUS` 得 file/pos_A，落状态行。T2：runIncremental 以 `incremental.debezium.database-host`（假设指向 oldB 机）构造 connector，`startChain:153-157` 用 (server=mig-jobId-0, offset=**oldA 机的位点**) 种子化 → 引擎从 oldB 机 binlog 的 file/pos_A 位置起读（数值巧合则静默跳过或重复重放——重复被 keyed upsert 吸收、**跳过即丢增量**；越界则 connector 报错属响亮幸面）；同时 include 清单中 oldA 机上的源表在 oldB 机不存在、永不产生事件——**无任何报错**。交叠后果：增量重放静默部分化，VERIFY 期 lag 门禁对"从未订阅的表"无观测量（offset 指纹按订阅面恒静，C3-008 修复后依然如此），唯一拦截是 count/checksum；`verify.count=false` 且未配 checksum 时 verifyResult=passed → confirmSwitch → DONE，目标库静默缺行。
- **现状**: 模块三个标识面各自内部派生正确（pinP0 的 qs→数据源路由、buildCdcConfig 的参数装配、tableIncludeList 的源表名全集），但三者间的对齐前提——「sourceQuerySpaces[0] 的物理服务器 = debezium.database-host 所指服务器 = 全部源表所在服务器/schema」——无一处校验，owner doc「边界与前置条件」（CDC 白名单、binlog 保留窗、物理 JDBC 直读、停写切换）也未声明单服务器源边界；`sourceQuerySpaces` 在 rescale 下甚至不参与源表展开（`TargetShardMapping.rescale` 走 `currentStrategyForEntity` 的旧策略 qs pattern），仅剩 pinP0 这一个消费点，代表性假设完全无守卫。
- **风险**: 多数据源源端拓扑下增量机制的正确性前提（P0 起全序重放）静默失效；失败点后置到 VERIFY 甚至 DONE，与 C3-009 同型的"工作量沉没后暴露"形态。
- **建议**: 三选一或组合：(a) 配置期校验——`sourceQuerySpaces` 数量 >1 时对增量启用作业显式 ERR_MIG_CONFIG_INVALID（声明单服务器源边界）；(b) 按 qs 逐一 pin P0 并按「源表 → qs → server」分组校验与 debezium host 的一致性（host 无法从 JDBC 面直接判定时可要求配置显式声明 `source-server-qs` 对齐键）；(c) 最小防线——owner doc 补「incremental 仅支持单一源服务器/schema」边界声明 + buildCdcConfig 校验 tableIncludeList 的表在 database-name 下存在性。任一方案均应把校验放在 PREPARE（fail-fast 最早点）。
- **失效面枚举**（F3 同族清扫：位点/订阅标识派生全站点）: ① `pinP0:356-361`（本条主对象，qs[0] 代表性假设）；② `SourcePositionPinner.JdbcPositionProvider:59-99`（执行方，per-qs 路由正确，排除）；③ `buildCdcConfig:89-95` host/serverName 派生（链内自洽面第 3 轮⑤已核，本条对象是其与 ①/④ 的跨对象缺失）；④ `sourceTableNames:190-196` tableIncludeList（订阅面装载超出一 host 覆盖，随 ③ 同修）；⑤ `CdcOffsetSeedBuilder.buildSeed:58-64`（消费 P0+serverName，键内自洽，排除）；⑥ `startChain:153-157` 种子注入（正确侧，排除）。
- **信心水平**: 确定（三方无交叉校验为代码可证；「静默部分化」的具体形态依赖部署拓扑与连接器对不匹配表的处理策略，已按条件计价降 P2）
- **误报排除**: 非「已声明边界」——owner doc 边界清单不含单服务器源，配置解析（`MigrationJobConfig.fromMap:110-113`）明文接受 qs 数组；非 C3-008 重复——C3-008 报探测/注册两侧**名**错位，本条报 P0/订阅/源表三方**拓扑身份**无对齐，站点与修复点不同；非平台设施重审——DebeziumConfig 装配在本模块。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现。复核：pinP0 qs[0] 代表性假设（Orchestrator:356-361）、buildCdcConfig 单 host/单 database + tableIncludeList 跨 qs 全集（IncrementalJobManager:91-96/:127）、owner doc「边界与前置条件」确无单服务器源声明（data-migration.md L24-27 仅 CDC 白名单/binlog 保留窗/物理直读/停写切换）、source-query-spaces 必填数组（fromMap L110-113）全部成立；静默部分化依赖多服务器/多 schema 源拓扑，条件性 P2 计价合理。

## 回归面提示 / 维度集外线索

（维度集外线索：本轮无。回归面提示如下，不立案为缺陷。）

| # | 站点 | 行为变化 |
|---|---|---|
| 1 | `FullCopyBatchJob.copyTable:100` taskKey 加 qs 维度（C3-011 修复） | 既有部署中按旧 taskKey 落过的 NopBatchTask 行不再被命中——重试/续跑将新建任务行而非复用；需在修复说明中标注对存量批记录的兼容语义 |
| 2 | 扩展 C3-001 与 C3-011 的联动修复 | jobName 与 taskKey 唯一化应共用「完整 PhysicalTable 键」单一事实源（如 `PhysicalTable` 增加 `toKey()`），两处独立派生会再次分叉 |
| 3 | `TestMigrationPipelineE2E` | 现有用例锚定单 qs 形态；relocate 多源 qs 用例需断言第二张源表确实被复制（copy 计数/目标行数），并断言 M 条链 jobName 互异 |
| 4 | C3-012 修复（对齐校验） | 单库部署为恒真断言、行为不变；多源部署从静默部分化变为 PREPARE 显式 ERR_MIG_CONFIG_INVALID——行为收紧需登记 owner doc |
| 5 | C3-008/009/010 修复联测 | 统一键派生落地后，本矩阵 #1/#9/#17/#18/#20/#21 六个丢维度站点应一次性收敛到同一派生方法；回归面以第 3 轮回归面提示表 1-6 为基础叠加本表 |

## 继续判定

**输出终止信号，C3 维度深挖收敛（第 4 轮为末轮）。** 依据：

1. 本轮新增 2 条（C3-011、C3-012），**均为 P2，零新增 P0/P1**——按「除新 P0/P1 实证外输出终止信号」的收敛判据满足。
2. 标识符对齐矩阵对六目标类及其唯一接线方的 32 个跨对象引用点完成穷举：5 个已立案（C3-008/009/010 及 C3-001/C3-003 关联面）、1 个按同根不立案记为扩展 C3-001、2 个本轮立案（均为条件性 P2、不打开新失效面），其余 24 个站点判定自洽或有 fail-fast 守卫。同族高发根因已收敛为单一可修模式（tableName/裸配置名派生代替完整 PhysicalTable 身份），且六个丢维度站点可用统一键派生方法一次性闭合。
3. 其余 C3 面（探针内部并发、FullCopy 异步边界、PubSub、lettuce 异步链、rpc/tdengine 桥接）经第 2/3 轮复核无新 P0/P1 实证，维持收敛。
4. 建议收尾动作（非新一轮审计）：C3-011/C3-012 与 C3-001/008/009/010 的修复应作为**一个统一键派生方案**设计（矩阵「统一根因模式」节 + 回归面提示表 1-5 为设计输入），落地后做一次定向复核即可。

## 复核结论（WI 2.5 C3 独立复核）

本文件 2 条条目（C3-011/C3-012）逐条回码复核：**保留 2（均 P2，维持原严重度），驳回 0，待定 0**；现状均为新发现。标识符对齐矩阵 32 站点抽验（#9/#17/#21 主对象及 #2/#15/#28 对照侧）与 rescale 对照面、同根扩展 C3-001（jobName 丢 qs）经代码级复核成立——扩展面经本复核确认为 C3-001 维持 P1 的确定性触发依据（relocate 多源 qs 单次成功启动内同名相撞）。终止信号（第 4 轮为末轮）维持有效。判定表与最终保留清单集中见 C3-async-chains.md「维度复核结论」节。
