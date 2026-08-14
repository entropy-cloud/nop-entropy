# Cycle 2 / I1 — 输出契约族不变式沉淀（Output-Contract Family Invariant Gates）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 2 / I1 行（I6 派生，触发证据 = PD-15）；I3 派生登记 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` §4（PD-15 不变式陈述 + 门禁表达候选）；I6 裁定 `2026-08-12-1217-7-...`（门禁范围 + 跨 task pin 处置）；invariant-catalog.md §5 既有五不变式先例；mission `nop-stream-invariant-loop.json`
> Related: 前置 `2026-08-12-1217-7-nop-stream-invariants-cycle1-I6-closure-and-cycle2-derivation.md`（I6，硬串行依赖：门禁范围裁定 + 跨 task 人工确认待办登记在案）；后继 Cycle 2 / I2（red list 审计）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 2 / I1. 不变式沉淀（输出契约族门禁）

## Purpose

将 PD-15 输出契约族不变式（不变式 #6）沉淀为一等门禁并入 CI：JUnit `@ParameterizedTest` 穷举全部 main `Output` 实现类的 `collect(OutputTag, X)` 行为（三态分类：转发 / fail-fast / 已知违约 pin）+ **类级枚举完备性**（新增 main `Output` 实现类不入注册表即红）+ **发射点注册表**（全部 6 个 side-output 发射点登记，新增发射点即红）+ mjs 静态扫描器扩展（`scan-output-contract` 子命令，含嵌套类方法体解析）+ committed 回归测试；跨 task 已知违约实例按 I6 裁定以过渡 pin 登记（关联 `HG-01`；移除 = Cycle 2 / I4 interim fail-fast 修复落地后）。门禁全绿后为 Cycle 2 / I2 提供确定性 red list 基底。

## Current Baseline

- **I6 为硬前置**：本 plan 执行前 `2026-08-12-1217-7-...`（Cycle 1 / I6）必须已 `completed`——门禁范围裁定、跨 task 缺口双层裁决（interim fail-fast 预授权分派 Cycle 2 / I4 + 线协议 `HG-01` 人工确认门）、roadmap Cycle 2 / I1 行（含触发证据）均已落档。若执行时 I6 未 completed，本 plan 不得开始任何 Phase——立即返回 `blocked`（防门禁范围与裁定不一致，Anti-Hollow 纪律）。
- **不变式 #6（PD-15）陈述**：「任何 `Output.collect(OutputTag, X)` 调用必须被转发到注册的 side-output 消费者，不得静默丢弃；无注册消费者 → fail-fast」。历史证据：R15-AR-4（原始 finding）；I2 探查 PR-1 确认 live；RL-7 修复 `b20fcd0e1`。
- **live 代码基线（2026-08-12 实测）**：main 代码 `Output` 实现类共 4 个——`ChainingOutput`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:35`，`collect(OutputTag)` :111 = 转发 + 无消费者 fail-fast `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；`TimestampedCollector`（`operators/TimestampedCollector.java:46`，`collect(OutputTag)` :97 = 纯转发至被包装 output——**透传目标敏感**：包装 no-op output 时同样丢弃，分类以被包装对象语义为准）；`RecordWriterOutput`（`execution/StreamTaskInvokable.java:611` private 嵌套类，`collect(OutputTag)` :645 = 空体 no-op，跨 task）；`BroadcastingRecordWriterOutput`（同文件 :660，`collect(OutputTag)` :706 = 空体 no-op，跨 task）。后两者 = I6 裁定关联实例（interim fail-fast 预授权分派 Cycle 2 / I4；线协议支持 = `HG-01`），过渡 pin 登记。
- **side-output call-site（live 实测 6 个发射点，main 代码）**：`ProcessOperator.java:111`（ProcessFunction `ctx.output` 发射）/ `:134`（OnTimer `ctx.output` 发射）；`WindowOperator.java:1030`（late-data `sideOutput` 发射，方法声明 :1029）/ `:1860`（ProcessWindowFunction `ctx.output` 发射，方法声明 :1856）；`CepOperator.java:483`（late-data 发射）/ `:777`（PatternProcessFunction `ctx.output` 发射，方法声明 :770）。**注册表行号语义 = 发射点**（`Output.collect(OutputTag-typed, ...)` 调用行；方法声明为间接层，不登记；`Output` 实现类内部转发调用（如 `TimestampedCollector.java:98`）属 V1/V3 管辖，不属发射点）。in-task 链式路径由 `StreamTaskInvokable.registerSideOutputConsumer`（`StreamTaskInvokable.java:310-313`）→ 共享 consumer map → `ChainingOutput.sideOutputConsumers` 接线；**跨 task 生产可达**：`GraphExecutionPlan.java:454-458` → `StreamTaskInvokable`（`wireOperators` :239/:245、`wireTailToRecordWriter` :352）→ task tail 算子 setOutput(`RecordWriterOutput`/`BroadcastingRecordWriterOutput`) → side-output 静默丢弃（I6 已裁决：已确认契约缺口；interim fail-fast 预授权分派 Cycle 2 / I4；线协议支持 = 人工确认待办 `HG-01`）。
- **既有门禁基础设施**：JUnit 门禁类命名模式 `Test*Invariant*`（9 类 / 92 tests，`cycle1-I6-input.md`）；表完备性门禁 ×3 模块（`TestInvariantTableCompleteness` 等，**per-class 语义**：仅对 gate-inventory.json 已登记类做方法级双向相等，全新类不入表不触发红）；mjs 工具 `check-nop-stream-invariants.mjs`（子命令 inventory / sync / scan-iterations / self-test / init / all；**pin 匹配 = 违规串精确匹配 `p.key`**（`compareViolationsToPins` :530-536）；现有方法体解析仅提取顶层类（brace-depth 1，:94-97 先例）；`mjs-pins.json` 位置与结构在案）；E2E 基线 `TestSideOutputChainingE2E`（nop-stream-runtime，3 用例：端到端转发 / 无消费者 fail-fast / StreamTaskInvokable 接线，I4 新增全绿）。
- **设计文档先例**：`ai-dev/design/nop-stream/` 下 window-design.md / cep-design.md 已含「不变式」节（I1 定义「设计文档补不变式节」的既有载体）；core-design.md 为 core 层契约载体。
- **真正剩余的 gap**：输出契约族无专属 JUnit 门禁；`Output` 实现类无类级枚举完备性门禁（现有机制检不出新类）；无发射点注册表；mjs 无输出契约静态扫描（嵌套类方法体解析）；不变式 #6 未在 invariant-catalog.md 定稿。

