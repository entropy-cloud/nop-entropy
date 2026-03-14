/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions;

/**
 * A sink function that consumes elements from the stream.
 *
 * @param <T> The type of elements consumed by the sink
 */
@FunctionalInterface
public interface SinkFunction<T> extends StreamFunction {
    
    /**
     * Consumes the given element.
     *
     * @param value The element to consume
     * @throws Exception This method may throw exceptions
     */
    void consume(T value) throws Exception;
}