/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.sql;

import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2309 Phase 1：{@link NullsOrderRewriter} 的直接单测。
 *
 * <p>断言的是<b>改写产物的方向与取值</b>，不是"含不含 case when"——后者无法区分
 * 方向写反的改写（SQL 依然可执行、依然含 case when，只有行序会静默相反）。
 *
 * <p>行序正确性由 {@code TestEqlNullsOrderEndToEnd} / {@code TestEqlNullsOrderMatrix}
 * 在真实数据库上验证；本类负责把算法规格钉死，使 EQL 通道与 bean 通道共用实现时
 * 任何一侧的偏差都能在此被看到。
 */
public class TestNullsOrderRewriter {

    static String render(boolean asc, Boolean nullsFirst) {
        SQL.SqlBuilder sb = SQL.begin();
        NullsOrderRewriter.appendCaseRewrite(sb, out -> out.append("AGE"), asc, nullsFirst);
        return sb.getText().replaceAll("\\s+", " ").trim();
    }

    static String nativeOut(boolean asc, Boolean nullsFirst) {
        SQL.SqlBuilder sb = SQL.begin();
        NullsOrderRewriter.appendNative(sb, out -> out.append("AGE"), asc, nullsFirst);
        return sb.getText().replaceAll("\\s+", " ").trim();
    }

    // ---- 四种方向的 CASE 取值与键方向 ----
    //
    // 规则：CASE 键方向 = asc 的反方向；then 值 = (nullsFirst == asc) ? 1 : 0

    @Test
    public void testAscNullsFirst() {
        assertEquals("case when AGE is null then 1 else 0 end desc, AGE asc", render(true, true));
    }

    @Test
    public void testAscNullsLast() {
        assertEquals("case when AGE is null then 0 else 1 end desc, AGE asc", render(true, false));
    }

    @Test
    public void testDescNullsFirst() {
        assertEquals("case when AGE is null then 0 else 1 end asc, AGE desc", render(false, true));
    }

    @Test
    public void testDescNullsLast() {
        assertEquals("case when AGE is null then 1 else 0 end asc, AGE desc", render(false, false));
    }

    /**
     * 语义交叉校验：把四种产物按「键方向」排序后，NULL 组必须排在请求的一侧。
     *
     * <p>这是对上面四个字面量断言的独立补充——若有人把 then/else 或键方向整体写反，
     * 四个硬编码期望值会一起变红，但本断言能从语义侧指出错在哪一侧。
     */
    @Test
    public void testNullGroupLandsOnRequestedSideForAllFourCombinations() {
        // ASC + FIRST：键 DESC，null 组键值 1（大于 0）⇒ 排在前
        assertTrue(nullRankFirst(render(true, true)), "ASC+NULLS FIRST");
        // ASC + LAST：键 DESC，null 组键值 0 ⇒ 排在后
        assertFalse(nullRankFirst(render(true, false)), "ASC+NULLS LAST");
        // DESC + FIRST：键 ASC，null 组键值 0 ⇒ 排在前
        assertTrue(nullRankFirst(render(false, true)), "DESC+NULLS FIRST");
        // DESC + LAST：键 ASC，null 组键值 1 ⇒ 排在后
        assertFalse(nullRankFirst(render(false, false)), "DESC+NULLS LAST");
    }

    /** 解析产物的 CASE 键方向与 null 组键值，判断 null 组是否排在非 null 组之前 */
    static boolean nullRankFirst(String rendered) {
        boolean keyDesc = rendered.contains("end desc");
        int thenValue = Integer.parseInt(
                rendered.substring(rendered.indexOf("then ") + 5, rendered.indexOf(" else ")).trim());
        // 键 DESC 时大值在前；键 ASC 时小值在前
        return keyDesc ? thenValue > 0 : thenValue < 1;
    }

    // ---- 原生直出 ----

    @Test
    public void testNativeOmitsNullsClauseWhenNotRequested() {
        assertEquals("AGE asc", nativeOut(true, null));
        assertEquals("AGE desc", nativeOut(false, null));
    }

