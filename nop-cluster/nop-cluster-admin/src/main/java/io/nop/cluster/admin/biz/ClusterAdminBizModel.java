package io.nop.cluster.admin.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.core.context.IServiceContext;

@BizModel("ClusterAdmin")
public class ClusterAdminBizModel {

    @BizMutation
    public void shutdown(IServiceContext ctx) {

    }
}
