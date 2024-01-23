package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanSetValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [31:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("setClass",this.getSetClass());
    }

    public BeanSetValue cloneInstance(){
        BeanSetValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanSetValue instance){
        super.copyTo(instance);
        
        instance.setSetClass(this.getSetClass());
    }

    protected BeanSetValue newInstance(){
        return (BeanSetValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
