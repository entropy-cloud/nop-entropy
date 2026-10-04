# 32 EQL WI9b — CAST 类型系统·时区类型（`StdSqlType` LOCAL 时区成员落地 + `dataTypeName_` 拼写新增 + DDL 映射与迁移裁定）

> Plan Status: active
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI9b 节 `:327-332`、门控矩阵 WI9b 行 `:115`、Work Item Status `:92`（`todo`）、共同约束 1-4 `:263-280`、Cross-Cutting 全部 `:380-390`、roadmap 级 Exit Criteria `:444-456`、关键约束与红线 `:458-468`）
Related: `ai-dev/plans/25-eql-wi9a-cast-type-aliases.md`（硬前置，`completed`；本 plan 的结构先例与 WI9b 边界声明的出处）、`ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，`completed`）、`ai-dev/plans/19-eql-wi2-class-b-defects.md`（硬前置，`completed`；BOOLEAN 文法先例）、`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（G-1/G-3 已裁决）、`ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md`（WI9a Phase 0 裁定；其 §五对照表含 WI9b 边界注记，本 plan 落地时须回写）

## Purpose

收口 roadmap WI9b：把 `StdSqlType` 中注释态的 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE` 两个枚举成员落成活成员，并在 `dataTypeName_` 文法中新增 `TIME WITH LOCAL TIME ZONE` / `TIMESTAMP WITH LOCAL TIME ZONE` 拼写，使 CAST 时区类型达成"文法接受集 = 语义接受集 = 可生成面"三者一致；同时完成 roadmap EC ② 要求的迁移裁定（`No migration required` 或升级 SQL，不接受留空）与 EC ① 要求的 DDL 真库往返验证。

WI9b 必须独立于 WI9a 成 plan（roadmap `:329` 明文：这是**取消注释**而非新增，会改变既有部署的 DDL 类型映射，需独立迁移评估）。本 plan 起草时的增量实测对这一独立性的实质内容做了落点（见 Current Baseline"迁移影响面"节与差异登记第 1 条）。

**裁决人注记（ask-first，同 WI9a UUID 形态）**：本 plan 触及 `nop-kernel/nop-commons` 的 `StdSqlType`（模型层行为变更，`nop-commons` **不是** `nop-*-api`，不属 AGENTS.md 字面 Protected Area，按 roadmap 门控矩阵 WI9b 行 `:115` 走「AI Must Ask Before」第 1 条 ask-first）。书面裁定形态与 WI9a UUID 裁定相同：**平台负责人经执行指令授权、AI 代拟裁定文档、人可推翻**，落 Phase 0 裁定文档"裁决人注记"小节。本 plan 同时**自加 plan-first 更严门禁**（roadmap `:330` 明文）：执行前须通过独立子 agent draft review；**不得反向以 plan-first 之名替代 ask-first 询问**。`.g4` 侧的生成管线 plan-first 是独立成立的第二条门禁（roadmap `:115`"这条是真的"）。

## 修订记录（draft review 后，2026-10-04）

独立审查结论：0 Blocker、2 Major、4 Minor，"修订后可执行"。本轮修订清单：

1. **文件改名 `31-` → `32-`**（`31` 与既有 `31-flux-yaml-page-fallback.md` 撞号；初稿 `30-` 已避让过 `30-nop-nosql-remaining.md`），标题与本 plan 自引用同步。
2. **Major-1**：`getNames()` 键集计数更正为 **30 → 32**（活枚举成员 26 + `INT`/`TEXT`/`NUMBER`/`BOOL` 四别名（无键冲突）= 30；加 2 成员 = 32）；初稿"34 → 36"沿袭了 WI9a 裁定文档 §九 的自记算术错误（25+1 误记 30）。**WI9a 裁定文档 §九 需加勘误注记——已登记为本 plan Phase 0 裁定文档的移交项**（Phase 0 EC 第 1 条）并在 Phase 4 勘误注记中给指引。
3. **Major-2**：补全"全负结果"（支持载体空集）的裁定出口与 EC ① 满足形态——Phase 0 第 6 项映射候选扩为 (a) 无撞名新键 / (b) 空集全负（该项单独 ask-first + 预定义全负形态）/ (c) 定向撞名覆盖（登记 reverse 映射行为变化 + 单独 ask-first）；Goals 第 3 条加"≥1 载体否则回到 scope 裁决"前提；Closure Gates EC ① 与 Phase 3 相应项加全负分支。
4. **Minor-1**：行号勘误——`dataTypeName_` `:438-446`、BOOLEAN `:444`、`sqlIdentifier_` `:445`、WI9a 收窄注释块 `:428-437`、`AT TIME ZONE` roadmap 引用 `:373`。
5. **Minor-2**：Closure Gates EC ⑦ 的 WI9b 行流转起点按现状更正为 `todo` → `done`（执行前主会话按正常流转先翻 `planned`，保留勘误注记即可）。
6. **Minor-3**：新增交付物认领——Phase 4（roadmap WI9b 节文本原地勘误注记，收口前置，纯文档）。
7. **Minor-4**：Phase 2 开工前置核验新增"确认无并行 WI 的 `.g4`→`EqlParser` 重生成债未清零"（WI7 Phase 1 `09fa140ff5` 的重生成产物显式欠账"随下一次提交"）。

## Current Baseline

以下事实全部于 2026-10-04 在本工作区分支 `feat/eql-cross-db-compat` 实测核验（含 file:line）。WI9a 已收口（2026-10-04），行号均为 WI9a 落地后现态；执行时仍一律以符号定位为准，每个 Phase 开工前先复核锚点符号仍存在。

### 硬前置状态（全部满足，无排队门）

- WI9a `completed`（`ai-dev/plans/25-eql-wi9a-cast-type-aliases.md`，closure 2026-10-04，closure audit 一次通过；提交链 `5154661e75`（StdSqlType 别名+UUID）/ `41bfbcba56`（.g4 收窄））。WI9a Closure Gates 显式登记：时区区域零触碰（`git diff 90806e869e..HEAD -- nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java` 无任何 TIME_ZONE/LOCAL 行变化，本 plan 起草时复核仍成立），并在 Deferred But Adjudicated 中把时区类型全部门面移交本 plan（successor 路径已登记，无名义 owner 问题）。
- WI0 / WI2 `completed`（同 WI9a plan Current Baseline 记载）；WI0 两件产物（语料基线快照 + 方言矩阵快照门禁）在位。
- G-1 / G-3 已裁决（2026-10-03，`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`）。本 plan 对 G-1 的适用面 = Phase 0 对时区原生类型做版本门评估（见 Phase 0 第 6 项）；对 G-3 的适用面 = 其机制结论（`stdNameMap` 同时驱动 DDL 类型映射与合法 `stdSqlType=` 取值闸门）继续约束本 plan 的影响面分析，本 plan **不新增别名**。
- roadmap 门控矩阵 WI9b 行（`:115`）要求的前置 = **G-1、G-3、WI9a** —— 全部满足。roadmap Work Item Status 的 WI9b 行现为 `todo`（`:92`），依赖图 `WI9a --> WI9b`（`:432`）已解锁。

### `StdSqlType.java` 现状（时区成员注释态 + 关键新发现：字面取消注释不可编译）

- 位置：`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`。`TIME_WITH_LOCAL_TIME_ZONE` 注释态在 `:38-39`、`TIMESTAMP_WITH_LOCAL_TIME_ZONE` 注释态在 `:42-43`（roadmap `:329` 引用的 `:38,42` 与现态一致；WI9a 的 UUID 枚举追加在 GEOMETRY 之后 `:108-117`，未推移这两处行号）。
- **注释体的构造参数是已废弃的旧形态**：写的是 `PrecScale.NO_NO | PrecScale.YES_NO` 与 `SqlTypeFamily.TIME` / `SqlTypeFamily.TIMESTAMP`——当前枚举构造器是 4 参 `(boolean allowPrecision, boolean allowScale, int jdbcType, StdDataType stdDataType)`（`:124-129`），`PrecScale` / `SqlTypeFamily` 两个类型在现文件中**不存在也无 import**。⇒ **"取消注释"字面执行必然编译失败**；落地形态是"以现构造器形态重写这两个成员"，其中 `jdbcType` / `stdDataType` / `allowPrecision` / `allowScale` 四个取值是 Phase 0 的显式裁定项（候选与不变量约束见差异登记第 1 条）。这是本 plan 相对 roadmap 字面描述的第一处工作量修正。
- 三个静态 map 的不变量（Phase 0 裁定与 Phase 1 聚焦测试的核验对象，WI9a UUID 三 map 核验先例）：
  - `stdNameMap`（声明 `:155`；static 块 `:160-181`）：INT put `:161`、WI9a 三别名 put `:167-169`、`values()` 遍历灌入 `:171-178`。**新枚举成员经 `values()` 循环自动入表**（UUID 先例，`:166` 注释明示），无须显式 put；`getNames()`（`:197-199`）键集预期 **30 → 32**（活枚举成员 26 + `INT`/`TEXT`/`NUMBER`/`BOOL` 四别名、无键冲突 = 30；加 2 成员 = 32。初稿曾按 WI9a 裁定文档 §九 的"30→34"自记误写 34→36，该自记系算术误记，勘误移交见 Phase 0 EC 第 1 条与修订记录第 2 条）；闸门 A/B 错误消息的 `ARG_ALLOWED_NAMES` 集合同步变大（WI9a 差异登记第 4 条同款，预期行为）。
  - `jdbcTypeMap`（`:156`，**先到先得** `:174-175`）：现 `Types.OTHER` 的首个声明者是 `OTHER`（`:89`）。两个时区成员的声明位置（`TIME` `:32` 与 `TIMESTAMP` `:40` 之间的 `:38/:42` 区）**先于** `:89`——若裁定沿用注释体的 `Types.OTHER`，则 `fromJdbcType(Types.OTHER)` 将从 `OTHER` 静默翻转为时区成员。候选替代：JDBC 4.2 的 `Types.TIME_WITH_TIMEZONE = 2013` / `Types.TIMESTAMP_WITH_TIMEZONE = 2014`（键唯一，零翻转）。消费方实证：`JdbcHelper.java:164`（`nop-persistence/nop-dao`，JDBC 元数据读回路径）、`JdbcMetaDiscovery.java:346`（`nop-dbtool-core`）。
  - `stdTypeMap`（`:158`，**last-wins** `:177` 循环；循环后仅显式重钉 `STRING→VARCHAR` / `BYTES→VARBINARY` 两条 `:179-180`）：`StdDataType.TIME` 现归 `TIME`（`TIME` `:32` 为唯一 `StdDataType.TIME` 成员）、`StdDataType.TIMESTAMP` 现归 `TIMESTAMP`（`:40`）。时区成员若取 `StdDataType.TIME` / `StdDataType.TIMESTAMP` 且声明在 `:38/:42`（均晚于 `TIME`/`TIMESTAMP` 中的前者、`TIMESTAMP_WITH_LOCAL_TIME_ZONE` 晚于 `TIMESTAMP`），将**静默翻转** `fromStdDataTYpe` 的返回。消费方实证：`DialectImpl.java:647`（nop-dao）、`SchemaToOrmModel.java:97`（nop-orm-model，逆向建模路径）、`BaseDataFieldMeta.java:48`（nop-dataset）、`StdSqlType.fromJavaClass` `:214-219`（如 `LocalTime.class → StdDataType.TIME` 路径会改返回时区成员）。保持策略候选：沿用既有重钉先例在循环后补 `TIME` / `TIMESTAMP`（或 `DATETIME`，按裁定）两条显式重钉。
- 时区成员的 `StdDataType` 取值候选边界：`StdDataType` 现有 `DATETIME(13)`（`:93`）/`TIMESTAMP(14)`（`:96`）/`TIME(15)`（`:99`），无时区专属成员；新增 `StdDataType` 成员不在本 plan 范围（登记为 Phase 0 被拒候选即 supernatural scope，除非负责人推翻）。

### `dataTypeName_` 文法现状与 WI9a 收窄对时区备选的影响（本 plan 起草的核心新基线）

- 唯一挂载点仍是 `cast`：`BaseRule.g4:339`（`CAST LP_ (expr=sqlExpr | NULL) AS dataType=sqlTypeExpr RP_`）+ `sqlTypeExpr`（`:424-426`：`name=dataTypeName_` + 可选 `(precision[,scale])` + `characterSet_?` + `collateClause_?`）⇒ 本 plan 只影响 CAST 目标类型；orm.xml 的 `stdSqlType=` 属性走闸门 B，不经此文法。
- 现态（WI9a 提交 `41bfbcba56` 后）：`dataTypeName_`（`:438-446`）= 12 个活备选（CHAR/VARCHAR/NUMERIC/DECIMAL/INTEGER/SMALLINT/FLOAT/REAL/DATE/TIME/TIMESTAMP）+ BOOLEAN（`:444`，WI2 先例）+ `sqlIdentifier_` 单词兜底（`:445`）。规则上方有 WI9a 收窄注释块（`:428-437`），其末行明文"TIME/TIMESTAMP WITH LOCAL TIME ZONE 拼写归 WI9b（两个名字集，互不替）"。
- **WI9a 删除的 14 个死备选中确实含 `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE`（非 LOCAL）**（提交 `41bfbcba56` 实测，删除前形态经 `git show 41bfbcba56^` 复核）。
- **LOCAL 变体在任何历史时点都不存在于文法**：`git log --all -S "LOCAL TIME ZONE" -- nop-persistence/nop-orm-eql/model/antlr/` 全历史只命中 WI9a 的文档/注释提交，从未命中文法备选本体。⇒ **roadmap WI9b 的"同步 `dataTypeName_` 拼写"按事实定性是"新增备选"而非"恢复/同步既有拼写"**——实施形态与 WI2 BOOLEAN 新增（`9a76048670`）同类，而非恢复 WI9a 删除项。这是本 plan 相对 roadmap 字面描述的第二处工作量修正（定性变化：新增 ≠ 恢复，红→绿的锚点形态完全不同，见差异登记第 2/3 条）。
- **非 LOCAL 变体（`TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE`）不应恢复**：`StdSqlType` 从未有（注释态也没有）这两个非 LOCAL 成员——恢复文法备选将复活"解析可过、`SqlTypeExpr_name` 必拒 `ERR_EQL_INVALID_SQL_TYPE`"的 BUG-11 死备选，直接违背 WI9a 刚收口的收窄目标。裁定倾向（Phase 0 终裁）：**只新增 LOCAL 变体，非 LOCAL 维持删除态**，不对称性在 owner doc 对照表登记。
- 词法可行性（零 token 增删预期）：`WITH=72`（`EqlParser.java:31`）、`LOCAL=162`（`:44`）、`ZONE=352`（`:80`）均为既有 token；`TIME`/`TIMESTAMP` 亦为 keyword token 且已是活备选。⇒ 新增备选预期与 WI2 BOOLEAN / WI9a 收窄同口径：重生成仅 `EqlParser.java` / `Eql.interp` 变化，`ast/_gen` / `EqlLexer*` / `*.tokens` / `_EqlASTBuildVisitor.java` 零 diff，无 token 重编号风险。
- 失败形态基线（修复前红锚）：多词 LOCAL 拼写**今天即解析失败**（`sqlIdentifier_` 是单词级兜底，承载不了多词；失败码 `nop.err.antlr.*` 族，同 WI9a 收窄后形态）——**不是**"解析过 + 语义必拒"。红→绿断言锚定这一形态迁移。
- `sqlTypeExpr` 的 precision 位置限制（须登记进对照表的已知能力边界）：`(precision[,scale])` 紧跟 `dataTypeName_` 之后（`:425`）⇒ `cast(x as timestamp(6) with local time zone)` 不在文法接受面（WI9a 删除前的非 LOCAL 备选同限）。Phase 0 裁定接受 bare 形态并登记，还是扩展文法形态（成本上浮），终裁在 Phase 0。

### DDL 类型映射现状矩阵（std → native 方向为 EC ① 的决定性事实）

- **std → native（DDL 生成方向）：全方言空集**。全部 18 个 dialect XML（`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/`）grep 实测无任何 `stdSqlType="TIME_WITH_LOCAL_TIME_ZONE"` / `"TIMESTAMP_WITH_LOCAL_TIME_ZONE"` 条目。⇒ 枚举落活后、若不补映射，`cast(x as timestamp with local time zone)` 在**所有**方言解析通过、生成期响亮报 `nop.err.dao.dialect.std-data-type-not-supported`（`SqlDataTypeMapping.stdToNativeSqlType` `:80-114` 抛点 `:89`/`:112`；TIME/TIMESTAMP 家族**无降级链**——既有降级只覆盖 TINYINT→SMALLINT→INTEGER `:83-88` 与 VARCHAR→CLOB / VARBINARY→BLOB `:101-110`）。这与 WI9a 差异登记第 1 条（NUMBER→NUMERIC 支持方言空集）完全同构：**roadmap WI9b EC ① 的"真库往返测试通过"在零映射状态下无载体**，必须先裁定并落地至少一个载体的 `<sqlDataType>` 条目（平台对外契约新增，授权形态先例 = WI2 差异登记 #2 / WI9a UUID 四方言映射逐条独立提交）。这是本 plan 相对 roadmap 字面描述的第三处（也是最大一处）工作量修正。
- native → std（元数据读回方向，**不受取消注释影响**的既有事实，登记防误读）：`h2.dialect.xml:238`（native `TIMESTAMP`，alias `TIMESTAMP WITH TIME ZONE` → std TIMESTAMP）、`duckdb.dialect.xml:61`（`TIMESTAMP WITH TIME ZONE`/alias `TIMESTAMPTZ` → TIMESTAMP）、`mysql.dialect.xml:196`（`TIMESTAMP(6) WITH TIME ZONE`，code `DATETIME(6)` → TIMESTAMP）、`oracle.dialect.xml:249`（native `TIMESTAMP`，alias `TIMESTAMP(6) WITH LOCAL TIME ZONE` → TIMESTAMP；该条目来自 2023-12-06 提交 `da039b94ba`）。`postgresql` / `mssql` / `dm` 无任何时区 native 条目（`timestamptz` / `datetimeoffset` 均 grep 0 命中）。⇒ 方言侧对"带时区"的既有立场是**降级映射到 std TIMESTAMP**，不是独立 std 类型；本 plan 新增映射与这些既有条目共存于同一张 `nativeTypes` 表。
- 键冲突机制（Phase 0 裁定映射条目时的硬约束，WI9a §三 G-3 增补裁决的机制先例）：`SqlDataTypeMapping.register`（`:49-62`）以 native 名（`name` + 全部 `alias`，`toUpperCase`）为 `nativeTypes` 键，**同名 put 静默 last-wins 覆盖**（`:55`/`:59`）。⇒ 新条目的 `name`/`alias` 若与 h2/oracle/mysql 既有条目的 alias 撞名（如 oracle 已把 `TIMESTAMP(6) WITH LOCAL TIME ZONE` 占为 alias），会**无信号改写既有 native→std 反查结果**。映射条目裁定必须逐条列出与既有键集的撞名核对。
- `dialect.xdef` 的 `std-sql-type` 域（闸门 B）零声明改动（WI9a 同结论）：新枚举名经 `SimpleStdDomainHandlers.java:1630-1634` / `:1667-1671` 的 `fromStdName` 反查自动放行，`ARG_ALLOWED_NAMES = StdSqlType.getNames()` 自动扩集。

### 迁移影响面与使用面实证（EC ② 的裁定输入）

- **使用面 0 命中（起草时全量 grep 实测，三口径）**：① `git ls-files '*.orm.xml'` = 114 个（19 个生成 `_app.orm.xml` + 95 个手写，与 roadmap 计数一致），其中 `stdSqlType` 取时区值 = **0**；② 全仓 `*.xml`（含 migration.xml / xdef / xmeta / sql-lib / _vfs 资源）取 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE` = **0**（全仓唯一出现处是 `StdSqlType.java` 的注释本体）；③ Java 生产代码引用 = 仅 `StdSqlType.java` 注释。⇒ EC ② 的预期裁定是 **`No migration required`**，理由 = 0 使用实证（无既有部署的任何模型/迁移脚本引用这两个 std 类型，取消注释改变的是"未来可写什么"而非"既有部署已写了什么"）；但按 roadmap `:332` EC ②"不接受留空"，裁定理由必须落档，且 0 命中统计在执行时重跑归档（WI9a G-3 双口径重跑先例）——若重跑非 0，回到 ask-first 升级裁决。
- 既有部署 DDL 映射变化的真实落点（roadmap `:329` "改变既有部署的 DDL 类型映射"的机制拆解，Phase 0 裁定文档须逐条回应）：(a) `getNames()` 扩集改变闸门 A/B 的错误消息与合法取值集（纯增量）；(b) `fromJdbcType(Types.OTHER)` / `fromStdDataTYpe(TIME|TIMESTAMP|DATETIME)` 的潜在静默翻转（见上节，可用裁定消除）；(c) migration.xml 的 `type` / `newType` 属性（`migration.xdef:166` / `:287`，`!std-sql-type` 域）合法取值集扩大（0 使用 ⇒ 纯增量）；(d) 逆向建模路径（`SchemaToOrmModel.java:97`、`JdbcHelper.java:164`）在读到数据库原生时区列时的 std 归类不受影响（native→std 条目不变）。

