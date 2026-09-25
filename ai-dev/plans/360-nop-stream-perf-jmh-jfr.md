# 360 nop-stream 性能收敛：JMH 基准 + JFR 迭代优化

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: 审计 `ai-dev/audits/2026-09/2026-09-26-0546-deep-audit-nop-stream-quality/03-performance.md`（16 条 P0/P1/P2 经独立复核确认）+ Top-10 JMH 候选清单
> Related: 358（正确性修复，先行）、359（可读性整改）

## Purpose

为 nop-stream 数据面/控制面热路径建立可复现的 JMH 基准与 JFR 观测方法，然后按"归因→优化→测量→留舍"逐轮迭代，直到所有剩余优化候选的收益低于 2%（即无可收割的 ≥2% 项）为止。产出：基准模块（长期资产）、基线与各轮测量证据、一组经验证 ≥2% 收益的性能修复。

## Current Baseline

- 审计确认的热路径缺陷（详见 03 报告，此处按优化轮分组）：
  - 每记录级：远程边 4 次 JSON 编解码 + 每记录 Class.forName 无缓存（StreamElementCodec:58,121-168、KafkaStringWireCodec:48,61）；Micrometer Timer.record(Duration) 每记录分配 2 个 Duration + 4 次 nanoTime（StreamTaskInvokable:904-1000、MicrometerStreamTaskMetrics:85-96）；Memory 后端每状态操作 new TypedNamespaceAndKey + Objects.hash 装箱（MemoryKeyedStateBackend:414-416）；EventId/NodeId/TimerEntry 热键 Objects.hash 装箱。
  - 每访问级：RocksDBKeyEncoder 每次状态访问重新 JSON 编码 namespace+key（:91-103,242-264）；JavaStreamSerializer 每次反序列化重建 JEP290 filter + System.getProperty（:106、StreamDeserializationFilter:69-85）。
  - 每窗口/每事件级：RocksDBAggregatingState.add 每记录 RMW+双 JSON（:131-151，P0）；RocksDBListState.add O(n) 读改写（:116-128，P0）；MergingWindowSet 每记录全量重建（WindowOperator:671,1294-1298）；storeElementTimestamp 整列表 get+put（WindowOperator:1489-1502）；CEP NFAState 定时器批次全量序列化往返（CepOperator:972-983，P0）；SharedBuffer 每事件写穿+accessor close 清空双缓存（P0，修复须 key-scoped 缓存键）；getSortedTimestamps 每 timer 批全扫（CepOperator:985-991）；bufferEvent 整桶 get+put（CepOperator:833-842）。
  - 传输/IO：RemoteResultPartition.write 全程 synchronized 且锁内同步 send（:154-170）；InputGate 空通道固定 50ms 阻塞轮询（:618,680）；CheckpointSerDe byte[] base64 放大（:96-117）。
- 基建先例：`nop-benchmark/` 模块组（JMH 1.33，nop-benchmark-json/orm/xlang/xpl 四子模块）；rocksdbjni 9.11.2（含 macOS aarch64 原生库）。
- JDK 版本支持 JFR（JDK 21）；JFR 分析用 `jfr` CLI（print/summary）。
- 358/359 先行完成（缺陷修复与行为保持重构落地后才开始优化轮，避免基准漂移）。

## Goals

- 新增 `nop-benchmark-stream` JMH 模块（挂入 nop-benchmark 模块组），覆盖 8 条主热路径，基线数据（含 gc.alloc.rate.norm）入 evidence 存档。
- 逐轮优化：每轮 JFR 归因 → 实施候选 → 模块测试 → 基准对比；实测在至少一条主基准上 ≥2% 且其他主基准无 >2% 回退才保留，否则 revert 并记录。
- **收敛判据（本计划核心出口）**：连续一轮满足——(a) 全部未实施候选的预期收益（按 JFR 归因占比估算）<2%，且 (b) 对下一条候选实测验证 <2% 后 revert，即停止迭代。停止裁定需 JFR profile 佐证（无单点 ≥2% 可收割项）。
- 每轮优化不改变对外行为语义（序列化格式、checkpoint 格式、指标语义保持；指标实现可换等价 API）。

