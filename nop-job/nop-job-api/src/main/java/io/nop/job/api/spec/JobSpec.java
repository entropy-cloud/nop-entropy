/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api.spec;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.job.api.execution.IJobInvoker;

import java.util.Map;

@DataBean
public class JobSpec {
    private String jobName;
    private long jobVersion;

    /**
     * 用于逻辑分组。jobName需要是全局唯一的，不同分组下的jobName也不允许重复
     */
    private String jobGroup;

    private String displayName;
    private String description;

    /**
     * 对应BeanContainer中的某个已注册的bean。一般情况下beanName= 'jobInvoker_'+jobInvoker。
     * 这个bean必须是静态单例，且为{@link IJobInvoker}类型或者具有名为invoke的方法，且此方法可以通过反射机制 封装为IJobInvoker接口。
     */
    private String jobInvoker;
    private Map<String, Object> jobParams;

    private TriggerSpec triggerSpec;

    /**
     * 任务执行完毕后是否自动从JobScheduler中删除
     */
    private boolean onceTask;

    public long getJobVersion() {
        return jobVersion;
    }

    public void setJobVersion(long jobVersion) {
        this.jobVersion = jobVersion;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getJobInvoker() {
        return jobInvoker;
    }

    public void setJobInvoker(String jobInvoker) {
        this.jobInvoker = jobInvoker;
    }

    public Map<String, Object> getJobParams() {
        return jobParams;
    }

    public void setJobParams(Map<String, Object> jobParams) {
        this.jobParams = jobParams;
    }

    public TriggerSpec getTriggerSpec() {
        return triggerSpec;
    }

    public void setTriggerSpec(TriggerSpec triggerSpec) {
        this.triggerSpec = triggerSpec;
    }

    public boolean isOnceTask() {
        return onceTask;
    }

    public void setOnceTask(boolean onceTask) {
        this.onceTask = onceTask;
    }
}