### `nop-db-migration` 与时区类型的关系（roadmap EC ⑤ 把它纳入 `-pl` 的依据）

- 模块位置 `nop-persistence/nop-db-migration`；DDL 执行器直接消费 `dialect.stdToNativeSqlType`：`AddColumnExecutor.java:116-122`（null type 兜底 VARCHAR `:118`）、`AlterColumnExecutor.java:69-75`；`MigrationHistoryManager.java:226`（BOOLEAN 用例）。模型侧 `_ColumnDefinition.java:80` / `_AlterColumnChange.java:80` 的 `type`/`newType` 字段类型即 `StdSqlType`（xdef 绑定，`migration.xdef:166`/`:287`）。⇒ 时区 std 类型一旦落活，migration.xml 的列定义即可合法引用它并走同一 DDL 生成链；EC ⑤ 的 `-pl` 必跑即回归这条链。
- 真库载体先例：`AbstractMigrationTestCase`（`nop-persistence/nop-db-migration/src/test/java/io/nop/db/migration/AbstractMigrationTestCase.java`）= **H2 in-memory**（`createH2DataSource` + `DialectManager.instance().getDialect("h2")` + `DROP ALL OBJECTS` 隔离）；`nop-dao` 的 `TestDialect extends JdbcTestCase`（`TestDialect.java:52`）是同款真库执行路径先例。**真库 CI 只有 H2**（roadmap EC ⑥/各 WI [未取证] 纪律的前提事实）。

