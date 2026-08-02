/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.UUID;

import io.nop.api.core.annotations.core.Internal;
import io.nop.commons.crypto.HashHelper;
import io.nop.core.lang.json.JsonTool;

/**
 * Stable hash function and key&#8594;key-group assignment for keyed state.
 *
 * <p><b>Stability contract (G38).</b> The hash returned for a key depends only
 * on the key's <i>value</i>, never on JVM identity, {@code System.identityHashCode},
 * or POJO {@code hashCode()} implementations that may vary across JVM
 * instances. For JDK value types whose {@code hashCode()} is contractually
 * stable and value-derived (String, primitive wrappers, BigDecimal/BigInteger,
 * UUID, Date), {@code hashCode()} is reused directly &#8212; this preserves
 * routing parity with the legacy {@code (key.hashCode() & 0x7FFFFFFF) % shardCount}
 * formula for the built-in key types that dominate real jobs (String/Long/Integer).
 * For every other type (user POJOs, windows, tuples) the hash is Murmur3 over
 * the canonical JSON bytes, which is deterministic across JVMs and process
 * restarts.
 *
 * <p><b>Key&#8594;group mapping (G37).</b>
 * {@code keyGroupId = (stableHash(key) & 0x7FFFFFFF) % maxParallelism}, where
 * {@code maxParallelism} is the job-global upper bound ({@link KeyGroup#DEFAULT_MAX_PARALLELISM}
 * by default). The mapping stays stable for the job lifetime because
 * {@code maxParallelism} does not change; a rescale only reshuffles which
 * subtask owns which contiguous {@link KeyGroupRange}.
 */
@Internal
public final class KeyGroupAssignment {

    private KeyGroupAssignment() {
    }

    /**
     * Compute a stable, cross-JVM-deterministic 32-bit hash for {@code key}.
     *
     * @param key the raw user key ({@code null} hashes to {@code 0})
     * @return a stable int hash (may be negative; callers mask with
     *         {@code & 0x7FFFFFFF} before taking the modulus)
     */
    public static int stableHash(Object key) {
        if (key == null) {
            return 0;
        }
        if (isStableValueHashType(key)) {
            return key.hashCode();
        }
        try {
            String json = JsonTool.serialize(key, false);
            return HashHelper.murmur3_32(json);
        } catch (RuntimeException notSerializable) {
            // Best-effort fallback for keys that cannot participate in JSON
            // serialization (e.g. synthetic test doubles, non-@DataBean objects
            // that never reach a real checkpoint). Real keyed-state keys must
            // be JSON-serializable to be checkpointed, so this branch is only
            // hit by non-production inputs.
            return key.hashCode();
        }
    }

    /**
     * Assign {@code key} to a key-group id in {@code [0, maxParallelism)}.
     *
     * @param key             raw user key
     * @param maxParallelism  job-global upper bound on key groups (&ge; 1)
     * @return key-group id in {@code [0, maxParallelism)}
     */
    public static int assignToKeyGroup(Object key, int maxParallelism) {
        if (maxParallelism < 1) {
            throw new IllegalArgumentException("maxParallelism must be at least 1: " + maxParallelism);
        }
        return (stableHash(key) & 0x7FFFFFFF) % maxParallelism;
    }

