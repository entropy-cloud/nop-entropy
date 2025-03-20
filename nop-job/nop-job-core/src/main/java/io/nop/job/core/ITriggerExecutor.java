/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

/**
 * 定时触发action
 *
 * @author canonical_entropy@163.com
 */
public interface ITriggerExecutor {

    /**
     * 根据trigger返回的调度时间，不断调度执行action，直至trigger返回-1
     */
    ITriggerExecution execute(ITrigger trigger, ITriggerAction action, ITriggerContext context);

    ITriggerExecution fireNow(ITriggerAction action, ITriggerContext context);
}