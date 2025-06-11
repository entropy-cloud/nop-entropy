package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagTextCodec;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.util.RecordMetaHelper;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.nop.record.RecordErrors.ARG_EXPECTED;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ERR_RECORD_VALUE_NOT_MATCH_STRING;
import static io.nop.record.util.RecordMetaHelper.resolveTagTextCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTextCodec;

public class ModelBasedTextRecordDeserializer extends AbstractModelBasedRecordDeserializer<ITextDataReader>
        implements IModelBasedTextRecordDeserializer {
    private final FieldCodecRegistry registry;

    public ModelBasedTextRecordDeserializer(FieldCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected IBitSet readTags(ITextDataReader in, RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException {
        IFieldTagTextCodec codec = resolveTagTextCodec(typeMeta, registry);
        if (codec == null)
            return null;
        return codec.decodeTags(in, typeMeta, context);
    }

    @Override
    protected void readCollectionWithCodec(ITextDataReader in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldTextCodec codec = resolveTextCodec(field, registry);
        if (codec != null) {
            codec.decode(in, record, field.getLength(), context,this);
        } else {
            readCollection(in, field, record, context);
        }
    }

    @Override
    protected void readOffset(ITextDataReader in, int offset, IFieldCodecContext context) throws IOException {
        in.skip(offset);
    }

    @Override
    protected void readString(ITextDataReader in, String str, Charset charset, IFieldCodecContext context) throws IOException {
        String read = in.read(str.length());
        if (!str.equals(read))
            throw new NopException(ERR_RECORD_VALUE_NOT_MATCH_STRING)
                    .param(ARG_POS, in.pos())
                    .param(ARG_EXPECTED, str);
    }

    @Override
    protected Object readField0(ITextDataReader in, RecordSimpleFieldMeta field,
                                Object record, IFieldCodecContext context) throws IOException {
        IFieldTextCodec decoder = resolveTextCodec(field, registry);
        if (decoder != null) {
            Object value = decoder.decode(in, record, field.getLength(), context,this);
            return value;
        } else {
            String str = in.read(field.getLength());
            if (field.getContent() != null) {
                if (!field.getContent().utf8().equals(str))
                    throw new NopException(ERR_RECORD_VALUE_NOT_MATCH_STRING).param(ARG_POS, in.pos()).param(ARG_EXPECTED, field.getContent().utf8());
            }
            str = RecordMetaHelper.trimText(str, field);
            return str;
        }
    }
}