# 44-A Materialization Point Mechanism（Region Failover 前置 #1）

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Item 44（successor plan 1/5）; `ai-dev/design/nop-stream/failover-design.md` §9（go 裁定 + 物化点语义选项 B + blast radius + scope）+ §五.1（物化点支持，术语已更新为选项 B）; `ai-dev/design/nop-stream/00-vision.md` §七（聚焦定位不变）
> Mission: nop-stream-production
> Work Item: 44. Region-based failover — successor plan 1/5（materialization point mechanism）
> Related: **决策 plan** `2026-08-03-1403-1-region-based-failover.md`（completed — go confirmed）; **successor 2** `2026-08-03-1600-2-region-identification.md`（region 自动识别，硬依赖本 plan 的物化 marker）; **successor 3** `2026-08-03-1600-3-supervision-loop-execution-model.md`（supervision loop，消费本 plan 的重放能力）; **successor 4** drain/reconnect（TBD，消费本 plan 的 epoch marking + 交付 consistent-cut 对齐协议）; Stage 27 `2026-07-26-0433-2-targeted-failover.md`（NO-GO 裁定，本 successor chain 解除之）

## Purpose

引入物化点**机制**（选项 B 流式 + 物化点），使 region 边界的 producer/consumer 生命周期可解耦——producer 写主 queue 同时写物化存储（旁路），consumer 可从物化点重放。本 plan 为后续 region-based failover 提供**数据面机制基础**：解除 Stage 27 NO-GO §3.3 死锁 2（上游死→下游永挂）的**重放能力**，并为死锁 1（下游死→上游阻塞）提供机制基础（完整解除需 successor 4 drain/reconnect 的 producer 非阻塞逻辑配合）。

本 plan **只交付机制 + 显式 opt-in 标记**，不交付 region 边界**自动识别**（successor 2）、mid-execution 重启（successor 3）、consistent-cut 对齐协议与 reconnect（successor 4）。

## Current Baseline

经 live 仓库核对（2026-08-03，独立子 agent 已验证引用一致性）：

- **所有 edge 均为 pipelined**：`JobGraphGenerator.determinePartitionType()`（`JobGraphGenerator.java:557-565`）partitioner==null → `PIPELINED`（`:560`），非 null → `PIPELINED_BOUNDED`（`:563`）。`ResultPartitionType.BLOCKING`（`ResultPartitionType.java:70`，`pipelined=false`）**从不被产生**，`isBlocking()`（`:127-129`）零调用者。本 plan 不改变此默认（选项 B：物化是 edge 级附加 metadata，非枚举变更）。
- **数据交换 = by-reference 阻塞队列**：`ResultPartition`（`ResultPartition.java:50`）持有 `LinkedBlockingQueue<StreamElement>`，`write()`（`:109-129`）满时 `queue.put()` 阻塞。无旁路物化写入路径。
- **消费侧直接持有引用**：`InputChannel` 直接持有 `ResultPartition` 引用消费；无重放路径。`RecordReader<T>`（`io.nop.stream.core.execution.RecordReader`）是 wraps `InputChannel` 的 thin wrapper（`read()`/`isFinished()` 委托），其重放能力随 `InputChannel` 自动受益——本 plan 的重放路径以 `InputChannel` 为修改目标。edge 级元数据载体是 `JobEdge`（代码库无 `IntermediateResult` 类）。
- **无物化点抽象、无 region 概念**：生产代码仅 `JobCoordinator.java:220,891` 两处 forward-looking 注释提及 region；无物化存储/重放基础设施。
- **决策已落**：`failover-design.md` §9.8 裁定 go（选项 B 流式 + 物化点）；§9.1 定义语义、§9.2 blast radius、§9.4 死锁解除机理表。§五.1 术语已更新为选项 B（物化点 marker，非 BLOCKING 枚举）。

### 真正剩余的 gap

- 物化点机制完全缺失（旁路写入 / 重放 / epoch marking / in-memory 存储）——本 plan 交付对象。
- consistent-cut 对齐**协议**（重放起点选择）缺失——successor 4（本 plan 只交付 epoch **标记**，对齐协议属 plan 4）。
- region 边界自动识别缺失——successor 2。
- supervision loop / reconnect / per-region counter 缺失——successor 3/4/5。

## Goals

- **物化点机制**：edge 显式标记启用物化后，producer 写主 queue **同时**写物化存储（旁路），consumer 可从物化点重放。
- **epoch 标记**：物化内容自带 epoch 标记（数据打 tag），为 successor 4 的 consistent-cut 对齐协议提供基础。
- **in-memory 物化存储实现**（生产级 RocksDB/磁盘物化属 Non-Goal）。
- **不改变默认 partition type**（仍 PIPELINED/PIPELINED_BOUNDED）；物化是 `JobEdge` 级 opt-in 附加元数据。

