package io.nop.record.codec.impl;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecFactory;
import io.nop.record.codec.IFieldConfig;
import io.nop.record.codec.IFieldTextCodec;

public abstract class StaticFieldCodecFactory implements IFieldCodecFactory, IFieldTextCodec, IFieldBinaryCodec {

    @Override
    public IFieldBinaryCodec newBinaryCodec(IFieldConfig config) {
        return this;
    }

    @Override
    public IFieldTextCodec newTextCodec(IFieldConfig config) {
        return this;
    }
}
