package io.nop.rpc.grpc.status;

import io.grpc.Status;
import io.nop.api.core.beans.ApiResponse;
import io.nop.commons.util.StringHelper;

public class GrpcStatusMapping {
    public Status mapToStatus(ApiResponse<?> response) {
        if (response.isOk()) {
            return ok();
        } else {
            Status status = Status.fromCodeValue(response.getStatus());
            if (!StringHelper.isEmpty(response.getMsg()))
                status = status.withDescription(response.getMsg());
            return status;
        }
    }

    public Status ok() {
        return Status.fromCode(Status.Code.OK);
    }
}
