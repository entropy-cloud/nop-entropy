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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        // round-robin the tables over the reader slots
        for (int i = 0; i < tables.size(); i++) {
            int subtask = i % parallelism;
            shareBySubtask.computeIfAbsent(subtask, k -> new ArrayList<>()).add(tables.get(i));
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
        // restored unassigned tables are re-offered to registering readers (one table per
        // registration — a partial-assignment recovery)
        List<String> unassigned = new ArrayList<>(state.getUnassignedTables());
        for (String table : unassigned) {
            shareBySubtask.computeIfAbsent(state.getNextSubtaskIndex(), k -> new ArrayList<>())
                    .add(table);
        }
        LOG.info("DebeziumTableSplitEnumerator restored {} unassigned table(s)", unassigned.size());
    }

    @Override
    public void close() {
        // no external resources
    }
}
