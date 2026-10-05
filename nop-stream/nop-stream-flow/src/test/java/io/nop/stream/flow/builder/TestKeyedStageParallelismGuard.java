/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 FLOW-5 regression pin: a keyed stage declared with a parallelism that
 * differs from its upstream routing parallelism fails at BUILD time — FORWARD
 * routing would concentrate all records on subtask 0 and a rescale would split
 * keyed state from traffic.
 */
class TestKeyedStageParallelismGuard {

    static {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    private static StreamModel parseInline(String body) {
        String xml = "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "name=\"flow5-guard\" version=\"1\" parallelism=\"1\">"
                + body
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    public void keyedStageParallelismMismatchFailsAtBuild() {
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver resolver =
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("mapFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x);
        resolver.register("mapFn2", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x);
        // sink via a second map + terminal sink bean is unnecessary — the guard
        // fires during edge assembly, before any downstream bean is resolved
        resolver.register("sinkFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x);
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"agg\" name=\"agg\" bean=\"mapFn\" parallelism=\"4\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"e1\" from=\"src\" to=\"agg\" partition=\"HASH\" keyExpr=\"userId\"/>"
                        + "<edge id=\"e2\" from=\"agg\" to=\"out\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolver).build());
        assertTrue(String.valueOf(ex.getParam("detail")).contains("R5 FLOW-5")
                        || String.valueOf(ex.getMessage()).contains("parallelism"),
                () -> "keyed-stage parallelism mismatch must fail at build: " + ex.getMessage());
    }
}
