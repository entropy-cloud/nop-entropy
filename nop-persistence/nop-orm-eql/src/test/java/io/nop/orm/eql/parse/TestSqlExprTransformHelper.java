package io.nop.orm.eql.parse;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.eval.SqlExprTransformHelper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSqlExprTransformHelper {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        String sqlText = "concat('a',?, a + b + ?)";
        Expression expr = SqlExprTransformHelper.parseSqlToExpression(null, sqlText);
        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        //cp.getScope().setFunctionProvider();
        IEvalAction action = cp.buildEvalAction(expr);

        List<Object> params = new ArrayList<>();
        params.add(1);
        params.add(2);

        Map<String, Object> o = new HashMap<>();
        o.put("a", 1);
        o.put("b", 2);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(OrmEqlConstants.VAR_PARAMS, params);
        scope.setLocalValue(OrmEqlConstants.VAR_O, o);

        Object result = action.invoke(scope);
        assertEquals("a15", result);
    }

    private Object evalExpression(String sqlText) {
        Expression expr = SqlExprTransformHelper.parseSqlToExpression(null, sqlText);
        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        IEvalAction action = cp.buildEvalAction(expr);
        return action.invoke(XLang.newEvalScope());
    }

    @Test
    public void testHexLiteralToExpression() {
        // 解析阶段已剥离0x前缀，求值转换必须直接按裸十六进制串解析
        assertEquals(Boolean.TRUE, evalExpression("0x0A = 10"));
    }

    @Test
    public void testBitLiteralToExpression() {
        // 解析阶段已剥离b'..'前缀，求值转换必须直接按裸二进制串解析
        assertEquals(Boolean.TRUE, evalExpression("b'1010' = 10"));
    }

    @Test
    public void testLargeHexLiteralToExpression() {
        // 超过64位的十六进制字面量应按BigInteger解析而不是抛NumberFormatException
        assertEquals(Boolean.TRUE, evalExpression("0xFFFFFFFFFFFFFFFFF != 0"));
    }
}
