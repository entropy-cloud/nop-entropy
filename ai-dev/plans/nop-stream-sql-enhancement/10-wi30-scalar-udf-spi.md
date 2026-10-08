# 10 WI30 标量 UDF SPI

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI30 行）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4b SELECT 投影行（WI29 后标量子集）；docs-for-ai/03-modules/nop-stream-sql.md（用户文档落点）；survey seatunnel 篇（形态参考）
> Related: IAggregatorFunctionResolver（同构先例——flow SPI + app-beans 注册 + BeanContainer 按类型查找）；SqlRegularFunction AST（WI29 后 compileScalar default 仍拒——UDF 注册即消费面）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

标量 UDF SPI：应用注册自定义标量函数（open/close 生命周期），SQL 标量表达式中以函数名调用；注册面与 IAggregatorFunctionResolver 同构（BeanContainer 按类型查找），非 roadmap 字面的 ServiceLoader（实措辞差异如实记录）。

## Current Baseline

2026-10-08 实测：SqlRegularFunction AST（name + args，EqlASTKind ordinal 53）；compileScalar/SqlScalarXplPrinter default 拒（WI29 后仍拒正则函数）；IAggregatorFunctionResolver 先例 = flow SPI 接口 + nop-stream-sql app-beans 注册 + BeanContainer.tryGetBeanByType 查找（AdvancedTransforms :480-489）；XLang xpl 通道调用自定义 bean 方法可用（xpl 静态调用需 import，SqlCastHelper 先例）。

## Goals

1. **SPI**：flow 新接口 `IScalarFunctionResolver`——`Object resolve(String fnName)` 返回可调用实例/描述（或 `Function<Object[], Object>` 语义等价形态，plan 实施时钉定）；生命周期 open/close（AutoCloseable 或显式接口方法）。
2. **求值器**：compileScalar/resolveType 增 SqlRegularFunction 分支——resolver 查找 fnName；未注册 fnName typed fail-fast（`nop.err.stream.invalid-arg`，文案列出已注册名）；编译期一次解析（非逐记录查找）。
3. **打印器**：SqlScalarXplPrinter 增 SqlRegularFunction 分支——参数 xpl 逐个打印包裹为 UDF 调用 xpl（形态实施时实测钉定——bean 方法调用/静态 helper 两者择一并钉码）。
4. **nop-stream-sql 实现**：`StreamScalarFunctionResolver`（app-beans 注册）+ 内置注册 API（`register(fnName, fn)`）+ open/close 生命周期传播。
5. **测试**：求值（注册函数求值/未注册 fail-fast/参数传递/null 透传）；打印通道 xpl 断言；E2E（含 UDF 调用查询真实执行）；生命周期（close 传播）。
6. **文档**：nop-stream-sql.md UDF 注册小节（roadmap 明文落点）；roadmap WI30 状态行；日志。

## Non-Goals

- 聚合 UDF（IAggregatorFunctionResolver 面不变）；语法级 CREATE FUNCTION；非标量返回（table function）；roadmap 字面 ServiceLoader 改造（如实记录实为 BeanContainer 同构——比照聚合先例）。

## Scope

### In Scope

- nop-stream-flow：IScalarFunctionResolver SPI；nop-stream-sql：StreamScalarFunctionResolver 实现 + app-beans；compileScalar/resolveType/SqlScalarXplPrinter 三分支；测试（eval/xpl/E2E/生命周期）；nop-stream-sql.md UDF 小节
- roadmap：WI30 状态行；日志

### Out Of Scope

- 聚合 UDF；CREATE FUNCTION 语法；ServiceLoader 通道

## Execution Plan

### Phase 1 - SPI/求值/打印/测试

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/spi/`、`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/`

- Item Types: `Feature`

- [x] SPI：IScalarFunctionResolver 接口（flow/spi）——resolve(fnName) 返回标量函数描述；生命周期方法；javadoc 记与聚合先例的同构关系与 ServiceLoader 差异披露
- [x] 实现：StreamScalarFunctionResolver（nop-stream-sql）——内置注册 API + app-beans 注册 + AutoCloseable close 传播
- [x] 求值/打印：compileScalar/resolveType/SqlScalarXplPrinter 增 SqlRegularFunction 分支——resolver BeanContainer 查找（未注册 typed fail-fast 列已注册名）；打印通道形态实测钉定
- [x] 测试：TestStreamScalarFunctionResolver（求值/未注册/参数/null/生命周期）+ xpl 断言 + E2E（UDF 查询真实执行）
- [x] 文档：nop-stream-sql.md UDF 注册小节
- [x] 全量回归：nop-stream-sql -am

Exit Criteria:

- [x] **端到端验证**（guide #22）：注册 UDF 的查询经真实执行产生正确结果
- [x] **接线验证**（guide #23）：SPI bean → compileScalar/打印器双通道连通（双向钉码）
- [x] **无静默跳过**（guide #24）：未注册 fnName typed fail-fast（列已注册名）
- [x] 新功能测试覆盖（guide #25）：SPI/求值/打印/E2E/生命周期五处
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [x] owner doc 更新：nop-stream-sql.md
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: in progress
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi30-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI30 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)`）；重跑解析器核对 items 18 milestones 4
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，scan-hollow --severity high 退出码 0

## Closure Gates

- [x] 行为/契约结果已达成：UDF 注册→SQL 调用→求值/打印双通道可用
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：SPI bean→编译器→求值/打印全链连通（E2E 真实执行）
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 标量 UDF SPI 交付——IScalarFunctionResolver（flow/spi）+ StreamScalarFunctionResolver（静态持有者模式）+ compileScalar/打印器双通道 + E2E 真跑。ServiceLoader 字面表述实为 IoC/静态持有同构如实披露；EQL 函数名小写归一执行发现记录。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Evidence:
  - Exit Criteria 七项 PASS：SPI 接口/求值/打印/E2E（UDF 查询真实执行 strLen(item) 7 行精确断言）/未注册 fail-fast/生命周期/文档
  - Anti-Hollow：SPI bean→static default→compileScalar 分支→eval 调用全链 + 打印通道 xpl 静态调用断言
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/10-wi30-scalar-udf-spi.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work
