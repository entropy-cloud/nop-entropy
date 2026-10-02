package io.nop.cluster.naming;

import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 CachingNamingService 的注册/发现语义：注册类操作直接透传底层 naming service，
 * 发现类操作（getInstances/getInstancesAsync）按 serviceName 缓存，底层只查询一次。
 */
@Timeout(10)
public class TestCachingNamingService {

    static class CountingNamingService implements INamingService {
        final Map<String, List<ServiceInstance>> services = new HashMap<>();
        final AtomicInteger registerCount = new AtomicInteger();
        final AtomicInteger unregisterCount = new AtomicInteger();
        final AtomicInteger updateCount = new AtomicInteger();
        final AtomicInteger queryCount = new AtomicInteger();

        void addInstance(String serviceName, ServiceInstance instance) {
            services.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(instance);
        }

        @Override
        public void registerInstance(ServiceInstance instance) {
            registerCount.incrementAndGet();
        }

        @Override
        public void unregisterInstance(ServiceInstance instance) {
            unregisterCount.incrementAndGet();
        }

        @Override
        public void updateInstance(ServiceInstance instance) {
            updateCount.incrementAndGet();
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            queryCount.incrementAndGet();
            return services.getOrDefault(serviceName, List.of());
        }

        @Override
        public List<String> getServices() {
            return new ArrayList<>(services.keySet());
        }

        @Override
        public int order() {
            return 0;
        }
    }

    static ServiceInstance instance(String serviceName, String instanceId, int port) {
        ServiceInstance inst = new ServiceInstance();
        inst.setServiceName(serviceName);
        inst.setInstanceId(instanceId);
        inst.setAddr("127.0.0.1");
        inst.setPort(port);
        return inst;
    }

    @Test
    public void testRegisterDelegatesToUnderlying() {
        CountingNamingService base = new CountingNamingService();
        CachingNamingService caching = new CachingNamingService(base, 60_000);

        ServiceInstance inst = instance("svc-a", "i1", 9001);
        caching.registerInstance(inst);
        caching.updateInstance(inst);
        caching.unregisterInstance(inst);

        assertEquals(1, base.registerCount.get(), "register must delegate");
        assertEquals(1, base.updateCount.get(), "update must delegate");
        assertEquals(1, base.unregisterCount.get(), "unregister must delegate");
    }

    @Test
    public void testGetInstancesQueriesUnderlyingOnce() {
        CountingNamingService base = new CountingNamingService();
        base.addInstance("svc-a", instance("svc-a", "i1", 9001));
        base.addInstance("svc-a", instance("svc-a", "i2", 9002));
        CachingNamingService caching = new CachingNamingService(base, 60_000);

        List<ServiceInstance> first = caching.getInstances("svc-a");
        List<ServiceInstance> second = caching.getInstances("svc-a");

        assertEquals(2, first.size(), "discovery must return registered instances");
        assertEquals(1, base.queryCount.get(), "second read must be served from cache");
        assertSame(first, second, "cached list identity must be stable");
    }

    @Test
    public void testGetInstancesAsyncCachesResult() throws Exception {
        CountingNamingService base = new CountingNamingService();
        base.addInstance("svc-b", instance("svc-b", "i1", 9101));
        CachingNamingService caching = new CachingNamingService(base, 60_000);

        List<ServiceInstance> asyncResult = caching.getInstancesAsync("svc-b")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, asyncResult.size(), "async discovery must see the instance");

        // 异步结果写入缓存后，同步读取不再触发底层查询
        caching.getInstances("svc-b");
        assertEquals(1, base.queryCount.get(), "sync read after async cache fill must not re-query");

        // 再次异步读取命中缓存
        List<ServiceInstance> cached = caching.getInstancesAsync("svc-b")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertSame(asyncResult, cached, "second async read must be served from cache");
        assertEquals(1, base.queryCount.get());
    }

    @Test
    public void testGetServicesDelegates() {
        CountingNamingService base = new CountingNamingService();
        base.addInstance("svc-a", instance("svc-a", "i1", 9001));
        base.addInstance("svc-b", instance("svc-b", "i2", 9101));
        CachingNamingService caching = new CachingNamingService(base, 60_000);

        List<String> services = caching.getServices();
        assertTrue(services.contains("svc-a") && services.contains("svc-b"),
                "service list must delegate to underlying naming service");
    }
}
