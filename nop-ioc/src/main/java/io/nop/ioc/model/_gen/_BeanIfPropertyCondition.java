package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanIfPropertyCondition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/beans.xdef <p>
 * 配置变量的值为true或者指定值的时候返回true
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanIfPropertyCondition extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enableIfDebug
     * 
     */
    private boolean _enableIfDebug  = false;
    
    /**
     *  
     * xml name: enableIfMissing
     * 当配置变量的值为空时，是否认为条件为true
     */
    private boolean _enableIfMissing  = false;
    
    /**
     *  
     * xml name: name
     * 配置变量名
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: value
     * 如果不指定，则缺省为true
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
     *  当配置变量的值为空时，是否认为条件为true
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
     *  配置变量名
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
     *  如果不指定，则缺省为true
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
        
        out.putNotNull("enableIfDebug",this.isEnableIfDebug());
        out.putNotNull("enableIfMissing",this.isEnableIfMissing());
        out.putNotNull("name",this.getName());
        out.putNotNull("value",this.getValue());
    }

    public BeanIfPropertyCondition cloneInstance(){
        BeanIfPropertyCondition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanIfPropertyCondition instance){
        super.copyTo(instance);
        
        instance.setEnableIfDebug(this.isEnableIfDebug());
        instance.setEnableIfMissing(this.isEnableIfMissing());
        instance.setName(this.getName());
        instance.setValue(this.getValue());
    }

    protected BeanIfPropertyCondition newInstance(){
        return (BeanIfPropertyCondition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
