# 2294 unit-test-coverage-roadmap WI2 — nop-commons / nop-api-core / kernel 小模块补强

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI2 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

补强 nop-kernel 组内除 nop-core/nop-xlang 外的模块：nop-commons（23.97%）、nop-api-core（29.29%）、nop-dataset（21.63%）、nop-codegen（26.07%）；nop-record-mapping（58.16%）与 nop-markdown（56.28%）已达标仅确认。helpers/util 边角值域与 api-core 模型解析优先，全部纯逻辑档位。

## Current Baseline

- nop-commons 23.97% 行 / 19057L；低覆盖靶点：MutableIntArray 195L/0%、LongHashMap 163L/0%、KeyedList 125L/0%、SetFunctions 116L/0%、tuple/collections 域大量 0%。
- nop-api-core 29.29% 行 / 7357L；靶点：MessageSubscribeOptions、MultiMessageSubscription、JsonParseOptions、JsonSchema 等（注意 PropMeta/Label/MetricField 为注解定义，不可实例化，不作为靶点）。
- nop-dataset 21.63% / 906L（lowCoverageClasses 仅 IDataParameters 接口 1 条——无可直接测试的具体类靶点，增量主要来自被其他模块测试间接触达或放弃，如实记录）；nop-codegen 26.07% / 1289L（maven parse、graalvm config、CodeGenTask 等 10 个 0% 类）。
- **测试基建（对抗审查确认）**：nop-commons / nop-api-core / nop-dataset 现有测试为**裸 JUnit5**（无 BaseTestCase——它在 nop-core，而 nop-core 依赖 nop-commons，测试引入 BaseTestCase 需 test-scope 反向依赖 = 循环依赖 = 必须改 pom，禁止）。nop-codegen 依赖 nop-core，可用 BaseTestCase 档位。
- **测量管线约束（WI0 实测）**：四模块全部在 nop-kernel 无 parent 子树，`-Pcoverage test -pl :X` 不产 exec。**验证**用 `mvnq -- test -pl :<module> -am -fae`；**测量**统一走 `删模块 exec → ai-dev/tools/coverage-baseline.sh --skip-test --label wi2-2026-10-02`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- collections/tuple/service 域核心数据结构语义测试（扩容/边界/不变量/迭代器行为）。
- api-core 模型解析与 options 类语义测试。
- dataset/codegen 可纯逻辑测试类补测。
- 各模块 `mvnq -- -Pcoverage test -pl :<module> -fae` 全绿；记录覆盖增量。

## Non-Goals

- 不修改产品代码；不为 GraalVM config 生成器的构建期行为强凑测试；不强求一次达到 55%（残余按 closure 规则显式裁定）。

## Scope

### In Scope

- `nop-kernel/nop-commons|nop-api-core|nop-dataset|nop-codegen/src/test/**` 新增测试与资源。
- 覆盖增量实测记录。

### Out Of Scope

- 产品代码、pom、生成模板；nop-core/nop-xlang（WI1/WI3）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: in progress
Targets: `nop-kernel/*/src/test/**`

- Item Types: `Fix`

- [ ] nop-commons：collections/tuple/service 域 ≥8 个靶点类补测（裸 JUnit5 写法，含边界与不变量断言）。
- [ ] nop-api-core：options/json/schema/模型类 ≥6 个靶点补测（裸 JUnit5）。
- [ ] nop-dataset + nop-codegen 合计 ≥4 个靶点补测（dataset 无具体类靶点，以 codegen 为主；dataset 侧如实记录不可测结论）。
- [ ] 四模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增/扩展测试类 ≥18 个，每个含显式语义断言。
- [ ] 四模块测试全绿（既有测试零回归）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI2 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 重建四模块覆盖报告：删各模块 `target/*.exec` → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi2-2026-10-02`，记录基线→复测增量。
- [ ] nop-record-mapping（58.16%）与 nop-markdown（56.28%）"已达标不回退"确认：对比复测快照数字（无新增测试，数字应与基线一致或更高）。
- [ ] 残余缺口显式裁定（达标 / deferred with reason）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI2 checkbox。

Exit Criteria:

- [ ] 四模块增量数字记录在案。
- [ ] 残余缺口裁定有记录。
- [ ] roadmap WI2 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 四模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码/pom 零修改（git 证据）
- [ ] 覆盖增量实测记录（基线 vs 复测）
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：新增测试断言语义而非仅实例化（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2294-unit-test-wi2-kernel-modules.md --strict` 退出码 0
- [ ] roadmap WI2 checkbox 与 plan/log 一致

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

- Risks And Rollback: 纯测试增量，可单独删除回滚。
