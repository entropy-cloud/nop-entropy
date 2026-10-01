# 2287 DuckDB WI5 — 数据正确性四方对拍矩阵

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI5；WI0 裁定报告（ORM 路由实测）；WI2/WI3 交付；对抗审查 agent_f530c5cd（三轮收敛：首轮 5B/2M/3m、二轮 1 新 B + B5/m3 残留、三轮 1 行文本 blocker + 3 minor，全部按处方修复，达成可执行共识）
> Related: 2282（WI0 spike）、2284（WI2 文件面契约）、2286（WI4）

## Purpose

在**同一确定性数据集**上完成四方对拍，固化"任何一条腿都不漂移"的业务不变量 golden 断言，为后续 WI6/WI7 提供正确性参照。对拍发现的真实差异要么收敛（修配置）、要么如实记录为引擎差异裁定，不允许静默。

## Current Baseline

- 四方 legs 的技术路径全部有实测先例：
  - **leg A（DuckDB 经 nop-duckdb 执行层）**：DuckDbEngine.openFile/openMemory + raw SQL（WI2 24 测绿）；显式类型 read 须在 engine Connection 上手写 `read_csv(..., types=...)` 原生 SQL（DuckDbFiles.readCsv 硬编码 read_csv_auto 无类型参数，**本 WI 不改生产 API**）
  - **leg B（DuckDB 经 ORM/EQL 方言路径）**：WI0 spike `_tmp/duckdb-spike/.../Phase3OrmNamedRoutingTest.java` 实测通过——setTestConfig 配默认 h2 + 命名 `duck` 数据源 + `nop.dao.config.query-space-to-dialect=duck=duckdb` + `nop.orm.init-database-schema=true`，`querySpace="duck"` 实体路由成功
  - **leg C（RDB 下推经 nop-dao）**：同 spike 命名数据源 + H2 方言先例；H2 无版本声明可解析（root pom → nop-bom → nop-dependencies → quarkus-bom/spring-boot-dependencies 管理，nop-benchmark-orm 先例）
  - **leg D（tablesaw）**：nop-tablesaw 0.43.1 CSV 读入（WI2 已桥接）
- **QueryBean 聚合/join 机制（对抗审查 B1/B2 实证）**：ORM 执行路径**不消费** `query.getAggregates()`（DaoQueryHelper.queryToSelectFieldsSql 只读 `getFields()`，聚合靠 `QueryFieldBean.setAggFunc()` + `groupBy`）；`aggFunc="count"` 字段 `*` 被 checkFieldName 拒绝——**count(*) 字面在 EQL leg 不可表达，用 count(非空列) 等价不变量替代**；`query.joins` 是主子表拆分 + dimFields **内存对齐**（MdxQuerySplitter），NULL 键经 ConvertHelper.toString 归一空串（MdxQueryExecutor），与 SQL 标准 NULL 不匹配语义相反——**join 语义不对拍 EQL leg**（登记为平台已证行为），join 在 legs A/C（原生 SQL）/D（tablesaw）对拍
- **跨引擎数值类型语义（对抗审查 B5 实证）**：DuckDB `AVG(DECIMAL)` 返回 DOUBLE、H2 返回 DECIMAL、tablesaw 0.43.1 **无 DECIMAL 列型**（dec 落 DoubleColumn）——数据集 DECIMAL 值全部取**二进制精确值**（0.25/0.5/0.75/1.25 类，4 位小数内精确表示），使 DOUBLE 与 DECIMAL 数值精确相等；AVG 返回类型差异预裁定为**引擎差异（非缺陷）**，值断言用 BigDecimal.valueOf(double) 精确比较
- **classpath 扩展安全性已核实**：nop-orm AbstractJdbcTestCase 带 nop-dao test classpath 且不设数据源配置即可 CoreInitialization.initialize()——`nopDataSource` 是惰性 bean；nop-duckdb 加 test deps 不会破坏既有启动
- **配置泄漏机制（对抗审查 M1）**：BaseTestCase.setTestConfig 是进程级静态写穿，CoreInitialization.destroy() 不复位 config；nop-duckdb 测试共享 JVM（5 类顺序 initialize/destroy）——泄漏的 `nop.orm.init-database-schema=true` 会让后续每个测试类 boot 时真实连库建表。**处置：nop-duckdb pom surefire forkCount=1 reuseForks=false**（spike 同款配置，逐类独立 JVM，根因隔离）
- spike 测试树必备件（对抗审查 B3/B4）：命名数据源 bean 须手写 beans 文件（`ioc:collect-beans name-prefix="nopDataSource_"` 收集，无配置驱动自动注册）；orm 模型加载须 `_vfs/nop/duckdb/_module` 模块标记（ModuleManager 只发现带 _module 的模块，spike 有此文件）
- WI2 已固化桥类型漂移边界（前导零→VARCHAR、空串→NULL 坍缩）——数据集避开已知漂移形态
- 测试树现状：nop-duckdb 37 测全绿；VFS 测试资源在 `_vfs/nop/duckdb/`（beans/task 已有；orm + `_module` 待加）

