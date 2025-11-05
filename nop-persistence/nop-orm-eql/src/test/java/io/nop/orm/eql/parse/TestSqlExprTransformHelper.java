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
}
