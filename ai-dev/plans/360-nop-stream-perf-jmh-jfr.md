# 360 nop-stream 性能收敛：JMH 基准 + JFR 迭代优化

> Plan Status: completed
> Last Reviewed: 2026-09-26
> Source: 审计 `ai-dev/audits/2026-09/2026-09-26-0546-deep-audit-nop-stream-quality/03-performance.md`（16 条 P0/P1/P2 经独立复核确认）+ Top-10 JMH 候选清单
> Related: 358（正确性修复，先行）、359（可读性整改）

## Purpose

为 nop-stream 数据面/控制面热路径建立可复现的 JMH 基准与 JFR 观测方法，然后按"归因→优化→测量→留舍"逐轮迭代，直到所有剩余优化候选的收益低于 2%（即无可收割的 ≥2% 项）为止。产出：基准模块（长期资产）、基线与各轮测量证据、一组经验证 ≥2% 收益的性能修复。

## Current Baseline

- 审计确认的热路径缺陷（详见 03 报告）：每记录级（远程边 4 次 JSON+Class.forName、Micrometer Duration 分配、Memory 键对象分配+装箱 hash、热键 Objects.hash）；每访问级（RocksDBKeyEncoder 重编码、JEP290 filter 重建）；每窗口/事件级（RocksDB 聚合 RMW 双 JSON、RocksDB 列表 O(n)、MergingWindowSet 全量重建、storeElementTimestamp 整表、CEP NFAState 定时器批次全量序列化、SharedBuffer 写穿+每事件清缓存）；传输/IO（RemoteResultPartition 锁内同步 send、InputGate 50ms 轮询、CheckpointSerDe base64）。
- 基建先例：`nop-benchmark/` 模块组（JMH 1.33）；rocksdbjni 9.11.2（含 macOS aarch64）。
- 358/359 先行完成。

## Goals

- 新增 `nop-benchmark-stream` JMH 模块，覆盖 8 条主热路径，基线数据（含 alloc.rate.norm）入 evidence。
- 逐轮优化：JFR/审计归因 → 实施 → 测试 → 测量；≥2% 且无 >2% 回退才保留，否则 revert 并记录。
- 收敛判据：连续一轮全部候选预期 <2% 且实测验证，JFR 佐证无单点 ≥2% 可收割项。
- 每轮优化不改变对外行为语义（序列化格式、checkpoint 格式、指标语义保持）。

## Non-Goals

- 不重写状态后端存储引擎、不引入新序列化框架（二进制 envelope 属格式变更，登记 follow-up）。
- 不做 CEP 增量状态持久化等涉及正确性协议重设计的项（登记 follow-up）。
- 不优化运维面冷路径；不做跨环境绝对数字对比（同机前后对比）。

## Scope

### In Scope

- 新模块 `nop-benchmark/nop-benchmark-stream`；core/runtime/rocksdb/cep 热路径性能修复（行为语义保持）。

### Out Of Scope

- 连接器四模块、fraud-example（冷路径）；wire 格式变更；CEP 状态增量化协议重设计；RocksDB merge operator（视验证收益与风险裁定）。

## Execution Plan

### Phase 1 - 基准基建与基线（无优化）

Status: completed
Targets: `nop-benchmark/nop-benchmark-stream`（新模块）、`evidence` 目录

- Item Types: `Proof`

- [x] 建 nop-benchmark-stream 模块（parent=nop-benchmark，JMH 1.33，proc=full 照抄 nop-benchmark-json），挂入 nop-benchmark/pom.xml modules
- [x] 首批 8 基准 14 方法落地并冒烟全通（StreamElementCodecRoundTrip、RocksDbKeyedState、MemoryKeyedState、WindowOperatorProcessElement、TimerService、NfaProcess、SharedBufferRegister、CheckpointSerDe）；【补充】状态基准补 accessPattern（local 常态/rotate 最坏）双口径，缓存类优化以 local 口径评估；listAdd 裁剪为 100/1000 两档（10k 档摊还复位开销失真）
- [x] 全部基准跑通并记录基线（-prof gc；baseline.md 入 evidence）
- [x] JFR 方法论落地（baseline.md 方法论节）；【偏差裁定】R1 前置归因采用审计 03 的调用路径分析（独立复核确认），JFR 自 R2 归因与 Phase 4 起执行——避免为录制回滚生产代码

