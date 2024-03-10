/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast._gen._QualifiedName;

public class QualifiedName extends _QualifiedName implements MetaValue {
    private boolean resolved;

    public static QualifiedName valueOf(Identifier id) {
        QualifiedName ret = new QualifiedName();
        ret.setLocation(id.getLocation());
        ret.setName(id.getName());
        return ret;
    }

    private void getFullName(StringBuilder sb) {
        sb.append(this.getName());
        if (next != null) {
            sb.append('.');
            next.getFullName(sb);
        }
    }

    public String getFullName() {
        if (getNext() == null)
            return getName();
        StringBuilder sb = new StringBuilder();
        getFullName(sb);
        return sb.toString();
    }

    public static QualifiedName valueOf(SourceLocation loc, String name) {
        Guard.notNull(name, "name is null");

        QualifiedName node = new QualifiedName();
        node.setLocation(loc);
        node.setName(name);
        return node;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        checkAllowChange();
        this.resolved = resolved;
    }

    public String getSimpleName() {
        if (next != null)
            return next.getSimpleName();
        return StringHelper.lastPart(getName(), '.');
    }
}