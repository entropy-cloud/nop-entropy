/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author canonical_entropy@163.com
 */
public abstract class DelegateOutputStream extends OutputStream {

    protected abstract OutputStream getInternalStream(boolean allowNull) throws IOException;

    @Override
    public void write(int b) throws IOException {
        this.getInternalStream(false).write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        getInternalStream(false).write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        getInternalStream(false).write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        OutputStream os = this.getInternalStream(true);
        if (os != null)
            os.flush();
    }

    @Override
    public void close() throws IOException {
        OutputStream os = this.getInternalStream(true);
        if (os != null)
            os.close();
    }
}