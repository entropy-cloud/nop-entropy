# 2308 EQL ORDER BY NULLS FIRST/LAST 多方言支持

> Plan Status: draft
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

### 已实测事实（2026-10-04，docker / 本地 jar 实跑）

数据 `T(id,v) = (1,10),(2,NULL),(3,20)`：

| 库 | 版本 | 原生 `NULLS FIRST/LAST` | 缺省 ASC | 缺省 DESC | `CASE WHEN` 模拟 |
|---|---|---|---|---|---|
| H2 | 2.3.232 | **支持**（4 组合全 OK） | `2;1;3;` ⇒ NULLS **FIRST** | — | n/a |
| PostgreSQL | 16.15 | **支持**（4 组合全 OK） | `1;3;2;` ⇒ NULLS LAST | `2;3;1;` ⇒ NULLS **FIRST**（翻转） | 可用 |
| MySQL | 8.0.46 | **不支持**（`You have an error ... near 'nulls first'`） | `2;1;3;` ⇒ NULLS **FIRST** | — | **可用**（4 组合全对） |

已声明取值与实测一致：`supportNullsFirst` h2/postgresql=true（实测支持）、mysql 未设=false（实测不支持）；
`defaultNullsFirst` mysql=true、postgresql=false 均与实测吻合。

**`CASE WHEN` 模拟四组合（MySQL 8.0.46 实测，本计划 emission 算法的依据）**：

| 目标 | 改写 | 实测 |
|---|---|---|
| ASC + NULLS FIRST | `case when v is null then 0 else 1 end, v asc` | `2;1;3;` ✓ |
| ASC + NULLS LAST | `case when v is null then 1 else 0 end, v asc` | `1;3;2;` ✓ |
| DESC + NULLS FIRST | `case when v is null then 1 else 0 end desc, v desc` | `2;3;1;` ✓ |
| DESC + NULLS LAST | `case when v is null then 0 else 1 end desc, v desc` | `3;1;2;` ✓ |

### 已实测推翻的一个设计前提：不能用「请求==缺省则省略」优化

DuckDB 官方文档（`duckdb.org/docs/current/sql/query_syntax/orderby`）逐字：

> "By default, DuckDB sorts ASC and NULLS LAST... For the default ASC direction this matches PostgreSQL.
> Note, however, that **DuckDB keeps NULLS LAST even for DESC ordering, whereas PostgreSQL places NULLs
> first on DESC**. To match PostgreSQL in both directions, set `default_null_order = 'NULLS_LAST_ON_ASC_FIRST_ON_DESC'`."

DuckDB 的空序模型有 **4 种**（`NULLS_FIRST` / `NULLS_LAST` / `NULLS_FIRST_ON_ASC_LAST_ON_DESC` /
`NULLS_LAST_ON_ASC_FIRST_ON_DESC`），而 `defaultNullsFirst` 只有 **1 个布尔**。
实测 PostgreSQL 确实在 DESC 翻转（`2;3;1;`），但 DuckDB 不翻转。

⇒ **单布尔无法推导 DESC 缺省**。若沿用它做「省略子句」判定：
`duckdb + ORDER BY x DESC NULLS FIRST` 会被判为「DESC 缺省就是 NULLS FIRST」→ 省略子句
→ DuckDB 实际返回 NULLS LAST。**查询成功、行序错、分页取到错数据**——正是本计划要消灭的失败模式。

**裁定（Phase 1 固化）：放弃「请求==方言缺省则省略」优化，永远显式输出。**
这样 emission 算法**只依赖 `supportNullsFirst` 一个能力位**，`defaultNullsFirst` 在本计划中**不使用**。
代价是产出略冗长（即使请求恰与缺省一致也显式输出）；收益是彻底消除「缺省推导」这一整类静默错序风险。

### 取值缺口

| 方言 | 缺口 | 处置 |
|---|---|---|
| `h2` | 未声明 `defaultNullsFirst`（实测缺省 NULLS FIRST） | 本计划不使用该属性，**无需补**；但 Phase 0 仍记录实测值 |
| `db2` / `es` | 两属性均未声明 | 同上；`es` 本身是占位桩（`driverClassName`/`dbProductNames` 均为 `xx`），不做核实 |
| `dm` | `supportNullsFirst` 未设（=false），但 DM 兼容 Oracle，很可能实际支持 | Phase 0 核实；未核实前按 false（走 CASE 改写，**语义仍正确**） |
| `tdengine` | `supportNullsFirst` 未设 | 同上 |
| `mariadb`/`mysql5.7`/`postgis`/`oracle-reverse`/`h2gis` | 继承父方言 | 无需设值 |

