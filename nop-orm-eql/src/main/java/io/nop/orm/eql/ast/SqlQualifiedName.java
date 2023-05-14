/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.commons.util.objects.PropPath;
import io.nop.core.lang.ast.ASTNode;
import io.nop.orm.eql.ast._gen._SqlQualifiedName;

public class SqlQualifiedName extends _SqlQualifiedName {
    private SqlTableSource resolvedSource;

    public SqlTableSource getResolvedSource() {
        return resolvedSource;
    }

    public void setResolvedSource(SqlTableSource resolvedSource) {
        this.resolvedSource = resolvedSource;
    }

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        super.copyExtFieldsTo(node);
        SqlQualifiedName name = (SqlQualifiedName) node;
        name.resolvedSource = resolvedSource;
    }

    public PropPath toPropPath() {
        return toPropPath(null);
    }

    public PropPath toPropPath(PropPath next) {
        return new PropPath(getName(), getNext() == null ? next : getNext().toPropPath(next));
    }

    public String getResolvedName() {
        if (resolvedSource != null)
            return resolvedSource.getAliasName();
        return getFullName();
    }

    public String getFullName() {
        if (getNext() == null)
            return getName();
        StringBuilder sb = new StringBuilder();
        getFullName(sb);
        return sb.toString();
    }

    public void getFullName(StringBuilder sb) {
        if (getNext() == null) {
            sb.append(getName());
        } else {
            sb.append(getName());
            sb.append('.');
            getNext().getFullName(sb);
        }
    }
}
