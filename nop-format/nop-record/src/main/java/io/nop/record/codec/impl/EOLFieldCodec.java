package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.RecordErrors;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldCodecFactory;
import io.nop.record.codec.IFieldConfig;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.serialization.IModelBasedTextRecordDeserializer;
import io.nop.record.serialization.IModelBasedTextRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.ITextDataWriter;

import java.io.EOFException;
import java.io.IOException;

import static io.nop.record.RecordErrors.ARG_FIELD_PATH;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ARG_VALUE;

public class EOLFieldCodec implements IFieldCodec, IFieldCodecFactory {
    public static final EOLFieldCodec INSTANCE = new EOLFieldCodec();

    @Override
    public IFieldBinaryCodec newBinaryCodec(IFieldConfig config) {
        return this;
    }

    @Override
    public IFieldTextCodec newTextCodec(IFieldConfig config) {
        return this;
    }

    @Override
    public Object decode(IBinaryDataReader input,
                         Object record, int length,
                         IFieldCodecContext context, IModelBasedBinaryRecordDeserializer deserializer) throws IOException {
        int t = input.read();
        if (t == -1)
            throw new EOFException();
        if (t == '\r') {
            t = input.read();
            if (t == -1)
                throw new EOFException();
        }
        if (t != '\n')
            throw new NopException(RecordErrors.ERR_RECORD_NOT_END_OF_LINE)
                    .param(ARG_POS, input.pos())
                    .param(ARG_VALUE, t)
                    .param(ARG_FIELD_PATH, context.getFieldPath());
        return '\n';
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException {
        output.writeByte((byte) '\n');
    }

    @Override
    public Object decode(ITextDataReader input, Object record, int length,
                         IFieldCodecContext context, IModelBasedTextRecordDeserializer deserializer) throws IOException {
        int t = input.readChar();
        if (t == -1)
            throw new EOFException();
        if (t == '\r') {
            t = input.readChar();
            if (t == -1)
                throw new EOFException();
        }
        if (t != '\n')
            throw new NopException(RecordErrors.ERR_RECORD_NOT_END_OF_LINE)
                    .param(ARG_POS, input.pos())
                    .param(ARG_VALUE, t)
                    .param(ARG_FIELD_PATH, context.getFieldPath());
        return "\n";
    }

    @Override
    public void encode(ITextDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedTextRecordSerializer serializer) throws IOException {
        output.append('\n');
    }
}
