/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * api-core SPI 接口面零签名钉（CRA R1-04，plan 2499 Phase 2，裁定⑤ SPI 策略终裁：
 * 零接口签名变更 source-compat。设计权威来源：
 * {@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.5）。
 *
 * <p>PA 义务配套：SPI 回归面 ×11 实现类的验证方式 = 接口面签名钉（本测试，方法集逐个断言）
 * + nop-api-core/nop-commons 全量测试门禁承载行为回归 + 仓级 grep 重枚举留证
 * （_tmp/phase2-spi-enumeration.log）。接口方法集若发生变化，本测试即红，强制触发
 * ×11 全表回归义务。
 */
public class TestApiCoreSpiSurface {

    /** IContextProvider 接口方法集（派生自 io.nop.api.core.context.IContextProvider）。 */
    private static final List<String> I_CONTEXT_PROVIDER_METHODS = Arrays.asList(
            "currentContext", "getOrCreateContext", "newContext", "attachContext", "detachContext");

    @Test
    public void testIContextProviderMethodSetUnchanged() {
        List<String> names = publicMethodNames(IContextProvider.class);
        assertEquals(I_CONTEXT_PROVIDER_METHODS.size(), names.size(),
                "IContextProvider method set changed: " + names + " — triggers x11 SPI regression obligation");
        for (String expected : I_CONTEXT_PROVIDER_METHODS) {
            assertTrue(names.contains(expected),
                    "IContextProvider." + expected + " must exist (zero-signature pin)");
        }
    }

    @Test
    public void testIContextCarrierMethodsUnchanged() throws Exception {
        // 裁定③/⑤钉：context 传播以既有 IContext.runOnContext/executeWithContext/syncGet 承载，
        // 不新增接口方法
        assertMethod(IContext.class, "runOnContext", Runnable.class);
        assertMethod(IContext.class, "executeWithContext", Callable.class);
        assertMethod(IContext.class, "syncGet", CompletionStage.class);
        assertMethod(IContext.class, "isClosed");
        assertMethod(IContext.class, "getSourceContext");
    }

    @Test
    public void testICancelTokenMethodSetUnchanged() {
        List<String> names = declaredMethodNames(ICancelToken.class);
        // isCancelled / getCancelReason / appendOnCancel / removeOnCancel（抽象）
        // + appendOnCancelTask（default）——方法集逐名钉死，新增即红
        assertTrue(names.containsAll(Arrays.asList("isCancelled", "getCancelReason",
                        "appendOnCancel", "removeOnCancel", "appendOnCancelTask")),
                "ICancelToken method set changed: " + names);
        assertEquals(5, names.size(), "ICancelToken method count changed: " + names);
        // 裁定⑥钉：appendOnCancel 不承诺补发、bindCancelToken 内置补触发——契约经静态工具承载，
        // 接口不新增「注册即补发」形态
        assertNoMethodNamed(ICancelToken.class, "tryAppendOnCancel");
    }

    @Test
    public void testICancellableExtendsICancelToken() {
        assertTrue(ICancelToken.class.isAssignableFrom(io.nop.api.core.util.ICancellable.class),
                "ICancellable must continue to extend ICancelToken");
    }

    /**
     * 生产实现类钉（SPI 回归面在 nop-api-core 模块内的部分）：BaseContextProvider（IContextProvider ×1）、
     * BaseContext / DelegateContext（IContext ×2）——跨模块的 ICancellable/ICancelToken 实现类
     * 经仓级 grep 重枚举登记（_tmp/phase2-spi-enumeration.log）。
     */
    @Test
    public void testApiCoreProductionImplementationsUnchanged() {
        assertTrue(IContextProvider.class.isAssignableFrom(BaseContextProvider.class));
        assertTrue(IContext.class.isAssignableFrom(BaseContext.class));
        assertTrue(IContext.class.isAssignableFrom(DelegateContext.class));
    }

    private static List<String> publicMethodNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() != Object.class)
                names.add(method.getName());
        }
        return names;
    }

    private static List<String> declaredMethodNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            // 排除 default 方法生成的 lambda 合成方法
            if (!method.isSynthetic())
                names.add(method.getName());
        }
        return names;
    }

    private static void assertMethod(Class<?> type, String name, Class<?>... paramTypes) throws Exception {
        Method method = type.getMethod(name, paramTypes);
        assertEquals(name, method.getName());
    }

    private static void assertNoMethodNamed(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            assertTrue(!method.getName().equals(name),
                    "unexpected new interface method: " + type.getSimpleName() + "." + name);
        }
    }
}
