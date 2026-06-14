package io.nop.ai.agent.reliability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Functional in-memory {@link ICheckpointManager} implementation — the sibling
 * of {@link NoOpCheckpoint} (pass-through default). Per-session checkpoints are
 * stored in-memory so the save→retrieve round-trip is available within a single
 * process. This is the simplest functional form proving the
 * {@link ICheckpointManager} contract is non-hollow (consistent with the
 * plan 177 {@code CountingLedger} pattern that validated the L3-6 contract
 * link with a real in-memory counter).
 *
 * <p><b>In-memory, non-persistent</b>: checkpoints survive only as long as this
 * instance lives. A DB-backed persistent checkpoint store (e.g.
 * {@code DBCheckpointStore}) is an independent successor, following the plan
 * 179 {@code DBDenialLedger} pattern. The in-memory default is fully usable
 * for single-process crash recovery and for testing the dispatch-loop wiring.
 *
 * <p><b>Thread safety</b>: guaranteed by concurrent data structures.
 * Per-session checkpoint lists are stored as synchronized {@link ArrayList}s
 * keyed by {@code sessionId} in a {@link ConcurrentHashMap}. The watermark
 * index ({@link #getCheckpoint}) is a separate {@link ConcurrentHashMap}.
 * Multiple sessions may access the same instance concurrently; per-session
 * lists are isolated by key.
 *
 * <p><b>Checkpoint trigger range</b> (design §5.4a Decision): this manager
 * records a checkpoint for <b>every</b> tool execution, not just long-running
 * tools. The §5.4a "only long-running tool" qualifier is intended to bound
 * persistence I/O frequency and therefore applies to the future DB-backed /
 * journal successor (roadmap A4), not to this in-memory default. This is
 * consistent with the L3-6 pattern where plan 177's in-memory counter did
 * not filter by trigger source, deferring filtering to the plan 179 DB layer.
 *
 * <p><b>Retention</b>: the per-session list grows unbounded (no
 * rotation/cap). At test scale this is safe; a retention/rotation policy is
 * a non-blocking follow-up.
 */
public class ToolExecutionCheckpoint implements ICheckpointManager {

    private final ConcurrentHashMap<String, List<Checkpoint>> bySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Checkpoint> byWatermark = new ConcurrentHashMap<>();

    @Override
    public void saveCheckpoint(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "Checkpoint must not be null");
        String sid = checkpoint.getSessionId();
        if (sid != null) {
            bySession.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(checkpoint);
        }
        byWatermark.put(checkpoint.getWatermark(), checkpoint);
    }

    @Override
    public Checkpoint getLatestCheckpoint(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        List<Checkpoint> list = bySession.get(sessionId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        synchronized (list) {
            return list.get(list.size() - 1);
        }
    }

    @Override
    public Checkpoint getCheckpoint(String watermark) {
        if (watermark == null) {
            return null;
        }
        return byWatermark.get(watermark);
    }
}
