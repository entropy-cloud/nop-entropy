package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.AlterColumnChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 修改列
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AlterColumnChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: columnName
     * 列名
     */
    private java.lang.String _columnName ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: newColumnName
     * 新列名（重命名时使用）
     */
    private java.lang.String _newColumnName ;
    
    /**
     *  
     * xml name: newDecimalDigits
     * 新小数位数
     */
    private java.lang.Integer _newDecimalDigits ;
    
    /**
     *  
     * xml name: newDefaultValue
     * 新的默认值
     */
    private java.lang.String _newDefaultValue ;
    
    /**
     *  
     * xml name: newNullable
     * 新的 nullable 设置
     */
    private java.lang.Boolean _newNullable ;
    
    /**
     *  
     * xml name: newRemark
     * 新的列注释
     */
    private java.lang.String _newRemark ;
    
    /**
     *  
     * xml name: newSize
     * 新大小
     */
    private java.lang.Integer _newSize ;
    
    /**
     *  
     * xml name: newType
     * 新类型
     */
    private io.nop.commons.type.StdSqlType _newType ;
    
    /**
     *  
     * xml name: schemaName
     * 模式名（可选）
     */
    private java.lang.String _schemaName ;
    
    /**
     *  
     * xml name: tableName
     * 表名
     */
    private java.lang.String _tableName ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: columnName
     *  列名
     */
    
    public java.lang.String getColumnName(){
      return _columnName;
    }

    
    public void setColumnName(java.lang.String value){
        checkAllowChange();
        
        this._columnName = value;
           
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
     * xml name: newColumnName
     *  新列名（重命名时使用）
     */
    
    public java.lang.String getNewColumnName(){
      return _newColumnName;
    }

    
    public void setNewColumnName(java.lang.String value){
        checkAllowChange();
        
        this._newColumnName = value;
           
    }

    
    /**
     * 
     * xml name: newDecimalDigits
     *  新小数位数
     */
    
    public java.lang.Integer getNewDecimalDigits(){
      return _newDecimalDigits;
    }

    
    public void setNewDecimalDigits(java.lang.Integer value){
        checkAllowChange();
        
        this._newDecimalDigits = value;
           
    }

    
    /**
     * 
     * xml name: newDefaultValue
     *  新的默认值
     */
    
    public java.lang.String getNewDefaultValue(){
      return _newDefaultValue;
    }

    
    public void setNewDefaultValue(java.lang.String value){
        checkAllowChange();
        
        this._newDefaultValue = value;
           
    }

    
    /**
     * 
     * xml name: newNullable
     *  新的 nullable 设置
     */
    
    public java.lang.Boolean getNewNullable(){
      return _newNullable;
    }

    
    public void setNewNullable(java.lang.Boolean value){
        checkAllowChange();
        
        this._newNullable = value;
           
    }

    
    /**
     * 
     * xml name: newRemark
     *  新的列注释
     */
    
    public java.lang.String getNewRemark(){
      return _newRemark;
    }

    
    public void setNewRemark(java.lang.String value){
        checkAllowChange();
        
        this._newRemark = value;
           
    }

    
    /**
     * 
     * xml name: newSize
     *  新大小
     */
    
    public java.lang.Integer getNewSize(){
      return _newSize;
    }

    
    public void setNewSize(java.lang.Integer value){
        checkAllowChange();
        
        this._newSize = value;
           
    }

    
    /**
     * 
     * xml name: newType
     *  新类型
     */
    
    public io.nop.commons.type.StdSqlType getNewType(){
      return _newType;
    }

    
    public void setNewType(io.nop.commons.type.StdSqlType value){
        checkAllowChange();
        
        this._newType = value;
           
    }

    
    /**
     * 
     * xml name: schemaName
     *  模式名（可选）
     */
    
    public java.lang.String getSchemaName(){
      return _schemaName;
    }

    
    public void setSchemaName(java.lang.String value){
        checkAllowChange();
        
        this._schemaName = value;
           
    }

    
    /**
     * 
     * xml name: tableName
     *  表名
     */
    
    public java.lang.String getTableName(){
      return _tableName;
    }

    
    public void setTableName(java.lang.String value){
        checkAllowChange();
        
        this._tableName = value;
           
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
        
        out.putNotNull("columnName",this.getColumnName());
        out.putNotNull("id",this.getId());
        out.putNotNull("newColumnName",this.getNewColumnName());
        out.putNotNull("newDecimalDigits",this.getNewDecimalDigits());
        out.putNotNull("newDefaultValue",this.getNewDefaultValue());
        out.putNotNull("newNullable",this.getNewNullable());
        out.putNotNull("newRemark",this.getNewRemark());
        out.putNotNull("newSize",this.getNewSize());
        out.putNotNull("newType",this.getNewType());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("tableName",this.getTableName());
        out.putNotNull("type",this.getType());
    }

    public AlterColumnChange cloneInstance(){
        AlterColumnChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AlterColumnChange instance){
        super.copyTo(instance);
        
        instance.setColumnName(this.getColumnName());
        instance.setId(this.getId());
        instance.setNewColumnName(this.getNewColumnName());
        instance.setNewDecimalDigits(this.getNewDecimalDigits());
        instance.setNewDefaultValue(this.getNewDefaultValue());
        instance.setNewNullable(this.getNewNullable());
        instance.setNewRemark(this.getNewRemark());
        instance.setNewSize(this.getNewSize());
        instance.setNewType(this.getNewType());
        instance.setSchemaName(this.getSchemaName());
        instance.setTableName(this.getTableName());
        instance.setType(this.getType());
    }

    protected AlterColumnChange newInstance(){
        return (AlterColumnChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
