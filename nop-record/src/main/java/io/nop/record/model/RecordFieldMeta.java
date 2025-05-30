/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.record.model._gen._RecordFieldMeta;

import java.util.Map;

public class RecordFieldMeta extends _RecordFieldMeta {

    private SimpleTextTemplate normalizedTemplate;

    public RecordFieldMeta() {

    }

    public void init(RecordDefinitions defs) {
        if (getEndian() == null && defs.getDefaultEndian() != null) {
            setEndian(defs.getDefaultEndian());
        }
    }

    public String getTypeByCaseValue(String caseValue) {
        Map<String, String> typeMap = getSwitchTypeMap();
        if (typeMap == null)
            return null;
        String type = typeMap.get(caseValue);
        if (type == null)
            type = typeMap.get("*");
        return type;
    }

    public boolean isMatchTag(IBitSet tags) {
        if (tags == null)
            return true;

        int tagIndex = getTagIndex();
        if (tagIndex < 0)
            return true;

        return tags.get(tagIndex);
    }

    public SimpleTextTemplate getNormalizedTemplate() {
        return this.normalizedTemplate;
    }
}
