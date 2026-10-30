package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.functions.StreamFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.SerializationRoundTripCopy;

import io.nop.xlang.exec.TestCompiledXlangFunctionStub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 2512 C3-009 (adjudication ③, C10-003 closure gate — regression anchor
 * registered against the core work item): RuntimeContext per-subtask closure
 * for {@link ProcessOperator}.
 *
 * <p>Defect shape (red before the fix): {@code copyForSubtask} passed the same
 * UDF reference to every subtask, and each subtask's {@code open()} wrote a
 * fresh {@code StreamingRuntimeContext} (with its own keyed state store) into
 * the SHARED RichFunction instance field — with parallelism >= 2 every subtask's
 * keyed state collapsed into the last-writer-wins backend. The white-box
 * assertions below pin the fixed shape: each subtask's UDF, runtime context and
 * keyed state store are distinct instances, and keyed counting behaves
 * independently per subtask (UserHistoryEnricher lazy-init consumption shape).
 */
public class TestC3_009RuntimeContextPerSubtaskIsolation {

    /**
     * Stateful RichFunction UDF in the UserHistoryEnricher shape: lazily
     * initializes a keyed value state from the runtime context on first record
     * (RuntimeContext consumption, not just field existence).
     */
    public static class CountingKeyedFunction extends ProcessFunction<String, String> {
        private static final long serialVersionUID = 1L;

        private transient ValueState<Integer> countState;

        @Override
        public void processElement(String value, Context ctx,
                                   Collector<String> out) throws Exception {
            if (countState == null) {
                countState = getRuntimeContext().getKeyedStateStore()
                        .getState(new ValueStateDescriptor<>("count", Integer.class, 0));
            }
            int count = countState.value();
            countState.update(count + 1);
            out.collect(value + ":" + (count + 1));
        }
    }

