/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * Factory constructing {@code SinkFunction} endpoint instances (connector-design.md §8.1 D1).
 * Introduces no new execution contract: the created object is exactly the existing
 * {@code SinkFunction} the DataStream API consumes.
 */
public interface IStreamSinkFactory extends IStreamConnectorFactory {

    /**
     * Constructs a new sink endpoint instance from the given config. Missing required params
     * fail fast with {@code ERR_STREAM_CONNECTOR_PARAM_REQUIRED}. Unknown-param rejection is
     * explicitly NOT performed here (field-level conf validation is item 20's boundary).
     */
    SinkFunction<?> createSink(StreamConnectorConfig config);
}
