package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [132:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ForkTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: aggregateVarName
     * 
     */
    private java.lang.String _aggregateVarName ;
    
    /**
     *  
     * xml name: aggregator
     * 
     */
    private io.nop.task.model.TaskBeanModel _aggregator ;
    
    /**
     *  
     * xml name: autoCancelUnfinished
     * 
     */
    private boolean _autoCancelUnfinished  = false;
    
    /**
     *  
     * xml name: joinType
     * 
     */
    private io.nop.task.model.TaskStepJoinType _joinType ;
    
    /**
     *  
     * xml name: producer
     * 
     */
    private io.nop.task.model.TaskBeanModel _producer ;
    
    /**
     *  
     * xml name: var
     * 
     */
    private java.lang.String _var ;
    
    /**
     * 
     * xml name: aggregateVarName
     *  
     */
    
    public java.lang.String getAggregateVarName(){
      return _aggregateVarName;
    }

    
    public void setAggregateVarName(java.lang.String value){
        checkAllowChange();
        
        this._aggregateVarName = value;
           
    }

    
    /**
     * 
     * xml name: aggregator
     *  
     */
    
    public io.nop.task.model.TaskBeanModel getAggregator(){
      return _aggregator;
    }

    
    public void setAggregator(io.nop.task.model.TaskBeanModel value){
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
    
    public io.nop.task.model.TaskStepJoinType getJoinType(){
      return _joinType;
    }

    
    public void setJoinType(io.nop.task.model.TaskStepJoinType value){
        checkAllowChange();
        
        this._joinType = value;
           
    }

    
    /**
     * 
     * xml name: producer
     *  
     */
    
    public io.nop.task.model.TaskBeanModel getProducer(){
      return _producer;
    }

    
    public void setProducer(io.nop.task.model.TaskBeanModel value){
        checkAllowChange();
        
        this._producer = value;
           
    }

    
    /**
     * 
     * xml name: var
     *  
     */
    
    public java.lang.String getVar(){
      return _var;
    }

    
    public void setVar(java.lang.String value){
        checkAllowChange();
        
        this._var = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._aggregator = io.nop.api.core.util.FreezeHelper.deepFreeze(this._aggregator);
            
           this._producer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._producer);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("aggregateVarName",this.getAggregateVarName());
        out.put("aggregator",this.getAggregator());
        out.put("autoCancelUnfinished",this.isAutoCancelUnfinished());
        out.put("joinType",this.getJoinType());
        out.put("producer",this.getProducer());
        out.put("var",this.getVar());
    }
}
 // resume CPD analysis - CPD-ON
