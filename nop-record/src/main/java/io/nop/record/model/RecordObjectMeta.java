/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanConstructor;
import io.nop.core.reflect.bean.MethodBeanConstructor;
import io.nop.record.model._gen._RecordObjectMeta;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD;

public class RecordObjectMeta extends _RecordObjectMeta implements IRecordFieldsMeta {
    private SimpleTextTemplate normalizedTemplate;

    private IBeanConstructor constructor;

    private Charset charsetObj;

    public RecordObjectMeta() {

    }

    public Charset getCharsetObj() {
        return charsetObj;
    }

    public void init(RecordDefinitions defs) {
        if (getTemplate() != null) {
            this.normalizedTemplate = SimpleTextTemplate.normalize(getTemplate());
        }

        for (RecordFieldMeta field : getFields()) {
            field.init(defs);
        }

        this.charsetObj = defs.getDefaultCharsetObj();

        if (getBeanClass() != null) {
            constructor = new MethodBeanConstructor(ReflectionManager.instance().loadClassModel(getBeanClass()).getConstructor(0));
        } else {
            constructor = LinkedHashMap::new;
        }
    }

    public Object newBean() {
        return constructor.newInstance();
    }

    @Override
    public SimpleTextTemplate getNormalizedTemplate() {
        return this.normalizedTemplate;
    }

    @Override
    public RecordFieldMeta requireField(String fieldName) {
        RecordFieldMeta field = getField(fieldName);
        if (field == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD)
                    .param(ARG_FIELD_NAME, fieldName);
        return field;
    }
}