## Goals

- 同一数据集（CrossEngineDataset：10 行 × 8 列，含 NULL、二进制精确 DECIMAL(12,4) 值、>2^53 大整数、DATE/TIMESTAMP、unicode 字符串、BOOLEAN）上，四方对拍全部一致
- 类型矩阵：每类型在可表达 legs 上读出类型与值一致（golden 常量锚定，不用快照文件）
- 聚合/join 语义：count(非空列)、avg 忽略 NULL、NULL 等值 join（SQL 标准：NULL 键不匹配）、隐式类型提升——golden 硬编码预期值
- 对拍差异处置裁定：SQL 引擎（A/B/C 属同一 SQL 语义域）间不一致 = 缺陷信号（登记 WI8 输入并阻断 closure）；预声明的类型差异（AVG 返回型、tablesaw 列型）与 tablesaw 语义差异（如 join 的 NULL 处理）= 引擎差异，如实记录裁定

## Non-Goals

- 性能/大负载（WI7）；外存溢出场景（WI6）；xlsx 入口（WI2 已测）
- 修 duckdb.dialect.xml / ddl_duckdb.xlib 缺陷：对拍若暴露方言缺陷，登记到 WI8 输入清单（roadmap Cross-Cutting #7：不在本 WI 夹带修复）；leg B 的降级路径见 Phase 1 执行项
- tablesaw 与 SQL 引擎的语义差异"修正"——只记录裁定
- 生产代码改动（pom test deps/surefire 配置与测试资源除外）

## Scope

### In Scope

- nop-duckdb pom：test scope 增加 nop-dao、nop-orm、nop-orm-eql（io.github.entropy-cloud 无版本）、com.h2database:h2（无版本）；surefire forkCount=1 reuseForks=false
- 测试资源：`_vfs/nop/duckdb/_module`（空标记）、`_vfs/nop/duckdb/orm/app.orm.xml`（DynamicOrmEntity 实体）、`_vfs/nop/duckdb/beans/app-parity.beans.xml`（命名数据源 nopDataSource_duck：Hikari maxPoolSize=1 + jdbc:duckdb 指向 per-leg 独立文件）
- 测试：CrossEngineDataset（数据集 + golden 常量）、TestCrossEngineTypeMatrix、TestCrossEngineAggJoin
- 既有 37 测在 classpath/fork 配置变更后无回归
- 当日 ai-dev/logs/ 条目

### Out Of Scope

- nop-dao/nop-orm/tablesaw 修改；CI

## Execution Plan

### Phase 1 - 对拍基建与类型矩阵

Status: completed
Targets: `nop-duckdb/pom.xml`、`nop-duckdb/src/test/`

- Item Types: `Proof`

