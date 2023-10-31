package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [224:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfJoinStepModel extends io.nop.wf.core.model.WfStepModel {
    
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
        
        out.put("joinType",this.getJoinType());
        out.put("mayActivated",this.getMayActivated());
        out.put("waitStepNames",this.getWaitStepNames());
    }
}
 // resume CPD analysis - CPD-ON
