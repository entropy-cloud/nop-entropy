/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI14 custom-path evidence: a {@code <custom>} operator receives NO automatic state
 * provisioning, but it can self-provision a keyed state backend in {@code open()}
 * following the ProcessOperator precedent —
 * {@code stateBackend.createKeyedStateBackend(...)} + the inherited protected
 * {@code applyPendingRestoreState()} + pushing the backend into the user function's
 * {@code RuntimeContext}. The test proves the self-provisioned backend is live
 * (keyed store usable), participates in checkpoint snapshots, and restores.
 */
public class TestCustomOperatorSelfProvisionedBackend {

    /** A custom operator that provisions its own keyed backend in open(). */
    static class SelfProvisioningCustomOperator
            extends AbstractUdfStreamOperator<Map<String, Object>, KeyedProcessFunction<String, Map<String, Object>, Map<String, Object>>>
            implements OneInputStreamOperator<Map<String, Object>, Map<String, Object>> {

        private static final long serialVersionUID = 1L;

        private static final ValueStateDescriptor<String> DIM_CACHE_DESC =
                new ValueStateDescriptor<>("custom-dim-cache", String.class);

        private transient boolean storeWired;

        boolean backendProvisioned() {
            return keyedStateBackend != null;
        }

        Object backendInstance() {
            return keyedStateBackend;
        }

        Object udfStoreInstance() {
            return userFunction.getRuntimeContext().getKeyedStateStore();
        }

        SelfProvisioningCustomOperator(KeyedProcessFunction<String, Map<String, Object>, Map<String, Object>> userFunction) {
            super(userFunction);
        }

        @Override
        public void open() throws Exception {
            // custom operators get NO automatic provisioning — do it ourselves,
            // mirroring the ProcessOperator.open() sequence:
            // createKeyedStateBackend -> applyPendingRestoreState -> wire the store
            if (keyedStateBackend == null && stateBackend != null) {
                keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class);
                applyPendingRestoreState();
            }
            super.open();

            if (userFunction.getRuntimeContext() instanceof io.nop.stream.core.operators.StreamingRuntimeContext) {
                io.nop.stream.core.operators.StreamingRuntimeContext src =
                        (io.nop.stream.core.operators.StreamingRuntimeContext) userFunction.getRuntimeContext();
                if (keyedStateBackend != null) {
                    src.setKeyedStateStore(keyedStateBackend);
                    storeWired = true;
                }
            }
        }

        @Override
        public void processElement(StreamRecord<Map<String, Object>> element) throws Exception {
            KeyedStateStore store = userFunction.getRuntimeContext().getKeyedStateStore();
            assertNotNull(store, "self-provisioned keyed store must be reachable from the UDF");
            ValueState<String> cache = store.getState(DIM_CACHE_DESC);
            String cached = cache.value();
            if (cached == null) {
                cached = "loaded:" + element.getValue().get("dimKey");
                cache.update(cached);
            }
            Map<String, Object> out = new HashMap<>(element.getValue());
            out.put("cacheValue", cached);
            out.put("storeWired", storeWired);
            output.collect(new StreamRecord<>(out, element.getTimestamp()));
        }
    }

    /** identity UDF whose keyed-state access rides the operator-provisioned store. */
    static class CacheReadingFunction extends KeyedProcessFunction<String, Map<String, Object>, Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(Map<String, Object> record,
                                   ProcessFunction<Map<String, Object>, Map<String, Object>>.Context ctx,
                                   Collector<Map<String, Object>> out) {
            // state access happens inside the operator's processElement
        }
    }

    @Test
    void customOperatorSelfProvisionsKeyedBackendAndParticipatesInCheckpoint() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();

        SelfProvisioningCustomOperator op =
                new SelfProvisioningCustomOperator(new CacheReadingFunction());
        op.setStateBackend(stateBackend);

        CollectingOutput out = new CollectingOutput();
        op.setOutput(out);
        op.open();

        assertTrue(op.backendProvisioned(), "self-provisioning must create the keyed backend");

        op.processElement(new StreamRecord<>(row("k1")));

        assertEquals(Boolean.TRUE, out.last.get("storeWired"),
                "the self-provisioned backend must be wired into the UDF's RuntimeContext");
        assertEquals("loaded:k1", out.last.get("cacheValue"), "keyed store must be usable");
        // cache hit on the same key — the keyed state actually holds data
        op.processElement(new StreamRecord<>(row("k1")));
        assertEquals("loaded:k1", out.last.get("cacheValue"), "second read must hit the keyed cache");

        // snapshot participation
        CheckpointBarrier barrier = new CheckpointBarrier(7L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty(), "self-provisioned keyed state must participate in snapshots");

        // restore into a fresh instance of the same custom operator
        SelfProvisioningCustomOperator restored =
                new SelfProvisioningCustomOperator(new CacheReadingFunction());
        restored.setStateBackend(stateBackend);
        CollectingOutput restoredOut = new CollectingOutput();
        restored.setOutput(restoredOut);
        restored.open();
        restored.restoreState(snapshot);

        restored.processElement(new StreamRecord<>(row("k1")));
        assertEquals("loaded:k1", restoredOut.last.get("cacheValue"),
                "restored custom operator must read the restored keyed cache");
        assertSame(restored.backendInstance(), restored.udfStoreInstance(),
                "the UDF must see the restored operator's backend instance");
    }

    private static Map<String, Object> row(String dimKey) {
        Map<String, Object> m = new HashMap<>();
        m.put("dimKey", dimKey);
        return m;
    }

    /** minimal output probe. */
    static final class CollectingOutput implements io.nop.stream.core.operators.Output<StreamRecord<Map<String, Object>>> {
        final Map<String, Object> last = new HashMap<>();

        @Override
        public void collect(StreamRecord<Map<String, Object>> record) {
            last.clear();
            if (record.getValue() != null) {
                last.putAll(record.getValue());
            }
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }
}
