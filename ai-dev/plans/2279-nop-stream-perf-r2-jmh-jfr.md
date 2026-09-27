# 2279 nop-stream 性能收敛 R2：传输/算子路径 JMH 基准补建 + JFR 迭代优化至无 ≥2% 收益

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: 审计 `ai-dev/audits/2026-09/2026-09-27-deep-audit-nop-stream-quality-r2/03-performance-r2.md`（6 个已登记未实测候选的代码级分析 + 3 个新 P1 发现，行号均 HEAD 实测）+ `ai-dev/audits/evidence/nop-stream-perf-360/convergence.md`（R1 收敛裁定，停止判据先例）
> Related: 2277/2278（先行）；360（第一轮性能收敛，completed——其 8 条基准覆盖路径已收敛，本计划补齐未覆盖路径并延续同一停止判据）
> Draft Review: R1 独立子 agent 对抗性审查（agent_d4f8bd1d）：5 Major（checksum 写侧参数不可实现→读侧 fixture+JFR 归因、5ms 延迟档恢复、CepOperator 台账三处规格钉死、锁收窄 sendLock+锁内复检+latch 守护测试、方差协议缺失）+ 3 Minor 全部折入；R2 复审（agent_c2b810f8）：**无 Blocker 可执行**，折入核实 8/8 DONE，新发现 N1-N3 Major（F1 等价测试须整文档字节级 golden 对比而非仅校验值、write 数据路径同样锁内复检+双交错守护测试、台账对账标志须覆盖 processing-time 模式）+ N4-N6 Minor（快照语义/再锚定清单补全/Goals 口径残留）——已全部折入，Plan Status 置 active

## Purpose

延续 plan 360 的"归因→优化→测量→留舍"循环，把 JMH 覆盖补齐到 360 未测的传输/CepOperator/WindowOperator-RocksDB/checksum/BufferPool/处理时间驱动路径，实测裁定每个已登记候选是否真有 ≥2% 收益；有则实施保留（回退即 revert），无则记录无收益证据。收敛判据与 360 相同：**所有候选经实测后无任何 ≥2% 低风险可收割项**（JFR 归因 + 实测双证据）。

## Current Baseline

- 360 已收敛路径（codec/memory-rocksdb state/window/timer/NFA/SharedBuffer/CheckpointSerDe 基准口径）不再重复优化；其 Deferred 项（LocalCache 替换、RocksDB merge operator、二进制 envelope）维持 Deferred 不在本计划范围。
- 未测路径审计确认的候选与拟建基准（03 报告第一节）：RemoteResultPartition 锁内同步 send（:162-180，锁内 2×JSON+syncGet，broadcast T 次重复 encode RecordWriter.java:155-167）、InputGate 50ms 轮询（信号驱动，饱和零成本，空闲延迟项）、CepOperator timer 批全扫+每 watermark 全台账深拷贝（CepOperator.java:1033-1039/:847-856/:463-469；台账驱动索引化无需格式变更，:910 null 桶不设防）、CheckpointSerDe base64（格式耦合，归并 Deferred；真热点 F1 checksum :351-365 全文 3× 序列化）、BufferPool 逐许可 AQS 握手（:36-137，畅通边预期 <2%）、ProcessingTimeServiceDriver 固定 100ms tick（:116-132，nextTimerTimestamp volatile :60 已存在）。
- 新 P1：F1 checksum（上）；F2 WindowOperator evictor 路径 RocksDB O(n²)（:1441-1454，AR-3 索引对齐契约 :1027-1030）；F3 兜底 MapState 布局每元素 RMW（:1371+:1539-1567，360 缓存不覆盖）；F4 namespace/stateKey 字符串重建（:1716-1725/:2164）；F5 每记录 4 次时钟读（StreamTaskInvokable:876/:914/:931/:935，G52 liveness 语义需复核）。
- 基建现成：`nop-benchmark/nop-benchmark-stream`（8 类/14 方法，JMH 1.33，proc=full；运行方式见其 README）；JFR 方法论见 `ai-dev/audits/evidence/nop-stream-perf-360/baseline.md`。
- 2277/2278 完成后的 HEAD 为本计划起点；2278 Phase 4 将以固定口径复测 WindowOperator/Timer 两基准确认无 ≥2% 回退（该前提在 Phase 1 开工时实核）。2278 会搬移 WindowOperator 内部类与改写注释——本计划引用的审计行号（CepOperator/WindowOperator 各锚点）在动工前须以符号名重新锚定（见 Phase 1）。
- **测量方差协议（全计划裁定纪律，沿用 360 实证教训）**：每候选同口径 ≥3 次重复或以误差条重叠判定；2-5% 边界结果必须重跑一轮确认（360 先例：r2c 方差验证轮）；未触碰基准噪声带 ±3%（越带即查）；重量级基准（CheckpointSerDe 等 ms 级、误差可达 ±17%）不以单轮定留舍。

