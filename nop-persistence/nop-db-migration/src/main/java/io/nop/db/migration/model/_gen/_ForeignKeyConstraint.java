package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.ForeignKeyConstraint;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 外键约束
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ForeignKeyConstraint extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: columnNames
     * 本表列名列表
     */
    private java.util.Set<java.lang.String> _columnNames ;
    
    /**
     *  
     * xml name: name
     * 约束名
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: onDelete
     * 删除时的动作：CASCADE | SET_NULL | NO_ACTION | RESTRICT
     */
    private io.nop.db.migration.ForeignKeyAction _onDelete ;
    
    /**
     *  
     * xml name: onUpdate
     * 更新时的动作：CASCADE | SET_NULL | NO_ACTION | RESTRICT
     */
    private io.nop.db.migration.ForeignKeyAction _onUpdate ;
    
    /**
     *  
     * xml name: refColumnNames
     * 引用列名列表
     */
    private java.util.Set<java.lang.String> _refColumnNames ;
    
    /**
     *  
     * xml name: refSchemaName
     * 
     */
    private java.lang.String _refSchemaName ;
    
    /**
     *  
     * xml name: refTableName
     * 引用表名
     */
    private java.lang.String _refTableName ;
    
    /**
     * 
     * xml name: columnNames
     *  本表列名列表
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
     * xml name: name
     *  约束名
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
     * xml name: onDelete
     *  删除时的动作：CASCADE | SET_NULL | NO_ACTION | RESTRICT
     */
    
    public io.nop.db.migration.ForeignKeyAction getOnDelete(){
      return _onDelete;
    }

    
    public void setOnDelete(io.nop.db.migration.ForeignKeyAction value){
        checkAllowChange();
        
        this._onDelete = value;
           
    }

    
    /**
     * 
     * xml name: onUpdate
     *  更新时的动作：CASCADE | SET_NULL | NO_ACTION | RESTRICT
     */
    
    public io.nop.db.migration.ForeignKeyAction getOnUpdate(){
      return _onUpdate;
    }

    
    public void setOnUpdate(io.nop.db.migration.ForeignKeyAction value){
        checkAllowChange();
        
        this._onUpdate = value;
           
    }

    
    /**
     * 
     * xml name: refColumnNames
     *  引用列名列表
     */
    
    public java.util.Set<java.lang.String> getRefColumnNames(){
      return _refColumnNames;
    }

    
    public void setRefColumnNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._refColumnNames = value;
           
    }

    
    /**
     * 
     * xml name: refSchemaName
     *  
     */
    
    public java.lang.String getRefSchemaName(){
      return _refSchemaName;
    }

    
    public void setRefSchemaName(java.lang.String value){
        checkAllowChange();
        
        this._refSchemaName = value;
           
    }

    
    /**
     * 
     * xml name: refTableName
     *  引用表名
     */
    
    public java.lang.String getRefTableName(){
      return _refTableName;
    }

    
    public void setRefTableName(java.lang.String value){
        checkAllowChange();
        
        this._refTableName = value;
           
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
        out.putNotNull("name",this.getName());
        out.putNotNull("onDelete",this.getOnDelete());
        out.putNotNull("onUpdate",this.getOnUpdate());
        out.putNotNull("refColumnNames",this.getRefColumnNames());
        out.putNotNull("refSchemaName",this.getRefSchemaName());
        out.putNotNull("refTableName",this.getRefTableName());
    }

    public ForeignKeyConstraint cloneInstance(){
        ForeignKeyConstraint instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ForeignKeyConstraint instance){
        super.copyTo(instance);
        
        instance.setColumnNames(this.getColumnNames());
        instance.setName(this.getName());
        instance.setOnDelete(this.getOnDelete());
        instance.setOnUpdate(this.getOnUpdate());
        instance.setRefColumnNames(this.getRefColumnNames());
        instance.setRefSchemaName(this.getRefSchemaName());
        instance.setRefTableName(this.getRefTableName());
    }

    protected ForeignKeyConstraint newInstance(){
        return (ForeignKeyConstraint) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
