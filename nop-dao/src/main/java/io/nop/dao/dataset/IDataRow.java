/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dataset;

import io.nop.core.lang.sql.binder.IDataParameters;

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
}