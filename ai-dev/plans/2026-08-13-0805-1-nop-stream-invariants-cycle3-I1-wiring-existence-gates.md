# Cycle 3 / I1 — 生产 wiring 存在性不变式沉淀（Runtime Service Wiring-Existence Invariant Gates）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：2 Blocker（Phase 2 (c) fail-fast 断言 core 依赖方向不可实现 / V1 首跑必红——`setKeyedStateBackend`/`setOperatorStateBackend` main 零调用点）+ 4 Major（ProcessOperator:46 消费方遗漏 / V1 调用点匹配规则未钉死 / 反射枚举无参数类型过滤 / `CepOperator.java:84` javadoc 误作接线点）+ 若干 Minor，全部修复；round 2：全部修复在案 + live 逐行复核一致（含 `internal-creation` 裁定、V1 成员访问形态规则、receiver 限定作用域、fail-fast 归属 cep/runtime 既有 E2E），残余 3 Minor（receiver 限定已并入；红证据记录 / Rule #25 编号引用）非阻塞，verdict 可转 active）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 3 / I1 行（Loop Rule 派生，触发证据 = open-audit P0-01/P1-02 先例链）；open-audit 总评 §1 `ai-dev/audits/2026-08-12-1217-open-audit-nop-stream-invariant-loop.md`（新失败族「运行时服务注入完整性」）；roadmap Follow-up Backlog「生产 wiring 存在性不变式门禁沉淀（Cycle 3 / I1 派生候选）」条目；plan `2026-08-13-0132-1-nop-stream-runtime-service-wiring.md` Deferred But Adjudicated 段（派生候选登记，Successor Required: yes）；invariant-catalog.md §5 既有六不变式先例；mission `nop-stream-invariant-loop.json`
> Related: 前置 `2026-08-13-0132-1-nop-stream-runtime-service-wiring.md`（修复落地，收口 2026-08-13，本 plan 触发条件）；后继 Cycle 3 / I2（red list 审计）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 3 / I1. 不变式沉淀（生产 wiring 存在性门禁）

## Purpose

将运行时服务注入完整性族（不变式 #7）沉淀为一等门禁并入 CI：接线点注册表 `wiring-registry.json`（服务注入 API × 生产接线点 × 消费方类枚举）+ JUnit `TestWiringExistenceInvariant`（API 面完备性 + 生产接线运行时连通断言 + 无服务 fail-fast 行为）+ mjs `scan-wiring` 子命令（V1 仅测试注入检测 / V2 消费方枚举完备性 / V3 失效接线点 / V4 新注入 API / V5 失效消费方）+ committed 回归测试。门禁全绿后为 Cycle 3 / I2 提供确定性 red list 基底。

## Current Baseline

