package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanConstructorArgModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [127:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanConstructorArgModel extends io.nop.ioc.model.BeanPropValue {
    
    /**
     *  
     * xml name: index
     * 
     */
    private int _index ;
    
    /**
     *  
     * xml name: ioc:skip-if-empty
     * 
     */
    private boolean _iocSkipIfEmpty  = false;
    
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
     * xml name: index
     *  
     */
    
    public int getIndex(){
      return _index;
    }

    
    public void setIndex(int value){
        checkAllowChange();
        
        this._index = value;
           
    }

    
    /**
     * 
     * xml name: ioc:skip-if-empty
     *  
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
        
        out.put("index",this.getIndex());
        out.put("iocSkipIfEmpty",this.isIocSkipIfEmpty());
        out.put("ref",this.getRef());
        out.put("value",this.getValue());
    }

    public BeanConstructorArgModel cloneInstance(){
        BeanConstructorArgModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanConstructorArgModel instance){
        super.copyTo(instance);
        
        instance.setIndex(this.getIndex());
        instance.setIocSkipIfEmpty(this.isIocSkipIfEmpty());
        instance.setRef(this.getRef());
        instance.setValue(this.getValue());
    }

    protected BeanConstructorArgModel newInstance(){
        return (BeanConstructorArgModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
