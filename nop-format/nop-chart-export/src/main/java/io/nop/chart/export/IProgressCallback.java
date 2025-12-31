package io.nop.chart.export;

/**
 * Progress callback interface for chart export operations
 */
public interface IProgressCallback {
    
    /**
     * 报告进度
     * @param progress 进度百分比 (0-100)
     * @param message 进度消息
     */
    void reportProgress(int progress, String message);
    
    /**
     * 检查是否已取消
     * @return true如果操作已被取消
     */
    boolean isCancelled();
    
    /**
     * 设置总步骤数
     * @param totalSteps 总步骤数
     */
    default void setTotalSteps(int totalSteps) {
        // 默认实现为空
    }
    
    /**
     * 完成一个步骤
     * @param stepMessage 步骤消息
     */
    default void completeStep(String stepMessage) {
        // 默认实现为空
    }
}