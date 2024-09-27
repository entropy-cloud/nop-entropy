/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.commons.util.StringHelper;
import io.nop.record.model._gen._RecordObjectMeta;

public class RecordObjectMeta extends _RecordObjectMeta implements IRecordFieldsMeta {
    private String normalizedTemplate;

    public RecordObjectMeta() {

    }

    public String getNormalizedTemplate() {
        if (normalizedTemplate == null && getTemplate() != null) {
            this.normalizedTemplate = StringHelper.normalizeTemplate(getTemplate());
        }
        return this.normalizedTemplate;
    }
}
