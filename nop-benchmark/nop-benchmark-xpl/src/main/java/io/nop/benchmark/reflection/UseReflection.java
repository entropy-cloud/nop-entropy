/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.benchmark.reflection;

import io.nop.benchmark.BaseBenchmark;
import io.nop.benchmark.model.Stock;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import java.util.List;

public class UseReflection extends BaseBenchmark {
    List<Stock> stocks;

    @Setup
    public void init() {
        ReflectionManager.instance().clearCache();
        ReflectionManager.instance().clearInvokers();
        stocks = Stock.dummyItems();
    }

    @Benchmark
    public void benchmark() {
        for (Stock stock : stocks) {
            BeanTool.getComplexProperty(stock, "price");
        }
    }
}
