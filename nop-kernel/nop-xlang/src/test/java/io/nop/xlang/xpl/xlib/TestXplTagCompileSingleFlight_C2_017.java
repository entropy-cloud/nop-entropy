/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.xlib;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 2.1-C2-017 回归（plan 2504 Phase 2，与 C2-001 同变更集）：LazyCompiledFunction compile 单飞化。
 *
 * <p>统一不变式「监视器内只做双检+占位，编译体在监视器外」的运行时可观测断言（非类型存在断言）：
 * <ul>
 * <li>并发单飞：N 线程经真实 getFunctionModel 链并发触发同一标签编译 —— 全部有界完成、
 * 全部获得同一编译产物实例（同 key 去重不回退）；</li>
 * <li>自递归标签编译契约保持：Nested 型标签编译期重入 compile() 仍可完成（调用点边界回归，
 * 防止单飞化破坏自递归标签这一合法形态）；</li>
 * <li>真实模板编译+执行链：单飞化后编译产物经 invoke 路径可用。</li>
 * </ul>
 */
public class TestXplTagCompileSingleFlight_C2_017 extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static XplLibTagCompiler compilerFor(String libPath, String tagName) {
        XplTagLib lib = (XplTagLib) XplLibHelper.loadLib(libPath);
        XplTag tag = (XplTag) lib.getTag(tagName);
        assertNotNull(tag, "tag must exist: " + tagName);
        XplLibTagCompiler compiler = tag.getTagCompiler();
        assertNotNull(compiler, "tag compiler must be wired at lib load");
        return compiler;
    }

    /**
     * 并发单飞（真实链）：8 线程经 getFunctionModel 并发触发同一标签编译。
     * 绿形态：全部有界完成、同一 FunctionModel、同一编译产物 invoker 实例、零失败。
     */
    @Test
    public void testConcurrentCompileSingleFlight_C2_017() throws Exception {
        XplLibTagCompiler compiler = compilerFor("/test/my.xlib", "Add");

        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<AtomicReference<Object>> results = new ArrayList<>();
        List<AtomicReference<Throwable>> errors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            AtomicReference<Object> r = new AtomicReference<>();
            AtomicReference<Throwable> e = new AtomicReference<>();
            results.add(r);
            errors.add(e);
            Thread t = new Thread(() -> {
                try {
                    start.await(3, TimeUnit.SECONDS);
                    r.set(compiler.getFunctionModel(XLangOutputMode.none));
                } catch (Throwable ex) {
                    e.set(ex);
                }
            }, "c2-017-compile-" + i);
            t.setDaemon(true);
            threads.add(t);
        }
        threads.forEach(Thread::start);
        start.countDown();
        for (Thread t : threads)
            t.join(15_000);

        for (int i = 0; i < n; i++) {
            Throwable err = errors.get(i).get();
            assertNull(err, "thread " + i + " must compile without failure");
            assertNotNull(results.get(i).get(), "thread " + i + " must obtain the function model");
        }
        Object first = results.get(0).get();
        for (int i = 1; i < n; i++) {
            assertSame(first, results.get(i).get(), "single-flight dedup: all threads share one FunctionModel");
        }
        IFunctionModel model = (IFunctionModel) first;
        assertNotNull(model.getInvoker(), "compiled invoker must be published");
    }

    /**
     * 自递归标签编译契约（Nested 型，真实链）：compile() 编译期重入保持可完成，
     * 编译产物非空 —— 单飞化不得破坏自递归标签这一合法编译形态。
     */
    @Test
    public void testSelfRecursiveTagStillCompiles_C2_017() {
        XplLibTagCompiler compiler = compilerFor("/test/my.xlib", "Nested");

        IFunctionModel model = compiler.getFunctionModel(XLangOutputMode.text);
        assertNotNull(model);
        assertNotNull(model.getInvoker(), "recursive tag must compile to a non-null invoker");

        // 编译产物可再取（编译完成后 compile() 幂等返回）
        IFunctionModel again = compiler.getFunctionModel(XLangOutputMode.text);
        assertSame(model, again);
    }

    /**
     * 真实模板编译+执行链（XplCompiler 接线，rule 23）：使用 my.xlib 的 Add 标签编译并执行模板，
     * 证明单飞化后编译产物经 parseTag→lazyCompile→invoke 全链可用。
     */
    @Test
    public void testTemplateUsingTagCompilesAndEvaluates_C2_017() {
        XLangCompileTool cp = XLang.newCompileTool();
        cp.getScope().addLib("my", XplLibHelper.loadLib("/test/my.xlib"));
        ExprEvalAction action = cp.compileXpl(SourceLocation.fromClass(getClass()), "<my:Add a=\"1\" b=\"2\"/>");
        IEvalContext context = new ServiceContextImpl();
        Object result = action.invoke(context);
        assertEquals("12", String.valueOf(result), "a+b is string concat for text attrs");
    }
}
