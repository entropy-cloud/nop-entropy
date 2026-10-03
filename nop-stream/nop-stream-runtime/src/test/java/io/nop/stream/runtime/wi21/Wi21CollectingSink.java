/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 */
package io.nop.stream.runtime.wi21;

import io.nop.stream.core.common.functions.SinkFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** WI21 build-stability fixture sink. */
public class Wi21CollectingSink<T> implements SinkFunction<T> {

    private static final long serialVersionUID = 1L;

    private final transient List<T> collected = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void consume(T value) {
        collected.add(value);
    }

    public List<T> getCollected() {
        return collected;
    }
}
