package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.match.IPeekMatchCondition;
import io.nop.record.match.IPeekMatchConditionChecker;
import io.nop.record.match.IPeekMatchRule;
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
import static io.nop.record.RecordErrors.ARG_VALUE;
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
    protected String getRawDataString(IBinaryDataReader in, int length) throws IOException {
        return in.peekByteString(length).hex();
    }

    @Override
    protected void readCollectionWithCodec(IBinaryDataReader in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        IFieldBinaryCodec codec = resolveBinaryCodec(field, registry);
        if (codec != null) {
            Object ret = codec.decode(in, record, field.getLength(), context, this);
            if (field.getVarName() != null) {
                context.setValue(field.getVarName(), ret);
            }
            if (!field.isVirtual()) {
                context.setValue(field.getPropOrFieldName(), ret);
            }
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
                    .param(ARG_EXPECTED, str)
                    .param(ARG_VALUE, StringHelper.bytesToHex(read));
    }

    @Override
    protected Object readField0(IBinaryDataReader in, RecordSimpleFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        int length = getFieldLength(in, field, record, context);

        IFieldBinaryCodec codec = resolveBinaryCodec(field, registry);
        if (codec != null) {
            Object value = codec.decode(in, record, length, context, this);
            return value;
        } else {
            String str = decodeString(in, field.getCharsetObj(), length);
            if (field.getContent() != null) {
                if (!field.getContent().utf8().equals(str))
                    throw new NopException(ERR_RECORD_VALUE_NOT_MATCH_STRING)
                            .param(ARG_POS, in.pos()).param(ARG_EXPECTED, field.getContent().utf8())
                            .param(ARG_VALUE, str);
            }
            str = RecordMetaHelper.trimText(str, field);
            return str;
        }
    }

    String decodeString(IBinaryDataReader in, Charset charset, int length) throws IOException {
        byte[] bytes = in.readBytes(length);
        return new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    @Override
    protected String determineObjectTypeByRule(IPeekMatchRule rule, IBinaryDataReader in,
                                               RecordFieldMeta field, Object record, IFieldCodecContext context) {
        // 创建条件检查器实现
        IPeekMatchConditionChecker checker = new BinaryDataPeekChecker(in);

        // 执行规则匹配
        return rule.match(checker);
    }

    /**
     * 实现IPeekMatchConditionChecker接口，用于检查二进制数据条件
     */
    private static class BinaryDataPeekChecker implements IPeekMatchConditionChecker {
        private final IBinaryDataReader reader;

        public BinaryDataPeekChecker(IBinaryDataReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean matchCondition(IPeekMatchCondition condition) {
            try {
                // 从二进制数据中读取指定偏移量和长度的数据
                ByteString data = reader.peekNextByteString(
                        condition.getOffset(),
                        condition.getLength()
                );

                // 比较字节数据
                if (condition.getBytes() != null) {
                    return Arrays.equals(condition.getBytes(), data.toByteArray());
                }

                // 比较字符串数据
                if (condition.getValue() != null) {
                    String expected = condition.getValue();
                    byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
                    return Arrays.equals(bytes, data.toByteArray());
                }

                return false;
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }
}
