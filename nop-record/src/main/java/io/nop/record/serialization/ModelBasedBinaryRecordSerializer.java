package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.util.RecordMetaHelper;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.nop.record.util.RecordMetaHelper.resolveBinaryCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTagBinaryCodec;

public class ModelBasedBinaryRecordSerializer extends AbstractModelBasedRecordSerializer<IBinaryDataWriter> {
    private final FieldCodecRegistry registry;

    public ModelBasedBinaryRecordSerializer(FieldCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void writeField0(IBinaryDataWriter out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        Object value = getFieldValue(field, record, context);
        IFieldBinaryCodec encoder = resolveBinaryCodec(field, registry);
        if (encoder != null) {
            context.enterField(field.getName());
            try {
                encoder.encode(out, value, field.getLength(), context, null);
            } finally {
                context.leaveField(field.getName());
            }
        } else {
            if (value != null)
                out.writeByteString(toBytes(value, field.getCharsetObj()));
        }
    }

    @Override
    protected IBitSet writeTags(IBinaryDataWriter out, RecordFieldMeta field, RecordObjectMeta typeMeta, Object value, IFieldCodecContext context) throws IOException {
        IFieldTagBinaryCodec codec = field == null ? null : resolveTagBinaryCodec(field, registry);
        if (codec == null)
            return null;
        return codec.encodeTags(out, value, field, typeMeta, context);
    }

    @Override
    protected void writeObjectWithCodec(IBinaryDataWriter out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec encoder = resolveBinaryCodec(field, registry);
        encoder.encode(out, record, field.getLength(),  context,
                (output, value, length, ctx, bodyEncoder) -> {
                    try {
                        writeSwitch(output, field, value, context);
                    } catch (Exception e) {
                        throw NopException.adapt(e);
                    }
                });
    }

    @Override
    protected void writeOffset(IBinaryDataWriter out, int offset, IFieldCodecContext context) throws IOException {
        for (int i = 0; i < offset; i++) {
            out.writeS1((byte) 0);
        }
    }

    @Override
    protected void writeString(IBinaryDataWriter out, String str, Charset charset, IFieldCodecContext context) throws IOException {
        out.writeBytes(str.getBytes(charset));
    }

    private Object getFieldValue(RecordFieldMeta field, Object record, IFieldCodecContext context) {
        ByteString bs = field.getContent();
        if (bs != null) {
            return RecordMetaHelper.padBinary(bs, field);
        } else {
            Object value = getProp(field, record, context);
            if (field.getPadding() != null) {
                bs = toBytes(value, field.getCharsetObj());
                return RecordMetaHelper.padBinary(bs, field);
            }
            return value;
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
