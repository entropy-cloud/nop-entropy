/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.tpl;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.nop.benchmark.BaseBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public class PebbleBenchmark extends BaseBenchmark {

    private Map<String, Object> context;

    private PebbleTemplate template;

    @Setup
    public void setup() throws PebbleException {
        PebbleEngine engine = new PebbleEngine.Builder().autoEscaping(false).build();
        template = engine.getTemplate("templates/stocks.pebble.html");
        this.context = getContext();
    }

    @Benchmark
    public String benchmark() throws PebbleException, IOException {
        StringWriter writer = new StringWriter();
        template.evaluate(writer, context);
        return writer.toString();
    }

}
