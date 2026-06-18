/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.dao.store;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 跨 worker reserved cost 聚合查询的行结果（{@code NopJobTaskMapper.sumReservedCostByWorker}）。
 * <p>
 * 字段名与 sql-lib EQL 的 select 别名一致，由 SmartRowMapper 自动映射。
 *
 * @param workerInstanceId worker 实例 id（group by key）
 * @param cpu              已归因 task 的 CPU 求和（毫核）
 * @param memory           已归因 task 的 memory 求和（MB）
 */
@DataBean
public class WorkerReservedCost {
    private String workerInstanceId;
    private Integer cpu;
    private Integer memory;

    public String getWorkerInstanceId() {
        return workerInstanceId;
    }

    public void setWorkerInstanceId(String workerInstanceId) {
        this.workerInstanceId = workerInstanceId;
    }

    public Integer getCpu() {
        return cpu;
    }

    public void setCpu(Integer cpu) {
        this.cpu = cpu;
    }

    public Integer getMemory() {
        return memory;
    }

    public void setMemory(Integer memory) {
        this.memory = memory;
    }
}
