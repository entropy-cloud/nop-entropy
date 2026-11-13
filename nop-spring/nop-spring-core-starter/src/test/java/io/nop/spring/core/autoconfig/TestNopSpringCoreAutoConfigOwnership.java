package io.nop.spring.core.autoconfig;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.4-MA-04（plan 2562 裁定 B①）载体：NopSpringCoreAutoConfig.onClose 归属守卫。
 *
 * <p>非 owner（宿主已预初始化的嵌套 Spring 场景）关闭 Spring 上下文时不得整体销毁
 * CoreInitialization 生命周期；owner（IocCoreInitializer 已用 appContainer 覆盖注册表
 * 指针）关闭时正常 destroy。</p>
 *
 * <p>隔离设计：同 surefire fork 内 CoreInitialization 静态状态被本测试前推到
 * {@code INITIALIZER_PRIORITY_INTERNAL}（partial-initialize viability，
 * 见 CoreInitialization#initializeTo）——只推进标志位、不触碰 IoC/VFS 高层
 * 初始化器；@AfterEach 统一回滚（CoreInitialization.destroy + 注销 BeanContainer），
 * 与模块内既有测试（均不依赖平台初始化状态）解耦。</p>
 */
public class TestNopSpringCoreAutoConfigOwnership {

    private final StaticApplicationContext context = new StaticApplicationContext();

    @AfterEach
    public void restoreStaticState() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void testNonOwnerCloseDoesNotDestroyLifecycle() {
        // 宿主预初始化（嵌套 Spring 场景）：生命周期归属宿主，不属于 Spring 上下文
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_INTERNAL);
        assertTrue(CoreInitialization.isInitialized());

        NopSpringCoreAutoConfig config = new NopSpringCoreAutoConfig();
        config.registerContainer(context);

        // 未运行 NopApplication.run —— IocCoreInitializer 从未覆盖桥接容器，
        // 注册表指针仍指向本上下文的桥接容器 == 非 owner
        config.onClose(new ContextClosedEvent(context));

        // 归属守卫红面：去掉守卫后 onClose 会 destroy 平台生命周期（initialized 变 false）
        assertTrue(CoreInitialization.isInitialized(),
                "non-owner close must not destroy the CoreInitialization lifecycle");
    }

    @Test
    public void testOwnerCloseDestroysLifecycle() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_INTERNAL);
        assertTrue(CoreInitialization.isInitialized());

        NopSpringCoreAutoConfig config = new NopSpringCoreAutoConfig();
        config.registerContainer(context);

        // 模拟 IocCoreInitializer.initialize 注册表指针覆盖（回写桥 owner 侧形态）：
        // appContainer 取代桥接容器成为当前注册表
        IBeanContainer appContainer = new io.nop.spring.core.ioc.NopSpringBeanContainer(
                new StaticApplicationContext());
        BeanContainer.registerInstance(appContainer);

        config.onClose(new ContextClosedEvent(context));

        // owner 关闭必须销毁生命周期（守卫不得误伤 owner 侧 destroy）
        assertFalse(CoreInitialization.isInitialized(),
                "owner close must destroy the CoreInitialization lifecycle");
    }
}
