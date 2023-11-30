package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [156:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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
     * xml name: step
     * 
     */
    private int _step  = 0;
    
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
     * xml name: step
     *  
     */
    
    public int getStep(){
      return _step;
    }

    
    public void setStep(int value){
        checkAllowChange();
        
        this._step = value;
           
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
        
        out.put("beginExpr",this.getBeginExpr());
        out.put("endExpr",this.getEndExpr());
        out.put("index",this.getIndex());
        out.put("step",this.getStep());
        out.put("var",this.getVar());
    }
}
 // resume CPD analysis - CPD-ON