    /**
     * Compute the contiguous {@link KeyGroupRange} owned by subtask
     * {@code subtaskIndex} under a fixed job-global {@code maxParallelism} and
     * the current per-vertex {@code parallelism}. This is the nop-stream
     * minimal equivalent of Flink's {@code KeyGroupRangeAssignment}: the
     * {@code maxParallelism} key groups are partitioned as evenly as possible
     * into {@code parallelism} contiguous ranges.
     *
     * <p>Properties (verified by focused tests):
     * <ul>
     *   <li>the {@code parallelism} ranges are mutually disjoint and their
     *       union is exactly {@code [0, maxParallelism)}</li>
     *   <li>{@code key&#8594;group} mapping depends only on {@code maxParallelism},
     *       so changing {@code parallelism} never moves a key to a different
     *       group (only which subtask owns the group changes) &#8212; the
     *       foundation of Stage 35 partial rescale recovery</li>
     * </ul>
     *
     * @param maxParallelism job-global key-group upper bound (&ge; 1)
     * @param parallelism    per-vertex subtask count (&ge; 1, &le; {@code maxParallelism})
     * @param subtaskIndex   subtask index in {@code [0, parallelism)}
     * @return the contiguous {@link KeyGroupRange} owned by that subtask
     * @throws IllegalArgumentException on any invalid argument
     */
    public static KeyGroupRange computeKeyGroupRangeForSubtaskIndex(int maxParallelism, int parallelism, int subtaskIndex) {
        if (maxParallelism < 1) {
            throw new IllegalArgumentException("maxParallelism must be at least 1: " + maxParallelism);
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be at least 1: " + parallelism);
        }
        if (parallelism > maxParallelism) {
            throw new IllegalArgumentException(
                    "parallelism (" + parallelism + ") must not exceed maxParallelism (" + maxParallelism + ")");
        }
        if (subtaskIndex < 0 || subtaskIndex >= parallelism) {
            throw new IllegalArgumentException(
                    "subtaskIndex (" + subtaskIndex + ") must be in [0, " + parallelism + ")");
        }

        // Even partitioning of maxParallelism groups into parallelism contiguous
        // ranges: the first (maxParallelism mod parallelism) subtasks get one
        // extra group. This keeps ranges contiguous and the union exact.
        int base = maxParallelism / parallelism;
        int rem = maxParallelism % parallelism;

        int start = subtaskIndex * base + Math.min(subtaskIndex, rem);
        int size = base + (subtaskIndex < rem ? 1 : 0);
        int end = start + size;
        return new KeyGroupRange(start, end);
    }

    /**
     * Given a key-group id, return the index of the subtask (under the given
     * {@code maxParallelism} / {@code parallelism}) that owns it. Inverse of
     * {@link #computeKeyGroupRangeForSubtaskIndex}. Stage 35 rescale consumes
     * this to route a restored group to its new owner.
     *
     * @throws IllegalArgumentException on any invalid argument
     */
    public static int assignKeyGroupToSubtask(int keyGroupId, int maxParallelism, int parallelism) {
        if (maxParallelism < 1) {
            throw new IllegalArgumentException("maxParallelism must be at least 1: " + maxParallelism);
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be at least 1: " + parallelism);
        }
        if (parallelism > maxParallelism) {
            throw new IllegalArgumentException(
                    "parallelism (" + parallelism + ") must not exceed maxParallelism (" + maxParallelism + ")");
        }
        if (keyGroupId < 0 || keyGroupId >= maxParallelism) {
            throw new IllegalArgumentException(
                    "keyGroupId (" + keyGroupId + ") must be in [0, " + maxParallelism + ")");
        }
        // Invert the contiguous-even partition: subtask boundaries are at
        // start(i) = i * base + min(i, rem). Find the largest i whose start <= keyGroupId.
        int base = maxParallelism / parallelism;
        int rem = maxParallelism % parallelism;
        for (int i = parallelism - 1; i >= 0; i--) {
            int start = i * base + Math.min(i, rem);
            if (keyGroupId >= start) {
                return i;
            }
        }
        return 0;
    }

    /**
     * @return {@code true} if {@code key}'s {@link Object#hashCode()} is
     * contractually stable and value-derived (so delegating to it preserves
     * both cross-JVM determinism and routing parity with the legacy formula).
     */
    private static boolean isStableValueHashType(Object key) {
        if (key instanceof String
                || key instanceof Integer
                || key instanceof Long
                || key instanceof Boolean
                || key instanceof Byte
                || key instanceof Short
                || key instanceof Character
                || key instanceof Float
                || key instanceof Double
                || key instanceof BigInteger
                || key instanceof BigDecimal
                || key instanceof UUID
                || key instanceof Date) {
            return true;
        }
        if (key instanceof Enum<?>) {
            return true;
        }
        return false;
    }
}
