/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators.join;

import io.nop.stream.core.common.buffer.PerKeyOrderedBuffer;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.JoinMatch;
import io.nop.stream.core.model.JoinSideRecord;
import io.nop.stream.core.model.JoinType;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WI13: the dual-stream equi-join operator (join-operator.md §3/§4). Consumes the
 * union of both side-tagged inputs (each element is a {@link JoinSideRecord}
 * carrying its pre-computed equi-key, produced by the DSL builder's per-side tag
 * map after {@code union → keyBy(equiKey)}), and joins them per key. Event-time
 * semantics (D6=(a)); D1=(a) final-value output — pairs and outer completions emit
 * exactly once, no retract markers.
 *
 * <p>Two forms:
 *
 * <ul>
 *   <li><b>hash join</b> ({@code windowDuration == null}): a matching pair emits
 *       eagerly when its later element arrives; at each watermark advance, records
 *       with ts ≤ watermark that never matched are completed with a null other side
 *       (LEFT/RIGHT/FULL only — INNER emits nothing unmatched), then all elements
 *       up to the watermark are trimmed. Matching lifetime ends at the watermark
 *       frontier: a later element does not match an already-trimmed record
 *       (bounded state per join-operator.md §7 — no unbounded hash buffer).</li>
 *   <li><b>window join</b> ({@code windowDuration != null}, tumbling event-time):
 *       pairs match only within the same window; the window fires at
 *       {@code windowEnd + timeout} (timeout = the D9-released lateness grace),
 *       unmatched records on the completion side (LEFT only — the builder rejects
 *       RIGHT/FULL window joins) are completed, and the window's entries are
 *       dropped (bounded state). Records arriving after their window fired are
 *       dropped on sight.</li>
 * </ul>
 *
 * <p>State design (roadmap §十 binding — "join 缓冲同规则", no self-managed map as
 * the durable authority): a {@link PerKeyOrderedBuffer} (WI12's reusable ordered
 * buffer, relocated to core so both the window and join operators share it) is the
 * in-memory working view; every buffered element is ALSO persisted into keyed
 * MapState (one entry per element, entry key {@code bufferKey\0ts\0seq}). Buffer
 * key = {@code equiKey\0L|R} plus {@code \0windowStart} in the window form; the
 * equi-key must not contain the {@code \u0000} separator character. The
 * {@code matched} flag lives in the buffered {@link JoinSideRecord} (working view)
 * and its durable copy is rewritten when a pair emits, so a restored buffer never
 * double-completes. The keyed backend is self-provisioned in {@link #open()} per
 * the ProcessOperator precedent; restore flows through the keyed lineage while the
 * operator-state entry carries the working view across the boundary (WI12 pattern —
 * note the engine's keyed MapState reads are scoped to the CURRENT key, so the
 * whole-key-set view transport is what restores computation state).
 */
public class EquiJoinOperator<L, R> extends AbstractStreamOperator<JoinMatch<L, R>>
        implements OneInputStreamOperator<JoinSideRecord<Object>, JoinMatch<L, R>> {

    private static final long serialVersionUID = 1L;

    private static final String BUFFER_STATE = "equi-join-buffer";
    private static final char SEP = '\u0000';
    private static final String LEFT_TAG = "L";
    private static final String RIGHT_TAG = "R";

    private final JoinType joinType;
    private final Long windowDuration;
    private final long timeout;

    /** working view: bufferKey → ordered entries (rebuilt from keyed state on restore). */
    private transient PerKeyOrderedBuffer<String, JoinSideRecord<Object>> buffer;
    /** durable copy: one keyed-state entry per buffered element. */
    private transient MapState<String, JoinSideRecord<Object>> bufferState;
    private long currentWatermark;

    public EquiJoinOperator(JoinType joinType, Long windowDuration, long timeout) {
        this.joinType = joinType;
        this.windowDuration = windowDuration;
        this.timeout = timeout;
    }

    @Override
    public EquiJoinOperator<L, R> copyForSubtask() {
        // fresh instance per subtask — the keyed backend is provisioned per task in
        // open(), so parallel subtasks never share state (WI12 audit B-2 pattern)
        return new EquiJoinOperator<>(joinType, windowDuration, timeout);
    }

    @Override
    public void open() throws Exception {
        super.open();
        // ProcessOperator precedent: self-provision the keyed backend so the
        // buffer's durable copy lives in keyed state lineage
        if (keyedStateBackend == null && stateBackend != null) {
            keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class);
            applyPendingRestoreState();
        }
        buffer = new PerKeyOrderedBuffer<>();
        if (keyedStateBackend != null) {
            bufferState = rawKeyedBackend().getMapState(
                    new MapStateDescriptor(BUFFER_STATE, String.class, JoinSideRecord.class));
        }
    }

    @Override
    public OperatorSnapshotResult snapshotState(io.nop.stream.core.checkpoint.StateSnapshotContext context) throws Exception {
        // super carries the keyed-state lineage (§十 durable channel); the
        // operator-state entry is the working-view restore transport (WI12 pattern)
        OperatorSnapshotResult result = super.snapshotState(context);
        result.putOperatorState(BUFFER_STATE, buffer);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        // restoreState may rebuild the backend's state objects (MemoryStateSerDe
        // clears and re-creates them per state name) — re-acquire the handle so
        // this operator's field binds to the restored state object, not an
        // orphaned pre-restore instance (writes through an orphan would bypass
        // the snapshot lineage entirely)
        if (keyedStateBackend != null) {
            bufferState = rawKeyedBackend().getMapState(
                    new MapStateDescriptor(BUFFER_STATE, String.class, JoinSideRecord.class));
        }
        if (snapshotResult != null) {
            Object restored = snapshotResult.getOperatorState(BUFFER_STATE);
            if (restored instanceof PerKeyOrderedBuffer) {
                buffer.putAll((PerKeyOrderedBuffer<String, JoinSideRecord<Object>>) restored);
            }
        }
    }

    @Override
    public void processElement(StreamRecord<JoinSideRecord<Object>> element) throws Exception {
        JoinSideRecord<Object> rec = element.getValue();
        long ts = element.hasTimestamp() ? element.getTimestamp() : 0L;
        setCurrentKey(rec.getEquiKey());
        if (windowDuration != null && isLateForWindow(ts)) {
            // the window already fired and dropped: late records are dropped on
            // sight (fire-and-update semantics — no completion, no state growth)
            return;
        }
        emitMatches(rec, ts);
        String bufferKey = bufferKey(rec, ts);
        long[] tsKey = buffer.add(bufferKey, ts, rec);
        persistCurrent(bufferKey, tsKey, rec);
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        currentWatermark = mark.getTimestamp();
        if (windowDuration == null) {
            completeAndTrimHash();
        } else {
            fireReachedWindows();
        }
        output.emitWatermark(mark);
    }

    @Override
    public void processWatermarkStatus(WatermarkStatus status) {
        output.emitWatermarkStatus(status);
    }

    // ----------------------------------------------------------------
    // hash form
    // ----------------------------------------------------------------

    private void completeAndTrimHash() throws Exception {
        for (String key : snapshotKeys()) {
            if (isCompletionSide(sideOf(key))) {
                completeUnmatched(key);
            }
            trimToWatermark(key, currentWatermark);
        }
    }

    // ----------------------------------------------------------------
    // window form
    // ----------------------------------------------------------------

    private boolean isLateForWindow(long ts) {
        return currentWatermark >= windowStart(ts) + windowDuration + timeout;
    }

    private long windowStart(long ts) {
        return Math.floorDiv(ts, windowDuration) * windowDuration;
    }

    private void fireReachedWindows() throws Exception {
        for (String key : snapshotKeys()) {
            long winStart = windowStartOf(key);
            if (currentWatermark >= winStart + windowDuration + timeout) {
                fireWindow(key, winStart);
            }
        }
    }

    /** fires one window: completes unmatched records on the completion side, drops the window's entries on both sides. */
    private void fireWindow(String key, long winStart) throws Exception {
        if (isCompletionSide(sideOf(key))) {
            completeUnmatched(key);
        }
        dropAll(key);
        String twin = twinWindowKey(key, winStart);
        if (twin != null && buffer.size(twin) > 0) {
            dropAll(twin);
        }
    }

    // ----------------------------------------------------------------
    // matching
    // ----------------------------------------------------------------

    private void emitMatches(JoinSideRecord<Object> rec, long ts) throws Exception {
        String otherKey = bufferKeyOf(rec.getEquiKey(), !rec.isLeft(), ts);
        for (long[] tsKey : buffer.sortedTimestamps(otherKey)) {
            JoinSideRecord<Object> other = buffer.valueAt(otherKey, tsKey);
            if (windowDuration != null && windowStart(ts) != windowStart(tsKey[0])) {
                continue;
            }
            output.collect(new StreamRecord<>(matchOf(rec, other), ts));
            rec.setMatched(true);
            other.setMatched(true);
            // both durable copies flip in lockstep so a restored buffer never
            // re-completes a matched record (the arriving record's own durable
            // copy is written after this loop, already flipped)
            persistCurrent(otherKey, tsKey, other);
        }
    }

    private void completeUnmatched(String key) throws Exception {
        for (long[] tsKey : buffer.sortedTimestamps(key)) {
            JoinSideRecord<Object> rec = buffer.valueAt(key, tsKey);
            if (!rec.isMatched()) {
                output.collect(new StreamRecord<>(completionOf(sideOf(key), rec), tsKey[0]));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private JoinMatch<L, R> matchOf(JoinSideRecord<Object> a, JoinSideRecord<Object> b) {
        JoinSideRecord<Object> left = a.isLeft() ? a : b;
        JoinSideRecord<Object> right = a.isLeft() ? b : a;
        return new JoinMatch<>((L) left.getPayload(), (R) right.getPayload());
    }

    @SuppressWarnings("unchecked")
    private JoinMatch<L, R> completionOf(String side, JoinSideRecord<Object> rec) {
        return LEFT_TAG.equals(side)
                ? new JoinMatch<>((L) rec.getPayload(), null)
                : new JoinMatch<>(null, (R) rec.getPayload());
    }

    private boolean isCompletionSide(String side) {
        if (JoinType.LEFT == joinType) {
            return LEFT_TAG.equals(side);
        }
        if (JoinType.RIGHT == joinType) {
            return RIGHT_TAG.equals(side);
        }
        if (JoinType.FULL == joinType) {
            return true;
        }
        return false; // INNER never completes
    }

    // ----------------------------------------------------------------
    // state plumbing
    // ----------------------------------------------------------------

    private void trimToWatermark(String key, long watermark) throws Exception {
        removeDurable(key, buffer.trimToWatermarkWithKeys(key, watermark));
    }

    private void dropAll(String key) throws Exception {
        removeDurable(key, buffer.trimToWatermarkWithKeys(key, Long.MAX_VALUE));
    }

    private void removeDurable(String key, List<long[]> trimmed) throws Exception {
        if (bufferState == null) {
            return;
        }
        for (long[] tsKey : trimmed) {
            try {
                bufferState.remove(durableKey(key, tsKey[0], tsKey[1]));
            } catch (Exception e) {
                throw stateError("failed to remove equi-join buffer entry from keyed state", e);
            }
        }
    }

    private void persistCurrent(String bufferKey, long[] tsKey, JoinSideRecord<Object> rec) throws Exception {
        if (bufferState == null) {
            return;
        }
        try {
            bufferState.put(durableKey(bufferKey, tsKey[0], tsKey[1]), rec);
        } catch (Exception e) {
            throw stateError("failed to persist equi-join buffer entry into keyed state", e);
        }
    }

    private static String durableKey(String bufferKey, long ts, long seq) {
        return bufferKey + SEP + ts + SEP + seq;
    }

    private String bufferKey(JoinSideRecord<?> rec, long ts) {
        return bufferKeyOf(rec.getEquiKey(), rec.isLeft(), ts);
    }

    private String bufferKeyOf(Object equiKey, boolean left, long ts) {
        String key = String.valueOf(equiKey) + SEP + (left ? LEFT_TAG : RIGHT_TAG);
        if (windowDuration != null) {
            key = key + SEP + windowStart(ts);
        }
        return key;
    }

    private String twinWindowKey(String key, long winStart) {
        // key = equiKey + SEP + side (+ SEP + winStart in the window form)
        String side = sideOf(key);
        String equiKey = equiKeyOf(key);
        String twinSide = LEFT_TAG.equals(side) ? RIGHT_TAG : LEFT_TAG;
        return windowDuration == null
                ? equiKey + SEP + twinSide
                : equiKey + SEP + twinSide + SEP + winStart;
    }

    private String equiKeyOf(String bufferKey) {
        String withoutWin = windowDuration == null ? bufferKey : bufferKey.substring(0, bufferKey.lastIndexOf(SEP));
        int idx = withoutWin.lastIndexOf(SEP);
        return withoutWin.substring(0, idx);
    }

    private String sideOf(String bufferKey) {
        String withoutWin = windowDuration == null ? bufferKey : bufferKey.substring(0, bufferKey.lastIndexOf(SEP));
        return withoutWin.substring(withoutWin.lastIndexOf(SEP) + 1);
    }

    private long windowStartOf(String bufferKey) {
        return Long.parseLong(bufferKey.substring(bufferKey.lastIndexOf(SEP) + 1));
    }

    /** buffer keys at call time (watermark processing must not iterate a mutating view). */
    private List<String> snapshotKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : buffer.keys()) {
            keys.add(key);
        }
        return keys;
    }

    private NopException stateError(String detail, Exception e) {
        NopException ex = new StreamException(NopStreamErrors.ERR_STREAM_STATE_ERROR, e);
        ex.param("detail", detail);
        return ex;
    }

    // ----------------------------------------------------------------
    // test/diagnostic surface
    // ----------------------------------------------------------------

    /** test/diagnostic: the keyed state size visible to this operator. */
    public int keyedBufferEntries() {
        if (bufferState == null) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<String, JoinSideRecord<Object>> ignored : bufferState.entries()) {
            n++;
        }
        return n;
    }

    /**
     * test/diagnostic: whether any durable entry in the CURRENT key's scope carries
     * {@code matched=true} (call {@code setCurrentKey(equiKey)} first — the engine's
     * keyed MapState reads are scoped to the current key).
     */
    public boolean keyedAnyMatched() {
        if (bufferState == null) {
            return false;
        }
        for (Map.Entry<String, JoinSideRecord<Object>> e : bufferState.entries()) {
            if (e.getValue().isMatched()) {
                return true;
            }
        }
        return false;
    }

    /** test/diagnostic: total buffered (working view) entries across all keys. */
    public int bufferedCount() {
        int n = 0;
        for (String key : buffer.keys()) {
            n += buffer.size(key);
        }
        return n;
    }

    /** the keyed state name carrying the durable buffer copy. */
    public static String bufferStateName() {
        return BUFFER_STATE;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private io.nop.stream.core.common.state.backend.IKeyedStateBackend<String> rawKeyedBackend() {
        return (io.nop.stream.core.common.state.backend.IKeyedStateBackend) keyedStateBackend;
    }
}
