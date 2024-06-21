package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.CallStepTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 调用task step步骤定义库中的某个指定步骤。步骤定义库的基本结构与task相同，只是没有执行能力
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CallStepTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: libName
     * 
     */
    private java.lang.String _libName ;
    
    /**
     *  
     * xml name: libVersion
     * 
     */
    private java.lang.Long _libVersion ;
    
    /**
     *  
     * xml name: stepName
     * 
     */
    private java.lang.String _stepName ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: libName
     *  
     */
    
    public java.lang.String getLibName(){
      return _libName;
    }

    
    public void setLibName(java.lang.String value){
        checkAllowChange();
        
        this._libName = value;
           
    }

    
    /**
     * 
     * xml name: libVersion
     *  
     */
    
    public java.lang.Long getLibVersion(){
      return _libVersion;
    }

    
    public void setLibVersion(java.lang.Long value){
        checkAllowChange();
        
        this._libVersion = value;
           
    }

    
    /**
     * 
     * xml name: stepName
     *  
     */
    
    public java.lang.String getStepName(){
      return _stepName;
    }

    
    public void setStepName(java.lang.String value){
        checkAllowChange();
        
        this._stepName = value;
           
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
        
        out.putNotNull("libName",this.getLibName());
        out.putNotNull("libVersion",this.getLibVersion());
        out.putNotNull("stepName",this.getStepName());
        out.putNotNull("type",this.getType());
    }

    public CallStepTaskStepModel cloneInstance(){
        CallStepTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CallStepTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setLibName(this.getLibName());
        instance.setLibVersion(this.getLibVersion());
        instance.setStepName(this.getStepName());
        instance.setType(this.getType());
    }

    protected CallStepTaskStepModel newInstance(){
        return (CallStepTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
