package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableRow;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableRow extends io.nop.core.model.table.impl.AbstractRow {
    
    /**
     *  
     * xml name: cells
     * 
     */
    private java.util.List<io.nop.office.doc.model.WordTableCell> _cells = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.Double _height ;
    
    /**
     *  
     * xml name: hidden
     * 
     */
    private boolean _hidden  = false;
    
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
    private io.nop.office.doc.model.WordTableRowTemplateModel _model ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     * 
     * xml name: cells
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.WordTableCell> getCells(){
      return _cells;
    }

    
    public void setCells(java.util.List<io.nop.office.doc.model.WordTableCell> value){
        checkAllowChange();
        
        this._cells = value;
           
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
    
    public io.nop.office.doc.model.WordTableRowTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.WordTableRowTemplateModel value){
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cells = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cells);
            
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("cells",this.getCells());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("hidden",this.isHidden());
        out.putNotNull("id",this.getId());
        out.putNotNull("model",this.getModel());
        out.putNotNull("styleId",this.getStyleId());
    }

    public WordTableRow cloneInstance(){
        WordTableRow instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableRow instance){
        super.copyTo(instance);
        
        instance.setCells(this.getCells());
        instance.setHeight(this.getHeight());
        instance.setHidden(this.isHidden());
        instance.setId(this.getId());
        instance.setModel(this.getModel());
        instance.setStyleId(this.getStyleId());
    }

    protected WordTableRow newInstance(){
        return (WordTableRow) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
