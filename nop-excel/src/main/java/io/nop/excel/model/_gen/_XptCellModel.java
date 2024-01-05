package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.XptCellModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [100:34:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XptCellModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: colExtendForSibling
     * 同一列的兄弟单元格展开时是否自动拉伸本单元格
     */
    private boolean _colExtendForSibling  = false;
    
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
     * 单元格展开时首先根据expandExpr计算得到expandedValue。如果没有指定expandExpr，但是指定了field和ds,
     * 在自动根据ds中的field数据进行分组汇总，返回分组汇总得到的结果。
     * 注意：展开表达式执行时还没有完成Excel层次坐标的构建，所以这里不能使用层次坐标表达式
     */
    private io.nop.core.lang.eval.IEvalAction _expandExpr ;
    
    /**
     *  
     * xml name: expandInplaceCount
     * 在模板中已经预留了几个展开单元格空间。如果展开表达式返回个数小于这个值，则不需要新增单元格。
     */
    private java.lang.Integer _expandInplaceCount ;
    
    /**
     *  
     * xml name: expandOrderBy
     * 对expandExpr返回的列表进行排序
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
     * xml name: exportFormattedValue
     * 如果设置为true，则导出Excel时会应用formatExpr或者Excel配置的NumberFormat，
     * 格式化为字符串后导出，而不是导出单元格的原始值。缺省情况下会导出原始值，使用Excel的NumberFormat来格式化
     */
    private boolean _exportFormattedValue  = false;
    
    /**
     *  
     * xml name: exportFormula
     * 导出到Excel时保持公式
     */
    private boolean _exportFormula  = false;
    
    /**
     *  
     * xml name: field
     * 
     */
    private java.lang.String _field ;
    
    /**
     *  
     * xml name: formatExpr
     * 在valueExpr执行后执行，计算得到用于显示的单元格文本
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
     * xml name: rowExtendForSibling
     * 同一行的兄弟单元格展开时是否自动拉伸本单元格
     */
    private boolean _rowExtendForSibling  = false;
    
    /**
     *  
     * xml name: rowParent
     * 
     */
    private io.nop.core.model.table.CellPosition _rowParent ;
    
    /**
     *  
     * xml name: rowTestExpr
     * 返回false的时候表示当前单元格所在的行需要被删除
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
     * 在单元格展开之后执行，可以通过层次坐标获取到相关联单元格
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
     * xml name: colExtendForSibling
     *  同一列的兄弟单元格展开时是否自动拉伸本单元格
     */
    
    public boolean isColExtendForSibling(){
      return _colExtendForSibling;
    }

    
    public void setColExtendForSibling(boolean value){
        checkAllowChange();
        
        this._colExtendForSibling = value;
           
    }

    
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
     *  单元格展开时首先根据expandExpr计算得到expandedValue。如果没有指定expandExpr，但是指定了field和ds,
     * 在自动根据ds中的field数据进行分组汇总，返回分组汇总得到的结果。
     * 注意：展开表达式执行时还没有完成Excel层次坐标的构建，所以这里不能使用层次坐标表达式
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
     *  在模板中已经预留了几个展开单元格空间。如果展开表达式返回个数小于这个值，则不需要新增单元格。
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
     *  对expandExpr返回的列表进行排序
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
     * xml name: exportFormattedValue
     *  如果设置为true，则导出Excel时会应用formatExpr或者Excel配置的NumberFormat，
     * 格式化为字符串后导出，而不是导出单元格的原始值。缺省情况下会导出原始值，使用Excel的NumberFormat来格式化
     */
    
    public boolean isExportFormattedValue(){
      return _exportFormattedValue;
    }

    
    public void setExportFormattedValue(boolean value){
        checkAllowChange();
        
        this._exportFormattedValue = value;
           
    }

    
    /**
     * 
     * xml name: exportFormula
     *  导出到Excel时保持公式
     */
    
    public boolean isExportFormula(){
      return _exportFormula;
    }

    
    public void setExportFormula(boolean value){
        checkAllowChange();
        
        this._exportFormula = value;
           
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
     *  在valueExpr执行后执行，计算得到用于显示的单元格文本
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
     * xml name: rowExtendForSibling
     *  同一行的兄弟单元格展开时是否自动拉伸本单元格
     */
    
    public boolean isRowExtendForSibling(){
      return _rowExtendForSibling;
    }

    
    public void setRowExtendForSibling(boolean value){
        checkAllowChange();
        
        this._rowExtendForSibling = value;
           
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
     *  返回false的时候表示当前单元格所在的行需要被删除
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
     *  在单元格展开之后执行，可以通过层次坐标获取到相关联单元格
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
        
        out.put("colExtendForSibling",this.isColExtendForSibling());
        out.put("colParent",this.getColParent());
        out.put("colTestExpr",this.getColTestExpr());
        out.put("domain",this.getDomain());
        out.put("ds",this.getDs());
        out.put("editorId",this.getEditorId());
        out.put("expandExpr",this.getExpandExpr());
        out.put("expandInplaceCount",this.getExpandInplaceCount());
        out.put("expandOrderBy",this.getExpandOrderBy());
        out.put("expandType",this.getExpandType());
        out.put("exportFormattedValue",this.isExportFormattedValue());
        out.put("exportFormula",this.isExportFormula());
        out.put("field",this.getField());
        out.put("formatExpr",this.getFormatExpr());
        out.put("keepExpandEmpty",this.isKeepExpandEmpty());
        out.put("linkExpr",this.getLinkExpr());
        out.put("rowExtendForSibling",this.isRowExtendForSibling());
        out.put("rowParent",this.getRowParent());
        out.put("rowTestExpr",this.getRowTestExpr());
        out.put("styleIdExpr",this.getStyleIdExpr());
        out.put("valueExpr",this.getValueExpr());
        out.put("viewerId",this.getViewerId());
    }

    public XptCellModel cloneInstance(){
        XptCellModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XptCellModel instance){
        super.copyTo(instance);
        
        instance.setColExtendForSibling(this.isColExtendForSibling());
        instance.setColParent(this.getColParent());
        instance.setColTestExpr(this.getColTestExpr());
        instance.setDomain(this.getDomain());
        instance.setDs(this.getDs());
        instance.setEditorId(this.getEditorId());
        instance.setExpandExpr(this.getExpandExpr());
        instance.setExpandInplaceCount(this.getExpandInplaceCount());
        instance.setExpandOrderBy(this.getExpandOrderBy());
        instance.setExpandType(this.getExpandType());
        instance.setExportFormattedValue(this.isExportFormattedValue());
        instance.setExportFormula(this.isExportFormula());
        instance.setField(this.getField());
        instance.setFormatExpr(this.getFormatExpr());
        instance.setKeepExpandEmpty(this.isKeepExpandEmpty());
        instance.setLinkExpr(this.getLinkExpr());
        instance.setRowExtendForSibling(this.isRowExtendForSibling());
        instance.setRowParent(this.getRowParent());
        instance.setRowTestExpr(this.getRowTestExpr());
        instance.setStyleIdExpr(this.getStyleIdExpr());
        instance.setValueExpr(this.getValueExpr());
        instance.setViewerId(this.getViewerId());
    }

    protected XptCellModel newInstance(){
        return (XptCellModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
