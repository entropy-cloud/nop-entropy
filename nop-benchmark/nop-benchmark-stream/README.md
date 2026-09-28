# nop-benchmark-stream

nop-stream 热路径 JMH 基准（plan 360 建；plan 2279 补传输/算子路径）。JMH 1.33，与父 pom 配置一致。

## 基准集（13 类 / 20+ 方法）

| 基准类 | 被测生产路径 |
|---|---|
| StreamElementCodecRoundTripBench | StreamElementCodec encode/decode + KafkaStringWireCodec toWire/fromWire（远程边逐记录编解码） |
| RocksDbKeyedStateBench | RocksDBKeyedStateBackend value/aggregating/list 状态读写（`accessPattern`=local 同键连续 / rotate 轮转；`listPreload`=列表基线长度） |
| MemoryKeyedStateBench | MemoryKeyedStateBackend value/aggregating/internalList（accessPattern 同上；internalListAdd=WindowOperator 内部窗口内容路径，plan 01 quality-perf F2 口径） |
| WindowOperatorProcessElementBench | WindowOperator.processElement + processWatermark（TUMBLING/SLIDING/SESSION/EVICTOR） |
| TimerServiceBench | HeapInternalTimerService registerEventTimeTimer / advanceWatermark |
| NfaProcessBench | NFA.process / advanceTime（`patternDepth`=1/5/20；`conditionCost`=cheap/billable——billable 档为 PROCEED 双重评估候选的测量口径，plan 01 quality-perf E3） |
| SharedBufferRegisterBench | SharedBufferAccessor registerEvent + put（key-scoped 缓存路径） |
| CheckpointSerDeBench | CheckpointSerDe serializeEpochManifest / deserialize（10k keyed + 1MB bytes）；`deserializeNoChecksum`=读侧 checksum off 档（fixture 剥离，legacy 容忍路径） |
| RemoteTransportWriteBench | RemoteResultPartition.write 锁内 encode+同步 send（`sendDelayNanos`=0/100µs/5ms 后端延迟桩，`fanout`=1/4/16；`writerHeartbeatSamePartition` 组=写/心跳同 monitor 互卡形状） |
| CepOperatorBench | CepOperator.processElement 缓冲 RMW + processWatermark timer 批 drain（`backend`=MEMORY/ROCKSDB × `keys`=1/64 × `bucketsPerKey`=8/64） |
| InputGateReadLoopBench | InputGate 读取循环（`gapNanos`=0/100µs/10ms 生产间隔；饱和档量吞吐，大 gap 档若 @Group 栅栏失真按计划退化为独立 harness） |
| BufferPoolPermitBench | BufferPool acquire/release（`poolCapacity`=1 强制乒乓/64 近畅通；真实 ResultPartition/InputChannel 路径） |
| ProcessingTimeDriverLatencyBench | ProcessingTimeServiceDriver 处理时间定时器触发延迟（`tickMs`=100/20；SampleTime，fire 延迟 = op − 50ms 提前量） |

## 运行

```bash
# 全量（fork=1，warmup 3x2s，measurement 5x2s，含分配率）
./mvnw -q install -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-rocksdb -DskipTests
./mvnw -q compile -pl nop-benchmark/nop-benchmark-stream
CP=nop-benchmark/nop-benchmark-stream/_tmp/cp-stream.txt   # 由依赖构建生成，或 mvn dependency:build-classpath 自行生成
java -cp nop-benchmark/nop-benchmark-stream/target/classes:$(cat $CP) org.openjdk.jmh.Main ".*" -f 1 -wi 3 -w 2s -i 5 -r 2s -prof gc | tee _tmp/nop-stream-perf/raw.txt

# 单项 + JFR 录制
java -cp ... org.openjdk.jmh.Main "NfaProcessBench.processEvent" -p patternDepth=20 \
  -jvmArgsAppend "-XX:StartFlightRecording=filename=_tmp/nop-stream-perf/rec.jfr,settings=profile,dumponexit=true"
jfr print --events jdk.ExecutionSample _tmp/nop-stream-perf/rec.jfr   # CPU 归因
```

原始输出（`*-raw.txt`、`*.jfr`）一律写入 `_tmp/nop-stream-perf/`（已被 `.gitignore` 忽略，不入库）；`ai-dev/audits/evidence/` 只落提炼后的对比表与裁定文档。

历史测量证据：`ai-dev/audits/evidence/nop-stream-perf-360/`（baseline / r1 / r2 / final 各轮对比与收敛裁定）、`ai-dev/audits/evidence/nop-stream-perf-2279/`（plan 2279 传输/算子路径基线与各轮对比）。