Exit Criteria:

- [x] `./mvnw compile -pl nop-benchmark/nop-benchmark-stream` 通过；14 方法冒烟全通且量级合理（listAdd 100→1000 梯度 25→134µs 呈预期 O(n)）
- [x] baseline.md 落档：22 行主指标 + 命令 + 方法论
- [x] `docs-for-ai/01-repo-map/module-groups.md` nop-stream 行补基准模块说明；`nop-benchmark-stream/README.md` 落地运行方式
- [x] No new test required: benchmark-only module
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 优化轮 R1：低风险每记录/每访问开销

Status: completed
Targets: StreamElementCodec、JavaStreamSerializer、RocksDBKeyedStateBackend、MemoryKeyedStateBackend、EventId/NodeId/TimerEntry、MicrometerStreamTaskMetrics

- Item Types: `Fix`

- [x] R1 归因：审计 03 调用路径分析（独立复核确认）替代前置 JFR（裁定见 Phase 1 偏差）
- [x] valueType→Class 解码缓存（resolveValueType：白名单校验每调用仍执行——【偏差记录】计划原文"白名单校验结果缓存"收敛为更保守的"合法名缓存 Class、非法名每次 typed 拒绝"，实测 decodeOnly -28% 成立）
- [x] JEP290 filter 实例缓存（CachedConfig 按属性值键控刷新，属性仍每调用读取）
- [x] RocksDBKeyedStateBackend (currentKey,currentNamespace)→byte[] 前缀缓存（equals 失效；落点在后端）
- [x] Memory TypedNamespaceAndKey 复用（cachedNamespaceAndKey(namespace,key) 双入口）+ EventId/NodeId/TimerEntry 手写 hashCode（null 安全）
- [x] Micrometer record(long, NANOSECONDS)
- [x] R1 测量：对比表（r1-raw；codec -28%、Memory local -61~-83%、NFA d20 -38%、SharedBuffer -45%、窗口 -18~-44%）

Exit Criteria:

- [x] R1 留舍完成：6 项全部保留（对比表 evidence）；无 revert 项
- [x] R1 focused 测试（Minimum Rules #25）：TestPlan360R1Equivalence 4 条（解码缓存同输入同结果/非法类名每次拒绝/未知类 typed/filter 属性变更刷新）；TestPlan360KeyCacheInvalidation 2 条（RocksDB 前缀缓存 key/namespace 切换失效）
- [x] `./mvnw test`（core/cep/rocksdb 等）全绿不少于基线
- [x] **无静默跳过**：缓存失效路径显式处理
- [x] 序列化/存储/指标语义不变（等价性说明见 evidence 与测试）
- [x] **端到端验证**：现有 e2e 全绿
- [x] No owner-doc update required；daily log 已更新

### Phase 3 - 优化轮 R2/R3：中风险状态与传输路径

Status: completed
Targets: WindowOperator/MergingWindowSet、SharedBuffer、RocksDBAggregatingState、RemoteResultPartition、InputGate、CepOperator

- Item Types: `Fix`

- [x] R2-JFR：flushCache 占 SharedBufferRegister ~20% 样本（jfr-SharedBufferRegister*.jfr），推翻"<2%"初判 → 实施 R3
- [x] SharedBuffer key-scoped 缓存（getAccessor(key)：缓存键 (key,id) 复合 ScopedId、scoped close 免清缓存、legacy no-arg 路径行为不变仍 flush；state 键保持 raw EventId/NodeId 不进 checkpoint 格式；驱逐日志 DEBUG→TRACE——驱逐风暴下 DEBUG 为可测量成本）；配套 TestSharedBufferKeyScopedCache 4 条（真实 MemoryKeyedStateBackend 双键隔离）
- [x] RocksDBAggregatingState.add 前向缓存 accumulator（TTL 旁路、clear/applyMigration 失效、写穿保留）：local -20~-23% 保留
- [x] MergingWindowSet 按 key 复用——**实测 SESSION +9.4% 回退（memory 后端 state.get 为引用返回，重建成本低），按留舍纪律 revert**（r2c 方差验证排除噪声；窗口测试 64 条全绿确认恢复）
- [x] RemoteResultPartition 锁收窄 / InputGate 非阻塞轮询 / CepOperator getSortedTimestamps+bufferEvent——**裁定不实施**（无基准覆盖→无 ≥2% 实测证据；或涉及状态布局兼容），逐项理由见 convergence.md
- [x] R2/R3 测量：r2/r2b/r2c/final/final2 五轮对比（r2c 方差验证）

