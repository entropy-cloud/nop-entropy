package io.nop.fsm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [21:6:0:0]/nop/schema/biz/state-machine.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _StateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: activities
     * 进入state时启动activity，离开state时停止activity
     */
    private java.util.Set<java.lang.String> _activities ;
    
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
     * xml name: entry
     * 进入本状态时执行的action的列表
     */
    private java.util.Set<java.lang.String> _entry ;
    
    /**
     *  
     * xml name: exit
     * 退出本状态时执行的action的列表
     */
    private java.util.Set<java.lang.String> _exit ;
    
    /**
     *  
     * xml name: final
     * 是否为结束状态。如果是，则进入此状态会导致子状态机退出。
     */
    private boolean _final  = false;
    
    /**
     *  
     * xml name: handle-error
     * 状态迁移出现异常时触发的监听函数。如果返回true，则认为异常已经被处理，不对外抛出异常
     */
    private io.nop.core.lang.eval.IEvalAction _handleError ;
    
    /**
     *  
     * xml name: id
     * 为了简化设计，状态的id在整个状态机内部唯一，即子状态的id也不能和父状态id重复。
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: initial
     * 当状态为复合状态，具有嵌套结构时，initial指定初始进入的子状态的id
     */
    private java.lang.String _initial ;
    
    /**
     *  
     * xml name: meta
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _meta ;
    
    /**
     *  
     * xml name: onDone
     * 子状态机成功执行完毕后会触发跳转，这里指定跳转到的目标状态
     */
    private java.lang.String _onDone ;
    
    /**
     *  
     * xml name: on-entry
     * 进入状态时触发的监听函数
     */
    private io.nop.core.lang.eval.IEvalAction _onEntry ;
    
    /**
     *  
     * xml name: onError
     * 子状态执过程中出现异常时，父状态机可以捕获异常，跳转到指定状态
     */
    private java.lang.String _onError ;
    
    /**
     *  
     * xml name: on-exit
     * 离开状态时触发的监听函数
     */
    private io.nop.core.lang.eval.IEvalAction _onExit ;
    
    /**
     *  
     * xml name: stateValue
     * state的id为字符串形式，但是保存到数据库中的实体状态字段可能是整数值，通过stateValue可以指定对应的保存到数据库中的值。
     */
    private java.lang.Object _stateValue ;
    
    /**
     *  
     * xml name: state
     * 子状态
     */
    private KeyedList<io.nop.fsm.model.StateModel> _states = KeyedList.emptyList();
    
    /**
     *  
     * xml name: transition
     * 
     */
    private KeyedList<io.nop.fsm.model.StateTransitionModel> _transitions = KeyedList.emptyList();
    
    /**
     * 
     * xml name: activities
     *  进入state时启动activity，离开state时停止activity
     */
    
    public java.util.Set<java.lang.String> getActivities(){
      return _activities;
    }

    
    public void setActivities(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._activities = value;
           
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
     * xml name: entry
     *  进入本状态时执行的action的列表
     */
    
    public java.util.Set<java.lang.String> getEntry(){
      return _entry;
    }

    
    public void setEntry(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._entry = value;
           
    }

    
    /**
     * 
     * xml name: exit
     *  退出本状态时执行的action的列表
     */
    
    public java.util.Set<java.lang.String> getExit(){
      return _exit;
    }

    
    public void setExit(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._exit = value;
           
    }

    
    /**
     * 
     * xml name: final
     *  是否为结束状态。如果是，则进入此状态会导致子状态机退出。
     */
    
    public boolean isFinal(){
      return _final;
    }

    
    public void setFinal(boolean value){
        checkAllowChange();
        
        this._final = value;
           
    }

    
    /**
     * 
     * xml name: handle-error
     *  状态迁移出现异常时触发的监听函数。如果返回true，则认为异常已经被处理，不对外抛出异常
     */
    
    public io.nop.core.lang.eval.IEvalAction getHandleError(){
      return _handleError;
    }

    
    public void setHandleError(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._handleError = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  为了简化设计，状态的id在整个状态机内部唯一，即子状态的id也不能和父状态id重复。
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: initial
     *  当状态为复合状态，具有嵌套结构时，initial指定初始进入的子状态的id
     */
    
    public java.lang.String getInitial(){
      return _initial;
    }

    
    public void setInitial(java.lang.String value){
        checkAllowChange();
        
        this._initial = value;
           
    }

    
    /**
     * 
     * xml name: meta
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getMeta(){
      return _meta;
    }

    
    public void setMeta(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._meta = value;
           
    }

    
    public boolean hasMeta(){
        return this._meta != null && !this._meta.isEmpty();
    }
    
    /**
     * 
     * xml name: onDone
     *  子状态机成功执行完毕后会触发跳转，这里指定跳转到的目标状态
     */
    
    public java.lang.String getOnDone(){
      return _onDone;
    }

    
    public void setOnDone(java.lang.String value){
        checkAllowChange();
        
        this._onDone = value;
           
    }

    
    /**
     * 
     * xml name: on-entry
     *  进入状态时触发的监听函数
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnEntry(){
      return _onEntry;
    }

    
    public void setOnEntry(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onEntry = value;
           
    }

    
    /**
     * 
     * xml name: onError
     *  子状态执过程中出现异常时，父状态机可以捕获异常，跳转到指定状态
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
     * xml name: on-exit
     *  离开状态时触发的监听函数
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnExit(){
      return _onExit;
    }

    
    public void setOnExit(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onExit = value;
           
    }

    
    /**
     * 
     * xml name: stateValue
     *  state的id为字符串形式，但是保存到数据库中的实体状态字段可能是整数值，通过stateValue可以指定对应的保存到数据库中的值。
     */
    
    public java.lang.Object getStateValue(){
      return _stateValue;
    }

    
    public void setStateValue(java.lang.Object value){
        checkAllowChange();
        
        this._stateValue = value;
           
    }

    
    /**
     * 
     * xml name: state
     *  子状态
     */
    
    public java.util.List<io.nop.fsm.model.StateModel> getStates(){
      return _states;
    }

    
    public void setStates(java.util.List<io.nop.fsm.model.StateModel> value){
        checkAllowChange();
        
        this._states = KeyedList.fromList(value, io.nop.fsm.model.StateModel::getId);
           
    }

    
    public io.nop.fsm.model.StateModel getState(String name){
        return this._states.getByKey(name);
    }

    public boolean hasState(String name){
        return this._states.containsKey(name);
    }

    public void addState(io.nop.fsm.model.StateModel item) {
        checkAllowChange();
        java.util.List<io.nop.fsm.model.StateModel> list = this.getStates();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.fsm.model.StateModel::getId);
            setStates(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_states(){
        return this._states.keySet();
    }

    public boolean hasStates(){
        return !this._states.isEmpty();
    }
    
    /**
     * 
     * xml name: transition
     *  
     */
    
    public java.util.List<io.nop.fsm.model.StateTransitionModel> getTransitions(){
      return _transitions;
    }

    
    public void setTransitions(java.util.List<io.nop.fsm.model.StateTransitionModel> value){
        checkAllowChange();
        
        this._transitions = KeyedList.fromList(value, io.nop.fsm.model.StateTransitionModel::getId);
           
    }

    
    public io.nop.fsm.model.StateTransitionModel getTransition(String name){
        return this._transitions.getByKey(name);
    }

    public boolean hasTransition(String name){
        return this._transitions.containsKey(name);
    }

    public void addTransition(io.nop.fsm.model.StateTransitionModel item) {
        checkAllowChange();
        java.util.List<io.nop.fsm.model.StateTransitionModel> list = this.getTransitions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.fsm.model.StateTransitionModel::getId);
            setTransitions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_transitions(){
        return this._transitions.keySet();
    }

    public boolean hasTransitions(){
        return !this._transitions.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._states = io.nop.api.core.util.FreezeHelper.deepFreeze(this._states);
            
           this._transitions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._transitions);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("activities",this.getActivities());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("entry",this.getEntry());
        out.put("exit",this.getExit());
        out.put("final",this.isFinal());
        out.put("handleError",this.getHandleError());
        out.put("id",this.getId());
        out.put("initial",this.getInitial());
        out.put("meta",this.getMeta());
        out.put("onDone",this.getOnDone());
        out.put("onEntry",this.getOnEntry());
        out.put("onError",this.getOnError());
        out.put("onExit",this.getOnExit());
        out.put("stateValue",this.getStateValue());
        out.put("states",this.getStates());
        out.put("transitions",this.getTransitions());
    }
}
 // resume CPD analysis - CPD-ON
