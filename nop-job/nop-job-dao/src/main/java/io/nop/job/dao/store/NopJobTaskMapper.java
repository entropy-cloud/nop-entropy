/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.dao.store;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;

import java.util.List;
import java.util.Map;

/**
 * NopJobTask 的 sql-lib mapper。
 * 用 EQL 聚合查询实现 reserved cost 求和，避免在 store 中拼装 QueryBean 聚合语句。
 * 通过 @SqlLibMapper 注册到 IoC 容器，bean id 为本接口的全名。
 * <p>
 * 返回 {@code Map<String,Object>}（key 为 select 别名），由 {@code JobTaskStoreImpl}
 * 转换为 {@link io.nop.job.api.resource.ResourceVector}（不可变值类型，无默认构造器，
 * SmartRowMapper 无法直接实例化）。
 */
@SqlLibMapper("/nop/job/sql/NopJobTask.sql-lib.xml")
public interface NopJobTaskMapper {

    /**
     * 单 worker 已归因 task cost 求和。worker 侧 {@code JobWorkerScannerImpl.scanOnce} 用于
     * 算 myRemaining = myCapacity - myReserved。
     * <p>
     * 返回 Map（key: cpu, memory）；无匹配行时返回 null（由调用方降级为 ZERO）。
     *
     * @param workerInstanceId worker 实例 id
     * @param activeStatuses   计入 reserved 的状态集，建议传 {@code NopJobCoreConstants.RESERVED_TASK_STATUSES}
     */
    Map<String, Object> sumReservedCost(@Name("workerInstanceId") String workerInstanceId,
                                        @Name("activeStatuses") List<Integer> activeStatuses);

    /**
     * 跨 worker 已归因 task cost 求和，按 workerInstanceId 分组。
     * dispatcher 侧 WorkerLoad 派生用（Plan 215）。
     * <p>
     * worker_instance_id 为 NULL 的历史行不返回。
     */
    List<WorkerReservedCost> sumReservedCostByWorker(@Name("activeStatuses") List<Integer> activeStatuses);
}
