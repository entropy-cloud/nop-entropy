//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.core.service;

    import java.util.concurrent.CompletionStage;
    import io.nop.core.context.IServiceContext;
    import io.nop.api.core.beans.FieldSelectionBean;

    
        import io.nop.wf.api.beans.WfStartRequestBean;
    
        import io.nop.wf.api.beans.WfStartResponseBean;
    
        import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
    
        import io.nop.wf.api.beans.WfActionRequestBean;
    
        import java.lang.Object;
    
        import io.nop.wf.api.beans.WfCommandRequestBean;
    

    @SuppressWarnings({"PMD"})
    public interface WorkflowServiceSpi{

    
     /**
      * 启动工作流 
      */
     WfStartResponseBean startWorkflow(WfStartRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
     /**
      * 通知子工作流结束 
      */
     void notifySubFlowEnd(WfSubFlowEndRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
     /**
      * 执行动作 
      */
     CompletionStage<Object> invokeActionAsync(WfActionRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
     /**
      * 中止工作流 
      */
     void killWorkflow(WfCommandRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
     /**
      * 暂停工作流 
      */
     void suspendWorkflow(WfCommandRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
     /**
      * 继续工作流 
      */
     void resumeWorkflow(WfCommandRequestBean request,
            FieldSelectionBean selection, IServiceContext ctx);
            
    }
