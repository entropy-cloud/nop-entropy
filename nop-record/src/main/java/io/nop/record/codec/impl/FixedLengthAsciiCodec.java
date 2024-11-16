package io.nop.record.codec.impl;

import io.nop.api.core.convert.ConvertHelper;
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

public class FixedLengthAsciiCodec implements IFieldBinaryCodec, IFieldTextCodec {
    public static FixedLengthAsciiCodec LEFT_PAD = new FixedLengthAsciiCodec(PaddingKind.left, ' ');
    public static FixedLengthAsciiCodec RIGHT_PAD = new FixedLengthAsciiCodec(PaddingKind.right, ' ');

    private final PaddingKind paddingKind;
    private final char paddingChar;

    public FixedLengthAsciiCodec(PaddingKind paddingKind, char paddingChar) {
        this.paddingKind = paddingKind;
        this.paddingChar = paddingChar;
    }

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length,
                         Charset charset, IFieldCodecContext context) throws IOException{
        byte[] bytes = input.readBytes(length);
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        String str = new String(bytes, charset);
        return trim(str);
    }

    String trim(String str) {
        if (paddingKind == PaddingKind.left) {
            return StringHelper.trimLeft(str, paddingChar);
        } else if (paddingKind == PaddingKind.right) {
            return StringHelper.trimRight(str, paddingChar);
        }
        return str;
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
                       IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException {
        String text = ConvertHelper.toString(value, "");
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        byte[] bytes = text.getBytes(charset);
        if (paddingKind != null) {
            int nPad = length - bytes.length;
            if (nPad > 0 && paddingKind == PaddingKind.left) {
                writePad(output, nPad);
            }
            output.writeBytes(bytes);
            if (nPad > 0 && paddingKind == PaddingKind.right) {
                writePad(output, nPad);
            }
        } else {
            output.writeBytes(bytes);
        }
    }

    void writePad(IBinaryDataWriter output, int nPad) throws IOException {
        for (int i = 0; i < nPad; i++) {
            output.writeByte((byte) paddingChar);
        }
    }

    String pad(String text, int length) {
        if (paddingKind == PaddingKind.left) {
            return StringHelper.leftPad(text, length, paddingChar);
        } else if (paddingKind == PaddingKind.right) {
            return StringHelper.rightPad(text, length, paddingChar);
        } else {
            return text;
        }
    }

    @Override
    public Object decode(ITextDataReader input, Object record,
                         int length, IFieldCodecContext context) throws IOException {
        String text = input.read(length);
        return trim(text);
    }

    @Override
    public void encode(ITextDataWriter output, Object value, int length,
                       IFieldCodecContext context, IFieldTextEncoder bodyEncoder) throws IOException {
        String text = ConvertHelper.toString(value, "0");
        text = pad(text, length);
        output.append(text);
    }
}
