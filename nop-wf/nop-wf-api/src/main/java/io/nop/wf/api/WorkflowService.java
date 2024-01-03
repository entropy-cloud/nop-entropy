//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api;

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

    
        import io.nop.wf.api.beans.WfStartRequestBean;
    
        import io.nop.wf.api.beans.WfStartResponseBean;
    
        import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
    
        import io.nop.wf.api.beans.WfActionRequestBean;
    
        import io.nop.wf.api.beans.WfCommandRequestBean;
    

    /**
     * 工作流服务 
     */
    @BizModel("WorkflowService")
    public interface WorkflowService{

    

        /**
         * 启动工作流 
         */
        @POST
        @Path("/r/WorkflowService__startWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("startWorkflow")
        CompletionStage<ApiResponse<WfStartResponseBean>> startWorkflowAsync(@RequestBean WfStartRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 启动工作流 
         */
        @POST
        @Path("/r/WorkflowService__startWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("startWorkflow")
        ApiResponse<WfStartResponseBean> startWorkflow(@RequestBean WfStartRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    

        /**
         * 通知子工作流结束 
         */
        @POST
        @Path("/r/WorkflowService__notifySubFlowEnd")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("notifySubFlowEnd")
        CompletionStage<ApiResponse<Void>> notifySubFlowEndAsync(@RequestBean WfSubFlowEndRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 通知子工作流结束 
         */
        @POST
        @Path("/r/WorkflowService__notifySubFlowEnd")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("notifySubFlowEnd")
        ApiResponse<Void> notifySubFlowEnd(@RequestBean WfSubFlowEndRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    

        /**
         * 执行动作 
         */
        @POST
        @Path("/r/WorkflowService__invokeAction")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("invokeAction")
        CompletionStage<ApiResponse<Object>> invokeActionAsync(@RequestBean WfActionRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 执行动作 
         */
        @POST
        @Path("/r/WorkflowService__invokeAction")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("invokeAction")
        ApiResponse<Object> invokeAction(@RequestBean WfActionRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    

        /**
         * 中止工作流 
         */
        @POST
        @Path("/r/WorkflowService__killWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("killWorkflow")
        CompletionStage<ApiResponse<Void>> killWorkflowAsync(@RequestBean WfCommandRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 中止工作流 
         */
        @POST
        @Path("/r/WorkflowService__killWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("killWorkflow")
        ApiResponse<Void> killWorkflow(@RequestBean WfCommandRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    

        /**
         * 暂停工作流 
         */
        @POST
        @Path("/r/WorkflowService__suspendWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("suspendWorkflow")
        CompletionStage<ApiResponse<Void>> suspendWorkflowAsync(@RequestBean WfCommandRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 暂停工作流 
         */
        @POST
        @Path("/r/WorkflowService__suspendWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("suspendWorkflow")
        ApiResponse<Void> suspendWorkflow(@RequestBean WfCommandRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    

        /**
         * 继续工作流 
         */
        @POST
        @Path("/r/WorkflowService__resumeWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("resumeWorkflow")
        CompletionStage<ApiResponse<Void>> resumeWorkflowAsync(@RequestBean WfCommandRequestBean request,
            @QueryParam(SYS_PARAM_SELECTION) String selection);


        /**
         * 继续工作流 
         */
        @POST
        @Path("/r/WorkflowService__resumeWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("resumeWorkflow")
        ApiResponse<Void> resumeWorkflow(@RequestBean WfCommandRequestBean request,
             @QueryParam(SYS_PARAM_SELECTION) String selection);

    
        /**
         * 启动工作流 
         */
        @POST
        @Path("/r/WorkflowService__startWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("startWorkflow")
        CompletionStage<ApiResponse<WfStartResponseBean>> api_startWorkflowAsync(ApiRequest<WfStartRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 启动工作流 
         */
        @POST
        @Path("/r/WorkflowService__startWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("startWorkflow")
        ApiResponse<WfStartResponseBean> api_startWorkflow(ApiRequest<WfStartRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 通知子工作流结束 
         */
        @POST
        @Path("/r/WorkflowService__notifySubFlowEnd")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("notifySubFlowEnd")
        CompletionStage<ApiResponse<Void>> api_notifySubFlowEndAsync(ApiRequest<WfSubFlowEndRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 通知子工作流结束 
         */
        @POST
        @Path("/r/WorkflowService__notifySubFlowEnd")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("notifySubFlowEnd")
        ApiResponse<Void> api_notifySubFlowEnd(ApiRequest<WfSubFlowEndRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 执行动作 
         */
        @POST
        @Path("/r/WorkflowService__invokeAction")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("invokeAction")
        CompletionStage<ApiResponse<Object>> api_invokeActionAsync(ApiRequest<WfActionRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 执行动作 
         */
        @POST
        @Path("/r/WorkflowService__invokeAction")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("invokeAction")
        ApiResponse<Object> api_invokeAction(ApiRequest<WfActionRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 中止工作流 
         */
        @POST
        @Path("/r/WorkflowService__killWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("killWorkflow")
        CompletionStage<ApiResponse<Void>> api_killWorkflowAsync(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 中止工作流 
         */
        @POST
        @Path("/r/WorkflowService__killWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("killWorkflow")
        ApiResponse<Void> api_killWorkflow(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 暂停工作流 
         */
        @POST
        @Path("/r/WorkflowService__suspendWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("suspendWorkflow")
        CompletionStage<ApiResponse<Void>> api_suspendWorkflowAsync(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 暂停工作流 
         */
        @POST
        @Path("/r/WorkflowService__suspendWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("suspendWorkflow")
        ApiResponse<Void> api_suspendWorkflow(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 继续工作流 
         */
        @POST
        @Path("/r/WorkflowService__resumeWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("resumeWorkflow")
        CompletionStage<ApiResponse<Void>> api_resumeWorkflowAsync(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 继续工作流 
         */
        @POST
        @Path("/r/WorkflowService__resumeWorkflow")
        @Consumes(MediaType.APPLICATION_JSON)
        @BizMutation("resumeWorkflow")
        ApiResponse<Void> api_resumeWorkflow(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

    }
