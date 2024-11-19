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
import io.nop.record.model._gen._RecordObjectMeta;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD;

public class RecordObjectMeta extends _RecordObjectMeta implements IRecordFieldsMeta {
    private SimpleTextTemplate normalizedTemplate;

    public RecordObjectMeta() {

    }

    public void init(RecordDefinitions defs) {
        if (getTemplate() != null) {
            this.normalizedTemplate = SimpleTextTemplate.normalize(getTemplate());
        }

        for (RecordFieldMeta field : getFields()) {
            field.init(defs);
        }
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
