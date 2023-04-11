//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.core.service;

    import java.util.concurrent.CompletionStage;
    import io.nop.core.context.IServiceContext;

    
        import io.nop.rule.api.beans.RuleRequestBean;
    
        import io.nop.rule.api.beans.RuleKeyBean;
    
        import io.nop.rule.api.beans.RuleMetaBean;
    

    @SuppressWarnings({"PMD"})
    public interface RuleServiceSpi{

    
     /**
      * 执行规则 
      */
     java.util.Map executeRule(RuleRequestBean request, IServiceContext ctx);
            
     /**
      * 得到规则元数据 
      */
     RuleMetaBean getRuleMeta(RuleKeyBean request, IServiceContext ctx);
            
    }
