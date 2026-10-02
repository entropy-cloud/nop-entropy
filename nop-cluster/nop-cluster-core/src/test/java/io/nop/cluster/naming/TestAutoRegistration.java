package io.nop.cluster.naming;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 AutoRegistration 的注册生命周期语义：start 时注册、stop 时注销、
 * version 元数据必须是 npm 语义化版本、autoUpdate 开启后周期性刷新注册。
 */
@Timeout(10)
public class TestAutoRegistration {

    static class RecordingNamingService implements INamingService {
        final AtomicInteger registerCount = new AtomicInteger();
        final AtomicInteger unregisterCount = new AtomicInteger();
        final AtomicInteger updateCount = new AtomicInteger();
        volatile ServiceInstance lastRegistered;
        volatile ServiceInstance lastUnregistered;

        @Override
        public void registerInstance(ServiceInstance instance) {
            registerCount.incrementAndGet();
            lastRegistered = instance;
        }

        @Override
        public void unregisterInstance(ServiceInstance instance) {
            unregisterCount.incrementAndGet();
            lastUnregistered = instance;
        }

        @Override
        public void updateInstance(ServiceInstance instance) {
            updateCount.incrementAndGet();
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return List.of();
        }

        @Override
        public List<String> getServices() {
            return List.of();
        }

        @Override
        public int order() {
            return 0;
        }
    }

    private AutoRegistration newRegistration(INamingService namingService, Map<String, String> metadata) {
        AutoRegistration registration = new AutoRegistration(namingService);
        registration.setServiceName("test-svc");
        registration.setAddr("10.0.0.1");
        registration.setPort(9002);
        registration.setWeight(50);
        registration.setClusterName("BJ-IDC");
        registration.setGroupName("order-group");
        if (metadata != null)
            registration.setMetadata(metadata);
        return registration;
    }

    @Test
    public void testStartRegistersInstanceWithConfiguredFields() {
        RecordingNamingService naming = new RecordingNamingService();
        AutoRegistration registration = newRegistration(naming, Map.of("version", "1.2.3"));

        registration.start();

        assertEquals(1, naming.registerCount.get(), "start must register exactly once");
        ServiceInstance inst = naming.lastRegistered;
        assertNotNull(inst);
        assertEquals("test-svc", inst.getServiceName());
        assertEquals("10.0.0.1", inst.getAddr());
        assertEquals(9002, inst.getPort());
        assertEquals(50, inst.getWeight());
        assertEquals("BJ-IDC", inst.getClusterName());
        assertEquals("order-group", inst.getGroupName());
        assertTrue(inst.isHealthy(), "registered instance must be healthy");
        assertTrue(inst.isEnabled(), "registered instance must be enabled");

        registration.stop();
    }

    @Test
    public void testStopUnregistersLastInstance() {
        RecordingNamingService naming = new RecordingNamingService();
        AutoRegistration registration = newRegistration(naming, null);

        registration.start();
        registration.stop();

        assertEquals(1, naming.unregisterCount.get(), "stop must unregister");
        assertEquals(naming.lastRegistered.getInstanceId(), naming.lastUnregistered.getInstanceId(),
                "unregistered instance must be the one registered at start");
    }

    @Test
    public void testInvalidVersionMetadataRejected() {
        RecordingNamingService naming = new RecordingNamingService();
        AutoRegistration registration = newRegistration(naming, Map.of("version", "not-a-version!"));

        assertThrows(NopException.class, registration::start,
                "non npm-like version metadata must be rejected at start");
        assertEquals(0, naming.registerCount.get(), "failed validation must not register");
    }

    @Test
    public void testAutoUpdateRefreshesRegistration() throws Exception {
        RecordingNamingService naming = new RecordingNamingService();
        AutoRegistration registration = newRegistration(naming, null);
        registration.setAutoUpdate(true);
        registration.setAutoUpdateInterval(java.time.Duration.ofMillis(20));

        registration.start();
        try {
            // 自旋等待后台刷新发生（防挂起规则：短 sleep 自旋，不无限阻塞）
            AtomicBoolean refreshed = new AtomicBoolean(false);
            for (int i = 0; i < 200 && !refreshed.get(); i++) {
                refreshed.set(naming.updateCount.get() > 0);
                if (!refreshed.get())
                    Thread.sleep(10);
            }
            assertTrue(refreshed.get(), "autoUpdate must periodically refresh registration");
        } finally {
            registration.stop();
        }
        int updatesAtStop = naming.updateCount.get();
        assertEquals(1, naming.unregisterCount.get(), "stop must unregister");

        Thread.sleep(100);
        assertEquals(updatesAtStop, naming.updateCount.get(),
                "stop must cancel the auto-update timer");
    }
}
