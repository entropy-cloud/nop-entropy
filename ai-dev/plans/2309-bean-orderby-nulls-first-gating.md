# 2309 bean 路径与聚合路径 ORDER BY 空序方言门控（F1 + F3）

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/plans/2308-eql-orderby-nulls-first-multi-dialect.md` 的 `Deferred But Adjudicated`（F1 / F3）
> Related: `ai-dev/design/nop-orm-scope-and-non-goals.md` §3.8、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

## Purpose

把 EQL 之外两条 ORDER BY 空序产出路径接到 `supportNullsFirst` 能力位上（或在无法判定时响亮失败），
消除**已确认的 live defect**：在不接受 `NULLS FIRST/LAST` 语法的数据库上产出语法错误的 SQL。

## Current Baseline

EQL 通道已由 plan 2308 收口：`nullsFirst` tri-state，true → 原生子句，false → `CASE WHEN` 改写
（紧邻前置同级键），三类不可改写位置 fail-fast。契约见 design §3.8。

### 依赖方向（决定性约束，先于一切方案）

`nop-dao/pom.xml:17-20` 依赖 `nop-core`；`nop-kernel/nop-core/pom.xml` **不依赖** `nop-dao`。
故 `io.nop.core.lang.sql.SQL$SqlBuilder`（在 nop-core）**在编译期无法引用** `io.nop.dao.dialect.IDialect`——
单方向依赖，引用即循环。**任何「改 nop-core 让它认识方言」的方案都不成立。**

但方言在 **ORM 层已经可达**，无需任何新公开 API：

- `OrmEntityDao` 持有 `ormTemplate`（`:61`）与 `getEntityModel()`；
  `IOrmTemplate.getSessionFactory()`（`IOrmTemplate.java:32`）→ `IOrmSessionFactory.getDialectForQuerySpace(String)`
  （`IOrmSessionFactory.java:56`）。既有先例：`OrmEntityDao:787` 已用
  `orm().getDbEstimatedClock(getEntityModel().getQuerySpace())` 走同一取法。
- `JdbcBatchLoaderProvider:223` 已有 `jdbcTemplate.getDialectForQuerySpace(querySpace)`。

### F1（bean 路径无方言门控）—— 实际缺陷点已定位

`SQL.SqlBuilder` 的空序输出**完全方言盲**（`SQL.java:589-597` 无条件 append）。但真正把它接进
查询产出的调用点，其方言可达性各不相同：

| # | 产出点 | 是否已持方言 | 状态 |
|---|--------|--------------|------|
| 1 | `GenSqlHelper.appendOrderBy(sb, IDialect dialect, ...)` `GenSqlHelper.java:829-847`，`sb.nullsFirst(...)` 在 **:845** | **是**（参数已传入却未用于该判定） | **live defect，修** |
| 2 | `GenSqlHelper.genOrderBy(..., IDialect dialect, ...)` `GenSqlHelper.java:552-569`，`:567` | **是**（同上） | **live defect，修** |
| 3 | `DaoQueryHelper.appendOrderBy` `:213-226`（`sb.orderField` 在 `:224`） | 否，需加参数 | **live defect，修**；内部调用方 `:88`（`queryToSelectObjectSql`）、`:160`（`queryToSelectFieldsSql`）、`:303`（`queryToFindNextSql`，用 `orderByPk`） |
| 4 | `DaoQueryHelper.appendReverseOrderBy` `:228-241`（`sb.reverseOrderField` 在 `:239`，**且 `:236` 反转排序方向**） | 否 | **live defect，修**；改写必须同时翻转 CASE 键方向 |
| 5 | `SQL.SqlBuilder.reverseOrderField` `:610-622` | — | 随 #4 一并由调用方修正；本方法保持方言盲 |
| 6 | `SQL.SqlBuilder.orderBy(String, List<OrderFieldBean>)` `:672-686`（`:683`）、`orderBy(String, OrderFieldBean)` `:688-693`（`:691`） | — | 无 main 调用方，仅被 #3/#4 经 `orderField` 间接触发 |
| 7 | `QueryBeanHelper.buildOrderBySql` `nop-kernel/nop-core/.../QueryBeanHelper.java:53-76`（`:69`/`:71` 直接 append 文本） | 否（且在 nop-core，**不可能**持有） | **零调用方**（全仓 grep 确认）⇒ 死 API，非用户可达缺陷，见 D4 |
| 8 | `OrderFieldBean.toString()` `nop-kernel/nop-api-core/.../OrderFieldBean.java:35-39` 渲染 `" nulls first"` | — | 仅调试输出，不参与 SQL，见 D5 |
| 9 | `OrderFieldBean.shouldNullsFirst()` `OrderFieldBean.java:132-136` | — | 仅被内存比较器（`OrderByComparator`）使用，不参与 SQL，见 D5 |

`DaoQueryHelper.appendOrderBy` 的**外部**调用方：`OrmEntityDao.java:526`（`queryToSelectObjectSql`）、
`OrmEntityDao.java:541-542,700`（`queryToFindNextSql`）、`JdbcBatchLoaderProvider.java:214`
（`queryToSelectFieldsSql`）。三者方言均可达（见上）。

### F3（聚合路径）—— 真实缺陷点是 `dialect == null`，不是「非 MySQL 方言」

`nop-metadata-service/.../query/AggregationHelper.java`：

- `:331` `buildOrderByClause(..., String dialect)`，`dialect` 是 JDBC `databaseProductName` **字符串**。
- `:361` `"MySQL".equalsIgnoreCase(dialect)` 特判；命中则 `:365` 用单布尔推导「是否与 MySQL 缺省一致」，
  一致则省略子句、不一致则 fail-fast。
- **未命中时 `:375` 无条件 `sb.append(" NULLS FIRST"/" NULLS LAST")`**。

**但 `mariadb` / `dm` / `tdengine` / `db2` 永远走不到 `:375`**：`MetaAggregationExecutor.java:56-57` 与
`AggregationContext.java:23-24` 定义 `SUPPORTED_DIALECTS = {H2, MySQL, PostgreSQL}`，
`ExternalAggregationProcessor:72`、`MixedSameDbJoinAggregationProcessor:126`、
`ExternalExternalJoinAggregationProcessor:93`、`EntityAggregationProcessor:183` 四处 guard 先一步抛
`ERR_AGGR_UNSUPPORTED_DIALECT`。
（2308 的 Deferred 段落曾把 mariadb/dm/tdengine/db2 列为 F3 缺陷面，该表述**错误**，此处更正。）

**真实且唯一可达的缺陷**：`EntityAggregationProcessor.java:141` 与
`EntityEntityJoinAggregationProcessor.java:128` **刻意**传 `dialect = null`
（两处注释均指向 plan `2026-08-06-1228-1` 的 Deferred，并写明隐患是
「ORM 背后接 MySQL 的部署场景」）。此时 `:375` 无条件输出原生 `NULLS` 子句——
若该 ORM 底层是 MySQL，即为**语法错误 SQL**。

**两处「方言拿不到」的注释本身已经过期**：`EntityAggregationProcessor.java:137` 写
「`IOrmTemplate` 无方言 API」，但 `IOrmTemplate extends ISqlExecutor`
（`IOrmTemplate.java:30`）而 `ISqlExecutor.java:40` 就声明了
`IDialect getDialectForQuerySpace(String querySpace)`；`MetaQueryContext.java:57` 已暴露
`orm()`。**方言是拿得到的**，故终态是「解析出真实方言」而非「fail-fast」
（后者会把今天在 H2 / PostgreSQL 上正常工作的 ORM 聚合查询变成硬失败）。

### 已裁定的设计约束（Phase 1 结论，见 Goals 前的 Decisions）

- **D1**：F1 一律在 **ORM 层**修，`SQL.SqlBuilder` 与 `nop-core` **不动**。方言经
  `ormTemplate.getSessionFactory().getDialectForQuerySpace(entityModel.getQuerySpace())` 取得。
  不新增任何跨模块公开 API，不触碰 Protected Area。
- **D2**：F3 的修复面**只有** `dialect == null` 这两条 ORM 路径。
  `mariadb`/`dm`/`tdengine`/`db2` 不在范围内（已被上游 guard 拦住）。
- **D3**（经二轮审查修正）：`nameToExpr` **并非全是聚合表达式**——度量走
  `buildNameToExprTable` 的 `measures.get(i).aggSql`（`:254`，聚合表达式），
  但**维度**走 `dims.get(i).column`（`:264`）与 `dims.get(i).qualifiedCol`（`:291`），
  二者是**纯列引用**，按 design §3.8 是**允许改写**的。
  故 F3 的终态既不是「一律 fail-fast」也不是「一律改写」，而是**按方言能力位分流**：
  `supportNullsFirst=true` → 原生；`false` 且排序键是纯列引用 → CASE 改写；
  `false` 且排序键是聚合表达式 → fail-fast（无法安全复制表达式）。
- **D8**：`buildOrderByClause` 末参 `String dialect` 是 **productName**（`"H2"`/`"MySQL"`/`"PostgreSQL"`），
  与 `IDialect.getName()`（方言名 `"h2"`/`"mysql"`/`"postgresql"`）**不是同一命名空间**。
  F3 走「把 null 换成 `IDialect.getName()`」即可：`"MySQL".equalsIgnoreCase("mysql")` 为真，
  h2 / postgresql 落 else 走原生——与既有三态语义完全一致，**不需改签名、不需改既有测试**。
- **D4**：`QueryBeanHelper.buildOrderBySql` 零调用方 ⇒ 不是用户可达缺陷。
  本计划只加 javadoc 危险标注（nop-core 行为不变）；**删除它属 nop-core 变更，不在本计划范围**。
- **D5**：`OrderFieldBean.toString()` 与 `shouldNullsFirst()` 不参与 SQL 产出
  （前者仅调试字符串，后者仅供内存比较器），本计划不动，仅在 design 留痕说明其非 SQL 语义。

## Goals

- F1 #1–#4 四处产出点按 `supportNullsFirst` 决定「原生 / CASE 改写 / 响亮失败」，与 EQL 通道同一契约。
- F3 的 `dialect == null` 路径不再产出无法校验的原生 `NULLS` 子句。
- 两处失败路径均给出**含 `reason` 的错误码**，不静默降级。
- 不新增跨模块公开 API；`nop-core` 零改动。
- 补齐取值/门控守护测试，并用变异验证证明其有效。

## Non-Goals

- 不做 `ORDER BY ... COLLATE`（2308 已裁定 out-of-scope）。
- 不改 `QueryBean` / `OrderFieldBean` 的公开 API 签名。
- 不删 `QueryBeanHelper.buildOrderBySql`（属 nop-core 变更，见 D4）。
- 不改 `SQL.SqlBuilder` 的任何方法（D1）。
- 不重命名/重构 `MetaAggregationExecutor` / `AggregationContext` 的 `SUPPORTED_DIALECTS` 白名单。

## Scope

### In Scope

| 模块 | 改动 |
|------|------|
| `nop-persistence/nop-orm` | `GenSqlHelper:845`、`:567` 使用已有 `IDialect`；`DaoQueryHelper:213`/`:228` 增加 `IDialect` 参数；`OrmEntityDao:526/541` 传方言 |
| `nop-batch/nop-batch-jdbc` | `JdbcBatchLoaderProvider:214` 传方言（`:223` 已有取法） |
| `nop-metadata/nop-metadata-service` | `AggregationHelper` 的 `dialect == null` 分支改为 fail-fast |
| `nop-kernel/nop-core` | **仅** `QueryBeanHelper.buildOrderBySql` / `OrderFieldBean` 的 javadoc 标注（无行为变更） |
| `docs-for-ai`、`ai-dev/design` | 删除 2308 留下的「该路径当前有缺陷」过时警告；design 留痕 D3–D5 |

### Out Of Scope

- `nop-core` 的任何行为变更（Protected Area，见 D1/D4）。
- `SQL.SqlBuilder` 的签名或行为变更。
- `productName → IDialect` 的**通用**映射设施：F3 只需 3 个已知取值且走 D3 的 fail-fast，不需要通用映射
  （`DialectManager.getDialect(String)` 按方言**名**索引，与 productName 命名空间亦不一致：
  `MySQL` vs `mysql` / `mysql5.7`，见 M6 裁定）。

## Decisions

| 裁定 | 内容 | 依据 |
|------|------|------|
| D1 | F1 在 ORM 层修，`nop-core` 与 `SQL.SqlBuilder` 零改动 | 依赖方向单向（`nop-dao` → `nop-core`）；方言在 ORM 层已可达且有 `OrmEntityDao:787` 先例 |
| D2 | F3 修复面仅 `dialect == null` 两条 ORM 路径 | `SUPPORTED_DIALECTS = {H2, MySQL, PostgreSQL}` 上游 guard 已拦住其余方言 |
| D3 | 聚合路径按能力位分流：原生 / 纯列则改写 / 聚合表达式则 fail-fast | 维度键是纯列（`:264`/`:291`），度量键是 `aggSql`（`:254`），须分别裁定 |
| D4 | `QueryBeanHelper.buildOrderBySql` 只加 javadoc 标注，不删除 | 零调用方，非用户可达；删除属 nop-core 变更 |
| D5 | `OrderFieldBean.toString()` / `shouldNullsFirst()` 不动 | 不参与 SQL 产出 |
| D6 | 反转排序（`appendReverseOrderBy`）必须同步翻转 CASE 键方向 | 否则 `findNext` 静默返回相反空序 |
| D7 | 门控失败一律抛带 `reason` 的错误码 | Anti-Slacking：不得静默忽略空序请求 |
| D8 | F3 以 `IDialect.getName()` 填充 null，不改 `buildOrderByClause` 签名 | `equalsIgnoreCase` 使 `"MySQL"`/`"mysql"` 匹配，h2/postgresql 走原生，语义与现状一致 |
| D9 | 复用单元必须落在 `nop-orm-eql`（`AstToSqlGenerator` 所在模块）并迁移之 | `nop-orm` 依赖 `nop-orm-eql`；单元若放 `nop-orm`，EQL 通道无法复用，"单一实现" gate 不可达 |

## Execution Plan

### Phase 1 - 提取并复用空序改写能力（Feature）

Status: completed
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/`

