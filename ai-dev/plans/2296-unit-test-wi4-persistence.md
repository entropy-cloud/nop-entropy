# 2296 unit-test-coverage-roadmap WI4 — nop-persistence 核心域补强

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI4 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；docs-for-ai/02-core-guides/model-first-development.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-persistence 组核心域补强：nop-orm-eql EQL→SQL 翻译（53.24%，方言分支/函数翻译/子查询/分页）、nop-orm-model 模型加载与校验（40.61%）、nop-dao（45.81%）；nop-db-migration（79.24%）已达标仅确认。orm 主模块 136 个测试文件为就近模式参照。

## Current Baseline

- nop-orm-eql 53.24% 行 / 34.40% 分支 / 6046L；公开翻译入口为 `EqlCompiler.compile(String, String, ISqlCompileContext)`（既有 TestEqlCompileSql 已示范端到端编译断言 SQL 文本）。注意：EqlASTOptimizer（825L/0%）**无任何调用点**（生成派发代码），只能直接实例化喂 AST，语义测试价值低，不作优先靶点；SqlOperator 29.03%、SqlParamTypeResolver 35.85% 为可测补充。
- nop-orm-model 40.61% / 1709L；靶点：OrmComputePropModel 0%、OrmModel 30.19% 等。注意 OrmModelLoader 依赖 ModuleManager 初始化 + xdef 加载，需新搭初始化设施（现有 4 个测试全为裸 JUnit 手工建模）。
- nop-dao 45.81% / 3495L；靶点：SnowflakeSequenceGenerator 0%（public 构造器含 workerId 越界错误路径，纯 JUnit 可测）、JdbcDataSet 19.48%、JdbcTemplateImpl 37.26%、DefaultTransactionManager 39.33%。
- **测试档位（对抗审查确认）**：本组模块**无 nop-autotest 依赖**，`@NopTestConfig`/`JunitBaseTestCase` 不可用（禁止为此改 pom）。nop-dao 既有真实模式为模块内 `JdbcTestCase extends BaseTestCase`（nop-persistence/nop-dao/src/test/java/io/nop/dao/jdbc/JdbcTestCase.java）+ Hikari H2 `jdbc:h2:mem:` + `CoreInitialization`；**禁用 testcontainers（需 Docker）**。
- **方言装配**：orm-eql 测试环境无 dialect.xml 组件加载器，按既有 TestEqlCompileSql 先例手工构造最小 DialectModel；真实方言资源在 nop-dao `_vfs/nop/dao/dialect/*.dialect.xml`（orm-eql 有 compile 依赖可读 classpath）。
- nop-db-migration 79.24% 已达标（≥55%），仅确认不回退（Phase 2 显式项）。
- **测量管线**：本组 pom 链经 nop-persistence→root 继承 coverage profile，`mvnq -- -Pcoverage test -pl :<module> -fae` 可产 exec（四份 plan 中唯一）；报告/解析统一走 `ai-dev/tools/coverage-baseline.sh --skip-test --label wi4-2026-10-02`（删模块 exec 后由缺口补跑重建，保证口径与 WI0 一致）。mvnq = `ai-dev/tools/mvnq`。

## Goals

- orm-eql：EQL→SQL 翻译语义用例 ≥20 个（函数翻译、子查询、分页 limit/offset、参数绑定、方言分支——以 H2/MySQL 方言为主）。
- orm-model：模型加载/校验/计算属性语义用例 ≥8 个。
- dao：可测域（序列生成器、dataset 包装、txn 语义）≥6 个用例。
- 三模块（orm-eql/orm-model/dao）`mvnq -- test -pl :<module> -am -fae` 全绿；记录增量。

## Non-Goals

