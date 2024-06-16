package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelCell;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelCell extends io.nop.core.model.table.impl.AbstractCell {
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: formula
     * 
     */
    private java.lang.String _formula ;
    
    /**
     *  
     * xml name: mergeAcross
     * 向右合并的列数。mergeAcross + 1 == colSpan
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
    private io.nop.excel.model.XptCellModel _model ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: richText
     * 
     */
    private io.nop.excel.model.ExcelRichText _richText ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.commons.type.StdDataType _type ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.Object _value ;
    
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
     * xml name: formula
     *  
     */
    
    public java.lang.String getFormula(){
      return _formula;
    }

    
    public void setFormula(java.lang.String value){
        checkAllowChange();
        
        this._formula = value;
           
    }

    
    /**
     * 
     * xml name: mergeAcross
     *  向右合并的列数。mergeAcross + 1 == colSpan
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
    
    public io.nop.excel.model.XptCellModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.excel.model.XptCellModel value){
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
     * xml name: richText
     *  
     */
    
    public io.nop.excel.model.ExcelRichText getRichText(){
      return _richText;
    }

    
    public void setRichText(io.nop.excel.model.ExcelRichText value){
        checkAllowChange();
        
        this._richText = value;
           
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
     * xml name: type
     *  
     */
    
    public io.nop.commons.type.StdDataType getType(){
      return _type;
    }

    
    public void setType(io.nop.commons.type.StdDataType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public java.lang.Object getValue(){
      return _value;
    }

    
    public void setValue(java.lang.Object value){
        checkAllowChange();
        
        this._value = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
           this._richText = io.nop.api.core.util.FreezeHelper.deepFreeze(this._richText);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("comment",this.getComment());
        out.putNotNull("formula",this.getFormula());
        out.putNotNull("mergeAcross",this.getMergeAcross());
        out.putNotNull("mergeDown",this.getMergeDown());
        out.putNotNull("model",this.getModel());
        out.putNotNull("name",this.getName());
        out.putNotNull("richText",this.getRichText());
        out.putNotNull("styleId",this.getStyleId());
        out.putNotNull("type",this.getType());
        out.putNotNull("value",this.getValue());
    }

    public ExcelCell cloneInstance(){
        ExcelCell instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelCell instance){
        super.copyTo(instance);
        
        instance.setComment(this.getComment());
        instance.setFormula(this.getFormula());
        instance.setMergeAcross(this.getMergeAcross());
        instance.setMergeDown(this.getMergeDown());
        instance.setModel(this.getModel());
        instance.setName(this.getName());
        instance.setRichText(this.getRichText());
        instance.setStyleId(this.getStyleId());
        instance.setType(this.getType());
        instance.setValue(this.getValue());
    }

    protected ExcelCell newInstance(){
        return (ExcelCell) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