方言继承树（已核实）：`default` → {`postgresql` → {`duckdb`, `postgis`}, `mysql` → {`mariadb`, `mysql5.7`},
`oracle` → {`oracle-reverse`}, `h2` → {`h2gis`}, `db2`, `dm`, `mssql`, `tdengine`, `es`}；
`geo-support` / `window-expr-support` 为 mixin。只需在 ~8 个叶子定义文件设值。
`_dump/**` 下的同名副本是构建产物、未纳入版本控制，**不改**。

### 语法与 AST 现状

- `BaseRule.g4:365-367`：`sqlOrderByItem : expr=sqlExpr asc=(ASC | DESC)? ;`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:365-367`） —— 插入点。
- **`NULLS` token 不存在**；但 **`FIRST`（`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4:406`）与 `LAST`（`:474`）token 已存在**，
  只是无任何 parser rule 引用、也不在 `unreservedWord_`。
- 新 token 的落点约定：SQL-92 关键字住 `SQL92Keyword.g4`（`TUMBLE` 先例在 `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4:650-652`）；
  `LATERAL` 在 `Keyword.g4:202`。本计划的 `NULLS` 落 `SQL92Keyword.g4`（与 FIRST/LAST 同处）。
- `SqlOrderByItem`（`EqlAST.xjava:232-250`）当前只有 `expr` + `asc`（2307 删除了 `collate` 与 `nullsFirst`）。
- **三态 Boolean 字段需要手写钩子**：`boolean asc` 由手写 `EqlASTBuildVisitor.SqlOrderByItem_asc(Token)`
  提供（`EqlASTBuildVisitor.java:132-136`），生成侧只做 `if(ctx.asc != null) ret.setAsc(...)`。
  `Boolean nullsFirst`（null/TRUE/FALSE 三态）必须同样提供手写钩子，xgen 不会自动生成三态逻辑。
  `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:248-249` 有明确警告：同 prop 不能出现在规则的不同备选分支（duplicate-prop-label 校验）。
  既有同类范式见 `sqlWindowFrameBound`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:250-252`）。
- `sqlOrderBy` 有 **3 个使用点**：查询级 ORDER BY、`OVER ( ... orderBy ... )`（`BaseRule.g4:225`）、
  **命名窗口 `WINDOW w AS ( ... orderBy ... )`（`BaseRule.g4:270`）**。后两者都不支持加额外排序键。
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
- `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`（新增 `NULLS` token）、`model/antlr/BaseRule.g4`（`sqlOrderByItem` + `unreservedWord_`）
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

Status: planned
Targets: `nop-dao/.../dialect/{dm,tdengine}.dialect.xml`、`ai-dev/tools/`、`_tmp/nulls/`（临时探针，收口前删除）

- Item Types: `Proof`

- [ ] 把 `_tmp/nulls/` 已有的 `H2Nulls.java`（原生支持 + DESC 缺省）、`DbNulls.java`（三库四组合）、`Sim.java`（CASE 四组合）**转成常驻脚本**（`ai-dev/tools/`），记录命令与期望输出；不重复写探针
- [ ] 补 `mariadb` 实测（docker `mariadb:*`）：确认不支持原生语法 ⇒ 佐证 F3 的白名单现状与本计划 CASE 路径的必要性
- [ ] 核实 2 个方言的 `supportNullsFirst`：`dm`（疑似应为 true，DM 兼容 Oracle）、`tdengine`。无法实测则按 false（保守，CASE 路径语义仍正确）
- [ ] **不**为 `h2`/`db2`/`es` 补 `defaultNullsFirst`（本计划不使用该属性），但把 h2 的实测缺省值记入矩阵
- [ ] 矩阵落进本 plan 的 Current Baseline，**扩列**现有三库实测表而非新增第二套 baseline（Rule #7）

Exit Criteria:

- [ ] **证明**：每个方言的 `supportNullsFirst` 取值都有证据来源（实测输出 / 官方文档 / 保守缺省标注）
- [ ] **证明**：常驻脚本可复制执行，输出与矩阵一致
- [ ] 矩阵与 Current Baseline 合并为**唯一一套** baseline，无并存
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 1 - 裁定：emission 算法与适用边界（Decision）

Status: planned
Targets: 本 plan、`ai-dev/design/nop-orm-scope-and-non-goals.md`

- Item Types: `Decision`

