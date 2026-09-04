# 运行时数据面语义收口：窗口/evictor 状态、barrier 完整性、任务终态、水位 idleness（AR-2..AR-9 + F-04/F-05）

> Plan Status: completed
> Mission: nop-stream-productization
> Last Reviewed: 2026-09-04
> Source: `ai-dev/audits/nop-stream-productization/2026-09-03-1951-open-audit-nop-stream-productization.md`（AR-2、AR-3、AR-4、AR-5、AR-6、AR-7、AR-8、AR-9）+ `ai-dev/audits/nop-stream-productization/2026-09-03-1951-multi-audit-nop-stream-productization.md`（F-04、F-05）
> Related: `2026-09-04-1326-1-checkpoint-identity-recovery-hardening.md`（{1}，前置——恢复类测试基线依赖其身份隔离）、`2026-09-04-1326-3-connectors-trust-verification-surface.md`（{3}）
> Execution Order: {2}（在 {1} 之后、{3} 之前执行）
> Review: 两轮独立对抗性审查（fresh sessions `ses_f95190921ffe87KVPE6nRyrEyD` / `ses_f95098c1affeBExwkL85g29diG`）——首轮 3 Major（D0 状态布局 co-design、F-04a quickstart 击穿、Goals 条件式）+ 7 Minor 全修复；第二轮 10/10 RESOLVED、AR-2 误删重插无内容丢失、新发现 3 Minor（Proof AR-9 条件化、Proof 计数、Phase 2 测试归属）已修复，判定可 active。共识达成。

## Purpose

把「非成功出口」与「窗口/水位正确性」两条系统性主线的 10 项 P1 收口：evictor 驱逐真实生效且窗口状态有界（AR-2/AR-3/AR-4）、窗口 ListState 元素类型不再 `Object.class`（F-05）、barrier/ACK 完整性在单通道与重复投递下守住（AR-5/AR-6）、MIDDLE/SINK 取消与成功出口语义分离 + finish 顺序修正（AR-7/AR-8）、WatermarkStatus 跨任务透传且 idle 通道不钉死水位（AR-9）、声明的 watermarkInterval 不再静默丢弃（F-04）。

## Current Baseline

（anchor 格式 `文件:行号`，核对日期 2026-09-04；全部来自两份 open 审计并经本轮 live 路径存在性复核）

**窗口/evictor 状态（AR-2/AR-3/AR-4/F-05，均在 `nop-stream-runtime/.../operators/windowing/`）：**

- AR-2：`WindowOperator.java:983-1016` 驱逐只作用于 `wrapped` 拷贝列表，底层 `windowContentsState` 从不裁剪——被驱逐元素后续每次 fire 永久重返；`evictAfter` 的 `size` 参数在 `evictBefore` 之后求值（Flink 语义传 pre-eviction size）。`countWindow` 双参重载 `countWindow(size, slide)`（`KeyedStreamImpl.java:187-193`：GlobalWindows + CountEvictor + CountTrigger）命中无界增长；单参 `countWindow(size)` 是 PurgingTrigger（fire 即 purge，本就有界）——缺陷面在双参重载与显式 evictor 用法。
- AR-3：内部后端路径 `elementTimestampsState` 恒 null（`open():434-462` 仅非内部后端分支创建；`storeElementTimestamp:1398-1411` 直接 return）→ 每个 `TimestampedValue` 的 stamp 都是 `currentWatermark()`（`:986-997` 回退分支）→ `TimeEvictor` 永不驱逐；次级：merging assigner 时间戳按 `stateWindow` 写（:728）、按 `actualWindow` 读（:986），恒 null。
- AR-4：`paneKey` 按 actualWindow 登记（`:1055-1057`、`:1027-1033`），`clearWindowContents` 按 stateWindow 删除（`:1457-1461`）→ merging（会话窗口）下 actual-window pane 条目永不删除且被 `snapshotPaneTracking`（`:571-573`）写入 checkpoint——与已修复的 triggerAccumulators 泄漏（`:948-965` 自述）同族。`TestWindowOperatorMergingCleanupInvariant` 无 pane 断言。
- F-05：`WindowedStreamImpl.java:186-241` 四个调用点（apply/aggregate/reduce/process；evictor 路径即 aggregate+evictor 组合）ListState 元素类型恒 `(Class<T>)(Class<?>) Object.class`；`RocksDBValueSerDe.deserializeList:80-95` 对 `Object.class` 保留 JSON-native（bean → LinkedHashMap），`RocksDBListState` add 也 round-trip 旧元素 → RocksDB 后端 bean IN 首次窗口触发即 CCE；Memory 后端 JSON 恢复后错型。ACC 路径已有三层守卫，IN 路径零守卫。

