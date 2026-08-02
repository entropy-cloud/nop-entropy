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
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Encodes the composite (namespace, routed-key) pair into a deterministic
 * {@code byte[]} for use as a RocksDB column-family key, and decodes it back.
 *
 * <p><b>Stage 34 layout (version 2) &#8212; key-group sortable prefix.</b>
 * <pre>
 *   [keyGroupId:int32 BE][nsLen:int32 BE][nsJsonBytes][keyLen:int32 BE][keyJsonBytes]
 * </pre>
 * The {@code keyGroupId} is a non-negative big-endian int32 written as the
 * <em>first</em> bytes, so lexicographic byte order equals numeric order and
 * all keys of one key-group are contiguous in the SST. This is what Stage 35
 * range-intersection partial restore relies on.
 *
 * <p><b>Legacy layout (version 1, pre-Stage-34).</b>
 * <pre>
 *   [nsLen:int32 BE][nsJsonBytes][shardId:int32 BE][keyLen:int32 BE][keyJsonBytes]
 * </pre>
 * The {@code shardId} sat in the middle (after the namespace) and was not a
 * sortable prefix, so range scans by shard were impossible. Version 1 SST
 * files (Stage 30/31 incremental checkpoints) cannot be decoded by this
 * encoder and are rejected fail-fast (see {@link #verifyKeyLayoutVersion}).
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

    /**
     * Current composite-key binary layout version. Version 2 puts the
     * key-group id as a big-endian sortable prefix (Stage 34). Version 1
     * (pre-Stage-34) embedded the shard id in the middle of the key and is
     * not decodable by this encoder.
     */
    static final int KEY_LAYOUT_VERSION = 2;

    /**
     * Legacy layout version (pre-Stage-34). Used only for fail-fast detection.
     */
    static final int LEGACY_KEY_LAYOUT_VERSION = 1;

    static final String KEY_LAYOUT_VERSION_FIELD = "keyLayoutVersion";

    private RocksDBKeyEncoder() {
    }

    /**
     * Build the composite storage key bytes for the given namespace, raw key
     * and key-group id.
     *
     * <p>Layout: {@code [keyGroupId:int32 BE][nsLen:int32 BE][nsJsonBytes][keyLen:int32 BE][keyJsonBytes]}
     *
     * @param namespace   current namespace (may be a typed object such as TimeWindow)
     * @param rawKey      the raw user key (before group routing)
     * @param keyGroupId  key-group id in {@code [0, maxParallelism)} (written as
     *                    big-endian sortable prefix)
     * @return deterministic composite key bytes
     */
    static byte[] encode(Object namespace, Object rawKey, int keyGroupId) {
        byte[] nsBytes = namespaceToJsonBytes(namespace);
        byte[] keyBytes = keyToJsonBytes(rawKey);
        byte[] result = new byte[4 + 4 + nsBytes.length + 4 + keyBytes.length];
        int off = 0;
        off = writeInt32(result, off, keyGroupId);
        off = writeInt32(result, off, nsBytes.length);
        System.arraycopy(nsBytes, 0, result, off, nsBytes.length);
        off += nsBytes.length;
        off = writeInt32(result, off, keyBytes.length);
        System.arraycopy(keyBytes, 0, result, off, keyBytes.length);
        return result;
    }

    /**
     * Decode a composite storage key into its namespace, raw key and
     * key-group id parts.
     *
     * @param data       the composite key bytes
     * @param keyType    the key class, used to parse the key JSON back to the key type
     * @return a holder for the decoded namespace, raw key and key-group id
     */
    static DecodedKey decode(byte[] data, Class<?> keyType) {
        int off = 0;
        int keyGroupId = readInt32(data, off);
        off += 4;
        int nsLen = readInt32(data, off);
        off += 4;
        byte[] nsBytes = new byte[nsLen];
        System.arraycopy(data, off, nsBytes, 0, nsLen);
        off += nsLen;
        Object namespace = jsonToNamespace(nsBytes);

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
        return new DecodedKey(namespace, rawKey, keyGroupId);
    }

    /**
     * Return the byte length of the base composite key (without any map-key suffix).
     */
    static int baseKeyLength(byte[] data) {
        int off = 4; // keyGroupId
        int nsLen = readInt32(data, off);
        off += 4 + nsLen;
        int keyLen = readInt32(data, off);
        off += 4 + keyLen;
        return off;
    }

    /**
     * Verify that a snapshot data map is compatible with the current key
     * layout before restoring. The full-snapshot path stores raw user keys
     * (layout-agnostic), so an absent version field (cross-backend Memory
     * snapshot, or a legacy raw-key snapshot) is tolerated there. The
     * incremental path consumes RocksDB-internal SST files whose bytes carry
     * the binary layout, so it requires the exact current version.
     *
     * @param snapshotData the {@link io.nop.stream.core.common.state.backend.StateSnapshot}
     *                     data map (may be {@code null})
     * @param incremental  {@code true} for the incremental SST restore path
     *                     (strict version match), {@code false} for the full path
     *                     (tolerates absent version)
     * @throws StreamException if the layout version is present and incompatible
     */
    static void verifyKeyLayoutVersion(Map<String, Object> snapshotData, boolean incremental) {
        Object v = snapshotData == null ? null : snapshotData.get(KEY_LAYOUT_VERSION_FIELD);
        int version = (v instanceof Number) ? ((Number) v).intValue() : 0; // 0 = absent
        if (incremental) {
            if (version != KEY_LAYOUT_VERSION) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "Incremental checkpoint SST uses incompatible key layout version "
                                + (version == 0 ? "(absent/legacy)" : version)
                                + ": current encoder requires version " + KEY_LAYOUT_VERSION
                                + ". Old-layout SST files cannot be decoded by the key-group encoder; "
                                + "a full snapshot restore is required.");
            }
            return;
        }
        // Full path: raw user keys are layout-agnostic, so only fail-fast when
        // an explicit incompatible RocksDB layout version is stamped.
        if (version != 0 && version != KEY_LAYOUT_VERSION) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "Full snapshot carries incompatible RocksDB key layout version "
                            + version + ": current encoder requires version " + KEY_LAYOUT_VERSION
                            + " (or an absent/raw-key Memory snapshot).");
        }
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
        final int keyGroupId;

        DecodedKey(Object namespace, Object rawKey, int keyGroupId) {
            this.namespace = namespace;
            this.rawKey = rawKey;
            this.keyGroupId = keyGroupId;
        }
    }
}
