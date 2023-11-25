package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [7:2:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actions
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfActionModel> _actions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: allowStepLoop
     * 是否允许步骤之间构成循环（除了backLink连接之外）。为了支持回退操作，需要所有步骤节点构成有向无环图。
     * 在回退连接上标记backLink=true可以忽略该循环依赖
     */
    private boolean _allowStepLoop  = false;
    
    /**
     *  
     * xml name: bizEntityStateProp
     * 
     */
    private java.lang.String _bizEntityStateProp ;
    
    /**
     *  
     * xml name: check-action-auth
     * 触发每一个action的时候所执行的权限验证逻辑
     */
    private io.nop.core.lang.eval.IEvalAction _checkActionAuth ;
    
    /**
     *  
     * xml name: check-manage-auth
     * 对工作流执行管理操作的时候所执行的权限验证逻辑
     */
    private io.nop.core.lang.eval.IEvalAction _checkManageAuth ;
    
    /**
     *  
     * xml name: check-start-auth
     * 启动工作流的时候执行的权限验证逻辑
     */
    private io.nop.core.lang.eval.IEvalAction _checkStartAuth ;
    
    /**
     *  
     * xml name: deploy
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _deploy ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: diagram
     * 前端设计器所设计的可视化流程图
     */
    private java.lang.String _diagram ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: end
     * 
     */
    private io.nop.wf.core.model.WfEndModel _end ;
    
    /**
     *  
     * xml name: listeners
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfListenerModel> _listeners = KeyedList.emptyList();
    
    /**
     *  
     * xml name: manager-assignment
     * 
     */
    private io.nop.wf.core.model.WfAssignmentModel _managerAssignment ;
    
    /**
     *  
     * xml name: on-error
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _onError ;
    
    /**
     *  
     * xml name: on-signal
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _onSignal ;
    
    /**
     *  
     * xml name: priority
     * 
     */
    private int _priority  = 100;
    
    /**
     *  
     * xml name: start
     * start只对应唯一启动步骤， 避免多个地方都写判断。可以很方便的实现回退到初始节点
     */
    private io.nop.wf.core.model.WfStartModel _start ;
    
    /**
     *  
     * xml name: steps
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfStepModel> _steps = KeyedList.emptyList();
    
    /**
     *  
     * xml name: subscribes
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfSubscribeModel> _subscribes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: undeploy
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _undeploy ;
    
    /**
     *  
     * xml name: wfName
     * 
     */
    private java.lang.String _wfName ;
    
    /**
     *  
     * xml name: wfVersion
     * 
     */
    private long _wfVersion  = 0L;
    
    /**
     * 
     * xml name: actions
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfActionModel> getActions(){
      return _actions;
    }

    
    public void setActions(java.util.List<io.nop.wf.core.model.WfActionModel> value){
        checkAllowChange();
        
        this._actions = KeyedList.fromList(value, io.nop.wf.core.model.WfActionModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfActionModel getAction(String name){
        return this._actions.getByKey(name);
    }

    public boolean hasAction(String name){
        return this._actions.containsKey(name);
    }

    public void addAction(io.nop.wf.core.model.WfActionModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfActionModel> list = this.getActions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfActionModel::getName);
            setActions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_actions(){
        return this._actions.keySet();
    }

    public boolean hasActions(){
        return !this._actions.isEmpty();
    }
    
    /**
     * 
     * xml name: allowStepLoop
     *  是否允许步骤之间构成循环（除了backLink连接之外）。为了支持回退操作，需要所有步骤节点构成有向无环图。
     * 在回退连接上标记backLink=true可以忽略该循环依赖
     */
    
    public boolean isAllowStepLoop(){
      return _allowStepLoop;
    }

    
    public void setAllowStepLoop(boolean value){
        checkAllowChange();
        
        this._allowStepLoop = value;
           
    }

    
    /**
     * 
     * xml name: bizEntityStateProp
     *  
     */
    
    public java.lang.String getBizEntityStateProp(){
      return _bizEntityStateProp;
    }

    
    public void setBizEntityStateProp(java.lang.String value){
        checkAllowChange();
        
        this._bizEntityStateProp = value;
           
    }

    
    /**
     * 
     * xml name: check-action-auth
     *  触发每一个action的时候所执行的权限验证逻辑
     */
    
    public io.nop.core.lang.eval.IEvalAction getCheckActionAuth(){
      return _checkActionAuth;
    }

    
    public void setCheckActionAuth(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._checkActionAuth = value;
           
    }

    
    /**
     * 
     * xml name: check-manage-auth
     *  对工作流执行管理操作的时候所执行的权限验证逻辑
     */
    
    public io.nop.core.lang.eval.IEvalAction getCheckManageAuth(){
      return _checkManageAuth;
    }

    
    public void setCheckManageAuth(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._checkManageAuth = value;
           
    }

    
    /**
     * 
     * xml name: check-start-auth
     *  启动工作流的时候执行的权限验证逻辑
     */
    
    public io.nop.core.lang.eval.IEvalAction getCheckStartAuth(){
      return _checkStartAuth;
    }

    
    public void setCheckStartAuth(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._checkStartAuth = value;
           
    }

    
    /**
     * 
     * xml name: deploy
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getDeploy(){
      return _deploy;
    }

    
    public void setDeploy(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._deploy = value;
           
    }

    
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
     * xml name: diagram
     *  前端设计器所设计的可视化流程图
     */
    
    public java.lang.String getDiagram(){
      return _diagram;
    }

    
    public void setDiagram(java.lang.String value){
        checkAllowChange();
        
        this._diagram = value;
           
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
     * xml name: end
     *  
     */
    
    public io.nop.wf.core.model.WfEndModel getEnd(){
      return _end;
    }

    
    public void setEnd(io.nop.wf.core.model.WfEndModel value){
        checkAllowChange();
        
        this._end = value;
           
    }

    
    /**
     * 
     * xml name: listeners
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfListenerModel> getListeners(){
      return _listeners;
    }

    
    public void setListeners(java.util.List<io.nop.wf.core.model.WfListenerModel> value){
        checkAllowChange();
        
        this._listeners = KeyedList.fromList(value, io.nop.wf.core.model.WfListenerModel::getId);
           
    }

    
    public io.nop.wf.core.model.WfListenerModel getListener(String name){
        return this._listeners.getByKey(name);
    }

    public boolean hasListener(String name){
        return this._listeners.containsKey(name);
    }

    public void addListener(io.nop.wf.core.model.WfListenerModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfListenerModel> list = this.getListeners();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfListenerModel::getId);
            setListeners(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_listeners(){
        return this._listeners.keySet();
    }

    public boolean hasListeners(){
        return !this._listeners.isEmpty();
    }
    
    /**
     * 
     * xml name: manager-assignment
     *  
     */
    
    public io.nop.wf.core.model.WfAssignmentModel getManagerAssignment(){
      return _managerAssignment;
    }

    
    public void setManagerAssignment(io.nop.wf.core.model.WfAssignmentModel value){
        checkAllowChange();
        
        this._managerAssignment = value;
           
    }

    
    /**
     * 
     * xml name: on-error
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnError(){
      return _onError;
    }

    
    public void setOnError(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onError = value;
           
    }

    
    /**
     * 
     * xml name: on-signal
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnSignal(){
      return _onSignal;
    }

    
    public void setOnSignal(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onSignal = value;
           
    }

    
    /**
     * 
     * xml name: priority
     *  
     */
    
    public int getPriority(){
      return _priority;
    }

    
    public void setPriority(int value){
        checkAllowChange();
        
        this._priority = value;
           
    }

    
    /**
     * 
     * xml name: start
     *  start只对应唯一启动步骤， 避免多个地方都写判断。可以很方便的实现回退到初始节点
     */
    
    public io.nop.wf.core.model.WfStartModel getStart(){
      return _start;
    }

    
    public void setStart(io.nop.wf.core.model.WfStartModel value){
        checkAllowChange();
        
        this._start = value;
           
    }

    
    /**
     * 
     * xml name: steps
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfStepModel> getSteps(){
      return _steps;
    }

    
    public void setSteps(java.util.List<io.nop.wf.core.model.WfStepModel> value){
        checkAllowChange();
        
        this._steps = KeyedList.fromList(value, io.nop.wf.core.model.WfStepModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfStepModel getStep(String name){
        return this._steps.getByKey(name);
    }

    public boolean hasStep(String name){
        return this._steps.containsKey(name);
    }

    public void addStep(io.nop.wf.core.model.WfStepModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfStepModel> list = this.getSteps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfStepModel::getName);
            setSteps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_steps(){
        return this._steps.keySet();
    }

    public boolean hasSteps(){
        return !this._steps.isEmpty();
    }
    
    /**
     * 
     * xml name: subscribes
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfSubscribeModel> getSubscribes(){
      return _subscribes;
    }

    
    public void setSubscribes(java.util.List<io.nop.wf.core.model.WfSubscribeModel> value){
        checkAllowChange();
        
        this._subscribes = KeyedList.fromList(value, io.nop.wf.core.model.WfSubscribeModel::getId);
           
    }

    
    public io.nop.wf.core.model.WfSubscribeModel getSubscribe(String name){
        return this._subscribes.getByKey(name);
    }

    public boolean hasSubscribe(String name){
        return this._subscribes.containsKey(name);
    }

    public void addSubscribe(io.nop.wf.core.model.WfSubscribeModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfSubscribeModel> list = this.getSubscribes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfSubscribeModel::getId);
            setSubscribes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_subscribes(){
        return this._subscribes.keySet();
    }

    public boolean hasSubscribes(){
        return !this._subscribes.isEmpty();
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
     * xml name: undeploy
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getUndeploy(){
      return _undeploy;
    }

    
    public void setUndeploy(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._undeploy = value;
           
    }

    
    /**
     * 
     * xml name: wfName
     *  
     */
    
    public java.lang.String getWfName(){
      return _wfName;
    }

    
    public void setWfName(java.lang.String value){
        checkAllowChange();
        
        this._wfName = value;
           
    }

    
    /**
     * 
     * xml name: wfVersion
     *  
     */
    
    public long getWfVersion(){
      return _wfVersion;
    }

    
    public void setWfVersion(long value){
        checkAllowChange();
        
        this._wfVersion = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actions);
            
           this._end = io.nop.api.core.util.FreezeHelper.deepFreeze(this._end);
            
           this._listeners = io.nop.api.core.util.FreezeHelper.deepFreeze(this._listeners);
            
           this._managerAssignment = io.nop.api.core.util.FreezeHelper.deepFreeze(this._managerAssignment);
            
           this._start = io.nop.api.core.util.FreezeHelper.deepFreeze(this._start);
            
           this._steps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._steps);
            
           this._subscribes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._subscribes);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("actions",this.getActions());
        out.put("allowStepLoop",this.isAllowStepLoop());
        out.put("bizEntityStateProp",this.getBizEntityStateProp());
        out.put("checkActionAuth",this.getCheckActionAuth());
        out.put("checkManageAuth",this.getCheckManageAuth());
        out.put("checkStartAuth",this.getCheckStartAuth());
        out.put("deploy",this.getDeploy());
        out.put("description",this.getDescription());
        out.put("diagram",this.getDiagram());
        out.put("displayName",this.getDisplayName());
        out.put("end",this.getEnd());
        out.put("listeners",this.getListeners());
        out.put("managerAssignment",this.getManagerAssignment());
        out.put("onError",this.getOnError());
        out.put("onSignal",this.getOnSignal());
        out.put("priority",this.getPriority());
        out.put("start",this.getStart());
        out.put("steps",this.getSteps());
        out.put("subscribes",this.getSubscribes());
        out.put("tagSet",this.getTagSet());
        out.put("undeploy",this.getUndeploy());
        out.put("wfName",this.getWfName());
        out.put("wfVersion",this.getWfVersion());
    }
}
 // resume CPD analysis - CPD-ON
