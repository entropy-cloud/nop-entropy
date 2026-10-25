/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONFIG_ERROR;

/**
 * WI2 (plan 2343 Phase 3): enumerator of the debezium FLIP-27 source — the B-layer
 * table routing successor (adjudication R2). On start the include list is decomposed
 * into one split per table; when a reader registers, ITS ENTIRE round-robin share is
 * delivered at once (each reader runs one engine scoped to its assigned tables — a
 * mid-run engine reconfiguration is impossible, see the reader's D-4 guard).
 *
 * <p>Deployment-parity gate (legacy {@code validateParallelism} :500-515): more reader
 * slots than tables fails typed at start, and every reader is guaranteed a non-empty
 * share (round-robin over readers ≤ tables — the hash-collision zero-share case cannot
 * arise).
 */
public class DebeziumTableSplitEnumerator
        implements SplitEnumerator<DebeziumTableSplit, DebeziumCdcEnumeratorState> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumTableSplitEnumerator.class);

    private final List<String> tables;
    /** subtask index → table share (computed at start, consumed by addReader). */
    private final Map<Integer, List<String>> shareBySubtask = new LinkedHashMap<>();
    /**
     * C3-004 (plan 2503 Phase 3): restored unassigned tables, RECORDED — never pre-bucketed.
     * Consumed by {@link #start(SplitEnumeratorContext)} so each table lands in exactly one
     * share bucket (difference-set semantics: the restored set never adds a second
     * registration for a table the fresh recompute already buckets).
     */
    private final Set<String> restoredUnassigned = new LinkedHashSet<>();
    /** Recorded reader slot for restored tables that fall out of a shrunken include list. */
    private int restoredNextSubtaskIndex;
    private boolean parallelismValidated;
    private transient SplitEnumeratorContext<DebeziumTableSplit> context;

    public DebeziumTableSplitEnumerator(List<String> tables) {
        this.tables = tables == null ? Collections.emptyList() : new ArrayList<>(tables);
    }

    @Override
    public void start(SplitEnumeratorContext<DebeziumTableSplit> context) {
        this.context = context;
        int parallelism = context.getTotalParallelism();
        if (!parallelismValidated) {
            validateParallelism(parallelism);
            parallelismValidated = true;
        }
        // round-robin the tables over the reader slots. C3-004: every table lands in
        // EXACTLY ONE bucket (its position share) — the restored unassigned set merges into
        // the same bucketing below instead of forming a second registration.
        for (int i = 0; i < tables.size(); i++) {
            int subtask = i % parallelism;
            shareBySubtask.computeIfAbsent(subtask, k -> new ArrayList<>()).add(tables.get(i));
        }
        // C3-004 (plan 2503 Phase 3): restored unassigned tables that are no longer in the
        // current include list (include-list shrink across restart) keep a single-bucket
        // re-offer under the recorded reader slot, so the re-offer is never silently dropped.
        // They cannot double-register: the recompute above never touches them.
        for (String table : restoredUnassigned) {
            if (!tables.contains(table)) {
                LOG.warn("DebeziumTableSplitEnumerator restored unassigned table {} is no longer "
                        + "in the include list; re-offering via the recorded reader slot {}",
                        table, restoredNextSubtaskIndex);
                shareBySubtask.computeIfAbsent(restoredNextSubtaskIndex, k -> new ArrayList<>())
                        .add(table);
            }
        }
        LOG.info("DebeziumTableSplitEnumerator started: {} tables over {} readers (shares={})",
                tables.size(), parallelism, shareBySubtask);
    }

    void validateParallelism(int parallelism) {
        if (tables.isEmpty()) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                    .param(ARG_DETAIL, "DebeziumConfig.tableIncludeList is required when the CDC source "
                            + "runs parallel: table sharding is the routing domain "
                            + "(single-table parallel snapshot: use snapshotMaxThreads, layer A)");
        }
        if (parallelism > tables.size()) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                    .param(ARG_DETAIL, "CDC source parallelism " + parallelism
                            + " exceeds tableIncludeList size " + tables.size()
                            + ": route at most one reader per table; for intra-table parallel "
                            + "snapshot use snapshotMaxThreads (layer A)");
        }
    }

    private void deliverShare(int subtaskIndex) {
        List<String> share = shareBySubtask.remove(subtaskIndex);
        if (share == null || share.isEmpty() || context == null) {
            return;
        }
        List<DebeziumTableSplit> splits = new ArrayList<>(share.size());
        for (String table : share) {
            splits.add(new DebeziumTableSplit(table));
        }
        context.getDeliveryService().assignSplits(subtaskIndex, splits);
        LOG.info("DebeziumTableSplitEnumerator assigned {} table(s) to subtask {}: {}",
                splits.size(), subtaskIndex, share);
    }

    @Override
    public void addReader(int subtaskIndex) {
        deliverShare(subtaskIndex);
    }

    @Override
    public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {
        // the full share was delivered at registration; nothing further to hand out
    }

    @Override
    public DebeziumCdcEnumeratorState snapshotState(long checkpointId) {
        List<String> unassigned = new ArrayList<>();
        for (List<String> share : shareBySubtask.values()) {
            unassigned.addAll(share);
        }
        return new DebeziumCdcEnumeratorState(unassigned, shareBySubtask.size());
    }

    @Override
    public void restoreState(DebeziumCdcEnumeratorState state) {
        if (state == null || state.getUnassignedTables() == null
                || state.getUnassignedTables().isEmpty()) {
            return;
        }
        // C3-004 (plan 2503 Phase 3): restored unassigned tables are RECORDED, not
        // pre-bucketed. Pre-fix they were dumped into a single nextSubtaskIndex bucket while
        // start()'s full recompute ALSO registered them in their position buckets — the same
        // table ended up in two readers' shares and two engines consumed it (recovered-state
        // ∪ fresh-share union instead of the required difference set; each table duplicated
        // downstream). Now start()'s position recompute alone decides the single bucket.
        restoredUnassigned.addAll(state.getUnassignedTables());
        restoredNextSubtaskIndex = state.getNextSubtaskIndex();
        LOG.info("DebeziumTableSplitEnumerator restored {} unassigned table(s)",
                restoredUnassigned.size());
    }

    @Override
    public void close() {
        // no external resources
    }
}
