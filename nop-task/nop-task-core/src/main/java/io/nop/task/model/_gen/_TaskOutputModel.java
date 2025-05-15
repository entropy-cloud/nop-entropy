package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskOutputModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
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
     * xml name: dump
     * 
     */
    private boolean _dump  = false;
    
    /**
     *  
     * xml name: exportAs
     * 返回时会将output中的变量设置到parentScope中，一般情况下设置的变量名与output变量名相同。可以通过exportAs来改变这个变量名
     */
    private java.lang.String _exportAs ;
    
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
     * xml name: roles
     * 
     */
    private java.util.Set<java.lang.String> _roles ;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     *  
     * xml name: toTaskScope
     * 如果为true，则输出变量到整个task共享的scope中，否则输出到parentScope中
     */
    private boolean _toTaskScope  = false;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _value ;
    
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
     * xml name: dump
     *  
     */
    
    public boolean isDump(){
      return _dump;
    }

    
    public void setDump(boolean value){
        checkAllowChange();
        
        this._dump = value;
           
    }

    
    /**
     * 
     * xml name: exportAs
     *  返回时会将output中的变量设置到parentScope中，一般情况下设置的变量名与output变量名相同。可以通过exportAs来改变这个变量名
     */
    
    public java.lang.String getExportAs(){
      return _exportAs;
    }

    
    public void setExportAs(java.lang.String value){
        checkAllowChange();
        
        this._exportAs = value;
           
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
     * xml name: roles
     *  
     */
    
    public java.util.Set<java.lang.String> getRoles(){
      return _roles;
    }

    
    public void setRoles(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._roles = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    
    public io.nop.xlang.xmeta.ISchema getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.xlang.xmeta.ISchema value){
        checkAllowChange();
        
        this._schema = value;
           
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

    
    /**
     * 
     * xml name: toTaskScope
     *  如果为true，则输出变量到整个task共享的scope中，否则输出到parentScope中
     */
    
    public boolean isToTaskScope(){
      return _toTaskScope;
    }

    
    public void setToTaskScope(boolean value){
        checkAllowChange();
        
        this._toTaskScope = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValue(){
      return _value;
    }

    
    public void setValue(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._value = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("dump",this.isDump());
        out.putNotNull("exportAs",this.getExportAs());
        out.putNotNull("name",this.getName());
        out.putNotNull("persist",this.isPersist());
        out.putNotNull("roles",this.getRoles());
        out.putNotNull("schema",this.getSchema());
        out.putNotNull("source",this.getSource());
        out.putNotNull("toTaskScope",this.isToTaskScope());
        out.putNotNull("type",this.getType());
        out.putNotNull("value",this.getValue());
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
        instance.setDump(this.isDump());
        instance.setExportAs(this.getExportAs());
        instance.setName(this.getName());
        instance.setPersist(this.isPersist());
        instance.setRoles(this.getRoles());
        instance.setSchema(this.getSchema());
        instance.setSource(this.getSource());
        instance.setToTaskScope(this.isToTaskScope());
        instance.setType(this.getType());
        instance.setValue(this.getValue());
    }

    protected TaskOutputModel newInstance(){
        return (TaskOutputModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
