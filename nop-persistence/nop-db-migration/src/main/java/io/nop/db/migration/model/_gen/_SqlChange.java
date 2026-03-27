package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.SqlChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 执行自定义 SQL
 * 用于数据库特定的操作或复杂的数据迁移
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SqlChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: body
     * 标准SQL（与数据库无关）
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: dbSpecific
     * 数据库特定的SQL
     */
    private KeyedList<io.nop.db.migration.model.DbSpecificSql> _dbSpecific = KeyedList.emptyList();
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: splitStatements
     * 是否按分号分割多条SQL（默认 true）
     */
    private boolean _splitStatements  = true;
    
    /**
     *  
     * xml name: stripComments
     * 是否移除注释（默认 false）
     */
    private boolean _stripComments  = false;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: body
     *  标准SQL（与数据库无关）
     */
    
    public java.lang.String getBody(){
      return _body;
    }

    
    public void setBody(java.lang.String value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: dbSpecific
     *  数据库特定的SQL
     */
    
    public java.util.List<io.nop.db.migration.model.DbSpecificSql> getDbSpecific(){
      return _dbSpecific;
    }

    
    public void setDbSpecific(java.util.List<io.nop.db.migration.model.DbSpecificSql> value){
        checkAllowChange();
        
        this._dbSpecific = KeyedList.fromList(value, io.nop.db.migration.model.DbSpecificSql::getDbType);
           
    }

    
    public io.nop.db.migration.model.DbSpecificSql getSql(String name){
        return this._dbSpecific.getByKey(name);
    }

    public boolean hasSql(String name){
        return this._dbSpecific.containsKey(name);
    }

    public void addSql(io.nop.db.migration.model.DbSpecificSql item) {
        checkAllowChange();
        java.util.List<io.nop.db.migration.model.DbSpecificSql> list = this.getDbSpecific();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.db.migration.model.DbSpecificSql::getDbType);
            setDbSpecific(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_dbSpecific(){
        return this._dbSpecific.keySet();
    }

    public boolean hasDbSpecific(){
        return !this._dbSpecific.isEmpty();
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
     * xml name: splitStatements
     *  是否按分号分割多条SQL（默认 true）
     */
    
    public boolean isSplitStatements(){
      return _splitStatements;
    }

    
    public void setSplitStatements(boolean value){
        checkAllowChange();
        
        this._splitStatements = value;
           
    }

    
    /**
     * 
     * xml name: stripComments
     *  是否移除注释（默认 false）
     */
    
    public boolean isStripComments(){
      return _stripComments;
    }

    
    public void setStripComments(boolean value){
        checkAllowChange();
        
        this._stripComments = value;
           
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
        
           this._dbSpecific = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dbSpecific);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("dbSpecific",this.getDbSpecific());
        out.putNotNull("id",this.getId());
        out.putNotNull("splitStatements",this.isSplitStatements());
        out.putNotNull("stripComments",this.isStripComments());
        out.putNotNull("type",this.getType());
    }

    public SqlChange cloneInstance(){
        SqlChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SqlChange instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setDbSpecific(this.getDbSpecific());
        instance.setId(this.getId());
        instance.setSplitStatements(this.isSplitStatements());
        instance.setStripComments(this.isStripComments());
        instance.setType(this.getType());
    }

    protected SqlChange newInstance(){
        return (SqlChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
