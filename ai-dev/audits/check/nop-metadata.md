# nop-metadata 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-metadata（service/dao/api）
- 文件数: 实测 src/main/java 共 282 个（service 129 / dao 120（其中 39 个为 `_gen/` 生成）/ api 30 / core 2 / app 1；任务描述的 441 与实测不符，按实测声明）
- 覆盖范围声明:
  - **全文深读（35 个文件）**：service 层全部高风险包的核心实现——query 包（MetaJoinExecutor、CrossDbJoinMerger、SqlPagination、FilterToSqlTranslator、AggregationHelper、MetaAggregationExecutor、MetaTableQueryExecutor、Entity/External/Sql/EntityEntity/ExternalExternal/MixedSameDb/CrossDbInMemory 各 AggregationProcessor、MemoryFilterEvaluator、MemoryOrderByComparator、GranularityBucketing、DefaultFilterApplicator、CrossDbConfigHolder、JoinExternalSideResolver）、lineage 包（SqlColumnLineageExtractor、SqlSourceTableExtractor）、quality 包（MetaQualityRuleExecutor、MetaQualityCheckpointScheduler、MetaQualityCheckpointExecutor、MetaQualityScorer、CheckpointActionDispatcher）、reconciliation 包（三个类）、sync（ExternalTableStructureReader）、profiling（MetaTableProfiler）、connection（MetaDataSourceConnectionProcessor）、tableref（TableReferenceExecutor）、datasource（MetaDataSourceResolver）、sqlview（SqlViewFieldTypeInferrer）、field（ExpressionMeasureValidator）；
  - **关键 BizModel 深读**：NopMetaTableBizModel、NopMetaDataSourceBizModel、NopMetaQualityRuleBizModel、NopMetaQualityCheckpointBizModel、NopMetaLineageEdgeBizModel + NopMetaLineageEdgeQueryAction、NopMetaTableQueryAction、NopMetaReconciliationConfigBizModel；
  - **抽查**：beans 注册（app-service.beans.xml + _service.beans.xml）、model/nop-metadata.orm.xml 唯一键、api 模块 DTO 清单、EqlASTParser 线程安全性（nop-orm-eql 源码确认 per-call newParser）；
  - **全局 grep 扫描**：空 catch、`new RuntimeException`、`printStackTrace`、`getConnection/close`、`synchronized`、`@Inject private`、Spring `@Value`、SQL 字符串拼接、无界 `findAllByQuery`、`param("sql")` —— 每个命中点均已回读上下文核实；
  - **未深读**：dao 层非生成 biz 接口与实体包装类（声明式）、其余 CRUD 型 BizModel（NopMetaDict/Tag/Glossary/OrmModel 等，无外部连接/SQL 拼接面）、search/entity 传播处理器（LineageTagPropagation/AutoClassification，仅元数据内查询）、deploy/model/web 目录。这些未深读区域如存在问题，风险等级以 P3 为主，不排除遗漏。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 5 |
| P3 | 3 |

总体评价：该模块代码经过多轮自审（代码内大量 AR-xx/F-x/plan 引用），SQL 注入防护（标识符白名单 + PreparedStatement 参数绑定 + custom_sql token 级黑名单）、JDBC URL/SSRF 加固、凭证治理、并发守卫、分页参数绑定（AR-01 双模式）均相当扎实，未发现 P0 级数据错误/安全/崩溃问题。发现集中在：内存中无上限全量拉取（profiling）、对账策略边界（fuzzy 永不自动匹配、静默截断 1000 行）、防护面不对齐（expression measure 函数黑名单弱于 custom_sql）、跨数据源 upsert 覆盖。

## 发现列表

### [P1] 数值剖析全列拉取无行数上限，单次调用可致内存耗尽

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:331-346`（调用点 `collectNumericStats` :304）
- **维度**: D6（辅 D2）
- **证据**:
```java
/** 拉取非空数值并升序排序（仅依赖可移植 ORDER BY，全方言精确）。 */
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
- **现状**: `profileTable`（@BizMutation，用户可对任一已注册 external/sql/entity 表触发）对**每个数值列**执行无 LIMIT 的整列拉取，全部装入 `List<Double>` 后排序，用于 median/percentiles/distribution 的 in-app 计算。行数上限、列值截断、采样均不存在。同类其他查询（跨库 JOIN 的 `maxCrossDbRows=10000`、queryTableData 的 `max-limit` 封顶）均有界，唯独此路径无界。
- **风险**: 对千万行级外部表执行一次剖析：外部库全表扫描 + 网络整列传输 + JVM 堆内 `List<Double>`（每元素约 24-32 字节，1 亿行 ≈ 3GB+）→ OOM / 长时间阻塞外部数据源。每个数值列重复一次。
- **建议**: 加配置化行数上限（超限标记 `unavailable:["median-truncated"]` 而非伪造）；或改用各方言近似的 `PERCENTILE_CONT`/采样；至少对 `rowCount` 超阈值的表跳过 in-app 统计。
- **误报排除**: 确认 `profileTable` 经 `TableReferenceExecutor` 直连外部库，SQL 不经平台 ORM 分页拦截；javadoc 明示 "in-app 精确（全方言…拉取列值排序后 Java 计算"，是有意设计但**缺边界**，与模块自身处处设限的风格相悖，非误报。

