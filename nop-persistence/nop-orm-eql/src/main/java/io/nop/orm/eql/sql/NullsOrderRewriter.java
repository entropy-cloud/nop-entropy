/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.sql;

import io.nop.core.lang.sql.SQL;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Plan 2309 Phase 1：ORDER BY 空序的方言无关改写单元。
 *
 * <p><b>为什么存在</b>：EQL 通道（{@link AstToSqlGenerator}）与 bean 通道
 * （{@code GenSqlHelper} / {@code DaoQueryHelper}）都需要在目标方言不支持原生
 * {@code NULLS FIRST/LAST} 时把空序要求改写成等价的排序键。改写的方向与取值逻辑
 * 是<b>纯粹由 (asc, nullsFirst) 决定的算法规格</b>，与 AST、与通道无关，因此
 * 必须只有一份实现——两份实现迟早会漂移，而漂移表现为"SQL 仍可执行、行序静默相反"。
 *
 * <p><b>改写规则</b>（语义：先按「是否为 NULL」分组，再按原列排序）：
 * <ul>
 *   <li>CASE 键方向 = {@code asc} 的<b>相反</b>方向（先分组，故用反序把 null 组排到目标位置）</li>
 *   <li>{@code then} 值 = {@code nullsFirst == asc ? "1" : "0"}，{@code else} 值取反</li>
 *   <li>随后输出原排序列 + {@code asc} 方向</li>
 * </ul>
 * 四种组合已由真实数据库执行验证（见 {@code TestEqlNullsOrderEndToEnd} 与
 * {@code TestEqlNullsOrderMatrix}），并经「prepend 到列表头」变异测试证明可判别。
 *
 * <p><b>不做什么</b>：不读任何方言能力位（由调用方二选一），不做「请求等于方言缺省
 * 则省略子句」的优化（单个布尔无法表达各方言真实空序模型，见 design §3.8）。
 *
 * <p><b>本单元不判定窗口与 DISTINCT</b>：那两项需要 AST（{@code OVER(...)} 嵌套、
 * {@code SELECT DISTINCT} 上下文），bean 通道无 AST、不可判定，故仍由 EQL 通道
 * 自行 fail-fast。本单元只暴露表达式可复制性检查。
 */
public final class NullsOrderRewriter {

    private NullsOrderRewriter() {
    }

    /**
     * 原生直出：{@code <col> <dir> [nulls first|nulls last]}。
     *
     * <p>{@code nullsFirst == null} 时不输出空序修饰——这保证「未请求空序的既有查询
     * 产物逐字不变」。
     */
    public static void appendNative(SQL.SqlBuilder out, Consumer<SQL.SqlBuilder> column,
                                    boolean asc, Boolean nullsFirst) {
        column.accept(out);
        out.append(asc ? " asc" : " desc");
        if (nullsFirst != null)
            out.append(nullsFirst ? " nulls first" : " nulls last");
    }

    /**
     * CASE WHEN 改写：{@code case when <col> is null then <v1> else <v2> end <反方向>, <col> <方向>}。
     *
     * <p>调用方必须先经 {@link #duplicationBlocker} 确认表达式可复制；本方法不重复校验，
     * 以免同一条规则出现两份实现。
     *
     * @param asc <b>翻转之后</b>的排序方向。{@code SQL.SqlBuilder.reverseOrderField} 会同时
     *            翻转 {@code isDesc} 与 {@code nullsFirst}，把翻转后的组合传进来即可自动得到
     *            正确的 CASE 键方向（见 plan 2309 D6）
     */
    public static void appendCaseRewrite(SQL.SqlBuilder out, Consumer<SQL.SqlBuilder> column,
                                         boolean asc, boolean nullsFirst) {
        out.append("case when ");
        column.accept(out);
        out.append(" is null then ");
        out.append(nullsFirst == asc ? "1" : "0");
        out.append(" else ");
        out.append(nullsFirst == asc ? "0" : "1");
        out.append(" end");
        // CASE 键方向与原方向相反：先按 nullness 分组，再用反序把目标组排到位
        out.append(asc ? " desc" : " asc");
        out.append(", ");
        column.accept(out);
        out.append(asc ? " asc" : " desc");
    }

    /**
     * 表达式能否被安全复制（改写会把排序表达式写两遍）。
     *
     * <p>EQL 通道额外要求 AST 节点是 {@code SqlColumnName}（纯列引用）；bean 通道没有 AST，
     * 只能做文本级检查，故本方法覆盖两者共有的红线：<b>含绑定参数标记 {@code ?}</b>。
     * 绑定参数重复出现会破坏参数顺序，改写后必然出错。
     *
     * @return 阻断原因；{@link Optional#empty()} 表示可安全复制
     */
    public static Optional<String> duplicationBlocker(String columnSql) {
        if (columnSql == null || columnSql.isEmpty())
            return Optional.of("empty-expression");
        if (columnSql.indexOf('?') >= 0)
            return Optional.of("bind-parameter-not-duplicable");
        return Optional.empty();
    }
}