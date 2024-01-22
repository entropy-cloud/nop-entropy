package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanUnlessPropertyCondition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [165:14:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanUnlessPropertyCondition extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enableIfDebug
     * 
     */
    private boolean _enableIfDebug  = false;
    
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
     * xml name: enableIfDebug
     *  
     */
    
    public boolean isEnableIfDebug(){
      return _enableIfDebug;
    }

    
    public void setEnableIfDebug(boolean value){
        checkAllowChange();
        
        this._enableIfDebug = value;
           
    }

    
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
        
        out.put("enableIfDebug",this.isEnableIfDebug());
        out.put("enableIfMissing",this.isEnableIfMissing());
        out.put("name",this.getName());
        out.put("value",this.getValue());
    }

    public BeanUnlessPropertyCondition cloneInstance(){
        BeanUnlessPropertyCondition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanUnlessPropertyCondition instance){
        super.copyTo(instance);
        
        instance.setEnableIfDebug(this.isEnableIfDebug());
        instance.setEnableIfMissing(this.isEnableIfMissing());
        instance.setName(this.getName());
        instance.setValue(this.getValue());
    }

    protected BeanUnlessPropertyCondition newInstance(){
        return (BeanUnlessPropertyCondition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
