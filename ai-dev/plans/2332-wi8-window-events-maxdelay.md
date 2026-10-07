# 2332 WI8 窗口事件化与兜底出数（OPEN/CLOSE/IDLE/RESUME + sideOutput 图级路由 + maxDelay）

> Plan Status: draft
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI8（M2，单一连贯面三子项）+ 对比分析 P1-7（TDengine 对标）+ 10-06 XDSL 可移植性分析
> Related: WI17（SQL 窄补强引用本 WI 事件面——XDSL 声明面见 Deferred m1）；plan 2329（OutputTag 先例）

## Purpose

把窗口生命周期事件化（OPEN/CLOSE/IDLE/RESUME 外发）、XDSL sideOutput 图级路由打通、maxDelay 超时兜底出数作为一个连贯面落地（TDengine 对标）：窗口状态变化可观测可路由，慢数据有兜底出数保障。

## Current Baseline（研究 + audit 双重核实）

- WindowOperator（runtime operators/windowing/WindowOperator.java）：窗口**隐式**存在于 keyed-state 命名空间（`addWindowElement:1427` 纯状态追加，无存在性检查/枚举 API）；FIRE = `emitWindowContents:1037`（PaneInfo timing 分类 EARLY/ON_TIME/LATE——只分类无幂等）；清理 = onTimer `isCleanupTime` 分支（cleanupTime = maxTimestamp + allowedLateness；processing-time 窗口 cleanup = windowEnd；GlobalWindows cleanupTime = Long.MAX_VALUE 即永不清理）。
- 侧输出设施（比预想完整）：core `OutputTag`、`WindowContext.output(tag,v)`（用前须 set window 否则 NPE）、迟到数据 `sideOutputLateData(OutputTag)` 已在用；**HG-01 跨任务传输已建成**——`StreamMessageEnvelope.outputTagId`/`TYPE_SIDE_OUTPUT_RECORD`、`SideOutputElement`、`StreamElementCodec` 双向编解码、`StreamTaskInvokable:990-1037` 收端按 tagId 分派 + `registerSideOutputConsumer:418`、链内 `ChainingOutput:111-129` 分派（无消费方 fail-fast）；core `StreamEdge:85` 已有 outputTag 字段但 StreamGraphGenerator/JobGraphGenerator **零消费**。
- XDSL：stream.xdef（**位于 nop-kernel/nop-xdefs**）已有 `<sideOutput tag condition>` 声明；`AdvancedTransforms.buildSideOutput:810-820` 抛 NOT_IMPLEMENTED；builder failFastOnUnsupportedRegistries:424-429 对 `<sideInputs>` 抛错；`<edge>` 无 outputTag（StreamEdgeModel 无该字段）；Java 消费面 `SideOutputDataStream` map/filter/keyBy 全部 ERR_STREAM_UNSUPPORTED。XDSL 窗口路径 = `AdvancedTransforms.buildWindow:190-212` 消费 `WindowingStrategyModel` → `WindowedStreamImpl`。
- maxDelay 全仓无；flow 模型类继承生成物（`_gen/_WindowingStrategyModel` 由 nop-stream-flow 的 exec-maven-plugin 在 generate-sources 阶段跑 `precompile/gen-stream-xdsl.xgen` 生成——**前提：改过的 nop-xdefs 先进 classpath**）。
- IDLE 对应物：`WatermarkStatus.IDLE/ACTIVE` 跨任务传播已交付；operator 接收点 = **独立方法 `processWatermarkStatus`**（非 processWatermark；`AbstractStreamOperator:455` 默认转发；同包 `OverWindowOperator:309` 有覆写先例）。WindowOperator 未覆写。
- 真实缺口层：①活动窗口 registry（无）；②xdef edge.outputTag + strategy.maxDelay + 再生成；③AdvancedTransforms.buildSideOutput 实现；④StreamGraphGenerator/JobGraphGenerator 的 outputTag 边消费；⑤SubtaskPlanBuilder/RemoteGraphExecutionPlanBuilder 的 tag 消费方注册；⑥XDSL maxDelay 全链接线。
- 回归面：runtime windowing 包 37 个测试文件；fail-fast 测试 TestStreamModelDslBuilderFailFast:69-71、TestAdvancedTransforms:523、:83-88 需随支持面翻转。

## Goals

### 裁定节（audit B1/B2/M1-M7 处置）

