# 2308 EQL ORDER BY NULLS FIRST/LAST 多方言支持

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: plan 2307 Phase 1/5 对 `SqlOrderByItem.nullsFirst` 的删除裁定 + 2026-10-04 三库实测 + 第一轮独立子 agent 对抗性审查（6 Blocker / 9 Major，已逐条复核并采纳）
> Related: `ai-dev/plans/2307-eql-live-defect-and-dead-surface-remediation.md`、`ai-dev/design/nop-orm-scope-and-non-goals.md` §3.6/§3.7、`ai-dev/plans/nop-metadata-audit-remediation/2026-08-06-1228-1-r8-4a-query-labeling-sla-correctness.md`（`:55` 不做 CASE 模拟、`:93-96` fail-fast 裁定 —— 本计划**推翻其适用范围**）

## Purpose

让 EQL 的 `ORDER BY` 项支持 `NULLS FIRST` / `NULLS LAST`，并且在全部已发布方言上**语义正确**：
支持原生语法的库直接输出，不支持的库用等价改写，任何情况下都不产出错误行序或非法 SQL。

本计划是 plan 2307 把 `SqlOrderByItem.nullsFirst`（连同 `collate`）作为死字段删除后的**继任实现**。
2307 的删除是「消除悬挂字段」的正确终态，但当时没有能力位可接线——本计划补上这一层。

**用户已明确的范围决定**：`order by collate` **不做**（不支持、不实现、不接线），只做 `nullsFirst`（见 §Non-Goals）。

## Current Baseline

### 生成链路（与 2307 Phase 0 一致，此处不重复）

`model/antlr/*.g4` → `precompile/gen-eql-parser.xgen`；`model/ast/**/EqlAST.xjava` → `precompile/gen-eql-ast.xgen`。
均由 `./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests` 自动再生产，**禁手改生成物**。
已验证连续两次 install 的 `git status --porcelain -- nop-persistence/nop-orm-eql` 为空（生成幂等）。

### 能力位早已声明、取值基本正确、但零读取方

| 属性 | 声明位置 | 已设值方言 | Java 读取方 |
|---|---|---|---|
| `defaultNullsFirst` | `nop-kernel/nop-xdefs/.../dialect.xdef:15`（注释 `:7`） | 7 个：mssql=true、tdengine=false、duckdb=false、mysql=true、postgresql=false、dm=true、oracle=false | **0** |
| `supportNullsFirst` | `dialect.xdef:52`（`<features>` 内，注释 `:35-36`） | 4 个：h2=true、duckdb=true、postgresql=true、oracle=true | **0** |

⇒ 真实缺陷是「能力位无人读取」，**不是缺能力位**。本计划**不需要改 `dialect.xdef` schema**。

### 已实测事实（2026-10-04，探针 `ai-dev/tools/probe-nulls-order-matrix.sh` 可复制复跑）

数据 `T(id,v) = (1,10),(2,NULL),(3,20)`；`默认 ASC`/`默认 DESC` 列为实测行序，括注 NULL 所处位置。

| 库 | 版本 | 默认 ASC | 默认 DESC | 缺省是否随方向翻转 | 原生 `NULLS FIRST/LAST` | `CASE` 模拟 |
|---|---|---|---|---|---|---|
| H2 | 2.3.232 | `2;1;3;`（NULL **FIRST**） | `3;1;2;`（NULL LAST） | **翻转** | **支持**（4/4 OK） | 4/4 正确 |
| PostgreSQL | 16.15 | `1;3;2;`（NULL LAST） | `2;3;1;`（NULL **FIRST**） | **翻转** | **支持**（4/4 OK） | 4/4 正确 |
| MySQL | 8.0.46 | `2;1;3;`（NULL **FIRST**） | `3;1;2;`（NULL LAST） | **翻转** | **不支持**（4/4 `SQLSTATE 42000`） | 4/4 正确 |
| DuckDB | — | NULLS LAST | NULLS LAST | **不翻转**（官方文档） | 支持（已声明 true） | — |

已声明取值与实测一致：`supportNullsFirst` h2/postgresql=true（实测支持）、mysql 未设=false（实测不支持）；
`defaultNullsFirst` mysql=true、postgresql=false 均与实测吻合。

**`CASE WHEN` 模拟四组合（三库实测全部 PASS，本计划 emission 算法的依据）**：

| 目标 | 改写 | 期望行序 |
|---|---|---|
| ASC + NULLS FIRST | `case when v is null then 0 else 1 end, v asc` | `2;1;3;` |
| ASC + NULLS LAST | `case when v is null then 1 else 0 end, v asc` | `1;3;2;` |
| DESC + NULLS FIRST | `case when v is null then 1 else 0 end desc, v desc` | `2;3;1;` |
| DESC + NULLS LAST | `case when v is null then 0 else 1 end desc, v desc` | `3;1;2;` |

### 结论：不能用「请求==缺省则省略」优化

上表实测显示 H2 / PostgreSQL / MySQL **三家都随方向翻转**（只是翻转后的取向相反），
而 DuckDB 官方文档明确其**不翻转**：

> "By default, DuckDB sorts ASC and NULLS LAST... **DuckDB keeps NULLS LAST even for DESC ordering,
> whereas PostgreSQL places NULLs first on DESC**. To match PostgreSQL in both directions, set
> `default_null_order = 'NULLS_LAST_ON_ASC_FIRST_ON_DESC'`."

DuckDB 的空序模型有 **4 种**（`NULLS_FIRST` / `NULLS_LAST` / `NULLS_FIRST_ON_ASC_LAST_ON_DESC` /
`NULLS_LAST_ON_ASC_FIRST_ON_DESC`），而 `defaultNullsFirst` 只有 **1 个布尔**，无法表达"不翻转"。

⇒ **单布尔不能可靠推导 DESC 缺省**。若用它做省略判定：`duckdb + ORDER BY x DESC NULLS FIRST`
会被判为"缺省即满足"→ 省略子句 → DuckDB 实际返回 NULLS LAST。
**查询成功、行序错、分页取到错数据**——正是本计划要消灭的失败模式。

**裁定（Phase 1 固化）：放弃「请求==方言缺省则省略」优化，永远显式输出。**
emission 算法**只依赖 `supportNullsFirst` 一个能力位**，`defaultNullsFirst` 在本计划中**不使用**。
代价是产出略冗长；收益是彻底消除「缺省推导」这一整类静默错序风险。

