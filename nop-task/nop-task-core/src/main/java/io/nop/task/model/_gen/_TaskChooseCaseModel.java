package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskChooseCaseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [185:18:0:0]/nop/schema/task/task.xdef <p>
 * 可能用于全局跳转，因此不使用嵌套步骤定义
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskChooseCaseModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: match
     * 
     */
    private java.lang.String _match ;
    
    /**
     *  
     * xml name: to
     * 
     */
    private java.lang.String _to ;
    
    /**
     * 
     * xml name: match
     *  
     */
    
    public java.lang.String getMatch(){
      return _match;
    }

    
    public void setMatch(java.lang.String value){
        checkAllowChange();
        
        this._match = value;
           
    }

    
    /**
     * 
     * xml name: to
     *  
     */
    
    public java.lang.String getTo(){
      return _to;
    }

    
    public void setTo(java.lang.String value){
        checkAllowChange();
        
        this._to = value;
           
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
        
        out.putNotNull("match",this.getMatch());
        out.putNotNull("to",this.getTo());
    }

    public TaskChooseCaseModel cloneInstance(){
        TaskChooseCaseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskChooseCaseModel instance){
        super.copyTo(instance);
        
        instance.setMatch(this.getMatch());
        instance.setTo(this.getTo());
    }

    protected TaskChooseCaseModel newInstance(){
        return (TaskChooseCaseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
