package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [162:14:0:0]/nop/schema/beans.xdef <p>
 * 配置变量的值为true或者指定值的时候返回true
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanIfPropertyCondition extends io.nop.core.resource.component.AbstractComponentModel {
    
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
        
        out.put("enableIfMissing",this.isEnableIfMissing());
        out.put("name",this.getName());
        out.put("value",this.getValue());
    }
}
 // resume CPD analysis - CPD-ON
