package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [111:10:0:0]/nop/schema/wf/wf.xdef <p>
 * suspended状态下所有action都不可用
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _WfActionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: after-transition
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterTransition ;
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfArgVarModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: common
     * 是否每个步骤都具有的公共操作
     */
    private boolean _common  = false;
    
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
     * xml name: forActivated
     * 是否在步骤处于活动状态时可调用
     */
    private boolean _forActivated  = true;
    
    /**
     *  
     * xml name: forFlowEnded
     * 是否在工作流结束之后可调用
     */
    private boolean _forFlowEnded  = false;
    
    /**
     *  
     * xml name: forHistory
     * 是否在步骤处于历史状态时可调用
     */
    private boolean _forHistory  = false;
    
    /**
     *  
     * xml name: forReject
     * 是否退回操作，退回操作可以没有配置步骤迁移
     */
    private boolean _forReject  = false;
    
    /**
     *  
     * xml name: forWaiting
     * 是否在工作流步骤处于等待状态时可调用
     */
    private boolean _forWaiting  = false;
    
    /**
     *  
     * xml name: forWithdraw
     * 是否是撤回操作, 撤回操作可以没有配置步骤迁移
     */
    private boolean _forWithdraw  = false;
    
    /**
     *  
     * xml name: group
     * 
     */
    private java.lang.String _group ;
    
    /**
     *  
     * xml name: internal
     * 
     */
    private boolean _internal  = false;
    
    /**
     *  
     * xml name: local
     * 是否局部操作，不导致本步骤结束
     */
    private boolean _local  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: persist
     * 是否将action执行记录保存到数据库中
     */
    private boolean _persist  = true;
    
    /**
     *  
     * xml name: saveActionRecord
     * 
     */
    private boolean _saveActionRecord  = true;
    
    /**
     *  
     * xml name: sortOrder
     * 
     */
    private int _sortOrder  = 0;
    
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
     * xml name: transition
     * 
     */
    private io.nop.wf.core.model.WfTransitionModel _transition ;
    
    /**
     *  
     * xml name: waitSignals
     * 对应一组globalVars中必须存在的变量名，只有这些变量为truthy, action才允许被触发
     */
    private java.util.Set<java.lang.String> _waitSignals ;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     *  
     * xml name: when-steps
     * 仅当common=true的时候使用，用于限制仅应用某些步骤
     */
    private java.util.Set<java.lang.String> _whenSteps ;
    
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
     * xml name: arg
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfArgVarModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.wf.core.model.WfArgVarModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.wf.core.model.WfArgVarModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfArgVarModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.wf.core.model.WfArgVarModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfArgVarModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfArgVarModel::getName);
            setArgs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_args(){
        return this._args.keySet();
    }

    public boolean hasArgs(){
        return !this._args.isEmpty();
    }
    
    /**
     * 
     * xml name: common
     *  是否每个步骤都具有的公共操作
     */
    
    public boolean isCommon(){
      return _common;
    }

    
    public void setCommon(boolean value){
        checkAllowChange();
        
        this._common = value;
           
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
     * xml name: forActivated
     *  是否在步骤处于活动状态时可调用
     */
    
    public boolean isForActivated(){
      return _forActivated;
    }

    
    public void setForActivated(boolean value){
        checkAllowChange();
        
        this._forActivated = value;
           
    }

    
    /**
     * 
     * xml name: forFlowEnded
     *  是否在工作流结束之后可调用
     */
    
    public boolean isForFlowEnded(){
      return _forFlowEnded;
    }

    
    public void setForFlowEnded(boolean value){
        checkAllowChange();
        
        this._forFlowEnded = value;
           
    }

    
    /**
     * 
     * xml name: forHistory
     *  是否在步骤处于历史状态时可调用
     */
    
    public boolean isForHistory(){
      return _forHistory;
    }

    
    public void setForHistory(boolean value){
        checkAllowChange();
        
        this._forHistory = value;
           
    }

    
    /**
     * 
     * xml name: forReject
     *  是否退回操作，退回操作可以没有配置步骤迁移
     */
    
    public boolean isForReject(){
      return _forReject;
    }

    
    public void setForReject(boolean value){
        checkAllowChange();
        
        this._forReject = value;
           
    }

    
    /**
     * 
     * xml name: forWaiting
     *  是否在工作流步骤处于等待状态时可调用
     */
    
    public boolean isForWaiting(){
      return _forWaiting;
    }

    
    public void setForWaiting(boolean value){
        checkAllowChange();
        
        this._forWaiting = value;
           
    }

    
    /**
     * 
     * xml name: forWithdraw
     *  是否是撤回操作, 撤回操作可以没有配置步骤迁移
     */
    
    public boolean isForWithdraw(){
      return _forWithdraw;
    }

    
    public void setForWithdraw(boolean value){
        checkAllowChange();
        
        this._forWithdraw = value;
           
    }

    
    /**
     * 
     * xml name: group
     *  
     */
    
    public java.lang.String getGroup(){
      return _group;
    }

    
    public void setGroup(java.lang.String value){
        checkAllowChange();
        
        this._group = value;
           
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
     * xml name: local
     *  是否局部操作，不导致本步骤结束
     */
    
    public boolean isLocal(){
      return _local;
    }

    
    public void setLocal(boolean value){
        checkAllowChange();
        
        this._local = value;
           
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
     *  是否将action执行记录保存到数据库中
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
     * xml name: saveActionRecord
     *  
     */
    
    public boolean isSaveActionRecord(){
      return _saveActionRecord;
    }

    
    public void setSaveActionRecord(boolean value){
        checkAllowChange();
        
        this._saveActionRecord = value;
           
    }

    
    /**
     * 
     * xml name: sortOrder
     *  
     */
    
    public int getSortOrder(){
      return _sortOrder;
    }

    
    public void setSortOrder(int value){
        checkAllowChange();
        
        this._sortOrder = value;
           
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
     * xml name: waitSignals
     *  对应一组globalVars中必须存在的变量名，只有这些变量为truthy, action才允许被触发
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
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._when = value;
           
    }

    
    /**
     * 
     * xml name: when-steps
     *  仅当common=true的时候使用，用于限制仅应用某些步骤
     */
    
    public java.util.Set<java.lang.String> getWhenSteps(){
      return _whenSteps;
    }

    
    public void setWhenSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._whenSteps = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
           this._transition = io.nop.api.core.util.FreezeHelper.deepFreeze(this._transition);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("afterTransition",this.getAfterTransition());
        out.put("args",this.getArgs());
        out.put("common",this.isCommon());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("forActivated",this.isForActivated());
        out.put("forFlowEnded",this.isForFlowEnded());
        out.put("forHistory",this.isForHistory());
        out.put("forReject",this.isForReject());
        out.put("forWaiting",this.isForWaiting());
        out.put("forWithdraw",this.isForWithdraw());
        out.put("group",this.getGroup());
        out.put("internal",this.isInternal());
        out.put("local",this.isLocal());
        out.put("name",this.getName());
        out.put("persist",this.isPersist());
        out.put("saveActionRecord",this.isSaveActionRecord());
        out.put("sortOrder",this.getSortOrder());
        out.put("source",this.getSource());
        out.put("specialType",this.getSpecialType());
        out.put("transition",this.getTransition());
        out.put("waitSignals",this.getWaitSignals());
        out.put("when",this.getWhen());
        out.put("whenSteps",this.getWhenSteps());
    }
}
 // resume CPD analysis - CPD-ON
