package io.nop.record.codec.impl;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecFactory;
import io.nop.record.codec.IFieldConfig;

public abstract class StaticFieldBinaryCodecFactory implements IFieldCodecFactory, IFieldBinaryCodec {
    @Override
    public IFieldBinaryCodec newBinaryCodec(IFieldConfig config) {
        return this;
    }
}
