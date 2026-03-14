/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.stream.core.common.functions.sink;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * A sink function that prints elements to standard output.
 *
 * <p>The basic syntax for using a PrintSinkFunction is as follows:
 *
 * <pre>{@code
 * DataSet<String> input = ...;
 *
 * input.addSink(new PrintSinkFunction<>());
 * }</pre>
 *
 * <p>This sink will print each element to standard output, optionally with a prefix
 * to help identify which sink is producing the output.
 *
 * @param <T> The type of elements to be printed
 */
public class PrintSinkFunction<T> implements SinkFunction<T> {
    
    private final String prefix;
    
    /**
     * Creates a PrintSinkFunction with no prefix.
     */
    public PrintSinkFunction() {
        this(null);
    }
    
    /**
     * Creates a PrintSinkFunction with the specified prefix.
     *
     * @param prefix Optional prefix to identify the sink output
     */
    public PrintSinkFunction(String prefix) {
        this.prefix = prefix;
    }
    
    /**
     * Consumes and prints the given element to standard output.
     *
     * @param value The element to consume and print
     * @throws Exception This method may throw exceptions
     */
    public void consume(T value) throws Exception {
        if (prefix != null) {
            System.out.println(prefix + ": " + value);
        } else {
            System.out.println(value);
        }
    }
}