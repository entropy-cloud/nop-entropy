package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.InvokeTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [203:14:0:0]/nop/schema/task/task.xdef <p>
 * 执行指定bean上的指定方法
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _InvokeTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: method
     * 
     */
    private java.lang.String _method ;
    
    /**
     *  
     * xml name: returnAs
     * 指定bean方法的返回值所对应的返回变量名，缺省为RESULT
     */
    private java.lang.String _returnAs ;
    
    /**
     * 
     * xml name: bean
     *  
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: method
     *  
     */
    
    public java.lang.String getMethod(){
      return _method;
    }

    
    public void setMethod(java.lang.String value){
        checkAllowChange();
        
        this._method = value;
           
    }

    
    /**
     * 
     * xml name: returnAs
     *  指定bean方法的返回值所对应的返回变量名，缺省为RESULT
     */
    
    public java.lang.String getReturnAs(){
      return _returnAs;
    }

    
    public void setReturnAs(java.lang.String value){
        checkAllowChange();
        
        this._returnAs = value;
           
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
        
        out.putNotNull("bean",this.getBean());
        out.putNotNull("method",this.getMethod());
        out.putNotNull("returnAs",this.getReturnAs());
    }

    public InvokeTaskStepModel cloneInstance(){
        InvokeTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(InvokeTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setBean(this.getBean());
        instance.setMethod(this.getMethod());
        instance.setReturnAs(this.getReturnAs());
    }

    protected InvokeTaskStepModel newInstance(){
        return (InvokeTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
