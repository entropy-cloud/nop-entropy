package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanConstantModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanConstantModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: ioc:sort-order
     * 
     */
    private int _iocSortOrder  = 100;
    
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
     * xml name: static-field
     * 
     */
    private java.lang.String _staticField ;
    
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
        
        out.putNotNull("id",this.getId());
        out.putNotNull("iocAllowOverride",this.isIocAllowOverride());
        out.putNotNull("iocDefault",this.isIocDefault());
        out.putNotNull("iocSortOrder",this.getIocSortOrder());
        out.putNotNull("name",this.getName());
        out.putNotNull("scope",this.getScope());
        out.putNotNull("staticField",this.getStaticField());
    }

    public BeanConstantModel cloneInstance(){
        BeanConstantModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanConstantModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setIocAllowOverride(this.isIocAllowOverride());
        instance.setIocDefault(this.isIocDefault());
        instance.setIocSortOrder(this.getIocSortOrder());
        instance.setName(this.getName());
        instance.setScope(this.getScope());
        instance.setStaticField(this.getStaticField());
    }

    protected BeanConstantModel newInstance(){
        return (BeanConstantModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
