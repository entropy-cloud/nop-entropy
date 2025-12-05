/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codec.support;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.nop.codec.IBinaryCodec;

public abstract class AbstractByteBufCodec implements IBinaryCodec {
    @Override
    public byte[] encodeBytes(byte[] data) {
        ByteBuf buf = encodeBuf(Unpooled.wrappedBuffer(data), UnpooledByteBufAllocator.DEFAULT);
        byte[] bytes = ByteBufUtil.getBytes(buf);
        return bytes;
    }

    @Override
    public byte[] decodeBytes(byte[] data) {
        ByteBuf buf = decodeBuf(Unpooled.wrappedBuffer(data), UnpooledByteBufAllocator.DEFAULT);
        return ByteBufUtil.getBytes(buf);
    }

    public abstract ByteBuf encodeBuf(ByteBuf data, ByteBufAllocator allocator);

    public abstract ByteBuf decodeBuf(ByteBuf data, ByteBufAllocator allocator);
}