- Item Types: `Feature`

- [x] 按 D9 在 **`nop-orm-eql`**（`AstToSqlGenerator` 所在模块）提取方言无关的改写单元；
      因 `nop-orm` 依赖 `nop-orm-eql`，单元放 `nop-orm` 会导致 EQL 通道无法复用
- [x] **迁移** `AstToSqlGenerator.printOrderByItemForSql` 的 CASE 逻辑到该单元，
      EQL 通道改为调用它——确保全仓**只有一份**改写实现
- [x] 单元契约（签名固定，避免执行者自行发明 API）：
      输入「排序列 SQL 片段 + asc + nullsFirst」，输出改写后的 ORDER BY 片段；
      `asc` 为翻转**之后**的排序方向。改写规则（与 2308 已验证实现逐字一致）：
      - CASE 键方向 = `asc` 的**相反**方向
      - `then` 值 = `nullsFirst == asc ? "1" : "0"`，`else` 值取反
      - 随后输出原排序列 + `asc` 方向
- [x] 提供「原生直出」与「改写」两个入口，调用方按 `IDialect.isSupportNullsFirst()` 二选一
- [x] 不可改写位置（表达式含 `?` 绑定标记 / 序号定位 / 非纯列引用）抛带 `reason` 的错误码。
      注：窗口与 DISTINCT 判定依赖 AST，**bean 路径无 AST**，故这两个 reason 在 bean 路径
      不可判定——单元只暴露 `expression-not-duplicable` 与 `nulls-not-specified`，
      窗口/DISTINCT 的 fail-fast 仍留在 EQL 通道（那里才有 AST）