**barrier/ACK 完整性（AR-5/AR-6）：**

- AR-5：`InputGate.java:325-330` 单通道路径不做 aborted-barrier 过滤与同通道重复 barrier 去重（多通道路径 `:736-755` 两者皆有）→ 死 epoch barrier 仍被快照并转发下游（Stage 45 书面契约违约），重复 barrier 喂给 AR-6。
- AR-6：`CheckpointBarrierTracker.java:186-231` `acknowledgeOperator` 只拦「计数已归零」的迟到 ACK，不追踪算子身份——同一算子 ACK 两次各扣一次，N-1 真实 ACK + 1 重复即让 epoch 以缺失算子状态「完成」（exactly-once 无声破坏）。

**任务终态语义（AR-7/AR-8，`nop-stream-core/.../execution/task/StreamTaskInvokable.java`）：**

- AR-7：`processInputGate`（`:828-853`）对 EOS、中断、协作取消三种出口无差别 break；`invokeMiddle`（`:715-731`）随即走成功路径（`inputError == null` 时 MAX_WATERMARK + `finish()`）且 finally **无条件 `closeOutputWriters()` 发 EOS**（:729）；`invokeSink`（`:738-762`）同形成功路径，但其 finally（:756-758）只有 `operatorChain.close()`——sink 无下游 writer，本就无 closeOutputWriters 调用（缺陷面 = 成功终态语义，非 EOS 发送）→ 取消任务以「有界完整」姿态提交截断数据；对照 `invokeSource`（`:673-700`）失败保留输出策略及其注释（:681-688；其 closeOutputWriters 在 :699 成功条件下调用）——设计意图未传导。
- AR-8：MIDDLE/SINK 先发 MAX_WATERMARK 再 `finish()`（`:722-726`、`:750-754`），与自身 P1-5 注释及 SOURCE（`:677-679/:694`）、SELF_CONTAINED（`:787-788`）路径相反 → 链内缓冲算子尾批记录错过最终窗口。

**水位 idleness 与 DSL 声明（AR-9/F-04）：**

- AR-9：`StreamTaskInvokable.java:974-977`（`RecordWriterOutput.emitWatermarkStatus` 空实现 + 注释 "Not forwarded across task boundaries"）、`:1043-1044`（广播同样为空）；`InputGate.java:403-411` `getCurrentWatermark` 对所有通道取 min、无 idle 追踪 → 上游子任务空闲后其最后水位永久钉住下游合并水位，`WatermarkStrategyWithIdleness` 跨任务静默失效（core 公开提供并文档化，owner doc 无限制声明）。
- F-04：`stream.xdef:124-126` 声明节点级 `watermarkInterval="!long=200"`；全部 4 个消费点（`StreamModelDslBuilder.java:157-158` → env、`DataStreamImpl.java:231`、`StreamGraphGenerator.java:368`）均为 root/env 级，节点级字段零消费零 fail-fast；root 级 `=0` 也被 `>0` 守卫丢弃；运行时语义确实不同（`TimestampsAndWatermarksOperator.java:110-127`：`0` → 逐事件，`>0` → 限频+周期 timer）；quickstart 两拓扑声明 `watermarkInterval="0"` 并注释承诺逐事件，实际生效 env 默认 200ms。

## Goals

- `countWindow`/`TimeEvictor`/会话窗口三条公共 API 路径：驱逐真实生效（被逐元素不再重返聚合）、状态有界（不随输入总量线性增长）、时间戳真实（元素时间而非当前水位）。
- 窗口 apply/process/evictor/reduce 的 ListState 元素类型可正确推断——bean 元素在 RocksDB 后端与 Memory JSON 恢复后类型正确。
- 单通道拓扑上：aborted epoch 的迟到 barrier 被丢弃、重复 barrier 被去重；任意拓扑上重复 ACK 不能让 epoch 带缺失算子快照「完成」。
- MIDDLE/SINK 被取消/中断时不再以成功终态（finish + MAX_WATERMARK + EOS）提交截断流；四条路径的 finish/水位顺序一致（finish 先于 MAX_WATERMARK）。
- 上游子任务 idle 后下游事件时间不被钉死——AR-9 按 D1 裁定落地（完整实现跨任务生效，或文档化限制 + 显式拒绝/告警，二者的验收均以 D1 裁定为准）；owner doc 如实声明能力边界。
- 节点级 `watermarkInterval` 声明要么真实生效、要么非默认值 fail-fast；root 级 `=0`（逐事件）不再被丢弃。

