package io.nop.xlang.truffle.runtime;

import org.graalvm.polyglot.Engine;

import java.util.Objects;

/**
 * 共享 Engine 单例（设计 truffle 02 §五；plan I8 Phase 1 §4 定稿）：进程内一份 polyglot
 * Engine，多个 Context 显式绑定同一 Engine 时共享/缓存 AST、parse 缓存与已优化代码
 * （{@code Context.Builder#engine(Engine)} 契约）——SHARED 形态下跨 Context 复用的载体。
 *
 * <p>创建时机 = 惰性（首次池打开/显式调用时创建；不使用类初始化 holder——初始化失败会退化为
 * {@code NoClassDefFoundError} 掩盖真实原因）。创建失败 = 显式 {@code IllegalStateException}
 * fail-fast（"注册不可用条目 + 降级解释器"归 I9，本类不做路由）。进程生命周期内不关闭
 * （单例即进程资产；池的开关不回收 Engine）。
 */
public final class XLangTruffleEngine {

    private static volatile Engine sharedEngine;

    private XLangTruffleEngine() {
    }

    /**
     * 共享 Engine 单例（并发安全惰性初始化；创建失败每次显式 fail-fast，不缓存失败态）。
     */
    public static Engine sharedEngine() {
        Engine engine = sharedEngine;
        if (engine == null) {
            synchronized (XLangTruffleEngine.class) {
                engine = sharedEngine;
                if (engine == null) {
                    engine = createEngineOrFailFast();
                    sharedEngine = engine;
                }
            }
        }
        return engine;
    }

    private static Engine createEngineOrFailFast() {
        try {
            Engine engine = Engine.create();
            Objects.requireNonNull(engine, "engine");
            return engine;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "failed to create XLang truffle shared engine (truffle backend unavailable): " + e, e);
        }
    }
}
