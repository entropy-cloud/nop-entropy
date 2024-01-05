package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanPropEntryValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [97:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanPropEntryValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: key
     * 
     */
    private java.lang.String _key ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getBody(){
      return _body;
    }

    
    public void setBody(java.lang.String value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: key
     *  
     */
    
    public java.lang.String getKey(){
      return _key;
    }

    
    public void setKey(java.lang.String value){
        checkAllowChange();
        
        this._key = value;
           
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
        
        out.put("body",this.getBody());
        out.put("key",this.getKey());
    }

    public BeanPropEntryValue cloneInstance(){
        BeanPropEntryValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanPropEntryValue instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setKey(this.getKey());
    }

    protected BeanPropEntryValue newInstance(){
        return (BeanPropEntryValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
