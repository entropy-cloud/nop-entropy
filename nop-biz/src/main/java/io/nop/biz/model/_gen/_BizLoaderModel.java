package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [82:10:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BizLoaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.biz.model.BizActionArgModel> _args = KeyedList.emptyList();
    
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
        
        out.put("args",this.getArgs());
        out.put("cache",this.getCache());
        out.put("disabled",this.isDisabled());
        out.put("name",this.getName());
        out.put("return",this.getReturn());
        out.put("source",this.getSource());
    }
}
 // resume CPD analysis - CPD-ON
