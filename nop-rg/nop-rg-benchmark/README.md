# nop-rg-benchmark

JMH 基准模块（plan 2265 JMH-01..05）。基准数据仅代表运行机器环境（非性能承诺口径）。

## 运行方式

**不要使用 `mvn exec:java`**（JMH fork 的子 JVM 无法从 Maven classworlds classpath 启动，实测 ClassNotFoundException）。

```bash
# 1. 编译并导出 classpath
./mvnw -pl nop-rg/nop-rg-benchmark compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -q

# 2. 运行（cwd 必须是 nop-rg/nop-rg-benchmark/）
cd nop-rg/nop-rg-benchmark
java -cp target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main "ScalarSearchBenchmark" -f 1 -wi 2 -i 5 -w 1s -r 1s
```

## 双档参数

- **迭代档**（筛选候选，快）：`-f 1 -wi 2 -i 5 -w 1s -r 1s`
- **收尾档**（判定与吞吐比裁定，稳）：`-f 3 -wi 3 -i 5 -w 1s -r 1s`

判定协议见 `ai-dev/plans/2265-nop-rg-wave3-performance.md` 迭代记录表：
保留条件 = 收益 ≥ max(2%, 3×σ_run) 且 JMH 误差棒不重叠；σ_run 为同版本基线收尾档连跑 ≥2 次的最大相对偏离。

## 基准清单

| 基准 | 内容 | roadmap |
| --- | --- | --- |
| ScalarSearchBenchmark | BMH 标量搜索吞吐（1MB/64MB × 命中/未命中） | JMH-02 |
| GlobBenchmark | 单 glob 与 GlobMatcher 集合匹配吞吐 | JMH-03 |
| CoordinatorEndToEndBenchmark | coordinator 全链路（1MB/64MB/512MB corpus，16 分片） | JMH-04 |
| RgCompareBenchmark | rg 子进程 `-c` 端到端对比（同 corpus；spawn 开销计入） | JMH-05 |
| VectorCompareBenchmark | 标量 vs Vector（SPI 发现；孵化模块缺失时降级并打印警示） | design 测试策略表"标量 vs Vector"行 |

实测记录（macOS arm64 / JDK 26 / 128-bit species；plan 2267 R1 场景矩阵化，收尾档）：
Vector 相对标量吞吐（`scalarScan`/`vectorScan`）：

| 场景 | 标量 | 向量 | 向量/标量 |
| --- | --- | --- | --- |
| sparse-short-6B（needle，~1/6 行） | 1.27 ops/ms | 1.01 | 80%——劣化 |
| dense-short-6B（~1/2 行） | 1.34 | 0.97 | 72%——劣化 |
| sparse-mid-16B | 3.48 | 12.03 | **3.5x** |
| dense-mid-16B | 3.25 | 8.08 | **2.5x** |
| sparse-long-32B | 6.60 | 12.52 | **1.9x** |
| dense-long-32B | 5.72 | 9.15 | **1.6x** |

e2e 配对判定（CoordinatorEndToEndBenchmark long-32B 64MB，共测 3 对交替）：VECTOR 全部胜出
（+4.4%/+5.2%/+21.6%，中位 +5.2%）。据此 plan 2267 R1 落地 **provider 长度阈值策略**
（`SIMD_MIN_PATTERN_LENGTH = 16`）：短于阈值返回标量等价（避免 --vector 回退短模式性能），
8-15B 段未测保守归标量。`--vector` 的 gate 是正确性与降级行为；长模式的 SIMD 收益已可实测。

corpus：固定种子伪随机文本行（逐字节可再生），存放于 `$TMPDIR/nop-rg-bench-corpus/`，构建一次复用。
不同场景（needle/long）使用独立子目录——`CorpusUtil.ensureFile` 复用键只有 path+size，不含内容指纹。

## 收敛循环结论（plan 2267）

优化迭代循环以共测配对协议（基线/候选背靠背 3 对交替）执行至严格终止（连续两轮无候选通过保留条件）：

- **R1 保留**：SIMD 模式长度阈值策略（`SIMD_MIN_PATTERN_LENGTH=16`，长模式 e2e 配对中位 +5.2%）。
- R2/R3/R4 回退：SWAR LF 扫描（64MB/512MB 中位 -0.20%/-0.35%，候选更慢）、span-gap 行计数（-2.43%/+0.50%，方向不一致）、count 口径单遍融合扫描（+0.99%/+2.23% 名义但 3σ 离散度≫2% 且 CI 重叠）——三项独立证据表明 count 口径 e2e 为带宽/缓存行为受限，指令级与遍数级优化均落在负载噪声地板（±8%）之下。
- 终态热点（count 口径）：indexOf 48.9%（逐字节循环即地板）、matchesAt 25.3%、aggregate 8.9%——碎片化，无 ≥2% 可实现候选。
- 吞吐比收尾：64MB 82.3%；512MB 六次中位 49.05%（本机外部负载下测量，代码与 2265 测得 51.3% 时行为等价——详见 plan 2267 记录表裁定行）。
- 判定 JSON 证据：仓库 `_tmp/nop-rg-bench/`（临时目录，可再生）。
