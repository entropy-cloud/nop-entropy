package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchRetryPolicyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchRetryPolicyModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: exceptionFilter
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _exceptionFilter ;
    
    /**
     *  
     * xml name: exponentialDelay
     * 
     */
    private java.lang.Boolean _exponentialDelay  = true;
    
    /**
     *  
     * xml name: jitterRatio
     * 
     */
    private java.lang.Double _jitterRatio  = 0.3;
    
    /**
     *  
     * xml name: maxRetryCount
     * 
     */
    private java.lang.Integer _maxRetryCount ;
    
    /**
     *  
     * xml name: maxRetryDelay
     * 
     */
    private java.lang.Integer _maxRetryDelay ;
    
    /**
     *  
     * xml name: retryDelay
     * 
     */
    private java.lang.Integer _retryDelay ;
    
    /**
     * 
     * xml name: exceptionFilter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getExceptionFilter(){
      return _exceptionFilter;
    }

    
    public void setExceptionFilter(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._exceptionFilter = value;
           
    }

    
    /**
     * 
     * xml name: exponentialDelay
     *  
     */
    
    public java.lang.Boolean getExponentialDelay(){
      return _exponentialDelay;
    }

    
    public void setExponentialDelay(java.lang.Boolean value){
        checkAllowChange();
        
        this._exponentialDelay = value;
           
    }

    
    /**
     * 
     * xml name: jitterRatio
     *  
     */
    
    public java.lang.Double getJitterRatio(){
      return _jitterRatio;
    }

    
    public void setJitterRatio(java.lang.Double value){
        checkAllowChange();
        
        this._jitterRatio = value;
           
    }

    
    /**
     * 
     * xml name: maxRetryCount
     *  
     */
    
    public java.lang.Integer getMaxRetryCount(){
      return _maxRetryCount;
    }

    
    public void setMaxRetryCount(java.lang.Integer value){
        checkAllowChange();
        
        this._maxRetryCount = value;
           
    }

    
    /**
     * 
     * xml name: maxRetryDelay
     *  
     */
    
    public java.lang.Integer getMaxRetryDelay(){
      return _maxRetryDelay;
    }

    
    public void setMaxRetryDelay(java.lang.Integer value){
        checkAllowChange();
        
        this._maxRetryDelay = value;
           
    }

    
    /**
     * 
     * xml name: retryDelay
     *  
     */
    
    public java.lang.Integer getRetryDelay(){
      return _retryDelay;
    }

    
    public void setRetryDelay(java.lang.Integer value){
        checkAllowChange();
        
        this._retryDelay = value;
           
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
        
        out.putNotNull("exceptionFilter",this.getExceptionFilter());
        out.putNotNull("exponentialDelay",this.getExponentialDelay());
        out.putNotNull("jitterRatio",this.getJitterRatio());
        out.putNotNull("maxRetryCount",this.getMaxRetryCount());
        out.putNotNull("maxRetryDelay",this.getMaxRetryDelay());
        out.putNotNull("retryDelay",this.getRetryDelay());
    }

    public BatchRetryPolicyModel cloneInstance(){
        BatchRetryPolicyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchRetryPolicyModel instance){
        super.copyTo(instance);
        
        instance.setExceptionFilter(this.getExceptionFilter());
        instance.setExponentialDelay(this.getExponentialDelay());
        instance.setJitterRatio(this.getJitterRatio());
        instance.setMaxRetryCount(this.getMaxRetryCount());
        instance.setMaxRetryDelay(this.getMaxRetryDelay());
        instance.setRetryDelay(this.getRetryDelay());
    }

    protected BatchRetryPolicyModel newInstance(){
        return (BatchRetryPolicyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
