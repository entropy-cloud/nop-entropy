package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.CreateTableChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 创建表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CreateTableChange extends io.nop.db.migration.model.DbChangeModel {
    
    /**
     *  
     * xml name: columns
     * 列定义
     */
    private KeyedList<io.nop.db.migration.model.ColumnDefinition> _columns = KeyedList.emptyList();
    
    /**
     *  
     * xml name: foreignKey
     * 外键约束
     */
    private io.nop.db.migration.model.ForeignKeyConstraint _foreignKey ;
    
    /**
     *  
     * xml name: name
     * 表名
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: primaryKey
     * 主键约束（可选，也可以在列上直接指定 primaryKey）
     */
    private io.nop.db.migration.model.PrimaryKeyConstraint _primaryKey ;
    
    /**
     *  
     * xml name: remark
     * 表注释
     */
    private java.lang.String _remark ;
    
    /**
     *  
     * xml name: schemaName
     * 模式名（可选）
     */
    private java.lang.String _schemaName ;
    
    /**
     *  
     * xml name: uniqueConstraint
     * 唯一约束
     */
    private io.nop.db.migration.model.UniqueConstraint _uniqueConstraint ;
    
    /**
     * 
     * xml name: columns
     *  列定义
     */
    
    public java.util.List<io.nop.db.migration.model.ColumnDefinition> getColumns(){
      return _columns;
    }

    
    public void setColumns(java.util.List<io.nop.db.migration.model.ColumnDefinition> value){
        checkAllowChange();
        
        this._columns = KeyedList.fromList(value, io.nop.db.migration.model.ColumnDefinition::getName);
           
    }

    
    public io.nop.db.migration.model.ColumnDefinition getColumn(String name){
        return this._columns.getByKey(name);
    }

    public boolean hasColumn(String name){
        return this._columns.containsKey(name);
    }

    public void addColumn(io.nop.db.migration.model.ColumnDefinition item) {
        checkAllowChange();
        java.util.List<io.nop.db.migration.model.ColumnDefinition> list = this.getColumns();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.db.migration.model.ColumnDefinition::getName);
            setColumns(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_columns(){
        return this._columns.keySet();
    }

    public boolean hasColumns(){
        return !this._columns.isEmpty();
    }
    
    /**
     * 
     * xml name: foreignKey
     *  外键约束
     */
    
    public io.nop.db.migration.model.ForeignKeyConstraint getForeignKey(){
      return _foreignKey;
    }

    
    public void setForeignKey(io.nop.db.migration.model.ForeignKeyConstraint value){
        checkAllowChange();
        
        this._foreignKey = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  表名
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
     * xml name: primaryKey
     *  主键约束（可选，也可以在列上直接指定 primaryKey）
     */
    
    public io.nop.db.migration.model.PrimaryKeyConstraint getPrimaryKey(){
      return _primaryKey;
    }

    
    public void setPrimaryKey(io.nop.db.migration.model.PrimaryKeyConstraint value){
        checkAllowChange();
        
        this._primaryKey = value;
           
    }

    
    /**
     * 
     * xml name: remark
     *  表注释
     */
    
    public java.lang.String getRemark(){
      return _remark;
    }

    
    public void setRemark(java.lang.String value){
        checkAllowChange();
        
        this._remark = value;
           
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
     * xml name: uniqueConstraint
     *  唯一约束
     */
    
    public io.nop.db.migration.model.UniqueConstraint getUniqueConstraint(){
      return _uniqueConstraint;
    }

    
    public void setUniqueConstraint(io.nop.db.migration.model.UniqueConstraint value){
        checkAllowChange();
        
        this._uniqueConstraint = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._columns = io.nop.api.core.util.FreezeHelper.deepFreeze(this._columns);
            
           this._foreignKey = io.nop.api.core.util.FreezeHelper.deepFreeze(this._foreignKey);
            
           this._primaryKey = io.nop.api.core.util.FreezeHelper.deepFreeze(this._primaryKey);
            
           this._uniqueConstraint = io.nop.api.core.util.FreezeHelper.deepFreeze(this._uniqueConstraint);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("columns",this.getColumns());
        out.putNotNull("foreignKey",this.getForeignKey());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
        out.putNotNull("primaryKey",this.getPrimaryKey());
        out.putNotNull("remark",this.getRemark());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("type",this.getType());
        out.putNotNull("uniqueConstraint",this.getUniqueConstraint());
    }

    public CreateTableChange cloneInstance(){
        CreateTableChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CreateTableChange instance){
        super.copyTo(instance);
        
        instance.setColumns(this.getColumns());
        instance.setForeignKey(this.getForeignKey());
        instance.setName(this.getName());
        instance.setPrimaryKey(this.getPrimaryKey());
        instance.setRemark(this.getRemark());
        instance.setSchemaName(this.getSchemaName());
        instance.setUniqueConstraint(this.getUniqueConstraint());
    }

    protected CreateTableChange newInstance(){
        return (CreateTableChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
