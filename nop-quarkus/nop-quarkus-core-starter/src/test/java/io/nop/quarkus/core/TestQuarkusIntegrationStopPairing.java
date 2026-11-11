package io.nop.quarkus.core;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.quarkus.core.ioc.NopQuarkusBeanContainer;
import io.quarkus.runtime.ShutdownEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.4-MA-04（plan 2562 裁定 B②）载体：Quarkus 集成层注册面 start/stop 配对钉。
 *
 * <p>配对锚点是 {@code QuarkusIntegration.start()} 写入的全局注册面（容器注册表指针、
 * GlobalMeterRegistry、系统属性），不是 NopQuarkusBeanContainer.stop/restart——两者
 * 为空体，禁止作为配对目标（rule 24）。钩子面 = CDI
 * {@code QuarkusIntegrationShutdownObserver}（@Observes ShutdownEvent）。</p>
 *
 * <p>隔离设计：测试仅调用不含 Arc/ConfigProvider 全链路的 package-private 注册单元
 * （registerQuarkusContainer/applyConfigOverrides + stop 全链），不动
 * CoreInitialization 静态状态；@AfterEach 强制 stop + 注销注册表回滚 fork 状态。</p>
 */
public class TestQuarkusIntegrationStopPairing {

    @AfterEach
    public void teardown() {
        QuarkusIntegration.stop();
        BeanContainer.registerInstance(null);
        System.clearProperty("quarkus.profile");
    }

    @Test
    public void testShutdownObserverCallsStopAndRestoresRegistry() {
        // 全新 fork：注册表为空 == 前置容器为 null，stop 回滚后注册表应回到未初始化
        assertFalse(BeanContainer.isInitialized());

        System.setProperty("quarkus.profile", "test");
        QuarkusIntegration.applyConfigOverrides();
        QuarkusIntegration.registerQuarkusContainer();
        assertTrue(BeanContainer.instance() instanceof NopQuarkusBeanContainer,
                "start must register the quarkus bridge container");

        // 钩子钉：ShutdownEvent observer 必须触发 QuarkusIntegration.stop 撤销注册面
        new QuarkusIntegrationShutdownObserver().onShutdown(new ShutdownEvent());

        assertFalse(BeanContainer.isInitialized(),
                "stop must roll the registry pointer back to the pre-start state (null)");
        assertNull(System.getProperty(ApiConfigs.CFG_PROFILE.getName()),
                "stop must clear the profile system property set by start");
    }

    @Test
    public void testStopRestoresPreviousContainerInstance() {
        NoopBeanContainer sentinel = new NoopBeanContainer("sentinel");
        BeanContainer.registerInstance(sentinel);

        QuarkusIntegration.registerQuarkusContainer();
        assertNotEquals(sentinel, BeanContainer.instance());
        assertTrue(BeanContainer.instance() instanceof NopQuarkusBeanContainer);

        QuarkusIntegration.stop();

        assertSame(sentinel, BeanContainer.instance(),
                "stop must restore the exact pre-start container (IocCoreInitializer.destroy write-back pattern)");
    }

    @Test
    public void testStopIsIdempotentWhenNotStarted() {
        // 未 start 时 stop 必须是安全 no-op，不吞掉现有注册表
        NoopBeanContainer sentinel = new NoopBeanContainer("sentinel");
        BeanContainer.registerInstance(sentinel);

        QuarkusIntegration.stop();

        assertSame(sentinel, BeanContainer.instance(),
                "stop without a prior start must not touch the current registry");
    }
}