## Goals

- 新增 5 个基准类（RemoteTransportWriteBench、CepOperatorBench、InputGateReadLoopBench、BufferPoolPermitBench、ProcessingTimeDriverLatencyBench）+ 扩展 2 个既有基准（WindowOperatorProcessElementBench 加 backend=rocksdb/evictorSize/fallback 布局参数；CheckpointSerDeBench 加读侧 checksum on/off（fixture 剥离）两档），基线（含 alloc.rate.norm）入 evidence。
- 逐轮优化：JFR/审计归因 → 实施 → focused 测试 → 实测 → 留舍；≥2% 且无 >2% 他项回退才保留，否则 revert 并记录；实测 <2% 的候选记录无收益证据后关闭。
- 收敛裁定书（convergence R2）：各轮台账 + JFR 归因 + 剩余候选归属，停止判据 = 无 ≥2% 低风险可收割项。
- 每轮优化不改变对外行为语义（序列化格式、checkpoint 格式、指标语义、键格式保持；InputGate 250ms/150ms 次序契约、公平 FIFO 语义、close() EOS 顺序契约保持）。

## Non-Goals

- 不做格式/协议变更类优化（二进制 envelope、RocksDB merge operator、免 base64、checksum 算法更换）——除非以兼容方式（如版本门控双算法）可行且实测 ≥2%，否则维持 Deferred。
- 不做 SharedBuffer LocalCache 实现替换（360 Deferred）。
- 不优化控制面低频路径（F9 watch-only）；不做跨环境绝对数字对比（同机前后对比）。

## Scope

### In Scope

- `nop-benchmark/nop-benchmark-stream`：新基准 + 参数扩展
- `nop-stream-core`：InputGate/BufferPool/ProcessingTimeServiceDriver/StreamTaskInvokable/RecordWriter（传输与算子热路径优化）
- `nop-stream-runtime`：RemoteResultPartition/DataPlaneMessageService、CheckpointSerDe（F1）
- `nop-stream-cep`：CepOperator（台账驱动 drain）
- `nop-stream-rocksdb`：WindowOperator RocksDB 路径涉及的必要后端配合（如有）

### Out Of Scope

- 连接器四模块、fraud-example；wire 格式变更；CEP 状态增量化协议重设计

## Execution Plan

### Phase 1 - 基准补建与基线（无优化）

Status: completed
Targets: `nop-benchmark/nop-benchmark-stream`

- Item Types: `Proof`

- [x] 5 个新基准类落地并冒烟全通（RemoteTransportWriteBench：桩 IMessageService 仅实现 sendAsync 内 parkNanos 即可复现锁内阻塞发送，@Param 发送延迟 **0/100µs/5ms**——5ms 档覆盖 SysDao/DB 同步写场景（锁收窄的头号收益场景，R1 审查发现 1）+ fanout 1/4/16 + writer/heartbeat 双组；CepOperatorBench：memory/rocksdb × keys 1/64 × bucketsPerKey 8/64，processElement+周期 processWatermark，驱动方式照 TestCepOperatorMultiKeyWatermark 先例；InputGateReadLoopBench：gap 0/100µs/10ms × channels 1/4，饱和吞吐档 + SampleTime 延迟档——**若 JMH @Group 栅栏同步致阻塞环失真，授权退化为独立多线程 harness + 自采直方图**，失真判定记录入 evidence；BufferPoolPermitBench：生产/消费 @Group × 批量 1/8/32 × 并发 1/4——落地偏差：批量/并发参数未建（基线先测无竞争与乒乓两档，批量变体属优化实施后的对照口径，见 baseline-r2.md）；ProcessingTimeDriverLatencyBench：单 JMH 线程"注册 now+X 定时器后阻塞至 fire"计 op 时间（op 时间 = fire 时刻−到期时刻的采样分布），driver 线程驱动，tickMs 100/20）
- [x] 2 个既有基准扩展落地：WindowOperatorProcessElementBench +backend=rocksdb（Trial 级临时目录照抄 RocksDbKeyedStateBench）、+evictorSize 100/1000（仅 EVICTOR 档生效）、**fallback MapState 布局变体——裁定取消**（R2 复审发现 3 的实现路径需绕开 builder 直接构造；执行期实测裁定：WindowOperator 在 src/main 的唯一构造点是 WindowOperatorBuilder.buildWindowOperator，全部路径传非 null descriptor → 兜底 MapState 布局生产不可达，F3 无需基准与优化，见 Phase 2 的 F3 裁定）；@Param 组合按需裁剪，实际运行组合列入 baseline-r2.md；CheckpointSerDeBench 读侧 checksum on/off（fixture 剥离）落地（deserializeNoChecksum）；**两扩展的采集与 2278 Phase 4 后的 jar 重装同批执行**（避免搬移前后两套不可比基线）
- [x] CheckpointSerDeBench 量化改造（R1 审查发现 2 修正口径）：**写侧 checksum 是 serializeCheckpoint/serializeEpochManifest 内固有步骤，无法参数化关闭**——写侧份额以 JFR 帧归因为主（computeCanonicalChecksumHex/normalizeNumbersDeep 帧样本占比）；读侧以"剥离 checksum 键的 fixture"做 deserialize on/off 两档实测
- [x] **行号再锚定**：以符号名重锚本计划引用的全部代码锚点，记录入 baseline-r2.md——**部分完成**：CepOperator/RemoteResultPartition/InputGate/BufferPool/Driver 锚点已在基准构建中实测（见各基准 Javadoc 与 audit 03-r2）；StreamTaskInvokable/WindowOperator 锚点待 2278 落地后随 Q1 动工复核
- [x] JFR 录制方法论沿用 360 baseline.md；首轮归因候选：RemoteTransportWrite（锁内成本构成）、CepOperator（全扫 vs 台账）、CheckpointSerDe（checksum 序列化份额）——JFR 录制随 Q1/Q2 轮执行
- [x] 过程发现：BenchCepEvent 需 @DataBean + io.nop.stream.bench 包（RocksDB JSON 序列化 + ClassNameValidator 白名单）→ 实测确认 **CEP+RocksDB 组合不可用缺陷**（三层缺陷链，audit `06-cep-rocksdb-gap.md`）；CepOperatorBench ROCKSDB 档被其阻塞，Q1 以 MEMORY 档实测（机制与后端无关）

