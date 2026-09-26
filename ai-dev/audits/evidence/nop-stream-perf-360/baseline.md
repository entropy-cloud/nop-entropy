# nop-stream-perf-360 基线（Phase 1）

> Date: 2026-09-26
> Env: macOS arm64, JDK 21, JMH 1.33, fork=1, warmup 3x2s, measurement 5x2s, -prof gc
> 配置说明：Memory/RocksDB 状态基准当前为 key 轮转模式（rotate，最坏情形）；R1 前补 local（同 key 连续，常态路径）访问模式后以 v2 口径对比缓存类优化。
> 命令：`java -cp target/classes:$(cat _tmp/cp-stream.txt) org.openjdk.jmh.Main ".*" -f 1 -wi 3 -w 2s -i 5 -r 2s -prof gc`
> 原始输出：`_tmp/nop-stream-perf/baseline-raw.txt`（未入库，本表为完整提炼）

| Benchmark | params | score | error | unit | alloc B/op |
|---|---|---|---|---|---|
| CheckpointSerDeBench.deserialize | — | 9.363 | ±1.644 | ms/op | 31799001.844 |
| CheckpointSerDeBench.serialize | — | 10.489 | ±1.765 | ms/op | 39982429.904 |
| MemoryKeyedStateBench.aggregatingAdd | — | 121.564 | ±34.184 | ns/op | 237.021 |
| MemoryKeyedStateBench.valueGetUpdate | — | 110.636 | ±16.631 | ns/op | 285.027 |
| NfaProcessBench.processEvent | patternDepth=1 | 0.702 | ±0.201 | us/op | 65828.409 |
| NfaProcessBench.processEvent | patternDepth=5 | 5.834 | ±2.138 | us/op | 65828.409 |
| NfaProcessBench.processEvent | patternDepth=20 | 25.554 | ±6.551 | us/op | 65828.409 |
| RocksDbKeyedStateBench.aggregatingAdd | listPreload=100 | 5.379 | ±1.125 | us/op | 2373.878 |
| RocksDbKeyedStateBench.aggregatingAdd | listPreload=1000 | 5.449 | ±0.900 | us/op | 2373.878 |
| RocksDbKeyedStateBench.listAdd | listPreload=100 | 34.210 | ±26.933 | us/op | 1082382.131 |
| RocksDbKeyedStateBench.listAdd | listPreload=1000 | 117.979 | ±27.866 | us/op | 1082382.131 |
| RocksDbKeyedStateBench.valueGetUpdate | listPreload=100 | 4.572 | ±1.305 | us/op | 2573.634 |
| RocksDbKeyedStateBench.valueGetUpdate | listPreload=1000 | 5.192 | ±1.742 | us/op | 2573.634 |
| SharedBufferRegisterBench.registerEventAndPut | — | 1.452 | ±0.240 | us/op | 3985.770 |
| StreamElementCodecRoundTripBench.decodeOnly | — | 3.597 | ±0.489 | us/op | 5160.465 |
| StreamElementCodecRoundTripBench.fullRoundTrip | — | 5.102 | ±0.655 | us/op | 8312.742 |
| TimerServiceBench.advanceWatermark | keyCount=1000 | 132.343 | ±7.941 | ns/op | 500.943 |
| TimerServiceBench.registerTimer | keyCount=1000 | 99.868 | ±16.610 | ns/op | 435.636 |
| WindowOperatorProcessElementBench.processElement | windowType=TUMBLING | 0.195 | ±0.033 | us/op | 922.841 |
| WindowOperatorProcessElementBench.processElement | windowType=SLIDING | 1.100 | ±0.151 | us/op | 922.841 |
| WindowOperatorProcessElementBench.processElement | windowType=SESSION | 2.188 | ±0.208 | us/op | 922.841 |
| WindowOperatorProcessElementBench.processElement | windowType=EVICTOR | 0.343 | ±0.048 | us/op | 922.841 |
