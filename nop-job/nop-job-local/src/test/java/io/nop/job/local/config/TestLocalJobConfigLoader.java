package io.nop.job.local.config;

import io.nop.job.api.IJobScheduler;
import io.nop.job.api.JobDetail;
import io.nop.job.api.JobState;
import io.nop.job.api.config.LocalInvokerConfig;
import io.nop.job.api.config.LocalJobConfig;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestLocalJobConfigLoader {

    static class SchedulerSpy implements IJobScheduler {
        boolean activated;
        boolean deactivated;
        JobSpec lastAddedJob;

        @Override
        public void activate() {
            this.activated = true;
        }

        @Override
        public void deactivate() {
            this.deactivated = true;
        }

        @Override
        public void addJob(JobSpec spec, boolean allowUpdate) {
            this.lastAddedJob = spec;
        }

        @Override
        public List<String> getJobNames() {
            return Collections.emptyList();
        }

        @Override
        public JobDetail getJobDetail(String jobName) {
            return null;
        }

        @Override
        public JobState getJobState(String jobName) {
            return null;
        }

        @Override
        public boolean removeJob(String jobName) {
            return false;
        }

        @Override
        public boolean resumeJob(String jobName) {
            return false;
        }

        @Override
        public boolean suspendJob(String jobName) {
            return false;
        }

        @Override
        public boolean cancelJob(String jobName) {
            return false;
        }

        @Override
        public boolean fireNow(String jobName) {
            return false;
        }
    }

    @Test
    void testBuildJobSpecWithFullConfig() {
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        LocalJobConfig config = new LocalJobConfig();
        config.setJobName("myJob");
        config.setDisplayName("My Job");
        config.setDescription("A test job");
        config.setJobGroup("group1");
        config.setOnceTask(false);

        TriggerSpec trigger = new TriggerSpec();
        trigger.setCronExpr("0 0 * * * ?");
        config.setTrigger(trigger);

        LocalInvokerConfig invoker = new LocalInvokerConfig();
        invoker.setBean("myService");
        invoker.setMethod("doWork");
        config.setInvoker(invoker);

        config.setParams(Map.of("key1", "value1"));

        JobSpec spec = loader.buildJobSpec(config);

        assertEquals("myJob", spec.getJobName());
        assertEquals("My Job", spec.getDisplayName());
        assertEquals("A test job", spec.getDescription());
        assertEquals("group1", spec.getJobGroup());
        assertFalse(spec.isOnceTask());
        assertNotNull(spec.getTriggerSpec());
        assertEquals("0 0 * * * ?", spec.getTriggerSpec().getCronExpr());
        assertEquals("beanMethod", spec.getJobInvoker());
        assertNotNull(spec.getJobParams());
        assertEquals("myService", spec.getJobParams().get("beanName"));
        assertEquals("doWork", spec.getJobParams().get("methodName"));
        assertEquals("value1", spec.getJobParams().get("key1"));
    }

    @Test
    void testBuildJobSpecWithMinimalConfig() {
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        LocalJobConfig config = new LocalJobConfig();
        config.setJobName("minimalJob");

        LocalInvokerConfig invoker = new LocalInvokerConfig();
        invoker.setBean("myService");
        invoker.setMethod("doWork");
        config.setInvoker(invoker);

        JobSpec spec = loader.buildJobSpec(config);

        assertEquals("minimalJob", spec.getJobName());
        assertNotNull(spec.getTriggerSpec());
        assertEquals("beanMethod", spec.getJobInvoker());
        assertEquals("myService", spec.getJobParams().get("beanName"));
        assertEquals("doWork", spec.getJobParams().get("methodName"));
    }

    @Test
    void testBuildJobSpecWithMergedParams() {
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        LocalJobConfig config = new LocalJobConfig();
        config.setJobName("paramJob");

        LocalInvokerConfig invoker = new LocalInvokerConfig();
        invoker.setBean("myService");
        invoker.setMethod("doWork");
        config.setInvoker(invoker);

        config.setParams(Map.of("customParam", "customValue"));

        JobSpec spec = loader.buildJobSpec(config);

        assertEquals("myService", spec.getJobParams().get("beanName"));
        assertEquals("doWork", spec.getJobParams().get("methodName"));
        assertEquals("customValue", spec.getJobParams().get("customParam"));
    }

    @Test
    void testBuildJobSpecWithNullTrigger() {
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        LocalJobConfig config = new LocalJobConfig();
        config.setJobName("noTriggerJob");

        LocalInvokerConfig invoker = new LocalInvokerConfig();
        invoker.setBean("myService");
        invoker.setMethod("doWork");
        config.setInvoker(invoker);

        config.setTrigger(null);

        JobSpec spec = loader.buildJobSpec(config);

        assertEquals("noTriggerJob", spec.getJobName());
        assertNotNull(spec.getTriggerSpec());
    }

    @Test
    void testBuildJobSpecWithOnceTask() {
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        LocalJobConfig config = new LocalJobConfig();
        config.setJobName("onceJob");
        config.setOnceTask(true);

        LocalInvokerConfig invoker = new LocalInvokerConfig();
        invoker.setBean("myService");
        invoker.setMethod("doWork");
        config.setInvoker(invoker);

        JobSpec spec = loader.buildJobSpec(config);

        assertEquals("onceJob", spec.getJobName());
        assertTrue(spec.isOnceTask());
    }

    @Test
    void testInitWithNonExistentPathDoesNotActivate() {
        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        loader.setScheduler(scheduler);
        loader.setConfigPath("/nonexistent/scheduler.yaml");
        loader.init();

        assertFalse(scheduler.activated);
    }

    @Test
    void testDestroyDeactivatesScheduler() {
        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        loader.setScheduler(scheduler);
        loader.destroy();

        assertTrue(scheduler.deactivated);
    }
}
