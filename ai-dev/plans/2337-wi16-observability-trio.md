# 2337 WI16 观测三件套（忙闲占比 + watermark 级联 + 队列填充比）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI16（M4）+ 裁定 §一 P1 #12（队列填充比复用既有 gauge 推导，不重复建设直测面）
> Related: plan 2329（WI4 registry 面）；productization item 32（queue.size gauge 已 done）

## Purpose

补齐 Flink 对标的运行时可观测三件套：task 忙/背压/空闲时间占比、watermark 级联滞后、边级队列填充比——全部落既有指标基建（StreamMetricsRegistries 组合 registry + ops /metrics 暴露 + 权威名表），不新建注册体系。

## Current Baseline（2026-10-08 研究核实 + 对抗性审查修订）

- **指标基建**：core `StreamMetricsRegistries`（进程级 CompositeMeterRegistry，:30/:39）；ops `/metrics` 由 PrometheusMeterRegistry attach 暴露（TextFormat 0.0.4/OpenMetrics，StreamOpsHttpServer :103-119/:185-214）；权威名表 = nop-stream.md:24（`nop.stream.<layer>.<name>`）。测试锚点：TestStreamOpsHttpServer.testMetricsTextFormat004WithThreeLevelFamilies、TestChannelQueueGauges。
- **task 计时挂点（净新增累计面）**：主循环 `StreamTaskInvokable.processInputGate`（:1045，loop 顶 markActivity :1052 / read :1064 / empty=idle continue :1065-1087 / per-element markProgress :1090）；busy 代理 = `classifyAndDispatch` :1106-1112 的 processingTime 包络；backpressured 代理 = emit 包络 emitTime（RecordWriterOutput :1210-1223 / EdgeWriterOutput :1303-1312，`StreamTaskMetrics.emitTime` javadoc 明示「生产侧背压代理」）；idle 代理 = read 返回 empty（InputGate idle-return 阈值 250ms）。**三态时间占比累计面完全不存在**，但四个挂点全部现成。
- **口径钉定（审查 F1 Blocker 修订）**：emitTime 包络嵌套于 processingTime 包络内（emit 发生在 processElement 内部）——busy/backpressured 不可独立累计否则占比和超 1。**采方案 B'**：接口只扩 `idleTime(nanos)` 与 `elapsedTime(nanos)` 两个新累计；backpressured = 既有 emitTime Timer 总和；busy = 引擎侧派生（processingTime.sum − emitTime.sum，嵌套关系使减法在求和意义下严格成立）。**残差归属显式声明**：processWatermark 处理、side-output/tag-edge 发射（不经 emitTime 包络）、barrier/watermark 写出不计入任何态 = 「other」残差（busy+backpressured+idle ≤ elapsed，差值即 other，名表写明）。
- **SOURCE/SELF_CONTAINED 部分口径（审查 F2 Blocker 修订）**：invokeSource/invokeSelfContained 对 source 是整段黑盒（processInputGate 不被调用，processingTime 恒 0）——source 只暴露 backpressured 与 elapsed 累计，三 ratio 均恒 NaN（elapsed 恒 0——源内部 poll 阻塞细分维持 Non-Goals，删「同构覆盖」措辞）。
- **接口波及（审查 F6）**：实现者恰 3 个——NOOP（空实现）、TaskMetricsHandle（显式委托，**不可用 default 方法**——default 空实现会让 Handle 静默丢转发）、MicrometerStreamTaskMetrics（真实实现）；注入调用点（TaskManager :542/:730、TaskCheckpointWiring :99）零改动。
- **审查其余发现处置补记（F5/F8/F9）**：F5 单位口径 = 派生全部在引擎侧完成只暴露 ratio gauge（scrape 侧零换算）+ 名表 operator 层数据面例外声明；F8 测试先例（TestInputGateWatermarkIdleness / TestChannelQueueGauges / TestObservabilityWiringE2E 宿主）全部采纳写入 Goals；F9 模板补 Non-Blocking Follow-ups 节、日期以执行环境时钟为准。
- **watermark 可读点**：下游 `InputGate` per-channel `currentWatermarks[]`（:118，init Long.MIN_VALUE）+ 合并 min getter `getCurrentWatermark()`（:532-548，剔除 idle 通道）；上游发射点 RecordWriterOutput/EdgeWriterOutput emitWatermark。operator 侧 one-input 透传不驻留。**零 watermark 指标现状**。
- **watermark gauge 钉定（审查 F3）**：注册必须复用 ChannelQueueGauges D2c 的静态 holder 重绑模式（Micrometer gauge 弱引用+同名重注册静默丢弃陷阱——恢复重建会冻结在旧 gate 上）；SOURCE/SELF_CONTAINED 无 gate（inputGate==null）不注册；**首 watermark 前 gauge 返回 NaN**（声明 sentinel，避免 MIN_VALUE 毒化聚合；processing-time 任务恒 NaN）；lag = max(非 idle 通道 wm) − merged（两侧同剔除语义）。
- **fill ratio 调和（审查 F4）**：ratio gauge 是**同一 holder 上的派生读数**（valueFunction 读同一 AtomicReference，非第二个直测探针），动机 = scrape 侧不硬编码 1024 + capacity 未来可配；local ResultPartition 维持 docs 推导口径（cap 可从 getTotalCapacity 读，对称性由名表说明）。capacity 以字段存于 RemoteInputChannel（构造时记录，size()+remainingCapacity() 有并发撕裂）。ratio clamp [0,1]（瞬态溢出窗口）。
- **queue gauge**：`ChannelQueueGauges`（runtime transport，:57）注册 `nop.stream.io.channel.queue.size`，tags jobId/edgeId/sourceSubtask/targetSubtask（edge 粒度），仅订阅通道 bind。**capacity 缺口**：RemoteInputChannel 无 capacity getter（容量 = builder 常量 1024）；local 路径 `ResultPartition` 已有 size()/getTotalCapacity()/isBackpressured() 三件套。

