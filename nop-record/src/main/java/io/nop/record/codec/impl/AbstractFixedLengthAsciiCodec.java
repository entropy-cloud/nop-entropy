package io.nop.record.codec.impl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ByteHelper;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.IFieldCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.serialization.IModelBasedTextRecordDeserializer;
import io.nop.record.serialization.IModelBasedTextRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.ITextDataWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Function;

import static io.nop.record.RecordErrors.ARG_FIELD_PATH;

public abstract class AbstractFixedLengthAsciiCodec implements IFieldCodec {
    private final char pad;
    private final boolean leftPad;
    private final Charset charset;

    public AbstractFixedLengthAsciiCodec(char pad, boolean leftPad, Charset charset) {
        this.pad = pad;
        this.leftPad = leftPad;
        this.charset = charset;
    }

    public boolean isLeftPad() {
        return leftPad;
    }

    public char getPad() {
        return pad;
    }

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length, IFieldCodecContext context,
                         IModelBasedBinaryRecordDeserializer deserializer) throws IOException {
        byte[] bytes = input.readBytes(length);
        return decodeBytes(bytes, charset, err -> new NopException(err).param(ARG_FIELD_PATH, context.getFieldPath()));
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException {
        String text = ConvertHelper.toString(value, "");
        byte[] bytes = padBytes(text.getBytes(charset), length);
        output.writeBytes(bytes);
    }

    @Override
    public Object decode(ITextDataReader input, Object record,
                         int length, IFieldCodecContext context, IModelBasedTextRecordDeserializer deserializer) throws IOException {
        String text = input.read(length);
        text = trimString(text);
        return decodeString(text, err -> new NopException(err).param(ARG_FIELD_PATH, context.getFieldPath()));
    }

    @Override
    public void encode(ITextDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedTextRecordSerializer serializer) throws IOException {
        String text = ConvertHelper.toString(value, "0");
        text = padString(text, length);
        output.append(text);
    }

    protected Object decodeBytes(byte[] bytes, Charset charset, Function<ErrorCode, NopException> errorFactory) {
        String text = new String(bytes, charset);
        text = trimString(text);
        return decodeString(text, errorFactory);
    }

    protected String padString(String text, int length) {
        if (leftPad) {
            return StringHelper.forceLeftPad(text, length, pad);
        } else {
            return StringHelper.forceRightPad(text, length, pad);
        }
    }

    protected byte[] padBytes(byte[] bytes, int length) {
        if (leftPad) {
            return ByteHelper.forceLeftPad(bytes, length, (byte) pad);
        } else {
            return ByteHelper.forceRightPad(bytes, length, (byte) pad);
        }
    }

    protected String trimString(String text) {
        if (leftPad) {
            text = StringHelper.trimLeft(text, pad);
        } else {
            text = StringHelper.trimRight(text, pad);
        }
        return text;
    }

    protected abstract Object decodeString(String text, Function<ErrorCode, NopException> errorFactory);

}
