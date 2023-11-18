package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [8:2:0:0]/nop/schema/wf/assignment.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfAssignmentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actors
     * 
     */
    private java.util.List<io.nop.wf.core.model.WfAssignmentActorModel> _actors = java.util.Collections.emptyList();
    
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
        
        this._actors = value;
           
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actors);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("actors",this.getActors());
        out.put("defaultOwnerExpr",this.getDefaultOwnerExpr());
        out.put("ignoreNoAssign",this.isIgnoreNoAssign());
        out.put("mustInAssignment",this.isMustInAssignment());
        out.put("selectExpr",this.getSelectExpr());
        out.put("selection",this.getSelection());
        out.put("useManagerWhenNoAssign",this.isUseManagerWhenNoAssign());
    }
}
 // resume CPD analysis - CPD-ON
