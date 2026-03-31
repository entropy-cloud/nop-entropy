package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.ForeignKeyExistsPrecondition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 检查外键是否存在
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ForeignKeyExistsPrecondition extends io.nop.db.migration.model.DbPreconditionModel {
    
    /**
     *  
     * xml name: constraintName
     * 外键约束名
     */
    private java.lang.String _constraintName ;
    
    /**
     *  
     * xml name: expect
     * 期望结果
     */
    private io.nop.db.migration.PreconditionExpect _expect ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: schemaName
     * 模式名（可选）
     */
    private java.lang.String _schemaName ;
    
    /**
     *  
     * xml name: tableName
     * 表名（可选）
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
     * xml name: constraintName
     *  外键约束名
     */
    
    public java.lang.String getConstraintName(){
      return _constraintName;
    }

    
    public void setConstraintName(java.lang.String value){
        checkAllowChange();
        
        this._constraintName = value;
           
    }

    
    /**
     * 
     * xml name: expect
     *  期望结果
     */
    
    public io.nop.db.migration.PreconditionExpect getExpect(){
      return _expect;
    }

    
    public void setExpect(io.nop.db.migration.PreconditionExpect value){
        checkAllowChange();
        
        this._expect = value;
           
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
     *  表名（可选）
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
        
        out.putNotNull("constraintName",this.getConstraintName());
        out.putNotNull("expect",this.getExpect());
        out.putNotNull("id",this.getId());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("tableName",this.getTableName());
        out.putNotNull("type",this.getType());
    }

    public ForeignKeyExistsPrecondition cloneInstance(){
        ForeignKeyExistsPrecondition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ForeignKeyExistsPrecondition instance){
        super.copyTo(instance);
        
        instance.setConstraintName(this.getConstraintName());
        instance.setExpect(this.getExpect());
        instance.setId(this.getId());
        instance.setSchemaName(this.getSchemaName());
        instance.setTableName(this.getTableName());
        instance.setType(this.getType());
    }

    protected ForeignKeyExistsPrecondition newInstance(){
        return (ForeignKeyExistsPrecondition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
