/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.enums;

public enum SqlJoinType {
    JOIN("join"), LEFT_JOIN("left join"), RIGHT_JOIN("right join"), FULL_JOIN("full join"),
    /**
     * Plan 2307 V2：<code>CROSS JOIN</code>。
     *
     * <p>该类型<strong>不由 grammar 直接产生</strong>——<code>CROSS</code> 登记在
     * <code>unreservedWord_</code>（允许 <code>cross</code> 作标识符），而
     * <code>sqlSingleTableSource</code> 的 alias 是可选的，ANTLR 的 ALL(*) 预测在
     * 「alias=cross」与「crossJoin_」两条都能完整解析时一律取最小备选号（即先吃 alias）。
     * 故 <code>from a cross join b</code> 会被切成 <code>alias=cross</code> + 裸 <code>JOIN</code>。
     *
     * <p>该误解析由 {@code SqlJoinTableSource#normalize()} 在建树时还原：
     * 见到「左表别名恰为 cross 且是无条件 JOIN」即判定为 CROSS JOIN 并复位别名。
     */
    CROSS_JOIN("cross join");

    private final String text;

    SqlJoinType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
