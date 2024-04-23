package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [116:6:0:0]/nop/schema/task/task.xdef <p>
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
     * xml name: concurrent
     * 指定本步骤的scope是否需要支持并发访问，如果是，则scope会使用ConcurrentHashMap，否则就使用普通的HashMap
     */
    private boolean _concurrent  = false;
    
    /**
     *  
     * xml name: errorName
     * 
     */
    private java.lang.String _errorName ;
    
    /**
     *  
     * xml name: extType
     * 用于指定步骤的扩展类型
     */
    private java.lang.String _extType ;
    
    /**
     *  
     * xml name: ignoreResult
     * 
     */
    private boolean _ignoreResult  = false;
    
    /**
     *  
     * xml name: internal
     * 
     */
    private boolean _internal  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: next
     * 本步骤执行完毕后缺省跳转到的步骤。如果没有指定，则缺省步骤为下一个兄弟节点
     */
    private java.lang.String _next ;
    
    /**
     *  
     * xml name: nextOnError
     * 如果配置了nextOnError，则当内部抛出异常的时候会被自动包装为ErrorBean对象，并跳转到指定步骤。
     */
    private java.lang.String _nextOnError ;
    
    /**
     *  
     * xml name: recordMetrics
     * 
     */
    private boolean _recordMetrics  = false;
    
    /**
     *  
     * xml name: runOnContext
     * 指定本步骤中的操作都使用IContext.runOnContext来执行
     */
    private boolean _runOnContext  = false;
    
    /**
     *  
     * xml name: saveState
     * 是否需要持久化状态用于失败后重新执行本步骤时的状态恢复
     */
    private java.lang.Boolean _saveState ;
    
    /**
     *  
     * xml name: sync
     * 
     */
    private boolean _sync  = false;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: useParentScope
     * 
     */
    private boolean _useParentScope  = false;
    
    /**
     *  
     * xml name: waitErrorSteps
     * 
     */
    private java.util.Set<java.lang.String> _waitErrorSteps ;
    
    /**
     *  
     * xml name: waitSteps
     * 图模式执行时，需要等待前置步骤执行完毕才能执行本步骤
     */
    private java.util.Set<java.lang.String> _waitSteps ;
    
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
     * xml name: concurrent
     *  指定本步骤的scope是否需要支持并发访问，如果是，则scope会使用ConcurrentHashMap，否则就使用普通的HashMap
     */
    
    public boolean isConcurrent(){
      return _concurrent;
    }

    
    public void setConcurrent(boolean value){
        checkAllowChange();
        
        this._concurrent = value;
           
    }

    
    /**
     * 
     * xml name: errorName
     *  
     */
    
    public java.lang.String getErrorName(){
      return _errorName;
    }

    
    public void setErrorName(java.lang.String value){
        checkAllowChange();
        
        this._errorName = value;
           
    }

    
    /**
     * 
     * xml name: extType
     *  用于指定步骤的扩展类型
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
     * xml name: ignoreResult
     *  
     */
    
    public boolean isIgnoreResult(){
      return _ignoreResult;
    }

    
    public void setIgnoreResult(boolean value){
        checkAllowChange();
        
        this._ignoreResult = value;
           
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
     * xml name: nextOnError
     *  如果配置了nextOnError，则当内部抛出异常的时候会被自动包装为ErrorBean对象，并跳转到指定步骤。
     */
    
    public java.lang.String getNextOnError(){
      return _nextOnError;
    }

    
    public void setNextOnError(java.lang.String value){
        checkAllowChange();
        
        this._nextOnError = value;
           
    }

    
    /**
     * 
     * xml name: recordMetrics
     *  
     */
    
    public boolean isRecordMetrics(){
      return _recordMetrics;
    }

    
    public void setRecordMetrics(boolean value){
        checkAllowChange();
        
        this._recordMetrics = value;
           
    }

    
    /**
     * 
     * xml name: runOnContext
     *  指定本步骤中的操作都使用IContext.runOnContext来执行
     */
    
    public boolean isRunOnContext(){
      return _runOnContext;
    }

    
    public void setRunOnContext(boolean value){
        checkAllowChange();
        
        this._runOnContext = value;
           
    }

    
    /**
     * 
     * xml name: saveState
     *  是否需要持久化状态用于失败后重新执行本步骤时的状态恢复
     */
    
    public java.lang.Boolean getSaveState(){
      return _saveState;
    }

    
    public void setSaveState(java.lang.Boolean value){
        checkAllowChange();
        
        this._saveState = value;
           
    }

    
    /**
     * 
     * xml name: sync
     *  
     */
    
    public boolean isSync(){
      return _sync;
    }

    
    public void setSync(boolean value){
        checkAllowChange();
        
        this._sync = value;
           
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

    
    /**
     * 
     * xml name: useParentScope
     *  
     */
    
    public boolean isUseParentScope(){
      return _useParentScope;
    }

    
    public void setUseParentScope(boolean value){
        checkAllowChange();
        
        this._useParentScope = value;
           
    }

    
    /**
     * 
     * xml name: waitErrorSteps
     *  
     */
    
    public java.util.Set<java.lang.String> getWaitErrorSteps(){
      return _waitErrorSteps;
    }

    
    public void setWaitErrorSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._waitErrorSteps = value;
           
    }

    
    /**
     * 
     * xml name: waitSteps
     *  图模式执行时，需要等待前置步骤执行完毕才能执行本步骤
     */
    
    public java.util.Set<java.lang.String> getWaitSteps(){
      return _waitSteps;
    }

    
    public void setWaitSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._waitSteps = value;
           
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
        
        out.putNotNull("allowStartIfComplete",this.isAllowStartIfComplete());
        out.putNotNull("concurrent",this.isConcurrent());
        out.putNotNull("errorName",this.getErrorName());
        out.putNotNull("extType",this.getExtType());
        out.putNotNull("ignoreResult",this.isIgnoreResult());
        out.putNotNull("internal",this.isInternal());
        out.putNotNull("name",this.getName());
        out.putNotNull("next",this.getNext());
        out.putNotNull("nextOnError",this.getNextOnError());
        out.putNotNull("recordMetrics",this.isRecordMetrics());
        out.putNotNull("runOnContext",this.isRunOnContext());
        out.putNotNull("saveState",this.getSaveState());
        out.putNotNull("sync",this.isSync());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("useParentScope",this.isUseParentScope());
        out.putNotNull("waitErrorSteps",this.getWaitErrorSteps());
        out.putNotNull("waitSteps",this.getWaitSteps());
    }

    public TaskStepModel cloneInstance(){
        TaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskStepModel instance){
        super.copyTo(instance);
        
        instance.setAllowStartIfComplete(this.isAllowStartIfComplete());
        instance.setConcurrent(this.isConcurrent());
        instance.setErrorName(this.getErrorName());
        instance.setExtType(this.getExtType());
        instance.setIgnoreResult(this.isIgnoreResult());
        instance.setInternal(this.isInternal());
        instance.setName(this.getName());
        instance.setNext(this.getNext());
        instance.setNextOnError(this.getNextOnError());
        instance.setRecordMetrics(this.isRecordMetrics());
        instance.setRunOnContext(this.isRunOnContext());
        instance.setSaveState(this.getSaveState());
        instance.setSync(this.isSync());
        instance.setTagSet(this.getTagSet());
        instance.setUseParentScope(this.isUseParentScope());
        instance.setWaitErrorSteps(this.getWaitErrorSteps());
        instance.setWaitSteps(this.getWaitSteps());
    }

    protected TaskStepModel newInstance(){
        return (TaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
