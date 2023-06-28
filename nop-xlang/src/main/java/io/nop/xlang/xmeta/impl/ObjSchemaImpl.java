/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.xlang.xdef.SchemaKind;
import io.nop.xlang.xmeta.impl._gen._ObjSchemaImpl;

public class ObjSchemaImpl extends _ObjSchemaImpl implements IObjSchemaImpl {


    @Override
    public boolean isPropInherited(String name) {
        if (getRefSchema() != null)
            return getRefSchema().hasProp(name);
        return false;
    }

    @NoReflection
    public boolean isAbstract() {
        return Boolean.TRUE.equals(getAbstract());
    }

    @NoReflection
    public boolean isInterface() {
        return Boolean.TRUE.equals(getInterface());
    }

    @NoReflection
    public boolean isRefResolved() {
        return Boolean.TRUE.equals(getRefResolved());
    }

    @Override
    public SchemaKind getSchemaKind() {
        return SchemaKind.OBJ;
    }

}