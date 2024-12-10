package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchListenersModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchListenersModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: onBeforeChunkEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onBeforeChunkEnd ;
    
    /**
     *  
     * xml name: onBeforeTaskEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onBeforeTaskEnd ;
    
    /**
     *  
     * xml name: onChunkBegin
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onChunkBegin ;
    
    /**
     *  
     * xml name: onChunkEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onChunkEnd ;
    
    /**
     *  
     * xml name: onChunkTryBegin
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onChunkTryBegin ;
    
    /**
     *  
     * xml name: onChunkTryEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onChunkTryEnd ;
    
    /**
     *  
     * xml name: onConsumeBegin
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onConsumeBegin ;
    
    /**
     *  
     * xml name: onConsumeEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onConsumeEnd ;
    
    /**
     *  
     * xml name: onLoadBegin
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onLoadBegin ;
    
    /**
     *  
     * xml name: onLoadEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onLoadEnd ;
    
    /**
     *  
     * xml name: onTaskBegin
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onTaskBegin ;
    
    /**
     *  
     * xml name: onTaskEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onTaskEnd ;
    
    /**
     * 
     * xml name: onBeforeChunkEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnBeforeChunkEnd(){
      return _onBeforeChunkEnd;
    }

    
    public void setOnBeforeChunkEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onBeforeChunkEnd = value;
           
    }

    
    /**
     * 
     * xml name: onBeforeTaskEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnBeforeTaskEnd(){
      return _onBeforeTaskEnd;
    }

    
    public void setOnBeforeTaskEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onBeforeTaskEnd = value;
           
    }

    
    /**
     * 
     * xml name: onChunkBegin
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnChunkBegin(){
      return _onChunkBegin;
    }

    
    public void setOnChunkBegin(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onChunkBegin = value;
           
    }

    
    /**
     * 
     * xml name: onChunkEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnChunkEnd(){
      return _onChunkEnd;
    }

    
    public void setOnChunkEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onChunkEnd = value;
           
    }

    
    /**
     * 
     * xml name: onChunkTryBegin
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnChunkTryBegin(){
      return _onChunkTryBegin;
    }

    
    public void setOnChunkTryBegin(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onChunkTryBegin = value;
           
    }

    
    /**
     * 
     * xml name: onChunkTryEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnChunkTryEnd(){
      return _onChunkTryEnd;
    }

    
    public void setOnChunkTryEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onChunkTryEnd = value;
           
    }

    
    /**
     * 
     * xml name: onConsumeBegin
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnConsumeBegin(){
      return _onConsumeBegin;
    }

    
    public void setOnConsumeBegin(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onConsumeBegin = value;
           
    }

    
    /**
     * 
     * xml name: onConsumeEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnConsumeEnd(){
      return _onConsumeEnd;
    }

    
    public void setOnConsumeEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onConsumeEnd = value;
           
    }

    
    /**
     * 
     * xml name: onLoadBegin
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnLoadBegin(){
      return _onLoadBegin;
    }

    
    public void setOnLoadBegin(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onLoadBegin = value;
           
    }

    
    /**
     * 
     * xml name: onLoadEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnLoadEnd(){
      return _onLoadEnd;
    }

    
    public void setOnLoadEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onLoadEnd = value;
           
    }

    
    /**
     * 
     * xml name: onTaskBegin
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnTaskBegin(){
      return _onTaskBegin;
    }

    
    public void setOnTaskBegin(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onTaskBegin = value;
           
    }

    
    /**
     * 
     * xml name: onTaskEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnTaskEnd(){
      return _onTaskEnd;
    }

    
    public void setOnTaskEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onTaskEnd = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("onBeforeChunkEnd",this.getOnBeforeChunkEnd());
        out.putNotNull("onBeforeTaskEnd",this.getOnBeforeTaskEnd());
        out.putNotNull("onChunkBegin",this.getOnChunkBegin());
        out.putNotNull("onChunkEnd",this.getOnChunkEnd());
        out.putNotNull("onChunkTryBegin",this.getOnChunkTryBegin());
        out.putNotNull("onChunkTryEnd",this.getOnChunkTryEnd());
        out.putNotNull("onConsumeBegin",this.getOnConsumeBegin());
        out.putNotNull("onConsumeEnd",this.getOnConsumeEnd());
        out.putNotNull("onLoadBegin",this.getOnLoadBegin());
        out.putNotNull("onLoadEnd",this.getOnLoadEnd());
        out.putNotNull("onTaskBegin",this.getOnTaskBegin());
        out.putNotNull("onTaskEnd",this.getOnTaskEnd());
    }

    public BatchListenersModel cloneInstance(){
        BatchListenersModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchListenersModel instance){
        super.copyTo(instance);
        
        instance.setOnBeforeChunkEnd(this.getOnBeforeChunkEnd());
        instance.setOnBeforeTaskEnd(this.getOnBeforeTaskEnd());
        instance.setOnChunkBegin(this.getOnChunkBegin());
        instance.setOnChunkEnd(this.getOnChunkEnd());
        instance.setOnChunkTryBegin(this.getOnChunkTryBegin());
        instance.setOnChunkTryEnd(this.getOnChunkTryEnd());
        instance.setOnConsumeBegin(this.getOnConsumeBegin());
        instance.setOnConsumeEnd(this.getOnConsumeEnd());
        instance.setOnLoadBegin(this.getOnLoadBegin());
        instance.setOnLoadEnd(this.getOnLoadEnd());
        instance.setOnTaskBegin(this.getOnTaskBegin());
        instance.setOnTaskEnd(this.getOnTaskEnd());
    }

    protected BatchListenersModel newInstance(){
        return (BatchListenersModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