- **派生前置（已满足）**：plan `2026-08-13-0132-1`（P0-01/P1-02 修复）已 completed（2026-08-13 收口，证据 = roadmap ✅ 段 + `ai-dev/logs/2026/08-13.md`）；roadmap Follow-up Backlog 候选条目登记在案（Status: `todo`，触发 = plan 1 执行收口后按 Loop Rule 评估）；Loop Rule「新族强制沉淀」预授权自动派生（PD-n 先例链）。
- **不变式 #7（草案）陈述**：「任何被 main 代码（非测试）消费的运行时服务注入 API（如 `AbstractStreamOperator.setProcessingTimeService` / `setTimeServiceManager` / `setStateBackend` / `setKeyedStateBackend` / `setOperatorStateBackend` / `setOutput` / `setSnapshotCallback`）必须存在生产接线点（main 代码调用方，含注入时机声明）；仅被测试代码注入（测试 mock 规避、生产零调用）→ 违约（红）。新增消费这些服务的 main 调用（getter 调用点所属新类）必须入接线点注册表，否则红。」
- **历史审计证据**：open-audit P0-01（`CepOperator.java:347` open → `registerCacheStatisticsTimer` :356-368 → `getProcessingTimeService().getCurrentProcessingTime()` :366 生产路径 null NPE）；P1-02（PT 窗口永不触发，`WindowOperator.java:479` 无 PTS 早退面）；测试规避机制 = 13 个测试类经 `CepTestUtils.injectProcessingTimeService` / 私有静态 `setProcessingTimeService` 包装器注入 mock，生产路径零 E2E（open-audit P0-01 详情）。
- **live 代码基线（2026-08-13 实测）**：
  - **注入 API 面（main）**：`AbstractStreamOperator.java` — `setOutput` :140 / `setProcessingTimeService` :148 / `setStateBackend` :152 / `setKeyedStateBackend` :165 / `setOperatorStateBackend` :173 / `setTimeServiceManager` :181 / `setSnapshotCallback` :320。**已裁定前置事实（live 实测）**：`setKeyedStateBackend` / `setOperatorStateBackend` 全仓 main **零调用点**（生产路径 = `open()` 内 `stateBackend.createKeyedStateBackend()` 直建，如 `CepOperator.java:257` 面）；`setStateBackend` main 调用点 = `GraphModelCheckpointExecutor:742`；其余四 API 有生产接线点（见下）。该差异必须显式裁定（Phase 1），不得静默（既不能偷偷 pin 也不能悄悄缩小 V1 规则）。
  - **生产接线点（main 调用方，全部 live 实测）**：`StreamTaskInvokable.setupProcessingTimeServices`（:456-468，`setProcessingTimeService` :462 + `setTimeServiceManager` :463，4 个构造函数调用 :151/:164/:180/:195；:475-481 为独立方法 `startProcessingTimeDriver`，不属接线面）；`StreamTaskInvokable.wireTailToRecordWriter`（:422-428，`setOutput` :426）与 `wireOperators`（布线面）；`StreamTaskInvokable.setupSnapshotCallbacks`（:430-438，`setSnapshotCallback` :435）；`GraphModelCheckpointExecutor`（:733-744，`setStateBackend` :742 状态后端供给，`checkpointConfig` 驱动）。
  - **消费方（main getter/注册调用点）**：`getProcessingTimeService()` — `AbstractStreamOperator.java:144`（getter 本体）、`StreamTaskInvokable.java:353`、`WindowOperator.java:479`、`CepOperator.java:290/:300/:373/:381/:382/:401/:479/:490`；`getTimeServiceManager()` — `StreamTaskInvokable.java:345`、`AbstractStreamOperator.java:177`（getter 本体）；`registerTimerService` — `WindowOperator.java:471` **与 `ProcessOperator.java:46`（core，main）**（`WindowOperator.java:467` 注释自称 mirroring ProcessOperator.open()——**ProcessOperator 是 TSM 四环节的镜像先例，必须入消费方枚举，不得遗漏**）。
- **既有门禁基础设施**：JUnit 门禁类命名模式 `Test*Invariant*`（10 类 / 102 tests，`cycle2-I1-input.md`）；mjs 工具 `check-nop-stream-invariants.mjs`（子命令 inventory / sync / scan-iterations / scan-output-contract / self-test / all；pin 匹配协议 = 违规串精确匹配 `p.key`；类级枚举先例 = `scan-output-contract` V1）；`mjs-pins.json` 结构在案；表完备性门禁先例 = `TestInvariantTableCompleteness`（per-class 语义）；E2E 基线 = `TestStreamTaskInvokableProcessingTimeWiring`（3 用例，invokable 级生产接线运行时证据）+ `TestProcessingTimeWindowProductionE2E` + `TestCepProductionExecutionE2E`。
- **设计文档先例**：`ai-dev/design/nop-stream/core-design.md` §6.1 不变式节（#6 输出契约族，Cycle 2 / I1 定稿）；window-design.md / cep-design.md 既有不变式节；`mailbox-design.md` §7 / `time-model-design.md` §10.3 已记录生产接线契约（plan 1 收口）。
- **真正剩余的 gap**：无任何门禁约束「服务注入完整性」维度（open-audit 总评：「恰好是 mission 已沉淀六条不变式都不覆盖的维度」）；无接线点注册表；无仅测试注入检测；新增消费运行时服务的 main 类/新注入 API 零防护；不变式 #7 未在 invariant-catalog.md 定稿。

## Goals