### H2 对 `... WITH LOCAL TIME ZONE` DDL 的支持度：[未取证]

- H2 版本由 `spring-boot-dependencies` 3.5.14 BOM 托管（`nop-kernel/nop-dependencies/pom.xml:27`/`:157-158`；本机仓库缓存含 2.3.232）。H2 对 `TIMESTAMP WITH TIME ZONE` 的支持有方言 XML 侧间接佐证（`:238` 条目是逆向映射登记），但 **`TIME WITH LOCAL TIME ZONE` / `TIMESTAMP WITH LOCAL TIME ZONE` 两种 DDL 形态在 H2 2.x 是否可建表，本 plan 起草时未取得一手证据**（jar 字节探测不可定论：H2 将类型名按 token 组合解析）。按 roadmap 红线（`:465-466` 证据等级纪律），此项标 `[未取证]`，**Phase 0 第 6 项必须在 H2 测试载体上执行 DDL 取得一手结论**，并据此裁定 EC ① 的载体方言与往返测试形态；若 H2 不支持 LOCAL 形态，则"支持"类真库往返在 CI 可达范围内无载体，EC ① 须按"逐个列出不可验证方言 + 替代证据（标注 `[未取证]`）"的 WI5 EC ⑥ 同款纪律收口（候选替代证据形态由 Phase 0 裁定：如 DDL 渲染文本断言 + `getDialectForDataSource` 元数据链路模拟）。

