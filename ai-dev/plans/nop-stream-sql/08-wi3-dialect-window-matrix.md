# 08 WI3 方言窗口能力实跑矩阵

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI3 行、D15 裁定、D3 分层原则、§3.3）
> Related: `ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md`（D15）、`ai-dev/plans/nop-stream-sql/07-wi2-window-codegen-dialect-flags.md`（三能力位）
>
> 审查修订记录：经独立审查一轮修订——B1 补录 WI2 测试联动；M2 增 named-window 无 frame 对照轴；M3 矩阵枚举全部 18 文件；M4 增真实 h2 编译链端到端用例；M5 补 docker 降级触发证据。
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

产出 frame 三单位（ROWS/RANGE/GROUPS）乘命名窗口（inline / named）的支持矩阵，并据此填各 dialect.xml 的 features 值。Docker 可用性执行前实测：**docker info 退出码 0（daemon 运行中，2026-10-02 实测）**——D15 主路径部分可用。按 D15「可用时实跑」：**H2 真实执行 + PostgreSQL Testcontainers 实跑**（opt-in，注意容器版本 jdbc:tc:postgresql:9.6.12 较老——PG 11 才支持 GROUPS，矩阵如实记录版本与结论）；MySQL/Oracle/MariaDB/MSSQL 镜像重、非本轮目标，维持快照比对 + 逐方言标注未实测，禁止无标注收口。

## Current Baseline

- 三能力位（WI2 交付）：`supportWindowFrameRows`/`Range`/`Groups`，default.dialect.xml 集中缺省 false，仅 fixture 测试方言开启过（未填任何真实方言）。
- 方言文件清单（实测 18 个）：独立可加载的完整方言 db2/dm/duckdb/es/h2/h2gis/mariadb/mssql/mysql/mysql5.7/oracle/postgis/postgresql/tdengine + 基文件 default/geo-support/window-expr-support + oracle-reverse + selector 目录。window-expr-support（窗口函数登记）直接 extends 为 7 个（db2/dm/h2/mssql/mysql/oracle/postgresql），**传递面 12 个**（+mariadb、mysql5.7 经 mysql；h2gis 经 h2；postgis、duckdb 经 postgresql，WI4 后）。
- **H2 实跑联动面（审查 B1 实测）**：`TestDefaultDialectWindowFeatures`（WI2 交付）断言真实 h2 三能力位全 false 且 fixture（extends h2）Range/Groups false——h2 features 填 true 必然打破该测试，其更新必须列入本计划 Scope；「集中缺省 false」语义检查迁至不受影响载体（合成方言路径已覆盖）。
- **H2 实跑载体（实测）**：`JdbcTestCase.createDataSource()` 默认 `jdbc:h2:mem:`（:109-113），无 docker 依赖；`TestDialect` 即 H2 基类，docker 系子类才覆写数据源。nop-dao 测试可用真实 H2 执行任意 SQL。
- 编译链方言覆盖（WI4 实测）：nop-orm-eql 测试内可经 `CoreInitialization + DialectManager` 加载全部真实方言并走 EqlCompiler 全链（nop-dao 在 -am 反应堆内资源即最新）。
- DuckDB 跨 roadmap 所有权：`ai-dev/backlog/duckdb-integration-roadmap.md` WI8 拥有 duckdb 的函数验证与错误语义；WI3 若涉及 duckdb 只写 features 值不重复记账。
- es/tdengine：排名族窗口函数登记缺口未修（WI4 范围外）；聚合 over 编译不受影响（SqlAggregateFunction 不走方言登记），执行未实测。
- 已知不确定（roadmap §3.3 显式标注，未在本仓库验证）：Oracle 无 GROUPS、MySQL 8 对 GROUPS 的处理——本矩阵 H2 实跑可证 H2 侧行为，其余方言不臆断。

## Goals

