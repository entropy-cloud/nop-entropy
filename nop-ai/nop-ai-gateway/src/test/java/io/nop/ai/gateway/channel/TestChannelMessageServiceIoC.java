package io.nop.ai.gateway.channel;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.integration.api.channel.InboundChannelMessage;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * IoC wiring hollow-test for the optional inbound backbone (W6-4, design §3.3
 * 问题 B). Verifies the production-style {@code channelMessageService} bean
 * wiring resolves the optional {@code messageService} ref correctly under the
 * two deployment forms:
 * <ul>
 *   <li><b>Mode 1</b> (no {@link IMessageService} implementation deployed): the
 *       container starts normally and {@code channelMessageService.messageService}
 *       is {@code null} — direct fan-out is in effect.</li>
 *   <li><b>Mode 2</b> ({@code nop-message-core} on the classpath): the ref
 *       resolves to the REAL platform {@code nopLocalMessageService} instance
 *       (a {@link LocalMessageService}), and mode 2 is live.</li>
 * </ul>
 *
 * <p><b>Anti-Hollow</b>: mode 2 is verified both by field state (the injected
 * value is the exact platform bean instance, not a fabricated test bean) AND
 * behaviorally (a dispatch through {@code channelMessageService} reaches a
 * consumer subscribed to the {@code nopLocalMessageService} bean's topic,
 * proving the wiring really connects them at runtime).
 *
 * <p>This mirrors the {@code TestFeishuConnectorIoC} real-container pattern
 * ({@code AppBeanContainerLoader} + a test beans.xml that reproduces the
 * production bean definition in isolation).
 */
class TestChannelMessageServiceIoC {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainer startContainer(String resourcePath) {
        IResource resource = VirtualFileSystem.instance().getResource(resourcePath);
        IBeanContainer container = new AppBeanContainerLoader()
                .loadFromResource("test-cms-ioc", resource);
        container.start();
        return container;
    }

    /**
     * Mode 1: with no IMessageService bean on the container, the optional ref
     * resolves to null and the bean still constructs/starts. dispatchInbound
     * takes the direct branch.
     */
    @Test
    void mode1NoMessageServiceBeanResolvesToNullAndStarts() {
        IBeanContainer container = startContainer("/test/beans/test-channel-message-service-ioc-mode1.beans.xml");
        try {
            ChannelMessageServiceImpl svc = (ChannelMessageServiceImpl)
                    container.getBean("channelMessageService");
            assertNotNull(svc, "channelMessageService bean must exist");

            // the optional ref resolved to null: mode 1 (direct fan-out)
            assertNull(readMessageService(svc),
                    "without an IMessageService bean, messageService must be null (mode 1)");

            // behavioral: direct fan-out still works in mode 1
            TestChannelMessageService.RecordingListener listener =
                    new TestChannelMessageService.RecordingListener();
            svc.subscribeInbound(listener);
            svc.dispatchInbound(inbound("feishu", "user-1", "mode-1-direct"));
            assertEquals(1, listener.received.size(), "mode 1 direct fan-out delivers");
        } finally {
            container.stop();
        }
    }

    /**
     * Mode 2: with nop-message-core's message-core-defaults loaded, the ref
     * resolves to the REAL nopLocalMessageService instance and mode 2 is live.
     */
    @Test
    void mode2MessageServiceBeanResolvesToRealLocalMessageService() {
        IBeanContainer container = startContainer("/test/beans/test-channel-message-service-ioc-mode2.beans.xml");
        try {
            ChannelMessageServiceImpl svc = (ChannelMessageServiceImpl)
                    container.getBean("channelMessageService");
            LocalMessageService platformBus = (LocalMessageService)
                    container.getBean("nopLocalMessageService");
            assertNotNull(platformBus, "the real platform nopLocalMessageService bean must be present");

            IMessageService injected = readMessageService(svc);
            assertNotNull(injected, "with nop-message-core deployed, messageService must be injected (mode 2)");
            // it is a real LocalMessageService (not a fabricated stub)
            assertInstanceOf(LocalMessageService.class, injected,
                    "messageService must be a real LocalMessageService");
            // it is the EXACT platform bean instance (Anti-Hollow: not a parallel fake)
            assertSame(platformBus, injected,
                    "messageService must be the same instance as the platform nopLocalMessageService bean");

            // behavioral Anti-Hollow: a dispatch through channelMessageService
            // reaches a consumer subscribed to the platform bean's topic —
            // proving channelMessageService publishes via the REAL injected bus.
            String topic = ChannelMessageServiceImpl.INBOUND_TOPIC_PREFIX + "feishu";
            CaptureConsumer audit = new CaptureConsumer();
            platformBus.subscribe(topic, audit);

            svc.dispatchInbound(inbound("feishu", "user-1", "mode-2-backbone"));

            assertEquals(1, audit.received.size(),
                    "mode 2 dispatch must publish to the platform bus topic (wiring is live)");
            assertEquals("mode-2-backbone",
                    ((InboundChannelMessage) audit.received.get(0)).getText());
        } finally {
            container.stop();
        }
    }

    // ---- helpers ----------------------------------------------------------

    private static InboundChannelMessage inbound(String channelType, String userId, String text) {
        InboundChannelMessage m = new InboundChannelMessage();
        m.setChannelType(channelType);
        m.setUserId(userId);
        m.setText(text);
        return m;
    }

    /**
     * Read the package-private-in-spirit {@code messageService} field. There is
     * no public getter (it is an optional deployment-injection point, not part
     * of the usage contract), so the hollow-test inspects the field directly.
     */
    private static IMessageService readMessageService(ChannelMessageServiceImpl svc) {
        try {
            Field f = ChannelMessageServiceImpl.class.getDeclaredField("messageService");
            f.setAccessible(true);
            return (IMessageService) f.get(svc);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("unable to read messageService field", e);
        }
    }

    static class CaptureConsumer implements IMessageConsumer {
        final List<Object> received = new ArrayList<>();

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            received.add(message);
            return null;
        }
    }
}
