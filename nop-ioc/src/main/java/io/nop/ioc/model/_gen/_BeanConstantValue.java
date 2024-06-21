package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanConstantValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanConstantValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _beanValueType ;
    
    /**
     *  
     * xml name: static-field
     * 
     */
    private java.lang.String _staticField ;
    
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
        out.putNotNull("staticField",this.getStaticField());
    }

    public BeanConstantValue cloneInstance(){
        BeanConstantValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanConstantValue instance){
        super.copyTo(instance);
        
        instance.setBeanValueType(this.getBeanValueType());
        instance.setStaticField(this.getStaticField());
    }

    protected BeanConstantValue newInstance(){
        return (BeanConstantValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
