package io.nop.code.service.cluster;

/**
 * N6.5: per-index 访问策略 SPI。检查点在 ICodeIndexService 全部公共方法入口：
 * 读方法（含源码暴露面）走 checkReadAccess，写/变更方法走 checkWriteAccess。
 * 实现抛出异常即拒绝（推荐 NopException/SecurityError 语义异常）；静默返回视为放行。
 * 默认实现 Permissive（全放行）= 向后兼容零行为变化。
 */
public interface IndexAccessPolicy {

    void checkReadAccess(String indexId);

    void checkWriteAccess(String indexId);

    /**
     * per-index 本地路径白名单根；返回 null 回退全局 allowedLocalRoot。
     */
    default String getAllowedLocalRoot(String indexId) {
        return null;
    }
}