## Goals

- 新增 JUnit 门禁类 `TestOutputContractInvariant`（**nop-stream-core**，仅实现类级断言——core 不可引用 runtime 的 `WindowOperator`，依赖方向约束）：参数化穷举全部 main `Output` 实现类，断言 `collect(OutputTag, X)` 行为 ∈ {转发到注册消费者 / 无消费者 fail-fast / 已知违约 pin}；含 `registerSideOutputConsumer` ↔ 共享 consumer map 运行时连通断言（接线验证）。
- **类级枚举完备性**：`scan-output-contract` 扫描 `src/main/java` 全部 `implements Output` 类 → 必须 ⊆ 注册表 → 新增类即红；注册表 = 新文件 `ai-dev/audits/nop-stream-invariants/output-contract-registry.json`（独立于 `init` 再生的 gate-inventory.json，防覆盖）。
- **call-site 注册表**：6 个发射点全部登记（文件:行 + 所属算子 + 处置 = in-task 链式路径（E2E 覆盖）/ 跨 task 可达（`HG-01` 人工确认关联））；`scan-output-contract` 检出新增发射点即红（V4，见 Phase 3 违规语义）。
- mjs `scan-output-contract` 子命令（嵌套类方法体三态分类 + 类级枚举 + call-site 扫描 + pin 匹配），纳入 `all` + `self-test` 正反例 fixtures；pin 条目含 `key`（违规串，与 `compareViolationsToPins` 匹配协议一致）。
- 跨 task 已知违约实例**过渡 pin** 登记（2 条，`mjs-pins.json`：`key` = 违规串（与 `compareViolationsToPins` 匹配协议一致）、关联 `HG-01`；移除 = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新后，禁静默移除——`HG-01` 线协议支持落地属增强，不阻塞 pin 移除）。
- 门禁全绿（JUnit 门禁子集 0 failure + mjs `all` exit 0 + 既有 E2E 不回归）；invariant-catalog.md #6 定稿（状态 = 已沉淀）；`ai-dev/design/nop-stream/core-design.md` 补「不变式」节（先例 window-design.md / cep-design.md）；roadmap Cycle 2 / I1 行流转 `todo`→`planned`→`done`；committed 回归测试。
- 为 Cycle 2 / I2 提供确定性审计基底（门禁跨全部方法跑 = 全绿基线，I2 以此 + 对抗探查产出 red list）。