- **R-registry（B1）**：WindowOperator 增 **checkpointed 活动窗口 registry**——operator-state map（paneKey 键控，照 paneTracking 快照先例进出 checkpoint；**非 keyed state**——keyed state 无法跨 key 枚举，IDLE 全集必须可枚举）。OPEN = registry 首次插入该窗口时发（状态清空不清 registry——DISCARDING fire 后同窗口再来数据不重发 OPEN；FIRE_AND_PURGE/purge 移除 registry → 后续数据 = 新窗口实例重新 OPEN）。IDLE 枚举 = registry 全集。恢复 = registry 随 checkpoint 还原（不重发 OPEN）。**merging 语义**：merge 回调中移除被合并窗口条目 + 存留窗口条目**转移**到新 actualWindow 且不触发 OPEN（键法选 operator-state map 直接改键）；补 session-merge 不重发 OPEN 的测试项。
- **R-dispatch（B2）**：消费端分派链 = XDSL `<sideOutput tag condition>`（声明 transform 的 tag 复制语义：**匹配 condition 的记录复制进 tag 通道，主输出原样不变**）→ StreamGraph/JobGraph tag 边（激活 StreamEdge.outputTag 既有字段）→ SubtaskPlanBuilder/RemoteGraphExecutionPlanBuilder 为 tag 边目标注册 side-output consumer（复用 `StreamTaskInvokable.registerSideOutputConsumer`）喂目标算子输入。tag 边只载 SideOutputElement；tag 边不支持 keyBy 定制（跟随生产者 partition；声明 keyExpr = build 期 typed 拒绝）。`validateDag` 配对 = 声明 sideOutput 的 tag 必须存在 `from=<sideOutput 节点> to=<transform> outputTag=<tag>` 消费边（from 推定为 sideOutput 节点 id），反向（消费未声明的 tag）亦报错；**同 tag 多消费边 = validateDag 拒绝**（注册中心为替换语义，多消费=静默顶替）；tag 边声明 `partition`/`keyExpr` 一并 typed 拒绝。
- **R-status（M1）**：IDLE/RESUME 挂 `processWatermarkStatus` 覆写（保留 `output.emitWatermarkStatus` 转发——掐断会破坏下游 idleness 传播）；IDLE = 收到 IDLE status；RESUME = 收到 ACTIVE status 或此后首条数据。
- **R-maxdelay（M2/M3/M4）**：适用域 = **event-time 窗口**（processing-time 窗口 + maxDelay>0 = build 期 typed 拒绝，no-silent-skip）。timer 区分 = **pendingMaxDelay 标记集**（keyed state，checkpoint 参与）：maxDelay timer 触发时先查标记，命中则走兜底 FIRE（不回调 user triggerContext，杜绝 user trigger 误喂）并清标记；FIRE 不 purge（allowedLateness 兼容）。merge 处理 = 被合并窗口的 maxDelay 标记/timer 删除、合并窗口按新 end 重注册（仿 cleanup timer 的 merge 回调）。兜底 FIRE 的出数 timing 复用 PaneInfo EARLY（accumulation 语义不变；不新增枚举值——二次触发由 accumulation 模式既有语义承载）。
- **R-xdef（M5）**：xdef 改动在 nop-kernel/nop-xdefs；顺序 = 改 xdef → `./mvnw install -pl nop-kernel/nop-xdefs` → nop-stream-flow generate-sources 再生成（reactor 内 `-am` 亦可）→ 编译消费新字段。
- **R-sideinputs（M7）**：`<sideInputs>` 维持 fail-fast（本 WI 零解除），edge.outputTag 是唯一消费声明面——无双声明冲突。
- **R-events-tag（m2）**：窗口事件固定 OutputTag 的 id = `operatorUid + ":window-events"`（每算子唯一，避免进程级注册互相顶替）。

### (a) 窗口事件模型与外发
- core `WindowLifecycleEvent`（type OPEN/CLOSE/IDLE/RESUME + key + windowStart/End，Serializable）。
- 四事件点（见 R-registry/R-status）+ CLOSE = cleanup 分支（GlobalWindows 无 cleanup → 仅 OPEN/IDLE、无 CLOSE，registry 条目随算子关闭释放——user-guide 注明）。
- 外发 = 专用 OutputTag（R-events-tag），经 WindowContext.output（先 set window）；默认关闭（`WindowedStream.emitWindowEvents()` 显式启用；XDSL 声明面不在本 WI，见 Deferred）。

### (b) XDSL sideOutput 图级路由
- xdef `<edge>` 增 outputTag 属性（R-xdef 流程）+ StreamEdgeModel 字段 + 生成器消费（R-dispatch 链）。
- `AdvancedTransforms.buildSideOutput` 实现（复制语义 + condition）+ validateDag 配对校验；`<sideInputs>` 维持 fail-fast。

### (c) maxDelay 兜底出数
- xdef strategy.maxDelay + 再生成 + core 字段 + WindowingStrategyModel/Builder/WindowedStream API + AdvancedTransforms.buildWindow 消费 + **WindowOperator 全部构造器含 copyForSubtask copy 构造器透传**（漏拷 = 每 subtask 行为分叉）+ processing-time 窗口 typed 拒绝 + R-maxdelay timer/标记/merge。

## Non-Goals

- broadcast sideInputs（维持 fail-fast，Deferred）；XDSL emitWindowEvents 声明面（Deferred m1）；跨进程 sideOutput registry 新机制（HG-01 envelope 通道已够）；trigger 语义变更；SQL 面接线（WI17）。

## Scope

### In Scope

