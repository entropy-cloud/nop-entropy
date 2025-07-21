package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.ParallelTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 并行执行所有子步骤
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ParallelTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: aggregator
     * 对并行步骤执行结果进行汇总处理
     */
    private io.nop.core.lang.eval.IEvalFunction _aggregator ;
    
    /**
     *  
     * xml name: autoCancelUnfinished
     * 
     */
    private boolean _autoCancelUnfinished  = true;
    
    /**
     *  
     * xml name: joinType
     * 
     */
    private io.nop.commons.concurrent.AsyncJoinType _joinType ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: aggregator
     *  对并行步骤执行结果进行汇总处理
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAggregator(){
      return _aggregator;
    }

    
    public void setAggregator(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._aggregator = value;
           
    }

    
    /**
     * 
     * xml name: autoCancelUnfinished
     *  
     */
    
    public boolean isAutoCancelUnfinished(){
      return _autoCancelUnfinished;
    }

    
    public void setAutoCancelUnfinished(boolean value){
        checkAllowChange();
        
        this._autoCancelUnfinished = value;
           
    }

    
    /**
     * 
     * xml name: joinType
     *  
     */
    
    public io.nop.commons.concurrent.AsyncJoinType getJoinType(){
      return _joinType;
    }

    
    public void setJoinType(io.nop.commons.concurrent.AsyncJoinType value){
        checkAllowChange();
        
        this._joinType = value;
           
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
        
        out.putNotNull("aggregator",this.getAggregator());
        out.putNotNull("autoCancelUnfinished",this.isAutoCancelUnfinished());
        out.putNotNull("joinType",this.getJoinType());
        out.putNotNull("type",this.getType());
    }

    public ParallelTaskStepModel cloneInstance(){
        ParallelTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ParallelTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setAggregator(this.getAggregator());
        instance.setAutoCancelUnfinished(this.isAutoCancelUnfinished());
        instance.setJoinType(this.getJoinType());
        instance.setType(this.getType());
    }

    protected ParallelTaskStepModel newInstance(){
        return (ParallelTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
