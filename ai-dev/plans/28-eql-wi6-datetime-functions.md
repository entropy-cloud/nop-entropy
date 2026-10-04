# 28 EQL WI6 — 归一化日期时间函数族（`dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc` + `lastDay` 裁定）

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI6 节 + WI5 节的 EC ①-⑧ + 共同约束 1-4 + 「明确延后项」`lastDay(d)` 行 + 门控矩阵 WI6 行 + Cross-Cutting 全部 + 关键约束与红线，v7.2）；Current Baseline 全部代码事实于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验（矩阵快照、方言 XML、文法、语料 grep、两道 node 门禁实跑）
> Related: `ai-dev/plans/24-eql-wi5-sql92-function-syntax.md`（**硬前置，已 `completed`**——2026-10-04 收口，独立 closure audit 全 PASS、0 Blocker/0 Major；roadmap WI5 行已 `done`。**排队门已满足，本 plan 全部 Phase 可开工**，见硬前置声明）、`ai-dev/design/eql-cross-db-compat/2026-10-04-wi5-phase0-adjudications.md`（§1.3 字段函数注册设计、§9 差异登记，本 plan 直接消费）、`ai-dev/plans/27-eql-wi8-null-safe-predicates.md`（硬前置排队结构先例）、`ai-dev/plans/26-eql-wi7-pagination-and-nulls.md`（roadmap 行定价回写"独立交付物、原地留注记"机制先例，Phase 5 直接消费）

## 硬前置声明（开工门，非空话）

roadmap 门控矩阵 WI6 行明文硬前置 = **WI5**。实测现状（2026-10-04 独立复审更新）：`ai-dev/plans/24-eql-wi5-sql92-function-syntax.md` 已 **`completed`**（2026-10-04 收口，独立 closure audit 全 PASS、0 Blocker/0 Major/2 Minor，Closure Evidence 已写入 plan 24），roadmap Work Item Status 的 WI5 行已 **`done`**。⇒ **排队门已满足，本 plan 全部 Phase（含代码 Phase）可开工，无排队限制**。原起草期"代码 Phase 排队等 plan 24 completed"的限制即刻解除；各 Phase 保留的 plan 24 状态复核 bullet **降级为回退防护**（开工前快速确认其未回退为 `active`，发现回退即停工上报并记录 daily log——防止收口审计被翻案的极端情形阻塞后续 Phase 的正确性前提）。

## Purpose

收口 roadmap WI6 的**真实剩余缺口**。WI5 Phase 3 已把 `month`/`day`/`hour`/`minute`/`second`/`quarter` 六个字段函数注册到全部 15 个可加载方言、Phase 5 已放开五词遮蔽——**roadmap WI6 函数族清单的前六项已是可用能力，本 plan 不重复交付、只守护**。本 plan 交付的是：① 五个真缺口函数（`dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc`）按"逐 cell 无损 lowering"矩阵落地（能无损的方言 cell 注册，不能无损的 cell 编译期响亮报错并按约束 3 进 ledger，不静默产出错误 SQL）；② 「明确延后项」`lastDay(d)` 行的终局裁定与执行（落地或显式移出 + successor，不允许名义 owner）；③ roadmap WI6 EC 的剩余项（矩阵快照增量、owner doc 覆盖表、真库执行证据、语料 BC、独立 closure audit）与 WI5 遮蔽回归的守护。

## Current Baseline

> 锚点为**符号定位优先**；file:line 为 2026-10-03 实测参考值。WI5 收口审计若再平移行号，执行时以 Phase 0 复核为准。

### WI5 已落地、本 plan 直接消费或守护的能力（2026-10-03 实测）

