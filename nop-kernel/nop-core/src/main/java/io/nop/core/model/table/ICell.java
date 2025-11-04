/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    default void setRowOffset(int rowOffset){

    }

    default void setColOffset(int colOffset){

    }

    Object getValue();

    void setValue(Object value);

    String getComment();

    void setComment(String comment);
}