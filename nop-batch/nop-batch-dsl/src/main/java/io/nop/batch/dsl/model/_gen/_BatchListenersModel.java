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
        
        out.putNotNull("onChunkBegin",this.getOnChunkBegin());
        out.putNotNull("onChunkEnd",this.getOnChunkEnd());
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
        
        instance.setOnChunkBegin(this.getOnChunkBegin());
        instance.setOnChunkEnd(this.getOnChunkEnd());
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
