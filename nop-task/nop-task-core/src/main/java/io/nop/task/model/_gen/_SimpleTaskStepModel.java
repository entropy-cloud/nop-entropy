package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.SimpleTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [99:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SimpleTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
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
    }

    public SimpleTaskStepModel cloneInstance(){
        SimpleTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SimpleTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setBean(this.getBean());
    }

    protected SimpleTaskStepModel newInstance(){
        return (SimpleTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