### [P2] 对账 fuzzy 策略下 autoMatch 永远无法 MATCHED（候选不过滤阈值）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationProcessor.java:72-90`；`ReconciliationExecutor.java:124-143`
- **维度**: D1/D8
- **证据**:
```java
// LocalReconciliationProcessor.reconcile
List<ReconciliationCandidate> matched = new ArrayList<>();
for (NopMetaReconciliationEntity entity : pool) {
    double score = score(value, entity.getEntityName(), matchStrategy);
    if (score > 0.0) {          // fuzzy: sim > 0.0001 即入选，未用 autoMatchThreshold 过滤
        matched.add(new ReconciliationCandidate(..., score, ...));
    }
}
matched.sort(SCORE_DESC);
if (limit != null && limit > 0 && matched.size() > limit) { return ...subList(0, limit); }

// ReconciliationExecutor.judgeStatus（D5 钉死规则）
if (candidates.size() == 1) {
    if (candidates.get(0).getScore() >= effectiveThreshold) { return STATUS_MATCHED; }
    return STATUS_MULTIPLE;
}
return STATUS_MULTIPLE;   // 候选≥2 一律 MULTIPLE
```
- **现状**: fuzzy 模式下 levenshtein 相似度 > 0.0001 的**所有**候选实体都进入候选集（10 字符名相差 9 个字符 sim=0.1 仍入选），`reconcile` 不接收也不应用 `autoMatchThreshold`；而 judgeStatus 要求**恰好 1 个候选**才可能 MATCHED。候选池 ≥2 时（DEFAULT_CANDIDATE_LIMIT=50，实际池通常远大于 2）每行都返回 50 个候选 → 恒为 MULTIPLE。
- **风险**: `matchStrategy=fuzzy` + `autoMatch=true` 的对账配置在真实候选池下**永远**产出 MULTIPLE，自动匹配功能对 fuzzy 策略失效，全部落人工；与配置项 `autoMatchThreshold`（应表达"高分自动采纳"）的语义契约不符。
- **建议**: `reconcile` 接收 threshold 并在候选收集时过滤（score ≥ threshold 才入选）；或 judgeStatus 改为"最高分候选 score ≥ threshold 且与次高分差距足够 → MATCHED"（需同步修订设计 §3.2 钉死规则并钉测试）。
- **误报排除**: 核对 exact 路径（仅大小写不敏感等值入选，恰 1 候选可 MATCHED）语义正常；问题仅 fuzzy 路径。已确认 `ReconciliationExecutor` 调用链未在其他层做阈值过滤。

