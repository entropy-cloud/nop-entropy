package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestJobPartitionResolver {

    private static final String MY_HOST_ID = AppConfig.hostId();

    private JobPartitionResolver resolver;
    private MockNamingService namingService;

    @BeforeEach
    void setUp() {
        resolver = new JobPartitionResolver();
        namingService = new MockNamingService();
        resolver.setNamingService(namingService);
        resolver.setEnableCluster(true);
    }

    @Test
    void testStaticPartitionTakesPrecedenceOverCluster() {
        resolver.setAssignedPartitions("1,10");
        namingService.setInstances(List.of(
                createInstance(MY_HOST_ID, "host-a", 8080)
        ));

        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result);
        assertEquals("1,10", result.toString());
    }

    @Test
    void testClusterDisabledReturnsNull() {
        resolver.setEnableCluster(false);
        namingService.setInstances(List.of(
                createInstance(MY_HOST_ID, "host-a", 8080)
        ));

        assertNull(resolver.resolvePartitions());
    }

    @Test
    void testNoNamingServiceReturnsNull() {
        resolver.setNamingService(null);
        resolver.setEnableCluster(true);
        assertNull(resolver.resolvePartitions());
    }

    @Test
    void testEmptyInstancesReturnsNull() {
        namingService.setInstances(Collections.emptyList());
        assertNull(resolver.resolvePartitions());
    }

    @Test
    void testNullInstancesReturnsNull() {
        namingService.setInstances(null);
        assertNull(resolver.resolvePartitions());
    }

    @Test
    void testSingleInstanceGetsFullShortRange() {
        namingService.setInstances(List.of(
                createInstance(MY_HOST_ID, "host-a", 8080)
        ));

        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result);
        assertEquals(IntRangeBean.shortRange().toRangeSet().toString(), result.toString());
    }

    @Test
    void testMultipleInstancesGetPartitioned() {
        namingService.setInstances(List.of(
                createInstance("a-node", "host-a", 8080),
                createInstance(MY_HOST_ID, "host-b", 8080)
        ));

        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result);
        assertEquals(1, result.getRanges().size());
    }

    @Test
    void testMyInstanceNotFoundReturnsNull() {
        namingService.setInstances(List.of(
                createInstance("other-node", "host-a", 8080)
        ));

        assertNull(resolver.resolvePartitions());
    }

    @Test
    void testResolvesImmediatelyWithoutStabilizationDelay() {
        namingService.setInstances(List.of(
                createInstance(MY_HOST_ID, "host-a", 8080)
        ));
        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result, "First call should resolve immediately without stabilization delay");
    }

    @Test
    void testCacheReturnsSameResultWithinTtl() {
        namingService.setInstances(List.of(
                createInstance(MY_HOST_ID, "host-a", 8080)
        ));

        IntRangeSet first = resolver.resolvePartitions();
        assertNotNull(first);

        namingService.setInstances(List.of(
                createInstance("a-node", "host-a", 8080),
                createInstance(MY_HOST_ID, "host-b", 8080)
        ));

        IntRangeSet second = resolver.resolvePartitions();
        assertNotNull(second);
        assertEquals(first.toString(), second.toString(),
                "Cached result should be returned within TTL even if naming service changes");
    }

    private ServiceInstance createInstance(String instanceId, String host, int port) {
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId(instanceId);
        inst.setAddr(host);
        inst.setPort(port);
        inst.setHealthy(true);
        inst.setEnabled(true);
        return inst;
    }

    private static class MockNamingService implements INamingService {
        private List<ServiceInstance> instances;

        void setInstances(List<ServiceInstance> instances) {
            this.instances = instances != null ? new ArrayList<>(instances) : null;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return instances != null ? new ArrayList<>(instances) : null;
        }

        @Override
        public void registerInstance(ServiceInstance instance) {
        }

        @Override
        public void unregisterInstance(ServiceInstance instance) {
        }

        @Override
        public void updateInstance(ServiceInstance instance) {
        }

        @Override
        public List<String> getServices() {
            return Collections.emptyList();
        }
    }
}
