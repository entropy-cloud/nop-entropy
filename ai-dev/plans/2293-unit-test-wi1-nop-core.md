# 2293 unit-test-coverage-roadmap WI1 — nop-core 补强

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI1 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

为 nop-core（WI0 实测行覆盖 36.59%，30072 行，既有 61 个 Test* 测试类，以 live 为准）补充纯逻辑域单元测试，优先覆盖 roadmap 点名域（resource/VFS 加载与 Delta 合并、config 体系、entity 元模型、XML/JSON 解析边界、错误码注册），全部 `BaseTestCase` 级（无 DB 无 IoC），protected area 产品代码零修改。

## Current Baseline

- nop-core：36.59% 行 / 29.21% 分支，30072 行，src/test 现有 67 个 .java（61 个 Test*.java，以 live 为准）。基线复现：`ai-dev/tools/coverage-baseline.sh`（WI0 管线）。
- **测量管线约束（WI0 实测，对抗审查确认）**：nop-core 的 pom 链（nop-core→nop-kernel，组 pom 无 `<parent>`）不含 coverage profile，`mvnq -- -Pcoverage test -pl :nop-core` 是 no-op、不产 exec。**验证**用 `mvnq -- test -pl :nop-core -am -fae`；**测量**统一走 WI0 补跑管线：删该模块 `target/*.exec` → `ai-dev/tools/coverage-baseline.sh --skip-test --out ai-dev/analysis/2026-10 --label wi1-2026-10-02`（脚本内部以显式 `jacoco:prepare-agent -Djacoco.propertyName=jacocoArgLine -Djacoco.excludes=<root 口径>` 补跑 + 报告 + 解析）。
- mvnq = `ai-dev/tools/mvnq`（worktree 内 Maven 排队启动器）。偏差声明：roadmap 验证门写作 `./mvnw test -pl <module> -am`，本 plan 用 mvnq 等价执行（队列防冲突）。
- WI0 快照 `ai-dev/analysis/2026-10/coverage-baseline-2026-10-02.json` 中 nop-core 的 `lowCoverageClasses` 列 60 个低覆盖类。可测靶点示例（具体类优先，接口/抽象壳除外）：stat 域 JdbcSqlStat（391L/0%）、JdbcSqlStatValue、GlobalStatManager；reflect 域 AnnotationData（51L/0%）、ModifierBuilder（46L/0%）、AopAnnotationsLoader（34L/0%）。注意 IRpcStatManager/IJdbcStatManager 为接口不可直接覆盖，不作为靶点。
- 测试基建：既有 17 个测试 `extends BaseTestCase` + `CoreInitialization.initialize()/destroy()`（testing.md 纯逻辑档位），写法照此参照。
- nop-core 属 protected area（AGENTS.md）：本 WI 只新增 `src/test` 下文件与测试资源。

## Goals

- 新增纯逻辑单元测试覆盖 roadmap 点名域的公开语义：ResourceComponentManager/ComponentModelLoader 组件模型加载、DeltaResourceStore 合并语义、VfsConfigLoader 配置加载、XNode/JSON 解析边界、ErrorMessageManager 错误码注册语义、stat 域可测具体类。
- 断言业务语义而非凑行数：每个测试类至少一个显式语义断言（失败路径/边界值/不变量）。
- `mvnq -- test -pl :nop-core -am -fae` 全绿；经 WI0 补跑管线复测行覆盖并记录增量。

## Non-Goals

- 不修改任何产品代码（protected area 零修改）；测试暴露缺陷 → 记 `ai-dev/bugs/` 流程。
- 不强求一次达到 55% 目标——残余缺口按 roadmap closure 规则显式裁定（deferred → WI13 复裁或接受）。
- 不为 stat 域需要容器/后台线程的类强写挂起风险测试（遵守 testing.md 防挂起六规则）。

## Scope

### In Scope

- `nop-kernel/nop-core/src/test/java/**` 新增测试类与 `src/test/resources` 测试资源。
- 覆盖增量实测记录（基线 vs 复测）。

### Out Of Scope

- nop-core 产品代码、生成模板、pom。
- 需要容器/DB/IoC 的域（网关、cluster 等）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: planned
Targets: `nop-kernel/nop-core/src/test/**`

- Item Types: `Fix`

- [ ] 读取 WI0 快照 nop-core `lowCoverageClasses`，选定 ≥10 个可纯逻辑测试的 0% 具体类作为靶点（stat/reflect/resource/config/error 域优先；接口、抽象壳类、antlr 生成物除外）。
- [ ] 为每个靶点编写/扩展测试类，含正常路径 + 边界 + 错误路径断言；遵守 testing.md（BaseTestCase 初始化/销毁、@Timeout 防挂起、命名 Test*）。
- [ ] `mvnq -- test -pl :nop-core -am -fae` 全绿（含既有测试零回归）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增/扩展测试类 ≥10 个，每个含显式语义断言。
- [ ] `mvnq -- test -pl :nop-core -am -fae` 退出码 0。
- [ ] No owner-doc update required（纯测试增量）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`（复测快照）、`ai-dev/backlog/unit-test-coverage-roadmap.md`（WI1 checkbox）

- Item Types: `Proof` + `Decision`

- [ ] 重建 nop-core 覆盖报告：删该模块 `target/*.exec` → `ai-dev/tools/coverage-baseline.sh --skip-test --out ai-dev/analysis/2026-10 --label wi1-2026-10-02`（WI0 补跑管线），重新解析快照并记录行覆盖增量。
- [ ] 按 roadmap closure 规则裁定：达标（≥55%）或显式记录残余缺口与延期/接受理由（写入本 plan Deferred 段 + roadmap WI1 勾选注记）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI1 checkbox。

Exit Criteria:

- [ ] 复测数字与增量记录在案（基线 36.59% → 复测 X%）。
- [ ] 残余缺口裁定有记录（达标或 deferred with reason）。
- [ ] roadmap WI1 checkbox 与 plan/log 三处一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 纯测试增量计划：`./mvnw compile` 不适用（无产品代码）；`mvnq -- test -pl :nop-core -am -fae` 即构建验证门。

- [ ] 全部新增测试绿（含既有测试零回归）
- [ ] 产品代码/pom 零修改（git 证据）
- [ ] 覆盖增量实测记录（基线 vs 复测）
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：新增测试断言语义而非仅实例化（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2293-unit-test-wi1-nop-core.md --strict` 退出码 0
- [ ] roadmap WI1 checkbox 与 plan/log 一致

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

- Risks And Rollback: 纯测试增量，失败可单独删除测试文件回滚；不触及产品行为。