## Non-Goals

- **不修复跨 task 缺口的任何代码**（interim fail-fast = 预授权分派 Cycle 2 / I4，不在本 plan；线协议支持 = `HG-01` 人工确认门，执行门未过；本 plan 仅过渡 pin + 注册表留痕）。
- **不改 `Output` 公共接口 / RecordWriter 线协议 / 模块边界**（接口契约零变更，I4 先例；结构性重构 = 人工确认门）。
- **不执行 Cycle 2 / I2 审计**（red list 生成属下一 work item，消费本 plan 门禁）。
- **不引入 ArchUnit**（既有 deferred 裁定：`optimization candidate`，涉及依赖变更，触发时另立 Decision）。
- **不修复本 plan 门禁发现的既有行为差异**（若门禁首跑暴露未知违约实例 / 未知发射点：记录证据 + 分类（已知实例遗漏 → 补注册表自洽；新实例 / 新族 → 移交 I2 裁决）；首次跑红 = 补表属 I1 内职责（I1 deferred 裁定先例），非表缺失的代码违背才移交 I2）。

## Scope

### In Scope

- 注册表设计定稿（`output-contract-registry.json` schema：实现类表 + 行为分类 + 发射点表 + test-only 豁免清单；pin schema 含 `key`）。
- JUnit 门禁类 `TestOutputContractInvariant`（core，实现类级断言 + 接线断言）。
- mjs `scan-output-contract` 子命令（类级枚举 + 嵌套类方法体三态分类 + call-site 扫描 + pin 匹配）+ `all` 纳入 + `self-test` fixtures。
- 跨 task pin 登记 + 门禁全绿验证 + 文档收口（catalog #6 定稿 / core-design.md 不变式节 / 门禁统计落点 / logs / roadmap 行流转）。

### Out Of Scope

- 跨 task 线协议修复（人工确认门）。
- Cycle 2 / I2–I6（后续 work items）。
- 既有 9 门禁类内容改写（只增不弱化；表完备性维持既有 per-class 语义，类级枚举由 `scan-output-contract` 承担，不并改既有 JUnit 门禁）。

## Execution Plan

