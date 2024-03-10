/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset;

import io.nop.commons.type.StdDataType;

import java.io.Serializable;

public interface IDataFieldMeta extends Serializable {
    String getFieldName();

    String getSourceFieldName();

    /**
     * 列所属的数据库表的表名
     */
    String getFieldOwnerEntityName();

    StdDataType getFieldStdType();

    default boolean isComputed(){
        return false;
    }
}