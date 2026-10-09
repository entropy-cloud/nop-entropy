# 2356 WI10 — `<sideOutput>` 剩余声明面：lateData tag 与 allowedLateness 联动

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI10（v8）
> Related: plan 2332（multi-engine WI8b sideOutput 主路径）、plan 2351（WI5 trigger）、plan 2354（WI8 earlyResults）、plan 2355（WI9 聚合目录）

## Purpose

把 roadmap WI10 收口到：`<window lateData="tag">` 声明面全链路可用——声明解析、builder 接线、tag 路由协议校验、迟到数据 E2E 进侧输出下游顶点；主路径 sideOutput tap（plan 2332 已交付）保持回归不变。经核实的 fail-fast 钉子面为空（见 Current Baseline），放行面即新增消费点。

## Current Baseline

live 核实（2026-10-09，HEAD 1c4dd6adb3 之后工作树）：

- **主路径 tap 已交付**（plan 2332，multi-engine WI8b）：`<sideOutput tag condition>` 节点 → `SideOutputTapOperator`；消费协议 `<edge from="<sideOutput 节点>" to="<transform>" outputTag="<tag>">`；`StreamModelDslBuilder.validateSideOutputDeclarations` 做 pairing fail-fast（duplicate tag / 消费边引用未声明 tag / 声明无消费边 / 同 tag 多消费边 / tag 边 partition≠FORWARD / tag 边 keyExpr / tag 边目标类型限 map/filter/flatMap/process/custom）。
- **运行时 late 通道已完备**：`WindowedStreamImpl.sideOutputLateData(OutputTag)`（public API，plan 369 Phase 2；`getLateDataOutputTag()` 为 public）→ `WindowOperator` 构造参数 `lateDataOutputTag` → 迟到元素路径 `isElementLate = eventTime && ts+allowedLateness <= wm` → 有 tag 则 `sideOutput(element)`（`output.collect(lateDataOutputTag, element)`），无 tag 则 `numLateRecordsDropped` 计数丢弃。`TestWindowedStreamSideOutputLateData`（runtime，Java API 面 E2E）已绿，且证明 allowedLateness=0 下 lateData 依然有意义（ts<=wm 的 skipped 记录即 late）——**因此不引入「lateData 必须配 allowedLateness>0」校验，机制本身即联动**。
- **allowedLateness 声明面已释放**（multi-engine D9）：xdef strategy+node 双面、`buildWindow` 消费合并值（node Long override strategy long）。
- **`<window>` 是 virtual build point**（Item 29 钉子）：不产生自有顶点；window operator 属于下游 `<aggregate>/<reduce>/<process>` 顶点（实测 `buildProcess` 不接受 WindowedStream，实际宿主面为 aggregate/reduce）；`streamRegistry["<window id>"]` 存 `WindowedStreamImpl`；其 `parallelism` 声明被 fail-fast 拒绝。
- **graph 层 tag 路由协议**：`Transformation.setInputOutputTag` → `StreamGraphGenerator` 将该输入边标记为 tag edge——协议与 producer 类型无关，只要 producer 算子执行 `output.collect(tag, record)`；宿主 aggregate 顶点可同时有主输出边与 tag 边，运行时 `EdgeWriterOutput` 按边过滤（主边弃 tagged、tag 边只放行匹配 tag），与 tap 形态同构。远端 wire 面（`RemoteGraphExecutionPlanBuilder` registerTagEdgeConsumer）已存在，同协议。
- **模块依赖边界（测试落点约束）**：nop-stream-flow 的 pom 无 nop-stream-runtime 依赖（runtime 依赖 flow，反向不可加）；flow 全量 build 含窗口宿主的模型会撞 `WindowOperatorFactory` 加载墙（`ERR_STREAM_UNSUPPORTED`，`TestAdvancedTransforms.aggregateDispatchesToWindowedAggregatePath` 正向钉此行为）。因此：**lateData 的结构校验必须全部落在 validateDag 阶段（factory 墙之前，flow 模块可测）；正向接线断言由 runtime E2E 承担**（inline fixture 先例 `TestEarlyResultsE2E`、tap E2E 先例 `TestSideOutputPipelineE2E`）。
- **真正剩余的 gap**：
  1. 声明面缺 `lateData`：xdef `<window>` 节点无该属性，`_StreamWindowModel` 无字段；
  2. `buildWindow` 不调 `sideOutputLateData`——声明永远到不了 operator；
  3. `validateSideOutputDeclarations` 只认 `<sideOutput>` 节点为 tag producer——lateData tag 的消费边会被 `ERR_STREAM_REF_UNKNOWN` 拒绝；
  4. 无声明面 lateData 测试；主路径 tap 的 E2E 已覆盖 tap 面回归。
