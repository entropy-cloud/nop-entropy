package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [85:6:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskStepModel extends io.nop.task.model.TaskExecutableModel {
    
    /**
     *  
     * xml name: allowStartIfComplete
     * 如果设置为false, 则重新执行时已经完成的步骤会被跳过
     */
    private boolean _allowStartIfComplete  = false;
    
    /**
     *  
     * xml name: extType
     * 
     */
    private java.lang.String _extType ;
    
    /**
     *  
     * xml name: internal
     * 
     */
    private boolean _internal  = false;
    
    /**
     *  
     * xml name: next
     * 本步骤执行完毕后缺省跳转到的步骤。如果没有指定，则缺省步骤为下一个兄弟节点
     */
    private java.lang.String _next ;
    
    /**
     *  
     * xml name: onError
     * 当内部抛出异常的时候跳转到哪个步骤
     */
    private java.lang.String _onError ;
    
    /**
     *  
     * xml name: returnAs
     * 
     */
    private java.lang.String _returnAs ;
    
    /**
     *  
     * xml name: saveState
     * 是否需要持久化状态用于失败后重新执行本步骤时的状态恢复
     */
    private boolean _saveState  = false;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     * 
     * xml name: allowStartIfComplete
     *  如果设置为false, 则重新执行时已经完成的步骤会被跳过
     */
    
    public boolean isAllowStartIfComplete(){
      return _allowStartIfComplete;
    }

    
    public void setAllowStartIfComplete(boolean value){
        checkAllowChange();
        
        this._allowStartIfComplete = value;
           
    }

    
    /**
     * 
     * xml name: extType
     *  
     */
    
    public java.lang.String getExtType(){
      return _extType;
    }

    
    public void setExtType(java.lang.String value){
        checkAllowChange();
        
        this._extType = value;
           
    }

    
    /**
     * 
     * xml name: internal
     *  
     */
    
    public boolean isInternal(){
      return _internal;
    }

    
    public void setInternal(boolean value){
        checkAllowChange();
        
        this._internal = value;
           
    }

    
    /**
     * 
     * xml name: next
     *  本步骤执行完毕后缺省跳转到的步骤。如果没有指定，则缺省步骤为下一个兄弟节点
     */
    
    public java.lang.String getNext(){
      return _next;
    }

    
    public void setNext(java.lang.String value){
        checkAllowChange();
        
        this._next = value;
           
    }

    
    /**
     * 
     * xml name: onError
     *  当内部抛出异常的时候跳转到哪个步骤
     */
    
    public java.lang.String getOnError(){
      return _onError;
    }

    
    public void setOnError(java.lang.String value){
        checkAllowChange();
        
        this._onError = value;
           
    }

    
    /**
     * 
     * xml name: returnAs
     *  
     */
    
    public java.lang.String getReturnAs(){
      return _returnAs;
    }

    
    public void setReturnAs(java.lang.String value){
        checkAllowChange();
        
        this._returnAs = value;
           
    }

    
    /**
     * 
     * xml name: saveState
     *  是否需要持久化状态用于失败后重新执行本步骤时的状态恢复
     */
    
    public boolean isSaveState(){
      return _saveState;
    }

    
    public void setSaveState(boolean value){
        checkAllowChange();
        
        this._saveState = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
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
        
        out.put("allowStartIfComplete",this.isAllowStartIfComplete());
        out.put("extType",this.getExtType());
        out.put("internal",this.isInternal());
        out.put("next",this.getNext());
        out.put("onError",this.getOnError());
        out.put("returnAs",this.getReturnAs());
        out.put("saveState",this.isSaveState());
        out.put("tagSet",this.getTagSet());
    }

    public TaskStepModel cloneInstance(){
        TaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskStepModel instance){
        super.copyTo(instance);
        
        instance.setAllowStartIfComplete(this.isAllowStartIfComplete());
        instance.setExtType(this.getExtType());
        instance.setInternal(this.isInternal());
        instance.setNext(this.getNext());
        instance.setOnError(this.getOnError());
        instance.setReturnAs(this.getReturnAs());
        instance.setSaveState(this.isSaveState());
        instance.setTagSet(this.getTagSet());
    }

    protected TaskStepModel newInstance(){
        return (TaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
