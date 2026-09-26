# nop-benchmark-stream

nop-stream 热路径 JMH 基准（plan 360）。JMH 1.33，与父 pom 配置一致。

## 基准集（8 类 / 14 方法）

| 基准类 | 被测生产路径 |
|---|---|
| StreamElementCodecRoundTripBench | StreamElementCodec encode/decode + KafkaStringWireCodec toWire/fromWire（远程边逐记录编解码） |
| RocksDbKeyedStateBench | RocksDBKeyedStateBackend value/aggregating/list 状态读写（`accessPattern`=local 同键连续 / rotate 轮转；`listPreload`=列表基线长度） |
| MemoryKeyedStateBench | MemoryKeyedStateBackend value/aggregating（accessPattern 同上） |
| WindowOperatorProcessElementBench | WindowOperator.processElement + processWatermark（TUMBLING/SLIDING/SESSION/EVICTOR） |
| TimerServiceBench | HeapInternalTimerService registerEventTimeTimer / advanceWatermark |
| NfaProcessBench | NFA.process / advanceTime（`patternDepth`=1/5/20） |
| SharedBufferRegisterBench | SharedBufferAccessor registerEvent + put（key-scoped 缓存路径） |
| CheckpointSerDeBench | CheckpointSerDe serializeEpochManifest / deserialize（10k keyed + 1MB bytes） |

## 运行

```bash
# 全量（fork=1，warmup 3x2s，measurement 5x2s，含分配率）
./mvnw -q install -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-rocksdb -DskipTests
./mvnw -q compile -pl nop-benchmark/nop-benchmark-stream
CP=nop-benchmark/nop-benchmark-stream/_tmp/cp-stream.txt   # 由依赖构建生成，或 mvn dependency:build-classpath 自行生成
java -cp nop-benchmark/nop-benchmark-stream/target/classes:$(cat $CP) org.openjdk.jmh.Main ".*" -f 1 -wi 3 -w 2s -i 5 -r 2s -prof gc

# 单项 + JFR 录制
java -cp ... org.openjdk.jmh.Main "NfaProcessBench.processEvent" -p patternDepth=20 \
  -jvmArgsAppend "-XX:StartFlightRecording=filename=rec.jfr,settings=profile,dumponexit=true"
jfr print --events jdk.ExecutionSample rec.jfr   # CPU 归因
```

历史测量证据：`ai-dev/audits/evidence/nop-stream-perf-360/`（baseline / r1 / r2 / final 各轮对比与收敛裁定）。
