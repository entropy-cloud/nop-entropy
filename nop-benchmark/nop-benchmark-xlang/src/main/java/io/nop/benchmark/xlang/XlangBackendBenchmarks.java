/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * 三后端执行基准入口（I12 Phase 2，main() JMH runner——nop-benchmark-xpl
 * `TestTemplateEngine` 先例同构接线）。
 *
 * <p>无参运行 = 全部四组用例（①静态三向 ②动态对比 ③池成本与梯度 ④缓存敏感性），结果落
 * `xlang-backend-benchmark-result.json`；带参运行 = 委托 JMH CLI（正则选择 + `-wi/-i/-f/-p/-prof`
 * 覆盖——如 `-prof gc` 采样分配压力，Q1/Q4 锚点数据的补充面）。复跑命令见模块 README。
 */
public class XlangBackendBenchmarks {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            org.openjdk.jmh.Main.main(args);
            return;
        }
        Options opt = new OptionsBuilder()
                .include(StaticThreeWayBenchmark.class.getSimpleName())
                .include(DynamicCompareBenchmark.class.getSimpleName())
                .include(ContextPoolBenchmark.class.getSimpleName())
                .include(TranslationCacheBenchmark.class.getSimpleName())
                .result("xlang-backend-benchmark-result.json")
                .resultFormat(ResultFormatType.JSON)
                .build();
        new Runner(opt).run();
    }
}