### 既有 EQL 语料与全绿口径（沿用 WI9a 口径）

- 7 个含 `<eql>` 的 `sql-lib.xml`；本 plan roadmap EC ⑤ `-pl`（`:nop-orm-eql,:nop-orm,:nop-dao,:nop-db-migration,:nop-job-dao,:nop-auth-service -am`）能覆盖的必测三份：`:nop-job-dao`（NopJobTask）、`:nop-auth-service`（test）、`:nop-orm`（test）。`:nop-orm-demo` 三份与 `nop-spring-simple-demo` 一份不在 `-pl` 内，由 roadmap 级 EC 第 8 条 + WI0 静态快照兜底。**禁止声称"7 个全测"**。起草时实测：语料无任何时区类型拼写（`cast(` 语料与 `WITH LOCAL TIME ZONE` 全 0 命中），预期快照零 diff（执行时复核）。
- stream 既有失败基线 1 项：`nop-stream-runtime` 的 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 头部红线事实）——"全绿"口径 = 本 plan 相关模块全绿 + 该既有失败与基线一致。该模块经 `:nop-auth-service` 的依赖闭包进入本 plan EC ⑤ 命令的 `-am` 范围。
- **"114 个 orm.xml 全量回归"（EC ③）的执行形态（预写，消除字面不可达，WI9a 同款组合判据）**：带测 `./mvnw clean install -T 1C` 因 stream 既有失败字面不可达 ⇒ 采用组合等效判据：(1) `./mvnw clean install -DskipTests -T 1C`（安装包 + codegen 重生成全链）；(2) roadmap EC ⑤ 字面命令 `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-db-migration,:nop-job-dao,:nop-auth-service -am -T 1C`（orm 全闭包带测的实际执行面）。两条全绿 = EC ③/⑤ 通过；不采用 `-Dtest` 排除单测的豁免口径作为门定义（已知 flake 按 WI9a 先例"方法级排除 + 单跑取证 + 如实记录"处理）。

### WI9b 相邻边界声明（防 scope creep）

- `StdSqlType.java` 注释块中其余成员（`INTERVAL_*` 族 `:44-69`、`NULL`/`ANY`/`SYMBOL`/`MULTISET`/`MAP`/`DISTINCT`/`STRUCTURED`/`ROW`/`CURSOR` 等 `:78-103`、被注释的 `TIME` 变体 `:36-37`）**全部不在本 plan 范围**——roadmap WI9b 只点名两个 LOCAL 时区成员。
- `AT TIME ZONE` / 时区函数语义归 WI12（roadmap `:373`）；本 plan 不做任何时区换算语义。
- 非 LOCAL 的 `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE` 枚举成员不存在于 roadmap WI9b 范围（roadmap 只点名 LOCAL 两个），本 plan 不新增它们（恢复其文法备选的问题在 Phase 0 以"不恢复"为倾向裁定，见上文）。

## Goals

- `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE` 以现构造器形态落为活枚举成员：`fromStdName` 反查可用，闸门 A（`EqlASTBuildVisitor.java:474` 的 `ARG_ALLOWED_NAMES`）与闸门 B（8 个 xdef 的 `std-sql-type` 域，含 `migration.xdef:166`/`:287`）自动放行新取值；三 map 既有不变量（`fromJdbcType(Types.OTHER)`、`fromStdDataTYpe(TIME/TIMESTAMP/DATETIME)`、`fromJavaClass(LocalTime.class)` 等）零静默翻转，有聚焦测试钉死。
- `dataTypeName_` 新增 `TIME WITH LOCAL TIME ZONE` / `TIMESTAMP WITH LOCAL TIME ZONE` 两备选（零 token 增删、重生成产物随文法提交）：`cast(x as time with local time zone)` / `cast(x as timestamp with local time zone)` 解析 → AST → （按 Phase 0 裁定的映射方言）方言情 native 文本全链可用；无映射方言保持生成期响亮报错；非 LOCAL 变体维持删除态（BUG-11 不复活）。
- DDL 映射载体按 Phase 0 裁定落地（逐方言独立提交），EC ① 真库往返测试通过或按 `[未取证]` 纪律登记替代证据；无映射方言的负向响亮报错各有用例（roadmap EC ① 的"支持/不支持"两类齐全，WI9a 同款）。**前提：Phase 0 须裁定出 ≥1 个"支持"载体（候选 (a) 无撞名新键或候选 (c) 定向撞名覆盖）；若载体空集（候选 (b) 全负），本 Goal 不成立，回到 scope 裁决——仅当负责人书面接受零载体收口后，才按 Phase 0 第 6 项预定义的全负形态收口 EC ①。**
- EC ② 迁移裁定落档：预期 `No migration required` + 0 使用实证重跑归档；不接受留空。
- owner doc 同步：`docs-for-ai/02-core-guides/orm-model-design.md`（stdSqlType 类型面 + 时区类型行）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（CAST 类型表 `:266` 行的 WI9b 边界注记回写为落地态 + 新增拼写对照表）。
- 114 个 orm.xml 全量回归门（组合判据）通过；三份必测语料 BC 与 WI0 基线快照逐字一致。

## Non-Goals

- 不新增 `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE`（非 LOCAL）的枚举成员或文法备选；不恢复 WI9a 删除的其余 12 个死备选。
- 不触碰 `StdSqlType.java` 注释块中 INTERVAL 族与其他被注释成员；不新增 `StdDataType` 成员（除非负责人推翻 Phase 0 被拒候选，那属 scope change 须显式裁决）。
- 不改任何既有枚举成员的 `jdbcType`/`stdDataType`/`allowPrecision`/`allowScale`，不改任何既有 `<sqlDataType>` 条目（新条目默认不撞名；定向撞名覆盖须走 Phase 0 第 6 项候选 (c) 单独 ask-first，见 Protected Areas 表）。
- 不实现时区换算语义、`AT TIME ZONE`、`INTERVAL` 语义（WI12/未登记项）；不做类型 lowering/双档方言。
- 不删任何 lexer token；不触碰 `characterSet_`/`collateClause_`（WI2 BUG-15 守卫底线）；不改 `EqlAST.xjava`/`ast/_gen`（无新增 AST 节点/字段；`SqlTypeExpr` 复用现有 `name` 字段）。
- 不引入 `:nop-orm-tdengine` / `:nop-orm-demo` 进 `-pl`（roadmap 级 EC 第 8 条覆盖）；不做真实多数据库 CI。
- 不做 `dm`/`tdengine`/`db2` 的时区映射与取证（无一手文档/不可达，红线 `:465` 纪律）。

## Scope

### In Scope

