package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableColumnConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: hidden
     * 
     */
    private boolean _hidden  = false;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.WordTableColumnTemplateModel _model ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Double _width ;
    
    /**
     * 
     * xml name: hidden
     *  
     */
    
    public boolean isHidden(){
      return _hidden;
    }

    
    public void setHidden(boolean value){
        checkAllowChange();
        
        this._hidden = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.WordTableColumnTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.WordTableColumnTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: styleId
     *  
     */
    
    public java.lang.String getStyleId(){
      return _styleId;
    }

    
    public void setStyleId(java.lang.String value){
        checkAllowChange();
        
        this._styleId = value;
           
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
        
        out.putNotNull("hidden",this.isHidden());
        out.putNotNull("model",this.getModel());
        out.putNotNull("styleId",this.getStyleId());
        out.putNotNull("width",this.getWidth());
    }

    public WordTableColumnConfig cloneInstance(){
        WordTableColumnConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableColumnConfig instance){
        super.copyTo(instance);
        
        instance.setHidden(this.isHidden());
        instance.setModel(this.getModel());
        instance.setStyleId(this.getStyleId());
        instance.setWidth(this.getWidth());
    }

    protected WordTableColumnConfig newInstance(){
        return (WordTableColumnConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
