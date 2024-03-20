package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.LoopNTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [164:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LoopNTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: beginExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beginExpr ;
    
    /**
     *  
     * xml name: endExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _endExpr ;
    
    /**
     *  
     * xml name: index
     * 
     */
    private java.lang.String _index ;
    
    /**
     *  
     * xml name: stepExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _stepExpr ;
    
    /**
     *  
     * xml name: var
     * 
     */
    private java.lang.String _var ;
    
    /**
     * 
     * xml name: beginExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeginExpr(){
      return _beginExpr;
    }

    
    public void setBeginExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beginExpr = value;
           
    }

    
    /**
     * 
     * xml name: endExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEndExpr(){
      return _endExpr;
    }

    
    public void setEndExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._endExpr = value;
           
    }

    
    /**
     * 
     * xml name: index
     *  
     */
    
    public java.lang.String getIndex(){
      return _index;
    }

    
    public void setIndex(java.lang.String value){
        checkAllowChange();
        
        this._index = value;
           
    }

    
    /**
     * 
     * xml name: stepExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getStepExpr(){
      return _stepExpr;
    }

    
    public void setStepExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._stepExpr = value;
           
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
        
        out.putNotNull("beginExpr",this.getBeginExpr());
        out.putNotNull("endExpr",this.getEndExpr());
        out.putNotNull("index",this.getIndex());
        out.putNotNull("stepExpr",this.getStepExpr());
        out.putNotNull("var",this.getVar());
    }

    public LoopNTaskStepModel cloneInstance(){
        LoopNTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LoopNTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setBeginExpr(this.getBeginExpr());
        instance.setEndExpr(this.getEndExpr());
        instance.setIndex(this.getIndex());
        instance.setStepExpr(this.getStepExpr());
        instance.setVar(this.getVar());
    }

    protected LoopNTaskStepModel newInstance(){
        return (LoopNTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
