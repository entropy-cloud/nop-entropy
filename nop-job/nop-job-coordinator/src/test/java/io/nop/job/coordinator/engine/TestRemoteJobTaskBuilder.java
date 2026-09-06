package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestRemoteJobTaskBuilder extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private RemoteJobTaskBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RemoteJobTaskBuilder();
        builder.setDaoProvider(daoProvider);
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
        inst.setInstanceId(host + ":" + port);
        inst.setAddr(host);
        inst.setPort(port);
        inst.setHealthy(healthy);
        inst.setEnabled(enabled);
        return inst;
    }

    @Test
    void testBuildsAttributedTaskPerHealthyInstance() {
        List<ServiceInstance> instances = new ArrayList<>();
        instances.add(createInstance("h1", 8080, true, true));
        instances.add(createInstance("h2", 8080, true, true));

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

        assertEquals(2, tasks.size());
        for (NopJobTask task : tasks) {
            assertNotNull(task.getWorkerInstanceId(), "remote task must be attributed");
            assertNotNull(task.getTargetHost(), "targetHost required for nop-svc-target-host routing");
            assertEquals(_NopJobCoreConstants.TASK_STATUS_WAITING, task.getTaskStatus());
        }
        // 归因与 targetHost 一一对应到实例（targetHost 为实例 host:port 全地址）
        assertTrue(tasks.stream().anyMatch(t -> t.getTargetHost().equals("h1:8080")
                && t.getWorkerInstanceId().equals("h1:8080")));
        assertTrue(tasks.stream().anyMatch(t -> t.getTargetHost().equals("h2:8080")
                && t.getWorkerInstanceId().equals("h2:8080")));
    }

    @Test
    void testAllUnhealthyThrowsNoAvailableInstance() {
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
        assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "all-unhealthy must throw (plan 339 semantics), not degrade to single");
    }

    @Test
    void testMissingServiceNameThrows() {
        NopJobFire fire = createFire(null);
        assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "missing serviceName must fail fast");
    }
}
