//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api;

    import io.nop.api.core.beans.ApiRequest;
    import io.nop.api.core.beans.ApiResponse;
    import java.util.concurrent.CompletionStage;
    import io.nop.api.core.util.FutureHelper;
    import io.nop.api.core.util.ICancelToken;
    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.api.core.annotations.biz.BizMutation;
    import io.nop.api.core.annotations.biz.BizQuery;
    import io.nop.api.core.annotations.biz.RequestBean;

    
        import io.nop.rule.api.beans.RuleRequestBean;
    
        import io.nop.rule.api.beans.RuleResultBean;
    
        import io.nop.rule.api.beans.RuleKeyBean;
    
        import io.nop.rule.api.beans.RuleMetaBean;
    

    /**
     * 规则引擎服务 
     */
    @BizModel("RuleService")
    public interface RuleService{

    
        /**
         * 
         */
        @BizMutation
        CompletionStage<ApiResponse<RuleResultBean>> executeRuleAsync(ApiRequest<RuleRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<RuleResultBean> executeRuleAsync(@RequestBean RuleRequestBean request){
            return executeRuleAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @BizMutation
        ApiResponse<RuleResultBean> executeRule(ApiRequest<RuleRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default RuleResultBean executeRule(@RequestBean RuleRequestBean request){
            return executeRule(ApiRequest.build(request), null).get();
        }
    
        /**
         * 
         */
        @BizQuery
        CompletionStage<ApiResponse<RuleMetaBean>> getRuleMetaAsync(ApiRequest<RuleKeyBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<RuleMetaBean> getRuleMetaAsync(@RequestBean RuleKeyBean request){
            return getRuleMetaAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @BizQuery
        ApiResponse<RuleMetaBean> getRuleMeta(ApiRequest<RuleKeyBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default RuleMetaBean getRuleMeta(@RequestBean RuleKeyBean request){
            return getRuleMeta(ApiRequest.build(request), null).get();
        }
    
    }
