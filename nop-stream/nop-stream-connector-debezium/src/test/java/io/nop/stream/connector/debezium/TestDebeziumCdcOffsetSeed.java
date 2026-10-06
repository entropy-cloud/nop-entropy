/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 数据搬迁契约（ai-dev/design/nop-orm-data-migration-pipeline-design.md §4.2）：
 * CDC 源首跑 offset 种子化——"首跑 offset 可种子化为编排器钉定的 P0"是本设计对 offset 存储
 * 初始化提出的契约级新增要求。
 *
 * <p>覆盖：种子注入后首跑 offset store 内容=种子；AR-03 不注入时清空行为逐字节不变；
 * 恢复路径（checkpoint 已有 CDC offset）种子不生效——checkpoint 是真相源；空 snapshot
 * （无 CDC entry）路径种子生效（core runtime 首跑传非 null 空 snapshot）。</p>
 */
public class TestDebeziumCdcOffsetSeed {

    private static final String CONNECTOR = "test-seed-cdc";

    @AfterEach
    void cleanup() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
    }

    private DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(CONNECTOR);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath("/tmp/" + CONNECTOR + "/schema-history.dat");
        return config;
    }

    private Map<String, String> seed(String file, long pos) {
        String key = "{\"server\":\"mig-order\"}";
        String value = "{\"file\":\"" + file + "\",\"pos\":" + pos + ",\"row\":1}";
        Map<String, String> seed = new LinkedHashMap<>();
        seed.put(Base64.encode(key), Base64.encode(value));
        return seed;
    }

    /** 与 NopStreamOffsetBackingStore 相同的 base64 编码语义（UTF-8 字节）。 */
    private static final class Base64 {
        static String encode(String text) {
            return java.util.Base64.getEncoder()
                    .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String decoded(Map<String, String> serialized, String keyPrefix) {
        for (Map.Entry<String, String> entry : serialized.entrySet()) {
            String key = new String(java.util.Base64.getDecoder().decode(entry.getKey()),
                    StandardCharsets.UTF_8);
            if (key.startsWith(keyPrefix))
                return new String(java.util.Base64.getDecoder().decode(entry.getValue()),
                        StandardCharsets.UTF_8);
        }
        return null;
    }

    @Test
    void testSeededFirstRunCarriesSeedInOffsetStore() throws Exception {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.setInitialOffsets(seed("binlog.000004", 2048L));

        source.initializeState(null); // first run → clear stale registry, then seed P0

        NopStreamOffsetBackingStore store = source.getOffsetStore();
        assertNotNull(store);
        Map<String, String> offsets = NopStreamOffsetBackingStore.toSerializable(store.getOffsets());
        assertEquals(1, offsets.size());
        String value = decoded(offsets, "{\"server\"");
        assertNotNull(value);
        assertTrue(value.contains("\"file\":\"binlog.000004\""), value);
        assertTrue(value.contains("\"pos\":2048"), value);
    }

    @Test
    void testFirstRunWithoutSeedKeepsAr03EmptyStore() throws Exception {
        // AR-03 既有语义不回归：不注入种子时首跑 store 为空
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.initializeState(null);
        NopStreamOffsetBackingStore store = source.getOffsetStore();
        assertNotNull(store);
        assertTrue(store.getOffsets().isEmpty());
    }

    @Test
    void testSeedSupersedesStaleSameJvmRegistryEntry() throws Exception {
        // 模拟同 JVM 上一轮 run 遗留的陈旧 offset（AR-03 场景）
        NopStreamOffsetBackingStore stale = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        Map<ByteBuffer, ByteBuffer> staleOffsets = new LinkedHashMap<>();
        staleOffsets.put(ByteBuffer.wrap("stale-partition".getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap("stale-offset".getBytes(StandardCharsets.UTF_8)));
        stale.setOffsets(staleOffsets);

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.setInitialOffsets(seed("binlog.000006", 8L));
        source.initializeState(null);

        // REPLACE 语义：种子决定性地取代陈旧 registry 内容（setOffsets 先 clear）
        Map<String, String> offsets = NopStreamOffsetBackingStore.toSerializable(
                source.getOffsetStore().getOffsets());
        assertNull(decoded(offsets, "stale"));
        assertEquals(1, offsets.size());
    }

    @Test
    void testCheckpointRestoreIgnoresSeed() throws Exception {
        // 恢复路径：checkpoint 已有 CDC offset entry → checkpoint 是真相源，种子不生效
        DebeziumCdcSourceFunction first = new DebeziumCdcSourceFunction(newConfig());
        first.initializeState(null);
        NopStreamOffsetBackingStore store = first.getOffsetStore();
        Map<ByteBuffer, ByteBuffer> checkpointed = new LinkedHashMap<>();
        checkpointed.put(
                ByteBuffer.wrap("{\"server\":\"mig-order\"}".getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap("{\"file\":\"checkpointed.bin\", \"pos\":99}"
                        .getBytes(StandardCharsets.UTF_8)));
        store.setOffsets(checkpointed);
        OperatorSnapshotResult snapshot = first.snapshotState(1L);

        TaskStateSnapshot state = new TaskStateSnapshot(
                new io.nop.stream.core.checkpoint.TaskLocation("", "", "", 0));
        for (Map.Entry<String, Object> entry : snapshot.getOperatorStates().entrySet()) {
            state.putOperatorState(entry.getKey(), entry.getValue());
        }

        DebeziumCdcSourceFunction restored = new DebeziumCdcSourceFunction(newConfig());
        restored.setInitialOffsets(seed("binlog.000007", 555L));
        restored.initializeState(state);

        Map<String, String> offsets = NopStreamOffsetBackingStore.toSerializable(
                restored.getOffsetStore().getOffsets());
        String value = decoded(offsets, "{\"server\"");
        assertNotNull(value);
        assertTrue(value.contains("checkpointed.bin"), value);
        assertTrue(value.contains("\"pos\":99"), value);
    }

    @Test
    void testEmptySnapshotPathAppliesSeed() throws Exception {
        // core runtime 首跑（StreamSourceOperator）传非 null 空 snapshot：无 CDC offset truth，
        // 种子同样生效（否则部署面首跑种子化失效）
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.setInitialOffsets(seed("binlog.000008", 64L));
        source.initializeState(new TaskStateSnapshot(new io.nop.stream.core.checkpoint.TaskLocation("", "", "", 0)));

        Map<String, String> offsets = NopStreamOffsetBackingStore.toSerializable(
                source.getOffsetStore().getOffsets());
        String value = decoded(offsets, "{\"server\"");
        assertNotNull(value);
        assertTrue(value.contains("\"file\":\"binlog.000008\""), value);
    }

    @Test
    void testEmptySnapshotWithoutSeedKeepsEmptyStore() throws Exception {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.initializeState(new TaskStateSnapshot(new io.nop.stream.core.checkpoint.TaskLocation("", "", "", 0)));
        assertTrue(source.getOffsetStore().getOffsets().isEmpty());
    }

    @Test
    void testSeedSurvivesSerializationRoundTrip() throws Exception {
        // 种子随函数序列化（跨 JVM 部署描述符）存活
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.setInitialOffsets(seed("binlog.000010", 3L));

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(source);
        }
        DebeziumCdcSourceFunction copy;
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            copy = (DebeziumCdcSourceFunction) ois.readObject();
        }
        Map<String, String> seedCopy = copy.getInitialOffsetsForTest();
        assertNotNull(seedCopy);
        assertEquals(1, seedCopy.size());
        copy.initializeState(null);
        String value = decoded(NopStreamOffsetBackingStore.toSerializable(
                copy.getOffsetStore().getOffsets()), "{\"server\"");
        assertTrue(value != null && value.contains("binlog.000010"));
    }

    /** 防 TreeMap 语义漂移的哨兵：toSerializable 返回 base64 键的稳定排序。 */
    @Test
    void testToSerializableSorted() {
        TreeMap<String, String> out = NopStreamOffsetBackingStore.toSerializable(
                Map.of(ByteBuffer.wrap("b".getBytes()), ByteBuffer.wrap("2".getBytes()),
                        ByteBuffer.wrap("a".getBytes()), ByteBuffer.wrap("1".getBytes())));
        assertEquals(java.util.Base64.getEncoder().encodeToString("a".getBytes()),
                out.firstKey());
        assertEquals(2, out.size());
    }
}
