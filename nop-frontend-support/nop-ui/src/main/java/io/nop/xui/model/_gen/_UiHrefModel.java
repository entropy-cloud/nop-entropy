package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiHrefModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/disp.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiHrefModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: blank
     * 是否打开新页面
     */
    private java.lang.Boolean _blank ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: url
     * 
     */
    private java.lang.String _url ;
    
    /**
     * 
     * xml name: blank
     *  是否打开新页面
     */
    
    public java.lang.Boolean getBlank(){
      return _blank;
    }

    
    public void setBlank(java.lang.Boolean value){
        checkAllowChange();
        
        this._blank = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: url
     *  
     */
    
    public java.lang.String getUrl(){
      return _url;
    }

    
    public void setUrl(java.lang.String value){
        checkAllowChange();
        
        this._url = value;
           
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
        
        out.putNotNull("blank",this.getBlank());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("url",this.getUrl());
    }

    public UiHrefModel cloneInstance(){
        UiHrefModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiHrefModel instance){
        super.copyTo(instance);
        
        instance.setBlank(this.getBlank());
        instance.setLabel(this.getLabel());
        instance.setUrl(this.getUrl());
    }

    protected UiHrefModel newInstance(){
        return (UiHrefModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
