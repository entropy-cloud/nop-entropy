//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthGroupInputBean;
    import io.nop.auth.api.beans.NopAuthGroupOutputBean;
    import io.nop.api.core.api.ICrudApi;
    import io.nop.api.core.api.ICrudTreeApi;
    

    @BizModel("NopAuthGroup")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthGroupApi extends ICrudApi<NopAuthGroupInputBean, NopAuthGroupOutputBean>,
        ICrudTreeApi<NopAuthGroupOutputBean> {
    }
