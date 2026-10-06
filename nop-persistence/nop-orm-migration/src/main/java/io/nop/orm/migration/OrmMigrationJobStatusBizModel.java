/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 编排 job 状态的只读查询面（设计 §4.8 m5 最小 Biz 面，落编排模块）：
 * 仅暴露状态行读取，无任何变更入口（阶段推进只能走编排器编程 API）。
 */
@BizModel("OrmMigrationJob")
public class OrmMigrationJobStatusBizModel {

    private volatile OrmMigrationOrchestrator orchestrator;

    /**
     * 宿主装配注入（编排器 bean 的只读视图）。
     */
    public void setOrchestrator(OrmMigrationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @BizQuery
    public Map<String, Object> getJob(@io.nop.api.core.annotations.core.Name("jobId") String jobId) {
        OrmMigrationOrchestrator orchestrator = this.orchestrator;
        if (orchestrator == null)
            throw new IllegalStateException("orm-migration orchestrator is not wired into the biz model");
        MigrationJobState state = orchestrator.getState(jobId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", state.getJobId());
        result.put("phase", state.getPhase().name());
        result.put("scenario", state.getScenario());
        result.put("entityName", state.getEntityName());
        result.put("positionP0", state.getPositionP0());
        result.put("fullTaskName", state.getFullTaskName());
        result.put("verifyResult", state.getVerifyResult());
        result.put("routeRebuild", state.getRouteRebuild());
        result.put("abortReason", state.getAbortReason());
        return result;
    }
}
