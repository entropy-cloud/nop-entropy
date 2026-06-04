//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthDeptInputBean;
    import io.nop.auth.api.beans.NopAuthDeptOutputBean;
    import io.nop.api.core.api.ICrudApi;
    import io.nop.api.core.api.ICrudTreeApi;
    

    @BizModel("NopAuthDept")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthDeptApi extends ICrudApi<NopAuthDeptInputBean, NopAuthDeptOutputBean>,
        ICrudTreeApi<NopAuthDeptOutputBean> {
    }
