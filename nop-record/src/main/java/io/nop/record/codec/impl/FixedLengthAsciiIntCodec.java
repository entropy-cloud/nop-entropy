package io.nop.record.codec.impl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.codec.IFieldTextEncoder;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.input.IRecordTextInput;
import io.nop.record.output.IRecordBinaryOutput;
import io.nop.record.output.IRecordTextOutput;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.nop.record.RecordErrors.ARG_FIELD_PATH;

public class FixedLengthAsciiIntCodec implements IFieldBinaryCodec, IFieldTextCodec {
    public static FixedLengthAsciiIntCodec INSTANCE = new FixedLengthAsciiIntCodec();

    @Override
    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context) {
        byte[] bytes = input.readBytes(length);
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        return ConvertHelper.toPrimitiveInt(new String(bytes, charset), err -> new NopException(err).param(ARG_FIELD_PATH, context.getFieldPath()));
    }

    @Override
    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset,
                       IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) {
        String text = ConvertHelper.toString(value, "0");
        text = StringHelper.leftPad(text, length, '0');
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        output.writeBytes(text.getBytes(charset));
    }

    @Override
    public Object decode(IRecordTextInput input, int length, IFieldCodecContext context) {
        String text = input.read(length);
        return ConvertHelper.toPrimitiveInt(text, err -> new NopException(err).param(ARG_FIELD_PATH, context.getFieldPath()));
    }

    @Override
    public void encode(IRecordTextOutput output, Object value, int length,
                       IFieldCodecContext context, IFieldTextEncoder bodyEncoder) throws IOException {
        String text = ConvertHelper.toString(value, "0");
        text = StringHelper.leftPad(text, length, '0');
        output.append(text);
    }
}