### Phase 1 - 注册表与门禁语义设计定稿

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/output-contract-registry.json`（新建，设计稿）、`invariant-catalog.md`（#6 草稿节）、`mjs-pins.json`（pin schema 说明）

- Item Types: `Decision`

- [x] 实现类表定稿：live 枚举全部 main `Output` 实现类（实测 4 个：`ChainingOutput` / `TimestampedCollector` / `RecordWriterOutput` / `BroadcastingRecordWriterOutput`；test 目录 `TestOutput`、`MockOutput`、`CapturingOutput`、`NopOutput` 等 → test-only 豁免清单，不入强制表）；每类登记 = {fqcn, 文件路径, `collect(OutputTag)` 行为分类, 处置}；行为分类三态：`forward`（ChainingOutput 转发+无消费者 fail-fast / TimestampedCollector 透传——注「透传目标敏感」）/ `pinned-known-violation`（RecordWriterOutput / BroadcastingRecordWriterOutput，跨 task，**过渡 pin**，关联 `HG-01`）
- [x] call-site 表定稿（**发射点语义**）：6 个发射点全量登记 = {文件:行（发射点，`ProcessOperator.java:111/:134`、`WindowOperator.java:1030/:1860`、`CepOperator.java:483/:777`）, 所属算子（ProcessOperator / WindowOperator / CepOperator）, 触发场景（ProcessFunction ctx.output / OnTimer ctx.output / late-data / ProcessWindowFunction ctx.output / PatternProcessFunction ctx.output）, 处置（in-task 链式路径 = E2E 覆盖 / 跨 task 可达 = `HG-01` 关联）}
- [x] pin schema 定稿：`mjs-pins.json` 条目 = `{key: <违规串>（必需，与 compareViolationsToPins 的 p.key 精确匹配协议一致）, id, file, invariant: "#6", classification: "known-violation", reason: <interim fail-fast 已预授权分派 Cycle 2 / I4 + 线协议支持 = HG-01 人工确认待办关联>, removalTrigger: "Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新后移除，禁静默移除"}`；2 条 pin（RecordWriterOutput / BroadcastingRecordWriterOutput）的 key 值在 Phase 3 实现时以扫描器实际违规串为准
- [x] 违规语义 V1–V5 定稿（见 Phase 3 定义）+ test-only 豁免规则（仅扫描 `src/main/java`；test 目录实现类不参与枚举与 call-site 判定）
- [x] 确认 gate-inventory.json 是否需要补 Output 实现类方法（既有 inventory 表以 I0 §4.1 classifier 枚举变更型方法；若 `collect(OutputTag)` 已在表中保持原样；若未含，本 plan 仅在注册表中登记，**不改 gate-inventory.json schema**——类级枚举与行为分类归 `scan-output-contract` 单一职责）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 实现类表 + 发射点表（6 个）+ pin schema（含 `key`）+ 违规语义 V1–V5 定稿在案（`output-contract-registry.json` 设计稿 + catalog #6 草稿节），每条 = repo-observable（文件:行 + 期望行为 + 断言方式）
- [x] 三态分类可断言；透传目标敏感性（TimestampedCollector）已记录
- [x] 本 Phase 为设计定稿（Decision），不涉及行为变更 → `No owner-doc update required` 除 catalog #6 草稿节
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - JUnit 门禁实现（core，实现类级）

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/`（新增 `TestOutputContractInvariant`）；nop-stream-runtime（`TestSideOutputChainingE2E` 保持绿，作为端到端证据）

- Item Types: `Fix | Proof`

- [x] **test-first**：新增 `TestOutputContractInvariant`，先写断言（参数化穷举 4 个 main 实现类，每类断言 `collect(OutputTag, X)` 行为 ∈ {forward, fail-fast, pinned}——**分类以注册表为准**：分类迁移（如 I4 将 pin 迁移为 fail-fast）须同步更新注册表与断言（先例 = I4 pin 翻转测试））→ 先红（跨 task 空体实例未声明 pin 语义时红）→ 按 Phase 1 表登记 pin 后转绿
- [x] 实现类级断言（core 内可测）：`ChainingOutput` = 转发至注册消费者（`registerSideOutputConsumer` 后 collect 命中消费者）+ 无消费者 fail-fast（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；`TimestampedCollector` = 透传至被包装 output（包装 recording output 断言收到；包装 no-op output 断言丢弃 = 透传目标敏感语义）——包装 `RecordWriterOutput` 的场景 = 等价跨 task 丢弃，断言记录在案（不新增修复，注释关联 pin）
- [x] 私有嵌套类实例化路径（RecordWriterOutput / BroadcastingRecordWriterOutput）：反射 `setAccessible(true)` 实例化 + 以 mock/最小 `RecordWriter` 构造；断言 `collect(OutputTag, record)` 当前行为 = 空体 no-op（记录 = pin 语义，断言与 `mjs-pins.json` 两条 pin 对应）——先例：I4 pin 翻转测试（ClusterRegistry 等）同风格
- [x] **接线断言（Rule #23）**：`StreamTaskInvokable.registerSideOutputConsumer` → 共享 consumer map → `ChainingOutput.sideOutputConsumers` 运行时连通（测试内注册后经 ChainingOutput 转发命中消费者实例）
- [x] **端到端证据（Rule #22，归属定案）**：端到端路径（`sideOutput(lateDataOutputTag)` → `ChainingOutput` → 消费者；无消费者 fail-fast）由 **nop-stream-runtime 既有 `TestSideOutputChainingE2E`（3 用例）**承担——core 门禁类不可引用 runtime `WindowOperator`（依赖方向约束）；本 phase 保持 `TestSideOutputChainingE2E` 全绿不回归，不新增 core 内 E2E 副本
- [x] 无静默跳过（Rule #24）：门禁用例验证每个 main 实现类 `collect(OutputTag)` 路径有明确行为（转发 / fail-fast / pin 留痕）；新增实现类默认红（不入注册表 / 空体无 pin = 违约）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestOutputContractInvariant` 已提交（参数化穷举 4 实现类 + 接线断言 + 反射实例化断言），先红后绿证据在案（红 = 跨 task 实例未 pin 时；绿 = pin 登记后）
- [x] **端到端验证**：`TestSideOutputChainingE2E`（runtime）保持 3/3 绿（sideOutput → ChainingOutput → 消费者完整路径断言在案）
- [x] **接线验证**：`registerSideOutputConsumer` ↔ `ChainingOutput.sideOutputConsumers` 运行时连通断言在案
- [x] **无静默跳过**：无空体 / continue / 吞异常作为正常实现；未实现路径显式 fail；透传目标敏感性（TimestampedCollector 包装 no-op）已断言记录
- [x] 相关 `ai-dev/design/`（不变式节）与 `invariant-catalog.md`（#6）已更新或明确记账到 Phase 4
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - mjs `scan-output-contract` 子命令

