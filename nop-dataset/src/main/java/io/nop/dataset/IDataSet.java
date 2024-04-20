/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset;

import io.nop.dataset.record.IRecordInput;

import java.util.Map;

/**
 * 对JDBC ResultSet的封装
 */
public interface IDataSet extends IRecordInput<IDataRow> {
    IDataSetMeta getMeta();

    boolean isDetached();

    IDataSet detach();

    default IRecordInput<Map<String, Object>> toMapInput() {
        return map(IDataRow::toMap, getMeta());
    }

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