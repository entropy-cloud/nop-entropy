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
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
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
 * WI12 (plan 2358): the five-registry adjudication contract.
 *
 * <ul>
 *   <li>{@code <environments>} — wired: exactly one profile applies its
 *       deploymentMode (STATE assertion on the built env); multiple profiles reject;
 *       DISTRIBUTED + an ACTIVE checkpoint declaration rejects (the mode would be
 *       silently inert); enabled=false + DISTRIBUTED is legal.</li>
 *   <li>{@code <requirements>} — wired for the statically decidable subset:
 *       TWO_PHASE_COMMIT_SINK must match a resolved 2PC sink bean (declaration
 *       without the sink = false declaration → reject); DISTRIBUTED_EXECUTION is
 *       always aggregated (legal); other values have no build-time verdict →
 *       reject; a 2PC sink WITHOUT the declaration stays legal (a requirement is
 *       not a census).</li>
 *   <li>{@code <checkpointParticipants>} — wired as a contract pin: unknown id →
 *       reject; bean-form function implementing CheckpointParticipant → legal;
 *       bean not implementing it → reject; xpl-form target → reject.</li>
 *   <li>{@code <streams>} — removed from the schema (parse-layer rejection pinned
 *       in TestStreamModelDslBuilderFailFast).</li>
 *   <li>{@code <coders>} — pinned Java-only surface (NOT_IMPLEMENTED with the
 *       rewritten message).</li>
 * </ul>
 */
public class TestWi12RegistryFamilies {

    private static final String BASE_TRANSFORMS =
            "<source id=\"src\" bean=\"noopSrc\"/>"
                    + "<sink id=\"out\" bean=\"noopSink\"/>";
    private static final String BASE_EDGES =
            "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ---------------- environments ----------------

