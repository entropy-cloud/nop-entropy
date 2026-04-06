package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTable;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTable extends io.nop.core.model.table.impl.AbstractTable<io.nop.office.doc.model.WordTableRow> {
    
    /**
     *  
     * xml name: cols
     * 
     */
    private java.util.List<io.nop.office.doc.model.WordTableColumnConfig> _cols = java.util.Collections.emptyList();
    
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
    private io.nop.office.doc.model.WordTableTemplateModel _model ;
    
    /**
     *  
     * xml name: rows
     * 
     */
    private java.util.List<io.nop.office.doc.model.WordTableRow> _rows = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     * 
     * xml name: cols
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.WordTableColumnConfig> getCols(){
      return _cols;
    }

    
    public void setCols(java.util.List<io.nop.office.doc.model.WordTableColumnConfig> value){
        checkAllowChange();
        
        this._cols = value;
           
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
    
    public io.nop.office.doc.model.WordTableTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.WordTableTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: rows
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.WordTableRow> getRows(){
      return _rows;
    }

    
    public void setRows(java.util.List<io.nop.office.doc.model.WordTableRow> value){
        checkAllowChange();
        
        this._rows = value;
           
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
        
           this._cols = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cols);
            
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
           this._rows = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rows);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("cols",this.getCols());
        out.putNotNull("id",this.getId());
        out.putNotNull("model",this.getModel());
        out.putNotNull("rows",this.getRows());
        out.putNotNull("styleId",this.getStyleId());
    }

    public WordTable cloneInstance(){
        WordTable instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTable instance){
        super.copyTo(instance);
        
        instance.setCols(this.getCols());
        instance.setId(this.getId());
        instance.setModel(this.getModel());
        instance.setRows(this.getRows());
        instance.setStyleId(this.getStyleId());
    }

    protected WordTable newInstance(){
        return (WordTable) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
