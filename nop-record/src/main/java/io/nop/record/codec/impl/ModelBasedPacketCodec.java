package io.nop.record.codec.impl;

import io.netty.buffer.ByteBuf;
import io.nop.codec.IPacketCodec;
import io.nop.record.model.PacketCodecModel;

public class ModelBasedPacketCodec implements IPacketCodec {
    private final PacketCodecModel codecModel;

    public ModelBasedPacketCodec(PacketCodecModel codecModel) {
        this.codecModel = codecModel;
    }

    @Override
    public int determinePacketLength(ByteBuf buf) {
        return 0;
    }

    @Override
    public Object decodeFromBuf(ByteBuf buf, Class targetType) {
        return null;
    }

    @Override
    public void encodeToBuf(Object message, ByteBuf buf) {

    }
}
