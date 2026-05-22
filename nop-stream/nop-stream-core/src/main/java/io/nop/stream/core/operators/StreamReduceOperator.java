/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.streamrecord.StreamRecord;

import java.util.HashMap;
import java.util.Map;

public class StreamReduceOperator<T>
        extends AbstractUdfStreamOperator<T, ReduceFunction<T>>
        implements OneInputStreamOperator<T, T> {

    private static final long serialVersionUID = 1L;
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
            throw new IllegalStateException(
                    "StreamReduceOperator requires key context. "
                  + "Ensure the stream is keyed via keyBy() before applying reduce.");
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
    @SuppressWarnings("unchecked")
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        result.putOperatorStateJava(REDUCE_STATE_KEY, (java.io.Serializable) values);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        Map<Object, T> restored = snapshotResult.getOperatorStateJava(REDUCE_STATE_KEY);
        if (restored != null) {
            values = restored;
        }
    }
}