- [x] 四种方向组合的改写产物有单测断言（行序语义，非仅字符串）

Exit Criteria:

- [x] 四种方向组合的改写产物有单测断言（行序语义，而非仅字符串）
- [x] **接线验证**：**变异验证**——把 `NullsOrderRewriter` 的 CASE 键方向翻转后重跑 `TestEqlNullsOrderCompile`，**4 个用例转红**，证明 EQL 通道确实经该单元产出（而非绕过）。已回滚复绿。另 grep 确认 main 代码中 `case when` 字面量**只剩一处**（`NullsOrderRewriter:71`），满足"单一实现" gate
- [x] **无静默跳过**：不可改写位置抛带 `reason` 的错误码，不返回 null / 空串
- [x] **端到端验证**：迁移后 `TestEqlNullsOrderMatrix`（docker PG16 + MySQL8）5/5、`TestEqlNullsOrderEndToEnd`（H2）21/21、`nop-orm-eql` 全量 305 全绿——复用既有测试而非新写，证明重构行为等价
- [x] No owner-doc update required（纯内部重构，用户可见行为不变）
- [x] `ai-dev/logs/` 当日条目已更新

### Phase 2 - F1 bean 路径接线（Fix）

Status: planned
Targets: `nop-persistence/nop-orm`、`nop-batch/nop-batch-jdbc`、
`nop-kernel/nop-core` 与 `nop-kernel/nop-api-core`（**仅 javadoc**）