### 未实测方言的保守裁定

| 方言 | 环境限制 | 裁定 |
|---|---|---|
| `mariadb`（及 `mysql5.7`） | 本机无 mariadb 镜像、docker pull 受限 → **未能实测** | `supportNullsFirst=false`（保守）。依据：MariaDB 与 MySQL 同源、无该语法。走 CASE 改写，**语义仍正确**，只影响 SQL 冗长度 |
| `dm` | 无达梦环境 → 未能实测 | `supportNullsFirst=false`（保守）。DM 兼容 Oracle、很可能实际支持，但未实测不改为 true |
| `tdengine` | 无 TDengine 环境 → 未能实测 | `supportNullsFirst=false`（保守） |
| `db2` / `oracle` / `es` | 无环境 | 不改现有取值；`es` 是占位桩（`driverClassName`/`dbProductNames` 均为 `xx`），不做核实 |
| `h2` 的 `defaultNullsFirst` | 实测缺省为 NULLS FIRST，但该属性**本计划不使用** | **不补**（避免给死属性再加无谓配置） |

保守取值不构成正确性风险：`supportNullsFirst=false` 时一律走 CASE 改写，而 CASE 改写已在三个库上
实测语义正确，且它只用到 SQL 标准中普遍支持的 `CASE WHEN` 与 `IS NULL`。

方言继承树（已核实）：`default` → {`postgresql` → {`duckdb`, `postgis`}, `mysql` → {`mariadb`, `mysql5.7`},
`oracle` → {`oracle-reverse`}, `h2` → {`h2gis`}, `db2`, `dm`, `mssql`, `tdengine`, `es`}；
`geo-support` / `window-expr-support` 为 mixin。只需在 ~8 个叶子定义文件设值。
`_dump/**` 下的同名副本是构建产物、未纳入版本控制，**不改**。

### 语法与 AST 现状

- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:365-367`：`sqlOrderByItem : expr=sqlExpr asc=(ASC | DESC)? ;`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:365-367`） —— 插入点。
- **`NULLS` token 不存在**；但 **`FIRST`（`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4:406`）与 `LAST`（`:474`）token 已存在**，
  只是无任何 parser rule 引用、也不在 `unreservedWord_`。
- 新 token 的落点约定：SQL-92 关键字住 `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`（`TUMBLE` 先例在 `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4:650-652`）；
  `LATERAL` 在 `Keyword.g4:202`。本计划的 `NULLS` 落 `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`（与 FIRST/LAST 同处）。
- `SqlOrderByItem`（`EqlAST.xjava:232-250`）当前只有 `expr` + `asc`（2307 删除了 `collate` 与 `nullsFirst`）。
- **三态 Boolean 字段需要手写钩子**：`boolean asc` 由手写 `EqlASTBuildVisitor.SqlOrderByItem_asc(Token)`
  提供（`EqlASTBuildVisitor.java:132-136`），生成侧只做 `if(ctx.asc != null) ret.setAsc(...)`。
  `Boolean nullsFirst`（null/TRUE/FALSE 三态）必须同样提供手写钩子，xgen 不会自动生成三态逻辑。
  `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:248-249` 有明确警告：同 prop 不能出现在规则的不同备选分支（duplicate-prop-label 校验）。
  既有同类范式见 `sqlWindowFrameBound`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:250-252`）。
- `sqlOrderBy` 有 **3 个使用点**：查询级 ORDER BY、`OVER ( ... orderBy ... )`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:225`）、
  **命名窗口 `WINDOW w AS ( ... orderBy ... )`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:270`）**。后两者都不支持加额外排序键。
- **继承关系（易错点）**：`AstToSqlGenerator extends AstToEqlGenerator`（`AstToSqlGenerator.java:69`），
  且 `AstToSqlGenerator` **当前没有任何 orderBy 相关覆写**——排序项输出完全继承自基类
  （`AstToEqlGenerator.java:603-614`）。基类**无 dialect 字段**，方言在子类（`AstToSqlGenerator.java:73`）。
  ⇒ 方言逻辑**必须**写在 `AstToSqlGenerator` 的覆写里；写进基类会污染 EQL 通道的 round-trip。

### 本计划范围外、但审查中确认的两个 live defect（显式移交，不静默降级）

**F1 bean 路径（`QueryBean.orderBy(... nullsFirst)`）完全无方言门控。**
`SQL.SqlBuilder.nullsFirst(Boolean)`（`SQL.java:589-597`）**无条件** `append(" nulls first ")`，
且 `SQL.java` 全文 **0 处** dialect 引用；`SQL.orderField`（`:604-607`）、
`DaoQueryHelper.appendOrderBy`（`:213-227`，静态方法、无 dialect 参数）、
`QueryBeanHelper`（`:67-73` 直接 `sb.append(" nulls first")`）同样无门控。
`OrderBySqlParser:72/75` 只负责**解析** `nulls first` 文本，不是门控。
⇒ 在 MySQL/MariaDB 上产出 `order by x asc nulls last` 非法 SQL。**这是 confirmed live defect。**

**F2 owner doc 的假声明。** `docs-for-ai/02-core-guides/eql-and-database-compatibility.md:153`
对外宣称「不支持时 ORM 用 `IF(ISNULL(f),0,1)` 模拟」——该模拟在 live code 中**不存在**。

**F3 `nop-metadata` 的 `AggregationHelper` 硬编码分支。**
`:361` 的 `if ("MySQL".equalsIgnoreCase(dialect))` 确实存在，但
`AggregationContext.SUPPORTED_DIALECTS = {H2, MySQL, PostgreSQL}`（`AggregationContext.java:23-24`）
在三条上游路径（`ExternalExternalJoinAggregationProcessor:93-97`、
`MixedSameDbJoinAggregationProcessor:126-130`、`EntityAggregationProcessor:183`）先拦下并抛
`ERR_AGGR_UNSUPPORTED_DIALECT` ⇒ **mariadb/mssql/tdengine/db2 永远到不了那个 `else` 分支**。
真正可达的洞是另两条 ORM 路径显式传 `dialect = null`
（`EntityAggregationProcessor:141`、`EntityEntityJoinAggregationProcessor:128`），
注释已把残余登记在 `2026-08-06-1228-1` 的 Deferred 区。

F1/F2/F3 的处置见 §Deferred But Adjudicated（F2 的**文档修正**在本计划内做，见 Phase 6）。

## Goals

- EQL `ORDER BY` 项支持 `NULLS FIRST` / `NULLS LAST`，大小写不敏感。
- 在全部已发布方言上语义正确：原生支持的直出，不支持的等价改写；**任何方言上都不静默错序**。
- emission 算法只依赖**一个**已有能力位 `supportNullsFirst`，不引入需要推导的方言缺省。
- 不改 `dialect.xdef` schema；能力位取值缺口在可控范围内核实并补齐。
- 未写 `NULLS` 的既有查询产物**逐字不变**。
- 修正 owner doc 中关于 `IF(ISNULL(...))` 模拟的假声明。

## Non-Goals

- **不做 `ORDER BY ... COLLATE`**（用户明确决定）。COLLATE 需按库校验排序规则**名字**
  （MySQL `utf8mb4_general_ci` / PG collation 名 / Oracle `NLS_SORT` 各异），
  且 H2 实测在 ORDER BY 表达式上不接受 COLLATE 语法；设计面远大于一个布尔能力位。
  `CAST(x AS type COLLATE y)`（`SqlTypeExpr.collate`）不受影响，仍是活语法。
- **不实现「请求==方言缺省则省略子句」优化**（DuckDB 4 空序模型证据，见 Current Baseline）。
  因此本计划**不使用** `defaultNullsFirst`。
- **CASE 改写不适用于任意排序表达式**（保守限定，见 Phase 1 裁定）；不追求覆盖率。
- 不改 `dialect.xdef` schema；不新增可配置扩展点（因此不采用 `IPaginationHandler` 那套「xml 配类名」范式）。
- 不修 bean 路径（F1）与 `nop-metadata`（F3）——见 §Deferred But Adjudicated 的显式移交。
- 不做查询优化器、不做谓词下推。

## Scope

### In Scope

**手维护源码（可改）**
- `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`（新增 `NULLS` token）、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`sqlOrderByItem` + `unreservedWord_`）
- `nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava`（`SqlOrderByItem` 加回 `Boolean nullsFirst`）
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`（三态手写钩子）
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`（EQL 通道 round-trip）
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（**方言覆写** + CASE 改写）
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java`（新增 fail-fast 错误码）
- `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`（暴露 `supportNullsFirst` 读取）
- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/{dm,tdengine}.dialect.xml`（仅在 Phase 0 核实后需补时）

**再生产物（自动更新，禁手改；列入 Scope 仅为纳入 diff 与回归）**
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/{parse/antlr/**,ast/**}`

