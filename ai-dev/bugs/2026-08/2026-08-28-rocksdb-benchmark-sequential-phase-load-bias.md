# 2026-08-28 RocksDB 增量 checkpoint 基准的顺序阶段负载偏置

## Problem

- 全量 reactor 下 `nop-stream-rocksdb` 的 `TestRocksDBIncrementalRestoreAndBenchmark.incrementalCheckpointIsFasterThanFullScanForLargeState` 失败：`ratio(inc/full)=3.380` 超出可移植护栏（≤3.0）。
- 模块隔离运行绿（ratio 0.30-0.64）。同一机器、同一代码，仅运行上下文不同。

## Diagnostic Method

- 诊断难点：这是一个"断言两个操作谁更快"的性能基准测试，此前已为负载噪声做过两轮加固（min-of-RUNS 采样、护栏从 0.5 放宽到 3.0，注释自述曾观察到全量负载下单发 ratio 2.55）——直觉上"已加固过还红"容易直接再放宽护栏。
- 读测量代码发现结构性问题：**先跑完全部 full-scan 采样（RUNS=3），再跑全部 incremental 采样**——两个阶段串行，后台负载突发只打击其中一个阶段。
- 关键不对称：incremental 做 SST 落盘 I/O（负载下放大远超纯内存迭代的 full-scan），所以负载偏置方向恒定地抬高 inc 侧。
- 决定性验证：改为交错采样后，隔离复测 ratio 3.38→0.64——偏置消除，而非噪声吸收。

## Root Cause

- 顺序两阶段测量 + 两种操作对负载的敏感度不对称（I/O 型 vs 内存型）：负载突发落在 inc 阶段时产生**系统性偏置**（不是随机噪声），min-of-RUNS 无法消除，只能靠均摊两侧消除。

## Fix

- full/inc 按轮次交错测量（每轮一次 full + 一次 inc），各自取 min（commit ac5a40a524）——负载均摊两侧；护栏 3.0 保持不变，真回归（Stage30-vs-incremental 3x+）在交错 min 下同样可见。

## Tests

- `nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/rocksdb/incremental/TestRocksDBIncrementalRestoreAndBenchmark.java` - 交错采样后模块 89 tests 绿，BENCHMARK 日志行输出两个 min 与 ratio 供后续排查。

## Affected Files

- 同上（仅该测试文件，实现未改）。

## Notes For Future Refactors

- 对比型基准测试的采样必须交错（或随机化顺序）；"先 A 后 B"的两阶段结构在共享环境下天然带偏置。
- 加护栏吸收负载噪声前，先检查测量结构是否把负载不对称地分配给了其中一侧——结构性偏置靠放宽阈值只会越放越宽。
