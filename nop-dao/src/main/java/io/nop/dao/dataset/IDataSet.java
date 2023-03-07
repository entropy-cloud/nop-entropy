/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dataset;

import io.nop.commons.record.IRecordInput;

/**
 * 对JDBC ResultSet的封装
 */
public interface IDataSet extends IRecordInput<IDataRow> {
    IDataSetMeta getMeta();

    boolean isDetached();

    IDataSet detach();

    /**
     * 设置内部预取的数据条目数
     */
    default void setFetchSize(int fetchSize) {

    }

    /**
     * 数据集最多获取多少条数据
     */
    default void setMaxRows(long maxRows) {

    }
}