/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ARG_DETAIL;
import static io.nop.orm.migration.MigrationErrors.ARG_JOB_ID;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_P0_NOT_PINNED;

/**
 * P0 → Debezium CDC offset 种子（设计 §4.2 契约级新增要求的实现面）。
 *
 * <p>把 PREPARE 钉定的位点转换为 Debezium embedded engine 的 offset backing store 条目形态：
 * key = 源分区 JSON（{@code {"server":"<serverName>"}}），value = 位点 JSON。
 * 序列化契约与 {@code NopStreamOffsetBackingStore.toSerializable} 一致（UTF-8 字节 → base64），
 * 由 {@code DebeziumCdcSourceFunction} 的首跑种子化路径注入 registry，配合
 * {@code snapshot.mode=never} 让引擎从 P0 起全序重放，不做 initial snapshot。</p>
 *
 * <p>位点键遵循 Debezium 各连接器的 offset 契约：
 * mysql = file/pos（+ gtids 可选）+ row/event/ts_sec/server_id 缺省；postgres = lsn（数值形态，
 * {@code X/Y} 十六进制 → {@code (X<<32)|Y}）+ txId/ts。</p>
 */
public final class CdcOffsetSeedBuilder {

    private CdcOffsetSeedBuilder() {
    }

    /**
     * 构造首跑种子（可序列化形态，直接供 {@code DebeziumCdcSourceFunction.setInitialOffsets}）。
     *
     * @param jobId      编排作业标识（错误定位）
     * @param position   PREPARE 钉定的 P0
     * @param serverName Debezium serverName（offset 分区键）
     */
    public static Map<String, String> buildSerializedSeed(String jobId, SourcePosition position,
                                                          String serverName) {
        require(position, jobId);
        Map<ByteBuffer, ByteBuffer> raw = buildSeed(position, serverName);
        Map<String, String> serialized = new LinkedHashMap<>();
        for (Map.Entry<ByteBuffer, ByteBuffer> entry : raw.entrySet()) {
            serialized.put(encode(entry.getKey()), encode(entry.getValue()));
        }
        return serialized;
    }

    public static Map<ByteBuffer, ByteBuffer> buildSeed(SourcePosition position, String serverName) {
        String partitionJson = "{\"server\":" + quote(serverName) + "}";
        String offsetJson = offsetJson(position);
        Map<ByteBuffer, ByteBuffer> seed = new LinkedHashMap<>();
        seed.put(bytes(partitionJson), bytes(offsetJson));
        return seed;
    }

    private static String offsetJson(SourcePosition position) {
        StringBuilder sb = new StringBuilder("{");
        if (SourcePosition.TYPE_MYSQL.equalsIgnoreCase(position.getConnectorType())) {
            String binlogFile = requireValue(position.getBinlogFile(), "binlogFile");
            sb.append("\"file\":").append(quote(binlogFile));
            sb.append(",\"pos\":").append(position.getBinlogPos());
            sb.append(",\"row\":1,\"event\":1,\"server_id\":0,\"ts_sec\":0");
            if (position.getGtidSet() != null && !position.getGtidSet().isEmpty())
                sb.append(",\"gtids\":").append(quote(position.getGtidSet()));
        } else if (SourcePosition.TYPE_POSTGRES.equalsIgnoreCase(position.getConnectorType())) {
            sb.append("\"lsn\":").append(parseLsn(position.getLsn()));
            sb.append(",\"txId\":0,\"ts\":0");
        } else {
            throw new IllegalArgumentException("unsupported connector type: " + position.getConnectorType());
        }
        return sb.append("}").toString();
    }

    static long parseLsn(String lsn) {
        if (lsn == null || lsn.isEmpty())
            throw new IllegalArgumentException("postgres LSN is required for the offset seed");
        int slash = lsn.indexOf('/');
        if (slash < 0)
            return Long.parseLong(lsn);
        long high = Long.parseLong(lsn.substring(0, slash), 16);
        long low = Long.parseLong(lsn.substring(slash + 1), 16);
        return (high << 32) | low;
    }

    private static void require(SourcePosition position, String jobId) {
        if (position == null)
            throw new NopException(ERR_MIG_P0_NOT_PINNED).param(ARG_JOB_ID, jobId)
                    .param(ARG_DETAIL, "position P0 is null");
        if (SourcePosition.TYPE_MYSQL.equalsIgnoreCase(position.getConnectorType())
                && (position.getBinlogFile() == null || position.getBinlogPos() == null))
            throw new NopException(ERR_MIG_P0_NOT_PINNED).param(ARG_JOB_ID, jobId)
                    .param(ARG_DETAIL, "mysql P0 requires binlogFile and binlogPos");
        if (SourcePosition.TYPE_POSTGRES.equalsIgnoreCase(position.getConnectorType())
                && (position.getLsn() == null || position.getLsn().isEmpty()))
            throw new NopException(ERR_MIG_P0_NOT_PINNED).param(ARG_JOB_ID, jobId)
                    .param(ARG_DETAIL, "postgres P0 requires lsn");
    }

    private static String requireValue(String value, String field) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException(field + " is required for the offset seed");
        return value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static ByteBuffer bytes(String json) {
        return ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] arr = new byte[dup.remaining()];
        dup.get(arr);
        return Base64.getEncoder().encodeToString(arr);
    }
}