- **六个字段函数 15/15 注册**：`default.dialect.xml` +6 `<native>`（`month:263`/`day:268`/`hour:273`/`minute`/`second`/`quarter:288`，各带 `<testSql>`）；`mssql.dialect.xml:210-233` 六个 `<template>` → `datepart(field,{0})`；`oracle.dialect.xml:197+` 六个 `<template>` → `extract(FIELD from {0})`（`quarter` = `to_number(to_char(...))`）；`postgresql.dialect.xml:169+` 六个 `<template>` → `extract(FIELD from {0})`；mysql/mariadb/mysql5.7/h2/h2gis 等原生名即真名。矩阵快照增量 +90（15 方言 × 6）。**`month(d)`/`day(d)` 等直接调用今天已端到端可用**（解析 → transform 解析 → 方言 lowering 三层连通），证据：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlKeywordShadowDialect.java`（h2 上 `day(o.name)`/`month(o.name)`/`year(o.name)` lowering 断言 + 未注册方言 `ERR_EQL_UNKNOWN_FUNCTION` 响亮锁定）、`TestEqlExtractFunctionDialect.java`。
- **五词遮蔽已放开**：`DAY/HOUR/MINUTE/SECOND/QUARTER` 在 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `unreservedWord_`（`:72` 起）；`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlKeywordShadowReleaseParse.java` + `TestEqlKeywordShadowDialect.java` 为其回归面。⇒ **roadmap WI6 EC"另加"的"遮蔽放开后可解析"回归测试已由 WI5 交付**，本 plan 守护其保持绿，不新建重复测试。
- **15 个可加载方言**（db2 经 WI3 补 mandatory 子节点后可加载；`es` pseudo；`default`/`geo-support`/`window-expr-support` 不可加载）——沿用 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi5-phase0-adjudications.md` §9.1 口径。矩阵快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` + 门禁 `ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`（2026-10-03 实跑 exit 0：18 登记 / 15 可加载 / 3 不可加载）。
- **函数解析与模板机制（复用，不改）**：`EqlTransformVisitor.visitSqlRegularFunction`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:1475-1502`，2026-10-04 独立复审实测；起草期曾记 `:1454-1481`，WI5 收口提交平移所致——**Phase 0 复核预期必漂，以符号定位为准**）——未注册抛 `ERR_EQL_UNKNOWN_FUNCTION`、元数校验（均在同方法内，内部行号随平移漂移，Phase 0 以符号定位复核）；`AstToSqlGenerator.visitSqlRegularFunction`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:499-503`）；函数名构建期 `toLowerCase(Locale.ROOT)`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java` 的 `SqlRegularFunction_name`，2026-10-04 复审实测 `:577`；起草期曾记 `:407-409`——同上漂移登记）⇒ EQL 书写 `dateAdd(...)`/`lastDay(...)` 查表用小写名 `dateadd`/`lastday`。`TemplateSQLFunction` marker 越界/元数加载期校验在位（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/TemplateSQLFunction.java`）。

### 缺口函数的解析现状（2026-10-03 实测，决定零文法改动形态）

- `dayofweek`/`weekofyear`/`dateadd`/`datediff`/`datetrunc`/`lastday` **均不是任何 keyword token**（在 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`、`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 三个文法文件 grep 0 命中）⇒ 它们走 `IDENTIFIER_` → `sqlIdentifier_func_` → `sqlRegularFunction` **今天已可解析**，transform 层 `getFunction(name)` 为 null 即报 `ERR_EQL_UNKNOWN_FUNCTION`（响亮，无静默）。⇒ **缺口纯粹是方言函数表注册，默认形态 = 零 `.g4`/AST 改动、零重生成、`ast/_gen/` 零 diff**——与 roadmap 门控矩阵 WI6 行 Protected Area = `—` 一致。
- **部分可用的既有注册**：`dateadd`/`datediff` 已注册于 **h2/h2gis**（`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/h2.dialect.xml:161`/`:180`，H2 原生、**unit 为字符串字面量**，如 `DATEADD('MONTH', -1, DATE '2001-01-31')`；`datediff` 语义 = "crossed unit boundaries"）⇒ `dateAdd(unit, n, d)`/`dateDiff(unit, d1, d2)` 在 h2/h2gis 上今天已经可用（camelCase 书写小写化后命中既有注册）。其余 13 方言 0 注册。
- **`dayofweek`/`weekofyear`/`datetrunc`/`lastday`：15 方言全 0 注册**（快照取证）。
- **文法相关既有事实**：`extractField__`（`BaseRule.g4:334`）只认 `YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|QUARTER` 七字段，`WEEK/MICROSECOND` 文法层拒绝（WI5 裁定 §1.3："若 WI6 注册 week 族再扩字段规则"）；`intervalUnit_`（`:401-403`）**含 `WEEK`**（`interval 1 week` 今天可解析）。plan 24 的 Deferred 项「`WEEK` 关键字遮蔽」明文"WI6 若出现 weekofyear 归一化需求时一并复核"——本 plan Phase 0 必须复核并给出结论。

### 既有 EQL 语料相容性（2026-10-03 实测）

7 个含 `<eql>` 的 `sql-lib.xml` 对 `dayofweek`/`weekofyear`/`dateadd`/`datediff`/`datetrunc`/`lastday`/`last_day`/`date_add`（含大小写变体）grep **0 命中**（含三份必测：`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`、`nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`）。⇒ 默认形态（纯函数表注册）不改解析、不改回显，**语料 BC 风险面为零**；判据仍以 `TestEqlCorpusBaseline` 棘轮逐字比对兜底。

### 缺口函数 × 方言原生能力矩阵摘要（Phase 0 逐 cell 一手引证后升格为 owner doc 覆盖表）

> 证据等级：`[实测]` = 本仓库注册面/CI 真库（H2 in-mem）；`[文档]` = 一手文档（引证落 Phase 0 裁定文档与 owner doc）；`[待一手取证]` = 起草期仅有二手线索，Phase 0 必须开官方文档核实后才能写进覆盖表；`[未取证]` = dm/tdengine 无可引用一手文档（红线）、db2 弱证据纪律、es 伪方言——**一律不计入覆盖率**。

| 函数（EQL 规范名 / 注册小写名） | mysql / mariadb / mysql5.7 | h2 / h2gis | postgresql / postgis | oracle / oracle-reverse | mssql | duckdb | dm / tdengine / db2 / es |
|---|---|---|---|---|---|---|---|
| `dayofweek` | 原生 `DAYOFWEEK(d)` = **ODBC 1=Sunday..7** `[文档]` | 原生 `DAY_OF_WEEK`（1=Sunday）与 `ISO_DAY_OF_WEEK`（1=Monday） `[文档]` | 无函数；`EXTRACT(DOW)`=0=Sunday..6、`EXTRACT(ISODOW)`=1=Monday..7 `[文档]` | 无函数；`TO_CHAR(d,'D')` **NLS 地域依赖** `[文档]` | 无函数；`DATEPART(weekday,d)` **SET DATEFIRST 依赖** `[文档]` | 原生 `dayofweek(d)` = **0=Sunday..6=Saturday** `[文档]`（`isodow`=1=Monday 亦有） | `[未取证]` |
| `weekofyear` | 原生 `WEEKOFYEAR(d)` = ISO 周（= `WEEK(d,3)`） `[文档]` | `EXTRACT(WEEK)`；是否有独立 `ISO_WEEK` 函数 `[待一手取证]` | `EXTRACT(WEEK FROM d)` = ISO 周 `[文档]` | 无函数；`TO_CHAR(d,'IW')` = ISO 周（确定性） `[文档]` | 无独立函数；`DATEPART(iso_week,d)` = ISO 周 `[文档]` | 原生 `weekofyear(d)` = ISO 周 `[文档]` | `[未取证]` |
| `dateAdd` | `DATE_ADD(d, INTERVAL n unit)`——**unit 裸关键字** `[文档]` | `dateadd('unit',n,d)` **已注册 `[实测]`**，unit 字符串 | interval 算术（`d + n * interval`） `[文档]` | 算术 / `NUMTODSINTERVAL`+`NUMTOYMINTERVAL` **按单位域分两函数** `[文档]` | `DATEADD(unit,n,d)`——**unit 裸关键字（是否接受字符串需取证）** `[文档]` | `date_add(d, INTERVAL n unit)` / 算术 `[文档]` | `[未取证]` |
| `dateDiff` | `DATEDIFF(d1,d2)` **仅天、仅 2 参**；`TIMESTAMPDIFF` = 完整单位数 ≠ 边界跨越 `[文档]` | `datediff('unit',d1,d2)` **已注册 `[实测]`**，边界跨越 | 减法：`date-date`=天数 / `timestamp-timestamp`=interval `[文档]` | 减法 `d1-d2`（DATE 含时间分数，非边界跨越） `[文档]` | `DATEDIFF(unit,d1,d2)` 边界跨越——unit 裸关键字 `[文档]` | `date_diff('part',d1,d2)` 边界跨越，unit 字符串 `[文档]` | `[未取证]` |
| `dateTrunc` | **无原生**（DATE_FORMAT/MAKEDATE 构造式均非无损等价） `[文档]` | **仓内已证存在且接受裸关键字单位**：`h2.dialect.xml:195-196` 既有 `<template name="date"><source>date_trunc(DAY,{0})</source></template>`（随既有 h2 真库测试全绿执行）；单位词表与**带引号字符串接受性** `[待一手取证]`（决定字符串单位约定下 h2 cell 可达性） | `date_trunc(text, d)`，unit 字符串 `[文档]` | `TRUNC(d, fmt)`——单位词表不同（'MM'/'IW'/'HH'…），非全量无损 `[文档]` | `DATETRUNC(unit,d)` **SQL Server 2022 起**（= G-1 承诺基线）——unit 裸关键字 `[文档]`（证据上限见 Phase 0 取证项①） | `date_trunc(text, d)`，unit 字符串 `[文档]` | `[未取证]` |
| `lastDay`（ledger 项） | 原生 `LAST_DAY(d)` `[文档]` | H2 functions.html 有 `LAST_DAY` `[待一手取证]` | **无原生**；`date_trunc('month',d) + interval '1 month - 1 day'` 为**精确**等价 `[文档]` | 原生 `LAST_DAY(d)` `[文档]` | 原生 `EOMONTH(d)` `[文档]` | 原生 `last_day(d)` `[文档]` | `[未取证]` |

**机制约束（归一化形态的核心矛盾，Phase 0 裁定的主战场）**：`<template>` 的 marker 按参数原样渲染——EQL 字符串字面量参数渲染为**带引号**文本。⇒ 单位参数采用**字符串字面量**约定（如 `dateTrunc('month', d)`）时可无损到达 pg/duckdb 的字符串单位函数与 h2 `dateadd`/`datediff`（h2 二者的 `<testSql>` 即字符串单位实参，仓内真库已证）；**h2 `date_trunc` 的仓内证据为裸关键字单位**（`h2.dialect.xml:195-196` `date_trunc(DAY,{0})`），带引号字符串是否同被接受待 Phase 0 取证（裸关键字语法位 ⇒ 该 cell 在字符串单位约定下可能不可达）；而 **mysql `DATE_ADD` 的 unit、mssql `DATEADD/DATEDIFF/DATETRUNC` 的 datepart、oracle interval 字面量的单位均为裸关键字语法位**，字符串实参是否被接受需 Phase 0 逐方言一手取证——不接受者该 cell 即**无法经函数表模板无损到达**（fixed template 无法把 `'month'` 去引号、也无法按单位值分支，如 oracle 的 DS/YM 双函数分叉）。这类 cell 按约束 3 处理：**不注册（编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮）+ 进 ledger**，不允许"注册一个只对部分单位正确的模板"（= 只打印不设门的净回归）。`dayofweek` 的**规范计法选择**（ODBC 1=Sunday vs ISO 1=Monday）是同级别裁定：模板算术可跨计法换算（如 `EXTRACT(ISODOW FROM {0})`、`1 + ((DAYOFWEEK({0}) + 5) % 7)` 类），但必须钉死一种并全方言对齐。

### 全绿口径与 stream 豁免

含 `:nop-auth-service` 的命令会拖入 `nop-stream-runtime`，其 `-T 1C` 时序 flake 按基线豁免（均单跑 PASS）：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（WI0 既档，`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头；**roadmap Cross-Cutting「必跑命令」行现行登记项**）+ `TestMessageSourceFunctionRestart`（WI1 收口轮实测，`ai-dev/plans/03-eql-wi1-class-a-defects.md` Closure 段）。⇒ **"全绿" = 本 WI 相关模块全绿 + 豁免项与基线一致**，不得据此判本 WI 失败。⚠️ **豁免清单以收口日实跑为准**：roadmap Cross-Cutting 现行登记 1 项，本 plan 起草期沿用 WI5 收口期的 2 项口径，两处不一致时**优先级 = 收口日实跑基线**（逐项单跑 PASS 复核后刷新豁免清单并记录 daily log，不预设"恰好 2 项"）。

