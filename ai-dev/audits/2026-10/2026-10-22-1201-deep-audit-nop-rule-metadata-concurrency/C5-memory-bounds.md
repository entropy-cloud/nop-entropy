# C5 内存有界性与大数据量 — 初审报告（盲审批次 A + 批次 C）

- **审计单元**: nop-rule（批 A）+ nop-metadata-service entity 包（批 C）
- **维度**: C5 内存有界性/无界物化（并发·异步·资源审计维度集）
- **基线**: `_tmp/cra-baseline-nop-rule-metadata.txt` @ 81a7c228e7（与本仓 HEAD 一致）
- **盲审声明**: 初审轮 1/2。本报告撰写期间未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何已落盘文件；仅依据 live code + 主 agent 提供的基线文本判断。现状标注（新发现/已知未修复）留待复核阶段。只读审计，未修改任何源文件。
- **CL 前缀**: `nop-rule-metadata-CL-0NN`（维度集外线索自 CL-301 起）
- **批次范围**:
  - 批 A = nop-rule 全部（任务书口径 72f/5,221；live 复核 = `_gen/` 外 71f/5,205 + 保留文件 `_NopRuleDaoConstants.java`（16 行）= 72f/5,221，口径吻合）
  - 批 C = nop-metadata-service `service/entity/` 包 42f/7,069 行
- **批 D 割界**: 聚合/连接结果集上界（`CrossDbConfigHolder.maxCrossDbRows` 消费面、`MetaAggregationExecutor`/join 截断层）不在本批；`NopMetaEntityBizModel.normalizeJoinQueryLimit`（L461-470，"正值原样透传，超大值由截断层显式拒绝"）指向的截断层属批 D，本批未审。

---

## 一、无界风险清单（路径 × 增长源 × 声明上界 × 判定）

判定列取值：**立案**（详见发现条目）/ **有界**（核实有界设计，不立案）/ **已声明边界**（代码注释或 owner 裁定明文声明的限制，按方法论"区分缺陷与已声明的边界"不立案，登记备复核）。

### 批 A（nop-rule）

| # | 路径 | 增长源 | 声明上界 | 判定 |
|---|---|---|---|---|
| A1 | `RuleModelCompiler.compileMatrix`（nop-rule-core …/model/compile/RuleModelCompiler.java:139-144）`IEvalAction[rowMax][colMax]` 编译产物矩阵 | 规则矩阵行列数 | `Guard.checkArgument(rowMax < 500)` + `colMax < 500`（编译期 fail-fast，≤499×499 单元） | 有界 |
| A2 | 规则树递归（编译期 `compileTree`/`compileDecider`；加载期 `DaoRuleModelLoader.buildRuleTree`；**每请求执行期** `ExecutableRule.execute`/`RuleDecider.execute`） | 存储的规则节点树深度（DB admin 数据） | 无（对照 A1 矩阵面有硬护栏，树面无任何深度上限） | **立案 → C5-005** |
| A3 | `RuleRuntime.logMessages`/`outputLists`/`outputs`（execute/RuleRuntime.java:41-45,153-158） | 规则节点数 × 输出名数 | 单次请求级 RuleRuntime；上限 = 单规则节点/输出数；`collectLogMessage` 仅在 GraphQL selection 请求时开启 | 有界 |
| A4 | `TraceVarsExecutableRule` trace context（execute/TraceVarsExecutableRule.java） | trace 变量值大小 | 单值截断 200 字符（`sanitizeTraceValue`） | 有界 |
| A5 | 规则模型缓存：`RuleManager.getExecutableRule` 把编译产物 memoize 到 `RuleModel`，模型实例由 `ResourceComponentManager` 全局模型缓存持有 | 不同规则模型数量 | 平台模型缓存按 path 键控（平台级容量/淘汰配置，非本模块面） | 有界（平台缓存承载） |
| A6 | `RuleDslModelLoader.loadObjectFromPath` / `DaoRuleModelLoader.loadObjectFromPath` 全量解析+编译单个规则 | 单规则 XML/modelText 大小 | 单规则定义规模（admin 模型文件） | 有界 |
| A7 | `RuleTableModelParser` Excel 规则表解析（excel/RuleTableModelParser.java 全文） | 上传 xlsx 单 sheet 规模 | 单文件解析 → 模型树；矩阵产物再受 A1 的 500×500 护栏兜底 | 有界 |
| A8 | `NopRuleDefinitionBizModel.importExcelFile`（nop-rule-service …/entity/NopRuleDefinitionBizModel.java:125-144）`ExcelHelper.loadXlsxObject` 全量物化 xlsx | 上传文件大小 | 平台标准导入模式（imp path + IOrmEntityFileStore 临时文件），admin 单文件操作 | 有界（平台标准模式；上界=平台上传限制，本模块未重复设防） |
| A9 | `NopRuleNodeBizModel.predicateLabel` 的 `context.getCache().computeIfAbsent("rule-input-schema:"+ruleId,…)`（nop-rule-service …/entity/NopRuleNodeBizModel.java:42） | 单请求内引用的 distinct 规则数 | `ServiceContextImpl.getCache()` 为请求级 MapCache（ServiceContextImpl.java:137-142），随请求结束回收 | 有界 |
| A10 | `RuleRuntime` 无 svcCtx 时的 `new MapCache<>("rule-rt-cache", false)`（execute/RuleRuntime.java:74） | runtime 生命周期内条目 | per-runtime 实例，随请求回收 | 有界 |

### 批 C（nop-metadata-service entity 包）

| # | 路径 | 增长源 | 声明上界 | 判定 |
|---|---|---|---|---|
| C1 | `NopMetaLineageEdgeQueryAction.buildLineageGraph`（:417-424）全量载入血缘边建图 | 血缘边表行数 | `q.setLimit(maxEdges + 1)` + 超限 fail-fast `ERR_LINEAGE_GRAPH_TOO_LARGE`（DEFAULT_LINEAGE_MAX_EDGES=100,000，NopMetaLineageEdgeBizModel.java:38） | 有界 |
| C2 | `NopMetaLineageEdgeQueryAction.buildTableNameIndex`（:440-448） | 实体表行数 | `setLimit(maxTables + 1)` + fail-fast（DEFAULT 100,000） | 有界 |
| C3 | `deleteMeasureParseEdges`（:491-501）`findAllByQuery` + 逐实体 delete 循环 | 单表自环陈旧边数 | 无显式上限；同文件 `deleteSqlParseEdges`（:476-489）已改 `deleteByQuery` 单语句删除且注释自证 load+delete 循环有害 | **立案 → C5-006** |
| C4 | `extractMeasureLineage`（:310-312）`measureDao.findAllByQuery(mq)` 无 limit | 单表 measure 行数 | 无（键过滤到单表，admin 目录规模） | 有界（键选择性承载，登记备复核） |
| C5 | `NopMetaDataSourceBizModel.syncExternalTables`（:648）→ `ExternalTableStructureReader.read` 全量物化外部 catalog | 外部数据库表数 × 列数（完全在模块控制外） | 无任何 limit/分块/批量旋钮 | **立案 → C5-001** |
| C6 | `migrateDataSourcesCredential`（:425-427）`dao().findAllByQuery` 全量载入数据源行 | 数据源目录行数 | 无 limit/分块；admin 一次性迁移工具 | **立案 → C5-002** |
| C7 | `AutoClassificationProcessor.discoverClassification`（:227-237）全量载入全部 enabled 分类 | 分类表全表行数 | 无；且载入结果仅用于 isEmpty 判断后即丢弃 | **立案 → C5-003** |
| C8 | `NopMetaQualityRuleBizModel.executeQualityRulesForDataSource`（:231-233,242-243）规则 `findAllByQuery` 无 limit + results/errors DTO 全量累积 | 挂载规则数（admin 目录） | 无显式上限 | **立案 → C5-004** |
| C9 | `NopMetaModuleBizModel.readText`（:682-690）`readAllBytes` 全文读入（C5b 基线唯一 full-read 站点） | orm.xml 模型文件大小 | VFS 模型资源（开发工件，非用户上传通道）；内容本身需作为 sourceContent 持久化（delta/full 双存储） | 有界（基线站点核实闭合） |
| C10 | `buildGlobalClassNameToModuleId`（:616-655）三连 `findAllByQuery` 全图加载（modules / full ormModels / full entities） | 全目录模块×实体规模 | **已声明边界**：L614"首版全量加载可接受（元数据目录规模有限）"、L637/L645"infra 边界：全图无界加载…保留 dao 直连（plan 353 MD-1 裁定）" | 已声明边界 |
| C11 | `NopMetaQualityRuleBizModel.findExternalTables`（:322-330）dao 直连全量加载 external 表 | querySpace 下 external 表数 | **已声明边界**：L324"catalog 收集的外部表清单可超 Biz findList 的 max-page-size（1000），保留 dao 直连（plan 353 MD-1 裁定）" | 已声明边界 |
| C12 | `NopMetaDataSourceBizModel.findExternalTables`（:799-805）同名 helper 用 `tableBiz.findList` | 同 C11 | max-page-size（1000）页截断——与 C11 同族双实现、语义相悖 | 有界（截断）→ **CL-302**（截断=静默丢表，F2 形态） |
| C13 | `EXTERNAL_TABLE_UPSERT_LOCKS` static Map（NopMetaDataSourceBizModel.java:1053,1070） | distinct (moduleId,table,schema) 键 | **已声明边界**：L1050"key 集合有界（受元数据规模约束），长期持有可接受；单实例 baseline（R4.3 已裁定）" | 已声明边界 |
| C14 | `checkpointRunLocks`（NopMetaQualityCheckpointBizModel.java:171,203,268） | 并发运行中的 checkpointId | `putIfAbsent` + finally `remove` 配对清理 | 有界 |
| C15 | `LineageTagPropagationProcessor.propagateTags`（:86-94,124-135）递归传播 | 血缘可达节点数 | `MAX_DEPTH=3` + `visited` 集合双重上界 | 有界 |
| C16 | `NopMetaTagLabelBizModel.rejectDuplicateGlossaryTermLabel`（:138-139）/ `NopMetaBusinessDomainBizModel.rejectDuplicateRootDomainName`（:59-60）/ `NopMetaEntityFilterBizModel.validateDefaultUnique`（:116-125）查重守卫 `findAllByQuery` 全载 | 查重键命中行数 | 键选择性极强（守卫不变式即 ≤1~2 行/键；filter 键含 isDefault=true 等） | 有界（全载换 `findFirstByQuery`+id 排除为可选优化，不立案） |
| C17 | `NopMetaQualityCheckpointBizModel.triggerAutoScoring`（:363-395）scoreResults/errors 累积 | affectedTableIds × 1 条目 | 单次 checkpoint 执行级；表清单来自本次执行摘要 | 有界 |
| C18 | `SeedGlossaryData.seedGlossaryTerms`（service/SeedGlossaryData.java）`typeDao.findAll()` + 逐条 save | 语义类型表行数 | 一次性种子（"BuiltIn" 存在即短路返回），冷路径 | 有界 |
| C19 | `NopMetaModuleBizModel.collectModuleIndexedIds`（:198-226）级联删除前 id 收集 | 模块下 ormModel/entity/field 行数 | Biz `findList`（页截断承载） | 有界（截断正确性属其他维度，不展开） |
| C20 | `NopMetaEntityQueryAction.queryPhysicalData/queryExternalData/querySqlData`（:67-129） | 物理表/外部表行数 | 唯一入口 `NopMetaEntityBizModel.queryData` 经 `normalizeQueryLimit`（:445-455，缺省 limit + 可配置 max 上限，MA7.4-03） | 有界 |
| C21 | `importOrmModel`（NopMetaModuleBizModel.java:235-293）full 模型 DOM + delta 模型 + sourceContent 字符串三重物化 | 单 orm.xml 规模 | 单模块模型文件（admin 导入，REQUIRES_NEW 事务内逐实体 save+flush） | 有界（≈3× 单文件放大，登记备复核） |

