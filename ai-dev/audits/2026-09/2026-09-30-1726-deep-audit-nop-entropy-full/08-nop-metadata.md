# G8: nop-metadata 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计人**: 首轮审计子代理（维度 04 / 07 / 09 / 13 / 16）
- **范围**: `nop-metadata/` 全部子模块（449 个 Java 文件：main 280 / test 169），排除 `target/`、`_` 前缀生成文件
- **基线**: 主 agent grep 基线（裸异常 11 处、System.out 少量、@Inject private 0 处）+ 本轮逐条 live code 核实

## 审计范围

深读的优先文件（维度 13 重点面 + 各维度抽样）：

- **查询构造链**: `MetaAggregationExecutor` / `AggregationHelper` / `FilterToSqlTranslator` / `ExternalAggregationProcessor` / `EntityAggregationProcessor` / `EntityEntityJoinAggregationProcessor` / `ExternalExternalJoinAggregationProcessor` / `MixedSameDbJoinAggregationProcessor` / `CrossDbInMemoryAggregationProcessor` / `MemoryFilterEvaluator` / `MemoryOrderByComparator` / `GranularityBucketing` / `SqlPagination` / `MetaJoinExecutor` / `MetaEntityQueryExecutor` / `NopMetaEntityQueryAction` / `NopMetaEntityBizModel`
- **安全面**: `MetaDataSourceConnectionProcessor`（JDBC SSRF 全链）/ `HostSecurityUtil` / `CheckpointActionDispatcher`（webhook）/ `MetaQualityRuleExecutor`（custom_sql 沙箱）/ `ExpressionMeasureValidator` / `MetaModelChangedEventPublisher`（快照脱敏）
- **调度/血缘/对账/同步**: `MetaQualityCheckpointScheduler`（cron）/ `MetaQualityCheckpointExecutor` / `NopMetaQualityCheckpointBizModel`（R4.3 运行标记）/ `SqlColumnLineageExtractor` / `SqlSourceEntityExtractor` / `SqlSelectFieldExtractor` / `SqlViewFieldTypeInferrer` / `ReconciliationExecutor` / `LocalReconciliationProcessor` / `NopMetaReconciliationConfigBizModel`
- **维度 04**: `nop-metadata/model/nop-metadata.orm.xml` 全文核查（38 实体：PK 38/38 `tagSet="seq"`、67 UK、98 index、33 `ext:dict`、级联删除逐条有裁定注释）
- **维度 07**: 40 个 `@BizModel` 类逐一 grep 核查 `setEntityName` + `implements I*Biz`；`INopMetaEntityBiz` 等接口注解抽查；`NopMetaTagLabelBizModel` GLOSSARY 守卫
- **维度 09**: 裸异常 11 处逐条核实（全部在 src/test）；中文消息/字符串构造器异常 grep 零命中；`NopMetadataException` 四构造器齐备；运行 `check-error-param-consistency.mjs --module nop-metadata`（0 violation）
- **维度 16**: 158 个测试类清单 + 安全测试密度核查（HostSecurityUtil 24 / WebhookSsrf 29 / ConnectionSecurity 50 / CustomSqlSandbox 22 个 @Test）+ 守卫类测试（`TestNopMetaBizInterfaceCompleteness` 等）

**零发现维度说明**：

- **维度 04（ORM 模型与实体设计）— 零发现**。逐项核查：主键全部 `tagSet="seq"` VARCHAR(32)；status/type 类列均有 `ext:dict`（35 处，dict 文件与 `_vfs/dict/meta/` 20+ 个 yaml 对应）；displayName 中英双语齐备（`i18n-en:displayName`）；审计字段（createTime/updateTime/createdBy/updatedBy/version）38 实体齐备；索引覆盖所有高频查询面（`NopMetaEntityField.metaEntityId`、`NopMetaQualityResult.(qualityRuleId,executeTime)`、`.(runId,executeTime)` 等逐表有）；级联删除每处有 Why 注释（baseModuleId 自引用明确不级联防误删模块树；`checkpointId` soft-FK 显式不级联 = P2-26 裁定）。未发现可报告问题。
- **维度 07（BizModel 规范遵循）— 零发现**。40/40 BizModel 构造函数调 `setEntityName` 且 `implements` 对应 I*Biz；接口方法注解齐全（`@BizQuery`/`@BizMutation` + `@Name`，末参 `IServiceContext`）；`queryJoinData`/`queryAggregation` 超参数量为 owner doc P2-25 显式裁定例外；scheduler 注入 raw impl 为维度 07-02 显式裁定；无伪 BizModel（`NopMetaSearchBizModel` 例外已在 owner doc 登记）。