    private static void openWithOwnBackend(ProcessOperator<String, String> op) throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        io.nop.stream.core.common.state.backend.IKeyedStateBackend<String> keyedStateBackend =
                stateBackend.createKeyedStateBackend(String.class);
        op.setKeyedStateBackend(keyedStateBackend);
        op.open();
    }

    @Test
    void p2KeyedRichFunctionUdfGetsPrivateContextAndStorePerSubtask_C3_009_C10_003() throws Exception {
        CountingKeyedFunction shared = new CountingKeyedFunction();
        ProcessOperator<String, String> op0 = new ProcessOperator<>(shared).copyForSubtask();
        ProcessOperator<String, String> op1 = new ProcessOperator<>(shared).copyForSubtask();

        openWithOwnBackend(op0);
        openWithOwnBackend(op1);

        RichFunction f0 = (RichFunction) op0.getUserFunction();
        RichFunction f1 = (RichFunction) op1.getUserFunction();

        // White-box: each subtask's UDF instance, context and keyed state store
        // are distinct (before the fix all three collapsed to the shared
        // last-writer-wins instances).
        assertNotSame(f0, f1, "RichFunction UDF must be deep copied per subtask");
        RuntimeContext c0 = f0.getRuntimeContext();
        RuntimeContext c1 = f1.getRuntimeContext();
        assertNotSame(c0, c1, "each subtask must own its RuntimeContext");
        assertNotSame(c0.getKeyedStateStore(), c1.getKeyedStateStore(),
                "each subtask must own its keyed state store");
    }

    @Test
    void p2KeyedRichFunctionUdfCountsAreIsolatedAcrossSubtasks_C3_009_C10_003() throws Exception {
        CountingKeyedFunction shared = new CountingKeyedFunction();
        ProcessOperator<String, String> op0 = new ProcessOperator<>(shared).copyForSubtask();
        ProcessOperator<String, String> op1 = new ProcessOperator<>(shared).copyForSubtask();

        TestOutput<String> out0 = new TestOutput<>();
        TestOutput<String> out1 = new TestOutput<>();
        op0.setOutput(out0);
        op1.setOutput(out1);
        openWithOwnBackend(op0);
        openWithOwnBackend(op1);

        // Same key processed through both subtasks: counts must be independent
        // (before the fix both subtasks wrote through one shared context/backend
        // and the counts crossed).
        op0.processElement(new StreamRecord<>("k"));
        op0.processElement(new StreamRecord<>("k"));
        op1.processElement(new StreamRecord<>("k"));

        assertEquals("k:1", out0.getElements().get(0));
        assertEquals("k:2", out0.getElements().get(1));
        assertEquals("k:1", out1.getElements().get(0));
    }

    @Test
    void richFunctionUdfDeepCopiedWhileNonRichFunctionPassesThrough_C3_009() {
        // RichFunction path: deep copy (private context slot).
        CountingKeyedFunction fn = new CountingKeyedFunction();
        ProcessOperator<String, String> op = new ProcessOperator<>(fn);
        ProcessOperator<String, String> copy = op.copyForSubtask();
        assertNotSame(op, copy);
        assertNotSame(op.getUserFunction(), copy.getUserFunction(),
                "RichFunction UDF must not be shared across subtasks");

        // Non-RichFunction pass-through (zero regression for stateless UDFs).
        StreamFunction plain = new PlainFunction();
        assertSame(plain, SerializationRoundTripCopy.copyForSubtask(plain),
                "non-RichFunction UDFs keep the pass-through shared reference");
    }

    @Test
    void nonSerializableRichFunctionUdfFailsTypedOnCopy_C3_009() {
        ProcessOperator<String, String> op = new ProcessOperator<>(new NotSerializableRichFunction());
        assertThrows(StreamException.class, op::copyForSubtask,
                "non-Serializable RichFunction UDF must fail typed, not silently share");
    }

    @Test
    void copySharesOnlyCompiledXlangFunctionLeaves_C3_009() {
        TestCompiledXlangFunctionStub leaf = new TestCompiledXlangFunctionStub();
        ScriptedFunction fn = new ScriptedFunction(leaf);

        ProcessOperator<String, String> op = new ProcessOperator<>(fn);
        ProcessOperator<String, String> copy = op.copyForSubtask();

        assertNotSame(op.getUserFunction(), copy.getUserFunction(),
                "the scripted UDF wrapper itself must be private per subtask");
        assertSame(((ScriptedFunction) copy.getUserFunction()).leaf, leaf,
                "compiled io.nop.xlang.* eval function leaves are shared by reference");
    }

    @Test
    void copyFailsTypedForNonSerializableNonXlangLeaf_C3_009() {
        ProcessOperator<String, String> op = new ProcessOperator<>(
                new ScriptedFunction(new Object() {
                    @Override
                    public String toString() {
                        return "non-serializable non-xlang leaf";
                    }
                }));
        assertThrows(StreamException.class, op::copyForSubtask,
                "non-xlang non-Serializable leaves keep failing typed (no silent sharing fallback)");
    }

    /** Minimal non-RichFunction stream function for the pass-through branch. */
    public static class PlainFunction implements StreamFunction {
        private static final long serialVersionUID = 1L;
    }

    /** RichFunction UDF that is deliberately not serializable. */
    public static class NotSerializableRichFunction extends CountingKeyedFunction {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("serial")
        final Object notSerializable = new Object() {
        };
    }

    /**
     * RichFunction UDF wrapping a leaf that is NOT serializable. When the leaf
     * is a compiled {@code io.nop.xlang.*} eval function the round-trip shares
     * the leaf by reference; any other non-serializable leaf fails typed.
     */
    public static class ScriptedFunction extends ProcessFunction<String, String> {
        private static final long serialVersionUID = 1L;

        final Object leaf;

        public ScriptedFunction(Object leaf) {
            this.leaf = leaf;
        }

        @Override
        public void processElement(String value, Context ctx,
                                   Collector<String> out) {
            out.collect(value);
        }
    }
}