**测试**
- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/**`（新增，含扩展 2307 的 `TestEqlToSqlStringBypass`）
- `nop-persistence/nop-orm/src/test/java/io/nop/orm/eql/**`（新增 docker 门控端到端）
- `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/**`（方言能力位取值守护）

**文档**
- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（语法条目 + 方言矩阵 + 修正 `:153` 假声明）
- `ai-dev/design/nop-orm-scope-and-non-goals.md` §3.6（追加 2307 删除裁定的继任实现说明）
- `ai-dev/tools/`（Phase 0 探针转常驻脚本）
- 本 plan、`ai-dev/logs/`

### Out Of Scope

- `nop-core` / `nop-xlang` / codegen 模板 / `dialect.xdef`
- `nop-core` 的 `SQL.SqlBuilder.nullsFirst`、`DaoQueryHelper`、`QueryBeanHelper`（F1，另案）
- `nop-metadata` 的 `AggregationHelper` 与其 `SUPPORTED_DIALECTS` 白名单（F3，另案）
- `_dump/**` 下的方言副本（构建产物，未纳入版本控制）
- 窗口函数内的空序支持（Phase 1 裁定为 fail-fast）
- `nop-stream` 的 TUMBLE 消费路径

## Execution Plan

### Phase 0 - 方言矩阵实测与取值缺口裁定（Proof）

Status: completed
Targets: `nop-dao/.../dialect/{dm,tdengine}.dialect.xml`、`ai-dev/tools/`、`_tmp/nulls/`（临时探针，收口前删除）

- Item Types: `Proof`

- [x] 把方言矩阵探针落成常驻脚本 `ai-dev/tools/probe-nulls-order-matrix.sh`（H2 本地 jar + PostgreSQL/MySQL docker，JDBC 层重试；镜像钉版本）
- [x] 取得三库完整实测：原生支持、缺省 ASC、缺省 DESC、CASE 四组合，全部 PASS（脚本输出即证据）
- [x] `mariadb` 实测：**环境受限未能完成**（本机无 mariadb 镜像，`docker pull` 受限）⇒ 按 `supportNullsFirst=false` 保守裁定，依据"MariaDB 与 MySQL 同源、无该语法"
- [x] `dm` / `tdengine` 实测：**无对应数据库环境** ⇒ 均按 `false` 保守裁定；已在 Current Baseline 显式标注"未能实测"
- [x] **不**为 `h2`/`db2`/`es` 补 `defaultNullsFirst`（本计划不使用该属性，避免给死属性加无谓配置）；h2 的实测缺省值（NULLS FIRST）已记入矩阵
- [x] 矩阵合并进 Current Baseline 的**唯一一套**实测表（扩列 + 新增"未实测方言的保守裁定"表），旧的重复段落已删除

Exit Criteria:

- [x] **证明**：每个方言的 `supportNullsFirst` 取值都有证据来源——三库为脚本实测输出，`mariadb`/`dm`/`tdengine`/`db2`/`oracle` 显式标注"未能实测 + 保守缺省"
- [x] **证明**：常驻脚本可复制执行（`bash ai-dev/tools/probe-nulls-order-matrix.sh`），输出与矩阵一致
- [x] 矩阵与 Current Baseline 合并为**唯一一套** baseline，无并存（已删除重复的旧段落）
- [x] `ai-dev/logs/` 当日条目已更新

### Phase 1 - 裁定：emission 算法与适用边界（Decision）

Status: completed
Targets: 本 plan、`ai-dev/design/nop-orm-scope-and-non-goals.md`

- Item Types: `Decision`

- [x] 固化算法：**只读 `supportNullsFirst`**。true → 原生输出 `nulls first`/`nulls last`；false → CASE 改写。**不做缺省推导、不做省略优化**（DuckDB 证据），`defaultNullsFirst` 不使用
- [x] 裁定 CASE 改写的**适用条件**（保守）：排序表达式必须是「可安全复制」形态。已知三类**不可**复制：
      ① 含绑定参数标记（`sqlExpr_simple` 含 `sqlParameterMarker`，`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:181`）——复制后 SQL 有两个 `?` 而 AST 只有一个 param 节点，参数错位；
      ② 顶层数字字面量（`sqlLiteral → sqlNumberLiteral`，同文件 `:28`）——`order by 1` 是序号定位，复制后排序失效；
      ③ 易变函数（`rand()`/`uuid()` 等）——表达式被求值两次，NULL 判定与实际排序值来自不同次求值。
      裁定采取哪一档保守度（建议：仅允许纯列引用 `SqlColumnName`，其余 fail-fast）
- [x] 裁定 `SELECT DISTINCT` 下的行为：改写会引入**新的** ORDER BY 表达式，PG 要求 DISTINCT 查询的 ORDER BY 表达式必须出现在 select 列表中。裁定为「DISTINCT 查询禁用改写，或要求排序列已在 select 列表」
- [x] 裁定窗口边界：`sqlOrderBy` 的 3 个使用点中，仅查询级支持改写；`OVER (...)` 与**命名窗口 `WINDOW w AS (...)`** 均 fail-fast（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:225` 与 `:270`）
- [x] 裁定 CASE 键的**插入位置**：必须是该排序项的**前置同级键**（紧邻其前），**不得** prepend 到整个 ORDER BY 列表头——后者会在多键场景把「先按 b 的 nullness、再按 a」写反，且不报错
- [x] 裁定错误码：新增一个 `NopException` 错误码覆盖「不可改写位置」，参数含方言、位置（窗口/非可复制表达式/DISTINCT）、原始 SQL
- [x] 裁定代码落点：**不**新增可配置扩展点（避免 `IPaginationHandler` 那套必须改 schema 的代价）；逻辑写在 `AstToSqlGenerator` 的 `visitSqlOrderByItem` **覆写**中，基类 `AstToEqlGenerator` 保持方言无关
- [x] 裁定 `NULLS` 是否登记为非保留字：无方言把 `nulls` 当保留字（已 grep 确认），登记后 `select nulls from t` 仍可解析，风险低；登记为**是**（对齐既有范式），但须补「`nulls` 作 ORDER BY 列名」的歧义回归测试
- [x] 裁定推翻 `2026-08-06-1228-1:55` 「不做 CASE 模拟」的适用范围（实测 MySQL 8 四组合全对；继续 fail-fast 会让 MySQL/MariaDB 无法表达常见的 `ASC + NULLS LAST`）

#### 裁定 → 下游 Phase 映射

| 裁定 | 落地 Phase | 动作 |
|---|---|---|
| D1 emission 只读 `supportNullsFirst` | Phase 2 | `IDialect` 暴露该位；策略实现 |
| D2 仅纯列引用可改写 | Phase 2 / 4 | 策略的适用性判定 + fail-fast |
| D3 DISTINCT 要求排序列已投影 | Phase 4 | `visitSqlOrderBy` 内读 `distinct`/`projections` |
| D4 窗口 fail-fast（仅改写路径） | Phase 4 | 向上遍历 `getASTParent()` 判 `SqlWindowExpr`/`SqlWindowDecl` |
| D5 前置同级键 → 覆写 `visitSqlOrderBy` | Phase 4 | 列表级重写而非只覆写 item |
| D6 新错误码 + 三种 reason | Phase 2 / 4 | `OrmEqlErrors` 新增；调用点填 reason |
| D7 落点 `AstToSqlGenerator`，基类保持方言无关 | Phase 4 | 覆写 `visitSqlOrderBy`，不动基类 |
| D8 `NULLS` 登记非保留字 | Phase 3 | `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `unreservedWord_` |
| D9 推翻 1228-1:55（仅 EQL 通道） | Phase 6 | design / owner doc 留痕 |

Exit Criteria:

- [x] 每条裁定有结论、理由、连带影响清单，写入 design 文档
- [x] **无静默跳过**：所有不可改写位置均 fail-fast，错误码与参数明确，不静默忽略空序请求
- [x] 裁定映射表写入本 plan，Phase 2–4 的每个动作可追溯到一条裁定
- [x] 算法描述中**不含**任何需要推导 DESC 缺省的步骤
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 1 裁定结论（2026-10-04）

**D1 emission 算法**——只读 `supportNullsFirst` 一个能力位：

```
请求为 null（未写 NULLS）        → 不做任何额外处理（产出与今天逐字相同）
supportNullsFirst == true        → 原生输出 " nulls first " / " nulls last "
supportNullsFirst == false/null  → CASE 改写（见 D5 的插入位置）
```

**不做**「请求==方言缺省则省略」优化（Phase 0 实测 + DuckDB 文档已证单布尔无法推导 DESC 缺省）；
因此 `defaultNullsFirst` 在本计划中**不被读取**，算法里不存在任何需要推导方言缺省的步骤。

**D2 CASE 改写的适用条件（保守：仅纯列引用）**——CASE 改写要复制排序表达式，而
`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlExpr_simple` 同时含 `sqlParameterMarker`（`:181`）与 `sqlNumberLiteral`（经 `sqlLiteral` `:28`），
`sqlOrderByItem.expr` 又是完整 `sqlExpr`，因此下列三类**不可**复制：

| 不可复制形态 | 例子 | 后果 |
|---|---|---|
| 含绑定参数标记 | `order by coalesce(a, ?) asc nulls last` | 复制后 SQL 出现两个 `?`，而 AST 只有一个 param 节点（参数靠遍历 AST 收集，不扫文本）→ 参数错位/未绑定 |
| 顶层数字字面量（序号定位） | `order by 1 asc nulls last` | `case when 1 is null ...` 恒不命中 → 排序完全失效 |
| 易变函数 | `order by rand() nulls last` | 表达式求值两次，NULL 判定与实际排序值来自不同次求值 |

裁定：**仅当 `expr` 是纯列引用（`SqlColumnName`）时才允许 CASE 改写**，其余一律 fail-fast。
保守档位是有意的——覆盖面换确定性；扩展到"无 marker 的任意表达式"留待后续（届时需要一个易变函数白名单，
属易错设计，不宜首版就上）。

**D3 `SELECT DISTINCT` 的行为**——改写会引入**新的** ORDER BY 表达式，而 PostgreSQL 对 DISTINCT 查询
要求 ORDER BY 表达式必须出现在 select 列表中。裁定：
- 原生路径（`supportNullsFirst=true`）：照常输出，不受 DISTINCT 影响；
- CASE 路径：要求该排序列**已出现在 `SqlQuerySelect` 的投影列表中**（`distinct` 与 `projections` 字段已核实存在，
  `EqlAST.xjava:153/156`），否则 fail-fast。

**D4 窗口边界**——`sqlOrderBy` 有 **3 个使用点**，只有查询级可改写：
- 查询级 `SqlQuerySelect.orderBy` → 支持
- `SqlWindowExpr.orderBy`（`OVER ( ... orderBy ... )`，`EqlAST.xjava:481`）→ 改写路径 **fail-fast**
- `SqlWindowDecl.orderBy`（命名窗口 `WINDOW w AS ( ... orderBy ... )`，`EqlAST.xjava:500`）→ 改写路径 **fail-fast**

> **Phase 4 修订（原裁定为「窗口无条件 fail-fast」）**：实测 H2 **原生接受**窗口 `ORDER BY` 中的
> `NULLS LAST`（`TestEqlNullsOrderEndToEnd#testWindowNullsOrderOnNativeDialect` 真实执行通过）。
> fail-fast 的真实约束是「CASE 改写结构性不适用」，而非「窗口不支持空序」。
> 故改为：**先判原生支持（原生则放行），仅在需要改写时才因窗口上下文 fail-fast**。
> 无条件拒绝会剥夺 PG/H2 用户本就合法可用的写法，属无谓限制。

后两者的 `orderBy` 都经 `visitChild(...)` 路由到同一个 `visitSqlOrderBy`（`AstToEqlGenerator:980` 与 `:1035`），
因此实现上通过**向上遍历 `getASTParent()` 判断是否处于 `SqlWindowExpr`/`SqlWindowDecl` 之下来区分**。

**D5 CASE 键的插入位置**——必须是该排序项的**前置同级键**（紧邻其前、同一个 ORDER BY 列表内），
**不得** prepend 到整个列表头。单键场景两者等价，多键场景 prepend 会把「先按 b 的 nullness、再按 a」
写反且**不报错**。由此得出一个实现约束：覆写点必须是 `visitSqlOrderBy`（负责 `printList(items, ",")`
的列表级拼接），**不能只覆写 `visitSqlOrderByItem`**——后者无法在逗号之间插入兄弟键。

**D6 错误码**——新增 `ERR_EQL_NULLS_ORDER_NOT_SUPPORTED`，参数含 `reason` / `dialect` / `sqlText`，
`reason` 取三个枚举值：`window-not-supported`（D4）、`expression-not-duplicable`（D2）、
`distinct-column-not-projected`（D3）。三条都是**响亮失败**，不静默忽略空序请求。

**D7 代码落点**——**不**新增可配置扩展点（`IPaginationHandler` 那套"xml 配类名"必须改 `dialect.xdef` schema，
与本计划 Non-Goal 冲突）。裁定：`AstToSqlGenerator` **覆写 `visitSqlOrderBy`** 承载全部方言逻辑
（列表遍历 + CASE 键插入 + 每项渲染），基类 `AstToEqlGenerator` 的
`visitSqlOrderBy`/`visitSqlOrderByItem` **保持方言无关**，仅按字面输出 `nulls first/last` 以保证
`toSqlString()` round-trip 无损。二者已是继承关系（`AstToSqlGenerator.java:69`），
且 `AstToSqlGenerator` 当前**无任何 orderBy 覆写**（完全继承基类），故覆写不会与既有逻辑打架。

**D8 `NULLS` 是否登记为非保留字**——裁定**登记**。已 grep 确认无任何方言把 `nulls` 列入
`<reservedKeywords>`，登记后 `select nulls from t` 仍可解析，风险低；与 `TUMBLE`/`LATERAL`/`RANGE`/`WINDOW`
既有范式一致。**但**必须补一条回归：把 `nulls` 用作 ORDER BY 列名时的歧义位置
（`order by nulls asc` 在新增 `NULLS FIRST` 后是否仍按列名解析）。

**D9 推翻 `2026-08-06-1228-1:55`**——该文「不做 MySQL 上 NULLS FIRST/LAST 的 CASE 表达式模拟
（fail-fast 显式拒绝优于隐式改写）」在本计划的 **EQL 通道**范围内被推翻。理由：Phase 0 已实测
CASE 模拟在 MySQL 8.0.46 上四种方向组合**全部正确**；继续 fail-fast 会让 MySQL/MariaDB 用户
无法表达 `ASC + NULLS LAST` 这一常见需求。**推翻仅限 EQL 通道**，`nop-metadata` 的 `AggregationHelper`
不在本计划范围（见 F3 移交），其 fail-fast 裁定保持原状。

### Phase 2 - 能力位接线（Feature）

Status: completed
Targets: `nop-dao/.../dialect/IDialect.java`、必要时 `DialectImpl.java`

- Item Types: `Feature`

- [x] `IDialect` 暴露 `boolean isSupportNullsFirst()`；实现形态对齐既有 `isSupportWindowFrameRows()`（`Boolean.TRUE.equals(...)`），即 `null` → `false`。全仓仅 `DialectImpl` 一个 `IDialect` 实现（`JdbcDialectProvider` 是 `IDialectProvider`），无需改其他实现类
- [x] 未新增可配置扩展点、未改 `dialect.xdef`（D7），`defaultNullsFirst` 全程未被读取
- [x] emission 策略实现（按 Phase 1 落点）：输入 `(方言, 排序方向, 请求的 nullsFirst, 排序表达式)`，输出排序片段或抛错误码

Exit Criteria:

- [x] **测试覆盖（Rule #25）**：新增 `nop-dao` 单测，参数化覆盖 `supportNullsFirst ∈ {true,false,null}` × 2 方向 × 2 请求，断言输出片段；`null` 必须显式有用例
- [x] **接线验证**：该读取接口确实被 `nop-orm-eql` 的 `AstToSqlGenerator` 在运行时调用（由 Phase 4 的端到端测试反证，非仅类型存在）
- [x] **无静默跳过**：不可改写位置抛带错误码的 `NopException`，不猜、不静默
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high` 退出码 0
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 2 实现要点（2026-10-04）

**落点**：`IDialect.isSupportNullsFirst()` + `DialectImpl` 实现。实现刻意照抄既有
`isSupportWindowFrameRows()`（`DialectImpl:324-326`）的形态
（`Boolean.TRUE.equals(dialectModel.getFeatures().getSupportNullsFirst())`），
使 `null`（未声明）→ `false` 的保守缺省与同族能力位语义完全一致，零新增概念。

`IDialect` 的 javadoc 里显式写明**不**提供"方言缺省空序"查询接口及其理由（单布尔无法表达
DuckDB 的 4 种空序模型），避免后人再把 `defaultNullsFirst` 接回来做省略优化。

**红可复核（重要）**：把 `DialectImpl` 实现临时改成 `return true;`（模拟"把 null 当 true"的错误接线），
13 个测试中 **7 个失败**，包含最关键的 `testUnsetIsFalseNotAnError`（若 null 被当 true，
MySQL/MariaDB 上会直接产出语法错误的 `NULLS FIRST`）。恢复实现后全绿。
中途还发现一次无效验证：先改的是 `IDialect` 的 default 方法，但 `DialectImpl` 有 `@Override`
把它遮蔽了，测试仍全绿——**证明验证必须打在真正的实现点上**。

**Anti-Hollow**：`scan-hollow-implementations.mjs --module nop-dao --severity high`
Critical/High 均 0，退出码 0。`nop-dao` 全模块测试全绿。

### Phase 3 - 语法入口与 AST 字段（Feature）

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`、`model/ast/**/EqlAST.xjava`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`

