package io.nop.job.core;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopJobCoreConstants {
    
    /**
     * 调度状态: 已禁用 
     */
    int SCHEDULE_STATUS_DISABLED = 0;
                    
    /**
     * 调度状态: 已启用 
     */
    int SCHEDULE_STATUS_ENABLED = 10;
                    
    /**
     * 调度状态: 已暂停 
     */
    int SCHEDULE_STATUS_PAUSED = 20;
                    
    /**
     * 调度状态: 已完成 
     */
    int SCHEDULE_STATUS_COMPLETED = 30;
                    
    /**
     * 调度状态: 已归档 
     */
    int SCHEDULE_STATUS_ARCHIVED = 40;
                    
    /**
     * 触发批次状态: 等待分发 
     */
    int FIRE_STATUS_WAITING = 0;
                    
    /**
     * 触发批次状态: 分发中 
     */
    int FIRE_STATUS_DISPATCHING = 10;
                    
    /**
     * 触发批次状态: 执行中 
     */
    int FIRE_STATUS_RUNNING = 20;
                    
    /**
     * 触发批次状态: 执行成功 
     */
    int FIRE_STATUS_SUCCESS = 30;
                    
    /**
     * 触发批次状态: 执行失败 
     */
    int FIRE_STATUS_FAILED = 40;
                    
    /**
     * 触发批次状态: 执行超时 
     */
    int FIRE_STATUS_TIMEOUT = 50;
                    
    /**
     * 触发批次状态: 已取消 
     */
    int FIRE_STATUS_CANCELED = 60;
                    
    /**
     * 执行任务状态: 等待执行 
     */
    int TASK_STATUS_WAITING = 0;
                    
    /**
     * 执行任务状态: 已认领 
     */
    int TASK_STATUS_CLAIMED = 10;
                    
    /**
     * 执行任务状态: 执行中 
     */
    int TASK_STATUS_RUNNING = 20;
                    
    /**
     * 执行任务状态: 执行成功 
     */
    int TASK_STATUS_SUCCESS = 30;
                    
    /**
     * 执行任务状态: 执行失败 
     */
    int TASK_STATUS_FAILED = 40;
                    
    /**
     * 执行任务状态: 执行超时 
     */
    int TASK_STATUS_TIMEOUT = 50;
                    
    /**
     * 执行任务状态: 已取消 
     */
    int TASK_STATUS_CANCELED = 60;
                    
    /**
     * 触发来源: 定时触发 
     */
    int TRIGGER_SOURCE_SCHEDULE = 1;
                    
    /**
     * 触发来源: 手工触发 
     */
    int TRIGGER_SOURCE_MANUAL = 2;
                    
    /**
     * 触发来源: 恢复触发 
     */
    int TRIGGER_SOURCE_RECOVERY = 3;
                    
    /**
     * 执行器类型: Bean执行器 
     */
    int EXECUTOR_KIND_BEAN = 1;
                    
    /**
     * 执行器类型: RPC执行器 
     */
    int EXECUTOR_KIND_RPC = 2;
                    
    /**
     * 阻塞策略: 丢弃 
     */
    int BLOCK_STRATEGY_DISCARD = 1;
                    
    /**
     * 阻塞策略: 覆盖 
     */
    int BLOCK_STRATEGY_OVERLAY = 2;
                    
    /**
     * 阻塞策略: 并行 
     */
    int BLOCK_STRATEGY_PARALLEL = 3;
                    
    /**
     * 触发器类型: CRON 
     */
    int TRIGGER_TYPE_CRON = 1;
                    
    /**
     * 触发器类型: 固定频率 
     */
    int TRIGGER_TYPE_FIXED_RATE = 2;
                    
    /**
     * 触发器类型: 固定延时 
     */
    int TRIGGER_TYPE_FIXED_DELAY = 3;
                    
    /**
     * 触发器类型: 单次执行 
     */
    int TRIGGER_TYPE_ONCE = 4;
                    
}
