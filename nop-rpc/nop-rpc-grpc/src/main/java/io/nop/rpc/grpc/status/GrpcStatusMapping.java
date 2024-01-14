package io.nop.rpc.grpc.status;

import io.grpc.Status;
import io.nop.api.core.beans.ApiResponse;

public class GrpcStatusMapping {
    public Status mapToStatus(ApiResponse<?> response) {
        return Status.fromCode(Status.Code.OK);
    }
}
