/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for state backend with actual operators.
 */
public class TestStateBackendIntegration {

    private IStateBackend stateBackend;
    private IKeyedStateBackend<String> keyedStateBackend;

    @BeforeEach
    public void setUp() {
        stateBackend = new MemoryStateBackend();
        keyedStateBackend = stateBackend.createKeyedStateBackend(String.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (keyedStateBackend != null) {
            keyedStateBackend.close();
        }
    }

    @Test
    public void testValueStateWithSingleKey() throws Exception {
        keyedStateBackend.setCurrentKey("key1");
        ValueState<Integer> state = keyedStateBackend.getState(
                new ValueStateDescriptor<>("counter", Integer.class, 0));

        assertEquals(0, state.value(), "Initial state should be 0");

        state.update(10);
        assertEquals(10, state.value(), "State should update to 10");

        state.update(20);
        assertEquals(20, state.value(), "State should update to 20");
    }

    @Test
    public void testValueStateWithKeySwitching() throws Exception {
        ValueState<Integer> state = keyedStateBackend.getState(
                new ValueStateDescriptor<>("counter", Integer.class, 0));

        keyedStateBackend.setCurrentKey("key1");
        state.update(10);
        assertEquals(10, state.value(), "key1 state should be 10");

        keyedStateBackend.setCurrentKey("key2");
        state.update(20);
        assertEquals(20, state.value(), "key2 state should be 20");

        keyedStateBackend.setCurrentKey("key1");
        assertEquals(10, state.value(), "key1 state should remain 10");

        keyedStateBackend.setCurrentKey("key2");
        assertEquals(20, state.value(), "key2 state should remain 20");
    }

    @Test
    public void testValueStateClear() throws Exception {
        keyedStateBackend.setCurrentKey("key1");
        ValueState<Integer> state = keyedStateBackend.getState(
                new ValueStateDescriptor<>("counter", Integer.class, 0));

        state.update(100);
        assertEquals(100, state.value(), "State should be 100");

        state.clear();
        assertEquals(0, state.value(), "State should return to default value (0) after clear");

        state.update(5);
        assertEquals(5, state.value(), "State should start from default and update to 5");
    }

    @Test
    public void testMapStateWithKeySwitching() throws Exception {
        MapState<String, Integer> mapState = keyedStateBackend.getMapState(
                new MapStateDescriptor<>("counters", String.class, Integer.class));

        keyedStateBackend.setCurrentKey("key1");
        mapState.put("counter", 1);
        assertEquals(1, mapState.get("counter"));

        keyedStateBackend.setCurrentKey("key2");
        mapState.put("counter", 2);
        assertEquals(2, mapState.get("counter"));

        keyedStateBackend.setCurrentKey("key1");
        assertEquals(1, mapState.get("counter"));
    }

    @Test
    public void testMapStateIteration() throws Exception {
        keyedStateBackend.setCurrentKey("key1");
        MapState<String, Integer> mapState = keyedStateBackend.getMapState(
                new MapStateDescriptor<>("counters", String.class, Integer.class));

        mapState.put("a", 1);
        mapState.put("b", 2);
        mapState.put("c", 3);

        int entryCount = 0;
        for (Map.Entry<String, Integer> entry : mapState.entries()) {
            entryCount++;
            assertNotNull(entry.getKey(), "Entry key should not be null");
            assertNotNull(entry.getValue(), "Entry value should not be null");
        }
        assertEquals(3, entryCount, "Should iterate over 3 entries");

        int keyCount = 0;
        for (String key : mapState.keys()) {
            keyCount++;
            assertTrue(key.equals("a") || key.equals("b") || key.equals("c"));
        }
        assertEquals(3, keyCount, "Should iterate over 3 keys");

        int valueCount = 0;
        for (Integer value : mapState.values()) {
            valueCount++;
            assertTrue(value >= 1 && value <= 3);
        }
        assertEquals(3, valueCount, "Should iterate over 3 values");
    }

    @Test
    public void testMapStateOperations() throws Exception {
        keyedStateBackend.setCurrentKey("key1");
        MapState<String, Integer> mapState = keyedStateBackend.getMapState(
                new MapStateDescriptor<>("counters", String.class, Integer.class));

        mapState.put("a", 1);
        assertTrue(mapState.contains("a"), "Should contain key 'a'");
        assertFalse(mapState.isEmpty(), "Should not be empty");

        mapState.put("a", 10);
        assertEquals(10, mapState.get("a"), "Should update value for key 'a'");

        mapState.remove("a");
        assertFalse(mapState.contains("a"), "Should not contain key 'a' after removal");
        assertNull(mapState.get("a"), "Should return null for removed key");

        assertTrue(mapState.isEmpty(), "Should be empty after removing all entries");
    }

    @Test
    public void testMultipleStateTypes() throws Exception {
        keyedStateBackend.setCurrentKey("key1");

        ValueState<Integer> valueState = keyedStateBackend.getState(
                new ValueStateDescriptor<>("counter", Integer.class, 0));

        MapState<String, Integer> mapState = keyedStateBackend.getMapState(
                new MapStateDescriptor<>("counters", String.class, Integer.class));

        valueState.update(10);
        mapState.put("a", 1);
        mapState.put("b", 2);

        assertEquals(10, valueState.value());
        assertEquals(1, mapState.get("a"));
        assertEquals(2, mapState.get("b"));

        valueState.update(11);
        assertEquals(11, valueState.value());

        mapState.put("a", 100);
        assertEquals(100, mapState.get("a"));

        assertEquals(11, valueState.value());
    }

    @Test
    public void testNamespaceSwitching() throws Exception {
        keyedStateBackend.setCurrentKey("key1");
        keyedStateBackend.setCurrentNamespace("ns1");

        ValueState<Integer> state1 = keyedStateBackend.getState(
                new ValueStateDescriptor<>("counter", Integer.class, 0));

        state1.update(100);
        assertEquals(100, state1.value());

        keyedStateBackend.setCurrentNamespace("ns2");
        ValueState<Integer> state2 = keyedStateBackend.getState(
                new ValueStateDescriptor<>("counter", Integer.class, 0));

        state2.update(200);
        assertEquals(200, state2.value());

        keyedStateBackend.setCurrentNamespace("ns1");
        assertEquals(100, state1.value());

        keyedStateBackend.setCurrentNamespace("ns2");
        assertEquals(200, state2.value());
    }
}