- Item Types: `Fix`

**必须改签名的方法（4 个 `public static`）**——否则方言无法送达：

- [ ] `DaoQueryHelper.queryToSelectObjectSql:76`（`appendOrderBy` 调用在 `:88`）
- [ ] `DaoQueryHelper.queryToSelectFieldsSql:125`（`:160`）
- [ ] `DaoQueryHelper.queryToFindNextSql:287`（`:303`）
- [ ] `DaoQueryHelper.queryToFindPrevSql:307`（`appendReverseOrderBy` 调用在 `:323`）

**全部调用方（改签名后必须同步，遗漏即编译失败）**：

| 文件 | 行 | 说明 |
|------|-----|------|
| `DaoQueryHelper.java` | `:88` `:160` `:303` `:323` | 内部 |
| `OrmEntityDao.java` | `:526`、`:541-542`、`:546-548`、`:700`、`:713` | `:546-548`/`:713` 是 `appendReverseOrderBy` 的**唯一**外部通路（D6 挂在这里） |
| `MdxQueryExecutor.java` | `:65`、`:98`、`:184` | 经 `queryToSelectFieldsSql` |
| `JdbcBatchLoaderProvider.java` | `:214` | 方言取法见下 |
| `TestEntityDaoQuery.java` | `:82`、`:96`、`:102` | 测试同步 |

