package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanListModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanListModel extends io.nop.ioc.model.BeanListValue {
    
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
     * xml name: ioc:sort-order
     * 
     */
    private int _iocSortOrder  = 100;
    
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
     * xml name: ioc:sort-order
     *  
     */
    
    public int getIocSortOrder(){
      return _iocSortOrder;
    }

    
    public void setIocSortOrder(int value){
        checkAllowChange();
        
        this._iocSortOrder = value;
           
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
        
        out.putNotNull("id",this.getId());
        out.putNotNull("iocAllowOverride",this.isIocAllowOverride());
        out.putNotNull("iocDefault",this.isIocDefault());
        out.putNotNull("iocInitOrder",this.getIocInitOrder());
        out.putNotNull("iocSortOrder",this.getIocSortOrder());
        out.putNotNull("lazyInit",this.getLazyInit());
        out.putNotNull("name",this.getName());
        out.putNotNull("scope",this.getScope());
    }

    public BeanListModel cloneInstance(){
        BeanListModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanListModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setIocAllowOverride(this.isIocAllowOverride());
        instance.setIocDefault(this.isIocDefault());
        instance.setIocInitOrder(this.getIocInitOrder());
        instance.setIocSortOrder(this.getIocSortOrder());
        instance.setLazyInit(this.getLazyInit());
        instance.setName(this.getName());
        instance.setScope(this.getScope());
    }

    protected BeanListModel newInstance(){
        return (BeanListModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
