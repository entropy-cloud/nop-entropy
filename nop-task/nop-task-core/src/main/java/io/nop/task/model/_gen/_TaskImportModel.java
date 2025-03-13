package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskImportModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskImportModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: as
     * 
     */
    private java.lang.String _as ;
    
    /**
     *  
     * xml name: class
     * 
     */
    private java.lang.String _className ;
    
    /**
     * 
     * xml name: as
     *  
     */
    
    public java.lang.String getAs(){
      return _as;
    }

    
    public void setAs(java.lang.String value){
        checkAllowChange();
        
        this._as = value;
           
    }

    
    /**
     * 
     * xml name: class
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
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
        
        out.putNotNull("as",this.getAs());
        out.putNotNull("className",this.getClassName());
    }

    public TaskImportModel cloneInstance(){
        TaskImportModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskImportModel instance){
        super.copyTo(instance);
        
        instance.setAs(this.getAs());
        instance.setClassName(this.getClassName());
    }

    protected TaskImportModel newInstance(){
        return (TaskImportModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
