//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api;

    import java.util.concurrent.CompletionStage;

    import io.nop.api.core.beans.ApiRequest;
    import io.nop.api.core.beans.ApiResponse;
    import io.nop.api.core.util.FutureHelper;
    import io.nop.api.core.util.ICancelToken;
    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.api.core.annotations.biz.BizMutation;
    import io.nop.api.core.annotations.biz.BizQuery;
    import io.nop.api.core.annotations.biz.RequestBean;
    import static io.nop.api.core.ApiConstants.SYS_PARAM_SELECTION;

    import jakarta.ws.rs.POST;
    import jakarta.ws.rs.Path;
    import jakarta.ws.rs.Consumes;
    import jakarta.ws.rs.core.MediaType;
    import jakarta.ws.rs.QueryParam;

    
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
         * 执行规则 
         */
        @POST
        @Path("/r/RuleService__executeRule")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("executeRule")
        CompletionStage<ApiResponse<RuleResultBean>> executeRuleAsync(@RequestBean RuleRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 执行规则 
         */
        @POST
        @Path("/r/RuleService__executeRule")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("executeRule")
        ApiResponse<RuleResultBean> executeRule(@RequestBean RuleRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    

        /**
         * 得到规则元数据 
         */
        @POST
        @Path("/r/RuleService__getRuleMeta")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizQuery("getRuleMeta")
        CompletionStage<ApiResponse<RuleMetaBean>> getRuleMetaAsync(@RequestBean RuleKeyBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 得到规则元数据 
         */
        @POST
        @Path("/r/RuleService__getRuleMeta")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizQuery("getRuleMeta")
        ApiResponse<RuleMetaBean> getRuleMeta(@RequestBean RuleKeyBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    
        /**
         * 执行规则 
         */
        @POST
        @Path("/r/RuleService__executeRule")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("executeRule")
        CompletionStage<ApiResponse<RuleResultBean>> api_executeRuleAsync(ApiRequest<RuleRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 执行规则 
         */
        @POST
        @Path("/r/RuleService__executeRule")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("executeRule")
        ApiResponse<RuleResultBean> api_executeRule(ApiRequest<RuleRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 得到规则元数据 
         */
        @POST
        @Path("/r/RuleService__getRuleMeta")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizQuery("getRuleMeta")
        CompletionStage<ApiResponse<RuleMetaBean>> api_getRuleMetaAsync(ApiRequest<RuleKeyBean> request,
            ICancelToken cancelToken);

        /**
         * 得到规则元数据 
         */
        @POST
        @Path("/r/RuleService__getRuleMeta")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizQuery("getRuleMeta")
        ApiResponse<RuleMetaBean> api_getRuleMeta(ApiRequest<RuleKeyBean> request,
            ICancelToken cancelToken);

    }
