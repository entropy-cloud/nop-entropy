/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.Writer;

/**
 * @author canonical_entropy@163.com
 */
public abstract class DelegateWriter extends Writer {
    protected abstract Writer getInternalWriter(boolean allowNull) throws IOException;

    @Override
    public void write(int c) throws IOException {
        getInternalWriter(false).write(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        getInternalWriter(false).write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        getInternalWriter(false).write(cbuf, off, len);
    }

    @Override
    public void write(String str) throws IOException {
        getInternalWriter(false).write(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        getInternalWriter(false).write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        getInternalWriter(false).append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        getInternalWriter(false).append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        this.getInternalWriter(false).append(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        Writer out = getInternalWriter(true);
        if (out != null)
            out.flush();
    }

    @Override
    public void close() throws IOException {
        Writer out = getInternalWriter(true);
        if (out != null)
            out.close();
    }
}