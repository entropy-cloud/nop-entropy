//__XGEN_FORCE_OVERRIDE__
    package io.nop.task.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.task.api.beans.NopTaskDefinitionInputBean;
    import io.nop.task.api.beans.NopTaskDefinitionOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopTaskDefinition")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopTaskDefinitionApi extends ICrudApi<NopTaskDefinitionInputBean, NopTaskDefinitionOutputBean> {
    }
