/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.codec;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.record.codec._gen.FieldBinaryCodecRegistrar;
import io.nop.record.codec.impl.BitmapTagBinaryCodec;
import io.nop.record.codec.impl.EOLFieldCodec;
import io.nop.record.codec.impl.FixedLengthStringCodecFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.record.RecordErrors.ARG_CODEC;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD_CODEC;

@GlobalInstance
public class FieldCodecRegistry {
    public static FieldCodecRegistry DEFAULT = new FieldCodecRegistry();

    static {
        FieldBinaryCodecRegistrar.registerWordType(DEFAULT);
        DEFAULT.registerTagBinaryCodec("bitmap128", BitmapTagBinaryCodec.INSTANCE);
        DEFAULT.registerCodec("FLS", FixedLengthStringCodecFactory.INSTANCE);
        DEFAULT.registerCodec("EOL", EOLFieldCodec.INSTANCE);
    }

    private final Map<String, IFieldCodecFactory> codecFactories = new ConcurrentHashMap<>();

    private final Map<String, IFieldTagBinaryCodec> tagBinaryEncoders = new ConcurrentHashMap<>();

    private final Map<String, IFieldTagTextCodec> tagTextEncoders = new ConcurrentHashMap<>();

    public void registerCodec(String name, IFieldCodecFactory codec) {
        codecFactories.put(name, codec);
    }

    public void unregisterCodec(String name, IFieldCodecFactory codec) {
        codecFactories.remove(name, codec);
    }

    public void registerTagTextCodec(String name, IFieldTagTextCodec encoder) {
        tagTextEncoders.put(name, encoder);
    }

    public void unregisterTagTextCodec(String name, IFieldTagTextCodec encoder) {
        tagTextEncoders.remove(name, encoder);
    }

    public void registerTagBinaryCodec(String name, IFieldTagBinaryCodec encoder) {
        tagBinaryEncoders.put(name, encoder);
    }

    public void unregisterTagBinaryCodec(String name, IFieldTagBinaryCodec encoder) {
        tagBinaryEncoders.remove(name, encoder);
    }

    public IFieldTagTextCodec getTagTextCodec(String name) {
        return tagTextEncoders.get(name);
    }

    public IFieldTagBinaryCodec getTagBinaryCodec(String name) {
        return tagBinaryEncoders.get(name);
    }

    public IFieldTextCodec getTextCodec(String name, IFieldConfig config) {
        IFieldCodecFactory factory = codecFactories.get(name);
        return factory == null ? null : factory.newTextCodec(config);
    }

    public IFieldBinaryCodec getBinaryCodec(String name, IFieldConfig config) {
        IFieldCodecFactory factory = codecFactories.get(name);
        return factory == null ? null : factory.newBinaryCodec(config);
    }

    public IFieldBinaryCodec requireBinaryCodec(String name, IFieldConfig config) {
        IFieldBinaryCodec codec = getBinaryCodec(name, config);
        if (codec == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .param(ARG_FIELD_NAME, config.getName())
                    .param(ARG_CODEC, name);
        return codec;
    }
}
