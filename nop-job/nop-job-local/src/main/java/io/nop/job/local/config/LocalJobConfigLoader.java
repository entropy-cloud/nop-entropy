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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.job.api.JobApiErrors.ARG_CONFIG_PATH;
import static io.nop.job.api.JobApiErrors.ERR_JOB_LOCAL_CONFIG_INVALID;

public class LocalJobConfigLoader {
    static final Logger LOG = LoggerFactory.getLogger(LocalJobConfigLoader.class);

    /**
     * 每个 job 一个文件的目录约定，与 scheduler.yaml 同目录。
     * 模块往这里贡献 <jobName>.job.yaml，Loader 启动时按后缀扫描。
     * scheduler.yaml 不以 .job.yaml 结尾，不会被误扫描。
     * 注意：路径不以 / 结尾——VirtualFileSystem.getAllResources 拒绝尾部 /（nop.err.core.resource.invalid-path）。
     */
    static final String DEFAULT_JOB_DIR = "/nop/job/conf";
    static final String JOB_FILE_SUFFIX = ".job.yaml";

    private IJobScheduler scheduler;
    private String configPath = "/nop/job/conf/scheduler.yaml";
    private String jobDir = DEFAULT_JOB_DIR;

    @Inject
    public void setScheduler(IJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Inject
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public void setJobDir(String jobDir) {
        this.jobDir = jobDir;
    }

    @PostConstruct
    public void init() {
        LocalSchedulerConfig config = loadSchedulerConfig();
        if (config == null) {
            return;
        }
        if (!config.isEnabled()) {
            LOG.info("nop.job.local-config-loader.disabled: path={}", configPath);
            return;
        }

        scheduler.activate();

        Set<String> registered = new HashSet<>();

        // 旧机制（兼容期内保留）：scheduler.yaml 内联的 jobs 列表
        if (config.getJobs() != null) {
            for (LocalJobConfig jc : config.getJobs()) {
                registerJob(jc, registered);
            }
        }

        // 新机制：扫描 <jobDir>/<jobName>.job.yaml
        for (LocalJobConfig jc : scanJobConfigs(jobDir)) {
            registerJob(jc, registered);
        }
    }

    private LocalSchedulerConfig loadSchedulerConfig() {
        if (StringHelper.isEmpty(configPath)) {
            LOG.info("nop.job.local-config-loader.config-path-empty");
            return null;
        }

        IResource resource;
        try {
            resource = VirtualFileSystem.instance().getResource(configPath);
        } catch (Exception e) {
            LOG.info("nop.job.local-config-loader.vfs-not-available: path={}", configPath);
            return null;
        }

        if (!resource.exists()) {
            LOG.info("nop.job.local-config-loader.config-not-found: path={}", configPath);
            return null;
        }

        try {
            return JsonTool.loadDeltaBeanFromResource(resource, LocalSchedulerConfig.class);
        } catch (Exception e) {
            throw new NopException(ERR_JOB_LOCAL_CONFIG_INVALID, e)
                    .param(ARG_CONFIG_PATH, configPath);
        }
    }

    /**
     * 扫描 job 目录，加载所有 <jobName>.job.yaml 为 {@link LocalJobConfig}。
     * 文件按路径字典序处理，保证加载顺序稳定。目录不存在时静默返回空列表。
     */
    List<LocalJobConfig> scanJobConfigs(String dir) {
        List<LocalJobConfig> result = new ArrayList<>();
        if (StringHelper.isEmpty(dir)) {
            return result;
        }

        Collection<? extends IResource> resources;
        try {
            resources = VirtualFileSystem.instance().getAllResources(dir, JOB_FILE_SUFFIX);
        } catch (Exception e) {
            LOG.info("nop.job.local-config-loader.job-dir-not-available: dir={}", dir);
            return result;
        }
        if (resources == null || resources.isEmpty()) {
            return result;
        }

        List<IResource> sorted = new ArrayList<>(resources);
        sorted.sort(Comparator.comparing(IResource::getStdPath));

        for (IResource resource : sorted) {
            if (!resource.exists()) {
                continue;
            }
            try {
                LocalJobConfig jc = JsonTool.loadDeltaBeanFromResource(resource, LocalJobConfig.class);
                result.add(jc);
                LOG.info("nop.job.local-config-loader.job-file-loaded: path={}", resource.getStdPath());
            } catch (Exception e) {
                LOG.error("nop.job.local-config-loader.job-file-load-failed: path={}", resource.getStdPath(), e);
            }
        }
        return result;
    }

    private void registerJob(LocalJobConfig jobConfig, Set<String> registered) {
        String jobName = jobConfig.getJobName();
        if (StringHelper.isEmpty(jobName)) {
            LOG.warn("nop.job.local-config-loader.job-name-empty: skip");
            return;
        }
        if (!jobConfig.isEnabled()) {
            LOG.info("nop.job.local-config-loader.job-disabled: jobName={}", jobName);
            return;
        }
        if (!registered.add(jobName)) {
            LOG.warn("nop.job.local-config-loader.job-duplicate: jobName={}", jobName);
            return;
        }
        JobSpec spec = buildJobSpec(jobConfig);
        try {
            scheduler.addJob(spec, true);
            LOG.info("nop.job.local-config-loader.job-registered: jobName={}", jobName);
        } catch (Exception e) {
            LOG.error("nop.job.local-config-loader.job-register-failed: jobName={}", jobName, e);
        }
    }

    /**
     * 测试入口：跳过 VFS / 资源加载，直接对已构造好的 {@link LocalSchedulerConfig} 执行 jobs 注册逻辑。
     * 仅 package-private，不作为公开 API。
     */
    void applySchedulerConfig(LocalSchedulerConfig config) {
        if (!config.isEnabled()) {
            LOG.info("nop.job.local-config-loader.disabled");
            return;
        }
        scheduler.activate();
        Set<String> registered = new HashSet<>();
        if (config.getJobs() != null) {
            for (LocalJobConfig jc : config.getJobs()) {
                registerJob(jc, registered);
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