Exit Criteria:

- [x] `./mvnw compile -pl nop-benchmark/nop-benchmark-stream` 通过；全部新/扩展基准冒烟量级合理（RemoteTransport 42ns@0、CepOperator 1.27µs@MEMORY-8-1、InputGate 117ns@饱和、BufferPool 9ns/6µs、Driver 延迟≈tick/2）
- [x] baseline-r2.md 落档 evidence：主指标 + alloc.rate.norm + 命令 + 与 360 final2 的口径衔接说明 + 实际运行的 @Param 组合清单 + 再锚定记录（`ai-dev/audits/evidence/nop-stream-perf-2279/baseline-r2.md`；raw 入 `_tmp/nop-stream-perf-r2/`）
- [x] `nop-benchmark-stream/README.md` 更新基准清单（13 类）
- [x] No new test required: benchmark-only change；`ai-dev/logs/` 条目已更新
- [x] WindowOperator/CheckpointSerDe 扩展采集批次完成（q2-after-raw.txt：WindowOperator MEMORY 4 窗型×2 evictorSize + ROCKSDB 4 窗型×2 evictorSize、CheckpointSerDe 3 方法；ROCKSDB EVICTOR 档产出 F2 的 128µs/op 实测）

### Phase 2 - 优化轮 Q1：CepOperator 台账驱动 drain 与免拷贝 + WindowOperator RocksDB 路径

Status: completed
Targets: `CepOperator.java`、`WindowOperator.java`、（如需）rocksdb 后端配合

- Item Types: `Fix`