- H2 真实执行矩阵：`{rows, range, groups} × {inline frame, named window}` 共 6 组查询在内存 H2 上真实执行（建表 + 数据 + 查询 + 结果断言），记录每组通过/失败；**第 7 组对照查询：named window 无 frame**（`sum(val) over w ... window w as (partition by grp order by id)`）——H2 对 WINDOW 子句语法的支持是独立于 frame 单位的轴，失败归因不得记到 frame 头上（M2）。
- **真实 h2 方言编译链端到端（M4）**：CoreInitialization + DialectManager.getDialect("h2") + EqlCompiler 编译 frame 矩阵查询（TestPostgresWindowFunctionDialect 先例），并把生成 SQL 打回 H2 数据源执行——把 DB 能力证明与编译链接成端到端 Proof。
- **D15 降级触发证据（M5）**：实跑 `docker info` 记录结果（预期不可用），写入矩阵文档与当日日志，作为降级分支的触发证据而非未验证假设。
- 据 H2 实跑结果填 `h2.dialect.xml` features 值（通过的单位置 true，失败的保持 false 并在矩阵记录失败形态）
- 据 PostgreSQL 实跑结果填 `postgresql.dialect.xml` features 值（opt-in 容器实测；GROUPS 按 PG 11 前不支持的历史口径预期 false，以实跑为准；postgis/duckdb 经继承同值并在矩阵注明）。
- 其余独立方言（db2/dm/duckdb/mariadb/mssql/mysql/mysql5.7/oracle/postgis/h2gis）：SQL 快照比对（postgresql 升级为实跑）——EqlCompiler 全链产出矩阵查询的方言 SQL（快照文件入库），验证生成成功且语法形态正确；**features 一律保持 false**（无实跑证据不启用），逐方言标注「未实测」。
- 矩阵落档 `ai-dev/design/nop-stream/sql-window-dialect-matrix.md`（未来交付物）：矩阵行**枚举全部 18 个 dialect.xml + selector 目录逐文件裁定**（填值 / 显式 false+未实测 / 基文件不适用 / 反向工具文件不适用，M3）；含 H2 实跑证据（含第 7 组对照轴）、快照清单、D15 未实测标注、docker 触发证据。

## Non-Goals

- 不为 PG/Oracle/MySQL 等开启 features（无实跑证据；docker 可用后由后续复核开启）。
- 不验证窗口函数（rank 等）的执行语义（WI4 已登记编译面；函数实跑同属 docker 范围）。
- 不修 es/tdengine 登记缺口；不重复 duckdb WI8 的函数验证记账。
- 不改 AstToEqlGenerator/EqlTransformVisitor（WI2 已交付）。

## Scope

### In Scope

