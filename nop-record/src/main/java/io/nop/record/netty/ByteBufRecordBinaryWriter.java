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
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.ByteBuffer;

public class ByteBufRecordBinaryWriter implements IRecordBinaryWriter {
    private final ByteBuf byteBuf;

    public ByteBufRecordBinaryWriter() {
        this(Unpooled.buffer()); // 使用Unpooled工具类创建一个ByteBuf实例
    }

    public ByteBufRecordBinaryWriter(ByteBuf byteBuf) {
        this.byteBuf = byteBuf.retain();
    }

    @Override
    public void writeBytes(byte[] bytes) {
        byteBuf.writeBytes(bytes); // 将整个字节数组写入ByteBuf
    }

    @Override
    public void writeBytesPart(byte[] str, int start, int end) {
        byteBuf.writeBytes(str, start, end - start); // 将字节数组的一部分写入ByteBuf
    }

    @Override
    public void writeS1(byte c) {
        byteBuf.writeByte(c); // 写入一个字节
    }

    @Override
    public void writeU1(int c) {
        byteBuf.writeByte(c); // 写入一个字节
    }

    @Override
    public void writeByteBuffer(ByteBuffer buf) {
        byteBuf.writeBytes(buf); // 将ByteBuffer的内容写入ByteBuf
    }

    @Override
    public void writeS2be(short c) {
        byteBuf.writeShort(c); // 写入一个short类型的数据
    }

    @Override
    public void writeS2le(short c) {
        byteBuf.writeShortLE(c); // 写入一个short类型的数据
    }

    @Override
    public void writeU2be(int c) {
        byteBuf.writeShort(c); // 写入一个short类型的数据
    }

    @Override
    public void writeU2le(int c) {
        byteBuf.writeShortLE(c); // 写入一个short类型的数据
    }

    @Override
    public void writeS4be(int c) {
        byteBuf.writeInt(c); // 写入一个int类型的数据
    }

    @Override
    public void writeS4le(int c) {
        byteBuf.writeIntLE(c); // 写入一个int类型的数据
    }

    @Override
    public void writeU4be(long c) {
        byteBuf.writeInt((int) c); // 写入一个int类型的数据
    }

    @Override
    public void writeU4le(long c) {
        byteBuf.writeIntLE((int) c); // 写入一个int类型的数据
    }

    @Override
    public void writeS8be(long c) {
        byteBuf.writeLong(c); // 写入一个long类型的数据
    }

    @Override
    public void writeS8le(long c) {
        byteBuf.writeLongLE(c); // 写入一个long类型的数据
    }

    @Override
    public void writeU8be(long c) {
        byteBuf.writeLong(c); // 写入一个long类型的数据
    }

    @Override
    public void writeU8le(long c) {
        byteBuf.writeLongLE(c); // 写入一个long类型的数据
    }

    @Override
    public void writeF4be(float c) {
        byteBuf.writeFloat(c); // 写入一个float类型的数据
    }

    @Override
    public void writeF4le(float c) {
        byteBuf.writeFloatLE(c); // 写入一个float类型的数据
    }

    @Override
    public void writeF8be(double c) {
        byteBuf.writeDouble(c); // 写入一个double类型的数据
    }

    @Override
    public void writeF8le(double c) {
        byteBuf.writeDoubleLE(c); // 写入一个double类型的数据
    }

    // 其他方法，比如获取ByteBuf实例，可能需要实现
    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    // 当不再需要时，释放ByteBuf资源
    public void close() {
        byteBuf.release();
    }

    @Override
    public void flush() {

    }
}