- core：WindowLifecycleEvent、活动窗口 registry（含 checkpoint）、processWatermarkStatus 覆写、WindowingStrategy.maxDelay、maxDelay timer/标记集/merge 处理、StreamEdge.outputTag 消费（生成器）。
- flow：xdef（nop-xdefs）edge.outputTag + strategy.maxDelay + 再生成、buildSideOutput 实现、validateDag 配对、AdvancedTransforms（buildSideOutput/buildWindow/failFast 放行与消费）、WindowedStream/WindowedStreamImpl API。
- runtime：WindowOperator 全事件点 + copy 构造器透传 + maxDelay timer、SubtaskPlanBuilder/RemoteGraphExecutionPlanBuilder tag 消费方注册。
- 测试：事件时序（OPEN→数据→FIRE→再数据（不重发 OPEN）→CLOSE；IDLE→RESUME）、maxDelay 兜底（慢 watermark 流强制出数 + merge 场景 + processing-time 拒绝 + user trigger 不被误喂）、XDSL sideOutput 全链 E2E、fail-fast 矩阵更新。
- docs：user-guide（事件/maxDelay/sideOutput 用法 + GlobalWindows 无 CLOSE + cold-start 注意）。

### Out Of Scope

- broadcast sideInputs、XDSL emitWindowEvents、跨进程 registry 新机制、trigger/窗口分配语义变更。

## Execution Plan

### Phase 1 - (a)+(c) 引擎面（core + runtime + XDSL 接线）

Status: planned
Targets: core WindowOperator/WindowingStrategy、runtime、flow XDSL 接线

- Item Types: `Feature`

- [ ] R-registry：checkpointed 活动窗口 registry + OPEN/CLOSE 事件点
- [ ] R-status：processWatermarkStatus 覆写（保留转发）+ IDLE/RESUME
- [ ] R-maxdelay：xdef strategy.maxDelay（R-xdef 流程）+ core 字段 + 全链接线（M6 清单）+ pendingMaxDelay 标记集 + timer + merge 处理 + processing-time 拒绝
- [ ] `WindowedStream.emitWindowEvents()` + 测试：事件时序（含 DISCARDING 再来数据不重发 OPEN）、IDLE→RESUME、maxDelay 兜底/merge/拒绝/user-trigger 不误喂、默认关闭零回归

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-kernel/nop-xdefs -am` 全绿（R-xdef 顺序：先装 nop-xdefs）
- [ ] **端到端验证**：OPEN→数据→FIRE→CLOSE 时序断言；maxDelay 不依赖主 trigger 的兜底出数断言
- [ ] **接线验证**：XDSL maxDelay 声明 → operator 生效（builder 消费断言）；copyForSubtask 透传断言
- [ ] **无静默跳过**：processing-time 窗口 + maxDelay = build 期 typed 拒绝；事件未启用零外发
- [ ] user-guide 新增最小段落（emitWindowEvents/maxDelay 公共 API 是本 Phase 新增面——rule 17 要求）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - (b) XDSL sideOutput 图级路由

Status: planned
Targets: xdef、flow builder、图生成、runtime tag 消费注册

- Item Types: `Feature`

- [ ] xdef edge.outputTag（R-xdef 流程）+ StreamEdgeModel 字段 + StreamGraphGenerator/JobGraphGenerator 激活 outputTag 边
- [ ] buildSideOutput 实现（复制语义 + condition）+ validateDag 配对校验 + SubtaskPlanBuilder/RemoteGraphExecutionPlanBuilder tag 消费方注册（复用 registerSideOutputConsumer）
- [ ] 测试：XDSL 声明 → tag 边 → 下游消费全链 E2E；fail-fast 矩阵更新（buildSideOutput 支持面、sideInputs 维持 fail-fast、validateDag 双向配对错误）

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 全绿
- [ ] **端到端验证**：XDSL sideOutput + edge outputTag → 下游 transform 收到分流记录全链断言
- [ ] **接线验证**：AdvancedTransforms 原 fail-fast 处真实产生路由（E2E 断言下游收到）
- [ ] owner docs（user-guide）已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - closure audit 与提交

Status: planned
Targets: roadmap WI8、plan 2332

- Item Types: `Proof`

- [ ] 独立子 agent closure audit；roadmap WI8 勾选；git commit
- [ ] No owner-doc update required（Phase 2 已更新）

Exit Criteria:

- [ ] 独立 closure audit 通过且证据写入本 plan Closure 节
- [ ] roadmap WI8 已勾选、提交完成

## Closure Gates

- [ ] 三子项落地且联动（maxDelay 出数路径发窗口事件流）
- [ ] 默认关闭零回归（37 个 windowing 测试文件全绿）
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-core,nop-stream/nop-stream-flow,nop-kernel/nop-xdefs -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-import-order.mjs` 受影响模块无新增违规
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 与 `--module nop-stream/nop-stream-flow --severity high` 均退出码 0
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### XDSL emitWindowEvents 声明面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 事件经 Java API `WindowedStream.emitWindowEvents()` 可启用；XDSL strategy 声明面属类型化声明扩展（与 connectors.md 类型化裁定同域），WI17 引用事件面时经 Java API 组合可行
- Successor Required: `no`

### broadcast sideInputs

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 维持 fail-fast（R-sideinputs 裁定）；无当前需求触发
- Successor Required: `no`

## Closure

Status Note: （closure 时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:（closure 时填写）

Follow-up:

- （closure 时填写）
