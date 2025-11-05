/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableSource;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ARG_ALIAS;
import static io.nop.orm.eql.OrmEqlErrors.ARG_TABLE1;
import static io.nop.orm.eql.OrmEqlErrors.ARG_TABLE2;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DUPLICATE_TABLE_ALIAS;

/**
 * 保存对SqlTableSource的别名映射关系
 *
 * @author canonical_entropy@163.com
 */
public class SqlTableScope implements Serializable {

    private static final long serialVersionUID = 6799756585478430221L;

    private final Map<String, SqlTableSource> aliasToTables = new HashMap<>();

    private final SqlTableScope parent;

    /**
     * scope所在对象，用于调试诊断
     */
    private final EqlASTNode sqlObject;

    public SqlTableScope(EqlASTNode sqlObject, SqlTableScope parent) {
        this.sqlObject = sqlObject;
        this.parent = parent;
    }

    public EqlASTNode getSqlObject() {
        return sqlObject;
    }

    public SqlTableScope getParent() {
        return parent;
    }

    public SqlTableSource getTableByAlias(String alias) {
        SqlTableSource table = aliasToTables.get(alias);
        if (table == null && parent != null)
            table = parent.getTableByAlias(alias);
        return table;
    }

    public void addTable(String alias, SqlTableSource table) {
        SqlTableSource oldTable = this.aliasToTables.put(alias, table);
        if (oldTable != null && oldTable != table) {
            if (oldTable instanceof SqlSubqueryTableSource) {
                if (((SqlSubqueryTableSource) oldTable).isSameWithClause(table))
                    return;
            }
            throw new NopEvalException(ERR_EQL_DUPLICATE_TABLE_ALIAS).param(ARG_ALIAS, alias).param(ARG_TABLE2, table)
                    .param(ARG_TABLE1, oldTable);
        }
    }
}