- **fail-fast 钉子面为空**：`failFastOnUnsupportedWindowStrategy` / `failFastOnUnsupportedWindowNodeAttrs` 已是空体（triggerId/allowedLateness/earlyResults/evictor 已被 WI5/WI6/WI8/D9 释放），仅剩 strategy 级 ACCUMULATING_AND_RETRACTING 拒绝（D10 spec-only，与本 WI 无关）。本 WI 无钉子可删——放行面就是新增消费点。

## Goals

- `<window lateData="tagId">` 声明可用：xdef schema 面 + 模型字段 + `buildWindow` 接线到 `sideOutputLateData`（tag id 即声明值）。
- lateData tag 的消费协议：`<edge from="<window 的唯一 aggregate/reduce 宿主节点>" to="<single-input transform>" outputTag="<tagId>">`，运行时迟到记录经 tag 通道到达下游顶点。
- pairing 校验完备且全部前移到 validateDag 阶段：tag 非空、全局唯一（与 sideOutput tag、其他 window lateData tag 互斥）、宿主恰一（0 个或多个均拒绝）、恰一消费边、消费边 from 必须是宿主节点、目标类型/分区/keyExpr 约束同 tap 边。
- E2E：声明面模型 → build → execute → 迟到记录落侧输出下游 sink，主路径聚合结果不受影响（Anti-Hollow）。

## Non-Goals

- 不重写图级路由协议（roadmap 明示外部前置 multi-engine WI8b 已交付，本 WI 只做声明面接线）。
- 不做 strategy 级 `lateData`（lateData 是通道/可观测面，绑定具体 window 节点；allowedLateness 已双面，其合并值语义即 lateData 的联动语义，无需第二个声明面）。
- 不动 `WindowOperator` 运行时逻辑（late 路径/计数/清理均已存在）。
- 不做 CEP 组合的专项接线（roadmap 措辞「与 CEP/窗口组合」的窗口组合即本 WI 的窗口路径；CEP 侧无 late 概念，无接线点）。
- 不新增 L1/L2 lint 规则（WI2/WI5 矩阵不含 lateData 面；xdef 层已拒绝未知属性）。
- 不改 flow 模块 pom 依赖（runtime 不可反向依赖，见 Current Baseline 约束）。

## Scope

### In Scope

- `stream.xdef`：`<window>` 节点加 `lateData="string"` 属性。
- `_StreamWindowModel` 再生成（两步序列 + `_gen` 漂移守卫，本 WI 单面声明恰 1 个生成文件变更）。
- `StreamModelDslBuilder.validateSideOutputDeclarations` 扩展：lateData tag 结构校验（validateDag 阶段）。
- `AdvancedTransforms.buildWindow`：消费 `m.getLateData()` 接到 `sideOutputLateData`，tag id 即声明值（此时经 validateDag 已保证非空）。
- 新测试：flow 声明面 fail-fast 单测（validateDag 阶段）+ runtime E2E（fixture + 测试类，含正向接线断言）。
- owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md` 支持矩阵补 lateData 行。

### Out Of Scope

- sideInput / 生命周期回调 / 边级流控（WI11/WI13/WI14）。
- remote/deployment wire 面新改动（lateData tag 走既有 tag edge 协议；E2E 落本地嵌入模式）。
- `<process>` 宿主面（buildProcess 不接受 WindowedStream，宿主判定按 aggregate/reduce 实现；若未来 process 支持窗口宿主，校验面随之扩展）。

## Execution Plan

### Phase 1 - 声明面 schema/model/builder 接线与 pairing 校验

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`、`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/{AdvancedTransforms,StreamModelDslBuilder}.java`、`_StreamWindowModel`（再生成）

