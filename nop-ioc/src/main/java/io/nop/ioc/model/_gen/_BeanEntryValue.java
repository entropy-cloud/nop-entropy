package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanEntryValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [91:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.put("key",this.getKey());
        out.put("value",this.getValue());
        out.put("valueRef",this.getValueRef());
    }

    public BeanEntryValue cloneInstance(){
        BeanEntryValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanEntryValue instance){
        super.copyTo(instance);
        
        instance.setKey(this.getKey());
        instance.setValue(this.getValue());
        instance.setValueRef(this.getValueRef());
    }

    protected BeanEntryValue newInstance(){
        return (BeanEntryValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
