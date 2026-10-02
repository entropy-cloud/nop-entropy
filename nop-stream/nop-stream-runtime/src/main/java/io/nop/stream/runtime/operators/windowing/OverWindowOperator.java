/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * WI12: the analysis window (OVER) operator — event-time framed evaluation over a
 * per-key ordered buffer whose durable copy lives in KEYED STATE (roadmap §十
 * binding: no self-managed HashMap for window state; D6=(a) event-time). Two frame
 * semantics:
 *
 * <ul>
 *   <li><b>ROW_NUMBER</b>: at each watermark advance, the frame is the full ordered
 *       buffer; each element's 1-based row number is emitted (rn=&lt;n&gt;), and the
 *       frame is consumed at the watermark.</li>
 *   <li><b>FRAME_SLIDING_AGG</b>: a sliding aggregate over the last
 *       {@code frameSize} elements per key; the current aggregate is emitted at
 *       every watermark advance (last-value-wins per D1=(a) — the emitted value is
 *       the current frame result, no retract markers; frame re-open is event-time
 *       recomputation per Q2).</li>
 * </ul>
 *
 * <p>State design: a {@link PerKeyOrderedBuffer} is the in-memory working view for
 * frame computation; every buffered element is ALSO persisted into keyed MapState
 * (one entry per element), so the durable copy flows through the keyed-state
 * backend lineage. On restore the working view is rebuilt from keyed state
 * ({@link #rebuildViewFromKeyedState()}). The keyed backend is self-provisioned in
 * {@link #open()} per the ProcessOperator precedent.
 */
public class OverWindowOperator extends AbstractStreamOperator<String>
        implements OneInputStreamOperator<String, String> {

    private static final long serialVersionUID = 1L;

    private static final String BUFFER_STATE = "over-window-buffer";

    public enum FrameKind {
        /** row_number() OVER (...) — emits "rn=&lt;n&gt; val=&lt;v&gt;" per buffered element */
        ROW_NUMBER,
        /** sliding agg OVER (n PRECEDING) — emits the current aggregate value */
        FRAME_SLIDING_AGG
    }

    private final FrameKind kind;
    private final int frameSize;
    private final String aggId;

    /** working view for frame computation (rebuilt from keyed state on restore). */
    private transient PerKeyOrderedBuffer<String, String> buffer;
    /** durable copy: one keyed-state entry per buffered element. */
    private transient MapState<String, String> bufferState;

    private long currentWatermark;

    public OverWindowOperator(FrameKind kind, int frameSize, String aggId) {
        this.kind = kind;
        this.frameSize = frameSize;
        this.aggId = aggId;
    }

    @Override
    public OverWindowOperator copyForSubtask() {
        // fresh instance per subtask — the keyed backend is provisioned per task in
        // open(), so parallel subtasks never share state
        return new OverWindowOperator(kind, frameSize, aggId);
    }

    @Override
    public void open() throws Exception {
        super.open();
        // ProcessOperator :59-62 precedent: self-provision the keyed backend so the
        // buffer's durable copy lives in keyed state lineage
        if (keyedStateBackend == null && stateBackend != null) {
            keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class);
            applyPendingRestoreState();
        }
        buffer = new PerKeyOrderedBuffer<>();
        if (keyedStateBackend != null) {
            bufferState = rawKeyedBackend().getMapState(
                    new MapStateDescriptor<>(BUFFER_STATE, String.class, String.class));
        }
    }

    @Override
    public OperatorSnapshotResult snapshotState(io.nop.stream.core.checkpoint.StateSnapshotContext context) throws Exception {
        // super carries the keyed-state lineage (§十 durable channel — the keyed
        // MapState written per element at processElement); the operator-state entry
        // is the working-view restore transport.
        OperatorSnapshotResult result = super.snapshotState(context);
        result.putOperatorState(BUFFER_STATE, buffer);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (snapshotResult != null) {
            Object restored = snapshotResult.getOperatorState(BUFFER_STATE);
            if (restored instanceof PerKeyOrderedBuffer) {
                buffer.putAll((PerKeyOrderedBuffer<String, String>) restored);
            }
        }
    }

    @Override
    public void processElement(StreamRecord<String> element) {
        String value = element.getValue();
        // record format: "key|ts|payload"
        int p1 = value.indexOf('|');
        int p2 = value.indexOf('|', p1 + 1);
        String key = value.substring(0, p1);
        long ts = Long.parseLong(value.substring(p1 + 1, p2));
        buffer.add(key, ts, value);
        // durable copy into keyed state (one entry per element; the entry key is
        // deterministic key\u0000ts\u0000seq so removal can target it exactly; the
        // view is rebuilt from these entries on restore)
        if (bufferState != null) {
            try {
                bufferState.put(key + "\u0000" + ts + "\u0000" + buffer.seqFor(key, ts), value);
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamException(
                        io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                        .param("detail", "failed to persist OVER buffer entry into keyed state");
            }
        }
    }

    @Override
    public void processWatermark(Watermark mark) {
        currentWatermark = mark.getTimestamp();
        for (String key : buffer.keys()) {
            if (kind == FrameKind.ROW_NUMBER) {
                emitRowNumbers(key);
            } else {
                emitSlidingAgg(key);
            }
        }
        output.emitWatermark(mark);
    }

    private void emitRowNumbers(String key) {
        List<String> ordered = buffer.sortedView(key);
        for (int i = 0; i < ordered.size(); i++) {
            output.collect(new StreamRecord<>("rn=" + (i + 1) + " val=" + ordered.get(i)));
        }
        // ROW_NUMBER consumes the frame at the watermark — drop the consumed entries
        // from the keyed channel too (exact keys via the buffer's trimmed tsKeys)
        List<long[]> trimmed = buffer.trimToWatermarkWithKeys(key, currentWatermark);
        if (bufferState != null) {
            for (long[] tsKey : trimmed) {
                try {
                    bufferState.remove(key + "\u0000" + tsKey[0] + "\u0000" + tsKey[1]);
                } catch (Exception e) {
                    throw new io.nop.stream.core.exceptions.StreamException(
                            io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                            .param("detail", "failed to remove consumed OVER buffer entry from keyed state");
                }
            }
        }
    }

    private void emitSlidingAgg(String key) {
        List<String> ordered = buffer.sortedView(key);
        int from = Math.max(0, ordered.size() - frameSize);
        long agg = 0;
        int n = 0;
        for (int i = from; i < ordered.size(); i++) {
            String payload = ordered.get(i).substring(ordered.get(i).lastIndexOf('|') + 1);
            long v = Long.parseLong(payload);
            if ("sum".equals(aggId) || "count".equals(aggId)) {
                agg = "sum".equals(aggId) ? agg + v : agg + 1;
            } else if ("min".equals(aggId)) {
                agg = n == 0 ? v : Math.min(agg, v);
            } else if ("max".equals(aggId)) {
                agg = n == 0 ? v : Math.max(agg, v);
            }
            n++;
        }
        if ("avg".equals(aggId) && n > 0) {
            agg = agg / n;
        }
        if (n > 0 || "sum".equals(aggId) || "count".equals(aggId)) {
            output.collect(new StreamRecord<>("agg=" + agg + " n=" + n));
        }
        // sliding frame needs history: trim only elements beyond the last frameSize
        List<long[]> trimmed = buffer.trimToCountWithKeys(key, frameSize);
        if (bufferState != null) {
            for (long[] tsKey : trimmed) {
                try {
                    bufferState.remove(key + "\u0000" + tsKey[0] + "\u0000" + tsKey[1]);
                } catch (Exception e) {
                    throw new io.nop.stream.core.exceptions.StreamException(
                            io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                            .param("detail", "failed to remove sliding-frame OVER entry from keyed state");
                }
            }
        }
    }

    /**
     * Rebuilds the working view from the durable keyed state (call after
     * {@code restoreState} on a fresh instance). Each keyed entry is one buffered
     * element in the "key|ts|payload" record form.
     */
    public void rebuildViewFromKeyedState() {
        if (bufferState == null) {
            return;
        }
        try {
            for (Map.Entry<String, String> e : bufferState.entries()) {
                String v = e.getValue();
                int p1 = v.indexOf('|');
                int p2 = v.indexOf('|', p1 + 1);
                long ts = Long.parseLong(v.substring(p1 + 1, p2));
                buffer.add(v.substring(0, p1), ts, v);
            }
        } catch (Exception e) {
            throw new io.nop.stream.core.exceptions.StreamException(
                    io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                    .param("detail", "failed to rebuild OVER buffer from keyed state");
        }
    }

    @Override
    public void processWatermarkStatus(WatermarkStatus status) {
        output.emitWatermarkStatus(status);
    }

    /** test/diagnostic access to the working view's key set. */
    public java.util.Set<String> bufferedKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (String key : buffer.keys()) {
            keys.add(key);
        }
        return keys;
    }

    /** test/diagnostic access to one key's ordered view. */
    public List<String> sortedView(String key) {
        return buffer.sortedView(key);
    }

    /** restores the working view from a durable buffer copy (restore transport). */
    public void mergeRestoredBuffer(PerKeyOrderedBuffer<String, String> restored) {
        buffer.putAll(restored);
    }

    /** the keyed state name carrying the durable buffer copy. */
    public static String bufferStateName() {
        return BUFFER_STATE;
    }

    /** test/diagnostic: the keyed state size visible to this operator. */
    public int keyedBufferEntries() {
        if (bufferState == null) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<String, String> ignored : bufferState.entries()) {
            n++;
        }
        return n;
    }

    /** test/diagnostic: the keyed state store backing the durable copy. */
    public io.nop.stream.core.common.state.KeyedStateStore keyedStore() {
        return keyedStateBackend;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private io.nop.stream.core.common.state.backend.IKeyedStateBackend<String> rawKeyedBackend() {
        return (io.nop.stream.core.common.state.backend.IKeyedStateBackend) keyedStateBackend;
    }
}