- `nop-kernel/nop-commons`：`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`（两个时区成员按裁定形态落活；如 Phase 0 裁定需要，`stdTypeMap` 循环后补重钉行）。**单独提交**（回滚单位，WI9a 先例）。
- `nop-persistence/nop-orm-eql`：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_` 新增两备选 → 重生成产物随文法提交。**单独提交**。测试：`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/` 与 `compile/` 下新增/扩展用例（正/负向 + 错误码字面值断言 + 三 map 不变量守护 + WI9a 穷举式用例扩键）。
- 方言 XML（按 Phase 0 裁定清单）：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml` 新增 `<sqlDataType>` 条目，**逐条独立提交、各携该方言正向用例自洽可绿**（WI9a UUID 四方言先例）；不触碰 `dialect.xdef`。
- `nop-db-migration` / `nop-dao` / `nop-orm` 测试：真库往返（H2 载体）与聚焦回归用例。
- 文档：`docs-for-ai/02-core-guides/orm-model-design.md`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档 + 裁决人注记）、`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；以及任何 roadmap 未登记的文法/类型扩展（发现新缺陷 → 登记 roadmap / 新 plan，不顺手修；WI9a 遗留的 `DOUBLE` 拼写缺口等 follow-up 不在本 plan 顺手处理）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **"取消注释"字面不可行，实为"按现构造器形态重写"**：注释体使用已不存在的 `PrecScale`/`SqlTypeFamily`（`StdSqlType.java:38-39`/`:42-43` 实测），且 `Types.OTHER` 取值会静默翻转 `fromJdbcType` 先到先得键（`:174-175`）、`StdDataType.TIME/TIMESTAMP` 取值会静默翻转 `stdTypeMap` last-wins 键（`:177`）。roadmap 未提及；本 plan 把成员形态（`jdbcType`/`stdDataType`/`allowPrecision`/`allowScale` + 不变量保持策略）列为 Phase 0 显式裁定项，不作为可默认的实现细节。
2. **"同步拼写"按事实定性是"新增备选"**：`TIME WITH LOCAL TIME ZONE` / `TIMESTAMP WITH LOCAL TIME ZONE` 在 `.g4` 全历史从未存在（`git log --all -S` 实证），修复前失败形态是解析失败（`nop.err.antlr.*`）而非"解析过 + 语义必拒"。红→绿锚点、对照表行为列、测试形态都按"新增"设计（WI2 BOOLEAN 先例），不按"同步/恢复"设计。
3. **WI9a 已删除非 LOCAL 时区备选，roadmap 文本未更新**：roadmap v7.2（2026-10-03 定稿）早于 WI9a 执行（2026-10-04）；WI9a 删除的 14 个死备选含 `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE`。本 plan 的裁定倾向 = 不恢复（无枚举成员，恢复即 BUG-11 复活）；若负责人推翻并要求非 LOCAL 全家桶，那是 scope change 须显式裁决 + 补枚举成员 + 补映射评估。此条同时是对 WI9a plan Current Baseline"WI9b 边界声明"中"两个名字集互不替"约定的承接与终裁。
4. **EC ① 的载体空集**：roadmap WI9b 未提方言 XML，但 std→native 映射全方言为 0（实测），EC ① 真库往返在零映射下字面无载体。本 plan 增补"方言 `<sqlDataType>` 映射裁定与落地"为 Phase 0/Phase 3 的显式内容（授权形态先例 = WI2 差异登记 #2、WI9a UUID 映射逐条独立提交）；同时登记既有 alias 占位的键冲突机制（`SqlDataTypeMapping.register` last-wins，`oracle.dialect.xml:249` 已占 `TIMESTAMP(6) WITH LOCAL TIME ZONE` alias）为映射裁定的硬约束。
5. **H2 LOCAL TIME ZONE DDL 支持 [未取证]**：真库 CI 只有 H2，若 H2 不支持 LOCAL 形态，EC ① 的"真库往返"须按 WI5 EC ⑥ 同款"逐个列出 + 替代证据 + `[未取证]`"纪律收口；Phase 0 必须一手取证后才能定测试矩阵载体表。
6. **roadmap EC ⑤ 命令的闭包事实**：`:nop-db-migration` 为本 plan 新纳入 `-pl` 的模块（相对 WI9a 命令），其 `-am` 闭包与 `:nop-auth-service` 一道拖入 `nop-stream-runtime` 既有失败基线（1 项，豁免口径见 Current Baseline"既有 EQL 语料与全绿口径"节）。六个 artifactId 均实测存在，命令可运行。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-kernel/nop-commons` 的 `StdSqlType.java`（时区成员落活） | **模型层行为变更**（`stdNameMap` 同时驱动 DDL 类型映射与合法 `stdSqlType=` 取值闸门；`jdbcTypeMap`/`stdTypeMap` 影响元数据读回与逆向建模）。⚠️ `nop-commons` 不是 `nop-*-api`，非 AGENTS.md 字面 Protected Area；按 roadmap 门控矩阵 WI9b 行走「AI Must Ask Before」第 1 条 **ask-first** + 本 plan **自加 plan-first 更严门禁**，不得互相替代 | 裁决人注记（平台负责人经执行指令授权、AI 代拟、人可推翻，落裁定文档）+ 独立子 agent draft review 通过 | 书面裁定记录 + 三 map 不变量聚焦测试 + 影响面逐条回应（Current Baseline"迁移影响面"清单 (a)-(d)）+ 全量回归 |
| `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` 新增备选）→ `EqlParser.java` 等重生成 | **生成管线，plan-first**（AGENTS.md Protected Area"生成管线"；roadmap 门控矩阵 WI9b 行明文"这条是真的"） | 本 plan 即 plan-first 载体，执行前须通过 draft review | owner doc（`orm-model-design.md` + `eql-and-database-compatibility.md`）+ 回归测试（红/绿 + 错误码字面值 + 三份必测 BC） |
| 方言 XML 新增 `<sqlDataType>` 条目（按 Phase 0 裁定） | 平台对外契约新增（G-4 批准范围外） | roadmap WI9b EC ① 原文为授权依据（WI2 差异登记 #2 / WI9a UUID 先例）+ Phase 0 显式 Decision 项；逐条独立提交可 `git revert`；**条目 `name`/`alias` 默认不得与任何方言既有键集撞名**（撞名即静默改写 reverse 映射，必须逐条核对后裁定）；确需撞名的走 Phase 0 第 6 项**候选 (c)**：登记 reverse 映射行为变化 + 该项单独 ask-first | 正/负向用例（期望渲染文本按 `stdToNativeSqlType` 链路推导）+ 键集核对记录（候选 (c) 另附 reverse 映射行为变化登记与单独 ask-first 裁决引用）+ owner doc |
| `nop-persistence/nop-db-migration`、`nop-dao`、`nop-orm-eql` 手写测试 | 普通手写代码 | 无额外门禁 | 回归测试 |

本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 公共接口、`_gen/` 冻结生成物的手改（重生成产物由文法提交携带，不手改）；不删除任何 lexer token；不改 `EqlAST.xjava`/`ast/_gen`；不改既有 `<sqlDataType>` 条目。

## Execution Plan

Phase 0 为前置纯裁定、不产产品代码（其各项是 Phase 1/2/3 的硬前提）；Phase 1（模型层枚举）与 Phase 2（文法新增）相互独立、可分别落地与回滚，各自单独提交；Phase 3（映射 + 真库往返 + 全量门）依赖 Phase 1（方言 XML 的 `stdSqlType=` 取值经闸门 B 校验须枚举先落活）；Phase 4（roadmap 文本勘误，纯文档）为收口前置。顺序建议 Phase 1 → Phase 2 → Phase 3 → Phase 4。每个代码 Phase 开工前 rebase 到当时代码基线并复核锚点符号。

