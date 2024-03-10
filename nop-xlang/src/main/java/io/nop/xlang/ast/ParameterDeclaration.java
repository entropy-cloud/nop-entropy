/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.xlang.ast._gen._ParameterDeclaration;

public class ParameterDeclaration extends _ParameterDeclaration {
    private Object defaultValue;

    public static ParameterDeclaration valueOf(Identifier name) {
        Guard.notNull(name, "name");

        ParameterDeclaration node = new ParameterDeclaration();
        node.setLocation(name.getLocation());
        node.setName(name);
        return node;
    }

    public boolean isOptional() {
        return getInitializer() != null;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
}