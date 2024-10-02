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
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagTextCodec;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.output.IRecordTextOutput;
import io.nop.record.util.RecordMetaHelper;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.nop.record.util.RecordMetaHelper.resolveTagTextCodec;
import static io.nop.record.util.RecordMetaHelper.resolveTextCodec;

public class ModelBasedTextRecordOutput<T> extends AbstractModelBasedRecordOutput<IRecordTextOutput, T> {

    public ModelBasedTextRecordOutput(IRecordTextOutput out, RecordFileMeta fileMeta,
                                      IFieldCodecContext context, FieldCodecRegistry registry,
                                      IAggregatorProvider aggregatorProvider) {
        super(out, fileMeta, context, registry, aggregatorProvider);
    }

    public ModelBasedTextRecordOutput(IRecordTextOutput out, RecordFileMeta fileMeta) {
        this(out, fileMeta, new DefaultFieldCodecContext(), FieldCodecRegistry.DEFAULT, CompositeAggregatorProvider.defaultProvider());
    }

    @Override
    protected void writeObjectWithCodec(IRecordTextOutput out, RecordFieldMeta field, Object record) throws IOException {
        IFieldTextCodec encoder = resolveTextCodec(field, registry);
        encoder.encode(out, record, field.getLength(), context,
                (output, value, length, ctx, bodyEncoder) -> {
                    try {
                        writeSwitch(output, field, value);
                    } catch (Exception e) {
                        throw NopException.adapt(e);
                    }
                });
    }


    @Override
    protected void writeOffset(IRecordTextOutput out, int offset) throws IOException {
        for (int i = 0; i < offset; i++) {
            out.append(' ');
        }
    }

    @Override
    protected void writeString(IRecordTextOutput out, String str, Charset charset) throws IOException {
        out.append(str);
    }

    @Override
    protected IBitSet writeTags(IRecordTextOutput out, RecordFieldMeta field, RecordObjectMeta typeMeta, Object value) throws IOException {
        IFieldTagTextCodec codec = field == null ? null : resolveTagTextCodec(field, registry);
        if (codec == null)
            return null;
        return codec.encodeTags(out, value, field, typeMeta, context);
    }

    @Override
    protected void writeField0(IRecordTextOutput out, RecordFieldMeta field, Object record) throws IOException {
        Object value = getFieldValue(field, record);
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

    private Object getFieldValue(RecordFieldMeta field, Object record) {
        ByteString bs = field.getContent();
        if (bs != null) {
            String str = bs.toString(field.getCharset());
            return RecordMetaHelper.padText(str, field);
        } else {
            Object value = getProp(field, record);
            if (field.getPadding() != null) {
                String str = StringHelper.toString(value, "");
                return RecordMetaHelper.padText(str, field);
            }
            return value;
        }
    }

}
