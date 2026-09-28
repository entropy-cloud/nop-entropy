package io.nop.code.service.cluster;

/**
 * N6.5 默认策略：全放行（向后兼容）。部署方注入自有实现对接租户/权限系统。
 */
public class PermissiveIndexAccessPolicy implements IndexAccessPolicy {

    public static final PermissiveIndexAccessPolicy INSTANCE = new PermissiveIndexAccessPolicy();

    @Override
    public void checkReadAccess(String indexId) {
        // permissive
    }

    @Override
    public void checkWriteAccess(String indexId) {
        // permissive
    }
}
