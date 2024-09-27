/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.output.IRecordTextOutput;
import io.nop.record.util.RecordMetaHelper;

import java.io.IOException;

import static io.nop.record.util.RecordMetaHelper.resolveTextCodec;

public class ModelBasedTextRecordOutput<T> extends AbstractModelBasedRecordOutput<T> {
    private final IRecordTextOutput out;

    public ModelBasedTextRecordOutput(IRecordTextOutput out, RecordFileMeta fileMeta,
                                      IFieldCodecContext context, FieldCodecRegistry registry) {
        super(fileMeta, context, registry);
        this.out = out;
    }

    public ModelBasedTextRecordOutput(IRecordTextOutput out, RecordFileMeta fileMeta) {
        this(out, fileMeta, new DefaultFieldCodecContext(), FieldCodecRegistry.DEFAULT);
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
            out.append(' ');
        }
    }

    @Override
    protected void writeString(String str) throws IOException {
        out.append(str);
    }

    @Override
    protected void writeField0(RecordFieldMeta field, Object record) throws IOException {
        Object value = getFieldValue(field, record);
        IFieldTextCodec encoder = resolveTextCodec(field, registry);
        if (encoder != null) {
            context.enterField(field.getName());
            try {
                encoder.encode(out, value, field.getLength(), context);
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