### [P2] executeReconciliation 静默截断为前 1000 行，对账覆盖率失真

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationConfigBizModel.java:117-129`
- **维度**: D1/D8
- **证据**:
```java
// 取数：BizModel 调 queryTableData 取 items（B2 方案 b）。失败显式抛 ErrorCode（不吞异常）。
List<Map<String, Object>> items;
try {
    items = tableBizModel.queryTableData(metaTableId, null, null, null, null, context).getItems();
} catch (NopException e) { ... }
NopMetaReconciliationResult result = reconciliationExecutor.execute(config, items);
```
- **现状**: filter/limit/offset 全部传 null——`queryTableData` 的 `normalizeQueryLimit(null)` 返回 `DEFAULT_QUERY_LIMIT=1000`。对账只对表的**前 1000 行**执行；`executeReconciliation` 无任何分页/过滤参数，结果 `statistics.totalRows=1000`、`matchRate` 均基于该前缀计算，结果实体与 DTO 不携带"截断"标记。
- **风险**: 对超过 1000 行的表，对账结果被静默当作全表结论落库展示（matched/unmatched/matchRate 全部失真）；双向比对模块的核心承诺"覆盖比对"在大表上不成立，且调用方无感知、无法翻页补跑。
- **建议**: 至少在 `NopMetaReconciliationResult.statistics` 写入 `truncated:true / fetchedRows:1000`（对齐模块"不静默降级"的既定哲学）；更好是支持 offset/filter 分批执行或按 `config.columnName` 在库侧取数。
- **误报排除**: 已核实 `queryTableData` limit 归一化路径（NopMetaTableBizModel:406-416）与 `normalizeQueryLimit` 的 null→1000 语义；executor 与 DTO 中无任何截断标记字段。

### [P2] expression measure 函数黑名单缺 PG/H2 文件读取与远程执行族，防护面弱于 custom_sql 沙箱

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/field/ExpressionMeasureValidator.java:103-110`（对照面 `MetaQualityRuleExecutor.java:99-114`）
- **维度**: D5
- **证据**:
```java
// ExpressionMeasureValidator.FUNCTION_BLACKLIST（全部条目）
"SLEEP", "BENCHMARK", "LOAD_FILE", "GET_LOCK", "RELEASE_LOCK",
"PG_SLEEP", "PG_TERMINATE_BACKEND", "COPY",
"xp_cmdshell"

// MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS（同模块另一通道，含而上面缺）：
"PG_READ_FILE", "PG_READ_BINARY_FILE", "PG_LS_DIR", "PG_LS_LOGDIR", "PG_LS_WALDIR",
"PG_STAT_FILE", "SYS_EXEC", "RUNSCRIPT", "SCRIPT",
"FILE_READ", "FILE_WRITE", "BACKUP", "CSVWRITE", "CSVREAD", ...
```
- **现状**: FUNCTION_CALL token 只查 FUNCTION_BLACKLIST（P2-04 裁定有意不查关键字表），且无函数白名单；`checkDialectSupported` 仅拦 MySQL 的 DATE_TRUNC。因此 `PG_READ_FILE(...)`、`DBLINK(...)`、`LO_IMPORT(...)`、`FILE_READ(...)` 等未列入黑名单的函数可出现在 measure expression 中（字符串字面量被参数化为 `?` 后拼进 `MIN(fn(?))` 之类的聚合 SQL 在外部数据源执行）。而同模块 custom_sql 沙箱（F9 加固）已明确将这些函数族视为危险并拒绝——两个执行通道防护面不对齐。
- **风险**: 持有 measure 定义权限的用户可通过 expression measure 让平台在外部数据源上执行文件读取/远程连接函数（如 PG `MIN(PG_READ_FILE(?))` 返回服务器文件内容作为度量值）。前置条件较高（元数据写权限 + PG 侧超管/pg_read_server_files 角色），但属纵深防御缺口，且与本模块自定义的 F9 威胁模型直接矛盾。
- **建议**: 将 custom_sql 沙箱中的 PG 文件族/脚本族/H2 文件族同步补入 FUNCTION_BLACKLIST（两表语义不同可保持分离，条目应对齐）；中期改为函数白名单。
- **误报排除**: 已核对 tokenize 中 FUNCTION_CALL 判定（word 后跟 `(`）与 sqlFragment 直拼路径（loadExternalMeasures/loadJoinMeasuresWithResolver 将 `ve.sqlFragment` 直接嵌入 aggSql），确认无第二道拦截；排除"HAVING 字面量禁令可兜底"的疑议（measure expression 的字面量是允许并绑参的）。

### [P2] syncExternalTables 跨数据源同名同 schema 表静默互相覆盖（去重键不含 querySpace）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:899-939`
- **维度**: D1/D8
- **证据**:
```java
// 幂等 upsert：按 (metaModuleId, schema, tableName) 复合键去重 ...
// **跨数据源行为（Decision，plan 0852-3 Phase 2）**：去重键仍**不含 querySpace**——
// 跨数据源、同名同 schema 的表会互相覆盖（与 1905-1 收敛前语义一致）。
...
} else {
    table.setMetaSchema(infoSchema);
    table.setQuerySpace(dataSource.getQuerySpace());   // 被后同步的数据源覆盖
    table.setBuildSql(columnsJson);
    tableDao.updateEntity(table);
}
```
- **现状**: 两个数据源（不同 querySpace）都含同名同 schema 的物理表时，后执行的 sync 会把已有 NopMetaTable 行的 `querySpace`/`buildSql` 改写为自己，先同步的数据源失去该表的映射。代码注释已显式裁定为已知 follow-up。
- **风险**: 查询/质量规则/血缘后续全部路由到**错误的数据源**（模块定位中"跨库 JOIN 拼接错误=查询结果错误"的直接成因之一），且无任何告警或冲突报告——静默数据面漂移。
- **建议**: 短期在覆盖发生时（update 分支且 querySpace 变化）记录 WARN/事件；中期将 querySpace 纳入去重键（需评估存量迁移）。
- **误报排除**: 确认 4 列 UK 为 `(metaModuleId, tableName, isDelta, metaSchema)`（orm.xml），不含 querySpace，DB 层不拦；该行为已在代码中裁定但用户可见后果未在任何 API 响应中体现，按契约漂移报告。

