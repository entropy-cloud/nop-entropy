package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfStepModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: after-transition
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterTransition ;
    
    /**
     *  
     * xml name: allowReject
     * 
     */
    private boolean _allowReject  = false;
    
    /**
     *  
     * xml name: allowWithdraw
     * 
     */
    private boolean _allowWithdraw  = false;
    
    /**
     *  
     * xml name: appState
     * 进入步骤时将record的appState设置为特定值
     */
    private java.lang.String _appState ;
    
    /**
     *  
     * xml name: assignment
     * 如果不自动迁移，则必须有assignment
     */
    private io.nop.wf.core.model.WfAssignmentModel _assignment ;
    
    /**
     *  
     * xml name: before-transition
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeTransition ;
    
    /**
     *  
     * xml name: bizEntityState
     * 
     */
    private java.lang.String _bizEntityState ;
    
    /**
     *  
     * xml name: check-complete
     * 如果check-complete不为空，则为异步任务，需要等待任务结束才能迁移到下一步骤
     */
    private io.nop.core.lang.eval.IEvalAction _checkComplete ;
    
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
     * xml name: dueAction
     * 
     */
    private java.lang.String _dueAction ;
    
    /**
     *  
     * xml name: dueTimeExpr
     * 如果配置了超时表达式，则当超时时刻到达时会自动触发dueAction
     */
    private io.nop.core.lang.eval.IEvalAction _dueTimeExpr ;
    
    /**
     *  
     * xml name: independent
     * 正常情况下整个流程结束时所有正在运行的步骤都会被强制kill，而如果标记为independent，则可以继续保持运行状态。
     */
    private boolean _independent  = false;
    
    /**
     *  初始化为等待状态
     * xml name: initAsWaiting
     * 新建步骤时是否自动初始化为等待状态
     */
    private boolean _initAsWaiting  = false;
    
    /**
     *  
     * xml name: internal
     * 标记为internal的步骤不会在界面中显示
     */
    private boolean _internal  = false;
    
    /**
     *  
     * xml name: may-activated
     * 是否当前可以激活
     */
    private io.nop.core.lang.eval.IEvalPredicate _mayActivated ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: on-enter
     * 进入步骤实例时执行。on-enter失败时由父步骤的on-error捕获
     */
    private io.nop.core.lang.eval.IEvalAction _onEnter ;
    
    /**
     *  
     * xml name: on-error
     * 在本步骤执行action/source/transition失败时执行
     */
    private io.nop.core.lang.eval.IEvalAction _onError ;
    
    /**
     *  
     * xml name: on-exit
     * 步骤实例转换为历史状态后执行。在其中可以通过status判断步骤是否是因为被kill而结束
     */
    private io.nop.core.lang.eval.IEvalAction _onExit ;
    
    /**
     *  
     * xml name: optional
     * 本步骤是否可选步骤，如果不是，则步骤出现异常时将导致异常向父节点传播，最终可能导致整个流程终止
     */
    private boolean _optional  = false;
    
    /**
     *  
     * xml name: priority
     * 优先级
     */
    private int _priority  = 100;
    
    /**
     *  
     * xml name: ref-actions
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfRefActionModel> _refActions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: retry
     * 执行source的时候如果发生异常，则可以重试
     */
    private io.nop.wf.core.model.WfRetryModel _retry ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     *  
     * xml name: specialType
     * 可视化设计器识别的分类标记。每一种specialType对应设计器中的一种图标。
     */
    private java.lang.String _specialType ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: transition
     * 
     */
    private io.nop.wf.core.model.WfTransitionModel _transition ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: waitSignals
     * 对应一组globalVars中必须存在的变量名，只有这些变量不为null, 才activated
     */
    private java.util.Set<java.lang.String> _waitSignals ;
    
    /**
     *  
     * xml name: wfAppState
     * 
     */
    private java.lang.String _wfAppState ;
    
    /**
     * 
     * xml name: after-transition
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterTransition(){
      return _afterTransition;
    }

    
    public void setAfterTransition(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterTransition = value;
           
    }

    
    /**
     * 
     * xml name: allowReject
     *  
     */
    
    public boolean isAllowReject(){
      return _allowReject;
    }

    
    public void setAllowReject(boolean value){
        checkAllowChange();
        
        this._allowReject = value;
           
    }

    
    /**
     * 
     * xml name: allowWithdraw
     *  
     */
    
    public boolean isAllowWithdraw(){
      return _allowWithdraw;
    }

    
    public void setAllowWithdraw(boolean value){
        checkAllowChange();
        
        this._allowWithdraw = value;
           
    }

    
    /**
     * 
     * xml name: appState
     *  进入步骤时将record的appState设置为特定值
     */
    
    public java.lang.String getAppState(){
      return _appState;
    }

    
    public void setAppState(java.lang.String value){
        checkAllowChange();
        
        this._appState = value;
           
    }

    
    /**
     * 
     * xml name: assignment
     *  如果不自动迁移，则必须有assignment
     */
    
    public io.nop.wf.core.model.WfAssignmentModel getAssignment(){
      return _assignment;
    }

    
    public void setAssignment(io.nop.wf.core.model.WfAssignmentModel value){
        checkAllowChange();
        
        this._assignment = value;
           
    }

    
    /**
     * 
     * xml name: before-transition
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeTransition(){
      return _beforeTransition;
    }

    
    public void setBeforeTransition(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeTransition = value;
           
    }

    
    /**
     * 
     * xml name: bizEntityState
     *  
     */
    
    public java.lang.String getBizEntityState(){
      return _bizEntityState;
    }

    
    public void setBizEntityState(java.lang.String value){
        checkAllowChange();
        
        this._bizEntityState = value;
           
    }

    
    /**
     * 
     * xml name: check-complete
     *  如果check-complete不为空，则为异步任务，需要等待任务结束才能迁移到下一步骤
     */
    
    public io.nop.core.lang.eval.IEvalAction getCheckComplete(){
      return _checkComplete;
    }

    
    public void setCheckComplete(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._checkComplete = value;
           
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
     * xml name: dueAction
     *  
     */
    
    public java.lang.String getDueAction(){
      return _dueAction;
    }

    
    public void setDueAction(java.lang.String value){
        checkAllowChange();
        
        this._dueAction = value;
           
    }

    
    /**
     * 
     * xml name: dueTimeExpr
     *  如果配置了超时表达式，则当超时时刻到达时会自动触发dueAction
     */
    
    public io.nop.core.lang.eval.IEvalAction getDueTimeExpr(){
      return _dueTimeExpr;
    }

    
    public void setDueTimeExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._dueTimeExpr = value;
           
    }

    
    /**
     * 
     * xml name: independent
     *  正常情况下整个流程结束时所有正在运行的步骤都会被强制kill，而如果标记为independent，则可以继续保持运行状态。
     */
    
    public boolean isIndependent(){
      return _independent;
    }

    
    public void setIndependent(boolean value){
        checkAllowChange();
        
        this._independent = value;
           
    }

    
    /**
     * 初始化为等待状态
     * xml name: initAsWaiting
     *  新建步骤时是否自动初始化为等待状态
     */
    
    public boolean isInitAsWaiting(){
      return _initAsWaiting;
    }

    
    public void setInitAsWaiting(boolean value){
        checkAllowChange();
        
        this._initAsWaiting = value;
           
    }

    
    /**
     * 
     * xml name: internal
     *  标记为internal的步骤不会在界面中显示
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
     * xml name: may-activated
     *  是否当前可以激活
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getMayActivated(){
      return _mayActivated;
    }

    
    public void setMayActivated(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._mayActivated = value;
           
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
     * xml name: on-enter
     *  进入步骤实例时执行。on-enter失败时由父步骤的on-error捕获
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnEnter(){
      return _onEnter;
    }

    
    public void setOnEnter(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onEnter = value;
           
    }

    
    /**
     * 
     * xml name: on-error
     *  在本步骤执行action/source/transition失败时执行
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
     * xml name: on-exit
     *  步骤实例转换为历史状态后执行。在其中可以通过status判断步骤是否是因为被kill而结束
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
     * xml name: optional
     *  本步骤是否可选步骤，如果不是，则步骤出现异常时将导致异常向父节点传播，最终可能导致整个流程终止
     */
    
    public boolean isOptional(){
      return _optional;
    }

    
    public void setOptional(boolean value){
        checkAllowChange();
        
        this._optional = value;
           
    }

    
    /**
     * 
     * xml name: priority
     *  优先级
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
     * xml name: ref-actions
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfRefActionModel> getRefActions(){
      return _refActions;
    }

    
    public void setRefActions(java.util.List<io.nop.wf.core.model.WfRefActionModel> value){
        checkAllowChange();
        
        this._refActions = KeyedList.fromList(value, io.nop.wf.core.model.WfRefActionModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfRefActionModel getRefAction(String name){
        return this._refActions.getByKey(name);
    }

    public boolean hasRefAction(String name){
        return this._refActions.containsKey(name);
    }

    public void addRefAction(io.nop.wf.core.model.WfRefActionModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfRefActionModel> list = this.getRefActions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfRefActionModel::getName);
            setRefActions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_refActions(){
        return this._refActions.keySet();
    }

    public boolean hasRefActions(){
        return !this._refActions.isEmpty();
    }
    
    /**
     * 
     * xml name: retry
     *  执行source的时候如果发生异常，则可以重试
     */
    
    public io.nop.wf.core.model.WfRetryModel getRetry(){
      return _retry;
    }

    
    public void setRetry(io.nop.wf.core.model.WfRetryModel value){
        checkAllowChange();
        
        this._retry = value;
           
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
     * xml name: specialType
     *  可视化设计器识别的分类标记。每一种specialType对应设计器中的一种图标。
     */
    
    public java.lang.String getSpecialType(){
      return _specialType;
    }

    
    public void setSpecialType(java.lang.String value){
        checkAllowChange();
        
        this._specialType = value;
           
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
     * xml name: transition
     *  
     */
    
    public io.nop.wf.core.model.WfTransitionModel getTransition(){
      return _transition;
    }

    
    public void setTransition(io.nop.wf.core.model.WfTransitionModel value){
        checkAllowChange();
        
        this._transition = value;
           
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

    
    /**
     * 
     * xml name: waitSignals
     *  对应一组globalVars中必须存在的变量名，只有这些变量不为null, 才activated
     */
    
    public java.util.Set<java.lang.String> getWaitSignals(){
      return _waitSignals;
    }

    
    public void setWaitSignals(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._waitSignals = value;
           
    }

    
    /**
     * 
     * xml name: wfAppState
     *  
     */
    
    public java.lang.String getWfAppState(){
      return _wfAppState;
    }

    
    public void setWfAppState(java.lang.String value){
        checkAllowChange();
        
        this._wfAppState = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._assignment = io.nop.api.core.util.FreezeHelper.deepFreeze(this._assignment);
            
           this._refActions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._refActions);
            
           this._retry = io.nop.api.core.util.FreezeHelper.deepFreeze(this._retry);
            
           this._transition = io.nop.api.core.util.FreezeHelper.deepFreeze(this._transition);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterTransition",this.getAfterTransition());
        out.putNotNull("allowReject",this.isAllowReject());
        out.putNotNull("allowWithdraw",this.isAllowWithdraw());
        out.putNotNull("appState",this.getAppState());
        out.putNotNull("assignment",this.getAssignment());
        out.putNotNull("beforeTransition",this.getBeforeTransition());
        out.putNotNull("bizEntityState",this.getBizEntityState());
        out.putNotNull("checkComplete",this.getCheckComplete());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("dueAction",this.getDueAction());
        out.putNotNull("dueTimeExpr",this.getDueTimeExpr());
        out.putNotNull("independent",this.isIndependent());
        out.putNotNull("initAsWaiting",this.isInitAsWaiting());
        out.putNotNull("internal",this.isInternal());
        out.putNotNull("mayActivated",this.getMayActivated());
        out.putNotNull("name",this.getName());
        out.putNotNull("onEnter",this.getOnEnter());
        out.putNotNull("onError",this.getOnError());
        out.putNotNull("onExit",this.getOnExit());
        out.putNotNull("optional",this.isOptional());
        out.putNotNull("priority",this.getPriority());
        out.putNotNull("refActions",this.getRefActions());
        out.putNotNull("retry",this.getRetry());
        out.putNotNull("source",this.getSource());
        out.putNotNull("specialType",this.getSpecialType());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("transition",this.getTransition());
        out.putNotNull("type",this.getType());
        out.putNotNull("waitSignals",this.getWaitSignals());
        out.putNotNull("wfAppState",this.getWfAppState());
    }

    public WfStepModel cloneInstance(){
        WfStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfStepModel instance){
        super.copyTo(instance);
        
        instance.setAfterTransition(this.getAfterTransition());
        instance.setAllowReject(this.isAllowReject());
        instance.setAllowWithdraw(this.isAllowWithdraw());
        instance.setAppState(this.getAppState());
        instance.setAssignment(this.getAssignment());
        instance.setBeforeTransition(this.getBeforeTransition());
        instance.setBizEntityState(this.getBizEntityState());
        instance.setCheckComplete(this.getCheckComplete());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setDueAction(this.getDueAction());
        instance.setDueTimeExpr(this.getDueTimeExpr());
        instance.setIndependent(this.isIndependent());
        instance.setInitAsWaiting(this.isInitAsWaiting());
        instance.setInternal(this.isInternal());
        instance.setMayActivated(this.getMayActivated());
        instance.setName(this.getName());
        instance.setOnEnter(this.getOnEnter());
        instance.setOnError(this.getOnError());
        instance.setOnExit(this.getOnExit());
        instance.setOptional(this.isOptional());
        instance.setPriority(this.getPriority());
        instance.setRefActions(this.getRefActions());
        instance.setRetry(this.getRetry());
        instance.setSource(this.getSource());
        instance.setSpecialType(this.getSpecialType());
        instance.setTagSet(this.getTagSet());
        instance.setTransition(this.getTransition());
        instance.setType(this.getType());
        instance.setWaitSignals(this.getWaitSignals());
        instance.setWfAppState(this.getWfAppState());
    }

    protected WfStepModel newInstance(){
        return (WfStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
