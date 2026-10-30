package io.nop.wf.scheduler;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

/**
 * plan 2514 MA-05（裁定④）/ MA-23（裁定⑥）扫描配置键一览（缺省值一次定夺，
 * 设计文档 §3.7 为唯一键名权威，零键名二源）。
 */
public interface WfSchedulerConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(WfSchedulerConfigs.class);

    @Description("扫描分批单批上限；<=0 装配期 fail-fast")
    IConfigReference<Integer> CFG_WF_SCAN_BATCH_SIZE =
            AppConfig.varRef(s_loc, "nop.wf.scheduler.scan-batch-size", Integer.class, 500);

    @Description("单轮扫描时间预算（毫秒）；<0 非法，0=单批即止")
    IConfigReference<Integer> CFG_WF_SCAN_TIME_BUDGET_MS =
            AppConfig.varRef(s_loc, "nop.wf.scheduler.scan-time-budget-ms", Integer.class, 30000);

    @Description("扫描抢占租约时长（毫秒，崩溃恢复窗；> action 执行 p99）")
    IConfigReference<Integer> CFG_WF_SCAN_LEASE_MS =
            AppConfig.varRef(s_loc, "nop.wf.scheduler.scan-lease-ms", Integer.class, 120000);

    @Description("扫描节点标识（holderId 归因前缀；缺省=主机名+启动随机后缀，装配期一次生成）")
    IConfigReference<String> CFG_WF_SCHEDULER_NODE_ID =
            AppConfig.varRef(s_loc, "nop.wf.scheduler.node-id", String.class, null);
}
