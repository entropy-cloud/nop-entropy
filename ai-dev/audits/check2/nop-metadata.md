# nop-metadata 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-metadata
- 文件数: 282（src/main/java，其中非 `_gen/`、非 `_` 前缀生成文件 241 个，约 28,290 行）
- 覆盖范围声明: 深读约 68 个核心实现文件（模型导入/delta/manifest 链路：OrmModelImporter、NopMetaModuleBizModel、MetaManifestBuilder；数据源连接与安全链路：NopMetaDataSourceBizModel、MetaDataSourceConnectionProcessor、HostSecurityUtil、ExternalTableStructureReader；查询/聚合全链路：AggregationHelper、MetaAggregationExecutor、MetaJoinExecutor、AggregationContext、CrossDbJoinMerger、FilterToSqlTranslator、SqlPagination、GranularityBucketing、MemoryFilterEvaluator、MemoryOrderByComparator、全部 8 个 AggregationProcessor、全部 Join/CrossDb resolver、MetaTableQueryExecutor、MetaQueryContext、DefaultFilterApplicator；质量链路：MetaQualityRuleExecutor、MetaQualityCheckpointExecutor/Scheduler、CheckpointActionDispatcher、QualityAlertWorkflowProcessor、QualityResultWriter、MetaQualityScorer、NopMetaQualityRuleBizModel、NopMetaQualityCheckpointBizModel、NopMetaQualityScoreBizModel；画像：MetaTableProfiler；血缘：SqlColumnLineageExtractor、NopMetaLineageEdgeQueryAction、NopMetaLineageEdgeBizModel；字段/视图/表引用：ExpressionMeasureValidator、MetaTableFieldResolver、SqlSelectFieldExtractor、SqlViewFieldTypeInferrer、MetaTableReferenceResolver、TableReferenceExecutor；搜索/事件/契约/对账：NopMetaSearchProcessor、NopMetaIndexBuilder、MetaModelChangedEventPublisher、MetaContractChecker、ReconciliationExecutor、LocalReconciliationProcessor、NopMetaReconciliationConfigBizModel；标签自动化：AutoClassificationProcessor、LineageTagPropagationProcessor；以及 app-service.beans.xml 与 model/nop-metadata.orm.xml 交叉验证），约占非生成代码行数 85%。模式扫描（反模式 grep + 抽样阅读）覆盖剩余约 35 个纯 CRUD BizModel、39 个 dao 非生成 entity、39 个 biz 接口、30 个 api DTO、Errors 常量类。未深读区域: nop-metadata-dao 非生成 entity 的逐字段实现、纯 CRUD BizModel 的逐行阅读、api DTO 逐字段、MixedSameDb/ExternalExternal 两个 processor 的 SQL 拼接尾段（复用已深读的 AggregationHelper.buildMixedSameDbJoinSql/buildExternalExternalJoinSql）。本模块 nop-metadata-core 仅有常量类（1 个非生成文件），无编译/合并/差量计算引擎代码——该职责在 NopMetaModuleBizModel（ORM 模型导入 delta/full 双存储）与 MetaManifestBuilder（快照差量图），二者均已全量深读。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 6 |
| P3 | 5 |

## 发现列表