- [ ] `GenSqlHelper:845`（`appendOrderBy`）与 `:567`（`genOrderBy`）改用 Phase 1 单元；
      这两处**已持 `IDialect`**，不新增参数
- [ ] `OrmEntityDao` 取方言：`ormTemplate.getSessionFactory().getDialectForQuerySpace(getEntityModel().getQuerySpace())`
      （`getEntityModel()` 走 `requireEntityModel`，非 null；`ormTemplate` 在 `@PostConstruct init()` 已解引用，
      无参构造的空窗不可达。先例：`OrmEntityDao:787`）
- [ ] `JdbcBatchLoaderProvider:214` 取方言：`:223` 的 `dialect` 局部变量**声明在该 lambda 之后、不在作用域内**，
      故须改为捕获 `querySpace` 字段后在 lambda 内调用
      `jdbcTemplate.getDialectForQuerySpace(querySpace)`
- [ ] 按 D6 处理反转：`reverseOrderField` 会同时翻转 `isDesc` 与 `nullsFirst`（`SQL.java:616-619`），
      把翻转后的 `(asc, nullsFirst)` 组合喂给 Phase 1 单元即可，CASE 键方向自动随之翻转
- [ ] 按 D4/D5 为 `QueryBeanHelper.buildOrderBySql`（`nop-core`）与
      `OrderFieldBean.toString()`（**`nop-api-core`**）加危险标注（javadoc，无行为变更）
