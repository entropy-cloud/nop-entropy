/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.IndexedCombinedWatermarkStatus;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

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

    @Override
    public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {

    }

    @Override
    public void setKeyContextElement1(StreamRecord<?> record) throws Exception {

    }

    @Override
    public void setKeyContextElement2(StreamRecord<?> record) throws Exception {

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
//        if (timeServiceManager != null) {
//            timeServiceManager.advanceWatermark(mark);
//        }
        output.emitWatermark(mark);
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