package io.nop.batch.dsl.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.batch.core.BatchSkipPolicy;
import io.nop.batch.dsl.model._gen._BatchSkipPolicyModel;

public class BatchSkipPolicyModel extends _BatchSkipPolicyModel {
    private BatchSkipPolicy skipPolicy;

    public BatchSkipPolicyModel() {

    }

    public BatchSkipPolicy buildSkipPolicy() {
        if (skipPolicy != null)
            return skipPolicy;

        BatchSkipPolicy policy = new BatchSkipPolicy();
        if (getMaxSkipCount() != null)
            policy.setMaxSkipCount(getMaxSkipCount());
        policy.setSkipExceptionFilter((err, ctx) -> {
            return ConvertHelper.toTruthy(getExceptionFilter().call2(null, err, ctx, ctx.getEvalScope()));
        });

        this.skipPolicy = policy;
        return policy;
    }
}
