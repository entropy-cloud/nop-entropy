package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelSheetProtection;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/sheet-protection.xdef <p>
 * Excel 工作表保护配置
 * 启用后，结合单元格的 locked 属性实现编辑控制
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelSheetProtection extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: autoFilter
     * 是否允许使用自动筛选
     */
    private java.lang.Boolean _autoFilter ;
    
    /**
     *  
     * xml name: deleteColumns
     * 是否允许删除列
     */
    private java.lang.Boolean _deleteColumns ;
    
    /**
     *  
     * xml name: deleteRows
     * 是否允许删除行
     */
    private java.lang.Boolean _deleteRows ;
    
    /**
     *  
     * xml name: enabled
     * 是否启用工作表保护（true=启用，false=禁用）
     */
    private java.lang.Boolean _enabled ;
    
    /**
     *  
     * xml name: formatCells
     * 是否允许修改单元格格式
     */
    private java.lang.Boolean _formatCells ;
    
    /**
     *  
     * xml name: formatColumns
     * 是否允许调整列宽和列格式
     */
    private java.lang.Boolean _formatColumns ;
    
    /**
     *  
     * xml name: formatRows
     * 是否允许调整行高和行格式
     */
    private java.lang.Boolean _formatRows ;
    
    /**
     *  
     * xml name: insertColumns
     * 是否允许插入新列
     */
    private java.lang.Boolean _insertColumns ;
    
    /**
     *  
     * xml name: insertHyperlinks
     * 是否允许插入超链接
     */
    private java.lang.Boolean _insertHyperlinks ;
    
    /**
     *  
     * xml name: insertRows
     * 是否允许插入新行
     */
    private java.lang.Boolean _insertRows ;
    
    /**
     *  
     * xml name: objects
     * 是否允许操作图形对象（如图片、形状等）
     */
    private java.lang.Boolean _objects ;
    
    /**
     *  
     * xml name: password
     * 保护密码的哈希值（16进制字符串）
     */
    private java.lang.String _password ;
    
    /**
     *  
     * xml name: pivotTables
     * 是否允许修改数据透视表
     */
    private java.lang.Boolean _pivotTables ;
    
    /**
     *  
     * xml name: scenarios
     * 是否允许编辑方案
     * 注意：所有权限属性默认为 true（禁止操作）。当属性值为 false 时，表示允许该操作
     * 例如：formatCells="false" 表示允许用户修改单元格格式
     */
    private java.lang.Boolean _scenarios ;
    
    /**
     *  
     * xml name: selectLockedCells
     * 是否允许选择已锁定的单元格
     */
    private java.lang.Boolean _selectLockedCells ;
    
    /**
     *  
     * xml name: selectUnlockedCells
     * 是否允许选择未锁定的单元格
     */
    private java.lang.Boolean _selectUnlockedCells ;
    
    /**
     *  
     * xml name: sort
     * 是否允许排序操作
     */
    private java.lang.Boolean _sort ;
    
    /**
     * 
     * xml name: autoFilter
     *  是否允许使用自动筛选
     */
    
    public java.lang.Boolean getAutoFilter(){
      return _autoFilter;
    }

    
    public void setAutoFilter(java.lang.Boolean value){
        checkAllowChange();
        
        this._autoFilter = value;
           
    }

    
    /**
     * 
     * xml name: deleteColumns
     *  是否允许删除列
     */
    
    public java.lang.Boolean getDeleteColumns(){
      return _deleteColumns;
    }

    
    public void setDeleteColumns(java.lang.Boolean value){
        checkAllowChange();
        
        this._deleteColumns = value;
           
    }

    
    /**
     * 
     * xml name: deleteRows
     *  是否允许删除行
     */
    
    public java.lang.Boolean getDeleteRows(){
      return _deleteRows;
    }

    
    public void setDeleteRows(java.lang.Boolean value){
        checkAllowChange();
        
        this._deleteRows = value;
           
    }

    
    /**
     * 
     * xml name: enabled
     *  是否启用工作表保护（true=启用，false=禁用）
     */
    
    public java.lang.Boolean getEnabled(){
      return _enabled;
    }

    
    public void setEnabled(java.lang.Boolean value){
        checkAllowChange();
        
        this._enabled = value;
           
    }

    
    /**
     * 
     * xml name: formatCells
     *  是否允许修改单元格格式
     */
    
    public java.lang.Boolean getFormatCells(){
      return _formatCells;
    }

    
    public void setFormatCells(java.lang.Boolean value){
        checkAllowChange();
        
        this._formatCells = value;
           
    }

    
    /**
     * 
     * xml name: formatColumns
     *  是否允许调整列宽和列格式
     */
    
    public java.lang.Boolean getFormatColumns(){
      return _formatColumns;
    }

    
    public void setFormatColumns(java.lang.Boolean value){
        checkAllowChange();
        
        this._formatColumns = value;
           
    }

    
    /**
     * 
     * xml name: formatRows
     *  是否允许调整行高和行格式
     */
    
    public java.lang.Boolean getFormatRows(){
      return _formatRows;
    }

    
    public void setFormatRows(java.lang.Boolean value){
        checkAllowChange();
        
        this._formatRows = value;
           
    }

    
    /**
     * 
     * xml name: insertColumns
     *  是否允许插入新列
     */
    
    public java.lang.Boolean getInsertColumns(){
      return _insertColumns;
    }

    
    public void setInsertColumns(java.lang.Boolean value){
        checkAllowChange();
        
        this._insertColumns = value;
           
    }

    
    /**
     * 
     * xml name: insertHyperlinks
     *  是否允许插入超链接
     */
    
    public java.lang.Boolean getInsertHyperlinks(){
      return _insertHyperlinks;
    }

    
    public void setInsertHyperlinks(java.lang.Boolean value){
        checkAllowChange();
        
        this._insertHyperlinks = value;
           
    }

    
    /**
     * 
     * xml name: insertRows
     *  是否允许插入新行
     */
    
    public java.lang.Boolean getInsertRows(){
      return _insertRows;
    }

    
    public void setInsertRows(java.lang.Boolean value){
        checkAllowChange();
        
        this._insertRows = value;
           
    }

    
    /**
     * 
     * xml name: objects
     *  是否允许操作图形对象（如图片、形状等）
     */
    
    public java.lang.Boolean getObjects(){
      return _objects;
    }

    
    public void setObjects(java.lang.Boolean value){
        checkAllowChange();
        
        this._objects = value;
           
    }

    
    /**
     * 
     * xml name: password
     *  保护密码的哈希值（16进制字符串）
     */
    
    public java.lang.String getPassword(){
      return _password;
    }

    
    public void setPassword(java.lang.String value){
        checkAllowChange();
        
        this._password = value;
           
    }

    
    /**
     * 
     * xml name: pivotTables
     *  是否允许修改数据透视表
     */
    
    public java.lang.Boolean getPivotTables(){
      return _pivotTables;
    }

    
    public void setPivotTables(java.lang.Boolean value){
        checkAllowChange();
        
        this._pivotTables = value;
           
    }

    
    /**
     * 
     * xml name: scenarios
     *  是否允许编辑方案
     * 注意：所有权限属性默认为 true（禁止操作）。当属性值为 false 时，表示允许该操作
     * 例如：formatCells="false" 表示允许用户修改单元格格式
     */
    
    public java.lang.Boolean getScenarios(){
      return _scenarios;
    }

    
    public void setScenarios(java.lang.Boolean value){
        checkAllowChange();
        
        this._scenarios = value;
           
    }

    
    /**
     * 
     * xml name: selectLockedCells
     *  是否允许选择已锁定的单元格
     */
    
    public java.lang.Boolean getSelectLockedCells(){
      return _selectLockedCells;
    }

    
    public void setSelectLockedCells(java.lang.Boolean value){
        checkAllowChange();
        
        this._selectLockedCells = value;
           
    }

    
    /**
     * 
     * xml name: selectUnlockedCells
     *  是否允许选择未锁定的单元格
     */
    
    public java.lang.Boolean getSelectUnlockedCells(){
      return _selectUnlockedCells;
    }

    
    public void setSelectUnlockedCells(java.lang.Boolean value){
        checkAllowChange();
        
        this._selectUnlockedCells = value;
           
    }

    
    /**
     * 
     * xml name: sort
     *  是否允许排序操作
     */
    
    public java.lang.Boolean getSort(){
      return _sort;
    }

    
    public void setSort(java.lang.Boolean value){
        checkAllowChange();
        
        this._sort = value;
           
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
        
        out.putNotNull("autoFilter",this.getAutoFilter());
        out.putNotNull("deleteColumns",this.getDeleteColumns());
        out.putNotNull("deleteRows",this.getDeleteRows());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("formatCells",this.getFormatCells());
        out.putNotNull("formatColumns",this.getFormatColumns());
        out.putNotNull("formatRows",this.getFormatRows());
        out.putNotNull("insertColumns",this.getInsertColumns());
        out.putNotNull("insertHyperlinks",this.getInsertHyperlinks());
        out.putNotNull("insertRows",this.getInsertRows());
        out.putNotNull("objects",this.getObjects());
        out.putNotNull("password",this.getPassword());
        out.putNotNull("pivotTables",this.getPivotTables());
        out.putNotNull("scenarios",this.getScenarios());
        out.putNotNull("selectLockedCells",this.getSelectLockedCells());
        out.putNotNull("selectUnlockedCells",this.getSelectUnlockedCells());
        out.putNotNull("sort",this.getSort());
    }

    public ExcelSheetProtection cloneInstance(){
        ExcelSheetProtection instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelSheetProtection instance){
        super.copyTo(instance);
        
        instance.setAutoFilter(this.getAutoFilter());
        instance.setDeleteColumns(this.getDeleteColumns());
        instance.setDeleteRows(this.getDeleteRows());
        instance.setEnabled(this.getEnabled());
        instance.setFormatCells(this.getFormatCells());
        instance.setFormatColumns(this.getFormatColumns());
        instance.setFormatRows(this.getFormatRows());
        instance.setInsertColumns(this.getInsertColumns());
        instance.setInsertHyperlinks(this.getInsertHyperlinks());
        instance.setInsertRows(this.getInsertRows());
        instance.setObjects(this.getObjects());
        instance.setPassword(this.getPassword());
        instance.setPivotTables(this.getPivotTables());
        instance.setScenarios(this.getScenarios());
        instance.setSelectLockedCells(this.getSelectLockedCells());
        instance.setSelectUnlockedCells(this.getSelectUnlockedCells());
        instance.setSort(this.getSort());
    }

    protected ExcelSheetProtection newInstance(){
        return (ExcelSheetProtection) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