- [ ] 逐一核对 Current Baseline 表 #1–#9，每处标记「已修 / 不在范围 + 理由」

Exit Criteria:

- [ ] **端到端验证（Rule #22）**：`QueryBean.orderBy(... nullsFirst)` 经 DAO 真实执行并断言**结果行序**。
      先例：`AbstractJdbcTestCase.createDataSource()`（`:54`）可覆写，
      `nop-persistence/nop-orm/src/test/java/io/nop/orm/dialect/TestPostgreSqlJsonComponent.java:19-32`
      已用该机制在 `jdbc:tc:*?TC_DAEMON=true` 上跑完整 DAO 栈
- [ ] **真实数据库执行**：测试库 H2 的 `supportNullsFirst=true`（恒走原生分支），
      故**改写分支**验证必须用 docker 且命令显式带 `-Dnop.test.docker.enabled=true`
      （仓库无任何 pom 设置该属性，不显式传则测试被跳过 → **假绿**）。
      可复用 `TestEqlNullsOrderMatrix.buildDialect(boolean)`（`:69-84`）的合成方言模式
- [ ] **接线验证**：断言 `DaoQueryHelper` / `GenSqlHelper` 在运行时确实调用 Phase 1 单元
- [ ] **无静默跳过**：不可改写位置抛带 `reason` 的错误码
- [ ] `nop-core` 与 `nop-api-core` 行为**零变更**：`git diff --stat nop-kernel/nop-core nop-kernel/nop-api-core`
      仅注释行
- [ ] **owner-doc**：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的 bean 路径
      空序行为描述已更新（Phase 4 落地，但此处须确认 Phase 2 的行为变更已被覆盖）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 3 - F3 聚合路径方言解析（Fix）

Status: planned
Targets: `nop-metadata/nop-metadata-service`

- Item Types: `Fix`

- [ ] 按 D8 修正 `EntityAggregationProcessor:137-141` 与
      `EntityEntityJoinAggregationProcessor:126-128`：把 `null` 换成
      `ctx.orm().getDialectForQuerySpace(<querySpace>).getName()`，并删除已过期的
      「`IOrmTemplate` 无方言 API」注释（该断言与 `ISqlExecutor.java:40` 矛盾）
- [ ] `<querySpace>` 取自实体：`entity.getQuerySpace()`（`EntityAggregationProcessor:172` 已有先例）
- [ ] 按 D3 在 `AggregationHelper.buildOrderByClause` 内按能力位分流：
      `supportNullsFirst=true` → 原生；`false` 且键为纯列引用 → CASE 改写；
      `false` 且键为聚合表达式 → fail-fast（`expression-not-duplicable`）
- [ ] `:361` 的 `"MySQL".equalsIgnoreCase(dialect)` 改为覆盖 mysql 家族
      （`mysql` / `mysql5.7` / `mariadb` 均无该语法）。注意 `mariadb` 目前被
      `SUPPORTED_DIALECTS` guard 拦住、改不到，但判据本身不应只认一个字面量
- [ ] **保留** MySQL 既有语义：与缺省一致则省略子句、不一致则 fail-fast。
      `testBuildOrderByClauseMySqlDefaultSemanticsOmitsClause`、
      `testBuildOrderByClauseMySqlInexpressibleFailsFast` 必须**继续通过且未被削弱**
