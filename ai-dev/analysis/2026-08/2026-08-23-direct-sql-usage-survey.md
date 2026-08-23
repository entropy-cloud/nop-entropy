# fix-ai-check worktree 直接 SQL 使用全景调研：为什么用 SQL、能否用 EQL/QueryBean/dao 替代

> Status: resolved
> Date: 2026-08-23
> Scope: `nop-entropy-fix-ai-check` worktree 全部主代码（`*/src/main/java`），框架层与应用层
> Conclusion: 直接 SQL 分三类：框架基础设施（SQL 即其存在本身）、应用层有明确并发/事务语义裁定的原子语句（保留并写明原因）、无并发语义且已造成会话脱同步/路径错配的普通读写（2 组共 6 处，应改为 EQL/实体写，由 plan 2255 执行）

## Context

- 用户要求：排查 fix-ai-check worktree 中所有直接使用 SQL 的位置，回答"为什么要用 SQL"，评估能否用 EQL / QueryBean / `dao.tryUpdateWithVersion` 等替代；必须用 SQL 的写明原因。
- 该 worktree 检出分支 `fix-ai-check`（32 commits ahead / 14 behind master），包含大量 AI 修复提交及其基线上的 feature 代码（nop-auth MFA 家族、nop-datav、nop-credential、nop-ai 等均在 merge-base 中存在）。
- 调查方法：对全部 `*/src/main/java` 扫描 `IJdbcTemplate` 注入点、`SQL.begin()`、`new SQL(`、SQL 关键字字面量；逐文件读上下文判定语义；对每个"可替代"候选核对平台替代能力（EQL 编译器、`DaoQueryHelper`、`IOrmEntityDao.tryUpdateWithVersionCheck`）。

## 平台执行模型：同一段 SQL 文本的两条执行路径（判定基准）

`io.nop.core.lang.sql.SQL` 只是 SQL 文本载体，执行语义取决于执行器：

| 执行器 | 语义 | 名称解析 | 行为差异 |
|---|---|---|---|
| `IJdbcTemplate.xxx(SQL)` | **原生 SQL**，直连物理表 | 物理表名/列名 | 不经实体模型：无逻辑删除过滤、无租户列自动追加、绕过 ORM 一级缓存与会话脏跟踪 |
| `orm()/session.xxx(SQL)`（`IOrmTemplate` 实现 `ISqlExecutor`） | **EQL**，经 `JdbcQueryExecutor` → `EqlCompiler` 编译 | 实体全名/短名（如 `NopAiChatResponse`）；物理下划线名需 `SQL.begin().allowUnderscoreName()` 显式开启（`SessionFactoryImpl` 缺省 `false`），否则抛 `ERR_EQL_UNKNOWN_ENTITY_NAME` | 走实体模型：方言翻译、租户/逻辑删除处理、结果集元数据来自 EQL 投影 |

平台提供的替代能力（本次评估的替代选项）：

1. **EQL**（`orm().findAll/findFirst/executeUpdate(SQL)`）：支持 LEFT/RIGHT JOIN + ON 条件、GROUP BY、SUM/COUNT/MAX、子查询、CTE、`?` 参数（见 `nop-orm/src/test/.../TestEqlQuery.java`）；**update/delete 返回 affected rows**，天然支持条件更新 CAS（仓库内规范样例：`SysDaoResourceLockManager` 的 `SQL.begin().update(实体名)...where...` + `session.executeUpdate`，带 version/holderId 条件防互斥破坏）。
2. **QueryBean**：结构化查询（filter/orderBy/分页），`DaoQueryHelper` 负责拼 EQL；适合列表查询，不支持更新。
3. **`IOrmEntityDao.tryUpdateWithVersionCheck` / `updateWithRetry`**（`nop-orm/.../dao/IOrmEntityDao.java`）：乐观锁守卫的实体更新，版本冲突不抛错返回 false，可 reload 重试。适合"别覆盖并发修改"场景；不适合"自引用自增"（`SET C=C+1`）与"一次性消费"（`WHERE USED=0`）类单语句原子语义——那类语义实体读写反而引入读-改-写竞态窗口。