## Non-Goals

- 不重写状态后端存储引擎、不引入新序列化框架（二进制 envelope 编码属格式变更，超出本轮，登记 follow-up）。
- 不做 CEP 增量状态持久化等涉及正确性协议重设计的项（登记 follow-up，需独立计划+exactly-once 回归）。
- 不优化 OpsJobManager/webhook 等运维面冷路径。
- 不追求跨环境绝对数字；同一台机上前后对比（同 fork/同 warmup 配置）。

## Scope

### In Scope

- 新模块 `nop-benchmark/nop-benchmark-stream`（JMH 基准，仅 benchmark 代码与必要 test fixture）
- `nop-stream-core`、`nop-stream-runtime`、`nop-stream-rocksdb`、`nop-stream-cep` 的热路径性能修复（行为语义保持）

### Out Of Scope

- 连接器四模块、fraud-example（冷路径）
- 序列化 wire 格式变更、CEP 状态增量化协议重设计、RocksDB merge operator 引入（若 R2 中验证收益 <2% 或风险超限则按裁定处理）

## Execution Plan

### Phase 1 - 基准基建与基线（无优化）

Status: planned
Targets: `nop-benchmark/nop-benchmark-stream`（新模块）、`evidence` 目录

- Item Types: `Proof`

- [ ] 建 nop-benchmark-stream 模块（对齐 nop-benchmark 父 pom 的 JMH 配置与 annotationProcessor 惯例），挂入 nop-benchmark/pom.xml modules
- [ ] 首批 8 基准落地（输入构造取审计 Top-10 清单规格）：StreamElementCodecRoundTrip（encode→toWire→fromWire→decode + decode 单项）、RocksDbKeyedState（value/aggregating/list(100,1k,10k 三档)）、MemoryKeyedState、WindowOperatorProcessElement（tumbling/sliding/session/sliding+evictor）、TimerService（register+advanceWatermark）、NfaProcess（P=1/10/100）、SharedBufferRegister（Memory+RocksDB）、CheckpointSerDe（10k keyed+1MB java bytes）
- [ ] 全部基准跑通并记录基线（含 `-prof gc` 的 alloc.rate.norm；命令与原始输出存 `ai-dev/audits/evidence/nop-stream-perf-360/baseline.md`）
- [ ] JFR 方法论落地：基准 JVM 以 `-XX:StartFlightRecording` 录制，`jfr summary/print --events` 分析 allocation/CPU 归因，样例分析存 evidence（证明 ≥1 条基线归因与审计发现一致）

Exit Criteria:

- [ ] `./mvnw -pl nop-benchmark/nop-benchmark-stream -am compile` 通过；8 基准类可执行并产出稳定数字（同配置两次运行偏差 <10%）
- [ ] baseline.md 落档：每基准的 ops(s)、alloc/op(B) 两次运行记录 + JFR 归因样例
- [ ] `docs-for-ai/01-repo-map/module-groups.md` nop-stream 行与 nop-benchmark 说明已更新（新子模块）
- [ ] 新增功能测试要求（Minimum Rules #25）：基准模块非生产代码，`No new test required: benchmark-only module`；nop-benchmark-stream 挂入 reactor 且 `./mvnw compile -pl nop-benchmark -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 优化轮 R1：低风险每记录/每访问开销

Status: planned
Targets: StreamElementCodec、KafkaStringWireCodec、JavaStreamSerializer、RocksDBKeyEncoder、MemoryKeyedStateBackend、EventId/NodeId/TimerEntry、MicrometerStreamTaskMetrics

- Item Types: `Fix`

