//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.dyn.api.beans.NopDynEntityInputBean;
    import io.nop.dyn.api.beans.NopDynEntityOutputBean;
    import io.nop.api.core.api.ICrudApi;
    import io.nop.api.core.api.ICrudTreeApi;
    

    @BizModel("NopDynEntity")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopDynEntityApi extends ICrudApi<NopDynEntityInputBean, NopDynEntityOutputBean>,
        ICrudTreeApi<NopDynEntityOutputBean> {
    }
