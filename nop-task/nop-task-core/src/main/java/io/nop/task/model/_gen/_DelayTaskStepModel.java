package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.DelayTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 延迟执行后续step
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DelayTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: delayMillisExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _delayMillisExpr ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: delayMillisExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getDelayMillisExpr(){
      return _delayMillisExpr;
    }

    
    public void setDelayMillisExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._delayMillisExpr = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("delayMillisExpr",this.getDelayMillisExpr());
        out.putNotNull("type",this.getType());
    }

    public DelayTaskStepModel cloneInstance(){
        DelayTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DelayTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setDelayMillisExpr(this.getDelayMillisExpr());
        instance.setType(this.getType());
    }

    protected DelayTaskStepModel newInstance(){
        return (DelayTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
