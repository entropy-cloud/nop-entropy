/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_HASH_NOT_AVAILABLE;

/**
 * Internal schema resolver that computes a {@link SerializerFingerprint} from a
 * state's type signature. The checksum is a SHA-256 hex digest of a canonical
 * type-signature string composed from {@code stateType} + class FQNs of the
 * value type and any sub-types ({@code mapKeyType}, {@code accumulatorType},
 * {@code aggregateFunctionType} as applicable).
 *
 * <p>The canonical string format is an internal implementation detail; it must
 * be deterministic and stable across JVM restarts. The format is:
 * <pre>
 *   stateType={stateType};valueType={valueTypeFQN}[;mapKeyType={fqn}][;accumulatorType={fqn}][;aggregateFunctionType={fqn}]
 * </pre>
 * Fields appear in a fixed order; absent fields are omitted entirely (not
 * emitted as empty), so the same logical type signature always yields the
 * same canonical string.
 *
 * <p>This resolver supports two input modes that must produce identical
 * checksums for the same logical type signature:
 * <ul>
 *   <li>{@code fromDescriptor(stateType, descriptor)} — used at {@code getState()} time
 *       when the live descriptor is available. {@code stateType} is required because
 *       some state implementations share a descriptor class (e.g.
 *       {@code MemoryListState} and {@code MemoryInternalListState} both use
 *       {@code ListStateDescriptor}; {@code MemoryReducingState} and
 *       {@code MemoryInternalAppendingState} both use {@code ReducingStateDescriptor}).</li>
 *   <li>{@code fromTypeMetadata(...)} — used during snapshot serialization where
 *       only the recorded type-metadata strings are available.</li>
 * </ul>
 */
@Internal
public final class StateSchemaResolver {

    public static final String STATE_TYPE_VALUE = "ValueState";
    public static final String STATE_TYPE_MAP = "MapState";
    public static final String STATE_TYPE_LIST = "ListState";
    public static final String STATE_TYPE_INTERNAL_LIST = "InternalListState";
    public static final String STATE_TYPE_REDUCING = "ReducingState";
    public static final String STATE_TYPE_AGGREGATING = "AggregatingState";
    public static final String STATE_TYPE_INTERNAL_AGGREGATING = "InternalAggregatingState";
    public static final String STATE_TYPE_APPENDING = "AppendingState";

    private StateSchemaResolver() {
    }

    public static SerializerFingerprint fromDescriptor(String stateType, StateDescriptor<?> descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        if (stateType == null || stateType.isEmpty()) {
            throw new IllegalArgumentException("stateType must not be null or empty");
        }
        String stateName = descriptor.getName();
        String valueFqn = fqn(descriptor.getValueType());

        if (descriptor instanceof MapStateDescriptor) {
            MapStateDescriptor<?, ?> mapDesc = (MapStateDescriptor<?, ?>) descriptor;
            return build(stateName, stateType, valueFqn,
                    "mapKeyType", fqn(mapDesc.getKeyClass()));
        }
        if (descriptor instanceof AggregatingStateDescriptor) {
            AggregatingStateDescriptor<?, ?, ?> aggDesc = (AggregatingStateDescriptor<?, ?, ?>) descriptor;
            return build(stateName, stateType, valueFqn,
                    "aggregateFunctionType", fqn(aggDesc.getAggregateFunction() == null
                            ? null : aggDesc.getAggregateFunction().getClass()));
        }
        if (descriptor instanceof ReducingStateDescriptor) {
            ReducingStateDescriptor<?> reducingDesc = (ReducingStateDescriptor<?>) descriptor;
            return build(stateName, stateType, valueFqn,
                    "accumulatorType", fqn(reducingDesc.getAccumulatorType()));
        }
        return build(stateName, stateType, valueFqn);
    }

    public static SerializerFingerprint fromTypeMetadata(String stateName, String stateType,
                                                         String valueTypeFqn,
                                                         String mapKeyTypeFqn,
                                                         String accumulatorTypeFqn,
                                                         String aggregateFunctionTypeFqn) {
        String resolvedStateType = stateType != null ? stateType : STATE_TYPE_VALUE;
        Map<String, String> extras = new LinkedHashMap<>();
        if (mapKeyTypeFqn != null && !mapKeyTypeFqn.isEmpty()) {
            extras.put("mapKeyType", mapKeyTypeFqn);
        }
        if (accumulatorTypeFqn != null && !accumulatorTypeFqn.isEmpty()) {
            extras.put("accumulatorType", accumulatorTypeFqn);
        }
        if (aggregateFunctionTypeFqn != null && !aggregateFunctionTypeFqn.isEmpty()) {
            extras.put("aggregateFunctionType", aggregateFunctionTypeFqn);
        }
        return build(stateName, resolvedStateType, valueTypeFqn, extras);
    }

    private static SerializerFingerprint build(String stateName, String stateType, String valueFqn) {
        return build(stateName, stateType, valueFqn, null);
    }

    private static SerializerFingerprint build(String stateName, String stateType, String valueFqn,
                                               String extraKey, String extraValue) {
        Map<String, String> extras = new LinkedHashMap<>();
        if (extraValue != null && !extraValue.isEmpty()) {
            extras.put(extraKey, extraValue);
        }
        return build(stateName, stateType, valueFqn, extras);
    }

    private static SerializerFingerprint build(String stateName, String stateType, String valueFqn,
                                               Map<String, String> extras) {
        String canonical = buildCanonicalString(stateType, valueFqn, extras);
        String checksum = computeSHA256(canonical);
        return new SerializerFingerprint(stateName, SerializerFingerprint.DEFAULT_SCHEMA_VERSION, checksum);
    }

    static String buildCanonicalString(String stateType, String valueFqn, Map<String, String> extras) {
        StringBuilder sb = new StringBuilder();
        sb.append("stateType=").append(stateType == null ? "" : stateType);
        sb.append(";valueType=").append(valueFqn == null ? "" : valueFqn);
        if (extras != null) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                String v = e.getValue();
                if (v != null && !v.isEmpty()) {
                    sb.append(';').append(e.getKey()).append('=').append(v);
                }
            }
        }
        return sb.toString();
    }

    private static String fqn(Class<?> clazz) {
        return clazz == null ? "" : clazz.getName();
    }

    static String computeSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new StreamException(ERR_STREAM_HASH_NOT_AVAILABLE, e);
        }
    }

    public static boolean fingerprintsCompatible(SerializerFingerprint a, SerializerFingerprint b) {
        if (a == null || b == null) {
            return true;
        }
        if (a == b) {
            return true;
        }
        return Objects.equals(a.getSchemaChecksum(), b.getSchemaChecksum());
    }
}
