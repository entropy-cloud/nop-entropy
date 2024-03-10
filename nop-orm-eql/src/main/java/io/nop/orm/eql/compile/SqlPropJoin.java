/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

public class SqlPropJoin {
    private SqlSingleTableSource left;
    private SqlJoinType joinType;

    private SqlSingleTableSource right;

    // 主外键关联条件
    private SqlExpr condition;

    /**
     * 在from段明确指定了join条件，例如 from UserInfo o left join o.dept
     */
    private boolean explicit;

    private int refCount; // 有多少属性表达式用到了这个join对象

    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }

    public SqlJoinType getJoinType() {
        return joinType;
    }

    public void setJoinType(SqlJoinType joinType) {
        this.joinType = joinType;
    }

    public SqlSingleTableSource getLeft() {
        return left;
    }

    public void setLeft(SqlSingleTableSource left) {
        this.left = left;
    }

    public SqlSingleTableSource getRight() {
        return right;
    }

    public void setRight(SqlSingleTableSource right) {
        this.right = right;
    }

    public SqlExpr getCondition() {
        return condition;
    }

    public void setCondition(SqlExpr condition) {
        this.condition = condition;
    }

    public void addConditionFilter(SqlExpr filter) {
        if (filter == null)
            return;

        if (this.getCondition() == null) {
            this.setCondition(filter);
        } else {
            SqlBinaryExpr and = new SqlBinaryExpr();
            and.setOperator(SqlOperator.AND);
            and.setLeft(getCondition());
            and.setRight(filter);
            setCondition(and);
        }
    }

    public int getRefCount() {
        return refCount;
    }

    public void incRef() {
        refCount++;
    }

    public ISqlSelectionMeta getLeftTable() {
        return left.getTableName().getResolvedTableMeta();
    }

    public ISqlSelectionMeta getRightTable() {
        return right.getTableName().getResolvedTableMeta();
    }
}