### Phase 0 - 前置裁定

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档，含裁决人注记小节）、`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 与 `dialect-capability-matrix.json`（状态核验，只读）

- Item Types: `Decision`、`Proof`

- [x] 复核 WI0 两件产物状态未回退：语料基线快照（`TestEqlCorpusBaseline` 1/1 PASS，mtime 2026-10-04 18:46 晚于最后代码提交 `cb918dd3cc`；HEAD `8be89b0bc8` 为纯文档提交零代码 delta）与方言矩阵快照（`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0，2026-10-05 实跑）——裁定文档 §〇。
- [x] 重跑使用面 0 命中统计（三口径），结果登记进裁定文档 §一——**全 0 复现**（114 orm.xml / 全仓 xdef/xmeta/xml / Java 生产代码均 0 命中；`WITH LOCAL TIME ZONE` 扩展口径 9 文件全为文档/注释/native 别名）。
- [x] 裁定时区成员形态：**候选 (b) 补偿形态**——`Types.OTHER` + `StdDataType.TIME`/`TIMESTAMP` + `allowPrecision/allowScale=(false,false)` + 循环后三处显式重钉（`jdbcTypeMap.put(OTHER,OTHER)` + `stdTypeMap.put(TIME,TIME)` + `stdTypeMap.put(TIMESTAMP,TIMESTAMP)`）；候选 (a) 2013/2014 经 H2 一手探测**改判拒绝**（两标准码在真实驱动上表示非 LOCAL 保偏移类型，挂给 LOCAL 成员将静默误标 `JdbcHelper:164`/`JdbcMetaDiscovery:346` 读回归类）——裁定文档 §三（含三 map 不变量基线表、被拒候选、裁决人注记）。
- [x] 裁定 EC ② 迁移结论：**`No migration required`**（0 使用三口径实证 + 影响面 (a)-(d) 逐条：纯增量或已被重钉裁定消除）；升级指引一句归 Phase 3 owner doc——裁定文档 §四。
- [x] 裁定拼写与文法形态：① 标准拼写 = 两多词 LOCAL 名（大小写不敏感）；② 文法备选 = bare 多词备选，接受 precision 位置限制并登记（Deferred 段既有）；③ 非 LOCAL 不恢复**终裁**；④ **新增裁定项**：闸门 A 名称提取适配（`getText()` 无分隔符拼接会使多词备选复活 BUG-11 形态，裁定手写 `SqlTypeExpr_name` 多 token 重建归一化）——裁定文档 §五（含对照表定稿列结构 + 草表）。
- [x] 裁定 DDL 映射载体与 EC ① 往返测试设计：① H2 一手取证完成——2.4.240 实测两种 LOCAL 形态**均不可建**（非 LOCAL 2013/2014 可建），`[未取证]` 已改判；② 映射候选三选一 = **候选 (a)**：oracle 单条目 `TIMESTAMP WITH LOCAL TIME ZONE`（零撞名核对 §2.4）；`TIME_WITH_LOCAL_TIME_ZONE` 逐成员全负登记（全方言无宿主，不造降级映射）；候选 (b)/(c) 不触发（各自 ask-first 无需启动）；③ 负向载体 = h2 + 手工最小方言 + oracle（TIME 变体）；④ G-1 评估 = 无版本门张力，dm/tdengine/db2 维持 unverified；⑤ 往返测试设计四层断言链 + EC ① 满足形态 = **选项 2**（不可验证方言 oracle 逐个列出 + 替代证据 + 真实 Oracle 往返 `[未取证]`）——裁定文档 §六。
- [x] 裁定各 Phase 的提交切分：按默认（StdSqlType 单独 / .g4+重生成+闸门A适配单独 / oracle XML 单独 / 文档分离）——裁定文档 §七。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md`，含全部要求项（裁决人注记 §三.5、三口径重跑 §一、成员形态 + 三 map 保持策略 + 被拒候选 §三、EC ② 结论 §四、拼写/文法形态 + 非 LOCAL 终裁 + 对照表列结构 §五、H2 一手取证结论 `[未取证]` 已改判 §二.1、映射载体表 + 三选一结论 §六、G-1 评估 §6.2、往返测试设计 §6.3-6.4、提交切分 §七），以及 **WI9a §九 键集计数勘误移交项**（§八：自记 30→34 系算术误记，实测 30；不重写 WI9a 原文，照 G-3 增补裁决先例落本文档 + Phase 4 roadmap 注记指引）。
- [x] WI0 两件产物状态核验结论已记录（均 `completed`/未回退，裁定文档 §〇）。
- [x] 本 Phase 纯裁定，不改产品代码：`No owner-doc update required`（裁定文档本身即交付物；owner docs 归 Phase 1/2/3）。
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-05.md` 新建）。

### Phase 1 - `StdSqlType` 时区成员落活（模型层，单独提交）

Status: completed
Targets: `nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`、`nop-kernel/nop-commons` 聚焦测试（三 map 不变量 + 反查放行）

- Item Types: `Feature`（时区类型取值集扩展——roadmap WI9b 授权的新增能力，非缺陷修复；承载它的注释体是历史残迹，不算 live defect）、`Decision`（成员形态，Phase 0 已裁定）、`Proof`

- [x] 按 Phase 0 裁定落两个成员：以现 4 参构造器形态写入 `:38/:42` 区（保持 `TIME`/`TIMESTAMP` 相邻的声明序），按裁定补重钉行（Phase 0 §三终裁 = **三处**：`jdbcTypeMap.put(OTHER,OTHER)` + `stdTypeMap.put(TIME,TIME)` + `stdTypeMap.put(TIMESTAMP,TIMESTAMP)`）。**不修改任何既有成员与既有映射条目**；`getNames()` 键集 30 → 32 变化登记进裁定文档附录（§九）。
- [x] 修复前红（gate B 与反查层，本 Phase 的可观测红锚）：`fromStdName("TIME_WITH_LOCAL_TIME_ZONE")` / `("TIMESTAMP_WITH_LOCAL_TIME_ZONE")` 返回 null（`_tmp/wi9b/stdnames-probe.log` 2026-10-05 实测 + 裁定文档 §3.4 基线表）；闸门 B 红锚由接线用例的解析失败形态承载（`TestTimezoneTypeGateBWiring` javadoc 登记 `ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN`）；修复后同输入放行（`TestStdSqlTypeTimezoneMembers` 7/7 绿）。
- [x] 三 map 不变量聚焦测试（`TestStdSqlTypeTimezoneMembers` 7 用例逐条断言）：`fromJdbcType(Types.OTHER)` 仍返回 `OTHER`、`fromJdbcType(2013/2014)` 保持 null（候选 (a) 被拒的直接钉）；`fromStdDataTYpe(TIME/TIMESTAMP/DATETIME)` 与本 Phase 前一致；`fromJavaClass(LocalTime/LocalDateTime/Timestamp)` 不变；`fromStdName` 对两个新名返回裁定成员、对 `INT`/`TEXT`/`NUMBER`/`BOOL`/`UUID` 等既有键仍正确。
- [x] `nop-db-migration` 闸门 B 连通用例（`TestTimezoneTypeGateBWiring` 2/2 绿 + carrier `_vfs/nop/db-migration/wi9b/V1.0.0__tz_column.migration.xml`）：`type="TIMESTAMP_WITH_LOCAL_TIME_ZONE"` 经 xdef `!std-sql-type` 域解析为 `ColumnDefinition.type`（闸门 B 放行）→ `AddColumnExecutor.buildColumnType → stdToNativeSqlType` 链在 h2 上响亮报 `nop.err.dao.dialect.std-data-type-not-supported`（错误码字面值断言）；Phase 3 落 oracle 映射后同链补渲染断言。
- [x] `./mvnw test -pl :nop-commons,:nop-db-migration -am` 通过（nop-commons 358 | 0、nop-db-migration 97 | 0，2026-10-05 实跑）；全量门留待 Phase 3 统一执行。
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-05.md` Phase 1 条目）。

Exit Criteria:

- [x] 两个成员按裁定形态落活，`fromStdName`/闸门 B 的红→绿回归存在且断言具体键名与错误码；无静默跳过。
- [x] 三 map 不变量聚焦测试全绿（逐条断言，非注释性"应当不变"；含 `fromJdbcType(Types.TIMESTAMP)=DATETIME` 历史 first-wins 行为的如实钉死）。
- [x] `getNames()` 键集变化（30 → 32）已实测并登记（修复前 30 实测见裁定文档 §九；落活后 `assertEquals(32, getNames().size())` 断言绿）。
- [x] 接线验证测试通过（闸门 B → `AddColumnExecutor` 链路，非仅 `fromStdName` 单元返回值）。
- [x] `StdSqlType.java` 提交独立可审计（本提交仅含 StdSqlType.java + 两个测试文件 + carrier 资源，无文法/方言/文档逻辑改动）。
- [x] 本 Phase 改变 live baseline：**`No owner-doc update required`（Phase 0 裁定 §四——owner docs 统一在 Phase 3 随最终行为面同步，含 EC ② 结论与升级指引）**。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - `dataTypeName_` 时区拼写新增（.g4，单独提交）

Status: planned
Targets: `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` `:438-446`）→ 重生成产物、`nop-orm-eql` 测试、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`（新拼写入口——LOCAL 变体从未存在，非 BUG-11 型缺陷修复）、`Proof`