Status: completed
Targets: `ai-dev/tools/check-nop-stream-invariants.mjs`、`ai-dev/audits/nop-stream-invariants/output-contract-registry.json`（实现）、`mjs-pins.json`、`fixtures/`

- Item Types: `Fix | Proof`

- [x] 新增子命令 `scan-output-contract`，静态解析 `src/main/java`（**需嵌套类方法体解析——现有解析器仅顶层类 brace-depth 1（:94-97），须扩展以解析 `StreamTaskInvokable` 的 private 嵌套类 `RecordWriterOutput` / `BroadcastingRecordWriterOutput` 的 `collect(OutputTag)` 方法体**）：
  - **V1 类级枚举**：`implements Output` 类集合必须 ⊆ 注册表实现类表；新类不入表 = 红
  - **V2 失效类**：注册表实现类在代码中已不存在 `implements Output` = 红（强制诚实维护注册表）
  - **V3 行为漂移**：`collect(OutputTag)` **方法体分类** ≠ 注册表**实现类分类** = 红（如 ChainingOutput 回归空体、RecordWriterOutput 意外转发）。**两级分类词汇与匹配规则定稿**：实现类分类（注册表）= {`forward`, `fail-fast`, `pinned-known-violation`}；方法体分类（扫描器）= {`forward`, `fail-fast`, `no-op`}；匹配规则：方法体 `no-op` + 注册表 `pinned-known-violation` → pin 覆盖（绿）；其余任何「方法体 ≠ 注册表」→ 红。**方法体分类优先级**：`forward`（含转发调用：`consumer.accept(` / 其他对象 `.collect(` / `.accept(`）> `fail-fast`（含 `throw`）> `no-op`（空体 / 仅注释）——ChainingOutput（含 throw + consumer.accept）= forward（fail-fast 为子语义标注）；TimestampedCollector（`output.collect(...)`）= forward；RecordWriterOutput / BroadcastingRecordWriterOutput（空体）= no-op
  - **V4 新增发射点**：call-site 扫描算法 = （a）提取 `OutputTag<...> name` 声明（**字段 / 局部变量 / 方法参数三种形态**，regex 分别覆盖 `OutputTag<[^>]*>\s+(?:final\s+)?(\w+)` 与参数表 `\(\s*(?:final\s+)?OutputTag<[^>]*>\s+(\w+)`）→（b）匹配发射调用 `\.collect\(\s*<name>\s*,`（name ∈ 已声明 OutputTag 变量）→（c）**排除规则**：`Output` 实现类方法体内的转发调用（如 `TimestampedCollector.java:98` 的 `output.collect(outputTag, record)`——OutputTag 为方法参数形态）**不判 V4**，归 V1/V3 管辖（实现类行为由类级枚举 + 行为分类约束）→（d）发射点集合（非实现类）必须 ⊆ 注册表 call-site 表；新发射点 = 红（迫使分类处置：in-task → E2E 覆盖登记；跨 task 可达 → `HG-01` 关联登记）。**方法声明行（`void output(OutputTag, X)`）与内部辅助调用（如 `WindowOperator.java:600` 的 `sideOutput(element)`）不匹配 `\.collect(`，天然排除，不会误红**；注册表行号 = 发射点，与扫描输出行号同语义
  - **V5 失效发射点**：注册表发射点在代码中已不存在 = 红（如发射点重构/迁移须同步注册表）
  - **pin 匹配**：违规集合 ⊆ pins（`p.key` 精确匹配）= green；pin 只允许覆盖跨 task 实例类（V1/V3 类目）
