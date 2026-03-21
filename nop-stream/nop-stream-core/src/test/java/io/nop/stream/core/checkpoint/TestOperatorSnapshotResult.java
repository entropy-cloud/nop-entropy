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

class TestOperatorSnapshotResult {

    private OperatorSnapshotResult result;

    @BeforeEach
    void setUp() {
        result = new OperatorSnapshotResult();
    }

    @Test
    void testEmptyFactory() {
        OperatorSnapshotResult empty = OperatorSnapshotResult.empty();
        assertTrue(empty.isEmpty());
        assertTrue(empty.getOperatorStates().isEmpty());
        assertTrue(empty.getKeyedStates().isEmpty());
        assertTrue(empty.getRawKeyedStates().isEmpty());
    }

    @Test
    void testEmptySingleton() {
        OperatorSnapshotResult empty1 = OperatorSnapshotResult.empty();
        OperatorSnapshotResult empty2 = OperatorSnapshotResult.empty();
        assertSame(empty1, empty2);
    }

    @Test
    void testPutOperatorState() {
        byte[] state = "operator-state".getBytes();
        result.putOperatorState("op1", state);
        
        assertArrayEquals(state, result.getOperatorStates().get("op1"));
        assertEquals(1, result.getOperatorStates().size());
    }

    @Test
    void testPutKeyedState() {
        byte[] state = "keyed-state".getBytes();
        result.putKeyedState("key1", state);
        
        assertArrayEquals(state, result.getKeyedStates().get("key1"));
        assertEquals(1, result.getKeyedStates().size());
    }

    @Test
    void testPutRawKeyedState() {
        byte[] state = "raw-keyed-state".getBytes();
        result.putRawKeyedState("raw1", state);
        
        assertArrayEquals(state, result.getRawKeyedStates().get("raw1"));
        assertEquals(1, result.getRawKeyedStates().size());
    }

    @Test
    void testIsEmpty() {
        assertTrue(result.isEmpty());
        
        result.putOperatorState("op1", "data".getBytes());
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetStateCount() {
        assertEquals(0, result.getStateCount());
        
        result.putOperatorState("op1", "data1".getBytes());
        assertEquals(1, result.getStateCount());
        
        result.putKeyedState("key1", "data2".getBytes());
        assertEquals(2, result.getStateCount());
        
        result.putRawKeyedState("raw1", "data3".getBytes());
        assertEquals(3, result.getStateCount());
    }

    @Test
    void testEstimateSize() {
        assertEquals(0, result.estimateSize());
        
        byte[] data1 = new byte[50];
        byte[] data2 = new byte[100];
        byte[] data3 = new byte[150];
        
        result.putOperatorState("op1", data1);
        assertEquals(50, result.estimateSize());
        
        result.putKeyedState("key1", data2);
        assertEquals(150, result.estimateSize());
        
        result.putRawKeyedState("raw1", data3);
        assertEquals(300, result.estimateSize());
    }

    @Test
    void testBuilder() {
        byte[] opState = "operator".getBytes();
        byte[] keyState = "keyed".getBytes();
        byte[] rawState = "raw".getBytes();
        
        OperatorSnapshotResult built = OperatorSnapshotResult.builder()
                .putOperatorState("op1", opState)
                .putKeyedState("key1", keyState)
                .putRawKeyedState("raw1", rawState)
                .build();
        
        assertFalse(built.isEmpty());
        assertArrayEquals(opState, built.getOperatorStates().get("op1"));
        assertArrayEquals(keyState, built.getKeyedStates().get("key1"));
        assertArrayEquals(rawState, built.getRawKeyedStates().get("raw1"));
    }

    @Test
    void testBuilderEmptyReturnsSingleton() {
        OperatorSnapshotResult built = OperatorSnapshotResult.builder().build();
        assertSame(OperatorSnapshotResult.empty(), built);
    }

    @Test
    void testSerialization() throws Exception {
        result.putOperatorState("op1", "operator-data".getBytes());
        result.putKeyedState("key1", "keyed-data".getBytes());
        result.putRawKeyedState("raw1", "raw-data".getBytes());
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(result);
        oos.close();
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        OperatorSnapshotResult deserialized = (OperatorSnapshotResult) ois.readObject();
        
        assertEquals(result.getStateCount(), deserialized.getStateCount());
        assertArrayEquals(result.getOperatorStates().get("op1"), 
                deserialized.getOperatorStates().get("op1"));
        assertArrayEquals(result.getKeyedStates().get("key1"), 
                deserialized.getKeyedStates().get("key1"));
        assertArrayEquals(result.getRawKeyedStates().get("raw1"), 
                deserialized.getRawKeyedStates().get("raw1"));
    }
}
