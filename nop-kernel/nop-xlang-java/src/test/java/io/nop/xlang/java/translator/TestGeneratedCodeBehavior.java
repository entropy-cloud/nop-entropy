package io.nop.xlang.java.translator;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.MultiplyExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行为级验证：树 → 转译器 → 生成源码 → 测试域编译加载（JdkJavaCompiler 内存编译通路，
 * Phase 4 决策口径：nop-javac 不承担生产产物编译，仅测试域执行通路）→ 执行 → 断言。
 *
 * <p>覆盖 roadmap I2 验收"异常语义断言可执行"的行为级兜底（异常携带内嵌 SourceLocation 常量，
 * 防止常量存在但从未被断言携带的 vacuous 满足）与 EvalMethod 包装（janino 同构验证）。
 */
public class TestGeneratedCodeBehavior {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 行为级兜底：转译含抛错点的树 → 编译 → 执行 → 断言 NopException 错误码 +
     * SourceLocation 等于内嵌常量（path/line/col 全等）。
     */
    @Test
    public void testExceptionCarriesEmbeddedSourceLocationConstant() throws Exception {
        CompareUnit unit = staticUnit("exception-method");
        IExecutableExpression tree = CorpusV1.Compiler.INSTANCE.compile(unit);
        GeneratedJavaSource src = TRANSLATOR.translate(unit.getSourceLocationPath(), tree);
        Class<?> clazz = compileAndLoad(src);

        Method entry = GeneratedEvalBinding.findEntryMethod(clazz);
        try {
            entry.invoke(null, newScope());
            throw new AssertionError("expected NopEvalException from generated code");
        } catch (InvocationTargetException e) {
            NopEvalException err = assertInstanceOf(NopEvalException.class, e.getTargetException());
            assertEquals("nop.err.xlang.exec.invoke-method-fail", err.getErrorCode());
            SourceLocation loc = err.getErrorLocation();
            assertEquals(unit.getSourceLocationPath(), loc.getPath());
            assertEquals(2, loc.getLine());
            assertEquals(0, loc.getCol());
        }
    }

    /**
     * 纯表达式单元经 EvalMethodInvoker 包装可被调用（janino 先例同构验证）。
     */
    @Test
    public void testEvalMethodInvokerWrapping() throws Exception {
        SourceLocation loc = SourceLocation.fromLine("synthetic/plus.xpl", 1);
        IExecutableExpression tree = new PlusExecutable(loc,
                LiteralExecutable.build(loc, 1),
                new MultiplyExecutable(loc, LiteralExecutable.build(loc, 2), LiteralExecutable.build(loc, 3)));
        GeneratedJavaSource src = TRANSLATOR.translate("synthetic/plus.xpl", tree);
        Class<?> clazz = compileAndLoad(src);

        IEvalFunction fn = GeneratedEvalBinding.bind(clazz);
        Object result = fn.invoke(null, new Object[0], newScope());
        assertEquals(7, result);
        assertEquals(Integer.class, result.getClass());
    }

    /**
     * 注册全局函数分派（FunctionExecutable 族）：生成代码运行时经同一全局注册表解析 assign，
     * 副作用落到传入的同一 IEvalScope（对拍副作用层的绿路径基础）。
     */
    @Test
    public void testGlobalFunctionSideEffectOnScope() throws Exception {
        CompareUnit unit = staticUnit("method-static-side-effect");
        IExecutableExpression tree = CorpusV1.Compiler.INSTANCE.compile(unit);
        GeneratedJavaSource src = TRANSLATOR.translate(unit.getSourceLocationPath(), tree);
        Class<?> clazz = compileAndLoad(src);

        IEvalScope scope = newScope();
        Method entry = GeneratedEvalBinding.findEntryMethod(clazz);
        Object ret = entry.invoke(null, scope);
        assertEquals(null, ret);
        assertEquals(3, scope.getLocalValue("result"));
    }

    @Test
    public void testStringConcatBehavior() throws Exception {
        CompareUnit unit = staticUnit("arith-string-concat");
        IExecutableExpression tree = CorpusV1.Compiler.INSTANCE.compile(unit);
        GeneratedJavaSource src = TRANSLATOR.translate(unit.getSourceLocationPath(), tree);
        Class<?> clazz = compileAndLoad(src);

        Object ret = GeneratedEvalBinding.findEntryMethod(clazz).invoke(null, newScope());
        assertEquals("ab!", ret);
    }

    @Test
    public void testGeneratedClassNameDerivation() {
        GeneratedJavaSource src = TRANSLATOR.translate("synthetic/plus.xpl",
                LiteralExecutable.build(SourceLocation.fromLine("synthetic/plus.xpl", 1), 1));
        assertEquals("io.nop.xlang.gen.Gen_synthetic_plus_xpl", src.getClassName());
    }

    // ------------------------------------------------------------------

    private static CompareUnit staticUnit(String name) {
        return CorpusV1.units().stream()
                .filter(u -> u.getName().equals(name + "-static"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unit not found: " + name));
    }

    private static IEvalScope newScope() {
        return new EvalScopeImpl(new LinkedHashMap<>());
    }

    /**
     * 测试域编译通路：JdkJavaCompiler（nop-javac 内存编译）+ surefire classpath
     * （先例：nop-core TestAopCodeGenerator 经 getDefaultClassPaths() 内存编译）。
     */
    private static Class<?> compileAndLoad(GeneratedJavaSource src) {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();
        JavaCompileResult result = compiler.compile(src.getClassName(), src.getCode(), classPaths);
        assertTrue(result.isSuccess(), () -> "generated source compile failed: "
                + result.getErrorMessage() + "\n==== generated code ====\n" + src.getCode());
        return result.getGeneratedClass(src.getClassName());
    }
}