清单计数：24 行（批 A 10 + 批 C 扩展 11 组/21 条，其中 6 行立案、4 行已声明边界、14 行有界）。

---

## 二、发现条目

### [C5-001] syncExternalTables 外部 catalog 全量物化，无 limit/分块旋钮

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sync/ExternalTableStructureReader.java:52-92`（消费面 `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:645-663`）
- **证据片段**:
  ```java
  // ExternalTableStructureReader.java:58-80
  List<ExternalTableInfo> tables = new ArrayList<>();
  ...
  rs = metaData.getTables(null, schema, "%", TABLE_TYPES);
  while (rs.next()) {
      ...
      readColumns(metaData, columnSchema, tableName, info);   // 每表再全量拉 getColumns
      tables.add(info);                                        // 全部累积后才返回
  }
  // NopMetaDataSourceBizModel.java:648（消费点）
  List<ExternalTableInfo> tables = structureReader.read(conn, metaData, schemaPattern);
  for (ExternalTableInfo table : tables) { upsertExternalTableGuarded(...); }
  ```
- **严重程度**: P2
- **并发场景**: 单 admin mutation（GraphQL/xbiz `syncExternalTables`）持有一条外部 JDBC 连接，在 `withConnection` callback 内同步执行；无并发前提要求。
- **触发时序**: 管理员对仓库级外部数据源（万表级）触发 sync → `read` 遍历 `getTables` 全部行且逐表遍历 `getColumns`（:98-109）全部列 → 所有 `ExternalTableInfo`（表名/schema/remark + 每列 8 字段）在堆中一次性成形后才开始逐表 upsert → 峰值堆占用 = 整个外部 catalog 的对象图（万表×40 列×约 250B ≈ 100MB+ 量级，且 `read` 期间外部连接持续持有）。输入规模完全由外部数据库决定，模块内无 limit、无 schemaPattern 强制、无分批提交之间的解耦。
- **现状**: 扫描结果全量物化为 `List<ExternalTableInfo>` 后才进入逐表 upsert；逐表 upsert 本身已是 per-key 锁 + 独立事务（:1067-1079），全量物化对正确性无贡献，仅为先扫后处理的实现顺序。
- **风险**: 外部 catalog 规模不受本模块任何配置约束，仓库级数据源 sync 的内存峰值与 catalog 大小成正比，可触发长 GC 至 OOM；同批连接在物化阶段即被占用。规模包络未在任何 owner doc 声明（对照：血缘面显式声明了 10 万边包络）。
- **建议**: 流式化——`read` 改为回调/迭代器形态（逐表产出即 upsert，对齐既有 per-table 事务边界），或至少提供表数/列数上限旋钮（对齐血缘面 `maxEdges` fail-fast 先例，超限显式报 `ERR_*_TOO_LARGE`）。
- **失效面枚举**: `ExternalTableStructureReader.read` 消费面唯一 = `syncExternalTables`（NopMetaDataSourceBizModel.java:648）；同族"外部元数据全量物化"兄弟 = `collectCatalog`（:752-789，走 `catalogCollector.collectForTable` 逐表收集，且表清单经 `tableBiz.findList` 页截断——形态不同，不中弹）；`AutoClassificationProcessor`/quality 面无外部 catalog 读取。同族状态逐一登记如上。
- **信心水平**: 很可能
- **误报排除**: 不是"低频 admin 路径合理粗放"的同类误报——该路径的输入是模块外的外部数据库 catalog，规模不受平台数据字典约束；且本单元内血缘面（C1/C2 行）已确立"全量加载前先 setLimit+fail-fast"的既有设计标准，本站点偏离了同模块自己的标准。`schemaPattern` 为可选参数，无法依赖调用方缩小范围。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P2**——ExternalTableStructureReader.java:58-91 全量累积 + :98-110 逐表全列核实，消费面 NopMetaDataSourceBizModel.java:648 唯一入口核实；输入为模块外 catalog（不受平台字典约束）+ 同模块血缘面已有 setLimit+fail-fast 标准，P2 维持。**本维度主登记**（同站点跨批重复 C5-106 并入，见该条）；现状未修复。

### [C5-002] migrateDataSourcesCredential 全表 findAllByQuery 无 limit、无分块

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:425-451`
- **证据片段**:
  ```java
  QueryBean query = new QueryBean();
  query.addOrderField(NopMetaDataSource.PROP_NAME_dataSourceId, true); // 确定性排序（断点续跑收敛）
  List<NopMetaDataSource> rows = dao().findAllByQuery(query);
  int migrated = 0;
  ...
  for (NopMetaDataSource row : rows) {          // 全部行常驻内存，逐行 REQUIRES_NEW
      try { if (Boolean.TRUE.equals(migrateOneRow(row, context))) ... }
  ```
- **严重程度**: P3
- **并发场景**: admin mutation 单调用；行级迁移内部各自 `REQUIRES_NEW` 独立事务（:479），外层列表跨全部行级事务存活。
- **触发时序**: 数据源目录增长到 N 行 → 调用迁移 → 一次 `findAllByQuery` 把 N 个 ORM 实体及其 `connectionConfig` JSON 组件全部载入会话并保持到整批结束 → 峰值内存 = 全表实体 + connectionConfig 解析中间 Map（`parseConnectionConfigMap` 每行至少一次）。
- **现状**: 注释自证设计目标是"断点续跑收敛"（按 dataSourceId 排序），但实现仍是一次性全量快照，未利用排序做分块游标。
- **风险**: 数据源目录为 admin 维护，常态量级小（数十~数百行），故 P3；但该方法是常驻 API 面（非一次性脚本），目录增长后每次调用的内存基照线性上升，且 `failures` 列表无上限叠加。
- **建议**: 按 `dataSourceId` 游标分块（WHERE dataSourceId > lastKey LIMIT k），或至少 `setLimit` + 续跑提示；对齐同模块血缘面 limit+fail-fast 先例。
- **失效面枚举**: 批 C `findAllByQuery` 无过滤性 limit 的全量载入族：NopMetaDataSourceBizModel:427（本发现）；NopMetaQualityRuleBizModel:233（→ C5-004）；AutoClassificationProcessor:229（→ C5-003）、:206/:244（键过滤、有界）；LineageTagPropagationProcessor:114/:121（键过滤、有界）；NopMetaLineageEdgeQueryAction:312（单表键过滤、有界）、:420/:444/:463/:497（有 limit/fail-fast 或键过滤）；NopMetaModuleBizModel:630/:638/:646（已声明边界 C10）；NopMetaTagLabelBizModel:138、NopMetaBusinessDomainBizModel:59、NopMetaEntityFilterBizModel:116（键选择性有界，C16）。
- **信心水平**: 确定
- **误报排除**: 不是"低频管理操作合理粗放"误报——与 C5-001 同理，本单元既有标准是全量加载必须显式声明或设限；此处既无声明也无上限，且是可重复调用的常驻 mutation 面，不是启动期一次性逻辑。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3**——NopMetaDataSourceBizModel.java:428-429 `findAllByQuery` 无 limit、:437 逐行循环、行级 REQUIRES_NEW（:417 javadoc 自证）逐一核实；数据源目录 admin 小表量级，P3 恰当。与 C5-104 为同族异站点（非重复，各自独立立案）；现状未修复。

### [C5-003] AutoClassificationProcessor 全表载入分类仅为 isEmpty 判断，载入结果即弃

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/AutoClassificationProcessor.java:227-238`
- **证据片段**:
  ```java
  QueryBean allClsQuery = new QueryBean();
  allClsQuery.addFilter(FilterBeans.eq(NopMetaClassification.PROP_NAME_disabled, (byte) 0));
  List<NopMetaClassification> allEnabled = clsDao.findAllByQuery(allClsQuery);  // 全表实体载入
  if (allEnabled.isEmpty()) {          // 唯一消费点：布尔判断
      return null;
  }
  // AR-21…无分类绑定时不再任意回退
  LOG.warn("No classification bound to table metaEntityId={} ...");
  return null;                          // 非空分支同样 return null
  ```
- **严重程度**: P3
- **并发场景**: `suggestTags` mutation 单请求路径（per 实体触发），无并发前提。
- **触发时序**: 表无 Manual 标签绑定分类 → 走 fallback 分支 → 载入**全部 enabled 分类实体**（含全部列/JSON 配置组件 `autoClassificationConfig`）→ 仅做 `isEmpty()` 判断 → 无论空/非空都 `return null`。分类目录越大，单次调用的无效物化越大。
- **现状**: 两条分支返回值相同（null），载入的 `allEnabled` 除 size 外无任何消费；`autoClassificationConfig` JSON 列随实体一并物化属纯浪费。
- **风险**: 当前直接浪费 = O(分类表全表) 每次调用；更重要的是这是死代码形态——fallback 已被 AR-21 裁定移除，残留查询应整体删除，否则后续维护者可能误以为该查询承载语义。
- **建议**: 删除 `allEnabled` 查询与空判断（两分支等价），保留 LOG.warn 即可；若需区分"目录无 enabled 分类"与"有分类但未绑定"两种日志语义，用 `findFirstByQuery` 或 count 判断。
- **失效面枚举**: "全量载入仅作布尔判断"族：AutoClassificationProcessor:229（本发现，**全表**载入）；NopMetaTagLabelBizModel:138-139（anyMatch，键选择性有界，C16）；NopMetaBusinessDomainBizModel:59-60（同上，有界）；NopMetaEntityFilterBizModel:116-125（isDefault 键，不变式 ≤1 行，有界）。同族其余三处不中弹的原因均为键选择性极强，唯本站点是无键全表扫描。
- **信心水平**: 确定
- **误报排除**: 不是"防御性代码冗余"类误报——这不是冗余防御，是已被裁定移除的 fallback 语义残留（L233-234 注释自证 AR-21 移除回退），查询本体随语义一并失去消费者。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3**——AutoClassificationProcessor.java:229-236 核实：全表载入后唯一消费点为 `isEmpty()`，空/非空两分支均 `return null`，AR-21 注释在位，死代码形态成立；现状未修复。

### [C5-004] executeQualityRulesForDataSource 规则全量载入 + 结果 DTO 全量累积于单请求

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityRuleBizModel.java:229-243,248-283`
- **证据片段**:
  ```java
  QueryBean ruleQuery = new QueryBean();
  ruleQuery.addFilter(FilterBeans.in(NopMetaQualityRule.PROP_NAME_entityId, tableIds));
  List<NopMetaQualityRule> rules = ruleDao.findAllByQuery(ruleQuery);   // 无 limit
  ...
  List<QualityRuleExecuteResultDTO> results = new ArrayList<>();        // 逐规则累积
  List<ErrorDTO> errors = new ArrayList<>();
  connectionService.withConnection(..., (conn, metaData) -> {
      for (NopMetaQualityRule rule : rules) { ... results.add(buildSingleResultDto(row, judgment)); }
  ```
