/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;

public class DefaultFieldMapper implements IFieldMapper {
    public static final DefaultFieldMapper INSTANCE = new DefaultFieldMapper();

    @Override
    public Object getValue(IDataRow row, int index) {
        return row.getObject(index);
    }
}
