/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.core.resource.IResource;
import io.nop.dao.dialect.function.TemplateSQLFunction;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.ISqlFunctionModel;
import io.nop.dao.dialect.model.SqlTemplateModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.nop.core.lang.sql.StringSqlExpr.makeExpr;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EQL WI0：{@link TemplateSQLFunction} 越界数字 marker 的加载期校验（plan
 * ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md Phase 2）。
 *
 * <p>历史缺陷：构造函数解析 marker 但不校验索引，越界 {@code {n}} 在查询期静默产出
 * {@code " null "}。现在构造期即抛
 * {@code nop.err.dao.func-template-marker-out-of-range}，坏模板在方言加载期失败。
 */
public class TestTemplateSQLFunction {

    private static final String ERR_CODE = "nop.err.dao.func-template-marker-out-of-range";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** 越界 marker（{2} 超出 2 个参数声明）在构造期抛新错误码，param 带 funcName/索引/模板源。 */
    @Test
    public void testOutOfBoundsMarkerRejectedAtConstruction() {
        SqlTemplateModel model = new SqlTemplateModel();
        model.setName("myfunc");
        model.setSource("func({0},{2})");
        model.setArgTypes(Arrays.asList(StdSqlType.ANY, StdSqlType.ANY));

        NopException ex = assertThrows(NopException.class, () -> new TemplateSQLFunction(model));
        assertEquals(ERR_CODE, ex.getErrorCode());
        assertEquals("myfunc", ex.getParam("funcName"));
        assertEquals(2, ex.getParam("markerIndex"));
        assertEquals("func({0},{2})", ex.getParam("templateSource"));
    }

    /** 边界：最大合法索引 argCount-1 可正常构造并按声明序替换出正确 SQL 文本。 */
    @Test
    public void testBoundaryMarkerIndexBuildsCorrectSql() {
        SqlTemplateModel model = new SqlTemplateModel();
        model.setName("myfunc");
        model.setSource("func({1},{0})");
        model.setArgTypes(Arrays.asList(StdSqlType.ANY, StdSqlType.ANY));

        TemplateSQLFunction fn = new TemplateSQLFunction(model);
        SqlExprList expr = fn.buildFunctionExpr(null, asList(makeExpr("a"), makeExpr("b")), null);
        assertEquals("func(b,a)", expr.getSqlString());
    }

    /**
     * 记录既有行为（本 WI 保持不变）：非全数字 marker（如 {name}）不参与参数替换，
     * 构造期跳过、替换期保留字面文本。
     */
    @Test
    public void testNonDigitMarkerKeptLiteral() {
        SqlTemplateModel model = new SqlTemplateModel();
        model.setName("myfunc");
        model.setSource("func({0},{name})");
        model.setArgTypes(Collections.singletonList(StdSqlType.ANY));

        TemplateSQLFunction fn = new TemplateSQLFunction(model);
        SqlExprList expr = fn.buildFunctionExpr(null, Collections.singletonList(makeExpr("a")), null);
        assertEquals("func(a,{name})", expr.getSqlString());
    }

    /**
     * 全方言参数化守护：遍历全部可加载方言的每个 {@code <template>} 函数，重新构造
     * {@link TemplateSQLFunction}。构造成功即证明现存模板的全部数字 marker 都在
     * argTypes 范围内（越界者在方言加载路径上会被构造函数直接拒绝）；
     * 不可加载方言（default/db2/geo-support/window-expr-support）由
     * TestDialectCapabilityMatrix 的快照登记，不在此覆盖。
     */
    @Test
    public void testAllDialectTemplateFunctionsValid() {
        int checked = 0;
        for (IResource resource : TestDialectCapabilityMatrix.enumerateDialectResources()) {
            String dialectName = TestDialectCapabilityMatrix.dialectName(resource.getPath());
            IDialect dialect;
            try {
                dialect = DialectManager.instance().getDialect(dialectName);
            } catch (Exception e) {
                continue;
            }
            List<ISqlFunctionModel> functions = ((DialectImpl) dialect).getDialectModel().getFunctions();
            for (ISqlFunctionModel fnModel : functions) {
                if (!(fnModel instanceof SqlTemplateModel))
                    continue;
                SqlTemplateModel tplModel = (SqlTemplateModel) fnModel;
                TemplateSQLFunction fn = assertDoesNotThrow(() -> new TemplateSQLFunction(tplModel),
                        () -> dialectName + ": template function '" + tplModel.getName()
                                + "' must construct without out-of-range marker");
                assertEquals(CollectionHelper.toNotNull(tplModel.getArgTypes()).size(), fn.getMaxArgCount(),
                        () -> dialectName + ": template function '" + tplModel.getName()
                                + "' arg count contract");
                checked++;
            }
        }
        int checkedCount = checked;
        assertTrue(checked > 0,
                () -> "guard must exercise at least one template function across loadable dialects, got "
                        + checkedCount);
        System.out.println("TestTemplateSQLFunction guard checked template functions: " + checked);
    }
}
