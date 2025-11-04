/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.ObjectInput;

public class DelegateObjectInput implements ObjectInput {
    private final ObjectInput objectInput;

    public DelegateObjectInput(ObjectInput objectInput) {
        this.objectInput = objectInput;
    }

    public ObjectInput getObjectInput() {
        return objectInput;
    }

    public Object readObject() throws ClassNotFoundException, IOException {
        return objectInput.readObject();
    }

    public int read() throws IOException {
        return objectInput.read();
    }

    public int read(byte[] b) throws IOException {
        return objectInput.read(b);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return objectInput.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        return objectInput.skip(n);
    }

    public int available() throws IOException {
        return objectInput.available();
    }

    public void close() throws IOException {
        objectInput.close();
    }

    public void readFully(byte[] b) throws IOException {
        objectInput.readFully(b);
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        objectInput.readFully(b, off, len);
    }

    public int skipBytes(int n) throws IOException {
        return objectInput.skipBytes(n);
    }

    public boolean readBoolean() throws IOException {
        return objectInput.readBoolean();
    }

    public byte readByte() throws IOException {
        return objectInput.readByte();
    }

    public int readUnsignedByte() throws IOException {
        return objectInput.readUnsignedByte();
    }

    public short readShort() throws IOException {
        return objectInput.readShort();
    }

    public int readUnsignedShort() throws IOException {
        return objectInput.readUnsignedShort();
    }

    public char readChar() throws IOException {
        return objectInput.readChar();
    }

    public int readInt() throws IOException {
        return objectInput.readInt();
    }

    public long readLong() throws IOException {
        return objectInput.readLong();
    }

    public float readFloat() throws IOException {
        return objectInput.readFloat();
    }

    public double readDouble() throws IOException {
        return objectInput.readDouble();
    }

    public String readLine() throws IOException {
        return objectInput.readLine();
    }

    public String readUTF() throws IOException {
        return objectInput.readUTF();
    }

}