- [ ] `testBuildOrderByClauseNullDialectKeepsNullsClause`（`:206`）断言 `null` dialect **保留** NULLS 子句，
      与本 Phase 的修复方向直接矛盾。**裁定：属正当的行为变更更新，不是测试削弱**——
      它锁定的是本计划明确要取代的行为；替换为「真实方言下走原生 / 不支持方言下 fail-fast」的新断言，
      并在提交信息中写明这是行为变更
- [ ] 核对 `TestAggregationOrderByDialectWiring` 的判别性用例（`:131-133`）在改动后仍具判别力；
      若退化为非判别性通过，须补充能区分的用例

Exit Criteria:

- [ ] 两处 ORM 路径传入真实方言名，不再有 `null` 字面量（grep 确认）
- [ ] `dialect == null`（若仍可发生）请求空序时抛带 `reason` 的错误码，有单测断言错误码与 `reason`
- [ ] MySQL 既有两条语义（省略 / fail-fast）测试**未被削弱**且仍通过
- [ ] **接线验证**：证明 ORM 路径确实走到 `buildOrderByClause` 的新分支（而非绕过）——
      对两个 processor 各加一条断言产出 SQL 的用例
- [ ] **无静默跳过**：无「忽略空序请求继续执行」的分支
- [ ] **端到端验证**：ORM 聚合查询在 H2 上下文（H2 走原生）中请求空序产出可执行 SQL，
      经真实 JDBC 执行断言结果行序；MySQL 侧由既有单测覆盖语义
- [ ] **owner-doc**：`docs-for-ai` 聚合路径空序行为已更新（Phase 4 落地）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 4 - 文档、守护与过时警告清除（Fix + Proof）

Status: planned
Targets: `docs-for-ai/**`、`ai-dev/design/**`、`nop-orm*/src/test/**`

- Item Types: `Fix`, `Proof`

- [ ] **删除** `docs-for-ai/02-core-guides/eql-and-database-compatibility.md:143-150` 的
      「另一条通道（当前有缺陷，勿照搬本页描述）」警告——本计划落地后它即为**假声明**，
      且会误导用户回避一个已可用的特性
- [ ] 在该页补 bean 路径与聚合路径的空序行为终态描述（与 EQL 通道的差异：聚合路径为 fail-fast）
- [ ] design §3.8 追加 D3–D5 留痕：为何聚合路径不能改写、为何 `QueryBeanHelper` 不删、
      为何 `toString()`/`shouldNullsFirst()` 不属 SQL 语义
- [ ] 新增门控守护测试：断言「`supportNullsFirst=false` 的方言下 bean 通道产出 CASE 改写」
- [ ] 变异验证：把方言取值改错 / 把门控短路掉，守护测试必须转红

Exit Criteria:

- [ ] **证明**：改错任一方言取值或移除门控会导致守护测试失败
- [ ] owner doc 中不再有「该路径当前有缺陷」的过时声明（逐句核对）
- [ ] design 与实际终态一致，无 "Proposed vs Current" 叙事
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata-service --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 本 plan 与所改 owner doc 自身 0 条 broken link
- [ ] `ai-dev/logs/` 当日条目已更新

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部勾选后才能标 `completed`。
> **Protected Area 声明**：`nop-core` / `nop-api-core`（`nop-*-api`）按 AGENTS.md 属 Protected Area，
> 本计划对二者的改动**仅限 javadoc 注释**、无行为变更；`nop-core` / `nop-api-core` 的任何行为改动
> 都必须先取得用户 `ask-first` 确认并单独立项。

- [ ] F1 #1–#4 与 F3 两处 confirmed live defect 均已修复，无一留在 non-blocking 区
- [ ] 全仓**只有一份** CASE 改写实现：`grep -rn "case when " --include="*.java" ` 在 main 代码中
      只命中 Phase 1 的单元与其唯一调用点
- [ ] 端到端验证断言**结果行序**且经真实数据库执行；docker 命令显式带
      `-Dnop.test.docker.enabled=true`
