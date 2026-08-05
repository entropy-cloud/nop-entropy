> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata-audit-remediation
> Processed: 2026-08-06 — AR-02/AR-03 → plan `ai-dev/plans/nop-metadata-audit-remediation/2026-08-06-0553-1-ssrf-host-normalization-variants.md`；AR-04/AR-05 → plan `2026-08-06-0553-2-custom-sql-sandbox-dml-h2-hardening.md`；AR-01/AR-06/AR-07/AR-08/AR-09/AR-10 → plan `2026-08-06-0553-3-query-quality-import-correctness-batch.md`；AR-11~23（P2）→ roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`）

# 开放式对抗审查 — nop-metadata（mission: nop-metadata-audit-remediation，R6.6 收口后复检）

## 执行说明

- **切入点**：异常路径侦探（SSRF 变体复活面）+ 契约考古学家（分页 API / SLA / 血缘 API）+ 代码生成受害者（导入事务边界）+ 死代码清道夫 + 组合爆炸测试者（R6.x 修复与新代码交互）
- **方法**：完整阅读 06:55 multi-audit / open-audit 报告、roadmap（含 R6.0-R6.6 裁决记录）、arm-index 追踪矩阵；live code 完整阅读 quality 域（MetaQualityRuleExecutor/CheckpointExecutor/Scheduler/Scorer/Dispatcher）、query 域 26 文件、import/sync/search/recon/contract 域 20 文件、NopMetaDataSourceBizModel/NopMetaModuleBizModel/NopMetaLineageEdgeBizModel 等核心 BizModel；jshell 实测 macOS 主机名解析语义；MySQL 9.6.0 + PG 42.7.10 驱动实测 unbracketed IPv6 JDBC URL 建连行为；git 逐 commit 核对 R3.14/R5.1 等"已修复"声明的实际 diff
- **去重前置**：06:55 两轮报告的 5 P1（SSRF 归一化 / 血缘 API / 文档漂移 / 治理）与 32 条 backlog 中 R5/R6 已处置项**逐项核实为已修复**（HostSecurityUtil 双侧接线、血缘 fail-loud、实体表 39/39、P2-01~13 等），不重述；本报告只报**新发现**与**"已登记 fixed 但实际未修复"**的项

---

## P0 发现（阻断，必须修复）

### [AR-01] [P0] 同库 JOIN 路径 LIMIT/OFFSET 参数双重绑定 —— queryJoinData / queryAggregation 带分页参数时必然执行失败

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaJoinExecutor.java:358-371`（executeSameDbTableJoin 入参构造）+ `MetaJoinExecutor.java:669-679`（executeJdbcQuery 绑定）、`ExternalExternalJoinAggregationProcessor.java:116-124`、`MixedSameDbJoinAggregationProcessor.java:152-160`、`AggregationHelper.java:87-97`（executeJdbcQuery 绑定）
- **证据片段**:
  ```java
  // 三处调用点（以 MetaJoinExecutor:365-371 为例）：
  SqlPagination.appendLimitOffset(sql, limit, offset, null);   // SQL 拼 LIMIT ? / OFFSET ?
  if (limit != null) { params.add(limit); }                    // ← 调用方把 limit 加进 params
  if (offset != null && offset > 0) { params.add(offset); }
  ...
  holder[0] = executeJdbcQuery(conn, sqlText, params, limit, offset, join.getJoinId(), "same-db-table-join");
  // executeJdbcQuery:671-676 又独立绑定一次：
  for (Object p : filterParams) { st.setObject(idx++, p); }    // ← 含 limit/offset
  if (limit != null) { st.setObject(idx++, limit); }           // ← 二次绑定
  if (offset != null && offset > 0) { st.setObject(idx++, offset); }
  ```
- **严重程度**: P0 — 公开查询 API（`queryJoinData`/`queryAggregation`，GraphQL + INopMetaTableBiz 契约）在 `limit != null` 或 `offset > 0` 时**必然**抛 SQLException（参数占位符数 < 绑定数），同库 external/sql↔external/sql JOIN 与 mixed entity↔external JOIN 三路径全部命中；`docs-for-ai/03-modules/nop-metadata.md` 的 `queryAggregation(limit: 100)` 即触发
- **现状**: 全部 7 处 join 测试调用点均传 `limit=null, offset=null`（`TestAggregationExternalJoinAndPagination.java:98,171,280,...`——测试类名含 "Pagination" 但从未传分页参数），双绑错误完全无测试覆盖；R3.12 的 limit 治理只覆盖 queryTableData
- **风险**: 任何带分页/上限的真实客户端调用直接 500；分页契约完全失效
- **建议**: 三处调用点删除 `params.add(limit/offset)`，交由 executeJdbcQuery 统一绑定（对照正确的先例 `MetaJoinExecutor.java:455` fetchTableRows / `ExternalAggregationProcessor.java:83-85`）；补 `limit=2` / `offset=1` 回归用例
- **信心水平**: 确定（SQL 占位符数 vs 绑定数逐位核对 + 先例路径对比）

