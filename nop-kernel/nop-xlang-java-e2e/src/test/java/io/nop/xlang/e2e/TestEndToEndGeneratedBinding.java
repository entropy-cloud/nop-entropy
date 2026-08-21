/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.e2e;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.compare.RecordingEvalOutput;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.GeneratedManifestFiles;
import io.nop.xlang.java.gen.task.XlangJavaGenTask;
import io.nop.xlang.xpl.impl.XplModelParser;
import io.nop.xlang.xpl.xlib.XplTagLib;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端对拍验收（I11，roadmap 验收第一项——全真链路）：真实 `_vfs` 静态资源经
 * 任务扫描（构建管线 exec-maven-plugin 真实运行，产物落盘提交）→ 转译 → Maven 常规编译
 * （`_gen` 随 src/main/java）→ classpath 双清单装载（initializer 自动装载 = 供给闭环本体）
 * → 加载绑定（`XLang.parseXpl` hook / xlib 标签运行时缝）→ invoke；java 列 vs 解释器列
 * 全量一致（三层断言：返回值 typedEquals / 输出缓冲完整调用序列 / 异常语义（错误码 +
 * SourceLocation 回映射））+ 身份断言（执行体 = 生成类绑定 artifact）。解释器列 = 干净态
 * （缝清空 + RCM 缓存清空——防跨列污染）干净编译。I1 harness 断言工具承载
 * （`CompareValues`/`RecordingEvalOutput`——显式引用关系）。禁止自定义 ClassLoader/内存编译
 * 通路充当产物编译 ✓（产物编译 = Maven main compile 常规编译；测试仅消费）。
 */
public class TestEndToEndGeneratedBinding {

    private static final String[] XPL_UNITS = {
            "/test/xlang/e2e/expr.xpl", "/test/xlang/e2e/control.xpl",
            "/test/xlang/e2e/template.xpl", "/test/xlang/e2e/exception.xpl"
    };

    private static final String LIB_PATH = "/test/xlang/e2e/e2e.xlib";

