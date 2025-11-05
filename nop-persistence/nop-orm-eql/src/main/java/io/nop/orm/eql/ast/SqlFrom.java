/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlFrom;
import io.nop.orm.eql.compile.SqlTableScope;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SqlFrom extends _SqlFrom {
    private SqlTableScope tableScope;

    public SqlTableScope getTableScope() {
        return tableScope;
    }

    public void setTableScope(SqlTableScope tableScope) {
        this.tableScope = tableScope;
    }

    public SqlTableSource getFirstTableSource() {
        SqlTableSource source = getTableSources().get(0);
        return getFirstTableSource(source);
    }

    SqlTableSource getFirstTableSource(SqlTableSource source) {
        if (source instanceof SqlJoinTableSource) {
            SqlJoinTableSource join = (SqlJoinTableSource) source;
            return getFirstTableSource(join.getLeft());
        }
        return source;
    }

    public void forEachTableSource(Consumer<SqlTableSource> consumer) {
        for (SqlTableSource source : getTableSources()) {
            source.forEachTableSource(consumer);
        }
    }

    /**
     * from子句中以及所有propJoin中包含的对实体表的引用
     */
    public List<SqlSingleTableSource> getEntitySources() {
        List<SqlSingleTableSource> entitySources = new ArrayList<>();
        forEachTableSource(source -> {
            if (source.isEntityTableSource()) {
                entitySources.add((SqlSingleTableSource) source);
            }
        });
        return entitySources;
    }

    public void forEachSingleTableSource(Consumer<SqlSingleTableSource> consumer) {
        for (SqlTableSource source : getTableSources()) {
            if (source.isEntityTableSource()) {
                consumer.accept((SqlSingleTableSource) source);
            }
        }
    }
}