---

## P1 发现（必须修复）

### [AR-02] [P1] R5.1 SSRF 修复残余变体：JDBC URL 无方括号 IPv6 字面量绕过 isInternalHost —— 实测可连到 IPv6 loopback

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:293-295`（extractHost 端口剥离）、`HostSecurityUtil.java:210-217`（isInternalIpv6Literal）
- **证据片段**:
  ```java
  // extractHost 对 "jdbc:mysql://::1:3306/db"：
  int colon = hostPort.indexOf(':');          // = 0
  String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;  // → "::1:3306"（整个串）
  // HostSecurityUtil.isInternalHost("::1:3306")：含 ':' → isInternalIpv6Literal → getByName 抛 UnknownHostException → return false → 判定外部 → 放行
  ```
- **严重程度**: P1 — 安全：`jdbc:mysql://::1:3306/db` / `jdbc:postgresql://::1:5432/db` 等**无方括号 IPv6** 形式被放行。实测（MySQL Connector/J 9.6.0 + pgjdbc 42.7.10，本机 TCP listener on ::1）：两驱动均接受该 URL 并**成功建连到 ::1 loopback**（`TCP CONNECTED: remote=/[0:0:0:0:0:0:0:1]:52703`）。R5.1 只处理了 `[::1]` 带括号形式，同族残余绕过
- **现状**: 新发现（06:55 报告的 AR-01/02 已修，本变体未覆盖、无测试——`TestMetaDataSourceConnectionSecurity` 仅测带括号 IPv6）
- **风险**: 拥有数据源配置权限的用户可触达本机 IPv6 服务（::1 MySQL/管理端口、fe80:: link-local 设备）
- **建议**: extractHost 对 `hostPort.indexOf(':')==0`（多冒号无括号）时按 `lastIndexOf(':')` 剥离端口后把剩余部分交 HostSecurityUtil 判定；或 HostSecurityUtil 直接支持 "::1:3306" 形态（剥离尾 :port 再判 IPv6）；补 `::1:3306` / `fe80::1:3306` 正反用例
- **信心水平**: 确定（驱动实测 + 代码核对）

### [AR-03] [P1] R5.1 SSRF 修复残余变体：`localhost.` / `*.localhost.`（FQDN 尾点）绕过主机名 fast path —— jshell 实测解析到 127.0.0.1

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/security/HostSecurityUtil.java:186-189`（isInternalHostname）
- **证据片段**:
  ```java
  // isInternalHostname("localhost.")：
  if ("localhost".equals(h) || h.endsWith(".localhost")) return true;  // "localhost." 均不命中
  // jshell 26.0.1（macOS）实测：
  //   InetAddress.getByName("localhost.")   → 127.0.0.1
  //   InetAddress.getByName("a.localhost.") → 127.0.0.1
  ```
- **严重程度**: P1 — 安全：webhook（`http://localhost.:8080/`）与 JDBC（`jdbc:mysql://localhost.:3306/db`）两条路径的 host 校验都放行尾点变体，而 JDK/OS 实际解析到 loopback。`TestHostSecurityUtil` 覆盖 `localhost`/`a.localhost` 但不含尾点形式
- **现状**: 新发现（R5.1 统一实现时遗漏 FQDN 尾点归一化；`127.0.0.1.` 因前缀比对恰被拦截，`localhost.` 无前缀匹配可依）
- **风险**: 与 AR-02 同族——配置权限用户可 SSRF 到本机 loopback 服务
- **建议**: hostname 路径先剥一个尾 `.`（FQDN 标记）再比对；补 `localhost.` / `a.localhost.` / `127.0.0.1.` 正反用例
- **信心水平**: 确定（jshell 实测 + 代码核对）

