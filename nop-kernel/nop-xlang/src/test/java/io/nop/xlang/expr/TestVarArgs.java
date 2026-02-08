package io.nop.xlang.expr;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestVarArgs {
    @Test
    public void testVarArgs(){
        String expr = "o.call('aa','b')";
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("o", new MyClass());
        int ret = (Integer)action.invoke(scope);
        assertEquals(1, ret);
    }

    public static class MyClass{
        public int call(String cmd, String...args){
            return 1;
        }

        public int call(String cmd, String reflect, String...args){
            return 2;
        }
    }
}
