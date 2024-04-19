package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchJdbcReaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [52:10:0:0]/nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchJdbcReaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: query
     * 
     */
    private io.nop.api.core.beans.query.QueryBean _query ;
    
    /**
     *  
     * xml name: querySpace
     * 
     */
    private java.lang.String _querySpace ;
    
    /**
     *  
     * xml name: sql
     * 
     */
    private io.nop.core.lang.sql.ISqlGenerator _sql ;
    
    /**
     *  
     * xml name: sqlName
     * 
     */
    private java.lang.String _sqlName ;
    
    /**
     * 
     * xml name: query
     *  
     */
    
    public io.nop.api.core.beans.query.QueryBean getQuery(){
      return _query;
    }

    
    public void setQuery(io.nop.api.core.beans.query.QueryBean value){
        checkAllowChange();
        
        this._query = value;
           
    }

    
    /**
     * 
     * xml name: querySpace
     *  
     */
    
    public java.lang.String getQuerySpace(){
      return _querySpace;
    }

    
    public void setQuerySpace(java.lang.String value){
        checkAllowChange();
        
        this._querySpace = value;
           
    }

    
    /**
     * 
     * xml name: sql
     *  
     */
    
    public io.nop.core.lang.sql.ISqlGenerator getSql(){
      return _sql;
    }

    
    public void setSql(io.nop.core.lang.sql.ISqlGenerator value){
        checkAllowChange();
        
        this._sql = value;
           
    }

    
    /**
     * 
     * xml name: sqlName
     *  
     */
    
    public java.lang.String getSqlName(){
      return _sqlName;
    }

    
    public void setSqlName(java.lang.String value){
        checkAllowChange();
        
        this._sqlName = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._query = io.nop.api.core.util.FreezeHelper.deepFreeze(this._query);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("query",this.getQuery());
        out.putNotNull("querySpace",this.getQuerySpace());
        out.putNotNull("sql",this.getSql());
        out.putNotNull("sqlName",this.getSqlName());
    }

    public BatchJdbcReaderModel cloneInstance(){
        BatchJdbcReaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchJdbcReaderModel instance){
        super.copyTo(instance);
        
        instance.setQuery(this.getQuery());
        instance.setQuerySpace(this.getQuerySpace());
        instance.setSql(this.getSql());
        instance.setSqlName(this.getSqlName());
    }

    protected BatchJdbcReaderModel newInstance(){
        return (BatchJdbcReaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