- nop-dao 测试：新增 H2 实跑矩阵测试类（JdbcTestCase 模式）
- nop-orm-eql 测试：新增方言 SQL 快照生成测试（真实方言加载 + 矩阵查询编译 + 快照断言）与真实 h2 编译链端到端用例（M4，落点 nop-orm-eql——nop-dao 无 nop-orm-eql 依赖且 Maven 禁循环）
- `nop-persistence/nop-orm-eql/pom.xml`：新增 h2 test 依赖（版本走既有 dependencyManagement，无版本号声明，与 nop-dao 同法；M4 配套——nop-dao 的 h2 为 test scope 不可传递）
- `TestDefaultDialectWindowFeatures` 更新（B1 联动，见 Phase 1）
- owner doc：eql-and-database-compatibility.md 补「h2 frame 实测开启（WI3 矩阵）」一句（m11）
- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/h2.dialect.xml`（features 按 H2 实跑结果填写）
- SQL 快照文件（入库，放测试资源目录）
- ai-dev/design/nop-stream/sql-window-dialect-matrix.md（矩阵 + D15 标注）
- roadmap WI3 状态行与括注（含 D15 标注）；当日日志；plan 收口

### Out Of Scope

- docker opt-in 的 PG/Oracle/MySQL/MariaDB/MSSQL 实跑（环境不可用，D15 降级）；es/tdengine；duckdb 函数验证。

## Execution Plan

### Phase 1 - H2 真实执行矩阵（含 PostgreSQL 实跑升级）

Status: completed
Targets: nop-dao 测试

- Item Types: `Proof`

- [x] 新增测试类（extends JdbcTestCase，内存 H2；建表参照 TestDialect 先例在测试方法内 safeDrop + create）：表 t(id int primary key, grp varchar, val int) + 多行数据；6 组矩阵查询（rows/range/groups × inline/named-window，`select id, sum(val) over (...) from t order by id` 形态，named 形态用 `window w as (...)`）逐一执行并断言结果集非空且聚合值符合手算预期；**第 7 组 named-window 无 frame 对照查询**
- [x] docker info 实测：退出码 0（daemon 运行中）——D15 主路径部分可用，降级范围缩小为「非本轮目标的重镜像方言」
- [x] 新增 PostgreSQL 实跑矩阵测试类（**独立数据源，不继承 TestPostgreDialect**——执行时发现 9.6.12 镜像已移除且旧套件与 PG16 版本错配）：postgres:16-alpine 容器 7 组矩阵全 PASS，据此填 postgresql.dialect.xml features
- [x] `docker info` 结果记入矩阵文档与当日日志（D15 证据）
- [x] 记录每组通过/失败与失败异常形态（H2 不支持某单位时抛的语法错误即矩阵证据；WINDOW 子句语法支持单列独立轴）；据此填 h2.dialect.xml features
- [x] 同步更新 `TestDefaultDialectWindowFeatures`：h2 断言改实跑新值、fixture 断言按新值重写（「集中缺省 false」语义由合成方言用例继续承载）
- [x] 当日日志记录 H2 实跑结果明细

Exit Criteria:

- [x] 6 组矩阵查询 + 第 7 组对照轴在 H2 上全部有明确结论（通过或语法不支持），无 hang/无跳过
- [x] h2.dialect.xml 与 postgresql.dialect.xml features 与实跑结果一致（通过=true，未通过=false）
- [x] docker opt-in 测试在 `-Dnop.test.docker.enabled=true` 下实跑通过（PG 容器拉起并执行矩阵）
- [x] WI2 交付的 `TestDefaultDialectWindowFeatures` 与 `TestEqlCompileSql` 等测试随 h2 新值同步后全绿（B1 联动范围）

### Phase 2 - 其余方言 SQL 快照比对

Status: completed
Targets: nop-orm-eql 测试

- Item Types: `Proof`

- [x] 新增快照测试：CoreInitialization + DialectManager 加载 11 个独立方言（db2/dm/duckdb/mariadb/mssql/mysql/mysql5.7/oracle/postgis/postgresql/h2gis；mysql5.7 补入 M3 复核建议），对「无 frame 的 over(partition by/order by) 查询 + WINDOW 子句查询」走 EqlCompiler 全链（features 全 false 不影响这两类查询过能力门，WI2 实测裁定；**快照查询用 AppUser 实体上下文**，M8）。带 frame 的生成文本已由 WI2 合成方言单测覆盖；矩阵中 frame 列对本组方言记「能力位未启用（无实跑证据）」而非生成失败
- [x] golden 机制：10 个 golden 入库 `src/test/resources/__snapshot/<dialect>.window.sql`（`-- query:` 注释分隔），逐行 contains 断言经 normalize（小写+空白折叠）；golden 与断言同 PR 入库
- [x] 端到端用例见 Phase 1（TestH2WindowFrameCompileEndToEnd）
- [x] owner doc：eql-and-database-compatibility.md 补「已实测开启：h2 与 postgresql（postgis、duckdb 经继承）三能力位全开」一句（m11）
- [x] 快照文件入库（每方言一个 golden 文件，断言与文件一致）；方言间形态差异（分页/引用符/别名）如实入库
- [x] 矩阵文档 sql-window-dialect-matrix.md：**逐文件枚举全部 18 个 dialect.xml + selector 目录**（M3）——h2 填实跑值；db2/dm/duckdb/mariadb/mssql/mysql/mysql5.7/oracle/postgis/postgresql/h2gis 记「SQL 生成快照通过 + 执行未实测 + features 保持 false」；es/tdengine 记「排名族窗口函数登记缺失（WI4 范围外），聚合 over 编译不受影响，执行未实测」（m6 精确措辞）；default 记「基文件（承载三能力位集中缺省声明），不可独立加载」；geo-support/window-expr-support 记「基文件，不适用（window-expr-support 仅函数登记）」；oracle-reverse 记「反向工程工具文件，不适用」；selector 记「选择器目录，无能力位声明，不适用」；H2 WINDOW 子句支持单列独立轴结论；D15 标注段 + docker 触发证据

Exit Criteria:

- [x] 11 方言 × 2 类查询（over 子句、WINDOW 子句）快照生成全通过且 golden 入库（AppUser 实体上下文）
- [x] 矩阵文档含 D15 逐方言未实测标注（禁止无标注收口）
- [x] duckdb 行仅记 features 值状态，不重复 WI8 记账
- [x] owner doc 项移至上方 checklist（m11）

### Phase 3 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：首轮 FAIL（Blocker-1 owner doc 反引号路径触发 docs-for-ai→ai-dev 越界规则、Major-2 h2gis/duckdb 继承外溢表述错误）→ 修正后达成 audit 指明的 PASS 条件（机制与测试面首轮已全部 PASS：H2 七组与 PG16 七组实测、快照 10/10、端到端、105+142 回归、scan-hollow 0）；证据落 ai-dev/audits/nop-stream-sql/wi3-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-dao --severity high` 与 `--module nop-persistence/nop-orm-eql --severity high` 均退出码 0（执行与 audit 双重复核）
- [x] audit 通过后 roadmap WI3 `todo` → `done`（括注含 D15 标注与 PG 实跑升级说明、零圆括号字符）；解析器翻转后实测 items 31 milestones 7
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI3 = done（含 D15 标注），解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0