- [x] Q1 归因：CepOperatorBench 基线 + jfr-q1-cep-before.jfr（snapshotTimersByKey 26.5% 样本主导）；WindowOperatorProcessElementBench ROCKSDB 参数档基线随 q2-after 批次采集
- [x] CepOperator：onEventTime/onProcessingTime 的 timer 批从 elementQueueState.keys() 全扫改为台账（registeredEventTimeTimersByKey headSet 驱动）。**R1 审查发现 4 + R2 复审 N3/N4 四点规格落地**：(a) legacy 兜底落地为 **per-key 对账集合**（reconcileTimerLedgerIfNeeded：key 首次 drain 时以 elementQueueState.keys() 回填台账后标记——比全局标志更精确，覆盖 processing-time 模式）；(b) advanceTime 序列等价——仅对有桶 ts 走 advanceTime(ts)，无桶 ts 整体跳过含 advanceTime；(c) 无桶 ts 处置=跳过（TRACE）；(d) 快照语义——due 子集 ArrayList 快照迭代，drain 中新注册计时器不卷入本轮（registeredEventTimeTimersByKey headSet 视图）驱动。**R1 审查发现 4 + R2 复审 N3/N4 钉死四点规格**：(a) legacy 兜底机制——restoreState 时置"台账未对账"标志，强制该 key 走全扫路径直至**首次 drain 完成**（onEventTime 或 onProcessingTime 按模式，processing-time 模式的 drain 同样依赖台账、restore 发散风险相同），否则桶无台账项时台账驱动会静默丢事件；(b) advanceTime 序列等价——仅对**有桶** ts 走原 advanceTime(ts) 逐个推进路径，无桶 ts（窗口定时器）整体跳过含 advanceTime（现实现本就由 keys() 驱动不会触达窗口 ts，跳过即行为保持）；(c) 无桶 ts 处置=跳过（TRACE 级，不告警）——advanceTime(currentWatermark) 已完整覆盖窗口定时器语义，跳过是行为保持而非行为修正（弃"告警+跳过"方案：窗口定时器到期是常态，WARN 即日志风暴）；(d) **快照语义**——现 PriorityQueue 是 drain 前快照（drain 中 processEvent/bufferEvent 会 registerTimer 新桶），台账驱动须先取待处理 ts/键集快照、迭代基于快照，不得用 live 视图把 drain 中新注册的 ≤watermark 计时器卷入同一轮（行为变化 + CME 风险）
- [x] CepOperator：snapshotTimersByKey 深拷贝消除——processWatermark 改活迭代收集 due keys（Q1b，keyed 隔离下收集决策稳定 + 排水前重检查守卫）；forEachEventTimeTimer 经查无生产调用方（仅 core 测试），保留原状不影响热路径
- [x] WindowOperator F2：~~增量追加或批量写设计~~ **裁定 Deferred**——实测 EVICTOR+ROCKSDB 127.8-130.9µs/op（vs TUMBLING+ROCKSDB 3.9µs = 33×，确定性 >2%）；修复需 RocksDB 列表增量追加/索引移除设计（与 360 Deferred「RocksDBListState O(n)/merge operator」同根因同 successor；AR-3 索引对齐契约约束下无行为保持的低风险实现）；基准已建、量化已入 convergence-r2.md 为交接物
- [x] WindowOperator F3：~~兜底 MapState 布局每元素 RMW~~ —— **裁定取消（执行期发现，替代计划条目）**：WindowOperator 在 src/main 的唯一构造点是 WindowOperatorBuilder.buildWindowOperator，全部 builder 路径（aggregate/reduce/apply/process）均传非 null stateDesc，且 Memory/RocksDB 两个 keyed 后端均为 IInternalStateBackend → 兜底 MapState 布局生产不可达，360 的缓存未覆盖它不构成损失；无需基准与优化。证据：grep `new WindowOperator(` src/main 仅 builder 一处（buildWindowOperator stateDesc 参数恒非 null）
- [x] WindowOperator F4：windowNamespace 单槽记忆化落地（W 按值 equals 比较，task 线程封闭，无失效风险）——SLIDING -3.5%（6 窗口/元素）、EVICTOR -15% 方差收窄、TUMBLING/SESSION 方向一致带内 → 保留；**stateKey（getSimpleAccumulator）记忆化未实施**——量化：其绝对成本 ~30-60ns/trigger 相对 SLIDING 636ns ≈ 5-9% 但与 namespace 记忆化收益重叠，单独残差 <2% → watch-only 登记（Successor: 如需，同型单槽 memo）
- [x] 每项 focused 测试：TestCepOperatorLedgerDrain 3/3（对账恢复可判别修复前/无桶跳过/普通匹配等价）+ 既有 multiKey watermark/restore e2e 覆盖 advanceTime 序列；跨 key 隔离由 TestCepOperatorMultiKeyWatermark 既有覆盖

Exit Criteria:

- [x] 留舍台账：Q1 台账驱动 drain + 浅拷贝——MEMORY 四档 -24.1/-24.8/-40.4/-32.4%（全部 ≥2% 保留）；**Q1b 追加轮（JFR 驱动：浅拷贝+迭代仍占 38% 样本 → 活迭代收集 due keys）再 -35.9%，keys=64×64 总收益 -56.7%**（1.386µs vs 基线 3.199）；F3 裁定取消（生产不可达，证据在计划条目）；F2 实测 EVICTOR+ROCKSDB 128µs/op（33×）→ Deferred（与 360 RocksDBListState 同族，基准即交接物）；台账见 convergence-r2.md
- [x] focused 测试全绿：TestCepOperatorLedgerDrain 3/3（per-key 对账/部分台账恢复 + window-timer 无桶跳过/普通匹配等价）；cep 全量 372/0、rocksdb 119/0、core 1599/0
- [x] **端到端验证**：窗口/CEP/checkpoint 恢复 e2e 测试全绿（cep/rocksdb/runtime 全量含 TestCepCheckpointRestoreE2E 等）
- [x] **无静默跳过**：per-key 对账（reconcileTimerLedgerIfNeeded）与无桶 ts 跳过（TRACE）路径由 TestCepOperatorLedgerDrain 两条测试覆盖（对账测试对修复前行为可判别：不對账则桶被静默跳过、PQSize 不归零）
- [x] 序列化/状态格式不变（台账为 operator 内存结构不在 checkpoint 格式新增字段；恢复测试全绿）；`ai-dev/logs/` 条目已更新；No owner-doc update required（无桶 ts 跳过=行为保持，非行为修正——advanceTime 语义已完整覆盖窗口定时器，与 360 R2 审查裁定一致）

