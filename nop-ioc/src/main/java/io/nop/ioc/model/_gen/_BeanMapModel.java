package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [261:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _BeanMapModel extends io.nop.ioc.model.BeanMapValue {
    
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
     * xml name: ioc:init-order
     * 
     */
    private int _iocInitOrder  = 100;
    
    /**
     *  
     * xml name: lazy-init
     * 
     */
    private java.lang.Boolean _lazyInit  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.util.Set<java.lang.String> _name ;
    
    /**
     *  
     * xml name: scope
     * 
     */
    private java.lang.String _scope ;
    
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
     * xml name: ioc:init-order
     *  
     */
    
    public int getIocInitOrder(){
      return _iocInitOrder;
    }

    
    public void setIocInitOrder(int value){
        checkAllowChange();
        
        this._iocInitOrder = value;
           
    }

    
    /**
     * 
     * xml name: lazy-init
     *  
     */
    
    public java.lang.Boolean getLazyInit(){
      return _lazyInit;
    }

    
    public void setLazyInit(java.lang.Boolean value){
        checkAllowChange();
        
        this._lazyInit = value;
           
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

    
    /**
     * 
     * xml name: scope
     *  
     */
    
    public java.lang.String getScope(){
      return _scope;
    }

    
    public void setScope(java.lang.String value){
        checkAllowChange();
        
        this._scope = value;
           
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
        
        out.put("id",this.getId());
        out.put("iocAllowOverride",this.isIocAllowOverride());
        out.put("iocDefault",this.isIocDefault());
        out.put("iocInitOrder",this.getIocInitOrder());
        out.put("lazyInit",this.getLazyInit());
        out.put("name",this.getName());
        out.put("scope",this.getScope());
    }
}
 // resume CPD analysis - CPD-ON