## 发现

### [G8-13-01] custom_sql 沙箱黑名单族缺口：裸 `INTO` token 与 `PG_TERMINATE_BACKEND` 未收口（与兄弟校验器不对称）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:100-125`
- **证据片段**:
  ```java
  private static final Set<String> CUSTOM_SQL_FORBIDDEN_WORDS = unmodifiableSet(
          "UNION",
          "LOAD_FILE",
          "CALL", "EXEC", "EXECUTE",
          "SHUTDOWN",
          "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE", "RENAME",
          "INSERT", "UPDATE", "DELETE", "MERGE", "REPLACE",
          "COMMIT", "ROLLBACK", "SAVEPOINT", "SET", "TRANSACTION",
          ...
          "DO", "PG_SLEEP", "PG_CATALOG", "PG_STAT_USER_TABLES",
          "WITH");

  /** 多 token 危险序列（归一化分词后的连续 token 序列）。 */
  private static final String[][] CUSTOM_SQL_FORBIDDEN_SEQUENCES = {
          {"INTO", "OUTFILE"},
          {"INTO", "DUMPFILE"},
          ...
  ```
  对照兄弟校验器 `ExpressionMeasureValidator.java:86`（单 token `"INTO", "OUTFILE", "DUMPFILE"`）与 `ExpressionMeasureValidator.java:116`（`"PG_TERMINATE_BACKEND"`）——两项在 expression 侧均被拦截，custom_sql 侧缺失。
- **严重程度**: P2
- **现状**: custom_sql 的 token 级黑名单只拦截 `INTO OUTFILE`/`INTO DUMPFILE` 两个**序列**，裸 `INTO` token 不在单 token 黑名单中；`PG_TERMINATE_BACKEND` 完全缺席。PostgreSQL 的 `SELECT c INTO new_table FROM t` 是 `CREATE TABLE AS` 的同义 DDL（不出现 `CREATE` token），token 化后为 `SELECT C INTO NEW_TABLE FROM T`——无单 token 命中、无序列命中，沙箱放行；`SELECT count(*) FROM pg_stat_activity WHERE pg_terminate_backend(pid)` 同样无命中。`querySingleValue` 虽用 `executeQuery()`，但驱动侧"无结果集"异常发生在服务端**已执行之后**，DDL/杀连接副作用已生效（MySQL `SELECT ... INTO @var` 会话变量写入同理，影响较小）。
- **风险**: 持有质量规则配置权限（`NopMetaQualityRule` mutation）的用户可绕过沙箱"拒绝一切 DDL/DML"的显式契约（类注释安全边界声明"条目集合与 ExpressionMeasureValidator.KEYWORD_BLACKLIST 逐项对齐"），在外部数据源账号上创建/驻留任意表（存储耗尽/持久化攻击痕迹）或终止其他后端连接（DoS，可杀掉业务库连接）。
- **建议**: 在 `CUSTOM_SQL_FORBIDDEN_WORDS` 增补单 token `INTO`（与 R6.1 已在 expression 侧落地的拆分裁定同款——custom_sql 上下文中 `INTO TABLE`/`INTO @var` 均非合法只读检查语句形态，over-block 符合既定哲学）与 `PG_TERMINATE_BACKEND`；同时在 `TestMetaQualityRuleExecutorCustomSqlSandbox` 增补 `SELECT c INTO evil FROM t` 与 `pg_terminate_backend` 拒绝向量。
- **信心水平**: 很可能（黑名单不对称与 token 化行为由 live code 直接确证；`SELECT INTO` 在 PG 的 DDL 语义为标准文档行为，未起 live PG 实测 executeQuery 的驱动报错时序）。
- **误报排除**: 不是"黑名单永远列不完"的泛化抱怨——这两项是同一文件族内**兄弟校验器已收口、本校验器宣称逐项对齐却缺失**的具体缺口（F9/R6.1 裁定明确主张两侧对齐），属契约违约而非完备性哲学问题。
- **复核状态**: 未复核

