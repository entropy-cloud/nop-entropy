package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfJoinStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfJoinStepModel extends io.nop.wf.core.model.WfStepModel {
    
    /**
     *  
     * xml name: join-group-expr
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
     * xml name: passPercent
     * 
     */
    private java.lang.Double _passPercent ;
    
    /**
     *  
     * xml name: passWeight
     * 所有同意的参与者投票权重超过多少记为通过
     */
    private java.lang.Integer _passWeight ;
    
    /**
     *  
     * xml name: waitStepNames
     * joinType=and时所需要等待的上游步骤，如果未设置则按照图的依赖关系自动分析得到。
     */
    private java.util.Set<java.lang.String> _waitStepNames ;
    
    /**
     * 
     * xml name: join-group-expr
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
     * xml name: passPercent
     *  
     */
    
    public java.lang.Double getPassPercent(){
      return _passPercent;
    }

    
    public void setPassPercent(java.lang.Double value){
        checkAllowChange();
        
        this._passPercent = value;
           
    }

    
    /**
     * 
     * xml name: passWeight
     *  所有同意的参与者投票权重超过多少记为通过
     */
    
    public java.lang.Integer getPassWeight(){
      return _passWeight;
    }

    
    public void setPassWeight(java.lang.Integer value){
        checkAllowChange();
        
        this._passWeight = value;
           
    }

    
    /**
     * 
     * xml name: waitStepNames
     *  joinType=and时所需要等待的上游步骤，如果未设置则按照图的依赖关系自动分析得到。
     */
    
    public java.util.Set<java.lang.String> getWaitStepNames(){
      return _waitStepNames;
    }

    
    public void setWaitStepNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._waitStepNames = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("joinGroupExpr",this.getJoinGroupExpr());
        out.putNotNull("joinType",this.getJoinType());
        out.putNotNull("passPercent",this.getPassPercent());
        out.putNotNull("passWeight",this.getPassWeight());
        out.putNotNull("waitStepNames",this.getWaitStepNames());
    }

    public WfJoinStepModel cloneInstance(){
        WfJoinStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfJoinStepModel instance){
        super.copyTo(instance);
        
        instance.setJoinGroupExpr(this.getJoinGroupExpr());
        instance.setJoinType(this.getJoinType());
        instance.setPassPercent(this.getPassPercent());
        instance.setPassWeight(this.getPassWeight());
        instance.setWaitStepNames(this.getWaitStepNames());
    }

    protected WfJoinStepModel newInstance(){
        return (WfJoinStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
