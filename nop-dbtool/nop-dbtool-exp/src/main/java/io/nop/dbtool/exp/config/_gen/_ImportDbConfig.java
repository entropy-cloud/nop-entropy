package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ImportDbConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/import-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ImportDbConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: batchSize
     * 
     */
    private int _batchSize  = 0;
    
    /**
     *  
     * xml name: excludeTableNames
     * 
     */
    private java.util.Set<java.lang.String> _excludeTableNames ;
    
    /**
     *  
     * xml name: ignoreDuplicate
     * 
     */
    private boolean _ignoreDuplicate  = true;
    
    /**
     *  
     * xml name: importAllTables
     * 
     */
    private boolean _importAllTables  = true;
    
    /**
     *  
     * xml name: inputDir
     * 
     */
    private java.lang.String _inputDir ;
    
    /**
     *  
     * xml name: jdbc-connection
     * 
     */
    private io.nop.dbtool.exp.config.JdbcConnectionConfig _jdbcConnection ;
    
    /**
     *  
     * xml name: tableNamePrefix
     * 
     */
    private java.lang.String _tableNamePrefix ;
    
    /**
     *  
     * xml name: tables
     * 
     */
    private KeyedList<io.nop.dbtool.exp.config.ImportTableConfig> _tables = KeyedList.emptyList();
    
    /**
     *  
     * xml name: threadCount
     * 
     */
    private int _threadCount  = 0;
    
    /**
     * 
     * xml name: batchSize
     *  
     */
    
    public int getBatchSize(){
      return _batchSize;
    }

    
    public void setBatchSize(int value){
        checkAllowChange();
        
        this._batchSize = value;
           
    }

    
    /**
     * 
     * xml name: excludeTableNames
     *  
     */
    
    public java.util.Set<java.lang.String> getExcludeTableNames(){
      return _excludeTableNames;
    }

    
    public void setExcludeTableNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._excludeTableNames = value;
           
    }

    
    /**
     * 
     * xml name: ignoreDuplicate
     *  
     */
    
    public boolean isIgnoreDuplicate(){
      return _ignoreDuplicate;
    }

    
    public void setIgnoreDuplicate(boolean value){
        checkAllowChange();
        
        this._ignoreDuplicate = value;
           
    }

    
    /**
     * 
     * xml name: importAllTables
     *  
     */
    
    public boolean isImportAllTables(){
      return _importAllTables;
    }

    
    public void setImportAllTables(boolean value){
        checkAllowChange();
        
        this._importAllTables = value;
           
    }

    
    /**
     * 
     * xml name: inputDir
     *  
     */
    
    public java.lang.String getInputDir(){
      return _inputDir;
    }

    
    public void setInputDir(java.lang.String value){
        checkAllowChange();
        
        this._inputDir = value;
           
    }

    
    /**
     * 
     * xml name: jdbc-connection
     *  
     */
    
    public io.nop.dbtool.exp.config.JdbcConnectionConfig getJdbcConnection(){
      return _jdbcConnection;
    }

    
    public void setJdbcConnection(io.nop.dbtool.exp.config.JdbcConnectionConfig value){
        checkAllowChange();
        
        this._jdbcConnection = value;
           
    }

    
    /**
     * 
     * xml name: tableNamePrefix
     *  
     */
    
    public java.lang.String getTableNamePrefix(){
      return _tableNamePrefix;
    }

    
    public void setTableNamePrefix(java.lang.String value){
        checkAllowChange();
        
        this._tableNamePrefix = value;
           
    }

    
    /**
     * 
     * xml name: tables
     *  
     */
    
    public java.util.List<io.nop.dbtool.exp.config.ImportTableConfig> getTables(){
      return _tables;
    }

    
    public void setTables(java.util.List<io.nop.dbtool.exp.config.ImportTableConfig> value){
        checkAllowChange();
        
        this._tables = KeyedList.fromList(value, io.nop.dbtool.exp.config.ImportTableConfig::getName);
           
    }

    
    public io.nop.dbtool.exp.config.ImportTableConfig getTable(String name){
        return this._tables.getByKey(name);
    }

    public boolean hasTable(String name){
        return this._tables.containsKey(name);
    }

    public void addTable(io.nop.dbtool.exp.config.ImportTableConfig item) {
        checkAllowChange();
        java.util.List<io.nop.dbtool.exp.config.ImportTableConfig> list = this.getTables();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dbtool.exp.config.ImportTableConfig::getName);
            setTables(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_tables(){
        return this._tables.keySet();
    }

    public boolean hasTables(){
        return !this._tables.isEmpty();
    }
    
    /**
     * 
     * xml name: threadCount
     *  
     */
    
    public int getThreadCount(){
      return _threadCount;
    }

    
    public void setThreadCount(int value){
        checkAllowChange();
        
        this._threadCount = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._jdbcConnection = io.nop.api.core.util.FreezeHelper.deepFreeze(this._jdbcConnection);
            
           this._tables = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tables);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("batchSize",this.getBatchSize());
        out.putNotNull("excludeTableNames",this.getExcludeTableNames());
        out.putNotNull("ignoreDuplicate",this.isIgnoreDuplicate());
        out.putNotNull("importAllTables",this.isImportAllTables());
        out.putNotNull("inputDir",this.getInputDir());
        out.putNotNull("jdbcConnection",this.getJdbcConnection());
        out.putNotNull("tableNamePrefix",this.getTableNamePrefix());
        out.putNotNull("tables",this.getTables());
        out.putNotNull("threadCount",this.getThreadCount());
    }

    public ImportDbConfig cloneInstance(){
        ImportDbConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ImportDbConfig instance){
        super.copyTo(instance);
        
        instance.setBatchSize(this.getBatchSize());
        instance.setExcludeTableNames(this.getExcludeTableNames());
        instance.setIgnoreDuplicate(this.isIgnoreDuplicate());
        instance.setImportAllTables(this.isImportAllTables());
        instance.setInputDir(this.getInputDir());
        instance.setJdbcConnection(this.getJdbcConnection());
        instance.setTableNamePrefix(this.getTableNamePrefix());
        instance.setTables(this.getTables());
        instance.setThreadCount(this.getThreadCount());
    }

    protected ImportDbConfig newInstance(){
        return (ImportDbConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
