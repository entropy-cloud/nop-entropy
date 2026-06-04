//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthUserInputBean;
    import io.nop.auth.api.beans.NopAuthUserOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopAuthUser")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthUserApi extends ICrudApi<NopAuthUserInputBean, NopAuthUserOutputBean> {
    }
