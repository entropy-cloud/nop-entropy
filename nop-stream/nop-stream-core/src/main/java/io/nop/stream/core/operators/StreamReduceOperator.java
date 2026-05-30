/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

public class StreamReduceOperator<T>
        extends AbstractUdfStreamOperator<T, ReduceFunction<T>>
        implements OneInputStreamOperator<T, T> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(StreamReduceOperator.class);
    private static final String REDUCE_STATE_KEY = "reduce-state";

    private transient Object currentKey;
    private transient Map<Object, T> values;

    public StreamReduceOperator(ReduceFunction<T> reducer) {
        super(reducer);
    }

    @Override
    public void setCurrentKey(Object key) {
        this.currentKey = key;
        super.setCurrentKey(key);
    }

    @Override
    public Object getCurrentKey() {
        if (currentKey != null) {
            return currentKey;
        }
        return super.getCurrentKey();
    }

    @Override
    public void open() throws Exception {
        super.open();
        values = new HashMap<>();
    }

    @Override
    public void processElement(StreamRecord<T> element) throws Exception {
        Object key = getCurrentKey();
        if (key == null) {
            throw new StreamException(ERR_STREAM_OPERATOR_ERROR)
                    .param(ARG_OPERATOR_NAME, "StreamReduceOperator")
                    .param(ARG_DETAIL, "requires key context. Ensure the stream is keyed via keyBy() before applying reduce.");
        }
        T value = element.getValue();
        T currentValue = values.get(key);

        if (currentValue == null) {
            values.put(key, value);
            output.collect(element);
        } else {
            T reduced = userFunction.reduce(currentValue, value);
            values.put(key, reduced);
            output.collect(new StreamRecord<>(reduced, element.getTimestamp()));
        }
    }

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<Object, T> e : values.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", e.getKey());
            entry.put("value", e.getValue());
            if (e.getValue() != null) {
                entry.put("valueTypeName", e.getValue().getClass().getName());
            }
            entries.add(entry);
        }
        result.putOperatorState(REDUCE_STATE_KEY, entries);

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);

        Object stateObj = snapshotResult.getOperatorState(REDUCE_STATE_KEY);
        if (stateObj instanceof List) {
            values = new HashMap<>();
            List<?> entries = (List<?>) stateObj;
            for (Object item : entries) {
                if (item instanceof Map) {
                    Map<String, Object> entry = (Map<String, Object>) item;
                    Object key = entry.get("key");
                    Object value = entry.get("value");
                    // Defensive: skip entries where key or value is null after JSON round-trip
                    if (key == null || value == null) {
                        LOG.warn("Skipping restore entry with null key or value: key={}, value={}", key, value);
                        continue;
                    }
                    String expectedType = (String) entry.get("valueTypeName");
                    if (expectedType != null && !expectedType.equals(value.getClass().getName())) {
                        throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                                .param(ARG_EXPECTED_TYPE, expectedType)
                                .param(ARG_ACTUAL_TYPE, value.getClass().getName());
                    }
                    values.put(key, (T) value);
                }
            }
        }
    }
}
