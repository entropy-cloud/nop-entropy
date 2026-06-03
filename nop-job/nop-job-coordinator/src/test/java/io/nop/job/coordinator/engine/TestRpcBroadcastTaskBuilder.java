package io.nop.job.coordinator.engine;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRpcBroadcastTaskBuilder {

    private RpcBroadcastTaskBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RpcBroadcastTaskBuilder();
    }

    private NopJobFire createFire(String serviceName) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.getJobParamsSnapshotComponent().set_jsonValue(
                serviceName != null ? Map.of("serviceName", serviceName) : Map.of());
        return fire;
    }

    private ServiceInstance createInstance(String host, int port, boolean healthy, boolean enabled) {
        ServiceInstance inst = new ServiceInstance();
        inst.setAddr(host);
        inst.setPort(port);
        inst.setHealthy(healthy);
        inst.setEnabled(enabled);
        return inst;
    }

    @Test
    void testFiltersUnhealthyInstances() {
        List<ServiceInstance> instances = new ArrayList<>();
        instances.add(createInstance("h1", 8080, true, true));
        instances.add(createInstance("h2", 8080, false, true));
        instances.add(createInstance("h3", 8080, true, false));
        instances.add(createInstance("h4", 8080, false, false));

        builder.setDiscoveryClient(new IDiscoveryClient() {
            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                return instances;
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }
        });

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(1, tasks.size(), "Only healthy+enabled instances should get tasks");
        assertTrue(tasks.get(0).getTargetHost().contains("h1"));
    }

    @Test
    void testFallsBackWhenAllUnhealthy() {
        List<ServiceInstance> instances = new ArrayList<>();
        instances.add(createInstance("h1", 8080, false, true));

        builder.setDiscoveryClient(new IDiscoveryClient() {
            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                return instances;
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }
        });

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(1, tasks.size(), "Should fallback to DefaultJobTaskBuilder when all unhealthy");
        assertEquals(1, tasks.get(0).getTaskNo());
    }
}
