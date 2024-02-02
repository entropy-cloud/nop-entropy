package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiStyleComponentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [1:2:0:0]/nop/schema/xui/style-component.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiStyleComponentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: component
     * 
     */
    private java.lang.String _component ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
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
     * xml name: component
     *  
     */
    
    public java.lang.String getComponent(){
      return _component;
    }

    
    public void setComponent(java.lang.String value){
        checkAllowChange();
        
        this._component = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("component",this.getComponent());
        out.putNotNull("name",this.getName());
    }

    public UiStyleComponentModel cloneInstance(){
        UiStyleComponentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiStyleComponentModel instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setComponent(this.getComponent());
        instance.setName(this.getName());
    }

    protected UiStyleComponentModel newInstance(){
        return (UiStyleComponentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
