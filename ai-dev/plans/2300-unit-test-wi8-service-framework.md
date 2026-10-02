# 2300 unit-test-coverage-roadmap WI8 — nop-service-framework 补强

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI8 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；docs-for-ai/02-core-guides/service-layer.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-service-framework 补强：nop-biz CRUD/findPage/批量保存语义（44.10%）、nop-biz-auth-core 数据权限过滤（51.79%）、nop-gateway 补强（62.55%）、nop-biz-auth-api 结构性用例（28 main/0 test）。**BizModel 服务测试必须经 `IGraphQLEngine`（testing.md 禁令：禁止直调 bizObj.method）**。

## Current Baseline

- nop-biz 44.10% / 3769L；靶点：BizActionInvoker 0%、DevDocBizModel 9.52%、BizProxyInvocationHandler 12.62%、ManyToManyTool 16.22% 等 10 个低覆盖类。
- nop-biz-auth-core 51.79% / 1707L；靶点：AbstractLoginService 0%、UserContextImpl 23.4%、AuthHttpServerFilter 16.99%。
- nop-gateway 62.55% / 1709L（已达标 ≥45%）；靶点：GatewayHttpFilter 19.13%、ForwardProcessor 13.51% 等。
- nop-biz-auth-api：NO-EXEC（无 src/test；pom 实测无 junit 依赖，需按 Scope 裁定新增）。
- 模块组 nop-service-framework parent 链到 root（profile 继承正常）；测量走统一 baseline 脚本（删 exec → `--skip-test --label wi8-2026-10-02`）；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。
- WI0 失败遗留：nop-code-web 的 NopCodeWebPagesTest（IoC bean 装配缺失）不在本 WI 范围（WI12 跟进）。

## Goals

- nop-biz：CRUD/findPage/批量保存语义用例 ≥10 个（经 IGraphQLEngine 或 BizModel 等价公开通道）。
- nop-biz-auth-core：数据权限过滤/登录上下文语义用例 ≥8 个。
- nop-gateway：过滤器/转发语义用例 ≥6 个。
- nop-biz-auth-api：结构性用例 ≥4 个。
- 测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；不新建 HTTP E2E（进程内通道优先）。

## Scope

### In Scope

- `nop-service-framework/nop-biz|nop-biz-auth-core|nop-gateway|nop-biz-auth-api/src/test/**` 新增测试与资源。
- **pom 裁定**：为 nop-biz-auth-api 新增 `junit-jupiter` test-scope 依赖（实测缺失）并记录。

### Out Of Scope

- 产品代码；nop-graphql（48.97% 已达标）；nop-code-web 失败修复。

## Execution Plan

### Phase 1 - 增量测试编写

Status: planned
Targets: `nop-service-framework/*/src/test/**`

- Item Types: `Fix`

- [ ] nop-biz ≥10 用例（**全部经 IGraphQLEngine 通道**。参照：`docs-for-ai/05-examples/test-examples.java` 示例 2b/4/5 完整模板 + **nop-datav-service 测试**（全仓唯一 `@Inject IGraphQLEngine` 先例，如 TestNopDatavDataAuth）。**注意：nop-biz 现有 23 个测试无一使用该通道**（自制 mock/直调风格），不可照抄）。第一步先验证 nop-biz 测试 beans（app-test.beans.xml）能否注入 IGraphQLEngine——不能则经测试 delta beans 显式注册（测试资源，记录）。
- [ ] nop-biz-auth-core ≥8 用例；nop-gateway ≥6 用例；nop-biz-auth-api ≥4 用例。
- [ ] 四模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥28 用例，含显式语义断言；无任何 bizObj.method 直调。
- [ ] 四模块测试全绿。
- [ ] pom 变更（如有）仅 test-scope 依赖且已记录。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI8 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删四模块 exec → baseline 脚本复测，记录增量。
- [ ] 残余缺口显式裁定（engine 层目标 45%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI8 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI8 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 四模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码零修改；pom 仅 test-scope 新增且记录
- [ ] BizModel 测试全部经 IGraphQLEngine（禁令遵守）
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：CRUD/权限测试断言业务语义（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2300-unit-test-wi8-service-framework.md --strict` 退出码 0
- [ ] roadmap WI8 checkbox 与 plan/log 一致

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

- Risks And Rollback: 纯测试增量，可单独回滚。
