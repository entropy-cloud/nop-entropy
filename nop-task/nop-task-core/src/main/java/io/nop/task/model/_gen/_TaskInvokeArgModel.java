package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [126:18:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _TaskInvokeArgModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: index
     * 
     */
    private int _index ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _value ;
    
    /**
     * 
     * xml name: index
     *  
     */
    
    public int getIndex(){
      return _index;
    }

    
    public void setIndex(int value){
        checkAllowChange();
        
        this._index = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValue(){
      return _value;
    }

    
    public void setValue(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._value = value;
           
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
        out.put("value",this.getValue());
    }
}
 // resume CPD analysis - CPD-ON
