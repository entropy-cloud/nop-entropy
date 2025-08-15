/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model._gen._RecordFieldMeta;

import java.util.Map;

public class RecordFieldMeta extends _RecordFieldMeta {
    private boolean firstField;
    private boolean lastField;



    public RecordFieldMeta() {

    }

    public boolean isFirstField() {
        return firstField;
    }

    public void setFirstField(boolean firstField) {
        this.firstField = firstField;
    }

    public boolean isLastField() {
        return lastField;
    }

    public void setLastField(boolean lastField) {
        this.lastField = lastField;
    }

    public void init(RecordDefinitions defs) {
        if (getEndian() == null && defs.getDefaultEndian() != null) {
            setEndian(defs.getDefaultEndian());
        }
    }

    public String getTypeByCaseValue(String caseValue) {
        // 如果Map为空，则认为caseValue直接对应于类名
        Map<String, String> typeMap = getSwitchTypeMap();
        if (typeMap == null || typeMap.isEmpty())
            return caseValue;
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
}
