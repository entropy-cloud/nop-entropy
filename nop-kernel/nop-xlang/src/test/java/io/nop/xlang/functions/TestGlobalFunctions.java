package io.nop.xlang.functions;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGlobalFunctions {

    @Test
    public void testBeanExists() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setBeanProvider(new IBeanProvider() {
            @Override
            public boolean containsBean(String name) {
                return "myBean".equals(name);
            }

            @Override
            public Object getBean(String name) {
                return null;
            }

            @Override
            public <T> T getBeanByType(Class<T> clazz) {
                return null;
            }

            @Override
            public String getBeanScope(String name) {
                return null;
            }
        });

        assertTrue(GlobalFunctions.beanExists(scope, "myBean"));
        assertFalse(GlobalFunctions.beanExists(scope, "otherBean"));
    }

    static Object evalMacroExpr(String expr) {
        EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class);
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        IEvalScope scope = XLang.newEvalScope();
        return action.invoke(scope);
    }

    @Test
    public void testAndMacro() {
        assertTrue((Boolean) evalMacroExpr("AND(true, true)"));
        assertFalse((Boolean) evalMacroExpr("AND(true, false)"));
        assertFalse((Boolean) evalMacroExpr("AND(false, true)"));
        // 3 参数：最后一个参数不能被丢弃
        assertFalse((Boolean) evalMacroExpr("AND(true, true, false)"));
        assertTrue((Boolean) evalMacroExpr("AND(true, true, true)"));
        assertFalse((Boolean) evalMacroExpr("AND(false, false, true)"));
    }

    @Test
    public void testOrMacro() {
        assertTrue((Boolean) evalMacroExpr("OR(false, true)"));
        assertFalse((Boolean) evalMacroExpr("OR(false, false)"));
        // 3 参数：最后一个参数不能被丢弃
        assertTrue((Boolean) evalMacroExpr("OR(false, false, true)"));
        assertFalse((Boolean) evalMacroExpr("OR(false, false, false)"));
    }
}