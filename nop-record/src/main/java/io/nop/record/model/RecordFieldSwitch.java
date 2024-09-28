/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.record.model._gen._RecordFieldSwitch;

public class RecordFieldSwitch extends _RecordFieldSwitch {
    public RecordFieldSwitch() {

    }

    public String getTypeByCaseValue(String caseValue) {
        RecordFieldSwitchCase caseMeta = getCase(caseValue);
        if (caseMeta != null) {
            return caseMeta.getType();
        }
        return getDefault();
    }
}
