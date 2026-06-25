package io.nop.xlang.expr;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.xlang.api.XLang;
import io.nop.xlang.functions.GlobalFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * $scope.x 读取和 $scope.x = expr 写入 scope 变量的单元测试
 */
public class TestScopeVarAccess {

    @BeforeAll
    static void init() {
        EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class);
    }

    @Test
    public void testScopeVarRead() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("userId", 123);

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.userId");
        assertEquals(123, action.invoke(scope));
    }

    @Test
    public void testScopeVarReadUndefined() {
        IEvalScope scope = XLang.newEvalScope();

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.notExist");
        assertThrows(NopEvalException.class, () -> action.invoke(scope));
    }

    @Test
    public void testScopeVarWrite() {
        IEvalScope scope = XLang.newEvalScope();

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.userId = 123");
        action.invoke(scope);
        assertEquals(123, scope.getValue("userId"));
    }

    @Test
    public void testScopeVarWriteThenRead() {
        IEvalScope scope = XLang.newEvalScope();

        IEvalAction writeAction = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.userId = 123");
        writeAction.invoke(scope);

        IEvalAction readAction = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.userId");
        assertEquals(123, readAction.invoke(scope));
    }

    @Test
    public void testScopeVarWriteThenBareRead() {
        IEvalScope scope = XLang.newEvalScope();

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.userId = 123");
        action.invoke(scope);

        assertEquals(123, scope.getValue("userId"));
    }

    @Test
    public void testScopeVarWriteNull() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("userId", 123);

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.userId = null");
        action.invoke(scope);
        assertNull(scope.getValue("userId"));
    }

    @Test
    public void testScopeVarCompoundAssign() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("count", 10);

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.count += 5");
        Object result = action.invoke(scope);
        assertEquals(15, result);
        assertEquals(15, scope.getValue("count"));
    }

    @Test
    public void testAssignFunctionEquivalence() {
        IEvalScope scope1 = XLang.newEvalScope();
        IEvalAction a1 = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "assign(\"x\", 42)");
        a1.invoke(scope1);

        IEvalScope scope2 = XLang.newEvalScope();
        IEvalAction a2 = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.x = 42");
        a2.invoke(scope2);

        assertEquals(scope1.getValue("x"), scope2.getValue("x"));
        assertEquals(42, scope2.getValue("x"));
    }

    @Test
    public void testNormalPropertyAccessUnaffected() {
        IEvalScope scope = XLang.newEvalScope();
        TestBean bean = new TestBean();
        bean.setName("hello");
        scope.setLocalValue("bean", bean);

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "bean.name");
        assertEquals("hello", action.invoke(scope));
    }

    @Test
    public void testNormalPropertyAssignUnaffected() {
        IEvalScope scope = XLang.newEvalScope();
        TestBean bean = new TestBean();
        scope.setLocalValue("bean", bean);

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "bean.name = 'world'");
        action.invoke(scope);
        assertEquals("world", bean.getName());
    }

    @Test
    public void testScopeVarWriteReturnsValue() {
        IEvalScope scope = XLang.newEvalScope();

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.x = 99");
        Object result = action.invoke(scope);
        assertEquals(99, result);
    }

    @Test
    public void testScopeVarInExpression() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("a", 3);
        scope.setLocalValue("b", 4);

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.a + $scope.b");
        assertEquals(7, action.invoke(scope));
    }

    @Test
    public void testScopeVarWriteString() {
        IEvalScope scope = XLang.newEvalScope();

        IEvalAction action = XLang.newCompileTool()
                .allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "$scope.name = 'test'");
        action.invoke(scope);
        assertEquals("test", scope.getValue("name"));
    }

    public static class TestBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
