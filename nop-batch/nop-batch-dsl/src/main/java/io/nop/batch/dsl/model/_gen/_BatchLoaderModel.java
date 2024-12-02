package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchLoaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchLoaderModel extends io.nop.batch.dsl.model.BatchListenersModel {
    
    /**
     *  
     * xml name: adapter
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _adapter ;
    
    /**
     *  
     * xml name: aggregator
     * 
     */
    private java.lang.String _aggregator ;
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: file-reader
     * 当resourceIO/newRecordInputProvider/fileModelPath都没有指定的时候，会使用CsvResourceIO
     */
    private io.nop.batch.dsl.model.BatchFileReaderModel _fileReader ;
    
    /**
     *  
     * xml name: generator
     * 
     */
    private io.nop.batch.dsl.model.BatchGeneratorModel _generator ;
    
    /**
     *  
     * xml name: jdbc-reader
     * 
     */
    private io.nop.batch.dsl.model.BatchJdbcReaderModel _jdbcReader ;
    
    /**
     *  
     * xml name: orm-reader
     * 
     */
    private io.nop.batch.dsl.model.BatchOrmReaderModel _ormReader ;
    
    /**
     *  
     * xml name: saveState
     * 
     */
    private java.lang.Boolean _saveState ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _source ;
    
    /**
     * 
     * xml name: adapter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAdapter(){
      return _adapter;
    }

    
    public void setAdapter(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._adapter = value;
           
    }

    
    /**
     * 
     * xml name: aggregator
     *  
     */
    
    public java.lang.String getAggregator(){
      return _aggregator;
    }

    
    public void setAggregator(java.lang.String value){
        checkAllowChange();
        
        this._aggregator = value;
           
    }

    
    /**
     * 
     * xml name: bean
     *  
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: file-reader
     *  当resourceIO/newRecordInputProvider/fileModelPath都没有指定的时候，会使用CsvResourceIO
     */
    
    public io.nop.batch.dsl.model.BatchFileReaderModel getFileReader(){
      return _fileReader;
    }

    
    public void setFileReader(io.nop.batch.dsl.model.BatchFileReaderModel value){
        checkAllowChange();
        
        this._fileReader = value;
           
    }

    
    /**
     * 
     * xml name: generator
     *  
     */
    
    public io.nop.batch.dsl.model.BatchGeneratorModel getGenerator(){
      return _generator;
    }

    
    public void setGenerator(io.nop.batch.dsl.model.BatchGeneratorModel value){
        checkAllowChange();
        
        this._generator = value;
           
    }

    
    /**
     * 
     * xml name: jdbc-reader
     *  
     */
    
    public io.nop.batch.dsl.model.BatchJdbcReaderModel getJdbcReader(){
      return _jdbcReader;
    }

    
    public void setJdbcReader(io.nop.batch.dsl.model.BatchJdbcReaderModel value){
        checkAllowChange();
        
        this._jdbcReader = value;
           
    }

    
    /**
     * 
     * xml name: orm-reader
     *  
     */
    
    public io.nop.batch.dsl.model.BatchOrmReaderModel getOrmReader(){
      return _ormReader;
    }

    
    public void setOrmReader(io.nop.batch.dsl.model.BatchOrmReaderModel value){
        checkAllowChange();
        
        this._ormReader = value;
           
    }

    
    /**
     * 
     * xml name: saveState
     *  
     */
    
    public java.lang.Boolean getSaveState(){
      return _saveState;
    }

    
    public void setSaveState(java.lang.Boolean value){
        checkAllowChange();
        
        this._saveState = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fileReader = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fileReader);
            
           this._generator = io.nop.api.core.util.FreezeHelper.deepFreeze(this._generator);
            
           this._jdbcReader = io.nop.api.core.util.FreezeHelper.deepFreeze(this._jdbcReader);
            
           this._ormReader = io.nop.api.core.util.FreezeHelper.deepFreeze(this._ormReader);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("adapter",this.getAdapter());
        out.putNotNull("aggregator",this.getAggregator());
        out.putNotNull("bean",this.getBean());
        out.putNotNull("fileReader",this.getFileReader());
        out.putNotNull("generator",this.getGenerator());
        out.putNotNull("jdbcReader",this.getJdbcReader());
        out.putNotNull("ormReader",this.getOrmReader());
        out.putNotNull("saveState",this.getSaveState());
        out.putNotNull("source",this.getSource());
    }

    public BatchLoaderModel cloneInstance(){
        BatchLoaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchLoaderModel instance){
        super.copyTo(instance);
        
        instance.setAdapter(this.getAdapter());
        instance.setAggregator(this.getAggregator());
        instance.setBean(this.getBean());
        instance.setFileReader(this.getFileReader());
        instance.setGenerator(this.getGenerator());
        instance.setJdbcReader(this.getJdbcReader());
        instance.setOrmReader(this.getOrmReader());
        instance.setSaveState(this.getSaveState());
        instance.setSource(this.getSource());
    }

    protected BatchLoaderModel newInstance(){
        return (BatchLoaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