- [x] pom test deps（nop-dao/nop-orm/nop-orm-eql/h2）+ surefire forkCount=1 reuseForks=false；先跑既有 37 测确认无回归（执行修正：发现 nopDataSource 非"惰性"而是 nop-config 模块根本不在 classpath——无 ConfigInitializer 则 application.yaml 永不装载，补加 nop-config test dep + src/test/resources/application.yaml（最小 h2 默认源，nop-wf-service/nop-task-ext 同款先例），37/37 复跑全绿）
- [x] CrossEngineDataset：确定性数据集（代码内构造，同步产出 CSV 文本供 leg A/D）+ golden 常量；DECIMAL 列全部二进制精确值且**每个聚合组的 sum/count 商也是二进制精确值**（组 g0 四非 NULL 行 sum=2.5 avg=0.625、g1 四行 sum=5.0 avg=1.25、g2 一行非 NULL 1.5 + 一 NULL 行 avg=1.5、全局 sum=9.0 avg=1.0——dyadic 封闭，复核 B5 残留裁定）；避开 WI2 已知漂移形态（无前导零、无空串，NULL 用显式缺失）
- [x] 测试资源三件：`_module` 标记、`orm/app.orm.xml`（duckdb 路由实体 querySpace=duck、h2 实体、join 辅助实体，列型 DECIMAL(12,4)/BIGINT/DATE/TIMESTAMP/BOOLEAN/VARCHAR + 低基数分组列 grp）、`beans/app-parity.beans.xml`（nopDataSource_duck：Hikari maxPoolSize=1，jdbc-url 经 `@cfg:parity.duck.jdbc-url|jdbc:duckdb:` 动态注入——**fallback 必须非空**（内存库兜底），否则未设该 config 的测试类 boot 时 Hikari fail-fast 失败；由 setTestConfig 指向**独立于 leg A 的临时文件**——参照 spike app-spike.beans.xml 模式；per-leg 独立 .duckdb 文件规避 WI4 单写者/config-conflict 交互）
- [x] 四 legs 装配：A=engine.openFile（独立文件）+ 显式 DDL + `read_csv(types=...)` 原生 SQL；B=ORM save + QueryBean（duckdb 路由）；C=ORM save + QueryBean（H2 默认位）；D=tablesaw readCsv
- [x] **leg B DDL 缺陷降级路径**（m3 裁定，时序敏感）——降级路径未触发：duckdb 方言对 DECIMAL(12,4)/DATE/TIMESTAMP 自动建表一次通过（无需预建表）：schema initializer 是 boot 期 @PostConstruct（早于任何测试代码），故预建表必须在 @BeforeAll、CoreInitialization.initialize() **之前**，用**裸 duckdb JDBC**（duckdb_jdbc 已在 test classpath）对 **leg B 自己的文件**（leg A engine 文件对 leg B 数据源不可见）建同名同型表；initializer 的 existsTable 守卫幂等跳过（DataBaseSchemaInitializer.java:68 已核实）；**本降级仅覆盖执行期失败**——若方言缺陷表现为 DDL 生成期异常（类型映射缺失），预建表无法救 boot，直接登记 WI8 阻断；缺陷登记 WI8 输入清单，对拍继续
- [x] 类型矩阵断言：每 leg 读出全部 8 列 ×10 行与 golden 常量一致（DECIMAL 以 BigDecimal 精确比较、BIGINT 以 long（>2^53 值防 double 精度丢失）、DATE→LocalDate、TIMESTAMP→LocalDateTime、BOOLEAN、VARCHAR 含 unicode、NULL 位点精确；tablesaw dec 列以 BigDecimal.valueOf(doubleValue) 比较）

Exit Criteria:

- [x] 既有测试无回归：`./mvnw test -pl nop-duckdb` 退出码 0（≥37 测，fork 隔离下）
- [x] 类型矩阵：三个 SQL legs（A/B/C）对同一列返回相同值（类型映射差异如实记录：如 DuckDB AVG 返回 DOUBLE vs H2 DECIMAL——预裁定为引擎差异）；tablesaw leg 值一致（列型差异记录）
- [x] DECIMAL 精度：二进制精确值在各 leg **可表达位点**上 sum/avg 与 BigDecimal golden 精确一致（无浮点伪差；全局 avg 位点 EQL leg 不可表达，A/C/D 覆盖；AVG 返回类型差异按预裁定记录，不作缺陷信号）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0（Phase 1 收口门随 Phase 2 一并复跑）
- [x] No owner-doc update required（WI9 承接）
- [x] 当日 `ai-dev/logs/` 条目已更新

