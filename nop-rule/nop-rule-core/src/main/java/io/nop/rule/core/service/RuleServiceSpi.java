//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.core.service;

    import java.util.concurrent.CompletionStage;
    import io.nop.core.context.IServiceContext;
    import io.nop.api.core.beans.FieldSelectionBean;

    
        import io.nop.rule.api.beans.RuleRequestBean;
    
        import io.nop.rule.api.beans.RuleResultBean;
    
        import io.nop.rule.api.beans.RuleKeyBean;
    
        import io.nop.rule.api.beans.RuleMetaBean;
    

    /**
     * 规则引擎服务 
     */
    @SuppressWarnings({"PMD"})
    public interface RuleServiceSpi{

    
     /**
      * 执行规则 
      */
     RuleResultBean executeRule(RuleRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
     /**
      * 得到规则元数据 
      */
     RuleMetaBean getRuleMeta(RuleKeyBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
    }
