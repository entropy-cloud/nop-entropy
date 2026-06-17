package io.nop.job.local.config;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.config.LocalInvokerConfig;
import io.nop.job.api.config.LocalJobConfig;
import io.nop.job.api.config.LocalSchedulerConfig;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.job.api.JobApiErrors.ARG_CONFIG_PATH;
import static io.nop.job.api.JobApiErrors.ERR_JOB_LOCAL_CONFIG_INVALID;

public class LocalJobConfigLoader {
    static final Logger LOG = LoggerFactory.getLogger(LocalJobConfigLoader.class);

    private IJobScheduler scheduler;
    private String configPath = "/nop/job/conf/scheduler.yaml";

    @Inject
    public void setScheduler(IJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Inject
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    @PostConstruct
    public void init() {
        if (StringHelper.isEmpty(configPath)) {
            LOG.info("nop.job.local-config-loader.config-path-empty");
            return;
        }

        IResource resource;
        try {
            resource = VirtualFileSystem.instance().getResource(configPath);
        } catch (Exception e) {
            LOG.info("nop.job.local-config-loader.vfs-not-available: path={}", configPath);
            return;
        }

        if (!resource.exists()) {
            LOG.info("nop.job.local-config-loader.config-not-found: path={}", configPath);
            return;
        }

        LocalSchedulerConfig config;
        try {
            config = JsonTool.loadDeltaBeanFromResource(resource, LocalSchedulerConfig.class);
        } catch (Exception e) {
            throw new NopException(ERR_JOB_LOCAL_CONFIG_INVALID, e)
                    .param(ARG_CONFIG_PATH, configPath);
        }

        if (!config.isEnabled()) {
            LOG.info("nop.job.local-config-loader.disabled: path={}", configPath);
            return;
        }

        List<LocalJobConfig> jobs = config.getJobs();
        if (jobs == null || jobs.isEmpty()) {
            LOG.info("nop.job.local-config-loader.no-jobs: path={}", configPath);
            return;
        }

        scheduler.activate();

        for (LocalJobConfig jobConfig : jobs) {
            JobSpec spec = buildJobSpec(jobConfig);
            try {
                scheduler.addJob(spec, true);
                LOG.info("nop.job.local-config-loader.job-registered: jobName={}", spec.getJobName());
            } catch (Exception e) {
                LOG.error("nop.job.local-config-loader.job-register-failed: jobName={}", spec.getJobName(), e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        LOG.info("nop.job.local-config-loader.deactivating");
        scheduler.deactivate();
    }

    JobSpec buildJobSpec(LocalJobConfig config) {
        JobSpec spec = new JobSpec();
        spec.setJobName(config.getJobName());
        spec.setDisplayName(config.getDisplayName());
        spec.setDescription(config.getDescription());
        spec.setJobGroup(config.getJobGroup());
        spec.setOnceTask(config.isOnceTask());

        TriggerSpec triggerSpec = config.getTrigger();
        if (triggerSpec == null) {
            triggerSpec = new TriggerSpec();
        }
        spec.setTriggerSpec(triggerSpec);

        spec.setJobInvoker("beanMethod");

        Map<String, Object> jobParams = new HashMap<>();
        LocalInvokerConfig invokerConfig = config.getInvoker();
        if (invokerConfig != null) {
            jobParams.put("beanName", invokerConfig.getBean());
            jobParams.put("methodName", invokerConfig.getMethod());
        }
        if (config.getParams() != null) {
            jobParams.putAll(config.getParams());
        }
        spec.setJobParams(jobParams);

        return spec;
    }
}
