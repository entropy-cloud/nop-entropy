//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.job.api.beans.NopJobTaskInputBean;
    import io.nop.job.api.beans.NopJobTaskOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopJobTask")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopJobTaskApi extends ICrudApi<NopJobTaskInputBean, NopJobTaskOutputBean> {
    }