### [P1] ExpressionMeasureValidator 函数黑名单缺失 H2/PG 文件访问函数族，expression 型指标可在 H2 数据源上执行本地文件读取

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/field/ExpressionMeasureValidator.java:103`
- **维度**: D5
- **证据**:
```java
// ExpressionMeasureValidator.java:103-110 —— FUNCTION_BLACKLIST 全集
private static final Set<String> FUNCTION_BLACKLIST = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "SLEEP", "BENCHMARK", "LOAD_FILE", "GET_LOCK", "RELEASE_LOCK",
        "PG_SLEEP", "PG_TERMINATE_BACKEND", "COPY",
        "xp_cmdshell"
)));
```
对照 `MetaQualityRuleExecutor.java:109-114`（custom_sql 沙箱黑名单，AR-04/AR-05/F9 补齐了 FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD/RUNSCRIPT/SCRIPT/SYS_EXEC/PG_READ_FILE/PG_LS_DIR 等文件族）：
```java
"COPY", "PG_READ_FILE", "PG_READ_BINARY_FILE", "PG_LS_DIR", "PG_LS_LOGDIR", "PG_LS_WALDIR",
"PG_STAT_FILE", "SYS_EXEC", "RUNSCRIPT", "SCRIPT",
"FILE_READ", "FILE_WRITE", "BACKUP", "CSVWRITE", "CSVREAD",
```
- **现状**: expression 型 measure（`NopMetaTableMeasure.expression`）经 `validateStatic` 校验后，其 sqlFragment 被 `ExternalAggregationProcessor.loadExternalMeasures:104-110` 包装为 `aggSqlOf(aggFunc, ve.sqlFragment, ...)`，在 `withConnection` 建立的**外部数据源**上原生执行（`buildExternalAggregationSql` → `executeJdbcQuery`）。FUNCTION_CALL token 只查 FUNCTION_BLACKLIST（`scanBlacklist:513-519`，P2-04 裁定注释明确 FUNCTION_CALL 不查 KEYWORD_BLACKLIST），而 `FILE_READ`、`CSVWRITE`、`CSVREAD`、`RUNSCRIPT`、`BACKUP`（H2 内建）与 `PG_READ_FILE` 族（PG）均不在该黑名单内。save-time 校验（`NopMetaTableMeasureBizModel.validateMeasureField` → `saveTimeLoose()`）与 query-time 校验（`singleTableStrict`/`joinStrict`）用同一黑名单，两道防线同源失效。`checkDialectSupported` 仅对 MySQL 拒绝 `DATE_TRUNC`，H2/PG 全放行。
- **风险**: 对 H2 数据源（`ALLOWED_JDBC_PROTOCOLS` 明确允许 `jdbc:h2:file:`/`jdbc:h2:mem:`，见 MetaDataSourceConnectionProcessor.java:71-72），配置 `expression = "FILE_READ('/etc/passwd')"` 的 measure 可通过校验并在 queryAggregation 执行，本地文件内容经聚合结果集外泄；`CSVWRITE` 可写任意本地文件（进程权限内）。与同模块 quality custom_sql 沙箱的防御水位不一致（同一外部数据源账户、同类攻击面，custom_sql 明确拦截这些函数）。
- **建议**: 将 `MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS` 中的 H2 文件读写族（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD/RUNSCRIPT/SCRIPT/SYS_EXEC）与 PG 文件族（PG_READ_FILE/PG_READ_BINARY_FILE/PG_LS_DIR/PG_STAT_FILE 等）补入 `ExpressionMeasureValidator.FUNCTION_BLACKLIST`（两处匹配语义不同，保持两个集合分离但内容对齐文件/副作用族）。
- **误报排除**: 已读 `ExternalAggregationProcessor.execute:67-93` 确认 expression 在外部数据源连接内执行、方言门禁仅查 `SUPPORTED_DIALECTS` 包含 "H2"；已读 `scanBlacklist`/`tokenize:438-443` 确认 `FILE_READ(...)` 被归类为 FUNCTION_CALL 且 FUNCTION_CALL 只查 FUNCTION_BLACKLIST；已读 `MetaQualityRuleExecutor` javadoc（F9 DRY 裁定段）确认文件族是后来只补到 custom_sql 一侧。注：模块对 sql 视图 sourceSql 采取"用户显式提供、已知显式风险、直接执行"的策略（buildTableFromClause 注释），故具备 sql 表创建权限者本可经 sourceSql 达成同类效果——但 expression 通道是平台显式建设的沙箱（安全模型 javadoc 宣称"关键字/函数黑名单"），沙箱缺项仍属缺陷，据此定 P1 而非 P0。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. FUNCTION_BLACKLIST 补入 H2 文件族（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD/RUNSCRIPT/SCRIPT/SYS_EXEC）与 PG 文件族（PG_READ_FILE/PG_READ_BINARY_FILE/PG_LS_DIR/PG_LS_LOGDIR/PG_LS_WALDIR/PG_STAT_FILE，共 14 条，全部大写），与 custom_sql 沙箱文件族水位对齐；附带修正原死条目 `xp_cmdshell`（小写对 toUpperCase 归一的 FUNCTION_CALL token 永不命中）为 `XP_CMDSHELL`。save-time 与 query-time 共用同一黑名单，两道防线同源生效。红验证：TestExpressionMeasureValidator#testCheck2H2FileFamilyFunctionsBlocked（FILE_READ('/etc/passwd') 无异常抛出）+ #testCheck2PgFileFamilyFunctionsBlocked（PG_READ_FILE 无异常抛出）+ #testCheck2XpCmdshellLowercaseInputBlocked（小写输入未命中死条目）；负例 #testCheck2LegitFunctionsStillAllowed 钉死不误伤（ROUND/COALESCE/STDDEV_SAMP/UPPER）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P1] MetaTableProfiler 将整列非空值无上限拉入内存计算 median/percentiles/distribution，大表上可被 GraphQL 入口触发 OOM

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:331`
- **维度**: D6（资源耗尽）/D2
- **证据**:
```java
// loadSortedDoubles: 把整列非空值全部读入 List<Double>，无 LIMIT
private List<Double> loadSortedDoubles(Connection conn, String qualified, String col) throws SQLException {
    String sql = "SELECT " + col + " FROM " + qualified + " WHERE " + col + " IS NOT NULL ORDER BY " + col;
    List<Double> all = new ArrayList<>();
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
        while (rs.next()) {
            double v = rs.getDouble(1);
            if (!rs.wasNull()) { all.add(v); }
        }
    }
    Collections.sort(all);
    return all;
}
```
`collectNumericStats:304` 对每个数值列调用一次；`probeNumeric:221-224` 探测成功的未知类型列再拉一次。
- **现状**: `profileTable`（`NopMetaTableBizModel.profileTable:140-153`，@BizMutation，GraphQL 可达）对任意 external/sql/entity 表剖析时，每个数值列的 median/percentiles/distribution 以"全列拉取 + Java 排序"实现，无任何行数上限、无 maxRows 配置。外部表行数不受平台控制（外部业务库可达千万级）。
- **风险**: 一千万行 double ≈ 数百 MB 堆（Double 装箱 + ArrayList 开销约 40+ 字节/值），多个数值列顺序执行时老年代快速膨胀 → Full GC/OTOH OOM，整个应用进程受影响（非单请求隔离）。对照同模块跨库 JOIN 明确设 `CrossDbConfigHolder.maxCrossDbRows=10000` 防 OOM，profiler 无对等防护。
- **建议**: 为 loadSortedDoubles 增加行数上限（对齐 maxCrossDbRows 先例，超限显式抛 ErrorCode 或降级 `median/percentiles/distribution = null + unavailable=["too-many-rows"]`，模块已有 unavailable 降级机制可复用）；或改用 SQL 侧近似/分位数函数。
- **误报排除**: 已读 `MetaTableProfiler.profile:108-165` 全文确认无行数守卫、无分页；已读 `collectNumericStats/collectStringStats/probeNumeric` 确认调用链；已读 `NopMetaTableBizModel.profileTable` 确认入口无行数限制参数（仅 columns 过滤）；topValues 有 `LIMIT 10` 而本路径无，确认为遗漏而非设计。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. loadSortedDoubles 增加 MAX_IN_APP_SORT_ROWS=10000 上限（对齐 maxCrossDbRows 先例）：读到上限即停止消费行集并返回 null，collectNumericStats 将 median/percentiles/distribution 降级为 null 并 cs.markUnavailable("too-many-rows")（复用模块既有 unavailable 降级机制，不伪造、不整表失败）；min/max/mean/stddev 走 SQL 聚合不受影响。红验证：TestMetaTableProfilerResourceLimits#testMedianDegradedWhenRowsExceedInAppLimit（10001 行表 median=5001.0 非 null，与审计"整列拉取"证据吻合）；边界守护 #testMedianComputedAtExactRowLimit（恰好 10000 行 median=5000.5 正常计算）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P2] MetaTableProfiler 对每列重复执行全表 COUNT(*)，N 列表产生 3N+1 次全表聚合

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:185`
- **维度**: D6
- **证据**:
```java
// profileColumn 内（每列执行一次）:
long totalCount = queryLong(conn, "SELECT COUNT(*) FROM " + fromClause);   // :185
...
long distinctCount = queryLong(conn, "SELECT COUNT(DISTINCT " + col.name + ") FROM " + fromClause); // :191
long nullCount = totalCount - queryLong(conn, "SELECT COUNT(" + col.name + ") FROM " + fromClause); // :193
```
而 `profile:133` 已在表级执行过一次 `snapshot.setRowCount(countRows(conn, fromClause))`。
- **现状**: `SELECT COUNT(*) FROM <表>` 的结果与列无关（同一 fromClause），却在每个列剖析中重复执行；N 列表共执行 N+1 次 COUNT(*) + N 次 COUNT(DISTINCT col) + N 次 COUNT(col)。外部大表上 COUNT(*) 常为全表扫描/索引全扫，成本随列数线性放大。
- **风险**: 大表剖析耗时成倍放大（且与 P1 的整列拉取叠加），外部库负载被无谓放大；无正确性影响。
- **建议**: totalCount 提升到 profile() 表级计算一次并传入 profileColumn；如可行可将 COUNT(col)/COUNT(DISTINCT col) 与 emptyCount 合并为单条 `SELECT COUNT(col), COUNT(DISTINCT col), SUM(CASE WHEN col='' THEN 1 ELSE 0 END) FROM ...`。
- **误报排除**: 已读 profile/profileColumn 全文确认 totalCount 循环内重复且 fromClause 不变；确认 profileColumn 无缓存传递参数。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. profileColumn 删除每列的 `SELECT COUNT(*)`，totalCount 由 profile() 表级 countRows 计算一次传入（同一 fromClause，结果恒等价）；SQL 次数从 2N+1 类降为 N+1 类（每表恰一次 COUNT(*)）。红验证：TestMetaTableProfilerResourceLimits#testCountStarExecutedExactlyOncePerTable（2 列表 COUNT(*) 执行 3 次 ≠ 1，Statement 代理计数与审计 N+1 证据吻合）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P2] sql_parse 血缘重复抽取不清理陈旧边，sourceSql 变更后血缘图永久包含过期边

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:252`
- **维度**: D1
- **证据**:
```java
// extractColumnLineageFromSql:252-287 —— 仅按 existingEdgeMap 增量插入/更新 transformType，无删除
Map<List<String>, NopMetaLineageEdge> existingEdgeMap = batchLoadExistingColumnParseEdgeMap(resolvedSourceIds, targetId, dao);
...
NopMetaLineageEdge existing = existingEdgeMap.get(key);
if (existing == null && seenKeys.add(key)) { ... toSave.add(edge); }
else if (existing != null && !c.getTransformType().equals(existing.getTransformType())) {
    existing.setTransformType(c.getTransformType()); toUpdate.add(existing);
}
```
对照 `extractMeasureLineage:306` 的先清后建：`deleteMeasureParseEdges(targetId, dao);`（:508-518 按 lineageSource=measure_parse 删除全部旧边）。表级 `extractLineageFromSql:186-197` 同样只对 candidateSourceIds 做存在性跳过、不删除已不存在的源表边。
- **现状**: 同一 metaTableId 修改 sourceSql 后重新执行 `extractColumnLineageFromSql`/`extractLineageFromSql`，旧 SQL 产生的 sql_parse 边（列不再被引用/源表已移出）不会被删除，且 DB UK `(sourceTableId,sourceColumn,targetTableId,targetColumn)` 只防重复插入。血缘边表为 append-only 语义的只有手动 recordLineage；sql_parse 通道语义上是"从当前 SQL 重解析"，却无对账删除。
- **风险**: getUpstream/getDownstream/getImpactAnalysis 的血缘图随 SQL 演进累积过期边（且 buildLineageGraph 是全量加载，过期边还会消耗 maxEdges=100000 配额），影响分析结果失真——对依赖血缘做影响面评估的用户是错误数据。
- **建议**: 对齐 measure 路径：重抽取前删除该 targetTableId 下 `lineageSource=sql_parse` 的全部旧边再插入（同事务），或按本次解析结果与存量做差集删除。
- **误报排除**: 已通读 NopMetaLineageEdgeQueryAction 全文确认除 deleteMeasureParseEdges 外无任何 sql_parse 删除路径；已读 NopMetaLineageEdgeBizModel 确认无清理入口；已核对 orm.xml 中 lineage UK 定义（model/nop-metadata.orm.xml:1941）确认 UK 不阻止陈旧行留存。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 对齐 measure 路径先清后建：新增 deleteSqlParseEdges(targetTableId, dao, columnLevel)（deleteByQuery 单语句删除，不装载实体——陈旧边集可达 maxEdges 配额量级），extractColumnLineageFromSql 重抽取前清列级通道（sourceColumn 非空）、extractLineageFromSql 清表级通道（sourceColumn IS NULL），两通道互不误删（可独立重抽取）；原增量 update transformType 逻辑被"删除重插"覆盖（语义更强），同批 seenKeys 去重保留。红验证：TestSqlParseLineageStaleEdgeCleanup#testColumnLevelStaleEdgesRemovedAfterSourceSqlChange（sourceSql 换源后旧边 expected 0 but was 2）+ #testTableLevelStaleEdgesRemovedAfterSourceSqlChange（表级同形态）+ #testChannelIsolationBetweenTableAndColumnLevel（通道隔离守护）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P2] 模块版本号/外部系统模块的 read-then-write 无并发防护，并发触发 DB 唯一约束冲突使整批操作失败

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:991`（ensureExternalSystemModule）；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java:677`（computeNextModuleVersion）
- **维度**: D3
- **证据**:
```java
// NopMetaDataSourceBizModel.ensureExternalSystemModule —— find-then-insert，无锁/无 UK 兜底重试
NopMetaModule module = moduleDao.findFirstByQuery(query);      // :995
if (module != null) { return module.getMetaModuleId(); }
module = moduleDao.newEntity();
module.setModuleId(EXTERNAL_MODULE_ID);
module.setModuleVersion(1L);                                    // :1004
...
moduleDao.saveEntity(module);
orm().flushSession();                                           // :1008 并发第二方在此抛 UK 冲突
```
`computeNextModuleVersion`（NopMetaModuleBizModel:682-689）与 `computeNextManifestVersion`（:667-675）同为"查最大版本+1"模式。
- **现状**: 两个并发 `syncExternalTables`（不同 dataSourceId，或与并发 importOrmModel 交错）同时进入 ensureExternalSystemModule：双方 findFirstByQuery 均为 null（对方尚未提交），双方 insert `(nop/meta-external, version=1)`，后者 flush 时命中 `UK_NOP_META_MODULE_ID_VER` 抛异常——由于 ensureExternalSystemModule 不在任何 per-key 锁/REQUIRES_NEW 隔离内（对照 `upsertExternalTableGuarded:974-985` 专门为同类 NULL-schema 竞态做了 per-key 锁），异常向上传播使整个 syncExternalTables 失败。同理并发 importOrmModel 同一 appId 时 moduleVersion 撞号 → UK 冲突。
- **风险**: 并发窗口小但现实存在（多管理员同时触发同步/导入、cron 与手动交叉）；失败为 fail-loud 的 DB 约束错误而非数据损坏（UK 兜底），用户体验为"莫名唯一约束异常"。
- **建议**: ensureExternalSystemModule 复用 EXTERNAL_TABLE_UPSERT_LOCKS 的 per-key 锁 + REQUIRES_NEW 模式，或捕获 UK 冲突后重读返回既有模块；版本号递增路径可同样加锁或改用 `INSERT ... SELECT max+1` 语义。
- **误报排除**: 已读 syncExternalTables 全文确认 ensureExternalSystemModule 在 upsert 锁之外调用；已核对 orm.xml 确认 UK(moduleId,moduleVersion) 存在（:300-303）、moduleId 无单列 UK；已读 upsertExternalTableGuarded 注释确认模块对 NULL 并发竞态有明确防护先例，本处属遗漏。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（外部系统模块路径；版本号路径子项暂缓）. ensureExternalSystemModule 复用 upsertExternalTableGuarded 先例：EXTERNAL_MODULE_ENSURE_LOCK 单键锁（固定 moduleId=nop/meta-external，单例锁等效 per-key）+ REQUIRES_NEW 独立事务内 find→insert→flush→commit，后到线程 find 可见已提交行直接复用，消除并发 syncExternalTables 的 UK_NOP_META_MODULE_ID_VER 冲突致整批失败。暂缓子项（决策点）：computeNextModuleVersion/computeNextManifestVersion 的"查最大版本+1"竞态——正确修复需 per-appId 锁覆盖整个 importOrmModel（含框架在方法外提交的 commit 边界）或改 DB 侧原子递增语义，涉及导入事务编排重设计；现状 UK 兜底 fail-loud 无数据损坏（并发窗口小），如实施建议与"锁跨 commit"先例（upsertExternalTableGuarded javadoc 路径 C' 裁定）联动评审。免红理由：竞态窗口概率性、无法确定性红测；修复形态与已被既有并发语义测试族（R4.3 checkpoint 并发 + 多 schema upsert 测试）验证的 per-key 锁 + REQUIRES_NEW 先例完全一致，syncExternalTables 全路径经模块全量测试回归。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P2] NopMetaTable/NopMetaEntity delete 中自身的 removeFromIndex 无异常保护，索引清理失败会导致"DB 未删、索引已删"的分裂

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:135`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityBizModel.java:48`
- **维度**: D1（三态一致性）/D4
- **证据**:
```java
// NopMetaTableBizModel.delete:127-136
boolean deleted = super.delete(id, context);        // BizMutation 事务内
... eventPublisher.publishEventWithSnapshots(...);  // :129-134
searchService.removeFromIndex("MetaTable", id);     // :135 无 try/catch
return deleted;
```
对照同文件族已有的防护形态（NopMetaEntityBizModel.safeRemoveFromIndex:69-77，仅包裹子字段清理；NopMetaModuleBizModel.safeRemoveFromIndex:323-330）。`NopMetaSearchProcessor.removeFromIndex:399-417` 默认 fail-closed（searchIndexFailOpen=false 时抛 ERR_SEARCH_INDEX_REMOVE_FAILED）。
- **现状**: delete 在 BizMutation 事务内：removeFromIndex 先于事务提交执行且不可回滚；若引擎此时抛异常（默认 fail-closed），事务回滚 → DB 行仍存在，但索引文档已被 removeDocs 删除（或引擎内部半途状态），出现"实体存在却搜不到"的索引缺失。同类问题存在于 NopMetaEntityBizModel.delete:48 的主实体清理（仅字段级有 safeRemove 保护）。
- **风险**: 搜索引擎瞬时故障（文件锁、磁盘满）与 delete 操作相交时产生持久索引缺失；与模块在 importOrmModel 中专门解决的"索引/DB 三态一致"目标相悖。
- **建议**: 主实体清理与子实体清理统一走 safeRemoveFromIndex 形态（best-effort + WARN），或把 delete 的索引清理移到事务提交后（onAfterCommit）执行。
- **误报排除**: 已读 NopMetaSearchProcessor 确认默认抛异常（非吞异常）；已读 NopMetaEntityBizModel.delete 全文确认字段级有 catch 而主实体级无；已读 NopMetaTableBizModel.delete 确认无任何包裹。save() 路径的 addToIndex（:120）在 super.save 成功后、方法返回前调用，若 addToIndex 抛异常事务回滚但索引文档已写入（幽灵文档）——与 importOrmModel 的反向清理对账（indexImportedDocs:289-321）不对称，属同族问题，一并归入本条。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（delete 主实体路径；save() addToIndex 幽灵文档子项暂缓）. NopMetaTableBizModel.delete 与 NopMetaEntityBizModel.delete 的主实体 removeFromIndex 统一走 safeRemoveFromIndex 形态（best-effort + WARN，对齐 NopMetaModuleBizModel 先例）——索引清理失败不再回滚 DB 删除，消除"实体留存、索引已删"分裂。暂缓子项（决策点）：save() 路径 addToIndex 的幽灵文档——正确修复需把索引写入移到事务提交后（onAfterCommit）或引入与 importOrmModel indexImportedDocs 对等的反向清理对账，两者均改变 save 事务语义/新增对账机制，需独立设计评审（且 addToIndex 侧已有 ERROR 日志可见信号）。红验证：TestDeleteIndexFailureIsolation#testTableDeleteSurvivesIndexRemovalFailure / #testEntityDeleteSurvivesIndexRemovalFailure（经 primary 测试 bean ThrowingSearchProcessor 注入 fail-closed 异常，未修复时 delete mutation hasError=true 且 DB 行回滚留存）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P2] executeReconciliation 经 queryTableData 静默截断为前 1000 行，对账统计在大表上失真且无截断标记

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationConfigBizModel.java:119`
- **维度**: D1/D8
- **证据**:
```java
// executeReconciliation:116-120 —— limit=null 触发 normalizeQueryLimit 缺省 1000
items = tableBizModel.queryTableData(metaTableId, null, null, null, null, context).getItems();
```
`NopMetaTableBizModel.normalizeQueryLimit:406-416`：`limit == null || limit == 0 → DEFAULT_QUERY_LIMIT（1000）`。
- **现状**: 对账执行器 ReconciliationExecutor 把传入 rows 当作全量数据处理并落盘 `statistics.totalRows/matchedRows/matchRate`（ReconciliationExecutor:597-666），而取数被 queryTableData 的防 OOM 缺省 limit 静默截断到 1000 行；结果行与 statistics 均无任何"被截断"标记。>1000 行的目标表上 matchRate 只反映前 1000 行。
- **风险**: 对账结果作为数据治理依据（statistics 持久化到 NopMetaReconciliationResult）时系统性失真，且无告警信号；跨 1000 行边界时同一配置前后两次执行结果不可比。
- **建议**: executeReconciliation 显式传入与配置匹配的 limit（或分页拉全量/设对账专用上限），至少在 details 中记录 fetchedLimit 与 truncated 标记。
- **误报排除**: 已读 normalizeQueryLimit/normalizeJoinQueryLimit 全文确认 null → 1000 缺省；已读 ReconciliationExecutor.execute 确认无截断检测；已读 executeReconciliation 全文确认调用点传 null。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. executeReconciliation 显式传入对账取数上限（新配置 `nop.metadata.reconciliation.fetch-limit`，默认对齐 queryTableData 上限 DEFAULT_MAX_QUERY_LIMIT=10000，不再走 null→1000 静默缺省路径），并在持久化前向 statistics 追加 `fetchedLimit` 与 `truncated`（items 达到取数上限即保守置 true——无法区分是否还有更多行，fail-visible）。红验证：TestReconciliationFetchLimitMarker#testFetchLimitRecordedWhenBelowLimit / #testTruncationMarkedWhenRowsExceedFetchLimit（未修复时 statistics 无 fetchedLimit 键，断言 "must record fetchedLimit (was: null)" 失败；该类经 @NopTestProperty 把 fetch-limit 收紧为 5 使截断边界可测，7 行表 totalRows 断言 5≠7 双重锚定）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P2] JDBC 白名单允许 jdbc:h2:file: 且无路径约束，数据源管理员可在进程权限内任意路径创建/读写 H2 数据库文件

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:71`
- **维度**: D5
- **证据**:
```java
private static final Set<String> ALLOWED_JDBC_PROTOCOLS = new HashSet<>(Arrays.asList(
        "jdbc:mysql:", "jdbc:postgresql:", "jdbc:h2:mem:", "jdbc:h2:file:"));
...
// validateJdbcUrl: extractHosts 对无 "://" 的 jdbc:h2:file: 直接返回空列表（:568-572），
// 主机白名单/SSRF 校验整体跳过；DANGEROUS_URL_TOKENS 拦截 INIT=/RUNSCRIPT 等参数，但不约束文件路径
```
- **现状**: AR-02 白名单有意放行 `jdbc:h2:file:`（本地文件模式），`jdbc:h2:file:/any/path/db` 可指向文件系统任意位置（相对路径相对进程 CWD）：不存在则创建（H2 默认 `;IFEXISTS` 未强制），存在则打开读写。INIT/RUNSCRIPT/FILE_READ 等 URL 参数侧已被 DANGEROUS_URL_TOKENS 拦截，但文件库本身的建库/读写不受限。
- **风险**: 持有 NopMetaDataSource 创建权限的账号可在服务器任意可写路径落盘 DB 文件（结合 P1 的 expression 通道或 sql 视图 sourceSql 可读写其内容）；对多租户部署是本地文件系统面的越权。属纵深缺口而非直接 RCE（触发需数据源管理权限）。
- **建议**: 对 `jdbc:h2:file:` 增加路径前缀白名单配置（如仅允许 `${nop.metadata.datasource.h2-dir}` 下），并强制 `;IFEXISTS=TRUE` 语义（不自动创建）；或在文档中明确该通道权限边界并把 h2:file 从默认白名单降为显式配置开启。
- **误报排除**: 已读 validateJdbcUrl/extractHosts 全文确认 h2:file 无主机段、路径不校验；已读 DANGEROUS_URL_TOKENS 确认参数级防护存在但与路径无关；已读 AR-02 注释确认 h2:file 放行是有意裁定，本条按"有意的白名单 + 缺路径约束"定 P2 而非 P1。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 按审计建议"降为显式配置开启"：新增 `nop.metadata.datasource.h2-file-allowed-dirs` 配置（绝对目录前缀白名单），默认空 = 整体拒绝 jdbc:h2:file:（fail-closed，对齐 F2/F7 先例；reason 指引配置键）；配置后路径需绝对、不含 ".." 遍历段、落在允许目录的路径边界内（`/data/h2dbs-evil` 不是 `/data/h2dbs` 子路径）。已核对 nop-metadata app/deploy 种子与 nop-entropy-e2e 均无 h2:file 使用，默认拒绝无既有部署回归面。注意：这是有意的安全收紧（升级后存量 h2:file 数据源需配置 allowed-dirs 才可用）。红验证：TestH2FileProtocolPathAllowList#testH2FileBlockedByDefaultWithoutConfig（未修复时 jdbc:h2:file:/data/meta/db 无异常抛出，与审计"无路径约束"证据吻合）+ #testH2FileAllowedOnlyWithinConfiguredDirs / #testH2FilePathTraversalAndRelativePathsDenied（配置面红形态=新增配置字段 NoSuchFieldException，属新增配置面预期）+ #testH2MemUnaffected（mem 模式守护）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P3] 多处 SQL 构建硬编码 dialect=null（H2 语义），MySQL 数据源上 offset-only 分页为潜在非法 SQL（当前入口不可达）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java:292`、`:364`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/EntityAggregationProcessor.java:146`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/EntityEntityJoinAggregationProcessor.java:415`
- **维度**: D1（边界条件）
- **证据**:
```java
// MetaJoinExecutor.executeSameDbTableJoin:364 —— 外部数据源连接，却固定 H2 语义拼 LIMIT/OFFSET
SqlPagination.appendLimitOffset(sql, limit, offset, null);   // dialect=null → offset-only 时仅拼 " OFFSET ?"
```
SqlPagination javadoc 明确 MySQL 不允许 OFFSET without LIMIT（需 18446744073709551615 占位）。
- **现状**: `limit==null && offset>0` 时上述 4 处在 MySQL 后端会生成非法 SQL。当前 BizModel 入口 `normalizeJoinQueryLimit`（NopMetaTableBizModel:434-443）保证 limit 恒非 null（null/0 → 1000），路径不可达；但 `executeSameDbTableJoin` 的在码注释（:357-363）声称"dialeat-aware 在 callback 内补入"与实现（callback 外、dialect=null）不符，未来新增调用方易踩坑。
- **风险**: 低（latent）；注释与实现漂移易误导维护。
- **建议**: executeSameDbTableJoin 把 metaData 传入 callback 后按 productName 拼分页（或修正注释）；其余 ORM 路径保持既有 AR-20a 裁定即可。
- **误报排除**: 已读 SqlPagination/normalizeJoinQueryLimit/normalizeQueryLimit 确认入口层 guarantee；已确认 4 处调用点上下文。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. executeSameDbTableJoin（外部数据源连接的真实风险点）把 LIMIT/OFFSET 拼接移入 withConnection callback，按 `AggregationHelper.safeProductName(metaData)` 方言分派（MySQL offset-only 补 18446744073709551615 占位）；漂移注释改为与实现一致。其余 3 处（MetaJoinExecutor entity-entity EQL、EntityAggregationProcessor via-EQL、EntityEntityJoinAggregationProcessor）走 orm().executeQuery 平台默认方言，维持既有 AR-20a 裁定（Deferred But Adjudicated 已登记）。免红理由：offset-only（limit==null && offset>0）经 BizModel 入口 normalizeJoinQueryLimit 保证 limit 恒非 null、当前不可达（审计已确认），修复属 latent 防护 + 注释真值化，无可达行为差异可锚定；helper 级方言语义已由既有 TestSqlPaginationOffsetOnly 钉死，变更经模块全量测试（含 external JOIN 分页测试族）回归无漂移。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P3] CheckpointActionDispatcher/dispatchActions 的 "post-commit 投递" 契约与实现不符（实际为事务外、提交前）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:499`
- **维度**: D8（契约漂移）
- **证据**:
```java
// dispatchActions javadoc: "store（QualityResult）落盘 + flush 后…dispatch 经 runWithoutTransaction 在 store 事务之外执行"
txnTemplate.runWithoutTransaction(null, () -> { dispatcher.dispatch(cp, summary); return null; });
```
CheckpointActionDispatcher 类注释（:37-40）进一步声称"调用方经 ITransactionListener.onAfterCommit 在事务成功提交后调用 dispatch"。
- **现状**: executeCheckpoint 是 @BizMutation（框架事务包裹），dispatchActions 在方法体内、框架提交前执行；runWithoutTransaction 仅脱离事务，不等待提交。store 行已 flush 但未提交，webhook 已对外发出。
- **风险**: 极端场景（提交阶段失败）下 webhook 宣告的结果行实际未落库；主要是文档契约与实现漂移，日常影响小。
- **建议**: 实现改为注册 ITransactionListener.onAfterCommit（或修正两处 javadoc 为"事务外、提交前"）。
- **误报排除**: 已读 executeCheckpoint 调用次序与 @BizMutation 语义；已读 dispatcher 类注释原文。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（契约文档真值化；真 post-commit 实现暂缓）. 按审计给出的可选方案之二，修正 CheckpointActionDispatcher 类注释、NopMetaQualityCheckpointBizModel.dispatchActions javadoc 与 executeCheckpoint 行内注释为真值"事务外、提交前"（store 已 flush 未提交时 webhook 即发出，并明示极端提交失败场景的残余风险）。暂缓子项（决策点）：实现真正 onAfterCommit 投递与 R4.3 per-checkpoint 运行标记语义冲突——标记在方法体 finally 释放、先于框架提交，投递移到 post-commit 后 dispatch 窗口不再被标记覆盖，并发重复投递防护（TestNopMetaQualityCheckpointBizModel 的 dispatch 窗口 fail-fast 测试族）将失效；需与"标记改经 afterCompletion 释放"联动设计，属检查点并发语义改造，超出本条文档漂移修复范围。免红理由：纯契约文档修正（无行为变更），既有 dispatch 行为测试族（webhook/notify 投递 + 并发窗口）全量回归通过。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P3] buildDataSource 对 password 做 trim，含首尾空白的密码被静默改写

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:926`
- **维度**: D1（边界输入处理）
- **证据**:
```java
private String requireField(Map<String, Object> cfg, String key, String datasourceType) {
    ...
    Object value = cfg.get(key);
    return value == null ? "" : value.toString().trim();   // password 亦经此处 trim
}
```
`buildDataSource:266`：`String password = requireField(cfg, CFG_PASSWORD, datasourceType);`
- **现状**: jdbcUrl/username trim 合理，但合法密码可含首尾空格（尤其生成密码/Token 型口令），trim 后与真实凭据不一致 → 建连 401，且错误信息不会提示"密码被改写"。
- **风险**: 特定凭据下连接失败难排查；无数据危害。
- **建议**: password 用不 trim 的存在性检查（`cfg.containsKey` + 原样 toString）。
- **误报排除**: 已读 buildDataSource/requireField/requireNonBlank 调用链确认 password 与 jdbcUrl 共用 trim 路径；mergeCredentialConfig 侧（:339-342）未 trim，两路径行为也不一致。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 新增 requirePasswordField（存在性检查 + 原样 toString，null → 空串对齐 H2 空密码现状），buildDataSource 的 password 改走该路径不 trim；jdbcUrl/username（非凭据标识）维持 trim；与 mergeCredentialConfig 侧（原样合并）行为对齐。红验证：TestPasswordNoTrimBinding#testPasswordPreservesLeadingTrailingSpaces（反射调 private buildDataSource，getPassword() expected " pad " but was "pad"）+ #testPasswordMissingKeyFailsAndNullBecomesEmpty（缺 key 显式失败/null→空串语义守护）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P3] 内存聚合 MinAcc/MaxAcc 对非 Comparable 值裸 ClassCastException；truncateCrossDb 与 CrossDbJoinMerger.truncate 重复实现；buildDictItem 将 null 序列化为 "null" 字符串

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationContext.java:328`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java:884` 与 `CrossDbJoinMerger.java:261`；`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java:172`
- **维度**: D1/D4/D6（可维护性）
- **证据**:
```java
// AggregationContext.MinAcc.accumulate:328-331 —— 无类型守卫的强转
Comparable<Object> c = (Comparable<Object>) v;
if (!hasValue || c.compareTo(min) < 0) { min = c; hasValue = true; }

// OrmModelImporter.buildDictItem:172
item.setItemValue(String.valueOf(option.getValue()));   // value=null → 字面量 "null" 入库
```
另：`AggregationHelper.truncateCrossDb`（:884-910）与 `CrossDbJoinMerger.truncate`（:261-286）为逐行等价的两份实现（AR-09 注释各写一份）。
- **现状**: 跨库内存聚合 min/max 遇 JDBC 返回的 byte[]/Struct 等非 Comparable 类型时抛未包装 CCE；字典项 value 为 null 时入库 "null" 字符串（后续 UK(metaDictId,itemValue) 语义被占）；两份 truncate 逻辑需双处同步维护。
- **风险**: 低——异常路径报错不友好、脏数据需人工清理、重复实现有漂移风险。
- **建议**: MinAcc/MaxAcc 加 `instanceof Comparable` 守卫否则记 unavailable；buildDictItem 对 null 跳过或存空串并注释；truncate 收敛到单一工具方法。
- **误报排除**: 已读三个调用方上下文（memoryGroupBy 输入为 JDBC getObject 原值；buildDictItems 无 null 过滤；两处 truncate 调用点互不相引）。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（3 子项全部处置）. (1) MinAcc/MaxAcc.accumulate 加 `instanceof Comparable` 守卫（非 Comparable 值按 null 语义跳过，result null 不伪造，不再抛裸 CCE）——红验证 TestMemAggAccumulatorTypeGuard#testMinAccSkipsNonComparableValues / #testMaxAccSkipsNonComparableValues（byte[] 输入抛 `ClassCastException: [B cannot be cast to Comparable`，与审计证据吻合）+ #testNullValuesStillSkipped（null 语义守护）。(2) CrossDbJoinMerger.truncate 收敛为委托 AggregationHelper.truncateCrossDb（两实现逐行等价，AR-09 防溢出/负值拒绝语义单一来源）——免红理由：纯重复实现收敛、无可锚定行为差异，既有 TestMetaJoinTruncateOverflow 经委托入口 + TestAggregationHelperErrorParam 经被委托入口双向回归。(3) OrmModelImporter.buildDictItems 对 value=null 的选项跳过（不再把字面量 "null" 写入 itemValue 占据 UK）——红验证 TestOrmModelImporterDictItemNullValue#testNullValuedDictOptionsSkipped / #testAllNullOptionsProduceNoItems（未修复时产出 itemValue="null" 行）。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