- [ ] 固化算法：**只读 `supportNullsFirst`**。true → 原生输出 `nulls first`/`nulls last`；false → CASE 改写。**不做缺省推导、不做省略优化**（DuckDB 证据），`defaultNullsFirst` 不使用
- [ ] 裁定 CASE 改写的**适用条件**（保守）：排序表达式必须是「可安全复制」形态。已知三类**不可**复制：
      ① 含绑定参数标记（`sqlExpr_simple` 含 `sqlParameterMarker`，`BaseRule.g4:181`）——复制后 SQL 有两个 `?` 而 AST 只有一个 param 节点，参数错位；
      ② 顶层数字字面量（`sqlLiteral → sqlNumberLiteral`，同文件 `:28`）——`order by 1` 是序号定位，复制后排序失效；
      ③ 易变函数（`rand()`/`uuid()` 等）——表达式被求值两次，NULL 判定与实际排序值来自不同次求值。
      裁定采取哪一档保守度（建议：仅允许纯列引用 `SqlColumnName`，其余 fail-fast）
- [ ] 裁定 `SELECT DISTINCT` 下的行为：改写会引入**新的** ORDER BY 表达式，PG 要求 DISTINCT 查询的 ORDER BY 表达式必须出现在 select 列表中。裁定为「DISTINCT 查询禁用改写，或要求排序列已在 select 列表」
- [ ] 裁定窗口边界：`sqlOrderBy` 的 3 个使用点中，仅查询级支持改写；`OVER (...)` 与**命名窗口 `WINDOW w AS (...)`** 均 fail-fast（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:225` 与 `:270`）
- [ ] 裁定 CASE 键的**插入位置**：必须是该排序项的**前置同级键**（紧邻其前），**不得** prepend 到整个 ORDER BY 列表头——后者会在多键场景把「先按 b 的 nullness、再按 a」写反，且不报错
- [ ] 裁定错误码：新增一个 `NopException` 错误码覆盖「不可改写位置」，参数含方言、位置（窗口/非可复制表达式/DISTINCT）、原始 SQL
- [ ] 裁定代码落点：**不**新增可配置扩展点（避免 `IPaginationHandler` 那套必须改 schema 的代价）；逻辑写在 `AstToSqlGenerator` 的 `visitSqlOrderByItem` **覆写**中，基类 `AstToEqlGenerator` 保持方言无关
- [ ] 裁定 `NULLS` 是否登记为非保留字：无方言把 `nulls` 当保留字（已 grep 确认），登记后 `select nulls from t` 仍可解析，风险低；登记为**是**（对齐既有范式），但须补「`nulls` 作 ORDER BY 列名」的歧义回归测试
- [ ] 裁定推翻 `2026-08-06-1228-1:55` 「不做 CASE 模拟」的适用范围（实测 MySQL 8 四组合全对；继续 fail-fast 会让 MySQL/MariaDB 无法表达常见的 `ASC + NULLS LAST`）

Exit Criteria:

- [ ] 每条裁定有结论、理由、连带影响清单，写入 design 文档
- [ ] **无静默跳过**：所有不可改写位置均 fail-fast，错误码与参数明确，不静默忽略空序请求
- [ ] 裁定映射表写入本 plan，Phase 2–4 的每个动作可追溯到一条裁定
- [ ] 算法描述中**不含**任何需要推导 DESC 缺省的步骤
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 2 - 能力位接线（Feature）

Status: planned
Targets: `nop-dao/.../dialect/IDialect.java`、必要时 `DialectImpl.java`

- Item Types: `Feature`

- [ ] `IDialect` 暴露 `boolean supportNullsFirst()`（注意 `_DialectFeatures.getSupportNullsFirst()` 返回 `Boolean`，未声明即为 `null`；本计划把 `null` 明确定义为 **false = 不支持原生语法 → 走 CASE 改写**，这是安全的保守缺省，**不** fail-fast）
- [ ] 若 `IDialect` 已有等价访问（经 `getFeatures()`），则只加便捷方法，不重复暴露
- [ ] emission 策略实现（按 Phase 1 落点）：输入 `(方言, 排序方向, 请求的 nullsFirst, 排序表达式)`，输出排序片段或抛错误码

Exit Criteria:

- [ ] **测试覆盖（Rule #25）**：新增 `nop-dao` 单测，参数化覆盖 `supportNullsFirst ∈ {true,false,null}` × 2 方向 × 2 请求，断言输出片段；`null` 必须显式有用例
- [ ] **接线验证**：该读取接口确实被 `nop-orm-eql` 的 `AstToSqlGenerator` 在运行时调用（由 Phase 4 的端到端测试反证，非仅类型存在）
- [ ] **无静默跳过**：不可改写位置抛带错误码的 `NopException`，不猜、不静默
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high` 退出码 0
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 3 - 语法入口与 AST 字段（Feature）

