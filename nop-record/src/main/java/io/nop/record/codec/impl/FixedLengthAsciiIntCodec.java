package io.nop.record.codec.impl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.codec.IFieldTextEncoder;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.ITextDataWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.nop.record.RecordErrors.ARG_FIELD_PATH;

public class FixedLengthAsciiIntCodec implements IFieldBinaryCodec, IFieldTextCodec {
    public static FixedLengthAsciiIntCodec INSTANCE = new FixedLengthAsciiIntCodec();

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length,
                         Charset charset, IFieldCodecContext context) throws IOException {
        byte[] bytes = input.readBytes(length);
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        return ConvertHelper.toPrimitiveInt(new String(bytes, charset), err -> new NopException(err).param(ARG_FIELD_PATH, context.getFieldPath()));
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
                       IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException {
        String text = ConvertHelper.toString(value, "0");
        text = StringHelper.leftPad(text, length, '0');
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        output.writeBytes(text.getBytes(charset));
    }

    @Override
    public Object decode(ITextDataReader input, Object record,
                         int length, IFieldCodecContext context) throws IOException {
        String text = input.read(length);
        return ConvertHelper.toPrimitiveInt(text, err -> new NopException(err).param(ARG_FIELD_PATH, context.getFieldPath()));
    }

    @Override
    public void encode(ITextDataWriter output, Object value, int length,
                       IFieldCodecContext context, IFieldTextEncoder bodyEncoder) throws IOException {
        String text = ConvertHelper.toString(value, "0");
        text = StringHelper.leftPad(text, length, '0');
        output.append(text);
    }
}
