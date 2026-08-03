/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source.coordinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.source.AssignmentDeliveryService;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceSplit;
import io.nop.stream.core.source.SplitAssignmentProxy;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;

/**
 * Stage 49 D2/D3: in-process coordinator for FLIP-27 style split-based sources in LOCAL
 * execution mode. Owns the {@link SplitEnumerator} lifecycle, drives initial split
 * discovery + assignment, and snapshots/restores enumerator state into
 * {@code EpochManifest.sourceEnumeratorSnapshots}.
 *
 * <p>This is the LOCAL-mode landing of the D7 OperatorCoordinator bypass: the enumerator
 * is hardwired to this coordinator (which runs on the execution-driver thread), not to a
 * generic {@code OperatorCoordinator} abstraction. DISTRIBUTED mode would carry the same
 * semantics over Stage 39 cross-JVM control RPC (not implemented in v1).
 *
 * <p>Thread safety: enumerator methods are invoked from the coordinator thread (the thread
 * that owns this LocalSourceCoordinator). Reader interactions come through the
 * {@link ReaderChannel} which is safe for the reader task thread to call.
 */
@Internal
public class LocalSourceCoordinator<T extends SourceSplit, StateT> {

    private static final Logger LOG = LoggerFactory.getLogger(LocalSourceCoordinator.class);

    private final String vertexId;
    private final Source<?, T, StateT> source;
    private final int totalParallelism;

    private final Object enumeratorLock = new Object();

    private SplitEnumerator<T, StateT> enumerator;
    private SplitEnumeratorContext<T> enumeratorContext;
    private boolean started;

    /** Per-subtask reader channels. Each channel is a thread-safe queue + state flag. */
    private final Map<Integer, ReaderChannel<T>> readerChannels = new ConcurrentHashMap<>();

    /** Splits that have been reported finished by readers (for restore round-trip). */
    private final Set<String> finishedSplits = ConcurrentHashMap.newKeySet();

    /**
     * Set to true once the enumerator has been started AND handleSplitRequest has been
     * called at least once after start (so any initial-discovery splits have already been
     * pushed). Readers use this via {@link ReaderChannel#isEnumeratorExhausted()} to
     * short-circuit isFinished() when the enumerator has no more splits to give.
     */
    private volatile boolean enumeratorQueried;

    public LocalSourceCoordinator(String vertexId,
                                  Source<?, T, StateT> source,
                                  int totalParallelism) {
        this.vertexId = vertexId;
        this.source = source;
        this.totalParallelism = totalParallelism;
    }

    /**
     * Boots the enumerator if not yet booted. Called when the first reader registers.
     * If a checkpoint state is supplied (restore path), the enumerator is restored from it.
     */
    public void startEnumerator(StateT restoredState) {
        synchronized (enumeratorLock) {
            if (started) {
                return;
            }
            if (restoredState != null) {
                enumerator = source.restoreEnumerator(restoredState);
            } else {
                enumerator = source.createEnumerator();
            }

            // The delivery service pushes splits to per-subtask queues (one queue per reader).
            enumeratorContext = new SplitEnumeratorContext<>(totalParallelism, new AssignmentDeliveryService<T>() {
                @Override
                public void assignSplits(int subtaskIndex, List<T> splits) {
                    if (splits == null || splits.isEmpty()) {
                        return;
                    }
                    ReaderChannel<T> channel = readerChannels.get(subtaskIndex);
                    if (channel == null) {
                        LOG.warn("assignSplits to unregistered subtask {}: {} splits dropped",
                                subtaskIndex, splits.size());
                        return;
                    }
                    channel.deliver(splits);
                }

                @Override
                public boolean isReaderRegistered(int subtaskIndex) {
                    return readerChannels.containsKey(subtaskIndex);
                }
            });

            try {
                if (restoredState != null) {
                    enumerator.restoreState(restoredState);
                }
                enumerator.start(enumeratorContext);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start source enumerator for vertex " + vertexId, e);
            }
            started = true;
            LOG.debug("Source enumerator started for vertex {} (restored={})", vertexId, restoredState != null);
        }
    }

    /**
     * Registers a reader subtask and returns its assignment channel. The first reader to
     * register triggers enumerator boot (no restore state).
     */
    public ReaderChannel<T> registerReader(int subtaskIndex) {
        ReaderChannel<T> channel = new ReaderChannel<>(subtaskIndex, this);
        ReaderChannel<T> prev = readerChannels.putIfAbsent(subtaskIndex, channel);
        if (prev != null) {
            return prev;
        }
        // Boot the enumerator on the first-ever registration (fresh start path).
        startEnumerator(null);

        try {
            enumerator.addReader(subtaskIndex);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to addReader " + subtaskIndex + " for vertex " + vertexId, e);
        }
        return channel;
    }

    /**
     * Reader-side channel for receiving splits + reporting finished splits / requests.
     * Used as the {@link SplitAssignmentProxy} on the {@link SourceReaderContext}.
     *
     * <p>Delivered splits are pushed to the reader via the {@code onSplitsDelivered} callback
     * (set by {@code SourceReaderOperator.wireCoordinatorChannel()} to call
     * {@code reader.addSplits(...)}). This bridges the coordinator's push-based delivery
     * with the reader's {@code addSplits} entry point.
     */
    public static class ReaderChannel<T extends SourceSplit> implements SplitAssignmentProxy {
        private final int subtaskIndex;
        private final LocalSourceCoordinator<T, ?> owner;
        private final java.util.List<T> pendingSplits = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private volatile java.util.function.Consumer<List<T>> onSplitsDelivered;

