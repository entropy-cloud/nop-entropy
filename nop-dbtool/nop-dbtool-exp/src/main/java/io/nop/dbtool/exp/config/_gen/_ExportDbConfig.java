package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ExportDbConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/export-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExportDbConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: exportAllTables
     * 
     */
    private boolean _exportAllTables  = false;
    
    /**
     *  
     * xml name: exportFormats
     * 
     */
    private java.util.Set<java.lang.String> _exportFormats ;
    
    /**
     *  
     * xml name: jdbc-connection
     * 
     */
    private io.nop.dbtool.exp.config.JdbcConnectionConfig _jdbcConnection ;
    
    /**
     *  
     * xml name: outputDir
     * 
     */
    private java.lang.String _outputDir ;
    
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
    private KeyedList<io.nop.dbtool.exp.config.ExportTableConfig> _tables = KeyedList.emptyList();
    
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
     * xml name: exportAllTables
     *  
     */
    
    public boolean isExportAllTables(){
      return _exportAllTables;
    }

    
    public void setExportAllTables(boolean value){
        checkAllowChange();
        
        this._exportAllTables = value;
           
    }

    
    /**
     * 
     * xml name: exportFormats
     *  
     */
    
    public java.util.Set<java.lang.String> getExportFormats(){
      return _exportFormats;
    }

    
    public void setExportFormats(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._exportFormats = value;
           
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
     * xml name: outputDir
     *  
     */
    
    public java.lang.String getOutputDir(){
      return _outputDir;
    }

    
    public void setOutputDir(java.lang.String value){
        checkAllowChange();
        
        this._outputDir = value;
           
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
    
    public java.util.List<io.nop.dbtool.exp.config.ExportTableConfig> getTables(){
      return _tables;
    }

    
    public void setTables(java.util.List<io.nop.dbtool.exp.config.ExportTableConfig> value){
        checkAllowChange();
        
        this._tables = KeyedList.fromList(value, io.nop.dbtool.exp.config.ExportTableConfig::getName);
           
    }

    
    public io.nop.dbtool.exp.config.ExportTableConfig getTable(String name){
        return this._tables.getByKey(name);
    }

    public boolean hasTable(String name){
        return this._tables.containsKey(name);
    }

    public void addTable(io.nop.dbtool.exp.config.ExportTableConfig item) {
        checkAllowChange();
        java.util.List<io.nop.dbtool.exp.config.ExportTableConfig> list = this.getTables();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dbtool.exp.config.ExportTableConfig::getName);
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
        out.putNotNull("exportAllTables",this.isExportAllTables());
        out.putNotNull("exportFormats",this.getExportFormats());
        out.putNotNull("jdbcConnection",this.getJdbcConnection());
        out.putNotNull("outputDir",this.getOutputDir());
        out.putNotNull("tableNamePrefix",this.getTableNamePrefix());
        out.putNotNull("tables",this.getTables());
        out.putNotNull("threadCount",this.getThreadCount());
    }

    public ExportDbConfig cloneInstance(){
        ExportDbConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExportDbConfig instance){
        super.copyTo(instance);
        
        instance.setBatchSize(this.getBatchSize());
        instance.setExcludeTableNames(this.getExcludeTableNames());
        instance.setExportAllTables(this.isExportAllTables());
        instance.setExportFormats(this.getExportFormats());
        instance.setJdbcConnection(this.getJdbcConnection());
        instance.setOutputDir(this.getOutputDir());
        instance.setTableNamePrefix(this.getTableNamePrefix());
        instance.setTables(this.getTables());
        instance.setThreadCount(this.getThreadCount());
    }

    protected ExportDbConfig newInstance(){
        return (ExportDbConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