## Non-Goals

- **consistent-cut 对齐协议**（重放起点选择算法）——属 successor 4 drain/reconnect（`failover-design.md` §9.4 明确归属 §五.4）。本 plan 只交付 epoch **标记**，不交付对齐**协议**。
- **死锁 1 完整解除**（主 queue 满时 producer 不阻塞）——需 successor 4 的 producer 非阻塞逻辑配合；本 plan 只提供物化旁路**机制基础**。
- region 边界**自动识别**（successor 2）——本 plan 只交付 edge 显式 opt-in 标记的机制。
- supervision loop / mid-execution 重启（successor 3）。
- drain/reconnect 完整协议（successor 4）。
- per-region restart 计数器（successor 5）。
- RocksDB/磁盘物化存储（优化项，in-memory 先正确）。
- 改变流式执行定位（vision §七 不变，§9.3 已确认选项 B 一致）。
- 跨 JVM 物化（in-process 先正确）。

## Scope

### In Scope

- 物化点 SPI（可独立寻址、按 epoch 标记的物化缓冲抽象）+ in-memory 实现。
- `ResultPartition` 物化写入旁路路径（edge 标记启用时，write 主 queue + 物化 store；不启用时走原 by-reference 路径）。
- consumer 重放路径（`InputChannel` 从物化点读取；恢复时激活）。
- edge 级物化启用标记（**载体 = `JobEdge`**，region 分解以 edge 为切分点；不改 `ResultPartitionType` 枚举默认产生路径）。
- 物化数据 epoch **标记**（数据打 tag）——对齐**协议**属 successor 4。
- 测试：组件级（dual-write + replay round-trip）+ 接线验证（ResultPartition→store、InputChannel←store 调用连通）。

### Out Of Scope

- consistent-cut 对齐协议、producer 非阻塞逻辑（successor 4）。
- region 自动识别（successor 2）。
- supervision loop（successor 3）。
- per-region counter（successor 5）。
- RocksDB/磁盘物化、跨 JVM 物化。

## Deliverable Contracts（for downstream plans）

> 本 plan 交付的**可观测契约**（非代码签名——命名/数据结构属源码层）。下游 plan 据此消费。

- **物化 marker（给 successor 2）**：`JobEdge` 上可查询"是否启用物化"的布尔标记。successor 2 的 region 分解算法以此标记为切分依据——物化启用 edge 跨 region，未启用 edge 连通同一 region。
- **重放能力（给 successor 3）**：`InputChannel`（或其恢复等价物）提供"从物化点读取已物化数据"的能力，供 supervision loop 重启 consumer 后消费。本 plan 交付**能力**，"何时激活重放"由 successor 3 的重启调度触发。
- **epoch 标记（给 successor 4）**：物化数据自带 epoch tag。successor 4 的 consistent-cut 对齐协议据此选择重放起点。
- **不交付**：consistent-cut 对齐算法、producer 非阻塞溢出逻辑、reconnect-to-live-queue 切换——均属 successor 4。

## Execution Plan

### Phase 1 — 物化点机制 + epoch 标记

Status: completed
Targets: `nop-stream-core/execution/`（物化点抽象）; `ResultPartition.java`; `InputChannel`（重放路径）; `JobEdge`（物化 marker）; `failover-design.md`/`state-management-design.md`（owner-doc）

- Item Types: `Fix | Decision | Proof`

- [x] 物化点 SPI + in-memory 实现（可独立寻址、按 epoch 标记的物化缓冲）——`Decision`：存储抽象边界（write/replay/按 epoch 查询）属本 plan 设计决策，记录于 owner-doc
- [x] `ResultPartition` 物化写入旁路（edge 启用时，write 主 queue + 物化 store；不启用时走原 by-reference 路径）——`Fix`
- [x] `InputChannel` 重放路径（从物化点读取；恢复时激活）——`Fix`
- [x] edge 级物化启用标记（载体 = `JobEdge`；`determinePartitionType` 默认值不变）——`Decision`
- [x] 物化数据 epoch **标记**（数据打 tag；consistent-cut 对齐协议属 successor 4，本 plan Non-Goal）——`Fix`
- [x] 组件级测试：dual-write round-trip（write store → replay → 一致）+ 接线测试（ResultPartition→store、InputChannel←store 调用确实发生）——`Proof`

Exit Criteria:

