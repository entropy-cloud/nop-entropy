/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark;

import io.nop.benchmark.reflection.UseInvoker;
import io.nop.benchmark.reflection.UseReflection;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark Mode Cnt Score Error Units UseInvoker.benchmark thrpt 50 529837.331 ± 31980.090 ops/s
 * UseReflection.benchmark thrpt 50 496200.353 ± 23465.191 ops/s
 */
public class TestReflection {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(UseReflection.class.getSimpleName())
                .include(UseInvoker.class.getSimpleName()).result("result.json").resultFormat(ResultFormatType.JSON)
                .build();
        new Runner(opt).run();
    }
}
