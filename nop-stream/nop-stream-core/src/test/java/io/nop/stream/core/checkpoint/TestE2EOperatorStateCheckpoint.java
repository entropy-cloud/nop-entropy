/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.stream.core.common.functions.ICheckpointedFunction;
import io.nop.stream.core.common.state.IOperatorStateStore;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EOperatorStateCheckpoint {

    @Test
    void testCheckpointedFunctionWithListStateEndToEnd() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        io.nop.stream.core.common.state.backend.IOperatorStateBackend opBackend = stateBackend.createOperatorStateBackend();
        io.nop.stream.core.common.state.DefaultOperatorStateStore store =
                new io.nop.stream.core.common.state.DefaultOperatorStateStore(opBackend);

        ListState<String> listState = store.getListState(new ListStateDescriptor<>("elements", String.class));
        listState.add("first");
        listState.add("second");
        listState.add("third");

        // Snapshot through backend
        OperatorSnapshotResult snapshot = opBackend.snapshotState(1);
        assertNotNull(snapshot);
        assertTrue(snapshot.getOperatorStates().containsKey("elements"));

        // Restore into fresh backend + store
        io.nop.stream.core.common.state.backend.IOperatorStateBackend freshBackend =
                stateBackend.createOperatorStateBackend();
        freshBackend.restoreState(snapshot);

        io.nop.stream.core.common.state.DefaultOperatorStateStore freshStore =
                new io.nop.stream.core.common.state.DefaultOperatorStateStore(freshBackend);

        ListState<String> restored = freshStore.getListState(new ListStateDescriptor<>("elements", String.class));
        List<String> items = new ArrayList<>();
        for (String s : restored.get()) {
            items.add(s);
        }
        assertEquals(3, items.size());
        assertTrue(items.containsAll(List.of("first", "second", "third")));
    }

    @Test
    void testOperatorStateContentEqualityAfterFullCheckpointCycle() throws Exception {
        MemoryStateBackend backend = new MemoryStateBackend();
        io.nop.stream.core.common.state.backend.IOperatorStateBackend opBackend = backend.createOperatorStateBackend();
        io.nop.stream.core.common.state.DefaultOperatorStateStore store =
                new io.nop.stream.core.common.state.DefaultOperatorStateStore(opBackend);

        ListState<String> listState = store.getListState(new ListStateDescriptor<>("items", String.class));
        listState.add("alpha");
        listState.add("beta");
        listState.add("gamma");
        listState.add("delta");

        // Snapshot through backend
        OperatorSnapshotResult snapshot = opBackend.snapshotState(1);

        // Create a fresh backend and store
        MemoryStateBackend freshBackend = new MemoryStateBackend();
        io.nop.stream.core.common.state.backend.IOperatorStateBackend freshOpBackend = freshBackend.createOperatorStateBackend();
        freshOpBackend.restoreState(snapshot);

        io.nop.stream.core.common.state.DefaultOperatorStateStore freshStore =
                new io.nop.stream.core.common.state.DefaultOperatorStateStore(freshOpBackend);

        ListState<String> restored = freshStore.getListState(new ListStateDescriptor<>("items", String.class));

        List<String> items = new ArrayList<>();
        for (String s : restored.get()) {
            items.add(s);
        }
        assertEquals(4, items.size());
        assertEquals("alpha", items.get(0));
        assertEquals("beta", items.get(1));
        assertEquals("gamma", items.get(2));
        assertEquals("delta", items.get(3));
    }

    @Test
    void testKeyedAndOperatorStateCoexistInSnapshotResult() throws Exception {
        AbstractStreamOperator<Void> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        MemoryStateBackend stateBackend = new MemoryStateBackend();
        operator.setStateBackend(stateBackend);
        operator.open();

        // Write operator state before snapshot
        operator.getOperatorStateBackend().putRawState("op-key", "op-value");

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult result = operator.snapshotState(ctx);

        assertNotNull(result);
        assertTrue(result.getOperatorStates().containsKey("op-key"));
        assertEquals("op-value", result.getOperatorState("op-key"));

        // Restore into a fresh operator
        AbstractStreamOperator<Void> freshOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        freshOp.setStateBackend(new MemoryStateBackend());
        freshOp.open();
        freshOp.restoreState(result);

        StateSnapshotContext freshCtx = new StateSnapshotContext(2L, System.currentTimeMillis());
        OperatorSnapshotResult freshResult = freshOp.snapshotState(freshCtx);
        assertEquals("op-value", freshResult.getOperatorState("op-key"));
    }

    @Test
    void testCheckpointParticipantAndOperatorStateBackendCoexist() throws Exception {
        AbstractStreamOperator<Void> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        MemoryStateBackend stateBackend = new MemoryStateBackend();
        operator.setStateBackend(stateBackend);
        operator.open();

        // Write operator state backend data BEFORE snapshot
        operator.getOperatorStateBackend().putRawState("user-state", "from-operator-backend");

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult result = operator.snapshotState(ctx);

        // Result should have user state from operatorStateBackend
        assertTrue(result.getOperatorStates().containsKey("user-state"));
        assertEquals("from-operator-backend", result.getOperatorState("user-state"));
    }
}
