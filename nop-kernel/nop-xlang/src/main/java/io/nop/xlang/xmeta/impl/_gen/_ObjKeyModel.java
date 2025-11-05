package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xmeta.impl.ObjKeyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xmeta.xdef <p>
 * 除主键之外的其他唯一键
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ObjKeyModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: props
     * 
     */
    private java.util.Set<java.lang.String> _props ;
    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
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
     * xml name: props
     *  
     */
    
    public java.util.Set<java.lang.String> getProps(){
      return _props;
    }

    
    public void setProps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._props = value;
           
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
        
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("name",this.getName());
        out.putNotNull("props",this.getProps());
    }

    public ObjKeyModel cloneInstance(){
        ObjKeyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ObjKeyModel instance){
        super.copyTo(instance);
        
        instance.setDisplayName(this.getDisplayName());
        instance.setName(this.getName());
        instance.setProps(this.getProps());
    }

    protected ObjKeyModel newInstance(){
        return (ObjKeyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
