/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 43 (unaligned checkpoint): per-channel in-flight records captured at the
 * moment a checkpoint switches to unaligned mode (see {@code checkpoint-design.md}
 * §2.11). Rides the barrier ACK path into {@link TaskEpochSnapshot} and is
 * replayed on recovery before the task resumes reading new upstream data.
 *
 * <p><strong>Per-channel semantics</strong> (§2.11.2): for an <em>aligned</em>
 * channel the captured records are those buffered AFTER the barrier; for a
 * <em>non-aligned</em> channel they are ALL buffered records (pre-barrier). The
 * distinction is enforced by {@code InputGate} calling
 * {@code InputChannel.captureInFlightData(barrierReceived)} at the correct
 * moment; this class simply holds the result keyed by channel index.
 *
 * <p><strong>Persistence</strong>: {@link #toSerializableForm()} /
 * {@link #fromSerializableForm(Map)} convert each {@link StreamElement} to a
 * self-describing {@link StreamMessageEnvelope}-style map (records carry their
 * Java value type so they can be reconstructed). This makes {@code ChannelState}
 * JSON-serializable within {@link TaskEpochSnapshot} via {@code CheckpointSerDe}
 * while keeping the in-memory representation as live {@link StreamElement}
 * objects for the capture/replay path.
 */
public class ChannelState implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ChannelState.class);

    /**
     * channelIndex → in-flight records for that channel. {@code TreeMap} so
     * iteration order is deterministic (lower channel indices first) — important
     * for stable serialization and deterministic replay.
     */
    private final Map<Integer, List<StreamElement>> recordsByChannel = new TreeMap<>();

    public ChannelState() {
    }

    /**
     * Records the captured in-flight elements for a channel.
     *
     * @param channelIndex the channel index within the owning {@code InputGate}
     * @param elements     the drained in-flight elements (may be null/empty)
     */
    public void putRecords(int channelIndex, List<StreamElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        recordsByChannel.put(channelIndex, new ArrayList<>(elements));
    }

    /**
     * @return the in-flight elements for the given channel, or empty list
     */
    public List<StreamElement> getRecords(int channelIndex) {
        List<StreamElement> list = recordsByChannel.get(channelIndex);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * @return unmodifiable view of channelIndex → records
     */
    public Map<Integer, List<StreamElement>> getAllRecords() {
        Map<Integer, List<StreamElement>> view = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<StreamElement>> e : recordsByChannel.entrySet()) {
            view.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(view);
    }

    public boolean isEmpty() {
        if (recordsByChannel.isEmpty()) {
            return true;
        }
        for (List<StreamElement> list : recordsByChannel.values()) {
            if (list != null && !list.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int getTotalRecordCount() {
        int total = 0;
        for (List<StreamElement> list : recordsByChannel.values()) {
            if (list != null) {
                total += list.size();
            }
        }
        return total;
    }

    /**
     * Converts this state to a JSON-friendly map for persistence. Each channel
     * index maps to a list of envelope-style maps ({@link StreamMessageEnvelope}
     * field names). Records carry their value's Java class name so they can be
     * reconstructed on restore.
     *
     * @return a serializable map (channelIndex-as-string → list of envelope maps),
     *         or {@code null} when empty (so legacy snapshots stay compact)
     */
    public Map<String, Object> toSerializableForm() {
        if (isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<StreamElement>> e : recordsByChannel.entrySet()) {
            List<StreamElement> list = e.getValue();
            if (list == null || list.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> envelopeList = new ArrayList<>(list.size());
            for (StreamElement element : list) {
                String valueType = null;
                if (element.isRecord()) {
                    Object v = element.asRecord().getValue();
                    if (v != null) {
                        valueType = v.getClass().getName();
                    }
                } else if (element.isSideOutput()) {
                    // HG-01 (2026-08-14): side-output valueType derived from the inner
                    // record value class (edge-level valueType ignored, Phase 1 decision).
                    Object v = element.asSideOutput().getRecord().getValue();
                    if (v != null) {
                        valueType = v.getClass().getName();
                    }
                }
                StreamMessageEnvelope env = StreamElementCodec.encode(element, valueType, 0L);
                envelopeList.add(envelopeToMap(env));
            }
            out.put(Integer.toString(e.getKey()), envelopeList);
        }
        return out;
    }

    /**
     * Reconstructs a {@link ChannelState} from the persistent form produced by
     * {@link #toSerializableForm()}. Backward compatible: {@code null} or empty
     * input yields an empty state (aligned checkpoints without channel state).
     *
     * @param serializable the persistent map, or {@code null}
     * @return the reconstructed channel state (never null, possibly empty)
     */
    @SuppressWarnings("unchecked")
    public static ChannelState fromSerializableForm(Map<String, Object> serializable) {
        ChannelState state = new ChannelState();
        if (serializable == null || serializable.isEmpty()) {
            return state;
        }
        for (Map.Entry<String, Object> e : serializable.entrySet()) {
            int channelIndex;
            try {
                channelIndex = Integer.parseInt(e.getKey());
            } catch (NumberFormatException nfe) {
                // P1-09-01: skip a malformed channel index rather than failing the whole
                // restore, but never silently — every skip must be observable.
                LOG.warn("Skipping malformed channel index '{}' in channel state during restore (best-effort skip)",
                        e.getKey());
                continue;
            }
            Object value = e.getValue();
            if (!(value instanceof List)) {
                LOG.warn("Skipping channel '{}' in channel state during restore: value is not a List but {} (best-effort skip)",
                        channelIndex, value == null ? "null" : value.getClass().getName());
                continue;
            }
            List<StreamElement> elements = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (!(item instanceof Map)) {
                    LOG.warn("Skipping in-flight record of channel '{}' in channel state during restore: item is not a Map but {} (best-effort skip)",
                            channelIndex, item == null ? "null" : item.getClass().getName());
                    continue;
                }
                try {
                    // S-2 (2026-09-01 core audit): envelope reconstruction must stay
                    // INSIDE the per-element isolation guard — mapToEnvelope performs
                    // unchecked casts on snapshot fields (e.g. a non-String "type"
                    // would CCE), which must skip only this record, not abort the
                    // whole restore (P1-09-01 per-element isolation rule).
                    StreamMessageEnvelope env = mapToEnvelope((Map<String, Object>) item);
                    StreamElement element = StreamElementCodec.decode(env);
                    elements.add(element);
                } catch (Exception ex) {
                    // P1-09-01: a single undecodable in-flight record must not abort the
                    // whole restore — skip it (best-effort) and log the throwable so the
                    // exactly-once degradation (in-flight record silently lost) is
                    // observable. In-flight records are the only carrier of exactly-once
                    // semantics on the unaligned recovery path; skipping without a trace
                    // would silently downgrade to at-least-once (see error-handling.md
                    // per-element isolation rule: LOG.warn(..., e), throwable last).
                    LOG.warn("Skipping undecodable in-flight record of channel '{}' during restore (best-effort skip)",
                            channelIndex, ex);
                }
            }
            if (!elements.isEmpty()) {
                state.recordsByChannel.put(channelIndex, elements);
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelopeToMap(StreamMessageEnvelope env) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("epochId", env.getEpochId());
        m.put("type", env.getType());
        m.put("valueType", env.getValueType());
        m.put("payload", env.getPayload());
        m.put("timestamp", env.getTimestamp());
        m.put("hasTimestamp", env.isHasTimestamp());
        // HG-01 (2026-08-14): side-output tag id must survive the checkpoint snapshot
        // round-trip (the field-enumeration layer below ChannelState's codec call sites).
        m.put("outputTagId", env.getOutputTagId());
        return m;
    }

    private static StreamMessageEnvelope mapToEnvelope(Map<String, Object> m) {
        StreamMessageEnvelope env = new StreamMessageEnvelope();
        Object epoch = m.get("epochId");
        env.setEpochId(epoch instanceof Number ? ((Number) epoch).longValue() : 0L);
        env.setType((String) m.get("type"));
        env.setValueType((String) m.get("valueType"));
        env.setPayload(m.get("payload"));
        Object ts = m.get("timestamp");
        env.setTimestamp(ts instanceof Number ? ((Number) ts).longValue() : 0L);
        Object ht = m.get("hasTimestamp");
        env.setHasTimestamp(Boolean.TRUE.equals(ht));
        Object tagId = m.get("outputTagId");
        env.setOutputTagId(tagId instanceof String ? (String) tagId : null);
        return env;
    }
}
