package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [218:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfJoinStepModel extends io.nop.wf.core.model.WfStepModel {
    
    /**
     *  
     * xml name: joinGroupExpr
     * joinGroupExpr指定join时的分组条件。
     * join步骤缺省会等待所有前置步骤结束。如果指定了joinGroupExpr, 则joinGroupExpr相同的步骤会被认为是一组。
     * 例如上游步骤A, 下游join步骤为B, 在步骤B中设置了joinGroupExpr="wf.bizEntity.deptId", 则下游join步骤B汇聚时，
     * 会按照实体上标记的deptId进行分组,不同分组的A到达join步骤时会产生不同的B步骤实例。
     */
    private io.nop.core.lang.eval.IEvalAction _joinGroupExpr ;
    
    /**
     *  
     * xml name: joinType
     * joinType=and时等待所有上游步骤都到达时才激活，
     * 而joinType=or时只要有一个步骤到达就激活，后续步骤再到达时如果join步骤还处于活动状态，则不会产生新的join步骤。
     */
    private io.nop.wf.core.model.WfJoinType _joinType ;
    
    /**
     *  
     * xml name: may-activated
     * 是否当前可以激活
     */
    private io.nop.core.lang.eval.IEvalPredicate _mayActivated ;
    
    /**
     *  
     * xml name: waitStepNames
     * 
     */
    private java.util.Set<java.lang.String> _waitStepNames ;
    
    /**
     * 
     * xml name: joinGroupExpr
     *  joinGroupExpr指定join时的分组条件。
     * join步骤缺省会等待所有前置步骤结束。如果指定了joinGroupExpr, 则joinGroupExpr相同的步骤会被认为是一组。
     * 例如上游步骤A, 下游join步骤为B, 在步骤B中设置了joinGroupExpr="wf.bizEntity.deptId", 则下游join步骤B汇聚时，
     * 会按照实体上标记的deptId进行分组,不同分组的A到达join步骤时会产生不同的B步骤实例。
     */
    
    public io.nop.core.lang.eval.IEvalAction getJoinGroupExpr(){
      return _joinGroupExpr;
    }

    
    public void setJoinGroupExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._joinGroupExpr = value;
           
    }

    
    /**
     * 
     * xml name: joinType
     *  joinType=and时等待所有上游步骤都到达时才激活，
     * 而joinType=or时只要有一个步骤到达就激活，后续步骤再到达时如果join步骤还处于活动状态，则不会产生新的join步骤。
     */
    
    public io.nop.wf.core.model.WfJoinType getJoinType(){
      return _joinType;
    }

    
    public void setJoinType(io.nop.wf.core.model.WfJoinType value){
        checkAllowChange();
        
        this._joinType = value;
           
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
     * xml name: waitStepNames
     *  
     */
    
    public java.util.Set<java.lang.String> getWaitStepNames(){
      return _waitStepNames;
    }

    
    public void setWaitStepNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._waitStepNames = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("joinGroupExpr",this.getJoinGroupExpr());
        out.put("joinType",this.getJoinType());
        out.put("mayActivated",this.getMayActivated());
        out.put("waitStepNames",this.getWaitStepNames());
    }
}
 // resume CPD analysis - CPD-ON
