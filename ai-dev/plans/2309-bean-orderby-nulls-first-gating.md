# 2309 bean 路径与聚合路径 ORDER BY 空序方言门控（F1 + F3）

> Plan Status: draft
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
| 7 | `QueryBeanHelper.buildOrderBySql` `QueryBeanHelper.java:53-76`（`:69`/`:71` 直接 append 文本） | 否（且在 nop-core，**不可能**持有） | **零调用方**（全仓 grep 确认）⇒ 死 API，非用户可达缺陷，见 D4 |
| 8 | `OrderFieldBean.toString()` `:35-39` 渲染 `" nulls first"` | — | 仅调试输出，不参与 SQL，见 D5 |
| 9 | `OrderFieldBean.shouldNullsFirst()` `:132-136` | — | 仅被内存比较器（`OrderByComparator`）使用，不参与 SQL，见 D5 |

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
`MixedSameDbJoinAggregationProcessor:126`、`ExternalExternalJoinAggregationProcessor:93`、
`EntityAggregationProcessor:183` 三处 guard 先一步抛 `ERR_AGGR_UNSUPPORTED_DIALECT`。
（2308 的 Deferred 段落曾把 mariadb/dm/tdengine/db2 列为 F3 缺陷面，该表述**错误**，此处更正。）

**真实且唯一可达的缺陷**：`EntityAggregationProcessor.java:141` 与
`EntityEntityJoinAggregationProcessor.java:128` **刻意**传 `dialect = null`
（两处注释均指向 plan `2026-08-06-1228-1` 的 Deferred，并写明隐患是
「ORM 背后接 MySQL 的部署场景」）。此时 `:375` 无条件输出原生 `NULLS` 子句——
若该 ORM 底层是 MySQL，即为**语法错误 SQL**。

### 已裁定的设计约束（Phase 1 结论，见 Goals 前的 Decisions）

- **D1**：F1 一律在 **ORM 层**修，`SQL.SqlBuilder` 与 `nop-core` **不动**。方言经
  `ormTemplate.getSessionFactory().getDialectForQuerySpace(entityModel.getQuerySpace())` 取得。
  不新增任何跨模块公开 API，不触碰 Protected Area。
- **D2**：F3 的修复面**只有** `dialect == null` 这两条 ORM 路径。
  `mariadb`/`dm`/`tdengine`/`db2` 不在范围内（已被上游 guard 拦住）。
- **D3**：`AggregationHelper` 的排序表达式来自 `nameToExpr`，是 `SUM(x)` / `date_trunc(...)` 一类
  **聚合表达式**（`:346-350` 拒绝任何含 `?` 的表达式，正说明它们是表达式而非列引用）。
  design §3.8 规定改写**只对纯列引用**允许 ⇒ 聚合路径**无法**走 CASE 改写，
  其正确终态是 **fail-fast**。MySQL 现有语义（与缺省一致则省略、不一致则 fail-fast）
  **保持不变**，不改为改写。
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
| D3 | 聚合路径终态为 fail-fast，不改为 CASE 改写 | 排序表达式是聚合表达式，非纯列引用，design §3.8 不允许改写 |
| D4 | `QueryBeanHelper.buildOrderBySql` 只加 javadoc 标注，不删除 | 零调用方，非用户可达；删除属 nop-core 变更 |
| D5 | `OrderFieldBean.toString()` / `shouldNullsFirst()` 不动 | 不参与 SQL 产出 |
| D6 | 反转排序（`appendReverseOrderBy`）必须同步翻转 CASE 键方向 | 否则 `findNext` 静默返回相反空序 |
| D7 | 门控失败一律抛带 `reason` 的错误码 | Anti-Slacking：不得静默忽略空序请求 |

## Execution Plan

### Phase 1 - 复用既有空序改写能力（Feature）

