/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import io.nop.api.core.util.IFreezable;

public interface ICell extends ICellView, IFreezable {
    default ICell getRealCell() {
        return this;
    }

    ICell cloneInstance();

    IRow getRow();

    void setRow(IRow row);

    void setMergeAcross(int mergeAcross);

    void setMergeDown(int mergeDown);

    Object getValue();

    void setValue(Object value);
}