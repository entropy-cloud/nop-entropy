/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.scheduler;

import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.job.api.IJobInvoker;
import io.nop.job.api.ITriggerState;
import io.nop.job.api.TriggerFireResult;
import io.nop.job.api.TriggerStatus;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerExecutor;
import io.nop.job.core.scheduler.DefaultJobScheduler;
import io.nop.job.core.trigger.TriggerExecutorImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJobScheduler {

    static class MockJobInvoker implements IJobInvoker {
        @Override
        public CompletionStage<TriggerFireResult> invokeAsync(String jobName, Map<String, Object> jobParams,
                                                              ITriggerState state, ICancelToken cancelToken) {
            System.out.println("executionId=" + state.getLastExecutionId() + ",count=" + state.getExecutionCount());
            return null;
        }
    }

    @Test
    public void testSchedule() {
        ITriggerExecutor executor = new TriggerExecutorImpl(GlobalExecutors.globalTimer(),
                ErrorMessageManager.instance());

        DefaultJobScheduler scheduler = new DefaultJobScheduler(executor, invoker -> {
            return new MockJobInvoker();
        });

        scheduler.activate(1);
        JobSpec job = new JobSpec();
        job.setJobGroup("test");
        job.setJobName("test");
        job.setJobInvoker("default");
        TriggerSpec trigger = new TriggerSpec();
        trigger.setRepeatInterval(10);
        trigger.setRepeatFixedDelay(true);
        job.setTriggerSpec(trigger);
        scheduler.addJob(job, true, true);
        try {
            Thread.sleep(200);
        } catch (Exception e) {
        }

        scheduler.pauseJob("test");
        assertEquals(TriggerStatus.PAUSED, scheduler.getTriggerStatus("test"));

        assertEquals(true, scheduler.fireNow("test"));

        scheduler.startJob("test");

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }

        // 增大版本号，更新job定义
        JobSpec job2 = new JobSpec();
        job2.setVersion(2);
        job2.setJobInvoker("test");
        trigger.setRepeatInterval(1000);
        job2.setTriggerSpec(trigger);
        job2.setJobName("test");
        job2.setJobGroup("test");
        scheduler.addJob(job2, true, true);

        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        long count = scheduler.getJobDetail("test").getTriggerState().getExecutionCount();
        if (count <= 5) {
            try {
                Thread.sleep(500);
            } catch (Exception e) {
            }
        }
        assertTrue(count > 5);
        assertEquals(TriggerStatus.SCHEDULING, scheduler.getTriggerStatus("test"));

        try {
            Thread.sleep(200);
        } catch (Exception e) {
        }
        assertEquals(count, scheduler.getJobDetail("test").getTriggerState().getExecutionCount());

        scheduler.cancelJob("test");
        assertEquals(TriggerStatus.CANCELLED, scheduler.getTriggerStatus("test"));

        scheduler.deactivate();
    }
}