### [G8-09-01] queryData 执行失败路径把完整 SQL（含 sensitive 标记的 sourceSql 全文）作为超额 param 带入异常消息

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaEntityQueryExecutor.java:142-147`
- **证据片段**:
  ```java
  } catch (SQLException e) {
      throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_SQL_EXEC_FAILED, e)
              .param(NopMetadataErrors.ARG_META_ENTITY_ID, metaEntityId)
              .param("sql", sql)
              .param(NopMetadataErrors.ARG_ERROR, messageOf(e));
  }
  ```
  对照 `ERR_QUERY_SQL_EXEC_FAILED` 的 define（`DataSourceErrors.java:108-111`）只声明 `ARG_META_ENTITY_ID, ARG_ERROR` 两占位符；同模块先例 `MetaQualityRuleExecutor.queryLong`（`MetaQualityRuleExecutor.java:699-703`）为同一脱敏目标**显式移除**了完整 SQL param，理由原文："不设完整 SQL 参数（NopException.getMessage 会无条件拼入 params，完整 SQL 落日志与 R8.2 AR-16 脱敏目标冲突）"。
- **严重程度**: P3
- **现状**: sql 视图路径的 `queryData`/`querySqlData` 失败时，`sql` 是 `SELECT * FROM (<sourceSql>) _t ...`——`SOURCE_SQL` 列在 ORM 中标记 `tagSet="sensitive"`（`nop-metadata.orm.xml:648`，理由是"SQL 可内嵌敏感字面量"）。该全文作为**未声明超额 param** 传入后，`NopException.getMessage()` 无条件拼入 params，完整 SQL（连同 sourceSql 内嵌字面量）落入 ERROR 级异常日志。
- **风险**: 与模块自身的 AR-13/AR-16/P1-8 脱敏裁定（"INFO 只记 sqlHash、全文降 DEBUG、持久化面只落 sqlHash"）在错误日志面留下缺口；且 INV-ERROR-PARAM 守卫（`check-error-param-consistency.mjs`，本轮实测 0 violation 通过）只校验"占位符→param 缺失"方向，结构性看不见"超额 param"，该缺口不会被现有门禁捕获。
- **建议**: 删除 `.param("sql", sql)`，对齐 `queryLong` 形态改为 `.param(ARG_SQL_HASH, MetaQualityRuleExecutor.sqlHashOf(sql))`（需同步在 define 声明 `{sqlHash}` 占位符）；或给守卫脚本补"超额 param"检测方向。
- **信心水平**: 确定（超额 param 与 define 声明不一致、getMessage 拼 params 行为均为模块内文档化事实）。
- **误报排除**: 不是"错误信息越全越好"的反向误报——模块已在同类位置（queryLong/queryTimestamp/judgeCustomSql）三处收敛为 sqlHash 形态，此处是同语义家族的漏网点；也不与 `MiscErrors` 中 `{sql}` 占位族冲突（catalog/profiling 的 SQL 只含白名单标识符、无用户字面量，且其 define 显式声明了 `{sql}`）。
- **复核状态**: 未复核

### [G8-16-01] custom_sql 沙箱测试缺黑名单族对齐回归向量，两校验器"逐项对齐"主张无程序化守卫

- **文件**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestMetaQualityRuleExecutorCustomSqlSandbox.java:44-60`
- **证据片段**:
  ```java
  String[] dangerousPayloads = {
          "SELECT 1; DROP TABLE users",                                  // 分号 + DROP
          "SELECT * FROM users UNION SELECT password FROM mysql.user",   // UNION
          "SELECT * FROM users INTO OUTFILE '/tmp/leak'",                // INTO OUTFILE
          "SELECT * FROM users INTO DUMPFILE '/tmp/leak'",               // INTO DUMPFILE
          "LOAD DATA INFILE '/tmp/leak' INTO TABLE users",               // LOAD DATA
          "SELECT LOAD_FILE('/etc/passwd')",                              // LOAD_FILE
          ...
  ```
  25+ 拒绝向量中无一条是裸 `SELECT ... INTO <new_table>`（PG 建表 DDL 变体），也无 `pg_terminate_backend` 调用形态向量。
