/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import java.io.Serializable;

/**
 * 顶层 ORDER BY 排序项到编译产物投影列（{@link ICompiledSql#getFieldMetas()}）的序数映射条目
 * （plan 2321，设计文档 §3.1）。
 *
 * <p>每个顶层排序项对应一个条目。{@link #getOrdinal()} 返回该排序键在投影中的下标；
 * 排序键未投影时无有效映射，序数为 {@link #ORDINAL_NOT_PROJECTED}（-1）——消费方据此
 * 显式失败，不得静默降级为无序归并。</p>
 *
 * <p>本类为编译产物静态属性，随 SQL 文本一起进 QueryPlanCache，不携带任何执行期状态。</p>
 */
public class SqlOrderByOrdinal implements Serializable {
    private static final long serialVersionUID = 6823573120816347292L;

    /**
     * 排序键未投影（无映射项）时的序数哨兵值。
     */
    public static final int ORDINAL_NOT_PROJECTED = -1;

    private final int ordinal;
    private final boolean asc;
    private final Boolean nullsFirst;

    public SqlOrderByOrdinal(int ordinal, boolean asc, Boolean nullsFirst) {
        this.ordinal = ordinal;
        this.asc = asc;
        this.nullsFirst = nullsFirst;
    }

    /**
     * 排序键在 fieldMetas 中的序数。{@link #ORDINAL_NOT_PROJECTED} 表示排序键未投影。
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * 排序键是否已投影（存在有效映射）。
     */
    public boolean isProjected() {
        return ordinal >= 0;
    }

    public boolean isAsc() {
        return asc;
    }

    /**
     * NULLS FIRST/LAST 三态：null 表示未声明（由归并器取确定性缺省，见消费方约定）。
     */
    public Boolean getNullsFirst() {
        return nullsFirst;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("SqlOrderByOrdinal{ordinal=");
        sb.append(ordinal).append(",asc=").append(asc).append(",nullsFirst=").append(nullsFirst).append('}');
        return sb.toString();
    }
}