### [AR-04] [P1] custom_sql 沙箱不拦截 DML（INSERT/UPDATE/DELETE/MERGE/REPLACE）—— 规则 SQL 可修改外部数据源数据

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:69-77`（黑名单）、`:331-354`（validateCustomSqlSandbox）、`:653-676`（querySingleValue）
- **证据片段**:
  ```java
  // CUSTOM_SQL_FORBIDDEN_WORDS 无 INSERT/UPDATE/DELETE/MERGE/REPLACE（仅 DROP/TRUNCATE/ALTER/CREATE 等 DDL + 文件族）
  // DELETE FROM t / UPDATE t SET ... / INSERT INTO exfil SELECT ... 全部通过 token 校验
  value = querySingleValue(conn, sql);   // conn.prepareStatement(sql).executeQuery() —— MySQL/PG 先执行后报错
  ```
- **严重程度**: P1 — 安全：custom_sql 规则在外部数据源账户上执行（javadoc 自述），DML 在 MySQL/PG 驱动上先执行后报 "no result set"，数据修改生效；同模块兄弟校验器 `ExpressionMeasureValidator.java` KEYWORD_BLACKLIST 明确含 INSERT/UPDATE/DELETE/MERGE/REPLACE——本处遗漏与模块自身安全基线矛盾。R6.1 补的是文件/脚本族 6 项，DML 族未入
- **现状**: 新发现（javadoc :326-328 只承认 MERGE/REPLACE 为"未来需审查"，连 INSERT/UPDATE/DELETE 都未提及）
- **风险**: 可配规则的用户对只读账户外的任何数据源账户执行数据篡改/外带写入
- **建议**: 沙箱增加首 token 必须为 SELECT 的语句类型校验 + 黑名单补 DML/TCL 族（对齐 ExpressionMeasureValidator）；补 `DELETE`/`UPDATE`/CTE-DML 回归用例
- **信心水平**: 确定（代码核对；驱动"先执行后报错"语义为 MySQL/PG 标准行为）

### [AR-05] [P1] custom_sql 沙箱缺 H2 文件读写族（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE）—— javadoc "全覆盖"声明与实际不符

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:62-63, 76-77`
- **证据片段**:
  ```java
  // javadoc :62-63 声称 PostgreSQL 文件/目录访问族与 H2 RUNSCRIPT/SCRIPT 等"全覆盖"
  // 实际黑名单：PG_READ_FILE/PG_READ_BINARY_FILE/PG_LS_* / COPY / SYS_EXEC / RUNSCRIPT / SCRIPT ...
  // 缺失：H2 FILE_READ（任意文件读，SELECT 中可用，值直接返回规则）、FILE_WRITE、BACKUP TO、CSVWRITE、LOAD XML INFILE
  ```
- **严重程度**: P1 — 安全：H2 是受支持方言（协议白名单含 `jdbc:h2:mem:/file:`），`SELECT FILE_READ('/etc/passwd')` 类规则可读应用宿主机任意文件并把内容作为规则结果值回显；与 javadoc "文件读写全覆盖" 声明直接矛盾
- **现状**: 新发现（R6.1 补 PG 族时未覆盖 H2 函数族；全仓 grep 无 FILE_READ/FILE_WRITE/CSVWRITE 防护）
- **风险**: 宿主文件内容经质量规则结果外带；FILE_WRITE/BACKUP 写文件
- **建议**: 黑名单补 `FILE_READ`/`FILE_WRITE`/`BACKUP`/`CSVWRITE`/`LOAD XML` 序列（LOAD XML 进 FORBIDDEN_SEQUENCES）；修正 javadoc 断言
- **信心水平**: 确定（代码核对）

