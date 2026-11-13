package io.nop.cluster.nacos;

import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-001 (plan 2521): content-change events must REBUILD the ServiceInstance (copy-
 * on-write element) instead of mutating the shared object in place — old references
 * keep a stable view, the `_host` lazy cache of the fresh current reference reflects
 * the new endpoint, and locally maintained state (attrs/cleanupTasks) survives the
 * rebuild. C1-003: a snapshot delivered by getAllInstances after an event has already
 * landed must not overwrite the newer event state (event-state guard).
 */
public class TestNacosInstanceRebuildAndSnapshotGuard {

    private static Instance inst(String id, String ip, int port) {
        Instance instance = new Instance();
        instance.setInstanceId(id);
        instance.setIp(ip);
        instance.setPort(port);
        instance.setServiceName("svc");
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(true);
        return instance;
    }

    @Test
    public void contentChangeRebuildsInstanceAndPreservesLocalState() {
        NacosNamingService naming = new NacosNamingService();
        ServiceInfo info = new ServiceInfo();
        NacosNamingService.ServiceInfoListener listener = naming.new ServiceInfoListener("svc", info);

        listener.updateInstances(Arrays.asList(inst("i1", "10.0.0.1", 8001)));
        ServiceInstance oldRef = info.getServiceInstances().get(0);
        oldRef.setAttribute("localKey", "localValue");
        Runnable cleanup = () -> {
        };
        oldRef.addCleanupTask(cleanup);
        String primedHost = oldRef.getHost(); // prime the _host lazy cache
        assertEquals("10.0.0.1:8001", primedHost);

        // content change: same instanceId, migrated endpoint
        listener.updateInstances(Arrays.asList(inst("i1", "10.0.0.2", 8002)));

        // (a) old reference keeps a stable, self-consistent view (C10-006 suggestion (1))
        assertEquals("10.0.0.1", oldRef.getAddr(),
                "C1-001: in-place rewrite detected — the old reference must keep the old address");
        assertEquals(8001, oldRef.getPort(), "old reference port must stay stable");

        // (b) the fresh current reference resolves the NEW endpoint (C10-006 suggestion (2));
        // the old reference was primed pre-migration, the new one must compute from new addr/port
        ServiceInstance currentRef = info.getServiceInstances().get(0);
        assertNotSame(oldRef, currentRef,
                "C1-001: content change must rebuild the ServiceInstance object");
        assertEquals("10.0.0.2:8002", currentRef.getHost(),
                "C1-001: _host of the rebuilt instance must reflect the migrated endpoint");

        // modifyIndex increments only on real content change and carries on the rebuild
        assertEquals(1, currentRef.getModifyIndex(), "rebuild must carry modifyIndex = old + 1");

        // locally maintained state survives the rebuild (adjudication ② carry semantics)
        assertEquals("localValue", currentRef.getAttribute("localKey"),
                "attrs must be carried across the rebuild");
        assertTrue(currentRef.getCleanupTasks().contains(cleanup),
                "cleanupTasks must be carried across the rebuild");

        // unchanged event: no rebuild, no index bump
        listener.updateInstances(Arrays.asList(inst("i1", "10.0.0.2", 8002)));
        ServiceInstance sameRef = info.getServiceInstances().get(0);
        assertSame(currentRef, sameRef, "unchanged content must keep the same object");
        assertEquals(1, sameRef.getModifyIndex(), "unchanged content must not bump modifyIndex");
    }

    /**
     * 红③ (C1-003 / C10-005 half): an event landing between getAllInstances returning
     * and initInstances executing must win — the stale snapshot must not overwrite it.
     */
    @Test
    public void eventLandingDuringInitWinsOverStaleSnapshot() throws Exception {
        NacosNamingService naming = new NacosNamingService() {
            @Override
            public com.alibaba.nacos.api.naming.NamingService getNamingService() {
                return stubNamingService();
            }
        };

        // drive the production path once: subscribe + init snapshot (legitimate initial state)
        naming.getInstances("svc");
        ServiceInfo info = infoOf(naming, "svc");
        NacosNamingService.ServiceInfoListener listener = info.getListener();
        assertNotNull(listener, "listener must be registered on the service info");

        // event B (new state) lands, THEN the stale init snapshot A is applied —
        // the deterministic form of the :191→:192 window
        listener.onEvent(new NamingEvent("svc", Arrays.asList(inst("i1", "10.0.0.9", 9009))));
        listener.initInstances(Arrays.asList(inst("i1", "10.0.0.1", 8001)));

        ServiceInstance current = naming.getInstances("svc").get(0);
        assertEquals("10.0.0.9", current.getAddr(),
                "C1-003: init snapshot A must not overwrite the event state B that already landed");
        assertNotEquals("10.0.0.1", current.getAddr(),
                "stale snapshot won — event-state guard missing");
    }

    /**
     * R2-18 zero-regression pin: reads the {@code instanceCache} field by NAME via
     * reflection. Plan 2559 (裁定 F/A②) changed the field TYPE from ConcurrentHashMap to
     * LocalCache (bounded LRU, destroy-on-remove) — the pin is adapted same-diff (field
     * name unchanged, same lookup semantics), NOT weakened.
     */
    private static ServiceInfo infoOf(NacosNamingService naming, String serviceName) throws Exception {
        java.lang.reflect.Field field = NacosNamingService.class.getDeclaredField("instanceCache");
        field.setAccessible(true);
        io.nop.commons.cache.LocalCache<String, ServiceInfo> cache =
                (io.nop.commons.cache.LocalCache<String, ServiceInfo>) field.get(naming);
        return cache.getIfPresent(serviceName);
    }

    /**
     * JDK-proxy NamingService stub: only subscribe/unsubscribe/getAllInstances carry
     * behavior; the production path under test touches exactly those three.
     */
    @SuppressWarnings("unchecked")
    static com.alibaba.nacos.api.naming.NamingService stubNamingService() {
        return (com.alibaba.nacos.api.naming.NamingService) java.lang.reflect.Proxy.newProxyInstance(
                TestNacosInstanceRebuildAndSnapshotGuard.class.getClassLoader(),
                new Class[]{com.alibaba.nacos.api.naming.NamingService.class},
                (proxy, method, args) -> {
                    if ("getAllInstances".equals(method.getName()))
                        return java.util.Collections.emptyList();
                    return null;
                });
    }
}