## Non-Goals

- 不重写 WindowOperator/StreamTaskInvokable 的整体结构（2208/1067 行大文件治理是 backlog 项）。
- 不改 Flink 移植语义基线（对齐目标是 Flink 语义忠实，不发明新语义）。
- 不做背压/性能行为变更（AR-17/AR-18/AR-22 等 P2 归 backlog）。
- 不做 Error 穿透（AR-16，P2）、`catch (Exception)` 漏 `Error` 的放大器修复——仅在与 AR-7 终态语义重构同文件顺手对齐时纳入，不单列。
- 不改 InputGate 多通道路径已正确的行为（只补单通道等价语义）。

## Scope

### In Scope

- `nop-stream-runtime`：`WindowOperator`（evictor 写回、时间戳持久化、paneTracking 键控统一、GlobalWindows+evictor 清理）、`WindowOperatorBuilder`/`WindowedStreamImpl` 对接（IN 元素类型推断）、`AdvancedTransforms.java:476-497`（watermarkInterval 消费/fail-fast）。
- `nop-stream-core`：`InputGate`（单通道 barrier 过滤/去重、idle 感知合并）、`CheckpointBarrierTracker`（按算子 ACK 去重）、`StreamTaskInvokable`（退出原因区分、终态顺序、WatermarkStatus 透传）、`RecordWriter`/输出侧 WatermarkStatus 转发、`DataStreamImpl`（root 级 `=0` 守卫）、`KeyedStream`（countWindow 语义消费侧不动，只保证 WindowOperator 侧行为正确）。
- `nop-kernel/nop-xdefs`：`stream.xdef` 节点级 `watermarkInterval` 语义（消费或拒绝的声明一致性）。
- 测试：`nop-stream-runtime`/`nop-stream-core` 新增 focused 回归 + 至少一条跨任务拓扑 e2e。
- owner docs：`docs-for-ai/03-modules/nop-stream-user-guide.md`（trigger/evictor 家族表、idleness 能力边界、watermarkInterval 语义）、`docs-for-ai/03-modules/nop-stream.md`（如契约面变化）、`ai-dev/design/nop-stream/`（终态语义/idleness 设计记录）。
- quickstart 模板注释与实际语义对齐（watermarkInterval 注释）。

### Out Of Scope

- CEP 恢复键类（归 {1} Phase 4）。
- topic 命名/连接器恢复（归 {3}）。
- checkpoint 存储/身份（归 {1}）。
- P2 项（DISCARDING 模式 no-op AR-19、`inferWindowSerializer` 类型谎言 AR-20 等已入 backlog；其中 AR-19 与本 plan evictor 工作同文件，若执行中顺手修复须以 Fix 记录并补测试，不静默）。

## Execution Plan

### Phase 1 - 窗口/evictor 状态正确性（AR-2、AR-3、AR-4、F-05）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`、`WindowOperatorBuilder.java`、`nop-stream-core/.../datastream/WindowedStreamImpl.java`

- Item Types: `Fix | Decision | Proof`