- [x] 物化点 SPI + in-memory 实现存在，且被 `ResultPartition` 在 edge 启用时调用（**接线验证** #23：物化启用 edge 上 `ResultPartition.write` 确实调用物化 store，断言可观测）
- [x] `InputChannel` 重放路径存在且可从物化点读取（**接线验证** #23：重放调用确实命中物化 store，断言可观测）
- [x] edge 物化标记可显式设置在 `JobEdge` 上且不依赖 region 自动识别（手动标记即可启用物化）
- [x] 物化数据带 epoch tag（断言可观测）
- [x] **端到端验证说明**（#22）：本 plan 的"完整 mid-execution 重启 E2E"需 successor 3 的重启机制，**显式延迟到 successor 3 的 E2E 覆盖**。本 plan 交付组件级 + 接线级验证（dual-write round-trip + ResultPartition→store→InputChannel 调用链连通），作为 successor 3 E2E 的机制基础。这不是降级——组件级 + 接线级验证是机制 plan 的恰当粒度（参考 guide #22：组件级单测不能替代 E2E，但本 plan 的 E2E 责任在 successor 3，本 plan 负责让 successor 3 的 E2E 能跑通）。
- [x] **无静默跳过**（#24）：物化未启用时走原 by-reference 路径（默认行为，不抛异常）；物化启用但 store 不可用时 fail-fast（非静默吞掉）；未实现的分支抛 `UnsupportedOperationException`
- [x] 默认 partition type 不变（`determinePartitionType` 仍只返回 PIPELINED/PIPELINED_BOUNDED；物化是 `JobEdge` 附加 metadata）
- [x] owner-doc: `failover-design.md` 物化点机制落地状态更新（§五.1/§9.x implementation status）; `state-management-design.md` 如涉及物化存储契约
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 物化点机制可用（显式标记 edge 可物化写入 + 重放 + epoch 标记）且组件级 + 接线级验证通过
- [x] Deliverable Contracts 三项（物化 marker / 重放能力 / epoch 标记）均落地且可被下游消费
- [x] 默认流式行为零回归（既有作业行为不变，既有测试全绿）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] checkstyle / 代码规范检查通过
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope gap（consistent-cut 对齐协议、producer 非阻塞逻辑已显式归属 successor 4，非 in-scope）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：ResultPartition→物化 store→InputChannel 重放 调用链运行时连通（不只是类型存在）；无空方法体/静默跳过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### consistent-cut 对齐协议（重放起点选择）

- Classification: `out-of-scope improvement`（属 successor 4 drain/reconnect）
- Why Not Blocking Closure: `failover-design.md` §9.4 明确 epoch 协调归属 §五.4（successor 4）。本 plan 交付 epoch **标记**（数据基础），对齐**协议**（重放起点选择算法）属 successor 4。无标记则协议无从消费，故本 plan 的标记是 successor 4 的必要前置；但标记本身不依赖协议即可成立。
- Successor Required: yes（successor 4）

### producer 非阻塞溢出逻辑（死锁 1 完整解除）

- Classification: `out-of-scope improvement`（属 successor 4）
- Why Not Blocking Closure: §3.3 死锁 1 完整解除需"主 queue 满时 producer 不阻塞"，属 drain/reconnect（successor 4）的 producer 侧逻辑。本 plan 交付物化旁路**机制基础**（store 可写），但"主 queue 满时溢出到 store 而非阻塞"的调度逻辑属 successor 4。
- Successor Required: yes（successor 4）

### RocksDB/磁盘物化存储

- Classification: `optimization candidate`
- Why Not Blocking Closure: in-memory 物化先保证正确性；RocksDB 物化复用 Stage 30 基建属后续优化，不影响 region failover 正确性契约。
- Successor Required: no

### 跨 JVM 物化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: in-process 物化先正确；跨 JVM 物化属 successor 4 及后续跨 JVM scope。
- Successor Required: yes（successor 4 及后续）

## Non-Blocking Follow-ups

- 物化存储 TTL/清理（与 checkpoint retention 对齐）
- 物化写入性能调优（异步刷盘、批量写等）

## Closure

