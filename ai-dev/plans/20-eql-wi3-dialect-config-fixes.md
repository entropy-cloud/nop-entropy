# 20 EQL WI3 — 方言配置纠错（11 项）与 owner doc 对齐

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI3 节 + 门控矩阵 WI3 行 + Cross-Cutting 全部 + 关键约束与红线「证据等级纪律」，v7.2）
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，已完成）、`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（G-1/G-4 已裁决）、`ai-dev/plans/03-eql-wi1-class-a-defects.md`（并行实施，文件交集见专节）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（矩阵快照）

## Purpose

收口 roadmap WI3：11 项方言相关待结论项**逐项给出结论并落地**（7 项改值 + 1 项 `unverified` 只标注不改值 + 1 项确认无需改动 + 2 项版本门/selector），每项一个方言配置断言测试；`db2` selector 断链先复现再修；`INTERSECT`/`EXCEPT` 版本门按裁定形态落地且 mariadb `RETURNING` 版本门形态与之一致；`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 四处失真修正并全文与实际 dialect XML 逐项对账，`stringAgg`/`regexpLike`/17 条语义陷阱三个 ledger 文档项落档为本 WI 的独立小节。原 G-4 阻塞已由 WI0 裁决消解（见 Current Baseline）。

## Current Baseline

以下事实除特别注明外，全部于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 开文件实测核验。行号会随后续合并漂移，**执行时以符号定位为准**（各条同时给出符号名）。

### WI0 已落地（现成基础设施，本 plan 直接引用）

- 方言能力矩阵测试 `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java`（提交 `8b41b0a776`）＋快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（18 个 `*.dialect.xml` 全登记；`es` 标 `pseudo`；**`db2` 已登记 `loadable:false`、errorCode `nop.err.xlang.xdsl.missing-mandatory-child`、missing `driverClassName`——即断链事实**）；结构门禁 `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`（棘轮：新增 `*.dialect.xml` 未登记即红）；JUnit 侧字节级比对，重生成命令 `./mvnw test -pl :nop-dao -Dtest=TestDialectCapabilityMatrix -Dnop.dao.matrix.updateSnapshot=true`。
- 四门裁决 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`：
  - **G-4 已裁决（✅ 消解本 WI 的原阻塞）**：「批准 WI3 按 roadmap WI3 表逐项纠错（11 项，含 7 项改值）」，回滚单位 = 每项独立提交 `git revert`；裁决范围严格限于 11 项，超出清单的方言契约变更须重新走询问。
  - **G-1 已裁决（只约束本 WI 的两处版本门）**：承诺基线 MySQL 8.0.31 / MariaDB 10.2（窗口函数）/ MariaDB 13.0（`UPDATE … RETURNING`）；**低于门处置原则 = 编译期报错为默认、双档方言拆分为例外且须记录理由**；全部 unverified 边界维持 unverified 纪律、不计入覆盖率。
- 既有 EQL 语料基线快照 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（测试 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java`）。**实测三份必测语料（NopJobTask / nop-auth-service test / nop-orm test）均不含 `returning`/`intersect`/`except`** ⇒ 本 WI 的配置值翻转不触及语料回显；roadmap 给本 WI 的 `-pl :nop-dao,:nop-orm-eql -am`（不含 `:nop-job-dao`/`:nop-auth-service`）与此一致。

### 11 项逐项实测锚点（与 roadmap WI3 表逐条比对）

**A 组：`supportReturningForUpdate` 五处（第 1–5 项）**——消费通路实测：校验点 `EqlTransformVisitor.java:1559-1574`（`visitSqlUpdate`，抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `ARG_FEATURE=FEATURE_SUPPORT_RETURNING_FOR_UPDATE`，常量在 `OrmEqlConstants.java:25`）；accessor `IDialect.java:108` / `DialectImpl.java:327-328`；输出 `AstToEqlGenerator.java:346-352`（`returning <投影列表>`、无 `INTO`）；声明 `dialect.xdef:39`（javadoc「Update语句增加returning子句支持」）。

| # | 锚点（file:line，实测） | 现值 | roadmap 结论 |
|---|---|---|---|
| 1 | `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml:137` | `true` | 改 `false`（MySQL 8.4 文法无 RETURNING；`mysql5.7.dialect.xml:5` 已写 `false` 证明本意）。⚠️ 实测新增现场证据：`mysql.dialect.xml:136` 有误导注释 `<!-- mysql 8.0.14 和mariadb都支持returning语句 -->`，是该错误值的来源记录，改值时随同一提交修正 |
| 2 | `oracle.dialect.xml:100`（features 行，`supportSomeSubQuery="true" supportReturningForUpdate="true"`） | `true` | 改 `false`（Oracle `UPDATE … returning_clause` 仅允许 `INTO` 宿主变量形式，投影列表形式非法） |
| 3 | `h2.dialect.xml:18`（features 行，同含 `supportILike="true"`） | `true` | 改 `false`（H2 五页文档零 RETURNING） |
| 4 | `duckdb.dialect.xml:21`（`<features supportNullsFirst="true" supportReturningForUpdate="false"/>`） | `false` | **保持不动**（DuckDB RETURNING 仅在 INSERT/DELETE/MERGE）——「确认无需改动」桶，仍配防回归断言 |
| 5 | `dm.dialect.xml:67`（`supportSequence="true" supportReturningForUpdate="true"`） | `true` | **只标注 `unverified`、不改值**（DM8 无公开一手文档，红线禁止伪造「已修正」；文件头 `:3` 自注「使用DeepSeek仿照mysql.dialect.xml自动生成，没有经过测试」进一步支持 unverified 定性） |

**B 组：继承链与 mixin（第 6/7/8 项）**

- `mysql.dialect.xml:3` extends `default.dialect.xml,geo-support.dialect.xml,window-expr-support.dialect.xml`（实测）。
- `mysql5.7.dialect.xml:3` extends `mysql.dialect.xml`；全文件仅 `:5` 一行 `<features supportSequence="false" supportReturningForUpdate="false"/>` ⇒ 第 7 项成立：mysql5.7 经继承获得 window-expr-support 的 10 个窗口函数（`rank`/`dense_rank`/`row_number`/`lead`/`lag`/`first_value`/`last_value`/`nth_value`/`percent_rank`/`cume_dist`），而 MySQL 5.7 无窗口函数。
- `window-expr-support.dialect.xml`（实测全文）：**纯 `<functions>` 捆绑包**，10 个 `<native … onlyForWindowExpr="true">` 注册，无其他内容（零 `<features>`，实测）⇒「移除继承」存在两种可行机制——改继承链（中间方言）或 `x:override="remove"` 内联移除（先例：`mysql.dialect.xml:144-145` remove `cosh`/`sinh`、`postgresql.dialect.xml:155` remove `uuid`、`duckdb.dialect.xml:108-129` 跨两跳继承 remove 25 个 `st_*`，证明可删 mixin/祖先贡献的函数节点）——**两候选并列，由 Phase 0 对比裁定**（见 Phase 0/Phase 2）。
- `mariadb.dialect.xml:3` extends `mysql.dialect.xml`（也继承 window mixin）；自带 `<reservedKeywords>`（实测 `:10-105` 区间），`EXCEPT` 在 `:30`、`INTERSECT` 在 `:43` —— **仓库正确、无需改动**（勿被误「修正」）；窗口函数对 MariaDB 成立（10.2+）⇒ mariadb **不参与**第 7 项。
- 第 8 项（PG/DuckDB 窗口 mixin）✅ 已由 `add-stream-sql` 合并完成：`postgresql.dialect.xml:3` extends `default,geo-support,window-expr-support`、`duckdb.dialect.xml:8` extends `postgresql`（实测）——本项为「已完成」记录，只做回归断言，不占 11 项桶位。

**C 组：ilike 函数注册（第 9 项）**

