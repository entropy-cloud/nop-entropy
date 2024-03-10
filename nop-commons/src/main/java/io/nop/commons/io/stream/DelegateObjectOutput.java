/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.ObjectOutput;

public class DelegateObjectOutput implements ObjectOutput {
    private final ObjectOutput output;

    public DelegateObjectOutput(ObjectOutput output) {
        this.output = output;
    }

    public ObjectOutput getObjectOutput() {
        return output;
    }

    public void writeObject(Object obj) throws IOException {
        output.writeObject(obj);
    }

    public void write(int b) throws IOException {
        output.write(b);
    }

    public void write(byte[] b) throws IOException {
        output.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        output.write(b, off, len);
    }

    public void flush() throws IOException {
        output.flush();
    }

    public void close() throws IOException {
        output.close();
    }

    public void writeBoolean(boolean v) throws IOException {
        output.writeBoolean(v);
    }

    public void writeByte(int v) throws IOException {
        output.writeByte(v);
    }

    public void writeShort(int v) throws IOException {
        output.writeShort(v);
    }

    public void writeChar(int v) throws IOException {
        output.writeChar(v);
    }

    public void writeInt(int v) throws IOException {
        output.writeInt(v);
    }

    public void writeLong(long v) throws IOException {
        output.writeLong(v);
    }

    public void writeFloat(float v) throws IOException {
        output.writeFloat(v);
    }

    public void writeDouble(double v) throws IOException {
        output.writeDouble(v);
    }

    public void writeBytes(String s) throws IOException {
        output.writeBytes(s);
    }

    public void writeChars(String s) throws IOException {
        output.writeChars(s);
    }

    public void writeUTF(String s) throws IOException {
        output.writeUTF(s);
    }
}