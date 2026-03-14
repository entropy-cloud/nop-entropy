/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.sink;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * A sink that prints elements to the standard output stream.
 * This is mainly used for debugging purposes.
 *
 * @param <T> The type of elements to be printed
 */
public class PrintSink<T> implements SinkFunction<T> {
    
    private final String prefix;
    
    /**
     * Creates a new PrintSink with no prefix.
     */
    public PrintSink() {
        this("");
    }
    
    /**
     * Creates a new PrintSink with the specified prefix.
     *
     * @param prefix The prefix to print before each element
     */
    public PrintSink(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }
    
    @Override
    public void consume(T value) throws Exception {
        System.out.println(prefix + value);
    }
}