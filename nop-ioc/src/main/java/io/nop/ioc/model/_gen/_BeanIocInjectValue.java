package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanIocInjectValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanIocInjectValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _beanValueType ;
    
    /**
     *  
     * xml name: ioc:ignore-depends
     * 
     */
    private boolean _iocIgnoreDepends  = false;
    
    /**
     *  
     * xml name: ioc:optional
     * 
     */
    private boolean _iocOptional  = false;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getBeanValueType(){
      return _beanValueType;
    }

    
    public void setBeanValueType(java.lang.String value){
        checkAllowChange();
        
        this._beanValueType = value;
           
    }

    
    /**
     * 
     * xml name: ioc:ignore-depends
     *  
     */
    
    public boolean isIocIgnoreDepends(){
      return _iocIgnoreDepends;
    }

    
    public void setIocIgnoreDepends(boolean value){
        checkAllowChange();
        
        this._iocIgnoreDepends = value;
           
    }

    
    /**
     * 
     * xml name: ioc:optional
     *  
     */
    
    public boolean isIocOptional(){
      return _iocOptional;
    }

    
    public void setIocOptional(boolean value){
        checkAllowChange();
        
        this._iocOptional = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("beanValueType",this.getBeanValueType());
        out.putNotNull("iocIgnoreDepends",this.isIocIgnoreDepends());
        out.putNotNull("iocOptional",this.isIocOptional());
        out.putNotNull("type",this.getType());
    }

    public BeanIocInjectValue cloneInstance(){
        BeanIocInjectValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanIocInjectValue instance){
        super.copyTo(instance);
        
        instance.setBeanValueType(this.getBeanValueType());
        instance.setIocIgnoreDepends(this.isIocIgnoreDepends());
        instance.setIocOptional(this.isIocOptional());
        instance.setType(this.getType());
    }

    protected BeanIocInjectValue newInstance(){
        return (BeanIocInjectValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
