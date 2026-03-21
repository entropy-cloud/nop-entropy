/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskStateSnapshot {

    private TaskStateSnapshot snapshot;

    @BeforeEach
    void setUp() {
        snapshot = new TaskStateSnapshot(12345L);
    }

    @Test
    void testConstructor() {
        assertEquals(12345L, snapshot.getTaskId());
        assertTrue(snapshot.getOperatorStates().isEmpty());
        assertTrue(snapshot.getKeyedStates().isEmpty());
    }

    @Test
    void testPutAndGetOperatorState() {
        byte[] state1 = "operator-state-1".getBytes();
        byte[] state2 = "operator-state-2".getBytes();
        
        snapshot.putOperatorState("state1", state1);
        snapshot.putOperatorState("state2", state2);
        
        assertArrayEquals(state1, snapshot.getOperatorState("state1"));
        assertArrayEquals(state2, snapshot.getOperatorState("state2"));
        assertNull(snapshot.getOperatorState("nonexistent"));
        
        assertEquals(2, snapshot.getOperatorStates().size());
    }

    @Test
    void testPutAndGetKeyedState() {
        byte[] state1 = "keyed-state-1".getBytes();
        byte[] state2 = "keyed-state-2".getBytes();
        
        snapshot.putKeyedState("key1", state1);
        snapshot.putKeyedState("key2", state2);
        
        assertArrayEquals(state1, snapshot.getKeyedState("key1"));
        assertArrayEquals(state2, snapshot.getKeyedState("key2"));
        assertNull(snapshot.getKeyedState("nonexistent"));
        
        assertEquals(2, snapshot.getKeyedStates().size());
    }

    @Test
    void testIsEmpty() {
        assertTrue(snapshot.isEmpty());
        
        snapshot.putOperatorState("state", "data".getBytes());
        assertFalse(snapshot.isEmpty());
    }

    @Test
    void testGetStateCount() {
        assertEquals(0, snapshot.getStateCount());
        
        snapshot.putOperatorState("op1", "data1".getBytes());
        assertEquals(1, snapshot.getStateCount());
        
        snapshot.putKeyedState("key1", "data2".getBytes());
        assertEquals(2, snapshot.getStateCount());
        
        snapshot.putKeyedState("key2", "data3".getBytes());
        assertEquals(3, snapshot.getStateCount());
    }

    @Test
    void testEstimateSize() {
        assertEquals(0, snapshot.estimateSize());
        
        byte[] data1 = new byte[100];
        byte[] data2 = new byte[200];
        
        snapshot.putOperatorState("op1", data1);
        assertEquals(100, snapshot.estimateSize());
        
        snapshot.putKeyedState("key1", data2);
        assertEquals(300, snapshot.estimateSize());
    }

    @Test
    void testEmptyFactory() {
        TaskStateSnapshot empty = TaskStateSnapshot.empty(999L);
        assertEquals(999L, empty.getTaskId());
        assertTrue(empty.isEmpty());
    }

    @Test
    void testBuilder() {
        byte[] opState = "operator".getBytes();
        byte[] keyState = "keyed".getBytes();
        
        TaskStateSnapshot built = TaskStateSnapshot.builder(777L)
                .putOperatorState("op1", opState)
                .putKeyedState("key1", keyState)
                .build();
        
        assertEquals(777L, built.getTaskId());
        assertArrayEquals(opState, built.getOperatorState("op1"));
        assertArrayEquals(keyState, built.getKeyedState("key1"));
    }

    @Test
    void testSerialization() throws Exception {
        snapshot.putOperatorState("op1", "operator-data".getBytes());
        snapshot.putKeyedState("key1", "keyed-data".getBytes());
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(snapshot);
        oos.close();
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        TaskStateSnapshot deserialized = (TaskStateSnapshot) ois.readObject();
        
        assertEquals(snapshot.getTaskId(), deserialized.getTaskId());
        assertArrayEquals(snapshot.getOperatorState("op1"), deserialized.getOperatorState("op1"));
        assertArrayEquals(snapshot.getKeyedState("key1"), deserialized.getKeyedState("key1"));
    }
}