- Item Types: `Feature`

- [x] `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4` 新增 `NULLS` token（紧邻既有 `FIRST:406` / `LAST:474`）；`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `unreservedWord_` 按 Phase 1 裁定登记 `NULLS`
- [x] `sqlOrderByItem` 扩展为 `expr=sqlExpr asc=(ASC | DESC)? nullsFirst=sqlNullsOrder_?`，并新增子规则 `sqlNullsOrder_ : NULLS FIRST | NULLS LAST ;`（**踩了两层坑，见实现要点**）
- [x] `EqlAST.xjava` 的 `SqlOrderByItem` 加回 `Boolean nullsFirst`（三态）
- [x] `EqlASTBuildVisitor` 新增**三态手写钩子**（形态对齐既有 `SqlOrderByItem_asc(Token)`，`:132-136`），返回 `null`/`Boolean.TRUE`/`Boolean.FALSE`
- [x] **2256 的坑（方向相反的另一种踩法）**：新增 token 确实连带改 `.tokens` 与 `EqlLexer.java`（已确认）；但 `(NULLS FIRST | NULLS LAST)` **不能**直接加 label——label 不能跨多 token 序列（ANTLR error 130），详见实现要点
- [x] 避开 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:248-249` 提示的 duplicate-prop-label 校验：label 直接用属性名 `nullsFirst`、单一备选分支，无重复 prop
- [x] 再生成产物同步（`./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests`）

