package io.nop.cluster.nacos;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * 4.2-C1-005（plan 2559 裁定 E）: typed rejection error codes for the nacos leaf —
 * destroy/stop 后懒初始化/裸用字段访问必须类型化拒绝，不得静默复活客户端。
 */
public interface NacosErrors {
    ErrorCode ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED = define(
            "nop.err.cluster.nacos.naming-service-destroyed",
            "nacos naming service is already destroyed and cannot be re-initialized");

    ErrorCode ERR_CLUSTER_NACOS_CONFIG_SERVICE_STOPPED = define(
            "nop.err.cluster.nacos.config-service-stopped",
            "nacos config service is already stopped and cannot be used");
}
