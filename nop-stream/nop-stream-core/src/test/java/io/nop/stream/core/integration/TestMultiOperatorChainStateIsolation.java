package io.nop.stream.core.integration;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.operators.AbstractStreamOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestMultiOperatorChainStateIsolation {

    /**
     * Minimal concrete operator that holds its own keyed state backend and a named ValueState.
     */
    static class TestOperator extends AbstractStreamOperator<String> {

        private final String stateName;
        private final ValueStateDescriptor<Long> stateDesc;
        private MemoryKeyedStateBackend<String> backend;

        TestOperator(String stateName) {
            this.stateName = stateName;
            this.stateDesc = new ValueStateDescriptor<>(stateName, Long.class, 0L);
        }

        void initBackend() {
            this.backend = new MemoryKeyedStateBackend<>(String.class);
            this.setKeyedStateBackend(backend);
        }

        MemoryKeyedStateBackend<String> getBackend() {
            return this.backend;
        }

        ValueState<Long> getState() {
            return this.backend.getState(stateDesc);
        }

        void setCurrentKey(String key) {
            this.backend.setCurrentKey(key);
        }

        @Override
        public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
            OperatorSnapshotResult result = new OperatorSnapshotResult();
            if (keyedStateBackend != null) {
                StateSnapshot snapshot = keyedStateBackend.snapshotState();
                if (snapshot != null && !snapshot.isEmpty()) {
                    result.putKeyedState(stateName, snapshot);
                }
            }
            return result;
        }
    }

    @Test
    void testKeyedStateIsolationAcrossOperatorsAfterSnapshotRestore() throws Exception {
        // --- Phase 1: Create two operators, each with its own keyed state backend ---
        TestOperator opA = new TestOperator("stateA");
        TestOperator opB = new TestOperator("stateB");
        opA.initBackend();
        opB.initBackend();

        // --- Phase 2: Write state for key1 ---
        opA.setCurrentKey("key1");
        opA.getState().update(100L);

        opB.setCurrentKey("key1");
        opB.getState().update(200L);

        // --- Phase 3: Write state for key2 ---
        opA.setCurrentKey("key2");
        opA.getState().update(300L);

        opB.setCurrentKey("key2");
        opB.getState().update(400L);

        // --- Phase 4: Snapshot both operators ---
        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapA = opA.snapshotState(ctx);
        OperatorSnapshotResult snapB = opB.snapshotState(ctx);

        // Sanity: each snapshot contains its own state keyed by state name
        assertNotNull(snapA.getKeyedState("stateA"));
        assertNotNull(snapB.getKeyedState("stateB"));

        // --- Phase 5: Create new backend + operator instances and restore ---
        TestOperator restoredA = new TestOperator("stateA");
        TestOperator restoredB = new TestOperator("stateB");
        restoredA.initBackend();
        restoredB.initBackend();

        restoredA.restoreState(snapA);
        restoredB.restoreState(snapB);

        // --- Phase 6: Verify key1 ---
        restoredA.setCurrentKey("key1");
        assertEquals(Long.valueOf(100L), restoredA.getState().value(),
                "Operator A state for key1 should be 100");

        restoredB.setCurrentKey("key1");
        assertEquals(Long.valueOf(200L), restoredB.getState().value(),
                "Operator B state for key1 should be 200");

        // --- Phase 7: Verify key2 ---
        restoredA.setCurrentKey("key2");
        assertEquals(Long.valueOf(300L), restoredA.getState().value(),
                "Operator A state for key2 should be 300");

        restoredB.setCurrentKey("key2");
        assertEquals(Long.valueOf(400L), restoredB.getState().value(),
                "Operator B state for key2 should be 400");
    }

    @Test
    void testOperatorsDoNotInterfereWithEachOther() throws Exception {
        TestOperator opA = new TestOperator("stateA");
        TestOperator opB = new TestOperator("stateB");
        opA.initBackend();
        opB.initBackend();

        // Only write to operator A
        opA.setCurrentKey("key1");
        opA.getState().update(111L);

        // Operator B for key1 should remain at default (null)
        opB.setCurrentKey("key1");
        assertEquals(Long.valueOf(0L), opB.getState().value(),
                "Operator B should not see Operator A's state" );

        // Only write to operator B
        opB.getState().update(222L);

        // Operator A's value must be unchanged
        opA.setCurrentKey("key1");
        assertEquals(Long.valueOf(111L), opA.getState().value(),
                "Operator A state should not be affected by Operator B writes");
    }

    @Test
    void testSnapshotRestorePreservesIndependentKeys() throws Exception {
        TestOperator opA = new TestOperator("stateA");
        TestOperator opB = new TestOperator("stateB");
        opA.initBackend();
        opB.initBackend();

        // Write different values for different keys across operators
        opA.setCurrentKey("k1"); opA.getState().update(10L);
        opA.setCurrentKey("k2"); opA.getState().update(20L);
        opB.setCurrentKey("k1"); opB.getState().update(30L);
        opB.setCurrentKey("k2"); opB.getState().update(40L);

        // Snapshot and restore
        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapA = opA.snapshotState(ctx);
        OperatorSnapshotResult snapB = opB.snapshotState(ctx);

        TestOperator restoredA = new TestOperator("stateA");
        TestOperator restoredB = new TestOperator("stateB");
        restoredA.initBackend();
        restoredB.initBackend();

        restoredA.restoreState(snapA);
        restoredB.restoreState(snapB);

        // Verify all 4 key-operator combinations
        restoredA.setCurrentKey("k1");
        assertEquals(Long.valueOf(10L), restoredA.getState().value());

        restoredA.setCurrentKey("k2");
        assertEquals(Long.valueOf(20L), restoredA.getState().value());

        restoredB.setCurrentKey("k1");
        assertEquals(Long.valueOf(30L), restoredB.getState().value());

        restoredB.setCurrentKey("k2");
        assertEquals(Long.valueOf(40L), restoredB.getState().value());
    }
}
