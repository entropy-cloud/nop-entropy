package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [151:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _LoopTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: index
     * 
     */
    private java.lang.String _index ;
    
    /**
     *  
     * xml name: items
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _items ;
    
    /**
     *  
     * xml name: maxCount
     * 
     */
    private int _maxCount  = 0;
    
    /**
     *  
     * xml name: until
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _until ;
    
    /**
     *  
     * xml name: var
     * 
     */
    private java.lang.String _var ;
    
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
     * xml name: items
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getItems(){
      return _items;
    }

    
    public void setItems(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._items = value;
           
    }

    
    /**
     * 
     * xml name: maxCount
     *  
     */
    
    public int getMaxCount(){
      return _maxCount;
    }

    
    public void setMaxCount(int value){
        checkAllowChange();
        
        this._maxCount = value;
           
    }

    
    /**
     * 
     * xml name: until
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getUntil(){
      return _until;
    }

    
    public void setUntil(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._until = value;
           
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
        
        out.put("index",this.getIndex());
        out.put("items",this.getItems());
        out.put("maxCount",this.getMaxCount());
        out.put("until",this.getUntil());
        out.put("var",this.getVar());
    }
}
 // resume CPD analysis - CPD-ON