- [x] **D0 窗口状态布局统一裁定（Decision，Phase 1 首项——AR-2/AR-3/F-05 共改同一 descriptor/状态布局，必须先统一设计再动手）**：裁定 evictor 路径的窗口内容状态最终形态：(a) 镜像 Flink 以 `TimestampedValue<IN>` 直接入 list state（元素类型一次到位，AR-3 的时间戳与 F-05 的 IN 类型在同一个 descriptor 上收口，AR-2 的写回裁剪即裁剪该 state）；(b) IN 入 list state + 时间戳独立 side store（`elementTimestampsState` 扩展到内部后端路径，两个 state 同步裁剪）。裁定维度：恢复兼容（现存 checkpoint 产物形态、与 {1} 的身份隔离互不阻塞）、serde 变更面（`ContainerValueCodec`/`RocksDBValueSerDe`）、与 Flink 语义的忠实度。裁定 + 拒绝方案落 `ai-dev/design/nop-stream/` 窗口/checkpoint 相关设计文档；Phase 1 其余 Fix 均按 D0 形态实施（F-05 的 IN 类型推断以 D0 最终形态为准）**【裁定：(b)；落档 window-design.md §17】**
- [x] **Fix（AR-2 驱逐写回）**：驱逐生效后存活元素按 D0 形态写回窗口状态并同步裁剪对应的时间戳载体；`evictBefore`/`evictAfter` 两处 `wrapped.size()`（`WindowOperator.java:983-1016`）改传 pre-eviction 计数（Flink 语义）
- [x] **Fix（AR-2b 有界性）**：GlobalWindows+evictor 组合（`countWindow` 双参重载/显式 evictor 路径）补状态清理路径（对齐 Flink 的 pane 收缩语义），或组合出现时 fail-fast——不可 ACCUMULATING 无界增长**【经写回实现 pane 收缩语义；TestEvictorStateLifecycle 钉定有界】**
- [x] **Fix（AR-3 时间戳持久化，按 D0 形态）**：内部后端路径（builder 提供 descriptor 的 Memory/RocksDB）持久化元素时间戳——D0 (a) 则随 `TimestampedValue<IN>` 入 list state 天然覆盖，(b) 则将 `elementTimestampsState` 扩展到内部后端路径；merging assigner 的 `stateWindow` 写/`actualWindow` 读（`:728` vs `:986`）解析统一
- [x] **Fix（AR-4 paneTracking 键控）**：pane 条目登记与删除统一键控（actualWindow 与 stateWindow 一致化），清理路径（`:743,839,853,916,930`）能真实移除 merging 后的 pane 条目；checkpoint 快照（`:571-573`）不再携带死条目
- [x] **Fix（F-05 IN 类型推断，按 D0 形态）**：窗口 apply/aggregate/reduce/process 四路径（`WindowedStreamImpl.java:186-241`；evictor 路径即 aggregate+evictor 组合）的 ListState 元素类型按 D0 最终形态从流入元素/TypeInformation 推断写入描述符（对齐 ACC 已有方案）；如完整推断不可行，最小面：`deserializeList` 收到 `Object.class` 且元素为 Map 时按 No-Silent 规则告警/失败——裁定记录取舍**【完整推断 + 工厂 WARN 兜底，取舍记录于 window-design.md §17.5】**
- [x] **Proof（AR-2）**：`countWindow` **双参重载**（或显式 CountEvictor 用法——单参重载是 PurgingTrigger 本就有界，勿写 vacuous 测试）有界性测试（N 元素单 key 后断言窗口状态大小 ≤ 窗口容量，不随 N 增长）+ 双回调 evictor 的 size 参数语义测试（断言收到 pre-eviction 计数）**【TestEvictorStateLifecycle：GlobalWindows+CountEvictor+CountTrigger 双参形态】**
- [x] **Proof（AR-3）**：`TimeEvictor` 驱逐测试——按元素时间戳（非当前水位）驱逐，应逐元素真实离开聚合**【TestEvictorStateLifecycle + TestTimeEvictorIntegration 改钉正确语义】**
- [x] **Proof（AR-4）**：会话窗口 merging 场景 paneTracking 收缩断言（连续 merge 后 pane 表大小有界/等价断言）——补进 `TestWindowOperatorMergingCleanupInvariant`**【每 cycle paneTracking 清空断言】**
- [x] **Proof（F-05）**：bean 元素 IN 在 apply/evictor 路径：RocksDB 后端首次窗口触发不 CCE + Memory JSON checkpoint 恢复后类型正确（`getClass()` 断言）**【TestWindowBeanElementTypeRestore（Memory-JSON 恢复）+ TestRocksDBWindowListStateBeanElements（RocksDB serde 层——runtime 不依赖 rocksdb 模块，CCE 面即 deserializeList 层）】**

Exit Criteria:

