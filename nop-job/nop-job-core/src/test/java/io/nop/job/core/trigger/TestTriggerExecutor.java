/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerExecution;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class TestTriggerExecutor {
    @Test
    public void testExecute() {
        TriggerExecutorImpl executor = new TriggerExecutorImpl(GlobalExecutors.globalTimer(),
                ErrorMessageManager.instance());

        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(5);
        // 每隔1秒执行一次
        spec.setCronExpr("0/1 * * * * *");

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        TriggerContextImpl context = new TriggerContextImpl("test", spec);
        List<LocalDateTime> times = new CopyOnWriteArrayList<>();

        ITriggerExecution execution = executor.execute(trigger,
                (forceFire, ctx, cancelToken) -> GlobalExecutors.globalTimer().schedule(() -> {
                    times.add(DateHelper.millisToDateTime(ctx.getExecEndTime()));
                    times.add(DateHelper.millisToDateTime(ctx.getScheduledExecTime()));
                    return null;
                }, 100, TimeUnit.MILLISECONDS), context);

        FutureHelper.syncGet(execution.getFinishPromise());

        System.out.println("times=" + StringHelper.join(times, "\n"));
        int size = times.size();
        System.out.println("size=" + size);
        assertEquals(10, size);
    }
}
