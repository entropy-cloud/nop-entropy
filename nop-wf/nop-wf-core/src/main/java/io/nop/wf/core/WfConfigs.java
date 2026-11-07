package io.nop.wf.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

/**
 * nop-wf-core 配置键持有类（plan 2541 W-02，1.5-MA-07 裁定 D；先例=nop-task-core TaskConfigs /
 * nop-wf-scheduler WfSchedulerConfigs）。
 */
public interface WfConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(WfConfigs.class);

    @Description("批量转办请求级步骤数上限；待处理步骤数超上限显式报错（不静默截断），<=0 非法")
    IConfigReference<Integer> CFG_WF_TRANSFER_MAX_BATCH =
            AppConfig.varRef(s_loc, "nop.wf.transfer.max-batch", Integer.class, 200);
}