    /** 漏跑观测计数基线（init 自动装载后的值——本类不应产生任何该 reason 增量） */
    private static double missedBaseline;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        missedBaseline = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED);
    }

    @AfterAll
    public static void reset() {
        // 恢复 classpath 供给（缺省状态），清理缓存
        GeneratedManifestFiles.installSupplies(TestEndToEndGeneratedBinding.class.getClassLoader());
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void ensureSupplies() {
        // 供给闭环本体：classpath 双清单经 initializer 自动装载（本方法再确认——防前序用例清缝）
        boolean found = GeneratedManifestFiles.installSupplies(getClass().getClassLoader());
        assertTrue(found, "classpath manifests (build pipeline products) must be present");
        assertTrue(JavaEvalExecutionBackend.instance().isAvailable());
    }

    // ---- 供给闭环 + 排除负样本 ----

    @Test
    public void testClasspathSuppliesCarryAllFixtureKeys() {
        var scan = JavaEvalExecutionBackend.instance().getStaticScanList();
        for (String unit : XPL_UNITS)
            assertTrue(scan.contains(unit), "scan list must contain " + unit);
        assertTrue(scan.contains(LIB_PATH + "#Sum"));
        assertTrue(scan.contains(LIB_PATH + "#Greet"));
        assertTrue(scan.contains(LIB_PATH + "#RangeSum"));
        // 排除类型负样本：xgen/xrun/xtask 不入清单（口径表执行）
        assertFalse(scan.contains("/test/xlang/e2e/negatives/build-time.xgen"));
        assertFalse(scan.contains("/test/xlang/e2e/negatives/init.xrun"));
        assertFalse(scan.contains("/test/xlang/e2e/negatives/task.xtask"));
        // I12 物化：e2e 原生 7 单元（4 xpl + 3 tags）+ corpus 48 静态单元生产形态物化
        assertEquals(55, scan.size(), "fixture keys (4 xpl + 3 tags) + materialized corpus 48 units");
        // 物化单元逐目录计数（11 + 20 + 17）：corpus 新增单元漏扫即红灯（防漏下界）
        assertEquals(48, scan.stream().filter(k -> k.startsWith("/test/xlang/e2e/corpus/")).count(),
                "materialized corpus units (static 11 + static-a 20 + static-b 17)");
    }

    // ---- xpl 单元：java 列 vs 解释器列全量一致 + 身份断言 ----

    @Test
    public void testXplUnitsBoundExecutionMatchesInterpreter() {
        for (String path : XPL_UNITS) {
            assertXplColumnConsistency(path);
        }
    }

    private void assertXplColumnConsistency(String path) {
        // java 列：供给激活态模型加载（parseXpl 绑定 hook）→ choke point 直通绑定体
        XplModel boundModel = XLang.parseXpl(VirtualFileSystem.instance().getResource(path),
                XLangOutputMode.html);
        IExecutableExpression expr = boundModel.getExpr();
        assertTrue(expr instanceof EvalStaticBoundExecutable,
                path + ": loaded model must be bound (degradation would be a unit-level FAIL)");
        Method entry = (Method) ((EvalStaticBoundExecutable) expr).getBinding().getBindingArtifact();
        assertEquals("io.nop.xlang.gen." + EvalMethodConvention.generatedClassName(path), entry.getDeclaringClass().getName(),
                "identity artifact must be the deterministic generated class entry");

        IEvalScope javaScope = XLang.newEvalScope();
        javaScope.setLocalValue("name", "xlang");
        RecordingEvalOutput javaOut = new RecordingEvalOutput();
        ColumnResult javaResult = invokeColumn(() -> XLang.execute(expr, new EvalRuntime(javaScope, javaOut)),
                javaOut, javaScope);

        // 解释器列：干净态（缝清空 + 缓存清空）干净编译（同一前端，不经绑定 hook）
        GeneratedManifestFiles.clearSupplies();
        ResourceComponentManager.instance().clearCache("xpl");
        ResourceComponentManager.instance().clearCache("xlib");
        try {
            XplModel cleanModel = new XplModelParser().outputModel(XLangOutputMode.html)
                    .parseFromResource(VirtualFileSystem.instance().getResource(path));
            IEvalScope interpScope = XLang.newEvalScope();
            interpScope.setLocalValue("name", "xlang");
            RecordingEvalOutput interpOut = new RecordingEvalOutput();
            ColumnResult interpResult = invokeColumn(
                    () -> EvalExprProvider.getGlobalExecutor().execute(cleanModel.getExpr(),
                            new EvalRuntime(interpScope, interpOut)),
                    interpOut, interpScope);

            // 三层断言：返回值 / 输出缓冲 / 异常语义（错误码 + SourceLocation 回映射）
            if (interpResult.error == null) {
                assertTrue(javaResult.error == null,
                        path + ": interpreter ok but generated failed: " + String.valueOf(javaResult.error));
                assertTrue(CompareValues.typedEquals(interpResult.value, javaResult.value),
                        path + ": return value divergence: " + CompareValues.display(interpResult.value)
                                + " vs " + CompareValues.display(javaResult.value));
            } else {
                assertNotNull(javaResult.error, path + ": interpreter failed but generated returned value");
                assertEquals(errorCode(interpResult.error), errorCode(javaResult.error),
                        path + ": error code divergence");
                SourceLocation expected = ((NopException) interpResult.error).getErrorLocation();
                SourceLocation actual = ((NopException) javaResult.error).getErrorLocation();
                if (expected != null) {
                    assertNotNull(actual, path + ": generated error must carry source location");
                    assertEquals(expected.getPath(), actual.getPath(), path + ": loc path remap divergence");
                    assertEquals(expected.getLine(), actual.getLine(), path + ": loc line divergence");
                    assertEquals(expected.getCol(), actual.getCol(), path + ": loc col divergence");
                }
            }
            assertEquals(interpResult.output.getCalls(), javaResult.output.getCalls(),
                    path + ": output call sequence divergence");
        } finally {
            // 恢复供给 + 缓存干净
            GeneratedManifestFiles.installSupplies(getClass().getClassLoader());
            ResourceComponentManager.instance().clearCache("xpl");
            ResourceComponentManager.instance().clearCache("xlib");
        }
    }

    // ---- xlib 每标签：java 列（运行时缝绑定）vs 解释器列 ----

    @Test
    public void testXlibTagsBoundExecutionMatchesInterpreter() {
        assertTagColumn("Sum", new Object[]{3L, 4L}, 7L);
        // 单实参形态（函数自身缺省为空载荷——xlib attr defaultValue 在调用方内联）：
        // 绝对值不定（null 宽松算术语义），断言列一致性 + 身份即可
        assertTagColumn("Sum", new Object[]{3L}, null);
        assertTagColumn("RangeSum", new Object[]{4L}, 10); // int 循环累加（Integer 语义）
        assertTagColumn("Greet", new Object[]{"World"}, null); // text 输出标签（值 null、输出序列比对）
    }

    private void assertTagColumn(String tagName, Object[] args, Object expectedValue) {
        String key = LIB_PATH + '#' + tagName;

        // java 列：RCM 装载（供给激活态）→ 标签惰性编译完成点运行时缝绑定 body
        RecordingEvalOutput javaOut = new RecordingEvalOutput();
        IEvalScope javaScope = XLang.newEvalScope();
        ExecutableFunction boundFn = loadTagInvoker(tagName);
        assertTrue(boundFn.getBody() instanceof EvalStaticBoundExecutable,
                key + ": tag body must be bound via the runtime seam");
        Method artifact = (Method) ((EvalStaticBoundExecutable) boundFn.getBody()).getBinding()
                .getBindingArtifact();
        assertEquals("io.nop.xlang.gen." + EvalMethodConvention.generatedClassName(key), artifact.getDeclaringClass().getName(),
                key + ": identity artifact must be the deterministic tag generated class entry");
        if (expectedValue != null)
            assertEquals(expectedValue, invokeTag(boundFn, args, javaScope, javaOut));
        else
            invokeTag(boundFn, args, javaScope, javaOut);

        // 解释器列：干净态重装载（同源同树）
        GeneratedManifestFiles.clearSupplies();
        ResourceComponentManager.instance().clearCache("xlib");
        try {
            RecordingEvalOutput interpOut = new RecordingEvalOutput();
            IEvalScope interpScope = XLang.newEvalScope();
            ExecutableFunction cleanFn = loadTagInvoker(tagName);
            assertFalse(cleanFn.getBody() instanceof EvalStaticBoundExecutable, "clean column must be unbound");
            Object interpValue = invokeTag(cleanFn, args, interpScope, interpOut);
            Object javaValue = invokeTag(boundFn, args, javaScope, new RecordingEvalOutput());
            assertTrue(CompareValues.typedEquals(interpValue, javaValue), key + ": tag return divergence");
            assertEquals(interpOut.getCalls(), javaOut.getCalls(), key + ": tag output sequence divergence");
        } finally {
            GeneratedManifestFiles.installSupplies(getClass().getClassLoader());
            ResourceComponentManager.instance().clearCache("xlib");
        }
    }

    private ExecutableFunction loadTagInvoker(String tagName) {
        XplTagLib lib = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(LIB_PATH);
        Object invoker = lib.getTag(tagName).getFunctionModel().getInvoker();
        assertTrue(invoker instanceof ExecutableFunction, tagName + " invoker must be ExecutableFunction");
        return (ExecutableFunction) invoker;
    }

    private Object invokeTag(ExecutableFunction fn, Object[] args, IEvalScope scope, RecordingEvalOutput out) {
        if (out == null)
            return fn.invoke(null, args, scope);
        // 输出标签：调用方形态（EvalRuntime 携带 out + 帧 + body——LazyCompiledExecutableFunction 同构）
        EvalRuntime rt = new EvalRuntime(scope, out);
        io.nop.core.lang.eval.EvalFrame frame = new io.nop.core.lang.eval.EvalFrame(null, fn.getSlotNames());
        for (int i = 0; i < args.length; i++)
            frame.setArg(i, args[i]);
        rt.pushFrame(frame);
        try {
            return XLang.getExecutor().execute(fn.getBody(), rt);
        } finally {
            rt.popFrame();
        }
    }

    // ---- 重生成幂等复跑（验收第三项——验收口径：真实产物 == 任务再生成输出） ----

    @Test
    public void testRegenerationIdempotentAgainstCommittedProducts() {
        String basedir = System.getProperty("basedir", ".");
        File projectDir = new File(basedir);
        assertTrue(new File(projectDir, "src/main/resources/_vfs/test/xlang/e2e").isDirectory(),
                "e2e module project dir must be resolvable: " + projectDir.getAbsolutePath());
        XlangJavaGenTask.GenerationResult result = XlangJavaGenTask.generate(projectDir, true);
        assertTrue(result.isClean(), "committed products must equal task regeneration (drifts: "
                + result.getDrifts() + ")——源已改未重生成即红灯（stale 哨兵语义）");
        assertEquals(55, result.getUnits().size());
    }

    // ---- closed-world 结构断言（native 下界 (i)） ----

    @Test
    public void testClosedWorldStructureOfGeneratedClasses() throws Exception {
        File genDir = new File(System.getProperty("basedir", "."),
                "src/main/java/io/nop/xlang/gen");
        File[] sources = genDir.listFiles((d, n) -> n.startsWith("Gen_") && n.endsWith(".java"));
        assertNotNull(sources);
        assertEquals(55, sources.length);
        for (File src : sources) {
            String code = FileHelper.readText(src, null);
            assertFalse(code.contains("defineClass"), src + ": generated code must not define classes at runtime");
            assertFalse(code.contains("ClassLoader"), src + ": generated code must not touch class loaders");
            assertFalse(code.contains("javax.tools"), src + ": no runtime compilation APIs");
            assertFalse(code.toLowerCase().contains("janino"), src + ": no janino dependency");
        }
        // 生成类经应用类加载器常规加载（与测试类同一加载器——非自定义 ClassLoader）
        Class<?> genClass = Class.forName(
                "io.nop.xlang.gen." + EvalMethodConvention.generatedClassName("/test/xlang/e2e/expr.xpl"));
        assertEquals(TestEndToEndGeneratedBinding.class.getClassLoader(), genClass.getClassLoader(),
                "generated classes must load via the application class loader (classpath 常规加载)");
        // 装载路径绿灯：后端可用、无漏跑观测增量（红灯因果对照见
        // TestGeneratedManifestFiles.testMissedRunRedPathMarksUnavailableAndWarns）
        assertTrue(JavaEvalExecutionBackend.instance().isAvailable());
        assertEquals(missedBaseline, EvalBackendObservation.degradationCount(
                JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED), 1e-9,
                "no missed-run observation when manifests are present");
    }

    // ---- helpers ----

    private static String errorCode(Throwable t) {
        return t instanceof NopException && ((NopException) t).getErrorCode() != null
                ? ((NopException) t).getErrorCode().toString() : String.valueOf(t.getClass().getName());
    }

    private static final class ColumnResult {
        final Object value;
        final Throwable error;
        final RecordingEvalOutput output;

        ColumnResult(Object value, Throwable error, RecordingEvalOutput output) {
            this.value = value;
            this.error = error;
            this.output = output;
        }
    }

    private interface Invoker {
        Object invoke();
    }

    private ColumnResult invokeColumn(Invoker body, RecordingEvalOutput out, IEvalScope scope) {
        try {
            return new ColumnResult(body.invoke(), null, out);
        } catch (Throwable t) {
            return new ColumnResult(null, t, out);
        }
    }
}
