package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableCell;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableCell extends io.nop.core.model.table.impl.AbstractCell {
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: mergeAcross
     * 
     */
    private int _mergeAcross  = 0;
    
    /**
     *  
     * xml name: mergeDown
     * 
     */
    private int _mergeDown  = 0;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.WordTableCellTemplateModel _model ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     * 
     * xml name: comment
     *  
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
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
     * xml name: mergeAcross
     *  
     */
    
    public int getMergeAcross(){
      return _mergeAcross;
    }

    
    public void setMergeAcross(int value){
        checkAllowChange();
        
        this._mergeAcross = value;
           
    }

    
    /**
     * 
     * xml name: mergeDown
     *  
     */
    
    public int getMergeDown(){
      return _mergeDown;
    }

    
    public void setMergeDown(int value){
        checkAllowChange();
        
        this._mergeDown = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.WordTableCellTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.WordTableCellTemplateModel value){
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
     * xml name: value
     *  
     */
    
    public java.lang.String getValue(){
      return _value;
    }

    
    public void setValue(java.lang.String value){
        checkAllowChange();
        
        this._value = value;
           
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
        
        out.putNotNull("comment",this.getComment());
        out.putNotNull("id",this.getId());
        out.putNotNull("mergeAcross",this.getMergeAcross());
        out.putNotNull("mergeDown",this.getMergeDown());
        out.putNotNull("model",this.getModel());
        out.putNotNull("styleId",this.getStyleId());
        out.putNotNull("value",this.getValue());
    }

    public WordTableCell cloneInstance(){
        WordTableCell instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableCell instance){
        super.copyTo(instance);
        
        instance.setComment(this.getComment());
        instance.setId(this.getId());
        instance.setMergeAcross(this.getMergeAcross());
        instance.setMergeDown(this.getMergeDown());
        instance.setModel(this.getModel());
        instance.setStyleId(this.getStyleId());
        instance.setValue(this.getValue());
    }

    protected WordTableCell newInstance(){
        return (WordTableCell) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
