/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryStateBackend 单元测试
 */
public class TestMemoryStateBackend {

    private IStateBackend stateBackend;
    private IKeyedStateBackend<String> keyedBackend;

    @BeforeEach
    public void setUp() {
        stateBackend = new MemoryStateBackend();
        keyedBackend = stateBackend.createKeyedStateBackend(String.class);
    }

    @AfterEach
    public void tearDown() {
        if (keyedBackend != null) {
            keyedBackend.close();
        }
    }

    @Test
    public void testStateBackendName() {
        assertEquals("MemoryStateBackend", stateBackend.getName());
    }

    @Test
    public void testValueStateBasic() throws IOException {
        // 获取 ValueState
        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = keyedBackend.getState(descriptor);

        // 设置当前 key
        keyedBackend.setCurrentKey("user123");

        // 初始值应该是默认值
        assertEquals(Long.valueOf(0L), state.value());

        // 更新值
        state.update(100L);
        assertEquals(Long.valueOf(100L), state.value());

        // 再次更新
        state.update(200L);
        assertEquals(Long.valueOf(200L), state.value());

        // 清空
        state.clear();
        assertEquals(Long.valueOf(0L), state.value()); // 清空后应该返回默认值
    }

    @Test
    public void testValueStateWithDifferentKeys() throws IOException {
        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = keyedBackend.getState(descriptor);

        // 设置 key1 的值
        keyedBackend.setCurrentKey("user1");
        state.update(100L);
        assertEquals(Long.valueOf(100L), state.value());

        // 切换到 key2，值应该是默认值
        keyedBackend.setCurrentKey("user2");
        assertEquals(Long.valueOf(0L), state.value());
        state.update(200L);
        assertEquals(Long.valueOf(200L), state.value());

        // 切换回 key1，值应该保持不变
        keyedBackend.setCurrentKey("user1");
        assertEquals(Long.valueOf(100L), state.value());
    }

    @Test
    public void testValueStateWithNamespace() throws IOException {
        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>("sum", Long.class, 0L);
        ValueState<Long> state = keyedBackend.getState(descriptor);

        keyedBackend.setCurrentKey("user1");

        // namespace1
        keyedBackend.setCurrentNamespace("window-1h");
        state.update(100L);
        assertEquals(Long.valueOf(100L), state.value());

        // namespace2（不同的 namespace）
        keyedBackend.setCurrentNamespace("window-2h");
        assertEquals(Long.valueOf(0L), state.value()); // 不同 namespace，应该是默认值
        state.update(200L);

        // 切换回 namespace1
        keyedBackend.setCurrentNamespace("window-1h");
        assertEquals(Long.valueOf(100L), state.value());
    }

    @Test
    public void testMapStateBasic() throws IOException {
        MapStateDescriptor<String, Long> descriptor = new MapStateDescriptor<>("counts", String.class, Long.class);
        MapState<String, Long> state = keyedBackend.getMapState(descriptor);

        keyedBackend.setCurrentKey("user123");

        // 初始状态应该是空的
        assertTrue(state.isEmpty());

        // 添加值
        state.put("a", 1L);
        state.put("b", 2L);
        state.put("c", 3L);

        // 获取值
        assertEquals(Long.valueOf(1L), state.get("a"));
        assertEquals(Long.valueOf(2L), state.get("b"));
        assertEquals(Long.valueOf(3L), state.get("c"));
        assertNull(state.get("d")); // 不存在的 key

        // 检查包含
        assertTrue(state.contains("a"));
        assertFalse(state.contains("d"));

        // 检查大小
        assertFalse(state.isEmpty());
    }

    @Test
    public void testMapStateOperations() throws IOException {
        MapStateDescriptor<String, Long> descriptor = new MapStateDescriptor<>("counts", String.class, Long.class);
        MapState<String, Long> state = keyedBackend.getMapState(descriptor);

        keyedBackend.setCurrentKey("user123");

        // 添加多个值
        Map<String, Long> map = new HashMap<>();
        map.put("x", 10L);
        map.put("y", 20L);
        state.putAll(map);

        // 更新单个值
        state.put("x", 15L);
        assertEquals(Long.valueOf(15L), state.get("x"));

        // 删除值
        state.remove("y");
        assertNull(state.get("y"));

        // 清空
        state.clear();
        assertTrue(state.isEmpty());
    }

    @Test
    public void testMapStateWithDifferentKeys() throws IOException {
        MapStateDescriptor<String, Long> descriptor = new MapStateDescriptor<>("counts", String.class, Long.class);
        MapState<String, Long> state = keyedBackend.getMapState(descriptor);

        // key1
        keyedBackend.setCurrentKey("user1");
        state.put("a", 1L);
        state.put("b", 2L);

        // key2
        keyedBackend.setCurrentKey("user2");
        assertTrue(state.isEmpty());
        state.put("c", 3L);

        // 切换回 key1
        keyedBackend.setCurrentKey("user1");
        assertEquals(Long.valueOf(1L), state.get("a"));
        assertEquals(Long.valueOf(2L), state.get("b"));
        assertNull(state.get("c"));
    }

    @Test
    public void testMultipleStates() throws IOException {
        // 获取多个不同的状态
        ValueState<Long> countState = keyedBackend.getState(new ValueStateDescriptor<>("count", Long.class, 0L));
        ValueState<String> nameState = keyedBackend.getState(new ValueStateDescriptor<>("name", String.class));
        MapState<String, Long> mapState = keyedBackend.getMapState(new MapStateDescriptor<>("map", String.class, Long.class));

        keyedBackend.setCurrentKey("user1");

        // 操作不同的状态
        countState.update(100L);
        nameState.update("Alice");
        mapState.put("key1", 1L);

        // 验证各个状态独立
        assertEquals(Long.valueOf(100L), countState.value());
        assertEquals("Alice", nameState.value());
        assertEquals(Long.valueOf(1L), mapState.get("key1"));
    }

    @Test
    public void testDefaultValueNull() throws IOException {
        ValueStateDescriptor<String> descriptor = new ValueStateDescriptor<>("name", String.class);
        ValueState<String> state = keyedBackend.getState(descriptor);

        keyedBackend.setCurrentKey("user1");
        assertNull(state.value()); // 默认值应该是 null

        state.update("Bob");
        assertEquals("Bob", state.value());

        state.update(null); // 更新为 null 应该清空状态
        assertNull(state.value());
    }

    @Test
    public void testGetCurrentKeyAndNamespace() {
        keyedBackend.setCurrentKey("test-key");
        assertEquals("test-key", keyedBackend.getCurrentKey());

        keyedBackend.setCurrentNamespace("test-namespace");
        assertEquals("test-namespace", keyedBackend.getCurrentNamespace());

        // 测试 null namespace 应该设置为默认值
        keyedBackend.setCurrentNamespace(null);
        assertEquals(IKeyedStateBackend.DEFAULT_NAMESPACE, keyedBackend.getCurrentNamespace());
    }
}
