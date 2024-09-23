package io.nop.xlang.janino;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.impl.FunctionArgument;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import org.codehaus.janino.ScriptEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.core.reflect.impl.FunctionArgument.arg;
import static io.nop.core.type.PredefinedGenericTypes.INT_TYPE;
import static io.nop.core.type.PredefinedGenericTypes.STRING_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJaninoScriptCompiler extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testJanino() throws Exception {
        // 创建一个ScriptEvaluator对象
        ScriptEvaluator scriptEvaluator = new ScriptEvaluator();
        scriptEvaluator.setParameters(new String[]{"a", "b"}, new Class[]{int.class, int.class});
        scriptEvaluator.setReturnType(int.class);
        scriptEvaluator.cook("return a + b;");

        int result = (Integer) scriptEvaluator.evaluate(new Object[]{5, 10});
        assertEquals(15, result);
    }

    @Test
    public void testCompile() {
        List<FunctionArgument> args = List.of(arg("a", INT_TYPE), arg("b", INT_TYPE));
        IEvalFunction fn = XLang.newCompileTool().compileScript(null, "java", "if(a > 2) {\n return a+b;\n}\nelse{ return 444;} ", args, INT_TYPE);
        assertEquals(444, fn.call2(null, 1, 2, DisabledEvalScope.INSTANCE));
    }

    @Test
    public void testImport() {
        List<FunctionArgument> args = List.of(arg("a", INT_TYPE), arg("b", INT_TYPE));
        IEvalFunction fn = XLang.newCompileTool().compileScript(null, "java", "import java.util.Date;\n return new Date().toString() + '-'+ a;", args, STRING_TYPE);
        String ret = (String) fn.call2(null, 1, 2, DisabledEvalScope.INSTANCE);
        System.out.println(ret);
        assertTrue(ret.endsWith("-1"));
    }
}
