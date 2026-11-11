/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.nop.api.core.util.Guard;
import io.nop.codec.IPacketCodec;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PacketCodecHandler extends ByteToMessageCodec<Object> {
    static final Logger LOG = LoggerFactory.getLogger(PacketCodecHandler.class);

    private final IPacketCodec<Object> codec;
    private final int maxFrameLength;
    private final int maxDecodeErrorCount;
    /**
     * 4.3-C3-023（plan 2561 裁定 F）：encode 吞噬分支已删除，本阈值不再参与判定——
     * 仅为构造器签名兼容保留（调用方零改动）。
     */
    @SuppressWarnings("unused")
    private final int maxEncodeErrorCount;

    private int decodeErrorCount;

    /**
     * 4.3-C3-023（plan 2561 裁定 F）：结构化丢帧计数器——decode 吞噬（WARN）路径的单调累计，
     * 供调用方/运维观测真实丢帧量（{@code decodeErrorCount} 是带成功清零的滑动计数，非累计）。
     */
    private volatile long droppedFrameCount;

    public PacketCodecHandler(IPacketCodec<?> codec, int maxFrameLength, int maxDecodeErrorCount, int maxEncodeErrorCount) {
        Guard.notNull(codec, "codec");
        this.codec = (IPacketCodec<Object>) codec;
        this.maxFrameLength = maxFrameLength;
        this.maxEncodeErrorCount = maxEncodeErrorCount;
        this.maxDecodeErrorCount = maxDecodeErrorCount;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        // 保留一个后门。如果是直接发送byte数组，则直接写出，跳过codec编码
        if (msg instanceof byte[]) {
            out.writeBytes((byte[]) msg);
        } else {
            // 4.3-C3-023（plan 2561 裁定 F）：encode 异常直接上抛——MessageToByteEncoder 据
            // 异常 fail 本次 write promise（不自动关 channel，由调用方/exceptionCaught 观察）。
            // 原 markWriterIndex + resetWriterIndex + WARN 吞噬分支删除：encode 路径无 size 上限
            // 判定（maxFrameLength 仅 decode 强制），且吞噬后调用方误认发送成功而 write 出
            // 不完整帧——半帧静默投递比显式失败更危险。
            codec.encodeToBuf(msg, out);
        }
    }

    /**
     * 4.3-C3-023（plan 2561 裁定 F）：decode 吞噬路径的丢帧累计（单调，成功不清零）。
     */
    public long getDroppedFrameCount() {
        return droppedFrameCount;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int len = codec.determinePacketLength(in);
        if (len == IPacketCodec.UNDETERMINED_LENGTH) {
            LOG.trace("nop.netty.packet-length-not-determined");
            return;
        }

        if (len < 0) {
            LOG.info("nop.netty.receive-corrupted-frame");
            throw new CorruptedFrameException("nop.err.netty.corrupted-frame");
        }

        if (len > maxFrameLength) {
            LOG.info("nop.netty.frame-len-exceed-limit:len={},max={}", len, maxFrameLength);
            throw new TooLongFrameException("nop.err.netty.too-long-frame");
        }

        if (in.readableBytes() < len) {
            LOG.trace("nop.netty.skip-when-msg-incomplete");
            return;
        }

        // 与LengthFieldBasedFrameDecoder不同，这里不使用retainedSlice，也就不需要release返回的frame
        ByteBuf frame = in.readSlice(len);
        try {
            Object result = codec.decodeFromBuf(frame);

            if (result != null) {
                decodeErrorCount = 0;
                out.add(result);
            }
        } catch (RuntimeException e) {
            LOG.error("nop.netty.decode-msg-fail:frame={}", StringHelper.bytesToHex(ByteBufUtil.getBytes(frame)), e);

            // 4.3-C3-023（plan 2561 裁定 F）：真实增量 = 结构化丢帧计数（WARN 吞噬现状保留）
            droppedFrameCount++;

            decodeErrorCount++;

            if (decodeErrorCount > maxDecodeErrorCount)
                throw e;

            LOG.warn("nop.netty.warn.ignore-decode-msg-fail:errorCount={}", decodeErrorCount);
        }
    }
}