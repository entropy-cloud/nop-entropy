package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.DropTableChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 删除表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DropTableChange extends io.nop.db.migration.model.DbChangeModel {
    
    /**
     *  
     * xml name: cascadeConstraints
     * 是否级联删除约束（某些数据库支持）
     */
    private boolean _cascadeConstraints  = false;
    
    /**
     *  
     * xml name: name
     * 表名
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
     * xml name: cascadeConstraints
     *  是否级联删除约束（某些数据库支持）
     */
    
    public boolean isCascadeConstraints(){
      return _cascadeConstraints;
    }

    
    public void setCascadeConstraints(boolean value){
        checkAllowChange();
        
        this._cascadeConstraints = value;
           
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
        
        out.putNotNull("cascadeConstraints",this.isCascadeConstraints());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("type",this.getType());
    }

    public DropTableChange cloneInstance(){
        DropTableChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DropTableChange instance){
        super.copyTo(instance);
        
        instance.setCascadeConstraints(this.isCascadeConstraints());
        instance.setName(this.getName());
        instance.setSchemaName(this.getSchemaName());
    }

    protected DropTableChange newInstance(){
        return (DropTableChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