### [P2] 对账每行全量加载候选池（N+1 全表查询 + O(行×池) 编辑距离）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationProcessor.java:93-103`；调用方 `ReconciliationExecutor.java:89-91`
- **维度**: D6
- **证据**:
```java
// ReconciliationExecutor.execute —— 每行调用一次 reconcile
List<IReconciliationProcessor.ReconciliationCandidate> candidates =
        reconciliationService.reconcile(value, config.getTargetEntityType(),
                config.getIdentifierSpace(), config.getMatchStrategy(), DEFAULT_CANDIDATE_LIMIT);

// LocalReconciliationProcessor.reconcile —— 每次全量拉池
List<NopMetaReconciliationEntity> pool = loadCandidates(targetType, identifierSpace);
...
private List<NopMetaReconciliationEntity> loadCandidates(String targetType, String identifierSpace) {
    QueryBean q = new QueryBean();           // 无 setLimit
    ...
    return dao.findAllByQuery(q);
}
```
- **现状**: R 行对账 = R 次候选池全表查询（每行一次 `findAllByQuery`，无缓存）+ R×P 次 levenshtein（每次 O(len²)）。行侧虽有 1000 上限（见上一条），池侧 P 无上限。
- **风险**: 池 10 万实体 × 1000 行 = 1000 次全表查询 + 1 亿次编辑距离计算，对账耗时段级放大、平台库压力陡增。
- **建议**: 在 `execute` 入口加载一次候选池传入（或 processor 内按 (targetType, identifierSpace) 缓存）；exact 策略改为库侧 `eq(entityName)` 过滤。
- **误报排除**: 确认 `reconcile` 无 per-call 缓存字段、`LocalReconciliationProcessor` 为无状态单例 bean，每次调用都走 DAO。

### [P3] MetaTableQueryExecutor 异常参数携带完整 SQL（含 sourceSql 全文），与模块 sqlHash 脱敏约定不一致

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaTableQueryExecutor.java:142-147`
- **维度**: D4（辅 D5）
- **证据**:
```java
} catch (SQLException e) {
    throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_SQL_EXEC_FAILED, e)
            .param(NopMetadataErrors.ARG_META_TABLE_ID, metaTableId)
            .param("sql", sql)                    // sql 路径含 sourceSql 全文
            .param(NopMetadataErrors.ARG_ERROR, messageOf(e));
}
```
- **现状**: sql 路径的执行 SQL 为 `SELECT * FROM (<sourceSql>) _t ...`，失败时全文进入异常 param（NopException.getMessage 无条件拼接 params 进日志/可能外露）。同类执行器（MetaJoinExecutor/AggregationHelper）在 P1-8/AR-16 加固后 INFO 只记 sqlHash、异常不回显全文。
- **风险**: sourceSql 可内嵌敏感字面量（姓名/卡号等），与模块自定的脱敏口径不一致；信息暴露面大于必要。
- **建议**: 对齐先例——param 改为 `sqlHash`，全文降 DEBUG 日志。
- **误报排除**: 非 jdbcUrl 凭据泄漏（该面已由 `redactJdbcUrlsInText` 覆盖），仅 SQL 文本面。

### [P3] entity 路径聚合/JOIN SQL 在 INFO 级输出全文，与 external/sql 路径形态不一致

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/EntityAggregationProcessor.java:155`；`EntityEntityJoinAggregationProcessor.java:141`
- **维度**: D4
- **证据**:
```java
String sqlText = sql.toString();
LOG.info("queryAggregation entity SQL: {}", sqlText);   // 全文 @INFO
```
- **现状**: external/sql 路径统一 `INFO 只记 sqlHash`（P1-8/AR-16），entity 路径仍全文 INFO。entity SQL 不内嵌 sourceSql、比较值均参数化，暴露面有限（表名/列名/别名）。
- **风险**: 日志量与口径不一致；多表聚合高频调用时日志膨胀。
- **建议**: 统一为 sqlHash @INFO + 全文 @DEBUG。
- **误报排除**: 已核对 entity 路径 filter/having 值均走 `?` 绑定，SQL 文本无用户字面量。