## Goals

- **五个真缺口函数按 Phase 0 cell 矩阵落地**：每个函数 × 每方言 cell 只落**无损 lowering**（原生注册或模板），cell 的证据等级逐格标注；不能无损的 cell **不注册**（编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮报错，不允许静默产出语义错误的 SQL）并按约束 3 进「明确延后项」ledger 或本 plan Deferred。零 `.g4`/AST 改动（默认形态）。
- **`dayofweek` 规范计法裁定并全方言对齐**（Phase 0 钉死 ODBC 或 ISO 之一），`weekofyear` 钉死 ISO 周语义。
- **`lastDay(d)` ledger 行终局裁定并执行**（roadmap EC 明文"落地或显式移出并写 successor，不允许名义 owner"）：默认倾向落地（上方矩阵显示多数方言有原生或精确等价形态，ledger 旧理由"近似但不等价"对多数 cell 不成立）；若 Phase 0 证据显示无损面退化则显式移出 + 更新 roadmap ledger 行 + successor。
- **WI5 已交付能力零回归**：六个字段函数直接调用、五词遮蔽放开、`TestEqlKeywordShadowReleaseParse`/`TestEqlKeywordShadowDialect` 保持绿；`interval 1 day`/`interval 1 week` 形态逐字不变。
- 每个新注册 cell 具备 roadmap WI5 EC ①-④ 要求的四件套：断言**正确方言 SQL 文本**的正向用例（红→绿）、未注册方言编译期响亮报错的负向用例（复用 `ERR_EQL_UNKNOWN_FUNCTION`/元数错误码，不新起新码，除非 Phase 0 裁定必须）、矩阵快照增量同步、owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的"各库覆盖情况"表（逐格 `[实测]`/`[文档]`/`[未取证]`）。
- 新注册 cell 在 `TestDialect.testFunctions` 真库（H2）执行路径全绿（EC ⑥）；无法真库验证的方言逐个列出 + 替代证据。
- 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致（EC ⑦）；约束 4 的"一致性 + 不等价"两类用例齐备。
- 独立 closure audit（不同 task_id，evidence 写入本 plan Closure 段）（EC ⑧）。

## Non-Goals

- **不重复交付 `month`/`day`/`hour`/`minute`/`second`/`quarter` 的注册**——WI5 Phase 3 已 15/15 落地（见 Current Baseline），本 plan 只守护不重建；WI6 Goals 与验收不得声称这些函数为本 plan 交付面。
- **不做任何语法面改动**（默认形态）：不扩 `extractField__` 加 `WEEK`/`MICROSECOND`、不放开 `WEEK` 关键字遮蔽、不为"裸关键字单位"新增特殊语法备选分支。若 Phase 0 裁定某函数必须走文法形态才能无损 lowering，属**显式 scope change**——推翻 roadmap 门控矩阵 WI6 行 Protected Area = `—`，须按 roadmap 约束 1 路径定价（重生成 `EqlParser.java`/`_EqlASTBuildVisitor.java`）并回滚本条（登记进"与 roadmap 的差异登记"）。
- **不做 interval 算术的逐方言渲染**：`o.createTime + interval 1 day` 的 `sqlIntervalLiteral` 模板落地唯一 owner 是 **WI7b**（roadmap WI1/WI7b 明文）；本 plan 的 `dateAdd` 若在某方言 cell 上被裁定"与 interval 算术重叠、无独立无损模板"，该 cell 移交 WI7b 登记，不在本 plan 强落。
- 不做 `greatest`/`least`（ledger：G-2 未裁决，"若采纳忽略 NULL 进 WI6 同批"的触发条件**未成立**，维持 ledger 原状）；不碰 `stringAgg`/`regexpLike`/`count(*) FILTER` 等其他 ledger 项；不碰 WI3 的方言配置纠错。
- 不修 mssql 逗号形态 `extract(f,d)` 缺口（WI5 差异登记第 7 条：既有现状，非本 plan 交付面）。
- 不把 `:nop-orm-demo` / `nop-spring-simple-demo` / `:nop-orm-tdengine` / `:nop-db-migration` 拉进本 plan 的 `-pl`（roadmap 级 EC 第 8 条 + WI0 静态快照兜底）。
- 不新增 AST 节点（`EqlAST.xjava`/`ast/_gen/` 禁触，`EqlASTKind` ordinal 红线）；不删除任何 lexer token。

## Scope

### In Scope

- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（缺口函数的原生/模板注册 + `<testSql>` 补齐——**默认形态下本 plan 唯一的产品代码面**）；`nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java` 快照重生成、`TestDialect.java`/`TestSQLFunction.java` 真库证据面。
- `nop-persistence/nop-orm-eql` 测试面（正向/负向/守护用例；默认形态不改 main 代码、零重生成）。
- `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（EC ③ 增量）。
- `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（**三处，各自独立交付物、不得遗漏**：① Work Item Status WI6 行状态流转；② `lastDay(d)` ledger 行的裁定结果回写；③ **WI6 节文本的原地修订注记**（照 `ai-dev/plans/26-eql-wi7-pagination-and-nulls.md` Phase 4"roadmap 行定价回写——独立交付物，不得遗漏"机制，执行项落 Phase 5）：函数族清单注记六字段函数（`month`/`day`/`hour`/`minute`/`second`/`quarter`）已由 WI5 Phase 3/5 落地（证据指针 = plan 24 提交 `b9a2576c3a`（EXTRACT 逐字段注册 +6×15）/`eb8b91ff40`+`5e4be493d9`（遮蔽放开）+ plan 24 Closure 2026-10-04 audit 全 PASS）、本 plan 交付面收窄为 `dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc` + `lastDay` 裁定；EC"另加"遮蔽回归项注记归属——回归测试已由 WI5 Phase 5 交付（`TestEqlKeywordShadowReleaseParse`/`TestEqlKeywordShadowDialect`），WI6 守护不回归。**不删除原文、原地留注记**（"修订依据 = plan 28 落地实况"），保证历史可追溯；写明后继读取 WI6 行时的口径：六字段函数的直接调用能力不再计入 WI6 验收，后继 WI 的日期函数增量以收窄后清单为基线）。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（缺口函数"各库覆盖情况"表 + 单位参数约定说明 + 能力点登记表行）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；任何 roadmap 未登记的方言语法扩展（发现新缺陷/新需求 → 登记 roadmap，不顺手修）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **交付面收窄（非偏离，是 WI5 落地后的基线更新）**：roadmap WI6 节函数族清单含 `month`/`day`/`hour`/`minute`/`second`/`quarter`——这六项已被 WI5 Phase 3/5 顺带完成（15/15 注册 + 遮蔽放开 + 端到端可用）。本 plan 如实收窄为 `dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc` + `lastDay` 裁定 + EC 剩余项，**不重复声称完成已有能力**。
2. **可加载数 14→15**：roadmap Current Baseline 写 14 可加载；实测 15（db2 经 WI3 修复，快照 63 函数）。沿用 WI5 裁定 §9.1 口径，覆盖计数以快照实测为准。
3. **EC"另加"的遮蔽回归测试已存在**：roadmap WI6 EC 要求"day()/hour() 等各有一个遮蔽放开后可解析的回归测试"——`TestEqlKeywordShadowReleaseParse`/`TestEqlKeywordShadowDialect` 已交付并覆盖五词函数名位置 + 标识符位置 + corpus 棘轮比对。本 plan 的对应工作是**守护其保持绿**（尤其 WI6 若新增任何 main 代码时），不新建重复测试——不构成偏离。
4. **Protected Area 前提**：roadmap 门控矩阵 WI6 行 = `—` 与本 plan 默认形态（纯函数表注册）一致；但**单位参数若裁定走文法形态**（裸关键字单位语法 / `extractField__` 扩 WEEK）即触发 `.g4` → 生成管线 plan-first，与本行矛盾 ⇒ 届时必须 scope change + 差异登记 + Protected Areas 声明回滚，不得静默扩权。
5. **`lastDay` ledger 行理由过时**：roadmap 写"各库差异可用 `date_trunc` 近似但不等价"；实测 mysql/mariadb/oracle/duckdb（原生 `LAST_DAY`/`last_day`）、mssql（`EOMONTH`）、postgresql（`date_trunc` 精确算术）均有**精确等价形态**，"近似"理由对多数 cell 不成立。roadmap ledger 行本身明文"与 WI6 同批时重新评估"⇒ 本 plan Phase 0 执行该重新评估并回写结论（落地则更新该行 Successor 为"已落地"，移出则写 successor）。
6. **`es` 伪方言在快照内**：沿用 WI0 快照现状（`pseudo` 标记收录，不删行）；新函数**不得经 `default` 继承扩散到 `es`/`dm`/`tdengine`/`db2`**（见 Protected Areas 的注册面纪律）——与 WI5 TRIM 带 chars 档的"不进 default"先例一致；字段函数进 default 的先例仅适用于"语法 lowering 必需组成"，缺口函数无此前提。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-persistence/nop-dao` 的 `*.dialect.xml` 函数注册（默认形态的唯一产品面） | 平台对外方言契约的**纯增量**；`nop-dao` 无 codegen 管线（`pom.xml:119`），**不构成生成管线 Protected Area**（roadmap Cross-Cutting 第一行），与 roadmap WI6 行 `—` 一致 | 不触发 plan-first；逐 cell 登记于 Phase 0 裁定文档并同步矩阵快照 | 快照门禁 exit 0 + `TestDialect.testFunctions` H2 真库证据 + 负向用例 |
| **注册面纪律**：缺口函数的注册**只进有证据的方言 XML**，不进 `default`（防向 dm/tdengine/db2/es 泄漏未取证行为）——TRIM 带 chars 档先例（WI5 裁定 §1.4） | 未取证方言上这些函数编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮 = 预期行为 | 违反纪律（进 default 泄漏）视为 scope 违规 | 每函数一条"未取证方言编译期报错"负向用例锁定 |
| `.g4` / `EqlAST.xjava` / `ast/_gen/` / 重生成产物 | **默认禁触**（默认形态零文法改动；避免 `EqlASTKind` ordinal 重编号红线） | Phase 0 若裁定必须 → 显式 scope change + 约束 1 路径 ⓒ 定价 + plan-first（本 plan 即载体，需 draft review + Protected Areas 声明回滚） | 默认形态下每 Phase `git status` 中 `nop-persistence/nop-orm-eql/` main 零 diff |
| `EqlTransformVisitor`/`AstToSqlGenerator` 等既有 main 代码 | 默认不动（负向报错/元数校验/lowering 均为既有机制） | 若 Phase 0 裁定必须动（如单位值编译期校验需新消费点），逐点登记 | `git diff` 逐点可审计 |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`nop-core`/`nop-xlang`/`nop-xdef`。

## Execution Plan

顺序执行；每函数一个 Phase，相互独立、可分别落地与回滚。建议顺序 = 裁定确定性从高到低：lastDay（裁定项，先出结论以尽早回写 roadmap）→ weekofyear（ISO 语义各方言确定性最高）→ dayofweek（依赖规范计法裁定）→ dateTrunc → dateAdd/dateDiff（无损面预期最小）。每个代码 Phase 的提交按"XML 注册单独提交 + 测试同批"组织（无文法提交面）。

### Phase 0 - 裁定：cell 矩阵、规范计法、单位参数约定、lastDay 终局裁定（不改产品代码）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（只读取证）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（只读复核，ledger 回写随裁定执行 Phase）

> **前置状态（2026-10-04 复审更新）**：硬前置 plan 24 已 `completed`（2026-10-04 收口、独立 audit 全 PASS），roadmap WI5 行已 `done`——**排队门已满足，本 plan 全部 Phase 可开工**；各 Phase 开工前的 plan 24 状态复核已降级为回退防护（发现回退为 `active` 即停工上报）。

- Item Types: `Decision`、`Proof`

- [x] 复核 WI5 收口基础设施状态未回退（**回退防护**，记录于裁定文档）：矩阵快照 + 门禁（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0）、语料基线（`TestEqlCorpusBaseline` 通过）；plan 24 状态（已 `completed`/roadmap WI5 已 `done`——若发现回退为 `active`/`planned`，停工上报）。并复核本 plan Current Baseline 的关键锚点现位（**两处已登记漂移**：`EqlTransformVisitor.visitSqlRegularFunction` 起草期 `:1454-1481` → 2026-10-04 复审实测 `:1475-1502`；`EqlASTBuildVisitor` 的 `SqlRegularFunction_name` 起草期 `:407-409` → 复审实测 `:577`——Phase 0 复核预期必漂，以符号定位为准，复核结果记入裁定文档；另 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `unreservedWord_ :72`/`extractField__ :334`/`intervalUnit_ :401`、`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/h2.dialect.xml:161/:180/:195`）。
- [x] **裁定 `dayofweek` 规范计法**（Decision）：ODBC（1=Sunday..7，MySQL/H2 原生）vs ISO（1=Monday..7，ISODOW 族）。裁定依据至少含：现有 EQL/ORM 侧无既有 dayofweek 消费（grep 0 命中，无兼容包袱）；各方言到达成本（原生命中 vs 模板算术换算）；`java.time.DayOfWeek`/`WeekFields` 等 ORM 侧惯例对齐。产出：每方言 cell 的 lowering 表达式（原生名或模板算术，如 `EXTRACT(ISODOW FROM {0})`），mssql `DATEFIRST` 依赖与 oracle NLS `'D'` 地域依赖两 cell 除非有确定性换算模板否则**不落**（登记）。
- [x] **裁定单位参数约定与各函数的"可无损 cell 集"**（Decision，本 plan 主裁定）：逐函数 × 逐方言产出 cell 矩阵——(a) 原生注册名；(b) 模板（marker 渲染约束下的合法表达式，含引号字符串单位的可达性）；(c) 不可无损 → 不注册 + 响亮报错。必须一手取证的关键 cell（不得引用二手）：① mssql `DATEADD`/`DATEDIFF`/`DATETRUNC` 的 datepart 是否接受带引号字符串（不接受 ⇒ mssql 三函数全族仅原生命中不可达，或裁定特殊形态）——⚠️ **该结论证据上限为 `[文档]`**（Microsoft Learn 一手文档明文；CI 无 MSSQL 真库，不得标 `[实测]`；文档不明文者按证据缺失处理 → 不落 cell）；② H2 `LAST_DAY` 的存在性与签名（官方 functions.html）——`DATE_TRUNC` 的存在性与裸关键字单位接受性**已有仓内证据**（`h2.dialect.xml:195-196` 的 `date` 模板 `date_trunc(DAY,{0})`），剩余取证面 = 单位词表与**带引号字符串接受性**（决定字符串单位约定下 h2 cell 可达性）；③ mysql `DATE_ADD` 单位语法位对字符串的接受性（预期不接受，取证留痕）；④ oracle DS/YM 双 interval 函数的按单位分叉是否有单模板无损表达（预期无）。**被拒备选对照（必须记录进裁定文档）**：按单位拆函数名逐一注册（`dateAddDays`/`dateAddMonths`… 族）——拒绝理由：改变 roadmap WI6 节钉死的归一化调用面 `dateAdd(unit,n,d)`/`dateDiff`/`dateTrunc`，函数名组合爆炸且把单位分叉推给用户；若产品层面推翻此裁定须先修订 roadmap（scope change）。判据红线：**一个只对部分单位值正确的模板不得注册**（静默错误 SQL > 响亮报错，roadmap 排序原则 P1）。
- [x] **裁定 `dateDiff` 的语义锚点**（Decision，约束 3 最重灾区）：归一化语义钉死为"边界跨越"（h2/mssql/duckdb 共有语义）还是"完整单位数"或双形态——按"多数可无损 cell"与语义中立性裁定；MySQL/MariaDB `DATEDIFF`（仅天 2 参）、`TIMESTAMPDIFF`（完整单位数）与锚点语义的等价性逐 cell 判定，不等价 cell 不落。
- [x] **裁定 `lastDay(d)` 终局处置**（Decision，roadmap EC 点名）：默认倾向**落地**（`lastDay` → 注册名 `lastday`；mysql/mariadb/oracle/duckdb 原生、mssql `EOMONTH` 模板、postgresql `date_trunc` 精确算术模板、h2 待一手取证后定原生或模板）；语义钉死（返回 `d` 所在月份最后一天的日期值；时间分量处理——原生 `LAST_DAY` 族均返回 DATE/截断时间，模板方言对齐该语义；**时区维度纳入 cell 判据**：postgresql `date_trunc` 对 timestamptz 按会话 TimeZone 截断（时变），原生 `LAST_DAY` 族无此时变——模板 cell 须钉死输入类型域，差异进约束 4"不等价"登记 + owner doc 差异行）。若无损 cell 集 < 3 方言则改为**显式移出**：更新 roadmap ledger 行（结论 + successor = 后续有需求时随具备原生支持的方言批次立项），不允许名义 owner。
- [x] **复核 plan 24 的 WEEK 两项 Deferred 在 WI6 语境下的结论**（Decision）：① `WEEK` 关键字遮蔽——`weekofyear` 是整词标识符、无需放开 `WEEK`；`week(d)` 直接调用形态无 roadmap 需求 ⇒ 维持 watch-only（successor 指向后续真实需求登记）。② `extractField__` 扩 `WEEK`（`EXTRACT(WEEK FROM d)` 文法层今天拒绝）——属文法改动（Non-Goals 禁触面）；若产品需要，走 scope change，默认不扩。
- [x] **裁定注册面与负向错误码口径**（Decision）：注册只进有证据方言 XML、不进 `default`（差异登记第 6 条）；负向复用既有 `ERR_EQL_UNKNOWN_FUNCTION`/`ERR_EQL_FUNC_TOO_FEW_ARGS`/`ERR_EQL_FUNC_TOO_MANY_ARGS`，不新起新码（理由同 WI5 裁定 §6：归一化后走普通 `SqlRegularFunction` 通道，新码无触发点即死码）。
- [x] **约束 4"一致性 + 不等价"两类用例清单**落裁定文档：至少含——`dayofweek` 计法分叉（ODBC vs ISO 各库原生值不同，"不等价"登记 + 规范计法换算的"一致性"用例）；`dateDiff` 边界跨越 vs 完整单位数 vs 仅天分叉；`dateTrunc` 单位词表分叉（mssql 不支持缩写、oracle `TRUNC` 词表不同）；`weekofyear` ISO 周跨年边界（12 月末/1 月初）用例；`lastDay` 时区维度（postgresql `date_trunc` 对 timestamptz 按会话 TimeZone 截断、时变，vs 原生 `LAST_DAY` 族无此时变——"不等价"登记 + Phase 0 钉死的输入类型域"一致性"用例）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：逐函数 × 逐方言 cell 矩阵（证据等级逐格标注，关键 cell 的一手引证原文；mssql 引号接受性等 CI 不可达 cell 明示 `[文档]` 证据上限）、`dayofweek` 规范计法、单位参数约定（含"按单位拆函数名逐一注册"被拒备选对照）、`dateDiff` 语义锚点、`lastDay` 终局结论（落地 cell 清单或移出 + successor 文本，含时区维度判据）、WEEK 两项 Deferred 复核结论、注册面纪律、负向错误码口径、两类用例清单、plan 24 状态回退防护复核记录、锚点复核记录（含两处已登记漂移的现位确认）。
- [x] 裁定文档中每个"可无损 cell"都能回答： lowering 表达式是什么、单位参数如何渲染、该方言是否一手取证。
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（owner doc 的覆盖表随各代码 Phase 落）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - `lastDay(d)` 裁定执行（落地注册 或 移出 + ledger 回写）

Status: completed
Targets: `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（若裁定落地）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（ledger 行回写）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-dao`/`nop-orm-eql` 测试

- Item Types: `Decision`（执行 Phase 0 结论）、`Feature`（若落地）、`Proof`

- [x] **开工前置回退防护**：硬前置 plan 24 已 `completed`（2026-10-04 收口、audit 全 PASS）、roadmap WI5 行已 `done`——**排队门已满足，本 Phase 可开工**；开工前快速复核 plan 24 的 Plan Status 未回退（若回退为 `active`，本 Phase 停工上报，daily log 记录）。（2026-10-04 复核：plan 24 头部仍 `completed`，无回退。）
- [x] **裁定落地分支**：按 Phase 0 cell 清单注册（原生进 mysql/mariadb/oracle/duckdb 等有原生方言的 XML；mssql `EOMONTH({0})` 模板；postgresql `date_trunc` 精确算术模板；h2 按取证结论）——**不进 `default`**；h2 cell 带 `<testSql>` 供真库执行。语义断言以 Phase 0 钉死的口径为准。
- [x] **裁定移出分支**（仅当 Phase 0 结论为移出；Phase 0 结论=落地，本分支按其"仅当"条件不适用，checklist 保留为已裁定不触发）：roadmap「明确延后项」`lastDay(d)` 行更新为终局结论（不再"待重新评估"）+ successor 写明；owner doc 补一段"为什么不做"的裁定记录；本 Phase 其余执行项标记 cancelled 并在 daily log 说明。
- [x] 落地分支的正向用例（EC ①）：h2 上 `lastDay(...)` 断言具体 SQL 文本与**真库执行结果**（如 `lastDay('2026-02-10')` → `2026-02-28`）；再取一个模板方言断言渲染文本。
- [x] 负向用例（EC ②）：未注册方言（dm 或手构无注册方言）编译期报 `ERR_EQL_UNKNOWN_FUNCTION`（funcName=`lastday`）；无静态默认返回/回退原文输出（无静默跳过）。
- [x] 接线验证（Minimum Rules #23）：`lastDay` 从 EQL 文本 → parse → transform → 生成/报错的端到端用例。
- [x] 约束 4"不等价"登记：原生 `LAST_DAY` 返回 DATE 与带时间戳输入方言的返回类型差异 + postgresql `date_trunc` 算术模板对 timestamptz 的会话 TimeZone 依赖（时变，原生族无此时变——输入类型域以 Phase 0 钉死口径为准；owner doc 差异行）。
- [x] 矩阵快照增量同步（EC ③，落地分支）：`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [x] roadmap ledger 行回写（两分支均要求）：`lastDay(d)` 行状态 + 依据 + （若落地）从"延后"翻为"已由 WI6 落地"；与裁定文档一致。
- [x] owner doc（EC ④）：`lastDay` 覆盖表（`[实测]`/`[文档]`/`[未取证]`）+ 能力点登记表行；真库证据（EC ⑥）与语料 BC（EC ⑦）按全 plan 统一口径落。

