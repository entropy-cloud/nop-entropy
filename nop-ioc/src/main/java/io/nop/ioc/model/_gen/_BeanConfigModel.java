package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [235:6:0:0]/nop/schema/beans.xdef <p>
 * 指定parent属性时，从parent对应的bean继承配置。但是class/primary/abstract/autowire-candidate/lazy-init/depends-on等属性不会被继承
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanConfigModel extends io.nop.ioc.model.BeanValue {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: ioc:allow-override
     * 
     */
    private boolean _iocAllowOverride  = false;
    
    /**
     *  
     * xml name: ioc:default
     * 
     */
    private boolean _iocDefault  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.util.Set<java.lang.String> _name ;
    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: ioc:allow-override
     *  
     */
    
    public boolean isIocAllowOverride(){
      return _iocAllowOverride;
    }

    
    public void setIocAllowOverride(boolean value){
        checkAllowChange();
        
        this._iocAllowOverride = value;
           
    }

    
    /**
     * 
     * xml name: ioc:default
     *  
     */
    
    public boolean isIocDefault(){
      return _iocDefault;
    }

    
    public void setIocDefault(boolean value){
        checkAllowChange();
        
        this._iocDefault = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.util.Set<java.lang.String> getName(){
      return _name;
    }

    
    public void setName(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._name = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("id",this.getId());
        out.put("iocAllowOverride",this.isIocAllowOverride());
        out.put("iocDefault",this.isIocDefault());
        out.put("name",this.getName());
    }
}
 // resume CPD analysis - CPD-ON
