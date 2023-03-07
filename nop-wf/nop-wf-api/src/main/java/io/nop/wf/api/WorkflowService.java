//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api;

    import io.nop.api.core.beans.ApiRequest;
    import io.nop.api.core.beans.ApiResponse;
    import java.util.concurrent.CompletionStage;
    import io.nop.api.core.util.FutureHelper;

    
        import io.nop.wf.api.beans.WfStartRequestBean;
    
        import io.nop.wf.api.beans.WfStartResponseBean;
    
        import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
    
        import io.nop.wf.api.beans.WfActionRequestBean;
    
        import java.lang.Object;
    
        import io.nop.wf.api.beans.WfCommandRequestBean;
    

    /**
     * 工作流服务 
     */
    public interface WorkflowService{

    
        /**
         * 
         */
        CompletionStage<ApiResponse<WfStartResponseBean>> startWorkflowAsync(ApiRequest<WfStartRequestBean> request);

        /**
         * 
         */
        default ApiResponse<WfStartResponseBean> startWorkflow(ApiRequest<WfStartRequestBean> request){
            return FutureHelper.syncGet(startWorkflowAsync(request));
        }
    
        /**
         * 
         */
        CompletionStage<ApiResponse<Void>> notifySubFlowEndAsync(ApiRequest<WfSubFlowEndRequestBean> request);

        /**
         * 
         */
        default ApiResponse<Void> notifySubFlowEnd(ApiRequest<WfSubFlowEndRequestBean> request){
            return FutureHelper.syncGet(notifySubFlowEndAsync(request));
        }
    
        /**
         * 
         */
        CompletionStage<ApiResponse<Object>> invokeActionAsync(ApiRequest<WfActionRequestBean> request);

        /**
         * 
         */
        default ApiResponse<Object> invokeAction(ApiRequest<WfActionRequestBean> request){
            return FutureHelper.syncGet(invokeActionAsync(request));
        }
    
        /**
         * 
         */
        CompletionStage<ApiResponse<Void>> killWorkflowAsync(ApiRequest<WfCommandRequestBean> request);

        /**
         * 
         */
        default ApiResponse<Void> killWorkflow(ApiRequest<WfCommandRequestBean> request){
            return FutureHelper.syncGet(killWorkflowAsync(request));
        }
    
        /**
         * 
         */
        CompletionStage<ApiResponse<Void>> suspendWorkflowAsync(ApiRequest<WfCommandRequestBean> request);

        /**
         * 
         */
        default ApiResponse<Void> suspendWorkflow(ApiRequest<WfCommandRequestBean> request){
            return FutureHelper.syncGet(suspendWorkflowAsync(request));
        }
    
        /**
         * 
         */
        CompletionStage<ApiResponse<Void>> resumeWorkflowAsync(ApiRequest<WfCommandRequestBean> request);

        /**
         * 
         */
        default ApiResponse<Void> resumeWorkflow(ApiRequest<WfCommandRequestBean> request){
            return FutureHelper.syncGet(resumeWorkflowAsync(request));
        }
    
    }
