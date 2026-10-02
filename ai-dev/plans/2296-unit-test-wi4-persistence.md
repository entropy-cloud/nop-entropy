# 2296 unit-test-coverage-roadmap WI4 — nop-persistence 核心域补强

> Plan Status: active
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

Status: planned
Targets: `nop-persistence/*/src/test/**`

- Item Types: `Fix`

- [ ] orm-eql：经 `EqlCompiler.compile` 公开入口的翻译语义用例 ≥20 个（函数翻译、子查询、分页 limit/offset、参数绑定；方言按 TestEqlCompileSql 手工 DialectModel 先例；直测 public AST 类为补充）。
- [ ] orm-model：加载/校验/计算属性用例 ≥8 个（需初始化设施的靶点按需搭建）。
- [ ] dao：≥6 个用例（SnowflakeSequenceGenerator、JdbcDataSet/事务语义经 JdbcTestCase + H2；禁 testcontainers）。
- [ ] 三模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增/扩展测试 ≥34 个用例，含显式语义断言（SQL 翻译断言文本/参数）。
- [ ] 三模块测试全绿（既有零回归）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI4 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 重建覆盖报告：删三模块 `target/*.exec` → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi4-2026-10-02`，记录基线→复测增量。
- [ ] nop-db-migration「已达标不回退」确认：对比复测快照数字。
- [ ] 残余缺口显式裁定。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI4 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI4 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 三模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码/ORM 模型/pom 零修改（git 证据）
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：翻译类测试断言产出 SQL 而非仅构建 AST（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2296-unit-test-wi4-persistence.md --strict` 退出码 0
- [ ] roadmap WI4 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

（执行结束时填写）

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量，可单独回滚；dao 域 H2 测试若环境不稳，降级为纯逻辑可测部分并记录。
