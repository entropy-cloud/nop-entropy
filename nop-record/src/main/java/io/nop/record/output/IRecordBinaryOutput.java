/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.output;

import io.nop.commons.bytes.ByteString;

import java.io.Closeable;
import java.nio.ByteBuffer;

public interface IRecordBinaryOutput extends Closeable {
    IRecordBinaryOutput writeBytes(byte[] bytes);

    IRecordBinaryOutput writeBytesPart(byte[] str, int start, int end);

    IRecordBinaryOutput writeByteBuffer(ByteBuffer buf);

    default IRecordBinaryOutput writeByteString(ByteString bs) {
        return writeBytes(bs.toByteArray());
    }

    default IRecordBinaryOutput writeByte(byte c) {
        return writeS1(c);
    }

    default IRecordBinaryOutput writeShort(short c) {
        return writeS2Be(c);
    }

    default IRecordBinaryOutput writeInt(int c) {
        return writeS4Be(c);
    }

    default IRecordBinaryOutput writeLong(long c) {
        return writeS8Be(c);
    }

    default IRecordBinaryOutput writeFloat(float c) {
        return writeF4Be(c);
    }

    default IRecordBinaryOutput writeDouble(float c) {
        return writeF8Be(c);
    }

    IRecordBinaryOutput writeS1(byte c);

    IRecordBinaryOutput writeU1(int c);

    IRecordBinaryOutput writeS2Be(short c);

    IRecordBinaryOutput writeS2Le(short c);

    IRecordBinaryOutput writeU2Be(int c);

    IRecordBinaryOutput writeU2Le(int c);

    IRecordBinaryOutput writeS4Be(int c);

    IRecordBinaryOutput writeS4Le(int c);

    IRecordBinaryOutput writeU4Be(long c);

    IRecordBinaryOutput writeU4Le(long c);

    IRecordBinaryOutput writeS8Be(long c);

    IRecordBinaryOutput writeS8Le(long c);

    IRecordBinaryOutput writeF4Be(float c);

    IRecordBinaryOutput writeF4Le(float c);

    IRecordBinaryOutput writeF8Be(double c);

    IRecordBinaryOutput writeF8Le(double c);

}
