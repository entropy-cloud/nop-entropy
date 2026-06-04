//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthResourceInputBean;
    import io.nop.auth.api.beans.NopAuthResourceOutputBean;
    import io.nop.api.core.api.ICrudApi;
    import io.nop.api.core.api.ICrudTreeApi;
    

    @BizModel("NopAuthResource")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthResourceApi extends ICrudApi<NopAuthResourceInputBean, NopAuthResourceOutputBean>,
        ICrudTreeApi<NopAuthResourceOutputBean> {
    }
