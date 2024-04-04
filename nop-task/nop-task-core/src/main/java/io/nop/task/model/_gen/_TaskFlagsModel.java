package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskFlagsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [30:10:0:0]/nop/schema/task/task.xdef <p>
 * 可以根据动态设置的flag来决定是否执行本步骤
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskFlagsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: disable
     * 删除flag, 在子步骤范围内，这些flag无效
     */
    private java.util.Set<java.lang.String> _disable ;
    
    /**
     *  
     * xml name: enable
     * 设置flag，在子步骤范围内，这些flag有效
     */
    private java.util.Set<java.lang.String> _enable ;
    
    /**
     *  
     * xml name: match
     * 满足flag条件才执行本步骤
     */
    private java.util.function.Predicate<java.util.Set<java.lang.String>> _match ;
    
    /**
     *  
     * xml name: rename
     * 重命名flag。在子步骤范围内，flag被重命名为指定名称
     */
    private java.util.Map<java.lang.String,java.lang.String> _rename ;
    
    /**
     * 
     * xml name: disable
     *  删除flag, 在子步骤范围内，这些flag无效
     */
    
    public java.util.Set<java.lang.String> getDisable(){
      return _disable;
    }

    
    public void setDisable(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._disable = value;
           
    }

    
    /**
     * 
     * xml name: enable
     *  设置flag，在子步骤范围内，这些flag有效
     */
    
    public java.util.Set<java.lang.String> getEnable(){
      return _enable;
    }

    
    public void setEnable(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._enable = value;
           
    }

    
    /**
     * 
     * xml name: match
     *  满足flag条件才执行本步骤
     */
    
    public java.util.function.Predicate<java.util.Set<java.lang.String>> getMatch(){
      return _match;
    }

    
    public void setMatch(java.util.function.Predicate<java.util.Set<java.lang.String>> value){
        checkAllowChange();
        
        this._match = value;
           
    }

    
    /**
     * 
     * xml name: rename
     *  重命名flag。在子步骤范围内，flag被重命名为指定名称
     */
    
    public java.util.Map<java.lang.String,java.lang.String> getRename(){
      return _rename;
    }

    
    public void setRename(java.util.Map<java.lang.String,java.lang.String> value){
        checkAllowChange();
        
        this._rename = value;
           
    }

    
    public boolean hasRename(){
        return this._rename != null && !this._rename.isEmpty();
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
        
        out.putNotNull("disable",this.getDisable());
        out.putNotNull("enable",this.getEnable());
        out.putNotNull("match",this.getMatch());
        out.putNotNull("rename",this.getRename());
    }

    public TaskFlagsModel cloneInstance(){
        TaskFlagsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskFlagsModel instance){
        super.copyTo(instance);
        
        instance.setDisable(this.getDisable());
        instance.setEnable(this.getEnable());
        instance.setMatch(this.getMatch());
        instance.setRename(this.getRename());
    }

    protected TaskFlagsModel newInstance(){
        return (TaskFlagsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
