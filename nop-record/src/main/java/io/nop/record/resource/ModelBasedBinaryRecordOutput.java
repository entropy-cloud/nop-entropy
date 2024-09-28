/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.aggregator.CompositeAggregatorProvider;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.commons.bytes.ByteString;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.output.IRecordBinaryOutput;
import io.nop.record.util.RecordMetaHelper;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.nop.record.util.RecordMetaHelper.resolveBinaryCodec;

public class ModelBasedBinaryRecordOutput<T> extends AbstractModelBasedRecordOutput<T> {
    private final IRecordBinaryOutput out;

    public ModelBasedBinaryRecordOutput(IRecordBinaryOutput out, RecordFileMeta fileMeta,
                                        IFieldCodecContext context, FieldCodecRegistry registry,
                                        IAggregatorProvider aggregatorProvider) {
        super(fileMeta, context, registry, aggregatorProvider);
        this.out = out;
    }

    public ModelBasedBinaryRecordOutput(IRecordBinaryOutput out, RecordFileMeta fileMeta) {
        this(out, fileMeta, new DefaultFieldCodecContext(), FieldCodecRegistry.DEFAULT, CompositeAggregatorProvider.defaultProvider());
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    protected void writeOffset(int offset) throws IOException {
        for (int i = 0; i < offset; i++) {
            out.writeS1((byte) 0);
        }
    }

    @Override
    protected void writeString(String str, Charset charset) throws IOException {
        out.writeBytes(str.getBytes(charset));
    }

    @Override
    protected void writeField0(RecordFieldMeta field, Object record) throws IOException {
        Object value = getFieldValue(field, record);
        IFieldBinaryCodec encoder = resolveBinaryCodec(field, registry);
        if (encoder != null) {
            context.enterField(field.getName());
            try {
                encoder.encode(out, value, field.getLength(), field.getCharsetObj(), context);
            } finally {
                context.leaveField(field.getName());
            }
        } else {
            if (value != null)
                out.writeByteString(toBytes(value, field.getCharsetObj()));
        }
    }

    private Object getFieldValue(RecordFieldMeta field, Object record) {
        ByteString bs = field.getContent();
        if (bs != null) {
            return RecordMetaHelper.padBinary(bs, field);
        } else {
            Object value = getProp(field, record);
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