## Analysis

### 一、框架/基础设施层：SQL 是其存在本身，必须用 SQL

| 模块/文件 | 用途 | 必须用 SQL 的原因 |
|---|---|---|
| `nop-dao`（`JdbcTemplateImpl`/`JdbcBatcher`/`JdbcStatement`） | JDBC 访问层 | 它就是原生 SQL 的执行引擎本体 |
| `nop-db-migration`（`MigrationEngine`/`MigrationHistoryManager`/5 个 Executor/`IndexExistsChecker`） | DDL/数据迁移引擎 | CREATE/ALTER TABLE、跨库 precondition 是 DDL 语义，EQL 只覆盖 DML 实体操作 |
| `nop-orm-eql`（`AstToEqlGenerator`/`AstToSqlGenerator`） | EQL→SQL 编译器 | 生成 SQL 就是它的产物 |
| `nop-orm`（`DaoQueryHelper`/`OrmEntityDao`/`OrmSessionImpl`/`AddTenantColInitializer`） | ORM/EQL 引擎本体 + 租户列 DDL | 同上；`DaoQueryHelper` 是 QueryBean→EQL 的拼接器 |
| `nop-orm-drivers/nop-orm-tdengine`（`TdSqlHelper`/`TdEntityPersistDriver`） | TDengine 方言驱动 | 为无 ORM 支持的时序库生成方言 SQL（子表/TAGS 语法） |
| `nop-orm-geo`（`H2GisInitializer`） | 空间扩展初始化 | 执行 `init` 等方言命令 |
| `nop-stream-connector-jdbc`（`JdbcTwoPhaseCommitSink`） | 外部 JDBC 两阶段提交 sink | 目标是任意外部库表，无实体模型 |
| `nop-batch-jdbc` / `nop-batch-exp` | JDBC 批处理连接器 | 面向用户配置的任意 SQL 数据源 |
| `nop-stream-runtime`（`JdbcLeaderElector`/`JdbcClusterRegistry`/`JdbcCheckpointStorage`） | 选主/集群注册/checkpoint 基础设施表 | 独立基础设施表（`nop_stream_leader` 等）未注册实体模型；自身就是"无 ORM 依赖也能跑"的基建层 |
| `nop-autotest-core` | 测试基架（建库/结果比对） | 测试需要绕开被测抽象直接操纵库状态 |
| `nop-metadata-service` 聚合处理器 | 框架级动态聚合查询生成 | 生成 SQL 即其功能 |
| `nop-biz`（`TreeEntityHelper`） | 树查询框架功能 | EQL 虚拟表 `tree_page` 框架机制 |
| `nop-kernel`（`SqlHelper`/`FilterBeanToSQLTransformer`） | SQL 拼接工具 | 工具本体 |
| `nop-benchmark` | ORM 基准测试 | 对照组需要原生 SQL |

### 二、应用层保留 SQL 的位置（有明确语义裁定，逐点原因）

这些语句的共同特征：**单语句原子性是契约的一部分**（affected-row 裁决并发），或**必须独立于 ORM 会话状态**。EQL 虽可表达部分形态，但以下每处都有已写明的裁定理由，且部分依赖"绕过会话"这一原生 SQL 独有性质：