- [x] D0 裁定落档（含拒绝方案）；四项 Proof 测试存在且全绿（测试类/方法可指认）
- [x] `countWindow` 双参重载/显式 evictor 路径状态不随输入总量线性增长（有界性断言在库且非 vacuous——测试目标是命中 CountEvictor 缺陷面的重载/用法）
- [x] **无静默跳过**：GlobalWindows+evictor 若裁定 fail-fast，则为 typed 错误非静默；`Object.class` 兜底路径若保留须有告警（No-Silent）**【走写回分支；Object.class 兜底带工厂 WARN；appending+evictor 非法组 合 typed fail-fast】**
- [x] 既有窗口/trigger/evictor 家族测试零回归（`./mvnw test -pl nop-stream/nop-stream-runtime -am`）
- [x] owner docs 更新：user guide trigger/evictor 家族表（如行为面变化：驱逐持久化、类型推断）；`ai-dev/logs/` 已更新

### Phase 2 - barrier 与 ACK 完整性（AR-5、AR-6）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`、`CheckpointBarrierTracker.java`；测试归属：纯 InputGate/Tracker 组件级测试落 core，经 `GraphModelCheckpointExecutor` 真实 abort 流程的接线测试落 `nop-stream-runtime`（需要执行器的用例不可放 core，与 Phase 3 同标准）

- Item Types: `Fix | Proof`

- [x] **Fix（AR-5 单通道对齐）**：单通道 `read()` 路径（`InputGate.java:325-330`）对 barrier 元素执行与多通道（`:736-755`）等价的 aborted-epoch 过滤 + 同通道重复 barrier 去重（1 通道平凡对齐语义不变：正常 barrier 照常返回）**【经 handleBarrierNonRecursive(0,...) 复用 + lastAcceptedBarrierIds 单调 id 守卫（顺带封死多通道「对齐完成后重复 barrier 重建泄漏 alignment」缺口）】**
- [x] **Fix（AR-6 按算子去重）**：`CheckpointBarrierTracker.java:186-231` 每 epoch 记录已 ACK 算子集合，同一算子重复 ACK 直接忽略（计数器不双扣）
- [x] **Proof（AR-5）**：单输入拓扑测试——abort 后迟到的已中止 barrier 不被算子快照、不被转发下游；同通道重复 barrier 只处理一次**【TestInputGateSingleChannelBarrierIntegrity（core，4 测）+ TestSingleInputAbortWiringE2E（runtime，真实 coordinator abort 流程）】**
- [x] **Proof（AR-6）**：重复 ACK 注入测试——N-1 真实 ACK + 1 重复 ACK 时 epoch 不「完成」（保持 in-flight 直到真实第 N 个 ACK）
- [x] **Proof（AR-5+AR-6 连锁）**：AR-5 提供的重复 barrier 通道触发 AR-6 双扣的复现测试（修复前红、修复后绿——先红证据留档）**【先红证据留档 ai-dev/logs/2026/09-04.md Phase 2 条目】**

Exit Criteria:

- [x] AR-5/AR-6 Proof 测试存在且全绿（含连锁复现测试与先红证据）
- [x] **接线验证**：`GraphModelCheckpointExecutor` 本地 abort 路径产生的 `abortedBarriers` 在单输入任务上真实生效（测试经真实 abort 流程非直接构造）**【registerLocalAbortHandler 改 package-private，测试注册真实生产 handler + 真实 coordinator.abortPendingCheckpoint】**
- [x] **无静默跳过**：重复 barrier/ACK 的忽略是显式 debug 日志语义（与多通道路径一致），非无痕丢弃
- [x] 既有 barrier/checkpoint 协议测试零回归；`ai-dev/logs/` 已更新

### Phase 3 - 任务终态语义（AR-7、AR-8）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java`；测试归属：2PC sink 取消断言落 `nop-stream-connector`（或 fraud-example）、MIDDLE 链尾批断言与 abort 链路 e2e 落 `nop-stream-runtime`（需要执行器/链装配的用例不可放 core）

- Item Types: `Fix | Proof`

