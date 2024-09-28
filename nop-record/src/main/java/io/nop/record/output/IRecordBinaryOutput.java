/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.output;

import io.nop.commons.bytes.ByteString;

import java.nio.ByteBuffer;

public interface IRecordBinaryOutput extends IRecordOutputBase {
    void writeBytes(byte[] bytes);

    void writeBytesPart(byte[] str, int start, int end);

    void writeByteBuffer(ByteBuffer buf);

    default void writeByteString(ByteString bs) {
        writeBytes(bs.toByteArray());
    }

    default void writeByte(byte c) {
        writeS1(c);
    }

    default void writeShort(short c) {
        writeS2be(c);
    }

    default void writeInt(int c) {
        writeS4be(c);
    }

    default void writeLong(long c) {
        writeS8be(c);
    }

    default void writeFloat(float c) {
        writeF4le(c);
    }

    default void writeDouble(float c) {
        writeF8be(c);
    }

    void writeS1(byte c);

    void writeU1(int c);

    void writeS2be(short c);

    void writeS2le(short c);

    void writeU2be(int c);

    void writeU2le(int c);

    void writeS4be(int c);

    void writeS4le(int c);

    void writeU4be(long c);

    void writeU4le(long c);

    void writeS8be(long c);

    void writeS8le(long c);

    void writeU8be(long c);

    void writeU8le(long c);

    void writeF4le(float c);

    void writeF4be(float c);

    void writeF8be(double c);

    void writeF8le(double c);

}