## Goals

- **忙/背压/空闲占比（operator 族，口径 F1/F2 修订后）**：`StreamTaskMetrics` 接口只扩 `idleTime(long nanos)` 与 `elapsedTime(long nanos)` 两个累计（NOOP 空实现、TaskMetricsHandle 显式委托、Micrometer 真实实现——不用 default 方法）；Micrometer 侧注册派生 gauge：`nop.stream.operator.time.busy.ratio`（=(processingTime.sum−emitTime.sum)/elapsed）、`.backpressured.ratio`（=emitTime.sum/elapsed）、`.idle.ratio`（=idleTime.sum/elapsed）+ 底数 `nop.stream.operator.time.idle.nanos` / `.elapsed.nanos`（tags 同 operator 族 jobId/vertexId/subtask；elapsed=0 时 ratio=NaN）。累计点：processInputGate 的 read 调用包络（idle）与 invoke 起点（elapsed）；SOURCE/SELF_CONTAINED 只有 backpressured.ratio 与 elapsed（busy/idle 恒 NaN）。残差 other 在名表声明。
- **watermark 级联（operator 族）**：`InputGate` 增 per-channel 与 merged watermark getter；Micrometer 侧以 holder 重绑模式注册 `nop.stream.operator.watermark`（merged，NaN 哨兵）+ `nop.stream.operator.watermark.channel.lag`（max 非 idle 通道 − merged，NaN 哨兵）；无 gate 角色不注册。
- **边级队列填充比（io 族）**：`RemoteInputChannel` 构造时记录 capacity 字段（`getCapacity()`）；`ChannelQueueGauges` 同 holder 增注册 `nop.stream.io.channel.queue.fill.ratio`（size/capacity，clamp [0,1]，同 tags 同 bind/close 生命周期）；local ResultPartition 维持 docs 推导口径。
- **名表与暴露**：nop-stream.md 权威名表增列新名（operator 层数据面扩展 + io 层一行，残差 other 语义声明）；ops /metrics 零改动（registry 组合自动携带）。
- 测试：占比派生单测（processing/emit 累计 + idle 累计 → 三 ratio，含 elapsed=0 NaN 分支）、watermark lag（TestInputGateWatermarkIdleness 先例：多通道超前/滞后/idle 剔除）、fill ratio（TestChannelQueueGauges 先例：enqueue/dequeue 后 ratio 变化 + capacity 字段非 1024 构造 + clamp）、接线宿主 TestObservabilityWiringE2E 扩断言（真实 LOCAL 管线断言新 meter 存在——Anti-Hollow 宿主）。

## Non-Goals

- 跨进程聚合（JC/TM 逐进程 scrape 为既有口径）；直方图/分位数（gauge+counter 足够首版）；火焰图/JFR 集成；fill% 告警规则（运维侧）；SourceReader 内部 poll 阻塞细分（源 busy 以发射点计时为准）。

## Scope

### In Scope

- core：StreamTaskMetrics 接口扩面 + Micrometer 实现 + processInputGate/SELF_CONTAINED 分类累计 + InputGate watermark getter。
- runtime：ChannelQueueGauges fill ratio + RemoteInputChannel capacity + watermark gauges 接线。
- 测试与 nop-stream.md 名表更新。

### Out Of Scope

- 跨进程聚合；新暴露端点；告警；local ResultPartition 直测面。

## Execution Plan

### Phase 1 - 指标族落地与测试

Status: completed
Targets: core metrics/StreamTaskInvokable/InputGate、runtime transport、测试

- Item Types: `Feature`