- Item Types: `Feature`

- [x] xdef `<window>` 节点加 `lateData="string"` 属性（tag id，可选）
- [x] 两步再生成序列刷新 `_StreamWindowModel`：`./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` → `./mvnw generate-sources -pl nop-stream/nop-stream-flow`；`_gen` 漂移守卫确认恰 1 个文件变更（仅 `_StreamWindowModel.java`），无既有字段意外丢失
- [x] `validateSideOutputDeclarations` 扩展 lateData 结构校验（validateDag 阶段，错误码沿用既有语义、detail 指明 lateData 面）：(a) ~~空串 → REQUIRED_ATTR~~ **裁定修订（执行期实测）**：`lateData=""` 经 xdef 归一化为未声明（探针实证 `getLateData()==null`，平台级可选 string 属性统一行为；`!string` 是必填约束不适用于可选属性），builder 空值面即 null，「不静默」落点在声明非空时的 (b)-(f) 全链校验——空串无独立校验条目；(b) lateData tag 与任何 `<sideOutput>` tag 或其他 window 的 lateData tag 撞名 → `ERR_STREAM_DUPLICATE_ID`；(c) 宿主判定——声明 lateData 的 window 必须恰有一个 aggregate/reduce 下游宿主（0 个或 >1 个 → `ERR_STREAM_INVALID_ARG`，禁止静默取第一宿主）；(d) lateData tag 必须恰有一个消费边（缺失 → `ERR_STREAM_INVALID_ARG`，多条 → `ERR_STREAM_INVALID_ARG`）；(e) 消费边 outputTag 引用 lateData tag 时 from 必须是宿主节点 → 否则 `ERR_STREAM_REF_UNKNOWN`；(f) tap 边的 partition/keyExpr/目标类型约束对 lateData 消费边同效
- [x] `buildWindow` 消费 lateData：非空时接到 `sideOutputLateData`，tag id 即声明值（validateDag 已保证非空与宿主恰一；build 阶段不做重复校验）
- [x] 主路径回归：`<sideOutput>` tap 全部既有 pairing 校验行为不变
- [x] owner-doc 裁定：本 Phase 改 live baseline，文档更新集中 Phase 3（user guide 矩阵 + roadmap），本 Phase 无独立 owner-doc 更新

Exit Criteria:

- [x] `StreamWindowModel.getLateData()` 存在且来自再生成产物（非手写 `_` 前缀文件）；`_gen` 漂移恰 1 文件核实记录在 daily log
- [x] lateData 结构校验代码落位（validateDag 阶段可触达，不依赖 runtime factory）；既有 flow 测试回归绿
- [x] **无静默跳过**：lateData 空串、宿主 0/多、无消费边/多消费边均为 typed reject（行为验证在 Phase 2 单测）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am`（排除已知时序抖动测试，见日志先例）全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试：声明面校验（flow）+ runtime 迟到数据 E2E（正向接线）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/test/java/.../TestWi10LateDataDeclaration.java`（新）、`nop-stream/nop-stream-runtime/src/test/java/.../integration/TestLateDataSideOutputE2E.java`（新）。**执行形态修订**：E2E 采用 inline stream xml（`TestTriggerCountE2E` 先例，`InMemoryBeanFunctionResolver` 注册 bean）替代原拟的 fixture 文件 + beans.xml——同为声明面全链路（parseFromNode → DslBuilder → execute），少两个静态资源文件。

- Item Types: `Proof`

- [x] flow 单测（`TestWi10LateDataDeclaration`，8 用例，inline DSL，全部在 validateDag 阶段触发、不触碰 factory 墙）：
  - lateData 与 sideOutput tag 撞名 → `ERR_STREAM_DUPLICATE_ID` ✅
  - 两个 window 声明同一 lateData tag → `ERR_STREAM_DUPLICATE_ID` ✅
  - 消费边 from 非宿主节点（from=window 节点本身）→ `ERR_STREAM_REF_UNKNOWN` ✅
  - lateData 无消费边 → `ERR_STREAM_INVALID_ARG`；两条消费边 → `ERR_STREAM_INVALID_ARG` ✅
  - window 无宿主下游声明 lateData → `ERR_STREAM_INVALID_ARG`；两个 aggregate 消费同一 window（多宿主）声明 lateData → `ERR_STREAM_INVALID_ARG` ✅
  - lateData tag 边目标为 keyed 目标 → `ERR_STREAM_INVALID_ARG` ✅
  - ~~lateData="" → `ERR_STREAM_REQUIRED_ATTR`~~ **裁定修订**：空串经 xdef 归一化为未声明（见 Phase 1 (a)），无独立用例
  - 正向 build 断言不在 flow 模块（factory 墙约束，见 Current Baseline）——由 runtime E2E 承担
