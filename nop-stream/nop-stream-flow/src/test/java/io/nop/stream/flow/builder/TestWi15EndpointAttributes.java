/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI15 (plan 2361): the endpoint consistencyCapability declared-fidelity contract.
 *
 * <p>Four pins per endpoint face (source/sink):
 * <ol>
 *   <li>VALUE exemption — a parse of an XML model WITHOUT the attribute (the xdef
 *       default AT_LEAST_ONCE is force-injected at parse time, so the model value is
 *       never null) builds fine against a default-capability bean: the exemption is
 *       BY VALUE, not by "attribute absent" (which is unobservable).</li>
 *   <li>LEGAL MATCH — a NON-default declaration matching the bean's actual
 *       capability builds. This is the anti-degeneration pin: an implementation that
 *       rejects every non-default declaration would fail here.</li>
 *   <li>DECLARED-FIDELITY reject — a non-default declaration diverging from the
 *       bean → ERR_STREAM_INVALID_ARG naming both values (the engine validates
 *       connector consistency against the BEAN, so a diverging declaration would be
 *       inert).</li>
 *   <li>xpl-form reject — the generated wrappers always provide the default
 *       capability, so any non-default declaration on an xpl endpoint rejects.</li>
 * </ol>
 * The maintained rejections (params/maxParallelism/outputType·inputType) keep their
 * contract pins in {@code TestStreamFlowAuditFixes} (messages now carry the WI15
 * adjudication rationale).
 */
public class TestWi15EndpointAttributes {

    private static final String SOURCE_BEAN = "wi15Src";
    private static final String SINK_BEAN = "wi15Sink";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static InMemoryBeanFunctionResolver baseResolver() {
        return new InMemoryBeanFunctionResolver()
                .register(SOURCE_BEAN, new io.nop.stream.flow.testing.TestSourceFunction())
                .register(SINK_BEAN, new io.nop.stream.flow.testing.CollectingSinkFunction<>());
    }

    private static StreamModel parseXml(String xml) {
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    private static String sourceXml(String capabilityAttr) {
        String cap = capabilityAttr == null ? "" : (" consistencyCapability=\"" + capabilityAttr + "\"");
        return "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi15-src\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"" + SOURCE_BEAN + "\"" + cap + "/>"
                + "<sink id=\"out\" bean=\"" + SINK_BEAN + "\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
    }

    private static String sinkXml(String capabilityAttr) {
        String cap = capabilityAttr == null ? "" : (" consistencyCapability=\"" + capabilityAttr + "\"");
        return "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi15-sink\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"" + SOURCE_BEAN + "\"/>"
                + "<sink id=\"out\" bean=\"" + SINK_BEAN + "\"" + cap + "/>"
                + "</transforms>"
                + "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
    }

    // ---------------- source face ----------------

    @Test
    public void sourceValueExemptionOmittedAttributeBuilds() {
        // XML parse path: the attribute is OMITTED — the xdef default AT_LEAST_ONCE
        // is force-injected, and the value-exemption must let the build through even
        // though the plain bean's actual capability is BEST_EFFORT (the recorded
        // fidelity gap, see the plan Deferred section).
        StreamModel model = parseXml(sourceXml(null));
        assertNotNull(StreamModelDslBuilder.of(model, baseResolver()).build(),
                "value-exemption (AT_LEAST_ONCE injected) must let the default-capability bean build");
    }

    @Test
    public void sourceNonDefaultMatchingDeclarationIsNotImplementedYet() {
        // The plain test bean provides BEST_EFFORT; the only non-default source
        // capability is REPLAYABLE — a matching bean is not available in this test
        // fixture, so the legal-match pin for the source face is covered by the
        // sink face below (IDEMPOTENT-capable sink bean). Here we pin the honest
        // declared-fidelity reject for the non-matching non-default declaration.
        StreamModel model = parseXml(sourceXml("REPLAYABLE"));
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("REPLAYABLE") && ex.getMessage().contains("BEST_EFFORT"),
                () -> "the reject must name both the declared and the actual capability: "
                        + ex.getMessage());
    }

    // ---------------- sink face ----------------

    @Test
    public void sinkValueExemptionOmittedAttributeBuilds() {
        StreamModel model = parseXml(sinkXml(null));
        assertNotNull(StreamModelDslBuilder.of(model, baseResolver()).build(),
                "value-exemption (AT_LEAST_ONCE injected) must let the default-capability sink build");
    }

    /**
     * A sink bean that truthfully provides IDEMPOTENT semantics — the legal-match
     * pin's bean counterpart.
     */
    public static class IdempotentSink
            implements io.nop.stream.core.common.functions.SinkFunction<Object> {
        @Override
        public void consume(Object value) {
        }

        @Override
        public io.nop.stream.core.common.functions.sink.SinkConsistencyCapability getSinkConsistency() {
            return io.nop.stream.core.common.functions.sink.SinkConsistencyCapability.IDEMPOTENT;
        }
    }

    @Test
    public void sinkNonDefaultMatchingDeclarationBuilds() {
        // THE ANTI-DEGENERATION PIN: a non-default declaration (IDEMPOTENT) on a bean
        // that actually provides IDEMPOTENT must BUILD — an implementation that
        // rejects every non-default declaration fails here.
        StreamModel model = parseXml(sinkXml("IDEMPOTENT"));
        InMemoryBeanFunctionResolver resolver = baseResolver()
                .register(SINK_BEAN, new IdempotentSink());
        assertNotNull(StreamModelDslBuilder.of(model, resolver).build(),
                "a non-default declaration matching the bean's actual capability must build");
    }

    @Test
    public void sinkNonDefaultDivergingDeclarationRejects() {
        StreamModel model = parseXml(sinkXml("IDEMPOTENT"));
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("IDEMPOTENT") && ex.getMessage().contains("AT_LEAST_ONCE"),
                () -> "the reject must name both the declared and the actual capability: "
                        + ex.getMessage());
    }

    @Test
    public void sinkXplFormNonDefaultDeclarationRejects() {
        // The generated wrapper always provides the default capability — a non-default
        // declaration on an xpl endpoint can never hold.
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi15-xpl\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<sink id=\"out\" consistencyCapability=\"IDEMPOTENT\">"
                + "<source>log.info('x', event);</source></sink>"
                + "</transforms>"
                + "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
        StreamModel model = parseXml(xml);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("xpl-form"), () -> ex.getMessage());
    }
}
