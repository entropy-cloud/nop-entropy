//__XGEN_FORCE_OVERRIDE__
    package io.nop.retry.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.retry.api.beans.NopRetryDeadLetterInputBean;
    import io.nop.retry.api.beans.NopRetryDeadLetterOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopRetryDeadLetter")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopRetryDeadLetterApi extends ICrudApi<NopRetryDeadLetterInputBean, NopRetryDeadLetterOutputBean> {
    }
