package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskOutputModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [30:10:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskOutputModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: forAttr
     * 如果为true，则输出变量到taskContext.attributes集合中，否则输出到parentScope中
     */
    private boolean _forAttr  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: persist
     * 输出变量是否需要被持久化到数据库中。如果不设置持久化，则一旦中断任务则会丢失相应的输出变量
     */
    private boolean _persist  = false;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: forAttr
     *  如果为true，则输出变量到taskContext.attributes集合中，否则输出到parentScope中
     */
    
    public boolean isForAttr(){
      return _forAttr;
    }

    
    public void setForAttr(boolean value){
        checkAllowChange();
        
        this._forAttr = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: persist
     *  输出变量是否需要被持久化到数据库中。如果不设置持久化，则一旦中断任务则会丢失相应的输出变量
     */
    
    public boolean isPersist(){
      return _persist;
    }

    
    public void setPersist(boolean value){
        checkAllowChange();
        
        this._persist = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("forAttr",this.isForAttr());
        out.put("name",this.getName());
        out.put("persist",this.isPersist());
        out.put("source",this.getSource());
    }

    public TaskOutputModel cloneInstance(){
        TaskOutputModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskOutputModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setForAttr(this.isForAttr());
        instance.setName(this.getName());
        instance.setPersist(this.isPersist());
        instance.setSource(this.getSource());
    }

    protected TaskOutputModel newInstance(){
        return (TaskOutputModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
