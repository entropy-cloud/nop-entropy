/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

public enum SchemaKind {
    // 简单值，对应于ISimpleValueMeta
    SIMPLE,

    LIST,

    MAP,

    // 由props组成
    OBJ,

    // 由多个可选类型构成。如果是从多个对象类型中选择，则需要指定subTypeProp
    UNION;

    public boolean isList() {
        return LIST == this;
    }

    public boolean isSimple() {
        return SIMPLE == this;
    }

    public boolean isObj() {
        return OBJ == this;
    }

    public boolean isUnion() {
        return UNION == this;
    }

    public boolean isMap() {
        return MAP == this;
    }
}