- 新增 JUnit 门禁类 `TestWiringExistenceInvariant`（**nop-stream-core**，仅实现类级断言——core 不可引用 runtime 的 `WindowOperator`/`GraphModelCheckpointExecutor`/cep 的 `CepOperator`，依赖方向约束）：注册表驱动的参数化断言——(a) **API 面完备性**：反射枚举 `AbstractStreamOperator` 全部 `set*` 注入方法（**参数类型过滤**：参数类型 ∈ 已登记服务类型，非服务注入 API 如 `setKeyContextElement1/2` 不参与），每个已登记服务注入 API 必须 ∈ 注册表（新注入 API 不入表即红）；(b) **接线运行时连通断言（Rule #23）**：生产路径（invokable 构造注入）下算子 `getProcessingTimeService()` / `getTimeServiceManager()` 非 null 且与 invokable 同一实例；(c) **无服务 fail-fast 行为（归属 cep/runtime 既有 E2E）**：`CepOperator` PT 模式显式抛 `StreamException` / `WindowOperator` open WARN 已由 `TestCepProductionExecutionE2E` / `TestProcessingTimeWindowProductionE2E` 断言（core 不可引用，归属定案，不建 core 副本）。
- **接线点注册表**：新文件 `ai-dev/audits/nop-stream-invariants/wiring-registry.json`（独立于 `init` 再生的 gate-inventory.json，防覆盖）——services 表每条 = {服务 fqcn, 注入 API（类:方法）, 生产接线点（文件:行 + 注入时机: 构造/checkpoint 装配/布线）, 消费方类枚举（main getter/调用点）, disposition, test-only 豁免}。
- **mjs `scan-wiring` 子命令**（纳入 `all` + `self-test` 正反例 fixtures）：
  - **V1 仅测试注入检测**：注册表每个服务注入 API 必须在 `src/main/java` 存在调用点；全仓调用点仅存在于 `src/test` → 红（直接捕获 P0-01 原始形态）
  - **V2 消费方枚举完备性**：`src/main/java` 中 `getProcessingTimeService()` / `getTimeServiceManager()` 等已登记服务 getter 调用点所属类必须 ⊆ 注册表消费方枚举；新增消费类 → 红
  - **V3 失效接线点**：注册表登记的生产接线点（文件:行）在 main 代码已不存在 → 红（强制诚实维护）
  - **V4 新注入 API**：main 代码出现新的 `set<Service>(...)` 形态注入 API（`AbstractStreamOperator` 子类声明面）不入注册表 → 红
  - **V5 失效消费方/失效服务**：注册表登记的消费方类或服务已不存在 → 红
  - **pin 匹配**：违规集合 ⊆ pins（`p.key` 精确匹配）= green；pin 只允许覆盖已裁定 watch-only residual / 已知豁免实例
- 门禁全绿（JUnit 门禁子集 0 failure + mjs `all` exit 0 + 既有 E2E 不回归）；**5 个生产接线服务（Output / PTS / TSM / stateBackend / snapshotCallback）V1 全绿 + 2 个 internal-creation 服务（keyedStateBackend / operatorStateBackend）显式裁定登记在案（非静默）**；invariant-catalog.md #7 定稿（状态 = 已沉淀）；`core-design.md` 不变式节更新（#7）；roadmap Cycle 3 / I1 行流转 `todo`→`planned`→`done`；`cycle3-I1-input.md` 落档（Cycle 3 门禁统计唯一落点）；committed 回归测试。
- 为 Cycle 3 / I2 提供确定性审计基底（门禁跨全部服务注入 API 与消费方跑 = 全绿基线，I2 以此 + 对抗探查产出 red list）。

## Non-Goals

- **不修复任何 wiring 行为**（包括 open-audit P2-03 `InMemoryClusterRegistry.getNodeLease` 无锁 NPE 等 backlog 条目——P2 批次已裁定「no P2-only plan；类别清扫/复探触发时评估」，属 I2 复探 + I3 裁决面，不在本 plan）。
- **不执行 checkpoint/watermark 服务"仅测试注入"实例的修复**（plan 1 Non-Blocking Follow-up 登记：同族其余服务复探 = 复探时评估——本 plan 只沉淀门禁（V1 检测面），复探动作属 I2，修复属 I3 裁决后的 I4）。
- **不改 `AbstractStreamOperator` / 任何公共接口 / 模块边界**（接口契约零变更；结构性重构 = 人工确认门）。
- **不执行 Cycle 3 / I2 审计**（red list 生成属下一 work item，消费本 plan 门禁）。
- **不引入 ArchUnit**（既有 deferred 裁定：`optimization candidate`，触发时另立 Decision）。
- **不修复本 plan 门禁首跑暴露的既有差异**（若首跑发现未登记的注入 API / 消费方 / 接线点：记录证据 + 分类——已知面遗漏 → 补注册表自洽（I1 补表先例）；新实例 / 新族 → 移交 I2 裁决；首次跑红 = 补表属 I1 内职责）。

## Scope

### In Scope

- 注册表设计定稿（`wiring-registry.json` schema：services 表 + 消费方表 + test-only 豁免清单；pin schema 沿用 `key` 协议）。
- JUnit 门禁类 `TestWiringExistenceInvariant`（core，API 面完备性 + 接线连通 + 无服务 fail-fast 断言）。
- mjs `scan-wiring` 子命令（V1–V5）+ `all` 纳入 + `self-test` fixtures。
- 门禁全绿验证 + 文档收口（catalog #7 定稿 / core-design.md 不变式节 / `cycle3-I1-input.md` 落档 / logs / roadmap 行流转）。

### Out Of Scope

