package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.util.RecordMetaHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.nop.record.RecordErrors.ARG_EXPECTED;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ERR_RECORD_VALUE_NOT_MATCH_STRING;
import static io.nop.record.util.RecordMetaHelper.resolveBinaryCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTagBinaryCodec;

public class ModelBasedBinaryRecordDeserializer extends AbstractModelBasedRecordDeserializer<IBinaryDataReader>
        implements IModelBasedBinaryRecordDeserializer {
    private final FieldCodecRegistry registry;

    public ModelBasedBinaryRecordDeserializer(FieldCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected IBitSet readTags(IBinaryDataReader in, RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException {
        IFieldTagBinaryCodec codec = resolveTagBinaryCodec(typeMeta, registry);
        if (codec == null)
            return null;

        return codec.decodeTags(in, typeMeta, context);
    }

    @Override
    protected void readCollectionWithCodec(IBinaryDataReader in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec codec = resolveBinaryCodec(field, registry);
        if (codec != null) {
            codec.decode(in, record, field.getLength(), context, this);
        } else {
            readCollection(in, field, record, context);
        }
    }

    @Override
    protected void readOffset(IBinaryDataReader in, int offset, IFieldCodecContext context) throws IOException {
        in.skip(offset);
    }

    @Override
    protected void readString(IBinaryDataReader in, String str, Charset charset, IFieldCodecContext context) throws IOException {
        byte[] bytes = str.getBytes(charset == null ? StandardCharsets.UTF_8 : charset);
        byte[] read = in.readBytes(bytes.length);
        if (!Arrays.equals(bytes, read))
            throw new NopException(ERR_RECORD_VALUE_NOT_MATCH_STRING)
                    .param(ARG_POS, in.pos())
                    .param(ARG_EXPECTED, str);
    }

    @Override
    protected Object readField0(IBinaryDataReader in, RecordSimpleFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec codec = resolveBinaryCodec(field, registry);
        if (codec != null) {
            Object value = codec.decode(in, record, field.getLength(), context, this);
            return value;
        } else {
            String str = decodeString(in, field.getCharsetObj(), field.getLength());
            if (field.getContent() != null) {
                if (!field.getContent().utf8().equals(str))
                    throw new NopException(ERR_RECORD_VALUE_NOT_MATCH_STRING).param(ARG_POS, in.pos()).param(ARG_EXPECTED, field.getContent().utf8());
            }
            str = RecordMetaHelper.trimText(str, field);
            return str;
        }
    }

    String decodeString(IBinaryDataReader in, Charset charset, int length) throws IOException {
        byte[] bytes = in.readBytes(length);
        return new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset);
    }
}