Exit Criteria:

- [x] R2/R3 保留项：RocksDB 聚合前向缓存（-20~-23%）、SharedBuffer key-scoped（-41.3% vs 基线）；revert 1 项（MergingWindowSet）；不实施 3 项有据
- [x] R2/R3 focused 测试：SharedBuffer 跨键隔离 4 条、RocksDB 聚合缓存 clear 失效回归 2 条（见下 Closure——审计发现的 clear 作用域缺陷已修复+补测）
- [x] **端到端验证**：窗口/CEP/checkpoint 恢复/remote 传输现有测试全绿
- [x] **接线验证**：SharedBuffer scoped accessor 被 CepOperator 两处运行时调用（getAccessor(getCurrentKey())）；基准 NfaProcess/SharedBufferRegister 同路径镜像
- [x] `./mvnw test` 全绿（cep 366 含新 4 条；rocksdb 119 含新 4 条）
- [x] No owner-doc update required；daily log 已更新

### Phase 4 - 收敛裁定与停止

Status: completed
Targets: evidence 目录、daily log

- Item Types: `Proof`

- [x] 最终 JFR 录制（jfr-final-nfa20 等 6 份）：剩余热点为算法本体+guava LocalCache 机制+ScopedId/EventId equals（合计分散），无单点 ≥2% 低风险可收割项
- [x] 下一条候选实测验证（结项审计 F4 要求的实测留舍）：ScopedId hashCode 预计算 → NFA d20 实测 15502→14949ns（**-3.6% ≥2%，保留**）——该轮证明实测驱动循环持续有效；其余候选（ScopedId.equals 本体、EventId.equals、LocalCache 机制替换）为 key-scoped 设计固有成本或需换缓存实现（高风险），预估不构成 ≥2% 低风险项
- [x] 收敛裁定书 convergence.md（各轮台账+JFR 归因+剩余候选归属）

Exit Criteria:

- [x] 停止判据成立：convergence.md（JFR+实测双证据；第 4 轮实测保留项后剩余候选全部为固有成本/Deferred）
- [x] 全部轮次对比表入 evidence（baseline→r1→r2/r2b/r2c→final2，含留舍与 revert）
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

- [x] nop-benchmark-stream 基准资产落地且可复现（结项审计实跑 MemoryKeyedStateBench 与 final-table 一致：7.9ns/24B）
- [x] 多轮优化有 ≥2% 实测收益保留（9 项保留，-16~-83%）且收敛裁定证明无可收割项
- [x] 所有保留优化项语义等价（focused 等价性测试 + 既有测试全绿；格式/指标语义保持）
- [x] 全部留舍判定有测量证据（9 保留项有对比数据；revert 1 项；不实施 3 项有据）
- [x] 不存在被静默降级的 in-scope live defect（结项审计发现 R3 引入的 clear() 失效作用域缺陷已修复+补 2 条回归测试；详见 Closure）
- [x] `docs-for-ai/01-repo-map/module-groups.md` 已同步新模块
- [x] 独立子 agent closure-audit 已完成并记录证据（含停止判据复核）
- [x] **Anti-Hollow Check**：基准调用真实生产路径（audit 对照 NfaProcessBench→NFA.process、CodecBench→StreamElementCodec）；缓存失效路径连通（三份 focused 测试实跑 10/10）
- [x] `./mvnw compile -pl nop-benchmark/nop-benchmark-stream` 通过
- [x] `./mvnw test` 七模块全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### CEP NFAState 增量持久化 / SharedBuffer guava LocalCache 实现替换（Caffeine/自研）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 结项审计 F3 实测归因：guava LocalCache 机制合计约 40% 样本（connectAccessOrder 单点 17.1%）为剩余最大成本中心——替换缓存实现属热点治理正确方向，但需换库/自研+驱逐语义重设计+exactly-once 回归，超出本计划"低风险单点"边界
- Successor Required: `yes`（按需立专项计划）

