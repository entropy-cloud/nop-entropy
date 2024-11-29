package io.nop.batch.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopBatchDaoConstants {
    
    /**
     * 批处理任务状态: 已创建 
     */
    int TASK_STATUS_CREATED = 0;
                    
    /**
     * 批处理任务状态: 执行中 正在执行
     */
    int TASK_STATUS_RUNNING = 10;
                    
    /**
     * 批处理任务状态: 已暂停 临时挂起
     */
    int TASK_STATUS_SUSPENDED = 20;
                    
    /**
     * 批处理任务状态: 已完成 正常结束
     */
    int TASK_STATUS_COMPLETED = 30;
                    
    /**
     * 批处理任务状态: 已失败 异常结束
     */
    int TASK_STATUS_FAILED = 40;
                    
    /**
     * 批处理任务状态: 已取消 取消执行
     */
    int TASK_STATUS_CANCELLED = 50;
                    
    /**
     * 批处理任务状态: 已终止 强制终止
     */
    int TASK_STATUS_KILLED = 60;
                    
}
