/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset;

import io.nop.dataset.record.IRecordResourceMeta;
import io.nop.commons.type.StdDataType;

import java.util.List;

public interface IDataSetMeta extends IRecordResourceMeta {

    /**
     * 列名是否大小写敏感
     */
    boolean isCaseSensitive();

    /**
     * 得到列数。下标从0开始
     */
    int getFieldCount();

    String getFieldName(int index);

    /**
     * Record有可能由多个表关联得到。这里返回指定字段的来源表。如果一个字段有多个来源，则这里返回null
     *
     * @param index 下标从0开始
     * @return 数据库表名
     */
    default String getFieldOwnerEntityName(int index) {
        return null;
    }

    default String getSourceFieldName(int index) {
        return getFieldName(index);
    }

    int getFieldIndex(String colName);

    boolean hasField(String name);

    StdDataType getFieldStdType(int index);

    List<? extends IDataFieldMeta> getFieldMetas();
}