### RocksDBListState O(n) 追加 / RocksDB merge operator

- Classification: `optimization candidate`（实测 25µs@100/112µs@1000，确定性 >2%）
- Why Not Blocking Closure: 需列表分片存储或 merge operator——状态格式变更（plan Non-Goals），涉及 checkpoint 兼容设计
- Successor Required: `yes`

### 二进制 envelope 编码替代双层 JSON wire 格式

- Classification: `optimization candidate`
- Why Not Blocking Closure: 跨 JVM 兼容契约，需版本协商设计（decodeOnly 中 JSON parse 占主体，收益上限可观）
- Successor Required: `yes`

## Non-Blocking Follow-ups

- RemoteResultPartition.write 锁收窄、InputGate 非阻塞轮询、CepOperator getSortedTimestamps/bufferEvent 索引化（实施前需先建对应基准——本轮已登记"无测量不实施"纪律）
- NFA 每状态分配池化、EventId.equals（key-scoped 设计固有成本，watch-only）
- CheckpointSerDe byte[] 免 base64、BufferPool 批量许可、ProcessingTimeServiceDriver sleep-to-deadline

## Closure

Status Note: 基准资产落地（8 类/14 方法，审计实测可复现）；四轮优化（R1 六项、R2 一项、R3 一项+日志降级、审计驱动一项）共 10 项保留、1 项 revert、3 项有据不实施；final vs 基线 -16~-83% 零回退；JFR 归因证明剩余热点为算法本体与缓存机制，无单点 ≥2% 低风险可收割项。结项审计（agent_0812cece）驳回两轮问题（F1 clear() 失效作用域 live defect、F2 计划文件被并行会话覆盖）已全部修复：F1 修复+2 条回归测试（clear→add 从 createAccumulator 起步）、F2 本文件重建为真实 completed 文本；F3 归因叙事已按实测更正（guava 机制 ~40% 显式登记 Deferred）；F4 补做第 4 轮实测（ScopedId hash 预计算 -3.6% 保留）；F5 偏差已记录（白名单校验保持每调用执行，更保守）。
Completed: 2026-09-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_0812cece-47c0-4a04-807d-603eaf1dd1d7；首轮 REJECT 6 项发现）
- Audit Session: agent_0812cece-47c0-4a04-807d-603eaf1dd1d7
- Evidence:
  - Gate 停止判据：审计 PASS（JFR 资产 6 份可打开；listAdd Deferred 归属与 CheckpointSerDe 方差判定抽查可信）；F3 归因叙事已按审计实测更正并登记缓存实现替换候选
  - Anti-Hollow：审计 PASS（基准非替身；缓存失效路径连通；focused 测试 10/10 实跑绿）
  - 留舍诚实性：审计 PASS（MergingWindowSet revert 真实无残留；3 项不实施裁定合理）
  - 语义等价：审计 PASS（state 键保持 raw；Micrometer 等价；手写 hash null 安全）
  - F1 修复：RocksDBAggregatingState.clear() 无条件 invalidateAccumulatorCache() + TestPlan360AggregatingCacheClearInvalidation 2 条回归（clear→add 从 0 起步断言，可判别旧缺陷）
  - F2 修复：本计划文件重建（并行会话误写覆盖后的恢复），全部 in-scope 项勾选、Deferred 镜像完整
  - F4 修复：第 4 轮实测（ScopedId hash 预计算，NFA d20 -3.6% 保留）
  - 门禁：七模块测试全绿（core 1596 / runtime 1079 / cep 366 / rocksdb 119 / flow 118 / connector 69 / jdbc 43）；checklist --strict 退出码 0；scan-hollow high 退出码 0
  - 停止判据复核（第二轮）：第 4 轮保留项后，剩余候选=固有成本（ScopedId/EventId equals）或 Deferred（LocalCache 替换、格式/协议变更）——无 ≥2% 低风险项

Follow-up:

- Deferred But Adjudicated 三项 + Non-Blocking Follow-ups 所列（含 3 项"先建基准再实施"项）