Exit Criteria:

- [x] `lastDay(d)` 有终局裁定且已执行：落地分支 = 注册面与 cell 清单一致 + 快照门禁 exit 0；移出分支 = ledger 行更新 + successor 明确。无第三种"挂着不动"状态。
- [x] 落地分支：红→绿正向用例断言具体 SQL 文本 + h2 真库执行；负向用例断言具体错误码；接线验证通过；无静默跳过。
- [x] roadmap ledger 行、裁定文档、owner doc 三处口径一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - `weekofyear(d)` → ISO 周（归一化注册）

Status: completed
Targets: 同 Phase 1 的方言 XML/文档/测试面

- Item Types: `Feature`、`Proof`

- [x] **开工前置回退防护**：plan 24 已 `completed`（同上，排队门已满足）；快速复核其 Plan Status 未回退（回退为 `active` 即停工上报）。（2026-10-04 复核无回退。）
- [x] 修复前红：`weekofyear(o.createTime)` 今天在所有方言编译报 `ERR_EQL_UNKNOWN_FUNCTION`——回归测试先断言该基线（红），再断言落地后 lowering（绿）。（红证据 `_tmp/wi6/red-phase2.log`：7/8 fail。）
- [x] 按 Phase 0 cell 矩阵注册 ISO 周语义：mysql `WEEKOFYEAR({0})` 原生名命中；postgresql `EXTRACT(WEEK FROM {0})` 模板；oracle `TO_NUMBER(TO_CHAR({0},'IW'))` 模板；mssql `DATEPART(iso_week,{0})` 模板；duckdb 原生 `weekofyear` 命中；h2 按取证结论。不进 `default`；dm/tdengine/db2/es 响亮报错。
- [x] 正向用例（EC ①）：h2 文本 + 真库执行（含跨年边界输入，如 ISO 周 2025-12-29 属 2025-W01 类）+ 至少一个模板方言的渲染文本断言。
- [x] 负向用例（EC ②）：未注册方言 `ERR_EQL_UNKNOWN_FUNCTION`；元数负向（1 参函数的 0/2 参调用报元数错误码）。
- [x] 接线验证（#23）+ 约束 4"不等价"登记：非 ISO 周 各库 `WEEK(d)` 默认 mode 差异（owner doc 差异行——本 plan 只落 ISO，不落"locale 周"）。
- [x] 矩阵快照增量同步（EC ③）+ owner doc 覆盖表（EC ④）+ 真库证据（EC ⑥）+ 语料 BC（EC ⑦）。

