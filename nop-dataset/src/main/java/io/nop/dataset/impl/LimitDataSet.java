/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.impl;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.record.impl.LimitRecordInput;

/**
 * 限制数据集最多读取多少行
 */
public class LimitDataSet extends LimitRecordInput<IDataRow> implements IDataSet {
    public LimitDataSet(IDataSet input, long maxCount) {
        super(input, maxCount);
    }

    @Override
    public IDataSetMeta getMeta() {
        return getInput().getMeta();
    }

    public IDataSet getInput() {
        return (IDataSet) super.getInput();
    }

    @Override
    public boolean isDetached() {
        return getInput().isDetached();
    }

    @Override
    public IDataSet detach() {
        if (isDetached())
            return this;
        return new LimitDataSet(getInput().detach(), getMaxCount());
    }

    @Override
    public IDataSet limit(long maxCount) {
        if (this.getMaxCount() <= maxCount)
            return this;
        return new LimitDataSet(getInput(), maxCount);
    }
}
