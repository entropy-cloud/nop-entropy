/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.ByteBuffer;

public class ByteBufRecordBinaryOutput implements IRecordBinaryOutput {
    private final ByteBuf byteBuf;

    public ByteBufRecordBinaryOutput() {
        this(Unpooled.buffer()); // 使用Unpooled工具类创建一个ByteBuf实例
    }

    public ByteBufRecordBinaryOutput(ByteBuf byteBuf) {
        this.byteBuf = byteBuf.retain();
    }

    @Override
    public IRecordBinaryOutput writeBytes(byte[] bytes) {
        byteBuf.writeBytes(bytes); // 将整个字节数组写入ByteBuf
        return this;
    }

    @Override
    public IRecordBinaryOutput writeBytesPart(byte[] str, int start, int end) {
        byteBuf.writeBytes(str, start, end - start); // 将字节数组的一部分写入ByteBuf
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS1(byte c) {
        byteBuf.writeByte(c); // 写入一个字节
        return this;
    }

    @Override
    public IRecordBinaryOutput writeU1(int c) {
        byteBuf.writeByte(c); // 写入一个字节
        return this;
    }

    @Override
    public IRecordBinaryOutput writeByteBuffer(ByteBuffer buf) {
        byteBuf.writeBytes(buf); // 将ByteBuffer的内容写入ByteBuf
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS2Be(short c) {
        byteBuf.writeShort(c); // 写入一个short类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS2Le(short c) {
        byteBuf.writeShortLE(c); // 写入一个short类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeU2Be(int c) {
        byteBuf.writeShort(c); // 写入一个short类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeU2Le(int c) {
        byteBuf.writeShortLE(c); // 写入一个short类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS4Be(int c) {
        byteBuf.writeInt(c); // 写入一个int类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS4Le(int c) {
        byteBuf.writeIntLE(c); // 写入一个int类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeU4Be(long c) {
        byteBuf.writeInt((int) c); // 写入一个int类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeU4Le(long c) {
        byteBuf.writeIntLE((int) c); // 写入一个int类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS8Be(long c) {
        byteBuf.writeLong(c); // 写入一个long类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeS8Le(long c) {
        byteBuf.writeLongLE(c); // 写入一个long类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeF4Be(float c) {
        byteBuf.writeFloat(c); // 写入一个float类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeF4Le(float c) {
        byteBuf.writeFloatLE(c); // 写入一个float类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeF8Be(double c) {
        byteBuf.writeDouble(c); // 写入一个double类型的数据
        return this;
    }

    @Override
    public IRecordBinaryOutput writeF8Le(double c) {
        byteBuf.writeDoubleLE(c); // 写入一个double类型的数据
        return this;
    }

    // 其他方法，比如获取ByteBuf实例，可能需要实现
    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    // 当不再需要时，释放ByteBuf资源
    public void close() {
        byteBuf.release();
    }
}