- [ ] 门控守护测试存在且经变异验证有效
- [ ] `nop-core` 与 `nop-api-core` 仅注释变更：
      `git diff --stat nop-kernel/nop-core nop-kernel/nop-api-core` 无 `.java` 行为行改动
- [ ] `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dao,nop-persistence/nop-orm-drivers/nop-orm-tdengine,nop-batch/nop-batch-jdbc,nop-metadata/nop-metadata-service,nop-kernel/nop-core,nop-kernel/nop-api-core -am` 通过
      （含 `nop-orm-tdengine`：`TdSqlHelper:25` 静态导入 `GenSqlHelper.appendOrderBy`
      并在 `:256` 调用；`tdengine.dialect.xml` 未声明 `supportNullsFirst` ⇒ 回落 false ⇒
      Phase 2 后该方言会从原生改为改写，必须纳入回归）
- [ ] 跨模块回归通过，命令（8 模块，与 2308 同清单）：
      `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dao,nop-ai/nop-ai-service,nop-datav/nop-datav-service,nop-sys/nop-sys-dao,nop-wf/nop-wf-core,nop-wf/nop-wf-service -am -Dmaven.test.failure.ignore=true`
- [ ] `scan-hollow-implementations.mjs` 对 `nop-orm-eql`、`nop-orm`、`nop-dao`、
      `nop-metadata-service`、`nop-core` 逐个执行，均退出码 0
- [ ] `check-import-order.mjs --module <每个被改模块>` 退出码 0（该工具只接受 `--module`，无文件参数；
      且它是 advisory 工具，不作为不可降级硬门禁，仅用于避免新增噪音）
- [ ] 生成幂等：重跑 `./mvnw install -pl nop-persistence/nop-orm -DskipTests` 后
      `git status --porcelain nop-persistence/nop-orm` 为空
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` —— 本 plan 与所改 owner doc 自身 0 条 broken link；
      命令级残留错误按归因记录（当前基线存在与本计划无关的既有错误）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2309-bean-orderby-nulls-first-gating.md --strict` 退出码 0
- [ ] 独立子 agent closure audit 已完成，`Closure` 段已写入 evidence

## Deferred But Adjudicated

### 删除 `QueryBeanHelper.buildOrderBySql`（nop-core 死 API）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 该方法**零调用方**（全仓 grep 确认），不是用户可达缺陷，故不构成本计划的
  in-scope live defect。本计划只加 javadoc 危险标注。删除属 `nop-core` 行为/API 变更，
  按 AGENTS.md Protected Area 需独立立项
- Successor Required: `yes`
- Successor Path: 独立 plan（`nop-core` API 清理），需评估二进制兼容与外部调用方

### `ORDER BY ... COLLATE`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需先产出跨库 collation 名字映射表（MySQL `utf8mb4_general_ci` / PG collation 名 /
  Oracle `NLS_SORT` 各异），且 H2 实测在 ORDER BY 表达式上不接受 COLLATE 语法。2308 与本计划均维持不支持
- Successor Required: `yes`
- Successor Path: 独立 plan

### `productName → IDialect` 通用映射设施

- Classification: `optimization candidate`
- Why Not Blocking Closure: F3 按 D3 走 fail-fast，不需要方言即可安全失败；F1 在 ORM 层直接持有 `IDialect`，
  也无需 productName 映射。当前无消费方需要它。`DialectManager.getDialect(String)` 按方言名索引，
  与 productName 命名空间不一致（`MySQL` vs `mysql`/`mysql5.7`），若将来需要应作为独立设施立项
- Successor Required: `no`

## Non-Blocking Follow-ups

- `docs-en/dev-guide/orm/` 下的英文 ORM 文档若描述 bean 路径空序行为，需与本计划终态对齐
- `oracle` / `dm` / `tdengine` / `db2` 未实测空序行为：`supportNullsFirst=false` 走 CASE 改写
  在任何方言上都语义正确（纯列引用、非 DISTINCT/窗口），故不构成正确性风险，只影响 SQL 冗长度

## Closure

Status Note: 待填写
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 待填写