### Phase 2 - 聚合与 join 语义 golden 断言

Status: completed
Targets: `nop-duckdb/src/test/java/io/nop/duckdb/`

- Item Types: `Proof`

- [x] count：四 legs 对拍 = golden 值（EQL leg 用 `QueryFieldBean.forField("id").count()`，**name=源列名 "id"（alias 才是输出别名）**——MdxQuerySplitter.findField 按 getName() 匹配，PK 列名命中即不触发主键回退追加未分组列（复核 B1 已代码确认）；count(*) 字面不可表达已实证（B1），count(id) 与 count(*) 在数据集上等价（id 主键非空），等价性在 plan 层裁定）
- [x] avg 忽略 NULL：dec 列含 1 个 NULL，**EQL leg 用 grouped avg**（aggFunc="avg" 于 dec + groupBy 低基数列 grp，**且 grp 明字段须进入 fields**（name="grp" 普通字段，否则 internal 列不回填结果行、组无法对上行）；QueryBean 无 groupBy 的整体聚合会触发主键回退生成无 GROUP BY 非法 SQL，复核 Blocker 已裁定采用 grouped 形态；golden 按组锚定 g0=0.625/g1=1.25/g2=1.5，组 sum/count 商均 dyadic）；A/C/D legs 同分组口径对拍 + 全局 avg=1.0（全局 avg 位点 EQL 不可表达——整体聚合即被禁形态）；COUNT 位点同断言
- [x] NULL 等值 join：join 辅助表含 NULL 键行（PK 约束阻止 NULL 进主键列——实测修正为独立可空 link_id 列做 join 键，leg D 无 PK 约束直用可空 id）；A/C legs 原生 SQL inner join 丢 NULL 键行（8 行）全绿；D legs tablesaw join 同为 8 行（NA 键行丢弃，与 SQL 语义一致）；**B leg 不参与 join 对拍**（QueryBean.joins 为 dimFields 内存对齐——B2 裁定，作为平台行为记录于测试 javadoc）
- [x] 隐式类型提升：A/C legs `dec + id` 结果 = golden（BigDecimal 精确，NULL 位点保持 NULL）；D legs double+long dyadic 精确；B legs 类型保真读出替代（BIGINT→Long、DECIMAL→BigDecimal、DATE→LocalDate、**TIMESTAMP→java.sql.Timestamp（EQL 读路径实测形态，已固化）**）
- [x] 全部 golden 断言锚定业务不变量（硬编码预期值，无快照文件）

Exit Criteria:

