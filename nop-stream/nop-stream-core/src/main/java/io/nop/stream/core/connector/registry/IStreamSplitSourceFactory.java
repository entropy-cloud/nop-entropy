/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.stream.core.source.Source;

/**
 * Factory constructing FLIP-27 split-based {@code Source} endpoint instances
 * (connector-design.md §8.1 D1). Split sources have no instance-level consistency accessor
 * ({@code Source} is not a {@code SourceFunction}); their descriptor delivery semantic is
 * pinned to the capability matrix and behavior tests instead of an instance method check.
 */
public interface IStreamSplitSourceFactory extends IStreamConnectorFactory {

    /**
     * Constructs a new split-based source endpoint instance from the given config. Missing
     * required params fail fast with {@code ERR_STREAM_CONNECTOR_PARAM_REQUIRED}.
     */
    Source<?, ?, ?> createSource(StreamConnectorConfig config);
}
