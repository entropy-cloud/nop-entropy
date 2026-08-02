/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;

/**
 * JSON-based value serialization for RocksDB state values.
 *
 * <p>All values are serialized to JSON bytes via {@code JsonTool}, consistent
 * with the nop-stream design decision that JSON is the single serialization
 * format (see {@code state-management-design.md} §6.1).
 */
final class RocksDBValueSerDe {

    private RocksDBValueSerDe() {
    }

    static byte[] serialize(Object value) {
        if (value == null) {
            return null;
        }
        return JsonTool.serialize(value, false).getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    static <T> T deserialize(byte[] bytes, Class<T> type) {
        if (bytes == null) {
            return null;
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        if (type != null && type != Object.class) {
            return JsonTool.parseBeanFromText(json, type);
        }
        return (T) JsonTool.parseNonStrict(json);
    }

    @SuppressWarnings("unchecked")
    static <T> T deserializeObject(Object obj, Class<T> type) {
        if (obj == null) {
            return null;
        }
        if (type != null && type.isInstance(obj)) {
            return (T) obj;
        }
        String json = JsonTool.serialize(obj, false);
        return JsonTool.parseBeanFromText(json, type);
    }

    @SuppressWarnings("unchecked")
    static List<Object> deserializeList(byte[] bytes, Class<?> elementType) {
        if (bytes == null) {
            return new ArrayList<>();
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        Object parsed = JsonTool.parseNonStrict(json);
        if (parsed instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) parsed) {
                result.add(elementType != null && elementType != Object.class
                        ? deserializeObject(item, elementType) : item);
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    static Map<Object, Object> deserializeMap(byte[] bytes,
                                              Class<?> keyType, Class<?> valueType) {
        if (bytes == null) {
            return new LinkedHashMap<>();
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        Object parsed = JsonTool.parseNonStrict(json);
        if (parsed instanceof Map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
                Object k = keyType != null && keyType != Object.class
                        ? deserializeObject(entry.getKey(), keyType) : entry.getKey();
                Object v = valueType != null && valueType != Object.class
                        ? deserializeObject(entry.getValue(), valueType) : entry.getValue();
                result.put(k, v);
            }
            return result;
        }
        return new LinkedHashMap<>();
    }
}
