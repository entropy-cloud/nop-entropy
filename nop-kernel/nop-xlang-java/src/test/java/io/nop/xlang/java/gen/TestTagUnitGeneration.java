/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.xlang.api.XLang;
import io.nop.xlang.compare.RecordingEvalOutput;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;
import io.nop.xlang.xpl.xlib.XplTagLib;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xlib 每标签单元转译与执行测试（I11 Phase 2，Phase 1 §7 裁定的消费面验证）：
 * 根级 ExecutableFunction 指纹（签名/缺省/函数体载荷）、translateTagUnit 源码形态
 * （入口 (IEvalScope, Object[], IEvalOutput) + $fn 下降 + 缺省实参）与行为等价
 * （测试域编译通路 JdkJavaCompiler 诊断性编译——产物编译归常规构建，fixture 模块 e2e 承载）。
 */
public class TestTagUnitGeneration {

    private static final String LIB_PATH = "/test/xlang-java-gen/tags.xlib";

    private static XplTagLib lib;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        lib = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(LIB_PATH);
        assertNotNull(lib, "test xlib must be loadable from test classpath _vfs");
    }

    @AfterAll
    public static void reset() {
        CoreInitialization.destroy();
    }

    private static ExecutableFunction tagFn(String tagName) {
        Object invoker = lib.getTag(tagName).getFunctionModel().getInvoker();
        assertTrue(invoker instanceof ExecutableFunction, "tag invoker must be ExecutableFunction: " + tagName);
        return (ExecutableFunction) invoker;
    }

    private static Class<?> compileAndLoad(GeneratedJavaSource src) {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile(src.getClassName(), src.getCode(),
                JdkJavaCompiler.getDefaultClassPaths());
        assertTrue(result.isSuccess(), () -> "tag unit compile failed: " + result.getErrorMessage()
                + "\n==== code ====\n" + src.getCode());
        return result.getGeneratedClass(src.getClassName());
    }

    private static Method tagEntryOf(Class<?> clazz) throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(EvalMethodConvention.ENTRY_METHOD_NAME, IEvalScope.class,
                Object[].class, io.nop.core.lang.eval.IEvalOutput.class);
        assertTrue(Modifier.isStatic(m.getModifiers()), "tag entry must be static");
        return m;
    }

    // ---- 指纹根级 ExecutableFunction 分支（签名 + 缺省 + 函数体载荷全混合） ----

    @Test
    public void testRootFunctionFingerprintCoversSignatureAndBody() {
        SourceLocation loc = SourceLocation.fromPath("/fingerprint-test.xlib");
        IExecutableExpression body = LiteralExecutable.build(loc, 1);
        ExecutableFunction f1 = new ExecutableFunction(loc, loc, "t", 1, 1,
                new String[]{"a"}, IExecutableExpression.EMPTY_EXPRS, body);
        ExecutableFunction f1Again = new ExecutableFunction(loc, loc, "t", 1, 1,
                new String[]{"a"}, IExecutableExpression.EMPTY_EXPRS, LiteralExecutable.build(loc, 1));
        assertEquals(ExecutableTreeFingerprints.fingerprint(f1),
                ExecutableTreeFingerprints.fingerprint(f1Again), "structural-equal functions same fingerprint");

        // 签名差异（argCount/slotNames/缺省实参）→ 不同指纹（防参数变更 stale 漏检）
        ExecutableFunction f2 = new ExecutableFunction(loc, loc, "t", 2, 1,
                new String[]{"a", "b"}, new IExecutableExpression[]{LiteralExecutable.build(loc, 9)}, body);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(f1),
                ExecutableTreeFingerprints.fingerprint(f2), "signature change must change fingerprint");

        // 函数体差异 → 不同指纹
        ExecutableFunction f3 = new ExecutableFunction(loc, loc, "t", 1, 1,
                new String[]{"a"}, IExecutableExpression.EMPTY_EXPRS, LiteralExecutable.build(loc, 2));
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(f1),
                ExecutableTreeFingerprints.fingerprint(f3), "body change must change fingerprint");

        // 源位置差异 → 不同指纹（与其他根形态同一纪律）
        ExecutableFunction f4 = new ExecutableFunction(SourceLocation.fromPath("/other.xlib"),
                SourceLocation.fromPath("/other.xlib"), "t", 1, 1,
                new String[]{"a"}, IExecutableExpression.EMPTY_EXPRS, body);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(f1),
                ExecutableTreeFingerprints.fingerprint(f4));
    }

    // ---- translateTagUnit 源码形态与行为等价 ----

    @Test
    public void testTranslateTagUnitSourceForm() {
        ExecutableFunction fn = tagFn("Sum");
        GeneratedJavaSource src = new ExecToJavaTranslator().translateTagUnit(LIB_PATH + "#Sum", fn);
        assertEquals("io.nop.xlang.gen.Gen__test_xlang_java_gen_tags_xlib_Sum", src.getClassName());
        String code = src.getCode();
        assertTrue(code.contains("// source: " + LIB_PATH + "#Sum"), "header must carry entry key");
        assertTrue(code.contains("public static Object " + EvalMethodConvention.ENTRY_METHOD_NAME
                + "(IEvalScope $scope, Object[] $args, IEvalOutput $out)"),
                "tag entry signature must be (IEvalScope, Object[], IEvalOutput): \n" + code);
        assertTrue(code.contains("DO NOT EDIT") || code.contains("// source:"),
                "generation marker present");
    }

    @Test
    public void testSumTagGeneratedExecutionMatchesInterpreter() throws Exception {
        ExecutableFunction fn = tagFn("Sum");
        GeneratedJavaSource src = new ExecToJavaTranslator().translateTagUnit(LIB_PATH + "#Sum", fn);
        Class<?> clazz = compileAndLoad(src);
        Method entry = tagEntryOf(clazz);

        // 显式实参 + 单实参两形态，均与解释器 invoke 一致（xlib attr defaultValue 在调用方内联——
        // 函数自身 defaultArgValues 为空载荷，两侧同语义；defaultValue 端到端形态归 fixture e2e）
        IEvalScope scope = XLang.newEvalScope();
        Object gen3 = entry.invoke(null, scope, new Object[]{3L, 4L}, null);
        Object interp3 = fn.invoke(null, new Object[]{3L, 4L}, scope);
        assertEquals(interp3, gen3);
        assertEquals(7L, gen3);

        Object genDefault = entry.invoke(null, XLang.newEvalScope(), new Object[]{3L}, null);
        Object interpDefault = fn.invoke(null, new Object[]{3L}, XLang.newEvalScope());
        assertEquals(interpDefault, genDefault, "single-arg form must match interpreter semantics");
    }

    @Test
    public void testTextTagGeneratedExecutionMatchesInterpreterOutput() throws Exception {
        ExecutableFunction fn = tagFn("Greet");
        GeneratedJavaSource src = new ExecToJavaTranslator().translateTagUnit(LIB_PATH + "#Greet", fn);
        Class<?> clazz = compileAndLoad(src);
        Method entry = tagEntryOf(clazz);

        // 解释器列：调用方形态执行（EvalRuntime 携带 out + 帧 + body 直执行——
        // LazyCompiledExecutableFunction.execute 同构）
        RecordingEvalOutput interpOut = new RecordingEvalOutput();
        IEvalScope interpScope = XLang.newEvalScope();
        EvalRuntime rt = new EvalRuntime(interpScope, interpOut);
        EvalFrame frame = new EvalFrame(null, fn.getSlotNames());
        frame.setArg(0, "World");
        rt.pushFrame(frame);
        Object interpRet = XLang.getExecutor().execute(fn.getBody(), rt);
        rt.popFrame();

        // 生成列：绑定形态（TagGeneratedBinding 同参通路——帧槽实参 + 运行时 out）
        RecordingEvalOutput genOut = new RecordingEvalOutput();
        Object genRet = entry.invoke(null, XLang.newEvalScope(), new Object[]{"World"}, genOut);

        assertEquals(interpRet, genRet);
        assertEquals(interpOut.getCalls(), genOut.getCalls(),
                "text-mode tag output sequence must match interpreter");
    }

    // ---- 手工构造标签函数：缺省实参 + slot 读的生成行为（不依赖 VFS 资源形态） ----

    @Test
    public void testManualFunctionUnitWithDefaults() throws Exception {
        SourceLocation loc = SourceLocation.fromPath("/manual/tags.xlib#Calc");
        IExecutableExpression body = new PlusExecutable(loc,
                new SlotIdentifierExecutable(loc, "a", 0),
                new SlotIdentifierExecutable(loc, "b", 1));
        ExecutableFunction fn = new ExecutableFunction(loc, loc, "Calc", 2, 1,
                new String[]{"a", "b", "$local"}, new IExecutableExpression[]{LiteralExecutable.build(loc, 5)},
                body);
        GeneratedJavaSource src = new ExecToJavaTranslator().translateTagUnit("/manual/tags.xlib#Calc", fn);
        Class<?> clazz = compileAndLoad(src);
        Method entry = tagEntryOf(clazz);

        assertEquals(3L + 5L, entry.invoke(null, XLang.newEvalScope(), new Object[]{3L}, null));
        assertEquals(3L + 4L, entry.invoke(null, XLang.newEvalScope(), new Object[]{3L, 4L}, null));
        // 解释器 invoke 同参对照（含缺省实参求值进帧）
        assertEquals(fn.invoke(null, new Object[]{3L}, XLang.newEvalScope()),
                entry.invoke(null, XLang.newEvalScope(), new Object[]{3L}, null));
    }

    @Test
    public void testFingerprintOrderRuleFingerprintBeforeTranslate() {
        // 指纹-转译次序硬规则的等价面：同一函数先指纹后转译，指纹与"从未转译过的同构函数"一致
        // （转译不改变指纹输入——指纹先取；task 内以次序固化，此处锁定根级函数指纹稳定性）
        ExecutableFunction fnA = tagFn("Sum");
        String before = ExecutableTreeFingerprints.fingerprint(fnA);
        new ExecToJavaTranslator().translateTagUnit(LIB_PATH + "#Sum", fnA);
        // 转译后重取（理论上 task 不会这样做——次序规则保证不发生；此处断言指纹本身确定性）
        ResourceComponentManager.instance().clearCache("xlib");
        XplTagLib fresh = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(LIB_PATH);
        ExecutableFunction fnB = (ExecutableFunction) fresh.getTag("Sum").getFunctionModel().getInvoker();
        assertEquals(before, ExecutableTreeFingerprints.fingerprint(fnB),
                "freshly compiled identical tag must have same fingerprint");
    }
}
