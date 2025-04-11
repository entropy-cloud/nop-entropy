/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.spec.JobSpec;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.nop.job.api.JobApiErrors.ARG_JOB_NAME;
import static io.nop.job.api.JobApiErrors.ERR_JOB_UNKNOWN_JOB;

public interface IJobScheduler {

    List<String> getJobNames();

    @Nullable
    JobDetail getJobDetail(@Name("jobName") String jobName);

    /**
     * 得到指定任务的状态信息
     *
     * @param ignoreUnknown 当jobName对应的任务不存在时是否抛出异常
     */
    default List<JobDetail> getJobDetails(@Name("jobNames") Set<String> jobNames,
                                          @Name("ignoreUnknown") boolean ignoreUnknown) {
        List<JobDetail> ret = new ArrayList<>(jobNames.size());
        for (String jobName : jobNames) {
            JobDetail detail = getJobDetail(jobName);
            if (detail == null) {
                if (ignoreUnknown)
                    continue;
                throw new NopException(ERR_JOB_UNKNOWN_JOB).param(ARG_JOB_NAME, jobName);
            }
            ret.add(detail);
        }
        return ret;
    }

    /**
     * 加入任务，并自动启动trigger
     */
    void addJob(@Name("jobSpec") JobSpec spec,
                @Name("allowUpdate") boolean allowUpdate);

    default void addJobs(@Name("specs") Collection<JobSpec> specs,
                         @Name("allowUpdate") boolean allowUpdate) {
        for (JobSpec spec : specs) {
            addJob(spec, allowUpdate);
        }
    }

    /**
     * 删除任务。如果任务当前处于运行状态，则会先取消任务
     */
    boolean removeJob(@Name("jobName") String jobName);

    default boolean removeJobs(@Name("jobNames") Collection<String> jobNames) {
        if (jobNames == null)
            return false;

        boolean b = false;
        for (String jobName : jobNames) {
            if (removeJob(jobName))
                b = true;
        }
        return b;
    }

    default boolean clearJobs() {
        return removeJobs(getJobNames());
    }

    /**
     * 获取job当前状态。如果没有找到已注册的job，则返回null
     */
    int getTriggerStatus(@Name("jobName") String jobName);

    /**
     * 启动已经注册的任务
     */
    boolean resumeJob(@Name("jobName") String jobName);

    default boolean resumeJobs(@Name("jobNames") Collection<String> jobNames) {
        if (jobNames == null)
            return false;

        boolean b = false;
        for (String jobName : jobNames) {
            if (resumeJob(jobName))
                b = true;
        }
        return b;
    }

    default boolean resumeAllJobs() {
        return resumeJobs(getJobNames());
    }

    /**
     * 暂停任务。但是任务仍然保存在调度器中，并没有被删除
     */
    boolean suspendJob(@Name("jobName") String jobName);

    default boolean pauseJobs(@Name("jobNames") Collection<String> jobNames) {
        if (jobNames == null)
            return false;

        boolean b = false;
        for (String jobName : jobNames) {
            if (suspendJob(jobName))
                b = true;
        }
        return b;
    }

    default boolean pauseAllJobs() {
        return pauseJobs(getJobNames());
    }

    /**
     * 取消任务。任务取消后会进入取消状态，不会被自动调度
     */
    boolean cancelJob(@Name("jobName") String jobName);

    default boolean cancelJobs(@Name("jobNames") Collection<String> jobNames) {
        if (jobNames == null)
            return false;

        boolean b = false;
        for (String jobName : jobNames) {
            if (cancelJob(jobName))
                b = true;
        }
        return b;
    }

    default boolean cancelAllJobs() {
        return cancelJobs(getJobNames());
    }

    /**
     * 手动触发一次任务。如果任务正在执行，则返回false。如果任务没有处于调度状态，则临时调度一次。 任何时刻同一个jobName对应的任务只会有一个实例在执行。
     */
    boolean fireNow(@Name("jobName") String jobName);

    /**
     * 从数据库中装载持久化任务。
     */
    void activate();

    /**
     * deactivate之后不允许再接收外部指令。负载均衡场景下只有一个主任务调度器允许运行，如果发生主从切换，从服务器要执行deactivate操作。 处于deactivate状态的调度器不会再修改数据库。
     */
    void deactivate();
}