Exit Criteria:

- [x] 红→绿回归测试断言具体 SQL 文本（h2 + 至少一个模板方言）与跨年边界语义。
- [x] 负向用例断言具体错误码（unknown-function + 元数）；无静默跳过。
- [x] 接线验证用例通过（parse→transform→generate 三层连通）。
- [x] 矩阵快照门禁 exit 0；owner doc 覆盖表已落档（含 `[未取证]` 标注）。
- [x] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - `dayofweek(d)` → 规范计法（归一化注册）

Status: planned
Targets: 同 Phase 1 的方言 XML/文档/测试面

- Item Types: `Feature`、`Proof`

- [ ] **开工前置回退防护**：plan 24 已 `completed`（同上，排队门已满足）；快速复核其 Plan Status 未回退（回退为 `active` 即停工上报）。
- [ ] 修复前红：`dayofweek(o.createTime)` 今天全方言编译报 `ERR_EQL_UNKNOWN_FUNCTION`——先断言基线（红），再断言落地（绿）。
- [ ] 按 Phase 0 规范计法 + cell 矩阵注册：模板算术换算 cell（如 postgresql `EXTRACT(ISODOW FROM {0})` 或 mysql 换算表达式）与原生命中 cell 产出**同一规范值域**——每方言一条"同输入同规范值"的一致性断言（约束 4"一致性"类，跨方言值等价是本函数的核心验收）。
- [ ] `DATEFIRST`/NLS 依赖 cell（mssql `DATEPART(weekday)`、oracle `TO_CHAR 'D'`）：按 Phase 0 裁定——有确定性换算模板则落，否则不落 + 登记差异行；**不允许落一个值随会话设置漂移的 cell**。
- [ ] 正向用例（EC ①）：h2 文本 + 真库执行 + 至少一个换算 cell 的渲染文本断言。
- [ ] 负向用例（EC ②）：未注册方言 `ERR_EQL_UNKNOWN_FUNCTION`；元数负向。
- [ ] 接线验证（#23）+ 约束 4"不等价"登记：`dayofweek` 计法分叉（ODBC vs ISO 各库原生值对照表入 owner doc）。
- [ ] 矩阵快照增量同步（EC ③）+ owner doc 覆盖表（EC ④）+ 真库证据（EC ⑥）+ 语料 BC（EC ⑦）。

Exit Criteria:

- [ ] 红→绿回归测试断言具体 SQL 文本；跨方言"同输入同规范值"一致性用例通过。
- [ ] 负向用例断言具体错误码；无静态值/无会话依赖 cell 被落地；无静默跳过。
- [ ] 接线验证用例通过；计法分叉对照表已落 owner doc。
- [ ] 矩阵快照门禁 exit 0；owner doc 覆盖表已落档。
- [ ] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - `dateTrunc(unit, d)` → 单位字符串约定（无损 cell 注册）