- **严重程度**: P3
- **并发场景**: 单 admin mutation；整个规则批次在一条外部 JDBC 连接的 callback 内串行执行。
- **触发时序**: querySpace 下 external 表 T 张、挂载规则 R 条 → `in(entityId, tableIds)` 一次载入全部 R 条规则 → 逐规则判定并把含 `details` 的完整判定 DTO 累积进 `results` → 响应体与内存同时持有全部规则判定结果。规则数与表数均无上限旋钮。
- **现状**: 内存累积与响应 payload 同源同量；`tableIds` 来自 `findExternalTables`（dao 直连，已声明边界 C11 行），规则集规模 = 目录规模，无分批/分页执行路径。
- **风险**: 规则目录增长后单次批量的内存与响应体线性放大（含 judgment details 文本）；外部连接持有时长随之拉长（连接治理面见 CL-303）。量级由 admin 目录决定，故 P3。
- **建议**: 为批量执行提供 limit/分批旋钮，或 results 按需裁剪（只回执计数 + 失败清单，成功明细落 `NopMetaQualityResult` 由查询面分页取）。
- **失效面枚举**: 批量执行+结果累积族：NopMetaQualityRuleBizModel.executeQualityRulesForDataSource（本发现）；NopMetaQualityCheckpointBizModel.triggerAutoScoring（:363-395，累积量=单 checkpoint 的 affectedTableIds，有界，C17）；NopMetaDataSourceBizModel.collectCatalog（:755-793，表清单受页截断，有界，C12）；syncExternalTables（errors 累积量=失败表数，物化主面已由 C5-001 立案）。
- **信心水平**: 很可能
- **误报排除**: 不是"低频管理操作合理粗放"误报——同模块血缘面与 quality 检查点面均对"批量结果回执"设了键级或执行级边界，本站点是批量执行面中唯一无任何上限旋钮且结果全量回传的入口；且它是数据质量日常运营面（可反复触发），非一次性迁移工具。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3**——NopMetaQualityRuleBizModel.java:233-234 规则 `findAllByQuery` 无 limit、:248-250 results/errors 累积、:251 withConnection 整批包内执行核实；P3 恰当（admin 目录规模）。与 C5-107（MetaQualityCheckpointExecutor）为同族异站点（"质量批量执行+结果累积族"家族关联单列，不并入）；现状未修复。

### [C5-005] 规则树递归深度无护栏（编译期 + 每请求执行期），与矩阵面 500×500 硬护栏不对称

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/compile/RuleModelCompiler.java:76-92,134-144,160-171`；`nop-rule/nop-rule-dao/src/main/java/io/nop/rule/dao/model/DaoRuleModelLoader.java:163-176,179-211`；`nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/ExecutableRule.java:82-93`；`execute/RuleDecider.java:100-118`
- **证据片段**:
  ```java
  // RuleModelCompiler.java:139-142 —— 矩阵面有硬护栏
  int rowMax = rowDecider.getMaxLeafIndex() + 1;
  int colMax = colDecider.getMaxLeafIndex() + 1;
  Guard.checkArgument(rowMax < 500);
  Guard.checkArgument(colMax < 500);
  // ExecutableRule.java:85-93 —— 树面每请求递归，无深度护栏
  for (IExecutableRule child : children) {
      if (child.execute(ruleRt)) { ... }    // 递归深度 = 存储树深
  }
  ```
- **严重程度**: P3
- **并发场景**: 编译期发生在模型加载（`DaoRuleModelLoader.loadObjectFromPath:77` / `RuleManager.getExecutableRule`）；执行期递归在**每个规则请求**的热路径上（`RuleServiceImpl.executeRule` → `ruleManager.executeRule`）。
- **触发时序**: 规则节点表被构造出深链（parent 链深度 D，admin 数据，无应用层深度校验——保存面 `DaoRuleModelSaver.updateNodes` 同样按递归无界写入）→ 每次执行该规则的请求递归 D 层 → 栈帧 × D；D 超过线程栈余量（万级深链）即 `StackOverflowError`，且因规则被缓存 memoize，**每个请求都稳定复现**。
- **现状**: 同一编译器对矩阵产物有显式 `Guard.checkArgument(< 500)` 规模护栏，对树产物（`compileTree`/`compileDecider`/加载期 `buildRuleTree`/保存期 `updateNodes`/执行期 `execute`）无任何深度上限；护栏不对称是结构性事实，不是理论洁癖。
- **风险**: 深链规则一旦入库，该规则的所有执行请求全部 SOE（无降级路径）；XLang 表达式求值自身栈耗叠加上升。触发需 admin 数据病态构造，故 P3 而非 P1。
- **建议**: 在模型 `init()`（RuleModel.java:113-125 已有 `calcLeafIndex` 全树遍历点）或 `compileRule` 入口加树深/节点数 `Guard.checkArgument` 上限（对齐矩阵 500 先例），加载期 fail-fast。
- **失效面枚举**: 规则树递归族全清单：`DaoRuleModelLoader.buildRuleTree`（:179-211，加载期）、`RuleModelCompiler.compileTree`（:76-92）与 `compileDecider`（:160-171，编译期）、`ExecutableRule.execute`（:82-93）与 `RuleDecider.execute`（:100-118，执行期）、`RuleDecisionTreeModel.calcLeafIndex`（init 期，RuleModel.java:117,122-123）、`DaoRuleModelSaver.updateNodes`（:58-103，保存期）——六处全部无深度护栏；同模块矩阵面两处（compileMatrix:141-142）有护栏；批 C 对照组 `LineageTagPropagationProcessor.propagateEdge`（:131-135）有 `MAX_DEPTH=3` 护栏，证明本仓已有递归护栏惯例。
- **信心水平**: 确定（护栏缺失为直接事实；SOE 触发依赖病态数据，故 P3）
- **误报排除**: 不是" admin 数据不必设防"误报——同文件对矩阵面已用 `Guard` 设防，说明设计标准是"编译产物规模必须有护栏"；树面缺护栏属同族漏修（F3 形态），不是风格偏好。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3**——RuleModelCompiler.java:141-142 矩阵面 `Guard.checkArgument(<500)` 与 compileTree:76-92/compileDecider:160-171 无护栏不对称核实；ExecutableRule.execute:82-93、RuleDecider.execute:100-118 每请求递归、DaoRuleModelLoader.buildRuleTree:179-211、DaoRuleModelSaver.updateNodes:58-103 递归无界逐一核实；触发需病态 admin 数据，P3 恰当；现状未修复。

### [C5-006] deleteMeasureParseEdges 仍用 load+delete 循环，同文件孪生方法已改 deleteByQuery

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:476-501`
- **证据片段**:
  ```java
  // :486-488（sql_parse 通道，已修）
  // deleteByQuery 单语句删除（不装载实体）：陈旧边集合可大（maxEdges 配额 10 万），
  // load+delete 循环既慢也在无事务上下文的直调路径上跨 session 失败
  dao.deleteByQuery(q);
  // :497-500（measure 通道，未修）
  List<NopMetaLineageEdge> stale = dao.findAllByQuery(q);
  for (NopMetaLineageEdge e : stale) {
      dao.deleteEntity(e);
  }
  ```
- **严重程度**: P3
- **并发场景**: `extractMeasureLineage` mutation 单请求内先清后建；直调路径（不经 GraphQL 事务包装）下无事务上下文。
- **触发时序**: 单表累积 N 条 measure 自环陈旧边 → `findAllByQuery` 全量物化 N 个实体 → 逐条 `deleteEntity`；对照注释自证的问题（慢 + 无事务上下文直调路径跨 session 失败），measure 通道承受同一失效模式。
- **现状**: 同一文件、同一"重抽取前对账删除"语义的两个通道，sql_parse 已按 check2 P2-04 改单语句删除并写明理由，measure 通道未同步（F3 修实例不修类别）。
- **风险**: 内存面 = 陈旧自环边全量物化（量级 = 单表 measures×fields，通常小，故 P3）；正确性面 = 注释自证的"无事务上下文直调路径跨 session 失败"在 measure 通道同样成立（该正确性后果归 C6/C3 维度复核，本条按 C5 物化面立案）。
- **建议**: 对齐孪生方法改 `dao.deleteByQuery(q)`（filter 结构已就绪，仅换执行语句）。
- **失效面枚举**: load+delete 循环族（批 C 内）：NopMetaLineageEdgeQueryAction.deleteMeasureParseEdges（:491-501，**本发现，未修**）；deleteSqlParseEdges（:476-489，已修先例）；同族删除面 `NopMetaDataSourceBizModel.deleteByQuery` override（:573-580，findList 为注销凭证所需、语义不同，不中弹）；全仓其余 dao 删除均走 `deleteByQuery`/级联（未发现第三处 load+delete）。
- **信心水平**: 确定
- **误报排除**: 不是"量小不必优化"误报——立案依据不是性能，而是同一文件内两条注释即构成的同族不一致：修复理由已写明（"load+delete 循环既慢也…跨 session 失败"）却未覆盖孪生通道，属 F3 同族漏修的活的证据。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3**——deleteSqlParseEdges:486-488 `deleteByQuery` + 修复理由注释 vs deleteMeasureParseEdges:497-500 `findAllByQuery`+`deleteEntity` 循环，同文件孪生不对称逐字核实；现状未修复。

---

## 三、维度集外线索（CL 附记）

### nop-rule-metadata-CL-301（C2 形态）：RuleManager 编译记忆化的锁对象是平台缓存返回的模型实例

- **位置**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/RuleManager.java:55-65`
- **信号**: `synchronized (ruleModel) { if (ruleModel.getExecutableRule() == null) {compile; set;} }` 的 check-then-act 正确性依赖 `ResourceComponentManager.loadComponentModel(path)` 在并发下返回**同一** RuleModel 实例（否则两线程各锁各的副本、双重编译，且 `setExecutableRule` 各写各副本）。模型缓存失效/重载窗口内的实例换挡也使锁对象漂移（锁-final 检查形态）。强度：低-中（重复编译后果良性，正确性归属平台缓存契约，建议 C2 维度复核时对照 ResourceComponentManager 的实例身份契约）。

### nop-rule-metadata-CL-302（F2/背压形态）：同名 findExternalTables 双实现，collectCatalog 路径静默截断丢表

- **位置**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:798-805`（`tableBiz.findList`，max-page-size 1000 截断）vs `NopMetaQualityRuleBizModel.java:322-330`（注释明言"外部表清单可超 max-page-size（1000），保留 dao 直连（plan 353 MD-1 裁定）"）
- **信号**: 同名同语义 helper 双实现且语义相悖；`collectCatalog`（:752）消费截断后的清单——第 1000 张之后的 external 表**静默不收集**，无告警无错误，"半个成功"形态（F2）。修复方向已被 quality 侧注释裁定（dao 直连）。强度：强，建议独立立案（归属 F2/正确性维度复核）。

### nop-rule-metadata-CL-303（C6 形态）：整批执行在 withConnection callback 内，外部连接持有时长 = 整批时长

- **位置**: `NopMetaDataSourceBizModel.syncExternalTables`（:645-663，含逐表 REQUIRES_NEW 事务）、`collectCatalog`（:758-789）、`NopMetaQualityRuleBizModel.executeQualityRulesForDataSource`（:245-279，含 flushSession/clearSession 与结果写入）
- **信号**: 三个批量面均把 N 次子事务/子操作包在单条外部 JDBC 连接的 callback 内执行；批越大连接占用越久（与 C5-001/C5-004 的物化面同根），外部 DB 侧超时/网络抖动会一次性击穿整批。强度：中，建议 C6 维度复核连接借还与批量边界治理。