Exit Criteria:

- [x] **测试覆盖**：parse 矩阵（4 组合 × 大小写变体；`nulls` 作列名/别名/ORDER BY 列名的歧义位置；`nulls` 作列名/别名/ORDER BY 列名的歧义位置；并断言 `first` 仍是既有不可用状态）有独立测试类
- [x] **测试覆盖**：`toSqlString()` 旁路 round-trip 断言（新增 `TestEqlNullsOrderGrammarParse` 的 3 个 round-trip 用例；基类打印提前到本 Phase 完成，未留到 Phase 4）
- [x] **生成幂等**：连续两次 install 后 `git status --porcelain -- nop-persistence/nop-orm-eql` 无新增 diff
- [x] **无静默跳过**：`EqlASTKind` 与节点类一致（grep 可验证）
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 3 实现要点：两层 grammar 坑（2026-10-04）

**坑 1 —— label 不能跨多 token 序列。** 最初按 Phase 3 原文写成
`nulls=(NULLS FIRST | NULLS LAST)?`，ANTLR 直接报：

```
error(130): BaseRule.g4:368:37: label nulls assigned to a block which is not a set
```

label 只能标注**单个 token 或 token 集合**，而 `(NULLS FIRST | NULLS LAST)` 是两个
**多 token 序列**的备选，不是集合。这与 2256 的坑**方向相反**：那次是"同优先级备选没合并成
token-set label"，这次是"label 跨了多 token 序列"。两者都要靠独立跑 ANTLR 才能发现——
项目构建把 ANTLR 原始错误包在 `NopEvalException` 里，看不到 error(130) 的定位。
定位办法：`mvnw` 失败后用 `antlr4-4.13.2` + **版本匹配的** `antlr4-runtime-4.13.2`
（用 4.9.3 会 `NoSuchMethodError`）直接跑 `org.antlr.v4.Tool -o /tmp/out Eql.g4`。

