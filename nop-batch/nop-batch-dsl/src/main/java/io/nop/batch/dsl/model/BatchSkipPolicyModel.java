package io.nop.batch.dsl.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.batch.core.BatchSkipPolicy;
import io.nop.batch.dsl.model._gen._BatchSkipPolicyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchSkipPolicyModel extends _BatchSkipPolicyModel {
    static final Logger LOG = LoggerFactory.getLogger(BatchSkipPolicyModel.class);

    private BatchSkipPolicy skipPolicy;

    public BatchSkipPolicyModel() {

    }

    public BatchSkipPolicy buildSkipPolicy() {
        if (skipPolicy != null)
            return skipPolicy;

        BatchSkipPolicy policy = new BatchSkipPolicy();
        if (getMaxSkipCount() != null) {
            // 1.7-C7-005（plan 2546 W-01）：0 = 显式不设上限。装配期一次性 WARN 使该语义可见
            //（被跳过的记录不入任何账本，属 plan Deferred 的独立能力；此处只保证规模可见）。
            if (getMaxSkipCount() == 0)
                LOG.warn("nop.warn.batch.skip-policy-unbounded:maxSkipCount=0 (no upper bound configured)");
            policy.setMaxSkipCount(getMaxSkipCount());
        }
        policy.setSkipExceptionFilter((err, ctx) -> {
            return ConvertHelper.toTruthy(getExceptionFilter().call2(null, err, ctx, ctx.getEvalScope()));
        });

        this.skipPolicy = policy;
        return policy;
    }
}