| 位置 | 语句 | 保留原因 |
|---|---|---|
| `DbMfaChallengeStore`（nop-auth） | 原子递增 `FAIL_COUNT=FAIL_COUNT+1`（仅未过期行）、条件 DELETE 一次性消费、条件 UPDATE `VERIFIED_AT IS NULL` 恰一次迁移、`readVerifiedAt/readFailCount` 原始读 | Store 抽象有 Redis/Local 兄弟实现，语义对齐 Redis INCRBY/一次性 key；单语句原子 + affected-row 裁决是接口契约；原始读绕过 ORM 一级缓存（`markVerified` 是 raw UPDATE，会话缓存即脏） |
| `DbSmsCodeStore` / `DbEmailCodeStore`（nop-auth） | 同族原子递增/条件删除/原始读 | 同上（验证码 store 家族同语义） |
| `MfaFactorVerifier.verifyWebauthn`（nop-auth） | `UPDATE ... SET SIGN_COUNT=? WHERE SID=? AND SIGN_COUNT<?`（CAS）+ `touchLastUsed` | WebAuthn signCount 单调递增是协议安全要求：并发断言同一 credential 时，竞态方必须以 affected=0 失败（设计 §5.3.2 裁定，防计数回退/重放）。自引用比较条件单语句 CAS 是最严谨表达 |
| `LoginServiceImpl.markRecoveryCodeUsed`（nop-auth） | `UPDATE ... SET USED=1 WHERE SID=? AND USED=0` | 恢复码一次性消费：并发/regenerate 竞态方 affected=0 → 判 USED。实体读-改-写会引入检查-使用窗口 |
| `NopAuthUserBizModel` TOTP 失败计数 3 方法（nop-auth） | 锁定探测 SELECT、`TOTP_FAIL_COUNT=COALESCE(...)+1` 自增、条件作废/清零 | ① 原子自增（自引用算术，`tryUpdateWithVersion` 无法表达）；② 计数必须在 `REQUIRES_NEW` 独立事务先行落库——外层 `@BizMutation` 随 `ERR_AUTH_MFA_FAIL` 回滚，ORM 会话内的实体/EQL 写会随外层事务一起回滚，jdbcTemplate 单语句在独立事务内立即持久（代码注释有完整裁定）；③ 探测读必须绕过会话缓存读到并发递增后的新值；④ 清零语句显式不触碰 VERSION，避免与成功路径随后的实体写（lastVerifiedWindow 推进）乐观锁冲突 |
| `NopCredentialOauthStateStore`（nop-credential） | oauth state 一次性消费 UPDATE + 清理 DELETE | 一次性 CAS 消费（同恢复码家族语义） |
| `NopDatavDashboardShareBizModel.recordShareVisit`（nop-datav） | `VISIT_COUNT=COALESCE(...)+1` | 设计文档（permission-sharing-design）显式裁定：匿名统计**不走实体 update**——不 bump version、不触碰审计列（统计写不冒充管理操作），数据库端原子自增零丢失更新，失败 fail-open 仅 WARN |
| datav schedulers/recovery（`NopDatavAlertScheduler` 等 3 处） | `existsTable` 探测 | ORM/EQL 无"表是否存在"API；启动期探测避免未建表环境崩溃 |
| `PanelDataBinder`/`PanelSqlBuilder`/`AlertEvaluator`/ChatBI 取数（nop-datav） | 动态面板查询 | **执行用户定义的数据集 SQL 是 datav 的产品功能本体**（查询引擎语义：任意方言、动态拼装、行级限流） |
| `SysDaoResourceLockManager` / `SysSequenceGenerator` / `WorkflowDefinitionDO` / `TreeEntityHelper` | `SQL.begin().update/deleteFrom(实体名)...` 经 session 执行 | 已经是 **EQL**（实体名 + prop 名），且带 version/holderId 条件——本报告的合规规范样例，无需改动 |

> 评级说明：第二类中"一次性消费/原子自增"语义 EQL 亦可为（同为单语句 affected-row），但这些位置的裁定文档/注释均以"单语句原子 + 绕过会话"为契约写下，族内一致（Db/Redis/Local 三实现同语义），改动收益（去物理列名耦合）小于在安全关键路径上重写已验证代码的风险。**结论：保留，理由如上。**其中物理列名耦合是已知残余风险，见 Open Questions。

### 三、发现的问题：不需要 SQL、且 SQL 已造成实际缺陷（2 组 6 处）

#### F-1（P1，生产级路径错配）：`NopAiChatResponseBizModel.summarizeByModel`

