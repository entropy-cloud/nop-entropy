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