### [P3] ensureExternalSystemModule 惰性创建无并发守卫，首次并发 sync 一方失败

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:986-1005`
- **维度**: D3
- **证据**:
```java
private String ensureExternalSystemModule() {
    ...
    NopMetaModule module = moduleDao.findFirstByQuery(query);
    if (module != null) { return module.getMetaModuleId(); }
    module = moduleDao.newEntity();          // find-then-insert，无锁、无独立事务
    ...
    moduleDao.saveEntity(module);
    orm().flushSession();
    return module.getMetaModuleId();
}
```
- **现状**: 系统模块 `nop/meta-external` 首次创建是 find-then-insert，未使用同文件已有的 per-key 锁 + REQUIRES_NEW 模式（`upsertExternalTableGuarded`）。并发首次 sync 时两者都查空、都插入，`UK_NOP_META_MODULE_ID_VER(moduleId,moduleVersion)` 使后提交方在 flush/commit 抛唯一键冲突。
- **风险**: 竞态窗口极小（仅系统模块尚不存在的首次），后果是失败方整个 syncExternalTables 报错（fail-loud），重试即自愈（find 命中已有行）。无静默数据损坏。
- **建议**: 复用 `upsertExternalTableGuarded` 的锁 + 独立事务 + 冲突后重查模式。
- **误报排除**: 已核对 orm.xml 中 UK 存在（`UK_NOP_META_MODULE_ID_VER`），因此不会产生重复模块行，降级为 P3。

## 已核查并排除的疑点（误报排除记录）

1. **`MetaJoinExecutor.executeSameDbTableJoin` 向 `SqlPagination.appendLimitOffset` 传 dialect=null**（MetaJoinExecutor.java:364）：MySQL offset-only 会拼出非法 `OFFSET ?`，但 GraphQL 入口 `normalizeJoinQueryLimit`（NopMetaTableBizModel.java:434-443）保证 limit 永不为 null（null/0 → DEFAULT_QUERY_LIMIT），offset-only 不可达；`LIMIT ? OFFSET ?` 在 MySQL 合法。无实际缺陷。
2. **连接泄漏**：`MetaDataSourceConnectionProcessor.withConnection/testConnect` 均 finally `IoHelper.safeCloseObject(conn)`；所有 JDBC 消费点（14 处）经该回调或平台 `IJdbcTransaction`，未发现自管连接泄漏。PreparedStatement/ResultSet 均为 try-with-resources。
3. **空 catch / 裸 RuntimeException / printStackTrace**：全局扫描零命中（唯一 `RuntimeException[]` 是 TableReferenceExecutor 的错误持有数组）。
4. **Nop 平台规范（D7）**：无 `@Inject private` 字段（均为 protected + setter 注入）、无 Spring `@Value`（统一 `@InjectValue @cfg:`）、异常统一 `NopMetadataException` + `NopMetadataErrors` + `.param(...)`、bean 均在 `_vfs` beans.xml 显式注册（app-service.beans.xml 含非生成 bean，_service.beans.xml 含 BizModel + BizProxy）。
5. **LIMIT/OFFSET 参数绑定（AR-01 双模式）**：逐点核对 5 条 JDBC 执行路径——模式 A（调用方绑定：executeSameDbJoin、via-EQL 聚合 ×2）与模式 B（executeJdbcQuery 内绑定：table-table JOIN、external/external-external/mixed 聚合、fetchTableRows）参数顺序与占位符一致，无错绑。
6. **血缘抽取边界**：CTE/派生表递归有 `resolving` 环路守卫；`WITH RECURSIVE` 整体标 unresolved；通配符/多表无限定符/owner 未匹配均显式 unresolved 不伪造；血缘图 BFS 受 `maxEdges/maxTables`（默认 100k）约束。`EqlASTParser` 每次 parse 新建 parser，线程安全。
7. **custom_sql 沙箱**：token 级黑名单 + 多 token 序列匹配 + `;`/`/*!` 显式拒绝 + fail-closed（字符串内关键字亦拒），覆盖 DML/TCL/PG 文件族/H2 文件族/系统目录族，sqlHash 审计齐备。
8. **检查点调度与并发**：cron job 失败不转 `JobFireResult.ERROR`（防永久 FAILED）、addJob 失败清理残留 job、per-checkpoint `putIfAbsent` 运行标记 finally 释放、per-rule 失败隔离 + AR-14 ERROR 行补写，逻辑闭环。
9. **凭证**：bind/unbind/迁移四步同行级事务、明文清除、consumerRef 引用计数、jdbcUrl/userinfo 多点脱敏、`redactJdbcUrlsInText` 覆盖驱动消息回显面。
