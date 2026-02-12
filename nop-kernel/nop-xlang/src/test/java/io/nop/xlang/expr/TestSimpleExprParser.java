/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.expr.simple.SimpleExprParser;
import io.nop.xlang.functions.GlobalFunctions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSimpleExprParser {
    @Test
    public void testTemplateExpr() {
        String expr = "${tablePackages[entity.id]}.${entity.defKey.$camelCase(true)}";
        XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTemplateExpr(null, expr, false, ExprPhase.eval);
    }

    @Test
    public void testTpl() {
        EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class);
        String expr = "tpl`${name}`";
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("name", "abc");
        assertEquals("abc", action.invoke(scope));
    }

    @Test
    public void testSpread() {
        assertEquals("{\n" + "  \"desc\": false,\n" + "  \"name\": \"a\",\n" + "  \"nullsFirst\": null,\n"
                + "  \"owner\": null\n" + "}", JsonTool.serialize(eval("{...bean, ...NULL}"), true));
    }

    @Test
    public void testCpExpr() {
        String expr = "#{3}";
        assertEquals(3, XLang.newCompileTool().getStaticValue(null, expr));
    }

    @Test
    public void testNot() {
        assertEquals(false, eval("!x"));
    }

    @Test
    public void testNotEq() {
        assertEquals(true, eval("3 != 2"));
    }

    @Test
    public void testExtensionMethod() {
        assertEquals("myTest", eval("'my_test'.$camelCase(false)"));
    }

    @Test
    public void testArithmetic() {
        assertEquals(2, eval("x+1"));
        assertEquals(0, eval("x-1"));
        assertEquals(2, eval("x*2"));
        assertEquals(0.5, eval("x/2"));
        assertEquals(1, eval("x||2"));
    }

    @Test
    public void testIndexOf() {
        assertEquals(2, eval("'abc'.indexOf('c')"));
    }

    @Test
    public void testInstanceOf() {
        assertEquals(true, eval("'s' instanceof String"));
    }

    @Test
    public void testTernaryExpr() {
        BaseTestCase.forceStackTrace();
        String expr = "listGrid.rowDeleteIcon ?? (listGrid.rowDeleteLabel ? null : 'text-danger')";
        SimpleExprParser.newDefault().parseExpr(null, expr);
    }

    @Test
    public void testBinaryCompare() {
        String text = "pageModel.table.pager != 'none'";
        BinaryExpression expr = (BinaryExpression) SimpleExprParser.newDefault().parseExpr(null, text);
        assertNotNull(expr.getLeft());
    }

    @Test
    public void testArrayIndex() {
        String text = "a[0]";
        Expression expr = SimpleExprParser.newDefault().parseExpr(null, text);
        IExecutableExpression eval = XLang.newCompileTool().allowUnregisteredScopeVar(true).buildExecutable(expr);
        assertTrue(expr instanceof MemberExpression);
        assertTrue(eval instanceof GetAttrExecutable);
    }

    @Test
    public void testOptionalMember() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String text = "a ?. b  ?.  [3] ?. \n(3)";
        SimpleExprParser.newDefault().parseExpr(null, text);
    }

    @Test
    public void testFunctionCall() {
        BaseTestCase.forceStackTrace();
        Function<Integer, String> test = this::test;
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("test", test);

        String expr = "test(3)";
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        assertEquals("4", action.invoke(scope));
    }

    @Test
    public void testSource() {
        String source = "x ++; y=2; x = !x";
        XLangCompileTool cp = XLang.newCompileTool();
        Expression expr = cp.getCompiler().parseFullExpr(null, source, cp.getScope());
        assertEquals("x++;\n" +
                "y=2;\n" +
                "x=!x;\n", expr.toExprString());
    }

    @Test
    public void testDefaultValue() {
        String expr = "NULL.$toBoolean(true)";
        assertEquals(true, eval(expr));
    }

    @Test
    public void testIF() {
        ICancellable cancellable = EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("a", 0);
        scope.setLocalValue("b", 4);

        String expr = "IF(a>0,1,IF(b>3,2,3))";
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        assertEquals(2, action.invoke(scope));

        cancellable.cancel();
    }

    @Test
    public void testCompare() {
        String expr = "L.sort((a,b)=> a > b ? 1:0)";
        eval(expr);
    }

    @Test
    public void testConcat() {
        String str = "'//File:' + filePath.$fileFullName()+'\\n\\n'+fileTextChunk";
        XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, str);
    }

    @Test
    public void testAssign() {
        ApiRequest<Object> request = new ApiRequest<>();
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("request", request);

        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileFullExpr(null, "request.headers['id'] = 'a'");
        action.invoke(scope);
        assertEquals("a", request.getHeader("id"));
    }


    String test(int value) {
        return String.valueOf(value + 1);
    }

    Object eval(String expr) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 1);
        vars.put("m", CollectionHelper.buildImmutableMap(Pair.of("a", 1), Pair.of("b", "3")));
        vars.put("L", Arrays.asList(1, 2, 3));
        vars.put("NULL", null);

        OrderFieldBean bean = new OrderFieldBean();
        bean.setName("a");
        bean.setDesc(false);
        vars.put("bean", bean);

        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        return action.invoke(XLang.newEvalScope(vars));
    }
}
