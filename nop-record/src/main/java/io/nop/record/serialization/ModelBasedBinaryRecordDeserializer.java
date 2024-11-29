package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IBinaryDataReader;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.nop.record.RecordErrors.ARG_EXPECTED;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ERR_RECORD_VALUE_NOT_MATCH_STRING;
import static io.nop.record.util.RecordMetaHelper.resolveBinaryCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTagBinaryCodec;

public class ModelBasedBinaryRecordDeserializer extends AbstractModelBasedRecordDeserializer<IBinaryDataReader> {
    private final FieldCodecRegistry registry;

    public ModelBasedBinaryRecordDeserializer(FieldCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected IBitSet readTags(IBinaryDataReader in, RecordFieldMeta field, RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException {
        IFieldTagBinaryCodec codec = field == null ? null : resolveTagBinaryCodec(field, registry);
        if (codec == null)
            return null;
        return codec.decodeTags(in, field, context);
    }

    @Override
    protected void readObjectWithCodec(IBinaryDataReader in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec encoder = resolveBinaryCodec(field, registry);
        encoder.decode(in, record, field.getLength(), field.getCharsetObj(), context);
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
    protected void readField0(IBinaryDataReader in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec codec = resolveBinaryCodec(field, registry);
        if (codec != null) {
            context.enterField(field.getName());
            try {
                Object value = codec.decode(in, record, field.getLength(), field.getCharsetObj(), context);
                if (!field.isVirtual())
                    setPropByName(record, field.getPropOrFieldName(), value);
            } finally {
                context.leaveField(field.getName());
            }
        } else {
            String str = decodeString(in, field.getCharsetObj(), field.getLength());
            if (field.getPadding() != null) {
                char c = (char) field.getPadding().at(0);
                str = StringHelper.trimRight(str, c);
            }
            if (!field.isVirtual())
                setPropByName(record, field.getPropOrFieldName(), str);
        }
    }

    String decodeString(IBinaryDataReader in, Charset charset, int length) throws IOException {
        byte[] bytes = in.readBytes(length);
        return new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset);
    }
}