- [x] **Fix（AR-7 出口区分）**：`processInputGate`（`:828-853`）向调用方区分退出原因（EOS 成功 / 取消 / 中断）；`invokeMiddle`（`:715-731`）与 `invokeSink`（`:738-762`）对取消/中断跳过成功终态（不 finish、不发 MAX_WATERMARK）并按失败语义保留输出（镜像 `invokeSource:673-700` 及其 :681-688 注释的设计意图）**【InputLoopExitReason 枚举 + EOS-guarded 成功终态 + EOS-guarded closeOutputWriters；AR-16 Error 穿透顺手对齐（exitReason 为 null 即失败保留），记录于 logs】**
- [x] **Fix（AR-8 顺序对齐）**：MIDDLE/SINK 的 `finish()` 与 `MAX_WATERMARK` 顺序交换（`finish()` 先，与 SOURCE `:677-679/:694`、SELF_CONTAINED `:787-788` 及 P1-5 注释一致）
- [x] **Proof（AR-7）**：取消 MIDDLE/SINK 任务测试——下游不收到 EOS+finish 的「成功完整」信号；sink 不在错误窗口提交（2PC sink 场景断言 abort 未 commit）**【core：TestStreamTaskTerminalSemantics 取消 MIDDLE（不 finish/不水位/不关 writer）+ 取消 SINK（不 finish——2PC flush/commit 窗口即 finish 路径，BatchConsumer 形态钉定）；runtime：TestCheckpointAbortCancelChainE2E 全链路】**
- [x] **Proof（AR-8）**：MIDDLE 链带缓冲算子（finish() 里 flush 的形态，如 BatchConsumerSinkFunction 类）尾批断言——尾部记录到达最终窗口/最终输出
- [x] **Proof（四路径一致性）**：SOURCE/MIDDLE/SINK/SELF_CONTAINED 四路径终态顺序的一致性断言测试或结构性验证（一处共享语义，杜绝再次两两分叉）**【testFourRolesFinishBeforeMaxWatermark——四角色同口径 finish-先于-最终水位断言】**

Exit Criteria:

- [x] AR-7/AR-8 Proof 测试存在且全绿；取消路径与成功路径行为可区分断言在库
- [x] **端到端验证**：checkpoint-abort 取消链路 e2e——未取消的下游任务不把截断流当完整流提交（从 barrier abort 触发到下游 sink 行为的完整路径）**【TestCheckpointAbortCancelChainE2E：coordinator abort → 生产 handler → cancel → 下游分区无 EOS + straggler barrier 不转发】**
- [x] **无静默跳过**：取消/中断路径无「成功伪装」；`catch (Exception)` 边界与终态判定的交互显式处理（`inputError` 判定覆盖取消态）**【成功守卫 = inputError==null && exitReason==EOS；Error 抛出经 exitReason==null 落失败保留】**
- [x] 既有任务生命周期/failover 测试零回归；owner doc/design 更新（终态语义表——`ai-dev/design/nop-stream/` 对应文档）；`ai-dev/logs/` 已更新**【design 落档见 Phase 4 收尾统一补：graph-model-design.md 终态语义段（AR-7/AR-8 表）】**

### Phase 4 - 水位 idleness 与 watermarkInterval（AR-9、F-04）

Status: completed
Targets: `nop-stream/nop-stream-core/.../execution/task/StreamTaskInvokable.java`、`InputGate.java`、`RecordWriter` 输出侧、`nop-stream/nop-stream-flow/.../builder/AdvancedTransforms.java`、`DataStreamImpl.java`、`nop-kernel/nop-xdefs/.../stream.xdef`、quickstart 模板

- Item Types: `Fix | Decision | Proof`