- [ ] R1-JFR：R1 目标基准录制 JFR，确认归因（Class.forName/白名单/Duration/hash 装箱/键编码占比），存 evidence
- [ ] valueType→Class 解码缓存 + 白名单校验结果缓存（行为等价：同输入同结果，含非法类名仍拒）
- [ ] JEP290 filter 实例缓存（配置属性读取移出每次反序列化）
- [ ] RocksDB (currentKey,currentNamespace)→byte[] 前缀缓存（key/namespace 切换时失效；注意 RocksDBKeyEncoder.encode 为 static 无状态方法，缓存落点在 RocksDBKeyedStateBackend/状态访问层而非编码器内部）
- [ ] Memory 后端 TypedNamespaceAndKey 复用 + EventId/NodeId/TimerEntry 手写 hashCode（语义等价）
- [ ] Micrometer 记录改 `record(long, TimeUnit.NANOSECONDS)` 等价 API（消除 Duration 分配，指标语义不变）
- [ ] R1 测量：全基准重跑对比表（留舍判定逐项记录；<2% 的候选 revert 并记入 evidence）

Exit Criteria:

- [ ] R1 留舍完成：每个候选实测——≥2% 提升则保留（对比表入 evidence），<2% 则 revert 并记录；**或全部候选实测 <2% 且已全部 revert 并记录（视为提前进入 Phase 4 收敛裁定路径，本 Phase 照常关闭）**
- [ ] R1 新增分支 focused 测试（Minimum Rules #25）：(a) 解码缓存同输入同结果、非法类名仍抛 typed；(b) RocksDB 前缀缓存 key/namespace 切换后失效且读到正确值；(c) filter 缓存后序列化行为与旧路径一致（现有 Java 序列化测试守护，若无则补一条）；逐项或合并成一条测试类均可
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-cep -am` 全绿且不少于 359 完成后的基线
- [ ] **无静默跳过**：新增缓存失效路径显式处理（key/namespace 切换、非法类名拒绝），无吞异常
- [ ] 序列化/存储/指标语义不变的等价性说明记录（evidence 或 daily log）
- [ ] **端到端验证**（Minimum Rules #22）：现有 e2e（本地+remote 数据面）保持通过
- [ ] No owner-doc update required；`ai-dev/logs/` 条目已更新

### Phase 3 - 优化轮 R2：中风险状态与传输路径

Status: planned
Targets: MergingWindowSet/WindowOperator、SharedBuffer、RocksDB 聚合/列表状态、RemoteResultPartition、InputGate、CepOperator

- Item Types: `Fix`

- [ ] R2-JFR：R2 目标基准 JFR 归因存 evidence
- [ ] MergingWindowSet 按 key 复用（persist 后增量维护，替代每记录全量重建；窗口/会话合并测试保持通过）
- [ ] SharedBuffer 缓存生命周期改造：accessor close 改 key-scoped 清理（缓存键引入 key 维度，跨 key 正确性语义保持——审计 03 复核约束）
- [ ] RocksDBAggregatingState.add 消除双 JSON（每 key 前向缓存 accumulator 或等价方案；外部写路径失效语义保持）——若实测 <2% 或破坏语义则 revert 并裁定
- [ ] RemoteResultPartition.write 锁收窄（send 移出 synchronized，epoch/心跳字段用已有 atomic 保护；发送顺序语义保持）
- [ ] InputGate 空通道轮询改非阻塞探测+自适应退避（消除固定 50ms 阻塞；对齐语义由现有 barrier/watermark 测试守护）
- [ ] CepOperator getSortedTimestamps/bufferEvent 的整表扫描与整桶 RMW 治理（索引化或增量维护，若 <2% 则 revert 记录）
- [ ] R2 测量：全基准对比表 + 留舍记录（同 R1 判据）

Exit Criteria:

- [ ] R2 保留项合计在主基准上的净提升与逐项记录入 evidence；每项保留均 ≥2% 或已 revert
- [ ] R2 新增分支 focused 测试（Minimum Rules #25）：SharedBuffer key-scoped 缓存跨 key 隔离与失效正确性、MergingWindowSet 复用后 persist/恢复语义、（若实施）RocksDB 聚合前向缓存的外部写失效——由现有窗口/CEP/恢复测试守护的部分须显式列出测试名，无守护的部分补测
- [ ] **端到端验证**（Minimum Rules #22）：窗口（含 session/evictor）、CEP 匹配、checkpoint 恢复、remote 传输的现有测试全绿
- [ ] **接线验证**（Minimum Rules #23）：SharedBuffer key-scoped 缓存的失效路径确实被 accessor close/switch 调用（测试断言或代码追踪记录）
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-cep -am` 全绿
- [ ] No owner-doc update required；`ai-dev/logs/` 条目已更新