- [x] 开工前置核验：确认无并行 WI 的 `.g4`→`EqlParser` 重生成债未清零（已知在途项：WI7 Phase 1 `09fa140ff5` 落地时其重生成产物显式欠账"随下一次提交"——若该债未清，先协调清账，或确认其将随本 plan 文法提交一并清偿并在重生成 diff 审计中分账登记；否则本 Phase 的重生成 diff 审计会把并行 WI 的欠账 diff 误记为本 plan 改动）。结论登记进提交说明或日志。**（2026-10-05 提前核验完成：债已清零——WI7b 重生成产物随 `7048cf1474` 提交、WI8 两个文法提交各自携带重生成产物、工作区无 `.g4`/`EqlParser` 未提交改动；裁定文档 §〇）**
- [ ] 修复前红：`cast(x as time with local time zone)` / `cast(x as timestamp with local time zone)` 断言解析失败（错误码字面值：`nop.err.antlr.*` 族，Phase 0 裁定文档记录具体码与 offending token）——红证据留痕；同时断言非 LOCAL 拼写（`time with time zone`）维持 WI9a 收窄后形态不变（不复活）。
- [ ] 按 Phase 0 裁定形态给 `dataTypeName_` 新增两备选（bare 多词形态为倾向）；**不删除任何 lexer token**、不触碰 `characterSet_`/`collateClause_`/`sqlDateTimeLiteral`/`sqlIntervalExpr` 等共用 token 规则；文法改动**单独提交**，重生成产物随该提交。
- [ ] 重生成 diff 审计：确认仅 `EqlParser.java`/`Eql.interp` ATN 变化、token 表零增删（`WITH`/`LOCAL`/`ZONE` 均既有 token）、`ast/_gen`/`EqlLexer*`/`*.tokens`/`_EqlASTBuildVisitor.java` 零 diff（WI2/WI9a 同口径先例）；偏差即停，按生成管线 plan-first 重新裁定。
- [ ] 修复后绿（端到端，Anti-Hollow 主证据）：两个 LOCAL 拼写在裁定映射方言上解析 → 闸门 A → 生成器 → 断言具体方言情 native 文本（期望文本按 Phase 0 载体表取值）；无映射方言解析过、生成期响亮报 `nop.err.dao.dialect.std-data-type-not-supported`（错误码字面值断言）；`nosuchtype` 语义拒绝与 `ARG_ALLOWED_NAMES` 含新键断言存活。
- [ ] WI2/WI9a 回归底线存活的显式断言：`TestEqlCastBooleanParse`（`nosuchtype` + BOOLEAN 文本）、`TestEqlSqlTypeClauseGuard` 全部用例不改动且通过；`TestEqlCastTypeNarrowingParse` 的 stdNameMap 穷举式用例随键集 30 → 32 扩展（"文法放行 ⇒ 语义必过或语义响亮拒"证明覆盖新成员）。
- [ ] 对照表定稿并落档（Phase 0 第 5 项定稿列结构的实测终稿）：新增拼写行 + 非 LOCAL 维持删除态注记行 + precision 位置限制注记；回写 `eql-and-database-compatibility.md:266` 的 WI9b 边界注记为落地态。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [ ] 红→绿断言按裁定落地：修复前 `nop.err.antlr.*` 解析失败、修复后端到端可用；错误形态迁移逐项在对照表登记（错误码字面值级）。
- [ ] 新增后文法接受集 = `stdNameMap` 键集 ∪ `sqlIdentifier_` 兜底，且 32 键穷举式证明"文法放行 ⇒ 语义必过或语义响亮拒"，无"文法放行 + 静默错误 SQL"路径。
- [ ] 非 LOCAL 变体维持删除态有显式断言；WI2/WI9a 底线全部存活。
- [ ] `.g4` 提交独立可审计（`git log` 中文法提交不含逻辑改动）；重生成 diff 符合本 Phase 第 4 项口径（token 零增删）。
- [ ] 对照表定稿落档（owner doc + 裁定文档），含 `:266` 行回写。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-db-migration,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（口径见 Current Baseline"既有 EQL 语料与全绿口径"节，stream 既有 1 项失败豁免）。
- [ ] 既有 EQL 语料 BC：三份必测与 WI0 基线快照逐字一致（`TestEqlCorpusBaseline` 通过）；不声称"7 个全测"。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - DDL 映射落地 + 真库往返 + 全量收口（映射逐方言独立提交）

Status: planned
Targets: 按 Phase 0 裁定的 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`、`nop-dao`/`nop-db-migration` 测试、`docs-for-ai/02-core-guides/orm-model-design.md`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Feature`（映射条目——EC ① 载体）、`Proof`（真库往返 + 全量门）、`Decision`（若执行中需偏离 Phase 0 载体表，先改裁定文档再改代码）

