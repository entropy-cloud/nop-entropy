/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 */
package io.nop.stream.runtime.wi21;

import io.nop.stream.core.common.functions.source.SourceFunction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WI21 build-stability fixture: emits two keyed records (payload is a Map so the
 * XLang key expression {@code event.key} resolves). Build-only — the pipeline is
 * never executed in the stability test.
 */
public class Wi21SourceFunction implements SourceFunction<Map<String, String>> {

    private static final long serialVersionUID = 1L;

    private final String prefix;

    public Wi21SourceFunction(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void run(SourceContext<Map<String, String>> ctx) throws Exception {
        for (String k : new String[]{"k1", "k2"}) {
            Map<String, String> rec = new LinkedHashMap<>();
            rec.put("key", k);
            rec.put("val", prefix + ":" + k);
            ctx.collect(rec);
        }
    }

    @Override
    public void cancel() {
    }
}
