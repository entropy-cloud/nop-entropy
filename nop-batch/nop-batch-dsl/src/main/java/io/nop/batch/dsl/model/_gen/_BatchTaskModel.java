package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchTaskModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchTaskModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: batchSize
     * 
     */
    private int _batchSize ;
    
    /**
     *  
     * xml name: chunk-processor
     * 
     */
    private io.nop.batch.dsl.model.BatchChunkProcessorModel _chunkProcessor ;
    
    /**
     *  
     * xml name: concurrency
     * 同时启动多少个线程去并行处理
     */
    private int _concurrency  = 1;
    
    /**
     *  
     * xml name: executor
     * 
     */
    private java.lang.String _executor ;
    
    /**
     *  
     * xml name: inputSorter
     * 
     */
    private KeyedList<io.nop.api.core.beans.query.OrderFieldBean> _inputSorter = KeyedList.emptyList();
    
    /**
     *  
     * xml name: jitterRatio
     * 多线程执行时，如果每个线程处理的batchSize都相同，则可能导致同时读取数据库和同时写数据库，产生资源征用。 通过设置一个随机比例，将每个线程处理的batchSize动态调整为originalBatchSize * (1
     * + jitterRatio * random)， 使得每个线程的每个批次的负载随机化，从而破坏潜在的同步效应。
     */
    private java.lang.Double _jitterRatio ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: processor
     * 
     */
    private KeyedList<io.nop.batch.dsl.model.BatchProcessorModel> _processors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: rateLimit
     * 每秒最多处理多少条记录
     */
    private java.lang.Double _rateLimit ;
    
    /**
     *  
     * xml name: reader
     * 
     */
    private io.nop.batch.dsl.model.BatchReaderModel _reader ;
    
    /**
     *  
     * xml name: retryOneByOne
     * 重试的时候是否逐个重试，还是一整批重试
     */
    private java.lang.Boolean _retryOneByOne  = false;
    
    /**
     *  
     * xml name: retryPolicy
     * 
     */
    private io.nop.batch.dsl.model.BatchRetryPolicyModel _retryPolicy ;
    
    /**
     *  
     * xml name: singleMode
     * 
     */
    private java.lang.Boolean _singleMode  = false;
    
    /**
     *  
     * xml name: singleSession
     * 
     */
    private java.lang.Boolean _singleSession ;
    
    /**
     *  
     * xml name: skipPolicy
     * 
     */
    private io.nop.batch.dsl.model.BatchSkipPolicyModel _skipPolicy ;
    
    /**
     *  
     * xml name: tagger
     * 
     */
    private io.nop.batch.dsl.model.BatchTaggerModel _tagger ;
    
    /**
     *  
     * xml name: transactionScope
     * 
     */
    private io.nop.batch.core.BatchTransactionScope _transactionScope ;
    
    /**
     *  
     * xml name: writer
     * 
     */
    private KeyedList<io.nop.batch.dsl.model.BatchWriterModel> _writers = KeyedList.emptyList();
    
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
     * xml name: chunk-processor
     *  
     */
    
    public io.nop.batch.dsl.model.BatchChunkProcessorModel getChunkProcessor(){
      return _chunkProcessor;
    }

    
    public void setChunkProcessor(io.nop.batch.dsl.model.BatchChunkProcessorModel value){
        checkAllowChange();
        
        this._chunkProcessor = value;
           
    }

    
    /**
     * 
     * xml name: concurrency
     *  同时启动多少个线程去并行处理
     */
    
    public int getConcurrency(){
      return _concurrency;
    }

    
    public void setConcurrency(int value){
        checkAllowChange();
        
        this._concurrency = value;
           
    }

    
    /**
     * 
     * xml name: executor
     *  
     */
    
    public java.lang.String getExecutor(){
      return _executor;
    }

    
    public void setExecutor(java.lang.String value){
        checkAllowChange();
        
        this._executor = value;
           
    }

    
    /**
     * 
     * xml name: inputSorter
     *  
     */
    
    public java.util.List<io.nop.api.core.beans.query.OrderFieldBean> getInputSorter(){
      return _inputSorter;
    }

    
    public void setInputSorter(java.util.List<io.nop.api.core.beans.query.OrderFieldBean> value){
        checkAllowChange();
        
        this._inputSorter = KeyedList.fromList(value, io.nop.api.core.beans.query.OrderFieldBean::getName);
           
    }

    
    public io.nop.api.core.beans.query.OrderFieldBean getField(String name){
        return this._inputSorter.getByKey(name);
    }

    public boolean hasField(String name){
        return this._inputSorter.containsKey(name);
    }

    public void addField(io.nop.api.core.beans.query.OrderFieldBean item) {
        checkAllowChange();
        java.util.List<io.nop.api.core.beans.query.OrderFieldBean> list = this.getInputSorter();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.api.core.beans.query.OrderFieldBean::getName);
            setInputSorter(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_inputSorter(){
        return this._inputSorter.keySet();
    }

    public boolean hasInputSorter(){
        return !this._inputSorter.isEmpty();
    }
    
    /**
     * 
     * xml name: jitterRatio
     *  多线程执行时，如果每个线程处理的batchSize都相同，则可能导致同时读取数据库和同时写数据库，产生资源征用。 通过设置一个随机比例，将每个线程处理的batchSize动态调整为originalBatchSize * (1
     * + jitterRatio * random)， 使得每个线程的每个批次的负载随机化，从而破坏潜在的同步效应。
     */
    
    public java.lang.Double getJitterRatio(){
      return _jitterRatio;
    }

    
    public void setJitterRatio(java.lang.Double value){
        checkAllowChange();
        
        this._jitterRatio = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
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
     * xml name: processor
     *  
     */
    
    public java.util.List<io.nop.batch.dsl.model.BatchProcessorModel> getProcessors(){
      return _processors;
    }

    
    public void setProcessors(java.util.List<io.nop.batch.dsl.model.BatchProcessorModel> value){
        checkAllowChange();
        
        this._processors = KeyedList.fromList(value, io.nop.batch.dsl.model.BatchProcessorModel::getName);
           
    }

    
    public io.nop.batch.dsl.model.BatchProcessorModel getProcessor(String name){
        return this._processors.getByKey(name);
    }

    public boolean hasProcessor(String name){
        return this._processors.containsKey(name);
    }

    public void addProcessor(io.nop.batch.dsl.model.BatchProcessorModel item) {
        checkAllowChange();
        java.util.List<io.nop.batch.dsl.model.BatchProcessorModel> list = this.getProcessors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.batch.dsl.model.BatchProcessorModel::getName);
            setProcessors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_processors(){
        return this._processors.keySet();
    }

    public boolean hasProcessors(){
        return !this._processors.isEmpty();
    }
    
    /**
     * 
     * xml name: rateLimit
     *  每秒最多处理多少条记录
     */
    
    public java.lang.Double getRateLimit(){
      return _rateLimit;
    }

    
    public void setRateLimit(java.lang.Double value){
        checkAllowChange();
        
        this._rateLimit = value;
           
    }

    
    /**
     * 
     * xml name: reader
     *  
     */
    
    public io.nop.batch.dsl.model.BatchReaderModel getReader(){
      return _reader;
    }

    
    public void setReader(io.nop.batch.dsl.model.BatchReaderModel value){
        checkAllowChange();
        
        this._reader = value;
           
    }

    
    /**
     * 
     * xml name: retryOneByOne
     *  重试的时候是否逐个重试，还是一整批重试
     */
    
    public java.lang.Boolean getRetryOneByOne(){
      return _retryOneByOne;
    }

    
    public void setRetryOneByOne(java.lang.Boolean value){
        checkAllowChange();
        
        this._retryOneByOne = value;
           
    }

    
    /**
     * 
     * xml name: retryPolicy
     *  
     */
    
    public io.nop.batch.dsl.model.BatchRetryPolicyModel getRetryPolicy(){
      return _retryPolicy;
    }

    
    public void setRetryPolicy(io.nop.batch.dsl.model.BatchRetryPolicyModel value){
        checkAllowChange();
        
        this._retryPolicy = value;
           
    }

    
    /**
     * 
     * xml name: singleMode
     *  
     */
    
    public java.lang.Boolean getSingleMode(){
      return _singleMode;
    }

    
    public void setSingleMode(java.lang.Boolean value){
        checkAllowChange();
        
        this._singleMode = value;
           
    }

    
    /**
     * 
     * xml name: singleSession
     *  
     */
    
    public java.lang.Boolean getSingleSession(){
      return _singleSession;
    }

    
    public void setSingleSession(java.lang.Boolean value){
        checkAllowChange();
        
        this._singleSession = value;
           
    }

    
    /**
     * 
     * xml name: skipPolicy
     *  
     */
    
    public io.nop.batch.dsl.model.BatchSkipPolicyModel getSkipPolicy(){
      return _skipPolicy;
    }

    
    public void setSkipPolicy(io.nop.batch.dsl.model.BatchSkipPolicyModel value){
        checkAllowChange();
        
        this._skipPolicy = value;
           
    }

    
    /**
     * 
     * xml name: tagger
     *  
     */
    
    public io.nop.batch.dsl.model.BatchTaggerModel getTagger(){
      return _tagger;
    }

    
    public void setTagger(io.nop.batch.dsl.model.BatchTaggerModel value){
        checkAllowChange();
        
        this._tagger = value;
           
    }

    
    /**
     * 
     * xml name: transactionScope
     *  
     */
    
    public io.nop.batch.core.BatchTransactionScope getTransactionScope(){
      return _transactionScope;
    }

    
    public void setTransactionScope(io.nop.batch.core.BatchTransactionScope value){
        checkAllowChange();
        
        this._transactionScope = value;
           
    }

    
    /**
     * 
     * xml name: writer
     *  
     */
    
    public java.util.List<io.nop.batch.dsl.model.BatchWriterModel> getWriters(){
      return _writers;
    }

    
    public void setWriters(java.util.List<io.nop.batch.dsl.model.BatchWriterModel> value){
        checkAllowChange();
        
        this._writers = KeyedList.fromList(value, io.nop.batch.dsl.model.BatchWriterModel::getName);
           
    }

    
    public io.nop.batch.dsl.model.BatchWriterModel getWriter(String name){
        return this._writers.getByKey(name);
    }

    public boolean hasWriter(String name){
        return this._writers.containsKey(name);
    }

    public void addWriter(io.nop.batch.dsl.model.BatchWriterModel item) {
        checkAllowChange();
        java.util.List<io.nop.batch.dsl.model.BatchWriterModel> list = this.getWriters();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.batch.dsl.model.BatchWriterModel::getName);
            setWriters(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_writers(){
        return this._writers.keySet();
    }

    public boolean hasWriters(){
        return !this._writers.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._chunkProcessor = io.nop.api.core.util.FreezeHelper.deepFreeze(this._chunkProcessor);
            
           this._inputSorter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputSorter);
            
           this._processors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._processors);
            
           this._reader = io.nop.api.core.util.FreezeHelper.deepFreeze(this._reader);
            
           this._retryPolicy = io.nop.api.core.util.FreezeHelper.deepFreeze(this._retryPolicy);
            
           this._skipPolicy = io.nop.api.core.util.FreezeHelper.deepFreeze(this._skipPolicy);
            
           this._tagger = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tagger);
            
           this._writers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._writers);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("batchSize",this.getBatchSize());
        out.putNotNull("chunkProcessor",this.getChunkProcessor());
        out.putNotNull("concurrency",this.getConcurrency());
        out.putNotNull("executor",this.getExecutor());
        out.putNotNull("inputSorter",this.getInputSorter());
        out.putNotNull("jitterRatio",this.getJitterRatio());
        out.putNotNull("name",this.getName());
        out.putNotNull("processors",this.getProcessors());
        out.putNotNull("rateLimit",this.getRateLimit());
        out.putNotNull("reader",this.getReader());
        out.putNotNull("retryOneByOne",this.getRetryOneByOne());
        out.putNotNull("retryPolicy",this.getRetryPolicy());
        out.putNotNull("singleMode",this.getSingleMode());
        out.putNotNull("singleSession",this.getSingleSession());
        out.putNotNull("skipPolicy",this.getSkipPolicy());
        out.putNotNull("tagger",this.getTagger());
        out.putNotNull("transactionScope",this.getTransactionScope());
        out.putNotNull("writers",this.getWriters());
    }

    public BatchTaskModel cloneInstance(){
        BatchTaskModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchTaskModel instance){
        super.copyTo(instance);
        
        instance.setBatchSize(this.getBatchSize());
        instance.setChunkProcessor(this.getChunkProcessor());
        instance.setConcurrency(this.getConcurrency());
        instance.setExecutor(this.getExecutor());
        instance.setInputSorter(this.getInputSorter());
        instance.setJitterRatio(this.getJitterRatio());
        instance.setName(this.getName());
        instance.setProcessors(this.getProcessors());
        instance.setRateLimit(this.getRateLimit());
        instance.setReader(this.getReader());
        instance.setRetryOneByOne(this.getRetryOneByOne());
        instance.setRetryPolicy(this.getRetryPolicy());
        instance.setSingleMode(this.getSingleMode());
        instance.setSingleSession(this.getSingleSession());
        instance.setSkipPolicy(this.getSkipPolicy());
        instance.setTagger(this.getTagger());
        instance.setTransactionScope(this.getTransactionScope());
        instance.setWriters(this.getWriters());
    }

    protected BatchTaskModel newInstance(){
        return (BatchTaskModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
