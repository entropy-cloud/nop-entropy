/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.proto;

import io.grpc.ServerServiceDefinition;

public class ServiceSchema {
    private ServerServiceDefinition serviceDefinition;

    public ServerServiceDefinition getServiceDefinition() {
        return serviceDefinition;
    }

    public void setServiceDefinition(ServerServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }
}
