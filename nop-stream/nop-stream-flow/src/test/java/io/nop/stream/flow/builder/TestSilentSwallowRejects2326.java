/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 plan 2326 pins: previously-silently-swallowed DSL declarations must fail
 * loud at build time.
 *
 * <ul>
 *   <li><b>FLOW-02</b>: an edge INTO a source transform was accepted by
 *       validateDag and then silently ignored by buildSource (which never
 *       consumes upstreamIds) — the declared dependency vanished. Reverting the
 *       source-target check in buildTransforms turns
 *       {@code edgeIntoSourceFailsLoud} red.</li>
 * </ul>
 */
class TestSilentSwallowRejects2326 {

    static {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    private static StreamModel parseInline(String body) {
        String xml = "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "name=\"flow02-pin\" version=\"1\" parallelism=\"1\">"
                + body
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    void edgeIntoSourceFailsLoud() {
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver resolver =
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("sinkFn", new io.nop.stream.core.common.functions.sink.PrintSinkFunction<String>());
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"ok\" from=\"src\" to=\"out\"/>"
                        // the violation: an edge INTO the source
                        + "<edge id=\"bad\" from=\"out\" to=\"src\"/>"
                        + "</edges>");

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolver).build());
        // The contract is FAIL-LOUD (no silent swallow). The dedicated FLOW-02
        // message fires when the graph is otherwise acyclic; when the
        // edge-into-source also closes a cycle, the cycle check (which runs
        // first by adjudicated precedence) rejects with cyclic/unreachable —
        // either way the declaration can no longer be silently ignored.
        String detail = String.valueOf(ex.getParam("detail"));
        assertTrue(detail.contains("targets source transform")
                        || detail.contains("cycle or unreachable"),
                () -> "edge-into-source must be typed-rejected (FLOW-02 or cycle): " + detail);
    }
}