- [x] 注册表实现：`output-contract-registry.json`（4 实现类 + 6 发射点 + test-only 豁免清单），schema 与 Phase 1 定稿一致；`mjs-pins.json` 新增 2 条 pin（含 `key` = 扫描器实际违规串 + `HG-01` 关联 + removalTrigger = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新后移除——`HG-01` 线协议支持落地属增强，不阻塞 pin 移除）；**同步 `mjs-pins.json` 头注/note 字段语义（现声明仅 invariant #2 语义，须扩为 #2 + #6）**
- [x] `self-test` 扩展：正反例 fixtures 覆盖 V1–V5（正例 = ChainingOutput 转发 / 注册表自洽；反例 = 空体未 pin → 红；反例已 pin → 绿；新增类未登记 → 红；新增发射点未登记 → 红；行为漂移 → 红；**参数形态 OutputTag 的发射点（如 ProcessOperator 风格 `void output(OutputTag<X> outputTag, ...)`）→ 检出为发射点；Output 实现类内部转发调用（TimestampedCollector:98 风格，参数形态）→ 不判 V4（V1/V3 管辖）**）——证明扫描器无静默跳过
- [x] `all` 纳入 `scan-output-contract`；工具帮助头注释更新；`ai-dev/tools/README.md` 工具清单同步（README 实测存在，确定更新）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs scan-output-contract` 独立运行 exit 0（违规 ⊆ pins）；V1–V5 语义在代码注释 + 帮助头中可查
- [x] `self-test` 含 `scan-output-contract` 正反例（fixtures 在案，覆盖 V1–V5 各一正一反）
- [x] `all` exit 0（含新子命令）；pin = 2 条（跨 task 实例），与 `HG-01` 关联且 removalTrigger 在案
- [x] 无静默跳过：扫描器对未识别形态的 `collect(OutputTag)` 方法体 / 嵌套类解析失败 = 显式 fail（不静默归类）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 门禁全绿验证与文档收口

Status: completed
Targets: `nop-stream/` 门禁子集、`ai-dev/audits/nop-stream-invariants/invariant-catalog.md`、`ai-dev/design/nop-stream/core-design.md`、roadmap、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 门禁子集全绿：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 0 failure（含新增 `TestOutputContractInvariant`；依赖本地仓库 SNAPSHOT，遇不可解析先 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装，I5 先例）
- [x] 既有 E2E 不回归：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestSideOutputChainingE2E` 绿
- [x] mjs 全子命令 exit 0：`node ai-dev/tools/check-nop-stream-invariants.mjs all`（含 inventory / sync / scan-iterations / scan-output-contract / self-test）
- [x] 门禁统计落档：**新建 `ai-dev/audits/nop-stream-invariants/cycle2-I1-input.md`**（Cycle 2 门禁统计唯一落点，供 Cycle 2 / I2 消费；`cycle1-I6-input.md` 已被 I6 消费，不回写不追加）——新门禁类测试数、总门禁类数（9 → 10）、pin 明细
- [x] `invariant-catalog.md`：#6 定稿（不变式陈述 / 覆盖失败族 RL-7 + 跨 task 缺口 / 历史证据 R15-AR-4 + PR-1 / 检测方法 JUnit + mjs / 状态 = 已沉淀）
- [x] `ai-dev/design/nop-stream/core-design.md` 补「不变式」节（#6 输出契约族；先例 = window-design.md / cep-design.md 既有不变式节；输出契约属 core 层契约，载体 = core-design.md）
- [x] roadmap Cycle 2 / I1 行状态流转 `todo`→`planned`（激活时）→`done`（closure audit 通过后，不得提前）；`ai-dev/logs/` 顶部条目更新
- [x] committed 回归测试（门禁类 + 注册表 + fixtures + pin 条目同 commit，防漂移）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] JUnit 门禁子集 0 failure（10 门禁类，含新增）+ 既有 E2E 绿
- [x] mjs `all` exit 0（含 `scan-output-contract`）；pin = 2 条且有依据（key 可匹配）
- [x] `cycle2-I1-input.md` 落档（门禁 9 → 10 类 / 新增测试数 / pin 明细，唯一落点原则）
- [x] `invariant-catalog.md` #6 定稿 + `core-design.md` 不变式节 + roadmap 行流转在案
- [x] 测试统计 / 门禁统计 / pin 明细已写入 `ai-dev/logs/`
- [x] 无静默跳过：全部新增扫描/断言路径有正反例或显式 fail 语义

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] I6（`2026-08-12-1217-7-...`）已完成（硬前置）；门禁范围与 I6 裁定一致
- [x] 不变式 #6 已沉淀为一等门禁（JUnit + 类级枚举 + 发射点注册表 + mjs），全绿零命中
- [x] 跨 task 实例过渡 pin 登记在案（2 条，含 `key`，关联 `HG-01`）；pin 移除条件 = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新（留痕，禁静默移除）
- [x] 端到端 + 接线验证通过（`TestSideOutputChainingE2E` 3 用例；`registerSideOutputConsumer` ↔ `ChainingOutput.sideOutputConsumers` 连通）
- [x] 无静默跳过：门禁/扫描器对未实现路径显式 fail；无空体作为正常实现（除过渡 pin 留痕的跨 task 实例，pin 移除 = I4 修复落地）
- [x] 文档收口：catalog #6 定稿 + core-design.md 不变式节 + roadmap 行流转 + `cycle2-I1-input.md` 落档 + logs 记录
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（跨 task 缺口 = I6 裁定：interim fail-fast 预授权分派 Cycle 2 / I4 + 线协议 `HG-01` 人工确认门，非本 plan scope；I1 内发现的未知违约实例 / 发射点 → 记录 + 移交 I2 裁决，不静默忽略）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）`TestOutputContractInvariant` 与 `scan-output-contract` 在 `all` 路径运行时确实执行（非只存在文件），（b）端到端断言真实断言转发行为（`TestSideOutputChainingE2E` 3 用例在案），（c）无空方法体 / 静默跳过作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 通过（0 failure）
- [x] `./mvnw compile -pl nop-stream/nop-stream-core -q` 通过（新增测试类可编译；checkstyle 依 mission lint 命令执行，如配置则须通过）

