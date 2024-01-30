package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanConstantValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [42:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("staticField",this.getStaticField());
    }

    public BeanConstantValue cloneInstance(){
        BeanConstantValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanConstantValue instance){
        super.copyTo(instance);
        
        instance.setStaticField(this.getStaticField());
    }

    protected BeanConstantValue newInstance(){
        return (BeanConstantValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
