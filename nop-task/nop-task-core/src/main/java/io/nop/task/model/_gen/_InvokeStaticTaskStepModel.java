package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.InvokeStaticTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _InvokeStaticTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: method
     * 
     */
    private io.nop.xlang.expr.MethodRef _method ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: method
     *  
     */
    
    public io.nop.xlang.expr.MethodRef getMethod(){
      return _method;
    }

    
    public void setMethod(io.nop.xlang.expr.MethodRef value){
        checkAllowChange();
        
        this._method = value;
           
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
        
        out.putNotNull("method",this.getMethod());
        out.putNotNull("type",this.getType());
    }

    public InvokeStaticTaskStepModel cloneInstance(){
        InvokeStaticTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(InvokeStaticTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setMethod(this.getMethod());
        instance.setType(this.getType());
    }

    protected InvokeStaticTaskStepModel newInstance(){
        return (InvokeStaticTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