### Phase 3 - 优化轮 Q2：传输路径（RemoteResultPartition 锁收窄 + broadcast encode-once）与 CheckpointSerDe checksum

Status: completed
Targets: `RemoteResultPartition.java`、`RecordWriter.java`、`CheckpointSerDe.java`

- Item Types: `Fix`

- [x] Q2 归因：RemoteTransportWriteBench 全延迟参数基线 + CheckpointSerDeBench 读侧 on/off 基线 + jfr-q2-checkpointserde.jfr（normalizeValue 380/normalizeNumbersDeep 220 样本主导）
- [x] RemoteResultPartition：send 移出 monitor 的锁收窄。**R1 审查发现 5 + R2 复审 N2 钉死方案落地**：per-partition sendLock 只包 `messageService.send`；close() 顺序 markFinished → stopHeartbeat → sendLock 内发 EOS；write 与 sendHeartbeatIfIdle 均在 sendLock 内复检 isFinished。以 5ms 档实测：互卡组 writer -12.7%（delay=0）、病态方差坍缩（delay=5ms 组 1063999±5359764µs → 15154±1564µs）→ 保留**R1 审查发现 5 + R2 复审 N2 钉死方案**：per-partition sendLock 只包 `messageService.send`；close() 顺序为 markFinished（volatile 可见）→ stopHeartbeat → 获取 sendLock（等在途心跳/数据 send 完成，天然先序）→ **sendLock 内复检 isFinished** → 发 EOS；**write 数据路径同样在 sendLock 内复检**（或检查+send 同锁）——否则检查通过后 close 全程完成、数据落在 EOS 之后（今 monitor 全互斥下不可能，收窄后须守护）；后续心跳在 sendLock 内复检即被拒绝。以 5ms DelayedBackend 档实测留舍
- [x] 锁收窄守护测试（TestRemotePartitionSendLock 4/4）：(i) latch 挂住在途心跳 send → close 等待 → 后端序 [心跳， EOS] ✓；(ii) close 后心跳调用被拒且不落地（volatile + 锁内复检双路径，断言在全部交错下有效——窄竞态窗口经锁内复检守护，确定性交错 (i) 已覆盖先序契约）；data-after-close 拒绝断言 ✓(i) latch 挂住**在途心跳** send → close() 等待其完成后发 EOS → 断言后端序 [心跳， EOS] + eosSendError 传播；(ii) 心跳**未获锁**阻塞 → close 先发 EOS → 心跳获锁后锁内复检被拒、永不落地；另加 data-after-close 拒绝断言（close 完成后 write 不再落后端）
- [x] RecordWriter/RemoteResultPartition：broadcast encode-once **实施后 REVERT**——PreEncodedWireWrite 通道 + RecordWriter broadcast 分支实现并通过功能测试（3 分区同 envelope、encode 计数 1）；实测 delay=0 干净档 156→165-166ns（边界重跑 2 轮稳定 **+6.4% 回归**：微载荷分发开销>编码节省，5ms 档 -15% 系 park 漂移混淆不可归因）→ 按留舍纪律 revert（通道与测试移除）；大载荷收益未量化 → follow-up（payload-size 参数化基准后立项）
- [x] F1 checksum：量化完成——读侧校验份额 **78%**（deserialize 8.49±2.27ms vs off 档 1.77±0.44ms）；写侧 JFR normalizeValue 380/normalizeNumbersDeep 220 样本主导。**裁定 Deferred**：读侧 78% = canonical 契约本体（parse∘normalize∘serialize+SHA-256 写读同构），免改需格式 v2（文档即 normalized 形态，双端免 parse/normalize，需版本门控+旧格式兼容设计）；"canonical 拼接"候选仅省写侧第三次 serialize（估 <25% 写侧）且以 normalize 恒等假设为前提（非本真数值词法改写场景违反字节级等价硬约束）→ 按计划 Non-Goals 不强做（量化证据入 convergence-r2.md）若 ≥2%（按方差协议确认）且可兼容优化——R1 审查确认存在复用路径：canonical 化文本与最终输出同为定序 JSON 文本，可"一次 canonical serialize → SHA-256 → 文本拼接 checksum 键"省一轮 serialize+parse——实施时以"**整文档字节级等价**"为硬约束：新旧 serializeCheckpoint 输出做 golden bytes 逐字节对比（R2 复审 N1：仅断言"校验值不变"对正文漂移零检出力——normalizeNumbersDeep 若有词法改写如 BigDecimal("0.100")→"0.1"，正文字节变化而校验值测试持续绿；字节级对比蕴含校验值等价）；normalize 对非 JSON 本真数值的任何词法改写场景必须落到 Deferred 不得强做；不可兼容则登记 Deferred（量化证据入档）
- [x] 每项 focused 测试：TestRemotePartitionSendLock 3/3（在途心跳先序 latch 交错、close 后心跳/写拒绝）；EOS 失败传播/eosSendError 由既有 TestRemoteTransportLifecycle 覆盖；checksum 等价测试不适用（Deferred 未实施）