Status: planned
Targets: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/`

- Item Types: `Feature`

- [ ] 把 2308 已验证的 CASE 改写算法提取为**方言无关**的可复用单元（供 `GenSqlHelper` /
      `DaoQueryHelper` 调用），输入为「排序表达式 SQL 文本 + asc + nullsFirst」，输出为
      改写后的 ORDER BY 片段；**不得复制第二份实现**
- [ ] 该单元被至少两处调用，并有测试直接覆盖四种方向组合
- [ ] 单元对「非纯列引用」与「DISTINCT / 窗口上下文」的处理与 EQL 通道一致（响亮失败）

Exit Criteria:

- [ ] 四种方向组合的改写产物有单测断言（行序语义，而非仅字符串）
- [ ] **接线验证**：单测经 mock/spy 证明 `GenSqlHelper` 与 `DaoQueryHelper` 确实调用该单元，
      而非各自仍走老路径
- [ ] **无静默跳过**：不可改写位置抛带 `reason` 的错误码，不返回 null / 空串
- [ ] **端到端验证**：复用 2308 的等价性思路——同一查询在 EQL 通道与 bean 通道下的行序一致
- [ ] No owner-doc update required（本 Phase 只提取内部单元，不改变用户可见行为）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 2 - F1 bean 路径接线（Fix）

Status: planned
Targets: `nop-persistence/nop-orm`、`nop-batch/nop-batch-jdbc`、`nop-kernel/nop-core`（仅 javadoc）

- Item Types: `Fix`

- [ ] `GenSqlHelper:845`（`appendOrderBy`）与 `:567`（`genOrderBy`）改用 Phase 1 单元，
      按 `IDialect.isSupportNullsFirst()` 分支；这两处**已持方言**，不新增参数
- [ ] `DaoQueryHelper.appendOrderBy:213` 与 `appendReverseOrderBy:228` 增加 `IDialect` 参数
- [ ] `OrmEntityDao:526`、`:541-542` 传方言，取法
      `ormTemplate.getSessionFactory().getDialectForQuerySpace(getEntityModel().getQuerySpace())`
- [ ] `JdbcBatchLoaderProvider:214` 传方言，复用 `:223` 的取法
- [ ] 按 D6 处理 `appendReverseOrderBy` 的方向翻转：CASE 键方向须与反转后的排序方向一致
- [ ] 按 D4 为 `QueryBeanHelper.buildOrderBySql` 与 `OrderFieldBean.toString()` 加危险标注（javadoc，无行为变更）
- [ ] 逐一核对 Current Baseline 表 #1–#9，每处标记「已修 / 不在范围 + 理由」

Exit Criteria:

- [ ] **端到端验证（Rule #22）**：`QueryBean.orderBy(... nullsFirst)` 经 DAO 真实执行，
      断言**结果行序**（不是「SQL 含 NULLS 字样」）
- [ ] **真实数据库执行**：因测试库 H2 的 `supportNullsFirst=true`（恒走原生分支），
      改写分支验证**必须**用 docker 门控且命令显式带 `-Dnop.test.docker.enabled=true`
      （仓库无任何 pom 设置该属性，不显式传则测试被跳过 → 假绿）
- [ ] **接线验证**：断言 `DaoQueryHelper` / `GenSqlHelper` 在运行时被调用（spy 或产物断言）
- [ ] **无静默跳过**：三类不可改写位置抛带 `reason` 的错误码
- [ ] `nop-core` 行为**零变更**：`git diff --stat nop-kernel/nop-core` 仅注释行
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 3 - F3 聚合路径 `dialect == null`（Fix）

Status: planned
Targets: `nop-metadata/nop-metadata-service`

- Item Types: `Fix`

- [ ] `AggregationHelper:375` 的「未命中即无条件 append 原生 NULLS」改为：按 D3 抛
      带 `reason` 的错误码（`dialect-unavailable`），不产出无法校验的子句
- [ ] `EntityAggregationProcessor:141`、`EntityEntityJoinAggregationProcessor:128` 两处
      `null` 字面量改为可行动注释，指明为何无法解析方言（D3）
- [ ] **保留** `TestEntityAggregationProcessor` 的既有覆盖：`testBuildOrderByClauseMySqlDefaultSemanticsOmitsClause`、
      `testBuildOrderByClauseMySqlInexpressibleFailsFast` 应**继续通过**
- [ ] 改写 `testBuildOrderByClauseNullDialectKeepsNullsClause`（`:206`，断言 `null` dialect
      **保留** NULLS 子句）——该断言与本 Phase 的修复目标直接矛盾，属**预期失效**；
      改为断言新的 fail-fast 行为，并在提交信息中说明这是行为变更而非测试削弱
- [ ] 核对 `TestAggregationOrderByDialectWiring` 的判别性用例（`:131-133`）在本次改动后
      仍具判别力；若退化为非判别性通过，须补充能区分的用例

Exit Criteria:

- [ ] `dialect == null` 且请求空序时抛带 `reason` 的错误码，有单测断言错误码与 `reason`
- [ ] MySQL 既有两条语义（省略 / fail-fast）测试**未被削弱**且仍通过
- [ ] **接线验证**：证明 ORM 路径确实走到新的 fail-fast 分支（而非绕过 `buildOrderByClause`）
- [ ] **无静默跳过**：无「忽略空序请求继续执行」的分支
- [ ] **端到端验证**：ORM 聚合查询在 H2 上下文中请求空序时，行为符合 D3（响亮失败或明确支持），
      不产出非法 SQL
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

- [ ] F1 #1–#4 与 F3 两处 confirmed live defect 均已修复，无一留在 non-blocking 区
- [ ] 两条路径与 EQL 通道共用同一空序契约（design §3.8）；无第二份改写实现
- [ ] 端到端验证断言**结果行序**且经真实数据库执行；docker 命令显式带
      `-Dnop.test.docker.enabled=true`
- [ ] 门控守护测试存在且经变异验证有效
- [ ] `nop-core` 仅注释变更：`git diff --stat nop-kernel/nop-core` 无 `.java` 行为行改动
- [ ] 无新增跨模块公开 API
- [ ] `./mvnw test -pl nop-persistence/nop-orm,nop-persistence/nop-dao,nop-batch/nop-batch-jdbc,nop-metadata/nop-metadata-service,nop-kernel/nop-core -am` 通过
- [ ] 跨模块回归（2308 的 8 模块清单，命令见 `ai-dev/plans/2308-...md` 的 Closure Gates）
      + 本计划新增的 `nop-batch-jdbc` / `nop-metadata-service` / `nop-core` 全绿
- [ ] `scan-hollow-implementations.mjs` 对 `nop-orm`、`nop-dao`、`nop-metadata-service`、`nop-core` 均退出码 0
- [ ] `node ai-dev/tools/check-import-order.mjs` 对新增/修改文件 0 报错
- [ ] 生成幂等：重跑 `./mvnw install -pl nop-persistence/nop-orm -DskipTests` 后
      `git status --porcelain nop-persistence/nop-orm` 为空
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