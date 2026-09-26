# nop-stream-perf-360 收敛裁定书（Phase 4）

> Date: 2026-09-26
> 停止判据：连续一轮无任何 ≥2% 可收割候选（JFR 归因 + 实测验证双证据）

## 一、最终成绩（final2 vs baseline，同机同配置）

| 基准 | 基线 | 最终 | Δ% | alloc 基线→最终 |
|---|---|---|---|---|
| MemoryKeyedState.valueGetUpdate(local) | 43.0ns | 15.6ns | **-63.8%** | 264→24B |
| MemoryKeyedState.aggregatingAdd(local) | 44.4ns | 7.6ns | **-82.8%** | 216→24B |
| MemoryKeyedState value/aggr(rotate) | 98-102ns | 72-76ns | -23~-29% | 237-285→117B |
| NfaProcess.processEvent(d=1/5/20) | 702/5834/25554ns | 527/3672/15502ns | **-25/-37/-39%** | d20: 65828→45042B |
| RocksDbKeyedState.valueGetUpdate(local) | 2873-2926ns | 2427-2438ns | -15~-17% | 2281-2537→1000-1048B |
| RocksDbKeyedState.aggregatingAdd(local) | 2744-2901ns | 2203-2225ns | -20~-23% | 2145-2305→1672B |
| SharedBufferRegister.registerEventAndPut | 1452ns | 853ns | **-41.3%** | 3986→2541B |
| StreamElementCodec.decodeOnly/fullRoundTrip | 3597/5102ns | 2529/3903ns | -30/-24% | （不变，JSON 本体） |
| TimerService.register/advance | 100/132ns | 78/102ns | -22~-23% | 436→380B |
| WindowOperator TUMBLING/SLIDING/SESSION/EVICTOR | 195/1100/2188/343ns | 144/632/1845/241ns | **-16~-42%** | 全线下降 |
| CheckpointSerDe.serialize/deserialize | 10489/9363µs | 7636/7907µs | -27/-16%* | （*未改动该代码，判定为环境/JIT 方差，不计入成果） |
| RocksDbKeyedState.listAdd（NEW 参数化） | — | 25µs@100 / 112µs@1000 | — | O(n) 特征保持（见 Deferred） |

全部保留优化项均 ≥2% 实测收益；零回退（所有未触碰基准在 ±3% 噪声带内）。

## 二、各轮留舍台账

| 轮 | 候选 | 结果 | 证据 |
|---|---|---|---|
| R1 | valueType→Class 解码缓存+白名单前置 | 保留（decodeOnly -28%） | r1-raw |
| R1 | JEP290 filter 缓存（按属性值键控） | 保留（CEP 路径） | r1-raw |
| R1 | RocksDB (key,ns)→byte[] 前缀缓存 | 保留（value local -15%） | r1-raw |
| R1 | Memory TypedNamespaceAndKey 复用+手写 hash | 保留（local -61~-83%） | r1-raw |
| R1 | EventId/NodeId/TimerEntry 手写 hash | 保留（NFA d20 -38%、SharedBuffer -45%、Timer -27%） | r1-raw |
| R1 | Micrometer record(long,TimeUnit) | 保留（Duration 分配消除） | r1-raw |
| R2 | RocksDB 聚合前向缓存 accumulator | 保留（aggregating local -20~-23%） | r2b-raw |
| R2 | MergingWindowSet 按 key 复用 | **revert**（SESSION +9.4% 回退：memory 后端 state.get 为引用返回，重建成本低于缓存同步；r2c 方差验证排除机器噪声） | r2/r2b/r2c-raw |
| R2 | RemoteResultPartition 锁收窄 | 裁定不实施（无传输基准覆盖，无 ≥2% 证据） | — |
| R2 | InputGate 非阻塞轮询 | 裁定不实施（伤延迟非吞吐，无对应基准；涉及 retry 控流风险） | — |
| R2 | CepOperator getSortedTimestamps/bufferEvent | 裁定不实施（CepOperator 层无基准覆盖；状态布局兼容问题） | — |
| R3 | SharedBuffer key-scoped 缓存（getAccessor(key)） | 保留（-41.3%；R2-JFR 推翻"<2%"初判后实施） | final2-raw + jfr-r3 |
| R3 | SharedBuffer 驱逐日志 DEBUG→TRACE | 保留（驱逐风暴下 DEBUG 日志为可测量成本） | jfr-r3-sharedbuffer |

## 三、最终 JFR 归因（jfr-final-nfa20.jfr，剩余最热基准；结项审计 F3 修正版）

1359 个 ExecutionSample 的构成（审计实测）：NFA 状态机本体（doProcess/computeNextStates/process/addComputationState/handleTake|IgnoreEdge）约占一半；**guava LocalCache 机制（connectAccessOrder/segment 操作等）合计约 40% 样本，其中 connectAccessOrder 单点 top-frame 17.1%——剩余最大成本中心**；ScopedId/EventId.equals 合计 125 样本 = **9.2%**（key-scoped 设计与身份比较的固有成本）。据此登记新候选："SharedBuffer 缓存实现替换（guava→Caffeine/自研）"为 Deferred（见台账第四节补充），替换属热点治理正确方向但需驱逐语义重设计+exactly-once 回归，非低风险单点。

第 4 轮（结项审计 F4 驱动的实测留舍）：ScopedId hashCode 预计算 → NFA d20 实测 15502→14949ns（**-3.6% ≥2%，保留**）。此后剩余候选：ScopedId.equals 本体与 EventId.equals 为 (key,id) 复合键语义的固有比较成本（不可消除）；LocalCache 替换为 Deferred——**无 ≥2% 低风险可收割项，停止判据成立。**

## 四、剩余候选与归属

| 候选 | 预估收益 | 不继续原因 | 归属 |
|---|---|---|---|
| RocksDBListState O(n) 追加（25µs@100/112µs@1000 实测） | >2%（确定性） | 需列表分片存储或 merge operator——状态格式变更，plan Non-Goals | Deferred But Adjudicated（既有登记） |
| CEP NFAState 增量持久化 | 不确定 | 协议重设计+exactly-once 回归 | Deferred But Adjudicated（既有登记） |
| 二进制 envelope wire 编码 | >2%（decodeOnly 中 JSON 占主体） | 跨 JVM 兼容契约，需版本协商设计 | Deferred But Adjudicated（既有登记） |
| NFA 每状态分配池化 | <2%~5%（分散无单点） | Flink 移植算法核心重构，风险高 | watch-only（本裁定书登记） |
| SharedBuffer 缓存实现替换（guava→Caffeine/自研，connectAccessOrder 17.1% 单点） | >2%（可能显著） | 需换库/自研+驱逐语义重设计+exactly-once 回归 | Deferred（结项审计 F3 登记） |
| ScopedId hashCode 预计算 | 实测 -3.6% | **第 4 轮已实施保留**（结项审计 F4 驱动） | final2 后单测记录 |

## 五、停止裁定

满足停止判据：剩余候选或属 Deferred（格式/协议变更，已登记 successor 条件），或预估 <2%。JFR 归因（jfr-final-nfa20.jfr 等 5 份录制）与三轮实测（r1/r2/r2b/r2c/final2）双重佐证。