- [x] runtime E2E（inline stream xml）：source [10,120,50]（ts=value）→ timestampsAndWatermarks（per-event wm=ts-10）→ keyBy → window（tumbling 100ms + lateData="late-tag"）→ reduce(sum) → mainSink；`<edge from="sum" to="lateMap" outputTag="late-tag">` → lateMap → lateSink（tag 边起于宿主 reduce 顶点）。数据面用 `TestWindowedStreamSideOutputLateData` 的成熟形态；接线断言走数据面等价证明 + **变异探针**（断开 `sideOutputLateData` 接线 → E2E 必红 late=[]；恢复后 grep 断言恰 1 处可执行行 → 复绿）
- [x] E2E 断言（Anti-Hollow）：mainSink=[10,120]（on-time fire + 结束冲刷）且迟到记录 50 不在其中（反事实结构）；lateSink=[50] 恰收迟到记录
- [x] 主路径 tap 回归：`TestSideOutputPipelineE2E` 与 `TestStreamModelDslBuilderFailFast` 既有用例不改动且全绿（flow 全量 261 绿）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime`（涉及模块全跑，排除已知抖动，隔离复跑核实）——全量 1351 绿（connector 域 4 失败经定位为并发会话陈旧 m2 jar：debezium/jdbc/connector-batch/message-core/api-core 刷新后全绿，与本 WI 改动面无关，见 daily log）

Exit Criteria:

- [x] 新增测试全部绿且断言含数据内容（非仅「不抛异常」）
- [x] **端到端验证**：inline stream xml → DslModelParser → StreamModelDslBuilder → env.execute() → WindowOperator late 路径 → tag edge → lateMap → lateSink 完整链路有内容断言
- [x] **接线验证**：声明 → `sideOutputLateData` → operator 构造 → `output.collect(tag)` 全链路有 runtime 数据面证据 + 变异探针（断线红/恢复绿）；主路径 tap 无回归
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 与 `-pl nop-stream/nop-stream-runtime -am` 全绿（flow 261 绿；runtime 1351 绿；已知抖动清单未再现）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner doc 同步 + closure audit + 提交

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-user-guide.md`、`ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`、本 plan

- Item Types: `Follow-up`（文档）/ `Proof`（audit）

- [x] user guide 支持矩阵：窗口段补 `lateData` 属性声明与消费协议形态（含宿主节点规则与 allowedLateness 联动语义）
- [x] roadmap WI10 行勾选（带日期/plan 号/audit agent 标注），header 版本注记升 v9
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（fresh session）：逐 Gate 核验 live 代码与测试证据，Anti-Hollow 追踪 `execute()` 到 lateSink 的调用链——**APPROVE**（agent_4a3f9094，A1-A4/B1-B4/C1-C3/D1-D3/E3/F 全 PASS；亲跑 8+1+10+1 测试绿）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs nop-stream/nop-stream-flow --severity high` 退出码 0（audit 实测：60 文件 0 发现；注：`--module nop-stream-flow` 形态与工具路径语义不匹配，按直接路径形式为准——audit minor-1 裁定）
- [x] 文本一致性核对（Plan Status / Phase Status / Exit Criteria / Closure Gates / daily log）
- [x] 单次提交（plan + 实现 + 测试 + 文档 + roadmap 勾选 + daily log）

Exit Criteria:

- [x] 上述各项完成，audit 结论 APPROVE 且 evidence 写入本 plan Closure 段
- [x] roadmap WI10 已勾选且与 plan/日志一致

## Closure Gates

- [x] Phase 1/2/3 全部 completed
- [x] 声明面全链路（xdef → 模型 → 校验 → builder → operator → tag 路由 → 下游 sink）live 可验证
- [x] 主路径 tap 与全部既有窗口声明面测试无回归
- [x] focused verification：新增单测 + E2E 全绿证据在 daily log
- [x] 无 in-scope live defect 被降级为 follow-up
- [x] owner docs 同步（user guide 矩阵 + roadmap）
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：audit 验证端到端调用链连通 + 无空壳/静默跳过
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am`（或分模块）全绿
- [x] `check-plan-checklist.mjs --strict` 与 `scan-hollow-implementations.mjs` 退出码 0

