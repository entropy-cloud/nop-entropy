/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.FunctionInitializationContext;
import io.nop.stream.core.checkpoint.FunctionSnapshotContext;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.ICheckpointedFunction;
import io.nop.stream.core.common.state.DefaultOperatorStateStore;
import io.nop.stream.core.common.state.IOperatorStateStore;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.common.state.backend.IOperatorStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryOperatorStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestOperatorStateWiring {

    @Test
    void testInitializeStateProvidesOperatorStateStore() throws Exception {
        AtomicReference<IOperatorStateStore> capturedStore = new AtomicReference<>();

        ICheckpointedFunction fn = new ICheckpointedFunction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void snapshotState(FunctionSnapshotContext context) {
            }

            @Override
            public void initializeState(FunctionInitializationContext context) {
                capturedStore.set(context.getOperatorStateStore());
            }
        };

        AbstractUdfStreamOperator<Void, ICheckpointedFunction> operator = new AbstractUdfStreamOperator<>(fn) {
            private static final long serialVersionUID = 1L;
        };

        operator.setStateBackend(new MemoryStateBackend());
        operator.initializeState(new TaskStateSnapshot(null));

        assertNotNull(capturedStore.get());
        assertTrue(capturedStore.get() instanceof IOperatorStateStore);
    }

    @Test
    void testSnapshotStateIncludesOperatorStateBackend() throws Exception {
        AbstractStreamOperator<Void> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        backend.putRawState("my-key", "my-value");
        operator.setOperatorStateBackend(backend);

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult result = operator.snapshotState(ctx);

        assertNotNull(result);
        assertEquals("my-value", result.getOperatorState("my-key"));
    }

    @Test
    void testCheckpointedFunctionWriteThroughOperatorStateStore() throws Exception {
        List<String> capturedAfterRestore = new ArrayList<>();

        ICheckpointedFunction fn = new ICheckpointedFunction() {
            private static final long serialVersionUID = 1L;
            private transient ListState<String> listState;

            @Override
            public void snapshotState(FunctionSnapshotContext context) {
            }

            @Override
            public void initializeState(FunctionInitializationContext context) {
                IOperatorStateStore store = context.getOperatorStateStore();
                assertNotNull(store);
                listState = store.getListState(new ListStateDescriptor<>("items", String.class));
                if (context.isRestored()) {
                    try {
                        for (String s : listState.get()) {
                            capturedAfterRestore.add(s);
                        }
                    } catch (Exception e) {
                        throw new StreamRuntimeException(e);
                    }
                } else {
                    try {
                        listState.add("a");
                        listState.add("b");
                        listState.add("c");
                    } catch (Exception e) {
                        throw new StreamRuntimeException(e);
                    }
                }
            }
        };

        AbstractUdfStreamOperator<Void, ICheckpointedFunction> operator = new AbstractUdfStreamOperator<>(fn) {
            private static final long serialVersionUID = 1L;
        };

        MemoryStateBackend stateBackend = new MemoryStateBackend();
        operator.setStateBackend(stateBackend);

        // First initialize (not restored) — writes "a", "b", "c"
        operator.initializeState(new TaskStateSnapshot(null));

        // Snapshot
        StateSnapshotContext ctx = new StateSnapshotContext(42L, System.currentTimeMillis());
        OperatorSnapshotResult snapshotResult = operator.snapshotState(ctx);

        assertNotNull(snapshotResult);
        assertFalse(snapshotResult.getOperatorStates().isEmpty());
        assertTrue(snapshotResult.getOperatorStates().containsKey("items"));

        // Create a fresh operator with the same function and restore
        List<String> restoredItems = capturedAfterRestore;
        ICheckpointedFunction freshFn = new ICheckpointedFunction() {
            private static final long serialVersionUID = 1L;
            private transient ListState<String> listState;

            @Override
            public void snapshotState(FunctionSnapshotContext context) {
            }

            @Override
            public void initializeState(FunctionInitializationContext context) {
                IOperatorStateStore store = context.getOperatorStateStore();
                assertNotNull(store);
                listState = store.getListState(new ListStateDescriptor<>("items", String.class));
                if (context.isRestored()) {
                    try {
                        for (String s : listState.get()) {
                            restoredItems.add(s);
                        }
                    } catch (Exception e) {
                        throw new StreamRuntimeException(e);
                    }
                }
            }
        };

        AbstractUdfStreamOperator<Void, ICheckpointedFunction> freshOperator = new AbstractUdfStreamOperator<>(freshFn) {
            private static final long serialVersionUID = 1L;
        };

        MemoryStateBackend freshBackend = new MemoryStateBackend();
        freshOperator.setStateBackend(freshBackend);

        // Open to create operator state backend
        freshOperator.open();

        // Restore operator state into fresh operator's backend
        assertNotNull(freshOperator.getOperatorStateBackend());
        freshOperator.getOperatorStateBackend().restoreState(snapshotResult);

        // Now initialize state — isRestored=true, reads from backend
        TaskStateSnapshot taskSnapshot = new TaskStateSnapshot(null, 42L);
        taskSnapshot.putOperatorState("items", snapshotResult.getOperatorState("items"));
        freshOperator.initializeState(taskSnapshot);

        // Verify state contents match
        assertEquals(3, restoredItems.size());
        assertTrue(restoredItems.containsAll(List.of("a", "b", "c")));
    }

    @Test
    void testOpenCreatesOperatorStateBackend() throws Exception {
        AbstractStreamOperator<Void> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        assertNull(operator.getOperatorStateBackend());

        operator.setStateBackend(new MemoryStateBackend());
        operator.open();

        assertNotNull(operator.getOperatorStateBackend());
    }

    @Test
    void testRestoreStateRestoresOperatorState() throws Exception {
        AbstractStreamOperator<Void> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        operator.setStateBackend(new MemoryStateBackend());
        operator.open();

        assertNotNull(operator.getOperatorStateBackend());

        // Directly put state into the backend
        operator.getOperatorStateBackend().putRawState("test-key", "test-value");

        // Snapshot
        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapResult = operator.snapshotState(ctx);
        assertNotNull(snapResult.getOperatorState("test-key"));

        // Create fresh operator
        AbstractStreamOperator<Void> freshOperator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        freshOperator.setStateBackend(new MemoryStateBackend());
        freshOperator.open();

        // Restore
        freshOperator.restoreState(snapResult);

        // Verify state is restored
        OperatorSnapshotResult freshSnap = freshOperator.snapshotState(ctx);
        assertEquals("test-value", freshSnap.getOperatorState("test-key"));
    }
}
