package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.CustomTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CustomTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: customType
     * 
     */
    private java.lang.String _customType ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: customType
     *  
     */
    
    public java.lang.String getCustomType(){
      return _customType;
    }

    
    public void setCustomType(java.lang.String value){
        checkAllowChange();
        
        this._customType = value;
           
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
        
        out.putNotNull("customType",this.getCustomType());
        out.putNotNull("type",this.getType());
    }

    public CustomTaskStepModel cloneInstance(){
        CustomTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CustomTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setCustomType(this.getCustomType());
        instance.setType(this.getType());
    }

    protected CustomTaskStepModel newInstance(){
        return (CustomTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
