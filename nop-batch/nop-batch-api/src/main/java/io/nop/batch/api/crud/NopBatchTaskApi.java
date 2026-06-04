//__XGEN_FORCE_OVERRIDE__
    package io.nop.batch.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.batch.api.beans.NopBatchTaskInputBean;
    import io.nop.batch.api.beans.NopBatchTaskOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopBatchTask")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopBatchTaskApi extends ICrudApi<NopBatchTaskInputBean, NopBatchTaskOutputBean> {
    }
