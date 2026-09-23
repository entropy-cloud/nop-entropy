//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthRateLimitCounterInputBean;
    import io.nop.auth.api.beans.NopAuthRateLimitCounterOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopAuthRateLimitCounter")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthRateLimitCounterApi extends ICrudApi<NopAuthRateLimitCounterInputBean, NopAuthRateLimitCounterOutputBean> {
    }
