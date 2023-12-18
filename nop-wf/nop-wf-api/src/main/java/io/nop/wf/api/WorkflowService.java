//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api;

    import io.nop.api.core.beans.ApiRequest;
    import io.nop.api.core.beans.ApiResponse;
    import java.util.concurrent.CompletionStage;
    import io.nop.api.core.util.FutureHelper;
    import io.nop.api.core.util.ICancelToken;
    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.api.core.annotations.biz.BizMutation;
    import io.nop.api.core.annotations.biz.BizQuery;
    import io.nop.api.core.annotations.biz.RequestBean;

    import jakarta.ws.rs.POST;
    import jakarta.ws.rs.Path;

    
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
         * 
         */
        @POST
        @Path("/r/WorkflowService__startWorkflow")
        @BizMutation
        CompletionStage<ApiResponse<WfStartResponseBean>> startWorkflowAsync(ApiRequest<WfStartRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<WfStartResponseBean> startWorkflowAsync(@RequestBean WfStartRequestBean request){
            return startWorkflowAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__startWorkflow")
        @BizMutation
        ApiResponse<WfStartResponseBean> startWorkflow(ApiRequest<WfStartRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default WfStartResponseBean startWorkflow(@RequestBean WfStartRequestBean request){
            return startWorkflow(ApiRequest.build(request), null).get();
        }
    
        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__notifySubFlowEnd")
        @BizMutation
        CompletionStage<ApiResponse<Void>> notifySubFlowEndAsync(ApiRequest<WfSubFlowEndRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<Void> notifySubFlowEndAsync(@RequestBean WfSubFlowEndRequestBean request){
            return notifySubFlowEndAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__notifySubFlowEnd")
        @BizMutation
        ApiResponse<Void> notifySubFlowEnd(ApiRequest<WfSubFlowEndRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default void notifySubFlowEnd(@RequestBean WfSubFlowEndRequestBean request){
             notifySubFlowEnd(ApiRequest.build(request), null).get();
        }
    
        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__invokeAction")
        @BizMutation
        CompletionStage<ApiResponse<Object>> invokeActionAsync(ApiRequest<WfActionRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<Object> invokeActionAsync(@RequestBean WfActionRequestBean request){
            return invokeActionAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__invokeAction")
        @BizMutation
        ApiResponse<Object> invokeAction(ApiRequest<WfActionRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default Object invokeAction(@RequestBean WfActionRequestBean request){
            return invokeAction(ApiRequest.build(request), null).get();
        }
    
        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__killWorkflow")
        @BizMutation
        CompletionStage<ApiResponse<Void>> killWorkflowAsync(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<Void> killWorkflowAsync(@RequestBean WfCommandRequestBean request){
            return killWorkflowAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__killWorkflow")
        @BizMutation
        ApiResponse<Void> killWorkflow(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default void killWorkflow(@RequestBean WfCommandRequestBean request){
             killWorkflow(ApiRequest.build(request), null).get();
        }
    
        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__suspendWorkflow")
        @BizMutation
        CompletionStage<ApiResponse<Void>> suspendWorkflowAsync(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<Void> suspendWorkflowAsync(@RequestBean WfCommandRequestBean request){
            return suspendWorkflowAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__suspendWorkflow")
        @BizMutation
        ApiResponse<Void> suspendWorkflow(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default void suspendWorkflow(@RequestBean WfCommandRequestBean request){
             suspendWorkflow(ApiRequest.build(request), null).get();
        }
    
        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__resumeWorkflow")
        @BizMutation
        CompletionStage<ApiResponse<Void>> resumeWorkflowAsync(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default CompletionStage<Void> resumeWorkflowAsync(@RequestBean WfCommandRequestBean request){
            return resumeWorkflowAsync(ApiRequest.build(request), null).thenApply(ApiResponse::get);
        }

        /**
         * 
         */
        @POST
        @Path("/r/WorkflowService__resumeWorkflow")
        @BizMutation
        ApiResponse<Void> resumeWorkflow(ApiRequest<WfCommandRequestBean> request,
            ICancelToken cancelToken);

        /**
         * 
         */
        default void resumeWorkflow(@RequestBean WfCommandRequestBean request){
             resumeWorkflow(ApiRequest.build(request), null).get();
        }
    
    }
