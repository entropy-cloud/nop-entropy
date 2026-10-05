/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlJoinTableSource;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

public class SqlJoinTableSource extends _SqlJoinTableSource {

    /**
     * Plan 2307 V2：把被 ANTLR 误解析的 {@code CROSS JOIN} 还原回来。
     *
     * <p>缺陷机制：<code>CROSS</code> 登记在 {@code unreservedWord_}（允许 {@code cross} 作标识符），
     * 而 {@code sqlSingleTableSource} 的 alias 可选。对 {@code from a cross join b}，
     * 「alias=cross + 裸 JOIN」与「CROSS JOIN」两条路径都能完整解析，
     * ANTLR 的 ALL(*) 预测此时取最小备选号，于是先吃 alias，得到
     * {@code SqlJoinTableSource(joinType=JOIN, left=Single(a, alias=cross), right=b)}。
     *
     * <p>危害：别名被改成 <code>cross</code> 后，源查询里对 {@code a} 的任何引用
     * （如 {@code where a.id = b.id}）都会报 <code>unknown-column-name</code>——
     * 一条本应合法的查询被静默改变语义。仅在 grammar 补 {@code crossJoin_} 不够：
     * 实测那样做仍然是 alias 胜出（两备选依然都能完整解析）。
     *
     * <p>为何在此处（而非 transform 或 printer）还原：{@code normalize()} 由生成的
     * build visitor 在属性装配完成后立即调用，早于任何作用域注册。
     * 若等到 transform 阶段才改，{@code addAliasToScope} 已经用错误别名注册了 scope name。
     *
     * <p>判据收紧到「别名文本恰为 cross（忽略大小写）且 join 无条件」，因此
     * {@code from a cross}（把 cross 当别名）与 {@code from a cross join b on cond}
     * （带 on 的普通 join）都不受影响。
     *
     * <p>链式写法需要额外清理右操作数：{@code from a cross join b cross join c} 中，
     * ANTLR 先把第一个 {@code cross} 吃成 a 的别名、再把第二个吃成 b 的别名，
     * 于是第一层是「alias=cross + JOIN」被本方法纠正，而 <strong>右操作数 b 上残留的
     * {@code cross} 别名</strong>需要一并清除——那个 token 本是 CROSS JOIN 的关键字。
     * 不清除会输出 {@code a cross join b as cross join c}（`as cross` 是幻觉别名）。
     *
     * <p>已知的取舍：{@code from a cross join b cross}（真想把 b 别名为 cross）会退化成
     * b 无别名。该写法要求「CROSS JOIN 之后紧跟的表再被别名为 cross」，与链式 CROSS JOIN
     * 在词法上不可区分，而后者是合法常用形态、前者几无实际意义，故优先保证后者正确。
     */
    @Override
    public void normalize() {
        super.normalize();
        if (getJoinType() == SqlJoinType.JOIN && getCondition() == null
                && getLeft() instanceof SqlSingleTableSource) {
            SqlSingleTableSource left = (SqlSingleTableSource) getLeft();
            SqlAlias alias = left.getAlias();
            if (alias != null && "cross".equalsIgnoreCase(alias.getAlias())) {
                left.setAlias(null);
                setJoinType(SqlJoinType.CROSS_JOIN);
                // 链式 CROSS JOIN：右操作数上可能残留同一个 CROSS token 被当成别名
                if (getRight() instanceof SqlSingleTableSource) {
                    SqlSingleTableSource right = (SqlSingleTableSource) getRight();
                    SqlAlias rightAlias = right.getAlias();
                    if (rightAlias != null && "cross".equalsIgnoreCase(rightAlias.getAlias())) {
                        right.setAlias(null);
                    }
                }
            }
        }
    }

    @Override
    public ISqlSelectionMeta getResolvedTableMeta() {
        return getLeft().getResolvedTableMeta();
    }

    @Override
    public SqlSelect getSourceSelect() {
        return null;
    }

    public void addConditionFilter(SqlExpr filter) {
        if (filter == null)
            return;

        if (this.getCondition() == null) {
            this.setCondition(filter);
        } else {
            SqlBinaryExpr and = new SqlBinaryExpr();
            and.setOperator(SqlOperator.AND);
            SqlExpr cond = getCondition();
            cond.detach();
            and.setLeft(cond);
            and.setRight(filter);
            setCondition(and);
        }
    }
}
