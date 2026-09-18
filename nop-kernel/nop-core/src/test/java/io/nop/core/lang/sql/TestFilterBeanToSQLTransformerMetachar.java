package io.nop.core.lang.sql;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.core.CoreErrors.ERR_SQL_FILTER_INVALID_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-C4-1 回归测试：SQL 元字符黑名单必须无条件生效——即使调用方以
 * checkVarName=false（受信任模式）构造 transformer，含元字符的字段名/owner
 * 也必须被拒绝；合法字段名在 false 模式下照常渲染且值参数化。
 */
public class TestFilterBeanToSQLTransformerMetachar {

    private static void visit(boolean checkVarName, TreeBean filter) {
        FilterBeanToSQLTransformer transformer = new FilterBeanToSQLTransformer(SQL.begin(), checkVarName, "o");
        transformer.visit(filter, null);
    }

    private static NopException assertInvalidName(boolean checkVarName, TreeBean filter) {
        NopException e = assertThrows(NopException.class, () -> visit(checkVarName, filter));
        assertTrue(e.getErrorCode().equals(ERR_SQL_FILTER_INVALID_FIELD_NAME.getErrorCode()),
                "unexpected error code: " + e.getErrorCode());
        return e;
    }

    @Test
    public void testMetacharNameRejectedEvenWhenCheckVarNameFalse() {
        assertInvalidName(false, FilterBeans.eq("a;drop", 1));
        assertInvalidName(false, FilterBeans.eq("a b", 1));
        assertInvalidName(false, FilterBeans.eq("a'b", 1));
        assertInvalidName(false, FilterBeans.eq("a--b", 1));
        assertInvalidName(false, FilterBeans.eq("a/*b", 1));
        assertInvalidName(false, FilterBeans.eq("a#b", 1));
        assertInvalidName(false, FilterBeans.eq("o[1]", 1));
    }

    @Test
    public void testMetacharOwnerRejectedEvenWhenCheckVarNameFalse() {
        TreeBean filter = FilterBeans.eq("status", 1);
        filter.setAttr("owner", "o(1)");
        assertInvalidName(false, filter);
    }

    @Test
    public void testLegitNameRendersParameterizedWhenCheckVarNameFalse() {
        SQL.SqlBuilder sb = SQL.begin();
        FilterBeanToSQLTransformer transformer = new FilterBeanToSQLTransformer(sb, false, "o");
        transformer.visit(FilterBeans.eq("status", 1), null);

        SQL sql = sb.end();
        String text = sql.getText();
        assertTrue(text.contains("status"), "field name rendered: " + text);
        assertTrue(text.contains("?"), "value is parameterized: " + text);
    }

    @Test
    public void testInvalidPropPathStillRejectedWhenCheckVarNameTrue() {
        // 既有行为锁定：checkVarName=true 时非法 prop path（如中划线）依旧被拒绝
        assertInvalidName(true, FilterBeans.eq("create-time", 1));
    }
}
