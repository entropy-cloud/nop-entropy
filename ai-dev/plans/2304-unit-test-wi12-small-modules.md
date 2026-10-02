# 2304 unit-test-coverage-roadmap WI12 — 小模块收尾与达标确认

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI12 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

小模块收尾：nop-spring（spring-core-starter 0%）、nop-quarkus（starters NO-EXEC）、nop-file（dao 32.9%、service NO-EXEC）、nop-autotest（32.95%，测试基建自身）、nop-search（lucene 55.16%、core NO-EXEC）、nop-integration（api 15.35%，其余适配器多数已高）、nop-frontend-support、nop-utils、nop-dev-tools/nop-message（72% 已达标）/nop-credential（高，已达标）/nop-runner/nop-bytecode（76.47% 已达标）/nop-refactor-core（69.03% 已达标）/nop-report-core（64.36% 已达标）按 WI0 基线确认达标或补缺。另收口 WI0 遗留的 10 个无报告模块裁定与 WI0 失败清单中的 nop-code-web 测试装配缺失。

## Current Baseline

- 权威数字源：`ai-dev/analysis/2026-10/coverage-baseline-2026-10-02.json`（本 plan 不复制全表）。
- 明确缺口：nop-spring-core-starter 0%/126L；nop-file-service NO-EXEC；nop-search-core NO-EXEC；nop-integration-api 15.35%；nop-autotest-core 32.95%（>30% 已达标）；nop-file-dao 32.9%（已达标）。
- WI0 无报告模块裁定清单（基线报告）中归属本 WI 的：nop-kernel-cli、nop-lint-graphql、nop-lint-maven-plugin、nop-lint-nop、nop-js、nop-spring-demo/nop-spring-gateway、nop-graphql-grpc。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi12-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- 明确缺口模块补测：spring-core-starter、file-service、search-core、integration-api 各 ≥4 用例（可行者）。
- WI0 无报告遗留模块逐个处置：补测试或记录不可行裁定（lint 链先 `mvnq -- install -pl :nop-lint-nop -am -DskipTests` 再补跑）。
- nop-code-web NopCodeWebPagesTest 修复（IHttpClient 测试 bean 装配，测试面修复）。
- 已达标模块（autotest-core/file-dao/message-core/credential/bytecode/refactor-core/report-core/chart-export/lucene 等）不回退确认。
- 记录增量。

## Non-Goals

- 不修改产品代码；demo 模块（nop-spring-demo 等）只记录不裁定补测；nop-js（JS 构建链）只记录。

## Scope

### In Scope

- 上述模块的 `src/test/**` 新增与 pom test-scope 裁定；nop-code-web 测试装配修复（测试资源/测试 bean）。

### Out Of Scope

- 产品代码；JS 侧测试；demo 模块补测。

## Execution Plan

### Phase 1 - 补测与遗留处置

Status: planned
Targets: 缺口模块 `src/test/**`、WI0 遗留清单

- Item Types: `Fix`

- [ ] spring-core-starter、file-service、search-core、integration-api 各 ≥4 用例（模块结构不支持纯逻辑测试的，如实记录不可行裁定）。
- [ ] WI0 无报告遗留 8 项逐个处置（nop-rg-cli/vector 已由 WI0 裁定为 build-infra 独立项，不在本清单；lint 链先 `mvnq -- install -pl :nop-lint-nop -am -DskipTests` 再补跑 nop-lint-graphql/nop-lint-maven-plugin；其余记录裁定）。
- [ ] nop-lint-nop 普查测试修复（WI0 失败指派）：TestNopRuleSuites/TestProductionRuleCount/TestMetricsRuleSuites 断言更新至当前规则普查（69 套件/74 规则），逐个补入期望清单（不得整段删除断言或弱化"no silent drops"语义）。
- [ ] nop-code-web NopCodeWebPagesTest 修复转绿。
- [ ] 各模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 补测用例 ≥16 个或对应不可行裁定记录；遗留清单 8 项逐个有处置结果（rg 两项 WI0 已裁定移出）。
- [ ] nop-code-web 测试绿。
- [ ] pom 变更（如有）仅 test-scope 依赖且已记录。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与达标确认

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI12 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删模块 exec → baseline 脚本复测，记录增量。
- [ ] 已达标模块不回退确认；未达标残余显式裁定（移交 WI13 或接受）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI12 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI12 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 各模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码零修改；pom 仅 test-scope 新增且记录
- [ ] 覆盖增量实测记录
- [ ] WI0 遗留 8 项逐个处置（无静默跳过；rg 两项已由 WI0 裁定移出）
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：补测断言语义（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2304-unit-test-wi12-small-modules.md --strict` 退出码 0
- [ ] roadmap WI12 checkbox 与 plan/log 一致

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