- 模板先例 `oracle.dialect.xml:174-176`：`<template name="ilike" argTypes="VARCHAR,VARCHAR" returnType="BOOLEAN"><source>regexp_like({0},{1},'i')</source></template>` —— 全仓**唯一**注册了 ilike 的方言（实测 grep）。
- 消费点已存在：`AstToSqlGenerator.visitSqlLikeExpr`（`AstToSqlGenerator.java:478-497`）在 `ignoreCase` 时取 `dialect.getFunction("ilike")`，取不到抛 `ERR_EQL_NOT_SUPPORT_ILIKE`（`:495-496`）⇒ 第 9 项是**补函数注册**，不是改标志。
- `h2.dialect.xml:18 supportILike="true"` 正确保留；`default.dialect.xml:22 supportILike="false"` 为 PG/duckdb 继承的现值（勿翻标志，会引入假阴性）。
- `<functions>` 落点实测：`h2.dialect.xml:131-190`、`postgresql.dialect.xml:147-165`、`duckdb.dialect.xml:81-129`（duckdb 经 extends postgresql 合并函数表，是否需显式补配由 Phase 3 内 merge 语义实测裁定）。

**D 组：`defaultNullsFirst`（第 10/11 项）**

- 7 处 XML 配置实测：`mysql.dialect.xml:7`、`mssql.dialect.xml:5`、`dm.dialect.xml:8`、`tdengine.dialect.xml:5`（`="false"`）、`duckdb.dialect.xml:9`、`postgresql.dialect.xml:5`（`="false"`）、`oracle.dialect.xml:5`（`="false"`）；**`h2.dialect.xml` 无此属性**（第 11 项）。
- **零 Java 消费方实测**：全仓 grep 仅命中冻结生成物 `_DialectModel.java:283/288`（getter/setter）；`IDialect`/`DialectImpl`/两个生成器均零命中。声明侧完整：`dialect.xdef:7`（javadoc）+ `:15`（属性声明）。
- `defaultNullsFirst` 与 `supportNullsFirst` 正交（前=默认 NULL 序事实，后=是否原生接受 `NULLS FIRST/LAST` 子句）；`supportNullsFirst` 现配 4 处：`duckdb.dialect.xml:21`、`h2.dialect.xml:17`、`postgresql.dialect.xml:94`、`oracle.dialect.xml:99`（实测）——**两个标志在 `IDialect`/`DialectImpl` 都没有任何 accessor**。

### `defaultNullsFirst` 消费点调研结论（第 10 项分工裁定）

1. **ORDER BY 打印现状**：`AstToEqlGenerator.visitSqlOrderByItem`（`AstToEqlGenerator.java:582-594`）在 `getNullsFirst() != null` 时**无条件**打印 ` nulls first ` / ` nulls last `；`AstToSqlGenerator` **没有任何 OrderBy 相关代码**（grep 零命中），经继承共享同一路径 ⇒ 不存在「AstToSqlGenerator 侧缺失的独立打印点」。
2. **语法现状**：`BaseRule.g4:365-367` `sqlOrderByItem : expr=sqlExpr asc=(ASC | DESC)?` —— **EQL 文法今天根本不接受 `NULLS FIRST/LAST`** ⇒ `SqlOrderByItem.nullsFirst` 只能被程序化构造（ORM API / 代码建 AST），不能从 EQL 文本到达。
3. **结论（本 plan 的分工裁定）**：`defaultNullsFirst` 的一切可能消费点（按默认 NULL 序决定是否显式补 `NULLS FIRST/LAST`、或对不支持子句的方言做 lowering/报错）都与 WI7 的 `NULLS FIRST/LAST` 子句输出**同一个落点**（ORDER BY 打印路径 + 同一批方言证据）。WI3 若在生成器里发明消费语义，必然与 WI7 撞车或制造假语义。因此：
   - **WI3（第 10 项）交付 = accessor 层**：`IDialect.isDefaultNullsFirst()` + `DialectImpl` 实现（读 `dialectModel.getDefaultNullsFirst()`，照 `isSupportReturningForUpdate` 的 `Boolean.TRUE.equals(...)` 惯例，`DialectImpl.java:327-328` 先例）+ 8 方言配置断言测试（7 处现值 + h2 补配后）——即 roadmap「每项一个方言配置断言测试」对第 10 项的形态。
   - **WI7 承接消费点**（唯一 owner）：文法接受 `NULLS FIRST/LAST` + `supportNullsFirst` accessor 与门控 + `defaultNullsFirst` 的默认序行为断言。移交链以 roadmap WI7 Exit Criteria ②③④⑤ 为准（其 ③ 明文「`defaultNullsFirst` 的 7 个 XML 全部有消费路径断言」）。
   - **不许双 owner**：本 plan 的任何 Exit Criterion 不声称「`defaultNullsFirst` 已接线完成（生成器消费）」；WI3 与 WI7 之间存在的「有 accessor、暂无生成器消费方」中间态是**登记过的 successor 所有权移交**（先例：roadmap WI10b 表对 `supportNullsFirst` 的处置写明「WI7 负责补 accessor + 接线 + 断言」），不是新增死标志——roadmap EC 第 5 条「无新增死标志」约束的是「本 roadmap 引入的标志」，`defaultNullsFirst` 是存量标志，其 accessor 化正是消灭「连 accessor 都没有」这一更差状态的步骤。

### `INTERSECT`/`EXCEPT` 版本门形态调研（三选一，本 plan 推荐③）

缺口两处（roadmap 已论证）：MySQL ≤8.0.30 无 INTERSECT/EXCEPT；**MariaDB ≤10.2 无**（10.3 起，MDEV-10141）——按 G-1 承诺基线（MariaDB floor=10.2），mariadb 在承诺下限上就缺该算子，不能只当 MySQL 问题。文法侧 `DMLStatement.g4:82`（`SqlUnionSelect`）+ `:91-92`（`unionType_: (UNION|INTERSECT|EXCEPT) (ALL)?`）**对所有方言无差别接受**；打印侧 `AstToEqlGenerator.visitSqlUnionSelect:506-532` 经 `getIntercectKeyword()`（基类 `:537-539` 硬编码 `"intersect"`）/ `getExceptKeyword()`（`:541-543` 硬编码 `"except"`）输出；`AstToSqlGenerator` 覆写二者（`:501-506` 读 `dialect.getIntersectKeyword()`、`:509-515` 读 `dialect.getExceptKeyword()`，**空值回落 super**）。配置通路现状：`dialect.xdef:74` 的 `<sqls>` 只有 `exceptKeyword="string"`；`IDialect.java:151-153` `getIntersectKeyword()` 恒返回 `"intersect"`（**零配置通路**）；`DialectImpl` 只覆写 `getExceptKeyword()`（`:356-359`）；`oracle.dialect.xml:105 exceptKeyword="minus"` 已在用。

