package io.nop.task.core;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopTaskCoreConstants {
    
    /**
     * 步骤状态: 已创建 
     */
    int TASK_STEP_STATUS_CREATED = 0;
                    
    /**
     * 步骤状态: 已暂停 
     */
    int TASK_STEP_STATUS_SUSPENDED = 10;
                    
    /**
     * 步骤状态: 等待中 join步骤在等待上游步骤完成的过程中或者flow步骤在等待子流程完成的过程中处于此状态
     */
    int TASK_STEP_STATUS_WAITING = 20;
                    
    /**
     * 步骤状态: 执行中 步骤处于激活状态，一般此时在界面上才显示操作按钮
     */
    int TASK_STEP_STATUS_ACTIVATED = 30;
                    
    /**
     * 步骤状态: 已执行 步骤工作已经完成，等待执行步骤迁移
     */
    int TASK_STEP_STATUS_EXECUTED = 35;
                    
    /**
     * 步骤状态: 已完成 步骤正常结束
     */
    int TASK_STEP_STATUS_COMPLETED = 40;
                    
    /**
     * 步骤状态: 已超时 已超时
     */
    int TASK_STEP_STATUS_EXPIRED = 50;
                    
    /**
     * 步骤状态: 已失败 步骤异常结束
     */
    int TASK_STEP_STATUS_FAILED = 60;
                    
    /**
     * 步骤状态: 已中止 主动中止
     */
    int TASK_STEP_STATUS_KILLED = 70;
                    
    /**
     * 逻辑流: 已创建 
     */
    int TASK_STATUS_CREATED = 0;
                    
    /**
     * 逻辑流: 已暂停 
     */
    int TASK_STATUS_SUSPENDED = 10;
                    
    /**
     * 逻辑流: 等待中 等待调度
     */
    int TASK_STATUS_WAITING = 20;
                    
    /**
     * 逻辑流: 执行中 
     */
    int TASK_STATUS_ACTIVATED = 30;
                    
    /**
     * 逻辑流: 已完成 
     */
    int TASK_STATUS_COMPLETED = 40;
                    
    /**
     * 逻辑流: 已超时 
     */
    int TASK_STATUS_EXPIRED = 50;
                    
    /**
     * 逻辑流: 已失败 
     */
    int TASK_STATUS_FAILED = 60;
                    
    /**
     * 逻辑流: 已中止 
     */
    int TASK_STATUS_KILLED = 70;
                    
    /**
     * 逻辑流定义状态: 未发布 
     */
    int TASK_DEF_STATUS_UNPUBLISHED = 0;
                    
    /**
     * 逻辑流定义状态: 已发布 
     */
    int TASK_DEF_STATUS_PUBLISHED = 1;
                    
    /**
     * 逻辑流定义状态: 已过时 过时的流程不推荐使用
     */
    int TASK_DEF_STATUS_DEPRECATED = 2;
                    
    /**
     * 逻辑流定义状态: 已归档 已归档的流程不能新建实例
     */
    int TASK_DEF_STATUS_ARCHIVED = 3;
                    
}
