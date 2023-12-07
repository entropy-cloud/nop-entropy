package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [45:6:0:0]/nop/schema/xui/disp.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
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
        
        out.put("blank",this.getBlank());
        out.put("label",this.getLabel());
        out.put("url",this.getUrl());
    }
}
 // resume CPD analysis - CPD-ON
