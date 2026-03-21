/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.test;

import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test output implementation that collects all emitted elements.
 * 
 * <p>Thread-safe for concurrent test scenarios.
 *
 * @param <T> The type of output elements
 */
public class TestOutput<T> implements Output<StreamRecord<T>> {

    private final List<StreamRecord<T>> records = new CopyOnWriteArrayList<>();
    private final List<Watermark> watermarks = new CopyOnWriteArrayList<>();
    private final List<LatencyMarker> latencyMarkers = new CopyOnWriteArrayList<>();

    @Override
    public void collect(StreamRecord<T> record) {
        records.add(record);
    }

    @Override
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        // Side output - for now, just add to main records with tag info
        // Can be extended to track side outputs separately
        throw new UnsupportedOperationException("Side output not yet supported in test harness");
    }

    @Override
    public void emitWatermark(Watermark mark) {
        watermarks.add(mark);
    }

    @Override
    public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        // Track watermark status if needed for tests
    }

    @Override
    public void emitLatencyMarker(LatencyMarker latencyMarker) {
        latencyMarkers.add(latencyMarker);
    }

    @Override
    public void close() {
        // Nothing to close
    }

    /**
     * Gets all collected elements (without timestamps).
     */
    public List<T> getElements() {
        List<T> elements = new ArrayList<>();
        for (StreamRecord<T> record : records) {
            elements.add(record.getValue());
        }
 return Collections.unmodifiableList(elements);
    }

    /**
     * Gets all collected records (with timestamps).
     */
    public List<StreamRecord<T>> getRecords() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /**
     * Gets all emitted watermarks.
     */
    public List<Watermark> getWatermarks() {
        return Collections.unmodifiableList(new ArrayList<>(watermarks));
    }

    /**
     * Gets the last emitted watermark.
     */
    public Watermark getLastWatermark() {
        if (watermarks.isEmpty()) {
            return null;
        }
        return watermarks.get(watermarks.size() - 1);
    }

    /**
     * Gets the number of collected elements.
     */
    public int size() {
        return records.size();
    }

    /**
     * Checks if any elements were collected.
     */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /**
     * Clears all collected data.
     */
    public void clear() {
        records.clear();
        watermarks.clear();
        latencyMarkers.clear();
    }

    /**
     * Gets element at specific index.
     */
    public T get(int index) {
        return records.get(index).getValue();
    }

    /**
     * Gets timestamp of element at specific index.
     */
    public long getTimestamp(int index) {
        return records.get(index).getTimestamp();
    }
}
