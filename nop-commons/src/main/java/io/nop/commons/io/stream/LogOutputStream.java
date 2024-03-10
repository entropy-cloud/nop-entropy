/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 将输出到OutputStream中的内容打印到日志文件中
 */
public class LogOutputStream extends OutputStream {

    private final Logger logger;
    private final ByteQueue out = new ByteQueue();
    private final boolean error;

    public LogOutputStream(Logger logger, boolean error) {
        this.logger = logger;
        this.error = error;
    }

    public void print(String str) throws IOException {
        write(str.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized int available() {
        return out.available();
    }

    @Override
    public synchronized void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void flush() {
        flushToLog();
    }

    private void flushToLog() {
        if (out.available() > 0) {
            String str = out.readToString(StandardCharsets.UTF_8);
            out.shrink();
            if (error) {
                logger.error("{}", str);
            } else {
                logger.info("{}", str);
            }
        }
    }

    @Override
    public void close() throws IOException {
        flushToLog();
        out.clear();
    }
}