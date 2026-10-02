package io.nop.cluster.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 FilteredDiscoveryClient 的发现过滤语义：启用的过滤器链式生效、
 * 禁用的过滤器跳过、空列表短路、异步发现同样过滤。
 */
@Timeout(10)
public class TestFilteredDiscoveryClient {

    static ServiceInstance instance(String id, boolean healthy) {
        ServiceInstance inst = new ServiceInstance();
        inst.setServiceName("svc");
        inst.setInstanceId(id);
        inst.setHealthy(healthy);
        return inst;
    }

    static class HealthyFilter implements IServiceInstanceFilter {
        final AtomicInteger applied = new AtomicInteger();
        boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean accept(ServiceInstance instance) {
            applied.incrementAndGet();
            return instance.isHealthy();
        }
    }

    static class StaticDiscoveryClient implements IDiscoveryClient {
        final List<ServiceInstance> instances;

        StaticDiscoveryClient(List<ServiceInstance> instances) {
            this.instances = instances;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return instances;
        }

        @Override
        public List<String> getServices() {
            return List.of("svc");
        }

        @Override
        public int order() {
            return 0;
        }
    }

    @Test
    public void testEnabledFilterRemovesUnhealthy() {
        HealthyFilter filter = new HealthyFilter();
        FilteredDiscoveryClient client = new FilteredDiscoveryClient(
                new StaticDiscoveryClient(List.of(instance("i1", true), instance("i2", false))),
                List.of(filter));

        List<ServiceInstance> result = client.getInstances("svc");
        assertEquals(1, result.size(), "unhealthy instance must be filtered out");
        assertEquals("i1", result.get(0).getInstanceId());
        assertEquals(2, filter.applied.get(), "filter must be applied to every candidate");
    }

    @Test
    public void testDisabledFilterSkipped() {
        HealthyFilter filter = new HealthyFilter();
        filter.enabled = false;
        FilteredDiscoveryClient client = new FilteredDiscoveryClient(
                new StaticDiscoveryClient(List.of(instance("i1", true), instance("i2", false))),
                List.of(filter));

        List<ServiceInstance> result = client.getInstances("svc");
        assertEquals(2, result.size(), "disabled filter must not remove instances");
        assertEquals(0, filter.applied.get(), "disabled filter must not be invoked");
    }

    @Test
    public void testEmptyListBypassesFilters() {
        HealthyFilter filter = new HealthyFilter();
        FilteredDiscoveryClient client = new FilteredDiscoveryClient(
                new StaticDiscoveryClient(List.of()),
                List.of(filter));

        List<ServiceInstance> result = client.getInstances("svc");
        assertTrue(result.isEmpty());
        assertEquals(0, filter.applied.get(), "empty discovery result must bypass filters");
    }

    @Test
    public void testAsyncDiscoveryAppliesFilter() throws Exception {
        HealthyFilter filter = new HealthyFilter();
        StaticDiscoveryClient base = new StaticDiscoveryClient(
                List.of(instance("i1", true), instance("i2", false)));
        FilteredDiscoveryClient client = new FilteredDiscoveryClient(base, List.of(filter));

        List<ServiceInstance> result = client.getInstancesAsync("svc")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, result.size(), "async discovery must apply the same filters");

        List<ServiceInstance> again = client.getInstancesAsync("svc")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, again.size());
    }

    @Test
    public void testGetServicesDelegates() {
        FilteredDiscoveryClient client = new FilteredDiscoveryClient(
                new StaticDiscoveryClient(List.of()), List.of());
        assertEquals(List.of("svc"), client.getServices(), "service list must delegate to base client");
    }
}