### [P3] entity 聚合路径 LOG.info 输出完整 SQL，与模块 AR-16"INFO 只记 sqlHash"日志政策不一致

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/EntityAggregationProcessor.java:155`；`EntityEntityJoinAggregationProcessor.java:424`
- **维度**: D5（日志面）/D7（规范一致性）
- **证据**:
```java
LOG.info("queryAggregation entity SQL: {}", sqlText);            // EntityAggregationProcessor:155
LOG.info("queryAggregation entity bypass-EQL SQL: {}", sqlText); // :243
LOG.info("queryAggregation entity JOIN SQL: {}", sqlText);       // EntityEntityJoin:424
```
对照同模块 external/sql 路径的 P1-8/AR-16 形态（AggregationHelper.executeJdbcQuery:114 等）：`LOG.info("... sqlHash={}", sqlHashOf(sql)); LOG.debug("... SQL: {}", sql);`
- **现状**: entity 路径 SQL 文本（物理表/列名 + 聚合表达式片段）进 INFO 日志。表达式中字面量已参数化为 `?`，敏感字面量不在 SQL 文本内，实际泄漏面小，主要是与既定日志政策不一致。
- **风险**: 低；表结构信息进常规日志（INFO 级常落生产日志），与模块自身脱敏政策漂移。
- **建议**: 对齐 sqlHash 形态（3 处 INFO 改 sqlHash，SQL 全文降 DEBUG）。
- **误报排除**: 已读三处上下文确认 SQL 文本来源（均经标识符白名单、字面量参数化），确认无明文凭据入文；对照 external/sql/profiler/quality/join 五族路径均已改 sqlHash。
> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 3 处（EntityAggregationProcessor via-EQL/bypass-EQL、EntityEntityJoinAggregationProcessor JOIN）LOG.info 改 sqlHash 形态（复用 MetaQualityRuleExecutor.sqlHashOf），SQL 全文降 LOG.debug——与 external/sql/profiler/quality/join 五族路径的 AR-16 脱敏政策统一，entity 路径不再例外。免红理由：日志级别/脱敏形态调整（审计自评"实际泄漏面小，主要是政策一致性"），无行为断言面；既有聚合全量测试族回归确认日志改动不影响执行链。模块测试（core+dao+service）run 1339 / fail 0 / skip 0。

## 补充说明（核查过但未立为发现的项）

- `OrmModelImporter.buildDomain` 中 `setDescription(domain.getDisplayName())` 经核对 `_OrmDomainModel` 无 description 字段（nop-persistence/nop-orm-model），为合理兜底而非复制粘贴错误。
- `NopMetaModuleBizModel.readText` 使用 try-with-resources；`parseDeltaModel` fail-fast（P2-07 已修）；`importOrmModel` 的 per-path REQUIRES_NEW + 索引反向清理 + 事件同事务的编排核对无误。
- `MetaDataSourceConnectionProcessor` 的 SSRF 防护族（多主机/address=/host= 抽取、percent-decode fail-closed、hostless 哨兵、userinfo 脱敏、redactJdbcUrlsInText）与 `HostSecurityUtil` 判定经多轮深读未发现可利用绕过；webhook 侧（CheckpointActionDispatcher）同等防护齐全。
- D7 平台规范全量核查：无 `@Inject private` 字段（全部 protected/setter 注入）、无 Spring `@Value`/org.springframework 导入、7 处 `@InjectValue` 用法正确、app-service.beans.xml 与代码内注入点（含 @Nullable 可选装配、nopMetaQualityCheckpointScheduler）对账一致。
- `CrossDbInMemoryAggregationProcessor` 中 `executeJoin(table, joinId, filter, null, 0L, ctx)` 的实参经核对签名（limit=null, offset=0）语义正确（全量取数、不截断），非笔误。
- `MetaQualityRuleExecutor` custom_sql 沙箱（token 级黑名单 + 多 token 序列 + 可执行注释拒绝）、quality/profiler/catalog 的标识符白名单 + 参数绑定核对无误。
- `beans.xml`、`model/nop-metadata.orm.xml` 中 UK 定义与代码侧 upsert/去重逻辑（metaTable 4 列 UK 守卫、lineage 边 UK + seenKeys 同批去重）交叉验证一致。
