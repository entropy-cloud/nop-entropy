/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.materialization;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.streamrecord.StreamElement;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_POINT_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_POINT_SEALED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_STORE_OVERFLOW;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * In-memory {@link IMaterializationPoint} implementation.
 *
 * <p>Backed by a synchronized {@link ArrayList}. This is the correctness-first
 * implementation for region-boundary materialization (option B). Production-grade
 * RocksDB/disk materialization is a non-goal of the current plan and is deferred
 * as an optimization candidate.
 *
 * <p><b>Concurrency</b>: all mutating and read operations are synchronized on
 * {@code this}, so the producer write path and the recovery replay path can run
 * concurrently.
 *
 * <p><b>Store bound (FAM-1, plan 2510 R2-07, adjudication 10a as revised by the
 * combined-gate live evidence)</b>: the store is a checkpoint-scoped recovery
 * record plane — with the FAM-1 permit-retention fix it mirrors the in-flight
 * (not yet checkpoint-committed) records, so the recovery plane must be able to
 * hold ALL in-flight records; any fixed default bound smaller than the in-flight
 * count turns a slow consumer into an unrecoverable restart death (see
 * {@code TestRegionRestartInternalEdgeE2E.materializedEdge_replaySetLargerThanQueueCapacity_doesNotBlockRestart}).
 * The C5-001 unbounded-growth vector (silent queue-full reroute into the store)
 * is already eliminated by permit retention; the store shrinks as checkpoints
 * advance. Therefore:
 * <ul>
 *   <li><b>Unconfigured or {@code <= 0}</b>: NO hard bound (recovery-correctness
 *       default). Crossing the 1024 / 4096 / 16384 / ... size tiers emits a
 *       once-per-tier WARN with the point id and current size (plus the existing
 *       size metrics) so a pathologically large store stays observable.</li>
 *   <li><b>Explicitly configured {@code > 0}</b>: enables an operator-imposed
 *       fail-loud bound; exceeding it throws the typed
 *       {@code ERR_STREAM_MATERIALIZE_STORE_OVERFLOW}.</li>
 * </ul>
 */
