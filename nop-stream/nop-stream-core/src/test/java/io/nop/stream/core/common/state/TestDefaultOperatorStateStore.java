/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.common.state.backend.IOperatorStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryOperatorStateBackend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestDefaultOperatorStateStore {

    @Test
    void testGetListStateReturnsTypedListState() {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);
        ListStateDescriptor<String> descriptor = new ListStateDescriptor<>("test-list", String.class);
        ListState<String> listState = store.getListState(descriptor);
        assertNotNull(listState);
    }

    @Test
    void testAddGetClear() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);
        ListStateDescriptor<String> descriptor = new ListStateDescriptor<>("test-list", String.class);
        ListState<String> listState = store.getListState(descriptor);

        listState.add("a");
        listState.add("b");
        listState.add("c");

        List<String> items = new ArrayList<>();
        for (String s : listState.get()) {
            items.add(s);
        }
        assertEquals(3, items.size());
        assertTrue(items.contains("a"));
        assertTrue(items.contains("b"));
        assertTrue(items.contains("c"));

        listState.clear();
        List<String> afterClear = new ArrayList<>();
        for (String s : listState.get()) {
            afterClear.add(s);
        }
        assertTrue(afterClear.isEmpty());
    }

    @Test
    void testAddAll() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);
        ListStateDescriptor<Integer> descriptor = new ListStateDescriptor<>("nums", Integer.class);
        ListState<Integer> listState = store.getListState(descriptor);

        listState.addAll(List.of(1, 2, 3, 4, 5));

        List<Integer> items = new ArrayList<>();
        for (Integer i : listState.get()) {
            items.add(i);
        }
        assertEquals(5, items.size());
        assertTrue(items.containsAll(List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void testUpdate() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);
        ListStateDescriptor<String> descriptor = new ListStateDescriptor<>("update-test", String.class);
        ListState<String> listState = store.getListState(descriptor);

        listState.add("old1");
        listState.add("old2");
        listState.update(List.of("new1", "new2", "new3"));

        List<String> items = new ArrayList<>();
        for (String s : listState.get()) {
            items.add(s);
        }
        assertEquals(3, items.size());
        assertTrue(items.containsAll(List.of("new1", "new2", "new3")));
    }

    @Test
    void testMultipleNamedStatesCoexistIndependently() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);

        ListStateDescriptor<String> alphaDesc = new ListStateDescriptor<>("alpha", String.class);
        ListStateDescriptor<Integer> betaDesc = new ListStateDescriptor<>("beta", Integer.class);

        ListState<String> alpha = store.getListState(alphaDesc);
        ListState<Integer> beta = store.getListState(betaDesc);

        alpha.add("hello");
        alpha.add("world");

        beta.add(42);
        beta.add(99);

        List<String> alphaItems = new ArrayList<>();
        for (String s : alpha.get()) {
            alphaItems.add(s);
        }
        assertEquals(2, alphaItems.size());
        assertTrue(alphaItems.containsAll(List.of("hello", "world")));

        List<Integer> betaItems = new ArrayList<>();
        for (Integer i : beta.get()) {
            betaItems.add(i);
        }
        assertEquals(2, betaItems.size());
        assertTrue(betaItems.containsAll(List.of(42, 99)));
    }

    @Test
    void testSnapshotAndRestoreRoundTrip() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);
        ListStateDescriptor<String> descriptor = new ListStateDescriptor<>("roundtrip", String.class);
        ListState<String> listState = store.getListState(descriptor);

        listState.add("first");
        listState.add("second");
        listState.add("third");

        // Snapshot from the backend
        OperatorSnapshotResult snapshot = backend.snapshotState(1);
        assertNotNull(snapshot.getOperatorState("roundtrip"));

        // Restore into a fresh backend
        MemoryOperatorStateBackend freshBackend = new MemoryOperatorStateBackend();
        freshBackend.restoreState(snapshot);

        // Verify through a new store
        DefaultOperatorStateStore freshStore = new DefaultOperatorStateStore(freshBackend);
        ListState<String> restored = freshStore.getListState(descriptor);

        List<String> items = new ArrayList<>();
        for (String s : restored.get()) {
            items.add(s);
        }
        assertEquals(3, items.size());
        assertEquals("first", items.get(0));
        assertEquals("second", items.get(1));
        assertEquals("third", items.get(2));
    }

    @Test
    void testEmptyStoreSnapshotAndRestore() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);

        OperatorSnapshotResult snapshot = backend.snapshotState(1);
        assertTrue(snapshot.getOperatorStates().isEmpty());

        MemoryOperatorStateBackend freshBackend = new MemoryOperatorStateBackend();
        freshBackend.restoreState(snapshot);

        OperatorSnapshotResult result = freshBackend.snapshotState(1);
        assertTrue(result.getOperatorStates().isEmpty());
    }

    @Test
    void testOrderPreservedThroughSnapshotRestore() throws Exception {
        MemoryOperatorStateBackend backend = new MemoryOperatorStateBackend();
        DefaultOperatorStateStore store = new DefaultOperatorStateStore(backend);
        ListStateDescriptor<String> descriptor = new ListStateDescriptor<>("ordered", String.class);
        ListState<String> listState = store.getListState(descriptor);

        listState.add("a");
        listState.add("b");
        listState.add("c");
        listState.add("d");

        OperatorSnapshotResult snapshot = backend.snapshotState(1);

        MemoryOperatorStateBackend freshBackend = new MemoryOperatorStateBackend();
        freshBackend.restoreState(snapshot);

        DefaultOperatorStateStore freshStore = new DefaultOperatorStateStore(freshBackend);
        ListState<String> restored = freshStore.getListState(descriptor);

        List<String> items = new ArrayList<>();
        for (String s : restored.get()) {
            items.add(s);
        }
        assertEquals(4, items.size());
        assertEquals("a", items.get(0));
        assertEquals("b", items.get(1));
        assertEquals("c", items.get(2));
        assertEquals("d", items.get(3));
    }
}
