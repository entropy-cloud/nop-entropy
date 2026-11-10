package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeImageModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: assetRef
     * 
     */
    private java.lang.String _assetRef ;
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.Double _height ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.OfficeImageTemplateModel _model ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Double _width ;
    
    /**
     * 
     * xml name: assetRef
     *  
     */
    
    public java.lang.String getAssetRef(){
      return _assetRef;
    }

    
    public void setAssetRef(java.lang.String value){
        checkAllowChange();
        
        this._assetRef = value;
           
    }

    
    /**
     * 
     * xml name: height
     *  
     */
    
    public java.lang.Double getHeight(){
      return _height;
    }

    
    public void setHeight(java.lang.Double value){
        checkAllowChange();
        
        this._height = value;
           
    }

    
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
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.OfficeImageTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeImageTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public java.lang.Double getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Double value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("assetRef",this.getAssetRef());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("id",this.getId());
        out.putNotNull("model",this.getModel());
        out.putNotNull("type",this.getType());
        out.putNotNull("width",this.getWidth());
    }

    public OfficeImageModel cloneInstance(){
        OfficeImageModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeImageModel instance){
        super.copyTo(instance);
        
        instance.setAssetRef(this.getAssetRef());
        instance.setHeight(this.getHeight());
        instance.setId(this.getId());
        instance.setModel(this.getModel());
        instance.setType(this.getType());
        instance.setWidth(this.getWidth());
    }

    protected OfficeImageModel newInstance(){
        return (OfficeImageModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
