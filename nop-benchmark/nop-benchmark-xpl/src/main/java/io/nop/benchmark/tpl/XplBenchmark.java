/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.benchmark.tpl;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.benchmark.BaseBenchmark;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import java.util.Map;

public class XplBenchmark extends BaseBenchmark {
    private Map<String, Object> context;

    private ExprEvalAction template;

    @Setup
    public void setup() {
        // initInvokers();

        IResource resource = new ClassPathResource("classpath:templates/stocks.xpl");
        template = XLang.parseXpl(resource);
        this.context = getContext();
    }

    @Benchmark
    public String benchmark() {
        IEvalScope scope = XLang.newEvalScope(context);
        return template.generateText(scope);
    }

    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);

        XplBenchmark benchmark = new XplBenchmark();
        benchmark.setup();
        String output = benchmark.benchmark();
        System.out.println(output);
    }
}
