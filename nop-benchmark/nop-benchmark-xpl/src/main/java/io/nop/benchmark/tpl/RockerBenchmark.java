/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.benchmark.tpl;

import freemarker.template.TemplateException;
import io.nop.benchmark.BaseBenchmark;
import io.nop.benchmark.model.Stock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import java.io.IOException;
import java.util.List;

/**
 * Benchmark for Rocker template engine by Fizzed.
 * <p>
 * https://github.com/fizzed/rocker
 *
 * @author joelauer
 */
public class RockerBenchmark extends BaseBenchmark {

    private List<Stock> items;

    @Setup
    public void setup() throws IOException {
        // no config needed, replicate stocks from context
        this.items = Stock.dummyItems();
    }

    @Benchmark
    public String benchmark() throws TemplateException, IOException {
        return templates.stocks.template(this.items).render().toString();
    }

}
