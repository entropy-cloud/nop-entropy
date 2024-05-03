package io.nop.job.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopJobDaoConstants {
    
    /**
     * 作业实例状态: 已创建 
     */
    int JOB_INSTANCE_STATUS_CREATED = 0;
                    
    /**
     * 作业实例状态: 已暂停 
     */
    int JOB_INSTANCE_STATUS_SUSPENDED = 10;
                    
    /**
     * 作业实例状态: 等待中 等待调度
     */
    int JOB_INSTANCE_STATUS_WAITING = 20;
                    
    /**
     * 作业实例状态: 执行中 
     */
    int JOB_INSTANCE_STATUS_ACTIVATED = 30;
                    
    /**
     * 作业实例状态: 已完成 
     */
    int JOB_INSTANCE_STATUS_COMPLETED = 40;
                    
    /**
     * 作业实例状态: 已超时 
     */
    int JOB_INSTANCE_STATUS_EXPIRED = 50;
                    
    /**
     * 作业实例状态: 已失败 
     */
    int JOB_INSTANCE_STATUS_FAILED = 60;
                    
    /**
     * 作业实例状态: 已中止 
     */
    int JOB_INSTANCE_STATUS_KILLED = 70;
                    
    /**
     * 作业定义状态: 未发布 
     */
    int JOB_DEF_STATUS_UNPUBLISHED = 0;
                    
    /**
     * 作业定义状态: 已发布 
     */
    int JOB_DEF_STATUS_PUBLISHED = 1;
                    
    /**
     * 作业定义状态: 已过时 过时的作业不推荐使用
     */
    int JOB_DEF_STATUS_DEPRECATED = 2;
                    
    /**
     * 作业定义状态: 已归档 已归档的流程不能新建实例
     */
    int JOB_DEF_STATUS_ARCHIVED = 3;
                    
}
