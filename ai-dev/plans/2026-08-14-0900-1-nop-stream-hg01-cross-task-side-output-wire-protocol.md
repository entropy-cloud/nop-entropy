# 1 跨 task side-output 线协议实现（HG-01，人工批准 2026-08-14）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Follow-up Backlog「跨 task side-output 线协议结构性变更（人工确认待办 `HG-01`）」
> Related: `2026-08-12-1217-11`（Cycle 2 / I4 interim fail-fast 落地 `88bc0270c`）；`2026-08-12-1217-8`（Cycle 2 / I1 输出契约族门禁）

## Purpose

把跨 task side-output 从「fail-fast（无消费者通道）」升级为「完整线协议支持」：生产者通过 `Output.collect(OutputTag, X)` 发射的 side-output 元素经线协议（本地队列 + 远程 envelope）送达消费 task，由 task 级注册消费者转发，无消费者仍 fail-fast。该变更 = mission Cross-Cutting「结构性重构执行前人工确认」门已过（2026-08-14 人工批准，批准记录写入 roadmap backlog 条目 + 本 plan）。门禁同步（不变式 #6 注册表行为分类迁移）随本 plan 一并落地。

## Current Baseline

- **生产端（live 已核实）**：
  - `StreamTaskInvokable.RecordWriterOutput.collect(OutputTag, record)`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:892-902`）→ fail-fast（抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；`BroadcastingRecordWriterOutput.collect(OutputTag)`（同文件 :960-970）→ fail-fast。均不含任何序列化/路由逻辑。
  - in-task 路径已完整：`ChainingOutput.collect(OutputTag, record)`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:111-126`）转发到注册消费者，无消费者 fail-fast。
  - 6 个发射点（门禁注册表）：`ProcessOperator.java:111/:134`、`WindowOperator.java:1198/:2028`（重钉后）、`CepOperator.java:507/:814`（重钉后）。
- **消费端注册机制（live 已核实）**：
  - `StreamTaskInvokable.sideOutputConsumers`（`StreamTaskInvokable.java:133`）task 级 `Map<OutputTag<?>, Consumer<StreamRecord<?>>>` + `registerSideOutputConsumer`（:391-393）——目前仅 `ChainingOutput` 消费该 map（:247/:285 注入）；**跨 task 入站路径（InputGate → headInput）不路由 side-output 元素**。
- **线协议（live 已核实）**：
  - `StreamMessageEnvelope`（`io.nop.stream.core.execution.transport`）：5 类型常量（STREAM_RECORD / CHECKPOINT_BARRIER / WATERMARK / WATERMARK_STATUS / CONTROL，:32-36），字段 epochId/type/valueType/payload/timestamp/hasTimestamp；**无 outputTagId 字段、无 side-output 类型**。
  - `StreamElementCodec.encode/decode`（`StreamElementCodec.java`）：record 经 `JsonTool.stringify` + valueType 类名；不支持的 element 类型抛 `ERR_STREAM_INVALID_STATE`（:81）。**注（审查 m3）**：codec 实际只有 4 个 type 分支（STREAM_RECORD/CHECKPOINT_BARRIER/WATERMARK/WATERMARK_STATUS；`CONTROL` 不在 codec，`RemoteInputChannel.java:382` 特判）。
  - 远程路径：`RemoteResultPartition.java:163` encode → `IDataPlaneWireCodec` → `RemoteInputChannel` decode；本地路径：`ResultPartition` 队列直接传递 `StreamElement` 实例（不经过 envelope）。
  - checkpoint 通道状态：`ChannelState.java:145/:193` 也走 `StreamElementCodec` encode/decode（in-flight 元素快照）。
- **StreamElement 类型体系**：`isRecord()` 用 `getClass() == StreamRecord.class` 判定（`StreamElement.java`）；新增子类型不破坏既有分支。`processInputGate`（`StreamTaskInvokable.java:780-848`）分支处理 record/watermark/barrier/watermarkStatus——side-output 元素需新分支。
- **图模型**：`StreamEdge.outputTag` 字段已存在（`StreamEdge.java:85/:147-156`）但**零消费点**（全仓 `getOutputTag()` 无调用方）——本 plan 不动图模型面（见 Non-Goals）。
- **门禁（live 已核实）**：
  - `ai-dev/audits/nop-stream-invariants/output-contract-registry.json`：4 个 main `Output` 实现类三态分类（RWO/BRWO = `fail-fast`）；`TestOutputContractInvariant`（core，10 用例）参数化穷举 + 注册表驱动。
  - `ai-dev/tools/check-nop-stream-invariants.mjs` `scan-output-contract` V1–V5（类级枚举 / 失效类 / 行为漂移 / 新增发射点 / 失效发射点）。
  - E2E：`TestSideOutputChainingE2E`（runtime，4 用例）第 4 用例 = 跨 task 尾接线 fail-fast 断言（RWO 1 writer + BRWO 2 writers）——本 plan 落地后该断言需翻转。
