# 2293 unit-test-coverage-roadmap WI1 — nop-core 补强

> Plan Status: completed
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

Status: completed
Targets: `nop-kernel/nop-core/src/test/**`

- Item Types: `Fix`

- [x] 读取 WI0 快照靶点并选定可纯逻辑测试的 0% 具体类（stat 全域、reflect impl/aop、query 全域、json 3 类、graph 3 算法、StyleMap、XNode 两 Helper、CodeLangMap、NamedExceptionFilter、ModelBasedValidator、DefaultResourceRegion、DefaultTaskExecutionQueue、TccContext、ServiceContextImpl 等）。
- [x] 新增 25 个测试类 / 192 个 @Test 方法，全部含显式语义断言（失败路径/边界值/不变量），无仅实例化测试。
- [x] `mvnq -- test -pl :nop-core -am -fae` 退出码 0（BUILD SUCCESS，491 tests / 0F / 0E，既有测试零回归；中间态曾以两步等价验证绕过并行 agent 的上游临时红，见偏差 1，最终门已按 plan 命令复核）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增/扩展测试类 ≥10 个——实际 25 个，每个含显式语义断言。
- [x] `mvnq -- test -pl :nop-core -am -fae` 退出码 0。
- [x] No owner-doc update required（纯测试增量）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`（复测快照）、`ai-dev/backlog/unit-test-coverage-roadmap.md`（WI1 checkbox）

- Item Types: `Proof` + `Decision`

- [x] 重建 nop-core 覆盖报告（同上管线，快照 coverage-baseline-wi1-2026-10-02.json）：行覆盖 36.59%→44.05%（+7.46pp / +2243 行），分支 29.21%→34.54%；全仓加权 56.92%→57.96%。
- [x] 按 roadmap closure 规则裁定：44.05% < 55% 目标——deferred to WI13 复裁（Deferred 段含残余 111 个低覆盖类的分类统计与理由）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI1 checkbox。

Exit Criteria:

- [x] 复测数字与增量记录在案（36.59% → 44.05%）。
- [x] 残余缺口裁定有记录（Deferred 段）。
- [x] roadmap WI1 checkbox 与 plan/log 三处一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 纯测试增量计划：`./mvnw compile` 不适用（无产品代码）；`mvnq -- test -pl :nop-core -am -fae` 即构建验证门。

- [x] 全部新增测试绿（含既有测试零回归：491/0F/0E）
- [x] 产品代码/pom 零修改（git 证据：仅 src/test 新增 25 文件）
- [x] 覆盖增量实测记录（36.59%→44.05%）
- [x] 残余缺口显式裁定（无静默降级，见 Deferred 段）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：新增测试断言语义而非仅实例化（audit 抽查：TestDefaultTaskExecutionQueue 全 @Timeout+有界 get、TestFilterOpHelper 边界/null 语义/失败路径断言）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2293-unit-test-wi1-nop-core.md --strict` 退出码 0
- [x] roadmap WI1 checkbox 与 plan/log 一致

## 执行偏差记录

1. 验证门等价执行（中间态）：执行期间 `-am` 链上的 nop-commons 被并行 WI2 工作流进行中测试短暂编译失败阻断，采用两步等价验证（install 上游 -Dmaven.test.skip=true + 单模块迭代）；并行工作完成后已用完整 plan 门命令复核退出码 0。
2. WI0 快照 `lowCoverageClasses` 存在 `.slice(0,60)` 截断（coverage-baseline.mjs:133）——复测后剩余低覆盖类实为 111 个（自 jacoco XML 全量解析）。WI13 刷新时建议解除截断口径。

## Deferred But Adjudicated

### nop-core 残余覆盖缺口（44.05% vs 55% 预设目标）

- Classification: `optimization candidate`（deferred to WI13 复裁）
- Why Not Blocking Closure: roadmap closure 规则允许"有记录的延期裁定"。本 WI 完成 25 类/192 用例，行覆盖 +7.46pp；残余 111 个低覆盖类分类：接口 default 方法 9、抽象表模型族 ~16（需构造具体表格模型）、_gen 生成物 6（不应作靶点）、需 VFS/容器/IO ~18（违反纯逻辑/防挂起约束）、其余纯逻辑可测 ~62（ErrorMessageManager/Underscore/BeanDiffer/PropertyAccessor 族等，可作后继 WI 靶点池）。55% 预设对 nop-core 的合理性（含生成物/容器域占比）属 WI13 分模块复裁事项。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 基线刷新与逐模块显式裁定）

## Non-Blocking Follow-ups

- 6 项产品缺陷嫌疑走独立 bug 流程（见 ai-dev/bugs/2026-10/2026-10-02-wi1-defect-suspects.md）：FilterOpHelper.dateBetween max 失效、FilterOpHelper.like 方向反、ModifierBuilder.PUBLIC_MASK 恒 0、GlobalStatManager 排序方向反、AStarPathFinder scoreMap 未写入、低危观察 3 项（JdbcSqlStat.compareTo 懒加载、AnnotationData 噪声键、FilterBeanFormatter useFunctionCall 缺右括号）。

## Closure

Status Note: 25 测试类/192 用例全绿（491/0F/0E），nop-core 36.59%→44.05% 与 jacoco.xml 原始 counter 逐位一致，产品代码零修改，缺陷嫌疑经源码逐字核实（FilterOpHelper.java:269 确为 toLocalDate(min) 误写），Deferred 分类经独立全量重现 111 类证实合规。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_72176ad4-cbf1-40e7-b832-068a2ba73ded，fresh session）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量，失败可单独删除测试文件回滚；不触及产品行为。
