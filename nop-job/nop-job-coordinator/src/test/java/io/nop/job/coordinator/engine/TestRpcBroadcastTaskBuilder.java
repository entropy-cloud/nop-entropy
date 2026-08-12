package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_DISCOVERY_CLIENT_REQUIRED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_AVAILABLE_INSTANCE;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestRpcBroadcastTaskBuilder extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private RpcBroadcastTaskBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RpcBroadcastTaskBuilder();
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

    /**
     * Plan 339：全不健康不再 fallback，显式抛 ERR_JOB_NO_AVAILABLE_INSTANCE（运行时瞬态）。
     */
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
        NopException ex = assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "all-unhealthy must throw, not fall back to single (plan 339)");
        assertEquals(ERR_JOB_NO_AVAILABLE_INSTANCE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void testMultipleHealthyInstancesGetSeparateTasks() {
        List<ServiceInstance> instances = new ArrayList<>();
        instances.add(createInstance("h1", 8080, true, true));
        instances.add(createInstance("h2", 8081, true, true));
        instances.add(createInstance("h3", 8082, true, true));

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

        NopJobFire fire = createFire("multi-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(3, tasks.size(), "Each healthy instance should get a task");
        for (int i = 0; i < tasks.size(); i++) {
            assertNotNull(tasks.get(i).getTargetHost());
            assertEquals(i + 1, tasks.get(i).getTaskNo());
            assertEquals(3, tasks.get(i).getShardingTotal());
            assertEquals(i, tasks.get(i).getShardingIndex());
            assertEquals("h" + (i + 1) + ":" + (8080 + i), tasks.get(i).getWorkerInstanceId());
        }
    }

    /**
     * AR-99：serviceName 为非 String 类型（如 Boolean）时不抛 ClassCastException，而抛
     * ERR_JOB_SERVICE_NAME_REQUIRED（plan 339 显式失败，不再 fallback）。
     */
    @Test
    void testNonStringServiceNameThrowsServiceNameRequired() {
        builder.setDiscoveryClient(new IDiscoveryClient() {
            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                return List.of(createInstance("h1", 8080, true, true));
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }
        });
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f-ar99");
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("serviceName", true)); // non-String

        NopException ex = assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "non-String serviceName must fail explicitly (no CCE, no fallback)");
        assertEquals(ERR_JOB_SERVICE_NAME_REQUIRED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * Plan 339：discoveryClient 未注入是配置错误，显式抛 ERR_JOB_DISCOVERY_CLIENT_REQUIRED。
     */
    @Test
    void testNullDiscoveryClientThrowsDiscoveryClientRequired() {
        builder.setDiscoveryClient(null);

        NopJobFire fire = createFire("fallback-svc");
        NopException ex = assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "null discoveryClient must throw, not fall back to single (plan 339)");
        assertEquals(ERR_JOB_DISCOVERY_CLIENT_REQUIRED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * Plan 339：serviceName 缺失显式抛 ERR_JOB_SERVICE_NAME_REQUIRED。
     */
    @Test
    void testMissingServiceNameThrowsServiceNameRequired() {
        builder.setDiscoveryClient(new IDiscoveryClient() {
            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }
        });

        NopJobFire fire = createFire(null);
        NopException ex = assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "missing serviceName must throw, not fall back (plan 339)");
        assertEquals(ERR_JOB_SERVICE_NAME_REQUIRED.getErrorCode(), ex.getErrorCode());
    }
}
