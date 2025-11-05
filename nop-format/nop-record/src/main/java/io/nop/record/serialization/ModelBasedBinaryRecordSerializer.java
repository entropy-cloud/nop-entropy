package io.nop.record.serialization;

import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.nop.record.util.RecordMetaHelper.padBinary;
import static io.nop.record.util.RecordMetaHelper.resolveBinaryCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTagBinaryCodec;

public class ModelBasedBinaryRecordSerializer extends AbstractModelBasedRecordSerializer<IBinaryDataWriter>
        implements IModelBasedBinaryRecordSerializer {

    private final FieldCodecRegistry registry;

    public ModelBasedBinaryRecordSerializer(FieldCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void writeField0(IBinaryDataWriter out, RecordSimpleFieldMeta field, Object record,
                               Object value, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec encoder = resolveBinaryCodec(field, registry);
        if (encoder != null) {
            encoder.encode(out, value, field.getLength(), context, null);
        } else {
            if (field.getContent() != null) {
                out.writeByteString(padBinary(field.getContent(), field));
            } else {
                out.writeByteString(padBinary(toBytes(value, field.getCharsetObj()), field));
            }
        }
    }

    @Override
    protected IBitSet writeTags(IBinaryDataWriter out, RecordObjectMeta typeMeta, Object value, IFieldCodecContext context) throws IOException {
        IFieldTagBinaryCodec codec = resolveTagBinaryCodec(typeMeta, registry);
        if (codec == null)
            return null;
        return codec.encodeTags(out, value, typeMeta, context);
    }

    @Override
    protected void writeOffset(IBinaryDataWriter out, int offset, IFieldCodecContext context) throws IOException {
        for (int i = 0; i < offset; i++) {
            out.writeS1((byte) 0);
        }
    }

    @Override
    protected void writeString(IBinaryDataWriter out, String str, Charset charset, IFieldCodecContext context) throws IOException {
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        out.writeBytes(str.getBytes(charset));
    }

    @Override
    protected void writePadding(IBinaryDataWriter iBinaryDataWriter, ByteString padding, int length, IFieldCodecContext context) throws IOException {
        byte c = padding.at(0);
        for (int i = 0; i < length; i++) {
            iBinaryDataWriter.writeByte(c);
        }
    }

    ByteString toBytes(Object value, Charset charset) {
        if (value == null)
            return ByteString.EMPTY;
        if (value instanceof ByteString)
            return (ByteString) value;
        if (value instanceof byte[])
            return ByteString.of((byte[]) value);
        String str = value.toString();
        return ByteString.of(str.getBytes(charset));
    }
}