/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;

public interface IPacketCodec<T> {
    int UNDETERMINED_LENGTH = -1;
    int CORRUPTED_FRAME = -2;

    /**
     * 确定数据包的总长度，包括头部和尾部
     *
     * @param buf
     * @return 如果接收到的数据包不全，还无法确定长度，则返回 {@code  UNDETERMINED_LENGTH}。
     * 如果接收到的数据包不正确，例如mask不正确或者长度不正确，则返回 {@code CORRUPTED_FRAME}
     */
    int determinePacketLength(ByteBuf buf);

    T decodeFromBuf(ByteBuf buf);

    void encodeToBuf(T message, ByteBuf buf);

    default T decodeFromBytes(byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        return decodeFromBuf(buf);
    }

    default byte[] encodeToBytes(T message) {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        encodeToBuf(message, buf);
        return ByteBufUtil.getBytes(buf);
    }
}