package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [165:14:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanUnlessPropertyCondition extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enableIfMissing
     * 
     */
    private boolean _enableIfMissing  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     * 
     * xml name: enableIfMissing
     *  
     */
    
    public boolean isEnableIfMissing(){
      return _enableIfMissing;
    }

    
    public void setEnableIfMissing(boolean value){
        checkAllowChange();
        
        this._enableIfMissing = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public java.lang.String getValue(){
      return _value;
    }

    
    public void setValue(java.lang.String value){
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
        
        out.put("enableIfMissing",this.isEnableIfMissing());
        out.put("name",this.getName());
        out.put("value",this.getValue());
    }
}
 // resume CPD analysis - CPD-ON
