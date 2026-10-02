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
- nop-dataset 21.63% / 906L（lowCoverageClasses 仅 IDataParameters 接口 1 条——执行时发现其 default 方法即产品逻辑，已补测，见偏差 3）；nop-codegen 26.07% / 1289L（maven parse、graalvm config、CodeGenTask 等 10 个 0% 类）。
- **测试基建（对抗审查确认）**：nop-commons / nop-api-core / nop-dataset 现有测试为**裸 JUnit5**（无 BaseTestCase——它在 nop-core，而 nop-core 依赖 nop-commons，测试引入 BaseTestCase 需 test-scope 反向依赖 = 循环依赖 = 必须改 pom，禁止）。nop-codegen 依赖 nop-core，可用 BaseTestCase 档位。
- **测量管线约束（WI0 实测）**：四模块全部在 nop-kernel 无 parent 子树，`-Pcoverage test -pl :X` 不产 exec。**验证**用 `mvnq -- test -pl :<module> -am -fae`；**测量**统一走 `删模块 exec → ai-dev/tools/coverage-baseline.sh --skip-test --label wi2-2026-10-02`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- collections/tuple/service 域核心数据结构语义测试（扩容/边界/不变量/迭代器行为）。
- api-core 模型解析与 options 类语义测试。
- dataset/codegen 可纯逻辑测试类补测。
- 各模块 `mvnq -- test -pl :<module> -am -fae` 全绿；记录覆盖增量。

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

Status: completed
Targets: `nop-kernel/*/src/test/**`

- Item Types: `Fix`

- [x] nop-commons：collections/tuple/service 域 ≥8 个靶点类补测（裸 JUnit5，边界与不变量断言）——实际 8 个靶点类（MutableIntArray/LongHashMap/KeyedList/SetFunctions/IntArrayMap/ListFunctions/Tuple2/MutableLong）。
- [x] nop-api-core：options/json/schema/模型类 ≥6 个靶点补测（裸 JUnit5）——实际 7 个（MessageSubscribeOptions/MultiMessageSubscription/JsonParseOptions/JsonSchema/LogLevel/GraphQLResponseBean/FieldSelectionPrinter）。
- [x] nop-dataset + nop-codegen 合计 ≥4 个靶点补测——实际 5 个（dataset：IDataParameters default 方法；codegen：ReflectConfig/ProxyConfig/ReflectConfigGenerator/GraalvmConfigGenerator 路径契约）。
- [x] 四模块测试全绿（commons 350 tests、api-core 149、dataset 20、codegen 39；codegen 验证未带 -am，见偏差 1）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增/扩展测试类 ≥18 个，每个含显式语义断言——实际 20 个测试类。
- [x] 四模块测试全绿（既有测试零回归）。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI2 checkbox

- Item Types: `Proof` + `Decision`

- [x] 重建四模块覆盖报告：删各模块 `target/*.exec` → `coverage-baseline.sh --skip-test --label wi2-2026-10-02`，基线→复测：commons 23.97%→28.57%（+4.60）、api-core 29.29%→35.01%（+5.72）、dataset 21.63%→25.50%（+3.87）、codegen 26.07%→38.94%（+12.87）。
- [x] nop-record-mapping（58.16%→58.16%）与 nop-markdown（56.28%→56.28%）"已达标不回退"确认：复测与基线一致。
- [x] 残余缺口显式裁定：四模块均未达 55% 预设——deferred to WI13 复裁（Deferred 段）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI2 checkbox。

Exit Criteria:

- [x] 四模块增量数字记录在案（快照 coverage-baseline-wi2-2026-10-02.json）。
- [x] 残余缺口裁定有记录（Deferred 段）。
- [ ] roadmap WI2 checkbox 与 plan/log 一致（待 audit 后同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 四模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码/pom 零修改（git 证据：仅 src/test 新增）
- [x] 覆盖增量实测记录（基线 vs 复测）
- [x] 残余缺口显式裁定（无静默降级，见 Deferred 段）
- [x] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：新增测试断言语义而非仅实例化（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2294-unit-test-wi2-kernel-modules.md --strict` 退出码 0
- [ ] roadmap WI2 checkbox 与 plan/log 一致

## 执行偏差记录

1. nop-codegen 验证未带 `-am`：`-pl :nop-codegen -am` 会连带构建 nop-core，而 nop-core 当时含并行 WI1 工作流的未跟踪红测试（非本 plan 范围）。改用 `-pl :nop-codegen -fae` 对 .m2-repo 已安装依赖验证，codegen 自身 39 tests 全绿。
2. 共享文件（logs/roadmap/plan 勾选）按约束留给主会话处理。
3. dataset 超 plan 字面：plan 原记载"无可直接测试的具体类靶点"，执行发现 IDataParameters 接口 default 方法即产品逻辑，补测后该模块 lowCoverageClasses 清零（仅动 src/test，符合范围）。

## Deferred But Adjudicated

### 四模块残余覆盖缺口（距 55% 预设目标）

- Classification: `optimization candidate`（deferred to WI13 复裁）
- Why Not Blocking Closure: roadmap closure 规则允许"有记录的延期裁定"；本 WI 已按 plan 承诺完成靶点补测并实测增量（+3.87~+12.87）。残余缺口主体：commons 剩余 60 个低覆盖类中约 20 个为可续测纯逻辑（DefaultBitSet/BloomFilter/Lattice/Seq 等，WI13 或后继 WI 可续）、11 个并发域需并发测试基建、api-core 21 类中 4 个注解定义不可测、7 个接口需消费方触达、codegen 剩余多为 maven/构建期集成。以"55% 预设"对 utility 型模块是否合理，属 WI13 的分模块复裁事项。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 基线刷新与逐模块显式裁定）

## Non-Blocking Follow-ups

- 产品缺陷嫌疑 4 项（未修，已记录）：api-core GraphQLResponseBean.toErrorBean null Boolean 拆箱 NPE（@Disabled 测试钉住）、commons SetFunctions.concat 标量分支语义、SetFunctions.flatMap 标量分支疑似笔误、MutableIntArray.addAll 边界校验不一致——按 roadmap 硬边界走独立 bug 流程。

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）
