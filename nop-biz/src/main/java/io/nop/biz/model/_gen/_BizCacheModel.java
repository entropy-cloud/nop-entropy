package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizCacheModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [48:14:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 是否缓存action调用结果
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizCacheModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cacheKeyExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _cacheKeyExpr ;
    
    /**
     *  
     * xml name: cacheName
     * 
     */
    private java.lang.String _cacheName ;
    
    /**
     * 
     * xml name: cacheKeyExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getCacheKeyExpr(){
      return _cacheKeyExpr;
    }

    
    public void setCacheKeyExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._cacheKeyExpr = value;
           
    }

    
    /**
     * 
     * xml name: cacheName
     *  
     */
    
    public java.lang.String getCacheName(){
      return _cacheName;
    }

    
    public void setCacheName(java.lang.String value){
        checkAllowChange();
        
        this._cacheName = value;
           
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
        
        out.putNotNull("cacheKeyExpr",this.getCacheKeyExpr());
        out.putNotNull("cacheName",this.getCacheName());
    }

    public BizCacheModel cloneInstance(){
        BizCacheModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizCacheModel instance){
        super.copyTo(instance);
        
        instance.setCacheKeyExpr(this.getCacheKeyExpr());
        instance.setCacheName(this.getCacheName());
    }

    protected BizCacheModel newInstance(){
        return (BizCacheModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