- [ ] 按 Phase 0 裁定清单落 `<sqlDataType>` 条目：**逐方言独立提交、各携该方言正向渲染断言自洽可绿**（WI9a UUID 先例）；每条目提交说明含与既有 `nativeTypes` 键集的撞名核对结论（候选 (c) 撞名条目另附 reverse 映射行为变化登记与该项单独 ask-first 裁决的引用）。**全负裁定（候选 (b)）成立时清单为空集，本项转为对空集理由落档的核验**（含候选 (a)/(c) 被拒依据与 scope 裁决书面记录）。不触碰 `dialect.xdef`；不改既有条目。
- [ ] EC ① 真库往返测试（H2 载体，`AbstractMigrationTestCase`/`JdbcTestCase` 先例）：DDL 生成（`stdToNativeSqlType` 渲染文本断言）→ 真库建表 → JDBC 元数据读回 → std 归类比对，按 Phase 0 定稿的断言链执行并通过；H2 取证结论若为"不可建 LOCAL 形态"，按裁定落替代证据形态并把 H2 列入不可验证清单（`[未取证]` 纪律，逐方言列出，不用"响亮失败"类定性词替代字面证据）。
- [ ] 负向响亮报错回归：无映射方言（真方言如 mssql/postgresql 或手工最小方言，按 Phase 0 载体表）解析过、生成期断言 `nop.err.dao.dialect.std-data-type-not-supported`；跨方言支持面差异用例（约束 4"不等价"类）。
- [ ] `nop-db-migration` 全链回归：Phase 1 第 4 项的接线用例翻绿（渲染断言）或按裁定保留响亮报错形态并登记理由；`nop-db-migration` 既有测试套件零回归。
- [ ] 约束 4 一致性用例：同一 std 类型在同方言的等价拼写形态输出逐字一致（如适用；bare 形态唯一时登记"无同义拼写对"的理由）。
- [ ] **EC ③ + EC ⑤ 全量门**（执行形态 = Current Baseline"既有 EQL 语料与全绿口径"节的组合判据）：`./mvnw clean install -DskipTests -T 1C` 通过 **且** `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-db-migration,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（stream 既有 1 项失败与基线一致不判失败；已知 flake 按"方法级排除 + 单跑取证 + 如实记录"处理，不作为门定义的豁免口径）；114 个 orm.xml 影响清单以 Phase 0 第 2 项重跑结果归档（预期空清单；非空则回到 ask-first）。
- [ ] owner doc：`orm-model-design.md`（stdSqlType 类型面新增时区行 + EC ② 迁移结论登记 + 使用指引）；`eql-and-database-compatibility.md`（CAST 类型接受面/映射载体表 + 能力门登记如适用）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [ ] 每个"支持"载体方言有具体 native 文本正向断言（期望值按 `stdToNativeSqlType` 链路推导，非直觉填）；每个"不支持"载体有错误码字面值负向断言；无静默降级/改写。（全负裁定成立时，前半句按空集理由落档核验替代；负向断言要求不变。）
- [ ] EC ① 真库往返测试通过，或不可验证方言逐个列出并带 `[未取证]` 标注的替代证据，或（全负裁定成立时）按 Phase 0 第 6 项预定义全负形态收口（裁定文档登记），三选一按裁定执行。
- [ ] EC ② 迁移结论已落 owner doc（`No migration required` + 理由，或升级 SQL 清单），不留空。
- [ ] EC ③ 组合判据两命令通过，影响清单归档。
- [ ] 提交纪律可审计：`StdSqlType.java` 提交、`.g4` 提交、各方言 XML 提交相互分离且逐条可 `git revert`。
- [ ] owner doc（`orm-model-design.md` + `eql-and-database-compatibility.md`）已落档。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - roadmap WI9b 文本勘误（收口前置，纯文档）

Status: planned
Targets: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI9b 节 `:327-332`）

- Item Types: `Fix`（roadmap 文本与 live 事实不符的勘误，照 WI10a/WI10b 的 roadmap 原地注记先例）、`Proof`

- [ ] 在 roadmap WI9b 节加**带日期的原地勘误注记**（不重写原文）："取消注释"定性实为"按现构造器形态新增枚举成员"（注释体 `PrecScale`/`SqlTypeFamily` 已废弃，字面取消注释不可编译）；"同步 `dataTypeName_` 拼写"实为"新增 LOCAL 备选（全历史从未存在）"；非 LOCAL 变体不恢复裁定。注明依据 = 本 plan Current Baseline 实测与差异登记第 1-3 条。
- [ ] 同一注记内登记 WI9a 裁定文档 §九 键集计数的勘误指引（指向本 plan Phase 0 裁定文档的勘误移交项）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 勘误注记已落 roadmap WI9b 节且含日期与依据指引；原文未重写（`git diff` 仅注记增量）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [ ] roadmap WI9b EC ①：时区类型的 DDL 生成真库往返测试通过（H2 载体），或不可验证方言逐个列出并按 `[未取证]` 纪律登记替代证据，或（全负裁定成立时）仅"不支持"类负向用例齐全且 DDL 映射增补裁定为空集的理由已落档——三选一按 Phase 0 第 6 项裁定与 Phase 3 执行结果核对；全负分支须附 Goals 第 3 条的 scope 裁决书面记录。
- [ ] roadmap WI9b EC ②：迁移结论显式落档——`No migration required` + 0 使用实证重跑归档，或升级 SQL 清单；不留空。
- [ ] roadmap WI9b EC ③：114 个 orm.xml 全量回归通过（组合判据两命令，执行形态见 Current Baseline 专节）且影响清单已归档。
- [ ] roadmap WI9b EC ④：owner doc 已同步（`orm-model-design.md` + `eql-and-database-compatibility.md`，含 `:266` 边界注记回写与新拼写对照表）。
- [ ] roadmap WI9b EC ⑤：`./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-db-migration,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（含 stream 1 项既有失败豁免口径）。
- [ ] roadmap WI9b EC ⑥：既有 EQL 语料 BC 三份必测与 WI0 基线快照逐字一致；未声称"7 个全测"。
- [ ] roadmap WI9b EC ⑦：独立 closure audit 由**不同 `task_id`** 的子 agent 执行，evidence 写入本 plan `Closure` 段（guide #26/#27）；roadmap Work Item Status 的 WI9b 行据此才可 `todo` → `done`（WI9b 现状 `todo` `:92`；按 roadmap 状态流转，执行前主会话先翻 `planned`，本门以现状 `todo` 记起点、保留勘误注记即可）。
- [ ] 三个 Protected Area 的提交纪律可审计：`StdSqlType.java` 提交、`.g4`+重生成提交、方言 XML 逐条提交相互分离；无 lexer token 删除；无手改生成物；无既有 `<sqlDataType>` 条目被修改（`git diff` 审计）。
- [ ] 三 map 不变量（`fromJdbcType`/`fromStdDataTYpe`/`fromJavaClass` 既有结果零翻转）聚焦测试在 closure audit 复跑通过。
- [ ] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（若方言 XML 有新增条目，按 WI9a 先例核对快照口径是否需同步并记录结论）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 新增 broken link = 0（3 项既有 warning 基线不动：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，roadmap EC 第 6 条口径）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/32-eql-wi9b-tz-types.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中（时区类型是"改接受面"变更，anti-hollow 抓"只放行不设门/只落枚举无生成证据"的半实现形态；`nop-commons` 侧改动随三 map 聚焦测试与接线用例覆盖，closure audit 抽查）。
- [ ] Anti-Hollow Check：closure audit 已验证（a）`cast(x as timestamp with local time zone)` 从 EQL 文本 → 闸门 A → 生成器 → 方言文本（或响亮报错）的端到端路径有测试；（b）闸门 B → `AddColumnExecutor` 的 DDL 生成链有接线证据；（c）无空方法体/静默跳过/no-op 作为"已落地"。
- [ ] `./mvnw clean install -DskipTests -T 1C` 通过（codegen 重生成全链 + 安装包，组合判据第 1 条）。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-dao,:nop-db-migration,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（组合判据第 2 条 = EC ⑤）。
- [ ] checkstyle / 代码规范检查通过（imports 分组等仓库惯例）。

## Deferred But Adjudicated

### 非 LOCAL 时区类型（`TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE` 全家桶）

- Classification: `out-of-scope improvement`（roadmap WI9b 只点名 LOCAL 两成员；非 LOCAL 无枚举成员，Phase 0 终裁不恢复其文法备选——恢复即 BUG-11 死备选复活）
- Why Not Blocking Closure: roadmap WI9b 范围字面不含非 LOCAL；WI9a 刚收口的 BUG-11 收窄与其直接冲突；若未来需要，前置是新增枚举成员 + 方言映射评估（工作量等同本 plan 量级），属独立 scope change。
- Successor Required: `no`（除非 Phase 0 被负责人推翻——推翻时转为显式 scope change 并登记新 successor）
- Successor Path: roadmap 登记后入后续批次（当前无登记，不允许名义 owner）

### `sqlTypeExpr` 的 precision 位置限制（`timestamp(6) with local time zone` 形态不可解析）

- Classification: `watch-only residual`（WI9a 删除前的非 LOCAL 备选同限，非本 plan 引入的回归）
- Why Not Blocking Closure: bare 形态（`timestamp with local time zone`）已满足 EC ① 的类型表达；precision+时区组合拼写无任何语料/模型使用（Phase 0 重跑口径覆盖）。
- Successor Required: `no`
- Successor Path: 若出现真实需求，随 roadmap 新登记项评估文法形态扩展

## Non-Blocking Follow-ups

- `TestEqlCastBooleanCompile` javadoc 中"db2 不可实例化"过时注释（WI9a 遗留 follow-up，指名"WI9b 或下次触碰该文件时顺带更正"）：本 plan 若在 `parse/`/`compile/` 测试目录新增文件且该文件随 PR 触碰，顺带更正；否则维持登记。
- WI9a 遗留 `DOUBLE` 拼写缺口（`ARRAY`/`ANY` 同族）：roadmap 未登记的文法扩展，本 plan 不顺手修（Out Of Scope）。
- 既有 alias 占位（`oracle.dialect.xml:249` 等）与新增映射条目在 `nativeTypes` 键集上的长期治理（撞名静默覆盖机制的全局防线）：属 `nop-dao` 契约治理，非本 plan 范围；Phase 0 的逐条核对记录是本 plan 交付的局部防线。
- roadmap Cross-Cutting 的 CI `git diff --stat` 阈值告警 carrier 待定（WI0 Non-Blocking Follow-up 延续）：本 plan 以"提交切分 + 重生成 diff 审计口径"作为过程替代。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent（不同 task_id）>>
- Audit Session: <<session ID>>
- Evidence:
  - <<每条 Exit Criterion 的验证结果（PASS/FAIL + 对应 live code path 或 test name）>>
  - <<每条 Closure Gate 的验证结果>>
  - <<`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/32-eql-wi9b-tz-types.md --strict` 退出码>>
  - <<Anti-Hollow 检查结果 + `scan-hollow-implementations.mjs` 退出码>>
  - <<Deferred 项分类检查：确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