### [AR-06] [P1] 虚假关闭：MA7.6-05（SLA 配置 + 无 Catalog → 静默 PASS）登记 fixed 但代码从未修改 —— R3.14 修复实际只删了版权头

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/contract/MetaContractChecker.java:247-283`（evaluateSla）、`:404-407`（不可达死分支）；`ai-dev/audits/arm-index-nop-metadata.md`（MA7.6-05 → fixed 记录）；roadmap R3.14（done）
- **证据片段**:
  ```java
  // 当前代码：SLA 配置存在但 catalogAvailable=false 时
  // collectionStale=false; dataStale=false → slaFresh = !false && !false = true → 归并结果 PASS
  // git 核对：唯一修复 commit 9b769490e 对本文件的实际 diff = 仅删 7 行版权头；
  // commit message 却声明 "MA7.6-05：MetaContractChecker 无 Catalog 时 SLA 配置 → slaFresh=false"
  ```
- **严重程度**: P1 — 治理/契约：已登记"fixed（R3.14）+ closure audit PASS"的治理面缺陷（SLA 无数据静默绿灯）**仍 live**；MA7.6-05 原审计建议的 slaFresh=false 与死代码删除均未落地。这是"提交信息声明 ≠ 代码变更"的虚假关闭，closure audit（独立子代理）也未发现
- **现状**: 已知未修复（原登记 P2，经 R3.14 声称 fixed——实际从未修复；git log 证明 9b769490e 为最后一次修改且无 slaFresh diff）
- **风险**: 治理报告持续假绿；同类"commit message 声称修复但 diff 未含"的流程缺口可能在其它 R3.x 项中同样存在（建议对 R3.x 声称 fixed 的 P2 项抽样复核）
- **建议**: 落地 slaFresh=false（无 Catalog 且 SLA 已配置）语义 + 删死代码分支 + 补回归测试；核查 arm-index 中其他"声称 fixed 但无 diff 证据"的项；closure audit 增加"修复 diff 存在性"检查
- **信心水平**: 确定（git diff 逐行核对）

### [AR-07] [P1] 检查点执行路径忽略 NopMetaTable.metaSchema 默认值 —— 同一条规则随入口不同而检查不同的物理表

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointExecutor.java:288-293`；对照 `NopMetaQualityRuleBizModel.java:148, 247, 304-309`（单规则路径 resolveDefaultSchema 回退 table.metaSchema）
- **证据片段**:
  ```java
  // 单规则路径（RuleBizModel:304-309）：
  private static String resolveDefaultSchema(String schemaPattern, NopMetaTable table) {
      if (schemaPattern != null && !schemaPattern.trim().isEmpty()) return schemaPattern;
      return table.getMetaSchema();          // ← 回退持久化 schema
  }
  // 检查点路径（CheckpointExecutor:290）：schemaPattern 原样透传（cron 路径恒 null）
  return tableRefExecutor.execute(ref, (conn, metaData, productName) ->
      ruleExecutor.judge(conn, ref, schemaPattern, ...));   // ← 无 metaSchema 回退
  ```
- **严重程度**: P1 — 契约漂移：同一规则在"手动单规则执行"与"检查点/cron"两个入口可能评估**不同的物理表**（连接默认 schema vs 持久化 metaSchema），质量结果/自动评分静默基于错误表
- **现状**: 新发现（R4.2 多 schema 支持引入 resolveDefaultSchema 语义时只接了单规则与 catalog 路径，检查点路径遗漏）
- **风险**: 多 schema 数据源上检查点结果与单规则结果不一致，且 details 中无 schema 标记（`:134-136` 仅非空时记入）
- **建议**: `MetaQualityCheckpointExecutor.executeSingleRule` 复用相同 resolveDefaultSchema 语义（或下沉到 judge 统一解析）；补多 schema 检查点回归用例
- **信心水平**: 确定（代码核对）

### [AR-08] [P1] 导入批量路径：flush 后索引/事件失败 → clearSession 无法撤销 → 报失败但数据已提交；索引与 DB 事务边界分离（幽灵文档 + 级联删除残留）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java:186-208`（importOrmModel）、`:364-380`（importOrmModels catch）、`:143-155`（delete override 无索引清理）；`NopMetaEntityBizModel.java:35-40`；`NopMetaTableBizModel.java:120-131`
- **证据片段**:
  ```java
  // importOrmModel：flushSession（:189）→ addToIndex ×N（:191-199，R6.5 后 fail-closed 可抛）→ publishEvent（:204）
  // importOrmModels：catch (Exception e) → result.setSuccess(false) + orm().clearSession()（:371-378）
  //   —— clearSession 只清会话缓存，flush 已送出的 SQL 在外部事务提交时照常落库
  ```
- **严重程度**: P1 — 正确性：批量导入中单路径在 `addToIndex`/事件阶段失败时，**DB 行已 flush 且外部事务最终提交**（模块/实体部分落库），但结果 DTO 报 failed、事件缺失、索引部分写入——三态不一致；单路径 `importOrmModel` 失败则 DB 回滚但**已写 Lucene 文档成幽灵**（无对账清扫）；模块/实体**级联删除**不调 removeFromIndex（NopMetaModuleBizModel.delete 无任何索引清理；NopMetaEntityBizModel 只删 MetaEntity 不删 MetaEntityField）→ 搜索永久返回已删实体
- **现状**: 新发现（R6.5 把 addToIndex 改为 fail-closed 后，flush→索引顺序与批量 catch 的交互使该路径从"静默跳过"变为"报错但数据已提交"——组合效应此前未审计）
- **风险**: 调用方按失败结果重试造成重复导入；搜索命中幽灵/残留文档
- **建议**: 导入路径改为 per-path 独立事务（`ITransactionTemplate.runInTransaction`），索引写入移到提交后（outbox/afterCommit）或失败时反向 removeDocs；级联删除前收集子实体 id 并 removeFromIndex；补失败路径三态一致性测试
- **信心水平**: 确定（代码顺序核对）

