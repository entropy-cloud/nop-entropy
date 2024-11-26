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
     * xml name: chunk-processor-builder
     * 
     */
    private io.nop.batch.dsl.model.BatchChunkProcessorBuilderModel _chunkProcessorBuilder ;
    
    /**
     *  
     * xml name: concurrency
     * 同时启动多少个线程去并行处理
     */
    private int _concurrency  = 1;
    
    /**
     *  
     * xml name: consumer
     * 
     */
    private KeyedList<io.nop.batch.dsl.model.BatchConsumerModel> _consumers = KeyedList.emptyList();
    
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
     * xml name: loadRetryPolicy
     * 
     */
    private io.nop.batch.dsl.model.BatchRetryPolicyModel _loadRetryPolicy ;
    
    /**
     *  
     * xml name: loader
     * 
     */
    private io.nop.batch.dsl.model.BatchLoaderModel _loader ;
    
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
     * xml name: saveState
     * 
     */
    private java.lang.Boolean _saveState ;
    
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
     * xml name: taskKeyExpr
     * taskKey用于区分taskName相同的不同执行实例。同样的taskName+taskKey只允许执行一次
     */
    private io.nop.core.lang.eval.IEvalFunction _taskKeyExpr ;
    
    /**
     *  
     * xml name: taskName
     * 
     */
    private java.lang.String _taskName ;
    
    /**
     *  
     * xml name: taskVersion
     * 
     */
    private java.lang.Long _taskVersion ;
    
    /**
     *  
     * xml name: transactionScope
     * 
     */
    private io.nop.batch.core.BatchTransactionScope _transactionScope ;
    
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
     * xml name: chunk-processor-builder
     *  
     */
    
    public io.nop.batch.dsl.model.BatchChunkProcessorBuilderModel getChunkProcessorBuilder(){
      return _chunkProcessorBuilder;
    }

    
    public void setChunkProcessorBuilder(io.nop.batch.dsl.model.BatchChunkProcessorBuilderModel value){
        checkAllowChange();
        
        this._chunkProcessorBuilder = value;
           
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
     * xml name: consumer
     *  
     */
    
    public java.util.List<io.nop.batch.dsl.model.BatchConsumerModel> getConsumers(){
      return _consumers;
    }

    
    public void setConsumers(java.util.List<io.nop.batch.dsl.model.BatchConsumerModel> value){
        checkAllowChange();
        
        this._consumers = KeyedList.fromList(value, io.nop.batch.dsl.model.BatchConsumerModel::getName);
           
    }

    
    public io.nop.batch.dsl.model.BatchConsumerModel getConsumer(String name){
        return this._consumers.getByKey(name);
    }

    public boolean hasConsumer(String name){
        return this._consumers.containsKey(name);
    }

    public void addConsumer(io.nop.batch.dsl.model.BatchConsumerModel item) {
        checkAllowChange();
        java.util.List<io.nop.batch.dsl.model.BatchConsumerModel> list = this.getConsumers();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.batch.dsl.model.BatchConsumerModel::getName);
            setConsumers(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_consumers(){
        return this._consumers.keySet();
    }

    public boolean hasConsumers(){
        return !this._consumers.isEmpty();
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
     * xml name: loadRetryPolicy
     *  
     */
    
    public io.nop.batch.dsl.model.BatchRetryPolicyModel getLoadRetryPolicy(){
      return _loadRetryPolicy;
    }

    
    public void setLoadRetryPolicy(io.nop.batch.dsl.model.BatchRetryPolicyModel value){
        checkAllowChange();
        
        this._loadRetryPolicy = value;
           
    }

    
    /**
     * 
     * xml name: loader
     *  
     */
    
    public io.nop.batch.dsl.model.BatchLoaderModel getLoader(){
      return _loader;
    }

    
    public void setLoader(io.nop.batch.dsl.model.BatchLoaderModel value){
        checkAllowChange();
        
        this._loader = value;
           
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
     * xml name: taskKeyExpr
     *  taskKey用于区分taskName相同的不同执行实例。同样的taskName+taskKey只允许执行一次
     */
    
    public io.nop.core.lang.eval.IEvalFunction getTaskKeyExpr(){
      return _taskKeyExpr;
    }

    
    public void setTaskKeyExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._taskKeyExpr = value;
           
    }

    
    /**
     * 
     * xml name: taskName
     *  
     */
    
    public java.lang.String getTaskName(){
      return _taskName;
    }

    
    public void setTaskName(java.lang.String value){
        checkAllowChange();
        
        this._taskName = value;
           
    }

    
    /**
     * 
     * xml name: taskVersion
     *  
     */
    
    public java.lang.Long getTaskVersion(){
      return _taskVersion;
    }

    
    public void setTaskVersion(java.lang.Long value){
        checkAllowChange();
        
        this._taskVersion = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._chunkProcessorBuilder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._chunkProcessorBuilder);
            
           this._consumers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._consumers);
            
           this._inputSorter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputSorter);
            
           this._loadRetryPolicy = io.nop.api.core.util.FreezeHelper.deepFreeze(this._loadRetryPolicy);
            
           this._loader = io.nop.api.core.util.FreezeHelper.deepFreeze(this._loader);
            
           this._processors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._processors);
            
           this._retryPolicy = io.nop.api.core.util.FreezeHelper.deepFreeze(this._retryPolicy);
            
           this._skipPolicy = io.nop.api.core.util.FreezeHelper.deepFreeze(this._skipPolicy);
            
           this._tagger = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tagger);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("batchSize",this.getBatchSize());
        out.putNotNull("chunkProcessorBuilder",this.getChunkProcessorBuilder());
        out.putNotNull("concurrency",this.getConcurrency());
        out.putNotNull("consumers",this.getConsumers());
        out.putNotNull("executor",this.getExecutor());
        out.putNotNull("inputSorter",this.getInputSorter());
        out.putNotNull("jitterRatio",this.getJitterRatio());
        out.putNotNull("loadRetryPolicy",this.getLoadRetryPolicy());
        out.putNotNull("loader",this.getLoader());
        out.putNotNull("processors",this.getProcessors());
        out.putNotNull("rateLimit",this.getRateLimit());
        out.putNotNull("retryOneByOne",this.getRetryOneByOne());
        out.putNotNull("retryPolicy",this.getRetryPolicy());
        out.putNotNull("saveState",this.getSaveState());
        out.putNotNull("singleMode",this.getSingleMode());
        out.putNotNull("singleSession",this.getSingleSession());
        out.putNotNull("skipPolicy",this.getSkipPolicy());
        out.putNotNull("tagger",this.getTagger());
        out.putNotNull("taskKeyExpr",this.getTaskKeyExpr());
        out.putNotNull("taskName",this.getTaskName());
        out.putNotNull("taskVersion",this.getTaskVersion());
        out.putNotNull("transactionScope",this.getTransactionScope());
    }

    public BatchTaskModel cloneInstance(){
        BatchTaskModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchTaskModel instance){
        super.copyTo(instance);
        
        instance.setBatchSize(this.getBatchSize());
        instance.setChunkProcessorBuilder(this.getChunkProcessorBuilder());
        instance.setConcurrency(this.getConcurrency());
        instance.setConsumers(this.getConsumers());
        instance.setExecutor(this.getExecutor());
        instance.setInputSorter(this.getInputSorter());
        instance.setJitterRatio(this.getJitterRatio());
        instance.setLoadRetryPolicy(this.getLoadRetryPolicy());
        instance.setLoader(this.getLoader());
        instance.setProcessors(this.getProcessors());
        instance.setRateLimit(this.getRateLimit());
        instance.setRetryOneByOne(this.getRetryOneByOne());
        instance.setRetryPolicy(this.getRetryPolicy());
        instance.setSaveState(this.getSaveState());
        instance.setSingleMode(this.getSingleMode());
        instance.setSingleSession(this.getSingleSession());
        instance.setSkipPolicy(this.getSkipPolicy());
        instance.setTagger(this.getTagger());
        instance.setTaskKeyExpr(this.getTaskKeyExpr());
        instance.setTaskName(this.getTaskName());
        instance.setTaskVersion(this.getTaskVersion());
        instance.setTransactionScope(this.getTransactionScope());
    }

    protected BatchTaskModel newInstance(){
        return (BatchTaskModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