    @Test
    public void singleEnvironmentAppliesDeploymentMode() {
        StreamModel model = parse(
                "<environments><environment name=\"local\" deploymentMode=\"LOCAL\"/></environments>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, baseResolver()).build();
        // STATE assertion: the declared profile actually reached the environment.
        assertEquals(io.nop.stream.core.execution.DeploymentMode.LOCAL,
                env.getDeploymentMode(),
                "the declared environment profile must drive the built environment");
    }

    @Test
    public void multipleEnvironmentsReject() {
        StreamModel model = parse(
                "<environments>"
                        + "<environment name=\"a\" deploymentMode=\"LOCAL\"/>"
                        + "<environment name=\"b\" deploymentMode=\"LOCAL\"/>"
                        + "</environments>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("exactly one"),
                () -> ex.getMessage());
    }

    @Test
    public void distributedWithActiveCheckpointRejects() {
        // enabled=true (default) + interval>0 → the checkpoint path would win and the
        // DISTRIBUTED mode would be silently inert → typed reject.
        StreamModel model = parse(
                "<environments><environment name=\"dist\" deploymentMode=\"DISTRIBUTED\"/></environments>"
                        + "<checkpoint enabled=\"true\" interval=\"100\"/>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("silently inert"), () -> ex.getMessage());
    }

    @Test
    public void distributedWithDisabledCheckpointIsLegal() {
        // enabled=false → the checkpoint path never triggers → the mode is effective.
        StreamModel model = parse(
                "<environments><environment name=\"dist\" deploymentMode=\"DISTRIBUTED\"/></environments>"
                        + "<checkpoint enabled=\"false\" interval=\"100\"/>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, baseResolver()).build();
        assertEquals(io.nop.stream.core.execution.DeploymentMode.DISTRIBUTED,
                env.getDeploymentMode(),
                "disabled checkpoint + DISTRIBUTED must apply the declared mode");
    }

    // ---------------- requirements ----------------

    @Test
    public void twoPhaseRequirementWithTwoPhaseSinkIsLegal() {
        StreamModel model = parse(
                "<requirements><requirement value=\"TWO_PHASE_COMMIT_SINK\"/></requirements>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<sink id=\"out\" bean=\"twoPhaseSink\"/>",
                "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>");
        InMemoryBeanFunctionResolver resolver = baseResolver()
                .register("twoPhaseSink", new RecordingTwoPhaseSink());
        // A requirement matching reality must not reject (build-time consistency OK).
        StreamModelDslBuilder.of(model, resolver).build();
    }

    @Test
    public void twoPhaseRequirementWithoutTwoPhaseSinkRejects() {
        StreamModel model = parse(
                "<requirements><requirement value=\"TWO_PHASE_COMMIT_SINK\"/></requirements>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("declaration is false"), () -> ex.getMessage());
    }

    @Test
    public void twoPhaseSinkWithoutDeclarationStaysLegal() {
        // A requirement is not a census: the engine aggregates it anyway.
        StreamModel model = parse("",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<sink id=\"out\" bean=\"twoPhaseSink\"/>",
                "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>");
        StreamModelDslBuilder.of(model, baseResolver()
                .register("twoPhaseSink", new RecordingTwoPhaseSink())).build();
    }

    @Test
    public void nonDecidableRequirementRejects() {
        StreamModel model = parse(
                "<requirements><requirement value=\"EFFECTIVELY_ONCE\"/></requirements>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("no build-time static verdict"),
                () -> ex.getMessage());
    }

    // ---------------- checkpointParticipants ----------------

    @Test
    public void participantWithImplementingBeanIsLegal() {
        StreamModel model = parse(
                "<checkpointParticipants><participant value=\"out\"/></checkpointParticipants>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<sink id=\"out\" bean=\"participantSink\"/>",
                "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>");
        StreamModelDslBuilder.of(model, baseResolver()
                .register("participantSink", new ParticipantSink())).build();
    }

    @Test
    public void unknownParticipantIdRejects() {
        StreamModel model = parse(
                "<checkpointParticipants><participant value=\"nope\"/></checkpointParticipants>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString(),
                () -> ex.getMessage());
    }

    @Test
    public void nonFunctionCarryingTargetRejects() {
        StreamModel model = parse(
                "<checkpointParticipants><participant value=\"k\"/></checkpointParticipants>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<sink id=\"out\" bean=\"noopSink\"/>",
                "<edges><edge id=\"e0\" from=\"src\" to=\"k\"/>"
                        + "<edge id=\"e1\" from=\"k\" to=\"out\"/></edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("carries no function"), () -> ex.getMessage());
    }

    @Test
    public void xplFormTargetRejects() {
        StreamModel model = parse(
                "<checkpointParticipants><participant value=\"out\"/></checkpointParticipants>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<sink id=\"out\"><source>log.info('x', event);</source></sink>",
                "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("xpl-fn"), () -> ex.getMessage());
    }

    @Test
    public void beanNotImplementingParticipantRejects() {
        StreamModel model = parse(
                "<checkpointParticipants><participant value=\"out\"/></checkpointParticipants>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<sink id=\"out\" bean=\"noopSink\"/>",
                "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("does not implement CheckpointParticipant"),
                () -> ex.getMessage());
    }

    // ---------------- coders (Java-only pin) ----------------

    @Test
    public void codersStayNotImplementedWithJavaOnlyMessage() {
        StreamModel model = parse("<coders><coder id=\"c1\" className=\"java.lang.String\"/></coders>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, baseResolver()).build());
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("Java-only surface"), () -> ex.getMessage());
    }

    // ---------------- helpers ----------------

    private static InMemoryBeanFunctionResolver baseResolver() {
        return new InMemoryBeanFunctionResolver()
                .register("noopSrc", new TestStreamModelDslBuilderFailFast.NoopSource())
                .register("noopSink", (SinkFunction<Object>) v -> {
                });
    }

    /** Minimal two-phase sink for the requirement-consistency pins. */
    public static class RecordingTwoPhaseSink extends TwoPhaseCommitSinkFunction<Object> {
        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(Object value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
        }

        @Override
        public void rollback() {
        }
    }

    /** Sink bean that truthfully declares checkpoint participation. */
    public static class ParticipantSink implements SinkFunction<Object>, CheckpointParticipant {
        @Override
        public void consume(Object value) {
        }

        @Override
        public io.nop.stream.core.checkpoint.TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
        }

        @Override
        public void restoreFromEpoch(long epochId, io.nop.stream.core.checkpoint.TaskStateSnapshot state) {
        }
    }

    private static StreamModel parse(String topLevelXml, String transformsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-wi12-registries\" version=\"1\">"
                + topLevelXml
                + "<transforms>" + transformsXml + "</transforms>"
                + edgesXml
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }
}
