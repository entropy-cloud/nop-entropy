//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthSessionInputBean;
    import io.nop.auth.api.beans.NopAuthSessionOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopAuthSession")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthSessionApi extends ICrudApi<NopAuthSessionInputBean, NopAuthSessionOutputBean> {
    }