### [AR-09] [P1] queryJoinData / queryAggregation 无 limit 归一化 —— 负数/超大 limit 触发裸 IllegalArgumentException，缺省无上限

- **文件**: `NopMetaTableBizModel.java:255-297`（queryJoinData/queryAggregation 直传原始 limit）；`AggregationHelper.java:829-837`（truncateCrossDb）、`CrossDbJoinMerger.java:238-249`（truncate）；对照 `NopMetaTableBizModel.java:239, 368-376`（normalizeQueryLimit 仅 queryTableData）
- **证据片段**:
  ```java
  // limit=-5：to = Math.min(rows.size(), from + (-5)) → to < from → rows.subList(from, to) 裸 IllegalArgumentException
  // limit=Integer.MAX_VALUE 且 offset>0：from + limit.intValue() int 溢出为负 → 同样裸异常
  // limit=null：同库 JOIN SELECT 无 LIMIT 子句 → 全量结果集
  ```
- **严重程度**: P1 — 正确性/健壮性：公开 API 对非法参数抛未包装的 `IllegalArgumentException`（违反模块 "显式 ErrorCode" 约定，错误信息不可诊断）；缺省 limit 时同库 JOIN 无界
- **现状**: 新发现（R3.12 只给 queryTableData 加了归一化；`TestMetaJoinTruncateOverflow` 只测 limit > Integer.MAX_VALUE，未覆盖 from+limit 溢出与负数）
- **建议**: 两个入口套用 normalizeQueryLimit；truncate 用 long 运算 + 负数拒绝（ERR_PAGINATION_* 既有错误码可复用）
- **信心水平**: 确定（代码核对）

