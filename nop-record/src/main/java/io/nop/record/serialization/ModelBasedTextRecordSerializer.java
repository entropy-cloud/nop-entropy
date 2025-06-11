package io.nop.record.serialization;

import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagTextCodec;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
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
    protected void writeField0(ITextDataWriter out, RecordSimpleFieldMeta field, Object record,
                               Object value, IFieldCodecContext context) throws IOException {

        IFieldTextCodec encoder = resolveTextCodec(field, registry);
        if (encoder != null) {
            encoder.encode(out, value, field.getLength(), context, null);
        } else {
            ByteString content = field.getContent();
            if (content != null) {
                String str = content.toString(field.getCharset());
                out.append(RecordMetaHelper.padText(str, field));
            } else {
                String str = StringHelper.toString(value, "");
                str = RecordMetaHelper.padText(str, field);
                out.append(str);
            }
        }
    }

    @Override
    protected IBitSet writeTags(ITextDataWriter out, RecordObjectMeta typeMeta, Object value, IFieldCodecContext context) throws IOException {
        IFieldTagTextCodec codec = resolveTagTextCodec(typeMeta, registry);

        if (codec == null)
            return null;
        return codec.encodeTags(out, value, typeMeta, context);
    }

    @Override
    protected void writeOffset(ITextDataWriter out, int offset, IFieldCodecContext context) throws IOException {
        for (int i = 0; i < offset; i++) {
            out.append(' ');
        }
    }

    @Override
    protected void writePadding(ITextDataWriter iTextDataWriter, ByteString padding, int length, IFieldCodecContext context) throws IOException {
        char c = padding.charAt(0);
        for (int i = 0; i < length; i++) {
            iTextDataWriter.append(c);
        }
    }

    @Override
    protected void writeString(ITextDataWriter out, String str, Charset charset, IFieldCodecContext context) throws IOException {
        out.append(str);
    }
}
