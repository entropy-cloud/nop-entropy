/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Debezium 事件转换器
 * <p>
 * 将 Debezium JSON 格式的事件转换为 ChangeEvent 对象
 */
public class DebeziumEventConverter {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumEventConverter.class);

    /**
     * 转换 Debezium 事件
     */
    public static ChangeEvent convert(io.debezium.engine.ChangeEvent<byte[], byte[]> event) {
        if (event.value() == null) {
            return null;
        }

        // 使用 JsonTool 解析 JSON
        String valueJson = new String(event.value(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> valueMap = (Map<String, Object>) JsonTool.parseNonStrict(valueJson);
        Map<String, Object> payload = valueMap.containsKey("payload")
                ? (Map<String, Object>) valueMap.get("payload")
                : valueMap;

        // 解析元数据
        ChangeEventMetadata metadata = parseMetadata(payload);
        if (metadata == null) {
            return null;
        }

        // 解析操作类型
        String operation = (String) payload.get("op");
        if (operation == null) {
            return null;
        }

        // 解析时间戳
        Long tsMs = (Long) payload.get("ts_ms");
        long timestamp = tsMs != null ? tsMs : System.currentTimeMillis();

        // 解析数据
        Map<String, Object> before = (Map<String, Object>) payload.get("before");
        Map<String, Object> after = (Map<String, Object>) payload.get("after");

        // 解析主键
        Map<String, Object> key = parseKey(event.key());

        return new ChangeEvent(metadata, operation, before, after, key, timestamp);
    }

    private static ChangeEventMetadata parseMetadata(Map<String, Object> payload) {
        Map<String, Object> source = (Map<String, Object>) payload.get("source");
        if (source == null) {
            return null;
        }

        String connector = (String) source.get("connector");
        String serverName = (String) source.get("name");
        String database = (String) source.get("db");
        String schema = (String) source.get("schema");
        String table = (String) source.get("table");

        // MySQL 特定字段
        Long binlogPosition = (Long) source.get("pos");
        String binlogFile = (String) source.get("file");

        // PostgreSQL 特定字段
        Long lsn = (Long) source.get("lsn");

        // SQL Server 特定字段
        Long commitLsn = (Long) source.get("commit_lsn");

        return new ChangeEventMetadata(connector, serverName, database, schema, table, "data",
                binlogPosition, binlogFile, lsn, commitLsn);
    }

    private static Map<String, Object> parseKey(byte[] keyBytes) {
        String keyJson = new String(keyBytes, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> keyMap = (Map<String, Object>) JsonTool.parseNonStrict(keyJson);
        if (keyMap == null) {
            return null;
        }
        // 处理 payload 包装
        if (keyMap.containsKey("payload")) {
            return (Map<String, Object>) keyMap.get("payload");
        }
        return keyMap;
    }
}