Status Note: 物化点机制（选项 B）已交付：SPI + in-memory 实现、`ResultPartition` dual-write 旁路、`InputChannel` 重放路径、`JobEdge` opt-in marker、`GraphExecutionPlan.build` 接线、epoch 标记。组件级 + 接线级验证全绿（17 tests）；默认行为零回归（全 nop-stream 727 tests 0 failures）。Deliverable Contracts 三项均落地，可被 successor 2/3/4 消费。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: mission-driver EXECUTE 子 agent（独立执行上下文，非实施 agent）
- Evidence:
  - **Exit Criterion — 物化点 SPI + in-memory 实现被 ResultPartition 调用**：`IMaterializationPoint`（write/replay/replayAll/seal/size/getLastEpoch）+ `InMemoryMaterializationPoint`（synchronized ArrayList）+ `MaterializedElement`（epoch-tagged，Serializable）均存在；`ResultPartition.write()`（`:147-183`）在 `materializationPoint != null` 时 dual-write 调用 `point.write(element, currentMaterializationEpoch)`。接线测试 `TestMaterializationWiring.resultPartitionDualWritesToMaterializationStoreWithEpoch` 断言 store 收到 3 元素且 epoch tag 正确（0/0/5）。
  - **Exit Criterion — InputChannel 重放路径命中物化 store**：`InputChannel.replayMaterialized(fromEpoch)`（`:170-178`）+ `activateMaterializationReplay(fromEpoch)`（`:199-218`，injectFront 到 in-flight buffer）。接线测试 `inputChannelReplayReadsFromMaterializationStore` + `inputChannelActivateReplayInjectsMaterializedDataAheadOfQueue` 断言重放结果与 epoch 过滤正确。
  - **Exit Criterion — edge marker 可显式设置**：`JobEdge.isMaterializationEnabled()`/`setMaterializationEnabled(boolean)`（opt-in 默认 false）。测试 `jobEdgeMaterializationMarkerIsOffByDefaultAndSettable` + `graphExecutionPlanAttachesMaterializationPointToEnabledEdgePartitions`（marker on → partition 挂 point）/ `graphExecutionPlanDoesNotAttachMaterializationPointByDefault`（marker off → 无 point，零回归）。
  - **Exit Criterion — epoch tag 可观测**：`MaterializedElement.getEpoch()`；测试断言 `replayAll()`/`replay(fromEpoch)` 返回元素带正确 epoch。
  - **Exit Criterion — 端到端验证说明**：本 plan 交付组件级 + 接线级（已满足）；完整 mid-execution 重启 E2E 显式延迟到 successor 3（其重启机制是 E2E 前置）。
  - **Exit Criterion — 无静默跳过（#24）**：`replayOnChannelWithoutMaterializationPointFailsFast` 断言无 point 时抛 `ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED`（fail-fast，非返回空）；sealed point 写入抛 `ERR_STREAM_MATERIALIZE_POINT_SEALED`；bypass 写失败抛 `ERR_STREAM_MATERIALIZE_WRITE_FAILED`。
  - **Exit Criterion — 默认 partition type 不变**：`JobGraphGenerator.determinePartitionType()`（`:557-565`）仍只返回 PIPELINED/PIPELINED_BOUNDED；marker 是 `JobEdge` 附加 metadata，不改 `ResultPartitionType` 枚举产生路径。
  - **Exit Criterion — owner-doc 同步**：`failover-design.md` §五.1 implementation status block（`:136-141`）+ §9.x successor progress（`:306`）已更新；`state-management-design.md` 不涉及物化存储契约（其 "物化" 引用均为 checkpoint snapshot 语义，无关），无需更新。
  - **Exit Criterion — ai-dev/logs 已更新**：`ai-dev/logs/2026/08-03.md` 追加本 plan 条目。
  - **Closure Gate — `./mvnw test -pl nop-stream -am -T 1C`**：BUILD SUCCESS，727 tests 0 failures 0 errors（含新增 17 materialization tests）。`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` BUILD SUCCESS。
  - **Closure Gate — Anti-Hollow**：`TestMaterializationWiring` 10 tests 证明 ResultPartition→store→InputChannel 重放调用链运行时连通（`resultPartitionDualWritesToMaterializationStoreWithEpoch` 验证 dual-write 实际发生；`inputChannelActivateReplayInjectsMaterializedDataAheadOfQueue` 验证重放数据经 read() 真实返回）；无空方法体/静默跳过。
  - **Closure Gate — check-plan-checklist**：`node ai-dev/tools/check-plan-checklist.mjs <this-plan> --strict` 退出码 0。

Follow-up:

- consistent-cut 对齐协议（重放起点选择）— successor 4 drain/reconnect（Deferred But Adjudicated）
- producer 非阻塞溢出逻辑（死锁 1 完整解除）— successor 4（Deferred But Adjudicated）
- 物化存储 TTL/清理（与 checkpoint retention 对齐）— Non-Blocking Follow-up
- 物化写入性能调优（异步刷盘、批量写）— Non-Blocking Follow-up
