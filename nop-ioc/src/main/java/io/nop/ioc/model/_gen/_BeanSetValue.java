package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [31:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanSetValue extends io.nop.ioc.model.BeanCollectionValue {
    
    /**
     *  
     * xml name: set-class
     * 
     */
    private java.lang.String _setClass ;
    
    /**
     * 
     * xml name: set-class
     *  
     */
    
    public java.lang.String getSetClass(){
      return _setClass;
    }

    
    public void setSetClass(java.lang.String value){
        checkAllowChange();
        
        this._setClass = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("setClass",this.getSetClass());
    }
}
 // resume CPD analysis - CPD-ON
