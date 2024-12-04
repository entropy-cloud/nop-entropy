package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.type.StdDataType;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodec;
import io.nop.record.codec.IFieldCodecFactory;
import io.nop.record.codec.IFieldConfig;
import io.nop.record.codec.IFieldTextCodec;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class FixedLengthStringCodecFactory implements IFieldCodecFactory {
    public static FixedLengthStringCodecFactory INSTANCE = new FixedLengthStringCodecFactory();

    static final char SPACE_PAD = ' ';

    private final Map<String, IFieldCodec> fieldCodecs = new ConcurrentHashMap<>();

    public FixedLengthStringCodecFactory() {
        Charset charset = StandardCharsets.UTF_8;
        for (StdDataType dataType : StdDataType.values()) {
            if (dataType.isSimpleType()) {
                fieldCodecs.put(getKey(dataType, SPACE_PAD, true, charset),
                        newCodecFactory(dataType, SPACE_PAD, true, StandardCharsets.UTF_8));
                fieldCodecs.put(getKey(dataType, SPACE_PAD, false, charset),
                        newCodecFactory(dataType, SPACE_PAD, false, StandardCharsets.UTF_8));
            }
        }
    }

    IFieldCodec newCodecFactory(StdDataType dataType, char pad, boolean leftPad, Charset charset) {
        return new AbstractFixedLengthAsciiCodec(pad, leftPad, charset) {
            @Override
            protected Object decodeString(String text, Function<ErrorCode, NopException> errorFactory) {
                if (text.isEmpty())
                    return null;
                return dataType.convert(text, errorFactory);
            }
        };
    }

    char getPad(IFieldConfig config) {
        ByteString padding = config.getPadding();
        if (padding == null)
            return ' ';
        return (char) padding.at(0);
    }

    String getKey(StdDataType dataType, char pad, boolean leftPad, Charset charset) {
        return dataType.name() + "-" + (leftPad ? "L" : "R") + pad + charset;
    }

    public IFieldCodec getCodec(IFieldConfig config) {
        Charset charset = getCharset(config);
        char pad = getPad(config);
        StdDataType dataType = config.getStdDataType();
        String key = getKey(dataType, pad, config.isLeftPad(), charset);
        return fieldCodecs.computeIfAbsent(key, k -> newCodecFactory(dataType, pad, config.isLeftPad(), charset));
    }

    Charset getCharset(IFieldConfig config) {
        Charset charset = config.getCharsetObj();
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        return charset;
    }

    @Override
    public IFieldBinaryCodec newBinaryCodec(IFieldConfig config) {
        return getCodec(config);
    }

    @Override
    public IFieldTextCodec newTextCodec(IFieldConfig config) {
        return getCodec(config);
    }
}