Status: planned
Targets: `model/antlr/SQL92Keyword.g4`、`model/antlr/BaseRule.g4`、`model/ast/**/EqlAST.xjava`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java`

- Item Types: `Feature`

- [ ] `SQL92Keyword.g4` 新增 `NULLS` token（紧邻既有 `FIRST:406` / `LAST:474`）；`BaseRule.g4` 的 `unreservedWord_` 按 Phase 1 裁定登记 `NULLS`
- [ ] `sqlOrderByItem` 扩展为 `expr=sqlExpr asc=(ASC | DESC)? nulls=(NULLS FIRST | NULLS LAST)?`
- [ ] `EqlAST.xjava` 的 `SqlOrderByItem` 加回 `Boolean nullsFirst`（三态）
- [ ] `EqlASTBuildVisitor` 新增**三态手写钩子**（形态对齐既有 `SqlOrderByItem_asc(Token)`，`:132-136`），返回 `null`/`Boolean.TRUE`/`Boolean.FALSE`
- [ ] **2256 的坑**：新增 token 会连带改 `.tokens` 与 `EqlLexer.java`；同优先级备选须合并为 token-set label（`nulls=(NULLS FIRST | NULLS LAST)`），否则生成器给出不同 precedence
- [ ] 注意 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:248-249` 的 duplicate-prop-label 校验：`nulls` label 只能出现在一个备选分支
- [ ] 再生成产物同步（`./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests`）

Exit Criteria:

- [ ] **测试覆盖**：parse 矩阵（4 组合 × 大小写变体；`nulls` 作列名/别名/ORDER BY 列名的歧义位置；`FIRST`/`LAST` 单独出现不触发）有独立测试类
- [ ] **测试覆盖**：`toSqlString()` 旁路 round-trip 断言（扩展 2307 的 `TestEqlToSqlStringBypass`，新增 nulls 用例）
- [ ] **生成幂等**：连续两次 install 后 `git status --porcelain -- nop-persistence/nop-orm-eql` 无新增 diff
- [ ] **无静默跳过**：`EqlASTKind` 与节点类一致（grep 可验证）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 4 - 输出器接线与端到端验证（Feature）

Status: planned
Targets: `sql/{AstToEqlGenerator,AstToSqlGenerator}.java`、`nop-persistence/nop-orm/src/test/**`

- Item Types: `Feature`

- [ ] `AstToEqlGenerator#visitSqlOrderByItem`（基类）输出 `nulls first`/`nulls last`，**不含方言逻辑**，保证 EQL 通道 round-trip 无损
- [ ] `AstToSqlGenerator` **覆写** `visitSqlOrderByItem`（当前无覆写，完全继承基类）承载方言逻辑；须处理「是否处于窗口上下文」的判定
- [ ] CASE 键作为**前置同级键**插入（紧邻其排序项，不跨项、不 prepend 到列表头）
- [ ] 窗口两处（`OVER(...)`、命名窗口 `WINDOW w AS (...)`）接入 Phase 1 的 fail-fast
- [ ] 新增 docker 门控端到端测试：EQL 文本 → **经 `EqlCompiler` 编译** → 真实库执行 → 断言**结果行序**

Exit Criteria:

- [ ] **端到端验证（Rule #22）**：h2 + postgresql + mysql 三库实跑，覆盖 4 个方向组合；断言的是排序后行序（如 `{2,1,3}` vs `{1,3,2}`），不是「SQL 里有 nulls 字样」
- [ ] **端到端验证**：docker 测试必须**经 EQL 编译器**产出 SQL（范式对齐 `nop-dao/.../TestPostgresWindowFrameMatrix.java`，即 WI3 的「docker 门控 + 真实执行矩阵 → 回填 dialect.xml features」；**不要**照抄 `TestEqlLateralPostgres`——它用手写 SQL 常量、绕过编译器，只能证明 SQL 形态可执行）
- [ ] **多键正确性（Rule #22）**：多键 E2E 用例——第一键有重复值 + 第二键含 NULL，断言行序。用于钉死「CASE 键插在紧邻位置而非列表头」
- [ ] **DISTINCT 正确性**：DISTINCT 查询用例，按 Phase 1 裁定断言（改写或 fail-fast）
- [ ] **防误伤**：未写 `NULLS` 的既有 `ORDER BY` 产物**逐字不变**（复用 2307 的 bypass 基线范式）
- [ ] **接线验证**：能力位 → 策略 → 输出器三段在运行时连通（由行序断言反证）
- [ ] **无静默跳过**：不可改写位置抛带错误码异常
- [ ] docker 镜像**钉版本**（记录已知坑：`TestPostgreDialect` 曾钉的 9.6.12 镜像已下架导致拉取超时）
- [ ] 跨模块回归（2307 的 8 模块清单，命令见 Closure Gates）全绿
- [ ] `ai-dev/logs/` 当日条目已更新

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