# 06 WI4 窗口函数方言登记缺口修复（PG 系）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI4 行、R5、§3.3、跨 roadmap 去重规则）
> Related: `ai-dev/plans/nop-stream-sql/04-wi1-eql-window-grammar-ast.md`、`ai-dev/backlog/duckdb-integration-roadmap.md`（WI8 基线协调）
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

修复既有方言登记缺陷（R5）：PostgreSQL 系方言（postgresql 及其继承者 postgis、duckdb）未继承 window-expr-support.dialect.xml，10 个标准窗口函数（rank/dense_rank/row_number/lead/lag/first_value/last_value/nth_value/percent_rank/cume_dist）在 PG 方言下编译为 `ERR_EQL_UNKNOWN_FUNCTION`。修复后 PG 系方言可编译窗口函数。属 `Fix`（既有缺陷，非新方言测试）。

## Current Baseline

- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/window-expr-support.dialect.xml`：登记 10 个窗口函数，均 `onlyForWindowExpr="true"`；被 db2/dm/h2/mssql/mysql/oracle 六方言直接 extends（实测）。
- `postgresql.dialect.xml:3` `x:extends="default.dialect.xml,geo-support.dialect.xml"`——不含 window-expr-support；postgis extends postgresql；duckdb extends postgresql → 三者皆缺。default.dialect.xml（约 47 函数）与 geo-support.dialect.xml（全 st_*）与 10 窗口函数名**零交集**（审查实测），x:extends 按函数名合并、顺序不敏感。
- 校验点 `EqlTransformVisitor.visitSqlRegularFunction`（:1372-1383）：`dialect.getFunction(name) == null` → `ERR_EQL_UNKNOWN_FUNCTION`（OrmEqlErrors.java:171）；`onlyForWindowExpr` 函数在窗口表达式外使用 → `ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR`（:1378-1383）。函数名匹配经 `DialectImpl.java:75` CaseInsensitiveMap，大小写不敏感。
- **测试落点约束（审查 B1 实测）**：EQL 编译链（EqlCompiler/EqlTransformVisitor/AstToSqlGenerator）全在 nop-orm-eql，而 nop-dao 的 pom 只依赖 nop-core + nop-xlang，nop-orm-eql 反而依赖 nop-dao——给 nop-dao 加 test 依赖即 Maven 循环。编译级测试必须落 **nop-orm-eql** 测试目录。
- **真实方言加载路径（审查 M2 实测）**：`DialectManager.instance().getDialect("postgresql")` 经 ResourceComponentManager + DialectModelLoader 加载真实 dialect.xml，需 `CoreInitialization.initialize()`（nop-orm-eql 测试先例 `TestSqlExprTransformHelper:25`）。**反模式警告**：`TestEqlCompileSql` 用程序化合成 DialectModel（其 :53 注释明言测试环境未初始化 dialect.xml 加载器）——本计划禁止合成方言（会与被修文件脱钩形成空心验证）。
- **实体上下文约束（审查 M1 实测）**：EQL 编译对 from 实体做严格元数据解析，裸表名抛 `ERR_EQL_UNKNOWN_ENTITY_NAME`——用例须配最小实体模型（`TestEqlCompileSql` 的 entity()/TestCompileContext 模式）。
- TestPostgreDialect（nop-dao）为 docker opt-in；`TestDialect.java:193-196` 对 onlyForWindowExpr 且无 testSql 的函数跳过——新增 10 函数不会使存量 opt-in 测试变红。**Docker 在本执行环境不可用**（未验证），按 D15 走降级标注。
- 跨 roadmap 基线（审查 M5 实测）：`ai-dev/backlog/duckdb-integration-roadmap.md:43` WI8 以「继承链 default 49 ∪ postgresql 8 ∪ duckdb 覆盖 9 ≈ 50 净函数」为验证基线——本修复使 postgresql 链净增 10，该基线漂移须显式协调。

## Goals

- postgresql.dialect.xml 的 `x:extends` 增加 window-expr-support.dialect.xml → postgresql/postgis/duckdb 三方言经继承获得 10 个窗口函数。
- 新增**真实方言**编译级回归测试（落 nop-orm-eql）：CoreInitialization + DialectManager 加载 postgresql/postgis/duckdb 三个真实方言，断言 10 函数 getFunction 非空且 onlyForWindowExpr；以 TestEqlCompileSql 的最小实体上下文断言 `rank() over (order by o.id)` 类查询在 PG 方言可编译为 SQL；断言窗口函数在 OVER 外使用抛 `ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR`。
- 跨 roadmap 基线协调：当日日志记录 +10 漂移；duckdb-integration-roadmap WI8 行追加漂移括注（不改其判定语义，由该 roadmap 自行复核数字）。
- D15 降级标注：docker opt-in 的 TestPostgreDialect 无法在本环境实测，交付说明（roadmap WI4 括注 + 日志）逐项标注「未实测：TestPostgreDialect docker opt-in 存量回归，依赖 193-196 行跳过逻辑分析不受影响」，满足 D15「禁止无标注收口」。

## Non-Goals

- 不改 window-expr-support.dialect.xml 与其余 6 个已继承方言；不改 duckdb/postgis 文件本身。
- 不修 es/tdengine 同类缺口（roadmap §3.3 列 5 个方言缺登记，本计划完成判定只覆盖 PG 系三个；es/tdengine 残留记录于 Non-Blocking Follow-ups，豁免理由：完成判定未含、duckdb roadmap WI8 拥有方言函数验证所有权、WI5 文档校正时统一口径）。
- 不做 STDDEV/VAR 等聚合补充登记（归 WI2/WI3 如需）；不写 `<features>` 能力位（WI2）；不做多库实跑矩阵（WI3）。
- owner doc：eql-and-database-compatibility.md 的窗口口径宽宣示（:34）属 WI5 校正范围，本计划不重复处理。

## Scope

### In Scope

- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/postgresql.dialect.xml`（仅 x:extends 一行）
- 新增编译级回归测试（**nop-orm-eql** 测试目录，真实方言加载 + 最小实体上下文）
- `ai-dev/backlog/duckdb-integration-roadmap.md` WI8 行追加基线漂移括注（最小改动）
- roadmap WI4 状态行 + 括注（含 D15 未实测标注）；当日日志；plan 与收口

