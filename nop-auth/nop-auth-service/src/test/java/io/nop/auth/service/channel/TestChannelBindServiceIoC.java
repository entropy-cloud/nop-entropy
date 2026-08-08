package io.nop.auth.service.channel;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.auth.api.bind.IChannelBindService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IoC wiring verification for {@link ChannelBindServiceImpl} (Minimum Rules
 * #23). Loads the actual {@code auth-service.beans.xml} through the Nop
 * IoC container and verifies that:
 *
 * <ul>
 *   <li>the {@code channelBindService} bean is resolvable by id,</li>
 *   <li>the bean instance is assignable to the public
 *       {@link IChannelBindService} facade (the {@code ioc:type} attribute
 *       on the bean declaration exposes it under the facade type),</li>
 *   <li>the {@code <ioc:collect-beans by-type="...IChannelBindProvider">}
 *       wiring tolerates an empty provider set — no concrete provider is
 *       registered in this test classpath, so the collected list is empty
 *       and the bean must still construct. ({@link ChannelBindServiceImpl}
 *       only throws when {@code startBinding} is invoked without a
 *       provider, not at construction time.)</li>
 * </ul>
 *
 * <p>This complements {@link TestChannelBindServiceImpl} (which verifies
 * behaviour with a stub provider via direct setter injection) by proving
 * the bean is reachable through the container — the path a real
 * deployment would take.
 */
public class TestChannelBindServiceIoC {

    private static IBeanContainer container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IResource resource = VirtualFileSystem.instance()
                .getResource("/test/test-channel-bind-service.beans.xml");
        container = new AppBeanContainerLoader().loadFromResource("test", resource);
        container.start();
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @Test
    public void channelBindServiceBeanIsResolvable() {
        Object bean = container.getBean("channelBindService");
        assertNotNull(bean, "channelBindService bean must be resolvable from the IoC container");
        assertTrue(bean instanceof IChannelBindService,
                "channelBindService bean must implement IChannelBindService (ioc:type on the bean declaration)");
    }

    @Test
    public void channelBindServiceBeanResolvableByFacadeType() {
        Object bean = container.tryGetBeanByType(IChannelBindService.class);
        assertNotNull(bean, "IChannelBindService bean must be resolvable by facade type");
    }
}
