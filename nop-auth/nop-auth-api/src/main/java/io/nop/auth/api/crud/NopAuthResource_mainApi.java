//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthResource_mainInputBean;
    import io.nop.auth.api.beans.NopAuthResource_mainOutputBean;
    import io.nop.api.core.api.ICrudApi;
    import io.nop.api.core.api.ICrudTreeApi;
    

    @BizModel("NopAuthResource_main")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthResource_mainApi extends ICrudApi<NopAuthResource_mainInputBean, NopAuthResource_mainOutputBean>,
        ICrudTreeApi<NopAuthResource_mainOutputBean> {
    }