- [x] 聚合/join 不变量在全部可表达 legs 上一致；SQL-leg（A/B/C）间零不一致（无 WI8 登记项；DuckDB AVG→DOUBLE 类型差异按预裁定记录）
- [x] tablesaw 与 SQL 引擎的语义差异（如有）以文字裁定记录于测试 javadoc 或 plan 偏差（实测：join NA 键行为与 SQL 一致、dec 落 DoubleColumn、小值 id 落 IntColumn——均记录于测试注释）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0（46/46 + 上游模块全绿；审计员独立复跑 EXIT=0）
- [x] No owner-doc update required（WI9 承接）
- [x] 当日 `ai-dev/logs/` 条目已更新 WI5 收口记录

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复或登记 WI8 输入清单（本 WI 为 Proof，无新 defect）
- [x] 行为/契约结果已达成：四方对拍矩阵有 golden 断言且全绿
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift（对拍差异全部显式裁定）
- [x] 受影响的 owner docs 已同步，或明确 No owner-doc update required（显式归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证四 legs 真实经过各自引擎（leg B/C 确认 EQL 真实下推到对应方言生成 SQL 而非内存计算——聚合经 aggFunc 生成 FUNC(col) SQL、值来自 JDBC ResultSet；leg A 确认真实 duckdb 文件连接；leg D 确认 tablesaw Table API），无恒真断言、无被静默忽略的死字段调用（禁用 query.setAggregates）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] 代码规范核对通过（import 分组/命名/缩进人工核对 + scan-hollow 0；构建无 checkstyle 工具；审计 Minor 7 代码整洁度问题已修复）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2287-duckdb-wi5-cross-engine-parity-matrix.md --strict` 退出码 0
- [x] scan-hollow-implementations --module nop-duckdb --severity high 退出码 0

## Deferred But Adjudicated

### EQL leg 的 join 语义对拍

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: QueryBean.joins 是 MDX 式主子表内存对齐（dimFields、NULL 键归一空串），与 SQL equi-join 属不同语义域，强行对拍会产生伪差异；join 语义已由 legs A/C/D 覆盖。若未来需要 SQL join 语义的 EQL 表达（sql-lib 原生查询），另行立项
- Successor Required: `no`
- Successor Path: 无（行为已作为平台事实记录于测试 javadoc）

## Non-Blocking Follow-ups

- 对拍矩阵当前覆盖 7 列 × 10 行小数据集；更大规模类型矩阵（HUGEINT 边界、微秒 TIMESTAMP）可在 WI6/WI7 按需补充
- `nop.orm.init-database-schema` 的 duckdb 方言 DECIMAL(p,s)/TIMESTAMP DDL 实测结论若暴露缺陷，转 WI8 全量验证时一并修复回归

## Closure

Status Note: WI5 收口。独立 closure audit（fresh subagent）裁决 CAN CLOSE、0 blocker：四方 legs 真实经过各自引擎（leg B 排除法：H2 中无 parity_row 表，未路由即 table-not-found）、golden 断言锚定业务不变量（数据集独立验算 dyadic 封闭）、五个验证门独立复跑全 0、禁用 setAggregates 死字段核实（全仓 0 消费者）、Deferred 分类诚实。8 条 Minor 已在收口时修正（Phase 1 状态滞后、7/8 列笔误、B2 机制归属、代码整洁度）。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理（fresh session，read-only audit）
- Audit Session: agent_1404399e-58a5-4009-a5bb-4769fed8908f
- Evidence:
  - Phase 1 Exit Criteria 6/6 PASS（46/46 独立复跑 EXIT=0；golden 断言逐行逐列非恒真；dyadic 数据集独立验算 g0=0.625/g1=1.25/g2=1.5/全局 9.0/1.0 全部封闭）
  - Phase 2 Exit Criteria 5/5 PASS（count/分组 avg/join=8/promotion 四 legs 一致，SQL-leg 间零不一致；tablesaw 差异全部文字裁定）
  - Anti-Hollow 三项亲验：leg B 排除法下推实证（JdbcDataSetHelper nopDataSource_duck 路由 + H2 无 parity_row 表）；leg C 原生 SQL 命中 h2；leg D 真实 tablesaw API；grep 确认无 setAggregates 死字段调用（QueryBean.aggregates 全仓 main 0 消费者）
  - 验证门：./mvnw test -pl nop-duckdb（46/46）、-am、check-doc-links --strict、scan-hollow --severity high、check-plan-checklist --strict 全部退出码 0（审计员独立复跑）
  - 偏差 3 条与对抗审查裁定（B1/B2/B5）逐项与 live 代码一致；Deferred（EQL join）分类诚实
  - 审计 Minor 修复记录：Phase 1 Status 改 completed、7→8 列笔误、MdxQueryExecutor 归属更正、AggJoin 重复 import 与弃用 QueryBean 清理、log fork 计数改为口径化表述

Follow-up:

- 对拍矩阵更大规模类型矩阵（HUGEINT 边界、微秒 TIMESTAMP）在 WI6/WI7 按需补充（Non-Blocking Follow-ups 既有条目）
- no remaining plan-owned work
