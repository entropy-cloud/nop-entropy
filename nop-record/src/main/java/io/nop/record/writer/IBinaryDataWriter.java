/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.writer;

import io.nop.commons.bytes.ByteString;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IBinaryDataWriter extends IDataWriterBase {
    void writeBytes(byte[] bytes) throws IOException;

    void writeBytesPart(byte[] str, int start, int end) throws IOException;

    void writeByteBuffer(ByteBuffer buf) throws IOException;

    default void writeByteString(ByteString bs) throws IOException {
        writeBytes(bs.toByteArray());
    }

    default void writeByte(byte c) throws IOException {
        writeS1(c);
    }

    default void writeShort(short c) throws IOException {
        writeS2be(c);
    }

    default void writeInt(int c) throws IOException {
        writeS4be(c);
    }

    default void writeLong(long c) throws IOException {
        writeS8be(c);
    }

    default void writeFloat(float c) throws IOException {
        writeF4le(c);
    }

    default void writeDouble(float c) throws IOException {
        writeF8be(c);
    }

    void writeS1(byte c) throws IOException;

    void writeU1(int c) throws IOException;

    void writeS2be(short c) throws IOException;

    void writeS2le(short c) throws IOException;

    void writeU2be(int c) throws IOException;

    void writeU2le(int c) throws IOException;

    void writeS4be(int c) throws IOException;

    void writeS4le(int c) throws IOException;

    void writeU4be(long c) throws IOException;

    void writeU4le(long c) throws IOException;

    void writeS8be(long c) throws IOException;

    void writeS8le(long c) throws IOException;

    void writeU8be(long c) throws IOException;

    void writeU8le(long c) throws IOException;

    void writeF4le(float c) throws IOException;

    void writeF4be(float c) throws IOException;

    void writeF8be(double c) throws IOException;

    void writeF8le(double c) throws IOException;

}