### [AR-10] [P1] MySQL quarter/week 粒度模板语义错误 —— quarter 桶=月首、week 桶=天

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/GranularityBucketing.java:42-47`
- **证据片段**:
  ```java
  MYSQL_TEMPLATES.put("quarter", "DATE_FORMAT(%s,'%Y-%m-01 00:00:00')");  // ← 月首，非季度首
  MYSQL_TEMPLATES.put("week",    "DATE_FORMAT(%s,'%Y-%m-%d 00:00:00')");  // ← 与 day 逐字节相同
  // H2/PG 用 DATE_TRUNC('quarter'/'week')，正确；MySQL 路径静默分桶错误
  ```
- **严重程度**: P1 — 正确性：MySQL 数据源上 `granularity=quarter` 把同一季度拆成 3 个桶、`week` 退化为天粒度，聚合结果静默错误（无错误无警告）
- **现状**: 新发现（无 MySQL 方言回归测试；H2 测试只覆盖 H2 模板）
- **风险**: BI 报表按错误粒度出数；修复后历史结果与新增结果不可比
- **建议**: MySQL quarter 用 `CONCAT(YEAR(%s),'-',QUARTER(%s)*3-2,'-01')`，week 用 ISO 周模板（`%x-%v`）；补模板级单元测试
- **信心水平**: 确定（模板字符串核对）

---

## P2 发现（记录，不单独驱动 remediation plan）

### [AR-11] [P2] judgeRegex 方言判定启发式过宽 —— MySQL 上真实正则错误被误判 SKIP 静默消失

- **文件**: `MetaQualityRuleExecutor.java:710-718`
- **证据**: `isRegexpUnsupported` 用 `contains("regexp") || contains("syntax") || function&&not found` 子串匹配——MySQL（**支持** REGEXP）上非法 pattern 的报错 "Got error '...' from regexp" 含 "regexp" → 规则被标 SKIP 而非 ERROR，失败规则从 pass/fail 统计中静默消失（P2-08 文档化例外仅覆盖"方言不支持"，此处被意外放大到规则级 bug）
- **建议**: 按方言/签名集合匹配（如 "not supported"、"unknown function"）或按 productName 门控；补 MySQL 非法 pattern 判别测试
- **信心水平**: 很可能

### [AR-12] [P2] 调度器 cpId==null 分支在 try 外抛错且用错错误码 —— 违反 MA7.5-01 "不外抛" 契约

- **文件**: `MetaQualityCheckpointScheduler.java:201-207`
- **证据**: `executeScheduledCheckpoint` 中 `cpId==null` 直接 throw `ERR_CHECKPOINT_SCHEDULER_INVALID_CRON`（消息描述 "cron expression is invalid"）且**在 try 块之外**——一旦命中（遗留/损坏 job 参数），异常逃逸到 invoker 使 job 永久 FAILED，正是 MA7.5-01 要消除的失败模式；错误码也与实际失败（缺 checkpointId 参数）不符
- **建议**: null 分支改走 buildErrorResult + 新错误码（如 missing-checkpoint-id）
- **信心水平**: 确定

### [AR-13] [P2] 质量 SQL 错误码参数名不匹配 + expectPassWhen 错误上下文为字面量占位符

- **文件**: `QualityErrors.java`（ERR_QUALITY_SQL_NO_ROW/FAILED 声明 `{ruleKey}`）、`MetaQualityRuleExecutor.java:619-627`（只设 sql/error，从未设 ruleKey）、`:701-706`（`param(ARG_QUALITY_RULE_ID, "<evalExpectPassWhen>")` 字面量）
- **证据**: 两个 ErrorCode 消息占位 `{ruleKey}` 恒无法解析（渲染出字面 `{ruleKey}`），实际更相关的 sql 参数未声明；expectPassWhen 配置错误时所有规则都报告 `qualityRuleId=<evalExpectPassWhen>`，错误不可归属
- **建议**: 补 `.param(ARG_RULE_KEY, ruleKey)`（queryLong 调用链需线程化规则上下文）或改 ErrorCode 声明；evalExpectPassWhen 线程化 ruleKey
- **信心水平**: 确定

### [AR-14] [P2] 抛异常规则不写结果行 → autoScore 复用陈旧结果；skipCount/executedCount 语义不可对账

- **文件**: `MetaQualityCheckpointExecutor.java:155-163`（catch 只入 errors 不落盘）、`MetaQualityScorer.java:129-145`（findLatestResult 取到前一轮旧行）、`CheckpointExecutionResultDTO`（totalRuleCount/ruleResults 从不填充）、`NopMetaQualityRuleBizModel.java:257,269`（batch executedCount 只计不抛规则，与 checkpoint 语义不同）
- **证据**: 规则抛异常（数据源宕机/自定义 SQL 被拒）→ 无 ERROR 结果行 → 触发自动评分时该规则取"上一次"的行（可能是旧 PASS）→ 失败运行后评分仍显健康；skip 计数进 summary 但 DTO 无字段；batch 与 checkpoint 的 executedCount 口径不一致
- **建议**: 抛异常规则补写 ERROR 结果行（与 in-band ERROR 判定一致），或该表评分标记 scoreSkipped；DTO 补 skipCount/totalRuleCount
- **信心水平**: 确定

### [AR-15] [P2] freshness 负年龄恒 PASS（时钟偏移/未来时间戳掩盖新鲜度违约）

- **文件**: `MetaQualityRuleExecutor.java:720-725, 238-241`
- **证据**: `ageMinutesFromNow = (now - ts)/60000` 可为负（DB 时钟超前/未来时间戳），`pass = ageMinutes <= maxAgeMinutes` → 负值无条件通过
- **建议**: 钳制 `Math.max(0, ...)` 或 details 暴露原始差值
- **信心水平**: 确定

### [AR-16] [P2] 完整 SQL（含 custom_sql 字面量）以 INFO 级写日志

- **文件**: `MetaQualityRuleExecutor.java:616, 632, 654`
- **证据**: `LOG.info("qualityRule SQL: {}", sql)` / `LOG.info("qualityRule custom_sql: {}", sql)`——custom_sql 文本内嵌的敏感字面量（姓名/卡号等）随规则执行落日志（R6.2 刚把 rawJdbcUrl 从错误参数剔除，本处同类面未治理）
- **建议**: 改记 sqlHash 或降 DEBUG
- **信心水平**: 确定

### [AR-17] [P2] R6.3 per-key 锁 + REQUIRES_NEW 改变了 syncExternalTables 的原子性契约（未文档化）

- **文件**: `NopMetaDataSourceBizModel.java:508-519`（upsertExternalTableGuarded）
- **证据**: 每表独立 REQUIRES_NEW 提交后，外部连接在扫描中途失败（非 per-table 异常）时：此前"整批回滚、无任何表落库"，现在**已同步的表持久化、异常上抛、事件缺失**——调用方按失败处理并重试时出现部分状态；模块文档 §失败路径 只声明 per-row 隔离，未声明此原子性变化
- **建议**: 文档化该语义（或 catch 后返回部分成功结果 + 显式警告）；事件发布移到提交后
- **信心水平**: 很可能

### [AR-18] [P2] 导入内容手拼 JSON 不转义 + buildSql 反序列化未类型化（裸 ClassCastException）

- **文件**: `OrmModelImporter.java:208-216`（buildJoinConditionsJson 字符串拼接）、`MetaTableFieldResolver.java:340-359`
- **证据**: join left/right 值含 `"`/`\` 时产出非法 JSON 静默入库；`(List<Map<String,Object>>) parsed` 后元素非 Map 时 `col.get(...)` 裸 ClassCastException（违反显式 ErrorCode 契约）
- **建议**: JsonTool.stringify 构造；逐元素 instanceof 校验 + ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID
- **信心水平**: 确定

