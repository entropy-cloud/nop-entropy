/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;

/**
 * Encodes the composite (namespace, routed-key) pair into a deterministic
 * {@code byte[]} for use as a RocksDB column-family key, and decodes it back.
 *
 * <p>The encoding is a length-prefixed binary layout:
 * <pre>
 *   [nsLen:int32 BE][nsJsonBytes][shardId:int32 BE][keyJsonBytes]
 * </pre>
 *
 * <p>Namespace serialization mirrors {@code MemoryStateSerDe} so that snapshot
 * entries are cross-compatible: {@code TimeWindow} becomes a typed map,
 * {@code GlobalWindow} becomes the string {@code "GlobalWindow"}, and other
 * objects are passed through as-is.
 *
 * <p>The key is serialized to JSON. Because the live operator always holds the
 * current key as a typed Java object, the encoding only needs to be
 * deterministic and injective for get/put correctness. Round-trip fidelity for
 * the snapshot scan is sufficient because the snapshot value itself is
 * JSON-serialized downstream (same as the memory backend).
 */
final class RocksDBKeyEncoder {

    static final byte[] EMPTY_BYTES = new byte[0];

    private RocksDBKeyEncoder() {
    }

    /**
     * Build the composite storage key bytes for the given namespace and raw key.
     *
     * <p>Layout: {@code [nsLen:int32][nsJsonBytes][shardId:int32][keyLen:int32][keyJsonBytes]}
     *
     * @param namespace current namespace (may be a typed object such as TimeWindow)
     * @param rawKey    the raw user key (before shard routing)
     * @param shardId   shard id (0 when sharding is disabled)
     * @return deterministic composite key bytes
     */
    static byte[] encode(Object namespace, Object rawKey, int shardId) {
        byte[] nsBytes = namespaceToJsonBytes(namespace);
        byte[] keyBytes = keyToJsonBytes(rawKey);
        byte[] result = new byte[4 + nsBytes.length + 4 + 4 + keyBytes.length];
        int off = 0;
        off = writeInt32(result, off, nsBytes.length);
        System.arraycopy(nsBytes, 0, result, off, nsBytes.length);
        off += nsBytes.length;
        off = writeInt32(result, off, shardId);
        off = writeInt32(result, off, keyBytes.length);
        System.arraycopy(keyBytes, 0, result, off, keyBytes.length);
        return result;
    }

    /**
     * Decode a composite storage key into its namespace and raw key parts.
     *
     * @param data       the composite key bytes
     * @param keyType    the key class, used to parse the key JSON back to the key type
     * @return a holder for the decoded namespace and raw key
     */
    static DecodedKey decode(byte[] data, Class<?> keyType) {
        int off = 0;
        int nsLen = readInt32(data, off);
        off += 4;
        byte[] nsBytes = new byte[nsLen];
        System.arraycopy(data, off, nsBytes, 0, nsLen);
        off += nsLen;
        Object namespace = jsonToNamespace(nsBytes);

        int shardId = readInt32(data, off);
        off += 4;
        int keyLen = readInt32(data, off);
        off += 4;
        Object rawKey;
        if (keyLen <= 0) {
            rawKey = null;
        } else {
            byte[] keyBytes = new byte[keyLen];
            System.arraycopy(data, off, keyBytes, 0, keyLen);
            rawKey = jsonToKey(keyBytes, keyType);
        }
        return new DecodedKey(namespace, rawKey, shardId);
    }

    /**
     * Return the byte length of the base composite key (without any map-key suffix).
     */
    static int baseKeyLength(byte[] data) {
        int off = 0;
        int nsLen = readInt32(data, off);
        off += 4 + nsLen + 4;
        int keyLen = readInt32(data, off);
        off += 4 + keyLen;
        return off;
    }

    // ---------- namespace serialization (mirrors MemoryStateSerDe) ----------

    static Object serializeNamespace(Object namespace) {
        if (namespace == null) {
            return IKeyedStateBackend.DEFAULT_NAMESPACE;
        }
        if (namespace instanceof TimeWindow) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("@type", "TimeWindow");
            m.put("start", ((TimeWindow) namespace).getStart());
            m.put("end", ((TimeWindow) namespace).getEnd());
            return m;
        }
        if (namespace instanceof GlobalWindow) {
            return "GlobalWindow";
        }
        return namespace;
    }

    static Object deserializeNamespace(Object obj) {
        if (obj == null) {
            return IKeyedStateBackend.DEFAULT_NAMESPACE;
        }
        if (obj instanceof String) {
            String s = (String) obj;
            if ("GlobalWindow".equals(s)) {
                return GlobalWindow.get();
            }
            if ("VoidNamespace".equals(s)) {
                return io.nop.stream.core.common.state.VoidNamespace.INSTANCE;
            }
            return s;
        }
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) obj;
            Object type = m.get("@type");
            if ("TimeWindow".equals(type)) {
                return new TimeWindow(
                        ((Number) m.get("start")).longValue(),
                        ((Number) m.get("end")).longValue());
            }
        }
        return obj;
    }

    private static byte[] namespaceToJsonBytes(Object namespace) {
        Object ns = serializeNamespace(namespace);
        if (ns == null) {
            return EMPTY_BYTES;
        }
        return JsonTool.serialize(ns, false).getBytes(StandardCharsets.UTF_8);
    }

    private static Object jsonToNamespace(byte[] nsBytes) {
        if (nsBytes.length == 0) {
            return IKeyedStateBackend.DEFAULT_NAMESPACE;
        }
        String json = new String(nsBytes, StandardCharsets.UTF_8);
        Object parsed = JsonTool.parseNonStrict(json);
        return deserializeNamespace(parsed);
    }

    private static byte[] keyToJsonBytes(Object rawKey) {
        if (rawKey == null) {
            return EMPTY_BYTES;
        }
        return JsonTool.serialize(rawKey, false).getBytes(StandardCharsets.UTF_8);
    }

    private static Object jsonToKey(byte[] keyBytes, Class<?> keyType) {
        if (keyBytes.length == 0) {
            return null;
        }
        String json = new String(keyBytes, StandardCharsets.UTF_8);
        if (keyType != null && keyType != Object.class) {
            return JsonTool.parseBeanFromText(json, keyType);
        }
        return JsonTool.parseNonStrict(json);
    }

    private static int writeInt32(byte[] buf, int off, int value) {
        buf[off] = (byte) ((value >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((value >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((value >>> 8) & 0xFF);
        buf[off + 3] = (byte) (value & 0xFF);
        return off + 4;
    }

    private static int readInt32(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24)
                | ((buf[off + 1] & 0xFF) << 16)
                | ((buf[off + 2] & 0xFF) << 8)
                | (buf[off + 3] & 0xFF);
    }

    static final class DecodedKey {
        final Object namespace;
        final Object rawKey;
        final int shardId;

        DecodedKey(Object namespace, Object rawKey, int shardId) {
            this.namespace = namespace;
            this.rawKey = rawKey;
            this.shardId = shardId;
        }
    }
}
