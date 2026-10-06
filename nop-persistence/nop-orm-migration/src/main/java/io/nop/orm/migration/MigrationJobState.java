/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

/**
 * 编排作业状态行（设计 §4.1/§4.8 m5）：编排器重启后按状态行从最后完成的阶段继续，
 * 绝不隐式回退阶段。持久化为模块自治表 {@code nop_orm_migration_job}
 * （先例 = jdbc-2pc sink 的 epoch ledger 自治建表）。
 */
public class MigrationJobState {
    private String jobId;
    private MigrationPhase phase = MigrationPhase.PREPARE;
    private String scenario;
    private String entityName;
    /** PREPARE 钉定的源位点（JSON 形态，{@link SourcePosition}）。 */
    private String positionP0;
    /** FULL 阶段 batch 任务名（与 NopBatchTask.flowId/任务管理面关联的追溯锚）。 */
    private String fullTaskName;
    /** verify 结论 passed/failed（VERIFY 阶段写入，SWITCH confirm 门禁消费）。 */
    private String verifyResult;
    /** SWITCH 完成的路由重建语义：rebuild-done（热重建）或 restart-required（降级滚动重启）。 */
    private String routeRebuild;
    /** 中止原因（ABORT 阶段写入）。 */
    private String abortReason;
    private long createTimeMillis;
    private long updateTimeMillis;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public MigrationPhase getPhase() {
        return phase;
    }

    public void setPhase(MigrationPhase phase) {
        this.phase = phase;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getPositionP0() {
        return positionP0;
    }

    public void setPositionP0(String positionP0) {
        this.positionP0 = positionP0;
    }

    public String getFullTaskName() {
        return fullTaskName;
    }

    public void setFullTaskName(String fullTaskName) {
        this.fullTaskName = fullTaskName;
    }

    public String getVerifyResult() {
        return verifyResult;
    }

    public void setVerifyResult(String verifyResult) {
        this.verifyResult = verifyResult;
    }

    public String getRouteRebuild() {
        return routeRebuild;
    }

    public void setRouteRebuild(String routeRebuild) {
        this.routeRebuild = routeRebuild;
    }

    public String getAbortReason() {
        return abortReason;
    }

    public void setAbortReason(String abortReason) {
        this.abortReason = abortReason;
    }

    public long getCreateTimeMillis() {
        return createTimeMillis;
    }

    public void setCreateTimeMillis(long createTimeMillis) {
        this.createTimeMillis = createTimeMillis;
    }

    public long getUpdateTimeMillis() {
        return updateTimeMillis;
    }

    public void setUpdateTimeMillis(long updateTimeMillis) {
        this.updateTimeMillis = updateTimeMillis;
    }
}
