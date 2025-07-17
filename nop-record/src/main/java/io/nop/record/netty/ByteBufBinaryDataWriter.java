/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 * <p>
 * 警告：
 * 1. 该类非线程安全，其实例只应被单线程使用。
 * 2. 若byteBuf由外部传入，则资源回收（release）责任清晰，防止多次release。
 */

package io.nop.record.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.ByteBuffer;

public class ByteBufBinaryDataWriter implements IBinaryDataWriter {
    private final ByteBuf byteBuf;
    private boolean closed = false;

    public ByteBufBinaryDataWriter() {
        this(Unpooled.buffer());
    }

    public ByteBufBinaryDataWriter(ByteBuf byteBuf) {
        if (byteBuf == null) {
            throw new IllegalArgumentException("ByteBuf must not be null");
        }
        this.byteBuf = byteBuf;
    }

    @Override
    public void writeBytes(byte[] bytes) {
        if (bytes == null)
            throw new NullPointerException("bytes must not be null");
        byteBuf.writeBytes(bytes);
    }

    @Override
    public void writeBytesPart(byte[] str, int start, int end) {
        if (str == null)
            throw new NullPointerException("str must not be null");
        if (start < 0 || end > str.length || start > end)
            throw new IndexOutOfBoundsException("start/end out of bounds");
        byteBuf.writeBytes(str, start, end - start);
    }

    @Override
    public long getWrittenCount() {
        // writerIndex() 表示已写入的字节总数
        return byteBuf.writerIndex();
    }

    @Override
    public void writeS1(byte c) {
        byteBuf.writeByte(c);
    }

    @Override
    public void writeU1(int c) {
        if (c < 0 || c > 0xFF)
            throw new IllegalArgumentException("Unsigned byte (U1) out of range: " + c);
        byteBuf.writeByte(c);
    }

    @Override
    public void writeByteBuffer(ByteBuffer buf) {
        if (buf == null)
            throw new NullPointerException("ByteBuffer must not be null");
        byteBuf.writeBytes(buf);
    }

    @Override
    public void writeS2be(short c) {
        byteBuf.writeShort(c);
    }

    @Override
    public void writeS2le(short c) {
        byteBuf.writeShortLE(c);
    }

    @Override
    public void writeU2be(int c) {
        if (c < 0 || c > 0xFFFF)
            throw new IllegalArgumentException("Unsigned short (U2) out of range: " + c);
        byteBuf.writeShort(c);
    }

    @Override
    public void writeU2le(int c) {
        if (c < 0 || c > 0xFFFF)
            throw new IllegalArgumentException("Unsigned short (U2) out of range: " + c);
        byteBuf.writeShortLE(c);
    }

    @Override
    public void writeS4be(int c) {
        byteBuf.writeInt(c);
    }

    @Override
    public void writeS4le(int c) {
        byteBuf.writeIntLE(c);
    }

    @Override
    public void writeU4be(long c) {
        if (c < 0 || c > 0xFFFFFFFFL)
            throw new IllegalArgumentException("Unsigned int (U4) out of range: " + c);
        byteBuf.writeInt((int) c);
    }

    @Override
    public void writeU4le(long c) {
        if (c < 0 || c > 0xFFFFFFFFL)
            throw new IllegalArgumentException("Unsigned int (U4) out of range: " + c);
        byteBuf.writeIntLE((int) c);
    }

    @Override
    public void writeS8be(long c) {
        byteBuf.writeLong(c);
    }

    @Override
    public void writeS8le(long c) {
        byteBuf.writeLongLE(c);
    }

    @Override
    public void writeU8be(long c) {
        byteBuf.writeLong(c); // Java long本身就是64位
    }

    @Override
    public void writeU8le(long c) {
        byteBuf.writeLongLE(c);
    }

    @Override
    public void writeF4be(float c) {
        byteBuf.writeFloat(c);
    }

    @Override
    public void writeF4le(float c) {
        byteBuf.writeFloatLE(c);
    }

    @Override
    public void writeF8be(double c) {
        byteBuf.writeDouble(c);
    }

    @Override
    public void writeF8le(double c) {
        byteBuf.writeDoubleLE(c);
    }

    /**
     * 获取底层 ByteBuf。
     */
    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    /**
     * 释放 ByteBuf。注意不能多次调用。
     */
    public void close() {
        if (!closed) {
            closed = true;
            byteBuf.release();
        }
    }

    @Override
    public void flush() {
        // 对于内存缓冲对象无实际意义，可留空
    }
}