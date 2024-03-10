/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core;

import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * 负责协调父子工作流
 *
 * @author canonical_entropy@163.com
 */
public interface IWorkflowCoordinator {
    /**
     * 启动子工作流，返回工作流实例标识
     *
     * @param wfName
     * @param parentStep 父工作流当前步骤标识
     * @param args       传递到子工作流的变量
     * @return 工作流版本以及工作流实例id
     */
    WfReference startSubFlow(@Nonnull String wfName, Long wfVersion, @Nonnull WfStepReference parentStep,
                             Map<String, Object> args, @Nonnull IServiceContext context);

    /**
     * 子工作流结束时调用此接口通知父流程结果数据
     *
     * @param context
     * @param status     IWorkflow上定义的WF_STATUS常量
     * @param parentStep 父流程的步骤引用。startSubflow时传入的信息
     * @param results
     */
    void endSubFlow(WfReference wfRef, int status, @Nonnull WfStepReference parentStep,
                    Map<String, Object> results, @Nonnull IServiceContext context);
}