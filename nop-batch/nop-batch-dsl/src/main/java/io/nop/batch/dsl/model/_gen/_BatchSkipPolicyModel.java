package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchSkipPolicyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchSkipPolicyModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: exceptionFilter
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _exceptionFilter ;
    
    /**
     *  
     * xml name: maxSkipCount
     * 
     */
    private java.lang.Long _maxSkipCount ;
    
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
     * xml name: maxSkipCount
     *  
     */
    
    public java.lang.Long getMaxSkipCount(){
      return _maxSkipCount;
    }

    
    public void setMaxSkipCount(java.lang.Long value){
        checkAllowChange();
        
        this._maxSkipCount = value;
           
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
        out.putNotNull("maxSkipCount",this.getMaxSkipCount());
    }

    public BatchSkipPolicyModel cloneInstance(){
        BatchSkipPolicyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchSkipPolicyModel instance){
        super.copyTo(instance);
        
        instance.setExceptionFilter(this.getExceptionFilter());
        instance.setMaxSkipCount(this.getMaxSkipCount());
    }

    protected BatchSkipPolicyModel newInstance(){
        return (BatchSkipPolicyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
