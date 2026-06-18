/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.dao.mapper;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 单 worker reserved cost 聚合查询的行结果（{@code NopJobTaskMapper.sumReservedCost}）。
 * <p>
 * 字段名与 sql-lib EQL 的 select 别名一致，由 SmartRowMapper 自动映射。
 * 返回后在 Java 侧转换为 {@link io.nop.job.api.resource.ResourceVector}。
 */
@DataBean
public class ReservedCostRow {
    private Integer cpu;
    private Integer memory;

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