- 任何 wiring / 服务注入行为修复（I4，经 I2/I3）。
- Cycle 3 / I2–I6（后续 work items）。
- 既有 10 门禁类内容改写（只增不弱化；表完备性维持既有 per-class 语义）。

## Execution Plan

### Phase 1 - 注册表与门禁语义设计定稿

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/wiring-registry.json`（新建，设计稿）、`invariant-catalog.md`（#7 草稿节）

- Item Types: `Decision`

- [x] 服务表定稿：live 枚举全部 operator 运行时服务注入 API（实测 7 个：`setOutput` / `setProcessingTimeService` / `setTimeServiceManager` / `setStateBackend` / `setKeyedStateBackend` / `setOperatorStateBackend` / `setSnapshotCallback`——以 `AbstractStreamOperator` :140/:148/:152/:165/:173/:181/:320 为基准，执行时以 live 复核为准）；每服务登记 = {服务 fqcn, 注入 API, 生产接线点（文件:行 + 注入时机：构造（PTS/TSM）/ 装配布线（Output）/ checkpoint 装配（state backend）/ 快照（snapshotCallback））, 消费方类枚举（live getter/注册调用点）, disposition}；test-only 注入豁免清单（仅扫描 `src/main/java`；test 目录 mock 注入不参与 V1 判定——**豁免语义 = 服务注入 API 在 main 有接线点且 test 另有 mock 注入 = 绿；main 零接线仅 test 注入 = 红**）
- [x] **`setKeyedStateBackend` / `setOperatorStateBackend` 显式裁定（Block-2 前置事实，不得静默）**：两 API 全仓 main 零调用点（生产经 `stateBackend.createKeyedStateBackend()` 在 `open()` 内直建）——裁定 = **design-intent 内部创建（internal-creation）**：注册表 disposition = `internal-creation`，V1 规则给出显式 carve-out（该 disposition 下 main 零调用点不红，理由 = 生产路径经 backend 工厂直建，setter 为测试注入用后门），**carve-out 必须随注册表登记理由并接受棘轮规则约束（弱化/豁免需人工确认 + 留痕）**；同时登记 JUnit 断言（API 面完备性仍须入表，V4 仍覆盖）；**禁止**以静默缩小 V1 规则或偷偷 pin 方式处理（违反棘轮 + pin 纪律）
- [x] 消费方表定稿：live 枚举 main 消费方（`getProcessingTimeService()` / `getTimeServiceManager()` / `registerTimerService` 调用点——`CepOperator` :290/:300/:373/:381/:382/:401/:479/:490、`WindowOperator` :471/:479、**`ProcessOperator` :46（registerTimerService，core）**、`StreamTaskInvokable` :345/:353 等，执行时以 live 复核为准）；每消费方 = {类, getter/注册调用点（文件:行）, 所属服务, 无服务时行为（fail-fast / WARN / 正常语义）}
- [x] pin schema 定稿（沿用 `mjs-pins.json` 既有 `key` 协议；本 plan 预期零 pin——`internal-creation` carve-out 是注册表 disposition 而非 pin，与已裁定 watch-only residual 实例（如 `TimestampsAndWatermarksOperator` 守卫形态）如需登记以 Phase 3 扫描器实际违规串为准）
- [x] 违规语义 V1–V5 定稿（见 Phase 3 定义；**V1 调用点匹配规则钉死**：调用点 = 成员访问形态（`\.setX\(` / `this.setX\(` 等），方法声明与 javadoc 不计——否则每个注册 API 的自身声明恒使 main 调用点 ≥ 1，V1 永不红（空转））+ test-only 豁免规则；`gate-inventory.json` 是否需补登记确认（既有 inventory 以 I0 §4.1 classifier 枚举变更型方法；本 plan 注册表与 mjs `scan-wiring` 单一职责，不改 gate-inventory.json schema）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 服务表 + 消费方表 + pin schema + 违规语义 V1–V5 定稿在案（`wiring-registry.json` 设计稿 + catalog #7 草稿节），每条 = repo-observable（文件:行 + 期望行为 + 断言方式）
- [x] 注入时机分类（构造 / 装配布线 / checkpoint 装配 / 快照）与 test-only 豁免语义（main 零接线仅 test 注入 = 红）已记录
- [x] 本 Phase 为设计定稿（Decision），不涉及行为变更 → `No owner-doc update required` 除 catalog #7 草稿节
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - JUnit 门禁实现（core，API 面完备性 + 接线连通；fail-fast 行为归属 cep/runtime 既有 E2E）

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/`（新增 `TestWiringExistenceInvariant`）；nop-stream-cep（`TestCepProductionExecutionE2E`）；nop-stream-runtime（`TestProcessingTimeWindowProductionE2E` / `TestStreamTaskInvokableProcessingTimeWiring` 保持绿，作为端到端证据）

- Item Types: `Fix | Proof`

- [x] **test-first**：新增 `TestWiringExistenceInvariant`，注册表驱动参数化断言——(a) **API 面完备性**：反射枚举 `AbstractStreamOperator` 全部 `set*` 注入方法，**过滤规则 = 参数类型 ∈ 已登记服务类型**（沿用 V4 语义——`setKeyContextElement1/2`（StreamRecord 参数）等非服务注入 API 不入表不误红），断言每个已登记服务注入 API ∈ 注册表（**新增注入 API 不入表即红**；分类以注册表为准，注册表更新须同步断言——I1 pin 翻转先例）→ 先红（未登记形态存在时）→ 按 Phase 1 表登记后转绿
- [x] **接线运行时连通断言（Rule #23）**：invokable 级装配——`new StreamTaskInvokable(chain, ...)` 后算子 `getProcessingTimeService()` 非 null + 与 `invokable.getProcessingTimeService()` 同一实例 + `getTimeServiceManager()` 非 null + `numTimerServices()` 运行时注册证据（沿 `TestStreamTaskInvokableProcessingTimeWiring` 既有 3 用例形态，门禁类内复述关键断言而非依赖外部类——门禁自洽原则）
- [x] **无服务 fail-fast 行为断言（归属定案，依赖方向约束）**：`CepOperator` PT 模式无服务显式抛 `StreamException`（`CepOperator.java:479-487` 面，throw 实际 :484-486）+ `WindowOperator` PT assigner 无 PTS open WARN（:479 面）**不可在 core 测试类断言**（nop-stream-core 不可引用 cep/runtime 类，编译即失败）——归属 = **cep/runtime 既有 E2E 已断言**（`TestCepProductionExecutionE2E`「PT 模式无服务 fail-fast」用例 + `TestProcessingTimeWindowProductionE2E` 断言面），本 phase 保持其全绿并复核断言存在（不新增 core 内副本，不新增 cep/runtime 门禁类——避免测试膨胀，先例 = I1「端到端证据归属定案」）
- [x] **端到端证据（Rule #22，归属定案）**：生产接线端到端路径（env → invokable 构造注入 → 驱动 fire → task 线程回调 → 窗口 fire）由 **nop-stream-runtime 既有 `TestProcessingTimeWindowProductionE2E`（3 用例）** + **`TestCepProductionExecutionE2E`（cep）** 承担——core 门禁类不可引用 runtime（依赖方向约束）；本 phase 保持全绿不回归，不新增 core 内 E2E 副本
- [x] 无静默跳过（Rule #24）：门禁用例验证每个服务注入 API 有明确语义（生产接线点存在 / fail-fast / 注册表登记）；新增注入 API 默认红

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestWiringExistenceInvariant` 已提交（参数化 API 面完备性（参数类型过滤）+ 接线连通断言），先红后绿证据在案
- [x] **端到端验证**：`TestProcessingTimeWindowProductionE2E`（runtime）3/3 + `TestCepProductionExecutionE2E`（cep）4/4 保持绿（生产接线 → 驱动 → 回调完整路径断言在案；**含 PT 模式无服务 fail-fast 断言复核——fail-fast 行为归属 cep/runtime 既有 E2E，非 core 副本**）
- [x] **接线验证**：invokable 构造注入 ↔ 算子 getter 运行时连通断言在案（同一实例 + numTimerServices 注册证据）
- [x] **无静默跳过**：无空体 / continue / 吞异常作为正常实现；服务缺失路径显式 fail/WARN 已由归属测试断言（core 门禁覆盖面 = API 完备性 + 接线连通；fail-fast 行为由 cep/runtime E2E 覆盖，归属在案）
- [x] 相关 `ai-dev/design/`（core-design.md 不变式节）与 `invariant-catalog.md`（#7）已更新或明确记账到 Phase 4
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - mjs `scan-wiring` 子命令

Status: completed
Targets: `ai-dev/tools/check-nop-stream-invariants.mjs`、`ai-dev/audits/nop-stream-invariants/wiring-registry.json`（实现）、`mjs-pins.json`、`fixtures/`

- Item Types: `Fix | Proof`

- [x] 新增子命令 `scan-wiring`，静态解析 `src/main/java`（沿用 `scan-output-contract` 的 brace-depth 嵌套类解析先例）：
  - **V1 仅测试注入检测**：注册表每个服务注入 API（如 `setProcessingTimeService`）的调用点集合（全模块 `src/main` + `src/test` 双面扫描）——**调用点 = 成员访问形态（`\.setX\(` / `this\.setX\(`），方法声明与 javadoc 不计**（否则每个注册 API 的自身声明恒使 main 调用点 ≥ 1，V1 永不红）；**匹配作用域 = 按注册表「类:方法」标识限定接收者类型**（如 `setStateBackend` 只匹配 `AbstractStreamOperator` 子类面上的 `\.setStateBackend\(`，不匹配 `CheckpointConfig.setStateBackend` 等其他类同名方法——避免假绿；登记为执行时以扫描器 receiver 解析结果为准并留痕）；main 调用点 = 0 且 test 调用点 > 0 → 红（违规串含服务名 + 注入 API + 全仓调用点统计）；main 调用点 > 0 → 绿（无论 test 是否另有 mock 注入）；**carve-out**：disposition = `internal-creation` 的服务（`setKeyedStateBackend` / `setOperatorStateBackend`，Phase 1 已裁定）main 零调用点不红（理由登记于注册表，受棘轮约束）
  - **V2 消费方枚举完备性**：注册表已登记服务 getter（`getProcessingTimeService()` / `getTimeServiceManager()`）**与 `registerTimerService(` 调用**在 `src/main/java` 的调用点所属类集合必须 ⊆ 注册表消费方枚举（**`ProcessOperator`（:46 registerTimerService）与 `WindowOperator`（:471）都在枚举面**）；新类 → 红
  - **V3 失效接线点**：注册表登记的生产接线点（文件:行）已不存在 → 红
  - **V4 新注入 API**：`AbstractStreamOperator`（或其 main 子类面）出现未登记 `set*` 注入方法（**参数类型 = 已登记服务类型或新服务类型**——`setKeyContextElement1/2`（StreamRecord 参数）等非服务注入 API 不触发）→ 红
  - **V5 失效消费方/失效服务**：注册表登记的消费方类或服务在代码中已不存在 → 红
  - **pin 匹配**：违规集合 ⊆ pins（`p.key` 精确匹配）= green；pin 只允许覆盖已裁定豁免实例（预期零 pin 起步；`internal-creation` 为注册表 disposition 而非 pin）
- [x] 注册表实现：`wiring-registry.json`（services 表 + 消费方表 + test-only 豁免清单），schema 与 Phase 1 定稿一致；`mjs-pins.json` 头注语义扩展（现有注声明仅 #2/#6 语义，扩为含 #7）——如有豁免 pin，含 `key` + 关联裁定 + removalTrigger
- [x] `self-test` 扩展：正反例 fixtures 覆盖 V1–V5（正例 = 已接线服务 / 注册表自洽；反例 = 仅测试注入 → 红；main 零调用仅 test 调用 → 红；新消费类未登记 → 红；接线点删除 → 红；新注入 API 未登记 → 红）——证明扫描器无静默跳过
- [x] `all` 纳入 `scan-wiring`；工具帮助头注释更新；`ai-dev/tools/README.md` 工具清单同步

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs scan-wiring` 独立运行 exit 0；V1–V5 语义在代码注释 + 帮助头中可查
- [x] `self-test` 含 `scan-wiring` 正反例（fixtures 在案，覆盖 V1–V5 各一正一反）
- [x] `all` exit 0（含新子命令）
- [x] 无静默跳过：扫描器对未识别形态（嵌套类解析失败 / 未登记服务 getter）显式 fail（不静默归类）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 门禁全绿验证与文档收口

Status: completed
Targets: `nop-stream/` 门禁子集、`ai-dev/audits/nop-stream-invariants/invariant-catalog.md`、`ai-dev/design/nop-stream/core-design.md`、roadmap、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 门禁子集全绿：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 0 failure（含新增 `TestWiringExistenceInvariant`；依赖本地仓库 SNAPSHOT，遇不可解析先 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装，I5 先例）
- [x] 既有 E2E 不回归：`TestProcessingTimeWindowProductionE2E`（runtime）+ `TestCepProductionExecutionE2E`（cep）绿
- [x] mjs 全子命令 exit 0：`node ai-dev/tools/check-nop-stream-invariants.mjs all`（含 inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test）
- [x] 门禁统计落档：**新建 `ai-dev/audits/nop-stream-invariants/cycle3-I1-input.md`**（Cycle 3 门禁统计唯一落点，供 Cycle 3 / I2 消费；`cycle1-I6-input.md` / `cycle2-I1-input.md` 已被消费，不回写不追加）——新门禁类测试数、总门禁类数（10 → 11）、pin 明细
- [x] `invariant-catalog.md`：#7 定稿（不变式陈述 / 覆盖失败族（运行时服务注入完整性）/ 历史证据（open-audit P0-01/P1-02 + plan 1 修复基线）/ 检测方法 JUnit + mjs / 状态 = 已沉淀）
- [x] `ai-dev/design/nop-stream/core-design.md` 不变式节补 #7（先例 = §6.1 #6 输出契约族）
- [x] roadmap Cycle 3 / I1 行状态流转 `todo`→`planned`（激活时）→`done`（closure audit 通过后，不得提前）；`ai-dev/logs/` 顶部条目更新
- [x] committed 回归测试（门禁类 + 注册表 + fixtures + 工具 + catalog 同 commit，防漂移）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] JUnit 门禁子集 0 failure（11 门禁类，含新增）+ 既有 E2E 绿
- [x] mjs `all` exit 0（含 `scan-wiring`）
- [x] `cycle3-I1-input.md` 落档（门禁 10 → 11 类 / 新增测试数 / pin 明细，唯一落点原则）
- [x] `invariant-catalog.md` #7 定稿 + `core-design.md` 不变式节 + roadmap 行流转在案
- [x] 测试统计 / 门禁统计已写入 `ai-dev/logs/`
- [x] 无静默跳过：全部新增扫描/断言路径有正反例或显式 fail 语义

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] plan `2026-08-13-0132-1` 已完成（派生前置）；门禁范围与 open-audit 总评 §1 / roadmap 候选条目一致
- [x] 不变式 #7 已沉淀为一等门禁（JUnit + 接线点注册表 + mjs `scan-wiring`），全绿零命中
- [x] V1 仅测试注入检测已锁定 P0-01 原始形态（注入 API main 零调用点 → 红，调用点 = 成员访问形态）；5 个生产接线服务全绿 + 2 个 `internal-creation` 服务（`setKeyedStateBackend` / `setOperatorStateBackend`）显式裁定登记（理由在案，受棘轮约束，非静默豁免）
- [x] 端到端 + 接线验证通过（`TestProcessingTimeWindowProductionE2E` 3 用例 + `TestCepProductionExecutionE2E` 4 用例含 PT 无服务 fail-fast 断言复核；invokable 构造注入 ↔ 算子 getter 连通断言）
- [x] 无静默跳过：门禁/扫描器对未实现路径显式 fail；无空体作为正常实现
- [x] 文档收口：catalog #7 定稿 + core-design.md 不变式节 + roadmap 行流转 + `cycle3-I1-input.md` 落档 + logs 记录
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（I1 内发现的未知注入 API / 消费方 → 补注册表自洽或移交 I2 裁决，不静默忽略）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）`TestWiringExistenceInvariant` 与 `scan-wiring` 在 `all` 路径运行时确实执行（非只存在文件），（b）端到端断言真实断言生产接线行为（E2E 用例在案），（c）无空方法体 / 静默跳过作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` findings 集与既有基线一致（14 项既有债，零新增——沿 Cycle 2 / I1 钉定判据 `34aed42c1`）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 通过（0 failure）
- [x] `./mvnw compile -pl nop-stream/nop-stream-core -q` 通过（新增测试类可编译）

## Deferred But Adjudicated

### 层 1 — checkpoint / watermark 服务"仅测试注入"实例复探与修复

- Classification: `out-of-scope improvement`（同族复探 = plan `2026-08-13-0132-1` Non-Blocking Follow-up 登记，roadmap Loop Rule 复探触发面）
- Why Not Blocking Closure: 本 plan 只沉淀门禁检测面（V1 使"仅测试注入"成为可执行红条件）；复探动作属 Cycle 3 / I2（roadmap I2 行已登记「checkpoint/watermark 服务同族实例复探」），修复属 I3 裁决后的 I4。门禁落地前已由 plan 1 修复锁定 PTS/TSM 行为，不依赖复探结果。
- Successor Required: `yes`
- Successor Path: Cycle 3 / I2（复探 + red list）→ Cycle 3 / I3（裁决）→ Cycle 3 / I4（修复）

### I1 门禁首跑暴露的未知注入 API / 消费方 / 接线点（如有）

- Classification: `watch-only residual`（待 I2 裁决，非本 plan 处置项）
- Why Not Blocking Closure: 若 `scan-wiring` / 门禁首跑暴露未知服务注入 API / 消费方 / 接线点，记录证据 + 分类（已知面遗漏 → 补注册表自洽；新实例 / 新族 → 移交 I2 裁决），不静默忽略、不本 plan 修复（I1 职责边界先例 = Cycle 2 / I1「I1 门禁首跑暴露的未知违约实例」裁定）。
- Successor Required: `yes`
- Successor Path: Cycle 3 / I2（red list 生成 + 裁决）

## Non-Blocking Follow-ups

- `TimestampsAndWatermarksOperator` 静默守卫形态（:82-84）接线后自然失效复核 = Cycle 3 / I2 探查项（plan 1 watch-only residual 延续）。
- ArchUnit 架构约束门禁（`optimization candidate`，触发时另立 Decision，先例延续）。

## Closure

Status Note: 不变式 #7 沉淀（JUnit `TestWiringExistenceInvariant` 10 用例 + 接线点注册表 `wiring-registry.json`（7 服务，5 production-wired + 2 internal-creation 显式裁定）+ mjs `scan-wiring` V1–V5）全绿（JUnit 11 门禁类 112 tests / 0 failures + mjs `all` exit 0（pin 0）+ E2E 3/3 + 4/4 + 全量 2895 tests / 0 failures）、文档收口（catalog #7 定稿 + core-design.md §6.1 + roadmap 行 `done` + `cycle3-I1-input.md` 落档 + logs）、独立 closure audit APPROVE（fresh session）。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_0074568d2ffeVMwgbssBj4O920`，general agent，closure audit 独立于实现会话）
- Evidence:
  - Phase 1–3 Exit Criteria：4/4 + 6/6 + 5/5 PASS（live 证据：wiring-registry.json 7 服务含 injectionApi/wiringPoints(文件:行+注入时机)/consumerTable/dispositions；TestWiringExistenceInvariant 10 用例含参数化 API 面完备性 :162-210 / 反射完备性 :259-276 / 消费方表自洽 :296-325 / 接线连通 :336-359；mjs scan-wiring 帮助头语义 :944-987 + self-test fixtures V1–V5 正反例 :1657-1787 + `all` 纳入 :1958-1964；Phase 1-4 logs 在案）
  - Phase 4 Exit Criteria：8/8 PASS（live 复跑：JUnit 门禁子集 11 类 / 112 tests / 0 failures；E2E 3/3 + 4/4（含 fail-fast/WARN 断言复核）；mjs all/scan-wiring/self-test exit 0；cycle3-I1-input.md 落档（10→11 类 / pin 0）；catalog #7 = 已沉淀 :311；core-design §6.1 #7 :362-371；roadmap Cycle 3/I1 行 `done` + backlog 条目 ✅；committed 回归测试 = 本 closure run 提交）
  - Closure Gates：14/14 PASS（前置 plan `2026-08-13-0132-1` completed；#7 一等门禁全绿零命中；V1 锁定 P0-01 形态 + internal-creation 裁定在案（理由受棘轮约束）；E2E + 接线验证复跑；无静默跳过；文档收口齐备；deferred 分类诚实（out-of-scope improvement / watch-only residual 均附理由 + successor）；Anti-Hollow PASS（`all` → runScanWiring → evaluateWiring 同路径，fixtures 双向验证；scratch 红演示：wiring-point 行号 99999 → V3 违规 exit 1；JUnit/E2E 断言非空体）；`check-plan-checklist.mjs --strict` exit 0；`check-doc-links.mjs --strict` exit 0（0 errors，11 warnings 均为他 plan 前置引用非本 plan）；`scan-hollow-implementations.mjs --severity high` findings = 14 项既有债基线，本次改动零命中；门禁子集 0 failure；`compile -pl nop-stream/nop-stream-core` exit 0）
  - Anti-Hollow 检查结果：端到端调用链（all → scan-wiring → evaluateWiring；fixtures 正反例同代码路径）连通；E2E 断言真实（WARN ListAppender + StreamException fail-fast）；无空方法体/静默跳过
  - Deferred 项分类检查：确认无 in-scope live defect 被降级（层 1 checkpoint/watermark 复探 = out-of-scope improvement（V1 使其成为可执行红条件，复探属 I2）；未知 API/消费方 = watch-only residual（补表自洽或 I2 裁决）；TimestampsAndWatermarksOperator = I2 探查项；ArchUnit = optimization candidate）
  - 独立审计 verdict：**APPROVE**（唯一残留 = 实现后机制：勾选 Phase 4/Closure Gates + 填 Closure evidence + commit + Plan Status completed——本 closure run 完成）

Follow-up:

- no remaining plan-owned work（非阻塞：checkpoint/watermark 服务同族"仅测试注入"复探 = Cycle 3 / I2 探查项；`TimestampsAndWatermarksOperator` 静默守卫形态复核 = I2 watch-only 探查项；ArchUnit = optimization candidate）
