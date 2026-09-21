# nop-lint 性能基线（perf baseline）

> 日期: 2026-09-21 · 依据: ai-dev/plans/nop-lint/05-jmh-benchmark-baseline.md（roadmap item 13）
> 性质: 单机单次测量锚点，非统计严格结论；一切后续性能声称以本文数字为对照基线

## 环境

| 项 | 值 |
|---|---|
| JDK | OpenJDK 26.0.1 Zulu 26.30+11-CRaC-CA（arm64） |
| OS | macOS (darwin 25.6.0, Apple Silicon) |
| JMH | 1.33（fork 1，warmup 3×1s，measurement 5×1s，gc profiler） |
| ast-grep | 0.43.0（Homebrew，Rust 实现） |
| 语料 | 固定内嵌 Java 编译单元（~60 行，`BenchCorpus.JAVA_SOURCE` = `bench/corpus/OrderService.java`） |

## 基准结果（JMH，avgt，5 iterations）

| Benchmark | Score | 说明 |
|---|---|---|
| `compileRuleSet` | ≈ 0.1 ms/op | 3 条旗舰规则编译（规则加载冷路径；注：类声明规则在语料中零命中，其遍历成本仍计入） |
| `matchAllPatterns` | ≈ 1 ms/op（±0.001） | 已编译规则集 ×1 棵语法树全量匹配（热路径） |
| `parseAndMatch` | ≈ 1 ms/op（±0.001） | parse + 3 规则匹配（端到端单文件口径） |
| `parseAndMatch` alloc | 1.30 MB/op（分配速率 644 ± 483 MB/s，高噪声指标） | 门面子树包装与 children 列表物化 |

口径提示：1s measurement 下 ±0.001s 的误差粒度较粗，数字为量级锚点；复测时建议加大 iterations。

**目标口径对照（design 11 §6）**：编辑器 fast profile 预算 <50ms/文件——当前端到端 ≈1ms/文件，余量充足（~50×）。CI 分钟级口径同样满足。

## ast-grep 同规则对比

| 工具 | 口径 | 200 文件耗时 |
|---|---|---|
| nop-lint | warm JVM，parse+3 规则匹配/文件（`parseAndMatch` ×200 换算） | ≈ 0.2 s |
| ast-grep | 冷进程 + 规则加载 + 扫描 + 输出（`/usr/bin/time`） | ≈ 0.01 s（real 0.01） |

**口径不对称声明**：sg 计时含进程启动/规则解析/输出；nop-lint 为稳态 warm JVM 单文件口径，未含 JVM 启动（CI 场景应按进程口径另行测量）。两口径不可直接判优；本对比的目的：
1. 同一规则在 ast-grep 语义下命中数一致（200 文件 × 2 throws = **400 matches**，nop-lint M1 验收同规则命中结构一致）——**语义对齐验证**；
2. 建立后续（编辑器常驻进程、批量 CI）双口径对比的锚点。

复现：`bash nop-lint/bench/compare-ast-grep.sh`（脚本内置匹配数>0 断言，防零匹配空转计时）。

## JFR 热点（matchAllPatterns，60×1s 稳态录制，4972 ExecutionSample）

录制方式：`-jvmArgsAppend "-XX:StartFlightRecording=settings=profile,dumponexit=true"`（**必须注入 JMH forked JVM**——录制挂在 runner 父进程时采不到基准代码，首次录制即犯此错）。

| 帧 | 占比 |
|---|---|
| `TreeNavigator.locateInto` | 39.98% |
| `TSTreeCursor.resetTo` | 13.33% |
| `TreeNavigator.flattenedIndexOf` | 11.71% |
| `TreeNavigator.isChain` | 10.28% |
| `TSTreeCursor.pushFrame` | 10.28% |
| `SubtreeArena.checkLive` | 7.38% |
| nop-lint 帧（NodeIterator.next 0.86% + collect 0.54% 等） | ≈ 1.5% |

**结论**：≈93% 时间消耗在 nop-treesitter cursor 机制——每次 `LintNode.children()` 经 `node.cursor()` 触发 `resetTo` 全祖先链重建 + 线性子定位（nop-treesitter javadoc 已声明该成本形态）。nop-lint 匹配内核自身占比极低。

**优化候选（记录，不在本 plan 实施）**：
1. `TreeSitterLintNode.children()` 结果按 (tree,id) 缓存或匹配期内复用（消除重复 cursor 重建）；
2. 匹配期单游标下推遍历替代逐节点 children() 物化；
3. 分配热点（1.3MB/op）随 1/2 项自然下降。

复现 JFR：
```bash
CP="nop-lint/nop-lint-core/target/test-classes:nop-lint/nop-lint-core/target/classes:$(cat nop-lint/nop-lint-core/target/bench.cp)"
java -cp "$CP" org.openjdk.jmh.Main LintBenchmarks.matchAllPatterns -wi 5 -w 1s -i 60 -r 1s -f 1 \
  -jvmArgsAppend "-XX:StartFlightRecording=settings=profile,dumponexit=true,filename=$(pwd)/_tmp/matcher.jfr"
jfr view hot-methods _tmp/matcher.jfr
```

## 全量复现

```bash
bash nop-lint/bench/run-benchmarks.sh     # JMH 三基准 → _tmp/lint-bench-result.txt
bash nop-lint/bench/compare-ast-grep.sh   # ast-grep 同规则对比
./mvnw -pl nop-lint/nop-lint-core test    # BenchmarkSmokeTest 防基准腐坏
```
