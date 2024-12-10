package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchJdbcReaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchJdbcReaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fetchSize
     * 
     */
    private java.lang.Integer _fetchSize ;
    
    /**
     *  
     * xml name: maxFieldSize
     * 
     */
    private java.lang.Integer _maxFieldSize ;
    
    /**
     *  
     * xml name: maxRows
     * 
     */
    private java.lang.Long _maxRows ;
    
    /**
     *  
     * xml name: partitionIndexField
     * 
     */
    private java.lang.String _partitionIndexField ;
    
    /**
     *  
     * xml name: query
     * 
     */
    private io.nop.core.lang.xml.IXNodeGenerator _query ;
    
    /**
     *  
     * xml name: querySpace
     * 
     */
    private java.lang.String _querySpace ;
    
    /**
     *  
     * xml name: queryTimeout
     * 
     */
    private java.lang.Integer _queryTimeout ;
    
    /**
     *  
     * xml name: rowMapper
     * 
     */
    private java.lang.String _rowMapper ;
    
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
     * xml name: streaming
     * 
     */
    private boolean _streaming  = false;
    
    /**
     * 
     * xml name: fetchSize
     *  
     */
    
    public java.lang.Integer getFetchSize(){
      return _fetchSize;
    }

    
    public void setFetchSize(java.lang.Integer value){
        checkAllowChange();
        
        this._fetchSize = value;
           
    }

    
    /**
     * 
     * xml name: maxFieldSize
     *  
     */
    
    public java.lang.Integer getMaxFieldSize(){
      return _maxFieldSize;
    }

    
    public void setMaxFieldSize(java.lang.Integer value){
        checkAllowChange();
        
        this._maxFieldSize = value;
           
    }

    
    /**
     * 
     * xml name: maxRows
     *  
     */
    
    public java.lang.Long getMaxRows(){
      return _maxRows;
    }

    
    public void setMaxRows(java.lang.Long value){
        checkAllowChange();
        
        this._maxRows = value;
           
    }

    
    /**
     * 
     * xml name: partitionIndexField
     *  
     */
    
    public java.lang.String getPartitionIndexField(){
      return _partitionIndexField;
    }

    
    public void setPartitionIndexField(java.lang.String value){
        checkAllowChange();
        
        this._partitionIndexField = value;
           
    }

    
    /**
     * 
     * xml name: query
     *  
     */
    
    public io.nop.core.lang.xml.IXNodeGenerator getQuery(){
      return _query;
    }

    
    public void setQuery(io.nop.core.lang.xml.IXNodeGenerator value){
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
     * xml name: queryTimeout
     *  
     */
    
    public java.lang.Integer getQueryTimeout(){
      return _queryTimeout;
    }

    
    public void setQueryTimeout(java.lang.Integer value){
        checkAllowChange();
        
        this._queryTimeout = value;
           
    }

    
    /**
     * 
     * xml name: rowMapper
     *  
     */
    
    public java.lang.String getRowMapper(){
      return _rowMapper;
    }

    
    public void setRowMapper(java.lang.String value){
        checkAllowChange();
        
        this._rowMapper = value;
           
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

    
    /**
     * 
     * xml name: streaming
     *  
     */
    
    public boolean isStreaming(){
      return _streaming;
    }

    
    public void setStreaming(boolean value){
        checkAllowChange();
        
        this._streaming = value;
           
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
        
        out.putNotNull("fetchSize",this.getFetchSize());
        out.putNotNull("maxFieldSize",this.getMaxFieldSize());
        out.putNotNull("maxRows",this.getMaxRows());
        out.putNotNull("partitionIndexField",this.getPartitionIndexField());
        out.putNotNull("query",this.getQuery());
        out.putNotNull("querySpace",this.getQuerySpace());
        out.putNotNull("queryTimeout",this.getQueryTimeout());
        out.putNotNull("rowMapper",this.getRowMapper());
        out.putNotNull("sql",this.getSql());
        out.putNotNull("sqlName",this.getSqlName());
        out.putNotNull("streaming",this.isStreaming());
    }

    public BatchJdbcReaderModel cloneInstance(){
        BatchJdbcReaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchJdbcReaderModel instance){
        super.copyTo(instance);
        
        instance.setFetchSize(this.getFetchSize());
        instance.setMaxFieldSize(this.getMaxFieldSize());
        instance.setMaxRows(this.getMaxRows());
        instance.setPartitionIndexField(this.getPartitionIndexField());
        instance.setQuery(this.getQuery());
        instance.setQuerySpace(this.getQuerySpace());
        instance.setQueryTimeout(this.getQueryTimeout());
        instance.setRowMapper(this.getRowMapper());
        instance.setSql(this.getSql());
        instance.setSqlName(this.getSqlName());
        instance.setStreaming(this.isStreaming());
    }

    protected BatchJdbcReaderModel newInstance(){
        return (BatchJdbcReaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