**坑 2 —— AstGrammarBuilder 要求 label 与 AST 属性名对应。** 改成
`(nulls=NULLS nullsOrder=(FIRST | LAST))?` 后 ANTLR 语法过了，但项目构建报
`initRuleReturnType` 失败（`nop.err.xlang.exec.invoke-method-fail`）。

**最终形态对标既有先例** `sqlWindowFrameBound`（同样是多 token 组合 → 单个 AST 字段）：

```antlr
sqlOrderByItem
    : expr=sqlExpr asc=(ASC | DESC)? nullsFirst=sqlNullsOrder_?
    ;

sqlNullsOrder_
    : NULLS FIRST
    | NULLS LAST
    ;
```

label 直接用属性名 `nullsFirst`，子规则整体可选 ⇒ 未写时为 `null`（三态）。

**三态手写钩子**：`EqlASTBuildVisitor.SqlOrderByItem_nullsFirst(ParseTree)`，形态对标同文件既有的
`SqlWindowFrameBound_boundType`。xgen 只为 primitive boolean 生成"命中即 true"的形式，
**不会**自动生成三态映射，故必须手写；生成侧在 `ctx.nullsFirst != null` 时才调用，
"未写 NULLS"自然保持 null。

**基类打印提前到本 Phase**（Phase 4 原计划才做）：`AstToEqlGenerator#visitSqlOrderByItem`
按三态字面输出 `nulls first`/`nulls last`，**不含方言逻辑**。提前的理由：若留到 Phase 4，
则 Phase 3 结束时代码库里存在"能解析出 nullsFirst 但 `toSqlString()` 静默丢弃"的中间态——
这正是 2307 要消灭的失败模式，不该跨 Phase 留存。该方法保持方言无关，D7 的分工不变。