## Deferred But Adjudicated

### 层次 1 — 跨 task interim fail-fast 修复（`RecordWriterOutput` / `BroadcastingRecordWriterOutput` 空体 → fail-fast）

- Classification: `Fix`（已确认契约缺口 P1）——已确认 live defect，**必须修**，不属 deferral
- Why Not Blocking Closure: 本项**不延期**——I6 预裁决其处于 P1 自动修复预授权信封内（private 嵌套类行为修复，`Output` 接口零变更，同 RL-7 先例；Rule #24 强制 fail-fast），分派 Cycle 2 / I3 确认 → I4 执行（显式 successor ownership）。I1 内以过渡 pin 全绿落地，pin 移除 = I4 修复 + 注册表分类更新（本 plan 已定义该移除条件与同步机制）。
- Successor Required: `yes`
- Successor Path: Cycle 2 / I3（裁决确认）→ Cycle 2 / I4（修复 + 注册表分类更新 + pin 移除）

### 层次 2 — 跨 task side-output 线协议结构性变更（人工确认待办 `HG-01`）

- Classification: `Fix`（I6 裁定延续：已确认契约缺口 P1，执行门 = 人工确认）——非 watch-only residual
- Why Not Blocking Closure: ① 修复需 RecordWriter 线协议结构性重构，人工确认门未过（I6 登记在案：`HG-01`，含证据 / 修复方向 / 触发条件）；② 本 plan 以过渡 pin（含 `key`）+ 类级枚举 + 发射点注册表三重留痕——新增实现类 / 新增发射点 / 行为漂移 / pin 静默移除均触发红；③ in-task 路径已 fail-fast（I4 `b20fcd0e1`）；④ 已确认缺口登记 = 处置（人工确认执行门 + interim 分派），非延期。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan（跨 task 侧输出线协议设计 + 实现 + E2E）；触发条件 = 人工批准 + 跨 task side-output 需求出现（或 CI 门禁红暴露新实例）

### I1 门禁首跑暴露的未知违约实例 / 发射点（如有）

