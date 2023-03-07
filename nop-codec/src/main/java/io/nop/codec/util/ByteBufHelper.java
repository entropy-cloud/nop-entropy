/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codec.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.nop.api.core.util.Guard;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;

public class ByteBufHelper {
    public static void writeFully(GatheringByteChannel channel, ByteBuf byteBuf) throws IOException {
        if (byteBuf != null) {
            int length = byteBuf.readableBytes();
            int written = 0;
            if (byteBuf.nioBufferCount() == 1) {
                ByteBuffer byteBuffer = byteBuf.nioBuffer();
                while (written < length) {
                    written += channel.write(byteBuffer);
                }
            } else {
                ByteBuffer[] byteBuffers = byteBuf.nioBuffers();
                while (written < length) {
                    written += channel.write(byteBuffers);
                }
            }
        }
    }

    public static void writeBuf(OutputStream os, ByteBuf buf) throws IOException {
        writeBuf(os, buf, buf.readerIndex(), buf.readableBytes());
    }

    public static void writeBuf(OutputStream os, ByteBuf buf, int start, int length) throws IOException {
        int n = buf.capacity();
        Guard.checkOffsetLength(n, start, length);

        if (buf.hasArray()) {
            int baseOffset = buf.arrayOffset() + start;
            byte[] bytes = buf.array();
            os.write(bytes, baseOffset, length);
        } else {
            byte[] bytes = ByteBufUtil.getBytes(buf);
            os.write(bytes);
        }
    }
}