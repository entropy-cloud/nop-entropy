/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 → Debezium offset 种子（设计 §4.2 契约级新增要求的实现面）：mysql/pg 位点 JSON 形态、
 * 序列化契约与 {@code NopStreamOffsetBackingStore.toSerializable} 一致（base64）、种子可注入
 * 共享 registry（引擎侧同 connector 名绑定同一 map）、pg LSN 数值换算。
 */
public class TestCdcOffsetSeedBuilder {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMysqlSeedShape() {
        SourcePosition p0 = SourcePosition.mysql("binlog.000003", 1042L, "gtid-set-1");
        Map<ByteBuffer, ByteBuffer> seed = CdcOffsetSeedBuilder.buildSeed(p0, "mig-order");

        assertEquals(1, seed.size());
        Map.Entry<ByteBuffer, ByteBuffer> entry = seed.entrySet().iterator().next();
        String key = new String(toBytes(entry.getKey()), StandardCharsets.UTF_8);
        String value = new String(toBytes(entry.getValue()), StandardCharsets.UTF_8);
        assertEquals("{\"server\":\"mig-order\"}", key);
        assertTrue(value.contains("\"file\":\"binlog.000003\""), value);
        assertTrue(value.contains("\"pos\":1042"), value);
        assertTrue(value.contains("\"gtids\":\"gtid-set-1\""), value);
    }

    @Test
    public void testSerializedSeedRoundTripsThroughOffsetStore() {
        SourcePosition p0 = SourcePosition.mysql("binlog.000009", 77L, null);
        Map<String, String> serialized = CdcOffsetSeedBuilder.buildSerializedSeed("j1", p0, "srv");

        // base64 序列化契约：解码后与 toSerializable 形态一致（同一 base64 编码语义）
        String key = new String(Base64.getDecoder().decode(serialized.keySet().iterator().next()),
                StandardCharsets.UTF_8);
        assertEquals("{\"server\":\"srv\"}", key);

        // 注入共享 registry（引擎侧经 configure(name) 绑定同一 map）后可读回原始位点
        String connector = "seed-roundtrip";
        try {
            NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(connector);
            store.setOffsets(NopStreamOffsetBackingStore.fromSerializable(serialized));
            Map<String, String> readBack = NopStreamOffsetBackingStore.toSerializable(store.getOffsets());
            assertEquals(serialized, readBack);
        } finally {
            NopStreamOffsetBackingStore.clearConnector(connector);
        }
    }

    @Test
    public void testPostgresSeedUsesNumericLsn() {
        SourcePosition p0 = SourcePosition.postgres("0/15D6620");
        Map<String, String> serialized = CdcOffsetSeedBuilder.buildSerializedSeed("j2", p0, "pg-srv");
        String value = new String(
                Base64.getDecoder().decode(serialized.values().iterator().next()),
                StandardCharsets.UTF_8);
        long expected = (0L << 32) | Long.parseLong("15D6620", 16);
        assertTrue(value.contains("\"lsn\":" + expected), value);
        assertEquals(expected, CdcOffsetSeedBuilder.parseLsn("0/15D6620"));
    }

    @Test
    public void testMissingP0FailsTyped() {
        assertThrows(NopException.class,
                () -> CdcOffsetSeedBuilder.buildSerializedSeed("j3", null, "srv"));
        // mysql 位点缺 file/pos 显式失败
        assertThrows(NopException.class, () -> CdcOffsetSeedBuilder.buildSerializedSeed("j3",
                SourcePosition.postgres(null), "srv"));
    }

    @Test
    public void testPositionJsonRoundTrip() {
        SourcePosition p0 = SourcePosition.mysql("binlog.000001", 4L, null);
        SourcePosition parsed = SourcePosition.fromJson(p0.toJson());
        assertEquals("mysql", parsed.getConnectorType());
        assertEquals("binlog.000001", parsed.getBinlogFile());
        assertEquals(4L, parsed.getBinlogPos());

        SourcePosition pg = SourcePosition.fromJson(SourcePosition.postgres("1/2").toJson());
        assertEquals("postgres", pg.getConnectorType());
        assertEquals("1/2", pg.getLsn());
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] arr = new byte[dup.remaining()];
        dup.get(arr);
        return arr;
    }
}
