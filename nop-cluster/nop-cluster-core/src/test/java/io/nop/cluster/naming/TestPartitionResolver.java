/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.naming;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestPartitionResolver {

    private static final String MY_HOST_ID = AppConfig.hostId();

    private PartitionResolver resolver;
    private MockNamingService namingService;

    @BeforeEach
    void setUp() {
        resolver = new PartitionResolver();
        namingService = new MockNamingService();
        resolver.setNamingService(namingService);
        resolver.setEnableCluster(true);
    }

    @Test
    void testStaticPartitionTakesPrecedenceOverCluster() {
        resolver.setAssignedPartitions("1,10");
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));

        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result);
        assertEquals("1,10", result.toString());
    }

    @Test
    void testClusterDisabledReturnsNull() {
        resolver.setEnableCluster(false);
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));

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
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));

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
        namingService.setInstances(List.of(createInstance("other-node", "host-a", 8080)));

        assertNull(resolver.resolvePartitions());
    }

    @Test
    void testResolvesImmediatelyWithoutStabilizationDelay() {
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));
        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result, "First call should resolve immediately without stabilization delay");
    }

    @Test
    void testCacheReturnsSameResultWithinTtl() {
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));

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

    @Test
    void testExceptionPropagatesTypedAndNotCached() {
        // 4.2-C8-006（plan 2559 裁定 H/R25-3 fail 语义）：异常路径 typed 上抛、不缓存异常（快速失败）
        namingService.throwOnGet = new IllegalStateException("naming service down");
        NopException err = assertThrows(NopException.class, () -> resolver.resolvePartitions(),
                "naming service failure must propagate typed, not be cached");
        assertNotNull(err.getCause());

        // 异常不写负缓存：恢复后立即可解析
        namingService.throwOnGet = null;
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));
        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result, "exception path must not poison the cache (R25-3)");
    }

    @Test
    void testEmptyInstancesNegativeCachedWithinTtl() {
        // 4.2-C8-006（plan 2559 裁定 H）：空列表 → 负缓存短 TTL，命中期内不查 NamingService
        namingService.setInstances(Collections.emptyList());
        assertNull(resolver.resolvePartitions());
        assertEquals(1, namingService.getCalls());

        // 负缓存命中期内：即使实例恢复也直接返回空，且不再触发远程查询
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));
        assertNull(resolver.resolvePartitions());
        assertEquals(1, namingService.getCalls(),
                "negative cache hit must skip the naming service lookup");
    }

    @Test
    void testNegativeCacheExpiresAndRecovers() {
        resolver.setNegativeCacheTtlMs(0);
        namingService.setInstances(Collections.emptyList());
        assertNull(resolver.resolvePartitions());

        // TTL=0 → 负缓存立即过期，实例恢复后可解析
        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));
        IntRangeSet result = resolver.resolvePartitions();
        assertNotNull(result, "expired negative cache must re-resolve");
    }

    @Test
    void testMissNegativeCachedWithinTtl() {
        // 未命中（本实例不在候选集）同走负缓存（裁定 H）
        resolver.setNegativeCacheTtlMs(60000);
        namingService.setInstances(List.of(createInstance("other-node", "host-a", 8080)));
        assertNull(resolver.resolvePartitions());

        namingService.setInstances(List.of(createInstance(MY_HOST_ID, "host-a", 8080)));
        assertNull(resolver.resolvePartitions(),
                "miss-negative-cache must hold within TTL even after instances recover");
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
        private int getCalls;
        RuntimeException throwOnGet;

        int getCalls() {
            return getCalls;
        }

        void setInstances(List<ServiceInstance> instances) {
            this.instances = instances != null ? new ArrayList<>(instances) : null;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            getCalls++;
            if (throwOnGet != null)
                throw throwOnGet;
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