---

## 四、口径备注

1. 计数口径：本报告"findAllByQuery 无过滤性 limit 全量载入族"以 grep `findAllByQuery` 在批 C entity 包命中 11 站点逐一判定（见 C5-002 失效面枚举），非子串估算；批 A 递归族以 6 个递归方法逐一登记（C5-005 失效面枚举）。
2. C5a 基线（容器创建粗口径 count=0）与 C5b 基线（full-read 1 站点）均已复核：C5b 站点 = `NopMetaModuleBizModel.readText:684`，判定有界（清单 C9 行）；基线 count=0 与本报告立案不矛盾——立案站点均为"查询全量载入"形态，不在基线 C5a 的 `new ArrayList(...)` 字面口径内。
3. 批 D 割界见头部；`NopMetaEntityBizModel` 的 join/aggregation limit 透传链已核实入口归批 D，本批未重复审。
4. 全部发现为初审线索，未经独立复核；严重度以本领域判级标准给出，复核阶段可升降级。


---

# C5 初审追加（批 B+批 D）

- 审计批次：批 B（nop-metadata-service 主战场 22f：quality 8f + reconciliation 3f + event/sync/profiling/catalog/connection 包）+ 批 D（query 27f + lineage/field/search/sqlview/tableref/contract/datasource/manifest/security + service 根散文件 + nop-metadata-api/core/dao/app 只读抽查）
- 审计基线：commit 81a7c228e7；`_tmp/cra-baseline-nop-rule-metadata.txt`（C5a=0 / C5b full-read=1：NopMetaModuleBizModel）
- 盲审纪律：未读取 ai-dev/audits|plans|bugs|lessons|backlog 下任何已落盘文件；以 live code 为准；只读审计，未修改任何产品文件；`_` 前缀生成物不作发现对象
- 编号段：C5-101 起（批 B+D 保留段）；维度集外线索 CL-401 起

## 无界风险清单（路径 × 增长源 × 声明上界 × 判定）

| # | 路径 | 增长源 | 声明上界 | 由谁保证 | 判定 |
|---|------|--------|----------|----------|------|
| 1 | queryJoinData 同库 entity-entity（MetaJoinExecutor.executeSameDbJoin :221-286） | JOIN 明细行（最坏笛卡尔积） | 用户传入 limit（正值不封顶） | 仅用户自报 limit + DB 实际行数 | **无有效上界（C5-101）** |
| 2 | queryJoinData 同库 table-table（executeSameDbTableJoin :291-357） | 同上 | 同上 | 同上 | **无有效上界（C5-101）** |
| 3 | queryJoinData 跨库三路径（doCrossDbMerge*） | 双侧取数 + 合并行 | maxCrossDbRows=10000（双侧 limit+1 fail-fast + merged 逐行计数闸） | CrossDbJoinMerger.checkSizeLimit/checkMergedSizeLimit | 有界（模块自证防 OOM 标准，反证 #1/#2 不达标） |
| 4 | 跨库内存聚合（CrossDbInMemoryAggregationProcessor + memoryGroupBy :894-940） | 合并行 → 分组 | 输入 ≤ maxCrossDbRows（含内部 limit=null 调用 :62） | 继承 #3 闸门；分组数 ≤ 输入行数 | 有界 |
| 5 | 聚合 SQL 路径（Entity/External/Sql/EntityEntity/MixedSameDb 5 处理器） | GROUP BY 分组基数 | 分组数 + 用户 limit | GROUP BY 语义 + normalizeJoinQueryLimit（limit 恒非 null 但正值不封顶） | 有界（受 C5-101 同口径影响，聚合基数天然压缩） |
| 6 | CrossDbConfigHolder.maxCrossDbRows（:5） | — | 闸门常量 | public static 非 final，无配置接线 | **闸门形态缺陷（C5-102）** |
| 7 | 对账结果 details blob（ReconciliationExecutor.execute :67-121） | rows × 候选 × 候选 properties | 逐行候选 ≤50（AR-13）；无总量闸 | reconFetchLimit(≤10000 缺省) × DEFAULT_CANDIDATE_LIMIT 组合 | **组合上界缺失（C5-103）** |
| 8 | 对账候选池加载（LocalReconciliationProcessor.loadCandidates :93-103） | NopMetaReconciliationEntity 全池 | 无 LIMIT | 无 | **无界 findAll（C5-104）** |
| 9 | search 全量索引构建（NopMetaIndexBuilder 5 处 dao.findAll() :207/232/257/282/307） | 元数据全表（含最大表 NopMetaEntityField） | 无 LIMIT / 无分批 | 无（对照同文件 PURGE_ENUMERATION_LIMIT=10000） | **无界物化（C5-105）** |
| 10 | syncExternalTables 全库结构扫描（ExternalTableStructureReader.read :52-92） | 外部库全表×全列 | 无表数上限 | 无 | 弱上界（C5-106，同步语义要求全量） |
| 11 | checkpoint 规则集解析 + summary 累积（MetaQualityCheckpointExecutor :267-276, :123-203） | mounted 规则数 | 无 LIMIT（in 查询） | 元数据配置表规模 | 弱上界（C5-107） |
| 12 | profiler in-app 排序（MetaEntityProfiler.loadSortedDoubles :361-379） | 列非空值 | MAX_IN_APP_SORT_ROWS=10000，到顶停读+降级 unavailable | 代码闸门（check2 P1-2） | 有界（正面） |
| 13 | quality 规则判定 7 类（MetaQualityRuleExecutor :197-217） | 目标表行数 | 全部单值聚合（COUNT/MAX/单值） | SQL 聚合语义 | 有界（正面） |
| 14 | webhook/notify payload（CheckpointActionDispatcher :247, :498-504） | summary Map | 规则数（元数据规模） | executor 循环 | 有界 |
| 15 | manifest 构建（MetaManifestBuilder.build :52-134） | 模块实体×关系 | 元数据规模 | 调用方按模块加载 | 有界 |
| 16 | queryData 三形态（MetaEntityQueryExecutor + normalizeQueryLimit） | 行数据 | configuredMaxQueryLimit（缺省 10000）封顶 | BizModel 入口 | 有界（正面，与 #1/#2 口径对照） |
| 17 | api DTO items（QueryEntityDataResultDTO 等 30f 抽查） | 服务层传入 | 继承服务层 limit | 服务层 | 有界 |
| 18 | connection/host 解析（MetaDataSourceConnectionProcessor/HostSecurityUtil） | 配置串/URL 长度 | 字符串解析 | 输入形态 | 有界 |

---

## C5-101【P2】queryJoinData 同库原生 JOIN 明细路径无行数硬闸：limit 正值不封顶 + 注释声称的"截断层"在该路径不存在

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityBizModel.java L461-471；nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java L269-285、L342-355
- 证据（NopMetaEntityBizModel.java）：

```java
    private Long normalizeJoinQueryLimit(Long limit) {
        if (limit != null && limit < 0) {
            throw new NopMetadataException(NopMetadataErrors.ERR_PAGINATION_LIMIT_INVALID)
                    .param(NopMetadataErrors.ARG_LIMIT, limit);
        }
        if (limit == null || limit == 0) {
            return (long) DEFAULT_QUERY_LIMIT;
        }
        return limit;   // 正值原样透传，无上限封顶（对照 normalizeQueryLimit L453 的 Math.min(limit, max)）
    }
```

- 证据（MetaJoinExecutor.executeSameDbJoin L269-285）：`SqlPagination.appendLimitOffset(sql, limit, offset, null)` 后 `ctx.orm().executeQuery(sqlObj, null, this::collectRows)`——`collectRows`（L635-645）无行数上限地全量物化结果集；同库 table-table 路径 `executeJdbcQuery`（L648-688）同样 `while (rs.next())` 无闸。
- 现状：同库明细 JOIN 的物化上界 = min(实际 JOIN 行数， 用户 limit)。`normalizeJoinQueryLimit` 对正值不封顶（仅拒绝负值、null→1000），客户端可传 `limit=2147483647`；大表 1:N JOIN（如 10M×10 明细）在该 limit 下将整包物化进堆（行 Map × 列数）。
- 风险：GraphQL 公开入口 `queryJoinData` 可被单请求触发进程级内存峰值；与跨库路径自证的防 OOM 标准（maxCrossDbRows 双重 fail-fast 闸，CrossDbJoinMerger L100-114）不对称。
- 误报排除（为什么这次不是同类误报）：L458-460 注释自称"正值原样透传，超大值由截断层显式拒绝（分析/分页入口语义，保持原裁定）"——但**截断层（`AggregationHelper.truncateCrossDb` 的 `ERR_PAGINATION_LIMIT_TOO_LARGE`）只存在于跨库 merge 路径**（CrossDbJoinMerger.truncate L261-267）；同库原生路径走原生 SQL LIMIT，无任何截断层，注释声明的防线在该路径不存在，属契约漂移而非"已收敛裁定"的重报。"不静默截断"的 API 语义裁定不等于放弃内存上界——同模块 profiler（MAX_IN_APP_SORT_ROWS）与跨库 JOIN（maxCrossDbRows）均在"不伪造"前提下设了显式硬闸，本路径未达同一标准。
- 建议：同库原生 JOIN 路径增加独立行数硬闸（如沿 maxCrossDbRows 先例的常量闸或对 normalizeJoinQueryLimit 正值套用 configuredMaxQueryLimit 封顶），或在 truncate 缺失处补 `ERR_PAGINATION_LIMIT_TOO_LARGE` 等价校验；并修正 L458 注释使"截断层"指称与实际路径一致。
- 信心水平：高（入口归一化/SQL 拼接/物化循环三段证据链完整，跨库对照路径在库）。
- 严重度：P2（公开入口可触发的大内存峰值 + 注释契约漂移；非默认路径即 OOM，需配合大表数据，故不到 P1）。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**保留 P2**——normalizeJoinQueryLimit（NopMetaEntityBizModel.java:458-471）正值透传无封顶、L459 注释自称"超大值由截断层显式拒绝"核实；截断层（AggregationHelper.truncateCrossDb:961-963 的 `ERR_PAGINATION_LIMIT_TOO_LARGE`）经核**仅存在于跨库 merge 收尾**（CrossDbJoinMerger.truncate:259-267 委托），同库 executeSameDbJoin:279-285 走 `appendLimitOffset` 直拼 SQL LIMIT + collectRows:635-645 / executeJdbcQuery:664-680 无行数闸——注释契约漂移成立；`queryJoinData` 为 `@BizQuery`（NopMetaEntityBizModel.java:326）GraphQL 公开面成立。limit 入口确为公开参数、对照闸（maxCrossDbRows 双 fail-fast，CrossDbJoinMerger:100-114）确为跨库专属，P2 维持；现状未修复。

## C5-102【P3】CrossDbConfigHolder.maxCrossDbRows 为 public static 非 final 可变字段：构造期快照与执行期 live 读双口径，且无任何配置接线

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbConfigHolder.java L3-9；MetaJoinExecutor.java L73-75、L404、L436
- 证据（CrossDbConfigHolder.java）：

```java
public class CrossDbConfigHolder {
    /** 跨库拼接单侧结果集行数上限（防 OOM，超限显式失败）。 */
    public static int maxCrossDbRows = 10000;

    private CrossDbConfigHolder() {
    }
}
```

