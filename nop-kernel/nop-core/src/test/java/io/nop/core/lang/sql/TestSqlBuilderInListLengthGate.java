/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.sql;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.CoreConfigs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.nop.core.CoreErrors.ERR_CORE_SQL_IN_LIST_TOO_LARGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10b（WI10a 设计定稿 2026-10-03）：IN 列表长度门禁——`SqlBuilder` 集合展开路径契约测试。
 *
 * <p>挂载点裁定（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi10b-phase0-adjudications.md` §4.2）：
 * `SQL.SqlBuilder#spreadParams` 全局拦（全仓调用面 = `paramCollection` 链 +
 * `CollectSqlOutput` 直调，`paramCollection`-only / `FilterBeanToSQLTransformer`-only 均不完备）；
 * 计数口径 = Collection 顶层元素个数（行组按 1 计）；空集合的 `(NULL)` 既有行为保留在门禁之前。
 * 阈值同源 `nop.core.sql.in-list-max-size`（与 EQL 路径同一配置项），错误码
 * `nop.err.core.sql.in-list-too-large`，超限显式报错、不截断、不自动改写。
 */
public class TestSqlBuilderInListLengthGate {
    static final int THRESHOLD = 1000;

    @AfterAll
    public static void restoreConfig() {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE, THRESHOLD);
    }

    static List<Integer> numbers(int count) {
        List<Integer> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++)
            list.add(i);
        return list;
    }

    /**
     * 端到端（Minimum Rules #22/#23）：从 filter bean 的 `in` 操作符入口
     * （FilterBeanToSQLTransformer#visitCompareOp 的 FILTER_OP_IN → SqlBuilder.in → spreadParams）
     * 完整走通到报错，断言具体错误码与 itemCount/maxItems param。
     */
    @Test
    public void testFilterBeanInOverThresholdThrowsEndToEnd() {
        SQL.SqlBuilder sb = SQL.begin();
        FilterBeanToSQLTransformer transformer = new FilterBeanToSQLTransformer(sb, false, "o");
        NopException e = assertThrows(NopException.class,
                () -> transformer.visit(FilterBeans.in("id", numbers(THRESHOLD + 1)), null));
        assertEquals(ERR_CORE_SQL_IN_LIST_TOO_LARGE.getErrorCode(), e.getErrorCode());
        assertEquals(String.valueOf(THRESHOLD + 1), String.valueOf(e.getParam("itemCount")));
        assertEquals(String.valueOf(THRESHOLD), String.valueOf(e.getParam("maxItems")));
    }

    /**
     * NOT IN（FILTER_OP_NOT_IN）与 IN 落同一 `SqlBuilder.in` 消费点，同阈值同错误码。
     */
    @Test
    public void testFilterBeanNotInOverThresholdSameError() {
        SQL.SqlBuilder sb = SQL.begin();
        FilterBeanToSQLTransformer transformer = new FilterBeanToSQLTransformer(sb, false, "o");
        NopException e = assertThrows(NopException.class,
                () -> transformer.visit(FilterBeans.notIn("id", numbers(THRESHOLD + 1)), null));
        assertEquals(ERR_CORE_SQL_IN_LIST_TOO_LARGE.getErrorCode(), e.getErrorCode());
    }

    /**
     * 直调 `SqlBuilder#in`（绕过 filter 层的用户代码面）同样被门禁拦截
     * ——证明挂载点在 `spreadParams` 全局入口而非 filter 层。
     */
    @Test
    public void testSqlBuilderInDirectCallOverThresholdThrows() {
        SQL.SqlBuilder sb = SQL.begin();
        NopException e = assertThrows(NopException.class,
                () -> sb.in(null, "o.id", (Object) numbers(THRESHOLD + 1)));
        assertEquals(ERR_CORE_SQL_IN_LIST_TOO_LARGE.getErrorCode(), e.getErrorCode());
    }

    /**
     * 阈值内正常：`in` 集合展开为 `?` 参数化列表，marker 个数 == 元素个数。
     */
    @Test
    public void testWithinThresholdExpandsMarkers() {
        SQL sql = SQL.begin().in(null, "o.id", (Object) Arrays.asList(1, 2, 3)).end();
        assertEquals("o.id in (?,?,?)", sql.getText().replaceAll("\\s+", " "));
        assertEquals(3, sql.getParams().size(), "one marker per element");
    }

    /**
     * 空集合的既有行为不变（WI10a 设计 §3.4：空 IN 列表语义不在本策略范围）。
     * 现状锁定：`paramCollection` 的括号 + `spreadParams` 空集合的 `(NULL)` 叠加为
     * `((NULL))`；门禁必须在空集合分支之后，不改变这一既有输出。
     */
    @Test
    public void testEmptyCollectionKeepsNullExpansion() {
        SQL sql = SQL.begin().in((Collection<?>) Collections.emptyList()).end();
        assertEquals(" in ((NULL))", sql.getText().replaceAll("\\s+", " "));
    }

    /**
     * 配置通路接线验证（Minimum Rules #23）：调高配置后原超阈值集合可过；恢复后被拒
     * ——证明配置项被 `spreadParams` 真实读取。
     */
    @Test
    public void testConfigRaiseAllowsOverThresholdCollection() {
        try {
            AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE, THRESHOLD + 500);
            SQL sql = SQL.begin().in(null, "o.id", (Object) numbers(THRESHOLD + 1)).end();
            assertEquals(THRESHOLD + 1, sql.getParams().size(), "all elements parameterized after raise");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE, THRESHOLD);
        }
        assertThrows(NopException.class, () -> SQL.begin().in(null, "o.id", (Object) numbers(THRESHOLD + 1)));
    }

    /**
     * 行组按 1 计（Phase 0 计数口径裁定）：Collection 顶层元素个数为计数口径，
     * `Object[]` 行组元素不按展开数计——与 EQL 侧行值构造器处置正交，语句级参数总数为 Deferred。
     */
    @Test
    public void testRowGroupCountsAsSingleElement() {
        SQL.SqlBuilder sb = SQL.begin();
        // 每个行组展开为 (a,b) 两个 ?，但顶层只有 2 个元素——远低于阈值，不应被门禁拦截
        sb.spreadParams(Arrays.asList(new Object[]{1, "a"}, new Object[]{2, "b"}));
        SQL sql = sb.end();
        assertTrue(sql.getText().replace(" ", "").contains("(?,?),(?,?)"), "row-group shape preserved: " + sql.getText());
        assertEquals(4, sql.getParams().size(), "all row values parameterized");
    }
}
