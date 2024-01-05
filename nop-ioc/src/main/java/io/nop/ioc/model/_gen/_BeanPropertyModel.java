package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanPropertyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [135:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanPropertyModel extends io.nop.ioc.model.BeanPropValue {
    
    /**
     *  
     * xml name: ioc:skip-if-empty
     * 当属性值为空时跳过本属性的设置
     */
    private boolean _iocSkipIfEmpty  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: ref
     * 
     */
    private java.lang.String _ref ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     * 
     * xml name: ioc:skip-if-empty
     *  当属性值为空时跳过本属性的设置
     */
    
    public boolean isIocSkipIfEmpty(){
      return _iocSkipIfEmpty;
    }

    
    public void setIocSkipIfEmpty(boolean value){
        checkAllowChange();
        
        this._iocSkipIfEmpty = value;
           
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
     * xml name: ref
     *  
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
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
        
        out.put("iocSkipIfEmpty",this.isIocSkipIfEmpty());
        out.put("name",this.getName());
        out.put("ref",this.getRef());
        out.put("value",this.getValue());
    }

    public BeanPropertyModel cloneInstance(){
        BeanPropertyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanPropertyModel instance){
        super.copyTo(instance);
        
        instance.setIocSkipIfEmpty(this.isIocSkipIfEmpty());
        instance.setName(this.getName());
        instance.setRef(this.getRef());
        instance.setValue(this.getValue());
    }

    protected BeanPropertyModel newInstance(){
        return (BeanPropertyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