    @Test
    public void testNativeEmitsNullsClause() {
        assertEquals("AGE asc nulls first", nativeOut(true, true));
        assertEquals("AGE asc nulls last", nativeOut(true, false));
        assertEquals("AGE desc nulls first", nativeOut(false, true));
        assertEquals("AGE desc nulls last", nativeOut(false, false));
    }

    /**
     * 「未请求空序时产物逐字不变」的回归锚点：这是 2308 Closure Gate 之一，
     * 本单元是所有通道的共同出口，故在此也钉一遍。
     */
    @Test
    public void testUnrequestedNullsProducesNoExtraText() {
        assertFalse(nativeOut(true, null).contains("nulls"));
        assertFalse(nativeOut(false, null).contains("case"));
    }

    // ---- 可复制性判定 ----

    @Test
    public void testPlainColumnIsDuplicable() {
        assertTrue(NullsOrderRewriter.duplicationBlocker("AGE").isEmpty());
        assertTrue(NullsOrderRewriter.duplicationBlocker("t0.AGE").isEmpty());
        assertTrue(NullsOrderRewriter.duplicationBlocker("SUM(t0.AGE)").isEmpty());
    }

    @Test
    public void testBindParameterBlocksDuplication() {
        // 绑定参数写两遍会破坏参数顺序
        assertEquals("bind-parameter-not-duplicable",
                NullsOrderRewriter.duplicationBlocker("case when X = ? then 1 else 0 end").orElse(null));
    }

    @Test
    public void testEmptyExpressionBlocksDuplication() {
        assertEquals("empty-expression",
                NullsOrderRewriter.duplicationBlocker("").orElse(null));
        assertEquals("empty-expression",
                NullsOrderRewriter.duplicationBlocker(null).orElse(null));
    }

    // ---- 反转语义（D6） ----

    /**
     * 反转排序时，{@code SQL.SqlBuilder.reverseOrderField} 同时翻转 isDesc 与 nullsFirst。
     * 因此把翻转后的组合原样喂进来，CASE 键方向必须自动跟着翻转——
     * 否则 {@code findNext} 会静默返回相反的空序。
     */
    @Test
    public void testReversedOrderProducesReversedNullPlacement() {
        String forward = render(true, false);   // ASC + NULLS LAST
        String reversed = render(false, true);  // 等价于 DESC + NULLS FIRST
        assertEquals("case when AGE is null then 0 else 1 end asc, AGE desc", reversed);
        assertFalse(forward.equals(reversed), "反转后产物不应与正序相同");
    }

    @Test
    public void testReversedPairIsSelfConsistent() {
        // (asc=true,nullsFirst=false) 与 (asc=false,nullsFirst=true) 是「反转」对：
        // 二者的 null 相对位置必须相反
        assertTrue(nullRankFirst(render(false, true)));
        assertFalse(nullRankFirst(render(true, false)));
    }

    // ---- 渲染回调被调用两次 ----

    @Test
    public void testColumnRendererInvokedExactlyTwice() {
        int[] calls = {0};
        SQL.SqlBuilder sb = SQL.begin();
        NullsOrderRewriter.appendCaseRewrite(sb, out -> {
            calls[0]++;
            out.append("AGE");
        }, true, true);
        assertEquals(2, calls[0], "改写必须把排序列写两遍（CASE 键一次、原序列一次）");
    }

    @Test
    public void testNativeRendererInvokedExactlyOnce() {
        int[] calls = {0};
        SQL.SqlBuilder sb = SQL.begin();
        NullsOrderRewriter.appendNative(sb, out -> {
            calls[0]++;
            out.append("AGE");
        }, true, true);
        assertEquals(1, calls[0]);
    }

    // ---- Optional 契约 ----

    @Test
    public void testDuplicationBlockerReturnsEmptyOptionalForDuplicable() {
        Optional<String> blocker = NullsOrderRewriter.duplicationBlocker("AGE");
        assertTrue(blocker.isEmpty());
    }
}