- 不修改产品代码、ORM 模型结构（protected area plan-first，本 WI 纯测试）；不动数据库 schema 模型文件。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql|nop-orm-model|nop-dao/src/test/**` 新增测试与资源（db-migration 仅数字确认，不新增测试）。

### Out Of Scope

- 产品代码、orm 模型 XML、生成管线；nop-orm 主模块（已达标参照）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: in progress
Targets: `nop-persistence/*/src/test/**`

- Item Types: `Fix`

- [x] orm-eql：TestEqlTranslationSemantics 32 用例，全部经 `EqlCompiler.compile` 公开入口断言产出 SQL/参数/元数据（分页/子查询/_some 展开/union all/case when/函数翻译/未注册函数报错/关系导航 join 等）。
- [x] orm-model：TestOrmComputePropModel 10 + TestOrmModelIndexes 9 = 19 用例（计算属性 getter/setter 注入与错误码、模型索引/拓扑序/集合命名契约）。
- [x] dao：TestSnowflakeSequenceGenerator 7 + TestDefaultTransactionManager 8 + TestJdbcDataSetSemantics 6 = 21 用例（JdbcTestCase + H2；单调性/位布局/越界拒绝、真实事务 commit/rollback、类型化列读取）。
- [x] 三模块 `mvnq -- test -pl :<module> -am -fae` 全绿（91/32/161 tests，一次通过，零回归）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 6 文件 / 72 用例（≥34），全部含显式语义断言（SQL 文本/参数断言）。
- [x] 三模块测试全绿（既有零回归；45 skip 为既有 docker/testcontainers 跳过）。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI4 checkbox

- Item Types: `Proof` + `Decision`

- [x] 重建覆盖报告（label wi4-2026-10-02）：orm-eql 53.24%→57.58%（+4.34，**达标 ≥55%**）、orm-model 40.61%→47.10%（+6.49）、dao 45.81%→47.93%（+2.12）。
- [x] nop-db-migration「已达标不回退」确认：79.24% 持平。
- [x] 残余缺口显式裁定（Deferred 段：18 个低覆盖类分类，生成器/加载设施/接口为主体）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI4 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI4 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 三模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码/ORM 模型/pom 零修改（git 证据：仅 6 个 src/test 新文件）
- [x] 覆盖增量实测记录
- [x] 残余缺口显式裁定（无静默降级）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：翻译类测试断言产出 SQL 而非仅构建 AST（audit 抽查：TestEqlTranslationSemantics L256-264 断言 from APP_USER/参数序，TestSnowflakeSequenceGenerator 单调性/位布局断言）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2296-unit-test-wi4-persistence.md --strict` 退出码 0
- [x] roadmap WI4 checkbox 与 plan/log 一致

## 执行偏差记录

1. OrmModelLoader（baseline 0%/46L）未测：需 ModuleManager + xdef 初始化设施，代价高；按 plan 预授权改测可手工建模的 OrmModel/OrmComputePropModel。
2. orm-eql 最小方言装配补充两个先例内必需项（DialectImpl 构造期强制注册 current_timestamp 函数、无 from select 需 selectFromDual 模板）——测试内配置，非产品修改。
3. 测量快照 overall 含并行 WI 增量（58.1%），模块级数字不受影响。

## Deferred But Adjudicated

### persistence 组残余低覆盖类（18 个）

- Classification: `optimization candidate`（后继 WI 靶点池）
- Why Not Blocking Closure: orm-eql 已达标（57.58% ≥ 55%）；orm-model/dao 增量显著（+6.49/+2.12）。残余 18 类主体：orm-eql 4 个生成器/无调用点类（EqlASTOptimizer 825L 等，plan 已裁定不优先）、orm-model 5 个加载/推导设施类（需 ModuleManager）与接口 default、dao 9 个 JDBC 执行内部路径与接口。orm-model 55% 与 dao 55%/45% 的残余归 WI13 复裁。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 4 项产品缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi4-defect-suspects.md）：OrmModelInitializer.initRefs 集合注册时序、OrmComputePropModel 裸异常、JdbcTransactionFactory.openConnection 不挂事务（设计歧义）、OffsetFetchPaginationHandler buildPageExpr 缺 OFFSET 0。

## Closure

Status Note: 72 用例全绿（91/32/161），orm-eql 57.58% 达标且三处（plan/快照/XML counter 3481/6046）一致，产品/ORM 模型/pom 零修改（git 干净），缺陷嫌疑经源码核实（initRefs 两遍循环时序 L201-206/L216-218 实锤），Deferred 18 类对账吻合。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_3d7bac4c-7f68-434d-81d9-c649606a64d6，fresh session）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量，可单独回滚；dao 域 H2 测试若环境不稳，降级为纯逻辑可测部分并记录。
