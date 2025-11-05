package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizLoaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizLoaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.biz.model.BizActionArgModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: argsNormalizer
     * 
     */
    private java.lang.String _argsNormalizer ;
    
    /**
     *  
     * xml name: autoCreateField
     * 
     */
    private boolean _autoCreateField  = false;
    
    /**
     *  
     * xml name: cache
     * 是否缓存action调用结果
     */
    private io.nop.biz.model.BizCacheModel _cache ;
    
    /**
     *  
     * xml name: disabled
     * 
     */
    private boolean _disabled  = false;
    
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
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
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
     * xml name: argsNormalizer
     *  
     */
    
    public java.lang.String getArgsNormalizer(){
      return _argsNormalizer;
    }

    
    public void setArgsNormalizer(java.lang.String value){
        checkAllowChange();
        
        this._argsNormalizer = value;
           
    }

    
    /**
     * 
     * xml name: autoCreateField
     *  
     */
    
    public boolean isAutoCreateField(){
      return _autoCreateField;
    }

    
    public void setAutoCreateField(boolean value){
        checkAllowChange();
        
        this._autoCreateField = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
           this._cache = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cache);
            
           this._return = io.nop.api.core.util.FreezeHelper.deepFreeze(this._return);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("args",this.getArgs());
        out.putNotNull("argsNormalizer",this.getArgsNormalizer());
        out.putNotNull("autoCreateField",this.isAutoCreateField());
        out.putNotNull("cache",this.getCache());
        out.putNotNull("disabled",this.isDisabled());
        out.putNotNull("name",this.getName());
        out.putNotNull("return",this.getReturn());
        out.putNotNull("source",this.getSource());
    }

    public BizLoaderModel cloneInstance(){
        BizLoaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizLoaderModel instance){
        super.copyTo(instance);
        
        instance.setArgs(this.getArgs());
        instance.setArgsNormalizer(this.getArgsNormalizer());
        instance.setAutoCreateField(this.isAutoCreateField());
        instance.setCache(this.getCache());
        instance.setDisabled(this.isDisabled());
        instance.setName(this.getName());
        instance.setReturn(this.getReturn());
        instance.setSource(this.getSource());
    }

    protected BizLoaderModel newInstance(){
        return (BizLoaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