Status: planned
Targets: 同 Phase 1 的方言 XML/文档/测试面

- Item Types: `Feature`、`Proof`

- [ ] **开工前置回退防护**：plan 24 已 `completed`（同上，排队门已满足）；快速复核其 Plan Status 未回退（回退为 `active` 即停工上报）。
- [ ] 修复前红：`dateTrunc('month', o.createTime)` 今天全方言（除 h2 若取证确认有 `DATE_TRUNC`）编译报 `ERR_EQL_UNKNOWN_FUNCTION`——先断言基线（红），再断言落地（绿）。
- [ ] 按 Phase 0 cell 矩阵注册：pg/duckdb `date_trunc({0},{1})` 字符串单位模板（或原生命中）；h2 按取证结论；mssql 2022 `DATETRUNC`——**仅当**取证证明其接受字符串单位（或裁定等价模板）才落，否则不落 + 登记；MySQL/MariaDB 无原生且构造式非无损 ⇒ 不落 + ledger/差异行（约束 3）。
- [ ] 正向用例（EC ①）：h2 文本 + 真库执行（`dateTrunc('month', ...)` 断言截断结果）+ 至少一个模板方言渲染文本。
- [ ] 负向用例（EC ②）：不落 cell 的方言 `ERR_EQL_UNKNOWN_FUNCTION`（如 mysql）；元数负向。
- [ ] 接线验证（#23）+ 约束 4"不等价"登记：单位词表分叉（各方言支持的 unit 集合不同——规范词表 = 裁定文档钉死的交集或并集 + 各 cell 实际支持范围，owner doc 差异行）。
- [ ] 矩阵快照增量同步（EC ③）+ owner doc 覆盖表（EC ④）+ 真库证据（EC ⑥）+ 语料 BC（EC ⑦）。

Exit Criteria:

- [ ] 红→绿回归测试断言具体 SQL 文本与截断语义；规范单位词表已钉死并写入 owner doc。
- [ ] 负向用例断言具体错误码（mysql 等不落方言）；无静默跳过；无"部分单位正确"的模板被注册。
- [ ] 接线验证用例通过。
- [ ] 矩阵快照门禁 exit 0；owner doc 覆盖表已落档。
- [ ] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - `dateAdd` / `dateDiff` → 无损 cell 注册 + 约束 3 剩余面裁定（预期增量最小）

Status: planned
Targets: 同 Phase 1 的方言 XML/文档/测试面；`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（若 ledger 新增行）

- Item Types: `Feature`、`Decision`（不落 cell 的 ledger 处置）、`Proof`

- [ ] **开工前置回退防护**：plan 24 已 `completed`（同上，排队门已满足）；快速复核其 Plan Status 未回退（回退为 `active` 即停工上报）。
- [ ] 修复前红：`dateAdd(unit,n,d)`/`dateDiff(unit,d1,d2)` 今天仅在 h2/h2gis 可用（既有注册命中），其余方言编译报 `ERR_EQL_UNKNOWN_FUNCTION`——先断言该基线（红），再断言各落点（绿）。
- [ ] 按 Phase 0 cell 矩阵注册可无损 cell：候选含 duckdb `date_diff`（字符串单位边界跨越，与 `datediff` 语义锚点一致则落）、pg interval 算术模板（若裁定无损）、oracle 按单位域裁定结果、mssql/mysql 取证结果。**h2 既有注册行为不变**（守护用例：`dateAdd('MONTH',-1,d)`/`dateDiff('MONTH',d1,d2)` 在 h2 的 lowering 与真库执行与基线逐字一致）。
- [ ] **不可无损 cell 的 ledger 处置**（Decision）：逐 cell 写结论进裁定文档 + owner doc 差异表；属于"需产品需求才复议"的（如 mysql 的裸关键字单位形态）按 roadmap ledger 行格式登记（理由 / 重新评估触发条件 / successor）——不允许名义 owner。
- [ ] 正向用例（EC ①）：每个落点 cell 在 h2（若落）或对应方言的文本断言；约束 4"一致性 + 不等价"两类用例（`dateDiff` 边界跨越 vs 完整单位数 vs 仅天至少各一例登记）。
- [ ] 负向用例（EC ②）：不落方言 `ERR_EQL_UNKNOWN_FUNCTION`；元数负向（3 参形态在 2 参方言上元数报错——若存在此类注册）。
- [ ] 接线验证（#23）+ 矩阵快照增量同步（EC ③）+ owner doc 覆盖表（EC ④）+ 真库证据（EC ⑥）+ 语料 BC（EC ⑦）。
- [ ] **roadmap WI6 节文本回写（独立交付物，不得遗漏——照 plan 26 Phase 4"roadmap 行定价回写"机制，In Scope ③）**：对 roadmap WI6 节做**原地修订注记**（不删除原文，注记"修订依据 = plan 28 落地实况"）——① 函数族清单注记：六字段函数（`month`/`day`/`hour`/`minute`/`second`/`quarter`）已由 WI5 Phase 3/5 落地（证据指针 = plan 24 提交 `b9a2576c3a`/`eb8b91ff40`+`5e4be493d9` + plan 24 Closure 2026-10-04 audit 全 PASS），WI6 交付面收窄为 `dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc` + `lastDay` 裁定；② EC"另加"遮蔽回归项注记归属：回归测试已由 WI5 Phase 5 交付（`TestEqlKeywordShadowReleaseParse`/`TestEqlKeywordShadowDialect`），WI6 守护不回归；③ 写明后继读取 WI6 行时的口径（六字段函数直接调用能力不再计入 WI6 验收）。注记与本 plan 五函数 + `lastDay` 的终局收口状态一致后方可勾选。

Exit Criteria:

- [ ] `dateAdd`/`dateDiff` 的每个 cell 有终局结论：落地（注册 + 文本断言 + 证据等级）或不落（响亮报错 + ledger/差异登记）。无名义 owner。
- [ ] **roadmap WI6 节文本的原地修订注记已落地**（In Scope ③ / Phase 5 独立交付物）：函数族清单注记（含 WI5 落地证据指针 `b9a2576c3a`/`eb8b91ff40`+`5e4be493d9`）+ EC"另加"项归属注记 + 后继读取口径，原文未删、历史可追溯，与裁定文档/owner doc/ledger 口径一致。
- [ ] h2/h2gis 既有注册行为不变（守护用例通过）。
- [ ] 负向用例断言具体错误码；无静默跳过；无"部分单位正确"的模板被注册。
- [ ] 接线验证用例通过；`dateDiff` 语义分叉"不等价"登记已落 owner doc。
- [ ] 矩阵快照门禁 exit 0；owner doc 覆盖表已落档。
- [ ] `TestEqlCorpusBaseline` 通过；`./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] 五个缺口函数（`dayofweek`/`weekofyear`/`dateAdd`/`dateDiff`/`dateTrunc`）的 cell 矩阵全部有终局结论：每个 cell 落地（注册 + 正向文本断言 + 证据等级）或不落（编译期响亮报错 + ledger/差异登记）——`lastDay` 另有 Phase 1 终局裁定（roadmap EC 点名项）。
- [ ] 每个新注册 cell 的"方言未注册目标函数时编译期报错"负向用例存在且断言具体错误码（EC ②；复用既有 `ERR_EQL_UNKNOWN_FUNCTION` 族）。
- [ ] 方言能力矩阵快照的覆盖增量已同步并核实：`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（EC ③）。
- [ ] `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 已同步：五函数 + `lastDay` 的"各库覆盖情况"表（逐格 `[实测]`/`[文档]`/`[未取证]`）+ 单位参数约定与计法约定说明 + 能力点登记表行（EC ④）。
- [ ] **真库执行证据（EC ⑥）**：本 plan 新注册且在 H2 执行面的每个函数带 `<testSql>` 并在 `nop-dao/TestDialect.testFunctions` 真库路径全绿；无法真库验证的方言（mysql/mariadb/mssql/oracle/postgresql/duckdb/dm/tdengine/db2）已逐个列出并给替代证据，`dm`/`tdengine`/`db2` 按红线标 `[未取证]` 且未计入覆盖率。
- [ ] **既有 EQL 语料 BC（EC ⑦）**：`:nop-job-dao`(NopJobTask)、`:nop-auth-service`(test)、`:nop-orm`(test) 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"（`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份由 roadmap 级 EC 第 8 条 + WI0 静态快照覆盖）。
- [ ] **WI5 遮蔽回归守护（roadmap WI6 EC"另加"项）**：`TestEqlKeywordShadowReleaseParse`/`TestEqlKeywordShadowDialect` 全绿，五词 + 六字段函数行为零回归；`interval 1 day`/`interval 1 week` 形态逐字不变。
- [ ] 约束 4 的"一致性 + 不等价"两类用例/差异登记齐备（`dayofweek` 计法、`dateDiff` 语义、`dateTrunc` 单位词表、`weekofyear` ISO 跨年至少各一项）。
- [ ] 生成物纪律：默认形态下 `git status` 中 `nop-persistence/nop-orm-eql/` main 代码零 diff、`ast/_gen/` 零 diff（若发生 scope change 走文法形态，则 `.g4` 改动单独提交且门禁按约束 1 重置）。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap WI5 EC ⑤ 同款命令，WI6 EC"同 WI5 八项"）——**"全绿"口径**：本 WI 相关模块全绿 + `nop-stream-runtime` 既有 flake 与基线一致即豁免（起草期沿用 WI5 收口期 2 项：`TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`、`TestMessageSourceFunctionRestart`，均单跑 PASS），不得据此判本 WI 失败。⚠️ **豁免清单以收口日实跑为准**（roadmap Cross-Cutting 现行登记 1 项 vs 本 plan 沿用 2 项——优先级 = 收口日实跑基线，收口时逐项单跑复核后刷新并记录 daily log）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（既有基线 3 项 warning：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，不顺手修、只比对新增量）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/28-eql-wi6-datetime-functions.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（anti-hollow："只解析不 lowering/只打印不设门"正是半实现形态；本 plan 的对应风险 = 注册一个部分正确的模板当完整交付）。
- [ ] owner doc 已同步到 live baseline（见 EC ④ 条目），`docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 若路由/锚点变化已更新。
- [ ] **roadmap WI6 节文本回写已落地**（In Scope ③ / Phase 5 独立交付物：函数族清单 + EC"另加"遮蔽回归项的原地修订注记，含 WI5 落地证据指针 `b9a2576c3a`/`eb8b91ff40`+`5e4be493d9`；不删原文、历史可追溯）——本项是下方 roadmap 行状态流转的独立前置，不得遗漏。
- [ ] roadmap Work Item Status 的 WI6 行状态流转已执行：draft review 通过 → `planned`；**独立 closure audit 通过后才 `done`**（不得提前；roadmap Status values 纪律）。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）每个落点函数从 EQL 文本 → parse → transform 函数解析 → 方言 SQL 输出/报错的端到端调用链真实连通（不只是 XML 行存在）；(b) 无空方法体/静默跳过/no-op（不落 cell 必须响亮报错，不允许回退原文输出或部分正确模板）；(c) WI5 已交付能力零静默行为变化。
- [ ] **独立 closure audit 已完成并由不同 `task_id` 的子 agent 执行**，evidence 写入本 plan `Closure` 段（guide #26/#27；roadmap WI6 EC ⑧）。

## Deferred But Adjudicated

### 不可无损的 `dateAdd`/`dateDiff`/`dateTrunc` cell（裸关键字单位方言族等，Phase 5 逐 cell 确认后填实）

- Classification: `out-of-scope improvement`（逐 cell 标注；若某 cell 属"已确认产品需求被拒"则升格并回 roadmap ledger）
- Why Not Blocking Closure: roadmap 约束 3 明文"不满足能无损 lowering 的项一律不做"；这些 cell 的响亮报错（`ERR_EQL_UNKNOWN_FUNCTION`）保证无静默错误 SQL，supported baseline 在已落 cell 上成立。
- Successor Required: `yes`（重新评估触发条件逐 cell 写明：如"产品出现 MySQL 裸关键字单位形态需求""mssql datepart 字符串接受性取证翻案"）
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`「明确延后项」ledger（新增行，格式照既有行）。

