/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;

/**
 * IEntityModel/IEntityPropModel的共同基类，用于在eql语句的语义分析中标识具体返回字段所对应的对象类型
 */
public interface IOrmDataType {

    StdDataType getStdDataType();

    default StdSqlType getStdSqlType() {
        return StdSqlType.ANY;
    }

    OrmDataTypeKind getKind();
}