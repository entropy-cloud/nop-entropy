package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeDocPageModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: body
     * 
     */
    private java.util.List<io.nop.office.doc.model.OfficeBlock> _body = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: footer
     * 
     */
    private java.util.List<io.nop.office.doc.model.OfficeBlock> _footer = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: header
     * 
     */
    private java.util.List<io.nop.office.doc.model.OfficeBlock> _header = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.OfficeDocPageTemplateModel _model ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: orientation
     * 
     */
    private java.lang.String _orientation ;
    
    /**
     * 
     * xml name: body
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeBlock> getBody(){
      return _body;
    }

    
    public void setBody(java.util.List<io.nop.office.doc.model.OfficeBlock> value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: footer
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeBlock> getFooter(){
      return _footer;
    }

    
    public void setFooter(java.util.List<io.nop.office.doc.model.OfficeBlock> value){
        checkAllowChange();
        
        this._footer = value;
           
    }

    
    /**
     * 
     * xml name: header
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeBlock> getHeader(){
      return _header;
    }

    
    public void setHeader(java.util.List<io.nop.office.doc.model.OfficeBlock> value){
        checkAllowChange();
        
        this._header = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.OfficeDocPageTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeDocPageTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
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
     * xml name: orientation
     *  
     */
    
    public java.lang.String getOrientation(){
      return _orientation;
    }

    
    public void setOrientation(java.lang.String value){
        checkAllowChange();
        
        this._orientation = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._body = io.nop.api.core.util.FreezeHelper.deepFreeze(this._body);
            
           this._footer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._footer);
            
           this._header = io.nop.api.core.util.FreezeHelper.deepFreeze(this._header);
            
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("footer",this.getFooter());
        out.putNotNull("header",this.getHeader());
        out.putNotNull("model",this.getModel());
        out.putNotNull("name",this.getName());
        out.putNotNull("orientation",this.getOrientation());
    }

    public OfficeDocPageModel cloneInstance(){
        OfficeDocPageModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeDocPageModel instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setFooter(this.getFooter());
        instance.setHeader(this.getHeader());
        instance.setModel(this.getModel());
        instance.setName(this.getName());
        instance.setOrientation(this.getOrientation());
    }

    protected OfficeDocPageModel newInstance(){
        return (OfficeDocPageModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