- **严重程度**: P3
- **现状**: 沙箱测试对既有黑名单条目覆盖密集（22 个 @Test，含空白/注释/反引号变体钉死），但对"两个黑名单集合的族对齐"（`MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS` vs `ExpressionMeasureValidator.KEYWORD_BLACKLIST`/`FUNCTION_BLACKLIST`，F9 裁定注释主张"重叠项 22 项逐项对齐"）没有程序化断言——对齐是人工维护的注释级承诺，一侧增补另一侧漏补时无测试变红。
- **风险**: [G8-13-01] 类家族缺口（本次为 INTO/PG_TERMINATE_BACKEND，下次为新的方言副作用函数）不会被任何测试或守卫捕获；check2 P1（2026-08-23）补 H2/PG 文件族时两侧同批补齐纯靠人工纪律，回归风险随方言函数面增长单调上升。
- **建议**: 增加一个对齐断言测试（如 `CUSTOM_SQL_FORBIDDEN_WORDS ⊇ FUNCTION_BLACKLIST ∩ 可 SELECT 内联调用集`，或最小化为双向 diff 报告测试），并在拒绝向量中补 `SELECT c INTO evil FROM t` / `SELECT pg_terminate_backend(1)` 两条。
- **信心水平**: 确定（测试向量清单与两个黑名单集合均已逐一核对）。
- **误报排除**: 不是 P-8"无效负面测试"——现有向量本身有效；本条针对的是**缺失的向量与缺失的对齐守卫**（回归保护结构缺口），与 [G8-13-01] 是同根因的两面（一个报 main 缺口、一个报测试缺口），分开报告以便独立复核与排期。
- **复核状态**: 未复核