| 形态 | 评估 |
|---|---|
| ① 拆 mysql5.7 式新方言 | **不推荐**。G-1 之下 mysql 本体 floor=8.0.31 已含 INTERSECT，mysql 侧无「旧版」可拆；真要表达「mysql5.7/mariadb 不支持」仍需一条配置通路（否则继承关不掉），等于绕回③再叠加拆分成本；mariadb 的门槛差只有一个 minor 版本（10.2→10.3），为它拆方言会让 18→19、selector 与矩阵快照连锁变动，且把「承诺下限 10.2 能用的 mariadb」改名。G-1 也只把双档拆分列为「既有先例适用的场景」的例外。仅当 Phase 0 发现③不可行时回退本形态（须记录理由 + 快照 18→19 同步）。 |
| ② 运行时版本检查 | **不推荐**。方言模型无版本感知（`DialectModel` 无版本字段、`DialectImpl` 无 DatabaseMetaData 接线），等于为 WI3 新造一整套机制；与方言「声明式配置契约」相悖；G-1 的处置原则里没有它。 |
| ③ 补 `intersectKeyword` xdef 属性（**推荐**） | 与既有 `exceptKeyword` 完全对称（roadmap 认定的唯一可复用挂载点）；nop-dao 零生成（见差异登记 2）：`dialect.xdef` `<sqls>` 加 `intersectKeyword="string"` + 手写 retention `DialectSqls`（实测现为空子类，照 `DialectFeatures.java:20-31` window-frame 先例）+ `DialectImpl` 覆写 `getIntersectKeyword()`。**行为契约**（Phase 0 终裁后写入 design doc）:(a) 未配置 ⇒ 行为与今天逐字节一致（未配置在模型上为 null，默认回落 `"intersect"`，零 BC 破坏）；(b) 显式配置空串 ⇒ 该方言声明「无 INTERSECT 算子」⇒ 编译含 `INTERSECT[ ALL]` 的查询时抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（复用既有错误码，`ARG_FEATURE=intersectKeyword`，遵循 `EqlTransformVisitor.java:1561-1565` 先例；消费点 = `AstToSqlGenerator.getIntercectKeyword:501-506` 的空值回落改为显式拒绝——现逻辑「空即回落 super」无法表达「显式不支持」，需让 `DialectImpl` 区分 null 与空串）。⚠️ **契约 (b) 依赖一个必答实测项**：`intersectKeyword=""` 空串经 xdef `string` 属性绑定后，模型上保真为 `""` 还是被规范化为 `null`（与未配置不可区分）——若不保真，改用显式哨兵值或回退形态①（Phase 0 必答项，见下）；(c) 显式非空（如 `minus`）⇒ 用配置值。`EXCEPT` 走**同一条空值门规则**、复用既有 `exceptKeyword` 属性，无需新属性。落地配置：`mysql5.7.dialect.xml` 与 `mariadb.dialect.xml` 显式配置两个关键字为空串（floor=10.2 保守）；`mysql` 不配置（floor=8.0.31 已含）。**mariadb `RETURNING` 版本门形态与之一致**：`mariadb.dialect.xml` 显式 `supportReturningForUpdate="false"`（与 mysql 的翻转解耦、独立成声明），既有校验点 `EqlTransformVisitor.java:1559-1574` 即编译期报错；≥MariaDB 10.3 / ≥13.0 的部署经 **delta XML 显式开启**（owner doc 写明该升级路径与版本前提）——即「声明式保守值 + 编译期报错 + 部署级 delta 显式覆盖」，与 G-1/G-4 的「编译期报错为默认」一致。本形态不新增方言文件，矩阵快照保持 18。 |

### WI1 并行实施的文件交集（起草时实测：WI1 尚未落地）

代码侧 grep 实测（修订时复核）：`supportLateral`、`sqlIntervalLiteral` 全仓零命中 ⇒ **WI1 代码改动未落地**；`ai-dev/plans/03-eql-wi1-class-a-defects.md` 已 tracked 并定稿 active（commit `6d0f9fb49f`，roadmap Work Item Status 的 WI1/WI2 行已翻 `planned`）⇒ WI1 处于「planned 未实施」。按 WI1 plan 的 Targets，两 WI 的**交集文件**为：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（WI1 的 ESCAPE/LATERAL/for update 消费 vs 本 WI Phase 4 的空值门）。**协调规则**：本 plan 所有行号锚点以符号定位；每个 Phase 开工前重核交集文件的当期状态（WI1 是否已落同类改动）；两 WI 各自独立提交、后动者 rebase 后重跑双方测试命令；发现语义冲突（同方法同分支）时停下登记，不得顺手合并他人改动。

### owner doc 失真现状与三个小节落点