### Out Of Scope

- duckdb/postgis/其它方言文件；dialect.xdef；WI1/WI2/WI3/WI5 的内容；es/tdengine 修复。

## Execution Plan

### Phase 1 - 红：真实方言编译级回归测试先行

Status: completed
Targets: nop-orm-eql 测试（新增 TestPostgresWindowFunctionDialect 或并入既有方言相关测试类）

- Item Types: `Proof`

- [x] 新增测试（CoreInitialization.initialize() + DialectManager.instance().getDialect）：TestPostgresWindowFunctionDialect 3 用例——testWindowFunctionsRegistered（三方言 × 10 函数非空且 onlyForWindowExpr）、testRankOverCompilable（最小实体上下文 + EQL→SQL 编译产出含 rank()）、testRankOutsideWindowExprFails（OVER 外抛 ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR）
- [x] 修复前运行确认红：3 用例 2 Failures + 1 Error，全部因 unknown-function（nop.err.eql.unknown-function）——正是被修缺陷

Exit Criteria:

- [x] 修复前新用例红（unknown-function，与被修缺陷一致）
- [x] 测试使用真实 dialect.xml 加载（非合成 DialectModel），覆盖 PG 系三方言

### Phase 2 - 修复与转绿

Status: completed
Targets: postgresql.dialect.xml

- Item Types: `Fix`

- [x] postgresql.dialect.xml `x:extends` 增加 window-expr-support.dialect.xml（单行修改）
- [x] Phase 1 用例转绿（3/3）；`./mvnw test -pl nop-persistence/nop-orm-eql -am` 全绿 83 测试（80 既有 + 3 新增；-am 连带 nop-dao 资源变更，无 -am 时本地仓库旧快照 jar 会掩盖修复——已实测踩坑并记录日志）
- [x] duckdb-integration-roadmap WI8 行追加漂移括注（postgresql 链 +10 函数，基线数字待该 roadmap 复核）
- [x] owner doc 裁定：No owner-doc update required（窗口函数口径宽宣示校正归 WI5；本修复不改变已登记函数的语义面）