### `WEEK` 关键字遮蔽 / `extractField__` 扩 `WEEK`（`EXTRACT(WEEK FROM d)`）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `weekofyear` 作为整词标识符已覆盖 roadmap WI6 点名的周需求；`week(d)` 直接调用与 `EXTRACT(WEEK FROM d)` 均无 roadmap 需求输入，且二者均需文法改动（Non-Goals 禁触面）。plan 24 Deferred 明文"WI6 若出现 weekofyear 归一化需求时一并复核"——本 plan Phase 0 已复核并维持 watch-only。
- Successor Required: `no`（真实需求出现时先登记 roadmap 再立项）
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（新增登记行）。

### `greatest`/`least`（G-2 触发条件未成立，明确不入本批）

- Classification: `watch-only residual`
- Why Not Blocking Closure: roadmap ledger 行的进 WI6 条件是"G-2 裁决采纳忽略 NULL 后"；实测 G-2 至今未裁决（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` 未覆盖 G-2 的 NULL 策略终局）⇒ 触发条件未成立，维持 ledger 原状，本 plan 不承接。**若 G-2 在本 plan 执行中途获得裁决 ⇒ 由新批次/新 plan 立项承接，本 plan 不扩 scope**（不在执行中途移动验收基线；G-2 裁决结论照 roadmap ledger 行的 Successor 路径登记）。
- Successor Required: `yes`（G-2 裁决后按 ledger 行处置）
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`「明确延后项」`greatest`/`least` 行。

## Non-Blocking Follow-ups

- `es` 伪方言在 WI0 快照中以 `pseudo` 标记收录与 roadmap 红线"不参与能力矩阵"的口径差异，由 WI0 快照 owner（roadmap）统一裁定；本 plan 只沿用现状（WI5 plan 同款 follow-up）。
- CI 真库验证面扩展（mysql/oracle 等 docker 化执行）超出本 roadmap；若立项，各函数的 `[未取证]` 格在其上转 `[实测]`。
- H2 `ISO_WEEK`/`DATE_TRUNC`/`LAST_DAY` 的一手取证结论若与起草期二手线索不符（functions.html 增删函数），按 Phase 0 取证结果调整 cell 矩阵，不影响 plan 结构。

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent，须与执行者不同 task_id）
- Audit Session: （session/task ID）
- Evidence:
  - （每条 Exit Criterion 与 Closure Gate 的验证结果 PASS/FAIL + live code path / test name）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/28-eql-wi6-datetime-functions.md --strict` 退出码
  - Anti-Hollow 检查结果（端到端调用链追踪 + `scan-hollow-implementations.mjs` 退出码）
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
