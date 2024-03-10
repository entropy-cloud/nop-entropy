/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

import java.util.Map;

/**
 * 包含多个字段的结构体类型
 */
public interface IStructType extends IGenericType {

    Map<String, IGenericType> getFieldTypes();

    IGenericType getExtFieldType();

    default IGenericType getFieldType(String fieldName) {
        IGenericType type = getFieldTypes().get(fieldName);
        if (type == null) {
            type = getExtFieldType();
        }
        return type;
    }
}
