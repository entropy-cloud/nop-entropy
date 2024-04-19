/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset;


import io.nop.commons.util.CollectionHelper;
import io.nop.dataset.binder.IDataParameters;

import java.util.Map;

/**
 * 数据行
 */
public interface IDataRow extends IDataParameters {
    IDataSetMeta getMeta();

    int getFieldCount();

    /**
     * 是否脱离后端存储，可以保存在内存中
     *
     * @return
     */
    boolean isDetached();

    boolean isReadonly();

    void setReadonly(boolean readonly);

    Object[] getFieldValues();

    IDataRow toDetachedDataRow();

    default Map<String, Object> toMap() {
        Map<String, Object> map = CollectionHelper.newLinkedHashMap(getFieldCount());
        for (int i = 0, n = getFieldCount(); i < n; i++) {
            map.put(getMeta().getFieldName(i), getObject(i));
        }
        return map;
    }
}