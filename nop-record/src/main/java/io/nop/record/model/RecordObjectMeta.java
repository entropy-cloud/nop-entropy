/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.commons.text.SimpleTextTemplate;
import io.nop.record.model._gen._RecordObjectMeta;

public class RecordObjectMeta extends _RecordObjectMeta implements IRecordFieldsMeta {
    private SimpleTextTemplate normalizedTemplate;

    public RecordObjectMeta() {

    }

    public SimpleTextTemplate getNormalizedTemplate() {
        if (normalizedTemplate == null && getTemplate() != null) {
            this.normalizedTemplate = SimpleTextTemplate.normalize(getTemplate());
        }
        return this.normalizedTemplate;
    }

    @Override
    public RecordFieldMeta requireField(String fieldName) {
        return null;
    }
}
