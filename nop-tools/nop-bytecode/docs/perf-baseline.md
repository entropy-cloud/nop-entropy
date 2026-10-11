# nop-bytecode 内核性能基线（perf baseline）

> 日期: 2026-09-29 · 依据: ai-dev/plans/nop-bytecode/03-kernel-cfg-dataflow-nullflow.md（roadmap item 4）
> 性质: 单机单次测量锚点，非统计严格结论；一切后续性能声称以本文数字为对照基线
> 口径声明: 数字为 **item 6 正式口径**（IFNULL/IFNONNULL + ACMP-null 精化 + 断言恒启用建模，2026-09-29 重测；降配口径历史值 runNullflow=0.732ms 已被本次替换——两口径在合成语料上无可测差异）

## 环境

| 项 | 值 |
|---|---|
| JDK | OpenJDK 26.0.1, Zulu（arm64） |
| OS | macOS (darwin 25.6.0, Apple Silicon) |
| JMH | 1.33（avgt，fork 1，warmup 3×1s，measurement 5×1s） |
| 语料 | `CorpusGenerator` 确定性合成：**20 类 × 15 方法 = 300 方法**（混合形状：分支/循环/try-catch/字段访问/虚+静态+接口调用/TABLESWITCH+LOOKUPSWITCH/INVOKEDYNAMIC 字符串拼接）；语料形状正确性由 oracle 形状复核测试覆盖 |

## 基准结果（JMH，avgt，5 iterations，2026-09-29 实测）

| Benchmark | Score | 说明 |
|---|---|---|
| `parseCorpus` | 0.091 ± 0.012 ms/op | ClassReader accept 全语料（解析层） |
| `buildCfg` | 0.091 ± 0.027 ms/op | 全语料方法内 CFG 构建 |
| `solveDataflowOnly` | 0.394 ± 0.090 ms/op | CFG + nullness 抽象解释（语料已预解析——隔离 solver 成本） |
| `runNullflow` | 0.715 ± 0.115 ms/op | 端到端：解析 + CFG + 抽象解释 + 命中收集（正式口径重测值；降配口径历史值 0.732 ± 0.189） |

**CI 分钟级预算换算**：端到端 ≈ 0.73 ms / 20 类 ≈ 37 µs/类——千类级模块 ≈ 40 ms，万类级 ≈ 0.4 s；本通道定位 CI/构建期档位（分钟级预算）余量充足（≥3 个数量级）。注意语料为固定 20 类合成形状，真实模块的方法体更大，数字应按量级锚点而非线性外推消费。

**复现命令**（JMH test 依赖，fork 1 的 forked JVM 从 java.class.path 取 classpath——`exec:java` 不可用，nop-lint 同款两段式）：

```bash
./mvnw test-compile -pl nop-bytecode
./mvnw -pl nop-bytecode dependency:build-classpath -Dmdep.outputFile=_tmp/nop-bytecode-bench/test-classpath.txt -Dmdep.includeScope=test
java -cp "nop-bytecode/target/classes:nop-bytecode/target/test-classes:$(cat _tmp/nop-bytecode-bench/test-classpath.txt)" \
  org.openjdk.jmh.Main -f 1 -wi 3 -i 5 -tu ms -bm avgt NullflowKernelBenchmark
```

原始输出本地留档 `_tmp/nop-bytecode-bench/jmh-run2.log`（gitignore，不入 git；蒸馏数字即本文）。

## 与内核正确性的关系

语料与方法级正确性由测试保证（`OracleShapeAuditTest` 双语料 oracle 复核 + `ToyNullflowSemanticsTest` 语义锁 + `UnhandledOpcodeTest` 响亮失败）；本文件只承载性能锚点，不承载能力宣称。
