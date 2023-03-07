/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

public interface IBinaryCodec {
    byte[] encodeBytes(byte[] data);

    byte[] decodeBytes(byte[] data);

    default ByteBuf encodeBuf(ByteBuf data, ByteBufAllocator allocator) {
        byte[] bytes = ByteBufUtil.getBytes(data);
        byte[] ret = encodeBytes(bytes);
        return Unpooled.wrappedBuffer(ret);
    }

    default ByteBuf decodeBuf(ByteBuf data, ByteBufAllocator allocator) {
        byte[] bytes = ByteBufUtil.getBytes(data);
        byte[] ret = decodeBytes(bytes);
        return Unpooled.wrappedBuffer(ret);
    }
}