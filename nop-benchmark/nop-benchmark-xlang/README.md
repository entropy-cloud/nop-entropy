# nop-benchmark-xlang — 三后端执行基准（I12）

解释器 / java 生成类 / truffle 三后端执行性能基准（xlang-execution-optimization roadmap I12），
JMH + `main()` runner 接线（`nop-benchmark-xpl` 先例同构）。

## 用例组

| 组 | 类 | 内容 |
|---|---|---|
| ① 静态三向对比 | `StaticThreeWayBenchmark` | e2e main classpath 生产形态 46 单元耗时批（解释器显式旁路 / java 生产绑定 choke point / truffle 池租借稳态），三列 setup 期身份核验 |
| ② 动态对比 | `DynamicCompareBenchmark` | 自带 10 单元动态语料（truffle 经 choke point 真实路由 + 池运行时稳态 vs 解释器直驱旁路） |
| ③ 池成本与梯度 | `ContextPoolBenchmark` | `XLangContextPool.open(size)` 创建/销毁成本 + 租借求值稳态（单线程 / 4 线程）× 池大小 1/2/4/8/16 |
| ④ 缓存敏感性 | `TranslationCacheBenchmark` | 64 键工作集扫（命中 vs LRU 淘汰再翻译）× 容量 16/64/256/1024/4096 + 翻译单成本（全 miss） |

语料与身份核验口径：静态语料 = `nop-xlang-java-e2e` 物化 corpus + e2e 原生单元（52 xpl，
耗时裁选 46——排除 6 异常单元与 3 xlib 标签，显式记录于 `StaticCorpus`）；三列身份
（java = 确定性生成类入口 / truffle = 翻译 AST 经 CallTarget / 解释器 = 非 bound 执行体）
setup 期断言——环境或前置缺失显式失败（JMH error），无静默跳过。

## 复跑入口

```bash
# 构建基准模块（在 reactor 内）
./mvnw clean install -DskipTests -pl :nop-benchmark-xlang -am -T 1C

# 全量默认运行（四组用例；结果 JSON 落当前目录）
./mvnw exec:java -pl :nop-benchmark-xlang -Dexec.mainClass=io.nop.benchmark.xlang.XlangBackendBenchmarks

# JMH CLI 形态（正则选择 + 参数覆盖；示例：仅池梯度 + gc profiler）
./mvnw exec:java -pl :nop-benchmark-xlang -Dexec.mainClass=io.nop.benchmark.xlang.XlangBackendBenchmarks \
  -Dexec.args="ContextPoolBenchmark -prof gc"
```

> 运行时实际 JDK vendor/version 请记录入报告（stock JVM 形态以运行时为准）；GraalVM JVM
> 形态：`GRAALVM_HOME` 指向 GraalVM JDK 后以同一入口运行（truffle JIT 生效形态）。

## 产物

原始 JMH 结果与汇总分析落 `ai-dev/analysis/2026-08/2026-08-21-xlang-backend-benchmark.md`
（环境 / 参数 / 原始数据 / 结论 / Q1-Q4 量化口径专章）。