`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 实测 **143 行**（非任务口径的 124 行；起草后文档新增了 TUMBLE/frame/语法溯源小节，行号整体下移）。四处失真现位于：

- `:124` `supportNullsFirst` 行：声称「不支持时 ORM 用 `IF(ISNULL(f),0,1)` 模拟」——全仓无任何实现（`isnull(` 检索仅命中 `dialect.xdef:38-39` 的 javadoc 设想）；真实状态 = 无 accessor、无消费点，WI7 承接。
- `:127` `supportILike` 行：称「PostgreSQL 支持」——PG 实际继承 `default.dialect.xml:22` 的 `false`（原生支持但未注册模板，本 WI Phase 3 落地）。
- `:129` `supportReturningForUpdate` 行：缺关键限定（仅指 `UPDATE`、且要求投影列表形式无 `INTO`）；且 Phase 1 后 mysql/oracle/h2 的「典型差异数据库」示例需重写。
- `:130` `supportFullJoin` / `supportRightJoin` 行：称「部分数据库不支持」——两标志无 accessor、零消费（WI2/WI10b 归属），表述不成立。

三个 ledger 文档项落点（roadmap「明确延后项」指名归本 WI）：`stringAgg`（各库排序语义 / `WITHIN GROUP` / 分隔符三处分叉）、`regexpLike`（已确认有：PG/MariaDB/H2；已确认无：MSSQL/TDengine；未取证：Oracle/DuckDB/DB2/DM8/MySQL operator 页，两栏呈现）、调研报告 Part 9 的 17 条语义陷阱（转「陷阱 + 配置建议」小节）——均在 Phase 7 以 owner doc 独立小节落档。

## Goals

- roadmap WI3 的 11 项**逐项给出结论并落地**，分桶恰好 **7 改值（第 1/2/3/7/9/10/11）+ 1 unverified 标注（第 5）+ 1 确认不改（第 4）+ 2 版本门/selector（第 6/12）**，每项一个方言配置断言测试。
- `db2` selector 断链**先复现（引用 WI0 快照）再修**，DB2 自动选择恢复可用或显式降级为响亮失败。
- `INTERSECT`/`EXCEPT` 版本门按裁定形态（默认③）落地，mariadb `RETURNING` 门形态与之一致，未配置方零行为变化。
- owner doc 四处失真修正、全文与实际 dialect XML 逐项对账无差异，`stringAgg`/`regexpLike`/17 条语义陷阱三个小节落档。
- 矩阵快照与门禁同步（functions/extends/loadable 变化必须重生成快照），`check-eql-dialect-matrix-snapshot.mjs` 全程绿。

## Non-Goals

- 不改 roadmap WI3 表 11 项之外的任何方言契约值（G-4 批准范围严格限于 11 项；发现新问题登记 roadmap/新 plan）。
- 不做 `defaultNullsFirst` 的生成器消费、不接 `supportNullsFirst` accessor、不做 `NULLS FIRST/LAST` 文法接受——**全部归 WI7**（见调研结论）。
- 不填 `sqlIntervalLiteral` 模板、不修 A/B 类缺陷（WI1/WI2）、不做 `ORDER BY … COLLATE`（WI7）。
- 不做运行时数据库版本检查机制（形态②已拒）。
- 不把 `CROSS`/`EXCEPT`/`INTERSECT` 移出 mariadb 的 `<reservedKeywords>`（实测仓库正确）。
- 不顺手修 3 项既有 broken link（`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`）；门禁只比对本 WI 新增。
- 不引入 `:nop-orm-tdengine`/`:nop-db-migration`/`:nop-orm-demo` 进 `-pl`（roadmap 级 EC 第 8 条覆盖）。

## Scope

### In Scope

- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/`：`mysql.dialect.xml`（第 1 项 + `:136` 注释修正）、`oracle.dialect.xml`（第 2 项）、`h2.dialect.xml`（第 3/11 项 + ilike 模板）、`mysql5.7.dialect.xml`（第 7 项继承链 + 版本门配置）、`mariadb.dialect.xml`（第 6 项显式 false + 版本门配置）、`db2.dialect.xml` 与 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/selector/db2.selector.yaml`（第 12 项）；`window-expr-support.dialect.xml` 不改内容，仅被继承链引用调整。
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`：`<sqls>` 新增 `intersectKeyword="string"`（形态③；含 javadoc）。
- `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectSqls.java`（retention 手写字段）、`IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`（`getIntersectKeyword` 覆写 + `isDefaultNullsFirst`）。
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（Phase 4 空值门消费规则）；测试（nop-dao 配置断言 + nop-orm-eql 编译期报错/输出断言）。
- 矩阵快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` 同步 + 门禁复跑。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、（如路由变化）`docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`；裁定记录落 `ai-dev/design/eql-cross-db-compat/`；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；`nop-auth`、`model/*.orm.xml`、`nop-*-api` 公共接口、任何 `.g4`/AST 生成管线文件。

## 与 roadmap 的差异登记（起草时实测发现）

1. **owner doc 行号漂移**：roadmap（v7 重核验时）记录四处失真在 `:105/:108/:110/:111`、文件 124 行；起草实测文件已 143 行，四处失真在 **`:124/:127/:129/:130`**（期间 owner doc 新增 TUMBLE、frame 能力开关、语法溯源等小节）。本 plan 以实测行号 + 符号（表格行首的标志名）双重定位。
2. **「补 xdef 属性 + `_gen` 重生成（生成管线 plan-first）」已过时**：roadmap WI3 门小节仍沿用 v7 前的成本假设；按 Cross-Cutting 第一行（v7 更正）+ 实测（`nop-persistence/nop-dao/pom.xml:119` 无 precompile、`_DialectSqls.java` 冻结、retention `DialectSqls.java` 为空子类、`DialectFeatures.java:20-31` window-frame 手写先例），形态③为 **xdef + 手写 retention + `IDialect`/`DialectImpl`，零生成**，不触发生成管线。
3. **WI7 节的「`AstToEqlGenerator.java:589-595` 已经会打印 nulls first/last，只需让 `AstToSqlGenerator` 侧也产出」与继承事实不符**：`AstToSqlGenerator` 无 OrderBy 代码、继承即产出；且 `BaseRule.g4:365-367` 文法不接受 NULLS 语法，`nullsFirst` 字段今天只能程序化赋值。WI7 的真实工作是文法接受 + `supportNullsFirst` 门 + `defaultNullsFirst` 默认序消费。本 plan 的移交链以该准确现状为基准书写。
4. **roadmap 对第 10 项的桶位口径内部不一致**：Current Baseline 把第 10/11 项合称「2 项 Java 接线」，WI3 EC 又把第 10 项计入「7 项改值」。第 10 项实际无值可改（accessor 化 + 断言）。本 plan 按本文件「`defaultNullsFirst` 消费点调研结论」的分工执行，并在 closure 时按「第 10 项结论 = accessor + 配置断言 + 移交记录」逐项核对，不夸称改值。
5. **新发现现场证据**：`mysql.dialect.xml:136` 误导注释（见 A 组表）为 roadmap 未登记的第 1 项根因记录，纳入第 1 项同一提交修正（注释非契约，不扩 G-4 范围）。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| 方言 XML 能力值与继承链（11 项） | **平台对外契约**——非 AGENTS.md 字面 Protected Area，走「AI Must Ask Before」第 1 条 | **ask-first —— 已满足**：G-4 书面裁决（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` 第五节，2026-10-03，平台负责人授权，逐项批准 11 项） | 裁决文档 + 本 plan + 每项回归测试 |
| `dialect.xdef` + retention `DialectSqls` + `IDialect`/`DialectImpl` | **非生成管线、非 AGENTS.md 字面 Protected Area**（nop-dao 无 codegen 管线；`nop-xdefs`/`nop-dao` 非 `nop-*-api`） | 不触发 plan-first；对外契约纯增量（新可选属性/新 accessor，未配置零行为变化），登记于此 | owner doc 同步 + 矩阵快照门禁 + 未配置方言零变化断言 |
| `AstToSqlGenerator.java` 空值门消费规则 | 普通手写代码（nop-orm-eql 手写文件，非生成链） | 无额外门禁；注意与 WI1 的文件交集（见专节） | 负向用例（断言错误码）+ 未配置零变化断言 |
| 版本门若回退形态①（拆新方言） | 新增 `*.dialect.xml` ⇒ 矩阵快照 18→19 | 仍非生成管线；快照同步为硬门禁 | Phase 0 记录回退理由 |
| 生成物纪律 | 本 WI **零 `.g4`/`EqlAST.xjava` 改动、零 `_gen` 手改、零 lexer token 删除** | — | `git log` 无生成管线文件 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口。

## Execution Plan

Phase 1–7 相互独立、可分别落地与回滚；**每个 Phase 独立提交（G-4 回滚单位）**；触碰 functions/extends/loadable 的 Phase 结束时必须重生成矩阵快照并保持门禁绿。11 项分桶落位：第 1/2/3（Phase 1）、第 7（Phase 2）、第 9（Phase 3）、第 6（Phase 4）、第 12（Phase 5）、第 10/11（Phase 6）、第 4/5 的结论与标注（Phase 1）。

### Phase 0 - 前置核验与裁定

Status: completed（2026-10-04；裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi3-phase0-adjudications.md`）
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、WI1 交集文件状态核验

- Item Types: `Decision`、`Proof`

- [x] 复核 WI1 并行状态：`supportLateral`/`sqlIntervalLiteral` 是否已落地；若已落地，重核交集文件（`dialect.xdef`/`IDialect`/`DialectImpl`/`AstToSqlGenerator`）当期结构并更新本 plan 的锚点行号；若未落地，按起草锚点执行。（**实测：WI1/WI2 均已落地**；锚点漂移全表见裁定文档 §五，本 plan 后续行号一律以符号定位为准）
- [x] 终裁 `INTERSECT`/`EXCEPT` 版本门形态（默认③，含上方行为契约 a/b/c 三条；回退①须记录理由并触发快照 18→19 同步）；同步裁定 mariadb `RETURNING` 门的显式化写法与 delta 覆盖路径文案。（**终裁 = 形态③ + 显式哨兵 `none` 替代空串**（必答项实测否决空串，见下条）；契约 a/b/c 修正为 a/b′/c 三条，裁定文档 §一/§二）
- [x] **契约 (b) 必答项（实测取证，不得推断）**：`intersectKeyword=""` 空串经 xdef `string` 属性绑定后，模型上（`DialectSqls` retention 字段经 getter 读取）是保真 `""` 还是被规范化为 `null`——写一个最小方言 XML 加载实测，把原始输出证据记入裁定文档。**若空串不保真**（与未配置的 null 不可区分，契约 (b) 的「显式不支持」无法表达）：改用显式哨兵值（字面值随裁定记录）或回退形态①；Phase 4 契约 (b) 的落地写法与负向用例**依赖本结论**，未取证前 Phase 4 不得开工。（**实测结论：空串不保真——`exceptKeyword=""`（与拟新增 intersectKeyword 同元素/同类型/同绑定机制）在模型上为 null；改用哨兵 `none`。原始输出证据 + 附加发现「未声明属性加载期响亮报 `nop.err.xlang.xdsl.attr-not-allowed`（证明补 xdef 属性是必需前提）」均记入裁定文档 §二**）
- [x] 终裁第 7 项机制——**两个并列候选，裁定文档必须逐维对比后选择**：
  - **候选 A（新中间方言）**：新增 `mysql-base.dialect.xml`（承载 mysql 现有非窗口内容，extends `default,geo-support`）；`mysql.dialect.xml:3` 改 extends `mysql-base,window-expr-support`（mysql 能力不变）；`mysql5.7.dialect.xml:3` 改 extends `mysql-base`。
  - **候选 B（内联移除）**：`mysql5.7.dialect.xml` 新增 `<functions>` 块，对 10 个窗口函数逐个 `<native name="…" x:override="remove"/>`；继承链不动。机制可行性已被仓库现网证明（修订时补充实测先例）：`mysql.dialect.xml:144-145`（remove `cosh`/`sinh`）、`postgresql.dialect.xml:155`（remove `uuid`）、`duckdb.dialect.xml:108-129`（跨两跳继承 remove 25 个 `st_*`——证明可删 mixin/祖先贡献的函数节点）。
  - **必比四维**（写入裁定文档对比表）：① 快照算术（A：18→19；B：保持 18）；② 可加载方言数（A：14→15，`mysql-base` 带 mandatory 子节点；B：不变）；③ 回滚面（A：3 文件——新增 + `mysql.dialect.xml:3` + `mysql5.7.dialect.xml:3`；B：1 文件）；④ 语义差异——**mixin 未来新增函数是否泄漏进 mysql5.7**（A：结构性隔离，永不泄漏；B：列举式移除，mixin 新增函数会经继承泄漏，须随 mixin 演进维护 remove 清单，泄漏风险须显式接受或拒绝）。
  - 记录选择与被拒候选的逐维理由。（**终裁 = 候选 B**；四维对比表 + 理由（house style 先例/最小回滚面/快照稳定/泄漏风险以 live-parsed mixin 动态守卫测试机制化拒绝）见裁定文档 §三）
- [x] 终裁第 12 项修法：默认方案 = 给 `db2.dialect.xml` 补 `<driverClassName>` 与 `<jdbcUrlPattern>`（补齐 mandatory 子节点，参照 `dm.dialect.xml:11-15` 形态）；**驱动坐标候选字面值预写**（实施时以 DB2 JCC 官方文档核对）：`com.ibm.db2.jcc.DB2Driver` / `jdbc:db2://{host}:{port}/{db}`；备选 = 下掉 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/selector/db2.selector.yaml`（DB2 自动选择变为响亮失败）。记录选择理由。（**终裁 = 补 mandatory 子节点**；JCC 坐标已经 IBM 官方文档核对，拒绝下掉 selector 的理由见裁定文档 §四）
- [x] 核对本 plan 全部 file:line 锚点在执行时点仍成立（行号漂移则以符号重定位并在本 plan 勘误）。（**勘误：交集文件因 WI1/WI2 落地普遍漂移（如 `AstToSqlGenerator.getIntercectKeyword` :501→:567、`IDialect.getIntersectKeyword` :151→:168）；方言 XML/xdef/g4 锚点全部成立。全表见裁定文档 §五；本 plan 各 Phase 执行以符号定位为准**）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：版本门形态（三条行为契约）+ **空串绑定保真实测证据（必答项结论，Phase 4 开工前置）**、第 7 项机制（**候选 A/B 四维对比表 + 选择理由**）、第 12 项修法（含 JCC 驱动坐标候选）、WI1 交集核验结论、锚点复核结论。（`2026-10-04-wi3-phase0-adjudications.md` §一～§五）
- [x] 本 Phase 纯裁定不改产品代码：`No owner-doc update required`（裁定文档本身即交付物）。（临时实验 fixture/测试已删除，未进提交）
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-04.md`）

### Phase 1 - `supportReturningForUpdate` 值纠错（第 1/2/3/4/5 项）

Status: completed（2026-10-04；commits `76e4db0add`/`c3bf32f909`/`dec036777f`）
Targets: `mysql.dialect.xml`（`:136-137`）、`oracle.dialect.xml`（features 行）、`h2.dialect.xml`（`:17-21` features 行）、`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/`（新增/扩展配置断言测试）、`ai-dev/logs/`

- Item Types: `Fix`（第 1/2/3 项）、`Proof`（第 4 项防回归断言、第 5 项 unverified 固化断言）

- [x] 第 1 项：`mysql.dialect.xml:137` → `supportReturningForUpdate="false"`，同提交修正 `:136` 误导注释（删除或改写为如实记录：MySQL 无 `UPDATE … RETURNING`；MariaDB 的 `UPDATE … RETURNING` 自 13.0）。（commit `76e4db0add`：注释改写为语义说明+错误值来源记录；red-first 留痕=stash 原值 2 断言 expected false but was true）
- [x] 第 2 项：`oracle.dialect.xml` features 行 → `false`（Oracle 仅支持 `INTO` 宿主变量形式，投影列表形式非法）。（commit `c3bf32f909`，附语义注释）
- [x] 第 3 项：`h2.dialect.xml` features 行 → `false`（H2 无 RETURNING 子句）。（commit `dec036777f`，附语义注释）
- [x] 第 4 项：`duckdb.dialect.xml:21` 保持 `false` 不动；补防回归断言（duckdb 加载后 `isSupportReturningForUpdate()` 为 false）。（`TestDialectReturningForUpdateFlag.testDuckdbConfirmedFalse` + nop-orm-eql `testH2AndDuckdbRejectUpdateReturning` 的 duckdb 段）
- [x] 第 5 项：`dm.dialect.xml:67` 保持 `true` 不改值；补固化断言（dm 加载后为 true）并在测试 javadoc 标注 `unverified`（依据：DM8 无一手文档，G-1 红线）；同步在 `Deferred But Adjudicated` 登记（本 plan 已含）。（`TestDialectReturningForUpdateFlag.testDmUnverifiedValuePinned`，javadoc 显式 UNVERIFIED + successor 指针）
- [x] 每项一个方言配置断言测试（新测试类或扩展现有 per-dialect 测试）：对 18 个方言中受影响的 5 个直接方言断言 `DialectManager` 加载后 accessor 返回值与结论一致；对 mysql/oracle/h2 附加 nop-orm-eql 侧负向用例——`update … returning` 在这三种方言上编译期抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（断言具体错误码，非泛化异常）。（nop-dao `TestDialectReturningForUpdateFlag` 7 测试（mysql/oracle/h2 三改 + duckdb 确认 + dm 固化 + mariadb/mysql5.7/oracle-reverse/h2gis 涟漪）；nop-orm-eql `TestEqlUpdateReturningDialectGate` 5 测试（mysql/oracle/h2/duckdb 真实方言编译期拒绝，断言错误码+feature param；postgresql 正向对照））
- [x] **继承涟漪断言**（roadmap 未点名，本 plan 显式覆盖）：翻转经 `x:extends` 传播到衍生方言——`mariadb`（extends mysql）、`oracle-reverse`（extends oracle）、`h2gis`（extends h2）的加载后有效值均随父级同步为 `false`，纳入配置断言；衍生方言上 `update … returning` 同样编译期报错（经继承的 false 走同一校验点）。（accessor 侧 4 方言断言 + mariadb/oracle-reverse 真实方言门侧断言）

Exit Criteria:

- [x] 三个改值项落地且各自独立提交；`mysql.dialect.xml:136` 注释已修正。（`76e4db0add`/`c3bf32f909`/`dec036777f`）
- [x] 配置断言测试覆盖 5 个直接方言（3 改 + 1 保持 + 1 unverified 固化）+ 3 个衍生方言继承涟漪（mariadb/oracle-reverse/h2gis 均为 false），全绿。
- [x] nop-orm-eql 负向用例：mysql/oracle/h2 上 `update … returning` 编译期报错且断言错误码；`duckdb`/支持方言（如 postgresql `:94` 现值 true）不受影响（正向对照）。
- [x] 第 5 项 unverified 处置与本 plan `Deferred But Adjudicated` 条目一致，未伪造「已修正」。
- [x] 既有 EQL 语料 BC：三份必测语料不含 returning，`TestEqlCorpusBaseline` 回归不变（不声称 7 个全测）。（Phase 1 门禁 nop-orm-eql 239 测试全绿含该测试；语料零命中已 Phase 0 复核）
- [x] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿。（BUILD SUCCESS，2026-10-04）
- [x] owner doc 更新归 Phase 7 统一执行，本 Phase 标注 `owner-doc update deferred to Phase 7`；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - mysql5.7 窗口 mixin 移除（第 7 项）

Status: completed（2026-10-04；commit `b5c4e439b4`，按 Phase 0 裁定候选 B 落地）
Targets: 按 Phase 0 裁定（候选 A：新增 `mysql-base.dialect.xml` + 改 `mysql.dialect.xml:3`/`mysql5.7.dialect.xml:3` 继承链；候选 B：`mysql5.7.dialect.xml` 内联 10 个 `x:override="remove"`）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`、`nop-dao`/`nop-orm-eql` 测试

- Item Types: `Fix`、`Proof`

- [x] 按 Phase 0 裁定落地（候选 A 改继承链 / 候选 B 内联移除）：`mysql5.7` 的有效函数表不再含 10 个窗口函数；`mysql` 与 `mariadb` 的有效函数表**逐字节不变**（mariadb 10.2+ 确有窗口函数，勿顺手改）。（候选 B：mysql5.7 内联 10 个 remove；注释引用裁定文档 §三）
- [x] 配置断言测试：mysql5.7 加载后函数表不含 `rank`/`dense_rank`/`row_number`/`lead`/`lag`/`first_value`/`last_value`/`nth_value`/`percent_rank`/`cume_dist`；mysql 与 mariadb 仍含全量 10 个（防「顺手修正」回归）。（`TestMysql57WindowFunctionsRemoved` 4 测试）
- [x] **窗口回归载体（已核验事实，修订时实测）**：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/` 下三个窗口测试随本 Phase 全绿，作为 mysql5.7 路径的回归载体——`TestDialectWindowSqlSnapshot`（快照方言集含 `mysql5.7`）、`TestDefaultDialectWindowFeatures`、`TestPostgresWindowFunctionDialect`。核验依据：golden `nop-persistence/nop-orm-eql/src/test/resources/__snapshot/mysql5.7.window.sql`（实测内容）只含 `sum(…) over(…)`（走聚合硬编码路径，roadmap 约束 2：聚合名不经 `<functions>` 表），不经 window mixin 函数表；frame 能力位（`supportWindowFrameRows/Range/Groups`）继承自 `default.dialect.xml` 集中缺省，与 mixin 无关（mixin 文件零 `<features>`，实测）。⇒ 两个候选下三测试都应**原样通过**；**不得顺手修改 golden**（golden 变化即说明改动破坏了函数表以外的路径，属失败信号而非预期产物）。（Phase 2 门禁全绿含三测试；`git diff -- __snapshot/` 零改动实测）
- [x] 矩阵快照同步：候选 A 的新方言文件登记进快照（18→19，`mysql-base` 为 loadable）；候选 B 确认快照无需变更（保持 18、无 diff）；两候选均重跑 `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0 + `TestDialectCapabilityMatrix` 字节比对通过。（**执行期勘误**：候选 B 下 dialectCount 保持 18 正确，但 mysql5.7 的 functions 列表变化 -10 ⇒ 快照**有 diff、须重生成**（起草时「无 diff」假设不成立，与 EC「所有 functions 变化必须重生成快照」一致）；已重生成（10 deletions）+ 两道门禁绿）
- [x] 第 8 项（已完成记录）回归断言：postgresql/duckdb/postgis 窗口函数仍注册（防本 Phase 意外破坏继承链）。（`testPostgresFamilyWindowFunctionsStillRegistered`）

Exit Criteria:

- [x] mysql5.7 函数表断言红→绿（改动前 mysql5.7 含 10 个窗口函数，改动后不含）。（red-first 留痕：stash 原值 `rank` registered，主断言+守卫双红）
- [x] mysql/mariadb/postgresql/duckdb 函数表逐项不变（防回归断言全绿）。
- [x] 快照已同步且两道门禁（mjs 脚本 + JUnit 字节比对）均绿（候选 B：确认快照保持 18 无 diff、门禁复跑绿）；`es` 伪方言不参与断言。（快照 18 保持、mysql5.7 functions -10 重生成；mjs exit 0 + 字节比对绿）
- [x] 三个窗口回归测试（`TestDialectWindowSqlSnapshot`/`TestDefaultDialectWindowFeatures`/`TestPostgresWindowFunctionDialect`）**原样通过且 mysql5.7 窗口 golden 文件零改动**（`git diff -- 'nop-persistence/nop-orm-eql/src/test/resources/__snapshot/'` 可证）。
- [x] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿。（BUILD SUCCESS，2026-10-04）
- [x] owner-doc update deferred to Phase 7；`ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - ilike 函数注册（第 9 项）

Status: completed（2026-10-04；commit `f4c2a17cf4`）
Targets: `h2.dialect.xml`（`<functions>` `:131-190`）、`postgresql.dialect.xml`（`<functions>` `:147-165`）、（如 Phase 0/3 实测 merge 不可达则 `duckdb.dialect.xml` `<functions>` `:81-129`）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`、`nop-dao`/`nop-orm-eql` 测试

- Item Types: `Feature`（补注册）、`Proof`

- [x] h2 与 postgresql 注册 `ilike` 模板（照 `oracle.dialect.xml:174-176` 的元素形态；H2/PG 原生 ILIKE 算子，模板体输出 `<expr> ilike <value>` 语义）；duckdb 有效性实测：经继承其加载后函数表含 ilike 即可，不可达则显式补配（裁定记录在提交说明）。（**实测：duckdb 经 extends postgresql 继承可达**，无需显式补配——结论记录于提交说明）
- [x] **不改任何 `supportILike` 标志**（`default.dialect.xml:22` 的 `false` 与 `h2.dialect.xml:18` 的 `true` 均保持）。（锁定断言 `testSupportIlikeFlagsUntouched`）
- [x] 配置断言测试：h2/postgresql/duckdb 加载后 `getFunction("ilike") != null`；继承面 h2gis（经 h2）/postgis（经 postgresql）同样命中；mysql/mssql 等未注册方言仍为 null。（`TestIlikeFunctionRegistration` 4 测试，另含 oracle 既有防回归）
- [x] 行为断言测试（nop-orm-eql）：`ilike` 语义查询在 h2 上输出含 ` ilike ` 的 SQL（断言具体文本）；在未注册方言（如 mysql）上仍抛 `ERR_EQL_NOT_SUPPORT_ILIKE`（既有负向路径回归）。（`TestEqlIlikeDialectFunction` 3 测试，另含 oracle regexp_like 正向对照）
- [x] 矩阵快照同步（h2/postgresql/duckdb/h2gis/postgis 的 functions 列表变化）+ 两道门禁复跑绿。（快照 +5 处 ilike，mjs exit 0 + 字节比对绿）

Exit Criteria:

- [x] 配置断言 + 行为断言全绿；未注册方言负向路径不回归。
- [x] 快照已同步且 `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [x] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿。（BUILD SUCCESS，nop-dao 855 / nop-orm-eql 242）
- [x] owner-doc update deferred to Phase 7；`ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - INTERSECT/EXCEPT 版本门 + mariadb RETURNING 门（第 6 项 + 门形态）

Status: planned
Targets: `dialect.xdef`（`<sqls>` `:74-75` 区域）、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectSqls.java`、`IDialect.java`（`:151-157` 区域）、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`（`:356-359` 区域）、`AstToSqlGenerator.java`（`:501-515`）、`mysql5.7.dialect.xml`、`mariadb.dialect.xml`、`nop-dao`/`nop-orm-eql` 测试、`ai-dev/design/eql-cross-db-compat/`（Phase 0 契约文档落定）

- Item Types: `Feature`（intersectKeyword 配置通路）、`Fix`（mariadb 显式 false）、`Proof`

- [ ] 形态③落地（按 Phase 0 契约；**依赖 Phase 0 空串保真必答项结论**，若裁定为哨兵值/形态①则以裁定为准重写本条）：`dialect.xdef` `<sqls>` 新增 `intersectKeyword="string"`（带 javadoc：空串 = 方言无 INTERSECT 算子）→ `DialectSqls` retention 手写字段（window-frame 先例）→ `IDialect`/`DialectImpl` 覆写 `getIntersectKeyword()`（未配置回落 `"intersect"`，与现行为逐字节一致）→ `AstToSqlGenerator` 空值门（显式空串 ⇒ `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，`ARG_FEATURE=intersectKeyword`；`EXCEPT` 经既有 `exceptKeyword` 走同一空值门规则，无需新属性）。
- [ ] 门配置落地：`mysql5.7.dialect.xml` 与 `mariadb.dialect.xml` 配置 `intersectKeyword=""` 与 `exceptKeyword=""`（floor 保守）；`mysql` 不配置。
- [ ] 第 6 项：`mariadb.dialect.xml` 显式 `supportReturningForUpdate="false"`（与 mysql 的 Phase 1 翻转解耦，独立声明 floor 保守值）；既有校验点即编译期报错。
- [ ] **未配置零变化断言**：`oracle`（`exceptKeyword="minus"` 在用）、`mysql`、`h2` 等未配置 intersectKeyword 的方言，`union`/`intersect`/`except` 查询输出与改动前逐字节一致（BC 守护）。
- [ ] 负向用例（nop-orm-eql，加载真实方言模型）：mysql5.7 与 mariadb 上编译含 `INTERSECT`/`EXCEPT` 的查询抛错并断言错误码与 `ARG_FEATURE`；mariadb 上 `update … returning` 抛错（门形态与 INTERSECT 一致性的证明）。
- [ ] 部署级覆盖路径文档（Phase 7 落 owner doc）：≥MariaDB 10.3 / ≥13.0 部署经 delta XML 显式开启 `intersectKeyword`/`exceptKeyword`/`supportReturningForUpdate` 的写法与版本前提。
- [ ] mariadb 的 `<reservedKeywords>` 不动（`EXCEPT :30`/`INTERSECT :43` 实测正确）。

Exit Criteria:

- [ ] 未配置方言零变化断言全绿（BC）；`oracle` 的 `minus` 行为不变。
- [ ] mysql5.7/mariadb 负向用例断言具体错误码；`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` 复用而非新造错误码（或 Phase 0 若裁定新码，断言新码）。
- [ ] mariadb `RETURNING` 门与 INTERSECT 门形态一致性有测试证明（同一「声明式保守值 + 编译期报错 + delta 覆盖」模式）。
- [ ] `DialectSqls` retention 字段经 xdef 反射绑定真实读写（接线验证：配置值 → 模型 → accessor → 生成器行为差异，Minimum Rules #23）。
- [ ] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿。
- [ ] owner-doc update deferred to Phase 7；`ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - db2 selector 断链修复（第 12 项）

Status: planned
Targets: `db2.dialect.xml` 或 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/selector/db2.selector.yaml`（按 Phase 0 裁定）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`、`nop-dao` 测试

- Item Types: `Fix`、`Proof`

- [ ] **先复现再修**：按 WI0 快照登记的事实复现——快照 `dialects.db2` 记录 `loadable:false` + `nop.err.xlang.xdsl.missing-mandatory-child` + `missingMandatoryChild: driverClassName`；复现步骤 = `DialectManager` 按 productName `DB2` 经 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/selector/db2.selector.yaml` 命中 `dialectName: db2` 后实例化失败（或直接 try-load `db2.dialect.xml` 抛 `nop.err.xlang.xdsl.missing-mandatory-child`）。复现以一个**修复前红**的测试固化。
- [ ] 按 Phase 0 裁定修复（默认 = 补 `<driverClassName>`/`<jdbcUrlPattern>`，形态参照 `dm.dialect.xml:11-15`；备选 = 下掉 selector）：修复后 DB2 自动选择可用（或显式响亮失败），复现测试转绿。
- [ ] 矩阵快照同步：db2 的 `loadable`/functions/extends 状态按修复后实测重生成（若转 loadable，快照新增其函数表）+ 两道门禁复跑绿。
- [ ] **连带失真修正**：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestDialectWindowSqlSnapshot.java:37-38` 的注释「db2 缺 driverClassName 等必备节点无法独立加载（生产经 selector 注入）——不适用快照」在本 Phase 后可能失真——若走「补 mandatory 子节点」方案（db2 转可加载），必须同 Phase 更新该注释（是否把 db2 纳入快照方言集随裁定记录，不强制扩面）；若走「下掉 selector」方案，注释仍成立、保持原文不改。
- [ ] 既有 `db2` errorCodes 表（`-803` 等，`DialectSQLExceptionTranslator` 消费）不因修复改变。

Exit Criteria:

- [ ] 复现测试存在且修复前红（红→绿留痕于提交说明或日志）。
- [ ] 修复后配置断言测试：DB2 选择路径（selector → dialect 实例）端到端可用（或按备选方案断言响亮失败的错误形态）；`TestDialectSelector` 既有排序约定回归不变。
- [ ] 快照已同步且门禁绿。
- [ ] `TestDialectWindowSqlSnapshot.java:37-38` 注释状态与第 12 项修法裁定一致（可加载化 ⇒ 注释已更新；下掉 selector ⇒ 保持原文）。
- [ ] `./mvnw test -pl :nop-dao -am -T 1C` 全绿。
- [ ] owner-doc update deferred to Phase 7；`ai-dev/logs/` 对应日期条目已更新。

### Phase 6 - defaultNullsFirst accessor + H2 补配 + WI7 移交标注（第 10/11 项）

Status: planned
Targets: `h2.dialect.xml`（dialect 元素属性区 `:3-5`）、`IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`nop-dao` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（移交标注随 Phase 7 落档）

- Item Types: `Feature`（accessor）、`Fix`（第 11 项补配）、`Proof`（配置断言）

- [ ] 第 10 项 accessor 层：`IDialect.isDefaultNullsFirst()` + `DialectImpl` 实现（读 `dialectModel.getDefaultNullsFirst()`，`Boolean.TRUE.equals(...)` 惯例，照 `DialectImpl.java:327-328` 先例；零生成）。
- [ ] 第 11 项：`h2.dialect.xml` 补 `defaultNullsFirst="true"`（H2 默认 NULL 序 `LOW`，ASC 时 NULL 在前）；`supportNullsFirst="true"`（`:17`）保留不动。
- [ ] 配置断言测试：8 方言 accessor 返回值逐个断言——mysql/mssql/dm=true、tdengine/duckdb/postgresql/oracle=false、h2=true（补配后）；未配置属性的方言（如 h2 之外的 default 系）断言缺省行为（false 语义）与文档一致。
- [ ] **不写任何生成器消费**；在本 plan 与 owner doc 中写明移交链：消费点唯一 owner = WI7（roadmap WI7 EC ②③④⑤），WI3 交付止于 accessor + 配置正确性。
- [ ] 正交性标注素材：`defaultNullsFirst`（默认序事实）与 `supportNullsFirst`（子句接受度）两概念的边界、以及「单布尔不足以表达方向相关 NULL 序」（H2 `FIRST`/`LAST` 方向无关 vs `LOW`/`HIGH` 方向相关；DuckDB-vs-PG DESC 差异）证据指针，供 WI7 引用（Phase 7 落 owner doc）。

Exit Criteria:

- [ ] accessor 契约测试 8 方言全绿（含 h2 补配前后差异可断言）。
- [ ] 接线验证：XML 属性值 → 模型 → accessor 的读取链有测试证明（Minimum Rules #23）；本 Phase 新增公共方法若暂无消费方，其「暂无消费方」状态已在本 plan 与移交记录中显式声明（非静默 no-op，Minimum Rules #24 以移交记录履行）。
- [ ] 未改任何生成器代码（`git log` 可证）；`supportNullsFirst` 四处配置未被触碰。
- [ ] `./mvnw test -pl :nop-dao -am -T 1C` 全绿。
- [ ] owner-doc update deferred to Phase 7；`ai-dev/logs/` 对应日期条目已更新。

### Phase 7 - owner doc 对齐 + 矩阵快照终同步

Status: planned
Targets: `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`（如路由变化）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（终态复核）

- Item Types: `Fix`（文档失真）、`Proof`（对账）

- [ ] 四处失真修正（以符号定位，起草时行号 `:124/:127/:129/:130`）：`supportNullsFirst` 行改为如实状态（无 accessor/无消费点，WI7 承接；`IF(ISNULL(f),0,1)` 仅为 xdef javadoc 设想，无实现）；`supportILike` 行修正 PG 表述并写明 ilike 模板已注册方言清单（Phase 3 产物）；`supportReturningForUpdate` 行补「仅指 UPDATE、投影列表形式无 INTO」限定 + mysql/oracle/h2=false、duckdb=false 正确、mariadb 版本门的实测结论；`supportFullJoin`/`supportRightJoin` 行改为「无 accessor、零消费（WI2/WI10b 归属）」。
- [ ] 新增独立小节一：**`stringAgg` 各库差异**（排序语义 / `WITHIN GROUP` / 分隔符三处分叉、无法无损归一，对应 roadmap ledger 行落档）。
- [ ] 新增独立小节二：**`regexpLike` 支持面两栏表**（已确认有：PG/MariaDB/H2；已确认无：MSSQL/TDengine；`[未取证]`：Oracle/DuckDB/DB2/DM8/MySQL operator 页——按证据等级纪律呈现，不用比例表述）。
- [ ] 新增独立小节三：**语义陷阱 + 配置建议**（调研报告 Part 9 的 17 条语义陷阱转文档，对应 roadmap ledger 行落档）。
- [ ] 本 WI 落地内容同步：`intersectKeyword`/`exceptKeyword` 空值门契约与 delta 覆盖路径（Phase 4 产物）、mariadb 版本门、`defaultNullsFirst`/`supportNullsFirst` 正交性与移交状态、第 7 项 mixin 归属结论（mysql5.7 无窗口函数、mariadb 有）。
- [ ] 全文与实际 dialect XML **逐项对账**（标志表中每行抽查到至少一个方言 XML 实值）；owner doc 内 stream-roadmap 的 WI1/WI2/WI3/WI17 编号与本 roadmap WI3 的混用风险以限定词消除（避免读者误读）。
- [ ] **对账证据机械化**：daily log 附「features 标志 × 全部 `*.dialect.xml`（当前 18 个；候选 A 落地则 19 个）实值」的 grep 对账清单——对本 WI 触及的每个标志（`supportReturningForUpdate`/`supportNullsFirst`/`defaultNullsFirst`/`supportILike` 及版本门关键字配置）逐标志输出各 XML 实值（如 `grep -n "<标志名>" .../dialect/*.dialect.xml` 或等价表格），作为「逐项对账无差异」的可复核证据载体。
- [ ] `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`：owner doc 章节变化是否影响路由/锚点，变化则同步。
- [ ] 矩阵快照终态复核（所有 Phase 的快照同步无遗漏）+ `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：**本次改动新增 broken link = 0**（既有基线 3 项 warning、0 error、退出码 0；不得顺手修 3 个无关 pre-existing 文件，只比对新增量）。

Exit Criteria:

- [ ] 四处失真全部修正且逐项对账无差异（对账结果记录于 daily log 或本 plan 勘误注记），且 daily log 含「标志 × XML 实值」grep 对账清单（可复核证据）。
- [ ] 三个 ledger 小节落档且与 roadmap「明确延后项」对应行一致（`stringAgg`/`regexpLike` 的 ledger Successor 列即「文档归 WI3」，本 Phase 兑现）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 且新增 broken link = 0。
- [ ] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] roadmap WI3 的 11 项**逐项有结论**（7 改值 + 1 unverified 标注 + 1 确认不改 + 2 版本门/selector；第 8 项为已完成记录只做回归验证）：逐项核对——`mysql`/`oracle`/`h2` 的 `supportReturningForUpdate` → `false`；`duckdb` 保持 `false`；`dm` 显式保留 `unverified` 并登记 successor（不得要求 dm 被「修正」，closure audit 不得据此判失败）；`mariadb` 显式 false + 版本门形态与 INTERSECT 门一致；窗口 mixin 的 `mysql5.7` 移除（PG/DuckDB 补齐项回归验证）；`ilike` 注册；`defaultNullsFirst` accessor + 8 方言断言 + H2 补配；`db2` selector 断链已复现并修复；`INTERSECT` 版本门落地。
- [ ] 11 项**每项一个方言配置断言测试**（矩阵断言或独立测试类），全部存在且绿。
- [ ] `defaultNullsFirst` 消费点移交链已写明（WI3 不声称消费完成；WI7 为唯一消费 owner）；不存在两 WI 双重 owner 或双重「完成」声称。
- [ ] **矩阵快照同步**：所有 functions/extends/loadable 变化已重生成快照；`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0；`TestDialectCapabilityMatrix` 字节比对通过。
- [ ] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿——**「全绿」口径**：本 WI 相关模块全绿；该命令闭包不含 `:nop-auth-service`，故 `nop-stream-runtime` 既有失败（`TestStreamConnectorRegistryDiscovery`，见 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头登记）不进本闭包；若执行中扩大 `-pl` 面致其入闭包，以「与基线一致即豁免」口径处理，不得据此判本 WI 失败。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 改动新增 broken link = 0（既有 3 项 warning 基线不动：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/20-eql-wi3-dialect-config-fixes.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high` 与 `--module nop-orm-eql --severity high` 均零命中（anti-hollow 对本 WI 尤其关键：accessor-without-consumer、空值门不生效都是要抓的半实现形态）。
- [ ] 11 项逐项独立提交可审计（G-4 回滚单位 = `git revert` 单项）；零 `.g4`/`_gen`/lexer token 改动。
- [ ] owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）已同步：四处失真 + `stringAgg`/`regexpLike`/17 条语义陷阱三小节 + 版本门契约 + 逐项对账无差异；INDEX/source-anchors 已核对。
- [ ] Anti-Hollow Check：closure audit 已验证（a）`intersectKeyword` 空值门从 XML 配置 → retention → accessor → 生成器报错的运行时调用链真实连通（不只是字段存在）；(b) 未配置方言逐字节零变化有测试证据；(c) 无空方法体/静默跳过/no-op（第 10 项 accessor 的「暂无消费方」以移交记录显式声明）；(d) db2 修复从 selector 命中到方言实例化的端到端路径有测试。
- [ ] 独立 closure audit 已完成并由**不同 task_id** 的子 agent 执行，evidence 写入本 plan `Closure` 段（guide #26/#27）；roadmap Work Item Status 的 WI3 行据此才可 `todo` →（执行中）→ `done`。

## Deferred But Adjudicated

### dm `RETURNING` 语义取证（第 5 项的残余）

- Classification: `watch-only residual`
- Why Not Blocking Closure: DM8 无公开一手文档，G-1 证据等级纪律禁止以「未取证」支撑「已修正」结论；roadmap WI3 EC 明文本项交付即「显式保留 `unverified` + 登记 successor」，本条目即该登记。现值 `true` 的风险已由 unverified 标注显式化，不产生新的静默面。
- Successor Required: `yes`
- Successor Path: 取得 DM8 一手文档或可实测环境后，按 roadmap WI3 表第 5 行重新裁定（改值或确认），以独立提交落地并同步 owner doc 矩阵行；触发前不占任何 WI。

### `defaultNullsFirst` 生成器消费点（第 10 项的消费半边）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 消费点与 WI7 的 `NULLS FIRST/LAST` 子句输出同一落点（ORDER BY 打印路径），WI3 单方实现必然与 WI7 撞车或制造假语义；移交链已写入本 plan 专节与 owner doc，roadmap WI7 EC ②③④⑤ 为可核对的承接判据。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI7 节（文法接受 + `supportNullsFirst` accessor 与门控 + `defaultNullsFirst` 默认序消费与 7 XML 消费路径断言）。

## Non-Blocking Follow-ups

- `dialect.xdef:38-39` 的 javadoc 勘误：`supportNullsFirst` 的「`if(isnull(field),0,1)` 模拟」表述（无实现、且是 owner doc 失真的源头）与 `supportReturningForUpdate` 补「仅指 UPDATE、投影列表形式」限定——xdef 注释属配置 schema 文档，随下次触碰该文件的 WI 顺带修正。
- `getIntercectKeyword` 拼写修正（`AstToEqlGenerator.java:537` 基类 + `AstToSqlGenerator.java:501` 覆写 + 调用点，同步更名）：纯 cosmetic，涉及与 WI1 的共享文件，单独提交、不阻塞本 WI。
- EQL 回显路径的集合算子关键字不读方言（`AstToEqlGenerator.visitSqlUnionSelect` 经基类硬编码 `intersect`/`except`，而执行路径经 dialect 可输出 `minus` 等）——回显保真属 BUG-4（WI2）的范畴，登记其 scope 参考。
- `mariadb` 版本变体拆分方言（如 `mariadb-pre10.3`）：仅当产品侧出现「需要同时承诺 MariaDB 10.2 与 10.3+ 各自完整能力」的需求时复议（届时矩阵快照 18→19 + selector 同步）。

## Closure

Status Note: （closure audit 通过后填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:
  - （每条 Exit Criterion 的验证结果 PASS/FAIL + 对应 live code path 或 test name）
  - （每条 Closure Gate 的验证结果 PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/20-eql-wi3-dialect-config-fixes.md --strict` 退出码
  - Anti-Hollow 检查结果：`scan-hollow-implementations.mjs --module nop-dao/--module nop-orm-eql --severity high` 退出码 + 空值门/accessor 调用链追踪结论
  - Deferred 项分类检查：确认无 in-scope live defect 被降级（dm unverified 以 G-1 纪律与 roadmap EC 明文为据；defaultNullsFirst 移交以 roadmap WI7 EC 为据）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
- （或明确写 no remaining plan-owned work）
