package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.DbSpecificSql;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DbSpecificSql extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: body
     * 
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: dbType
     * 
     */
    private java.lang.String _dbType ;
    
    /**
     * 
     * xml name: body
     *  
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
     * xml name: dbType
     *  
     */
    
    public java.lang.String getDbType(){
      return _dbType;
    }

    
    public void setDbType(java.lang.String value){
        checkAllowChange();
        
        this._dbType = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("dbType",this.getDbType());
    }

    public DbSpecificSql cloneInstance(){
        DbSpecificSql instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DbSpecificSql instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setDbType(this.getDbType());
    }

    protected DbSpecificSql newInstance(){
        return (DbSpecificSql) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