Exit Criteria:

- [x] 修复后 PG 系三方言 10 窗口函数 getFunction 非空（测试证明）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 绿（83 测试含既有不回归）
- [x] onlyForWindowExpr 语义不回归（OVER 外使用抛 ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR，测试证明）
- [x] duckdb roadmap WI8 行漂移括注已加
- [x] No owner-doc update required（理由：口径校正归 WI5）
- [x] ai-dev/logs/ 当日条目更新

### Phase 3 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：核验修复实际生效（审计员实测红→绿闭环：还原修复行 3 用例 2F+1E 全 unknown-function，恢复后 3/3 绿）、三方言继承链、测试使用真实方言与实体上下文、回归结果、duckdb 括注与 D15 标注；裁定 PASS；证据落 ai-dev/audits/nop-stream-sql/wi4-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm-eql --severity high` 退出码 0（执行与 audit 双重复核）
- [x] audit 通过后 roadmap WI4 `todo` → `done`（括注含 D15 标注、内容无任何圆括号字符）；解析器翻转后实测 items 31 milestones 7
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] scan-hollow 高危零发现；roadmap WI4 = done（含 D15 标注），解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0

## Closure Gates

- [x] PG 系三方言（postgresql/postgis/duckdb）经继承获得 10 个窗口函数（真实方言加载实测）
- [x] onlyForWindowExpr 语义不回归
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 绿（83 测试）
- [x] 无静默跳过、无空壳实现
- [x] duckdb roadmap 基线漂移已协调（括注 + 日志）；D15 未实测项已标注（禁止无标注收口）
- [x] owner doc 裁定已记录（No owner-doc update required + 理由）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，红→绿闭环实测）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/06-wi4-pg-window-function-registration.md --strict` 退出码 0

## Deferred But Adjudicated

### TestPostgreDialect docker opt-in 存量回归

- Classification: `watch-only residual`
- Why Not Blocking Closure: Docker 在本执行环境不可用（未验证）；TestDialect.java:193-196 对 onlyForWindowExpr 且无 testSql 的函数跳过实跑断言，新增 10 函数经该逻辑分析不影响存量测试结果；编译级行为已由新增真实方言测试覆盖；D15 允许降级但须逐项标注——已在 roadmap WI4 括注与本 plan 标注
- Successor Required: `no`
- Successor Path: WI3 实跑矩阵（docker opt-in 可用时统一覆盖）

## Non-Blocking Follow-ups

- es/tdengine 同类登记缺口：完成判定未含，es/tdengine 缺口由本 roadmap §3.3 登记、留待后续 WI 认领（duckdb roadmap WI8 仅拥有 duckdb 方言的函数验证所有权）；WI5 文档校正时统一口径。

## Closure

Status Note: 既有方言登记缺陷修复完成——postgresql extends 链接入 window-expr-support，PG 系三方言获得 10 个窗口函数；红→绿闭环经审计员独立实测复现；真实方言加载回归与 -am 全量 83 测试全绿；duckdb 基线漂移已协调、D15 未实测项已标注。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi4-closure-audit.md
- Evidence:
  - 红→绿闭环：审计员临时还原修复行复现 2F+1E 全 unknown-function，恢复后 3/3 绿（测试非空转）
  - 测试真实方言加载（CoreInitialization + DialectManager），PG 系三方言 × 10 函数 + onlyForWindowExpr 守卫
  - 未越界：diff 全集 = postgresql.dialect.xml 1 行 + 新测试 + duckdb WI8 括注 + 日志 + plan
  - nop-orm-eql -am 全量 83 绿；scan-hollow 全路径 0 findings；check-doc-links --strict 退出码 0
  - D15 标注与 duckdb 基线协调齐备（审计逐项核验）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/06-wi4-pg-window-function-registration.md --strict` 退出码 0

Follow-up:

- 见 Non-Blocking Follow-ups；TestPostgreDialect docker 实跑归 WI3 统一覆盖
