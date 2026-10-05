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
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 FLOW-2 regression pin: a transform unreachable from any sink fails at
 * build time (pre-fix it was silently dropped from the compiled pipeline).
 * Models without any sink (partial wiring) skip the check.
 */
class TestSinkReachabilityGuard {

    @BeforeAll
    static void init() {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    private static StreamModel parseInline(String body) {
        String xml = "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "name=\"flow2-guard\" version=\"1\" parallelism=\"1\">"
                + body
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    public void unreachableTransformFailsAtBuild() {
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"orphan\" name=\"orphan\" bean=\"mapFn\"/>"
                        + "<map id=\"mid\" name=\"mid\" bean=\"mapFn\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"e1\" from=\"src\" to=\"mid\"/>"
                        + "<edge id=\"e2\" from=\"mid\" to=\"out\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertTrue(String.valueOf(ex.getParam("detail")).contains("not reachable from any sink"),
                () -> "unreachable transform must fail with FLOW-2 detail: " + ex.getParam("detail"));
    }

    @Test
    public void connectedModelDoesNotRaiseReachabilityError() {
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"mid\" name=\"mid\" bean=\"mapFn\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"e1\" from=\"src\" to=\"mid\"/>"
                        + "<edge id=\"e2\" from=\"mid\" to=\"out\"/>"
                        + "</edges>");
        try {
            StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()
                    .register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction())
                    .register("mapFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x)
                    .register("sinkFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x))
                    .build();
        } catch (StreamException e) {
            if (String.valueOf(e.getParam("detail")).contains("not reachable from any sink")) {
                throw new AssertionError("connected model must not raise FLOW-2 reachability error", e);
            }
            // other build errors (bean wiring shapes) are out of scope for this pin
        }
    }
}