### [G8-13-02] `CrossDbConfigHolder.maxCrossDbRows` 为 public static 可变全局量，防 OOM 上限不可配置且无写入方约束

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbConfigHolder.java:3-9`
- **证据片段**:
  ```java
  public class CrossDbConfigHolder {
      /** 跨库拼接单侧结果集行数上限（防 OOM，超限显式失败）。 */
      public static int maxCrossDbRows = 10000;

      private CrossDbConfigHolder() {
      }
  }
  ```
  消费点：`MetaJoinExecutor.java:74,404,436`、`CrossDbJoinMerger.java:29`；main 代码无任何写入点（仅测试构造小上限 merger 时经构造函数注入，不写静态字段）。
- **严重程度**: P3
- **现状**: 跨库 JOIN 的单侧取数上限（10000 行，内存合并防 OOM 的核心参数）是非 final 的 `public static int`：既不可像同类上限 `nop.metadata.query.max-limit`（`@InjectValue`）、`nop.metadata.reconciliation.fetch-limit` 那样部署配置，又暴露了可变全局状态（任何未来写入都会静默全局生效）。
- **风险**: 部署侧无法按内存预算调优跨库取数上限（想要 2000 行保守值或放宽只能改代码重发）；public static 可变字段形态与模块其余配置面（`@InjectValue` 注入 + protected 字段 + setter 覆盖）不一致，是并发安全上的隐患形态。
- **建议**: 改为 `@InjectValue("@cfg:nop.metadata.query.max-cross-db-rows|10000")` 注入 `MetaJoinExecutor`（沿 `CrossDbJoinMerger(int)` 构造注入既有通道），静态 holder 保留 final 常量缺省值；或最小化改为 `public static final int` 并接受不可配置。
- **信心水平**: 确定（字段形态与消费/写入点均已 grep 核实）。
- **误报排除**: 不是"魔法数字"风格洁癖——该值是安全/资源上限族的一员，同模块同语义上限（max-limit、fetch-limit、h2-file-allowed-dirs）全部走配置键，唯独此项硬编码且可变；列入"纯风格 ≤3 条"额度内。
- **复核状态**: 未复核

## 重点核验项结论（标准检查项，live code 复核）

1. **HAVING 注入 / 白名单双向断言（维度 13 标准检查项 9）— 通过**。`AggregationHelper.nameResolverFor`（`AggregationHelper.java:297-324`）：命中 `nameToExpr` 白名单 → 返回聚合表达式；未命中且无 `HAVING_EXPR_RESOLVED_ATTR` 标记 → `ERR_AGGR_HAVING_UNKNOWN_NAME` 显式失败，**无"未命中即透传"回退分支**。`MetaAggregationExecutor.preprocessHavingArithmetic`（F1 修复在位）递归入口先 `removeAttr(HAVING_EXPR_RESOLVED_ATTR)` 清除客户端可伪造标记，仅 expr 路径逐 token 白名单替换后重新置位；`substituteAndValidateHavingExpr` 对最终 SQL 再过 `ExpressionMeasureValidator.validateStatic` + 禁字面量。全部 7 条聚合路径（单表 external/entity/sql × 3 + join 3 + cross-db 内存 1）逐一核实 preprocess→translate 顺序正确；`collectBindParams` 二次 translate 复用已变异树，语义一致。ORDER BY（`buildOrderByClause` 与 `MemoryOrderByComparator`）均为纯白名单反查、未命中显式失败，无透传分支。
2. **SSRF 主机解析规范化（维度 13 标准检查项 8）— 通过**。`extractHosts`/`extractWebhookHost`：userinfo 按 `lastIndexOf('@')` 剥离（含 `@` 口令旁路）、方括号/无括号 IPv6、`::ffff:` IPv4-mapped 归一化、十进制/短格式/前导零十进制（严格十进制语义）、`0x` 十六进制 fail-closed 超集、FQDN 尾点归一化均覆盖；`HostSecurityUtil.isInternalIpv6Literal`/`isIpLiteral` 有 charset 前置过滤（F8：不触发 DNS）；多主机（逗号/address=/key-value/query `host=`/`hostaddr=`）逐主机校验（F2 + F2 再审计在位）；hostless 属性组哨兵 fail-closed；空/畸形主机形状 enforced 拒绝（F7/P2-06）。JDBC 协议白名单 + 危险参数 blocklist（F5）+ driverClassName 白名单 + H2 file 路径目录白名单（check2 P2）均在位。
3. **webhook 回调校验 — 通过**。协议白名单 http/https、method 白名单 POST/PUT、主机白名单 + 形状校验、重定向 fail-closed（全局跟随开启时拒绝投递 + 3xx 显式归类失败）、显式 timeout（`CheckpointActionDispatcher.java:224-281`）。
4. **cron 调度入口 — 通过**。`MetaQualityCheckpointScheduler.executeScheduledCheckpoint` 捕获 checkpoint 级异常防 job 永久 FAILED（MA7.5-01 在位）、并发拒绝降级 WARN（R4.3）；cron 触发经 raw impl 调 `executeCheckpoint(checkpointId, null, null)`，null context 为核定语义，BizModel 入口（GraphQL）仍走 `requireEntity` 权限链；per-checkpoint 运行标记（`ConcurrentHashMap.putIfAbsent` fail-fast）覆盖 executor+autoScore+dispatch 全程。
5. **SQL 视图 / syncExternalTables — 通过**。`createSqlView` 强制单条 SELECT（多语句/非 SELECT/通配符显式拒绝，`SqlSelectFieldExtractor.java:79-93`）；queryData 三路径（physical=ORM QueryBean 参数化、external=标识符白名单+参数绑定、sql=包装子查询）；`externalTableFromForJoin`/`buildFromClause` 对 tableName/alias/schema 全部过 `IDENTIFIER_PATTERN`；对账取数上限 fetchedLimit/truncated 可见（check2 P2 在位）。
6. **错误处理机械基线 — main 代码干净**。11 处裸异常全部位于 src/test（按口径不报）；`System.out`/`printStackTrace` main+test 均零命中；无中文错误消息、无字符串构造器异常滥用；批处理 per-element catch 全部 `LOG.warn/error(..., e)` 带 throwable 末参；`IoHelper.safeClose*` 用于资源关闭。

## 总评

nop-metadata 是一轮"防御纵深密度显著高于仓均"的模块：查询构造链上的标识符白名单、参数绑定、HAVING 双向断言、SSRF 逐主机校验、custom_sql 沙箱、快照脱敏等契约在 live code 中全部核实到位，且配有 158 个测试类（安全面 125+ 个 @Test）与程序化守卫（接口完整性、INV-ERROR-PARAM、invariant-guards）。本轮未发现 P0/P1：历史高危面（MA7.1-01 HAVING、MA7.2-01 SSRF）的修复经 live code 独立复验确认在位。剩余缺口集中在 custom_sql 黑名单与兄弟校验器的**族对齐残余**（P2×1）与错误日志脱敏的一处漏网点（P3×1），均为低侵入可排期修复。

## 统计

| 维度 | 发现数 | P0 | P1 | P2 | P3 |
|------|--------|----|----|----|----|
| 04 ORM 模型与实体设计 | 0 | 0 | 0 | 0 | 0 |
| 07 BizModel 规范遵循 | 0 | 0 | 0 | 0 | 0 |
| 09 错误处理与错误码 | 1 | 0 | 0 | 0 | 1 |
| 13 安全与权限模型 | 2 | 0 | 0 | 1 | 1 |
| 16 测试覆盖与质量 | 1 | 0 | 0 | 0 | 1 |
| **合计** | **4** | **0** | **0** | **1** | **3** |

注：本轮遵循"宁缺毋滥"口径，仅报告经 live code 逐条核实的发现；本模块经多轮审计-修复循环（代码内 F1-F9/AR-01~23/P2-XX 裁定注释密集在位），首轮产出低于 8 条属诚实结果，未以风格项凑数（纯风格项仅 [G8-13-02] 一条）。

## 子项复核结论

复核人：独立复核代理 R3（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G8-13-01] | 保留（维持 P2）| 缺口本体逐项核实成立：`MetaQualityRuleExecutor.java:100-115` 的 `CUSTOM_SQL_FORBIDDEN_WORDS` 无裸 `INTO`、无 `PG_TERMINATE_BACKEND`；`:118-125` 的序列表仅 `{"INTO","OUTFILE"}`/`{"INTO","DUMPFILE"}` 两族；token 化（`:421-436`，按 `[^A-Za-z0-9_]+` 切分）下 `PG_TERMINATE_BACKEND`（含下划线）成单 token 且不在名单，`SELECT C INTO EVIL FROM T` 无任何单 token/序列命中——两个 bypass 向量在 `validateCustomSqlSandbox`（`:388-411`）逐条重放确认放行。兄弟校验器不对称核实：`field/ExpressionMeasureValidator.java:86`（`"INTO", "OUTFILE", "DUMPFILE"` 单 token）与 `:116`（`"PG_TERMINATE_BACKEND"`）确实收口。执行时序核实：`querySingleValue`（`:738-743`）PreparedStatement + `executeQuery`，PG `SELECT INTO` 的服务端建表先于驱动报错，副作用已生效。一处定性微调（不影响判级）：原发现"契约违约"框架略有过申——`MetaQualityRuleExecutor.java:66-69` 的"逐项对齐"声明实际限定于 DML/TCL 族 + RENAME/LOCK/UNLOCK，且 `:83-91` 的 F9 DRY 裁定明确两集合**有意分离**、各自演进，故更准确的定性是"沙箱自身'拒绝一切 DDL/DML'契约（javadoc `:60-62` 演进登记）下的具体收口缺口 + 与兄弟校验器在同一方言族上的不对称"，而非"宣称逐项对齐却违约"。缺口、风险、修复建议（补 `INTO`/`PG_TERMINATE_BACKEND` 两 token + 拒绝向量测试）均成立，维持 P2。 |