- Classification: `watch-only residual`（待 I2 裁决，非本 plan 处置项）
- Why Not Blocking Closure: 若 `scan-output-contract` / 门禁首跑暴露未知 `Output` 实现或未知发射点，记录证据 + 分类（已知实例遗漏 → 补注册表自洽；新实例 / 新族 → 移交 I2 裁决），不静默忽略、不本 plan 修复（I1 职责边界先例）。
- Successor Required: `yes`
- Successor Path: Cycle 2 / I2（red list 生成 + 裁决）

## Non-Blocking Follow-ups

- ArchUnit 架构约束门禁（`optimization candidate`，触发时另立 Decision，先例延续）。
- `TestSideOutputChainingE2E` 扩展 ctx.output / ProcessWindowFunction / PatternProcessFunction 多输出路径的 E2E 用例：由 Cycle 2 / I2 对抗探查评估（发射点注册表已覆盖全部 6 个发射点，E2E 扩展不阻塞本 plan 关闭）。

## Closure

Status Note: 不变式 #6 沉淀（JUnit `TestOutputContractInvariant` + 类级枚举 + 发射点注册表 + mjs `scan-output-contract`）全绿、过渡 pin 2 条登记、文档收口、独立 closure audit 完成——门禁 10 类 / 102 tests / 0 failures，mjs `all` exit 0，E2E 3/3，`cycle2-I1-input.md` 落档，roadmap 行 `done`。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session `ses_00a08570bffePIWj9TZH40LbQV`）
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 test name / 文件:行）：
    - Phase 1: registry（4 实现类 + 6 发射点 + V1–V5 + pin schema）+ catalog #6 草稿 → **PASS**（audit §1/§4）
    - Phase 2: `TestOutputContractInvariant` 10 用例（registry-backed 参数化 + 接线断言 + 反射实例化）先红（2 failures，空体未 pin）后绿（10/10）→ **PASS**（audit §2）；E2E 3/3 → **PASS**（audit §5）
    - Phase 3: `scan-output-contract` 独立 exit 0 + `all` exit 0 + self-test 正反例 V1–V5 + pins 2 条 key 精确匹配（无 unpinned 无 stale）→ **PASS**（audit §3/§4）
    - Phase 4: 门禁子集 10 类 / 102 tests / 0 failures（core 40 + runtime 34 + cep 28）→ **PASS**（audit §6）；`cycle2-I1-input.md` + catalog #6 定稿 + core-design.md §6.1 不变式节 + roadmap `done` → **PASS**（audit §7）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）：I6 前置 completed（audit §1）；过渡 pin 2 条含 `key`/`HG-01`/removalTrigger（audit §4）；E2E + 接线断言在案（audit §2/§5）；无静默跳过（audit §8）；文档五处收口（audit §7）；无静默降级（跨 task 缺口 = I6 裁定分派 Cycle 2 / I4 + `HG-01`，I1 新发现零项）；独立 audit（本段）
  - `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码为 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（3 条 BROKEN_LINK 为 `2026-08-12-0615-3-...` credential plan 既有基线，非本 plan 文件）
  - `scan-hollow-implementations.mjs --module nop-stream --severity high`：findings 集与基线 `34aed42c1` 完全一致（既有 14 项，全为 fail-fast/有意 no-op），**零新增 finding**（audit §8，沿用 I2/I4 判据 = findings 集一致；工具无豁免机制，退出码非 0 属既有基线项）
  - Anti-Hollow 检查结果：`TestOutputContractInvariant` 与 `scan-output-contract` 在 `all` 路径运行时确实执行（surefire 10 tests 实测 + `all` exit 0 实测）；`TestSideOutputChainingE2E` 3 用例真实断言转发行为（consumer 收到 + fail-fast error code）；V1–V5 各正反例 fixture 在案（内存 + committed `OutputContractFixture.java`）
  - Deferred 项分类检查：跨 task 缺口 = 已确认契约缺口（Fix 类），interim fail-fast 预授权分派 Cycle 2 / I4（显式 successor ownership）、`HG-01` 线协议 = 人工确认执行门（I6 登记）；均未降级；I1 新发现（如有）移交 I2，未静默忽略（本轮零新发现）

Follow-up:

- Cycle 2 / I2（red list 生成 + 对抗探查）为直接 successor
- no other remaining plan-owned work