- [x] core：StreamTaskMetrics 扩 idleTime/elapsedTime（NOOP/Handle/Micrometer 三实现）+ Micrometer 派生 ratio gauges（busy=Math.max(0,processing−emit)/elapsed——jitter 防负、backpressured=emit/idle，elapsed=0 NaN 哨兵）+ processInputGate read 包络 idle 累计 + 迭代 elapsed 累计。**执行发现**：Micrometer gauge 弱引用陷阱真实命中（任务结束后实例不可达→gauge 消失，E2E 首跑 red 实证）——按 ChannelQueueGauges D2c 先例改静态 TASK_HOLDERS 强引用 + releaseHolder()（invoke finally 调用，结束后读 NaN 诚实标注）
- [x] core：InputGate getChannelWatermark/getMaxActiveChannelWatermark getter + operator watermark/lag gauges（静态 holder 重绑，SOURCE/SELF_CONTAINED 无 gate 不注册，首 watermark 前 NaN 哨兵）
- [x] runtime：RemoteInputChannel capacity 字段（getCapacity + DEFAULT_QUEUE_CAPACITY 转公有常量）+ ChannelQueueGauges fill.ratio（同 holder 派生读数 clamp [0,1]，release 后读 0）
- [x] 测试：TestStreamTaskMetricsTimeShare 2、TestInputGateWatermarkLag 3、TestChannelQueueFillRatioGauge 2、TestObservabilityWiringE2E 扩 7 项 meter 存在断言；TestChannelQueueGauges 4/4 零回归

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 全绿（全量链 EXIT=0；runtime 单跑 m2 旧 jar 假红两次由 -am/先 install 排除——WI5/WI9/WI7 同款纪律第三次记录）
- [x] **接线验证**：三族新指标被真实消费——TestObservabilityWiringE2E 扩 7 项 meter 存在断言（真实 LOCAL 管线）防空壳（调试中实证弱引用陷阱：无 holder 时 gauge 在断言时点已消失）
- [x] **无静默跳过**：TaskMetricsHandle 新方法显式委托 delegate（反序列化回落 NOOP 空实现不抛错——既有语义保持；**未用 default 方法**——default 空实现会让 Handle 静默丢转发，审查 F6 二选一裁定）
- [x] No owner-doc update required（docs 集中 Phase 2，名表已同步）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-08.md` plan 2337 条目）

### Phase 2 - 收口

Status: completed
Targets: nop-stream.md 名表、plan/roadmap

- Item Types: `Proof`

- [x] docs：nop-stream.md 权威名表三族新名 + 指标语义
- [x] 独立子 agent closure audit；roadmap WI16 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI16 已勾选、提交完成

## Closure Gates

- [x] 三族指标落地且在 /metrics 可观测（非孤岛注册）
- [x] 既有指标/测试零回归（TestChannelQueueGauges 4/4；全量链除既有 flake 零失败，两 flake 隔离复跑均绿）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 全绿（全量链 EXIT=0；runtime 单跑 m2 旧 jar 假红两次由 -am/先 install 排除——WI5/WI9/WI7 同款纪律第三次记录）
- [x] **Anti-Hollow Check**：占比累计/lag/fill 在运行链路被真实消费（单元 + WiringE2E 7 项断言）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Non-Blocking Follow-ups

- SOURCE 内部 poll 阻塞细分（busy/idle for sources）：需热路径新包络，无真实运维诉求前不做（Non-Goals 维持）
- 残差 other 的细分计量（watermark 处理/side-output 发射段）：出现占比失衡诊断诉求再做

## Deferred But Adjudicated

### operator 级 watermark 驻留指标

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: one-input 算子透传不驻留 watermark（引擎结构），task 级 merged gauge + channel lag 已覆盖级联观测；算子级需改算子基类，无消费诉求
- Successor Required: `no`

## Closure

Status Note: WI16 三件套（忙/背压/空闲占比派生 gauges + watermark 级联 lag + 边级队列填充比）落地并在真实 LOCAL 管线上注册消费（WiringE2E 7 项 meter 存在断言）；审查 2B/2M 口径钉定（方案 B' 派生、SOURCE 部分口径、watermark 三钉、fill 调和）与 audit 4 Minor（恒 NaN 措辞/F5-F9 补记/javadoc 归位/watermark key 补 jobId）全部处置。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_bbf3748c-6a84-499b-8788-c5bbb0df904d），全程只读
- Evidence:
  - 实现 vs plan PASS（六维度逐条 file:line：接口扩面三实现/B' 派生公式/watermark 三钉/累计挂点/fill 调和/名表 +8 行）
  - 测试真实覆盖 PASS（TimeShare 2 + WatermarkLag 3 + FillRatio 2 + WiringE2E 1 全绿；audit 独立重跑全量链 EXIT=0：core 1723 + runtime 1308 tests 0F/0E）
  - Anti-Hollow PASS（静态 TASK_HOLDERS 强引用与声明一致；E2E 7 项断言真验证真实管线注册；scan-hollow core+runtime 0）
  - owner doc PASS（名表 +8 行：残差 other 口径/NaN 哨兵/派生读数声明/local 推导口径）
  - Minor 处置：M1 恒 NaN 措辞统一；M2 审查 F5-F9 处置补记入 plan；M3 RemoteInputChannel javadoc 归位；M4 watermark holder key 补 jobId（同进程多作业归因瑕疵消除）
  - 提交圈定：仅 WI16 文件（core metrics/invokable/InputGate + runtime transport/测试 + docs + plan + roadmap 勾选 + 10-08 日志），sibling survey 不混入

Follow-up:

- batch-core TestBatchTaskCheckpoint 与 debezium TestDebeziumCdcCheckpoint kill 时序敏感 flake（既有 watch-only，隔离复跑均绿，历次 WI 记录一致）
- no remaining plan-owned work
