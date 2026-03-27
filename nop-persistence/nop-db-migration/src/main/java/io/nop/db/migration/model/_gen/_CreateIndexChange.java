package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.CreateIndexChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 创建索引
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CreateIndexChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: columnNames
     * 列名列表（逗号分隔）
     */
    private java.util.Set<java.lang.String> _columnNames ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: name
     * 索引名
     */
    private java.lang.String _name ;
    
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
     * xml name: unique
     * 是否为唯一索引
     */
    private boolean _unique  = false;
    
    /**
     * 
     * xml name: columnNames
     *  列名列表（逗号分隔）
     */
    
    public java.util.Set<java.lang.String> getColumnNames(){
      return _columnNames;
    }

    
    public void setColumnNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._columnNames = value;
           
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
     * xml name: name
     *  索引名
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

    
    /**
     * 
     * xml name: unique
     *  是否为唯一索引
     */
    
    public boolean isUnique(){
      return _unique;
    }

    
    public void setUnique(boolean value){
        checkAllowChange();
        
        this._unique = value;
           
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
        
        out.putNotNull("columnNames",this.getColumnNames());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("tableName",this.getTableName());
        out.putNotNull("type",this.getType());
        out.putNotNull("unique",this.isUnique());
    }

    public CreateIndexChange cloneInstance(){
        CreateIndexChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CreateIndexChange instance){
        super.copyTo(instance);
        
        instance.setColumnNames(this.getColumnNames());
        instance.setId(this.getId());
        instance.setName(this.getName());
        instance.setSchemaName(this.getSchemaName());
        instance.setTableName(this.getTableName());
        instance.setType(this.getType());
        instance.setUnique(this.isUnique());
    }

    protected CreateIndexChange newInstance(){
        return (CreateIndexChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
