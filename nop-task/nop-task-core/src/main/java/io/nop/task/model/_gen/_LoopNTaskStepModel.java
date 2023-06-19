package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [155:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _LoopNTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: begin
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _begin ;
    
    /**
     *  
     * xml name: end
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _end ;
    
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
     * xml name: begin
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBegin(){
      return _begin;
    }

    
    public void setBegin(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._begin = value;
           
    }

    
    /**
     * 
     * xml name: end
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEnd(){
      return _end;
    }

    
    public void setEnd(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._end = value;
           
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("begin",this.getBegin());
        out.put("end",this.getEnd());
        out.put("index",this.getIndex());
        out.put("step",this.getStep());
        out.put("var",this.getVar());
    }
}
 // resume CPD analysis - CPD-ON