        ReaderChannel(int subtaskIndex, LocalSourceCoordinator<T, ?> owner) {
            this.subtaskIndex = subtaskIndex;
            this.owner = owner;
        }

        /** Wire the callback that runs when splits are delivered. Idempotent. */
        public void setOnSplitsDelivered(java.util.function.Consumer<List<T>> callback) {
            this.onSplitsDelivered = callback;
            // Drain any splits that arrived before the callback was set (race: enumerator
            // assigned splits during registerReader before the operator wired the reader).
            drainPending();
        }

        void deliver(List<T> splits) {
            if (splits == null || splits.isEmpty()) {
                return;
            }
            pendingSplits.addAll(splits);
            drainPending();
        }

        private void drainPending() {
            java.util.function.Consumer<List<T>> cb = onSplitsDelivered;
            if (cb == null) {
                return;
            }
            List<T> toPush;
            synchronized (pendingSplits) {
                if (pendingSplits.isEmpty()) {
                    return;
                }
                toPush = new ArrayList<>(pendingSplits);
                pendingSplits.clear();
            }
            if (!toPush.isEmpty()) {
                cb.accept(toPush);
            }
        }

        /** Test accessor: any splits pending delivery (not yet consumed by reader). */
        public int pendingCount() {
            synchronized (pendingSplits) {
                return pendingSplits.size();
            }
        }

        @Override
        public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
            if (subtaskIndex != this.subtaskIndex) {
                return;
            }
            try {
                owner.enumerator.handleSplitRequest(subtaskIndex, reason);
            } catch (Exception e) {
                LOG.warn("handleSplitRequest failed for subtask {}", subtaskIndex, e);
            }
            // Mark that the enumerator has been queried post-start; readers use this to
            // short-circuit isFinished() when the enumerator had no splits to give.
            owner.enumeratorQueried = true;
        }

        /**
         * Returns true once the enumerator has been queried at least once post-start (so
         * initial-discovery splits have been pushed) AND no splits are currently pending
         * delivery to this reader. Readers use this to terminate when the source has
         * nothing more to give (e.g. empty directory).
         */
        public boolean isEnumeratorExhausted() {
            return owner.enumeratorQueried && pendingCount() == 0;
        }

        @Override
        public void reportFinishedSplits(int subtaskIndex, List<String> finishedSplitIds) {
            if (subtaskIndex != this.subtaskIndex) {
                return;
            }
            owner.finishedSplits.addAll(finishedSplitIds);
        }

        public int getSubtaskIndex() {
            return subtaskIndex;
        }
    }

    /**
     * Snapshots the enumerator state for checkpoint {@code checkpointId}. Called from the
     * coordinator thread (the same thread that owns this LocalSourceCoordinator).
     */
    public SourceEnumeratorSerializedState snapshotState(long checkpointId) {
        synchronized (enumeratorLock) {
            if (!started || enumerator == null) {
                return null;
            }
            try {
                StateT state = enumerator.snapshotState(checkpointId);
                SimpleVersionedSerializer<StateT> serializer = source.getEnumeratorStateSerializer();
                if (serializer == null) {
                    throw new IllegalStateException(
                            "Source " + source.getClass() + " returned null enumerator state serializer; "
                                    + "cannot checkpoint source enumerator state for vertex " + vertexId);
                }
                byte[] bytes = serializer.serialize(state);
                return new SourceEnumeratorSerializedState(serializer.getVersion(), bytes);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to snapshot source enumerator state for vertex " + vertexId, e);
            }
        }
    }

    /** Returns the source descriptor (used for serializer lookup at restore time). */
    public Source<?, T, StateT> getSource() {
        return source;
    }

    public String getVertexId() {
        return vertexId;
    }

    public int getTotalParallelism() {
        return totalParallelism;
    }

    public Set<String> getFinishedSplits() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(finishedSplits));
    }

    public void close() {
        synchronized (enumeratorLock) {
            if (enumerator != null) {
                try {
                    enumerator.close();
                } catch (Exception e) {
                    LOG.warn("Failed to close source enumerator for vertex {}", vertexId, e);
                }
            }
            started = false;
        }
    }

    /**
     * Returns a snapshot of currently registered reader subtask indices. Used in tests to
     * verify wiring.
     */
    public Set<Integer> getRegisteredReaders() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(readerChannels.keySet()));
    }

    /**
     * Aggregated snapshot of all per-reader pending splits (not yet pushed to reader).
     * Used in tests / restore verification.
     */
    public Map<Integer, List<String>> getPendingSplitIdsByReader() {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ReaderChannel<T>> entry : readerChannels.entrySet()) {
            List<String> ids = new ArrayList<>();
            // Snapshot via reflection-free path: pendingCount tells us if any are buffered
            // but not their ids (the channel doesn't expose them to the outside). Tests
            // that need ids should wait for delivery to the reader + check reader state.
            result.put(entry.getKey(), ids);
        }
        return result;
    }
}