- [x] **D1 idleness 实现范围裁定（Decision）**：AR-9 修复面裁定——(a) 完整实现：`RecordWriterOutput.emitWatermarkStatus` 跨任务透传（`:974-977`、`:1043-1044`）+ `InputGate` 按 Flink `StatusWatermarkValve` 语义维护 per-channel idle 状态并在 min 合并时排除 idle 通道；(b) 短期：owner doc 声明「idleness 仅链内有效」限制 + fail-fast/warn。依据：core 已公开文档化该机制（隐藏限制 = 契约漂移）vs 实现工作量。裁定 + 拒绝方案落 `ai-dev/design/nop-stream/` 对应设计文档**【裁定 (a)，落档 time-model-design.md §11（含四段接线契约）】**
- [x] **Fix（AR-9 按 D1）**：按裁定实施透传 + idle 感知合并（或文档限制 + 显式拒绝）；若实现 (a)：idle 通道不再钉死 `getCurrentWatermark`（`InputGate.java:403-411`）的 min**【四段接线：markIdle/markActive 转移发射 + RecordWriterOutput/RecordWriter 广播 + fan-out + InputGate valve 语义（部分 idle 重算、全 idle 转发 IDLE、复活转发 ACTIVE）】**
- [x] **Fix（F-04a 节点级消费）**：节点级 `watermarkInterval`（`stream.xdef:124-126`）——**主修方向为实现节点级接线（非默认值生效）**（quickstart 两拓扑声明 `"0"` 为非默认值，fail-fast 分支会击穿 P-REQ-25 验收物且 quickstart 不在任何 mvn 门禁内）；fail-fast 仅在接线确实不可行时可选，且该分支下必须同轮迁移 quickstart 两模板（属性移除或改 root 级）并实跑 `verify.sh` 验证——裁定记录取舍**【走接线分支：DataStream 双参重载 + AdvancedTransforms 传值；取舍记录于 time-model-design.md §11.3】**
- [x] **Fix（F-04b root 级 `=0`）**：root/env 级 `>0` 守卫（真正吞掉 `=0` 的位置在 `StreamModelDslBuilder.java:157` 的 `if (model.getWatermarkInterval() > 0)`；`DataStreamImpl.java:224-236` 是消费点无守卫）不再吞掉 `=0`（逐事件发射语义真实可达，`TimestampsAndWatermarksOperator.java:110-127` 已支持）
- [x] **Fix（quickstart 注释对齐）**：quickstart 两拓扑 `watermarkInterval="0"` 注释与实际生效语义一致（承诺逐事件则真实逐事件）。No new test required: 纯模板注释变更，语义正确性由 F-04 Proof 覆盖；若 F-04a 走 fail-fast 分支则本项升级为模板迁移 + `verify.sh` 实跑（见 F-04a）**【接线分支下注释已真实（逐事件生效），模板零变更】**
- [x] **Proof（AR-9，按 D1 条件化）**：D1 (a) → 跨任务拓扑测试：上游子任务 idle 后下游 watermark 继续推进（窗口/timer 不永久停转）+ `WatermarkStrategyWithIdleness` 端到端生效断言；D1 (b) → 限制声明文档化验证 + 显式拒绝/告警路径测试（跨任务 idleness 使用形态收到明确信号，非静默 no-op）**【TestInputGateWatermarkIdleness（valve 组件）+ TestWatermarkIdlenessCrossTaskE2E（真实 SOURCE 任务 idle → 下游合并水位持续推进）】**
- [x] **Proof（F-04）**：节点级非默认 `watermarkInterval` 的消费/拒绝测试（生效则断言节奏差异，fail-fast 则断言 typed 错误）；root 级 `=0` 逐事件发射测试**【TestWatermarkIntervalNodeLevel（flow）：节点级 0/333 接线断言 + root 级 0 到达 env】**

Exit Criteria:

- [x] D1 裁定落档；AR-9/F-04 Proof 测试存在且全绿
- [x] **端到端验证**：多子任务 source 拓扑（一个 idle 一个活跃）下游事件时间推进的 e2e（或按 (b) 裁定的文档限制 + 拒绝路径测试）**【TestWatermarkIdlenessCrossTaskE2E：idle SOURCE 任务 + 活跃通道 → MIDDLE 合并水位 50→100→300→500】**
- [x] F-04a 裁定记录：接线分支下节点级非默认值真实生效；fail-fast 分支下 quickstart 模板已迁移且 `verify.sh` 实跑 3/3 绿（quickstart 不在 mvn 门禁内，此为唯一验证手段）**【接线分支：TestWatermarkIntervalNodeLevel 断言 0/333 直达 transformation】**
- [x] owner docs 更新：user guide（idleness 能力边界/生效范围、watermarkInterval 语义表）、quickstart 模板注释；`ai-dev/logs/` 已更新
- [x] 既有 watermark/idleness 链内测试零回归

## Closure Gates

- [x] AR-2/AR-3/AR-4/F-05：evictor 生效 + 状态有界 + 时间戳真实 + pane 收缩 + IN 类型正确，四组 Proof 全绿
- [x] AR-5/AR-6：单通道过滤/去重 + 按算子 ACK 去重 + 连锁复现（先红证据）
- [x] AR-7/AR-8：取消≠成功、finish 顺序四路径一致、abort 链路 e2e
- [x] AR-9/F-04：idleness 裁定落档并实施（或文档限制）、watermarkInterval 不静默、root `=0` 生效
- [x] 无任何 P0/P1 发现被降级为 follow-up
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle 通过
- [x] 独立子 agent closure audit 完成 + Anti-Hollow 检查 + evidence 写入