### [AR-19] [P2] 跨库内存过滤与 SQL 路径语义漂移（NULL 比较 / LIKE 正则转义 / 空 or 节点）

- **文件**: `MemoryFilterEvaluator.java:101-110`（LIKE `%`→`.*`、`_`→`.` 后直接 matches，字面量中的 `.`/`(`/`+` 等元字符未转义）、`:223-246`（null 与任何值比较返回 -1/1 → `HAVING x < 100` 在聚合为 NULL 时内存路径保留行、SQL 路径排除）、`:171-181`（空 or 节点内存=FALSE，SQL=无过滤 TRUE）
- **证据**: 同一条 HAVING 在跨库内存路径与同库 SQL 路径产生不同结果集（如 LIKE 匹配面扩大、NULL 聚合组保留与否相反）
- **建议**: Pattern.quote 后再替换 %/_；比较含 null 一律 false（仅 is-null/not-null 判空）；evalAny(empty)=true
- **信心水平**: 确定（对照 FilterToSqlTranslator 语义）

### [AR-20] [P2] MySQL 上 NULLS FIRST/LAST 无条件拼接（语法错误）+ 跨库 join 键类型精确类比较（INT vs BIGINT 误拒）

- **文件**: `AggregationHelper.java:342-345`（无方言判断）、`CrossDbJoinMerger.java:122-159`
- **证据**: `orderBy` 显式 nullsFirst 在 MySQL 上生成非法 SQL；join 键左 INTEGER(JDBC Integer) 右 BIGINT(Long) 数值相等但 `leftType.equals(rightType)` 拒绝
- **建议**: MySQL 方言抑制或改写为 IS NULL 排序；Number 类型按 BigDecimal 比较
- **信心水平**: 确定/很可能

### [AR-21] [P2] R6.4 fail-loud 在自动化标注路径被 catch-all 吞掉 + 全局回退分类

- **文件**: `AutoClassificationProcessor.java:260-271`、`LineageTagPropagationProcessor.java:177-188`（`catch (Exception e) { LOG.error(...); return null; }`）、`AutoClassificationProcessor.java:218-228`（无绑定分类时回退 lexicographically-first enabled 全局分类）
- **证据**: 标签 save 触发自动提审（R6.4 后提审失败可抛 ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED）时异常被 processors 吞掉——标签以 Suggested 落库但审批流静默不建，恰是 R6.4 要修的静默面在自动化路径上的残留；无 Manual/派生分类时把全系统首个启用的分类规则套到任意表上
- **建议**: processors 聚合失败并向上返回/抛错；移除全局回退或要求显式绑定
- **信心水平**: 很可能

### [AR-22] [P2] SLA 未知时间单位静默按毫秒解析（week 变 1ms → 恒 stale）

- **文件**: `MetaContractChecker.java:353-355`
- **证据**: `toDurationMillis` default 分支 `tu = TimeUnit.MILLISECONDS`——`{"interval":1,"unit":"week"}` → 1ms → collectionStale 恒 true → 永久 FAIL；管理员配置笔误无任何错误信号
- **建议**: 未知单位抛 ERR_CONTRACT_SLA_INVALID
- **信心水平**: 确定

### [AR-23] [P2] 杂项批（登记，不展开）——delete 先摘 cron 后提交（删除失败丢调度）；索引重建不清陈旧文档/refresh 失败仅 warn/搜索 limit 负数直通引擎；ExternalTableStructureReader 扫描异常统一报 ERR_DIALECT_NOT_SUPPORTED + NULL 精度归 0；reconciliation 每行全量候选池 + 无长度上限的 levenshtein；profiler 整列载入内存；模块版本 read-then-insert 无唯一约束竞态；manifest 跨 DRAFTING 模块解析；事件快照 Map 分支跳过敏感列脱敏（潜在）