- 证据（MetaJoinExecutor.java）：构造器 `this.crossDbMerger = new CrossDbJoinMerger(CrossDbConfigHolder.maxCrossDbRows)`（L74，**构造期快照**进 final 字段）；同文件 fetch 侧 `q.setLimit(CrossDbConfigHolder.maxCrossDbRows + 1)`（L404）与 `Long fetchLimit = (long) CrossDbConfigHolder.maxCrossDbRows + 1`（L436）为**每次调用 live 读**。
- 现状：main 面 `rg "maxCrossDbRows"` 全部 12 处命中中**写点仅声明处默认值 1 处**（测试面除外）——该"Holder"既无 `@InjectValue`/config setter 接线，也非 final；MetaJoinExecutor 为 BizModel 单例字段（构造一次），一旦任何代码（测试遗留、未来运维开关）运行期改写该 static，同一 BizModel 内 merger 上界（旧快照）与 fetch 上界（新值）撕裂：fetch 允许放入 `新值+1` 行而 merger 按旧值判 `>旧值` 即炸（或反向：超收行进入合并）。
- 风险：闸门语义双口径的潜伏缺陷 + 可变全局闸门暴露面；当前 main 无写点故不构成现行错误。
- 误报排除：非"常量应加 final"的风格挑剔——问题是同一上界在同一执行链内有构造期快照与 live 读两种口径且字段可写，属结构性双源；基线 C1 细化已把 static 可变字段 main 面直接命中计为≈0，本条是"静态可变容器间接形态（holder）"口径，与基线口径一致不重复。
- 建议：`public static final` 化（当前无写点，零行为变化），或补配置接线并让 MetaJoinExecutor 每次执行重建 merger/统一 live 读，消除快照-live 双口径。
- 信心水平：高（全消费面已枚举，口径差异行号钉死）。
- 严重度：P3（潜伏缺陷 + 真实维护成本，无现行错误行为）。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**保留 P3（次登记）**——CrossDbConfigHolder.java:5 `public static int` 非 final、main 面写点仅声明处、MetaJoinExecutor.java:74 构造期快照 vs :404/:436 live 读核实成立。**跨维度同站点**：C1-101（C1 维度，P2）已就该 static 可变字段主登记、C7 侧 CL-601 亦引用（dispatch-log 已备案"maxCrossDbRows 族 C1-101/C5-102/CL-601"去重归合并裁定）；本条为主登记 C1-101 的**次登记**（C5 侧独有价值 = 快照/活读双口径对闸门语义的撕裂），合并时并入 C1-101 工单，判级分歧（P2 vs P3）留 7.1 终裁；现状未修复。

## C5-103【P2】对账结果单行 JSON blob 组合上界缺失：rows(≤fetchLimit) × 50 候选 × 候选 properties 全量序列化进单列

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/ReconciliationExecutor.java L51、L67-121、L150-172
- 证据：

```java
    static final int DEFAULT_CANDIDATE_LIMIT = 50;
    ...
        List<Map<String, Object>> details = new ArrayList<>(total);
        for (int rowIndex = 0; rowIndex < total; rowIndex++) {
            ...
            List<IReconciliationProcessor.ReconciliationCandidate> candidates =
                    reconciliationService.reconcile(value, ..., DEFAULT_CANDIDATE_LIMIT);
            ...
            details.add(toRowDetail(rowIndex, value, status, candidates));
        }
        ...
        result.setDetails(JsonTool.stringify(details));   // 整批序列化为单个 JSON 字符串 → 单 DB 行单列
```

- 现状：逐行候选 limit（AR-13，L47-51 注释"候选序列化进 details JSON 有界"）只约束**单行**；总详情体积 = `reconFetchLimit × 50 候选 × 每候选 entityId/entityName/score/properties(parsed Map)`。调用方 `NopMetaReconciliationConfigBizModel`（entity 包）`reconFetchLimit()` 缺省取 `DEFAULT_MAX_QUERY_LIMIT=10000` 且可配更大。最坏组合：10000 行 × 50 候选 = 50 万候选条目 + properties JSON，单个 String 峰值可达数百 MB（序列化期间原 Map 与产物 String 同时驻留），并作为单行单列落库。
- 风险：GraphQL executeReconciliation 入口一次调用触发的大内存峰值 + NopMetaReconciliationResult 单行膨胀（blob 列）。逐行上界的存在使本条不属"完全无界"，但总量闸缺失——上界仅由两个独立配置的乘积**传递性**构成，无任何一处显式声明或校验。
- 误报排除：非重复报告 AR-13 已收敛项——AR-13 裁定收敛的是"每行候选数上限 50"（单行维度），本条立的是"行数×候选×宽度"组合维度无总量闸，且 L49 注释自己承认"config-driven limit 移入 Non-Blocking Follow-ups"（总量闸被显式延后未落地）。
- 建议：对 `JsonTool.stringify(details)` 前的累计体积设显式上限（超限截断详情行并在 statistics 标记 `detailsTruncated`），或将 details 改为分页/流式消费；至少给 `reconFetchLimit` 乘 `DEFAULT_CANDIDATE_LIMIT` 的组合预期写进声明包络。
- 信心水平：高（乘积两端取值均有行号证据；候选宽度含 properties 为 `LocalReconciliationProcessor.parseProperties` L188-204 实证）。
- 严重度：P2。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**保留缺陷，降级 P2→P3**——组合上界缺失核实成立（DEFAULT_CANDIDATE_LIMIT=50 ReconciliationExecutor.java:47-51；reconFetchLimit 缺省 10000 可配，NopMetaReconciliationConfigBizModel.java:164-168 + @InjectValue:76-78；details 全量 stringify :117）。但可达性核减两点：(1) 最坏组合 10000×50 需**每行 50 个 fuzzy 命中**（EXACT 策略每行 ≤1 候选；fuzzy 需 ≥50 个相似名池条目/行值，现实池密度难饱和），现实最坏 ≈10^4~10^5 条目 = MB 级瞬态；(2) details 列为 **mediumtext precision=16777215**（nop-metadata.orm.xml:2582 实体 + :2602 列）——数百 MB blob 落库不成立（DB 侧 16MB fail-visible 上界），超限堆峰值仅在落库前瞬态存在。行侧包络（10000+truncated 标记）与行侧候选包络（50）双闸均在，缺的是乘积显式闸——降 P3，附激活回升条款（池 fuzzy 饱和或 fetch-limit 大幅上调时回升 P2）；现状未修复。

## C5-104【P3】对账候选池逐行全量 findAllByQuery 无 LIMIT：无界 API 使用点（每行重载全池）

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationProcessor.java L66、L93-103
- 证据：

```java
        List<NopMetaReconciliationEntity> pool = loadCandidates(targetType, identifierSpace);
        ...
    private List<NopMetaReconciliationEntity> loadCandidates(String targetType, String identifierSpace) {
        IEntityDao<NopMetaReconciliationEntity> dao = daoProvider.daoFor(NopMetaReconciliationEntity.class);
        QueryBean q = new QueryBean();
        if (targetType != null && !targetType.isEmpty()) { q.addFilter(...entityType...); }
        if (identifierSpace != null && !identifierSpace.isEmpty()) { q.addFilter(...identifierSpace...); }
        return dao.findAllByQuery(q);   // 无 setLimit，全池载入
    }
```

- 现状：`ReconciliationExecutor.execute` 循环内每行调一次 `reconcile(...)` → 每行一次全池 `findAllByQuery`（无 LIMIT）。单次驻留 = 池大小（transient，可 GC），内存有单点上界缺失但非累积；候选池表（NopMetaReconciliationEntity）为平台自有配置表。
- 风险：候选池大时单次载入内存峰值 × 行数次重复载入（DB 往返 O(rows)，CPU O(rows × pool × len²) fuzzy DP）。
- 误报排除：非 C7 性能项的重报——本条按 C5 口径立案的是"无 LIMIT 查询结果全量载入"这一无界 API 使用点本身；其 N+1 放大效应另记 CL-401（C7 形态），两处不重复计严重度。
- 建议：`reconcile` 对候选池查询加显式 limit/分页；或 executor 层把池加载提到循环外一次载入复用。
- 信心水平：高。
- 严重度：P3。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**保留 P3**——loadCandidates:93-103 无 setLimit 全池载入 + executor 每行调 `reconcile`（ReconciliationExecutor.java:79-82）核实成立；候选池为平台自有配置表，P3 恰当。与 C5-002 为同族异站点（非重复，各自立案）；现状未修复。

## C5-105【P2】search 全量索引构建 5 处 dao.findAll() 无 LIMIT 全量物化：最大元数据表 NopMetaEntityField 整表 + docs 双份驻留，单批 addDocs

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaIndexBuilder.java L207、L232、L257、L282、L307、L135-137
- 证据：

```java
    private List<SearchableDoc> buildMetaEntityFieldDocs(IndexResult result) {
        List<NopMetaEntityField> entities = daoProvider.daoFor(NopMetaEntityField.class).findAll();  // 无 LIMIT
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaEntityField e : entities) { ... docs.add(doc); }
        return docs;
    }
    ...
    if (!docs.isEmpty()) {
        searchEngine.addDocs(topic, docs);   // 全量单批提交
```