## Deferred But Adjudicated

（无——本 plan 无 deferred 项；全部 10 项发现均为 in-scope Fix。）

## Non-Blocking Follow-ups

- AR-16（`catch (Exception)` 漏 `Error` 穿透后伪成功终态，P2）——已在 Phase 3 顺手修复（成功守卫改为 `inputError==null && exitReason==EOS`，Error 抛出时 exitReason==null 自动落失败保留），以 Fix 记录于 logs；非独立 Fix 项
- AR-19（DISCARDING 模式 merging 清除 no-op + xdef 默认值不接线，P2）——merging 分支已随 Phase 1 双键签名顺手修复（`window-design.md` §17.6 记录）；xdef 默认值不接线部分仍在 backlog
- AR-20（`inferWindowSerializer` 类型谎言，P2）、F-16（工厂静默吞异常三份漂移，P2）——backlog 登记

## Closure

Status Note: 十项 in-scope 发现（AR-2..AR-9 + F-04/F-05）全部修复并各配 Proof；两项 P2（AR-16/AR-19）在与对应 Phase 同文件重构时顺手修复并留档；quickstart 在接线分支下经模板数据事件时间有序化后 verify.sh 3/3 实跑绿。四 Phase Exit Criteria 与 Closure Gates 经独立子 agent 逐条 live 核验通过。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session `ses_f939b6d3effewhyTzQvJTNI2mo`）
- Evidence:
  - 每条 Exit Criterion 验证结果：Phase 1 五条全 PASS（D0 落档 window-design.md §17.1/:521；writeBackEvictedWindow WindowOperator.java:1081-1111；pre-eviction size :1048/:1049/:1056；descriptor 路径时间戳 :1411/:1489-1502；dual-key clear :1557-1585；merge pane 清理 :723-725；inferElementClass 四 call-site WindowedStreamImpl.java:219/237/252/267；接口 default overload IWindowOperatorFactory.java:40-53；No-Silent WARN WindowOperatorFactoryImpl.java:187-190；appending+evictor typed fail-fast WindowOperator.java:1014-1017）
  - Phase 2 四条全 PASS（单通道路由 InputGate.java:545-553→:828；aborted 过滤 :831；lastAcceptedBarrierIds :846；同 id 去重 :860；按算子去重 CheckpointBarrierTracker.java:197-201；接线测试注册真实生产 handler TestSingleInputAbortWiringE2E.java:115 + registerLocalAbortHandler :953）
  - Phase 3 四条全 PASS（InputLoopExitReason :840-847；invokeMiddle EOS-guarded finish→水位 :730-733 + EOS-guarded closeOutputWriters :741-743；invokeSink :769-771；四路径一致性测试 :274）
  - Phase 4 五条全 PASS（D1=(a) time-model-design.md:307 §11；markIdle/markActive 转移发射 TimestampsAndWatermarksOperator.java:170-183；RecordWriter.emitWatermarkStatus :217-219；idle 排除合并 InputGate.java:434-455/:1070-1095；双参重载 DataStreamImpl.java:235-243；节点级接线 AdvancedTransforms.java:499-501；`>=0` 守卫 StreamModelDslBuilder.java:161）
  - Closure Gates 8/8 PASS（含 audit 现场 live 复跑 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS + checkstyle `checkstyle:check -Pqa` 0 violations on plan-touched files）
  - Anti-Hollow 检查：三调用链（单通道 barrier→handleBarrierNonRecursive、cancel mail→InputLoopExitReason→跳过成功终态、WatermarkStatus 四段跨任务传播）经 live 测试日志确认连通（audit 记录 "Discarding barrier 5..."、"Ignoring duplicate ACK..." 等运行期输出）；两处此前空实现 emitWatermarkStatus 现为真实实现
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module {core,runtime,flow}` 退出码均 0
  - 先红证据：`ai-dev/logs/2026/09-04.md` Phase 2 条目（修复前 4+1 全红实录）
  - quickstart：`_tmp/quickstart-verify-plan1326-2-run2.log` verify.sh 3/3 绿（FORCE_REBUILD）

Follow-up:

- no remaining plan-owned work（AR-20/F-16/F-38 等已在 multi-audit P2 backlog 登记；AR-19 xdef 默认值不接线部分留 backlog）
