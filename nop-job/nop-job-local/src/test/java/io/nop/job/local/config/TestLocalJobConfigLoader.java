package io.nop.job.local.config;

import io.nop.job.api.IJobScheduler;
import io.nop.job.api.JobDetail;
import io.nop.job.api.JobState;
import io.nop.job.api.config.LocalInvokerConfig;
import io.nop.job.api.config.LocalJobConfig;
import io.nop.job.api.config.LocalSchedulerConfig;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestLocalJobConfigLoader {

    static class SchedulerSpy implements IJobScheduler {
        boolean activated;
        boolean deactivated;
        JobSpec lastAddedJob;
        final List<JobSpec> addedJobs = new ArrayList<>();
        final List<String> addedJobNames = new ArrayList<>();

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
            this.addedJobs.add(spec);
            this.addedJobNames.add(spec.getJobName());
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

    @Test
    void testDisabledJobIsSkipped() {
        LocalJobConfig disabled = jobConfig("disabled-job");
        disabled.setEnabled(false);

        LocalSchedulerConfig config = new LocalSchedulerConfig();
        config.setEnabled(true);
        config.setJobs(List.of(disabled));

        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = newLoader(scheduler);
        loader.applySchedulerConfig(config);

        assertTrue(scheduler.activated);
        assertTrue(scheduler.addedJobs.isEmpty(), "disabled job must not be registered");
    }

    @Test
    void testEnabledJobIsRegistered() {
        LocalJobConfig enabled = jobConfig("enabled-job");
        enabled.setEnabled(true);

        LocalSchedulerConfig config = new LocalSchedulerConfig();
        config.setEnabled(true);
        config.setJobs(List.of(enabled));

        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = newLoader(scheduler);
        loader.applySchedulerConfig(config);

        assertEquals(List.of("enabled-job"), scheduler.addedJobNames);
    }

    @Test
    void testDefaultEnabledIsFalse() {
        // 不显式 setEnabled 时，Java 默认值应为 false，确保所有 job 必须显式开启
        LocalJobConfig job = jobConfig("default-job");
        assertFalse(job.isEnabled(), "LocalJobConfig.enabled must default to false");

        LocalSchedulerConfig config = new LocalSchedulerConfig();
        config.setEnabled(true);
        config.setJobs(List.of(job));

        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = newLoader(scheduler);
        loader.applySchedulerConfig(config);

        assertTrue(scheduler.addedJobs.isEmpty(), "job without explicit enabled=true must not be registered");
    }

    @Test
    void testMixedEnabledDisabledJobs() {
        LocalJobConfig a = jobConfig("job-a");
        a.setEnabled(true);
        LocalJobConfig b = jobConfig("job-b");
        b.setEnabled(false);
        LocalJobConfig c = jobConfig("job-c");
        // c 不显式 setEnabled，应被视为 false

        LocalSchedulerConfig config = new LocalSchedulerConfig();
        config.setEnabled(true);
        config.setJobs(List.of(a, b, c));

        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = newLoader(scheduler);
        loader.applySchedulerConfig(config);

        assertEquals(List.of("job-a"), scheduler.addedJobNames, "only job-a should be registered");
    }

    @Test
    void testDuplicateJobNameIsSkipped() {
        // 同名 job 第二个应被跳过（覆盖 registerJob 的 job-duplicate 分支）
        LocalJobConfig first = jobConfig("dup");
        first.setEnabled(true);
        LocalJobConfig second = jobConfig("dup");
        second.setEnabled(true);

        LocalSchedulerConfig config = new LocalSchedulerConfig();
        config.setEnabled(true);
        config.setJobs(List.of(first, second));

        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = newLoader(scheduler);
        loader.applySchedulerConfig(config);

        assertEquals(1, scheduler.addedJobs.size(), "duplicate jobName must be deduplicated");
    }

    @Test
    void testEmptyJobNameIsSkipped() {
        LocalJobConfig noName = jobConfig("");
        noName.setEnabled(true);

        LocalSchedulerConfig config = new LocalSchedulerConfig();
        config.setEnabled(true);
        config.setJobs(List.of(noName));

        SchedulerSpy scheduler = new SchedulerSpy();
        LocalJobConfigLoader loader = newLoader(scheduler);
        loader.applySchedulerConfig(config);

        assertTrue(scheduler.addedJobs.isEmpty(), "job without jobName must be skipped");
    }

    @Test
    void testScanJobConfigsReturnsEmptyWhenVfsUnavailable() {
        // VFS 未初始化时，scanJobConfigs 应静默返回空列表而不是抛异常
        LocalJobConfigLoader loader = newLoader(new SchedulerSpy());
        List<io.nop.job.api.config.LocalJobConfig> jobs = loader.scanJobConfigs("/some/nonexistent/dir/");
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }

    private static LocalJobConfigLoader newLoader(SchedulerSpy scheduler) {
        LocalJobConfigLoader loader = new LocalJobConfigLoader();
        loader.setScheduler(scheduler);
        return loader;
    }

    private static LocalJobConfig jobConfig(String name) {
        LocalJobConfig cfg = new LocalJobConfig();
        cfg.setJobName(name);
        LocalInvokerConfig invoker = new LocalInvokerConfig();
        invoker.setBean("myService");
        invoker.setMethod("doWork");
        cfg.setInvoker(invoker);
        return cfg;
    }
}