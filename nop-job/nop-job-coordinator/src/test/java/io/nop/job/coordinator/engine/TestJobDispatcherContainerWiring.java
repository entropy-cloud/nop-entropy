package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 339 容器级接线验证（R1 Blocker/R2 N3 落点）：经真实 {@code app-engine.beans.xml} 装配启动 IoC，
 * 断言 dispatcher bean 的 {@code taskBuilders} map 由 {@code <ioc:collect-beans as-map .../>} 实际注入
 * （非空壳），key = 去前缀 bean id，类型正确。接线断言不触 DB，在本环境（H2 pre-existing 失败）可运行。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobDispatcherContainerWiring extends JunitBaseTestCase {

    @Inject
    IJobDispatcherScanner dispatcher;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    @Test
    public void testTaskBuildersMapWiredFromCollectBeans() {
        JobDispatcherScannerImpl impl = assertInstanceOf(JobDispatcherScannerImpl.class, dispatcher);
        Map<String, IJobTaskBuilder> builders = impl.getTaskBuilders();

        assertEquals(4, builders.size(),
                "collect-beans must collect exactly the 4 registered nopJobTaskBuilder_* beans");
        assertTrue(builders.containsKey("single"), "key = bean id minus prefix ('single')");
        assertTrue(builders.containsKey("partition"));
        assertTrue(builders.containsKey("broadcast"));
        assertTrue(builders.containsKey("bestFit"));

        assertInstanceOf(DefaultJobTaskBuilder.class, builders.get("single"),
                "nopJobTaskBuilder_single must be a DefaultJobTaskBuilder");
        assertInstanceOf(RpcBroadcastTaskBuilder.class, builders.get("broadcast"));
        assertInstanceOf(PartitionTaskBuilder.class, builders.get("partition"));
        assertInstanceOf(AdaptiveJobTaskBuilder.class, builders.get("bestFit"));
    }

    /**
     * 端到端（Rule 22）：真实 beans.xml 装配下 single 路径 fire → scanOnce → task 落库。
     * 本环境被 pre-existing H2 失败（vendorCode=42104 "Table not found"，见
     * {@code ai-dev/logs/2026/08-11.md:95}，git stash 在 master 基线复现，与 plan 339 无关）阻塞；
     * 同断言已由 {@code TestJobCoordinatorScanner.testScheduleToFireToTask} 在健康环境覆盖
     * （该测试已适配 setTaskBuilders，见 plan 339 Phase 1 EC4）。恢复环境后移除 @Disabled 即可启用。
     */
    @Test
    @Disabled("blocked by pre-existing H2 env failure (vendorCode=42104, Table not found); see plan 339 Phase 1 EC3 fallback note")
    public void testSingleModeFireDispatchEndToEndPersistence() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId("wiring-sched");
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName("wiring-job");
        schedule.setScheduleStatus(10);
        schedule.setExecutorKind("test");
        schedule.setNextFireTime(new Timestamp(System.currentTimeMillis() + 60000));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId("default");
        fire.setGroupId("default");
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(new Timestamp(System.currentTimeMillis() - 1000));
        fire.setFireStatus(0);
        fire.setPartitionIndex((short) 1);
        fire.setVersion(0L);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        JobDispatcherScannerImpl impl = assertInstanceOf(JobDispatcherScannerImpl.class, dispatcher);
        impl.setBatchSize(10);
        impl.setLockTimeoutMs(1000);
        impl.setAssignedPartitions("1");
        impl.scanOnce();

        assertEquals(1, taskStore.findTasksByFireId(fire.getJobFireId()).size(),
                "single-mode fire must produce exactly one persisted task via real beans.xml wiring");
    }
}