## Closure Gates

- [x] H2 实跑矩阵 6 组 + 第 7 组对照轴有明确结论（真实 JDBC 执行，非 mock；audit 实测复现）
- [x] h2.dialect.xml features 与实跑结果一致（audit 实测七组全 PASS 后核对）
- [x] 10 方言快照 golden 入库且测试断言一致（audit 实测 10/10 + 格式核对）
- [x] 矩阵文档 D15 逐方言未实测标注齐全（audit 核对；h2gis/duckdb 继承外溢已按 audit Major-2 更正为「经继承生效 true×3，执行未实测」）
- [x] `./mvnw test -pl nop-persistence/nop-dao -am`（142 绿）与 `./mvnw test -pl nop-persistence/nop-orm-eql -am`（105 绿）
- [x] scan-hollow 两模块高危零发现（audit 实测）
- [x] owner doc 已补「已实测开启」句（audit Major-2 后含 h2gis 继承说明）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id；首轮 FAIL 2 项文本缺陷修正后达成 PASS 条件）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/08-wi3-dialect-window-matrix.md --strict` 退出码 0

## Deferred But Adjudicated

### PG/Oracle/MySQL 等 docker opt-in 实跑

- Classification: `watch-only residual`
- Why Not Blocking Closure: docker info 实测结果已记录（见矩阵文档与日志，Phase 1 执行项）；D15 允许降级（H2 实跑 + 快照比对 + 逐方言标注）；这些方言的 features 保持 false（缺省不启用）不影响任何既有行为，待 docker 环境复核后按矩阵结论开启
- Successor Required: `no`
- Successor Path: 环境具备时按矩阵文档「待复核」清单逐方言实跑并回填 features

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 实跑矩阵交付——docker 实测可用使 D15 主路径部分启用，H2 2.4.240 与 PostgreSQL 16 七组全 PASS，h2/postgresql features 填 true×3（h2gis/postgis/duckdb 经继承同值），10 方言快照 golden 入库，db2 独立加载失败单列，矩阵逐文件枚举 18+selector 并含 D15 全标注。首轮 audit FAIL 的 2 项文本缺陷修正后达成 audit 指明的 PASS 条件。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi3-closure-audit.md
- Evidence:
  - H2 与 PG16 实跑七组全 PASS（audit 双实测复现，非转述）；快照 10/10、WI2 联动 2/2、端到端 1/1；105+142 回归全绿
  - 首轮 FAIL 仅 2 项文本缺陷（Blocker-1 越界引用、Major-2 继承外溢表述）——修正后 doc-links 0 error，audit 指明无需重跑代码测试
  - plan 文本同步（10 方言、PG16 独立数据源——执行偏离均有据）与 checklist 勾齐（Minor-3/4）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/08-wi3-dialect-window-matrix.md --strict` 退出码 0

Follow-up:

- 无 plan-owned 剩余工作；矩阵 §6 待复核清单（MySQL/Oracle 等实跑）归环境具备后的后续复核