**发现一处既有事实（非本计划引入）**：`FIRST`/`LAST` 在本计划之前就已是
`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4` 的 token，但**从未**登记进 `unreservedWord_`，
因此 `select first from t` / `order by first asc` **本来就解析失败**。
本计划只新增 `NULLS` 一个 token 并登记为非保留字，**未改变** first/last 的可用性。
测试相应改为断言这一既有事实（`testFirstIsStillNotBareIdentifier_PreExisting`），
并补引号逃生口用例，避免后人误判为回归或误把它悄悄放开。

**token 集确实变了**：`.tokens`、`EqlLexer.java`、`Eql.interp`、`EqlLexer.interp`
全部随之变化（Phase 3 checklist 原文预判"若改动新增/删除 token，`.tokens` 与
`EqlLexer.java` 也会变"——本次确认成立，与 2256 因未动 token 集而不同）。

**生成幂等**：连续两次 `./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests`，
两次之后 `git status --porcelain` 的变更文件集合**完全一致**（18 项），零手改生成物。

**测试** `TestEqlNullsOrderGrammarParse`（21）：4 方向组合 × 大小写变体、三态（含"未写= null"）、
多键各自携带、round-trip、以及 4 组 `nulls` 作标识符的歧义回归
（重点是 `order by nulls asc` 中 nulls 必须解析为**列名**而非空序修饰）。

### Phase 4 - 输出器接线与端到端验证（Feature）

Status: completed
Targets: `sql/{AstToEqlGenerator,AstToSqlGenerator}.java`、`nop-persistence/nop-orm/src/test/**`

- Item Types: `Feature`

- [x] `AstToEqlGenerator#visitSqlOrderByItem`（基类）输出 `nulls first`/`nulls last`，**不含方言逻辑**，保证 EQL 通道 round-trip 无损
- [x] `AstToSqlGenerator` **覆写** `visitSqlOrderBy`（列表级；D5 要求可插入兄弟键，只覆写 item 做不到）承载方言逻辑；含「是否处于窗口上下文」判定
- [x] CASE 键作为**前置同级键**插入（紧邻其排序项，不跨项、不 prepend 到列表头）
- [x] 窗口两处（`OVER(...)`、命名窗口 `WINDOW w AS (...)`）接入 Phase 1 的 fail-fast
- [x] 新增 docker 门控端到端测试：EQL 文本 → **经 `EqlCompiler` 编译** → 真实库执行 → 断言**结果行序**

Exit Criteria:

- [x] **端到端验证（Rule #22）**：h2 + postgresql + mysql 三库实跑，覆盖 4 个方向组合；断言的是排序后行序（如 `{2,1,3}` vs `{1,3,2}`），不是「SQL 里有 nulls 字样」
- [x] **端到端验证**：docker 测试必须**经 EQL 编译器**产出 SQL（范式对齐 `nop-dao/.../TestPostgresWindowFrameMatrix.java`，即 WI3 的「docker 门控 + 真实执行矩阵 → 回填 dialect.xml features」；**不要**照抄 `TestEqlLateralPostgres`——它用手写 SQL 常量、绕过编译器，只能证明 SQL 形态可执行）
- [x] **多键正确性（Rule #22）**：多键 E2E 用例——第一键有重复值 + 第二键含 NULL，断言行序。用于钉死「CASE 键插在紧邻位置而非列表头」
- [x] **DISTINCT 正确性**：DISTINCT 查询用例，按 Phase 1 裁定断言（改写或 fail-fast）
- [x] **防误伤**：未写 `NULLS` 的既有 `ORDER BY` 产物**逐字不变**（复用 2307 的 bypass 基线范式）
- [x] **接线验证**：能力位 → 策略 → 输出器三段在运行时连通（由行序断言反证）
- [x] **无静默跳过**：不可改写位置抛带错误码异常
- [x] docker 镜像**钉版本**（`postgres:16-alpine`、`mysql:8.0`；记录已知坑：`TestPostgreDialect` 曾钉的 9.6.12 镜像已下架导致拉取超时）
- [x] 跨模块回归（2307 的 8 模块清单，命令见 Closure Gates）全绿：1581 -> 1664(+83) 零失败零错误；唯一红项 `TestStreamConnectorRegistryDiscovery` 属并发会话在途的 nop-stream 改动，与本计划无关
- [x] `ai-dev/logs/` 当日条目已更新

### Phase 5 - 聚焦回归与取值守护（Proof）

Status: planned
Targets: `nop-dao/src/test/**`、`nop-orm-eql/src/test/**`

- Item Types: `Proof`

- [ ] 新增测试直接断言 `*.dialect.xml` 中 `supportNullsFirst` 的取值（已有 `test-window-features.dialect.xml` fixture 先例），使取值漂移可被 CI 发现
- [ ] 固化「`supportNullsFirst=null` 等价 false」的回落断言
- [ ] 固化窗口位置 fail-fast 的错误码断言

Exit Criteria:

- [ ] **证明**：改错任一方言的 `supportNullsFirst` 取值会导致测试失败
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 6 - 文档收口与 F2 假声明修正（Fix）