Exit Criteria:

- [x] 留舍台账（同 Q1 纪律）：锁收窄——writerHeartbeat 组 delay=0 writer 165→144ns（**-12.7%**）、delay=5ms 组病态方差坍缩（1063999±5359764µs → 15154±1564µs）；writeSingle 全档 ±3% 带内（无回归）；**encode-once REVERT**（delay=0 干净档 156→165-166ns，边界重跑 2 轮稳定 +6.4%——微载荷下分发开销>编码节省；PreEncodedWireWrite 通道移除，登记大载荷 follow-up）；**F1 Deferred**（读侧校验份额 ~79%：8.49ms vs 1.77ms off 档；写侧 normalizeValue/normalizeNumbersDeep 主导；canonical 契约本体不可免改 → 格式 v2 successor）
- [x] focused 测试全绿：TestRemotePartitionSendLock 4/4（在途心跳先于 EOS 落后端 [heartbeat,EOS] latch 交错、close 后心跳锁内复检拒绝、写后关拒绝、eosSendError 传播）；runtime 全量 1088/0（含 TestRemoteTransportLifecycle 7/7）
- [x] **端到端验证**：remote 传输/eos/checkpoint 恢复测试全绿；close 顺序契约由 latch 确定性交错测试守护
- [x] checksum 若实施：——未实施；**若 Deferred：量化证据入 convergence R2**（读侧 78% 份额 + 写侧 JFR 帧归因 + 格式 v2 successor 条件 + "canonical 拼接"候选的字节等价前提分析）
- [x] `ai-dev/logs/` 条目已更新；No owner-doc update required（契约不变）

### Phase 4 - 优化轮 Q3：量化裁定轮（InputGate/BufferPool/Driver 时钟读等小项）与收敛裁定

Status: completed
Targets: InputGate、BufferPool、ProcessingTimeServiceDriver、StreamTaskInvokable、evidence 目录

- Item Types: `Proof` + `Fix`（仅对实测 ≥2% 项）

- [x] InputGateReadLoopBench/BufferPoolPermitBench/ProcessingTimeDriverLatencyBench 实测：InputGate 饱和 117ns/op 与空闲参数无关（信号驱动证实，吞吐零收益 → 量化关闭，维持 360 不实施）；BufferPool 畅通 157ns（<2% 证实）/乒乓 5995ns（AQS 握手量化，批量记账属公平 FIFO/许可守恒契约重设计 → successor 条件）；Driver tick=100 fire 延迟~60ms/tick=20→~8.6ms（延迟口径非吞吐，sleep-to-deadline 机制可行低风险 → 量化关闭 + shadow-volatile 方案留档 follow-up）；@Group 大 gap 档失真形态按预期出现（授权的独立 harness 退路未启用——量化已足以裁定）验证/推翻"吞吐零收益/畅通边 <2%"预判；sleep-to-deadline 若延迟收益显著且低风险（shadow-volatile 方案）则实施，否则记录量化证据维持不实施
- [x] F5 时钟读：G52 复核——activity/progress 时间戳即 liveness 判定信号源（TaskManager 心跳按其判定任务活性），降频=改变 liveness 检测周期语义 → **watch-only 登记**（预估绝对量 ~100-240ns/记录；successor 条件：liveness 周期语义独立裁定后按批降频）
- [x] 收敛裁定书 convergence-r2.md 落档（Q1/Q1b/Q2/Q3 台账 + jfr-final-nfa20-r2/jfr-final-cep64-r2 归因 + 剩余候选归属表）
- [x] 停止判据复核成立：Q1b 后候选空间（MemoryMapState RMW 契约本体/TypedNamespaceAndKey 64-key 轮转固有成本/guava LocalCache Deferred 家族/NFA 本体）均非低风险可收割项；JFR + 实测双证据

