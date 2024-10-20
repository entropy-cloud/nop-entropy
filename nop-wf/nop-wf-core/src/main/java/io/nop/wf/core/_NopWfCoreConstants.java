package io.nop.wf.core;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopWfCoreConstants {
    
    /**
     * 步骤状态: 已创建 
     */
    int WF_STEP_STATUS_CREATED = 0;
                    
    /**
     * 步骤状态: 已暂停 
     */
    int WF_STEP_STATUS_SUSPENDED = 10;
                    
    /**
     * 步骤状态: 等待中 join步骤在等待上游步骤完成的过程中或者flow步骤在等待子流程完成的过程中处于此状态
     */
    int WF_STEP_STATUS_WAITING = 20;
                    
    /**
     * 步骤状态: 执行中 步骤处于激活状态，一般此时在界面上才显示操作按钮
     */
    int WF_STEP_STATUS_ACTIVATED = 30;
                    
    /**
     * 步骤状态: 已执行 步骤工作已经完成，等待执行步骤迁移
     */
    int WF_STEP_STATUS_EXECUTED = 35;
                    
    /**
     * 步骤状态: 已完成 步骤正常结束
     */
    int WF_STEP_STATUS_COMPLETED = 40;
                    
    /**
     * 步骤状态: 已超时 已超时
     */
    int WF_STEP_STATUS_EXPIRED = 50;
                    
    /**
     * 步骤状态: 已失败 步骤异常结束
     */
    int WF_STEP_STATUS_FAILED = 60;
                    
    /**
     * 步骤状态: 已取消 取消执行
     */
    int WF_STEP_STATUS_CANCELLED = 70;
                    
    /**
     * 步骤状态: 已中止 主动中止
     */
    int WF_STEP_STATUS_KILLED = 80;
                    
    /**
     * 步骤状态: 已退回 退回当前步骤时，当前步骤的状态标记为REJECTED，便于和正常的历史状态区分开来
     */
    int WF_STEP_STATUS_REJECTED = 90;
                    
    /**
     * 步骤状态: 已撤销 发送到下一步骤之后，在下一步骤的状态转变为历史状态之前，可以执行撤回操作，被撤回的下一步骤状态变为WITHDRAWN。
     */
    int WF_STEP_STATUS_WITHDRAWN = 100;
                    
    /**
     * 步骤状态: 已转交 转交给其他人执行
     */
    int WF_STEP_STATUS_TRANSFERRED = 110;
                    
    /**
     * 工作流: 已创建 
     */
    int WF_STATUS_CREATED = 0;
                    
    /**
     * 工作流: 已暂停 
     */
    int WF_STATUS_SUSPENDED = 10;
                    
    /**
     * 工作流: 等待中 等待调度
     */
    int WF_STATUS_WAITING = 20;
                    
    /**
     * 工作流: 执行中 
     */
    int WF_STATUS_ACTIVATED = 30;
                    
    /**
     * 工作流: 已完成 
     */
    int WF_STATUS_COMPLETED = 40;
                    
    /**
     * 工作流: 已超时 
     */
    int WF_STATUS_EXPIRED = 50;
                    
    /**
     * 工作流: 已失败 
     */
    int WF_STATUS_FAILED = 60;
                    
    /**
     * 工作流: 已中止 
     */
    int WF_STATUS_KILLED = 70;
                    
    /**
     * 工作流定义状态: 未发布 
     */
    int WF_DEF_STATUS_UNPUBLISHED = 0;
                    
    /**
     * 工作流定义状态: 已发布 
     */
    int WF_DEF_STATUS_PUBLISHED = 1;
                    
    /**
     * 工作流定义状态: 已归档 已归档的流程不能新建实例
     */
    int WF_DEF_STATUS_ARCHIVED = 2;
                    
}