## Deferred But Adjudicated

（暂无——若执行中出现范围外发现，按 guide 规则显式登记）

## Non-Blocking Follow-ups

- Phase 2 E2E 若发现 remote/deployment wire 面对 lateData tag 需要额外接线，登记到 WI27 门禁时统一核（当前判断走同一 tag edge 协议，无独立改动；audit 复核：JobEdge.getOutputTagId 驱动、RemoteGraphExecutionPlanBuilder:328 既有注册，producer 无关协议成立）。

## Closure

Status Note: 声明面全链路落地并有双重证据：结构校验（8 fail-fast 用例，validateDag 阶段）+ 数据面 E2E（main=[10,120]/late=[50] 反事实断言）+ 变异探针（断开 sideOutputLateData 接线必红、恢复后 grep 断言恰 1 处可执行行复绿）。独立 closure audit（agent_4a3f9094，fresh session）全项 PASS/APPROVE：Anti-Hollow 代码级调用链（buildWindow → WindowedStreamImpl 四 factory 路径 → WindowOperator 构造 → isElementLate → sideOutput → EdgeWriterOutput tag 边路由）与数据面双证；flow 261 / runtime 1351 回归绿；文档三处同步（user-guide 专段+矩阵行、roadmap v9+WI10 勾选+E3 行）。执行期两项裁定（均有探针/实测依据并记录）：lateData="" 经 xdef 归一化为未声明（无独立校验）；E2E 采用 inline DSL 形态（TestTriggerCountE2E 先例）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: agent_4a3f9094（独立 fresh-session closure auditor）
- Audit Session: agent_4a3f9094-c3fd-44a9-81c4-f598f97a10be
- Evidence:
  - Phase 1 Exit：A1 PASS（_StreamWindowModel.java:135 getLateData 再生成产物、_gen 漂移恰 1 文件）；A2 PASS（build():209→buildTransforms:231→validateDag:491→validateSideOutputDeclarations:570 调用链，六类校验 :653-:891 逐一定位）；A3 PASS（buildWindow :225-228 接线非注释）；A4 PASS（五条 typed-throw 路径）
  - Phase 2 Exit：B1 PASS（TestWi10LateDataDeclaration 恰 8 用例断言错误码+token）；B2 PASS（E2E :126 main=[10,120] / :132 late=[50] / :135 反事实）；B3 PASS（audit 亲跑两测试类 fresh surefire 8+1 全绿）；B4 PASS（FailFast 10/10 + Tap E2E 1/1）
  - Anti-Hollow：C1 PASS（lateDataOutputTag 经 :330/:351/:369/:387 四条 factory 路径传至 WindowOperator:487）；C2 PASS（late 路径 :955-961 → :1786 collect → setInputOutputTag → StreamGraphGenerator:309 → EdgeWriterOutput:1376 tag 边路由）；C3 PASS（daily log 变异探针记录在案）
  - 文档：D1/D2/D3 PASS（user-guide :175/:255/:177；roadmap v9/:134/:77；doc-links --strict 0）
  - 工具：check-plan-checklist --strict 退出码 0（Closure 回填后终验）；scan-hollow 直接路径形式退出码 0（60 文件 0 发现；--module 形态与工具参数语义不匹配，audit minor-1）
  - Deferred 分类检查：无 in-scope defect 降级（audit F 项 PASS）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2356-wi10-sideoutput-late-data.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- remote/deployment wire 面 lateData tag 条件性检查 → WI27 门禁统一核（non-blocking，见 Non-Blocking Follow-ups）
