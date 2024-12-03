package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskFlowModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 支持异步执行的轻量化任务引擎。持久化状态为可选特性，如果在步骤上配置了saveState，则可以从任意步骤中断并恢复执行。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskFlowModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: auth
     * 设置task的访问权限
     */
    private io.nop.api.core.auth.ActionAuthMeta _auth ;
    
    /**
     *  
     * xml name: beans
     * 带ioc前缀的属性和子节点是相对于spring配置格式增加的扩展
     */
    private io.nop.ioc.model.BeansModel _beans ;
    
    /**
     *  
     * xml name: defaultSaveState
     * 
     */
    private boolean _defaultSaveState  = false;
    
    /**
     *  
     * xml name: defaultUseParentScope
     * 
     */
    private java.lang.Boolean _defaultUseParentScope ;
    
    /**
     *  
     * xml name: enterSteps
     * 
     */
    private java.util.Set<java.lang.String> _enterSteps ;
    
    /**
     *  
     * xml name: exitSteps
     * 
     */
    private java.util.Set<java.lang.String> _exitSteps ;
    
    /**
     *  
     * xml name: graphMode
     * 
     */
    private boolean _graphMode  = false;
    
    /**
     *  
     * xml name: restartable
     * 
     */
    private boolean _restartable  = true;
    
    /**
     *  
     * xml name: useParentBeanContainer
     * 如果设置为false，则任务只使用自身定义的beans，不使用IServiceContext中的beanContainer
     */
    private boolean _useParentBeanContainer  = false;
    
    /**
     *  
     * xml name: version
     * 
     */
    private long _version  = 0L;
    
    /**
     * 
     * xml name: auth
     *  设置task的访问权限
     */
    
    public io.nop.api.core.auth.ActionAuthMeta getAuth(){
      return _auth;
    }

    
    public void setAuth(io.nop.api.core.auth.ActionAuthMeta value){
        checkAllowChange();
        
        this._auth = value;
           
    }

    
    /**
     * 
     * xml name: beans
     *  带ioc前缀的属性和子节点是相对于spring配置格式增加的扩展
     */
    
    public io.nop.ioc.model.BeansModel getBeans(){
      return _beans;
    }

    
    public void setBeans(io.nop.ioc.model.BeansModel value){
        checkAllowChange();
        
        this._beans = value;
           
    }

    
    /**
     * 
     * xml name: defaultSaveState
     *  
     */
    
    public boolean isDefaultSaveState(){
      return _defaultSaveState;
    }

    
    public void setDefaultSaveState(boolean value){
        checkAllowChange();
        
        this._defaultSaveState = value;
           
    }

    
    /**
     * 
     * xml name: defaultUseParentScope
     *  
     */
    
    public java.lang.Boolean getDefaultUseParentScope(){
      return _defaultUseParentScope;
    }

    
    public void setDefaultUseParentScope(java.lang.Boolean value){
        checkAllowChange();
        
        this._defaultUseParentScope = value;
           
    }

    
    /**
     * 
     * xml name: enterSteps
     *  
     */
    
    public java.util.Set<java.lang.String> getEnterSteps(){
      return _enterSteps;
    }

    
    public void setEnterSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._enterSteps = value;
           
    }

    
    /**
     * 
     * xml name: exitSteps
     *  
     */
    
    public java.util.Set<java.lang.String> getExitSteps(){
      return _exitSteps;
    }

    
    public void setExitSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._exitSteps = value;
           
    }

    
    /**
     * 
     * xml name: graphMode
     *  
     */
    
    public boolean isGraphMode(){
      return _graphMode;
    }

    
    public void setGraphMode(boolean value){
        checkAllowChange();
        
        this._graphMode = value;
           
    }

    
    /**
     * 
     * xml name: restartable
     *  
     */
    
    public boolean isRestartable(){
      return _restartable;
    }

    
    public void setRestartable(boolean value){
        checkAllowChange();
        
        this._restartable = value;
           
    }

    
    /**
     * 
     * xml name: useParentBeanContainer
     *  如果设置为false，则任务只使用自身定义的beans，不使用IServiceContext中的beanContainer
     */
    
    public boolean isUseParentBeanContainer(){
      return _useParentBeanContainer;
    }

    
    public void setUseParentBeanContainer(boolean value){
        checkAllowChange();
        
        this._useParentBeanContainer = value;
           
    }

    
    /**
     * 
     * xml name: version
     *  
     */
    
    public long getVersion(){
      return _version;
    }

    
    public void setVersion(long value){
        checkAllowChange();
        
        this._version = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._auth = io.nop.api.core.util.FreezeHelper.deepFreeze(this._auth);
            
           this._beans = io.nop.api.core.util.FreezeHelper.deepFreeze(this._beans);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("auth",this.getAuth());
        out.putNotNull("beans",this.getBeans());
        out.putNotNull("defaultSaveState",this.isDefaultSaveState());
        out.putNotNull("defaultUseParentScope",this.getDefaultUseParentScope());
        out.putNotNull("enterSteps",this.getEnterSteps());
        out.putNotNull("exitSteps",this.getExitSteps());
        out.putNotNull("graphMode",this.isGraphMode());
        out.putNotNull("restartable",this.isRestartable());
        out.putNotNull("useParentBeanContainer",this.isUseParentBeanContainer());
        out.putNotNull("version",this.getVersion());
    }

    public TaskFlowModel cloneInstance(){
        TaskFlowModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskFlowModel instance){
        super.copyTo(instance);
        
        instance.setAuth(this.getAuth());
        instance.setBeans(this.getBeans());
        instance.setDefaultSaveState(this.isDefaultSaveState());
        instance.setDefaultUseParentScope(this.getDefaultUseParentScope());
        instance.setEnterSteps(this.getEnterSteps());
        instance.setExitSteps(this.getExitSteps());
        instance.setGraphMode(this.isGraphMode());
        instance.setRestartable(this.isRestartable());
        instance.setUseParentBeanContainer(this.isUseParentBeanContainer());
        instance.setVersion(this.getVersion());
    }

    protected TaskFlowModel newInstance(){
        return (TaskFlowModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
