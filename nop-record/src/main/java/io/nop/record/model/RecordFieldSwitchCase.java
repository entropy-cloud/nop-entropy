/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.record.model._gen._RecordFieldSwitchCase;

public class RecordFieldSwitchCase extends _RecordFieldSwitchCase {
    private RecordTypeMeta typeMeta;

    public RecordFieldSwitchCase() {

    }

    public RecordTypeMeta getTypeMeta() {
        return typeMeta;
    }

    public void init(RecordDefinitions defs) {
        typeMeta = defs.resolveType(getType());
    }
}