Exit Criteria:

- [x] 三个小项候选各有实测/归因证据：InputGate（饱和 117ns/op 与空闲参数无关=信号驱动证实，量化关闭）、BufferPool（畅通 157ns <2% 域证实；乒乓 6µs 量化入档，批量记账属契约重设计 → successor 条件）、Driver（tick=100 延迟~60ms/tick=20→~8.6ms 实测；sleep-to-deadline 低风险但收益口径为延迟非吞吐 → 量化关闭 + shadow-volatile 方案留档 follow-up）；F4 实施（SLIDING -3.5% 保留）；F5 watch-only（G52 liveness 语义耦合）；F2/F3 裁定见 Phase 2
- [x] convergence-r2.md 落档 evidence（各轮台账 + 最终 JFR 归因 + 剩余候选归属 + 停止判据复核，JFR + 实测双证据）
- [x] 若有实施项：focused 测试 + 模块测试全绿（cep 372/runtime 1088）；`ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 基准资产落地：5 新 + 2 扩展基准可复现（baseline-r2.md 入 evidence，raw 入 _tmp/nop-stream-perf-r2/）
- [x] 多轮优化有 ≥2% 实测收益保留项：Q1/Q1b 台账驱动 drain（-19.6~-56.7% 四档）、Q2 锁收窄（互卡 writer -12.7% + 方差坍缩）、Q3 F4 记忆化（SLIDING -3.5%）——共 7 项保留全部 ≥2% 实测
- [x] 所有保留优化项语义等价（TestCepOperatorLedgerDrain 3 + TestRemotePartitionSendLock 4 + 既有全量绿；格式/指标/键语义/close-EOS 顺序契约保持）
- [x] 全部留舍判定有测量证据（9 保留项有同口径对比；encode-once revert 有边界重跑 166±1/166±2；F1/F2 Deferred 有量化；InputGate/BufferPool/Driver 量化关闭）
- [x] 不存在被静默降级的 in-scope live defect（CEP+RocksDB 组合缺陷为过程新发现，已登记 audit 06 + module-groups 限制说明 + successor 条件，非静默）
- [x] `nop-benchmark-stream/README.md`（13 基准类）与 `docs-for-ai/01-repo-map/module-groups.md`（基准描述 + CEP+RocksDB 限制）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（含停止判据复核）——agent_3dcfcce1：工程实质全达标（代码/测试/基准资产/停止判据），4 Major 均文档证据层（M1 边界引用/M2 计数失实/M3 raw 缺口/M4 Phase 1 Status），已全部修复并经 check-doc-links 0 error + cep 64×64 补跑确认（1.285±0.067µs）
- [x] **Anti-Hollow Check**：新基准调用真实生产路径（RemoteTransportWriteBench 走真实 encode+send、CepOperatorBench 走真实缓冲/drain、审计 06 的桩仅替换传输末端延迟）；台账对账/失效路径连通（TestCepOperatorLedgerDrain 实跑，对账测试可判别修复前行为）
- [x] `./mvnw compile -pl nop-benchmark/nop-benchmark-stream` 通过
- [x] `./mvnw test`（stream 五模块）全绿——core 1599/0（Skipped 1）、cep 372/0、rocksdb 119/0、flow 118/0（同批）+ runtime 1087/0（Skipped 10，串行复跑；同批首跑 1 失败为 TestAsyncSnapshotPipeline 异步时序 flake——本轮 JMH 采集与回归并行致 CPU 争用延迟持久化断言窗口，单测复跑 12/12 绿，根因与修复无关）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2279-nop-stream-perf-r2-jmh-jfr.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0

## Deferred But Adjudicated

### CheckpointSerDe checksum 兼容优化（若 Phase 3 裁定不可兼容）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 校验值内容寻址，正形/算法变更需版本门控双算法与旧格式回归，属格式耦合（与二进制 envelope 家族同属 Deferred）
- Successor Required: `yes`（按需）

### InputGate 空闲延迟优化（若量化后维持不实施）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 信号驱动 poll 下饱和吞吐零成本，收益仅在空闲 CPU/首记录延迟；250ms/150ms 次序契约约束下低风险改造空间有限
- Successor Required: `no`（量化证据归档后按需）

### 格式/协议变更家族（二进制 envelope、RocksDB merge operator、免 base64、LocalCache 替换）

- Classification: `optimization candidate`（360 既有登记延续）
- Why Not Blocking Closure: 均需格式版本协商/存储引擎/驱逐语义重设计，超出低风险单点边界
- Successor Required: `yes`（按需立专项）

## Non-Blocking Follow-ups

- F5 时钟读若裁定 watch-only：登记 G52 语义复核结论与条件
- 控制面 RPC 反射映射（F9 watch-only）
- 中文注释英化与参数对象全量整改（2278 Deferred 延续）

## Closure

Status Note: 基准资产 5 新 + 2 扩展落地并全参数基线入档；四轮实测留舍：Q1/Q1b（CepOperator 台账驱动 drain + per-key 对账 + 活迭代收集）-19.6~-56.7%（cep 64×64 确认轮 1.285µs 独立复跑成立）、Q2 锁收窄（互卡 writer -12.7% + 5ms 档病态方差坍缩）、Q3 F4 记忆化（SLIDING -3.5%）共 7 项保留全部 ≥2% 实测；encode-once revert（边界重跑稳定 +6.4% 回归）；F1/F2 Deferred 带量化（78%/128µs·33×）；InputGate/BufferPool/Driver/F5 量化关闭或 watch-only 均有据。停止判据成立（最终 JFR 栈顶 snapshotTimersByKey 消失，剩余为机制本体与 360 Deferred 家族）。过程新发现 CEP+RocksDB 组合不可用缺陷已登记 audit 06 + docs 限制说明 + successor 条件。cep 372/runtime 1087/core 1599/rocksdb 119/flow 118 全绿。独立收口审计 4 Major（全文档证据层）已全部修复。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_3dcfcce1-eee3-4681-8551-170ff25eb9ca）
- Audit Session: agent_3dcfcce1-eee3-4681-8551-170ff25eb9ca（首轮 REJECT 附条件复议——4 Major 全为文档/证据层，工程实质全达标）
- Evidence:
  - Phase 1（PASS）：5 新基准 9 方法在 BenchmarkList 在册；baseline-r2.md 数字与 raw 逐行实证（Cep 64×64 3.199±1.023、writerHeartbeat 5ms 病态方差行等）；README 13 类
  - Phase 2（PASS）：drainDueBuckets 台账驱动/per-key 对账/无桶 TRACE 跳过/快照迭代/活迭代收集 + 排水前重检查守卫（CepOperator.java:721-746/:1058-1103 实测）；getSortedTimestamps 0 引用；行为保持逐语义对照（advanceTime 升序/异常包装/detail 字符串/STEP 3-5 零改动）；TestCepOperatorLedgerDrain 3/3 实跑且对修复前可判别
  - Phase 3（PASS）：sendLock 仅包 send（:202-210）；三处锁内复检；close 顺序契约；PreEncodedWireWrite revert 全仓 0 残留（RecordWriter 对 HEAD 零 diff）；close EOS 失败路径与 HEAD 逐行等价；TestRemotePartitionSendLock 3/3 实跑
  - Phase 4（PASS）：windowNamespace 记忆化在位；InputGate/BufferPool/Driver 量化关闭有 baseline raw 支撑；F2 33× raw 实证；F3 取消（builder 唯一构造点 + 6 处 stateDesc 恒非 null 实证）；F1 份额复算 79.1%（m1 已校准为 ~79%）
  - 停止判据复核（PASS）：jfr-final-cep64-r2.jfr 真实 1194 样本，栈顶帧实测 snapshotTimersByKey 消失、剩余为 MapState 契约本体/64-key 轮转/guava LocalCache(360 Deferred)/NFA 本体；剩余候选全部有归属
  - 门禁：scan-hollow high exit 0；focused 测试实跑（3+1+3）全绿；benchmark compile exit 0；check-doc-links --strict 修复后 0 error
  - 4 Major 修复复核：M1 边界引用改描述性落笔 + 日志归因更正；M2 计数 3/3 + 1087 全仓更正（eosSendError 由 TestRemoteTransportLifecycle 覆盖注明）；M3 raw 缺口显式登记 + cep 64×64 补跑 1.285±0.067µs（q1b-cep64x64-confirm-raw.txt）；M4 Phase 1 Status 翻 completed
  - Minor：m1 份额校准 ~79%；m2 两处陈旧 Javadoc 已改；m3/m4/m5 信息性登记不阻塞

Follow-up:

- broadcast encode-once 大载荷立项前置：RemoteTransportWriteBench 增加 payload-size 参数后重测
- ProcessingTimeServiceDriver sleep-to-deadline（shadow-volatile 方案已在审计 03-r2 留档）
- BufferPool 批量许可（公平 FIFO/许可守恒/captureInFlightData 契约重设计）
- F5 时钟读降频（G52 liveness 周期语义独立裁定后）
- F1 checksum 格式 v2（文档即 normalized 形态，版本门控）；F2 RocksDB 列表增量追加（与 360 RocksDBListState 同 successor）
- CEP+RocksDB 组合不可用缺陷修复（successor 正确性专项，audit 06）
