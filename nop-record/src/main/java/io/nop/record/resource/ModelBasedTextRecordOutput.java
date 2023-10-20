/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.encoder.FieldEncoderRegistry;
import io.nop.record.encoder.IFieldTextEncoder;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.output.IRecordOutputContext;
import io.nop.record.output.IRecordTextOutput;
import io.nop.record.util.RecordMetaHelper;
import io.nop.xlang.api.XLang;

import java.io.IOException;
import java.util.Map;

import static io.nop.record.util.RecordMetaHelper.getEncoder;

public class ModelBasedTextRecordOutput<T> extends AbstractTextRecordOutput<T> implements IRecordOutputContext {
    private final RecordFileMeta fileMeta;

    private final FieldEncoderRegistry registry;
    private IEvalScope scope;

    public ModelBasedTextRecordOutput(IRecordTextOutput out, RecordFileMeta fileMeta,
                                      IEvalScope scope, FieldEncoderRegistry registry) {
        super(out);
        this.fileMeta = fileMeta;
        this.scope = scope;
        this.registry = registry;
    }

    public ModelBasedTextRecordOutput(IRecordTextOutput out, RecordFileMeta fileMeta) {
        this(out, fileMeta, XLang.newEvalScope(), FieldEncoderRegistry.DEFAULT);
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public void setHeaderMeta(Map<String, Object> attributes) {
        if (fileMeta.getHeader() != null) {
            writeObject(fileMeta.getHeader(), attributes);
        }
    }

    @Override
    public void setTrailerMeta(Map<String, Object> trailerMeta) {
        if (fileMeta.getTrailer() != null) {
            writeObject(fileMeta.getTrailer(), trailerMeta);
        }
    }

    @Override
    public void write(T record) {
        writeObject(fileMeta.getBody(), record);
    }

    private void writeObject(RecordObjectMeta recordMeta, Object record) {
        try {
            for (RecordFieldMeta field : recordMeta.getFields()) {
                writeField(field, record);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private void writeField(RecordFieldMeta field, Object record) throws IOException {
        Object value = getFieldValue(field, record);
        IFieldTextEncoder encoder = getEncoder(field, registry);
        if (encoder != null) {
            encoder.encode(out, value, field, this);
        } else {
            if (value != null)
                out.append(value.toString());
        }
    }

    private Object getFieldValue(RecordFieldMeta field, Object record) {
        ByteString bs = field.getContent();
        if (bs != null) {
            String str = bs.toString(field.getEncoding());
            return padding(str, field);
        } else {
            Object value = getProp(record, field.getName());
            if (field.getPadding() != null) {
                String str = StringHelper.toString(value, "");
                return padding(str, field);
            }
            return value;
        }
    }

    private Object getProp(Object record, String propName) {
        return BeanTool.getComplexProperty(record, propName);
    }

    private String padding(String str, RecordFieldMeta field) {
        int len = str.length();
        RecordMetaHelper.checkMaxLen(len, field);
        RecordMetaHelper.checkMinLen(len, field);

        int expected = field.getLength();
        if (expected > 0) {
            if (len == expected)
                return str;

            ByteString padding = field.getPadding();
            if (padding != null) {
                String paddingStr = padding.toString(field.getEncoding());
                if (field.isLeftPad()) {
                    str = StringHelper.leftPad(str, expected, paddingStr.charAt(0));
                } else {
                    str = StringHelper.rightPad(str, expected, paddingStr.charAt(0));
                }
            }
        }
        return str;
    }

}