### Phase 4 - 收敛裁定与停止

Status: planned
Targets: evidence 目录、daily log

- Item Types: `Proof`

- [ ] 最终 JFR 全量录制（覆盖 8 基准），确认剩余热点均为单点 <2% 占比或属 Non-Goals 项
- [ ] 对下一条最优候选做实测验证（若 ≥2% 则实施后回到 R 模式继续一轮；若 <2% 则 revert 并记录，满足停止判据）。续轮不设固定上限，但每轮启动必须列出具体候选及其 ≥2% 收益依据（JFR 归因占比或同族先例实测）；当剩余候选全部为 Non-Goals 项或预估 <2% 时停止
- [ ] 收敛裁定书写入 evidence（列剩余候选清单+各自预期收益+为何停止），并核对 Non-Goals 登记项完整

Exit Criteria:

- [ ] 停止判据成立且有 evidence 佐证：连续一轮无任何 ≥2% 可收割候选（预期估算+实测验证双证据）
- [ ] 全部轮次对比表汇总入 evidence（baseline → R1 → R2 → final，含留舍与 revert 记录）
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

- [ ] nop-benchmark-stream 基准资产落地且基线可复现（两次运行偏差 <10%）
- [ ] 至少一轮优化有 ≥2% 实测收益保留，或收敛裁定证明无可收割项（二取一成立且记录在案）
- [ ] 所有保留的优化项语义等价（序列化/checkpoint/指标语义保持说明在案）
- [ ] 全部留舍判定有测量证据，无"未测先留"项
- [ ] 不存在被静默降级的 in-scope live defect（性能缺陷的修复或 revert+登记，二者必居其一；revert 项移入 Deferred But Adjudicated）
- [ ] `docs-for-ai/01-repo-map/module-groups.md` 已同步新模块（Phase 1）
- [ ] 独立子 agent closure-audit 已完成并记录证据（含停止判据复核）
- [ ] **Anti-Hollow Check**：closure audit 验证基准确实调用被优化的生产路径（非替身）；缓存失效路径运行时连通
- [ ] `./mvnw compile -pl nop-benchmark/nop-benchmark-stream -am` 通过
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-cep -am` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### CEP NFAState 增量持久化 / SharedBuffer 状态分片

- Classification: `optimization candidate`（若 R2 中 key-scoped 缓存已消除主要开销，则本项预期收益 <2%，归入 watch-only）
- Why Not Blocking Closure: 涉及状态持久化协议重设计与 exactly-once 回归，收益需 R1/R2 后重新评估；当前全量序列化开销若已被缓存治理压到 <2% 即无必要
- Successor Required: `no`（收敛裁定书登记后按需立计划）

### 二进制 envelope 编码替代双层 JSON wire 格式

- Classification: `optimization candidate`
- Why Not Blocking Closure: wire 格式变更是跨 JVM 兼容性契约，需版本协商设计；R1 的 Class/校验缓存后剩余 JSON 开销若 <2% 即无必要
- Successor Required: `no`

### RocksDB merge operator 引入

- Classification: `optimization candidate`
- Why Not Blocking Closure: 依赖原生 merge operator 语义与序列化器配合，若 R2 前向缓存方案实测达标则不引入
- Successor Required: `no`

## Non-Blocking Follow-ups

- CheckpointSerDe byte[] 免 base64 直写（若实测 <2% 或涉及存储格式版本则维持现状并记录）
- BufferPool 公平信号量改批量许可（数据面吞吐项，视 R1/R2 后 profile 决定）
- ProcessingTimeServiceDriver sleep-to-deadline（延迟精度项，不影响吞吐基准）

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<每条 Exit Criterion / Closure Gate 的验证结果 + 停止判据复核>>

Follow-up:

- <<Deferred But Adjudicated 三项>>
