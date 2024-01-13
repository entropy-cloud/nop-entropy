package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizActionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [28:10:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizActionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.biz.model.BizActionArgModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: args-normalizer
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _argsNormalizer ;
    
    /**
     *  
     * xml name: async
     * 是否异步调用。如果是异步调用，则返回CompletionStage。return部分描述的是异步返回的数据的类型
     */
    private boolean _async  = false;
    
    /**
     *  
     * xml name: auth
     * 
     */
    private io.nop.api.core.auth.ActionAuthMeta _auth ;
    
    /**
     *  
     * xml name: bizSequential
     * 
     */
    private boolean _bizSequential  = false;
    
    /**
     *  
     * xml name: cache
     * 是否缓存action调用结果
     */
    private io.nop.biz.model.BizCacheModel _cache ;
    
    /**
     *  
     * xml name: cache-evicts
     * 
     */
    private java.util.List<io.nop.biz.model.BizCacheEvictModel> _cacheEvicts = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: disabled
     * 
     */
    private boolean _disabled  = false;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: executor
     * executor + "Executor"为BeanLoader中的bean的id, 用于控制异步执行时的分组
     */
    private java.lang.String _executor ;
    
    /**
     *  
     * xml name: idempotent
     * 多次执行不会产生不同的结果
     */
    private boolean _idempotent  = false;
    
    /**
     *  
     * xml name: maker-checker
     * 
     */
    private io.nop.biz.model.BizMakerCheckerModel _makerChecker ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: return
     * 
     */
    private io.nop.biz.model.BizReturnModel _return ;
    
    /**
     *  
     * xml name: safe
     * 
     */
    private boolean _safe  = false;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     *  
     * xml name: tcc
     * 
     */
    private io.nop.biz.model.BizTccModel _tcc ;
    
    /**
     *  
     * xml name: timeout
     * 异步执行时的超时时间
     */
    private java.lang.Long _timeout ;
    
    /**
     *  
     * xml name: txn
     * 
     */
    private io.nop.biz.model.BizTxnModel _txn ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: arg
     *  
     */
    
    public java.util.List<io.nop.biz.model.BizActionArgModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.biz.model.BizActionArgModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.biz.model.BizActionArgModel::getName);
           
    }

    
    public io.nop.biz.model.BizActionArgModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.biz.model.BizActionArgModel item) {
        checkAllowChange();
        java.util.List<io.nop.biz.model.BizActionArgModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.biz.model.BizActionArgModel::getName);
            setArgs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_args(){
        return this._args.keySet();
    }

    public boolean hasArgs(){
        return !this._args.isEmpty();
    }
    
    /**
     * 
     * xml name: args-normalizer
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getArgsNormalizer(){
      return _argsNormalizer;
    }

    
    public void setArgsNormalizer(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._argsNormalizer = value;
           
    }

    
    /**
     * 
     * xml name: async
     *  是否异步调用。如果是异步调用，则返回CompletionStage。return部分描述的是异步返回的数据的类型
     */
    
    public boolean isAsync(){
      return _async;
    }

    
    public void setAsync(boolean value){
        checkAllowChange();
        
        this._async = value;
           
    }

    
    /**
     * 
     * xml name: auth
     *  
     */
    
    public io.nop.api.core.auth.ActionAuthMeta getAuth(){
      return _auth;
    }

    
    public void setAuth(io.nop.api.core.auth.ActionAuthMeta value){
        checkAllowChange();
        
        this._auth = value;
           
    }

    
    /**
     * 
     * xml name: bizSequential
     *  
     */
    
    public boolean isBizSequential(){
      return _bizSequential;
    }

    
    public void setBizSequential(boolean value){
        checkAllowChange();
        
        this._bizSequential = value;
           
    }

    
    /**
     * 
     * xml name: cache
     *  是否缓存action调用结果
     */
    
    public io.nop.biz.model.BizCacheModel getCache(){
      return _cache;
    }

    
    public void setCache(io.nop.biz.model.BizCacheModel value){
        checkAllowChange();
        
        this._cache = value;
           
    }

    
    /**
     * 
     * xml name: cache-evicts
     *  
     */
    
    public java.util.List<io.nop.biz.model.BizCacheEvictModel> getCacheEvicts(){
      return _cacheEvicts;
    }

    
    public void setCacheEvicts(java.util.List<io.nop.biz.model.BizCacheEvictModel> value){
        checkAllowChange();
        
        this._cacheEvicts = value;
           
    }

    
    /**
     * 
     * xml name: disabled
     *  
     */
    
    public boolean isDisabled(){
      return _disabled;
    }

    
    public void setDisabled(boolean value){
        checkAllowChange();
        
        this._disabled = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: executor
     *  executor + "Executor"为BeanLoader中的bean的id, 用于控制异步执行时的分组
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
     * xml name: idempotent
     *  多次执行不会产生不同的结果
     */
    
    public boolean isIdempotent(){
      return _idempotent;
    }

    
    public void setIdempotent(boolean value){
        checkAllowChange();
        
        this._idempotent = value;
           
    }

    
    /**
     * 
     * xml name: maker-checker
     *  
     */
    
    public io.nop.biz.model.BizMakerCheckerModel getMakerChecker(){
      return _makerChecker;
    }

    
    public void setMakerChecker(io.nop.biz.model.BizMakerCheckerModel value){
        checkAllowChange();
        
        this._makerChecker = value;
           
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
     * xml name: return
     *  
     */
    
    public io.nop.biz.model.BizReturnModel getReturn(){
      return _return;
    }

    
    public void setReturn(io.nop.biz.model.BizReturnModel value){
        checkAllowChange();
        
        this._return = value;
           
    }

    
    /**
     * 
     * xml name: safe
     *  
     */
    
    public boolean isSafe(){
      return _safe;
    }

    
    public void setSafe(boolean value){
        checkAllowChange();
        
        this._safe = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: tcc
     *  
     */
    
    public io.nop.biz.model.BizTccModel getTcc(){
      return _tcc;
    }

    
    public void setTcc(io.nop.biz.model.BizTccModel value){
        checkAllowChange();
        
        this._tcc = value;
           
    }

    
    /**
     * 
     * xml name: timeout
     *  异步执行时的超时时间
     */
    
    public java.lang.Long getTimeout(){
      return _timeout;
    }

    
    public void setTimeout(java.lang.Long value){
        checkAllowChange();
        
        this._timeout = value;
           
    }

    
    /**
     * 
     * xml name: txn
     *  
     */
    
    public io.nop.biz.model.BizTxnModel getTxn(){
      return _txn;
    }

    
    public void setTxn(io.nop.biz.model.BizTxnModel value){
        checkAllowChange();
        
        this._txn = value;
           
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
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
           this._auth = io.nop.api.core.util.FreezeHelper.deepFreeze(this._auth);
            
           this._cache = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cache);
            
           this._cacheEvicts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cacheEvicts);
            
           this._makerChecker = io.nop.api.core.util.FreezeHelper.deepFreeze(this._makerChecker);
            
           this._return = io.nop.api.core.util.FreezeHelper.deepFreeze(this._return);
            
           this._tcc = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tcc);
            
           this._txn = io.nop.api.core.util.FreezeHelper.deepFreeze(this._txn);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("args",this.getArgs());
        out.put("argsNormalizer",this.getArgsNormalizer());
        out.put("async",this.isAsync());
        out.put("auth",this.getAuth());
        out.put("bizSequential",this.isBizSequential());
        out.put("cache",this.getCache());
        out.put("cacheEvicts",this.getCacheEvicts());
        out.put("disabled",this.isDisabled());
        out.put("displayName",this.getDisplayName());
        out.put("executor",this.getExecutor());
        out.put("idempotent",this.isIdempotent());
        out.put("makerChecker",this.getMakerChecker());
        out.put("name",this.getName());
        out.put("return",this.getReturn());
        out.put("safe",this.isSafe());
        out.put("source",this.getSource());
        out.put("tcc",this.getTcc());
        out.put("timeout",this.getTimeout());
        out.put("txn",this.getTxn());
        out.put("type",this.getType());
    }

    public BizActionModel cloneInstance(){
        BizActionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizActionModel instance){
        super.copyTo(instance);
        
        instance.setArgs(this.getArgs());
        instance.setArgsNormalizer(this.getArgsNormalizer());
        instance.setAsync(this.isAsync());
        instance.setAuth(this.getAuth());
        instance.setBizSequential(this.isBizSequential());
        instance.setCache(this.getCache());
        instance.setCacheEvicts(this.getCacheEvicts());
        instance.setDisabled(this.isDisabled());
        instance.setDisplayName(this.getDisplayName());
        instance.setExecutor(this.getExecutor());
        instance.setIdempotent(this.isIdempotent());
        instance.setMakerChecker(this.getMakerChecker());
        instance.setName(this.getName());
        instance.setReturn(this.getReturn());
        instance.setSafe(this.isSafe());
        instance.setSource(this.getSource());
        instance.setTcc(this.getTcc());
        instance.setTimeout(this.getTimeout());
        instance.setTxn(this.getTxn());
        instance.setType(this.getType());
    }

    protected BizActionModel newInstance(){
        return (BizActionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
