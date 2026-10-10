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
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
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
 * WI19 (plan 2365): the declarative backfill-job shape — the XDSL declaration of
 * {@code jobTerminationMode="DRAIN"} (the bounded-replay + DRAIN workaround for the
 * file-tailing continuous source, per the WI0 adjudication) reaches the built
 * environment's checkpoint config. The ENGINE-side semantics (DRAIN truncation,
 * RECALCULATE/FILL_HISTORY recipes, UPSERT idempotency) are delivered by multi-engine
 * WI10 and pinned by {@code TestRecalculateBackfillE2E} — this pin is the
 * DECLARATION-side wiring: the model-declared mode lands in the env config that the
 * runtime consumes.
 *
 * <p>File-tailing continuous source: adjudicated OUT per WI0 — the trigger condition
 * (a real tailing-source demand → connector-factory extension, not a DSL-core change)
 * is documented in the user guide.
 */
public class TestWi19BackfillDeclaration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static StreamModel parse(String xml) {
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    public void declaredDrainModeReachesEnvironmentConfig() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi19-drain\" version=\"1\">"
                + "<checkpoint enabled=\"true\" interval=\"100\" jobTerminationMode=\"DRAIN\"/>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcBean\"/>"
                + "<sink id=\"out\" bean=\"sinkBean\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
        StreamModel model = parse(xml);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("srcBean", new io.nop.stream.flow.testing.TestSourceFunction())
                        .register("sinkBean",
                                (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                                })).build();
        // STATE assertion: the declared backfill termination semantics reach the env
        // config the runtime consumes (DRAIN = process the backlog, then stop).
        assertEquals(JobTerminationMode.DRAIN,
                env.getCheckpointConfig().getJobTerminationMode(),
                "the declared DRAIN mode must reach the environment checkpoint config");
    }

    @Test
    public void defaultTerminationModeIsCancel() {
        // Without a declaration the legacy default (CANCEL) applies — the backfill
        // semantics are opt-in, never a silent behavior change.
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi19-default\" version=\"1\">"
                + "<checkpoint enabled=\"true\" interval=\"100\"/>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcBean\"/>"
                + "<sink id=\"out\" bean=\"sinkBean\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
        StreamModel model = parse(xml);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("srcBean", new io.nop.stream.flow.testing.TestSourceFunction())
                        .register("sinkBean",
                                (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                                })).build();
        assertEquals(JobTerminationMode.CANCEL,
                env.getCheckpointConfig().getJobTerminationMode(),
                "the undeclared mode must stay at the legacy CANCEL default");
    }

}
