package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagTextCodec;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.util.RecordMetaHelper;
import io.nop.record.writer.ITextDataWriter;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.nop.record.util.RecordMetaHelper.resolveTagTextCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTextCodec;

public class ModelBasedTextRecordSerializer extends AbstractModelBasedRecordSerializer<ITextDataWriter> {
    private final FieldCodecRegistry registry;

    public ModelBasedTextRecordSerializer(FieldCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void writeField0(ITextDataWriter out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        Object value = getFieldValue(field, record, context);
        IFieldTextCodec encoder = resolveTextCodec(field, registry);
        if (encoder != null) {
            context.enterField(field.getName());
            try {
                encoder.encode(out, value, field.getLength(), context, null);
            } finally {
                context.leaveField(field.getName());
            }
        } else {
            if (value != null)
                out.append(value.toString());
        }
    }

    @Override
    protected IBitSet writeTags(ITextDataWriter out, RecordFieldMeta field, RecordObjectMeta typeMeta, Object value, IFieldCodecContext context) throws IOException {
        IFieldTagTextCodec codec = field == null ? null : resolveTagTextCodec(field, registry);
        if (codec == null)
            return null;
        return codec.encodeTags(out, value, field, typeMeta, context);
    }

    @Override
    protected void writeObjectWithCodec(ITextDataWriter out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldTextCodec encoder = resolveTextCodec(field, registry);
        encoder.encode(out, record, field.getLength(), context,
                (output, value, length, ctx, bodyEncoder) -> {
                    try {
                        writeSwitch(output, field, value, context);
                    } catch (Exception e) {
                        throw NopException.adapt(e);
                    }
                });
    }

    @Override
    protected void writeOffset(ITextDataWriter out, int offset, IFieldCodecContext context) throws IOException {
        for (int i = 0; i < offset; i++) {
            out.append(' ');
        }
    }

    @Override
    protected void writeString(ITextDataWriter out, String str, Charset charset, IFieldCodecContext context) throws IOException {
        out.append(str);
    }

    private Object getFieldValue(RecordFieldMeta field, Object record, IFieldCodecContext context) {
        ByteString bs = field.getContent();
        if (bs != null) {
            String str = bs.toString(field.getCharset());
            return RecordMetaHelper.padText(str, field);
        } else {
            Object value = getProp(field, record, context);
            if (field.getPadding() != null) {
                String str = StringHelper.toString(value, "");
                return RecordMetaHelper.padText(str, field);
            }
            return value;
        }
    }
}
