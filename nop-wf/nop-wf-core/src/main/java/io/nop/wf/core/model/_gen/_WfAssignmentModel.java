package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfAssignmentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [8:2:0:0]/nop/schema/wf/assignment.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfAssignmentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actors
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfAssignmentActorModel> _actors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: defaultActorExpr
     * 当actors集合中实际选择的人员为空时会使用defaultActor
     */
    private io.nop.core.lang.eval.IEvalAction _defaultActorExpr ;
    
    /**
     *  
     * xml name: defaultOwnerExpr
     * 对于每一个actor，从actor对应的user中选择一个作为owner
     */
    private io.nop.core.lang.eval.IEvalAction _defaultOwnerExpr ;
    
    /**
     *  
     * xml name: ignoreNoAssign
     * 是否允许不选择actor。如果不允许，则当selectedActors为空时会抛出异常
     */
    private boolean _ignoreNoAssign  = false;
    
    /**
     *  
     * xml name: mustInAssignment
     * 是否要求actor必须在assignment范围内选择。如果选择false, 则引擎执行时以前台传入的selectedActors为准，不检查是否它们是否在assignment范围内。
     */
    private boolean _mustInAssignment  = true;
    
    /**
     *  
     * xml name: selectExpr
     * 从actors集合中过滤得到实际使用的actor列表。仅当selection=auto时才会被使用。
     */
    private io.nop.core.lang.eval.IEvalAction _selectExpr ;
    
    /**
     *  
     * xml name: selection
     * 是否允许用户在actors范围内选择，是单选还是多选。缺省情况下所有配置的actor都会被自动选择
     */
    private io.nop.wf.api.actor.WfAssignmentSelection _selection ;
    
    /**
     *  
     * xml name: useManagerWhenNoAssign
     * 
     */
    private boolean _useManagerWhenNoAssign  = true;
    
    /**
     * 
     * xml name: actors
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfAssignmentActorModel> getActors(){
      return _actors;
    }

    
    public void setActors(java.util.List<io.nop.wf.core.model.WfAssignmentActorModel> value){
        checkAllowChange();
        
        this._actors = KeyedList.fromList(value, io.nop.wf.core.model.WfAssignmentActorModel::getActorModelId);
           
    }

    
    public io.nop.wf.core.model.WfAssignmentActorModel getActor(String name){
        return this._actors.getByKey(name);
    }

    public boolean hasActor(String name){
        return this._actors.containsKey(name);
    }

    public void addActor(io.nop.wf.core.model.WfAssignmentActorModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfAssignmentActorModel> list = this.getActors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfAssignmentActorModel::getActorModelId);
            setActors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_actors(){
        return this._actors.keySet();
    }

    public boolean hasActors(){
        return !this._actors.isEmpty();
    }
    
    /**
     * 
     * xml name: defaultActorExpr
     *  当actors集合中实际选择的人员为空时会使用defaultActor
     */
    
    public io.nop.core.lang.eval.IEvalAction getDefaultActorExpr(){
      return _defaultActorExpr;
    }

    
    public void setDefaultActorExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._defaultActorExpr = value;
           
    }

    
    /**
     * 
     * xml name: defaultOwnerExpr
     *  对于每一个actor，从actor对应的user中选择一个作为owner
     */
    
    public io.nop.core.lang.eval.IEvalAction getDefaultOwnerExpr(){
      return _defaultOwnerExpr;
    }

    
    public void setDefaultOwnerExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._defaultOwnerExpr = value;
           
    }

    
    /**
     * 
     * xml name: ignoreNoAssign
     *  是否允许不选择actor。如果不允许，则当selectedActors为空时会抛出异常
     */
    
    public boolean isIgnoreNoAssign(){
      return _ignoreNoAssign;
    }

    
    public void setIgnoreNoAssign(boolean value){
        checkAllowChange();
        
        this._ignoreNoAssign = value;
           
    }

    
    /**
     * 
     * xml name: mustInAssignment
     *  是否要求actor必须在assignment范围内选择。如果选择false, 则引擎执行时以前台传入的selectedActors为准，不检查是否它们是否在assignment范围内。
     */
    
    public boolean isMustInAssignment(){
      return _mustInAssignment;
    }

    
    public void setMustInAssignment(boolean value){
        checkAllowChange();
        
        this._mustInAssignment = value;
           
    }

    
    /**
     * 
     * xml name: selectExpr
     *  从actors集合中过滤得到实际使用的actor列表。仅当selection=auto时才会被使用。
     */
    
    public io.nop.core.lang.eval.IEvalAction getSelectExpr(){
      return _selectExpr;
    }

    
    public void setSelectExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._selectExpr = value;
           
    }

    
    /**
     * 
     * xml name: selection
     *  是否允许用户在actors范围内选择，是单选还是多选。缺省情况下所有配置的actor都会被自动选择
     */
    
    public io.nop.wf.api.actor.WfAssignmentSelection getSelection(){
      return _selection;
    }

    
    public void setSelection(io.nop.wf.api.actor.WfAssignmentSelection value){
        checkAllowChange();
        
        this._selection = value;
           
    }

    
    /**
     * 
     * xml name: useManagerWhenNoAssign
     *  
     */
    
    public boolean isUseManagerWhenNoAssign(){
      return _useManagerWhenNoAssign;
    }

    
    public void setUseManagerWhenNoAssign(boolean value){
        checkAllowChange();
        
        this._useManagerWhenNoAssign = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actors);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("actors",this.getActors());
        out.put("defaultActorExpr",this.getDefaultActorExpr());
        out.put("defaultOwnerExpr",this.getDefaultOwnerExpr());
        out.put("ignoreNoAssign",this.isIgnoreNoAssign());
        out.put("mustInAssignment",this.isMustInAssignment());
        out.put("selectExpr",this.getSelectExpr());
        out.put("selection",this.getSelection());
        out.put("useManagerWhenNoAssign",this.isUseManagerWhenNoAssign());
    }

    public WfAssignmentModel cloneInstance(){
        WfAssignmentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfAssignmentModel instance){
        super.copyTo(instance);
        
        instance.setActors(this.getActors());
        instance.setDefaultActorExpr(this.getDefaultActorExpr());
        instance.setDefaultOwnerExpr(this.getDefaultOwnerExpr());
        instance.setIgnoreNoAssign(this.isIgnoreNoAssign());
        instance.setMustInAssignment(this.isMustInAssignment());
        instance.setSelectExpr(this.getSelectExpr());
        instance.setSelection(this.getSelection());
        instance.setUseManagerWhenNoAssign(this.isUseManagerWhenNoAssign());
    }

    protected WfAssignmentModel newInstance(){
        return (WfAssignmentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
