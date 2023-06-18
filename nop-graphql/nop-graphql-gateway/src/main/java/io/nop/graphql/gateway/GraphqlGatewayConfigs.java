package io.nop.graphql.gateway;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;

import static io.nop.api.core.config.AppConfig.varRef;

public interface GraphqlGatewayConfigs {
    @Description("网关模型文件对应的虚拟文件路径")
    IConfigReference<String> CFG_GATEWAY_MODEL_PATH =
            varRef("nop.gateway.model-path", String.class, "/nop/main/app.gateway.xml");
}
