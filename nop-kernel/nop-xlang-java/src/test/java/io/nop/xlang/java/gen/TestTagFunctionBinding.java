/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.backend.EvalStaticDegradedExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.xpl.xlib.XplTagLib;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xlib 每标签运行时绑定缝测试（I11 Phase 2）：真实缝路径（LazyCompiledFunction 编译完成点 →
 * {@code EvalBackendRouter.bindTagFunction} → body 换绑）经 RCM 加载真实触发——命中 = body 为
 * {@link EvalStaticBoundExecutable}（artifact = 夹具生成类入口 Method）、缺省实参两形态执行正确；
 * 指纹失配 = degraded 包装 + 分级观测；清单外/开关关 = 静默原 body。夹具类 =
 * {@code Gen__test_xlang_java_gen_tags_xlib_Sum}（{@code TagFixtureMain} 再生成载体，
 * {@code TestTagFixtureSources} 反漂移护栏）。
 */
public class TestTagFunctionBinding {

    private static final String LIB_PATH = "/test/xlang-java-gen/tags.xlib";

    private static final String SUM_KEY = LIB_PATH + "#Sum";

    private static final String SUM_FIXTURE_FQN = EvalMethodConvention.GENERATED_PACKAGE + '.'
            + EvalMethodConvention.generatedClassName(SUM_KEY);

    private String sumFingerprint;

