//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.rule.api.beans.NopRuleNodeInputBean;
    import io.nop.rule.api.beans.NopRuleNodeOutputBean;
    import io.nop.api.core.api.ICrudApi;
    import io.nop.api.core.api.ICrudTreeApi;
    

    @BizModel("NopRuleNode")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopRuleNodeApi extends ICrudApi<NopRuleNodeInputBean, NopRuleNodeOutputBean>,
        ICrudTreeApi<NopRuleNodeOutputBean> {
    }
