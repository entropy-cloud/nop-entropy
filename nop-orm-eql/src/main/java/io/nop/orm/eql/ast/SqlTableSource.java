/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.orm.eql.ast._gen._SqlTableSource;
import io.nop.orm.eql.compile.SqlPropJoin;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class SqlTableSource extends _SqlTableSource {
    private Map<String, SqlPropJoin> propJoins;

    public boolean isGeneratedAlias() {
        return false;
    }

    public SqlAlias getAlias() {
        return null;
    }

    public abstract SqlSelect getSourceSelect();

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        super.copyExtFieldsTo(node);
        SqlTableSource source = (SqlTableSource) node;
        source.propJoins = propJoins == null ? null : new HashMap<>(propJoins);
    }

    public String getAliasName() {
        SqlAlias alias = getAlias();
        return alias == null ? null : alias.getAlias();
    }

    public SqlPropJoin getPropJoin(String propName) {
        if (propJoins == null)
            return null;
        return propJoins.get(propName);
    }

    public void addPropJoin(String propName, SqlPropJoin join) {
        if (propJoins == null) {
            propJoins = new LinkedHashMap<>();
        }
        propJoins.put(propName, join);
    }

    public Map<String, SqlPropJoin> getPropJoins() {
        return propJoins;
    }

    public abstract ISqlTableMeta getResolvedTableMeta();

    public void forEachTableSource(Consumer<SqlTableSource> consumer) {
        consumer.accept(this);
        if (propJoins != null) {
            for (SqlPropJoin propJoin : propJoins.values()) {
                propJoin.getRight().forEachTableSource(consumer);
            }
        }
        if (this instanceof SqlJoinTableSource) {
            SqlJoinTableSource join = (SqlJoinTableSource) this;
            join.getLeft().forEachTableSource(consumer);
            join.getRight().forEachTableSource(consumer);
        }
    }

    public boolean isEntityTableSource() {
        if (this instanceof SqlSingleTableSource) {
            return ((SqlSingleTableSource) this).getResolvedTableMeta() instanceof EntityTableMeta;
        }
        return false;
    }
}