/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.eventtime.IndexedCombinedWatermarkStatus;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractStreamOperator<OUT> implements StreamOperator<OUT> {
    protected transient Output<StreamRecord<OUT>> output;

    protected transient ProcessingTimeService processingTimeService;

    /**
     * Combined watermark for two-input operators. Lazily initialized on first use.
     * Single-input operators that call processWatermark() directly never use this.
     */
    private transient IndexedCombinedWatermarkStatus combinedWatermark;

    protected IStateBackend stateBackend;
    protected IKeyedStateBackend<?> keyedStateBackend;

    protected transient TimerServiceManager timeServiceManager;

    protected transient OperatorSnapshotResult lastSnapshotResult;

    protected transient Consumer<OperatorSnapshotResult> snapshotCallback;

    // ------------------------------------------------------------------------
    //  life cycle
    // ------------------------------------------------------------------------

    @Override
    public void open() throws Exception {
        // subclasses may override
    }

    @Override
    public void finish() throws Exception {
        // subclasses may override
    }

    @Override
    public void close() throws Exception {
        // subclasses may override
    }

    public Output<StreamRecord<OUT>> getOutput() {
        return output;
    }

    public void setOutput(Output<StreamRecord<OUT>> output) {
        this.output = output;
    }

    public ProcessingTimeService getProcessingTimeService() {
        return processingTimeService;
    }

    public void setStateBackend(IStateBackend stateBackend) {
        this.stateBackend = stateBackend;
    }

    public IStateBackend getStateBackend() {
        return stateBackend;
    }

    @SuppressWarnings("unchecked")
    public <K> IKeyedStateBackend<K> getKeyedStateBackend() {
        return (IKeyedStateBackend<K>) keyedStateBackend;
    }

    public void setKeyedStateBackend(IKeyedStateBackend<?> keyedStateBackend) {
        this.keyedStateBackend = keyedStateBackend;
    }

    public TimerServiceManager getTimeServiceManager() {
        return timeServiceManager;
    }

    public void setTimeServiceManager(TimerServiceManager timeServiceManager) {
        this.timeServiceManager = timeServiceManager;
    }

    /**
     * Restores operator state from a previously taken snapshot.
     * Default implementation restores keyed state from the state backend.
     *
     * @param snapshotResult the snapshot to restore from
     * @throws Exception if restoration fails
     */
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        if (snapshotResult == null) {
            return;
        }

        Map<String, Object> keyedStates = snapshotResult.getKeyedStates();
        if (keyedStateBackend != null && keyedStates != null && !keyedStates.isEmpty()) {
            for (Map.Entry<String, Object> entry : keyedStates.entrySet()) {
                Object stateObj = entry.getValue();
                if (stateObj instanceof StateSnapshot) {
                    keyedStateBackend.restoreState((StateSnapshot) stateObj);
                } else if (stateObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    StateSnapshot snapshot = new StateSnapshot((Map<String, Object>) stateObj);
                    keyedStateBackend.restoreState(snapshot);
                }
                break;
            }
        }
    }

    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = new OperatorSnapshotResult();

        if (keyedStateBackend != null) {
            StateSnapshot snapshot = keyedStateBackend.snapshotState();
            if (snapshot != null && !snapshot.isEmpty()) {
                result.putKeyedState("keyed-state", snapshot);
            }
        }

        return result;
    }

    public OperatorSnapshotResult getLastSnapshotResult() {
        return lastSnapshotResult;
    }

    public void setSnapshotCallback(Consumer<OperatorSnapshotResult> callback) {
        this.snapshotCallback = callback;
    }

    @Override
    public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {

    }

    @Override
    public void setKeyContextElement1(StreamRecord<?> record) throws Exception {

    }

    @Override
    public void setKeyContextElement2(StreamRecord<?> record) throws Exception {

    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
    }

    @Override
    public void notifyCheckpointAborted(long checkpointId) throws Exception {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setCurrentKey(Object key) {
        if (keyedStateBackend != null) {
            ((IKeyedStateBackend<Object>) keyedStateBackend).setCurrentKey(key);
        }
    }

    @Override
    public Object getCurrentKey() {
        if (keyedStateBackend != null) {
            return keyedStateBackend.getCurrentKey();
        }
        return null;
    }


    // ------- One input stream
    public void processLatencyMarker(LatencyMarker latencyMarker) throws Exception {
        reportOrForwardLatencyMarker(latencyMarker);
    }

    // ------- Two input stream
    public void processLatencyMarker1(LatencyMarker latencyMarker) throws Exception {
        reportOrForwardLatencyMarker(latencyMarker);
    }

    public void processLatencyMarker2(LatencyMarker latencyMarker) throws Exception {
        reportOrForwardLatencyMarker(latencyMarker);
    }

    protected void reportOrForwardLatencyMarker(LatencyMarker marker) {
        // all operators are tracking latencies
        //this.latencyStats.reportLatency(marker);

        // everything except sinks forwards latency markers
        this.output.emitLatencyMarker(marker);
    }

    public void processWatermark(Watermark mark) throws Exception {
        if (timeServiceManager != null) {
            timeServiceManager.advanceWatermark(mark);
        }
        output.emitWatermark(mark);
    }

    public void processBarrier(CheckpointBarrier barrier) throws Exception {
        OperatorSnapshotResult snapshotResult = null;
        if (barrier.snapshot()) {
            StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
            snapshotResult = snapshotState(context);
            this.lastSnapshotResult = snapshotResult;
        }
        if (snapshotCallback != null && snapshotResult != null) {
            snapshotCallback.accept(snapshotResult);
        }
        if (output != null) {
            output.emitBarrier(barrier);
        }
    }

    private void processWatermark(Watermark mark, int index) throws Exception {
        if (combinedWatermark == null) {
            combinedWatermark = IndexedCombinedWatermarkStatus.forInputsCount(2);
        }
        if (combinedWatermark.updateWatermark(index, mark.getTimestamp())) {
            processWatermark(new Watermark(combinedWatermark.getCombinedWatermark()));
        }
    }

    public void processWatermark1(Watermark mark) throws Exception {
        processWatermark(mark, 0);
    }

    public void processWatermark2(Watermark mark) throws Exception {
        processWatermark(mark, 1);
    }


    public void processWatermarkStatus(WatermarkStatus watermarkStatus) throws Exception {
        output.emitWatermarkStatus(watermarkStatus);
    }

    private void processWatermarkStatus(WatermarkStatus watermarkStatus, int index)
            throws Exception {
        if (combinedWatermark == null) {
            combinedWatermark = IndexedCombinedWatermarkStatus.forInputsCount(2);
        }
        boolean wasIdle = combinedWatermark.isIdle();
        if (combinedWatermark.updateStatus(index, watermarkStatus.isIdle())) {
            processWatermark(new Watermark(combinedWatermark.getCombinedWatermark()));
        }
        if (wasIdle != combinedWatermark.isIdle()) {
            output.emitWatermarkStatus(watermarkStatus);
        }
    }

    public final void processWatermarkStatus1(WatermarkStatus watermarkStatus) throws Exception {
        processWatermarkStatus(watermarkStatus, 0);
    }

    public final void processWatermarkStatus2(WatermarkStatus watermarkStatus) throws Exception {
        processWatermarkStatus(watermarkStatus, 1);
    }
}