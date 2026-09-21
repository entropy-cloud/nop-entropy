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

判定协议见 `ai-dev/plans/2265-nop-rg-wave3-performance.md` 迭代记录表与 `ai-dev/plans/2273-nop-rg-deep-audit-quality-perf.md`（2267 修订版共测配对：基线 = m2 快照 jar、候选 = target/classes，交替 3 对，判定基准 = 受影响口径 e2e + 其 σ）：
保留条件 = 收益 ≥ max(2%, 3×σ_pair) 且 JMH 误差棒不重叠。

## 基准清单

| 基准 | 内容 | roadmap |
| --- | --- | --- |
| ScalarSearchBenchmark | BMH 标量搜索吞吐（1MB/64MB × 命中/未命中） | JMH-02 |
| GlobBenchmark | 单 glob 与 GlobMatcher 集合匹配吞吐 | JMH-03 |
| CoordinatorEndToEndBenchmark | coordinator 全链路（1MB/64MB/512MB corpus，16 分片；plan 2273 增 `mode=count/text` 口径与 `many-small` 512×128KB 场景——运行时用 `-p` 钉定，如 `-p mode=text`、`-p scenario=many-small -p size=64MB -p mode=count`） | JMH-04 |
| RgCompareBenchmark | rg 子进程 `-c` 端到端对比（同 corpus；spawn 开销计入） | JMH-05 |
| VectorCompareBenchmark | 标量 vs Vector（SPI 发现；孵化模块缺失时降级并打印警示；plan 2273 增 12B 探测场景） | design 测试策略表"标量 vs Vector"行 |

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

plan 2273 Phase 2 补测（12B，迭代档，--add-modules）：sparse-mid-12B 标量 3.04 vs 向量 3.11 ops/ms
（+2.0% 名义但 CI 重叠）、dense-mid-12B 3.24 vs 3.21（-1.1%）——无 ≥2% 组件级胜出，
**维持 16B 阈值**。plan 2273 新增口径基线（收尾档，本机含外部负载 opencode 100% CPU）：
TEXT 口径 1MB 131.7 / 64MB 2.15 / 512MB 0.269 ops/s——**此为 R1 优化前的 autoflush 口径历史值**；
many-small（512×128KB）count 33.37 ± 0.80 ops/s
（对比 16×4MB 同总量 ~45-50 ops/s，每文件开销面可见）。HotspotProfiler 支持第三参数
`text` 切 TEXT 口径（ExecutionSample 对 native write 系统调用不可见，输出候选须以 JMH 配对判定）。

plan 2275 Phase 2 基线重建（收尾档，2026-09-22，R1 缓冲 + R5 跳子匹配解码现行口径，
本机含外部负载 load 7.5-12）：TEXT 口径（mode=text）1MB 577.9 / 64MB 12.09 / 512MB 1.568 ops/s；
count 口径（needle-6B）1MB 788.8-821.0 / 64MB 43.6-55.0 / 512MB 11.70-12.07 ops/s（双跑区间）；
many-small（512×128KB）count 31.82 ± 3.14 ops/s。row0 profile：
count = indexOf 46.6% + readByte 27.1%（扫描带宽地板，与 2267/2273 终态同构）；
text = BufferedWriter.write 29.9%（输出必需）+ indexOf 14.0% + ResultPrinter 行拼接 11.4%；
many-small = 行扫描 ~76% + matchesAt 19.3% + isBinary 1.9%。

corpus：固定种子伪随机文本行（逐字节可再生），存放于 `$TMPDIR/nop-rg-bench-corpus/`，构建一次复用。
不同场景（needle/long）使用独立子目录——`CorpusUtil.ensureFile` 复用键只有 path+size，不含内容指纹。

> 运维提示：cp.txt 中的 nop-rg-core 等依赖解析自本地 m2 仓库快照——core 改动后须先
> `./mvnw install -pl nop-rg/nop-rg-core[,nop-rg/nop-rg-cli,nop-rg/nop-rg-vector] -DskipTests`
> 刷新快照，否则 fork JVM 用旧 jar（plan 2268 Phase 4 实测：类移动后表现为 fork 内
> ClassNotFoundException）。

## 收敛循环结论（plan 2273 第二轮）

用户口径（TEXT 默认输出与多小文件场景）上重新执行严格收敛循环（共测配对 + 字面终止）：

- **R1 保留**：TEXT 输出缓冲——CLI autoflush 逐行 flush 改 64KB BufferedOutputStream 收尾 flush，
  TEXT e2e 中位 +387%/+403%（~5x，3/3 对方向一致，CI 零重叠）；字节序列不变（rg 同为缓冲输出）。
- **R5 保留**：TEXT 口径跳过子匹配文本解码（`SearchCommand.includeSubmatchText=false`，
  CLI TEXT 模式采用，`--json` 恒全量）；TEXT e2e 中位 +8.9%/+13.7%（6/6 对正向）。
- R3 回退：≤1MB 文件堆读替代 mmap——配对三对方向不一致（+30%/+35% CI 重叠、-15.2% 离散为负），
  eager 复制在 16 线程下不敌惰性缺页；R4 实现前否决（double-stat 消除算术上界 ≤1.5% < 2%）；
  R2 同行 memo 零触发面否决（corpus 每命中行恰 1 命中）。
- 12B SIMD 探测：无 ≥2% 组件级胜出（稀疏名义 +2.0% CI 重叠、密集 -1.1%）——维持 16B 阈值。
- 终态 profile：count 与 2267 终态同构（indexOf 42.3% + matchesAt 32.1%，扫描带宽地板）；
  text 剩余热点均为输出/扫描契约必需——循环字面终止（R3✗R4✗ 连续窗口 + 候选池枯竭）。
- 吞吐比收尾：64MB Coord 49.6 vs rg 80.4 = **61.7%**；512MB Coord 12.83 vs rg 23.07 = **55.7%**——双档 ≥50%，
  并优于 2267 的 512MB 49.05%（本日含残余负载 load ~8 仍达成，2267 watch-only 复测 follow-up 清偿）。
- 判定 JSON 证据：`_tmp/nop-rg-bench/p2273-*.json|log`（临时目录，可再生）。

## 收敛循环结论（plan 2267）

优化迭代循环以共测配对协议（基线/候选背靠背 3 对交替）执行至严格终止（连续两轮无候选通过保留条件）：

- **R1 保留**：SIMD 模式长度阈值策略（`SIMD_MIN_PATTERN_LENGTH=16`，长模式 e2e 配对中位 +5.2%）。
- R2/R3/R4 回退：SWAR LF 扫描（64MB/512MB 中位 -0.20%/-0.35%，候选更慢）、span-gap 行计数（-2.43%/+0.50%，方向不一致）、count 口径单遍融合扫描（+0.99%/+2.23% 名义但 3σ 离散度≫2% 且 CI 重叠）——三项独立证据表明 count 口径 e2e 为带宽/缓存行为受限，指令级与遍数级优化均落在负载噪声地板（±8%）之下。
- 终态热点（count 口径）：indexOf 48.9%（逐字节循环即地板）、matchesAt 25.3%、aggregate 8.9%——碎片化，无 ≥2% 可实现候选。
- 吞吐比收尾：64MB 82.3%；512MB 六次中位 49.05%（本机外部负载下测量，代码与 2265 测得 51.3% 时行为等价——详见 plan 2267 记录表裁定行）。
- 判定 JSON 证据：仓库 `_tmp/nop-rg-bench/`（临时目录，可再生）。
