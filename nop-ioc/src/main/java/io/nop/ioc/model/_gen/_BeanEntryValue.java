package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [91:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanEntryValue extends io.nop.ioc.model.BeanPropValue {
    
    /**
     *  
     * xml name: key
     * 
     */
    private java.lang.String _key ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     *  
     * xml name: value-ref
     * 
     */
    private java.lang.String _valueRef ;
    
    /**
     * 
     * xml name: key
     *  
     */
    
    public java.lang.String getKey(){
      return _key;
    }

    
    public void setKey(java.lang.String value){
        checkAllowChange();
        
        this._key = value;
           
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

    
    /**
     * 
     * xml name: value-ref
     *  
     */
    
    public java.lang.String getValueRef(){
      return _valueRef;
    }

    
    public void setValueRef(java.lang.String value){
        checkAllowChange();
        
        this._valueRef = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("key",this.getKey());
        out.put("value",this.getValue());
        out.put("valueRef",this.getValueRef());
    }
}
 // resume CPD analysis - CPD-ON