    @AfterAll
    public static void reset() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        CoreInitialization.initialize();
        // 干净缝 + 干净 RCM（防跨用例标签编译缓存串用）
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
        ResourceComponentManager.instance().clearCache("xlib");
        sumFingerprint = ExecutableTreeFingerprints.fingerprint(sumFn());
    }

    @AfterEach
    public void tearDown() {
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
        ResourceComponentManager.instance().clearCache("xlib");
    }

    private ExecutableFunction sumFn() {
        XplTagLib lib = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(LIB_PATH);
        Object invoker = lib.getTag("Sum").getFunctionModel().getInvoker();
        assertTrue(invoker instanceof ExecutableFunction, "Sum invoker must be ExecutableFunction");
        return (ExecutableFunction) invoker;
    }

    /** 干净态（无供给）下编译的对照函数——解释器基线 */
    private ExecutableFunction cleanSumFn() {
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        ResourceComponentManager.instance().clearCache("xlib");
        try {
            return sumFn();
        } finally {
            ResourceComponentManager.instance().clearCache("xlib");
            installSupplies(sumFingerprint);
        }
    }

    private void installSupplies(String fingerprint) {
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        entries.put(SUM_KEY, new GeneratedClassManifest.Entry(SUM_KEY, SUM_FIXTURE_FQN, fingerprint));
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(GeneratedClassManifest.of(entries));
        JavaEvalExecutionBackend.instance().setStaticScanList(List.of(SUM_KEY));
    }

    @Test
    public void testSeamBindsTagBodyWhenSuppliesActive() {
        double missBefore = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        double mismatchBefore = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        installSupplies(sumFingerprint);
        ResourceComponentManager.instance().clearCache("xlib");
        ExecutableFunction fn = sumFn();
        assertTrue(fn.getBody() instanceof EvalStaticBoundExecutable,
                "tag body must be wrapped by the load-time bound executable");
        EvalStaticBoundExecutable bound = (EvalStaticBoundExecutable) fn.getBody();
        Object artifact = bound.getBinding().getBindingArtifact();
        assertTrue(artifact instanceof Method, "identity artifact must be the generated entry method");
        assertEquals(SUM_FIXTURE_FQN, ((Method) artifact).getDeclaringClass().getName());

        // 执行 = 生成类；结果与解释器语义一致（显式实参两形态；单实参形态与干净函数一致——
        // xlib attr defaultValue 在调用方内联，函数自身缺省为空载荷）
        ExecutableFunction cleanFn = cleanSumFn();
        assertEquals(7L, fn.invoke(null, new Object[]{3L, 4L}, XLang.newEvalScope()));
        assertEquals(cleanFn.invoke(null, new Object[]{3L}, XLang.newEvalScope()),
                fn.invoke(null, new Object[]{3L}, XLang.newEvalScope()));
        // 命中 = 绿灯：零降级观测增量
        assertEquals(missBefore, EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING));
        assertEquals(mismatchBefore,
                EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                        EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH));
    }

    @Test
    public void testSeamSilentWhenKeyNotInScanList() {
        // 清单在场但键不在扫描清单（清单外/其它键）→ 静默原 body
        installSupplies(sumFingerprint);
        JavaEvalExecutionBackend.instance().setStaticScanList(List.of("/other/unit.xpl"));
        double before = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        ExecutableFunction fn = sumFn();
        assertFalse(fn.getBody() instanceof EvalStaticBoundExecutable, "out-of-scan-list key stays unwrapped");
        assertEquals(7L, fn.invoke(null, new Object[]{3L, 4L}, XLang.newEvalScope()));
        assertEquals(before, EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH), "no observation for silent branch");
    }

    @Test
    public void testSeamDegradesWithObservationOnFingerprintMismatch() {
        installSupplies("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        ResourceComponentManager.instance().clearCache("xlib");
        double before = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        ExecutableFunction fn = sumFn();
        assertTrue(fn.getBody() instanceof EvalStaticDegradedExecutable,
                "fingerprint mismatch must degrade the tag body");
        // 降级后执行 = 解释器兜底，结果正确（stale 缺陷不放大为不可用）
        assertEquals(7L, fn.invoke(null, new Object[]{3L, 4L}, XLang.newEvalScope()));
        assertEquals(before + 1, EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH),
                "stale mismatch must observe once per compile");
    }

    @Test
    public void testSeamDegradesWithObservationWhenBackendUnavailable() {
        installSupplies(sumFingerprint);
        ResourceComponentManager.instance().clearCache("xlib");
        JavaEvalExecutionBackend.instance().markUnavailable("injected-unavailable");
        // bindLoadedUnit 同款 reason 组合形态（unavailable:<reason>，I10 语义保持）
        String reason = EvalBackendObservation.REASON_UNAVAILABLE + ":injected-unavailable";
        double before = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID, reason);
        ExecutableFunction fn = sumFn();
        assertTrue(fn.getBody() instanceof EvalStaticDegradedExecutable,
                "unavailable backend must degrade the tag body");
        assertEquals(7L, fn.invoke(null, new Object[]{3L, 4L}, XLang.newEvalScope()));
        assertEquals(before + 1, EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID, reason));
    }

    @Test
    public void testSeamSilentWhenBackendDisabled() throws Exception {
        installSupplies(sumFingerprint);
        ResourceComponentManager.instance().clearCache("xlib");
        io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, false);
        try {
            ExecutableFunction fn = sumFn();
            assertFalse(fn.getBody() instanceof EvalStaticBoundExecutable,
                    "disabled java backend must keep original body");
            assertEquals(7L, fn.invoke(null, new Object[]{3L, 4L}, XLang.newEvalScope()));
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                    io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, true);
        }
    }

    @Test
    public void testBoundTagInvocationThroughCallerFrame() {
        // 调用方形态（LazyCompiledExecutableFunction.execute 同构）：调用方 rt（含 out）+ 帧 + body
        installSupplies(sumFingerprint);
        ResourceComponentManager.instance().clearCache("xlib");
        ExecutableFunction fn = sumFn();
        io.nop.core.lang.eval.EvalRuntime rt = new io.nop.core.lang.eval.EvalRuntime(XLang.newEvalScope());
        io.nop.core.lang.eval.EvalFrame frame = new io.nop.core.lang.eval.EvalFrame(null, fn.getSlotNames());
        frame.setArg(0, 10L);
        frame.setArg(1, 20L);
        rt.pushFrame(frame);
        Object result = XLang.getExecutor().execute(fn.getBody(), rt);
        rt.popFrame();
        assertEquals(30L, result, "bound tag body must execute via caller frame slots");
        assertNotNull(fn.getBody());
    }
}
