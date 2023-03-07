/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dataset.impl;

import io.nop.dao.dataset.IDataRow;
import io.nop.dao.dataset.IFieldMapper;

public class DefaultFieldMapper implements IFieldMapper {
    public static final DefaultFieldMapper INSTANCE = new DefaultFieldMapper();

    @Override
    public Object getValue(IDataRow row, int index) {
        return row.getObject(index);
    }
}
