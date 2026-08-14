/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.SideOutputElement;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OUTPUT_TAG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Output-contract family invariant gate (Cycle 2 / I1, invariant #6, PD-15): every main
 * {@link Output} implementation class's {@code collect(OutputTag, X)} behavior must match the
 * classification in {@code ai-dev/audits/nop-stream-invariants/output-contract-registry.json}
 * (classification vocabulary: {@code forward} / {@code fail-fast} / {@code pinned-known-violation}).
 *
 * <p>The registry is the single source of truth (mirroring the {@code InvariantTableCompleteness}
 * precedent of reading the ai-dev gate tables): a classification migration (e.g. Cycle 2 / I4
 * flipping the cross-task pins to fail-fast) MUST update both the registry and this test.
 *
 * <p>Behavior assertions (core-level, per class):
 * <ul>
 *   <li>{@link ChainingOutput} = forward to the registered consumer + fail-fast
 *       ({@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER}) without one (RL-7 fix, commit b20fcd0e1);</li>
 *   <li>{@link TimestampedCollector} = pure pass-through to the wrapped output — pass-through
 *       TARGET SENSITIVE: wrapping a fail-fast output inherits the wrapped object's fail-fast
 *       semantics (wrapping the cross-task {@code RecordWriterOutput} now surfaces
 *       {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER}, Cycle 2 / I4 interim fail-fast fix);</li>
 *   <li>{@code StreamTaskInvokable$RecordWriterOutput} / {@code $BroadcastingRecordWriterOutput}
 *       = fail-fast: cross-task {@code collect(OutputTag, X)} has no consumer channel, reflectively
 *       instantiated (private nested classes), must throw {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER}
 *       instead of silently dropping (Cycle 2 / I4 interim fail-fast fix, WI-C2-1; HG-01
 *       wire-protocol support = human confirmation gate, enhancement not a prerequisite).</li>
 * </ul>
 *
 * <p>E2E evidence (Rule #22) lives in nop-stream-runtime's {@code TestSideOutputChainingE2E}
 * (4 cases: end-to-end forwarding / no-consumer fail-fast / StreamTaskInvokable wiring /
 * cross-task tail-output fail-fast) — this core gate class must not reference runtime's
 * WindowOperator (dependency direction).
 */
public class TestOutputContractInvariant {

    static final String REGISTRY_REL_PATH = "ai-dev/audits/nop-stream-invariants/output-contract-registry.json";

    /** Main Output implementation classes of nop-stream (registry-backed case table). */
    static Stream<Arguments> registryImplementationClasses() {
        Map<String, Object> registry = loadRegistry();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes = (List<Map<String, Object>>) registry.get("implementationClasses");
        if (classes == null || classes.isEmpty()) {
            throw new IllegalStateException("output-contract-registry.json has no implementationClasses; "
                    + "no silent skip — a new main Output implementation class must be classified here");
        }
        return classes.stream()
                .map(entry -> Arguments.of(entry.get("fqcn"), entry.get("classification")));
    }

    /**
     * Locate the output-contract registry by walking up from the test working directory
     * (surefire runs with basedir = module dir). Fails loudly when not found — no silent skip.
     */
    public static Path findRegistryFile() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            Path candidate = dir.resolve(REGISTRY_REL_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException(
                "output-contract-registry.json not found (searched up from " + Paths.get("").toAbsolutePath()
                        + "); no silent skip — run from the repo root");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadRegistry() {
        try {
            Object parsed = JsonTool.parse(Files.readString(findRegistryFile()));
            if (!(parsed instanceof Map)) {
                throw new IllegalStateException("output-contract-registry.json is not a JSON object");
            }
            return (Map<String, Object>) parsed;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read output-contract-registry.json", e);
        }
    }

    /**
     * Parameterized exhaustion over the registry implementation-class table: the live
     * {@code collect(OutputTag, X)} behavior must match the registry classification. A cross-task
     * empty-body instance whose classification does NOT declare pin semantics (forward/fail-fast)
     * is red — the registry must declare pinned-known-violation for it.
     */
    @ParameterizedTest
    @MethodSource("registryImplementationClasses")
    void testCollectOutputTagBehaviorMatchesRegistry(String fqcn, String classification) throws Exception {
        switch (classification) {
            case "forward":
                assertForwardBehavior(fqcn);
                break;
            case "fail-fast":
                assertFailFastBehavior(fqcn);
                break;
            case "pinned-known-violation":
                assertPinnedNoOpBehavior(fqcn);
                break;
            default:
                fail("unknown registry classification '" + classification + "' for " + fqcn
                        + "; vocabulary = {forward, fail-fast, pinned-known-violation}");
        }
    }

    private void assertForwardBehavior(String fqcn) throws Exception {
        switch (fqcn) {
            case "io.nop.stream.core.operators.ChainingOutput": {
                OutputTag<String> tag = new OutputTag<>("forward-tag", BasicTypeInfo.STRING);
                List<StreamRecord<String>> received = new ArrayList<>();
                ChainingOutput<Object> out = new ChainingOutput<>(new NoOpInput(), "test", new java.util.HashMap<>());
                out.registerSideOutputConsumer(tag, received::add);
                out.collect(tag, new StreamRecord<>("v", 10));
                assertEquals(1, received.size(), "ChainingOutput must forward to the registered consumer");
                assertEquals("v", received.get(0).getValue());
                break;
            }
            case "io.nop.stream.core.operators.TimestampedCollector": {
                OutputTag<String> tag = new OutputTag<>("forward-tag", BasicTypeInfo.STRING);
                RecordingTagOutput wrapped = new RecordingTagOutput();
                TimestampedCollector<String> out = new TimestampedCollector<>(wrapped);
                out.collect(tag, new StreamRecord<>("v", 10));
                assertEquals(1, wrapped.sideReceived.size(),
                        "TimestampedCollector must pass through to the wrapped output");
                assertEquals("v", wrapped.sideReceived.get(0).getValue());
                break;
            }
            default:
                fail("no forward-behavior harness for " + fqcn
                        + "; a new forward-classified implementation class needs a harness here (no silent skip)");
        }
    }

    /**
     * Cycle 2 / I4 (WI-C2-1): cross-task instances fail fast. {@code collect(OutputTag, record)}
     * on the cross-task {@code RecordWriterOutput} / {@code BroadcastingRecordWriterOutput} has no
     * consumer channel — it must throw {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER} (with
     * {@code ARG_OUTPUT_TAG} / {@code ARG_DETAIL}) instead of silently dropping (Rule #24);
     * wire-protocol support = {@code HG-01} human confirmation gate (enhancement, not a
     * prerequisite for the interim fail-fast).
     */
    private void assertFailFastBehavior(String fqcn) throws Exception {
        switch (fqcn) {
            case "io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput": {
                ResultPartition partition = new ResultPartition();
                RecordWriter<Object> writer = new RecordWriter<>(partition);
                Object instance = instantiateNested(
                        "io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput",
                        RecordWriter.class, writer);
                StreamRuntimeException ex = assertThrows(StreamRuntimeException.class,
                        () -> invokeCollectOutputTagRaw(instance, fqcn, new StreamRecord<>("v", 10)),
                        "RecordWriterOutput.collect(OutputTag) must fail fast in the cross-task exchange "
                                + "(no consumer channel), never silently drop");
                assertEquals(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER.getErrorCode(), ex.getErrorCode(),
                        "fail-fast must use ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER");
                assertEquals("cross-task-tag", ex.getParam(ARG_OUTPUT_TAG),
                        "fail-fast must carry ARG_OUTPUT_TAG with the unregistered tag id");
                assertTrue(String.valueOf(ex.getParam(ARG_DETAIL)).contains("cross-task-tag"),
                        "fail-fast must carry ARG_DETAIL naming the unregistered tag");
                break;
            }
            case "io.nop.stream.core.execution.StreamTaskInvokable$BroadcastingRecordWriterOutput": {
                RecordingTagOutput inner = new RecordingTagOutput();
                List<Output<StreamRecord<Object>>> outputs = new ArrayList<>();
                outputs.add((Output<StreamRecord<Object>>) (Output<?>) inner);
                Object instance = instantiateNested(
                        "io.nop.stream.core.execution.StreamTaskInvokable$BroadcastingRecordWriterOutput",
                        List.class, outputs);
                StreamRuntimeException ex = assertThrows(StreamRuntimeException.class,
                        () -> invokeCollectOutputTagRaw(instance, fqcn, new StreamRecord<>("v", 10)),
                        "BroadcastingRecordWriterOutput.collect(OutputTag) must fail fast in the "
                                + "cross-task exchange (no consumer channel), never silently drop");
                assertEquals(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER.getErrorCode(), ex.getErrorCode(),
                        "fail-fast must use ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER");
                assertEquals("cross-task-tag", ex.getParam(ARG_OUTPUT_TAG),
                        "fail-fast must carry ARG_OUTPUT_TAG with the unregistered tag id");
                assertEquals(0, inner.sideReceived.size(),
                        "fail-fast must throw before forwarding anything to any broadcast output");
                break;
            }
            default:
                fail("no fail-fast harness for " + fqcn
                        + "; a new fail-fast-classified implementation class needs a harness here (no silent skip)");
        }
    }

    private void assertPinnedNoOpBehavior(String fqcn) throws Exception {
        switch (fqcn) {
            case "io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput": {
                // Transition pin (mjs-pins.json, HG-01): empty-body no-op — side outputs are NOT
                // forwarded across task boundaries. Recorded, not fixed here (interim fail-fast is
                // pre-authorized to Cycle 2 / I4). If this starts forwarding, partition.size() > 0 = red.
                ResultPartition partition = new ResultPartition();
                RecordWriter<Object> writer = new RecordWriter<>(partition);
                Object instance = instantiateNested("io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput",
                        RecordWriter.class, writer);
                invokeCollectOutputTag(instance, fqcn, new StreamRecord<>("v", 10));
                assertEquals(0, partition.size(),
                        "RecordWriterOutput.collect(OutputTag) is a pinned no-op (cross-task, HG-01); "
                                + "forwarding would violate the transition-pin semantics");
                break;
            }
            case "io.nop.stream.core.execution.StreamTaskInvokable$BroadcastingRecordWriterOutput": {
                RecordingTagOutput inner = new RecordingTagOutput();
                List<Output<StreamRecord<Object>>> outputs = new ArrayList<>();
                outputs.add((Output<StreamRecord<Object>>) (Output<?>) inner);
                Object instance = instantiateNested(
                        "io.nop.stream.core.execution.StreamTaskInvokable$BroadcastingRecordWriterOutput",
                        List.class, outputs);
                invokeCollectOutputTag(instance, fqcn, new StreamRecord<>("v", 10));
                assertEquals(0, inner.sideReceived.size(),
                        "BroadcastingRecordWriterOutput.collect(OutputTag) is a pinned no-op "
                                + "(cross-task, HG-01); forwarding would violate the transition-pin semantics");
                break;
            }
            default:
                fail("no pinned-behavior harness for " + fqcn
                        + "; a new pinned-classified implementation class needs a harness here (no silent skip)");
        }
    }

    static Object instantiateNested(String fqcn, Class<?> ctorParam, Object ctorArg) throws Exception {
        try {
            Class<?> clazz = Class.forName(fqcn);
            Constructor<?> ctor = clazz.getDeclaredConstructor(ctorParam);
            ctor.setAccessible(true);
            return ctor.newInstance(ctorArg);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("failed to instantiate " + fqcn, e.getCause());
        }
    }

    static void invokeCollectOutputTag(Object instance, String fqcn, StreamRecord<?> record) throws Exception {
        try {
            Method collect = instance.getClass().getDeclaredMethod("collect", OutputTag.class, StreamRecord.class);
            collect.setAccessible(true);
            collect.invoke(instance, new OutputTag<>("pin-tag", BasicTypeInfo.STRING), record);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("collect(OutputTag) threw in " + fqcn + " (pinned no-op expected)", e.getCause());
        }
    }

    /**
     * Invokes the reflectively-instantiated instance's {@code collect(OutputTag, record)}, unwrapping
     * {@link InvocationTargetException} so the runtime exception surfaces to the caller (fail-fast
     * harness, Cycle 2 / I4).
     */
    static void invokeCollectOutputTagRaw(Object instance, String fqcn, StreamRecord<?> record) throws Exception {
        try {
            Method collect = instance.getClass().getDeclaredMethod("collect", OutputTag.class, StreamRecord.class);
            collect.setAccessible(true);
            collect.invoke(instance, new OutputTag<>("cross-task-tag", BasicTypeInfo.STRING), record);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("collect(OutputTag) threw a checked exception in " + fqcn, cause);
        }
    }

    /** Forward + fail-fast of ChainingOutput (RL-7 fix, b20fcd0e1). */
    @Test
    void testChainingOutputForwardsToRegisteredConsumer() {
        OutputTag<String> tag = new OutputTag<>("chain-tag", BasicTypeInfo.STRING);
        List<StreamRecord<String>> received = new ArrayList<>();
        ChainingOutput<Object> out = new ChainingOutput<>(new NoOpInput(), "test-chain", new java.util.HashMap<>());
        out.registerSideOutputConsumer(tag, received::add);
        out.collect(tag, new StreamRecord<>("hello", 42));
        assertEquals(1, received.size());
        assertEquals("hello", received.get(0).getValue());
        assertEquals(42, received.get(0).getTimestamp());
    }

    @Test
    void testChainingOutputFailsFastWithoutConsumer() {
        OutputTag<String> tag = new OutputTag<>("unwired-tag", BasicTypeInfo.STRING);
        ChainingOutput<Object> out = new ChainingOutput<>(new NoOpInput(), "test-chain", new java.util.HashMap<>());
        StreamRuntimeException ex = assertThrows(StreamRuntimeException.class,
                () -> out.collect(tag, new StreamRecord<>("v", 10)),
                "side output without a registered consumer must fail fast, never silently drop");
        assertEquals(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER.getErrorCode(), ex.getErrorCode(),
                "fail-fast must use ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER");
    }

    /** TimestampedCollector = pure pass-through (forward classification). */
    @Test
    void testTimestampedCollectorPassesThroughToWrappedOutput() {
        OutputTag<String> tag = new OutputTag<>("pass-tag", BasicTypeInfo.STRING);
        RecordingTagOutput wrapped = new RecordingTagOutput();
        TimestampedCollector<String> out = new TimestampedCollector<>(wrapped);
        out.collect(tag, new StreamRecord<>("v", 5));
        assertEquals(1, wrapped.sideReceived.size());
        assertEquals("v", wrapped.sideReceived.get(0).getValue());
    }

    /**
     * Pass-through TARGET SENSITIVITY: wrapping a no-op output drops side outputs with the wrapped
     * object's semantics. TimestampedCollector itself never swallows — the drop is the wrapped
     * object's behavior (recorded, not fixed here).
     */
    @Test
    void testTimestampedCollectorPassThroughTargetSensitiveWithNoOpOutput() {
        OutputTag<String> tag = new OutputTag<>("pass-tag", BasicTypeInfo.STRING);
        TimestampedCollector<String> out = new TimestampedCollector<>(new NoOpTagOutput());
        // no exception, nothing delivered — pass-through target sensitive semantics (recorded)
        out.collect(tag, new StreamRecord<>("v", 10));
    }

    /**
     * Pass-through TARGET SENSITIVE (HG-01, 2026-08-14): wrapping the cross-task
     * {@code RecordWriterOutput} (reflectively instantiated) now inherits its wire-protocol
     * forwarding — {@code collect(OutputTag)} must deliver the tagged record to the wrapped
     * RWO (which broadcasts it through the cross-task exchange), never throw, never silently
     * drop (review M3; pre-HG-01 the wrapped RWO failed fast with
     * {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER}).
     */
    @Test
    void testTimestampedCollectorWrappingRecordWriterOutputForwards() throws Exception {
        ResultPartition partition = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(partition);
        Object rwo = instantiateNested("io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput",
                RecordWriter.class, writer);
        TimestampedCollector<Object> out = new TimestampedCollector<>((Output<StreamRecord<Object>>) (Output<?>) rwo);
        OutputTag<String> tag = new OutputTag<>("cross-task-tag", BasicTypeInfo.STRING);
        out.collect(tag, new StreamRecord<>("v", 10));

        assertEquals(1, partition.size(),
                "wrapping RecordWriterOutput must forward the tagged record into the cross-task exchange");
        StreamElement element = partition.read();
        assertTrue(element.isSideOutput(),
                "the forwarded element must be a SideOutputElement (HG-01 wire protocol)");
        assertEquals("cross-task-tag", element.asSideOutput().getOutputTagId(),
                "the forwarded element must carry the unmodified tag id");
        assertEquals("v", element.asSideOutput().getRecord().getValue());
    }

    /**
     * HG-01 (2026-08-14): RWO.collect(OutputTag) forwards a tagged SideOutputElement into the
     * writer's partitions (wire protocol), instead of the Cycle 2 / I4 interim fail-fast.
     * Non-registry-driven green evidence for the Phase 3 intermediate state (the registry-driven
     * parameterized test stays red until Phase 4 migrates the classification).
     */
    @Test
    void testRecordWriterOutputForwardsTaggedElement() throws Exception {
        ResultPartition partition = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(partition);
        Object rwo = instantiateNested("io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput",
                RecordWriter.class, writer);
        invokeCollectOutputTag(rwo, "io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput",
                new StreamRecord<>("payload", 3L));

        assertEquals(1, partition.size(), "RWO.collect(OutputTag) must enqueue one element");
        StreamElement element = partition.read();
        assertTrue(element.isSideOutput(), "enqueued element must be a SideOutputElement");
        assertEquals("pin-tag", element.asSideOutput().getOutputTagId());
        assertEquals("payload", element.asSideOutput().getRecord().getValue());
        assertEquals(3L, element.asSideOutput().getRecord().getTimestamp());
    }

    /**
     * HG-01 (2026-08-14): BRWO.collect(OutputTag) fans the tagged record out to every wrapped
     * output; each wrapped output copies the inner record (D5 — no shared element instance,
     * no aliasing across partition queues).
     */
    @Test
    void testBroadcastingRecordWriterOutputFansOutTaggedElement() throws Exception {
        RecordingTagOutput inner1 = new RecordingTagOutput();
        RecordingTagOutput inner2 = new RecordingTagOutput();
        List<Output<StreamRecord<Object>>> outputs = new ArrayList<>();
        outputs.add((Output<StreamRecord<Object>>) (Output<?>) inner1);
        outputs.add((Output<StreamRecord<Object>>) (Output<?>) inner2);
        Object brwo = instantiateNested(
                "io.nop.stream.core.execution.StreamTaskInvokable$BroadcastingRecordWriterOutput",
                List.class, outputs);
        invokeCollectOutputTag(brwo, "io.nop.stream.core.execution.StreamTaskInvokable$BroadcastingRecordWriterOutput",
                new StreamRecord<>("fan-value", 9L));

        assertEquals(1, inner1.sideReceived.size(), "every wrapped output must receive the tagged record");
        assertEquals("fan-value", inner1.sideReceived.get(0).getValue());
        assertEquals(1, inner2.sideReceived.size(), "every wrapped output must receive the tagged record");
        assertEquals("fan-value", inner2.sideReceived.get(0).getValue());
    }

    /**
     * Wiring verification (Rule #23): StreamTaskInvokable.registerSideOutputConsumer writes into the
     * shared consumer map that wireOperators() hands to every ChainingOutput — a consumer registered
     * on the invokable must be reachable from the wired ChainingOutput at runtime.
     */
    @Test
    void testInvokableRegistrationReachesWiredChainingOutput() {
        OutputTag<String> tag = new OutputTag<>("wired-tag", BasicTypeInfo.STRING);
        List<StreamRecord<String>> received = new ArrayList<>();
        TestChainOperator op1 = new TestChainOperator();
        TestChainOperator op2 = new TestChainOperator();
        OperatorChain chain = new OperatorChain(List.of(op1, op2));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        invokable.registerSideOutputConsumer(tag, received::add);
        ChainingOutput<Object> wired = (ChainingOutput<Object>) op1.getOutput();
        wired.collect(tag, new StreamRecord<>("w", 7));
        assertEquals(1, received.size(),
                "invokable-registered consumer must be reachable from the wireOperators() ChainingOutput");
        assertEquals("w", received.get(0).getValue());
    }

    /**
     * HG-01 (2026-08-14): inbound routing in {@code processInputGate} (review F4 — the private
     * while(true) loop is driven through a stub InputGate + a real SINK-role invokable). A
     * side-output element arriving on the input gate must be delivered to the consumer
     * registered by tag id (D4), never to the head operator.
     */
    @Test
    void testInboundSideOutputRouteDeliversToRegisteredConsumer() throws Exception {
        OutputTag<String> tag = new OutputTag<>("inbound-tag", BasicTypeInfo.STRING);
        List<StreamRecord<String>> received = new ArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);

        StubInputGate gate = new StubInputGate();
        StreamTaskInvokable consumer = new StreamTaskInvokable(
                new OperatorChain(List.of((io.nop.stream.core.operators.StreamOperator<?>) new TestChainOperator())),
                new ArrayList<RecordWriter<Object>>(), gate);
        consumer.registerSideOutputConsumer(tag, record -> {
            received.add((StreamRecord<String>) (StreamRecord<?>) record);
            delivered.countDown();
        });

        AtomicReference<Exception> invokeError = new AtomicReference<>();
        Thread taskThread = new Thread(() -> {
            try {
                consumer.invoke();
            } catch (Exception e) {
                invokeError.set(e);
            }
        });
        taskThread.start();

        try {
            gate.offer(new SideOutputElement("inbound-tag", new StreamRecord<>("routed-value", 11L)));
            assertTrue(delivered.await(10, TimeUnit.SECONDS),
                    "registered consumer must receive the inbound side-output element");
            assertEquals(1, received.size());
            assertEquals("routed-value", received.get(0).getValue());
            assertEquals(11L, received.get(0).getTimestamp());
            assertNull(invokeError.get(), "no error expected on the routed delivery path");
        } finally {
            gate.markFinished();
            taskThread.join(10_000);
        }
    }

    /**
     * HG-01 (2026-08-14): an inbound side-output element whose tag has no registered consumer
     * must fail fast at the consumption-side routing point with
     * {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER} (carrying the tag id) — never silently drop.
     */
    @Test
    void testInboundSideOutputRouteFailsFastWithoutConsumer() throws Exception {
        StubInputGate gate = new StubInputGate();
        StreamTaskInvokable consumer = new StreamTaskInvokable(
                new OperatorChain(List.of((io.nop.stream.core.operators.StreamOperator<?>) new TestChainOperator())),
                new ArrayList<RecordWriter<Object>>(), gate);

        AtomicReference<Throwable> taskError = new AtomicReference<>();
        Thread taskThread = new Thread(() -> {
            try {
                consumer.invoke();
            } catch (Throwable t) {
                taskError.set(t);
            }
        });
        taskThread.start();

        try {
            gate.offer(new SideOutputElement("no-consumer-tag", new StreamRecord<>("orphan", 1L)));
            long deadline = System.currentTimeMillis() + 10_000;
            while (taskError.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(taskError.get() != null, "unregistered tag must fail fast, never silently drop");
            assertTrue(taskError.get() instanceof StreamRuntimeException,
                    "fail-fast must surface as StreamRuntimeException, got: " + taskError.get());
            StreamRuntimeException ex = (StreamRuntimeException) taskError.get();
            assertEquals(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER.getErrorCode(), ex.getErrorCode(),
                    "fail-fast must use ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER");
            assertEquals("no-consumer-tag", ex.getParam(ARG_OUTPUT_TAG),
                    "fail-fast must carry ARG_OUTPUT_TAG with the unregistered tag id");
        } finally {
            gate.markFinished();
            taskThread.join(10_000);
        }
    }

    /** Stub InputGate fed directly by the test (review F4: processInputGate drive mechanism). */
    static class StubInputGate extends InputGate {
        private final LinkedBlockingQueue<StreamElement> queue = new LinkedBlockingQueue<>();
        private volatile boolean finished = false;

        StubInputGate() {
            // The base ctor requires >=1 channel; read()/isAllFinished() are overridden so the
            // backing partition is never touched by the routing path under test.
            super(List.of(new io.nop.stream.core.execution.InputChannel(new ResultPartition())));
        }

        void offer(StreamElement element) {
            queue.offer(element);
        }

        void markFinished() {
            finished = true;
        }

        @Override
        public Optional<StreamElement> read() {
            try {
                StreamElement element = queue.poll(100, TimeUnit.MILLISECONDS);
                return element != null ? Optional.of(element) : Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        @Override
        public boolean isAllFinished() {
            return finished && queue.isEmpty();
        }
    }

    static class NoOpInput implements Input<Object> {
        @Override
        public void processElement(StreamRecord<Object> element) {
        }

        @Override
        public void processWatermark(Watermark mark) {
        }

        @Override
        public void processWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void processLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void setKeyContextElement(StreamRecord<Object> record) {
        }
    }

    static class TestChainOperator extends AbstractStreamOperator<Object> implements Input<Object> {
        @Override
        public void processElement(StreamRecord<Object> element) {
        }

        @Override
        public void processWatermark(Watermark mark) {
        }

        @Override
        public void processWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void processLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void setKeyContextElement(StreamRecord<Object> record) {
        }
    }

    /** Test-only recording Output capturing collect(OutputTag, record) deliveries. */
    static class RecordingTagOutput implements Output<StreamRecord<String>> {
        final List<StreamRecord<String>> sideReceived = new ArrayList<>();

        @Override
        public void collect(StreamRecord<String> record) {
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            sideReceived.add((StreamRecord<String>) (StreamRecord<?>) record);
        }

        @Override
        public void close() {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }
    }

    /** Test-only no-op Output (pass-through target sensitivity probe). */
    static class NoOpTagOutput implements Output<StreamRecord<String>> {
        @Override
        public void collect(StreamRecord<String> record) {
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void close() {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }
    }
}
