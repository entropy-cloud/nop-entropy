package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [93:34:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _XptCellModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: colParent
     * 
     */
    private io.nop.core.model.table.CellPosition _colParent ;
    
    /**
     *  
     * xml name: colTestExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _colTestExpr ;
    
    /**
     *  
     * xml name: domain
     * 
     */
    private java.lang.String _domain ;
    
    /**
     *  
     * xml name: ds
     * 
     */
    private java.lang.String _ds ;
    
    /**
     *  
     * xml name: editorId
     * 
     */
    private java.lang.String _editorId ;
    
    /**
     *  
     * xml name: expandExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _expandExpr ;
    
    /**
     *  
     * xml name: expandInplaceCount
     * 单元格展开时
     */
    private java.lang.Integer _expandInplaceCount ;
    
    /**
     *  
     * xml name: expandOrderBy
     * 
     */
    private java.util.List<io.nop.api.core.beans.query.OrderFieldBean> _expandOrderBy ;
    
    /**
     *  
     * xml name: expandType
     * 
     */
    private io.nop.excel.model.constants.XptExpandType _expandType ;
    
    /**
     *  
     * xml name: field
     * 
     */
    private java.lang.String _field ;
    
    /**
     *  
     * xml name: formatExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _formatExpr ;
    
    /**
     *  
     * xml name: keepExpandEmpty
     * 当展开集合为空时，缺省会删除模板中定义的单元格以及它所在的行或者列。
     * 但是如果keepExpandEmpty为true，则只是清除当前单元格以及所有子单元格的内容，但是并不自动删除。
     */
    private boolean _keepExpandEmpty  = false;
    
    /**
     *  
     * xml name: linkExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _linkExpr ;
    
    /**
     *  
     * xml name: rowParent
     * 
     */
    private io.nop.core.model.table.CellPosition _rowParent ;
    
    /**
     *  
     * xml name: rowTestExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _rowTestExpr ;
    
    /**
     *  
     * xml name: styleIdExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _styleIdExpr ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _valueExpr ;
    
    /**
     *  
     * xml name: viewerId
     * 
     */
    private java.lang.String _viewerId ;
    
    /**
     * 
     * xml name: colParent
     *  
     */
    
    public io.nop.core.model.table.CellPosition getColParent(){
      return _colParent;
    }

    
    public void setColParent(io.nop.core.model.table.CellPosition value){
        checkAllowChange();
        
        this._colParent = value;
           
    }

    
    /**
     * 
     * xml name: colTestExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getColTestExpr(){
      return _colTestExpr;
    }

    
    public void setColTestExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._colTestExpr = value;
           
    }

    
    /**
     * 
     * xml name: domain
     *  
     */
    
    public java.lang.String getDomain(){
      return _domain;
    }

    
    public void setDomain(java.lang.String value){
        checkAllowChange();
        
        this._domain = value;
           
    }

    
    /**
     * 
     * xml name: ds
     *  
     */
    
    public java.lang.String getDs(){
      return _ds;
    }

    
    public void setDs(java.lang.String value){
        checkAllowChange();
        
        this._ds = value;
           
    }

    
    /**
     * 
     * xml name: editorId
     *  
     */
    
    public java.lang.String getEditorId(){
      return _editorId;
    }

    
    public void setEditorId(java.lang.String value){
        checkAllowChange();
        
        this._editorId = value;
           
    }

    
    /**
     * 
     * xml name: expandExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getExpandExpr(){
      return _expandExpr;
    }

    
    public void setExpandExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._expandExpr = value;
           
    }

    
    /**
     * 
     * xml name: expandInplaceCount
     *  单元格展开时
     */
    
    public java.lang.Integer getExpandInplaceCount(){
      return _expandInplaceCount;
    }

    
    public void setExpandInplaceCount(java.lang.Integer value){
        checkAllowChange();
        
        this._expandInplaceCount = value;
           
    }

    
    /**
     * 
     * xml name: expandOrderBy
     *  
     */
    
    public java.util.List<io.nop.api.core.beans.query.OrderFieldBean> getExpandOrderBy(){
      return _expandOrderBy;
    }

    
    public void setExpandOrderBy(java.util.List<io.nop.api.core.beans.query.OrderFieldBean> value){
        checkAllowChange();
        
        this._expandOrderBy = value;
           
    }

    
    /**
     * 
     * xml name: expandType
     *  
     */
    
    public io.nop.excel.model.constants.XptExpandType getExpandType(){
      return _expandType;
    }

    
    public void setExpandType(io.nop.excel.model.constants.XptExpandType value){
        checkAllowChange();
        
        this._expandType = value;
           
    }

    
    /**
     * 
     * xml name: field
     *  
     */
    
    public java.lang.String getField(){
      return _field;
    }

    
    public void setField(java.lang.String value){
        checkAllowChange();
        
        this._field = value;
           
    }

    
    /**
     * 
     * xml name: formatExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getFormatExpr(){
      return _formatExpr;
    }

    
    public void setFormatExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._formatExpr = value;
           
    }

    
    /**
     * 
     * xml name: keepExpandEmpty
     *  当展开集合为空时，缺省会删除模板中定义的单元格以及它所在的行或者列。
     * 但是如果keepExpandEmpty为true，则只是清除当前单元格以及所有子单元格的内容，但是并不自动删除。
     */
    
    public boolean isKeepExpandEmpty(){
      return _keepExpandEmpty;
    }

    
    public void setKeepExpandEmpty(boolean value){
        checkAllowChange();
        
        this._keepExpandEmpty = value;
           
    }

    
    /**
     * 
     * xml name: linkExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getLinkExpr(){
      return _linkExpr;
    }

    
    public void setLinkExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._linkExpr = value;
           
    }

    
    /**
     * 
     * xml name: rowParent
     *  
     */
    
    public io.nop.core.model.table.CellPosition getRowParent(){
      return _rowParent;
    }

    
    public void setRowParent(io.nop.core.model.table.CellPosition value){
        checkAllowChange();
        
        this._rowParent = value;
           
    }

    
    /**
     * 
     * xml name: rowTestExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getRowTestExpr(){
      return _rowTestExpr;
    }

    
    public void setRowTestExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._rowTestExpr = value;
           
    }

    
    /**
     * 
     * xml name: styleIdExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getStyleIdExpr(){
      return _styleIdExpr;
    }

    
    public void setStyleIdExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._styleIdExpr = value;
           
    }

    
    /**
     * 
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
    }

    
    /**
     * 
     * xml name: viewerId
     *  
     */
    
    public java.lang.String getViewerId(){
      return _viewerId;
    }

    
    public void setViewerId(java.lang.String value){
        checkAllowChange();
        
        this._viewerId = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("colParent",this.getColParent());
        out.put("colTestExpr",this.getColTestExpr());
        out.put("domain",this.getDomain());
        out.put("ds",this.getDs());
        out.put("editorId",this.getEditorId());
        out.put("expandExpr",this.getExpandExpr());
        out.put("expandInplaceCount",this.getExpandInplaceCount());
        out.put("expandOrderBy",this.getExpandOrderBy());
        out.put("expandType",this.getExpandType());
        out.put("field",this.getField());
        out.put("formatExpr",this.getFormatExpr());
        out.put("keepExpandEmpty",this.isKeepExpandEmpty());
        out.put("linkExpr",this.getLinkExpr());
        out.put("rowParent",this.getRowParent());
        out.put("rowTestExpr",this.getRowTestExpr());
        out.put("styleIdExpr",this.getStyleIdExpr());
        out.put("valueExpr",this.getValueExpr());
        out.put("viewerId",this.getViewerId());
    }
}
 // resume CPD analysis - CPD-ON