- 现状：`buildFullIndex` 5 个类型分支全部 `dao.findAll()` 整表载入（Classification/Tag/GlossaryTerm/MetaEntity/**MetaEntityField**），ORM 实体列表与转换后 docs 列表同时驻留，随后单批 `addDocs`。NopMetaEntityField 是元数据面最大表（实体数×平均列数；2000 实体×50 列 = 10 万行，ORM 实体 + doc 双份 → 数十 MB 起步，且随导入规模线性增长无封顶）。
- 风险：全量重建入口（GraphQL rebuildIndex）在大元数据规模部署下的整表物化峰值；无分批/流式。
- 误报排除：非基线 C5b 重报——基线 C5b full-read 仅命中 NopMetaModuleBizModel 1 站点，本条 5 站点为本次 live 扫描新增（口径差异：基线 grep 模式未覆盖 `dao.findAll()` 裸形态，本条以 `rg "daoFor\(.*\)\.findAll\(\)"` 复现）；且同文件 L32-37 `PURGE_ENUMERATION_LIMIT=10000` 的存在证明模块对"搜索面 10000 显式上界"已有自认标准，build 侧未达同一标准，非"约定大于配置"豁免面。
- 建议：按类型分批（如 1000/批）加载-转换-提交；或对 findAll 加显式上限并超额 fail-visible（沿 PURGE 侧 watch-only 裁定先例但给 build 侧显式化）。
- 信心水平：高（5 站点行号齐全，双份驻留链路完整）。
- 严重度：P2。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**保留缺陷，降级 P2→P3**——5 处 `findAll()` 行号（:207/:232/:257/:282/:307）+ 实体/docs 双份驻留 + :135-137 单批 addDocs 核实成立；但增长源是**模块自有元数据目录**（admin 数据字典），NopMetaEntityField 现实量级 = 实体数×平均列数，常态部署千~万级行、单行 doc 短文本（name/displayName/截断 comment），峰值 MB~数十 MB 级，非 C5-001 型"模块外无控输入"（该型才构成本单元 P2 先例）；入口 rebuildSearchIndex（NopMetaSearchBizModel.java:51-58）为 admin mutation。同文件 PURGE_ENUMERATION_LIMIT=10000 注释自证"真实场景类型文档数远小于上限"（NopMetaIndexBuilder.java:31-35）。降 P3，附激活回升条款（10^5+ 字段级部署时回升 P2）；现状未修复。

## C5-106【P3】syncExternalTables 全库结构扫描无表数上限：getTables("%") + 每表 getColumns 一次性物化

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sync/ExternalTableStructureReader.java L58-91、L94-114
- 证据：

```java
        List<ExternalTableInfo> tables = new ArrayList<>();
        ...
            rs = metaData.getTables(null, schema, "%", TABLE_TYPES);
            while (rs.next()) {
                ...
                readColumns(metaData, columnSchema, tableName, info);   // 每表全列
                tables.add(info);
            }
```

- 现状：同步语义要求全量扫描，但结果列表无任何行数/表数上限与分页；万表级外部库（数仓场景常见）一次调用物化 全表×全列 结构对象。
- 风险：大外部库单次 sync 的内存峰值；属"声明处理大数据量的路径未声明上界"，非现行错误。
- 误报排除：非把"全量同步"当问题——立的是无上限声明与无分批物化，非同步语义本身。
- 建议：加表数上限（超额 fail-visible）或分批提交持久化；至少在 sync 结果 DTO 声明包络。
- 信心水平：中高（物化链路确凿；实际部署规模未知）。
- 严重度：P3。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**并入 C5-001（跨批同站点重复，次登记关闭）**——本条与 C5-001 为同一站点（ExternalTableStructureReader.read :58-91 + syncExternalTables 消费面），批 B+D 复登；缺陷本体已被 C5-001 主登记吸收（P2 判级 + 流式化/上限旋钮建议一致），本条"同步语义要求全量"的弱化视角已在 C5-001 误报排除中覆盖。不单列工单，处置随 C5-001；现状未修复。

## C5-107【P3】checkpoint 规则集解析与 summary 累积随 mounted 规则数线性增长，加载查询无 LIMIT

- 文件：nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointExecutor.java L266-276、L123-203
- 证据：

```java
            if (!existingTableIds.isEmpty()) {
                QueryBean q = new QueryBean();
                q.addFilter(FilterBeans.in(NopMetaQualityRule.PROP_NAME_entityId, new ArrayList<>(existingTableIds)));
                List<NopMetaQualityRule> mounted = ruleDao.findAllByQuery(q);   // 无 LIMIT
                ...
        // execute() 循环：
        results.add(buildResultEntry(rule, judgment));   // 每规则一条全字段 entry 进 summary
```

- 现状：mounted 规则查询无 LIMIT；`summary.results`（每规则 6 键 Map）+ `summary.errors` 随规则数线性累积并整体进 GraphQL 返回与 webhook payload。规则表为管理员配置面，规模可控——弱上界。
- 风险：单 checkpoint 挂载数千规则时 summary Map 峰值 + 响应体放大（GraphQL/JSON 双份）。
- 误报排除：非 C7 单调增长（summary 为请求级，执行完即弃）；非 C2（无共享态）；纯 C5 请求级物化面。
- 建议：summary.results 支持摘要模式（计数 + 可选明细），或规则数上限声明。
- 信心水平：高。
- 严重度：P3。
- 复核状态：已复核（2026-10-22 C5 独立复核）：**保留 P3**——mounted 规则 `findAllByQuery` 无 LIMIT（MetaQualityCheckpointExecutor.java:270-276）+ results/errors 逐规则累积进 summary（:123-145 一带）核实成立；规则表为 admin 配置面，P3 恰当。与 C5-004 为同族异站点（"质量批量执行+结果累积族"家族关联单列，不并入）；现状未修复。

---

## 维度集外线索（CL-401 起，附记不立案）

### CL-401【C7 形态】对账循环 N+1 全池重载 + fuzzy DP 平方复杂度
- 文件：reconciliation/LocalReconciliationProcessor.java L66/L93-103/L144-170 + ReconciliationExecutor.java L76-111
- 线索：每行 `reconcile` 全池 `findAllByQuery` + 每候选 levenshtein O(len²)。rows=10000 × pool=N × len² 的 CPU/DB 往返放大，长时运行劣化形态；与 C5-104 同根，修复时一并处理。

### CL-402【C7 形态】MetaManifestBuilder.findOwnerUniqueId O(relations × entities) 线性反查
- 文件：manifest/MetaManifestBuilder.java L95-117、L195-204
- 线索：每条 relation 遍历全模块实体列表定位 owner；大模块（千实体×千关系）manifest 构建二次方。已有 `localClassToUnique` 索引可复用同模式建 `metaEntityId→uniqueId` 索引。

### CL-403【C1/C2 形态】CheckpointActionDispatcher 类注释自称"无状态"，实际携带 3 个运行期可变 protected 配置字段
- 文件：quality/CheckpointActionDispatcher.java L52、L88-99、L109-125
- 线索：`webhookAllowedHostsCsv`/`webhookTimeoutSeconds`/`followRedirectsEnabled` 为共享实例上的可变实例字段，经 `configureWebhookSsrf`/`configureRedirectPolicy` 运行期可写（javadoc 称 BizModel `@InjectValue` 注入）。单例载体 × 可变配置 = C1 共享可变态口径（非 static 故不在 C1a 直接命中内）；若注入发生在启动期则无害，需 C1 批次核实注入时序与可见性。

### CL-404【C6/C8 形态】webhook 投递时序残余：事务提交前投递，提交失败时已宣告结果未落库
- 文件：quality/CheckpointActionDispatcher.java L37-46（类注释"已知残余（check2 P3-10 暂缓项）"）
- 线索：dispatch 在 store 已 flush、框架事务未提交时执行；注释自认"提交阶段失败下 webhook 宣告的结果行实际未落库"，修复需与 R4.3 运行标记 afterCompletion 释放联动设计——已有登记的暂缓决策点，供 C6/C8 批次核对是否仍 open。

---

## 计数口径附记

- 本批清单行数 18（上表）；发现 7 条：P2×3（C5-101/103/105）、P3×4（C5-102/104/106/107）；CL 线索 4 条（CL-401~404）。
- `maxCrossDbRows` 消费面计数：`rg -n "maxCrossDbRows" --type java`（排除 src/test）= 12 命中（CrossDbConfigHolder 1 写点 + CrossDbJoinMerger 9 + MetaJoinExecutor 3 中合并同类）；NopMetaIndexBuilder/MetaEntityProfiler 另有 2 处注释引用（先例引用，非消费）。main 面赋值写点 = 1（声明默认值）。
- `dao.findAll()` 无界站点计数（本批）：`rg "daoFor\([^)]+\)\.findAll\(\)"` main 面 = NopMetaIndexBuilder 5 + SeedGlossaryData 1（元数据种子表，判定有界）；基线 C5b full-read=1（NopMetaModuleBizModel，不在本批文件集）口径不同未合并。
- holder 模式（`ArrayHolderUtils.newArrayHolder`）站点：MetaJoinExecutor/AggregationHelper/ExternalAggregationProcessor/MixedSameDbJoinAggregationProcessor/MetaEntityQueryExecutor 共 5 站点——均为**方法内局部 out-param**（lambda 回调载体），非共享态、无累积，C5 判定全部有界；与基线"query 聚合处理器 holder 模式 5 站点"对应吻合。

---

# C5 深挖第 2 轮

- **盲审声明**: 深挖轮 2。允许读本维度文件（前轮发现全文）作去重基线；未读取其他维度文件及 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何已落盘文件。以 live code（commit 81a7c228e7）为准；只读审计，未修改任何产品文件。
- **审计单元**: nop-rule + nop-metadata 全单元（合并批，边界扩大核查轮）
- **编号段**: C5-R2-001 起
- **去重基线**: 前轮 13 条（C5-001~006、C5-101~107）+ 两批无界风险清单 42 行。本轮目标 = 不与前轮重复的新发现 + 任务书六项扩大面核查。

## 边界扩大核查总表（六项 × 判定）

| # | 扩大面 | 判定 | 依据 |
|---|---|---|---|
| 1 | lineage 四端点边图重建规模（全图驻留 + 每查询重建 churn） | **立案 ×2**（C5-R2-001 / C5-R2-002） | 见发现条目 |
| 2 | search 索引增量路径（单文档更新是否触发全量） | 有界（核查闭合） | 增量面 = 各实体 BizModel 保存/删除钩子单文档 `addToIndex`/`removeFromIndex`（如 NopMetaEntityBizModel.java:133/:154）；引擎侧 `LuceneSearchEngine.addDocs`（nop-search-lucene …/LuceneSearchEngine.java:832-843）按 `FIELD_ID` **先 deleteDocuments 再 addDocuments**，幂等替换，单文档 alter 不触发全量重建，也无索引膨胀。查询面 `searchMetadata`（NopMetaSearchBizModel.java:62-81）负 limit 显式拒绝、正值封顶 100 |
| 3 | GraphQL selection 深层嵌套字段爆炸（BizModel 返回 Map 嵌套物化） | 有界（核查闭合） | 单元内全部 Map 返回面逐一核对：`executeJoin` 行 Map 为**扁平行**（MetaJoinExecutor.java:635-646 每行一层 LinkedHashMap，无嵌套展开）；`RuleResultBean.outputs` 上限 = 规则输出数（RuleServiceImpl.java:70-74）；`MetaContractChecker.check` 结果上限 = 契约配置的 ruleId 数（MetaContractChecker.java:167-225，findFirst per rule）；manifest content 上限 = 单模块实体+关系（generateManifest 经 Biz findList 页截断承载）。无随 selection 深度放大的嵌套物化路径 |
| 4 | Excel/JSON 导入导出流式 vs 物化 | 前轮已闭（A7/A8/C21），本轮无新面 | nop-rule excel 面 = `importExcelFile`（NopRuleDefinitionBizModel.java:125-144，平台标准上传 + A1 500×500 编译护栏兜底）；metadata 导入面 = `importOrmModel`（前轮 C21 有界登记）。全单元 grep `exportExcel|writeXlsx` main 面 = 0 站点——单元内不存在文件导出面；`ExcelHelper.loadXlsxObject` 为平台全量 DOM 解析（上界 = 平台上传限制，前轮已裁定有界） |
| 5 | profiling 采样行数上界与列基数 | 有界（核查闭合）+ F4 附记 | `loadSortedDoubles` MAX_IN_APP_SORT_ROWS=10000 到顶停读降级（前轮正面项 #12，MetaEntityProfiler.java:361-379）；`topValues` GROUP BY … LIMIT 10（:475-488）；`distribution` 固定 4 桶、`percentiles` 固定 5 键（:395-465）——列基数上界 = 表列数，单列峰值驻留 = 1 × 10000 个装箱 Double（逐列串行，用后即弃）。快照落库面 `appendProfilingResult`（NopMetaEntityBizModel.java:395-405）单行 JSON，体积有界。**F4 附记**：NopMetaProfilingResult/NopMetaCatalog/NopMetaQualityResult/NopMetaModelChangedEvent 四张时序表 append-only 且无保留/TTL 路径（全单元 grep 无对应 deleteByQuery/清理入口）——DB 单调增长非 C5 内存面，按 F4 画像移交 C7 维度核对是否已登记 |
| 6 | sqlview/tableref 解析中间产物的缓存驻留 | 有界（核查闭合） | sqlview/tableref/field/lineage 四包全部解析器（SqlSelectFieldExtractor、SqlColumnLineageExtractor、SqlSourceEntityExtractor、SqlViewFieldTypeInferrer、MetaEntityReferenceResolver、MetaEntityFieldResolver）**无任何 static/实例级缓存**（grep `static|cache|Cache` 逐一核对，仅请求级局部结构如 cteRegistry/aliasMap，随请求回收）；`ensureSqlFieldTypeInferrer`/`ensureTableRefExecutor` 惰性创建的是无状态组件实例（单例复用无状态对象 ≠ 缓存解析产物）。LIMIT 0 类型推断不物化数据行（SqlViewFieldTypeInferrer.java:157-176） |

本轮另以 `grep "findAllByQuery|\.findAll()"` 对全单元 main 面 22 个命中文件逐一重判（前轮清单之外新增核对：MetaEntityFieldResolver 3 站点、MetaDataSourceResolver 3 站点、AggregationHelper 3 站点、JoinFieldResolver/DefaultFilterApplicator/MetaQualityScorer/MetaQualityCheckpointScheduler 各 1 站点、MetaContractChecker findFirstByQuery per rule——全部键过滤/单表/配置表规模有界，不立案）；`while (rs.next())` 循环面复核 MetaCatalogCollector（仅 distinct 计数器，无行累积）与 MixedSameDbJoinAggregationProcessor（SQL GROUP BY + LIMIT）均闭合。

---

### [C5-R2-001] lineage 四查询端点每请求全量重建边图：全图驻留 + 无跨请求复用的对象 churn

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:55-144,417-438`；消费面 `NopMetaLineageEdgeBizModel.java:38,173-195`
- **证据片段**:
  ```java
  // NopMetaLineageEdgeQueryAction.java:417-437（四个端点共用，每次调用重建）
  private LineageGraph buildLineageGraph(IEntityDao<NopMetaLineageEdge> dao) {
      QueryBean q = new QueryBean();
      q.setLimit(maxEdges + 1);                              // 100,001 行
      List<NopMetaLineageEdge> allEdges = dao.findAllByQuery(q);   // 全列实体全量物化
      ...
      Map<String, List<String>> forward = new HashMap<>();
      Map<String, List<String>> reverse = new HashMap<>();
      Map<String, List<NopMetaLineageEdge>> columnForward = new HashMap<>();
      for (NopMetaLineageEdge edge : allEdges) { ... }       // 三重邻接结构重建
  // :55-56（端点侧，每请求触发）
  public List<String> getUpstream(String metaEntityId, IEntityDao<NopMetaLineageEdge> dao) {
      LineageGraph graph = buildLineageGraph(dao);           // 全图 DB 传输 + 重建，用后即弃
  ```
- **严重程度**: P3
- **现状**: `getUpstream`/`getDownstream`/`getLineagePath`/`getImpactAnalysis` 四个公开 `@BizQuery`（BizModel :173-195）每次调用都执行 `buildLineageGraph`：从 DB 全量传输至 `DEFAULT_LINEAGE_MAX_EDGES=100,000` 条边（NopMetaLineageEdgeBizModel.java:38），物化 17 列全列 ORM 实体（含 `transformExpr` precision=1000、`extConfig` json-4000、审计列，orm.xml:1780-1822），再构建 forward/reverse/columnForward 三重 Map 结构（约每边 2~3 个条目），而对单起点的 BFS 通常只消费可达子图的小部分；结果在请求结束即全部丢弃。无任何跨请求 memoize、无列投影、无增量邻接查询。`buildLineageGraph` 调用点共 4 处（:56/:78/:101/:139），逐一登记。
- **风险**: 每查询对象 churn 为 O(总边数)——10 万边包络下单请求分配约 10 万实体 + 20~30 万 Map 条目（百 MB 量级中间产物）+ 10 万行 DB 传输，全部为一次性垃圾；治理 UI 高频血缘查询/影响分析下 GC 压力与 DB 负载随目录规模线性放大。上限存在（超限 fail-fast `ERR_LINEAGE_GRAPH_TOO_LARGE`），不构成无界，故 P3 而非 P2。
- **建议**: (1) 短期——边查询加列投影（仅 sourceEntityId/targetEntityId/sourceColumn/targetColumn 4 列），消除 13 列无用物化；(2) 中期——邻接逐步扩展（`WHERE sourceEntityId IN (...) OR targetEntityId IN (...)` 逐层迭代，天然上限 = 可达子图 + 访问去重）替代全图重建；(3) 或对 LineageGraph 按 lineageEdge 表变更版本做短 TTL memoize（四端点共享，对齐模块内 `queryAction()` 惰性单例的既有形态）。
- **信心水平**: 高（4 个调用点 + 物化链 + 丢弃路径行号齐全）
- **误报排除**: (1) 不与前轮 C1 行重复——前轮核的是"上限存在性"（setLimit+fail-fast，判定有界），本轮按任务书扩大面核的是同一路径的"全图驻留 + 每查询重建物化成本"，正交判定；(2) 非 C9 性能重报——C7 侧已以 CL-604 登记 C9 形态（性能角度），本条以 C5 物化/驻留角度立案，归并由跨报告合并裁定阶段按"根因 + 失效层"口径处理；(3) 非"低频查询合理粗放"误报——四个端点均为公开 GraphQL 查询面（影响分析是治理平台日常操作），且同文件 `deleteSqlParseEdges` 注释自证模块对"大集合全量物化"已有明确成本意识（"陈旧边集合可大（maxEdges 配额 10 万）…load+delete 循环既慢"），本站点未达同一标准。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3**——buildLineageGraph:419-437（setLimit(maxEdges+1)+findAllByQuery 全列实体+三重 Map）与 4 端点每请求重建（:55/:78/:101/:139）核实；10 万边 fail-fast 包络在位故不升 P2；现状未修复。

### [C5-R2-002] lineage 抽取面名称索引与存在性校验全实体全列物化：仅消费 1~2 列却载入含 mediumtext sourceSql 的全行

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:440-456,458-469`；消费点 :173、:237、`NopMetaLineageEdgeBizModel.java:92`；列证据 `nop-metadata/model/nop-metadata.orm.xml:646-648`
- **证据片段**:
  ```java
  // NopMetaLineageEdgeQueryAction.java:440-455
  Map<String, String> buildTableNameIndex(IDaoProvider daoProvider) {
      ...
      List<NopMetaEntity> tables = tableDao.findAllByQuery(q);   // 全列实体，无投影
      ...
      for (NopMetaEntity t : tables) {
          if (t.getTableName() != null) {
              map.putIfAbsent(t.getTableName().toLowerCase(Locale.ROOT), t.getMetaEntityId());  // 仅消费 2 列
          }
      }
  // nop-metadata.orm.xml:646-648（被无谓物化的大列）
  <column code="SOURCE_SQL" displayName="来源SQL" domain="mediumtext" name="sourceSql"
          precision="16777215" propId="33" stdDataType="string" stdSqlType="VARCHAR"
          tagSet="sensitive" i18n-en:displayName="Source Sql"/>
  ```
- **严重程度**: P3
- **现状**: `buildTableNameIndex` 为建 tableName→metaEntityId 二列映射，`findAllByQuery(maxTables+1)` 全量载入 NopMetaEntity 全列 ORM 实体——NopOrm 标准加载无列投影，行内含 `sourceSql` mediumtext 域（precision 16777215，sql 视图实体持有完整 SQL 文本）与 `extConfig` json-4000。同形态站点 `loadExistingTableIds`（:458-469，`recordLineage` :92 消费）：存在性校验载入全列实体仅取 metaEntityId 一列。调用频度：3 个 extract mutation 各一次 nameToId 构建（extractMeasureLineage 走 fieldResolver 不经过此路径），recordLineage 每批一次。
- **风险**: sql 视图密集目录下，每次血缘抽取 mutation 物化 Σ(全部目录实体的 sourceSql 文本 + extConfig) 中间体，仅为取 1~2 列；量级 = 目录实体数 × 单实体大列宽度，受 `maxTables=100,000` fail-fast 兜底故有界，但与 C5-R2-001 同根（无投影/无复用）且与 R2-001 在同一 GraphQL 面叠加（extract 后紧跟 upstream 查询 = 双份全量物化）。
- **建议**: 与 C5-R2-001 一并纳入"lineage 面全量物化"治理：平台 QueryBean 支持列投影时改投影查询（tableName/metaEntityId 两列）；短期可先对 `loadExistingTableIds` 改 `findFirstByQuery` 逐 id 校验（referencedTableIds 为请求批次规模，小集合逐 id 查消除全目录扫描）。
- **信心水平**: 高（物化链路确凿；实际目录中 sql 视图占比未知，不升级严重度）
- **误报排除**: (1) 非前轮 C10/C11 重报——那两处是 `buildGlobalClassNameToModuleId`/quality 侧 `findExternalTables` 的**已声明边界**（L614/L637/L645 注释 + plan 353 MD-1 裁定 dao 直连），本轮立案的两个站点（buildTableNameIndex/loadExistingTableIds）不在任何已登记裁定清单内（前轮 C5-002 失效面枚举将 :444 归入"有 limit/fail-fast"组、:463 归入键过滤组，均为按"行数上限"维度的判定；本条立的是"全列放大（mediumtext）"维度，口径不同）；(2) `buildGlobalClassNameToModuleId`（:616-655）虽同具全列放大，但属 C10 已声明边界范围，本条不重复立案，仅在合并裁定时提示该边界裁定未覆盖"列投影"子问题。
- **复核状态**: 已复核（2026-10-22 C5 独立复核）：**保留 P3（局部修正）**——buildTableNameIndex:442-456 全目录全列载入仅消费 tableName/metaEntityId 两列核实成立；但 loadExistingTableIds:458-469 **已含 `in(metaEntityId, ids)` 过滤（:462）**，行集=请求批次规模，初审"全目录扫描"表述对该站点失真（建议中"逐 id 校验消除全目录扫描"不成立——其已是 id 集查询），该站点的真实残余问题仅为"行内宽列（sourceSql mediumtext，orm.xml:2602 同域形态）随行物化"，修法收敛为列投影一项；缺陷本体成立，P3 维持；现状未修复。

---

## 清扫表（本轮终止信号）

| 检查面 | 站点数 | 判定 | 立案/依据 |
|---|---|---|---|
| lineage 四端点边图重建 | 4 端点 + buildLineageGraph | **立案** | C5-R2-001（物化/churn 维度；性能维度已由 C7 侧 CL-604 以 C9 形态登记） |
| lineage 抽取面名称索引/存在性校验全列物化 | buildTableNameIndex + loadExistingTableIds | **立案** | C5-R2-002 |
| search 索引增量路径 | 16 调用点（6 BizModel 钩子）+ 引擎 addDocs/removeDocs | 有界 | delete-then-add by FIELD_ID 幂等，单文档更新不触发全量重建；searchMetadata limit 封顶 100 |
| GraphQL selection 深层嵌套 Map 物化 | 全单元 Map 返回面（executeJoin/outputs/contract/manifest） | 有界 | 全部扁平行/DTO/配置表规模，无随 selection 深度放大的嵌套物化 |
| Excel/JSON 导入导出 | nop-rule excel 面 + metadata 导入面 + 全单元导出面 | 前轮已闭 | A7/A8/C21；导出面 grep = 0 站点 |
| profiling 采样上界/列基数 | MetaEntityProfiler 全列统计路径 | 有界 | MAX_IN_APP_SORT_ROWS 闸 + topValues LIMIT 10 + 4 桶 distribution；F4 附记移交 C7（四张 append-only 时序表无保留策略） |
| sqlview/tableref 解析产物缓存驻留 | 四包解析器 6 类 | 有界 | 无 static/实例级缓存，全部请求级局部结构 |
| findAllByQuery 全量载入族复扫 | 22 文件逐一重判 | 有界（前轮已立案 13 条之外无新站点） | 新核对站点全部键过滤/单表/配置表规模 |
| load+delete 循环族复扫 | 全单元 | 已闭 | 唯一残留 = 前轮 C5-006（deleteMeasureParseEdges），无第三处 |
| readAllBytes/full-read 复扫 | 全单元 | 已闭 | 唯一站点 = 前轮 C9 行（NopMetaModuleBizModel.readText，有界） |

**本轮终止**：新发现 2 条（C5-R2-001、C5-R2-002，均 P3，同族——lineage 面无投影全量物化）；六项扩大面中 5 项核查闭合有界、1 项立案 2 条。前轮 13 条发现与 42 行清单无重复立案。

---

# C5 维度复核结论（C5 独立复核子代理，2026-10-22）

- **复核范围**: 15 条发现（C5-001~006 / C5-101~107 / C5-R2-001~002）逐条重读引用源码 + 7 条 CL（CL-301~303 / CL-401~404）逐条核实；批次覆盖核验。基线 commit 81a7c228e7 live code，只读复核（仅回填本文件复核状态行 + 追加本节）。
- **逐条判定**（判定 + 一行理由，详证见各发现「复核状态」行）：

| # | 初审判级 | 复核判定 | 理由（file:line 实证锚点） |
|---|---|---|---|
| C5-001 | P2 | **保留 P2（主登记）** | ExternalTableStructureReader.java:58-91 全量累积、:98-110 逐表全列、消费面唯一（NopMetaDataSourceBizModel.java:648）均核实；模块外无控输入 + 同模块血缘面 setLimit+fail-fast 标准在库，P2 成立 |
| C5-002 | P3 | **保留 P3** | NopMetaDataSourceBizModel.java:428-429 无 limit 全载 + :437 逐行循环核实；admin 小表量级，判级恰当 |
| C5-003 | P3 | **保留 P3** | AutoClassificationProcessor.java:229-236 全表载入仅 isEmpty 消费、双分支同返回 null 核实；死代码形态成立 |
| C5-004 | P3 | **保留 P3** | NopMetaQualityRuleBizModel.java:233-234/248-250/251 核实；与 C5-107 同族异站点，家族关联不并入 |
| C5-005 | P3 | **保留 P3** | RuleModelCompiler.java:141-142 矩阵 Guard vs :76-92/:160-171 树递归不对称、ExecutableRule.execute:82-93 / RuleDecider.execute:100-118 / buildRuleTree / updateNodes 六处无护栏核实；P3 恰当（需病态 admin 数据） |
| C5-006 | P3 | **保留 P3** | deleteSqlParseEdges:486-488（deleteByQuery+理由注释）vs deleteMeasureParseEdges:497-500（load+delete）同文件孪生不对称逐字核实 |
| C5-101 | P2 | **保留 P2** | normalizeJoinQueryLimit:NopMetaEntityBizModel.java:458-471 正值透传核实；截断层仅存在于跨库 merge（CrossDbJoinMerger.truncate:259-267→AggregationHelper.truncateCrossDb:961-963），同库路径 collectRows:635-645 / executeJdbcQuery:664-680 无闸——注释契约漂移成立；`queryJoinData` @BizQuery（:326）GraphQL 公开面成立 |
| C5-102 | P3 | **保留 P3（次登记，并入 C1-101）** | CrossDbConfigHolder.java:5 非 final + MetaJoinExecutor.java:74 快照 vs :404/:436 live 读核实；同站点跨维度主登记 = C1-101（P2），CL-601 重复引用；判级分歧留 7.1 |
| C5-103 | P2 | **降级 P2→P3（缺陷保留）** | 组合上界缺失成立（:47-51/：117 + reconFetchLimit 缺省 10000 NopMetaReconciliationConfigBizModel.java:164-168）；但最坏组合需每行 50 fuzzy 命中（EXACT ≤1/行，池密度难饱和）+ details 列 mediumtext 16MB（orm.xml:2582/:2602）使落库侧 fail-visible 有硬界——现实最坏 MB 级瞬态，附激活回升条款 |
| C5-104 | P3 | **保留 P3** | LocalReconciliationProcessor.java:93-103 无 setLimit + 每行重载核实；与 C5-002 同族异站点非重复 |
| C5-105 | P2 | **降级 P2→P3（缺陷保留）** | 5 处 findAll（:207/:232/:257/:282/:307）+ 双份驻留 + 单批 addDocs 核实成立；但增长源为模块自有元数据目录（常态千~万级字段、短文本 doc），非 C5-001 型模块外无控输入，admin mutation 入口；附激活回升条款（10^5+ 字段级部署） |
| C5-106 | P3 | **并入 C5-001（次登记关闭）** | 与 C5-001 完全同站点同缺陷，批 B+D 复登；主登记吸收，不单列工单 |
| C5-107 | P3 | **保留 P3** | MetaQualityCheckpointExecutor.java:270-276 无 LIMIT + summary 累积核实；与 C5-004 同族异站点 |
| C5-R2-001 | P3 | **保留 P3** | buildLineageGraph:419-437 + 4 端点每请求重建（:55/:78/:101/:139）核实；10 万边 fail-fast 包络在位，P3 恰当 |
| C5-R2-002 | P3 | **保留 P3（局部修正）** | buildTableNameIndex:442-456 成立；但 loadExistingTableIds:462 已含 `in(metaEntityId, ids)` 过滤，初审"全目录扫描"表述对该站点失真，宽列（sourceSql mediumtext）物化问题本身成立 |

- **驳回**: 0 条（无证伪驳回）。**降级**: 2 条（C5-103/C5-105，均 P2→P3 缺陷保留 + 激活回升条款）。**并入**: 1 条（C5-106→C5-001）。
- **CL 逐条裁定**: CL-301 **确认**（RuleManager.java:55-65 `synchronized(ruleModel)` check-then-act 依赖平台缓存实例身份，低-中强度，移交 C2 复核口径正确）；CL-302 **确认**（NopMetaDataSourceBizModel.java:798-805 `tableBiz.findList` 截断 vs NopMetaQualityRuleBizModel.java:322-330 dao 直连+plan 353 MD-1 注释，双实现语义相悖核实，F2 形态移交正确性维度）；CL-303 **确认**（三批量面 withConnection 整包核实 :645-663/:758-789/:245-279，移交 C6）；CL-401 **确认**（每行全池重载 + levenshtein O(len²) DP :168-197 核实，与 C5-104 同根同修）；CL-402 **确认**（findOwnerUniqueId:195-204 线性扫描 × 每关系调用 :96，O(R×E) 成立）；CL-403 **确认**（CheckpointActionDispatcher javadoc :52 自称"无状态"与 :88-99 三个 protected 可变配置字段矛盾核实，移交 C1 核注入时序）；CL-404 **确认**（javadoc :40-46"已知残余（check2 P3-10 暂缓项）"逐字核实，移交 C6/C8 核暂缓状态）。7/7 确认，0 驳回。
- **批次覆盖核验**: 批 A（nop-rule，清单 A1-A10 + C5-005）有核查痕迹——compiler/loader/saver/executor/service/excel/cache 全覆盖；批 C（entity 42f，清单 C1-C21 + C5-001~006）有核查痕迹——11 站点 findAllByQuery 族逐一登记；批 B（主战场 22f，18 行清单 + C5-102~107）有核查痕迹——quality/reconciliation/search/sync/profiling/catalog/connection 面面俱到；批 D（query 27f+其余，C5-101 + R2 六项扩大面 + 22 文件 findAllByQuery 复扫 + 清扫表 10 行）有核查痕迹。**缺口登记（不补审）**: (1) nop-metadata-api/core/dao/app 仅"只读抽查"，无独立清单行（已声明扫描深度，dao/app 为生成物+薄壳，可接受）；(2) R2 F4 附记（四张 append-only 时序表）移交 C7——经核 C7-004（P1）已登记该族，移交闭合；(3) 任务书口径 nop-metadata 242f 中 query 27f 由批 D 覆盖、entity 42f 由批 C 覆盖，余量为主战场 22f + 散文件，R2 复扫表已按 22 文件逐一重判闭合。
- **现状标注**: 全部 15 条发现基线 81a7c228e7 live 复核均**未修复**（源码与初审引用一致）；本单元为 CRA WI 5.5 首轮立案，无"已知未修复"前案冲突。

## 最终保留项

- **保留工单 13 条**: **P2×2**（C5-001 syncExternalTables 外部 catalog 全量物化；C5-101 同库 JOIN 无硬闸+注释契约漂移）+ **P3×11**（C5-002 migrateDataSourcesCredential 全表载入、C5-003 discoverClassification 死查询、C5-004 quality 规则批量全载+结果累积、C5-005 规则树递归无护栏、C5-006 deleteMeasureParseEdges 孪生漏修、C5-102 maxCrossDbRows 快照/活读双口径【次登记，并入 C1-101 工单】、C5-103 对账 details 组合上界【降级】、C5-104 候选池无 LIMIT、C5-105 search 5 处 findAll【降级】、C5-107 checkpoint summary 累积、C5-R2-001 lineage 全图每请求重建）。
- **并入关闭 1 条**: C5-R2-002（缺陷保留 P3，但 loadExistingTableIds 半边证据修正，与 C5-R2-001 同族同修，建议合并裁定阶段并入 C5-R2-001 单工单）。**并入关闭 1 条**: C5-106→C5-001。净口径：**15 条发现 → 14 个缺陷实体 → 13 张工单**（C5-R2-002 并入 C5-R2-001 工单、C5-106 并入 C5-001 工单）。
- **最终严重度分布**: P0×0 / P1×0 / **P2×2**（C5-001、C5-101）/ **P3×12**（按 14 个缺陷实体计，含 C5-R2-002；C5-102 并入 C1-101 工单后修复合并执行，严重度按 C1-101 P2 终裁）。
- **跨批/跨维度登记建议**: (1) **maxCrossDbRows 族三登记**——主登记 C1-101（C1 维度，P2），次登记 C5-102（本文件，P3），重复引用 CL-601（C7 侧）；dispatch-log 已备案，去重归合并裁定，建议 C5-102 并入 C1-101 工单并携带快照/活读双口径细节，判级分歧留 7.1。(2) **syncExternalTables 站点双登记**——C5-001 主（P2），C5-106 次关闭。(3) **质量批量执行+结果累积族**——C5-004 与 C5-107 同族异站点，家族关联单列、各自保留。(4) **对账面同根对**——C5-104 与 CL-401 同根同修；C5-R2-001 与 C7 侧 CL-604（C9 形态）同站点异维度，按"根因+失效层"合并。(5) C5-R2-002 与 C5-R2-001 建议并单工单（lineage 面列投影治理）。
- **修复优先序建议**: P2 两条先行（C5-001 流式化/上限旋钮；C5-101 同库 JOIN 硬闸 + L458 注释修正）；P3 中 C5-006（一行换 deleteByQuery）与 C5-003（删死查询）为零风险速修；C5-005 建议对齐矩阵面 Guard 惯例在模型 init 处加树深护栏。
