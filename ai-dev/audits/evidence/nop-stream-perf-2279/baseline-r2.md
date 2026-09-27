# nop-stream-perf-2279 基线（Phase 1）

> Date: 2026-09-27
> Machine: macOS arm64（与 plan 360 同机）；JDK 26.0.1 Zulu；JMH 1.33
> 口径：`-f 1 -wi 3 -w 2s -i 5 -r 2s -prof gc`（Driver 为 SampleTime）；单机前后对比
> Raw: `_tmp/nop-stream-perf-r2/baseline-new-raw.txt`（主采集）+ `baseline-new-rocksdb-raw.txt`（CepOperator ROCKSDB 档尝试）
> 代码基线：HEAD 718026a3e0（2277 已提交；2278 Phase 1-3 在工作区，均不影响本表被测路径——2278 仅拆分/搬移/注释，Phase 4 完成后将以 WindowOperator/Timer 两基准复测确认无回退）

## 新基准基线（5 类全参数矩阵）

### BufferPoolPermitBench（ns/op）
| 配置 | acquire/release 无竞争 | pingPong（组） |
|---|---|---|
| capacity=1（强制乒乓） | 9.16 ± 0.05 | **5995 ± 3164**（producer/consumer 各≈5995） |
| capacity=64（近畅通） | 9.04 ± 0.30 | 156.6 ± 51.4 |

→ 公平信号量无竞争 ~9ns；挤压边（capacity=1）每次许可传递 ~6µs（AQS park/unpark 握手主导）——批量许可候选的目标场景。畅通边 157ns，证实"畅通边预期 <2%"预判。

### CepOperatorBench（µs/op，eventTimeBufferDrain = 1 processElement + 1 processWatermark）
| keys | bucketsPerKey | MEMORY |
|---|---|---|
| 1 | 8 | 1.272 ± 0.208 |
| 1 | 64 | 1.770 ± 0.658 |
| 64 | 8 | 2.149 ± 0.365 |
| 64 | 64 | 3.199 ± 1.023 |

→ 随 keys×buckets 线性放大（watermark 每 tick 全台账深拷贝 + 全桶 keys() 扫描的成本特征）。ROCKSDB 档被上游缺陷阻塞（见 06-cep-rocksdb-gap.md），Q1 以 MEMORY 档实测。

### InputGateReadLoopBench（µs/op，组=producer+consumer）
| gapNanos | readLoop |
|---|---|
| 0（饱和） | 0.117 ± 0.064 |
| 100µs | 154.9 ± 0.8（≈ gap + 组栅栏开销） |
| 10ms | 14308 ± 413（≈ gap，预期内失真形态） |

→ 饱和吞吐 117ns/op 且与空闲参数无关——证实"信号驱动 poll 下饱和流零成本"；大 gap 档 per-op 时间被等待支配（计划预期的 @Group 失真形态，已授权退化为独立 harness；空闲成本项按量化关闭处理）。

### ProcessingTimeDriverLatencyBench（SampleTime，ms/op；fire 延迟 = op − 50ms 提前量）
| tickMs | p50 | p99 | p1.00 |
|---|---|---|---|
| 100 | 109.97（→ 延迟 ~60ms） | 148.1 | 148.1 |
| 20 | 58.59（→ 延迟 ~8.6ms） | 82.9 | 94.5 |

→ 触发延迟 ≈ tick/2 + mailbox 延迟，与机制分析一致。sleep-to-deadline 候选的收益口径：延迟（非吞吐）。

### RemoteTransportWriteBench（µs/op）
| 方法 | fanout | delay=0 | delay=100µs | delay=5ms |
|---|---|---|---|---|
| writeSingle | 1/4/16 | 42/43/42 ns | 154.2/154.5/154.2 µs | 7.21/7.33/7.31 ms |
| writeBroadcast | 1 | 44 ns | 154.3 µs | 7.34 ms |
| writeBroadcast | 4 | 156 ns | 615.9 µs | 29.40 ms |
| writeBroadcast | 16 | 615 ns | 2461 µs | 118.0 ms |
| writerHeartbeat 组 | 1 | 0.122（writer 0.165/heartbeat 0.078） | 4212±14840（高方差） | 1.06M±5.36M（极端方差） |
| writerHeartbeat 组 | 4 | 0.204 | 43838±363369 | 0.90M±5.13M |
| writerHeartbeat 组 | 16 | 0.322 | — | — |

→ 关键读数：(a) **锁内同步 send = 每 op 完整后端延迟**（5ms 档 7.3ms/op，broadcast ×fanout 线性）；(b) **同分区心跳互卡**：delay=0 时 writer 165ns vs 无竞争 42ns（monitor 竞争本身 4×），慢后端下方差爆炸（心跳线程与 writer 交替持锁 5ms）；(c) broadcast 的 T 次重复 encode 在 delay=0 档可见（156ns vs 4×43=172ns——encode 份额小，真实大 payload 下放大）。

### WindowOperatorProcessElementBench（扩展参数：backend × evictorSize）
TUMBLING/SLIDING/SESSION/EVICTOR × MEMORY/ROCKSDB × evictorSize 100/1000——**在 2278 Phase 4 搬移完成后、jar 重装后与 Q1 优化同批采集**（F2 evictor O(n²) 的 ROCKSDB 档是本轮新覆盖点；避免在搬移前采集两套不可比基线）。

### CheckpointSerDeBench（扩展：读侧 checksum on/off）
与上同批采集（写入侧 checksum 无法参数化关闭，写侧份额走 JFR 帧归因——F1）。

## 方法论

- 命令模板（单项 + JFR）：
  ```bash
  java -cp nop-benchmark/nop-benchmark-stream/target/classes:$(cat nop-benchmark/nop-benchmark-stream/_tmp/cp-stream.txt) \
    org.openjdk.jmh.Main "<regexp>" -f 1 -wi 3 -w 2s -i 5 -r 2s -prof gc \
    -jvmArgsAppend "-XX:StartFlightRecording=filename=rec.jfr,settings=profile,dumponexit=true"
  ```
- 方差协议（计划钉死）：每候选 ≥3 重复或误差条重叠判定；2-5% 边界重跑一轮；未触碰基准噪声带 ±3%；ms 级重量级基准（CheckpointSerDe）不以单轮定留舍
- 过程发现：BenchCepEvent 需 @DataBean + io.nop.stream.bench 包（RocksDB JSON 序列化与 ClassNameValidator 白名单）；由此实测确认 **CEP+RocksDB 组合不可用缺陷**（见 audit 06-cep-rocksdb-gap.md，successor 正确性专项承接）

## Q1 前置归因（jfr-q1-cep-before.jfr，CepOperatorBench MEMORY/keys=64/buckets=64，1523 样本）

| 帧 | 样本 | 占比 |
|---|---|---|
| CepOperator.processWatermark | 564 | 37.0% |
| **CepOperator.snapshotTimersByKey** | **404** | **26.5%** |
| ├ TreeMap.buildFromSorted | 234 | 15.4% |
| └ TreeMap.successor | 116 | 7.6% |
| MemoryMapState.getMap + TypedNamespaceAndKey.equals | ~150 | ~10% |
| NFA.process/doProcess（算法本体，平凡模式） | ~115 | ~7.5% |
| MemoryMapState.put | 49 | 3.2% |

→ snapshotTimersByKey 的 per-key TreeSet 深拷贝是 watermark drain 的主导成本（26.5%），远超 2% 留舍线；ledger 驱动 drain + 浅拷贝消除为 Q1 主项。复测该配置 2.743±0.083µs（基线 3.199±1.023 方差带内）。
