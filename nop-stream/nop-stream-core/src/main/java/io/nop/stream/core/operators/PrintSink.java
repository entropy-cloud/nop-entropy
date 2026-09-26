/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.io.PrintStream;

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
     * Optional target stream override (tests / output redirect). When null, the
     * sink writes to the live {@code System.out} at call time — identical to the
     * pre-injection behavior.
     */
    private transient PrintStream out;
    
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
    
    /**
     * Creates a new PrintSink with the specified prefix writing to the given
     * stream instead of standard output.
     *
     * @param prefix The prefix to print before each element
     * @param out    The target stream for the printed elements
     */
    public PrintSink(String prefix, PrintStream out) {
        this(prefix);
        this.out = out;
    }

    /**
     * Redirects subsequent output to the given stream instead of standard output.
     *
     * @param out The target stream for the printed elements
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }
    
    @Override
    public void consume(T value) throws Exception {
        resolveOut().println(prefix + value);
    }

    private PrintStream resolveOut() {
        return out != null ? out : System.out;
    }
}