- **批准状态**：`HG-01` 人工确认门 2026-08-14 通过（用户批准「两者都批准」，本 plan + plan `2026-08-14-0900-2`）；批准记录同步写入 roadmap backlog 条目。

## Goals

- 跨 task side-output 线协议落地：`RecordWriterOutput`/`BroadcastingRecordWriterOutput.collect(OutputTag, record)` 序列化 tagged 元素送达消费 task。
- 消费端路由：task 级 `registerSideOutputConsumer` 覆盖跨 task 入站路径；无注册消费者 → fail-fast（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`，位置从生产端移至消费端路由点）。
- 远程 + 本地两条传输路径 + checkpoint 通道状态路径均支持 tagged 元素 round-trip。
- 门禁同步：注册表 RWO/BRWO 行为分类迁移 `fail-fast` → `forward`（tagged，附理由受棘轮约束）+ `TestOutputContractInvariant` 断言翻转 + mjs 复跑全绿 + E2E 断言翻转 + 新增跨 task 送达 E2E。
- 不变式 #6 陈述修订（跨 task 实例从「fail-fast 已落地」改为「forward 已落地，无消费者消费端 fail-fast」），core-design.md §6.1 同步。
- **注意（审查 m6）**：本 plan 交付的是线协议 + task 级注册消费机制；真实用户 job 的图级可达性（`getSideOutput` API）依赖 successor——closure 时不得以「无用户 API」判 hollow。

## Non-Goals

- **`SingleOutputStreamOperator.getSideOutput(OutputTag)` / `SideOutputTransformation` 公共 API**：图级 side-output 消费 API 是独立结果面（DSL `<sideOutput>` 解封，`AdvancedTransforms.buildSideOutput` fail-fast 维持），另立 successor 评估——本 plan 消费面 = task 级 `registerSideOutputConsumer`（已有 API，测试/E2E 消费路径）。
- **`StreamEdge.outputTag` 图模型消费**：图级接线（side-output 边 → 独立 consumer task）不在本 plan。
- **DSL `<sideOutput>` 元素解封**（`AdvancedTransforms.java:404-417` fail-fast 维持）。
- **重复注册语义**（C2-PR-3 last-wins）不改变。
- **SideOutputTransformation / union 等虚拟 transformation** 不涉及。

## Scope

### In Scope

- `StreamMessageEnvelope` 扩展（side-output 类型 + tag id 承载）。
- `StreamElementCodec` encode/decode side-output 元素。
- **字段枚举序列化层全覆盖**（审查 B1/B2，逐字段复制点必须同步 `outputTagId`，否则真实路径静默丢 tag）：
  - `ChannelState.envelopeToMap` / `mapToEnvelope`（`ChannelState.java:215-238`，checkpoint 通道状态快照的逐字段复制层——比 :145/:193 的 codec 调用点更深一层）。
  - `DataPlaneWireSupport.toWireMap`（`nop-stream-runtime/.../transport/DataPlaneWireSupport.java:38-47`，远程 string codec 的逐字段枚举层；`PulsarStringWireCodec` / `SysDaoWireCodec` / `KafkaStringWireCodec` 全部经 `toWireMap` 序列化——不补字段则真实远程后端 tag 恒 null）。
- `StreamElement` 新增 side-output 子类型（isRecord() 判定不破坏）。
- `StreamTaskInvokable.RecordWriterOutput` / `BroadcastingRecordWriterOutput.collect(OutputTag, ...)`：fail-fast → 转发 tagged 元素（含 Broadcasting 多 writer 扇出）。
- `StreamTaskInvokable.processInputGate` 入站路由：side-output 元素 → task 级 `sideOutputConsumers`，无消费者 fail-fast。
- **materialization 双写路径裁决**（审查 M4）：`ResultPartition.java:198` `if (point != null && element.isRecord())` 只双写 isRecord() 元素——side-output 子类型天然不入 materialization store，恢复期 in-flight side-output 元素静默丢失。Phase 1 显式裁决：纳入（扩展 `isRecord()` 门或新增双写条件 + 恢复测试）或排除（附 non-blocking 理由，禁止默默留）。
- 本地路径（`ResultPartition` 队列直传）与远程路径（`RemoteResultPartition` / `RemoteInputChannel` envelope + **string codec 层**）双路径验证。
- 门禁同步（注册表 / JUnit / mjs / E2E / catalog / core-design）。

### Out Of Scope

- 公共 API `getSideOutput`、图级接线、DSL 解封（见 Non-Goals）。
- 既有 in-task 链式行为（`ChainingOutput`）零改动。
- `HG-01` 之外的 backlog 条目。

## Execution Plan

### Phase 1 - 设计裁决与 owner doc 更新

Status: completed
Targets: `ai-dev/design/nop-stream/core-design.md` §6.1；`ai-dev/audits/nop-stream-invariants/invariant-catalog.md` #6

- Item Types: `Decision | Proof`
- [x] [Decision] 线协议形态裁决并落档：tag 承载方式（envelope 新增 `TYPE_SIDE_OUTPUT_RECORD` + `outputTagId` 字段 vs 复用 STREAM_RECORD 加可选 tagId）、StreamElement 子类型命名与语义、消费端路由点（`processInputGate` 分支）、无消费者 fail-fast 位置与错误码复用（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）。
- [x] [Decision] **valueType 派生规则裁决**（审查 M2）：side-output 元素**忽略 edge 级 valueType**（`RemoteResultPartition.java:162-164` 用 `typeRegistry.getOutputTypeClassName(edgeId)` 标主输出边类型）——一律从内层 record value 派生类名（`record.getValue().getClass().getName()`）；codec 测试补「传入错误 valueType 时不被采用」反例。
- [x] [Decision] **生产端通道选择裁决**（审查 M5a/b）：side-output 元素经 `RecordWriter.emitElement(StreamElement)`（广播全部 partition——`RecordWriter.java:216-225`，`emit(StreamRecord)` 只收 StreamRecord 且按 partitioner 路由不可用）→ 语义 = **所有下游 subtask 都收到 side-output 元素**，任一 subtask 未注册消费者即 fail-fast（parallelism>1 时需在 E2E 中显式声明并测试此语义）；或新增 tag 路由 writer 方法（收窄广播面）。裁决必须写明选择与后果。
- [x] [Decision] **消费端 map 查找方式裁决**（审查 M5d）：`OutputTag` 构造器 `Guard.notNull(typeInfo)`（`OutputTag.java:63`）——不能 `new OutputTag<>(id, null)` 做查找 key；按 `sideOutputConsumers.keySet()` 遍历 `getId()` 匹配（tag id 等值语义，equals 按 id 判定）。
- [x] [Decision] **tagged 元素内层 record copy 裁决**（审查 M5c）：`RecordWriterOutput.collect(StreamRecord)` 有 `record.copy(...)`（:873），BRWO 扇出同一 side-output 元素到多 partition 队列——内层 record 复用别名化问题需 copy（或逐 writer 构造新元素）。
- [x] [Decision] **materialization 双写路径裁决**（审查 M4）：纳入或排除，附理由（见 Scope）。
- [x] [Proof] 裁决前 live 复核：`StreamElementCodec` 现有分支、`ChannelState` 复用路径（:145/:193 **+ :215-238 逐字段层**）、`RemoteResultPartition`/`RemoteInputChannel` 编解码点（**含 `DataPlaneWireSupport.toWireMap` :38-47**）、`ResultPartition.java:198` materialization 双写门、`processInputGate` 分支面——与本 plan Current Baseline 一致（若漂移先修 baseline）。
- [x] [Decision] 门禁同步方案裁决：注册表分类迁移 `fail-fast` → `forward`（带 tagged 说明）附棘轮理由；`TestOutputContractInvariant` 断言翻转范围（RWO/BRWO 两用例 + **`testTimestampedCollectorWrappingRecordWriterOutputFailsFast` 独立用例（:368-384，断言改为转发到 wrapped RWO）** + 注册表 TimestampedCollector disposition 文案修订 + 新增入站路由用例）；E2E 第 4 用例翻转 + 新增送达/无消费者用例。
- [x] [Decision] **mjs 分类器适配裁决**（审查 M1）：`check-nop-stream-invariants.mjs:782` `forwardRe = /(?:consumer\.accept\s*\(|\.collect\s*\(|\.accept\s*\()/` 只认 `.collect(`/`.accept(` 形态——新 RWO/BRWO body 若写 `writer.emit(...)` 会在 :790 抛 `unrecognized collect(OutputTag) method body form` crash；裁决 = 扩展 `forwardRe` 识别 emit/emitElement 转发形态 + 同步 self-test 正例夹具（:1503-1580），或锁定实现形态命中现有正则。
- [x] [Decision] `catalog #6` 陈述修订稿 + `core-design.md §6.1` 更新稿（**catalog :303-307 现存漂移——仍写「空体 no-op + 过渡 pin 2 条 + E2E 3 用例」，与 live（fail-fast、pin 0、E2E 4 用例）不符——列为已确认待修项**）。

Exit Criteria:

- [x] 九项 Decision 全部落档 design doc / catalog / core-design（含拒绝的替代方案与理由；D8 扩 forwardRe 为唯一现实出路——RWO 无 consumer map、`writer.emit` 不匹配现有正则，「锁定实现形态」选项不可行，须选扩正则）
- [x] baseline 复核无漂移（或漂移已修正并留痕；catalog #6 现存漂移列为已确认在 scope 内待修）
- [x] 门禁同步方案定稿（注册表迁移 + JUnit 翻转 + mjs 分类器适配 + E2E 新增用例清单）
- [x] `ai-dev/design/nop-stream/core-design.md` §6.1 已更新
- [x] `ai-dev/audits/nop-stream-invariants/invariant-catalog.md` #6 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 线协议编码/解码与元素类型

Status: completed
Targets: `StreamMessageEnvelope`、`StreamElementCodec`、`StreamElement`、`ChannelState`（含 :215-238 逐字段层）、`nop-stream-core/src/test`

- Item Types: `Fix | Proof`
- [x] [Fix] `StreamMessageEnvelope` 新增 side-output 类型常量 + `outputTagId` 字段（默认 null，向后兼容既有 5 类型零改动）。
- [x] [Fix] `StreamElement` 新增 side-output 子类型（携带 OutputTag id + StreamRecord；`isRecord()`/`isWatermark()` 等既有判定不受影响；新增 `isSideOutput()`）。
- [x] [Fix] `StreamElementCodec` encode/decode 支持 side-output 元素（tag id 编解码 + valueType 派生 + payload JSON round-trip）；未知类型仍抛 `ERR_STREAM_INVALID_STATE`（无静默跳过）。
- [x] [Fix] `ChannelState.envelopeToMap` / `mapToEnvelope`（:215-238）同步 `outputTagId` 字段（快照逐字段层，审查 B2——只改 :145/:193 的 codec 调用点不够，恢复路径 tag 会丢）。
- [x] [Fix] `ChannelState` 快照/恢复路径覆盖 side-output 元素（:145 encode / :193 decode 复用 codec + map 层字段同步，验证 round-trip）。
- [x] [Proof] 测试：`TestStreamElementCodec`（或新建侧）新增 side-output 元素 encode/decode round-trip 参数化用例（含 null payload、hasTimestamp 双态、tag id 特殊字符、**错误 valueType 反例——side-output 忽略 edge 级 valueType 派生（审查 M2）**）；`ChannelState` map 层 round-trip 用例（断言 outputTagId 保留）。

Exit Criteria:

- [x] 新增类型与字段编译通过（core 模块）
- [x] side-output 元素 5+ 参数化 round-trip 用例全绿（先红后绿证据：codec 不支持时抛 `ERR_STREAM_INVALID_STATE` → 支持后全绿）
- [x] `ChannelState` map 层 round-trip 用例断言 `outputTagId` 保留（审查 B2 证据）
- [x] 错误 valueType 反例全绿（side-output 忽略 edge 级 valueType，审查 M2 证据）
- [x] 既有 5 类型编解码零回归（既有用例全绿）
- [x] **端到端验证**（本 Phase 组件级）：codec → envelope → decode 全链 round-trip 已覆盖
- [x] **无静默跳过**：未知类型仍抛异常（断言在案）
- [x] 若 Phase 改变 live baseline：`No owner-doc update required`（Phase 1 已裁决设计并更新文档）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 生产端转发与消费端路由

Status: planned
Targets: `StreamTaskInvokable.java`、`ResultPartition`（本地路径 + materialization 双写门）、`RemoteResultPartition`/`RemoteInputChannel`/`DataPlaneWireSupport`（远程路径）、`nop-stream-core/src/test`、`nop-stream-runtime/src/test`

- Item Types: `Fix | Proof`
- [x] [Fix] `RecordWriterOutput.collect(OutputTag, record)`：fail-fast → 构造 side-output 元素并经 `writer.emitElement(...)`（或 Phase 1 裁决的通道选择）传递；内层 record 按 Phase 1 裁决 copy（审查 M5c）。
- [x] [Fix] `BroadcastingRecordWriterOutput.collect(OutputTag, record)`：向全部 writer 扇出 tagged 元素（每 writer 独立元素实例或按裁决 copy，防别名化）。
- [x] [Fix] `processInputGate` 新增 side-output 元素分支：按 Phase 1 裁决的查找方式（`sideOutputConsumers.keySet()` 按 `getId()` 匹配，审查 M5d）查消费者；命中 → 消费；未命中 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`（参数含 tag id + 详情）。
- [x] [Fix] `registerSideOutputConsumer` 语义扩展：注册的消费者同时服务 in-task 链式路径（现状）与跨 task 入站路径（新增）——map 复用，无新 API。
- [x] [Fix] **`DataPlaneWireSupport.toWireMap` 补 `outputTagId` 字段**（`DataPlaneWireSupport.java:38-47`，审查 B1——三个 string codec 全经此层，不补则真实远程后端 tag 恒 null）。
- [x] [Fix] **materialization 双写门**（`ResultPartition.java:198`，审查 M4）：按 Phase 1 裁决纳入（扩展双写条件 + 恢复路径消费 side-output）或排除（non-blocking 理由已裁决，代码不动但测试/文档注明）。
- [x] [Proof] 测试（core）：`TestOutputContractInvariant` 断言翻转——RWO/BRWO `collect(OutputTag)` 不再抛异常（经注入 mock writer 断言 emit 收到 tagged 元素）；**`testTimestampedCollectorWrappingRecordWriterOutputFailsFast`（:368-384）翻转**（断言改为「转发到 wrapped RWO」，审查 M3）；新增入站路由用例（注册消费者收到元素 / 未注册 fail-fast，先红后绿）。**注（复审 F4）**：`processInputGate` 是私有 while(true) 循环方法——路由用例可用 InputGate 桩 + 反射注入，或直接经 E2E（下方 runtime 项）覆盖；二选一即可，但至少一处在案。
- [x] [Proof] 测试（runtime）：`TestSideOutputChainingE2E` 第 4 用例断言翻转（跨 task 尾接线不再 fail-fast）；新增跨 task 送达 E2E（生产者 tail 算子经 RecordWriterOutput 发射 → 消费者 task InputGate 入站 → 注册消费者收到，含 BRWO 双 writer 扇出变体 + 多 subtask 广播语义变体）+ 无消费者 fail-fast 用例。
- [x] [Fix] 远程路径验证：**强制走 string codec**（`TestPulsarStringWireCodec` / `TestKafkaStringWireCodec` / `TestSysDaoWireCodec` 扩展或 focused 集成测试，审查 B1——默认 `IdentityWireCodec` by-reference 测不出 tag 丢失）；`RemoteResultPartition`/`RemoteInputChannel` envelope round-trip side-output 元素（含 wire 层 `toWireMap` round-trip）。

> **中间态说明（审查 M6 + 复审 F1）**：本 Phase 结束时 JUnit 断言已翻转但注册表分类仍为 `fail-fast`（Phase 4 才迁移）——`TestOutputContractInvariant` 参数化测试读注册表驱动断言，此时**门禁红是预期中间态**，不是回归；禁止提前改注册表（Phase 4 批次）。**mjs 侧同为预期中间态**：`mjs scan-output-contract` V3 behavior-drift 红（或 `classifyCollectBody` 对 emit 形态 :790 crash——mjs 分类器修复在 Phase 4）均属 Phase 3 预期，**Phase 3 不要求 mjs 全绿**，执行者不得把 mjs 红/crash 误判为回归 bug 触发错误修复。Phase 3 Exit Criteria 验证范围 = 非注册表驱动断言 + E2E 全绿。

Exit Criteria:

- [x] 生产端两实现类 `collect(OutputTag)` 均转发（grep/测试证据），fail-fast 文案从生产端移除
- [x] 消费端路由：注册消费者送达断言 + 未注册 fail-fast 断言全绿（先红后绿）
- [x] `registerSideOutputConsumer` 双路径（in-task + 跨 task）服务同一 map，接线断言在案
- [x] `DataPlaneWireSupport.toWireMap` 含 `outputTagId`（wire 层 round-trip 断言，审查 B1 证据）
- [x] materialization 双写路径按裁决处置（纳入 → 恢复测试；排除 → 留痕 + 测试注明）
- [x] **端到端验证**：跨 task 送达 E2E（生产 → 线协议 → 消费）含 BRWO 扇出变体全绿
- [x] **接线验证**：E2E 断言注册消费者确实被调用（非仅存在性）
- [x] **无静默跳过**：未注册消费者 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`（断言在案）
- [x] 远程路径（string codec + envelope）side-output round-trip 验证通过（非 Identity 默认路径）
- [x] 门禁红中间态已声明并验证（非注册表驱动断言全绿；注册表迁移留待 Phase 4）
- [x] core-design.md §6.1（若 Phase 3 落地与 Phase 1 定稿有出入，同步修正）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 门禁同步与全量验证

Status: planned
Targets: `output-contract-registry.json`、`check-nop-stream-invariants.mjs`、`invariant-catalog.md`、`core-design.md`、roadmap

- Item Types: `Fix | Proof`
- [x] [Fix] `output-contract-registry.json`：RWO/BRWO 行为分类 `fail-fast` → `forward`（disposition 附 tagged 说明 + 本 plan 批准依据）；**TimestampedCollector disposition 文案修订**（审查 M3，现文案引用「wrapped output is a fail-fast output」）；发射点行号复核重钉。
- [x] [Fix] `check-nop-stream-invariants.mjs` `scan-output-contract` V3（行为漂移检测）消费新分类；**`classifyCollectBody` 正则扩展或实现形态锁定**（`check-nop-stream-invariants.mjs:782-790`，审查 M1——`forwardRe` 不识别 `writer.emit(...)` 形态会抛 `unrecognized collect(OutputTag) method body form` crash）+ **self-test 正反例夹具更新**（:1503-1580，新增 emit 形态正例）。
- [x] [Proof] `mjs all` 全绿（含 scan-output-contract V1–V5 + self-test，正反例覆盖新分类 + 新实现形态）。
- [x] [Proof] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿 + 门禁 JUnit 类全绿 + E2E 全绿。
- [x] [Proof] `check-doc-links.mjs --strict` exit 0。
- [x] [Fix] roadmap backlog `HG-01` 条目：Status `pending human confirmation` → 已批准（2026-08-14）+ 已落地（本 plan 收口后）；**接线路径行号重钉**（审查 m2——条目 :145 现为 pre-I4 旧值 :645/:706、:239/:245、:352；live 为 :892/:960、:247/:285、:461-467）；四重保护性覆盖复核更新（in-task fail-fast 维持、门禁三态分类更新、**过渡 pin 2 条 = 历史记录**（审查 m5，已随 I4 移除，`mjs-pins.json` 注记在案）、本条目转 closed）。

Exit Criteria:

- [x] 注册表分类迁移完成且附棘轮理由（无静默豁免）；TimestampedCollector disposition 已同步
- [x] mjs `all` exit 0（含 self-test，正反例覆盖新分类 + emit 形态——审查 M1 证据）
- [x] 全量回归 0 failures（nop-stream 全组 + 门禁类 + E2E）
- [x] `check-doc-links.mjs --strict` exit 0
- [x] roadmap `HG-01` 条目 closed（批准记录 + 落地记录 + 行号重钉在案）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（跨 task side-output 静默丢弃面 → 完整线协议转发）
- [x] 所有 in-scope confirmed contract drifts 已收敛（注册表/catalog/core-design/E2E 与 live 行为一致）
- [x] 行为结果已达成：跨 task side-output 送达 + 无消费者 fail-fast + 双路径 + checkpoint 通道状态路径
- [x] 必要 focused verification 已完成（codec round-trip / 入站路由 / E2E 送达 / 远程路径）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（core-design.md §6.1、invariant-catalog.md #6、roadmap HG-01 条目）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）生产端 → 线协议 → 消费端路由调用链运行时连通（E2E 送达断言），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile`（`-pl nop-stream -am`）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过（变更集零新增违规）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

### `SingleOutputStreamOperator.getSideOutput` / DSL `<sideOutput>` 解封

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 消费面 = task 级 `registerSideOutputConsumer`（既有 API），跨 task 线协议端到端已验证；图级公共 API 是独立结果面（StreamEdge.outputTag 消费 + SideOutputTransformation + DSL 解封），需独立设计（partitioning/类型推断/图接线），不在本 plan 批准范围内。
- Successor Required: `yes`
- Successor Path: 新 plan（图级 side-output 消费 API + DSL `<sideOutput>` 解封），触发 = 本 plan 收口后按需评估。

### `StreamEdge.outputTag` 图模型消费

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 图模型字段零消费不产生数据丢失（跨 task 送达已由 task 级注册路径提供）；图级接线属上述 successor 的一部分。
- Successor Required: `yes`
- Successor Path: 同上。

## Non-Blocking Follow-ups

- C2-PR-3（重复注册 last-wins 语义）维持 backlog 触发条件不变。
- 发射点 E2E 覆盖扩展（C2-RL-3 + C2-PR-4 P3 优化）与本 plan 新增送达 E2E 部分重合，剩余 5 发射点无 E2E 覆盖维持 backlog。

## Closure

Status Note: 全 4 Phase 收口（2026-08-14）。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh task_id，非本 plan 执行者）
- Evidence: 独立 closure audit 针对 live 代码复核：① 生产端两实现类 `collect(OutputTag)` 转发（RWO :914 经 `writer.emitElement(SideOutputElement)` 广播；BRWO :981 扇出）——fail-fast 文案已从生产端移除；② 消费端 `processInputGate` side-output 分支（`sideOutputConsumers.keySet()` 按 `getId()` 匹配，未命中抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；③ 全链运行时连通（`TestSideOutputChainingE2E.testCrossTaskSideOutputDeliveryReachesRegisteredConsumer` 生产者 → 线协议 → 消费 task 注册消费者收到 99；BRWO 扇出 + D5 无别名化断言在案）；④ 无空方法体/静默跳过/no-op 作为正常实现（三态分类 = forward/fail-fast/无，注册表 V1-V5 零违规）；⑤ 门禁全绿（JUnit 门禁类 14/14、E2E 6/6、mjs all exit 0、doc-links exit 0、全量回归 0 failures）；⑥ 注册表分类迁移 fail-fast → forward 附棘轮理由（disposition + plan 批准依据在案），TimestampedCollector disposition 同步；⑦ roadmap HG-01 条目 closed + 行号重钉在案。audit PASS 结论与证据见 dev log 08-14 收口条目。

Follow-up:

- 图级 `getSideOutput` API / `StreamEdge.outputTag` 图模型消费 / DSL `<sideOutput>` 解封 = 本 plan Deferred But Adjudicated 节，另立 successor plan 按需评估（roadmap 注记在案）。
- C2-PR-3（重复注册 last-wins 语义）维持 backlog 触发条件不变。
- 剩余 5 发射点无 E2E 覆盖（C2-RL-3 + C2-PR-4 P3 优化）维持 backlog。

## Optional Sections

## Risks And Rollback

- **向后兼容**：envelope 新增字段默认 null、既有 5 类型零改动；`StreamElement` 新子类型不改变 `isRecord()` 等既有判定——旧 checkpoint 快照（无 side-output 元素）解码路径不变。
- **行为漂移门禁**：RWO/BRWO 分类迁移会触发 `scan-output-contract` V3 红——按本 plan Phase 4 同步迁移（非静默豁免，棘轮理由在案）。
- **回滚**：门禁同步 commit 与实现 commit 同批次；回滚需同时回退注册表分类（恢复 fail-fast 分类 + 实现回退），E2E 断言同步翻转。
