package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [42:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanConstantValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: static-field
     * 
     */
    private java.lang.String _staticField ;
    
    /**
     * 
     * xml name: static-field
     *  
     */
    
    public java.lang.String getStaticField(){
      return _staticField;
    }

    
    public void setStaticField(java.lang.String value){
        checkAllowChange();
        
        this._staticField = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("staticField",this.getStaticField());
    }
}
 // resume CPD analysis - CPD-ON