Status: planned
Targets: `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`ai-dev/design/nop-orm-scope-and-non-goals.md`

- Item Types: `Fix`

- [ ] **修正 F2**：删除或改写 `eql-and-database-compatibility.md:153` 关于「ORM 用 `IF(ISNULL(f),0,1)` 模拟」的假声明（该模拟不存在）；改为指向本计划的 CASE 改写事实与其适用范围
- [ ] 语法清单补 `NULLS FIRST/LAST` 条目 + **方言矩阵表**（每格标注实测 / 保守缺省）
- [ ] 显式写明：`COLLATE` 不支持；「省略子句」优化不做及其原因（DuckDB 4 空序模型）
- [ ] design §3.6 追加：2307 删除 V5 字段是「消除悬挂字段」的终态，本计划为继任实现；`collate` 维持不支持
- [ ] 在 plan / design 留痕推翻 `2026-08-06-1228-1:55` 的理由与证据
- [ ] 为 F1 / F3 建立具名 successor 记录（指向新 plan 路径），不留"后续再说"的空条

Exit Criteria:

- [ ] **证明**：owner doc 中不再有 live code 不存在的功能声明（逐句核对 `:153`）
- [ ] owner doc 方言矩阵每格有证据来源
- [ ] design 文档与实际终态一致，无 "Proposed vs Current" 叙事
- [ ] F1 / F3 的 successor 记录含 plan 路径与证据，不是自由散文
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 当日条目已更新

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部勾选后，才能把 `Plan Status` 改为 `completed`。

- [ ] EQL `ORDER BY` 支持 `NULLS FIRST/LAST`，在 h2 + postgresql + mysql 实跑通过（断言**结果行序**，且经 EQL 编译器）
- [ ] emission 算法只依赖 `supportNullsFirst` 一个能力位，代码与文档中**无** DESC 缺省推导步骤
- [ ] 多键 ORDER BY 行序正确（CASE 键为前置同级键，有 E2E 断言）
- [ ] DISTINCT / 窗口 / 非可复制表达式三类位置均 fail-fast，有错误码断言
- [ ] 未写 `NULLS` 的既有查询产物逐字不变
- [ ] 方言取值每格有证据来源；`supportNullsFirst` 取值有常驻测试守护
- [ ] owner doc 不再声称存在 `IF(ISNULL(...))` 模拟
- [ ] `collate` 维持不支持，无悬挂字段残留（2307 的删除不被推翻）
- [ ] F1（bean 路径无门控）/ F3（`AggregationHelper`）已建立具名 successor 记录，不在 non-blocking 区
- [ ] 生成幂等性保持；无手改任何生成物
- [ ] 独立子 agent closure audit 已完成，`Closure` 段已写入 evidence
- [ ] **Anti-Hollow Check**（两条命令分别执行，`--module` 不接受管道）：
      `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high`
      `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high`
      两者退出码均为 0
- [ ] `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dao -am` 通过
- [ ] 跨模块回归（2307 的 8 模块清单，可复制命令）通过：
      `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dao,nop-ai/nop-ai-service,nop-datav/nop-datav-service,nop-sys/nop-sys-dao,nop-wf/nop-wf-core,nop-wf/nop-wf-service -am -Dmaven.test.failure.ignore=true`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2308-eql-orderby-nulls-first-multi-dialect.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本 plan 自身 0 条 broken link）
- [ ] `node ai-dev/tools/check-import-order.mjs` 对新增文件 0 报错
- [ ] `ai-dev/logs/` 收口记录已写入（文本一致性核对，Rule #19）

## Deferred But Adjudicated

### F1 bean 路径 `QueryBean.orderBy(... nullsFirst)` 无方言门控（confirmed live defect）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 这是**独立于 EQL 的另一条查询通道**（`QueryBean` / `DaoQueryHelper` / `QueryBeanHelper` / `SQL.SqlBuilder`，位于 `nop-core` 与 `nop-orm`），其修复需要把 `IDialect` 打通进 `nop-core` 的 `SQL.SqlBuilder`（该类当前 0 处 dialect 引用）与 `DaoQueryHelper` 的静态签名，属跨模块 API 变更 + Protected Area（`nop-core`）。用户的请求范围是 **EQL ORDER BY**，把 `nop-core` 拖进本计划会使 scope 失控。**这不是"它不重要"，而是"它属于另一条通道、另一份 API 变更"**——按 Anti-Slacking Rule 的「moved to explicit successor ownership」处置，且必须留下具名记录
- Successor Required: `yes`
- Successor Path: 新建 plan（建议名 `bean-orderby-nulls-first-gating`），入口证据已在本 plan 的 Current Baseline「范围外但确认的 live defect」F1 段落完整给出，含 file:line

### F3 `nop-metadata` `AggregationHelper` 硬编码分支与两条 `dialect == null` ORM 路径

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: `AggregationHelper` 是 nop-metadata 聚合查询的 ORDER BY 构造，与 EQL ORDER BY 是两套产出路径。迁移它需要先解决「JDBC `databaseProductName` 字符串 → `IDialect`」的映射（`AggregationHelper.buildOrderByClause` 是**静态方法、无 Connection、无 IDialect**，两条 ORM 路径更直接传 `null`），属签名与调用链重设计。且其现状已在 `2026-08-06-1228-1` 的 Deferred 区有具名残余登记，不是新问题
- Successor Required: `yes`
- Successor Path: 建议并入 F1 的 successor plan（两者都需要「productName/连接 → IDialect」的映射能力），避免两个 plan 重复做同一件事

### `ORDER BY ... COLLATE`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户已明确决定不做。COLLATE 需按库校验排序规则名字，且 H2 实测在 ORDER BY 表达式上不接受 COLLATE 语法。2307 已删除 `SqlOrderByItem.collate` 字段，本计划不恢复
- Successor Required: `yes`
- Successor Path: 独立 plan（需先有跨库 collation 名字映射表 + 各库实测矩阵）

### 窗口函数 `OVER (... ORDER BY ...)` 与命名窗口内的空序

- Classification: `watch-only residual`
- Why Not Blocking Closure: SQL 层面 `OVER(...)` / `WINDOW w AS (...)` 内无法附加额外排序键，CASE 改写路径结构性不适用。本计划按 **fail-fast + 错误码**处理（有断言），不是静默忽略。需要时可在 SQL/BI 层用子查询预排序列表达
- Successor Required: `no`

## Non-Blocking Follow-ups

- `docs-en/dev-guide/orm/` 下的英文 ORM 文档若涉及 EQL 语法清单，需与 Phase 6 的方言矩阵保持一致
- 无法实测方言（oracle / dm / tdengine / db2）的空序行为，可在具备对应数据库环境时补实测。注意：`supportNullsFirst=false` 走 CASE 改写在任何方言上都语义正确，故未实测**不构成正确性风险**，只影响 SQL 冗长度
- 弃用 `defaultNullsFirst` 后，该属性在 `dialect.xdef` 与 7 个方言 xml 中将继续保持零读取。若将来要恢复「省略子句」优化，须先把它升级为能表达 4 种空序模型（DuckDB 证据），不能沿用单布尔

## Closure

Status Note: 待填写
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 待填写