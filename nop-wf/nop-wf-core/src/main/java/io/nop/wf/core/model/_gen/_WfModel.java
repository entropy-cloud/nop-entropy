package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [7:2:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
     * xml name: auths
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfModelAuth> _auths = KeyedList.emptyList();
    
    /**
     *  
     * xml name: bizEntityFlowIdProp
     * 
     */
    private java.lang.String _bizEntityFlowIdProp ;
    
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
     * xml name: check-edit-auth
     * 编辑工作流定义时执行的权限验证逻辑
     */
    private io.nop.core.lang.eval.IEvalAction _checkEditAuth ;
    
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
     * xml name: wfGroup
     * 
     */
    private java.lang.String _wfGroup ;
    
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
     * xml name: auths
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfModelAuth> getAuths(){
      return _auths;
    }

    
    public void setAuths(java.util.List<io.nop.wf.core.model.WfModelAuth> value){
        checkAllowChange();
        
        this._auths = KeyedList.fromList(value, io.nop.wf.core.model.WfModelAuth::getId);
           
    }

    
    public io.nop.wf.core.model.WfModelAuth getAuth(String name){
        return this._auths.getByKey(name);
    }

    public boolean hasAuth(String name){
        return this._auths.containsKey(name);
    }

    public void addAuth(io.nop.wf.core.model.WfModelAuth item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfModelAuth> list = this.getAuths();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfModelAuth::getId);
            setAuths(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_auths(){
        return this._auths.keySet();
    }

    public boolean hasAuths(){
        return !this._auths.isEmpty();
    }
    
    /**
     * 
     * xml name: bizEntityFlowIdProp
     *  
     */
    
    public java.lang.String getBizEntityFlowIdProp(){
      return _bizEntityFlowIdProp;
    }

    
    public void setBizEntityFlowIdProp(java.lang.String value){
        checkAllowChange();
        
        this._bizEntityFlowIdProp = value;
           
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
     * xml name: check-edit-auth
     *  编辑工作流定义时执行的权限验证逻辑
     */
    
    public io.nop.core.lang.eval.IEvalAction getCheckEditAuth(){
      return _checkEditAuth;
    }

    
    public void setCheckEditAuth(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._checkEditAuth = value;
           
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
     * xml name: wfGroup
     *  
     */
    
    public java.lang.String getWfGroup(){
      return _wfGroup;
    }

    
    public void setWfGroup(java.lang.String value){
        checkAllowChange();
        
        this._wfGroup = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actions);
            
           this._auths = io.nop.api.core.util.FreezeHelper.deepFreeze(this._auths);
            
           this._end = io.nop.api.core.util.FreezeHelper.deepFreeze(this._end);
            
           this._listeners = io.nop.api.core.util.FreezeHelper.deepFreeze(this._listeners);
            
           this._start = io.nop.api.core.util.FreezeHelper.deepFreeze(this._start);
            
           this._steps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._steps);
            
           this._subscribes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._subscribes);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("actions",this.getActions());
        out.putNotNull("allowStepLoop",this.isAllowStepLoop());
        out.putNotNull("auths",this.getAuths());
        out.putNotNull("bizEntityFlowIdProp",this.getBizEntityFlowIdProp());
        out.putNotNull("bizEntityStateProp",this.getBizEntityStateProp());
        out.putNotNull("checkActionAuth",this.getCheckActionAuth());
        out.putNotNull("checkEditAuth",this.getCheckEditAuth());
        out.putNotNull("checkManageAuth",this.getCheckManageAuth());
        out.putNotNull("checkStartAuth",this.getCheckStartAuth());
        out.putNotNull("deploy",this.getDeploy());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("diagram",this.getDiagram());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("end",this.getEnd());
        out.putNotNull("listeners",this.getListeners());
        out.putNotNull("onError",this.getOnError());
        out.putNotNull("onSignal",this.getOnSignal());
        out.putNotNull("priority",this.getPriority());
        out.putNotNull("start",this.getStart());
        out.putNotNull("steps",this.getSteps());
        out.putNotNull("subscribes",this.getSubscribes());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("undeploy",this.getUndeploy());
        out.putNotNull("wfGroup",this.getWfGroup());
        out.putNotNull("wfName",this.getWfName());
        out.putNotNull("wfVersion",this.getWfVersion());
    }

    public WfModel cloneInstance(){
        WfModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfModel instance){
        super.copyTo(instance);
        
        instance.setActions(this.getActions());
        instance.setAllowStepLoop(this.isAllowStepLoop());
        instance.setAuths(this.getAuths());
        instance.setBizEntityFlowIdProp(this.getBizEntityFlowIdProp());
        instance.setBizEntityStateProp(this.getBizEntityStateProp());
        instance.setCheckActionAuth(this.getCheckActionAuth());
        instance.setCheckEditAuth(this.getCheckEditAuth());
        instance.setCheckManageAuth(this.getCheckManageAuth());
        instance.setCheckStartAuth(this.getCheckStartAuth());
        instance.setDeploy(this.getDeploy());
        instance.setDescription(this.getDescription());
        instance.setDiagram(this.getDiagram());
        instance.setDisplayName(this.getDisplayName());
        instance.setEnd(this.getEnd());
        instance.setListeners(this.getListeners());
        instance.setOnError(this.getOnError());
        instance.setOnSignal(this.getOnSignal());
        instance.setPriority(this.getPriority());
        instance.setStart(this.getStart());
        instance.setSteps(this.getSteps());
        instance.setSubscribes(this.getSubscribes());
        instance.setTagSet(this.getTagSet());
        instance.setUndeploy(this.getUndeploy());
        instance.setWfGroup(this.getWfGroup());
        instance.setWfName(this.getWfName());
        instance.setWfVersion(this.getWfVersion());
    }

    protected WfModel newInstance(){
        return (WfModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
