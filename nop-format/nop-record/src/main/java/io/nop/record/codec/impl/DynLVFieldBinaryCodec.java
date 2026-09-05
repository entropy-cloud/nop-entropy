package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_MAX_LENGTH;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

/**
 * 动态长度值（Length-Value）二进制编解码器。
 * <p>
 * 当 {@code valueCodec == null} 时，值按原始字节处理：decode 返回 {@link ByteString}，encode 接受
 * {@link ByteString}（直接写出）或 {@link String}（按 UTF-8 编码写出）。
 * 注意：codec 无 charset 字段，String 固定按 UTF-8 编码，与模型 charset 可能不一致（已知限制）。
 */
public class DynLVFieldBinaryCodec implements IFieldBinaryCodec {

    /**
     * 未配置 maxLength（length<=0）时对线上 len 字段的防御上限，防止恶意长度触发超大 byte[] 分配
     */
    static final int MAX_UNBOUNDED_READ_LEN = 64 * 1024 * 1024;

    private final IFieldBinaryCodec lengthCodec;
    private final IFieldBinaryCodec valueCodec;
    private final Function<Object, Integer> lengthGetter;

    public DynLVFieldBinaryCodec(IFieldBinaryCodec lengthCodec, IFieldBinaryCodec valueCodec,
                                 Function<Object, Integer> lengthGetter) {
        this.lengthCodec = lengthCodec;
        this.lengthGetter = lengthGetter;
        this.valueCodec = valueCodec;
    }

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length, IFieldCodecContext context,
                         IModelBasedBinaryRecordDeserializer deserializer) throws IOException {
        int len = (Integer) lengthCodec.decode(input, record, length, context, deserializer);
        if (len <= 0) {
            return null;
        }

        if (length > 0 && len > length) {
            throw new NopException(ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG)
                    .param(ARG_POS, input.pos())
                    .param(ARG_LENGTH, len).param(ARG_MAX_LENGTH, length);
        }
        if (length <= 0 && len > MAX_UNBOUNDED_READ_LEN) {
            throw new NopException(ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG)
                    .param(ARG_POS, input.pos())
                    .param(ARG_LENGTH, len).param(ARG_MAX_LENGTH, MAX_UNBOUNDED_READ_LEN);
        }

        if (valueCodec == null) {
            return readBytesStrict(input, len);
        }
        return valueCodec.decode(input, record, len, context, deserializer);
    }

    private ByteString readBytesStrict(IBinaryDataReader input, int len) throws IOException {
        byte[] bytes = new byte[len];
        int nRead = 0;
        while (nRead < len) {
            int n = input.read(bytes, nRead, len - nRead);
            if (n <= 0)
                break;
            nRead += n;
        }
        if (nRead != len)
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param(ARG_POS, input.pos())
                    .param(ARG_LENGTH, len);
        return ByteString.of(bytes);
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException {
        int len = lengthGetter.apply(value);
        lengthCodec.encode(output, len, length, context, serializer);
        if (len > 0) {
            if (valueCodec != null) {
                valueCodec.encode(output, value, len, context, serializer);
                return;
            }

            if (value instanceof ByteString) {
                output.writeByteString((ByteString) value);
            } else if (value instanceof String) {
                output.writeBytes(((String) value).getBytes(StandardCharsets.UTF_8));
            } else {
                throw new UnsupportedOperationException(
                        "not yet implemented: unsupported value type " + (value == null ? "null" : value.getClass().getName())
                                + ", expected ByteString or String");
            }
        }
    }
}
