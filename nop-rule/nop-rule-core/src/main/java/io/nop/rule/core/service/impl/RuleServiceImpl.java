package io.nop.rule.core.service.impl;

    import java.util.concurrent.CompletionStage;
    import io.nop.core.context.IServiceContext;
    import io.nop.api.core.util.FutureHelper;
    import io.nop.api.core.beans.FieldSelectionBean;
    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.api.core.annotations.biz.BizQuery;
    import io.nop.api.core.annotations.biz.BizMutation;
    import io.nop.api.core.annotations.biz.RequestBean;
    import io.nop.rule.core.service.RuleServiceSpi;

    
        import io.nop.rule.api.beans.RuleRequestBean;
    
        import io.nop.rule.api.beans.RuleKeyBean;
    
        import io.nop.rule.api.beans.RuleMetaBean;
    

    @BizModel("RuleService")
    public class RuleServiceImpl implements RuleServiceSpi{

    
     @BizMutation
     @Override
     public java.util.Map executeRule(@RequestBean RuleRequestBean request,
          FieldSelectionBean selection, IServiceContext ctx){
        return null;
     }
            
     @BizQuery
     @Override
     public RuleMetaBean getRuleMeta(@RequestBean RuleKeyBean request,
          FieldSelectionBean selection, IServiceContext ctx){
        return null;
     }
            
    }
