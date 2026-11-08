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
        // 结构性证据（plan 2514-WI8，替代 sleep(100) 墙钟观察窗）：刷新已发生后、
        // stop 之前捕获定时器 future（stop 会将字段置 null——AutoRegistration:224）；
        // stop 后该 future 必为 done（cancel）——「定时器条目已从调度器移除」的确定性判据。
        java.lang.reflect.Field timerField = AutoRegistration.class.getDeclaredField("autoUpdateTimerFuture");
        timerField.setAccessible(true);
        java.util.concurrent.Future<?> timerFuture;
        try {
            // 条件等待后台刷新发生（超时上限仅挂死保护）；刷新发生即证明定时器已排程
            assertTrue(io.nop.api.core.util.FutureHelper.waitUntil(
                            () -> naming.updateCount.get() > 0, 5000),
                    "autoUpdate must periodically refresh registration");
            timerFuture = (java.util.concurrent.Future<?>) timerField.get(registration);
            assertNotNull(timerFuture, "autoUpdate start must have scheduled the refresh timer");
        } finally {
            registration.stop();
        }
        assertTrue(timerFuture.isDone(),
                "stop must cancel the auto-update timer entry (scheduled task no longer pending)");
        int updatesAtStop = naming.updateCount.get();
        assertEquals(1, naming.unregisterCount.get(), "stop must unregister");

        assertEquals(updatesAtStop, naming.updateCount.get(),
                "stop must cancel the auto-update timer");
    }
}
