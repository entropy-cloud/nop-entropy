/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.xlang.ast._gen._PropertyBinding;

public class PropertyBinding extends _PropertyBinding {

    public Identifier makeIdentifier() {
        Identifier id = getIdentifier();
        if (id == null) {
            id = Identifier.valueOf(getLocation(), getPropName());
            setIdentifier(id);
        }
        return id;
    }

    public void normalize() {
        if (propName == null) {
            if (identifier != null)
                propName = identifier.getName();
        } else if (identifier == null) {
            if (propName != null) {
                setIdentifier(Identifier.valueOf(getLocation(), propName));
            }
        }
    }
}