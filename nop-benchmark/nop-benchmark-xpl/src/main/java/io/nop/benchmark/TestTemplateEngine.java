/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark;

import io.nop.benchmark.tpl.FreemarkerBenchmark;
import io.nop.benchmark.tpl.PebbleBenchmark;
import io.nop.benchmark.tpl.RockerBenchmark;
import io.nop.benchmark.tpl.XplBenchmark;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark Mode Cnt Score Error Units FreemarkerBenchmark.benchmark thrpt 50 15453.624 ± 634.468 ops/s
 * PebbleBenchmark.benchmark thrpt 50 36390.545 ± 1636.851 ops/s RockerBenchmark.benchmark thrpt 50 78852.508 ± 3955.661
 * ops/s XplBenchmark.benchmark thrpt 50 41189.185 ± 2080.406 ops/s
 */
public class TestTemplateEngine {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(FreemarkerBenchmark.class.getSimpleName())
                .include(PebbleBenchmark.class.getSimpleName()).include(XplBenchmark.class.getSimpleName())
                .include(RockerBenchmark.class.getSimpleName()).result("result.json")
                .resultFormat(ResultFormatType.JSON).build();
        new Runner(opt).run();
    }
}