public class InMemoryMaterializationPoint implements IMaterializationPoint {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryMaterializationPoint.class);

    /**
     * FAM-1 (adjudication 10a, revised): independent store bound configuration
     * key. Unset or {@code <= 0} = no hard bound (WARN tiers only); {@code > 0} =
     * operator-imposed fail-loud bound.
     */
    public static final String STORE_MAX_ENTRIES_CONFIG = "nop.stream.materialization.store-max-entries";

    /** First size tier (entries) at which the unbounded-store WARN fires. */
    public static final int STORE_WARN_TIER_BASE = 1024;

    private final String pointId;
    /**
     * Explicit fail-loud bound; {@code <= 0} means no hard bound (default).
     */
    private final int storeMaxEntries;
    /**
     * Size tier already warned for the unbounded mode (0 = none warned yet).
     */
    private int warnedTierSize;
    private final List<MaterializedElement> store = new ArrayList<>();
    private volatile boolean sealed;
    private long lastEpoch = -1L;

    /**
     * Creates a point with the independently configured store bound
     * ({@code nop.stream.materialization.store-max-entries}: unset or {@code <= 0}
     * = no hard bound with WARN tiers; {@code > 0} = fail-loud bound).
     *
     * @param pointId the independently addressable id (must not be null)
     */
    public InMemoryMaterializationPoint(String pointId) {
        this(pointId, resolveConfiguredStoreMaxEntries());
    }

    /**
     * Creates a point with an explicit store bound.
     *
     * @param pointId         the independently addressable id (must not be null)
     * @param storeMaxEntries the fail-loud maximum number of materialized elements;
     *                        {@code <= 0} disables the hard bound (WARN tiers only)
     */
    public InMemoryMaterializationPoint(String pointId, int storeMaxEntries) {
        if (pointId == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "pointId");
        }
        this.pointId = pointId;
        this.storeMaxEntries = storeMaxEntries;
    }

    private static int resolveConfiguredStoreMaxEntries() {
        return Integer.getInteger(STORE_MAX_ENTRIES_CONFIG, 0);
    }

    @Override
    public String getPointId() {
        return pointId;
    }

    /**
     * @return the explicit fail-loud store bound, or {@code <= 0} when no hard
     *         bound is configured (unbounded recovery plane with WARN tiers)
     */
    public int getStoreMaxEntries() {
        return storeMaxEntries;
    }

    @Override
    public synchronized void write(StreamElement element, long epoch) throws InterruptedException {
        if (sealed) {
            throw new StreamException(ERR_STREAM_MATERIALIZE_POINT_SEALED)
                    .param(ARG_POINT_ID, pointId)
                    .param(ARG_DETAIL, "cannot write to a sealed materialization point");
        }
        if (storeMaxEntries > 0 && store.size() >= storeMaxEntries) {
            // FAM-1 (adjudication 10b', revised 10a): an explicitly configured
            // operator bound is the only fail-loud overflow signal. The unconfigured
            // default keeps the whole in-flight record set for checkpoint-scoped
            // recovery replay (recovery plane must be >= in-flight uncommitted
            // records; TestRegionRestartInternalEdgeE2E N1 guarantee).
            throw new StreamException(ERR_STREAM_MATERIALIZE_STORE_OVERFLOW)
                    .param(ARG_POINT_ID, pointId)
                    .param(ARG_DETAIL, "explicitly configured store bound exceeded; raise "
                            + STORE_MAX_ENTRIES_CONFIG + " or unset it to allow "
                            + "checkpoint-scoped recovery growth");
        }
        // MaterializedElement constructor rejects null element.
        store.add(new MaterializedElement(element, epoch));
        if (epoch > lastEpoch) {
            lastEpoch = epoch;
        }
        if (storeMaxEntries <= 0) {
            warnTierIfNeededLocked();
        }
    }

    /**
     * Once-per-tier WARN for the unbounded (recovery-default) store mode: crossing
     * 1024 / 4096 / 16384 / ... entries logs the point id and current size once per
     * tier (the store size itself is exposed by the existing {@link #size()}
     * metrics surface). Must be called while holding {@code this}.
     */
    private void warnTierIfNeededLocked() {
        int size = store.size();
        if (size < STORE_WARN_TIER_BASE) {
            return;
        }
        long tier = STORE_WARN_TIER_BASE;
        while (tier * 4L <= size) {
            tier *= 4L;
        }
        if (tier > warnedTierSize) {
            warnedTierSize = (int) tier;
            LOG.warn("Materialization point {} store reached {} entries (tier {}+) without an "
                    + "explicit bound: the checkpoint-scoped recovery plane mirrors all in-flight "
                    + "uncommitted records and shrinks as checkpoints advance. Configure "
                    + "{} (currently unset/<=0) only to impose a deliberate fail-loud cap.",
                    pointId, size, tier, STORE_MAX_ENTRIES_CONFIG);
        }
    }

    @Override
    public synchronized List<MaterializedElement> replay(long fromEpoch) {
        if (fromEpoch <= 0L) {
            // epoch <= 0 means "from the beginning"; avoid skipping elements
            // whose epoch is legitimately 0.
            return replayAll();
        }
        List<MaterializedElement> out = new ArrayList<>();
        for (MaterializedElement me : store) {
            if (me.getEpoch() >= fromEpoch) {
                out.add(me);
            }
        }
        return out;
    }

    @Override
    public synchronized List<MaterializedElement> replayAll() {
        return new ArrayList<>(store);
    }

    @Override
    public synchronized void seal() {
        this.sealed = true;
    }

    @Override
    public boolean isSealed() {
        return sealed;
    }

    @Override
    public synchronized int size() {
        return store.size();
    }

    @Override
    public synchronized long getLastEpoch() {
        return lastEpoch;
    }
}
