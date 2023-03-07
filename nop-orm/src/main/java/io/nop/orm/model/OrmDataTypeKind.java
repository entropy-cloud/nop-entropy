/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

public enum OrmDataTypeKind {
    ENTITY, COLUMN, TO_ONE_RELATION, TO_MANY_RELATION, ALIAS, COMPUTE, ID, COMPONENT,

    /**
     * 子查询返回的结果数据集
     */
    SELECT_RESULT,
    /**
     * 表达式或者函数的返回结果
     */
    EXPR;

    public boolean isCompute() {
        return this == COMPUTE;
    }

    public boolean isColumn() {
        return this == COLUMN;
    }

    public boolean isId() {
        return this == ID;
    }

    public boolean isRelation() {
        return this == TO_ONE_RELATION || this == TO_MANY_RELATION;
    }

    public boolean isToOneRelation() {
        return this == TO_ONE_RELATION;
    }

    public boolean isToManyRelation() {
        return this == TO_MANY_RELATION;
    }

    public boolean isAlias() {
        return this == ALIAS;
    }

    public boolean isComponent() {
        return this == COMPONENT;
    }
}
