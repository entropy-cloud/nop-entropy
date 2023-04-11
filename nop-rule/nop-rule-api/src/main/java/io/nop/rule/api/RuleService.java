//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api;

    import io.nop.api.core.beans.ApiRequest;
    import io.nop.api.core.beans.ApiResponse;
    import java.util.concurrent.CompletionStage;
    import io.nop.api.core.util.FutureHelper;

    
        import io.nop.rule.api.beans.RuleRequestBean;
    
        import io.nop.rule.api.beans.RuleKeyBean;
    
        import io.nop.rule.api.beans.RuleMetaBean;
    

    /**
     * 规则引擎服务 
     */
    public interface RuleService{

    
        /**
         * 
         */
        CompletionStage<ApiResponse<java.util.Map>> executeRuleAsync(ApiRequest<RuleRequestBean> request);

        /**
         * 
         */
        default ApiResponse<java.util.Map> executeRule(ApiRequest<RuleRequestBean> request){
            return FutureHelper.syncGet(executeRuleAsync(request));
        }
    
        /**
         * 
         */
        CompletionStage<ApiResponse<RuleMetaBean>> getRuleMetaAsync(ApiRequest<RuleKeyBean> request);

        /**
         * 
         */
        default ApiResponse<RuleMetaBean> getRuleMeta(ApiRequest<RuleKeyBean> request){
            return FutureHelper.syncGet(getRuleMetaAsync(request));
        }
    
    }