- 现状：`buildSummarySql` 拼的是**物理表名/列名** SQL（`FROM nop_ai_chat_response r LEFT JOIN nop_ai_model m`），却经 `orm().findAll(sql, ROW_MAPPER)` 执行——该路径按 **EQL** 编译（见执行模型表）。
- 缺陷：未设置 `allowUnderscoreName`（`SQL.begin()` 缺省 false），EQL 实体解析只认全名/短名（`OrmModelInitializer.entityMap` 的 key），`nop_ai_chat_response` 解析失败 → 生产运行时抛 `ERR_EQL_UNKNOWN_ENTITY_NAME`，接口完全不可用。
- 为什么没被测出：`TestNopAiChatResponseSummarizeByModel` 用 `IJdbcTemplate`（原生 SQL 路径）执行同一 `buildSummarySql`，注释声称"same ISqlExecutor.findAll contract as the production orm() path"——**两条路径语义不同**（原生 vs EQL 编译），测试掩盖了路径错配。
- 修正方向：重写为实体名 EQL（`from NopAiChatResponse r left join NopAiModel m on r.modelId = m.id`；EQL 已验证支持 JOIN ON/GROUP BY/SUM/COUNT/算术/参数），测试改走 `orm()` 真实路径（复用 `TestNopAiBizModelEntityCrud` 的 `OrmSessionFactoryBean` + DDL 范式）。

#### F-2（P2，会话后写造成三处衍生缺陷）：datav 5 处普通单行 UPDATE

`NopDatavDashboardBizModel.updateDashboardPublishState/updateDashboardFields`、`NopDatavScreenBizModel.updateScreenPublishState/updateScreenFields/setScreenThumbnail`。

共同模式：BizModel 已通过 `requireEntity` 把**同一行**实体加载进 ORM 会话（rollback 流程甚至已在内存改好字段），却用 jdbcTemplate 绕过会话 raw UPDATE 物理列。衍生缺陷：

1. **会话实体与库脱同步**：raw UPDATE 不更新会话内实体。`setScreenThumbnail` 注释称"返回最新主表行（含更新后的 thumbnail + version）"，实际 `getEntityById` 命中一级缓存返回**旧实例**（旧 thumbnail/旧 version）——注释承诺的行为不成立。publish 流程的 `afterEntityChange(entity)` 同样看到旧 publishState。
2. **手工模仿平台自动行为**：`setScreenThumbnail` 手写 `VERSION=VERSION+1`、`UPDATED_BY`、`UPDATE_TIME`——实体写（`dao.updateEntity`）自动完成且只刷脏列；其余 4 处则**根本不维护 version/审计列**（数据变了 version 不变，乐观锁账目失真）。
3. **物理列名耦合**：`NOP_DATAV_SCREEN.THUMBNAIL` 等硬编码，模型改名即断，且无租户/逻辑删除一致性保障。
- 这些语句**没有任何单语句原子性/affected-row 裁决需求**（发布/回滚是管理员单写者操作），是纯粹的"没必要用 SQL"。
- 修正方向：改实体写（`dao.updateEntity`）——语义即"部分列更新 + 乐观锁 + 审计列"，并顺带消除上述三缺陷。回滚流程 `restoreXxxFromSnapshot` 已把字段写进实体，直接提交即可。

> **执行期新发现（2026-08-23，plan 2255 Phase 1 实证）**：EQL 编译器算术优先级非标准——`a*b/1000000 + c*d/1000000` 被编译为 `((a*b)/(1000000+c*d))/1000000`（`/` 未比 `+` 结合更紧；经编译 SQL dump 实证）。EQL 中混合 `+ - * /` 的表达式**必须显式括号**，否则静默改变语义。另有：EQL 结果集字段名大小写敏感，`BeanRowMapper(camelCase=true)` 配套的投影别名必须 snake_case（camelCase 别名会被 `StringHelper.camelCase` 先整体小写而 miss 属性）。这两条是"EQL 可替代原生 SQL"结论的重要边界条件，已写入 owner doc（`docs-for-ai/02-core-guides/model-first-development.md` 直接 SQL 边界章节）。

## 第二轮复核（2026-08-23，用户质询触发，探针实证后即删）

用户对三个论断提出质询，逐项实证复核，**两处修正、一处精确化**：

### 勘误 1：MFA 家族"保留 raw SQL"的技术论据大部分不成立，可行 EQL 平移

