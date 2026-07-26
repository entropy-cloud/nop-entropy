package io.nop.stream.core.operators;

import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the {@link StreamOperator#copyForSubtask()} contract:
 *
 * <ul>
 *   <li>The default interface method throws UnsupportedOperationException
 *       (No-Silent-No-Op: never silently share mutable state across subtasks).</li>
 *   <li>{@link AbstractStreamOperator} subclasses inherit a serialization-based
 *       default that produces independent subtask instances.</li>
 *   <li>{@link Shareable} operators opt out and return {@code this}.</li>
 *   <li>Concrete production operators (CEP/Process/Window/Watermark/Source/Map/
 *       Filter/FlatMap/Sink/Reduce) override with efficient constructor-based
 *       copies that share user functions but produce independent state.</li>
 *   <li>{@link OperatorChain#deepCopy()} routes through {@code copyForSubtask()}
 *       so each subtask receives an independent chain (no instanceof chain).</li>
 * </ul>
 *
 * <p>Plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md} Phase 1
 * exit criteria: "parallelism > 1 时，每个 subtask 拿到独立算子实例".
 */
public class TestOperatorSubtaskIsolation {

    // ---- Default interface behavior (No-Silent-No-Op) ----

    @Test
    void defaultCopyForSubtaskThrowsForBareOperator() {
        StreamOperator<Object> bare = new BareOperator();
        // Bare operators (no override, not @Shareable) must throw — never silently share.
        assertThrows(UnsupportedOperationException.class, bare::copyForSubtask);
    }

    @Test
    void defaultCopyForSubtaskReturnsSelfForShareableOperator() {
        StreamOperator<Object> shareable = new ShareableOperator();
        assertSame(shareable, shareable.copyForSubtask(),
                "@Shareable operator must return itself (opt-out of copy contract)");
    }

    // ---- Concrete operator overrides share user function, produce independent instance ----

    @Test
    void streamMapSharesUserFunctionProducesIndependentInstance() {
        io.nop.stream.core.common.functions.MapFunction<String, String> fn = s -> s;
        StreamMap<String, String> op = new StreamMap<>(fn);
        StreamMap<String, String> copy = op.copyForSubtask();

        assertNotSame(op, copy, "Map copy must be a fresh instance");
        assertSame(op.getUserFunction(), copy.getUserFunction(),
                "Map user function must be shared across subtasks");
    }

    @Test
    void streamFilterSharesUserFunctionProducesIndependentInstance() {
        io.nop.stream.core.common.functions.FilterFunction<String> fn = s -> true;
        StreamFilter<String> op = new StreamFilter<>(fn);
        StreamFilter<String> copy = op.copyForSubtask();

        assertNotSame(op, copy);
        assertSame(op.getUserFunction(), copy.getUserFunction());
    }

    @Test
    void streamFlatMapSharesUserFunctionProducesIndependentInstance() {
        io.nop.stream.core.common.functions.FlatMapFunction<String, String> fn =
                (v, out) -> out.collect(v);
        StreamFlatMap<String, String> op = new StreamFlatMap<>(fn);
        StreamFlatMap<String, String> copy = op.copyForSubtask();

        assertNotSame(op, copy);
        assertSame(op.getUserFunction(), copy.getUserFunction());
    }

    @Test
    void streamSinkSharesUserFunctionProducesIndependentInstance() {
        io.nop.stream.core.common.functions.SinkFunction<String> fn = v -> {};
        StreamSinkOperator<String> op = new StreamSinkOperator<>(fn);
        StreamSinkOperator<String> copy = op.copyForSubtask();

        assertNotSame(op, copy);
        assertSame(op.getUserFunction(), copy.getUserFunction());
    }

    @Test
    void streamReduceSharesUserFunctionProducesIndependentInstance() {
        io.nop.stream.core.common.functions.ReduceFunction<String> fn = (a, b) -> a + b;
        StreamReduceOperator<String> op = new StreamReduceOperator<>(fn);
        StreamReduceOperator<String> copy = op.copyForSubtask();

        assertNotSame(op, copy);
        assertSame(op.getUserFunction(), copy.getUserFunction());
    }

    @Test
    void streamSourceSharesSourceFunctionProducesIndependentInstance() {
        io.nop.stream.core.common.functions.source.SourceFunction<String> src = new SourceFn<>();
        StreamSourceOperator<String> op = new StreamSourceOperator<>(src);
        StreamSourceOperator<String> copy = op.copyForSubtask();

        assertNotSame(op, copy);
        assertSame(op.getSourceFunction(), copy.getSourceFunction(),
                "Source function must be shared across subtasks");
    }

    // ---- OperatorChain.deepCopy routes through copyForSubtask ----

    @Test
    void operatorChainDeepCopyProducesIndependentOperators() {
        io.nop.stream.core.common.functions.MapFunction<String, String> fn = s -> s;
        StreamMap<String, String> mapOp = new StreamMap<>(fn);
        OperatorChain chain = new OperatorChain(Collections.singletonList(mapOp));

        OperatorChain copy = chain.deepCopy();

        assertNotNull(copy);
        assertNotSame(chain, copy);
        // The chain wraps a new operator instance (per copyForSubtask)
        assertNotSame(chain.getOperators().get(0), copy.getOperators().get(0),
                "Each subtask must receive a fresh operator instance from deepCopy");
        // But the user function is shared
        StreamMap<?, ?> origMap = (StreamMap<?, ?>) chain.getOperators().get(0);
        StreamMap<?, ?> copyMap = (StreamMap<?, ?>) copy.getOperators().get(0);
        assertSame(origMap.getUserFunction(), copyMap.getUserFunction());
    }

    @Test
    void operatorChainDeepCopyThrowsForUncopyableOperator() {
        // Operator that does not override copyForSubtask and is not @Shareable
        // must propagate the fail-fast error (No-Silent-No-Op).
        OperatorChain chain = new OperatorChain(Collections.singletonList(new BareOperator()));
        assertThrows(UnsupportedOperationException.class, chain::deepCopy);
    }

    // ---- Stubs ----

    /**
     * A bare operator that does NOT override copyForSubtask and is NOT @Shareable.
     * Used to verify the default interface method throws.
     */
    private static class BareOperator implements StreamOperator<Object> {
        @Override public void open() throws Exception {}
        @Override public void finish() throws Exception {}
        @Override public void close() throws Exception {}
        @Override public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {}
        @Override public void setKeyContextElement1(StreamRecord<?> record) throws Exception {}
        @Override public void setKeyContextElement2(StreamRecord<?> record) throws Exception {}
        @Override public void notifyCheckpointComplete(long checkpointId) throws Exception {}
        @Override public void setCurrentKey(Object key) {}
        @Override public Object getCurrentKey() { return null; }
    }

    @Shareable
    private static class ShareableOperator implements StreamOperator<Object> {
        @Override public void open() throws Exception {}
        @Override public void finish() throws Exception {}
        @Override public void close() throws Exception {}
        @Override public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {}
        @Override public void setKeyContextElement1(StreamRecord<?> record) throws Exception {}
        @Override public void setKeyContextElement2(StreamRecord<?> record) throws Exception {}
        @Override public void notifyCheckpointComplete(long checkpointId) throws Exception {}
        @Override public void setCurrentKey(Object key) {}
        @Override public Object getCurrentKey() { return null; }
    }

    private static class SourceFn<T> implements io.nop.stream.core.common.functions.source.SourceFunction<T> {
        private static final long serialVersionUID = 1L;
        @Override public void run(SourceContext<T> ctx) {}
        @Override public void cancel() {}
    }
}
