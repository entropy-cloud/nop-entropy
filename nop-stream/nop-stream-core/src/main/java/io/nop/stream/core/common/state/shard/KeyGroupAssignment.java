/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

import io.nop.api.core.annotations.core.Internal;
import io.nop.commons.crypto.HashHelper;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stable hash function and key&#8594;key-group assignment for keyed state.
 *
 * <p><b>Stability contract (G38).</b> The hash returned for a key depends only
 * on the key's <i>value</i>, never on JVM identity, {@code System.identityHashCode},
 * or POJO {@code hashCode()} implementations that may vary across JVM
 * instances. For JDK value types whose {@code hashCode()} is contractually
 * stable and value-derived (String, primitive wrappers, BigDecimal/BigInteger,
 * UUID, Date), {@code hashCode()} is reused directly. Enum keys hash by
 * {@code name()} because {@code Enum.hashCode()} is an identity hash. For every
 * other type (user POJOs, windows, tuples) the hash is Murmur3 over the
 * canonical JSON bytes, which is deterministic across JVMs and process
 * restarts.
 *
 * <p><b>Routing/ownership parity (AR-01).</b> Record routing (the keyBy
 * partitioner) and keyed-state ownership must use this same hash: routing goes
 * through {@link #assignToSubtask(Object, int, int)} (stableHash &#8594;
 * key-group &#8594; range owner), so a record always lands on the subtask that
 * owns &#8212; or will own, after a rescale restore &#8212; its keyed state.
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

    private static final Logger LOG = LoggerFactory.getLogger(KeyGroupAssignment.class);

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
        if (key instanceof Enum<?>) {
            // Enum.hashCode() is identity hash (JVM-variable), so enum keys hash
            // by name() — value-stable across JVMs and restarts (ST-03).
            return ((Enum<?>) key).name().hashCode();
        }
        // 回归覆盖 stream-2pc 单 subtask 归集（plan 2306 Phase 4）：
        // 所有值类型统一走 canonical JSON + murmur3_32。此前 String/Integer 等直接用
        // Object#hashCode()（算术递增），顺序 key 族（key-0/key-1/...）会全部落到相邻
        // key-group，P>1 时 100% 塌缩到单一 subtask——并行度静默退化为 1。
        // 注意：这改变 key→key-group 映射，旧 checkpoint 的 keyed state 布局不兼容
        // （2.0.0-SNAPSHOT 预发布阶段裁定接受；record routing 与 state ownership 仍经
        // 同一本函数保持 AR-01 parity）。
        try {
            String json = JsonTool.serialize(key, false);
            return HashHelper.murmur3_32(json);
        } catch (RuntimeException notSerializable) {
            // Best-effort fallback for keys that cannot participate in JSON
            // serialization (e.g. synthetic test doubles, non-@DataBean objects
            // that never reach a real checkpoint). Real keyed-state keys must
            // be JSON-serializable to be checkpointed, so this branch is only
            // hit by non-production inputs — logged so the JVM-variable hash
            // routing is observable if it ever fires in production.
            LOG.warn("Key of type {} is not JSON-serializable; falling back to identity hashCode for key-group routing (JVM-variable — not checkpoint-stable)",
                    key.getClass().getName(), notSerializable);
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
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "maxParallelism must be at least 1: " + maxParallelism);
        }
        return (stableHash(key) & 0x7FFFFFFF) % maxParallelism;
    }

    /**
     * Assign {@code key} directly to the index of the subtask that owns its
     * key-group under the given {@code maxParallelism} / {@code parallelism}.
     * This is the single entry point record routing must use so that a record
     * always lands on the subtask that owns (or will own, after a rescale
     * restore) its keyed state.
     *
     * @param key            raw user key
     * @param maxParallelism job-global key-group upper bound (&ge; 1)
     * @param parallelism    per-vertex subtask count (&ge; 1, &le; {@code maxParallelism})
     * @return owner subtask index in {@code [0, parallelism)}
     */
    public static int assignToSubtask(Object key, int maxParallelism, int parallelism) {
        int keyGroupId = assignToKeyGroup(key, maxParallelism);
        return assignKeyGroupToSubtask(keyGroupId, maxParallelism, parallelism);
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
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "maxParallelism must be at least 1: " + maxParallelism);
        }
        if (parallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "parallelism must be at least 1: " + parallelism);
        }
        if (parallelism > maxParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "parallelism (" + parallelism + ") must not exceed maxParallelism (" + maxParallelism + ")");
        }
        if (subtaskIndex < 0 || subtaskIndex >= parallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "subtaskIndex (" + subtaskIndex + ") must be in [0, " + parallelism + ")");
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
     * <p>O(1) closed form (plan 369 Phase 3), bit-exact equivalent to the
     * former descending linear scan over range starts
     * {@code start(i) = i*base + min(i, rem)}: the first {@code rem} subtasks
     * own {@code base+1} groups each ({@code [0, rem*(base+1))}), the remaining
     * {@code parallelism - rem} own {@code base} groups each. Hence
     * {@code g < rem*(base+1) ? g/(base+1) : rem + (g - rem*(base+1))/base}.
     * NOT the Flink formula {@code g*P/M} — the two differ at partition
     * boundaries (e.g. M=15, P=10, g=3: nop=1, Flink=2); equivalence with the
     * legacy scan is pinned by the full-grid property test.
     *
     * @throws IllegalArgumentException on any invalid argument
     */
    public static int assignKeyGroupToSubtask(int keyGroupId, int maxParallelism, int parallelism) {
        if (maxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "maxParallelism must be at least 1: " + maxParallelism);
        }
        if (parallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "parallelism must be at least 1: " + parallelism);
        }
        if (parallelism > maxParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "parallelism (" + parallelism + ") must not exceed maxParallelism (" + maxParallelism + ")");
        }
        if (keyGroupId < 0 || keyGroupId >= maxParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "keyGroupId (" + keyGroupId + ") must be in [0, " + maxParallelism + ")");
        }
        int base = maxParallelism / parallelism;
        int rem = maxParallelism % parallelism;
        int firstRegionEnd = rem * (base + 1);
        if (keyGroupId < firstRegionEnd) {
            return keyGroupId / (base + 1);
        }
        return rem + (keyGroupId - firstRegionEnd) / base;
    }

}