探针实证（ORM 会话工厂 + H2，生产同编译链）：
- `update NopAiChatResponse o set o.promptTokens = o.promptTokens + 1 where ...` 编译执行成功（affected=1，值 100→101）——**update SET 自引用算术有支持**，原"EQL update SET 算术无背书"论据作废；
- `... set o.completionTokens = 5 where ... and o.completionTokens is null` 条件更新成功（affected-row 判定语义与 raw SQL 相同，`executeUpdate` 返回 long）；
- `sql-lib.xml` 机制（`SqlLibManager` + `SqlLibProxyFactoryBean` mapper 接口代理，type=`eql`/`sql`/`query`）中 `eql` item 走 `ormTemplate.getSessionFactory().compileSql(...)`——与 `orm().executeUpdate(SQL)` **同一编译执行链**，是比 Java 拼字符串更规范的载体（debug 模式自动语法校验）。

原"保留"理由逐条复核：
- "绕过 ORM 会话缓存"——对标量投影读与 bulk update **不成立**：EQL 标量投影直查 DB；EQL bulk update 同样不更新一级缓存实体（与 raw SQL 行为一致）。`NopAuthMfaChallenge` 为 `tagSet="no-tenant"`，租户注入差异也不存在。
- "REQUIRES_NEW 独立事务"——事务边界由 txn 模板管理，与语句载体（jdbcTemplate/EQL）正交，EQL 在同一新事务内执行同样成立。
- 仍然成立的保留理由只剩工程判断：已验证安全关键路径的重写风险 vs 收益（去物理列名耦合、统一到 sql-lib 管理）。**结论修正：技术上可平移，"必须用 SQL"的表述过强**；是否平移交由后续计划裁定（见 Open Questions）。

### 勘误 2：camelCase 别名映射说法修正——用户直觉正确

- `BeanRowMapper.of(clazz, false)`（**缺省模式**，key 原样精确匹配）+ camelCase 投影别名：探针实证**映射成功**（EQL 字段名大小写敏感且别名原样保留，`as totalPromptTokens` 直接命中 `totalPromptTokens` 属性）。
- `BeanRowMapper.of(clazz, true)`（camelCase 模式）+ camelCase 别名：`StringHelper.camelCase(key,'_',false)` 先整体小写（`StringHelper.java:1166`）→ `modelid` miss 属性，探针实证抛 `nop.err.core.bean.unknown-prop propName=modelid`。
- 即两种组合皆可：`snake_case 别名 + camelCase=true`（plan 2255 采用，mapper 不动）或 `camelCase 别名 + camelCase=false`。原报告只陈述了前者约束、未指出后者可行，已修正 owner doc 表述。

### 精确化：EQL 算术优先级的真实触发规律（比初版结论影响面更大）

探针矩阵（编译 SQL 分组 + 实算值双重验证）：

| 表达式 | 实际分组 | 值 | 标准？ |
|---|---|---|---|
| `1+2*3` / `8/2+2` / `2*3+4` | 原样 | 7 / 6 / 10 | ✓ |
| `10-2-3` / `100/5/2` | 左结合 | 5 / 10 | ✓ |
| `X/2+3`（列，短链） | `X/2+3` | 53 | ✓ |
| **`8/2+4/2`** | **`(8/2+4)/2`** | **4（标准 6）** | ✗ |
| **`X/2+Y/2`（列）** | **`(X/2+Y)/2`** | — | ✗ |
| `a*b/c+d*e/c` | `((a*b)/(c+d*e))/c` | 8.99e-10 | ✗ |
| `(a*b)/c+(d*e)/c`（只括乘法） | `((a*b)/c+d*e)/c` | 0.000025 | ✗ |
| `((a*b)/c)+((d*e)/c)`（除法整体括号） | `A+B` | 0.00105 | ✓ 防御有效 |

规律：**表达式中出现多个 `/` 且中间夹 `+`/`-` 时分组翻转**（尾部的 `/` 把前面整个 `+` 链吞为左操作数）；单一 `/` 或 `/` 不跨 `+` 的表达式正常。最普通的 `A/B + C/D`（均值/比率/单价类）即中招。只给乘法加括号防不住，**每个含 `/` 的子表达式整体加括号**才可靠（plan 2255 生产修复恰好是此形态）。

