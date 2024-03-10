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
    public IRecordBinaryOutput append(byte[] bytes) {
        byteBuf.writeBytes(bytes); // 将整个字节数组写入ByteBuf
        return this;
    }

    @Override
    public IRecordBinaryOutput append(byte[] str, int start, int end) {
        byteBuf.writeBytes(str, start, end - start); // 将字节数组的一部分写入ByteBuf
        return this;
    }

    @Override
    public IRecordBinaryOutput append(byte c) {
        byteBuf.writeByte(c); // 写入一个字节
        return this;
    }

    @Override
    public IRecordBinaryOutput append(ByteBuffer buf) {
        byteBuf.writeBytes(buf); // 将ByteBuffer的内容写入ByteBuf
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