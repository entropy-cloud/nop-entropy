package io.nop.retry.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopRetryDaoConstants {
    
    /**
     * 重试记录状态: 待调度 
     */
    int RETRY_RECORD_STATUS_PENDING = 0;
                    
    /**
     * 重试记录状态: 重试中 
     */
    int RETRY_RECORD_STATUS_RETRYING = 1;
                    
    /**
     * 重试记录状态: 已完成 
     */
    int RETRY_RECORD_STATUS_COMPLETED = 2;
                    
    /**
     * 重试记录状态: 超限 
     */
    int RETRY_RECORD_STATUS_MAX_RETRIES = 3;
                    
    /**
     * 重试记录状态: 已暂停 
     */
    int RETRY_RECORD_STATUS_SUSPENDED = 4;
                    
    /**
     * 重试尝试状态: 等待中 
     */
    int RETRY_ATTEMPT_STATUS_WAITING = 0;
                    
    /**
     * 重试尝试状态: 执行中 
     */
    int RETRY_ATTEMPT_STATUS_RUNNING = 1;
                    
    /**
     * 重试尝试状态: 成功 
     */
    int RETRY_ATTEMPT_STATUS_SUCCESS = 2;
                    
    /**
     * 重试尝试状态: 失败 
     */
    int RETRY_ATTEMPT_STATUS_FAILED = 3;
                    
    /**
     * 重试尝试状态: 已停止 
     */
    int RETRY_ATTEMPT_STATUS_STOPPED = 4;
                    
    /**
     * 重试尝试状态: 已取消 
     */
    int RETRY_ATTEMPT_STATUS_CANCELED = 5;
                    
    /**
     * 重试任务类型: 业务重试 
     */
    int RETRY_TASK_TYPE_RETRY = 1;
                    
    /**
     * 重试任务类型: 回调任务 
     */
    int RETRY_TASK_TYPE_CALLBACK = 2;
                    
    /**
     * 退避策略: 固定间隔 
     */
    int BACKOFF_STRATEGY_FIXED_INTERVAL = 1;
                    
    /**
     * 退避策略: 指数退避 
     */
    int BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF = 2;
                    
    /**
     * 退避策略: Cron表达式 
     */
    int BACKOFF_STRATEGY_CRON_EXPRESSION = 3;
                    
    /**
     * 阻塞策略: 丢弃 
     */
    int BLOCK_STRATEGY_DISCARD = 1;
                    
    /**
     * 阻塞策略: 覆盖 
     */
    int BLOCK_STRATEGY_OVERWRITE = 2;
                    
    /**
     * 阻塞策略: 并行 
     */
    int BLOCK_STRATEGY_PARALLEL = 3;
                    
    /**
     * 回调触发类型: 成功时触发 
     */
    int CALLBACK_TRIGGER_TYPE_ON_SUCCESS = 1;
                    
    /**
     * 回调触发类型: 失败时触发 
     */
    int CALLBACK_TRIGGER_TYPE_ON_FAILURE = 2;
                    
    /**
     * 回调触发类型: 总是触发 
     */
    int CALLBACK_TRIGGER_TYPE_ALWAYS = 3;
                    
}