- **文件**: `NopMetaQualityCheckpointBizModel.java:265-292`、`NopMetaIndexBuilder.java:100-112, 39-120`、`NopMetaSearchBizModel.java:66-82`、`ExternalTableStructureReader.java:81-84, 155-158`、`LocalReconciliationProcessor.java:92-102, 137-163`、`MetaTableProfiler.java:260-275`、`NopMetaModuleBizModel.java:545-558`、`MetaManifestBuilder.java:71-78`、`MetaModelChangedEventPublisher.java:202-203`
- **证据**: 均为确认存在的低概率/维护性缺陷（详见各文件行号），单项不构成 plan 触发条件
- **建议**: 归入后续 backlog 跟踪
- **信心水平**: 确定（代码核对）

---

## 已核验为安全/已修复（不重复报告）

- 06:55 两轮报告的 5 P1：HostSecurityUtil 已双侧接线（AR-02/03 为其**新变体**，见上）、血缘 API fail-loud 已生效（`checkNoParseErrors` + 空 SQL 统一 ERR_LINEAGE_SQL_SOURCE_EMPTY）、文档漂移三件已修（实体表 39/39、META-004 枚举、I*Biz 例外声明）、11 个 P1 已登记追踪矩阵
- R6.1-R6.6 修复面：custom_sql 黑名单 23 词（P2-10）、webhook 重定向 fail-closed + rawJdbcUrl 脱敏（P2-11/12）、per-key 锁 + createSqlTable 守卫（AR-07/08）、fail-loud 三件（P2-06/07/09）、异常链/日志（P2-01/02/04）、21 死码删除（P2-03）——逐项 live 复核属实
- HAVING 注入面（R3.1）、checkTableExists（R6.4）、R4.3 幂等三层（进程内锁 + runId + UK）——复查无新绕过
- `0x7f.0.0.1`/`0177.0.0.1` 非可利用（JDK 严格十进制）——维持此前勘误
- `127.0.0.1.`/`10.0.0.1.`/`192.168.1.1.`/`169.254.x.x.` 尾点形式被前缀比对拦截——非绕过

## 总评

mission 修复管线工程质量整体仍高（R6 各轮修复 + 回归测试 + closure audit 全绿 923/0），但本次复检暴露三个值得立即关注的方向：

1. **SSRF 家族在"统一工具"后仍有活口（AR-02/03）**：R5.1 用 HostSecurityUtil 统一了两侧语义，但"统一"只覆盖了审计过的向量——无括号 IPv6（驱动实测可建连 loopback）与 FQDN 尾点 `localhost.` 均未归一化，且无一测试覆盖。教训：安全修复的验证链需要"修复提交后复测原向量 + 变异向量"两步，本轮再次验证此模式。
2. **已声明 fixed 的项存在虚假关闭（AR-06）**：MA7.6-05 的 commit message 声称修复、roadmap 标 done、closure audit PASS，但 diff 只有版权头删除。这是本 mission 自 06:55 AR-05（审计未回流）之后的第二种治理断裂形态——"提交信息声明 ≠ 代码变更"。建议对全部 R3.x 声称 fixed 的 P2 项做 diff 存在性抽查。
3. **分页/参数治理只做了一半（AR-01/09）**：R3.12 只修了 queryTableData，JOIN 路径的双绑 bug（P0，必炸）与无归一化（负 limit 裸异常）完全未被测试矩阵触及——测试类名 "ExternalJoinAndPagination" 实际从不传分页参数。这提示 mission 的"修复面验证"过度依赖既有测试文件名称，而非行为覆盖。

## 本次审查的盲区自评

- 未独立重跑 `./mvnw test`（依赖 R6.6 的 923/0 记录；本次为纯代码阅读 + 定向驱动实测）
- 未逐文件核对 web 模块 view.xml 与 xbiz 的 GraphQL 消费方式（AR-01 的 UI 触发面未实证）
- 未验证 MySQL 上 NULLS FIRST/LAST 与 quarter/week 模板的真实报错/分桶行为（无 MySQL 实例）
- 未对 AR-08 的索引幽灵文档做 Lucene 层实证（依赖代码顺序推导）
- 未审计 nop-metadata-app 运行时配置与多实例部署面
- reconciliation/profiler 的性能项（AR-23）仅静态确认，未做规模实测

## 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | JOIN 分页双绑必炸（公开 API 契约断裂） |
| P1 | 9 | SSRF 变体 ×2（IPv6 无括号 / localhost. 尾点）、custom_sql 沙箱 DML + H2 文件族 ×2、SLA 虚假关闭 ×1、schema 默认值漂移 ×1、导入事务/索引一致性 ×1、limit 归一化 ×1、MySQL 粒度模板 ×1 |
| P2 | 13 | 判定/诊断/语义漂移/杂项（AR-11~23） |
| P3 | 0 | — |

---

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