g4 根因（用户质询"查看 EQL 的 g4"）：`BaseRule.g4:164-172` 的 `sqlExpr_bit` 把 `| & << >> + - * /` 八个二元运算符**平铺为同一左递归规则的等价备选分支**，无显式优先级分层；而打印器 `AstToEqlGenerator.printLeft/printRight` 按 `SqlOperator` 的标准优先级表（`*`/`/`=60、`+`/`-`=70）加括号——**解析分组与打印模型脱节**，LL 预测在多 `/` 夹 `+` 链上产生与任何一致优先级模型都不符的树。这属框架缺陷，值得立项修复（改 g4 分层或修 AST 构建），修复前防御性写法进 owner doc。

## Conclusion

- 全景结论：worktree 内直接 SQL 分三类——(a) 框架基础设施，SQL 即其存在本身；(b) 应用层有并发/事务语义裁定的原子语句（一次性消费、CAS、原子自增、REQUIRES_NEW 计数、会话绕过读、产品级查询引擎），**保留**，原因如第二节表格；(c) 无并发语义的普通读写且已造成实际缺陷的 2 组 6 处（F-1 nop-ai 聚合查询路径错配；F-2 datav 5 处会话后写），**应改为 EQL/实体写**。
- 被否决的方案：
  - "把第二节全部改写成 EQL"——否决。EQL 与原生 SQL 同为单语句时原子性等价，但 (i) mfa/credential 家族的裁定契约以"绕过 ORM 会话 + REQUIRES_NEW + Redis 语义对齐"写下，重写安全关键路径收益仅是去物理列名耦合，风险不成比例；(ii) `COALESCE` 自增、`VERSION` 不触碰等语义在 EQL update SET 表达式支持上无既有测试背书，属为新语法冒险。
  - "用 `dao.tryUpdateWithVersion` 统一替换条件更新"——否决。它是读-改-写乐观锁语义，无法表达自引用自增与 WHERE 条件 CAS，替换即引入竞态窗口。
- 后续工作：`ai-dev/plans/2255-direct-sql-reduction.md`（F-1/F-2 修正 + owner doc 增补"直接 SQL 使用边界"规则）。
- owner-doc 同步裁定：`docs-for-ai/02-core-guides/model-first-development.md` 缺少"何时允许直接 SQL"的规则，本次调研的高频判定标准应沉淀进去（随 plan 2255 落地）。

## Open Questions

- [x] ~~watch-only：mfa/credential 家族平移 EQL——若未来 EQL update SET 算术表达式获得测试背书，可重新评估~~（2026-08-23 第二轮复核：探针已实证 update SET 算术与条件更新支持，技术障碍清除，见"第二轮复核"勘误 1；是否平移交后续计划裁定，物理列名耦合与 jdbcTemplate 双路径维护成本仍在）。
- [ ] **EQL 算术分组缺陷修复立项**：g4 `sqlExpr_bit` 平铺备选导致多 `/` 夹 `+` 链分组翻转（`A/B+C/D` → `(A/B+C)/D`），与 `SqlOperator` 打印优先级模型脱节。修复方向：g4 显式分层或 AST 构建修正 + 框架级回归测试（含本报告探针矩阵）。
- [ ] `NopDatavDashboardShareBizModel.recordShareVisit` 属第二节"保留"（设计文档裁定），但其"原子自增不走实体"形态与 F-2 的区分标准（是否需要单语句原子性）已在报告中写明，供后续复查对照。

## References

- `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java`（EQL 编译入口，`eql.isAllowUnderscoreName()`）
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SqlExprMetaCache.java`（下划线名仅 `allowUnderscoreName=true` 时解析）
- `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java`（EQL 条件 update/delete + affected-row 规范样例）
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/IOrmEntityDao.java`（`tryUpdateWithVersionCheck`/`updateWithRetry`）
- `nop-persistence/nop-orm/src/test/java/io/nop/orm/loader/TestEqlQuery.java`（EQL JOIN/GROUP BY/聚合/参数能力背书）
- `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiChatResponseBizModel.java`（F-1）
- `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavScreenBizModel.java` 等（F-2）
